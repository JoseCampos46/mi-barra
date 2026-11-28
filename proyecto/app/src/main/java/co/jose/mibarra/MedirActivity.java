package co.jose.mibarra;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ChpMedir (versión 48): tomar medidas sin parar a escribir.
 * Toca el botón verde → la app dice «Medida C» → él dice el número → la app lo anota y lo repite.
 * Las letras son solo etiquetas (lo que significa cada una está en su dibujo). Escribe exactamente lo que oye:
 * si dice «90», queda 90 y él lo corrige con el cursor. Solo escucha unos segundos después de cada toque.
 */
public class MedirActivity extends Activity {

    private static final int VERDE = 0xFF2E7D32, ROJO = 0xFFC62828, TEAL = 0xFF00695C, NEGRO = 0xFF111111;
    private static final int GOOGLE = 81;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<String[]> filas = new ArrayList<>();   // {letra, valor}
    private String trabajo = "Medidas";
    private TextToSpeech tts;
    private boolean ttsListo, escuchando;
    private TextView boton, oyo, titulo;
    private LinearLayout tabla;
    private ScrollView svTabla;

    private int dp(float v) { return Util.dp(this, v); }
    private void aviso(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(TEAL);
        trabajo = getSharedPreferences("medir", MODE_PRIVATE).getString("trabajo", "Medidas");
        leer();
        tts = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) { tts.setLanguage(new Locale("es", "CO")); ttsListo = true; }
        });
        pintar();
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
    }

    @Override
    protected void onDestroy() {
        VozLocal.parar();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    // ---------------- Letras: A…Z, AA, AB… ----------------

    static String letra(int i) {
        StringBuilder s = new StringBuilder();
        i++;
        while (i > 0) { int r = (i - 1) % 26; s.insert(0, (char) ('A' + r)); i = (i - 1) / 26; }
        return s.toString();
    }

    private String siguiente() { return letra(filas.size()); }

    /** Para que la voz diga bien la letra (ej. «be», no «b»). */
    private static String letraHablada(String l) {
        String[] n = {"a", "be", "ce", "de", "e", "efe", "ge", "hache", "i", "jota", "ka", "ele", "eme", "ene", "o", "pe", "cu", "erre", "ese", "te", "u", "uve", "doble u", "equis", "ye", "zeta"};
        StringBuilder s = new StringBuilder();
        for (char c : l.toCharArray()) s.append(s.length() > 0 ? " " : "").append(n[c - 'A']);
        return s.toString();
    }

    // ---------------- Pantalla ----------------

    private TextView texto(String s, float tam, int color, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(tam);
        t.setTextColor(color);
        if (negrita) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void pintar() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Color.WHITE);

        LinearLayout barra = new LinearLayout(this);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.setBackgroundColor(TEAL);
        barra.setPadding(dp(6), dp(6), dp(6), dp(6));
        TextView menu = texto("☰", 26, Color.WHITE, true);
        menu.setPadding(dp(10), 0, dp(10), 0);
        menu.setOnClickListener(v -> menu());
        barra.addView(menu);
        titulo = texto("", 18, Color.WHITE, true);
        titulo.setOnClickListener(v -> renombrar());
        barra.addView(titulo, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView x = texto("✕", 18, Color.WHITE, true);
        x.setGravity(Gravity.CENTER);
        GradientDrawable gx = new GradientDrawable();
        gx.setShape(GradientDrawable.OVAL);
        gx.setColor(ROJO);
        gx.setStroke(dp(2), Color.WHITE);
        x.setBackground(gx);
        x.setOnClickListener(v -> finishAndRemoveTask());
        barra.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
        col.addView(barra);

        // La tabla (arriba)
        tabla = new LinearLayout(this);
        tabla.setOrientation(LinearLayout.VERTICAL);
        svTabla = new ScrollView(this);
        svTabla.addView(tabla);
        col.addView(svTabla, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Botones pequeños
        String[] tx = {"📷 Foto del dibujo", "↩ Borrar la última", "💾 Guardar", "✉ Enviar a Claude"};
        Runnable[] ac = {() -> claude(true), this::borrarUltima, this::guardar, () -> claude(false)};
        for (int f = 0; f < 2; f++) {
            LinearLayout fila = new LinearLayout(this);
            for (int k = 0; k < 2; k++) {
                int i = f * 2 + k;
                Button bt = new Button(this);
                bt.setText(tx[i]);
                bt.setAllCaps(false);
                bt.setTextSize(16);
                bt.setTextColor(NEGRO);
                GradientDrawable g = new GradientDrawable();
                g.setColor(0xFFF1F1F1);
                g.setCornerRadius(dp(10));
                g.setStroke(dp(1), 0xFF9E9E9E);
                bt.setBackground(g);
                bt.setOnClickListener(v -> ac[i].run());
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f);
                p.setMargins(dp(4), dp(3), dp(4), dp(3));
                fila.addView(bt, p);
            }
            col.addView(fila);
        }

        oyo = texto("", 15, NEGRO, false);
        oyo.setGravity(Gravity.CENTER);
        oyo.setPadding(0, dp(2), 0, dp(2));
        col.addView(oyo);

        // El botón gigante (abajo, casi media pantalla): se toca con cualquier mano
        boton = texto("", 30, Color.WHITE, true);
        boton.setGravity(Gravity.CENTER);
        boton.setOnClickListener(v -> tocar());
        int alto = (int) (getResources().getDisplayMetrics().heightPixels * 0.40f);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(-1, alto);
        pb.setMargins(dp(8), dp(4), dp(8), dp(10));
        col.addView(boton, pb);

        setContentView(col);
        pintarTabla();
        pintarBoton();
    }

    private void pintarBoton() {
        titulo.setText("ChpMedir v" + Util.version(this) + " · " + trabajo);
        GradientDrawable g = new GradientDrawable();
        g.setColor(escuchando ? ROJO : VERDE);
        g.setCornerRadius(dp(22));
        boton.setBackground(g);
        boton.setText(escuchando ? "🎙\nEscuchando…\nDiga el número" : "🎙\nMedida " + siguiente() + "\n(toque aquí)");
    }

    private void pintarTabla() {
        tabla.removeAllViews();
        if (filas.isEmpty()) {
            TextView t = texto("Toque el botón verde y diga la medida.\nLa primera será la A.", 18, NEGRO, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(10), dp(24), dp(10), dp(10));
            tabla.addView(t);
            return;
        }
        for (int i = 0; i < filas.size(); i++) {
            String[] f = filas.get(i);
            LinearLayout fila = new LinearLayout(this);
            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setPadding(dp(12), 0, dp(12), 0);
            if (i == filas.size() - 1) fila.setBackgroundColor(0xFFE8F5E9);
            TextView l = texto(f[0], 24, NEGRO, true);
            fila.addView(l, new LinearLayout.LayoutParams(dp(70), -2));
            EditText e = new EditText(this);
            e.setText(f[1]);
            e.setTextSize(24);
            e.setTextColor(NEGRO);
            e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            e.setSingleLine(true);
            final int idx = i;
            e.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) { if (idx < filas.size()) { filas.get(idx)[1] = s.toString(); escribir(); } }
            });
            fila.addView(e, new LinearLayout.LayoutParams(0, -2, 1f));
            tabla.addView(fila, new LinearLayout.LayoutParams(-1, dp(58)));
        }
        svTabla.post(() -> svTabla.fullScroll(View.FOCUS_DOWN));
    }

    // ---------------- Tomar una medida ----------------

    private void tocar() {
        if (escuchando) { VozLocal.parar(); escuchando = false; pintarBoton(); return; }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }
        if (!VozLocal.listo(this)) { ofrecerDescarga(); return; }
        String l = siguiente();
        hablar("Medida " + letraHablada(l), this::escuchar);
    }

    private void escuchar() {
        escuchando = true;
        pintarBoton();
        oyo.setText("Escuchando la medida " + siguiente() + "…");
        final boolean[] listo = {false};
        VozLocal.escucharNumeros(this, 7000, new VozLocal.Oido() {
            @Override public void parcial(String t) { h.post(() -> oyo.setText("Oyendo: " + t)); }
            @Override public void frase(String t) {
                String n = numero(t);
                if (n == null || listo[0]) return;
                listo[0] = true;
                VozLocal.parar();
                h.post(() -> anotar(n));
            }
            @Override public void error(String t) { h.post(() -> { escuchando = false; pintarBoton(); oyo.setText("⚠ " + t); }); }
            @Override public void terminado() {
                h.post(() -> {
                    if (listo[0]) return;
                    escuchando = false;
                    pintarBoton();
                    oyo.setText("No entendí. Toque otra vez.");
                    hablar("No entendí. Toque otra vez", null);
                });
            }
        });
    }

    private void anotar(String n) {
        escuchando = false;
        String l = siguiente();
        filas.add(new String[]{l, n});
        escribir();
        pintarTabla();
        pintarBoton();
        oyo.setText("✔ Anotado: " + l + " = " + n);
        hablar(letraHablada(l) + ", " + n, null);
    }

    private void hablar(String s, Runnable despues) {
        if (!ttsListo) { if (despues != null) h.postDelayed(despues, 300); return; }
        String id = "m" + System.nanoTime();
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String u) {}
            @Override public void onDone(String u) { if (despues != null && id.equals(u)) h.postDelayed(despues, 150); }
            @Override public void onError(String u) { if (despues != null && id.equals(u)) h.post(despues); }
        });
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    /** Convierte lo oído («ochocientos cuarenta y cinco», «90», «dos mil cien») en número. Null si no hay número. */
    static String numero(String t) {
        if (t == null) return null;
        String s = t.toLowerCase(Locale.ROOT).replace("[unk]", " ").trim();
        if (s.matches(".*\\d.*")) {
            String d = s.replaceAll("[^0-9.,]", "").replace(',', '.');
            return d.isEmpty() ? null : d;
        }
        String[] p = s.split("\\s+");
        long total = 0, actual = 0;
        boolean alguno = false, decimales = false;
        StringBuilder dec = new StringBuilder();
        for (String w : p) {
            if (w.isEmpty() || w.equals("y")) continue;
            if (w.equals("coma") || w.equals("punto")) { decimales = true; continue; }
            if (w.equals("medio")) { if (alguno) { dec.setLength(0); dec.append('5'); decimales = true; } continue; }
            long v = valor(w);
            if (v < 0) continue;
            if (decimales) { dec.append(v); continue; }
            alguno = true;
            if (v == 1000) { total += (actual == 0 ? 1 : actual) * 1000; actual = 0; }
            else actual += v;
        }
        if (!alguno) return null;
        String r = String.valueOf(total + actual);
        return dec.length() > 0 ? r + "." + dec : r;
    }

    private static long valor(String w) {
        switch (w) {
            case "cero": return 0; case "uno": case "un": case "una": return 1; case "dos": return 2; case "tres": return 3;
            case "cuatro": return 4; case "cinco": return 5; case "seis": return 6; case "siete": return 7; case "ocho": return 8;
            case "nueve": return 9; case "diez": return 10; case "once": return 11; case "doce": return 12; case "trece": return 13;
            case "catorce": return 14; case "quince": return 15; case "dieciséis": case "dieciseis": return 16; case "diecisiete": return 17;
            case "dieciocho": return 18; case "diecinueve": return 19; case "veinte": return 20; case "veintiuno": return 21;
            case "veintidós": case "veintidos": return 22; case "veintitrés": case "veintitres": return 23; case "veinticuatro": return 24;
            case "veinticinco": return 25; case "veintiséis": case "veintiseis": return 26; case "veintisiete": return 27;
            case "veintiocho": return 28; case "veintinueve": return 29; case "treinta": return 30; case "cuarenta": return 40;
            case "cincuenta": return 50; case "sesenta": return 60; case "setenta": return 70; case "ochenta": return 80;
            case "noventa": return 90; case "cien": case "ciento": return 100; case "doscientos": return 200; case "trescientos": return 300;
            case "cuatrocientos": return 400; case "quinientos": return 500; case "seiscientos": return 600; case "setecientos": return 700;
            case "ochocientos": return 800; case "novecientos": return 900; case "mil": return 1000;
            default: return -1;
        }
    }

    // ---------------- Dictado sin internet (se baja una sola vez) ----------------

    private void ofrecerDescarga() {
        new AlertDialog.Builder(this).setTitle("🎙 Dictado sin internet")
                .setMessage("Para escuchar los números sin internet hay que bajar el español una sola vez (unos 40 MB, mejor con Wi‑Fi).\n\n"
                        + "Mientras tanto puede usar el dictado de Google (necesita internet).")
                .setPositiveButton("Bajar el español", (d, w) -> descargar())
                .setNeutralButton("Usar Google", (d, w) -> google())
                .setNegativeButton("Cancelar", null).show();
    }

    @SuppressWarnings("deprecation")
    private void descargar() {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Bajando el español…");
        pd.setCancelable(false);
        pd.show();
        VozLocal.descargar(this, new VozLocal.Avance() {
            @Override public void va(int pct, String txt) { h.post(() -> pd.setMessage(txt + "  " + pct + " %")); }
            @Override public void fin(boolean ok, String txt) {
                h.post(() -> { try { pd.dismiss(); } catch (Exception ignored) {} aviso(ok ? "✔ Listo: ya puede medir sin internet" : "⚠ " + txt); });
            }
        });
    }

    private void google() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Medida " + siguiente());
        try { startActivityForResult(i, GOOGLE); } catch (Exception e) { aviso("Este celular no tiene el dictado de Google"); }
    }

    @Override
    protected void onActivityResult(int q, int r, Intent d) {
        super.onActivityResult(q, r, d);
        if (q != GOOGLE || r != RESULT_OK || d == null) return;
        ArrayList<String> res = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (res != null) for (String t : res) { String n = numero(t); if (n != null) { anotar(n); return; } }
        aviso("No entendí el número. Toque otra vez.");
    }

    // ---------------- Guardar, borrar, enviar ----------------

    private File archivo() { File d = new File(getFilesDir(), "medir"); d.mkdirs(); return new File(d, trabajo.replaceAll("[^\\p{L}\\p{N} _-]", "") + ".tsv"); }

    private void leer() {
        filas.clear();
        try { for (String l : Files.readAllLines(archivo().toPath(), StandardCharsets.UTF_8)) { String[] p = l.split("\t", -1); if (p.length >= 2) filas.add(new String[]{p[0], p[1]}); } }
        catch (Exception ignored) {}
    }

    private void escribir() {
        StringBuilder s = new StringBuilder();
        for (String[] f : filas) s.append(f[0]).append('\t').append(f[1]).append('\n');
        try { Files.write(archivo().toPath(), s.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }

    private String comoTexto() {
        StringBuilder s = new StringBuilder("Medidas: " + trabajo + " (" + Util.fecha() + ")\n");
        for (String[] f : filas) s.append(f[0]).append(" = ").append(f[1]).append('\n');
        return s.toString();
    }

    private void borrarUltima() {
        if (filas.isEmpty()) return;
        String[] f = filas.remove(filas.size() - 1);
        escribir();
        pintarTabla();
        pintarBoton();
        oyo.setText("↩ Se borró " + f[0] + " = " + f[1]);
    }

    private void guardar() {
        try {
            Util.guardarBytes(this, trabajo.replaceAll("[^\\p{L}\\p{N} _-]", "") + "_" + Util.fecha() + ".txt", "text/plain",
                    comoTexto().getBytes(StandardCharsets.UTF_8));
            aviso("💾 Guardado en Descargas/MiBarra/Medir");
        } catch (Exception e) { aviso("No se pudo guardar: " + e.getMessage()); }
    }

    private void claude(boolean foto) {
        Intent i = new Intent(this, ClaudeActivity.class)
                .putExtra("asunto", "Medidas: " + trabajo)
                .putExtra("mensaje", comoTexto() + "\nLas letras están en el dibujo adjunto.")
                .putExtra("foto", foto);
        try { startActivity(i); } catch (Exception e) { aviso("No se pudo abrir"); }
    }

    // ---------------- ☰ ----------------

    private void renombrar() {
        EditText e = new EditText(this);
        e.setText(trabajo);
        e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Nombre del trabajo (ej. Escalera casa)").setView(e)
                .setPositiveButton("Listo", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty()) return;
                    escribir();
                    trabajo = n;
                    getSharedPreferences("medir", MODE_PRIVATE).edit().putString("trabajo", trabajo).apply();
                    leer();
                    pintarTabla();
                    pintarBoton();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void menu() {
        String[] op = {"🆕  Trabajo nuevo", "📂  Abrir un trabajo guardado", "🗑  Empezar de nuevo este trabajo",
                "🎙  Bajar el dictado sin internet", "🗣  Usar dictado de Google para esta medida", "📄  Escáner", "❓  Cómo se usa"};
        new AlertDialog.Builder(this).setTitle("☰  ChpMedir v" + Util.version(this)).setItems(op, (d, w) -> {
            switch (w) {
                case 0: renombrar(); break;
                case 1: abrirTrabajo(); break;
                case 2: new AlertDialog.Builder(this).setTitle("¿Borrar todas las medidas de «" + trabajo + "»?")
                        .setPositiveButton("Borrar", (dd, ww) -> { filas.clear(); escribir(); pintarTabla(); pintarBoton(); })
                        .setNegativeButton("No", null).show(); break;
                case 3: descargar(); break;
                case 4: google(); break;
                case 5: try { startActivity(new Intent(this, EscanerActivity.class)); } catch (Exception e) { aviso("No se pudo abrir"); } break;
                case 6: new AlertDialog.Builder(this).setTitle("Cómo se usa")
                        .setMessage("1. Toque el nombre de arriba y escriba el trabajo (ej. Escalera casa).\n"
                                + "2. Toque el botón verde. La app dice «Medida A».\n"
                                + "3. Diga el número (ej. ochocientos cuarenta y cinco).\n"
                                + "4. La app lo anota y se lo repite. Toque otra vez para la B, y así.\n"
                                + "5. Si se equivocó: «↩ Borrar la última», o toque el número y corríjalo.\n"
                                + "6. Al final: «📷 Foto del dibujo» y «✉ Enviar a Claude».\n\n"
                                + "Las letras son solo etiquetas: lo que significa cada una va en su dibujo.")
                        .setPositiveButton("Entendido", null).show(); break;
            }
        }).setNegativeButton("Cerrar", null).show();
    }

    private void abrirTrabajo() {
        File[] fs = new File(getFilesDir(), "medir").listFiles();
        if (fs == null || fs.length == 0) { aviso("Todavía no hay trabajos guardados"); return; }
        String[] n = new String[fs.length];
        for (int i = 0; i < fs.length; i++) n[i] = fs[i].getName().replace(".tsv", "");
        new AlertDialog.Builder(this).setTitle("📂 Trabajos").setItems(n, (d, w) -> {
            escribir();
            trabajo = n[w];
            getSharedPreferences("medir", MODE_PRIVATE).edit().putString("trabajo", trabajo).apply();
            leer();
            pintarTabla();
            pintarBoton();
        }).setNegativeButton("Cerrar", null).show();
    }
}
