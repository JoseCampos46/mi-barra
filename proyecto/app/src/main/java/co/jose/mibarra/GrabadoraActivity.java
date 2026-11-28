package co.jose.mibarra;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Grabadora con cara de reproductor de música: ▶/⏸, ⏹, modo tenue con opacidad y minimizar a la barra de arriba. */
public class GrabadoraActivity extends Base {

    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView boton, tiempo, estado, parar;
    private LinearLayout raiz;
    private SeekBar opacidad;
    private View nivel;
    private LinearLayout lista;
    private Switch lejos;
    private Runnable tic;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        List<String> p = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), 1);

        LinearLayout l = vertical();
        raiz = l;
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        l.setBackgroundColor(0xFF121212);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        // Portada como de disco
        TextView portada = new TextView(this);
        portada.setText("♪");
        portada.setTextSize(90);
        portada.setTextColor(0xCCFFFFFF);
        portada.setGravity(Gravity.CENTER);
        GradientDrawable fp = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF6A1B9A, 0xFF1565C0, 0xFF00897B});
        fp.setCornerRadius(dp(18));
        portada.setBackground(fp);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(210), dp(210));
        pp.topMargin = dp(6);
        l.addView(portada, pp);

        estado = new TextView(this);
        estado.setTextSize(21);
        estado.setTypeface(Typeface.DEFAULT_BOLD);
        estado.setTextColor(Color.WHITE);
        estado.setGravity(Gravity.CENTER);
        estado.setPadding(0, dp(14), 0, 0);
        l.addView(estado);
        TextView sub = new TextView(this);
        sub.setText("Mis audios");
        sub.setTextSize(14);
        sub.setTextColor(0xFF9E9E9E);
        sub.setGravity(Gravity.CENTER);
        l.addView(sub);

        // Barrita como la de una canción (en realidad muestra el nivel del micrófono)
        LinearLayout barra = new LinearLayout(this);
        GradientDrawable fb = new GradientDrawable();
        fb.setColor(0xFF424242);
        fb.setCornerRadius(dp(3));
        barra.setBackground(fb);
        nivel = new View(this);
        GradientDrawable fn = new GradientDrawable();
        fn.setColor(0xFF1DB954);
        fn.setCornerRadius(dp(3));
        nivel.setBackground(fn);
        barra.addView(nivel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        bp.setMargins(dp(20), dp(16), dp(20), dp(4));
        l.addView(barra, bp);
        tiempo = new TextView(this);
        tiempo.setTextSize(15);
        tiempo.setTypeface(Typeface.MONOSPACE);
        tiempo.setTextColor(0xFFBDBDBD);
        tiempo.setGravity(Gravity.CENTER);
        l.addView(tiempo);

        // Controles: ⏹  ( ▶ / ⏸ )
        LinearLayout ctl = horizontal();
        ctl.setGravity(Gravity.CENTER);
        parar = new TextView(this);
        parar.setText("⏹");
        parar.setTextSize(30);
        parar.setTextColor(Color.WHITE);
        parar.setGravity(Gravity.CENTER);
        parar.setOnClickListener(v -> detener());
        ctl.addView(parar, new LinearLayout.LayoutParams(dp(80), dp(80)));
        boton = new TextView(this);
        boton.setGravity(Gravity.CENTER);
        boton.setTextSize(40);
        boton.setTextColor(Color.BLACK);
        boton.setOnClickListener(v -> alternar());
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(dp(96), dp(96));
        op.setMargins(dp(18), 0, dp(18), 0);
        ctl.addView(boton, op);
        View hueco = new View(this);
        ctl.addView(hueco, new LinearLayout.LayoutParams(dp(80), dp(80)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(10);
        l.addView(ctl, cp);

        // Tres formas de verla: Normal · Tenue · Minimizar
        LinearLayout modos = horizontal();
        modos.addView(chip("☀ Normal", () -> modo(false)), peso(1));
        modos.addView(chip("🌙 Tenue", () -> modo(true)), peso(1));
        modos.addView(chip("▁ Minimizar", this::minimizar), peso(1));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        mp.topMargin = dp(16);
        l.addView(modos, mp);
        LinearLayout fila = horizontal();
        fila.setGravity(Gravity.CENTER_VERTICAL);
        TextView to = new TextView(this);
        to.setText("Opacidad ");
        to.setTextColor(0xFFBDBDBD);
        fila.addView(to);
        opacidad = new SeekBar(this);
        opacidad.setMax(90);
        opacidad.setProgress(prefs().getInt("grabOpacidad", 40) - 10);
        opacidad.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int v, boolean u) {
                if (!u) return;
                prefs().edit().putInt("grabOpacidad", v + 10).apply();
                if (prefs().getBoolean("grabTenue", false)) aplicarModo();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        fila.addView(opacidad, peso(1));
        l.addView(fila, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        lejos = new Switch(this);
        lejos.setText("  Modo lejos (para voces lejanas o con ruido)");
        lejos.setTextSize(15);
        lejos.setTextColor(0xFFE0E0E0);
        lejos.setChecked(prefs().getBoolean("grabLejos", false));
        lejos.setOnCheckedChangeListener((c, v) -> prefs().edit().putBoolean("grabLejos", v).apply());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(8);
        l.addView(lejos, sp);

        // Versión 18: pausa sola cuando hay 30 s de silencio y sigue sola al oír voz
        Switch sil = new Switch(this);
        sil.setText("  Pausar sola tras 30 s de silencio (sigue sola al oír voz)");
        sil.setTextSize(15);
        sil.setTextColor(0xFFE0E0E0);
        sil.setChecked(prefs().getBoolean("grabSilencio45", false));
        sil.setOnCheckedChangeListener((c, v) -> prefs().edit().putBoolean("grabSilencio45", v).apply());
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp2.topMargin = dp(8);
        l.addView(sil, sp2);

        TextView tl = titulo("\nMis audios guardados");
        tl.setTextSize(19);
        tl.setTextColor(Color.WHITE);
        l.addView(tl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        lista = vertical();
        l.addView(lista, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF121212);
        sv.addView(l);
        setContentView(sv);
        pintar();
        llenarLista();
        aplicarModo();
        // Versión 18: desde el botón flotante empieza de una y se esconde; queda la pastilla roja con el tiempo
        if (getIntent().getBooleanExtra("empezar", false) && !GrabadoraService.grabando
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            alternar();
            h.postDelayed(() -> moveTaskToBack(true), 700);
        }
        tic = new Runnable() { @Override public void run() { refrescar(); h.postDelayed(this, 120); } };
    }

    @Override protected void onResume() { super.onResume(); h.post(tic); pintar(); llenarLista(); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tic); }

    private android.content.SharedPreferences prefs() { return getSharedPreferences("barra", MODE_PRIVATE); }

    private TextView chip(String t, Runnable r) {
        TextView c = new TextView(this);
        c.setText(t);
        c.setTextSize(14);
        c.setTextColor(Color.WHITE);
        c.setGravity(Gravity.CENTER);
        GradientDrawable f = new GradientDrawable();
        f.setColor(0xFF2A2A2A);
        f.setCornerRadius(dp(24));
        f.setStroke(dp(1), 0xFF555555);
        c.setBackground(f);
        c.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private void modo(boolean tenue) {
        prefs().edit().putBoolean("grabTenue", tenue).apply();
        aplicarModo();
    }

    /** Tenue: la pantalla se apaga casi toda (según la opacidad) y baja el brillo. */
    private void aplicarModo() {
        boolean tenue = prefs().getBoolean("grabTenue", false);
        float o = tenue ? prefs().getInt("grabOpacidad", 40) / 100f : 1f;
        raiz.setAlpha(o);
        android.view.WindowManager.LayoutParams w = getWindow().getAttributes();
        w.screenBrightness = tenue ? Math.max(0.02f, o * 0.6f) : -1f;
        getWindow().setAttributes(w);
    }

    /** Se esconde y queda el control en la barra de arriba (⏸ ▶ ⏹). */
    private void minimizar() {
        if (GrabadoraService.grabando) aviso("Siga controlándola desde la barra de arriba: ⏸ ▶ ⏹");
        moveTaskToBack(true);
    }

    /** Botón grande: si no graba, empieza; si graba, pausa o sigue. */
    private void alternar() {
        if (GrabadoraService.grabando) {
            startService(new Intent(this, GrabadoraService.class).setAction(GrabadoraService.PAUSA));
            h.postDelayed(this::pintar, 250);
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                return;
            }
            startForegroundService(new Intent(this, GrabadoraService.class).putExtra("lejos", lejos.isChecked()));
            h.postDelayed(this::pintar, 400);
        }
    }

    private void detener() {
        if (!GrabadoraService.grabando) return;
        startService(new Intent(this, GrabadoraService.class).setAction(GrabadoraService.PARAR));
        h.postDelayed(() -> { pintar(); llenarLista(); }, 600);
    }

    private void pintar() {
        boolean g = GrabadoraService.grabando, pa = GrabadoraService.pausado;
        GradientDrawable o = new GradientDrawable();
        o.setShape(GradientDrawable.OVAL);
        o.setColor(Color.WHITE);
        boton.setBackground(o);
        boton.setText(g && !pa ? "⏸" : "▶");
        parar.setAlpha(g ? 1f : 0.3f);
        int sig = prefs().getInt("numGrab", 0) + (g ? 0 : 1);
        estado.setText(String.format(Locale.US, "Pista %03d", g ? GrabadoraService.numero : sig));
        if (!g) { tiempo.setText("00:00:00"); ancho(0); }
        else if (pa) { ancho(0); refrescarTiempo(); }
    }

    private void refrescarTiempo() {
        long s = GrabadoraService.segundos();
        tiempo.setText(String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                + (GrabadoraService.pausaSilencio ? "  ·  pausa por silencio" : GrabadoraService.pausado ? "  ·  en pausa" : ""));
    }

    private void refrescar() {
        if (!GrabadoraService.grabando) return;
        refrescarTiempo();
        if (GrabadoraService.pausado) return;
        int amp = GrabadoraService.nivel;
        double f = amp <= 0 ? 0 : Math.min(1, Math.log10(amp) / Math.log10(32767));
        ancho(f);
    }

    private void ancho(double f) {
        View p = (View) nivel.getParent();
        int w = (int) (p.getWidth() * f);
        ViewGroup.LayoutParams lp = nivel.getLayoutParams();
        lp.width = w;
        nivel.setLayoutParams(lp);
    }

    // ---------------- Lista ----------------

    private List<String[]> leer() {
        List<String[]> r = new ArrayList<>();
        try {
            for (String l : Files.readAllLines(GrabadoraService.indice(this).toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p.length >= 6) r.add(p);
            }
        } catch (Exception ignored) {}
        Collections.reverse(r);
        return r;
    }

    private void escribir(List<String[]> todas) {
        List<String[]> c = new ArrayList<>(todas);
        Collections.reverse(c);
        StringBuilder s = new StringBuilder();
        for (String[] p : c) s.append(String.join("\t", p)).append('\n');
        try { Files.write(GrabadoraService.indice(this).toPath(), s.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }

    private void llenarLista() {
        lista.removeAllViews();
        List<String[]> todas = leer();
        if (todas.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Todavía no hay audios.");
            t.setTextColor(0xFFBDBDBD);
            lista.addView(t);
        }
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy hh:mm a", new Locale("es", "CO"));
        for (String[] g : todas) {
            int n = Integer.parseInt(g[1]);
            long dur = Long.parseLong(g[4]) / 1000;
            LinearLayout c = vertical();
            GradientDrawable fondo = new GradientDrawable();
            fondo.setColor(0xFF1E1E1E);
            fondo.setCornerRadius(dp(10));
            c.setBackground(fondo);
            c.setPadding(dp(10), dp(6), dp(10), dp(6));
            TextView t = new TextView(this);
            t.setText(String.format(Locale.US, "%03d", n) + (g[2].isEmpty() ? "" : " · " + g[2]) + "\n"
                    + df.format(new Date(Long.parseLong(g[3]))) + " · " + (dur / 60) + " min " + (dur % 60) + " s");
            t.setTextSize(16);
            t.setTextColor(0xFFEEEEEE);
            c.addView(t);
            LinearLayout f = horizontal();
            // Versión 18: se oye aquí mismo, sin abrir otro programa
            SeekBar avance = new SeekBar(this);
            avance.setVisibility(View.GONE);
            final Button[] oir = new Button[1];
            oir[0] = chico(g[0].equals(sonando) && mp != null && mp.isPlaying() ? "⏸ Pausa" : "▶ Oír",
                    () -> oirAqui(g[0], oir[0], avance));
            f.addView(oir[0], peso(1));
            f.addView(chico("✎ Nombre", () -> renombrar(todas, g)), peso(1));
            f.addView(chico("💬 WhatsApp", () -> enviar(WHATSAPP, "audio/mp4", Uri.parse(g[0]))), peso(1));
            c.addView(f);
            c.addView(avance, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
            if (g[0].equals(sonando) && mp != null) { avance.setVisibility(View.VISIBLE); barraSonando = avance; botonSonando = oir[0]; }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            lista.addView(c, lp);
        }
    }

    // ---------------- Reproductor interno (versión 18) ----------------

    private android.media.MediaPlayer mp;
    private String sonando;
    private SeekBar barraSonando;
    private Button botonSonando;
    private final Runnable avanceR = new Runnable() {
        @Override public void run() {
            if (mp != null && barraSonando != null) {
                try { barraSonando.setProgress(mp.getCurrentPosition()); } catch (Exception ignored) {}
                h.postDelayed(this, 300);
            }
        }
    };

    private void oirAqui(String u, Button b, SeekBar barra) {
        if (u.equals(sonando) && mp != null) {
            if (mp.isPlaying()) { mp.pause(); b.setText("▶ Seguir"); }
            else { mp.start(); b.setText("⏸ Pausa"); h.post(avanceR); }
            return;
        }
        soltarReproductor();
        try {
            mp = new android.media.MediaPlayer();
            mp.setDataSource(this, Uri.parse(u));
            mp.prepare();
            sonando = u;
            barraSonando = barra;
            botonSonando = b;
            barra.setMax(mp.getDuration());
            barra.setProgress(0);
            barra.setVisibility(View.VISIBLE);
            barra.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int v, boolean u2) { if (u2 && mp != null) mp.seekTo(v); }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
            mp.setOnCompletionListener(m -> { b.setText("▶ Oír"); barra.setProgress(0); });
            mp.start();
            b.setText("⏸ Pausa");
            h.post(avanceR);
        } catch (Exception e) {
            soltarReproductor();
            aviso("No se pudo oír este audio");
        }
    }

    private void soltarReproductor() {
        h.removeCallbacks(avanceR);
        if (mp != null) { try { mp.release(); } catch (Exception ignored) {} mp = null; }
        if (botonSonando != null) botonSonando.setText("▶ Oír");
        if (barraSonando != null) barraSonando.setVisibility(View.GONE);
        sonando = null; barraSonando = null; botonSonando = null;
    }

    @Override protected void onDestroy() { soltarReproductor(); super.onDestroy(); }

    private Button chico(String t, Runnable r) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    /** Le agrega un nombre sin perder el número: "001 · Pedro Gómez · cotización baño". */
    private void renombrar(List<String[]> todas, String[] g) {
        EditText e = campoTexto("Cliente o tema", g[2]);
        new AlertDialog.Builder(this).setTitle(String.format(Locale.US, "Grabación %03d", Integer.parseInt(g[1])))
                .setView(e)
                .setPositiveButton("Guardar", (d, w) -> {
                    g[2] = limpio(e.getText().toString());
                    String archivo = String.format(Locale.US, "Grabacion %03d", Integer.parseInt(g[1]))
                            + (g[2].isEmpty() ? "" : " - " + g[2].replaceAll("[\\\\/:*?\"<>|]", " ")) + ".m4a";
                    try {
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.MediaColumns.DISPLAY_NAME, archivo);
                        getContentResolver().update(Uri.parse(g[0]), v, null, null);
                        g[5] = archivo;
                    } catch (Exception ignored) {}
                    escribir(todas);
                    llenarLista();
                }).setNegativeButton("Cancelar", null).show();
    }
}
