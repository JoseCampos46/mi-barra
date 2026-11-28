package co.jose.mibarra;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Versión 22: recortar una grabación sin otra app. Se mueven las dos rayas amarillas y se guarda el pedazo. */
public class RecortarActivity extends Base {

    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<String[]> grabs = new ArrayList<>();
    private String[] actual;
    private long durMs, ini, fin;
    private Corte corte;
    private TextView tInfo, tTiempos;
    private MediaPlayer mp;
    private Uri ultimoRecorte;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        leer();
        if (grabs.isEmpty()) { aviso("Todavía no hay grabaciones"); finish(); return; }
        escoger(grabs.get(0));
    }

    @Override protected void onDestroy() { pararMp(); super.onDestroy(); }

    private void leer() {
        grabs.clear();
        try (BufferedReader r = new BufferedReader(new FileReader(GrabadoraService.indice(this), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) { String[] g = l.split("\t", -1); if (g.length >= 5 && !g[0].isEmpty()) grabs.add(0, g); }
        } catch (Exception ignored) {}
    }

    private void escoger(String[] g) {
        pararMp();
        actual = g;
        ultimoRecorte = null;
        durMs = 0;
        try {
            MediaMetadataRetriever mr = new MediaMetadataRetriever();
            mr.setDataSource(this, Uri.parse(g[0]));
            String d = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            mr.release();
            durMs = d == null ? 0 : Long.parseLong(d);
        } catch (Exception e) { try { durMs = Long.parseLong(g[4]); } catch (Exception ignored) {} }
        if (durMs <= 0) durMs = 1000;
        ini = 0;
        fin = durMs;
        pantalla();
    }

    private String nombre(String[] g) {
        String n = g.length > 2 && !g[2].isEmpty() ? g[2] : g.length > 5 ? g[5].replace(".m4a", "") : "Grabación " + g[1];
        return n;
    }

    private static String mmss(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format(Locale.US, "%02d", s % 60);
    }

    private void pantalla() {
        LinearLayout raiz = vertical();
        raiz.setBackgroundColor(0xFF1B2140);
        raiz.setPadding(dp(14), dp(10), dp(14), dp(20));
        TextView t = titulo("✂ Recortar grabación");
        t.setTextColor(Color.WHITE);
        raiz.addView(t);
        tInfo = new TextView(this);
        tInfo.setTextColor(0xFFCFD8DC);
        tInfo.setTextSize(14);
        tInfo.setText(nombre(actual) + " · " + mmss(durMs));
        raiz.addView(tInfo);

        corte = new Corte(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x26FFFFFF);
        g.setCornerRadius(dp(16));
        corte.setBackground(g);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(120));
        cp.topMargin = dp(12);
        raiz.addView(corte, cp);
        tTiempos = new TextView(this);
        tTiempos.setTextColor(Color.WHITE);
        tTiempos.setTextSize(15);
        tTiempos.setPadding(0, dp(6), 0, 0);
        raiz.addView(tTiempos);
        TextView ay = new TextView(this);
        ay.setText("Mueva las rayas amarillas con el dedo para escoger el pedazo que se queda.");
        ay.setTextColor(0xFFCFD8DC);
        ay.setTextSize(14);
        ay.setPadding(0, dp(6), 0, dp(6));
        raiz.addView(ay);

        LinearLayout f = horizontal();
        Button oir = boton("▶ Escuchar pedazo", 0xFF2E7D32);
        oir.setOnClickListener(v -> escucharPedazo(oir));
        Button des = boton("↺ Deshacer", 0xFF546E7A);
        des.setOnClickListener(v -> { ini = 0; fin = durMs; corte.invalidate(); pintarTiempos(); });
        f.addView(oir, peso(1.4f));
        f.addView(des, peso(1));
        raiz.addView(f, ancho(54));
        Button rec = boton("✂ Recortar y guardar en Descargas/MiBarra/Grabadora", 0xFF6A1B9A);
        rec.setOnClickListener(v -> recortar());
        raiz.addView(rec, ancho(58));
        Button wa = boton("💬 Mandar por WhatsApp", 0xFF25A244);
        wa.setOnClickListener(v -> enviar(WHATSAPP, "audio/mp4", ultimoRecorte != null ? ultimoRecorte : Uri.parse(actual[0])));
        raiz.addView(wa, ancho(54));

        TextView tl = titulo("📂 Mis grabaciones");
        tl.setTextColor(Color.WHITE);
        tl.setPadding(0, dp(18), 0, dp(4));
        raiz.addView(tl);
        SimpleDateFormat df = new SimpleDateFormat("dd/MM h:mm a", new Locale("es", "CO"));
        for (int i = 0; i < Math.min(30, grabs.size()); i++) {
            String[] gr = grabs.get(i);
            String cuando = "";
            try { cuando = df.format(new Date(Long.parseLong(gr[3]))); } catch (Exception ignored) {}
            long d = 0;
            try { d = Long.parseLong(gr[4]); } catch (Exception ignored) {}
            Button x = boton((gr == actual ? "✔ " : "🎙 ") + nombre(gr) + "\n" + cuando + " · " + mmss(d), gr == actual ? 0xFF3949AB : 0xFF263055);
            x.setTextSize(14);
            x.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            x.setOnClickListener(v -> escoger(gr));
            LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(-1, -2);
            xp.topMargin = dp(6);
            raiz.addView(x, xp);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(raiz);
        sv.setBackgroundColor(0xFF1B2140);
        setContentView(sv);
        pintarTiempos();
    }

    private void pintarTiempos() {
        tTiempos.setText("Desde " + mmss(ini) + "     ·     Queda " + mmss(fin - ini) + "     ·     Hasta " + mmss(fin));
    }

    private void pararMp() {
        h.removeCallbacksAndMessages(null);
        if (mp != null) { try { mp.release(); } catch (Exception ignored) {} mp = null; }
    }

    private void escucharPedazo(Button b) {
        if (mp != null) { pararMp(); b.setText("▶ Escuchar pedazo"); return; }
        try {
            mp = new MediaPlayer();
            mp.setDataSource(this, Uri.parse(actual[0]));
            mp.prepare();
            mp.seekTo((int) ini);
            mp.start();
            b.setText("⏹ Parar");
            h.post(new Runnable() {
                @Override public void run() {
                    if (mp == null) return;
                    if (mp.getCurrentPosition() >= fin || !mp.isPlaying()) { pararMp(); b.setText("▶ Escuchar pedazo"); return; }
                    corte.posicion = mp.getCurrentPosition();
                    corte.invalidate();
                    h.postDelayed(this, 100);
                }
            });
        } catch (Exception e) { pararMp(); aviso("No se pudo oír"); }
    }

    private void recortar() {
        if (fin - ini < 1000) { aviso("El pedazo es muy corto"); return; }
        pararMp();
        aviso("✂ Recortando…");
        final String[] g = actual;
        final long a = ini, z = fin;
        new Thread(() -> {
            String nom = nombre(g) + " (recorte " + mmss(a).replace(':', '.') + "-" + mmss(z).replace(':', '.') + ").m4a";
            Uri out = null;
            try {
                MediaExtractor ex = new MediaExtractor();
                ex.setDataSource(this, Uri.parse(g[0]), null);
                int pista = -1;
                MediaFormat fmt = null;
                for (int i = 0; i < ex.getTrackCount(); i++) {
                    MediaFormat f = ex.getTrackFormat(i);
                    String m = f.getString(MediaFormat.KEY_MIME);
                    if (m != null && m.startsWith("audio/")) { pista = i; fmt = f; break; }
                }
                if (pista < 0) throw new Exception("sin audio");
                ex.selectTrack(pista);
                out = Util.crearEnDescargas(this, nom, "audio/mp4");
                if (out == null) throw new Exception("no se pudo crear el archivo");
                try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(out, "rw")) {
                    MediaMuxer mx = new MediaMuxer(pfd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    int t = mx.addTrack(fmt);
                    mx.start();
                    ex.seekTo(a * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    ByteBuffer buf = ByteBuffer.allocate(1 << 20);
                    MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                    long base = -1;
                    while (true) {
                        int n = ex.readSampleData(buf, 0);
                        if (n < 0) break;
                        long pt = ex.getSampleTime();
                        if (pt > z * 1000) break;
                        if (base < 0) base = pt;
                        bi.set(0, n, pt - base, (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                        mx.writeSampleData(t, buf, bi);
                        ex.advance();
                    }
                    mx.stop();
                    mx.release();
                }
                ex.release();
                Util.terminar(this, out);
                try (FileWriter w = new FileWriter(GrabadoraService.indice(this), StandardCharsets.UTF_8, true)) {
                    w.write(out + "\t" + g[1] + "\t" + nom.replace(".m4a", "") + "\t" + System.currentTimeMillis() + "\t" + (z - a) + "\t" + nom + "\n");
                } catch (Exception ignored) {}
                final Uri listo = out;
                runOnUiThread(() -> {
                    ultimoRecorte = listo;
                    aviso("✔ Recorte guardado en Descargas/MiBarra/Grabadora");
                    leer();
                    for (String[] x : grabs) if (x[0].equals(listo.toString())) { escoger(x); break; }
                    ultimoRecorte = listo;
                });
            } catch (Exception e) {
                if (out != null) try { getContentResolver().delete(out, null, null); } catch (Exception ignored) {}
                runOnUiThread(() -> aviso("No se pudo recortar: " + e.getMessage()));
            }
        }).start();
    }

    /** La barra del tiempo con las dos rayas amarillas que se arrastran. */
    final class Corte extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        long posicion = -1;
        private int agarrada = -1;

        Corte(Context c) { super(c); }

        private float x(long ms) { float m = dp(16); return m + (getWidth() - 2 * m) * ms / (float) durMs; }

        private long ms(float x) {
            float m = dp(16);
            return Math.max(0, Math.min(durMs, (long) ((x - m) / (getWidth() - 2 * m) * durMs)));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), m = dp(16);
            int barras = 60;
            float paso = (w - 2 * m) / barras;
            for (int i = 0; i < barras; i++) {
                long t = durMs * i / barras;
                float alto = hh * (0.25f + 0.45f * (float) Math.abs(Math.sin(i * 0.9 + (actual[0].hashCode() % 7)) * Math.cos(i * 0.31)));
                p.setColor(t >= ini && t <= fin ? 0xFFFF8A65 : 0x55FFFFFF);
                float xx = m + i * paso;
                c.drawRoundRect(xx, (hh - alto) / 2, xx + paso * 0.6f, (hh + alto) / 2, dp(2), dp(2), p);
            }
            p.setColor(0xFFFFD54F);
            for (long t : new long[]{ini, fin}) {
                float xx = x(t);
                c.drawRect(xx - dp(2), dp(6), xx + dp(2), hh - dp(6), p);
                c.drawCircle(xx, hh - dp(10), dp(9), p);
            }
            if (posicion >= 0) {
                p.setColor(Color.WHITE);
                float xx = x(posicion);
                c.drawRect(xx - dp(1), dp(10), xx + dp(1), hh - dp(10), p);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float xx = e.getX();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    agarrada = Math.abs(xx - x(ini)) <= Math.abs(xx - x(fin)) ? 0 : 1;
                    getParent().requestDisallowInterceptTouchEvent(true);
                case MotionEvent.ACTION_MOVE:
                    long t = ms(xx);
                    if (agarrada == 0) ini = Math.min(t, fin - 500);
                    else if (agarrada == 1) fin = Math.max(t, ini + 500);
                    ini = Math.max(0, ini);
                    fin = Math.min(durMs, fin);
                    invalidate();
                    pintarTiempos();
                    return true;
                default:
                    agarrada = -1;
                    return true;
            }
        }
    }
}
