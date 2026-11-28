package co.jose.mibarra;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Versión 27: ✍️ Escribir bien. Se escribe o se dicta, se corrige (sin internet), se organiza
 * y se manda a WhatsApp o se imprime en tamaño carta.
 */
public class EscribirActivity extends Base {

    private static final int VOZ = 71, CUENTA = 72;
    private VozLocutor voz;
    private static volatile Map<String, String> tildes;

    private EditText ed;
    private TextView tNota;
    private String antesDeCorregir;
    private SharedPreferences pr;
    private final Handler h = new Handler(Looper.getMainLooper());

    /** Lo amarillo: guarda cómo estaba antes, para deshacer con un toque. */
    static final class Marca extends BackgroundColorSpan {
        final String antes;
        Marca(String a) { super(0xFFFFF176); antes = a; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pr = getSharedPreferences("escribir", MODE_PRIVATE);
        cargarTildes();
        LinearLayout l = vertical();
        l.setBackgroundColor(0xFFF5F7FA);
        l.setPadding(dp(8), dp(6), dp(8), dp(8));

        ed = new EditText(this);
        ed.setTextSize(18);
        ed.setTextColor(0xFF111111);
        ed.setGravity(Gravity.TOP | Gravity.START);
        ed.setHint("Escriba o dicte aquí su escrito…");
        ed.setLineSpacing(0, 1.15f);
        ed.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(2), 0xFF90A4AE);
        ed.setBackground(g);
        ed.setText(pr.getString("borrador", ""));
        ed.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                int off = ed.getOffsetForPosition(e.getX(), e.getY());
                h.postDelayed(() -> tocoMarca(off), 120);
            }
            return false;
        });
        l.addView(ed, new LinearLayout.LayoutParams(-1, 0, 1f));

        tNota = new TextView(this);
        tNota.setTextSize(13.5f);
        tNota.setTextColor(0xFF333333);
        tNota.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable gn = new GradientDrawable();
        gn.setColor(0xFFFFF8E1);
        gn.setCornerRadius(dp(8));
        tNota.setBackground(gn);
        tNota.setText("✨ Escriba y toque Corregir. Lo que cambie queda en amarillo; toque una palabra amarilla para deshacerla.");
        tNota.setOnClickListener(v -> deshacerTodo());
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(6);
        l.addView(tNota, np);

        LinearLayout f1 = horizontal();
        f1.addView(btn("🎤 Dictar", 0xFFC62828, this::dictar), peso(1));
        f1.addView(btn("✨ Corregir", 0xFF6A1B9A, this::corregir), peso(1));
        f1.addView(btn("📋 Organizar", 0xFFEF6C00, this::organizar), peso(1));
        l.addView(f1, ancho(54));
        LinearLayout f2 = horizontal();
        Button wa = btn("🟢 Enviar texto", 0xFF25D366, this::enviarTexto);
        wa.setTextSize(17);
        Button im = btn("🖨 Imprimir carta", 0xFF263238, this::imprimir);
        im.setTextSize(17);
        f2.addView(wa, peso(1));
        f2.addView(im, peso(1));
        l.addView(f2, ancho(56));
        // Versión 29: Mi Voz — la cuenta, escuchar con la voz de locutor y mandar el audio
        LinearLayout f4 = horizontal();
        f4.addView(btn("🧾 Cuenta", 0xFF2E7D32, () -> startActivityForResult(new Intent(this, CuentaActivity.class), CUENTA)), peso(1));
        Button esc = btn("🔊 Escuchar", 0xFF1565C0, this::escuchar);
        esc.setOnLongClickListener(v -> { ajustesVoz(); return true; });
        f4.addView(esc, peso(1));
        f4.addView(btn("🎙 Enviar audio", 0xFFAD1457, this::enviarAudio), peso(1));
        l.addView(f4, ancho(54));
        LinearLayout f3 = horizontal();
        f3.addView(btn("📄 Copiar", 0xFF546E7A, () -> { if (hay()) Comun.copiar(this, texto()); }), peso(1));
        f3.addView(btn("💾 Guardar / Abrir", 0xFF1565C0, this::guardarAbrir), peso(1));
        f3.addView(btn("⚙ Voz", 0xFF455A64, this::ajustesVoz), peso(0.7f));
        l.addView(f3, ancho(50));
        setContentView(l);
        voz = new VozLocutor(this, null);
        agregarDeCuenta(getIntent());
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); agregarDeCuenta(i); }

    @Override protected void onDestroy() { if (voz != null) voz.cerrar(); super.onDestroy(); }

    /** Si la cuenta llegó de afuera (o del resultado), se pega al final del escrito. */
    private void agregarDeCuenta(Intent i) {
        if (i == null || i.getStringExtra("texto") == null) return;
        String t = i.getStringExtra("texto");
        i.removeExtra("texto");
        String act = texto();
        ed.setText(act.trim().isEmpty() ? t : act.replaceAll("\\s+$", "") + "\n\n" + t);
        ed.setSelection(ed.getText().length());
    }

    /** Enviar texto: si lo abrió el botón flotante encima de WhatsApp, se pega solo en ese chat. */
    private void enviarTexto() {
        if (!hay()) return;
        if (getIntent().getBooleanExtra("desdeWa", false)) {
            Comun.copiar(this, texto());
            sendBroadcast(new Intent(BarraService.AVISO_BARRA).setPackage(getPackageName()).putExtra("pegar", texto()));
            finish();
        } else Comun.whatsapp(this, texto());
    }

    private void escuchar() {
        if (!hay()) return;
        if (voz == null || !voz.lista()) { aviso("La voz todavía está cargando; intente en un momento"); return; }
        voz.hablar(texto());
        tNota.setText("🔊 Leyendo con la voz de locutor… (deje presionado 🔊 para graduar tono y velocidad)");
    }

    /** Graba el audio, deja escucharlo y luego lo manda por WhatsApp. */
    private void enviarAudio() {
        if (!hay()) return;
        if (voz == null || !voz.lista()) { aviso("La voz todavía está cargando; intente en un momento"); return; }
        tNota.setText("🎙 Preparando el audio…");
        java.io.File f = new java.io.File(getCacheDir(), "mivoz.wav");
        f.delete();
        voz.aArchivo(texto(), f, () -> runOnUiThread(() -> {
            if (!f.exists() || f.length() < 1000) { tNota.setText("No se pudo hacer el audio. Revise que Acapela esté instalada."); return; }
            tNota.setText("🎙 Audio listo. Escúchelo antes de enviarlo.");
            android.media.MediaPlayer mp = new android.media.MediaPlayer();
            try { mp.setDataSource(f.getAbsolutePath()); mp.prepare(); } catch (Exception e) { aviso("No se pudo abrir el audio"); return; }
            AlertDialog dd = new AlertDialog.Builder(this).setTitle("🎙 Audio con su voz de locutor")
                    .setMessage("Duración: " + Math.max(1, mp.getDuration() / 1000) + " segundos")
                    .setPositiveButton("🟢 Enviar", null).setNeutralButton("▶ Escuchar", null).setNegativeButton("Cancelar", null)
                    .setCancelable(false).create();
            dd.setOnShowListener(x -> {
                dd.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    if (mp.isPlaying()) { mp.pause(); mp.seekTo(0); } else mp.start();
                });
                dd.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    try { mp.release(); } catch (Exception ignored) {}
                    dd.dismiss();
                    try {
                        byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
                        android.net.Uri u = Util.guardarBytes(this, "MiVoz_" + Util.fecha() + ".wav", "audio/wav", b);
                        if (u != null) enviar(WHATSAPP, "audio/*", u);
                    } catch (Exception e) { aviso("No se pudo enviar el audio"); }
                });
                dd.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> { try { mp.release(); } catch (Exception ignored) {} dd.dismiss(); });
            });
            dd.show();
        }));
    }

    /** Versión 30: escoger la app de voz (Acapela, Google…) y luego la voz, como en otras aplicaciones. */
    private void escogerMotor(Button bv) {
        if (voz == null) return;
        java.util.List<String[]> ms = voz.motores();
        if (ms.isEmpty()) { aviso("No encontré apps de voz en el celular"); return; }
        String[] nombres = new String[ms.size()];
        for (int i = 0; i < ms.size(); i++) nombres[i] = ms.get(i)[0];
        new AlertDialog.Builder(this).setTitle("🗣 ¿Qué app de voz?").setItems(nombres, (d, w) -> {
            String paquete = ms.get(w)[1];
            String etiqueta = ms.get(w)[0];
            aviso("Cargando las voces…");
            voz.cerrar();
            voz = new VozLocutor(this, paquete, ok -> runOnUiThread(() -> {
                java.util.List<String[]> vs = voz.vocesEspanol();
                if (!ok || vs.isEmpty()) {
                    voz.escogerVoz(paquete, null);
                    getSharedPreferences("mivoz", MODE_PRIVATE).edit().putString("vozVista", etiqueta).apply();
                    bv.setText("🗣 Voz: " + etiqueta + "  ▾");
                    return;
                }
                String[] vn = new String[vs.size()];
                for (int i = 0; i < vs.size(); i++) vn[i] = vs.get(i)[0];
                new AlertDialog.Builder(this).setTitle("🗣 Voces en español · " + etiqueta).setItems(vn, (d2, k) -> {
                    voz.escogerVoz(paquete, vs.get(k)[1]);
                    String vista = etiqueta + " · " + vs.get(k)[0];
                    getSharedPreferences("mivoz", MODE_PRIVATE).edit().putString("vozVista", vista).apply();
                    bv.setText("🗣 Voz: " + vista + "  ▾");
                    voz.hablar("Hola, esta es mi voz.");
                }).show();
            }));
        }).show();
    }

    /** Tono (más grave = locutor), velocidad y acento latino; se guardan para la próxima vez. */
    private void ajustesVoz() {
        LinearLayout l = vertical();
        l.setPadding(dp(18), dp(8), dp(18), 0);
        android.content.SharedPreferences vp = getSharedPreferences("mivoz", MODE_PRIVATE);
        Button bv = new Button(this);
        bv.setText("🗣 Voz: " + vp.getString("vozVista", voz != null && VozLocutor.ACAPELA.equals(voz.motor) ? "Acapela" : "del celular") + "  ▾");
        bv.setAllCaps(false);
        bv.setTextSize(16);
        bv.setOnClickListener(v -> escogerMotor(bv));
        l.addView(bv);
        TextView t1 = new TextView(this); t1.setText("Tono (más grave = locutor)"); t1.setTextSize(15); l.addView(t1);
        android.widget.SeekBar tono = new android.widget.SeekBar(this); tono.setMax(100); tono.setProgress(vp.getInt("tono", 85) - 50); l.addView(tono);
        TextView t2 = new TextView(this); t2.setText("Velocidad"); t2.setTextSize(15); l.addView(t2);
        android.widget.SeekBar vel = new android.widget.SeekBar(this); vel.setMax(100); vel.setProgress(vp.getInt("vel", 92) - 50); l.addView(vel);
        android.widget.CheckBox lat = new android.widget.CheckBox(this);
        lat.setText("Acento latino (quita la «th» de la voz española)");
        lat.setChecked(vp.getBoolean("latino", true));
        l.addView(lat);
        AlertDialog dd = new AlertDialog.Builder(this).setTitle("⚙ Voz de locutor").setView(l)
                .setPositiveButton("Guardar", (d, w) -> voz.guardar(tono.getProgress() + 50, vel.getProgress() + 50, lat.isChecked()))
                .setNeutralButton("▶ Probar", null).setNegativeButton("Cancelar", null).create();
        dd.setOnShowListener(x -> dd.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            voz.guardar(tono.getProgress() + 50, vel.getProgress() + 50, lat.isChecked());
            voz.hablar("Buenos días. Le envío la cotización del cemento y la arena, como hablamos esta mañana.");
        }));
        dd.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ed != null) pr.edit().putString("borrador", texto()).apply();
    }

    private Button btn(String t, int c, Runnable r) {
        Button b = boton(t, c);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private String texto() { return ed.getText().toString(); }

    private boolean hay() {
        if (texto().trim().isEmpty()) { aviso("Todavía no hay nada escrito"); return false; }
        return true;
    }

    // ---------------- Diccionario de tildes ----------------

    private void cargarTildes() {
        if (tildes != null) return;
        new Thread(() -> {
            Map<String, String> m = new HashMap<>();
            try (InputStream in = getAssets().open("tildes.txt")) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                for (String l : bo.toString("UTF-8").split("\n")) {
                    int t = l.indexOf('\t');
                    if (t > 0) m.put(l.substring(0, t), l.substring(t + 1).trim());
                }
            } catch (Exception ignored) {}
            tildes = m;
        }).start();
    }

    // ---------------- Corregir ----------------

    private void quitarMarcas() {
        Editable e = ed.getText();
        for (Marca m : e.getSpans(0, e.length(), Marca.class)) e.removeSpan(m);
    }

    private void corregir() {
        if (!hay()) return;
        antesDeCorregir = texto();
        Corrector.Resultado r = Corrector.corregir(antesDeCorregir, tildes);
        if (r.texto.equals(antesDeCorregir)) {
            quitarMarcas();
            tNota.setText("👍 No encontré nada para corregir. Para revisar más a fondo use 📋 Organizar → 🌐 Revisión fina.");
            return;
        }
        int cursor = Math.min(ed.getSelectionStart(), r.texto.length());
        ed.setText(r.texto);
        Editable e = ed.getText();
        for (Corrector.Cambio c : r.cambios)
            if (c.fin > c.ini && c.fin <= e.length()) e.setSpan(new Marca(c.antes), c.ini, c.fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ed.setSelection(Math.max(0, cursor));
        int n = r.cambios.size();
        tNota.setText("✨ " + n + (n == 1 ? " corrección" : " correcciones") + ": mayúsculas, tildes, signos ¿? y espacios. "
                + "Toque una palabra amarilla para deshacerla. (Toque aquí para deshacer todo)");
    }

    private void deshacerTodo() {
        if (antesDeCorregir == null) return;
        new AlertDialog.Builder(this).setTitle("¿Deshacer el último cambio?")
                .setMessage("El escrito vuelve a quedar como estaba antes del último cambio.")
                .setPositiveButton("↩ Deshacer todo", (d, w) -> { ed.setText(antesDeCorregir); antesDeCorregir = null; tNota.setText("↩ Quedó como estaba."); })
                .setNegativeButton("Dejar así", null).show();
    }

    private void tocoMarca(int off) {
        Editable e = ed.getText();
        Marca[] ms = e.getSpans(Math.max(0, off - 1), Math.min(e.length(), off + 1), Marca.class);
        for (Marca m : ms) {
            int i = e.getSpanStart(m), f = e.getSpanEnd(m);
            if (i < 0 || off < i || off > f) continue;
            String ahora = e.subSequence(i, f).toString();
            String antes = m.antes;
            new AlertDialog.Builder(this).setTitle("¿Deshacer este cambio?")
                    .setMessage("Ahora dice:  " + ahora + "\nAntes decía:  " + (antes.isEmpty() ? "(nada)" : antes))
                    .setPositiveButton("↩ Deshacer", (d, w) -> {
                        int a = e.getSpanStart(m), z = e.getSpanEnd(m);
                        e.removeSpan(m);
                        if (a >= 0 && z >= a) e.replace(a, z, antes);
                    })
                    .setNeutralButton("✔ Dejarlo", (d, w) -> e.removeSpan(m))
                    .setNegativeButton("Cancelar", null).show();
            return;
        }
    }

    // ---------------- Dictar ----------------

    private void dictar() {
        try {
            startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Hable: lo que diga se escribe donde está el cursor"), VOZ);
        } catch (Exception e) { aviso("Este celular no tiene dictado por voz"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req == CUENTA && res == RESULT_OK && d != null) { agregarDeCuenta(d); return; }
        if (req != VOZ || res != RESULT_OK || d == null) return;
        ArrayList<String> rs = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (rs == null || rs.isEmpty()) return;
        String dicho = rs.get(0);
        Editable e = ed.getText();
        int i = Math.max(0, ed.getSelectionStart()), f = Math.max(0, ed.getSelectionEnd());
        int a = Math.min(i, f), z = Math.max(i, f);
        String antes = a > 0 && !Character.isWhitespace(e.charAt(a - 1)) ? " " : "";
        e.replace(a, z, antes + dicho);
        ed.setSelection(Math.min(e.length(), a + antes.length() + dicho.length()));
    }

    // ---------------- Organizar ----------------

    private void organizar() {
        // Versión 40: con la hoja vacía igual deja abrir la llave de Gemini
        if (texto().trim().isEmpty()) {
            new AlertDialog.Builder(this).setTitle("📋 Organizar").setMessage("La hoja está vacía. Escriba o dicte algo para organizarlo.")
                    .setPositiveButton("🔑 Llave de Gemini", (d, w) -> startActivity(new Intent(this, LlaveActivity.class)))
                    .setNegativeButton("Cerrar", null).show();
            return;
        }
        // Versión 38: «✨ Organizar con Gemini» de primero: el mismo arreglador del botón flotante
        String[] ops = {"✨  Organizar con Gemini (ideas, gramática y puntuación)", "¶  En párrafos", "1️⃣  En lista numerada",
                "👋  Saludo y despedida", "↕  Mover ideas", "🌐  Revisión fina con internet (gratis)", "🔑  Llave de Gemini"};
        new AlertDialog.Builder(this).setTitle("📋 Organizar el escrito").setItems(ops, (d, w) -> {
            switch (w) {
                case 0: organizarGemini(); break;
                case 1: cambiarTodo(Corrector.enParrafos(texto()), "¶ Quedó en párrafos."); break;
                case 2: cambiarTodo(Corrector.enLista(texto()), "1️⃣ Quedó en lista numerada."); break;
                case 3: saludo(); break;
                case 4: moverIdeas(); break;
                case 5: revisionFina(); break;
                default: startActivity(new Intent(this, LlaveActivity.class));
            }
        }).setNegativeButton("Cerrar", null).show();
    }

    /** Versión 38: Gemini ordena las ideas y corrige; se puede deshacer tocando la nota amarilla. */
    private void organizarGemini() {
        if (!Gemini.hayLlave(this)) {
            aviso("🔑 Primero pegue la llave gratis de Gemini (una sola vez)");
            startActivity(new Intent(this, LlaveActivity.class));
            return;
        }
        final String t = texto();
        final boolean[] termino = {false};
        final long inicio = System.currentTimeMillis();
        final android.os.Handler hh = new android.os.Handler(android.os.Looper.getMainLooper());
        hh.post(new Runnable() {
            @Override public void run() {
                if (termino[0]) return;
                tNota.setText("✨ Organizando con Gemini… " + ((System.currentTimeMillis() - inicio) / 1000) + " s ⏳");
                hh.postDelayed(this, 1000);
            }
        });
        Gemini.organizar(this, t, false, new Gemini.Respuesta() {
            @Override public void listo(String nuevo) {
                runOnUiThread(() -> {
                    termino[0] = true;
                    if (!texto().equals(t)) { tNota.setText("✨ El escrito cambió mientras organizaba. Toque otra vez Organizar."); return; }
                    cambiarTodo(nuevo, "✨ Organizado con Gemini.");
                });
            }
            @Override public void error(String m) { runOnUiThread(() -> { termino[0] = true; tNota.setText(m); }); }
        });
    }

    private void cambiarTodo(String nuevo, String nota) {
        antesDeCorregir = texto();
        ed.setText(nuevo);
        ed.setSelection(ed.getText().length());
        tNota.setText(nota + " (Toque aquí para deshacer)");
    }

    private void saludo() {
        LinearLayout l = vertical();
        l.setPadding(dp(18), dp(8), dp(18), 0);
        TextView t1 = new TextView(this);
        t1.setText("Saludo (arriba):");
        t1.setTextSize(15);
        l.addView(t1);
        EditText eS = new EditText(this);
        eS.setText(pr.getString("saludo", "Buenos días, señor ___."));
        eS.setTextSize(17);
        l.addView(eS);
        TextView t2 = new TextView(this);
        t2.setText("Despedida (abajo):");
        t2.setTextSize(15);
        t2.setPadding(0, dp(10), 0, 0);
        l.addView(t2);
        EditText eD = new EditText(this);
        eD.setText(pr.getString("despedida", "Atentamente,\nJose"));
        eD.setTextSize(17);
        l.addView(eD);
        TextView ay = new TextView(this);
        ay.setText("Ponga el cursor en ___ y escriba el nombre. Quedan guardados para la próxima vez.");
        ay.setTextSize(13);
        ay.setTextColor(0xFF666666);
        l.addView(ay);
        new AlertDialog.Builder(this).setTitle("👋 Saludo y despedida").setView(l)
                .setPositiveButton("Poner", (d, w) -> {
                    String s = eS.getText().toString().trim(), de = eD.getText().toString().trim();
                    pr.edit().putString("saludo", s.replaceAll("señor [^_.,]+", "señor ___")).putString("despedida", de).apply();
                    String[] p = Corrector.partes(texto());
                    cambiarTodo(Corrector.juntar(s, p[1], de), "👋 Saludo y despedida puestos.");
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void moverIdeas() {
        String[] p = Corrector.partes(texto());
        boolean parrafos = Corrector.enParrafosSeparados(p[1]);
        List<String> ideas = new ArrayList<>(Corrector.ideas(p[1]));
        if (ideas.size() < 2) { aviso("Hay una sola idea: no hay nada que mover"); return; }
        LinearLayout lista = vertical();
        lista.setPadding(dp(10), dp(6), dp(10), dp(6));
        ScrollView sv = new ScrollView(this);
        sv.addView(lista);
        Runnable[] pintar = new Runnable[1];
        pintar[0] = () -> {
            lista.removeAllViews();
            for (int i = 0; i < ideas.size(); i++) {
                final int k = i;
                LinearLayout fila = horizontal();
                fila.setGravity(Gravity.CENTER_VERTICAL);
                fila.setPadding(dp(8), dp(6), dp(4), dp(6));
                GradientDrawable g = new GradientDrawable();
                g.setColor(0xFFF1F4F8);
                g.setCornerRadius(dp(10));
                fila.setBackground(g);
                TextView t = new TextView(this);
                t.setText((i + 1) + ". " + ideas.get(i));
                t.setTextSize(15);
                t.setTextColor(0xFF111111);
                t.setMaxLines(4);
                fila.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
                Button arriba = boton("▲", 0xFF1565C0);
                arriba.setOnClickListener(v -> { if (k > 0) { ideas.add(k - 1, ideas.remove(k)); pintar[0].run(); } });
                Button abajo = boton("▼", 0xFF1565C0);
                abajo.setOnClickListener(v -> { if (k < ideas.size() - 1) { ideas.add(k + 1, ideas.remove(k)); pintar[0].run(); } });
                fila.addView(arriba, new LinearLayout.LayoutParams(dp(48), dp(44)));
                fila.addView(abajo, new LinearLayout.LayoutParams(dp(48), dp(44)) {{ leftMargin = dp(4); }});
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = dp(6);
                lista.addView(fila, lp);
            }
        };
        pintar[0].run();
        new AlertDialog.Builder(this).setTitle("↕ Mover ideas (▲ sube, ▼ baja)").setView(sv)
                .setPositiveButton("Listo", (d, w) -> cambiarTodo(Corrector.juntar(p[0], String.join(parrafos ? "\n\n" : " ", ideas), p[2]), "↕ Ideas en el nuevo orden."))
                .setNegativeButton("Cancelar", null).show();
    }

    // ---------------- Revisión fina (LanguageTool, gratis, con internet) ----------------

    private void revisionFina() {
        final String t = texto();
        if (t.length() > 18000) { aviso("El escrito es muy largo para la revisión fina"); return; }
        tNota.setText("🌐 Revisando con internet…");
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.languagetool.org/v2/check").openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(20000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                String cuerpo = "language=es&text=" + URLEncoder.encode(t, "UTF-8");
                try (OutputStream o = c.getOutputStream()) { o.write(cuerpo.getBytes(StandardCharsets.UTF_8)); }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                }
                JSONArray ms = new JSONObject(bo.toString("UTF-8")).getJSONArray("matches");
                runOnUiThread(() -> aplicarRevision(t, ms));
            } catch (Exception e) {
                runOnUiThread(() -> tNota.setText("🌐 No se pudo revisar: revise que tenga internet e intente otra vez."));
            }
        }).start();
    }

    private void aplicarRevision(String t, JSONArray ms) {
        if (!texto().equals(t)) { tNota.setText("🌐 El escrito cambió mientras revisaba. Toque otra vez Revisión fina."); return; }
        antesDeCorregir = t;
        quitarMarcas();
        Editable e = ed.getText();
        int hechos = 0, fin = Integer.MAX_VALUE;
        List<String> notas = new ArrayList<>();
        for (int i = ms.length() - 1; i >= 0; i--) {
            try {
                JSONObject m = ms.getJSONObject(i);
                int off = m.getInt("offset"), len = m.getInt("length");
                JSONArray rs = m.getJSONArray("replacements");
                if (rs.length() == 0 || off + len > fin || off + len > e.length()) continue;
                String viejo = e.subSequence(off, off + len).toString();
                String nuevo = rs.getJSONObject(0).getString("value");
                String tipo = m.optJSONObject("rule") != null ? m.getJSONObject("rule").optString("issueType", "") : "";
                // nombres propios (Jose, Carlos…) no se tocan
                if ("misspelling".equals(tipo) && !viejo.isEmpty() && Character.isUpperCase(viejo.charAt(0))) continue;
                e.replace(off, off + len, nuevo);
                if (!nuevo.isEmpty()) e.setSpan(new Marca(viejo), off, off + nuevo.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                fin = off;
                hechos++;
                if (notas.size() < 3) notas.add("• " + viejo + " → " + nuevo);
            } catch (Exception ignored) {}
        }
        if (hechos == 0) tNota.setText("🌐 Revisión fina: ¡todo está bien escrito! 👍");
        else tNota.setText("🌐 Revisión fina: " + hechos + (hechos == 1 ? " arreglo" : " arreglos") + " en amarillo (toque uno para deshacerlo).\n" + String.join("\n", notas));
    }

    // ---------------- Imprimir, guardar y abrir ----------------

    private void imprimir() {
        if (!hay()) return;
        String s = texto().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        String html = "<html><head><meta charset='utf-8'><style>@page{size:letter;margin:2.5cm}"
                + "body{font-family:serif;font-size:13pt;line-height:1.5;color:#000}</style></head><body>" + s + "</body></html>";
        imprimirHtml(html, "Escrito");
    }

    private File carpeta() {
        File f = new File(getFilesDir(), "escritos");
        f.mkdirs();
        return f;
    }

    private void guardarAbrir() {
        String[] ops = {"💾 Guardar este escrito", "📂 Abrir uno guardado", "📄 Nuevo (hoja en blanco)"};
        new AlertDialog.Builder(this).setTitle("Mis escritos").setItems(ops, (d, w) -> {
            if (w == 0) guardar();
            else if (w == 1) abrir();
            else nuevo();
        }).setNegativeButton("Cerrar", null).show();
    }

    private void guardar() {
        if (!hay()) return;
        EditText n = campoTexto("Nombre del escrito", primeraLinea(texto()));
        LinearLayout l = vertical();
        l.setPadding(dp(18), dp(6), dp(18), 0);
        l.addView(n);
        new AlertDialog.Builder(this).setTitle("💾 Guardar escrito").setView(l).setPositiveButton("Guardar", (d, w) -> {
            String nom = limpio(n.getText().toString());
            if (nom.isEmpty()) nom = "Escrito " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
            try {
                Files.write(new File(carpeta(), System.currentTimeMillis() + ".txt").toPath(),
                        (nom + "\n---TEXTO---\n" + texto()).getBytes(StandardCharsets.UTF_8));
                aviso("💾 Guardado: " + nom);
            } catch (Exception e) { aviso("No se pudo guardar"); }
        }).setNegativeButton("Cancelar", null).show();
    }

    private static String primeraLinea(String t) {
        String s = t.trim().split("\n")[0].trim();
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private void abrir() {
        File[] fs = archivos(carpeta());
        if (fs.length == 0) { aviso("Todavía no hay escritos guardados"); return; }
        String[] nombres = new String[fs.length];
        String[] textos = new String[fs.length];
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.US);
        for (int i = 0; i < fs.length; i++) {
            try {
                String s = new String(Files.readAllBytes(fs[i].toPath()), StandardCharsets.UTF_8);
                int k = s.indexOf("\n---TEXTO---\n");
                nombres[i] = (k > 0 ? s.substring(0, k) : "Escrito") + "   · " + f.format(new Date(fs[i].lastModified()));
                textos[i] = k > 0 ? s.substring(k + 13) : s;
            } catch (Exception e) { nombres[i] = "(no se pudo leer)"; textos[i] = ""; }
        }
        new AlertDialog.Builder(this).setTitle("📂 Mis escritos").setItems(nombres, (d, w) -> {
            antesDeCorregir = texto();
            ed.setText(textos[w]);
            tNota.setText("📂 Abierto: " + nombres[w] + "  (toque aquí para volver al anterior)");
        }).setNeutralButton("🗑 Borrar uno", (d, w) -> borrar(fs, nombres)).setNegativeButton("Cerrar", null).show();
    }

    private void borrar(File[] fs, String[] nombres) {
        new AlertDialog.Builder(this).setTitle("¿Cuál borrar?").setItems(nombres, (d, w) ->
                new AlertDialog.Builder(this).setTitle("¿Borrar " + nombres[w] + "?")
                        .setPositiveButton("🗑 Borrar", (x, y) -> { if (fs[w].delete()) aviso("Borrado"); })
                        .setNegativeButton("No", null).show()).setNegativeButton("Cerrar", null).show();
    }

    private void nuevo() {
        if (texto().trim().isEmpty()) return;
        new AlertDialog.Builder(this).setTitle("¿Empezar un escrito nuevo?")
                .setMessage("Si no lo ha guardado, guárdelo primero con 💾.")
                .setPositiveButton("📄 Nuevo", (d, w) -> { antesDeCorregir = texto(); ed.setText(""); tNota.setText("📄 Hoja en blanco. (Toque aquí para recuperar el anterior)"); })
                .setNegativeButton("Cancelar", null).show();
    }
}
