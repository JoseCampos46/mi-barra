package co.jose.mibarra;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versión 29: Radio reloj tipo Sony (solo acostado). Reemplaza al reloj de noche.
 * Hora grande en rojo, batería, temperatura, alarma con radio o con música propia, dormir y luz suave.
 */
public class RadioRelojActivity extends Activity {

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Locale es = new Locale("es", "CO");
    private SharedPreferences p;
    private TextView tFecha, tAlarma, tBat, tTemp, tSonando;
    private int bateria = -1;
    private boolean cargando;
    private Runnable dormirFin;
    private DigitosView reloj;
    private LinearLayout barraLuz;
    private SeekBar barraSeek;
    private int luzAhora = 1;
    private boolean puntos = true;
    /** A los 10 segundos sin tocar la barrita, se esconde y la luz vuelve al mínimo (cuida la pantalla de noche). */
    private final Runnable luzMinimo = () -> { luzAhora = 1; ponerLuz(); if (barraLuz != null) barraLuz.setVisibility(View.GONE); };

    private void esconderLuzLuego() { h.removeCallbacks(luzMinimo); h.postDelayed(luzMinimo, 10000); }

    private int dp(float v) { return Util.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("radioreloj", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setShowWhenLocked(true);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // Versión 30: hora enorme en cian (números de radio reloj), batería y temperatura grandes,
        // y una columna angosta de botones (símbolo encima, nombre debajo)
        LinearLayout raiz = new LinearLayout(this);
        raiz.setBackgroundColor(0xFF0E0E0E);
        raiz.setPadding(dp(6), dp(6), dp(6), dp(6));

        FrameLayout pant = new FrameLayout(this);
        pant.setBackground(Comun.fondo(this, Color.BLACK, 22));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(10), dp(16), dp(10));
        // Versión 31: arriba una línea delgada (✕ cerrar, batería, temperatura); la hora ocupa todo lo demás;
        // abajo otra línea delgada con la fecha a la izquierda y la alarma a la derecha
        col.setPadding(dp(8), dp(4), dp(8), dp(4)); // Versión 32: menos margen, la hora crece
        LinearLayout arriba = new LinearLayout(this);
        arriba.setGravity(Gravity.CENTER_VERTICAL);
        TextView cerrar = texto(18, Color.WHITE, "✕");
        cerrar.setBackground(Comun.fondo(this, 0xFFC62828, 10));
        cerrar.setOnClickListener(v -> { pararTodo(); finish(); });
        arriba.addView(cerrar, new LinearLayout.LayoutParams(dp(40), dp(30)));
        tBat = texto(25, 0xFF9CCC65); // Versión 34: letreros más grandes
        tBat.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tBat.setPadding(dp(14), 0, 0, 0);
        arriba.addView(tBat, new LinearLayout.LayoutParams(0, -2, 1f));
        tSonando = texto(15, 0xFF9CCC65);
        arriba.addView(tSonando, new LinearLayout.LayoutParams(0, -2, 1.4f));
        tTemp = texto(25, 0xFF81D4FA);
        tTemp.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        arriba.addView(tTemp, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(arriba);
        reloj = new DigitosView(this);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, 0, 1f);
        rl.topMargin = 0;
        rl.bottomMargin = 0;
        col.addView(reloj, rl);
        // Versión 33: deslizando el dedo de lado a lado sobre la hora sube o baja la luz
        reloj.setOnTouchListener(new View.OnTouchListener() {
            float x0; int luz0;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        x0 = e.getX(); luz0 = luzAhora;
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        float dx = e.getX() - x0;
                        if (Math.abs(dx) < dp(10)) return true;
                        luzAhora = Math.max(1, Math.min(100, luz0 + Math.round(dx * 100f / Math.max(1, v.getWidth()))));
                        ponerLuz();
                        h.removeCallbacks(luzMinimo);
                        barraLuz.setVisibility(View.VISIBLE);
                        barraSeek.setProgress(luzAhora);
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        // la luz queda como él la dejó; solo se esconde la barrita
                        h.postDelayed(() -> { if (barraLuz != null) barraLuz.setVisibility(View.GONE); }, 2500);
                        return true;
                }
                return true;
            }
        });
        LinearLayout abajo = new LinearLayout(this);
        abajo.setGravity(Gravity.CENTER_VERTICAL);
        tFecha = texto(23, 0xFF4DD0E1);
        tFecha.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tFecha.setSingleLine(true);
        tFecha.setEllipsize(android.text.TextUtils.TruncateAt.END);
        abajo.addView(tFecha, new LinearLayout.LayoutParams(0, -2, 1.3f));
        tAlarma = texto(23, 0xFFFFD54F);
        tAlarma.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tAlarma.setSingleLine(true);
        abajo.addView(tAlarma, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(abajo);
        pant.addView(col, new FrameLayout.LayoutParams(-1, -1));
        // La barrita de la luz: aparece al tocar 🔅 y se esconde sola
        barraLuz = new LinearLayout(this);
        barraLuz.setGravity(Gravity.CENTER_VERTICAL);
        barraLuz.setBackground(Comun.fondo(this, 0xEE222222, 14));
        barraLuz.setPadding(dp(12), 0, dp(8), 0);
        barraLuz.addView(texto(20, Color.WHITE, "🔅"));
        SeekBar luz = new SeekBar(this);
        luz.setMax(100);
        luz.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean u) { if (u) { luzAhora = Math.max(1, v); ponerLuz(); esconderLuzLuego(); } }
            @Override public void onStartTrackingTouch(SeekBar s) { h.removeCallbacks(luzMinimo); }
            @Override public void onStopTrackingTouch(SeekBar s) { esconderLuzLuego(); }
        });
        barraSeek = luz;
        barraLuz.addView(luz, new LinearLayout.LayoutParams(0, dp(48), 1f));
        barraLuz.setVisibility(View.GONE);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, dp(58), Gravity.BOTTOM);
        bp.setMargins(dp(14), 0, dp(14), dp(12));
        pant.addView(barraLuz, bp);
        raiz.addView(pant, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout der = new LinearLayout(this);
        der.setOrientation(LinearLayout.VERTICAL);
        der.setPadding(dp(8), 0, 0, 0);
        boton(der, "📻", "Radio", () -> { pararTodo(); SonidoService.radioFavorita(this); tSonando.setText("📻 Radio"); });
        boton(der, "🎵", "Música", this::escogerMusica);
        boton(der, "🎧", "Mi carpeta", () -> { pararTodo(); SonidoService.miCarpeta(this); tSonando.setText("🎧 Mi carpeta"); });
        boton(der, "🎙", "Grabar", this::grabarNota);
        boton(der, "⏹", "Parar", () -> { pararTodo(); tSonando.setText(""); });
        boton(der, "💤", "Dormir", this::dormir);
        boton(der, "⏰", "Alarma", this::alarma);
        boton(der, "🔅", "Luz", () -> {
            barraLuz.setVisibility(View.VISIBLE);
            barraSeek.setProgress(luzAhora);
            esconderLuzLuego();
        });
        raiz.addView(der, new LinearLayout.LayoutParams(dp(104), -1));
        setContentView(raiz);
        ponerLuz();
        registerReceiver(bat, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        despertarSiToca(getIntent());
    }

    /** Versión 31: el botón ◀ del celular siempre cierra el radio reloj (nunca queda trancado). */
    @Override public void onBackPressed() { pararTodo(); finish(); }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); despertarSiToca(i); }

    @Override protected void onResume() { super.onResume(); h.post(tic); botonFlotante(true); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tic); botonFlotante(false); }

    /** Versión 34: pantalla completa siempre (la barra de arriba del celular no se monta sobre la batería). */
    @Override public void onWindowFocusChanged(boolean foco) {
        super.onWindowFocusChanged(foco);
        if (foco) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    /** Versión 34: el botón flotante corre aparte; se le avisa por mensaje para que se esconda. */
    private void botonFlotante(boolean esconder) {
        sendBroadcast(new Intent(BarraService.ACCION_RELOJ).setPackage(getPackageName()).putExtra("esconder", esconder));
    }
    @Override protected void onDestroy() { try { unregisterReceiver(bat); } catch (Exception ignored) {} super.onDestroy(); }

    private final BroadcastReceiver bat = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            bateria = lv < 0 ? -1 : lv * 100 / sc;
            cargando = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            pintarBat();
        }
    };

    private void pintarBat() {
        if (bateria < 0) { tBat.setText(""); return; }
        tBat.setText((cargando ? "⚡ " : "🔋 ") + bateria + " %");
        tBat.setTextColor(bateria <= 20 ? 0xFFFF5252 : bateria <= 50 ? 0xFFFFD54F : 0xFF9CCC65);
    }

    private final Runnable tic = new Runnable() {
        @Override public void run() {
            Date d = new Date();
            reloj.poner(new SimpleDateFormat("h:mm", es).format(d), true);
            String a = new SimpleDateFormat("a", es).format(d).replace("a.m.", "a. m.").replace("p.m.", "p. m.");
            String f = new SimpleDateFormat("EEEE d 'de' MMMM", es).format(d).replace(".", "");
            tFecha.setText(a + " · " + f);
            long al = p.getLong("alarma", 0);
            tAlarma.setText(al > System.currentTimeMillis()
                    ? "⏰ " + new SimpleDateFormat("h:mm a", es).format(new Date(al)).replace("a.m.", "a. m.").replace("p.m.", "p. m.")
                    + " · " + (mensaje().exists() && p.getBoolean("conMensaje", true) ? "🎙 + " : "")
                    + (p.getInt("sonido", 0) == 3 ? "📻 Radio" : Melodias.NOMBRES[p.getInt("sonido", 0)])
                    : "⏰ Sin alarma");
            // Temperatura: la última que sacó la pantalla de inicio (se actualiza cuando hay internet)
            String cl = getSharedPreferences("inicio", MODE_PRIVATE).getString("clima", "");
            Matcher m = Pattern.compile("(-?\\d+)°").matcher(cl);
            tTemp.setText(m.find() ? "🌡 " + m.group(1) + " °C" : "");
            h.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50);
            tFecha.setTextColor(0xFF4DD0E1);
        }
    };

    private TextView texto(float t, int c) { return texto(t, c, ""); }

    private TextView texto(float t, int c, String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(t);
        v.setTextColor(c);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    /** Botón angosto: el símbolo arriba y el nombre debajo. */
    private void boton(LinearLayout l, String icono, String nombre, Runnable r) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2C2C2C, 0xFF1C1C1C});
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), 0xFF3A3A3A);
        b.setBackground(g);
        TextView i = texto(23, Color.WHITE, icono); // Versión 33: botones más grandes
        i.setIncludeFontPadding(false);
        b.addView(i);
        TextView n = texto(14, 0xFFCFD8DC, nombre);
        n.setIncludeFontPadding(false);
        n.setPadding(0, dp(3), 0, 0);
        b.addView(n);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = l.getChildCount() == 0 ? 0 : dp(6);
        l.addView(b, lp);
    }

    /** Luz de la pantalla: muy suave por defecto; él la gradúa. */
    private void ponerLuz() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.01f, luzAhora / 100f);
        getWindow().setAttributes(lp);
    }

    private void pararTodo() {
        try { if (mpMsg != null) { mpMsg.release(); mpMsg = null; } } catch (Exception ignored) {}
        Melodias.parar();
        SonidoService.parar(this);
        if (dormirFin != null) { h.removeCallbacks(dormirFin); dormirFin = null; }
    }

    private void escogerMusica() {
        new AlertDialog.Builder(this).setTitle("🎵 Música de Mi Barra").setItems(Melodias.NOMBRES, (d, w) -> {
            pararTodo();
            Melodias.tocar(w, false);
            tSonando.setText(Melodias.NOMBRES[w]);
        }).show();
    }

    private void dormir() {
        String[] ops = {"15 minutos", "30 minutos", "60 minutos"};
        int[] min = {15, 30, 60};
        new AlertDialog.Builder(this).setTitle("💤 Apagar el sonido en…").setItems(ops, (d, w) -> {
            SonidoService.dormir(this, min[w]);
            if (dormirFin != null) h.removeCallbacks(dormirFin);
            dormirFin = () -> { Melodias.parar(); tSonando.setText(""); };
            h.postDelayed(dormirFin, min[w] * 60000L);
            Toast.makeText(this, "💤 Se apaga en " + ops[w], Toast.LENGTH_SHORT).show();
        }).show();
    }

    /** Alarma: escoge la hora y con qué suena (música propia o radio). */
    private void alarma() {
        boolean hay = mensaje().exists();
        String[] ops = {"⏰ Poner alarma", "🔕 Quitar alarma",
                hay ? "🎙 Volver a grabar mi mensaje" : "🎙 Grabar un mensaje con mi voz",
                hay ? (p.getBoolean("conMensaje", true) ? "🔇 Que la alarma NO diga mi mensaje" : "🔊 Que la alarma diga mi mensaje") : "",
                hay ? "▶ Escuchar mi mensaje" : ""};
        java.util.List<String> lista = new java.util.ArrayList<>();
        for (String o : ops) if (!o.isEmpty()) lista.add(o);
        new AlertDialog.Builder(this).setTitle("⏰ Alarma").setItems(lista.toArray(new String[0]), (d, w) -> {
            if (w == 1) { quitarAlarma(); return; }
            if (w == 2) { grabarMensaje(); return; }
            if (w == 3) { p.edit().putBoolean("conMensaje", !p.getBoolean("conMensaje", true)).apply(); h.post(tic); return; }
            if (w == 4) { tocarMensaje(null); return; }
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(this, (tp, hh, mm) -> {
                String[] son = {Melodias.NOMBRES[0], Melodias.NOMBRES[1], Melodias.NOMBRES[2], "📻 Radio (emisora favorita)"};
                new AlertDialog.Builder(this).setTitle("¿Con qué suena?").setItems(son, (d2, s) -> {
                    p.edit().putInt("sonido", s).apply();
                    ponerAlarma(hh, mm);
                }).show();
            }, p.getInt("hh", 5), p.getInt("mm", 30), false).show();
        }).show();
    }

    private PendingIntent pi() {
        return PendingIntent.getActivity(this, 2929, new Intent(this, RadioRelojActivity.class).putExtra("despertar", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void ponerAlarma(int hh, int mm) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hh);
        c.set(Calendar.MINUTE, mm);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
        try {
            AlarmManager am = getSystemService(AlarmManager.class);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(c.getTimeInMillis(), pi()), pi());
            p.edit().putLong("alarma", c.getTimeInMillis()).putInt("hh", hh).putInt("mm", mm).apply();
            Toast.makeText(this, "⏰ Alarma lista", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo poner la alarma", Toast.LENGTH_LONG).show();
        }
        h.post(tic);
    }

    private void quitarAlarma() {
        try { getSystemService(AlarmManager.class).cancel(pi()); } catch (Exception ignored) {}
        p.edit().putLong("alarma", 0).apply();
        h.post(tic);
    }

    /** Versión 31: 🎙 Grabar un mensaje suelto; queda en Descargas/MiBarra/Mensajes. */
    private void grabarNota() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 31);
            return;
        }
        pararTodo();
        File tmp = new File(getCacheDir(), "nota_tmp.m4a");
        android.media.MediaRecorder r = new android.media.MediaRecorder(this);
        try {
            r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            r.setAudioEncodingBitRate(64000);
            r.setAudioSamplingRate(44100);
            r.setOutputFile(tmp.getAbsolutePath());
            r.prepare();
            r.start();
        } catch (Exception e) { r.release(); Toast.makeText(this, "No se pudo usar el micrófono", Toast.LENGTH_LONG).show(); return; }
        tSonando.setText("🔴 Grabando…");
        new AlertDialog.Builder(this).setTitle("🎙 Grabando su mensaje…")
                .setMessage("Hable. Cuando termine toque «Guardar».")
                .setCancelable(false)
                .setPositiveButton("✔ Guardar", (d, w) -> {
                    try { r.stop(); } catch (Exception ignored) {}
                    r.release();
                    tSonando.setText("");
                    try {
                        byte[] datos = java.nio.file.Files.readAllBytes(tmp.toPath());
                        String nombre = "Mensaje_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm", es).format(new Date()) + ".m4a";
                        Util.guardarBytes(this, nombre, "audio/mp4", datos);
                        Toast.makeText(this, "✔ Guardado en Descargas/MiBarra/Mensajes", Toast.LENGTH_LONG).show();
                    } catch (Exception e) { Toast.makeText(this, "No se pudo guardar", Toast.LENGTH_LONG).show(); }
                    tmp.delete();
                })
                .setNegativeButton("Cancelar", (d, w) -> { try { r.stop(); } catch (Exception ignored) {} r.release(); tmp.delete(); tSonando.setText(""); })
                .show();
    }

    private File mensaje() { return new File(getFilesDir(), "alarma_mensaje.m4a"); }

    private android.media.MediaPlayer mpMsg;

    /** Toca el mensaje grabado (a todo volumen de alarma); al terminar sigue con «despues». */
    private void tocarMensaje(Runnable despues) {
        try {
            if (mpMsg != null) mpMsg.release();
            mpMsg = new android.media.MediaPlayer();
            mpMsg.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM).build());
            mpMsg.setDataSource(mensaje().getAbsolutePath());
            mpMsg.setOnCompletionListener(m -> { m.release(); mpMsg = null; if (despues != null) despues.run(); });
            mpMsg.prepare();
            mpMsg.start();
        } catch (Exception e) { if (despues != null) despues.run(); }
    }

    /** Graba con el micrófono el mensaje que dirá la alarma. */
    private void grabarMensaje() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 30);
            return;
        }
        File tmp = new File(getFilesDir(), "alarma_mensaje_tmp.m4a");
        android.media.MediaRecorder r = new android.media.MediaRecorder(this);
        try {
            r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            r.setAudioEncodingBitRate(96000);
            r.setAudioSamplingRate(44100);
            r.setOutputFile(tmp.getAbsolutePath());
            r.prepare();
            r.start();
        } catch (Exception e) { r.release(); Toast.makeText(this, "No se pudo usar el micrófono", Toast.LENGTH_LONG).show(); return; }
        new AlertDialog.Builder(this).setTitle("🎙 Grabando su mensaje…")
                .setMessage("Hable ahora. Por ejemplo: «Buenos días, levántese que tiene cita médica a las 8».")
                .setCancelable(false)
                .setPositiveButton("✔ Listo", (d, w) -> {
                    try { r.stop(); } catch (Exception ignored) {}
                    r.release();
                    if (tmp.length() > 2000) { tmp.renameTo(mensaje()); p.edit().putBoolean("conMensaje", true).apply(); tocarMensaje(null);
                        Toast.makeText(this, "✔ Mensaje guardado para la alarma", Toast.LENGTH_SHORT).show(); }
                })
                .setNegativeButton("Cancelar", (d, w) -> { try { r.stop(); } catch (Exception ignored) {} r.release(); tmp.delete(); })
                .show();
    }

    /** Llegó la hora: prende la pantalla y suena (la música empieza bajito y va subiendo). Mañana a la misma hora otra vez. */
    private void despertarSiToca(Intent i) {
        if (i == null || !i.getBooleanExtra("despertar", false)) return;
        i.removeExtra("despertar");
        setTurnScreenOn(true);
        pararTodo();
        int s = p.getInt("sonido", 0);
        Runnable sonar = () -> {
            if (s == 3) { SonidoService.radioFavorita(this); tSonando.setText("📻 Radio"); }
            else { Melodias.tocar(s, true); tSonando.setText(Melodias.NOMBRES[s]); }
        };
        // Versión 30: primero su mensaje grabado (por ejemplo «levántese, tiene cita médica») y luego la música
        if (mensaje().exists() && p.getBoolean("conMensaje", true)) { tSonando.setText("🎙 Su mensaje"); tocarMensaje(sonar); }
        else sonar.run();
        ponerAlarma(p.getInt("hh", 5), p.getInt("mm", 30));
        new AlertDialog.Builder(this).setTitle("⏰ ¡Buenos días!").setCancelable(false)
                .setPositiveButton("⏹ Apagar", (d, w) -> { pararTodo(); tSonando.setText(""); })
                .setNegativeButton("💤 10 minutos más", (d, w) -> {
                    pararTodo();
                    tSonando.setText("💤 Suena otra vez en 10 min");
                    h.postDelayed(() -> { Melodias.tocar(s == 3 ? 0 : s, true); tSonando.setText("⏰"); }, 600000);
                }).show();
    }
}
