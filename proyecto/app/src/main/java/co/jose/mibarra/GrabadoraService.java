package co.jose.mibarra;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Graba la voz aunque la pantalla esté apagada. Numera sola: Grabación 001, 002…
 * Versión 18: motor propio. Si hay 30 segundos seguidos de silencio se pausa sola,
 * y cuando vuelve a oír voz sigue grabando sin perder la primera palabra (guarda 1 s de antes).
 */
public class GrabadoraService extends Service {
    static final String PARAR = "co.jose.mibarra.GRAB_PARAR";
    static final String PAUSA = "co.jose.mibarra.GRAB_PAUSA";
    static final int SILENCIO_SEG = 30;

    static volatile boolean grabando, pausado, pausaSilencio;
    /** Nivel del micrófono de 0 a 32767, para la barrita. */
    static volatile int nivel;
    static volatile int numero;
    /** Muestras ya guardadas en el archivo (sin contar pausas). */
    private static volatile long muestras;

    private static final int TASA = 44100;

    private ParcelFileDescriptor pfd;
    private Uri uri;
    private String nombreArchivo;
    private Thread hilo;
    private volatile boolean seguir;
    private volatile boolean resultadoOk;
    private final Handler h = new Handler(Looper.getMainLooper());

    static File indice(android.content.Context c) { return new File(c.getFilesDir(), "grabaciones.txt"); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        if (i == null || PARAR.equals(i.getAction())) { parar(); return START_NOT_STICKY; }
        if (PAUSA.equals(i.getAction())) { pausaOSeguir(); return START_NOT_STICKY; }
        if (grabando) return START_NOT_STICKY;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("grabadora", "Reproductor", NotificationManager.IMPORTANCE_LOW));
        numero = getSharedPreferences("barra", MODE_PRIVATE).getInt("numGrab", 0) + 1;
        pausado = false;
        pausaSilencio = false;
        muestras = 0;
        startForeground(31, aviso(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        try {
            String fecha = new java.text.SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(new java.util.Date());
            nombreArchivo = String.format(Locale.US, "Grabacion %03d - %s.m4a", numero, fecha);
            uri = Util.crearEnDescargas(this, nombreArchivo, "audio/mp4");
            pfd = getContentResolver().openFileDescriptor(uri, "rw");
            boolean lejos = i.getBooleanExtra("lejos", false);
            boolean silencio = getSharedPreferences("barra", MODE_PRIVATE).getBoolean("grabSilencio45", false);
            grabando = true;
            seguir = true;
            hilo = new Thread(() -> motor(lejos, silencio), "grabadora");
            hilo.start();
            getSharedPreferences("barra", MODE_PRIVATE).edit().putInt("numGrab", numero).apply();
            vibrar();
            avisarBarra();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo grabar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            limpiar(false);
        }
        return START_NOT_STICKY;
    }

    /** El corazón: lee el micrófono, decide si guarda o no, y lo convierte a AAC (.m4a). */
    private void motor(boolean lejos, boolean pausaPorSilencio) {
        AudioRecord ar = null;
        MediaCodec enc = null;
        MediaMuxer mux = null;
        boolean ok = false;
        try {
            int min = AudioRecord.getMinBufferSize(TASA, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            ar = new AudioRecord(lejos ? MediaRecorder.AudioSource.VOICE_COMMUNICATION : MediaRecorder.AudioSource.MIC,
                    TASA, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192) * 2);
            MediaFormat fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, TASA, 1);
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            enc.start();
            mux = new MediaMuxer(pfd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            ar.startRecording();

            final int N = 2048;                       // ~46 ms por bloque
            short[] pcm = new short[N];
            long umbral = 900;                         // bajo esto se considera silencio
            long bloquesSilencioMax = (long) SILENCIO_SEG * TASA / N;
            long silencioSeguido = 0;
            int vozSeguida = 0;
            ArrayDeque<short[]> antes = new ArrayDeque<>();   // 1 segundo de colchón mientras está pausada
            int maxAntes = TASA / N + 1;
            int[] pista = {-1};
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (seguir) {
                int n = ar.read(pcm, 0, N);
                if (n <= 0) continue;
                int pico = 0;
                for (int k = 0; k < n; k++) { int a = Math.abs(pcm[k]); if (a > pico) pico = a; }
                nivel = pausado ? 0 : pico;

                if (pausado) {
                    if (!pausaSilencio) continue;           // pausa con la mano: espera a que él siga
                    short[] copia = java.util.Arrays.copyOf(pcm, n);
                    antes.addLast(copia);
                    if (antes.size() > maxAntes) antes.removeFirst();
                    if (pico > umbral * 2) vozSeguida++; else vozSeguida = 0;
                    if (vozSeguida >= 3) {                  // volvió la voz: sigue grabando
                        pausado = false;
                        pausaSilencio = false;
                        silencioSeguido = 0;
                        vozSeguida = 0;
                        for (short[] b : antes) meter(enc, mux, pista, info, b, b.length);
                        antes.clear();
                        h.post(this::refrescarAviso);
                        avisarBarra();
                    }
                    continue;
                }

                meter(enc, mux, pista, info, pcm, n);

                if (pausaPorSilencio) {
                    if (pico < umbral) silencioSeguido++; else silencioSeguido = 0;
                    if (silencioSeguido >= bloquesSilencioMax) {
                        pausado = true;
                        pausaSilencio = true;
                        antes.clear();
                        h.post(this::refrescarAviso);
                        avisarBarra();
                    }
                }
            }
            // Fin: cerrar el archivo bien para que se pueda oír
            int in = enc.dequeueInputBuffer(10000);
            if (in >= 0) enc.queueInputBuffer(in, 0, 0, tiempoUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            sacar(enc, mux, pista, info, true);
            ok = pista[0] >= 0 && muestras > TASA / 2;
        } catch (Exception e) {
            h.post(() -> Toast.makeText(this, "Falla en la grabadora: " + e.getMessage(), Toast.LENGTH_LONG).show());
        } finally {
            try { if (ar != null) { ar.stop(); ar.release(); } } catch (Exception ignored) {}
            try { if (enc != null) { enc.stop(); enc.release(); } } catch (Exception ignored) {}
            try { if (mux != null) { mux.stop(); mux.release(); } } catch (Exception e) { ok = false; }
        }
        resultadoOk = ok;
    }

    private static long tiempoUs() { return muestras * 1_000_000L / TASA; }

    private void meter(MediaCodec enc, MediaMuxer mux, int[] pista, MediaCodec.BufferInfo info, short[] pcm, int n) {
        int hecho = 0;
        while (hecho < n) {
            int in = enc.dequeueInputBuffer(10000);
            if (in < 0) { sacar(enc, mux, pista, info, false); continue; }
            ByteBuffer b = enc.getInputBuffer(in);
            b.clear();
            b.order(java.nio.ByteOrder.LITTLE_ENDIAN);   // el audio PCM va al revés de lo normal en Java
            int cabe = Math.min(n - hecho, b.remaining() / 2);
            for (int k = 0; k < cabe; k++) b.putShort(pcm[hecho + k]);
            enc.queueInputBuffer(in, 0, cabe * 2, tiempoUs(), 0);
            muestras += cabe;
            hecho += cabe;
            sacar(enc, mux, pista, info, false);
        }
    }

    private void sacar(MediaCodec enc, MediaMuxer mux, int[] pista, MediaCodec.BufferInfo info, boolean hastaElFin) {
        while (true) {
            int out = enc.dequeueOutputBuffer(info, hastaElFin ? 10000 : 0);
            if (out == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!hastaElFin) return; else continue; }
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                pista[0] = mux.addTrack(enc.getOutputFormat());
                mux.start();
                continue;
            }
            if (out < 0) continue;
            ByteBuffer d = enc.getOutputBuffer(out);
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
            if (info.size > 0 && pista[0] >= 0) {
                d.position(info.offset);
                d.limit(info.offset + info.size);
                mux.writeSampleData(pista[0], d, info);
            }
            enc.releaseOutputBuffer(out, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
        }
    }

    private void parar() {
        if (!grabando) { limpiar(false); return; }
        long dur = segundos() * 1000;
        seguir = false;
        new Thread(() -> {
            try { if (hilo != null) hilo.join(8000); } catch (InterruptedException ignored) {}
            boolean ok = resultadoOk;
            h.post(() -> {
                if (ok) {
                    try (FileWriter w = new FileWriter(indice(this), StandardCharsets.UTF_8, true)) {
                        w.write(uri + "\t" + numero + "\t\t" + System.currentTimeMillis() + "\t" + dur + "\t" + nombreArchivo + "\n");
                    } catch (Exception ignored) {}
                    Toast.makeText(this, String.format(Locale.US, "💾 Grabación %03d guardada en Descargas/MiBarra/Grabadora", numero),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "La grabación quedó vacía y no se guardó", Toast.LENGTH_LONG).show();
                }
                limpiar(ok);
            });
        }).start();
    }

    /** Tiempo grabado sin contar las pausas. */
    static long segundos() {
        if (!grabando) return 0;
        return muestras / TASA;
    }

    private void pausaOSeguir() {
        if (!grabando) return;
        pausado = !pausado;
        pausaSilencio = false;
        refrescarAviso();
        avisarBarra();
    }

    /** El botón flotante vive en otro proceso: se le avisa por mensaje cada segundo mientras graba. */
    private final Runnable latido = new Runnable() {
        @Override public void run() { avisarBarra(); if (grabando) h.postDelayed(this, 1000); }
    };

    private void avisarBarra() {
        int est = !grabando ? 0 : !pausado ? 1 : pausaSilencio ? 2 : 3;
        try {
            sendBroadcast(new Intent(BarraService.AVISO_BARRA).setPackage(getPackageName())
                    .putExtra("grabEstado", est).putExtra("grabSeg", segundos()));
        } catch (Exception ignored) {}
        if (est != 0 && Looper.myLooper() == Looper.getMainLooper()) { h.removeCallbacks(latido); h.postDelayed(latido, 1000); }
    }

    private void refrescarAviso() {
        try { getSystemService(NotificationManager.class).notify(31, aviso()); } catch (Exception ignored) {}
    }

    private void vibrar() {
        try {
            android.os.Vibrator v = getSystemService(android.os.Vibrator.class);
            if (v != null) v.vibrate(android.os.VibrationEffect.createOneShot(250, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {}
    }

    /** Aviso de arriba con cara de reproductor de música: ⏸/▶ y ⏹. */
    private Notification aviso() {
        PendingIntent abrir = PendingIntent.getActivity(this, 0, new Intent(this, GrabadoraActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent parar = PendingIntent.getService(this, 1, new Intent(this, GrabadoraService.class).setAction(PARAR),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pausa = PendingIntent.getService(this, 2, new Intent(this, GrabadoraService.class).setAction(PAUSA),
                PendingIntent.FLAG_IMMUTABLE);
        String texto = !pausado ? "Grabando" : pausaSilencio ? "En pausa por silencio · sigue sola al oír voz" : "En pausa";
        Notification.Builder b = new Notification.Builder(this, "grabadora")
                .setContentTitle(String.format(Locale.US, "♪ Pista %03d", numero))
                .setContentText(texto)
                .setSmallIcon(pausado ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play)
                .setContentIntent(abrir)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, pausado ? "▶" : "⏸", pausa).build())
                .addAction(new Notification.Action.Builder(null, "⏹", parar).build())
                .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1))
                .setOngoing(true);
        if (grabando && !pausado) {
            b.setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis() - segundos() * 1000);
        }
        return b.build();
    }

    private void limpiar(boolean ok) {
        grabando = false;
        pausado = false;
        pausaSilencio = false;
        nivel = 0;
        if (pfd != null) { try { pfd.close(); } catch (Exception ignored) {} pfd = null; }
        if (uri != null) {
            try { if (ok) Util.terminar(this, uri); else getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
            uri = null;
        }
        avisarBarra();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
}
