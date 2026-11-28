package co.jose.mibarra;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

/** Graba la pantalla a un video MP4 liviano en Descargas. */
public class GrabarService extends Service {
    static final String PARAR = "co.jose.mibarra.PARAR";
    static volatile boolean grabando;

    private MediaProjection mp;
    private MediaRecorder mr;
    private VirtualDisplay vd;
    private ParcelFileDescriptor pfd;
    private Uri uri;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        if (i == null || PARAR.equals(i.getAction())) {
            parar();
            return START_NOT_STICKY;
        }
        if (grabando) return START_NOT_STICKY;

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("grabar", "Grabación de pantalla",
                NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi = PendingIntent.getService(this, 0,
                new Intent(this, GrabarService.class).setAction(PARAR), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "grabar")
                .setContentTitle("🎥 Grabando la pantalla")
                .setContentText("Toque aquí para parar y guardar")
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);

        try {
            int codigo = i.getIntExtra("codigo", 0);
            Intent datos = i.getParcelableExtra("datos");
            MediaProjectionManager m = getSystemService(MediaProjectionManager.class);
            mp = m.getMediaProjection(codigo, datos);
            mp.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { parar(); }
            }, new Handler(Looper.getMainLooper()));

            DisplayMetrics dm = new DisplayMetrics();
            ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
            float f = Math.min(1f, 1280f / Math.max(dm.widthPixels, dm.heightPixels));
            int w = ((int) (dm.widthPixels * f)) / 2 * 2;
            int alto = ((int) (dm.heightPixels * f)) / 2 * 2;

            uri = Util.crearEnDescargas(this, "Grabacion_" + Util.fecha() + ".mp4", "video/mp4");
            pfd = getContentResolver().openFileDescriptor(uri, "rw");

            boolean audio = i.getBooleanExtra("audio", false)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            mr = new MediaRecorder();
            if (audio) mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            if (audio) {
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mr.setAudioEncodingBitRate(96000);
                mr.setAudioSamplingRate(44100);
            }
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mr.setVideoSize(w, alto);
            mr.setVideoFrameRate(30);
            mr.setVideoEncodingBitRate(2_500_000);
            mr.setOutputFile(pfd.getFileDescriptor());
            mr.prepare();
            vd = mp.createVirtualDisplay("MiBarra", w, alto, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mr.getSurface(), null, null);
            // Cuenta 3, 2, 1 para que alcance a acomodarse en la app que va a grabar
            Runnable empezar = () -> {
                if (mr == null) return;
                try {
                    mr.start();
                    grabando = true;
                    if (BarraService.instancia != null) BarraService.instancia.mostrarPararGrabacion(true);
                    // Versión 18: vibra para saber que SÍ empezó, y el tiempo corre en la barra de arriba
                    try {
                        android.os.Vibrator vb = getSystemService(android.os.Vibrator.class);
                        if (vb != null) vb.vibrate(android.os.VibrationEffect.createOneShot(250, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } catch (Exception ignored) {}
                    try {
                        getSystemService(NotificationManager.class).notify(7, new Notification.Builder(this, "grabar")
                                .setContentTitle("🔴 Grabando la pantalla")
                                .setContentText("Toque aquí para parar y guardar")
                                .setSmallIcon(android.R.drawable.presence_video_online)
                                .setContentIntent(PendingIntent.getService(this, 0,
                                        new Intent(this, GrabarService.class).setAction(PARAR), PendingIntent.FLAG_IMMUTABLE))
                                .setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis())
                                .setOngoing(true).build());
                    } catch (Exception ignored) {}
                    Toast.makeText(this, "🎥🔇 Grabando. Para parar: baje la barra de arriba y toque «Grabando»", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "No se pudo grabar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    limpiar(false);
                }
            };
            BarraService barra = BarraService.instancia;
            if (barra != null) barra.cuentaRegresiva(empezar);
            else new Handler(Looper.getMainLooper()).postDelayed(empezar, 1500);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo grabar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            limpiar(false);
        }
        return START_NOT_STICKY;
    }

    private void parar() {
        if (!grabando) { limpiar(false); return; }
        grabando = false;
        boolean ok = true;
        try { mr.stop(); } catch (Exception e) { ok = false; }
        limpiar(ok);
        Toast.makeText(this, ok ? "🎥 Video guardado en la Galería (álbum Mi Barra)" : "La grabación fue muy corta",
                Toast.LENGTH_LONG).show();
    }

    private void limpiar(boolean ok) {
        grabando = false;
        if (BarraService.instancia != null) BarraService.instancia.mostrarPararGrabacion(false);
        if (mr != null) { try { mr.release(); } catch (Exception ignored) {} mr = null; }
        if (vd != null) { vd.release(); vd = null; }
        if (pfd != null) { try { pfd.close(); } catch (Exception ignored) {} pfd = null; }
        if (uri != null) {
            Uri u = uri;
            uri = null;
            try {
                if (ok) Util.terminar(this, u);
                else getContentResolver().delete(u, null, null);
            } catch (Exception ignored) {}
        }
        if (mp != null) {
            MediaProjection x = mp;
            mp = null;
            try { x.stop(); } catch (Exception ignored) {}
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
}
