package co.jose.mibarra;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reloj de cocina: cronómetro que habla, temporizador con canción o radio, alarma y radio. */
public class RelojActivity extends Base {

    private final Handler h = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout cuerpo;
    private Button[] pest = new Button[3];
    private int pestana;
    private TextView grande;
    private TextToSpeech voz;

    private android.animation.ObjectAnimator brillo;

    private static final int CANCION = 51;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("barra", MODE_PRIVATE);
        voz = new TextToSpeech(this, s -> { if (s == TextToSpeech.SUCCESS) voz.setLanguage(new Locale("es", "CO")); });
        LinearLayout raiz = vertical();
        raiz.setBackgroundColor(0xFF101418);
        LinearLayout fila = horizontal();
        String[] n = {"⏱ Cronómetro", "⏳ Temporizador", "⏰ Alarma · 📻"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            pest[i] = boton(n[i], 0xFF263238);
            pest[i].setTextSize(14);
            pest[i].setOnClickListener(v -> mostrar(k));
            fila.addView(pest[i], peso(1));
        }
        raiz.addView(fila, ancho(52));
        cuerpo = vertical();
        cuerpo.setPadding(dp(14), dp(10), dp(14), dp(14));
        ScrollView sv = new ScrollView(this);
        sv.addView(cuerpo);
        raiz.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(raiz);
        int pedida = getIntent().getIntExtra("pestana", -1); // Versión 22: abrir directo en la pestaña pedida
        mostrar(pedida >= 0 ? pedida : RelojService.fin > 0 || RelojService.sonando ? 1 : RelojService.cronoCorriendo() ? 0 : prefs.getInt("relojPest", 0));
    }

    @Override protected void onResume() { super.onResume(); h.post(tic); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tic); }
    @Override protected void onDestroy() { if (voz != null) voz.shutdown(); super.onDestroy(); }

    private final Runnable tic = new Runnable() {
        @Override public void run() { refrescar(); h.postDelayed(this, 200); }
    };

    private void mostrar(int k) {
        pestana = k;
        prefs.edit().putInt("relojPest", k).apply();
        for (int i = 0; i < 3; i++) pest[i].setBackgroundColor(i == k ? 0xFF1565C0 : 0xFF263238);
        if (brillo != null) { brillo.cancel(); brillo = null; }
        cuerpo.removeAllViews();
        grande = new TextView(this);
        grande.setTextSize(64);
        grande.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        grande.setTextColor(Color.WHITE);
        grande.setGravity(Gravity.CENTER);
        grande.setPadding(0, dp(10), 0, dp(10));
        cuerpo.addView(grande);
        if (k == 0) cronometro();
        else if (k == 1) temporizador();
        else alarma();
        refrescar();
    }

    private static String hms(long ms, boolean decimas) {
        long s = ms / 1000;
        String t = s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                : String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
        return decimas ? t + "," + (ms % 1000) / 100 : t;
    }

    private long cTiempo() { return RelojService.cronoCorriendo() ? System.currentTimeMillis() - RelojService.cInicio : 0; }

    private void refrescar() {
        if (grande == null) return;
        if (pestana == 0) {
            grande.setText(hms(cTiempo(), true));
        } else if (pestana == 1) {
            if (RelojService.sonando) grande.setText("⏰ ¡YA!");
            else if (RelojService.fin > 0) grande.setText(hms(Math.max(0, RelojService.fin - System.currentTimeMillis() + 999), false));
            else grande.setText("00:00");
        } else {
            grande.setText(new java.text.SimpleDateFormat("hh:mm a", new Locale("es", "CO")).format(new java.util.Date()));
        }
    }

    private void decir(String s) { try { voz.speak(s, TextToSpeech.QUEUE_FLUSH, null, "c"); } catch (Exception ignored) {} }

    // ---------------- Cronómetro con receta ----------------
    // Empezar → corre desde cero y dice el tiempo cada 5 minutos. Pare → guarda ese tiempo en la receta y queda en cero.

    private List<String[]> tiempos() { // ms, hora, nota
        List<String[]> r = new ArrayList<>();
        for (String l : prefs.getString("recTiempos", "").split("\n")) {
            String[] p = l.split("\\|", -1);
            if (p.length >= 3) r.add(p);
        }
        return r;
    }

    private void guardarTiempos(List<String[]> t) {
        StringBuilder b = new StringBuilder();
        for (String[] p : t) b.append(p[0]).append('|').append(p[1]).append('|').append(p[2].replace("|", "/").replace("\n", " ")).append('\n');
        prefs.edit().putString("recTiempos", b.toString()).apply();
    }

    private void cronometro() {
        boolean va = RelojService.cronoCorriendo();
        Button bt = boton(va ? "⏹   PARE" : "▶   EMPEZAR", va ? 0xFFD32F2F : 0xFF2E7D32);
        bt.setTextSize(28);
        GradientDrawable g = new GradientDrawable();
        g.setColor(va ? 0xFFD32F2F : 0xFF2E7D32);
        g.setCornerRadius(dp(40));
        g.setStroke(dp(5), va ? 0xFFFF8A80 : 0xFFB9F6CA);
        bt.setBackground(g);
        bt.setOnClickListener(v -> {
            if (RelojService.cronoCorriendo()) {
                long ms = System.currentTimeMillis() - RelojService.cInicio;
                RelojService.cInicio = 0;
                List<String[]> t = tiempos();
                t.add(new String[]{String.valueOf(ms), new java.text.SimpleDateFormat("hh:mm a", new Locale("es", "CO")).format(new java.util.Date()), ""});
                guardarTiempos(t);
                decir("Paré en " + hablarTiempo(ms));
            } else {
                RelojService.cInicio = System.currentTimeMillis();
                decir("Arrancó");
            }
            startForegroundService(new Intent(this, RelojService.class).setAction(RelojService.CRONO));
            mostrar(0);
        });
        cuerpo.addView(bt, new LinearLayout.LayoutParams(-1, dp(96)));
        // Efecto de luz: el botón late suave mientras espera o mientras corre
        brillo = android.animation.ObjectAnimator.ofFloat(bt, "alpha", 1f, 0.55f);
        brillo.setDuration(va ? 600 : 1100);
        brillo.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        brillo.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        brillo.start();
        cuerpo.addView(texto(va ? "🗣 Le digo el tiempo cada 5 minutos, aunque apague la pantalla." : "Toque EMPEZAR. Cuando toque PARE, el tiempo se guarda abajo y vuelve a cero."));

        // Tiempos guardados
        List<String[]> t = tiempos();
        if (!t.isEmpty()) {
            cuerpo.addView(texto("\n⏱ Tiempos (toque para escribir qué pasó, deje presionado para borrar):"));
            for (int i = 0; i < t.size(); i++) {
                final int k = i;
                String[] p = t.get(i);
                Button f = boton((i + 1) + ".   " + hms(Long.parseLong(p[0]), false) + "    (" + p[1] + ")" + (p[2].isEmpty() ? "" : "\n" + p[2]), 0xFF263238);
                f.setTextSize(17);
                f.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                f.setOnClickListener(v -> {
                    EditText e = campoTexto("Ej: pitó la olla, la dejé enfriar", p[2]);
                    new AlertDialog.Builder(this).setTitle("Tiempo " + (k + 1)).setView(e).setPositiveButton("Guardar", (d, w) -> {
                        p[2] = limpio(e.getText().toString()); guardarTiempos(t); mostrar(0);
                    }).setNegativeButton("Cancelar", null).show();
                });
                f.setOnLongClickListener(v -> { t.remove(k); guardarTiempos(t); mostrar(0); return true; });
                cuerpo.addView(f, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = dp(4); }});
            }
            long tot = 0;
            for (String[] p : t) tot += Long.parseLong(p[0]);
            cuerpo.addView(texto("Total: " + hms(tot, false)));
        }

        // La receta
        cuerpo.addView(texto("\n📒 Receta"));
        EditText tit = campoTexto("Título: Frijoles", prefs.getString("recTitulo", ""));
        tit.setTextColor(Color.WHITE);
        tit.setTextSize(19);
        EditText ing = campoTexto("Lo que eché: 1 porción de frijol (500 g), 3 porciones de agua…", prefs.getString("recTexto", ""));
        ing.setTextColor(Color.WHITE);
        ing.setSingleLine(false);
        ing.setMinLines(4);
        android.text.TextWatcher guardar = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence x, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable e) {
                prefs.edit().putString("recTitulo", tit.getText().toString()).putString("recTexto", ing.getText().toString()).apply();
            }
        };
        tit.addTextChangedListener(guardar);
        ing.addTextChangedListener(guardar);
        cuerpo.addView(tit);
        cuerpo.addView(ing);
        LinearLayout f = horizontal();
        Button gu = boton("💾 Guardar", 0xFF2E7D32);
        gu.setOnClickListener(v -> guardarReceta());
        Button mis = boton("📂 Mis recetas", 0xFF1565C0);
        mis.setOnClickListener(v -> misRecetas());
        Button wa = boton("💬", 0xFF25A244);
        wa.setOnClickListener(v -> textoWhatsapp(textoReceta()));
        Button nu = boton("✚ Nueva", 0xFF6D4C41);
        nu.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("¿Empezar una receta nueva? (guarde antes la que tiene)")
                .setPositiveButton("Sí", (d, w) -> {
                    prefs.edit().remove("recTitulo").remove("recTexto").remove("recTiempos").apply();
                    mostrar(0);
                }).setNegativeButton("No", null).show());
        f.addView(gu, peso(1.1f));
        f.addView(mis, peso(1.3f));
        f.addView(wa, peso(0.6f));
        f.addView(nu, peso(1f));
        cuerpo.addView(f, ancho(52));
    }

    private static String hablarTiempo(long ms) {
        long s = ms / 1000, m = s / 60;
        if (m == 0) return s + " segundos";
        return (m == 1 ? "un minuto" : m + " minutos") + (s % 60 >= 10 ? " y " + (s % 60) + " segundos" : "");
    }

    private String textoReceta() {
        StringBuilder b = new StringBuilder();
        String t = prefs.getString("recTitulo", "").trim();
        b.append("*").append(t.isEmpty() ? "Receta" : t).append("*\n");
        String x = prefs.getString("recTexto", "").trim();
        if (!x.isEmpty()) b.append(x).append("\n");
        List<String[]> ts = tiempos();
        if (!ts.isEmpty()) {
            b.append("\n⏱ Tiempos:\n");
            for (int i = 0; i < ts.size(); i++)
                b.append(i + 1).append(". ").append(hms(Long.parseLong(ts.get(i)[0]), false))
                 .append(ts.get(i)[2].isEmpty() ? "" : " — " + ts.get(i)[2]).append("\n");
        }
        return b.toString().trim();
    }

    private java.io.File carpetaRecetas() { java.io.File f = new java.io.File(getFilesDir(), "recetas"); f.mkdirs(); return f; }

    private void guardarReceta() {
        String t = prefs.getString("recTitulo", "").trim();
        if (t.isEmpty()) { aviso("Póngale un título a la receta"); return; }
        String datos = t + "\n---TIEMPOS---\n" + prefs.getString("recTiempos", "") + "---TEXTO---\n" + prefs.getString("recTexto", "");
        try {
            java.nio.file.Files.write(new java.io.File(carpetaRecetas(), t.replaceAll("[\\\\/:*?\"<>|]", " ") + ".txt").toPath(),
                    datos.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            aviso("💾 Receta «" + t + "» guardada");
        } catch (Exception e) { aviso("No se pudo guardar"); }
    }

    private void misRecetas() {
        java.io.File[] fs = carpetaRecetas().listFiles();
        if (fs == null || fs.length == 0) { aviso("Todavía no tiene recetas guardadas"); return; }
        java.util.Arrays.sort(fs, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        String[] n = new String[fs.length];
        for (int i = 0; i < fs.length; i++) n[i] = "📒 " + fs[i].getName().replaceAll("\\.txt$", "");
        new AlertDialog.Builder(this).setTitle("Mis recetas").setItems(n, (d, w) -> {
            try {
                String todo = new String(java.nio.file.Files.readAllBytes(fs[w].toPath()), java.nio.charset.StandardCharsets.UTF_8);
                int a = todo.indexOf("\n---TIEMPOS---\n"), b = todo.indexOf("---TEXTO---\n");
                prefs.edit().putString("recTitulo", todo.substring(0, a))
                        .putString("recTiempos", todo.substring(a + 15, b))
                        .putString("recTexto", todo.substring(b + 12)).apply();
                mostrar(0);
            } catch (Exception e) { aviso("No se pudo abrir"); }
        }).setNegativeButton("Cerrar", null).show();
    }

    // ---------------- Temporizador ----------------

    private void temporizador() {
        if (RelojService.fin > 0 || RelojService.sonando) {
            if (!RelojService.nombre.isEmpty()) cuerpo.addView(texto("Para: " + RelojService.nombre));
            Button p = boton(RelojService.sonando ? "⏹  APAGAR LA ALARMA" : "✖  Cancelar el temporizador", 0xFFC62828);
            p.setTextSize(20);
            p.setOnClickListener(v -> { startService(new Intent(this, RelojService.class).setAction(RelojService.PARAR)); h.postDelayed(() -> mostrar(1), 300); });
            cuerpo.addView(p, ancho(70));
            cuerpo.addView(texto(RelojService.sonando ? "😴 ¿Un ratico más?" : "➕ Agregar tiempo:"));
            LinearLayout mas = horizontal();
            for (int m : new int[]{5, 10, 15}) {
                Button bm = boton("+" + m + " min", 0xFF6A1B9A);
                bm.setTextSize(18);
                bm.setOnClickListener(v -> {
                    long base = RelojService.sonando ? 0 : Math.max(0, RelojService.fin - System.currentTimeMillis());
                    startForegroundService(new Intent(this, RelojService.class).setAction(RelojService.POSPONER).putExtra("ms", base + m * 60000L));
                    h.postDelayed(() -> mostrar(1), 300);
                });
                mas.addView(bm, peso(1));
            }
            cuerpo.addView(mas, ancho(60));
            cuerpo.addView(texto("Puede apagar la pantalla: sigue contando y le avisa con voz y sonido."));
            return;
        }
        EditText para = campoTexto("¿Para qué? (arroz, frijoles, horno…) — opcional", prefs.getString("relojNombre", ""));
        cuerpo.addView(para);
        cuerpo.addView(texto("Toque los minutos:"));
        int[] mins = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60};
        LinearLayout f = null;
        for (int i = 0; i < mins.length; i++) {
            if (i % 3 == 0) { f = horizontal(); cuerpo.addView(f, ancho(58)); }
            final int m = mins[i];
            Button bt = boton(m + " min", 0xFF2E7D32);
            bt.setTextSize(18);
            bt.setOnClickListener(v -> arrancar(m * 60000L, para.getText().toString().trim()));
            f.addView(bt, peso(1));
        }
        LinearLayout otro = horizontal();
        EditText mm = campoTexto("Otros minutos", "");
        mm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        otro.addView(mm, peso(1));
        Button go = boton("▶", 0xFF2E7D32);
        go.setOnClickListener(v -> {
            double x = Util.cantidad(mm.getText().toString());
            if (x <= 0) { aviso("Escriba los minutos"); return; }
            arrancar((long) (x * 60000), para.getText().toString().trim());
        });
        otro.addView(go, new LinearLayout.LayoutParams(dp(70), dp(54)));
        cuerpo.addView(otro);
        sonido();
    }

    private void arrancar(long ms, String nombre) {
        prefs.edit().putString("relojNombre", nombre).apply();
        startForegroundService(new Intent(this, RelojService.class).setAction(RelojService.INICIAR)
                .putExtra("ms", ms).putExtra("nombre", nombre));
        decir("Listo, " + (ms >= 60000 ? (ms / 60000) + (ms / 60000 == 1 ? " minuto" : " minutos") : (ms / 1000) + " segundos"));
        h.postDelayed(() -> mostrar(1), 300);
    }

    /** Qué suena al terminar: alarma del celular, una canción suya o la radio. */
    private void sonido() {
        cuerpo.addView(texto("\nAl terminar suena:"));
        int cual = prefs.getInt("relojSonido", 0);
        String[] n = {"🔔 Alarma", "🎵 Canción", "📻 Radio"};
        LinearLayout f = horizontal();
        for (int i = 0; i < 3; i++) {
            final int k = i;
            Button bt = boton(n[i], i == cual ? 0xFF1565C0 : 0xFF37474F);
            bt.setOnClickListener(v -> {
                if (k == 1) {
                    Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE);
                    try { startActivityForResult(it, CANCION); } catch (Exception e) { aviso("No encontré canciones"); }
                    return;
                }
                if (k == 2 && prefs.getString("relojRadio", "").isEmpty()) { pedirRadio(() -> { prefs.edit().putInt("relojSonido", 2).apply(); mostrar(1); }); return; }
                prefs.edit().putInt("relojSonido", k).apply();
                mostrar(1);
            });
            f.addView(bt, peso(1));
        }
        cuerpo.addView(f, ancho(50));
        if (cual == 1) cuerpo.addView(texto("Canción: " + prefs.getString("relojCancionNombre", "(escoja una)")));
        if (cual == 2) cuerpo.addView(texto("Radio: " + prefs.getString("relojRadio", "")));
    }

    @Override
    protected void onActivityResult(int r, int res, Intent d) {
        super.onActivityResult(r, res, d);
        if (r == CANCION && res == RESULT_OK && d != null && d.getData() != null) {
            Uri u = d.getData();
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            String nom = u.getLastPathSegment();
            try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int ix = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (ix >= 0) nom = c.getString(ix);
                }
            } catch (Exception ignored) {}
            prefs.edit().putString("relojCancion", u.toString()).putString("relojCancionNombre", nom).putInt("relojSonido", 1).apply();
            mostrar(1);
        }
    }

    private void pedirRadio(Runnable listo) {
        EditText e = campoTexto("https://…  (enlace de la emisora)", prefs.getString("relojRadio", ""));
        new AlertDialog.Builder(this).setTitle("📻 Enlace de la emisora")
                .setMessage("Pegue el enlace de transmisión de la emisora (lo da la página de la emisora; suele terminar en .mp3, .aac o /stream).")
                .setView(e)
                .setPositiveButton("Guardar", (d, w) -> {
                    String u = e.getText().toString().trim();
                    if (!u.startsWith("http")) { aviso("El enlace debe empezar por http"); return; }
                    prefs.edit().putString("relojRadio", u).apply();
                    listo.run();
                }).setNegativeButton("Cancelar", null).show();
    }

    // ---------------- Alarma y radio ----------------

    private void alarma() {
        Button al = boton("⏰  Poner una alarma (hora fija)", 0xFF6A1B9A);
        al.setTextSize(18);
        al.setOnClickListener(v -> {
            java.util.Calendar c = java.util.Calendar.getInstance();
            new TimePickerDialog(this, (tp, hh, mm) -> {
                EditText msg = campoTexto("¿Para qué? (opcional)", "");
                new AlertDialog.Builder(this).setTitle(String.format(Locale.US, "Alarma a las %02d:%02d", hh, mm)).setView(msg)
                        .setPositiveButton("Poner", (d, w) -> {
                            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                                    .putExtra(AlarmClock.EXTRA_HOUR, hh).putExtra(AlarmClock.EXTRA_MINUTES, mm)
                                    .putExtra(AlarmClock.EXTRA_MESSAGE, msg.getText().toString().trim().isEmpty() ? "Mi Barra" : msg.getText().toString().trim())
                                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                            try { startActivity(i); aviso(String.format(Locale.US, "⏰ Alarma puesta a las %02d:%02d en el reloj del celular", hh, mm)); }
                            catch (Exception e) { aviso("Este celular no deja poner alarmas desde otra app"); }
                        }).setNegativeButton("Cancelar", null).show();
            }, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), false).show();
        });
        cuerpo.addView(al, ancho(64));
        cuerpo.addView(texto("La alarma queda guardada en el reloj del celular, con su sonido; suena aunque Mi Barra esté cerrada."));
        Button ver = boton("📋  Ver mis alarmas", 0xFF455A64);
        ver.setOnClickListener(v -> { try { startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS)); } catch (Exception e) { aviso("No se pudo abrir el reloj"); } });
        cuerpo.addView(ver, ancho(50));

        cuerpo.addView(texto("\n📻 Radio"));
        boolean suena = RelojService.radio;
        Button ra = boton(suena ? "⏹  Apagar la radio" : "▶  Oír la radio", suena ? 0xFFC62828 : 0xFF2E7D32);
        ra.setTextSize(18);
        ra.setOnClickListener(v -> {
            if (RelojService.radio) { startService(new Intent(this, RelojService.class).setAction(RelojService.PARAR)); h.postDelayed(() -> mostrar(2), 300); return; }
            Runnable tocar = () -> {
                startForegroundService(new Intent(this, RelojService.class).setAction(RelojService.RADIO).putExtra("url", prefs.getString("relojRadio", "")));
                aviso("📻 Conectando con la emisora…");
                h.postDelayed(() -> mostrar(2), 400);
            };
            if (prefs.getString("relojRadio", "").isEmpty()) pedirRadio(tocar); else tocar.run();
        });
        cuerpo.addView(ra, ancho(60));
        Button cam = boton("✎  Cambiar la emisora", 0xFF455A64);
        cam.setOnClickListener(v -> pedirRadio(() -> mostrar(2)));
        cuerpo.addView(cam, ancho(48));
        String r = prefs.getString("relojRadio", "");
        if (!r.isEmpty()) cuerpo.addView(texto("Emisora: " + r));
    }

    private TextView texto(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFFCFD8DC);
        t.setTextSize(16);
        t.setPadding(dp(2), dp(8), dp(2), dp(4));
        return t;
    }
}
