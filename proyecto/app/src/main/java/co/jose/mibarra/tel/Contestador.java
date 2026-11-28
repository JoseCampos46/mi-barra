package co.jose.mibarra.tel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.telecom.Call;

import java.io.File;
import java.util.Locale;

/**
 * Versión 37: contestador propio. Contesta, pone el saludo (su voz grabada o la voz automática) por el altavoz,
 * hace el pito y graba el mensaje en Descargas/MiBarra/Contestador.
 * Ojo: Android no deja meter sonido directo en la llamada; el saludo sale por el altavoz y el micrófono lo pasa.
 */
final class Contestador {

    static final String FRASE = "Hola. En este momento no puedo contestar. Deje su mensaje después del tono.";

    private static final Handler h = new Handler(Looper.getMainLooper());
    private static MediaPlayer mp;
    private static TextToSpeech tts;
    private static ToneGenerator tg;
    private static Call actual;
    private static boolean pitado;
    private static String quien = "";
    private static int volViejo = -1;

    private Contestador() {}

    static File saludo(Context c) {
        return new File(c.getFilesDir(), "saludo.m4a");
    }

    static boolean atendiendo(Call k) {
        return k != null && actual == k;
    }

    /** true cuando ya sonó el pito y se está grabando el mensaje. */
    static boolean grabandoMensaje(Call k) {
        return atendiendo(k) && pitado;
    }

    static AudioAttributes atributos() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    /** Se llama cuando la llamada que mandamos al contestador ya quedó contestada. */
    static void empezar(LlamadaService s, Call k) {
        if (actual == k) return;
        actual = k;
        pitado = false;
        String num = LlamadaService.numero(k);
        String nom = Reglas.nombreContacto(s, num);
        quien = nom != null ? nom : (num.isEmpty() ? "Privado" : num);
        s.altavoz();
        try {
            AudioManager am = s.getSystemService(AudioManager.class);
            volViejo = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0);
        } catch (Exception ignored) {}
        h.postDelayed(() -> { if (actual == k) decirSaludo(s, k); }, 1200);
    }

    private static void decirSaludo(LlamadaService s, Call k) {
        File f = saludo(s);
        if (f.exists() && f.length() > 1000) {
            try {
                MediaPlayer m = new MediaPlayer();
                m.setAudioAttributes(atributos());
                m.setDataSource(f.getAbsolutePath());
                m.setOnCompletionListener(x -> pito(s, k));
                m.prepare();
                m.start();
                mp = m;
                return;
            } catch (Exception e) {
                soltarReproductor();
            }
        }
        tts = new TextToSpeech(s.getApplicationContext(), estado -> {
            TextToSpeech t = tts;
            if (estado != TextToSpeech.SUCCESS || t == null) {
                h.post(() -> pito(s, k));
                return;
            }
            try {
                t.setLanguage(new Locale("es", "CO"));
                t.setAudioAttributes(atributos());
                t.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) { h.post(() -> pito(s, k)); }
                    @Override public void onError(String id) { h.post(() -> pito(s, k)); }
                });
                t.speak(FRASE, TextToSpeech.QUEUE_FLUSH, null, "saludo");
            } catch (Exception e) {
                h.post(() -> pito(s, k));
            }
        });
    }

    private static void pito(LlamadaService s, Call k) {
        if (actual != k || pitado) return;
        pitado = true;
        soltarReproductor();
        soltarVoz();
        try {
            tg = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 700);
        } catch (Exception ignored) {}
        h.postDelayed(() -> {
            if (actual != k) return;
            soltarPito();
            Grabador.empezar(s, "Mensaje " + quien, "Contestador");
            s.avisarCambio();
            int max = Reglas.p(s).getInt("maxMensaje", 60);
            h.postDelayed(() -> {
                if (actual == k) {
                    try { k.disconnect(); } catch (Exception ignored) {}
                }
            }, max * 1000L);
        }, 900);
    }

    /** La llamada se acabó: guarda el mensaje y avisa. */
    static void terminar(Context c) {
        if (actual == null) return;
        actual = null;
        pitado = false;
        soltarReproductor();
        soltarVoz();
        soltarPito();
        restaurarVolumen(c);
        String n = Grabador.parar(c);
        if (n != null) avisoMensaje(c, quien);
    }

    /** Usted decidió tomar la llamada: se guarda lo que alcanzó a dejar y sigue la llamada normal. */
    static void tomar(Context c) {
        terminar(c);
    }

    private static void restaurarVolumen(Context c) {
        if (volViejo < 0) return;
        try {
            c.getSystemService(AudioManager.class).setStreamVolume(AudioManager.STREAM_VOICE_CALL, volViejo, 0);
        } catch (Exception ignored) {}
        volViejo = -1;
    }

    private static void soltarReproductor() {
        MediaPlayer m = mp;
        mp = null;
        if (m != null) {
            try { m.stop(); } catch (Exception ignored) {}
            try { m.release(); } catch (Exception ignored) {}
        }
    }

    private static void soltarVoz() {
        TextToSpeech t = tts;
        tts = null;
        if (t != null) {
            try { t.stop(); } catch (Exception ignored) {}
            try { t.shutdown(); } catch (Exception ignored) {}
        }
    }

    private static void soltarPito() {
        ToneGenerator t = tg;
        tg = null;
        if (t != null) {
            try { t.release(); } catch (Exception ignored) {}
        }
    }

    private static void avisoMensaje(Context c, String deQuien) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("contestador", "Mensajes del contestador", NotificationManager.IMPORTANCE_DEFAULT));
            Intent i = new Intent(c, MarcadorActivity.class).putExtra("pestana", 3)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, 40, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, "contestador")
                    .setSmallIcon(android.R.drawable.sym_call_missed)
                    .setContentTitle("📼 Mensaje de " + deQuien)
                    .setContentText("Tóquelo para oírlo en Mi Teléfono → Grabadas")
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build();
            nm.notify((int) (System.currentTimeMillis() % 100000) + 1000, n);
        } catch (Exception ignored) {}
    }
}
