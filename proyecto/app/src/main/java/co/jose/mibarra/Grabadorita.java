package co.jose.mibarra;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Versión 22: la grabadora propia en la página de herramientas, del tamaño del cuadro de la frase.
 * Reloj con el conteo y botones de reproductor: Grabar, Pausa, Parar, Escuchar y Recortar. No usa otras apps.
 */
final class Grabadorita extends LinearLayout {

    private final Handler h = new Handler(Looper.getMainLooper());
    private final TextView estado, reloj;
    private final TextView[] b = new TextView[5];
    private MediaPlayer mp;

    Grabadorita(Context c) {
        super(c);
        setOrientation(VERTICAL);
        int p = dp(10);
        setPadding(dp(14), p, dp(14), p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x26FFFFFF);
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1), 0x33FFFFFF);
        setBackground(g);

        LinearLayout arriba = new LinearLayout(c);
        arriba.setGravity(Gravity.CENTER_VERTICAL);
        estado = new TextView(c);
        estado.setTextColor(Color.WHITE);
        estado.setTextSize(13);
        estado.setTypeface(Typeface.DEFAULT_BOLD);
        arriba.addView(estado, new LayoutParams(0, -2, 1f));
        reloj = new TextView(c);
        reloj.setTextColor(Color.WHITE);
        reloj.setTextSize(38);
        reloj.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        reloj.setIncludeFontPadding(false);
        arriba.addView(reloj);
        addView(arriba, new LayoutParams(-1, 0, 1f));

        LinearLayout fila = new LinearLayout(c);
        String[][] bs = {{"⏺", "Grabar", "#E53935"}, {"⏸", "Pausa", "#546E7A"}, {"⏹", "Parar", "#37474F"}, {"▶", "Escuchar", "#2E7D32"}, {"✂", "Recortar", "#6A1B9A"}};
        for (int i = 0; i < 5; i++) {
            final int k = i;
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            b[i] = new TextView(c);
            b[i].setText(bs[i][0]);
            b[i].setTextSize(19);
            b[i].setTextColor(Color.WHITE);
            b[i].setGravity(Gravity.CENTER);
            GradientDrawable o = new GradientDrawable();
            o.setShape(GradientDrawable.OVAL);
            o.setColor(Color.parseColor(bs[i][2]));
            b[i].setBackground(o);
            col.addView(b[i], new LayoutParams(dp(46), dp(46)));
            TextView t = new TextView(c);
            t.setText(bs[i][1]);
            t.setTextSize(10.5f);
            t.setTextColor(Color.WHITE);
            col.addView(t);
            col.setOnClickListener(v -> tocar(k));
            fila.addView(col, new LayoutParams(0, -2, 1f));
        }
        addView(fila);
        pintar();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private final Runnable tic = new Runnable() {
        @Override public void run() { pintar(); h.postDelayed(this, 500); }
    };

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); h.post(tic); }
    @Override protected void onDetachedFromWindow() {
        h.removeCallbacks(tic);
        if (mp != null) { try { mp.release(); } catch (Exception ignored) {} mp = null; }
        super.onDetachedFromWindow();
    }

    private void pintar() {
        long s = GrabadoraService.grabando ? GrabadoraService.segundos() : mp != null ? mp.getCurrentPosition() / 1000 : 0;
        reloj.setText(String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        if (GrabadoraService.grabando)
            estado.setText(GrabadoraService.pausado ? "⏸ En pausa" : GrabadoraService.pausaSilencio ? "⏸ Silencio…" : "🔴 Grabando…");
        else if (mp != null && mp.isPlaying()) estado.setText("▶ Escuchando la última");
        else estado.setText("🎙 Grabadora");
        b[1].setText(GrabadoraService.grabando && GrabadoraService.pausado ? "▶" : "⏸");
        b[3].setText(mp != null && mp.isPlaying() ? "⏸" : "▶");
    }

    private void tocar(int k) {
        Context c = getContext();
        if (k == 0) {
            if (GrabadoraService.grabando) { aviso("Ya está grabando"); return; }
            if (c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                if (c instanceof Activity) ((Activity) c).requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 22);
                aviso("Dele permiso al micrófono y vuelva a tocar Grabar");
                return;
            }
            parar();
            c.startForegroundService(new Intent(c, GrabadoraService.class));
        } else if (k == 1) {
            if (GrabadoraService.grabando) c.startService(new Intent(c, GrabadoraService.class).setAction(GrabadoraService.PAUSA));
        } else if (k == 2) {
            if (GrabadoraService.grabando) c.startService(new Intent(c, GrabadoraService.class).setAction(GrabadoraService.PARAR));
            parar();
        } else if (k == 3) {
            if (GrabadoraService.grabando) { aviso("Primero pare la grabación"); return; }
            if (mp != null) { if (mp.isPlaying()) mp.pause(); else mp.start(); pintar(); return; }
            String u = ultima(c);
            if (u == null) { aviso("Todavía no hay grabaciones"); return; }
            try {
                mp = new MediaPlayer();
                mp.setDataSource(c, Uri.parse(u));
                mp.setOnCompletionListener(m -> parar());
                mp.prepare();
                mp.start();
            } catch (Exception e) { parar(); aviso("No se pudo oír"); }
        } else {
            if (GrabadoraService.grabando) { aviso("Primero pare la grabación"); return; }
            parar();
            c.startActivity(new Intent(c, RecortarActivity.class));
        }
        h.postDelayed(this::pintar, 300);
    }

    private void parar() {
        if (mp != null) { try { mp.release(); } catch (Exception ignored) {} mp = null; }
    }

    private void aviso(String s) { Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show(); }

    /** La dirección de la última grabación guardada. */
    static String ultima(Context c) {
        String u = null;
        try (BufferedReader r = new BufferedReader(new FileReader(GrabadoraService.indice(c), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) { String[] g = l.split("\t"); if (g.length >= 5 && !g[0].isEmpty()) u = g[0]; }
        } catch (Exception ignored) {}
        return u;
    }
}
