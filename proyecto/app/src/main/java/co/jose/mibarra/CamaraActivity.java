package co.jose.mibarra;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Cámara propia de Mi Barra: foto y video, de atrás o de adelante, con linterna. Guarda en Descargas/MiBarra. */
public class CamaraActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextureView textura;
    private CameraDevice camara;
    private CameraCaptureSession sesion;
    private ImageReader lector;
    private MediaRecorder mr;
    private ParcelFileDescriptor pfd;
    private Uri videoUri;
    private HandlerThread hilo;
    private Handler fondo;
    private CaptureRequest.Builder vista;
    private Size tamVista, tamFoto, tamVideo;
    private int orientacion = 90;
    private boolean video, delante, luz, grabando, tomando;
    private long inicioVideo;
    private TextView disparo, tiempo, bModo, bLuz, bGirar;
    private android.widget.ImageView miniatura;
    private Uri ultima;
    private String ultimaTipo = "image/*";

    /** Muestra la última foto o video abajo: un toque y la ve. */
    private void ponerMiniatura(Uri u, String tipo) {
        ultima = u; ultimaTipo = tipo;
        new Thread(() -> {
            android.graphics.Bitmap b = null;
            try { b = getContentResolver().loadThumbnail(u, new Size(200, 200), null); } catch (Exception ignored) {}
            final android.graphics.Bitmap fb = b;
            ui.post(() -> { if (fb != null) { miniatura.setImageBitmap(fb); miniatura.setVisibility(View.VISIBLE); } });
        }).start();
    }

    private int dp(float v) { return Util.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        video = getIntent().getBooleanExtra("video", false);
        armarPantalla();
        List<String> faltan = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) faltan.add(Manifest.permission.CAMERA);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) faltan.add(Manifest.permission.RECORD_AUDIO);
        if (!faltan.isEmpty()) requestPermissions(faltan.toArray(new String[0]), 1);
    }

    @Override
    public void onRequestPermissionsResult(int r, String[] p, int[] res) {
        super.onRequestPermissionsResult(r, p, res);
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) { if (textura.isAvailable()) abrir(); }
        else { aviso("Sin permiso de cámara no puedo tomar fotos"); finish(); }
    }

    @Override protected void onResume() { super.onResume(); if (textura.isAvailable()) abrir(); }
    @Override protected void onPause() { if (grabando) pararVideo(); cerrar(); super.onPause(); }

    private void aviso(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private TextView boton(String t, float tam) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(tam);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x66000000);
        g.setCornerRadius(dp(30));
        v.setBackground(g);
        return v;
    }

    private void armarPantalla() {
        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.BLACK);
        textura = new TextureView(this);
        raiz.addView(textura, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        textura.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { abrir(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
        // Arriba: linterna, tiempo del video, cerrar
        LinearLayout arriba = new LinearLayout(this);
        arriba.setGravity(Gravity.CENTER_VERTICAL);
        arriba.setPadding(dp(12), dp(12), dp(12), 0);
        bLuz = boton("🔦", 22);
        bLuz.setOnClickListener(v -> { luz = !luz; bLuz.setAlpha(luz ? 1f : 0.5f); repetir(); });
        bLuz.setAlpha(0.5f);
        arriba.addView(bLuz, new LinearLayout.LayoutParams(dp(56), dp(56)));
        tiempo = new TextView(this);
        tiempo.setTextColor(Color.WHITE);
        tiempo.setTextSize(20);
        tiempo.setGravity(Gravity.CENTER);
        arriba.addView(tiempo, new LinearLayout.LayoutParams(0, dp(56), 1f));
        TextView cerrar = boton("✕", 22);
        cerrar.setOnClickListener(v -> finish());
        arriba.addView(cerrar, new LinearLayout.LayoutParams(dp(56), dp(56)));
        raiz.addView(arriba, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        // Abajo: modo foto/video, disparo, girar cámara
        LinearLayout abajo = new LinearLayout(this);
        abajo.setGravity(Gravity.CENTER_VERTICAL);
        abajo.setPadding(dp(16), 0, dp(16), dp(28));
        miniatura = new android.widget.ImageView(this);
        miniatura.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        miniatura.setVisibility(View.INVISIBLE);
        GradientDrawable gm = new GradientDrawable();
        gm.setCornerRadius(dp(12));
        gm.setStroke(dp(2), Color.WHITE);
        miniatura.setBackground(gm);
        miniatura.setClipToOutline(true);
        miniatura.setOnClickListener(v -> {
            if (ultima == null) return;
            // Versión 16: se ve dentro de Mi Barra, sin preguntar "Abrir con"
            try { startActivity(new Intent(this, VerActivity.class).setDataAndType(ultima, ultimaTipo).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
            catch (Exception e) { aviso("No se pudo abrir"); }
        });
        abajo.addView(miniatura, new LinearLayout.LayoutParams(dp(56), dp(56)) {{ rightMargin = dp(8); }});
        bModo = boton("", 16);
        bModo.setOnClickListener(v -> { if (!grabando) { video = !video; pintar(); } });
        abajo.addView(bModo, new LinearLayout.LayoutParams(0, dp(56), 1f));
        disparo = new TextView(this);
        disparo.setGravity(Gravity.CENTER);
        disparo.setOnClickListener(v -> { if (video) { if (grabando) pararVideo(); else grabarVideo(); } else foto(); });
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(86), dp(86));
        dl.setMargins(dp(18), 0, dp(18), 0);
        abajo.addView(disparo, dl);
        bGirar = boton("🔄", 24);
        bGirar.setOnClickListener(v -> { if (!grabando) { delante = !delante; cerrar(); abrir(); } });
        abajo.addView(bGirar, new LinearLayout.LayoutParams(0, dp(56), 1f));
        raiz.addView(abajo, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(raiz);
        pintar();
    }

    private void pintar() {
        bModo.setText(video ? "📷 Foto" : "🎬 Video");
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(video ? (grabando ? Color.WHITE : 0xFFE53935) : Color.WHITE);
        g.setStroke(dp(6), video ? 0xFFFFCDD2 : 0xFFB0BEC5);
        disparo.setBackground(g);
        disparo.setText(grabando ? "⏹" : "");
        disparo.setTextSize(30);
        disparo.setTextColor(0xFFE53935);
        bModo.setAlpha(grabando ? 0.3f : 1f);
        bGirar.setAlpha(grabando ? 0.3f : 1f);
        if (!grabando) tiempo.setText(video ? "VIDEO" : "FOTO");
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
        if (camara != null || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            CameraManager cm = getSystemService(CameraManager.class);
            String id = null;
            CameraCharacteristics ch = null;
            int quiero = delante ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
            for (String i : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(i);
                Integer lado = c.get(CameraCharacteristics.LENS_FACING);
                if (lado != null && lado == quiero) { id = i; ch = c; break; }
            }
            if (id == null) { aviso("No encontré esa cámara"); delante = false; return; }
            Integer o = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            orientacion = o == null ? 90 : o;
            StreamConfigurationMap mapa = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            tamVista = mejor(mapa.getOutputSizes(SurfaceTexture.class), 1920, 4 / 3.0);
            tamFoto = mejor(mapa.getOutputSizes(ImageFormat.JPEG), 4200, 4 / 3.0);
            tamVideo = mejor(mapa.getOutputSizes(MediaRecorder.class), 1920, 16 / 9.0);
            ajustar();
            hilo = new HandlerThread("camara");
            hilo.start();
            fondo = new Handler(hilo.getLooper());
            lector = ImageReader.newInstance(tamFoto.getWidth(), tamFoto.getHeight(), ImageFormat.JPEG, 2);
            lector.setOnImageAvailableListener(this::guardarFoto, fondo);
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) { camara = c; sesionVista(); }
                @Override public void onDisconnected(CameraDevice c) { c.close(); camara = null; }
                @Override public void onError(CameraDevice c, int e) {
                    c.close(); camara = null;
                    ui.post(() -> aviso("La cámara está ocupada por otra app (¿linterna o grabadora?). Ciérrela y vuelva a abrir."));
                }
            }, fondo);
        } catch (Exception e) { aviso("No se pudo abrir la cámara: " + e.getMessage()); }
    }

    /** La vista ocupa el ancho sin estirar la imagen. */
    private void ajustar() {
        ui.post(() -> {
            View p = (View) textura.getParent();
            int w = p.getWidth(), h = p.getHeight();
            if (w == 0) return;
            Size s = grabando ? tamVideo : tamVista;
            float forma = (float) Math.max(s.getWidth(), s.getHeight()) / Math.min(s.getWidth(), s.getHeight());
            int alto = Math.round(w * forma), ancho = w;
            if (alto > h) { alto = h; ancho = Math.round(h / forma); }
            textura.setLayoutParams(new FrameLayout.LayoutParams(ancho, alto, Gravity.CENTER));
        });
    }

    private void sesionVista() {
        try {
            SurfaceTexture st = textura.getSurfaceTexture();
            if (st == null || camara == null) return;
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
            vista.set(CaptureRequest.FLASH_MODE, luz ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            sesion.setRepeatingRequest(vista.build(), null, fondo);
        } catch (Exception ignored) {}
    }

    private int giro() { return delante ? (orientacion + 180) % 360 == 90 ? 270 : orientacion : orientacion; }

    // ---------------- Foto ----------------

    private void foto() {
        if (tomando || camara == null || sesion == null) return;
        tomando = true;
        try {
            CaptureRequest.Builder b = camara.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(lector.getSurface());
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            b.set(CaptureRequest.FLASH_MODE, luz ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            b.set(CaptureRequest.JPEG_ORIENTATION, orientacion);
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 92);
            sesion.capture(b.build(), null, fondo);
            ui.removeCallbacks(sinFoto);
            ui.postDelayed(sinFoto, 6000);
            textura.animate().alpha(0.2f).setDuration(80).withEndAction(() -> textura.animate().alpha(1f).setDuration(120));
        } catch (Exception e) { tomando = false; aviso("No se pudo tomar la foto"); }
    }

    /** Versión 22: si la cámara no entrega la foto, avisa en vez de quedarse callada. */
    private final Runnable sinFoto = () -> { if (tomando) { tomando = false; aviso("La cámara no entregó la foto. Toque otra vez el botón."); } };

    private void guardarFoto(ImageReader r) {
        Image im = r.acquireLatestImage();
        if (im == null) return;
        byte[] d;
        try { ByteBuffer bb = im.getPlanes()[0].getBuffer(); d = new byte[bb.remaining()]; bb.get(d); } finally { im.close(); }
        try {
            Uri u = Util.guardarBytes(this, "Foto_" + Util.fecha() + ".jpg", "image/jpeg", d);
            ui.post(() -> {
                tomando = false;
                ui.removeCallbacks(sinFoto);
                // Versión 22: si la pidió otra pantalla (Foto con medidas), se la devuelve de una vez
                if (getIntent().getBooleanExtra("devolver", false)) { setResult(RESULT_OK, new android.content.Intent().setData(u)); finish(); return; }
                aviso("📷 Foto guardada: Galería › álbum Fotos (MiBarra)");
                ponerMiniatura(u, "image/jpeg");
            });
        } catch (Exception e) { ui.post(() -> { tomando = false; aviso("No se pudo guardar la foto"); }); }
    }

    // ---------------- Video ----------------

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
                    ui.post(() -> { pintar(); ajustar(); ui.post(reloj); });
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
        if (camara != null) { try { if (sesion != null) sesion.close(); } catch (Exception ignored) {} sesion = null; sesionVista(); ajustar(); }
    }

    private void limpiarVideo(boolean ok) {
        if (mr != null) { try { mr.release(); } catch (Exception ignored) {} mr = null; }
        if (pfd != null) { try { pfd.close(); } catch (Exception ignored) {} pfd = null; }
        if (videoUri != null) {
            try {
                if (ok) { Util.terminar(this, videoUri); ponerMiniatura(videoUri, "video/mp4"); }
                else getContentResolver().delete(videoUri, null, null);
            } catch (Exception ignored) {}
            videoUri = null;
        }
    }

    private void cerrar() {
        try { if (sesion != null) sesion.close(); } catch (Exception ignored) {}
        sesion = null;
        if (camara != null) { camara.close(); camara = null; }
        if (lector != null) { lector.close(); lector = null; }
        if (hilo != null) { hilo.quitSafely(); hilo = null; fondo = null; }
    }
}
