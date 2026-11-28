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
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

/**
 * Versión 18: mini cámara flotante encima de cualquier app.
 * Ocupa el cuarto de arriba de la pantalla (se puede estirar hasta un tercio),
 * se arrastra a donde uno quiera, tiene opacidad, y toma foto o video.
 * La ventana la pone el servicio de Mi Barra (BarraService) para que quede por encima de todo.
 */
public class MiniCamaraService extends Service {
    static volatile boolean activa;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout ventana;
    private TextureView textura;
    private TextView disparo, tiempo, bModo;
    private android.content.SharedPreferences prefs;

    private CameraDevice camara;
    private CameraCaptureSession sesion;
    private CaptureRequest.Builder vista;
    private ImageReader lector;
    private MediaRecorder mr;
    private ParcelFileDescriptor pfd;
    private Uri videoUri;
    private HandlerThread hilo;
    private Handler fondo;
    private Size tamVista, tamFoto, tamVideo;
    private int orientacion = 90;
    private boolean video, grabando, tomando;
    private long inicioVideo;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        if (i != null && "cerrar".equals(i.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (activa) return START_NOT_STICKY;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("minicam", "Mini cámara", NotificationManager.IMPORTANCE_LOW));
        PendingIntent cerrar = PendingIntent.getService(this, 7, new Intent(this, MiniCamaraService.class).setAction("cerrar"),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "minicam")
                .setContentTitle("📷 Mini cámara abierta")
                .setContentText("Toque aquí para cerrarla")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(cerrar)
                .setOngoing(true).build();
        int tipo = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            tipo |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        try { startForeground(41, n, tipo); } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir la mini cámara: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        // Versión 41: sus propias preferencias (corre aparte y borraba lo que guardaban las demás pantallas)
        prefs = getSharedPreferences("minicam", MODE_PRIVATE);
        BarraService b = BarraService.instancia;
        if (b == null) {
            Toast.makeText(this, "Active Mi Barra en Accesibilidad para usar la mini cámara", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        wm = (WindowManager) b.getSystemService(WINDOW_SERVICE);
        activa = true;
        if (prefs.getBoolean("ojoPuerta", true)) armarOjo(b); else armarVentana(b);
        return START_NOT_STICKY;
    }

    private int dp(float v) { return Util.dp(this, v); }

    private int altoPantalla() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        return m.heightPixels;
    }

    private FrameLayout circulo;

    /**
     * Versión 30: la mini cámara «ojo de puerta»: una ventanita redonda con aro metálico.
     * Se arrastra con el dedo a donde quiera; un toque = foto (o empezar/parar video).
     * Abajo, una tirita: ✕ · Foto/Video · opacidad · ➖ ➕ tamaño.
     */
    private void armarOjo(BarraService b) {
        int lado = Math.max(dp(110), Math.min(dp(320), prefs.getInt("ojoLado", dp(170))));
        int anchoV = Math.max(lado, dp(290));
        lp = new WindowManager.LayoutParams(anchoV, lado + dp(56), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs.getInt("ojoX", getResources().getDisplayMetrics().widthPixels - anchoV - dp(8));
        lp.y = prefs.getInt("ojoY", dp(40));
        lp.alpha = prefs.getInt("minicamOpaco", 85) / 100f;

        ventana = new FrameLayout(b);
        LinearLayout col = new LinearLayout(b);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        circulo = new FrameLayout(b);
        circulo.setBackgroundColor(Color.BLACK);
        circulo.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        circulo.setClipToOutline(true);
        textura = new TextureView(b);
        circulo.addView(textura, new FrameLayout.LayoutParams(lado, lado * 4 / 3, Gravity.CENTER));
        textura.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { abrir(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });
        // Aro metálico como el de una mirilla
        View aro = new View(b);
        GradientDrawable ag = new GradientDrawable();
        ag.setShape(GradientDrawable.OVAL);
        ag.setStroke(dp(6), 0xFFB0BEC5);
        aro.setBackground(ag);
        circulo.addView(aro, new FrameLayout.LayoutParams(-1, -1));
        tiempo = new TextView(b);
        tiempo.setTextColor(Color.WHITE);
        tiempo.setTextSize(13);
        tiempo.setShadowLayer(4, 0, 0, Color.BLACK);
        circulo.addView(tiempo, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        col.addView(circulo, new LinearLayout.LayoutParams(lado, lado));
        // Arrastrar el ojo con el dedo; un toque sin mover = disparar
        final float[] t0 = new float[4];
        final boolean[] movio = {false};
        circulo.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    t0[0] = e.getRawX(); t0[1] = e.getRawY(); t0[2] = lp.x; t0[3] = lp.y; movio[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - t0[0], dy = e.getRawY() - t0[1];
                    if (Math.abs(dx) + Math.abs(dy) > dp(8)) movio[0] = true;
                    if (movio[0]) { lp.x = (int) (t0[2] + dx); lp.y = (int) Math.max(0, t0[3] + dy); try { wm.updateViewLayout(ventana, lp); } catch (Exception ignored) {} }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (movio[0]) prefs.edit().putInt("ojoX", lp.x).putInt("ojoY", lp.y).apply();
                    else if (!video) foto(); else if (grabando) pararVideo(); else grabarVideo();
                    return true;
            }
            return false;
        });

        LinearLayout tira = new LinearLayout(b);
        tira.setGravity(Gravity.CENTER_VERTICAL);
        tira.setPadding(dp(4), dp(4), dp(4), 0);
        TextView cerrar = chip(b, "✕");
        cerrar.setOnClickListener(v -> stopSelf());
        tira.addView(cerrar);
        bModo = chip(b, "📷");
        bModo.setOnClickListener(v -> { if (!grabando) { video = !video; pintar(); } });
        tira.addView(bModo);
        SeekBar op = new SeekBar(b);
        op.setMax(80);
        op.setProgress(prefs.getInt("minicamOpaco", 85) - 20);
        op.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int v, boolean u) {
                if (!u) return;
                lp.alpha = (v + 20) / 100f;
                prefs.edit().putInt("minicamOpaco", v + 20).apply();
                try { wm.updateViewLayout(ventana, lp); } catch (Exception ignored) {}
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        tira.addView(op, new LinearLayout.LayoutParams(0, dp(36), 1f));
        TextView menos = chip(b, "➖");
        menos.setOnClickListener(v -> cambiarLado(-dp(30)));
        tira.addView(menos);
        TextView mas = chip(b, "➕");
        mas.setOnClickListener(v -> cambiarLado(dp(30)));
        tira.addView(mas);
        disparo = new TextView(b); // no se ve en el ojo: el disparo es tocar el círculo
        col.addView(tira, new LinearLayout.LayoutParams(-1, dp(52)));
        ventana.addView(col, new FrameLayout.LayoutParams(-1, -1));
        pintar();
        try { wm.addView(ventana, lp); } catch (Exception e) {
            Toast.makeText(this, "No se pudo mostrar la mini cámara", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    /** ➖ ➕ : agranda o achica el ojo. */
    private void cambiarLado(int d) {
        int lado = Math.max(dp(110), Math.min(dp(320), circulo.getLayoutParams().width + d));
        prefs.edit().putInt("ojoLado", lado).apply();
        circulo.setLayoutParams(new LinearLayout.LayoutParams(lado, lado));
        lp.width = Math.max(lado, dp(290));
        lp.height = lado + dp(56);
        try { wm.updateViewLayout(ventana, lp); } catch (Exception ignored) {}
        ViewGroup.LayoutParams tl = textura.getLayoutParams();
        float forma = tl.width > 0 ? (float) tl.height / tl.width : 4f / 3f;
        textura.setLayoutParams(new FrameLayout.LayoutParams(lado, Math.round(lado * forma), Gravity.CENTER));
    }

    private void armarVentana(BarraService b) {
        DisplayMetrics m = getResources().getDisplayMetrics();
        int ancho = m.widthPixels;
        int alto = Math.max(prefs.getInt("minicamAlto", altoPantalla() / 4), dp(140));
        lp = new WindowManager.LayoutParams(ancho, alto, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = prefs.getInt("minicamY", dp(30));
        lp.alpha = prefs.getInt("minicamOpaco", 85) / 100f;

        ventana = new FrameLayout(b);
        ventana.setBackgroundColor(Color.BLACK);
        ventana.setClipChildren(true);
        textura = new TextureView(b);
        ventana.addView(textura, new FrameLayout.LayoutParams(ancho, ancho * 4 / 3, Gravity.CENTER));
        textura.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { abrir(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });

        // Arriba: Foto/Video · tiempo · ✕
        LinearLayout arriba = new LinearLayout(b);
        arriba.setGravity(Gravity.CENTER_VERTICAL);
        arriba.setPadding(dp(6), dp(4), dp(6), 0);
        bModo = chip(b, "📷 Foto");
        bModo.setOnClickListener(v -> { if (!grabando) { video = !video; pintar(); } });
        arriba.addView(bModo);
        tiempo = new TextView(b);
        tiempo.setTextColor(Color.WHITE);
        tiempo.setTextSize(14);
        tiempo.setShadowLayer(4, 0, 0, Color.BLACK);
        tiempo.setGravity(Gravity.CENTER);
        arriba.addView(tiempo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView cerrar = chip(b, "✕");
        cerrar.setOnClickListener(v -> stopSelf());
        arriba.addView(cerrar);
        ventana.addView(arriba, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // Abajo: ✥ mover · opacidad · disparo · ⇕ tamaño
        LinearLayout abajo = new LinearLayout(b);
        abajo.setGravity(Gravity.CENTER_VERTICAL);
        abajo.setPadding(dp(6), 0, dp(6), dp(6));
        TextView mover = chip(b, "✥");
        mover.setOnTouchListener(arrastre(false));
        abajo.addView(mover);
        SeekBar op = new SeekBar(b);
        op.setMax(80);
        op.setProgress(prefs.getInt("minicamOpaco", 85) - 20);
        op.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean u) {
                if (!u) return;
                lp.alpha = (v + 20) / 100f;
                prefs.edit().putInt("minicamOpaco", v + 20).apply();
                try { wm.updateViewLayout(ventana, lp); } catch (Exception ignored) {}
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        abajo.addView(op, new LinearLayout.LayoutParams(0, dp(36), 1f));
        disparo = new TextView(b);
        disparo.setGravity(Gravity.CENTER);
        disparo.setOnClickListener(v -> { if (!video) foto(); else if (grabando) pararVideo(); else grabarVideo(); });
        LinearLayout.LayoutParams dpp = new LinearLayout.LayoutParams(dp(52), dp(52));
        dpp.setMargins(dp(6), 0, dp(6), 0);
        abajo.addView(disparo, dpp);
        TextView tam = chip(b, "⇕");
        tam.setOnTouchListener(arrastre(true));
        abajo.addView(tam);
        ventana.addView(abajo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        pintar();
        try { wm.addView(ventana, lp); } catch (Exception e) {
            Toast.makeText(this, "No se pudo mostrar la mini cámara", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private TextView chip(android.content.Context c, String t) {
        TextView x = new TextView(c);
        x.setText(t);
        x.setTextColor(Color.WHITE);
        x.setTextSize(15);
        x.setGravity(Gravity.CENTER);
        x.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x99000000);
        g.setCornerRadius(dp(16));
        x.setBackground(g);
        return x;
    }

    /** ✥ mueve la ventana arriba/abajo; ⇕ cambia el alto entre 1/4 y 1/3 de la pantalla (o más). */
    private View.OnTouchListener arrastre(boolean tamano) {
        final float[] t0 = new float[3];
        return (v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    t0[0] = e.getRawY(); t0[1] = lp.y; t0[2] = lp.height;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float d = e.getRawY() - t0[0];
                    if (tamano) lp.height = (int) Math.max(dp(140), Math.min(altoPantalla() / 2f, t0[2] + d));
                    else lp.y = (int) Math.max(0, Math.min(altoPantalla() - lp.height, t0[1] + d));
                    try { wm.updateViewLayout(ventana, lp); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    prefs.edit().putInt("minicamY", lp.y).putInt("minicamAlto", lp.height).apply();
                    return true;
            }
            return false;
        };
    }

    private void pintar() {
        bModo.setText(circulo != null ? (video ? "🎥" : "📷") : (video ? "🎥 Video" : "📷 Foto"));
        bModo.setAlpha(grabando ? 0.4f : 1f);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(video ? 0xFFE53935 : Color.WHITE);
        g.setStroke(dp(3), video ? 0xFFFFCDD2 : 0xFFB0BEC5);
        disparo.setBackground(g);
        disparo.setText(grabando ? "⏹" : "");
        disparo.setTextColor(Color.WHITE);
        disparo.setTextSize(20);
        if (!grabando) tiempo.setText("");
    }

    private static Size mejor(Size[] ops, int maxLado, double forma) {
        Size r = null;
        for (Size s : ops) {
            int l = Math.max(s.getWidth(), s.getHeight()), c = Math.min(s.getWidth(), s.getHeight());
            if (l > maxLado || Math.abs((double) l / c - forma) > 0.05) continue;
            if (r == null || (long) s.getWidth() * s.getHeight() > (long) r.getWidth() * r.getHeight()) r = s;
        }
        return r != null ? r : ops[0];
    }

    private void abrir() {
        if (camara != null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            aviso("Falta el permiso de cámara: ábrala una vez desde Mi Barra › Cámara");
            stopSelf();
            return;
        }
        try {
            CameraManager cm = getSystemService(CameraManager.class);
            String id = null;
            CameraCharacteristics ch = null;
            for (String i : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(i);
                Integer lado = c.get(CameraCharacteristics.LENS_FACING);
                if (lado != null && lado == CameraCharacteristics.LENS_FACING_BACK) { id = i; ch = c; break; }
            }
            if (id == null) { aviso("No encontré la cámara de atrás"); stopSelf(); return; }
            Integer o = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            orientacion = o == null ? 90 : o;
            StreamConfigurationMap mapa = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            tamVista = mejor(mapa.getOutputSizes(SurfaceTexture.class), 1600, 4 / 3.0);
            tamFoto = mejor(mapa.getOutputSizes(ImageFormat.JPEG), 4200, 4 / 3.0);
            tamVideo = mejor(mapa.getOutputSizes(MediaRecorder.class), 1920, 16 / 9.0);
            hilo = new HandlerThread("minicam");
            hilo.start();
            fondo = new Handler(hilo.getLooper());
            lector = ImageReader.newInstance(tamFoto.getWidth(), tamFoto.getHeight(), ImageFormat.JPEG, 2);
            lector.setOnImageAvailableListener(this::guardarFoto, fondo);
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) { camara = c; sesionVista(); }
                @Override public void onDisconnected(CameraDevice c) { c.close(); camara = null; }
                @Override public void onError(CameraDevice c, int e) {
                    c.close(); camara = null;
                    ui.post(() -> aviso("La cámara está ocupada por otra app. Ciérrela y vuelva a abrir la mini cámara."));
                }
            }, fondo);
        } catch (Exception e) { aviso("No se pudo abrir la cámara: " + e.getMessage()); }
    }

    private void tamTextura(Size s) {
        ui.post(() -> {
            int w = circulo != null ? circulo.getLayoutParams().width : lp.width;
            float forma = (float) Math.max(s.getWidth(), s.getHeight()) / Math.min(s.getWidth(), s.getHeight());
            textura.setLayoutParams(new FrameLayout.LayoutParams(w, Math.round(w * forma), Gravity.CENTER));
        });
    }

    private void sesionVista() {
        try {
            SurfaceTexture st = textura.getSurfaceTexture();
            if (st == null || camara == null) return;
            tamTextura(tamVista);
            st.setDefaultBufferSize(tamVista.getWidth(), tamVista.getHeight());
            Surface s = new Surface(st);
            vista = camara.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            vista.addTarget(s);
            camara.createCaptureSession(Arrays.asList(s, lector.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession ss) { sesion = ss; repetir(); }
                @Override public void onConfigureFailed(CameraCaptureSession ss) { ui.post(() -> aviso("No se pudo iniciar la cámara")); }
            }, fondo);
        } catch (Exception e) { ui.post(() -> aviso("Error de cámara: " + e.getMessage())); }
    }

    private void repetir() {
        if (sesion == null || vista == null) return;
        try {
            vista.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            vista.set(CaptureRequest.CONTROL_AF_MODE, grabando ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            sesion.setRepeatingRequest(vista.build(), null, fondo);
        } catch (Exception ignored) {}
    }

    private void foto() {
        if (tomando || camara == null || sesion == null) return;
        tomando = true;
        try {
            CaptureRequest.Builder b = camara.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(lector.getSurface());
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            b.set(CaptureRequest.JPEG_ORIENTATION, orientacion);
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 92);
            sesion.capture(b.build(), null, fondo);
            textura.animate().alpha(0.2f).setDuration(80).withEndAction(() -> textura.animate().alpha(1f).setDuration(120));
        } catch (Exception e) { tomando = false; aviso("No se pudo tomar la foto"); }
    }

    private void guardarFoto(ImageReader r) {
        Image im = r.acquireLatestImage();
        if (im == null) return;
        byte[] d;
        try { ByteBuffer bb = im.getPlanes()[0].getBuffer(); d = new byte[bb.remaining()]; bb.get(d); } finally { im.close(); }
        try {
            Util.guardarBytes(this, "Foto_" + Util.fecha() + ".jpg", "image/jpeg", d);
            ui.post(() -> { tomando = false; aviso("📷 Foto guardada en la Galería"); });
        } catch (Exception e) { ui.post(() -> { tomando = false; aviso("No se pudo guardar la foto"); }); }
    }

    private void grabarVideo() {
        if (camara == null) return;
        try {
            if (sesion != null) { sesion.close(); sesion = null; }
            videoUri = Util.crearEnDescargas(this, "Video_" + Util.fecha() + ".mp4", "video/mp4");
            pfd = getContentResolver().openFileDescriptor(videoUri, "rw");
            boolean audio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            mr = new MediaRecorder();
            if (audio) mr.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mr.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mr.setVideoSize(tamVideo.getWidth(), tamVideo.getHeight());
            mr.setVideoFrameRate(30);
            mr.setVideoEncodingBitRate(tamVideo.getWidth() >= 1920 ? 10_000_000 : 5_000_000);
            if (audio) { mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); mr.setAudioEncodingBitRate(128000); mr.setAudioSamplingRate(44100); }
            mr.setOrientationHint(orientacion);
            mr.setOutputFile(pfd.getFileDescriptor());
            mr.prepare();
            SurfaceTexture st = textura.getSurfaceTexture();
            st.setDefaultBufferSize(tamVideo.getWidth(), tamVideo.getHeight());
            tamTextura(tamVideo);
            Surface sv = new Surface(st), sr = mr.getSurface();
            vista = camara.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            vista.addTarget(sv);
            vista.addTarget(sr);
            camara.createCaptureSession(Arrays.asList(sv, sr), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession ss) {
                    sesion = ss;
                    grabando = true;
                    repetir();
                    try { mr.start(); } catch (Exception e) { ui.post(() -> aviso("No se pudo grabar")); return; }
                    inicioVideo = System.currentTimeMillis();
                    ui.post(() -> { pintar(); ui.post(reloj); });
                }
                @Override public void onConfigureFailed(CameraCaptureSession ss) { ui.post(() -> aviso("No se pudo grabar")); }
            }, fondo);
        } catch (Exception e) { aviso("No se pudo grabar: " + e.getMessage()); limpiarVideo(false); }
    }

    private final Runnable reloj = new Runnable() {
        @Override public void run() {
            if (!grabando) return;
            long s = (System.currentTimeMillis() - inicioVideo) / 1000;
            tiempo.setText(String.format(Locale.US, "🔴 %02d:%02d", s / 60, s % 60));
            ui.postDelayed(this, 500);
        }
    };

    private void pararVideo() {
        if (!grabando) return;
        grabando = false;
        boolean ok = true;
        try { mr.stop(); } catch (Exception e) { ok = false; }
        limpiarVideo(ok);
        aviso(ok ? "🎬 Video guardado en la Galería" : "El video fue muy corto");
        pintar();
        if (camara != null) { try { if (sesion != null) sesion.close(); } catch (Exception ignored) {} sesion = null; sesionVista(); }
    }

    private void limpiarVideo(boolean ok) {
        if (mr != null) { try { mr.release(); } catch (Exception ignored) {} mr = null; }
        if (pfd != null) { try { pfd.close(); } catch (Exception ignored) {} pfd = null; }
        if (videoUri != null) {
            try { if (ok) Util.terminar(this, videoUri); else getContentResolver().delete(videoUri, null, null); } catch (Exception ignored) {}
            videoUri = null;
        }
    }

    private void aviso(String s) { ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show()); }

    @Override
    public void onDestroy() {
        if (grabando) pararVideo();
        try { if (sesion != null) sesion.close(); } catch (Exception ignored) {}
        sesion = null;
        if (camara != null) { camara.close(); camara = null; }
        if (lector != null) { lector.close(); lector = null; }
        if (hilo != null) { hilo.quitSafely(); hilo = null; }
        if (ventana != null && ventana.isAttachedToWindow()) { try { wm.removeView(ventana); } catch (Exception ignored) {} }
        activa = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
