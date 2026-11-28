package co.jose.mibarra;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Sigue contando con la pantalla apagada:
 *  - Temporizador: al terminar dice "se acabó el tiempo" y suena (alarma, canción o radio) hasta que lo apaguen o pospongan.
 *  - Cronómetro: dice en voz cuánto va, cada 5 minutos, hasta que le digan "Pare".
 *  - Radio.
 */
public class RelojService extends Service {
    static final String INICIAR = "co.jose.mibarra.RELOJ_INICIAR", PARAR = "co.jose.mibarra.RELOJ_PARAR",
            RADIO = "co.jose.mibarra.RELOJ_RADIO", POSPONER = "co.jose.mibarra.RELOJ_POSPONER",
            CRONO = "co.jose.mibarra.RELOJ_CRONO", REVISAR = "co.jose.mibarra.RELOJ_REVISAR",
            RECORDAR = "co.jose.mibarra.RELOJ_RECORDAR";
    static volatile boolean esRecordatorio;
    static volatile long fin;                // temporizador: cuándo termina (0 = no hay)
    static volatile boolean sonando, radio;
    static volatile String nombre = "";
    static volatile long cInicio;            // cronómetro: cuándo arrancó (0 = parado)
    private long ultimoMinutoDicho;

    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private TextToSpeech voz;
    private PowerManager.WakeLock wl;
    private boolean enPrimerPlano;

    @Override public IBinder onBind(Intent i) { return null; }

    static boolean cronoCorriendo() { return cInicio > 0; }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        String a = i == null ? REVISAR : i.getAction();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("reloj", "Reloj de cocina", NotificationManager.IMPORTANCE_HIGH));
        if (voz == null) voz = new TextToSpeech(this, s -> { if (s == TextToSpeech.SUCCESS) voz.setLanguage(new Locale("es", "CO")); });
        if (PARAR.equals(a)) {                       // apaga la alarma / cancela el temporizador / apaga la radio
            soltarSonido();
            esRecordatorio = false;
            fin = 0; sonando = false; radio = false;
            h.removeCallbacks(callarSola);
        } else if (RECORDAR.equals(a)) {
            soltarSonido();
            h.removeCallbacks(callarSola);
            radio = false;
            esRecordatorio = true;
            nombre = i.getStringExtra("texto") == null ? "Recordatorio" : i.getStringExtra("texto");
            sonar();
            actualizar();
            return START_NOT_STICKY;
        } else if (INICIAR.equals(a) || POSPONER.equals(a)) {
            soltarSonido();
            sonando = false; radio = false;
            h.removeCallbacks(callarSola);
            fin = System.currentTimeMillis() + i.getLongExtra("ms", 5 * 60000);
            if (INICIAR.equals(a)) { nombre = i.getStringExtra("nombre") == null ? "" : i.getStringExtra("nombre"); esRecordatorio = false; }
            if (POSPONER.equals(a)) decir("Listo, " + (i.getLongExtra("ms", 0) / 60000) + " minutos más");
        } else if (RADIO.equals(a)) {
            soltarSonido();
            fin = 0; sonando = false; radio = true;
            tocar(Uri.parse(i.getStringExtra("url")), false);
        } else if (CRONO.equals(a)) {
            ultimoMinutoDicho = (System.currentTimeMillis() - cInicio) / 60000;
        }
        actualizar();
        return START_NOT_STICKY;
    }

    /** Pone o quita el aviso de arriba según lo que esté activo, y se apaga si no queda nada. */
    private void actualizar() {
        boolean algo = fin > 0 || sonando || radio || cronoCorriendo();
        if (!algo) { apagar(); return; }
        Notification n = aviso();
        if (!enPrimerPlano) { startForeground(41, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE); enPrimerPlano = true; }
        else getSystemService(NotificationManager.class).notify(41, n);
        if (wl == null) {
            wl = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MiBarra:reloj");
            wl.setReferenceCounted(false);
        }
        if (fin > 0 || cronoCorriendo()) wl.acquire(3 * 60 * 60000L);
        h.removeCallbacks(tic);
        h.post(tic);
    }

    private final Runnable tic = new Runnable() {
        @Override public void run() {
            long ahora = System.currentTimeMillis();
            if (fin > 0 && ahora >= fin) { sonar(); }
            if (cronoCorriendo()) {
                long min = (ahora - cInicio) / 60000;
                if (min > 0 && min % 5 == 0 && min != ultimoMinutoDicho) {
                    ultimoMinutoDicho = min;
                    decir("Van " + min + " minutos");
                }
            }
            if (fin > 0 || cronoCorriendo()) h.postDelayed(this, 1000);
        }
    };

    private final Runnable callarSola = () -> { soltarSonido(); sonando = false; actualizar(); };

    private void sonar() {
        fin = 0;
        sonando = true;
        getSystemService(NotificationManager.class).notify(41, aviso());
        if (esRecordatorio) {
            decir("Recordatorio. " + nombre);
            h.postDelayed(() -> { if (sonando) { soltarSonido(); decir("Le recuerdo: " + nombre); h.postDelayed(this::tocarAlarma, 6000); } }, 60000);
        } else decir("Se acabó el tiempo" + (nombre.isEmpty() ? "" : " de " + nombre));
        android.content.SharedPreferences p = getSharedPreferences("barra", MODE_PRIVATE);
        String cancion = p.getString("relojCancion", ""), url = p.getString("relojRadio", "");
        int cual = esRecordatorio ? 0 : p.getInt("relojSonido", 0); // 0 alarma del celular, 1 canción, 2 radio
        Uri u = cual == 1 && !cancion.isEmpty() ? Uri.parse(cancion) : cual == 2 && !url.isEmpty() ? Uri.parse(url)
                : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        final Uri fu = u;
        h.postDelayed(() -> { if (sonando) tocar(fu, cual != 2); }, 1800);
        h.postDelayed(callarSola, 15 * 60000); // si nadie la apaga, se calla sola a los 15 minutos
    }

    private void decir(String s) { try { voz.speak(s, TextToSpeech.QUEUE_FLUSH, null, "r"); } catch (Exception ignored) {} }

    private void tocar(Uri u, boolean repetir) {
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(radio ? AudioAttributes.USAGE_MEDIA : AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setDataSource(this, u);
            mp.setLooping(repetir);
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnErrorListener((m, w, e) -> { if (!radio) tocarAlarma(); return true; });
            mp.prepareAsync();
        } catch (Exception e) {
            if (!radio) tocarAlarma();
        }
    }

    private void tocarAlarma() {
        soltarSonido();
        try {
            mp = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (mp != null) { mp.setLooping(true); mp.start(); }
        } catch (Exception ignored) {}
    }

    private PendingIntent servicio(int cod, String accion, long ms) {
        return PendingIntent.getService(this, cod, new Intent(this, RelojService.class).setAction(accion).putExtra("ms", ms),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification aviso() {
        PendingIntent abrir = PendingIntent.getActivity(this, 0, new Intent(this, RelojActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, "reloj")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentIntent(abrir).setOngoing(true);
        if (sonando) {
            b.setContentTitle(esRecordatorio ? "📅 " + nombre : "⏰ ¡Se acabó el tiempo!" + (nombre.isEmpty() ? "" : " · " + nombre))
                    .setContentText(esRecordatorio ? "Recordatorio · apague o recuérdeme en 5 minutos" : "Apague o duerma 5 minutos más")
                    .setFullScreenIntent(abrir, true).setCategory(Notification.CATEGORY_ALARM)
                    .addAction(new Notification.Action.Builder(null, "⏹ Apagar", servicio(1, PARAR, 0)).build())
                    .addAction(new Notification.Action.Builder(null, "😴 +5 min", servicio(2, POSPONER, 5 * 60000)).build())
                    .addAction(new Notification.Action.Builder(null, "+10 min", servicio(3, POSPONER, 10 * 60000)).build());
        } else if (fin > 0) {
            b.setContentTitle("⏳ Temporizador" + (nombre.isEmpty() ? "" : " · " + nombre))
                    .setUsesChronometer(true).setChronometerCountDown(true).setWhen(fin).setShowWhen(true)
                    .setContentText(cronoCorriendo() ? "⏱ El cronómetro también va corriendo" : "Sigue contando aunque apague la pantalla")
                    .addAction(new Notification.Action.Builder(null, "✖ Cancelar", servicio(1, PARAR, 0)).build());
        } else if (radio) {
            b.setContentTitle("📻 Radio sonando").setContentText("Toque Apagar para quitarla")
                    .addAction(new Notification.Action.Builder(null, "⏹ Apagar radio", servicio(1, PARAR, 0)).build());
        } else {
            b.setContentTitle("⏱ Cronómetro corriendo").setUsesChronometer(true).setWhen(cInicio).setShowWhen(true)
                    .setContentText("Le digo el tiempo cada 5 minutos. Toque para pararlo.");
        }
        return b.build();
    }

    private void soltarSonido() {
        if (mp != null) { try { mp.stop(); } catch (Exception ignored) {} try { mp.release(); } catch (Exception ignored) {} mp = null; }
    }

    private void apagar() {
        h.removeCallbacksAndMessages(null);
        soltarSonido();
        if (wl != null && wl.isHeld()) wl.release();
        if (voz != null) { voz.shutdown(); voz = null; }
        enPrimerPlano = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() { soltarSonido(); if (wl != null && wl.isHeld()) wl.release(); super.onDestroy(); }
}
