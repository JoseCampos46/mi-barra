package co.jose.mibarra;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Versión 17: el "parlante" de Mi Barra. Toca la radio o una lista de audios,
 * sigue sonando con la pantalla apagada y se maneja desde el aviso de arriba.
 */
public class SonidoService extends Service {
    static final String URL = "co.jose.mibarra.SON_URL", LISTA = "co.jose.mibarra.SON_LISTA", PAUSA = "co.jose.mibarra.SON_PAUSA",
            SIG = "co.jose.mibarra.SON_SIG", ANT = "co.jose.mibarra.SON_ANT", PARAR = "co.jose.mibarra.SON_PARAR",
            DORMIR = "co.jose.mibarra.SON_DORMIR", IR = "co.jose.mibarra.SON_IR";

    // Lo que está sonando (las pantallas lo leen)
    static volatile String titulo = "";
    static volatile boolean activo, pausado, esRadio, cargando;
    static volatile int indice;
    static volatile long apagarA;          // 0 = no se apaga solo
    static volatile boolean azar, repetir = true;
    static MediaPlayer mp;
    static final List<String[]> cola = new ArrayList<>();   // {título, uri}

    private final Handler h = new Handler(Looper.getMainLooper());
    private WifiManager.WifiLock wifi;
    private boolean enPrimerPlano;

    @Override public IBinder onBind(Intent i) { return null; }

    // ---------------- Atajos para las otras pantallas ----------------

    static void radio(Context c, String nombre, String url) {
        c.startForegroundService(new Intent(c, SonidoService.class).setAction(URL).putExtra("titulo", nombre).putExtra("url", url));
    }

    static void lista(Context c, List<String[]> pistas, int desde, boolean alAzar) {
        synchronized (cola) { cola.clear(); cola.addAll(pistas); }
        azar = alAzar;
        c.startForegroundService(new Intent(c, SonidoService.class).setAction(LISTA).putExtra("desde", desde));
    }

    static void accion(Context c, String a) {
        if (!activo && !URL.equals(a) && !LISTA.equals(a)) return;
        try { c.startService(new Intent(c, SonidoService.class).setAction(a)); } catch (Exception ignored) {}
    }

    static void parar(Context c) { accion(c, PARAR); }

    static void dormir(Context c, int minutos) {
        try { c.startService(new Intent(c, SonidoService.class).setAction(DORMIR).putExtra("min", minutos)); } catch (Exception ignored) {}
    }

    /** La primera emisora favorita. */
    static void radioFavorita(Context c) {
        List<String[]> f = leer(new File(c.getFilesDir(), "radio_favoritas.tsv"));
        if (f.isEmpty()) {
            Toast.makeText(c, "Primero escoja sus emisoras en 📻 Radio", Toast.LENGTH_LONG).show();
            try { c.startActivity(new Intent(c, RadioActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
            return;
        }
        radio(c, f.get(0)[0], f.get(0)[1]);
    }

    /** La última lista que escuchó, en desorden. */
    static void miCarpeta(Context c) {
        String n = "";
        try { n = new String(Files.readAllBytes(new File(c.getFilesDir(), "lista_ultima.txt").toPath()), StandardCharsets.UTF_8).trim(); } catch (Exception ignored) {}
        List<String[]> p = n.isEmpty() ? new ArrayList<>() : leer(new File(new File(c.getFilesDir(), "listas"), n + ".tsv"));
        if (p.isEmpty()) {
            Toast.makeText(c, "Primero arme una lista en 🎧 Reproductor", Toast.LENGTH_LONG).show();
            try { c.startActivity(new Intent(c, ReproductorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
            return;
        }
        lista(c, p, -1, true);
    }

    static List<String[]> leer(File f) {
        List<String[]> r = new ArrayList<>();
        try {
            for (String s : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = s.split("\t", -1);
                if (p.length >= 2 && !p[1].isEmpty()) r.add(new String[]{p[0], p[1]});
            }
        } catch (Exception ignored) {}
        return r;
    }

    static void escribir(File f, List<String[]> l) {
        StringBuilder s = new StringBuilder();
        for (String[] x : l) s.append(x[0].replace('\t', ' ').replace('\n', ' ')).append('\t').append(x[1]).append('\n');
        try { f.getParentFile().mkdirs(); Files.write(f.toPath(), s.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }

    // ---------------- El servicio ----------------

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        String a = i == null ? "" : i.getAction();
        primerPlano();
        if (URL.equals(a)) {
            esRadio = true;
            synchronized (cola) { cola.clear(); cola.add(new String[]{i.getStringExtra("titulo"), i.getStringExtra("url")}); }
            tocar(0);
        } else if (LISTA.equals(a)) {
            esRadio = false;
            int n;
            synchronized (cola) { n = cola.size(); }
            if (n == 0) { apagar(); return START_NOT_STICKY; }
            int d = i.getIntExtra("desde", 0);
            if (d < 0) d = azar ? (int) (Math.random() * n) : 0;
            tocar(d);
        } else if (PAUSA.equals(a)) {
            if (mp != null) {
                try {
                    if (pausado) { mp.start(); pausado = false; } else { mp.pause(); pausado = true; }
                } catch (Exception ignored) {}
            }
        } else if (SIG.equals(a)) {
            siguiente(1);
        } else if (ANT.equals(a)) {
            if (mp != null && !esRadio && mp.getCurrentPosition() > 5000) { try { mp.seekTo(0); } catch (Exception ignored) {} }
            else siguiente(-1);
        } else if (IR.equals(a)) {
            if (mp != null && !esRadio) try { mp.seekTo(i.getIntExtra("ms", 0)); } catch (Exception ignored) {}
        } else if (DORMIR.equals(a)) {
            int m = i.getIntExtra("min", 0);
            apagarA = m <= 0 ? 0 : System.currentTimeMillis() + m * 60000L;
            h.removeCallbacks(dormirR);
            if (m > 0) h.postDelayed(dormirR, m * 60000L);
            Toast.makeText(this, m <= 0 ? "⏲ No se apaga solo" : "⏲ Se apaga en " + m + " minutos", Toast.LENGTH_SHORT).show();
        } else if (PARAR.equals(a)) {
            apagar();
            return START_NOT_STICKY;
        }
        avisar();
        return START_NOT_STICKY;
    }

    private final Runnable dormirR = this::apagar;

    private void tocar(int k) {
        String[] p;
        synchronized (cola) {
            if (cola.isEmpty()) { apagar(); return; }
            k = ((k % cola.size()) + cola.size()) % cola.size();
            p = cola.get(k);
        }
        indice = k;
        titulo = p[0] == null ? "" : p[0];
        soltar();
        activo = true;
        pausado = false;
        cargando = true;
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(esRadio ? AudioAttributes.CONTENT_TYPE_MUSIC : AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            mp.setDataSource(this, Uri.parse(p[1]));
            mp.setOnPreparedListener(m -> { cargando = false; m.start(); avisar(); });
            mp.setOnCompletionListener(m -> { if (!esRadio) siguiente(1); });
            mp.setOnErrorListener((m, w, e) -> {
                cargando = false;
                Toast.makeText(this, esRadio ? "📻 Esa emisora no responde ahora" : "No se pudo tocar: " + titulo, Toast.LENGTH_SHORT).show();
                if (!esRadio) h.postDelayed(() -> siguiente(1), 800); else apagar();
                return true;
            });
            mp.prepareAsync();
            if (esRadio) {
                if (wifi == null) {
                    wifi = getSystemService(WifiManager.class).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MiBarra:radio");
                    wifi.setReferenceCounted(false);
                }
                wifi.acquire();
            }
        } catch (Exception e) {
            cargando = false;
            Toast.makeText(this, "No se pudo tocar: " + titulo, Toast.LENGTH_SHORT).show();
        }
        avisar();
    }

    private void siguiente(int paso) {
        int n;
        synchronized (cola) { n = cola.size(); }
        if (n == 0) { apagar(); return; }
        if (esRadio) return;
        int k;
        if (azar && n > 1) { do { k = (int) (Math.random() * n); } while (k == indice); }
        else {
            k = indice + paso;
            if (k >= n && !repetir) { apagar(); return; }
        }
        tocar(k);
    }

    private void primerPlano() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("sonido", "Radio y audios", NotificationManager.IMPORTANCE_LOW));
        Notification n = aviso();
        if (!enPrimerPlano) {
            if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(52, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(52, n);
            enPrimerPlano = true;
        }
    }

    private void avisar() {
        if (!activo) return;
        try { getSystemService(NotificationManager.class).notify(52, aviso()); } catch (Exception ignored) {}
    }

    private PendingIntent pi(String a, int code) {
        return PendingIntent.getService(this, code, new Intent(this, SonidoService.class).setAction(a), PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification aviso() {
        Class<?> pantalla = esRadio ? RadioActivity.class : ReproductorActivity.class;
        PendingIntent abrir = PendingIntent.getActivity(this, 60, new Intent(this, pantalla).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, "sonido")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle((esRadio ? "📻 " : "🎧 ") + (titulo.isEmpty() ? "Mi Barra" : titulo))
                .setContentText(cargando ? "Conectando…" : pausado ? "En pausa" : esRadio ? "Sonando la radio" : "Sonando")
                .setContentIntent(abrir)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (!esRadio) b.addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "⏮", pi(ANT, 61)).build());
        b.addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, pausado ? "▶" : "⏸", pi(PAUSA, 62)).build());
        if (!esRadio) b.addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "⏭", pi(SIG, 63)).build());
        b.addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "⏹", pi(PARAR, 64)).build());
        return b.build();
    }

    private void soltar() {
        if (mp != null) {
            MediaPlayer m = mp;
            mp = null;
            try { m.stop(); } catch (Exception ignored) {}
            try { m.release(); } catch (Exception ignored) {}
        }
        if (wifi != null && wifi.isHeld()) try { wifi.release(); } catch (Exception ignored) {}
    }

    private void apagar() {
        h.removeCallbacks(dormirR);
        apagarA = 0;
        soltar();
        activo = false;
        pausado = false;
        cargando = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        enPrimerPlano = false;
        stopSelf();
    }

    @Override public void onDestroy() { soltar(); activo = false; super.onDestroy(); }

    static void mezclar(List<String[]> l) { Collections.shuffle(l); }
}
