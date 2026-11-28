package co.jose.mibarra;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.pdf.PdfDocument;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.ToneGenerator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Escáner con nivel: la cámara muestra una burbuja; cuando el celular está derecho sobre la hoja
 * el marco se pone verde, pita, y (si quiere) toma la foto solo. Luego se ajustan las 4 esquinas,
 * se endereza, se escoge Color / Gris / Blanco y negro y se guarda en PDF o imagen.
 */
public class EscanerActivity extends Base implements SensorEventListener {

    private static final float DERECHO = 1.5f; // grados
    private final Handler ui = new Handler(Looper.getMainLooper());

    // Cámara
    private TextureView textura;
    private GuiaView guia;
    private CameraDevice camara;
    private CameraCaptureSession sesion;
    private ImageReader lector;
    private HandlerThread hilo;
    private Handler fondo;
    private CaptureRequest.Builder vista;
    private String idCam;
    private int orientacionSensor = 90;
    private boolean luz, auto, tomando, enCamara;
    private Size tamVista, tamFoto;
    private Button bLuz, bAuto;

    // Nivel
    private SensorManager sm;
    private final float[] g = new float[3];
    private boolean hayDato, estabaDerecho;
    private long derechoDesde;
    private ToneGenerator tono;

    // Páginas
    private final List<File> paginas = new ArrayList<>();
    private Bitmap foto;
    private int filtro = 2; // 0 color, 1 gris, 2 blanco y negro

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sm = getSystemService(SensorManager.class);
        auto = getSharedPreferences("barra", MODE_PRIVATE).getBoolean("escAuto", false);
        filtro = getSharedPreferences("barra", MODE_PRIVATE).getInt("escFiltro", 2);
        try { tono = new ToneGenerator(AudioManager.STREAM_MUSIC, 70); } catch (Exception ignored) {}
        File c = new File(getCacheDir(), "escaner");
        c.mkdirs();
        File[] viejos = c.listFiles();
        if (viejos != null) for (File f : viejos) f.delete();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 5);
        pantallaCamara();
    }

    @Override
    public void onRequestPermissionsResult(int r, String[] p, int[] res) {
        super.onRequestPermissionsResult(r, p, res);
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && enCamara && textura.isAvailable()) abrirCamara();
        else if (res.length > 0) aviso("Sin permiso de cámara no se puede escanear");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (s == null) s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
        if (enCamara && textura != null && textura.isAvailable()) abrirCamara();
    }

    @Override
    protected void onPause() {
        sm.unregisterListener(this);
        cerrarCamara();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (tono != null) tono.release();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!enCamara && foto != null) { foto = null; pantallaCamara(); }
        else if (enCamara && !paginas.isEmpty()) pantallaPaginas();
        else super.onBackPressed();
    }

    // ================= 1. Cámara con nivel =================

    private void pantallaCamara() {
        enCamara = true;
        tomando = false;
        LinearLayout raiz = vertical();
        raiz.setBackgroundColor(Color.BLACK);
        FrameLayout marco = new FrameLayout(this);
        textura = new TextureView(this);
        marco.addView(textura, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        guia = new GuiaView(this);
        marco.addView(guia, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        raiz.addView(marco, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bot = horizontal();
        bot.setGravity(Gravity.CENTER_VERTICAL);
        bot.setPadding(dp(6), dp(8), dp(6), dp(8));
        bLuz = boton(luz ? "🔦 Luz: sí" : "🔦 Luz", 0xFF37474F);
        bLuz.setOnClickListener(v -> { luz = !luz; bLuz.setText(luz ? "🔦 Luz: sí" : "🔦 Luz"); repetirVista(); });
        bot.addView(bLuz, peso(1));
        TextView disparo = new TextView(this);
        disparo.setText("●");
        disparo.setTextSize(52);
        disparo.setTextColor(Color.WHITE);
        disparo.setGravity(Gravity.CENTER);
        disparo.setOnClickListener(v -> tomarFoto());
        bot.addView(disparo, new LinearLayout.LayoutParams(dp(90), dp(80)));
        bAuto = boton(auto ? "⚡ Sola: sí" : "⚡ Sola: no", 0xFF37474F);
        bAuto.setOnClickListener(v -> {
            auto = !auto;
            bAuto.setText(auto ? "⚡ Sola: sí" : "⚡ Sola: no");
            getSharedPreferences("barra", MODE_PRIVATE).edit().putBoolean("escAuto", auto).apply();
            aviso(auto ? "La foto se toma sola cuando el celular quede derecho" : "Toque el botón blanco para tomar la foto");
        });
        bot.addView(bAuto, peso(1));
        raiz.addView(bot);
        if (!paginas.isEmpty()) {
            Button ver = boton("📑  Ver " + paginas.size() + (paginas.size() == 1 ? " página" : " páginas") + " y guardar", 0xFF2E7D32);
            ver.setOnClickListener(v -> pantallaPaginas());
            raiz.addView(ver, ancho(50));
        }
        setContentView(raiz);
        textura.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { abrirCamara(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
        if (textura.isAvailable()) abrirCamara();
    }

    private static Size mejor(Size[] ops, int maxLado, boolean cuatroTercios) {
        Size r = null;
        for (Size s : ops) {
            int largo = Math.max(s.getWidth(), s.getHeight()), corto = Math.min(s.getWidth(), s.getHeight());
            if (largo > maxLado) continue;
            if (cuatroTercios && Math.abs(largo * 3 - corto * 4) > largo / 20) continue;
            if (r == null || (long) s.getWidth() * s.getHeight() > (long) r.getWidth() * r.getHeight()) r = s;
        }
        if (r == null) r = ops[0];
        return r;
    }

    private void abrirCamara() {
        if (camara != null || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            CameraManager cm = getSystemService(CameraManager.class);
            idCam = null;
            CameraCharacteristics ch = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer lado = c.get(CameraCharacteristics.LENS_FACING);
                if (lado != null && lado == CameraCharacteristics.LENS_FACING_BACK) { idCam = id; ch = c; break; }
            }
            if (idCam == null) { aviso("No encontré la cámara de atrás"); return; }
            Integer o = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            orientacionSensor = o == null ? 90 : o;
            StreamConfigurationMap mapa = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            tamVista = mejor(mapa.getOutputSizes(SurfaceTexture.class), 1920, true);
            tamFoto = mejor(mapa.getOutputSizes(ImageFormat.JPEG), 4200, true);
            ajustarTextura();
            hilo = new HandlerThread("camara");
            hilo.start();
            fondo = new Handler(hilo.getLooper());
            lector = ImageReader.newInstance(tamFoto.getWidth(), tamFoto.getHeight(), ImageFormat.JPEG, 2);
            lector.setOnImageAvailableListener(this::fotoLista, fondo);
            cm.openCamera(idCam, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) { camara = c; iniciarVista(); }
                @Override public void onDisconnected(CameraDevice c) { c.close(); camara = null; }
                @Override public void onError(CameraDevice c, int e) {
                    c.close(); camara = null;
                    ui.post(() -> aviso("La cámara está ocupada. Cierre otras apps de cámara."));
                }
            }, fondo);
        } catch (Exception e) {
            aviso("No se pudo abrir la cámara: " + e.getMessage());
        }
    }

    /** La vista previa ocupa el ancho y conserva la forma de la foto (sin estirar). */
    private void ajustarTextura() {
        ui.post(() -> {
            View p = (View) textura.getParent();
            int w = p.getWidth(), h = p.getHeight();
            if (w == 0 || tamVista == null) return;
            float forma = (float) Math.max(tamVista.getWidth(), tamVista.getHeight()) / Math.min(tamVista.getWidth(), tamVista.getHeight());
            int alto = Math.round(w * forma), ancho = w;
            if (alto > h) { alto = h; ancho = Math.round(h / forma); }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ancho, alto, Gravity.CENTER);
            textura.setLayoutParams(lp);
            guia.setLayoutParams(new FrameLayout.LayoutParams(ancho, alto, Gravity.CENTER));
        });
    }

    private void iniciarVista() {
        try {
            SurfaceTexture st = textura.getSurfaceTexture();
            if (st == null) return;
            st.setDefaultBufferSize(tamVista.getWidth(), tamVista.getHeight());
            Surface s = new Surface(st);
            vista = camara.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            vista.addTarget(s);
            camara.createCaptureSession(Arrays.asList(s, lector.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession ss) { sesion = ss; repetirVista(); }
                @Override public void onConfigureFailed(CameraCaptureSession ss) { ui.post(() -> aviso("No se pudo iniciar la cámara")); }
            }, fondo);
        } catch (Exception e) {
            ui.post(() -> aviso("Error de cámara: " + e.getMessage()));
        }
    }

    private void repetirVista() {
        if (sesion == null || vista == null) return;
        try {
            vista.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            vista.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            vista.set(CaptureRequest.FLASH_MODE, luz ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            sesion.setRepeatingRequest(vista.build(), null, fondo);
        } catch (Exception ignored) {}
    }

    private void tomarFoto() {
        if (tomando || camara == null || sesion == null) return;
        tomando = true;
        try {
            CaptureRequest.Builder b = camara.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(lector.getSurface());
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            b.set(CaptureRequest.FLASH_MODE, luz ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 92);
            sesion.capture(b.build(), null, fondo);
            if (tono != null) tono.startTone(ToneGenerator.TONE_PROP_BEEP2, 120);
            aviso("📸 Tomando…");
        } catch (Exception e) {
            tomando = false;
            aviso("No se pudo tomar la foto");
        }
    }

    private void fotoLista(ImageReader r) {
        Image im = r.acquireLatestImage();
        if (im == null) return;
        byte[] datos;
        try {
            ByteBuffer bb = im.getPlanes()[0].getBuffer();
            datos = new byte[bb.remaining()];
            bb.get(datos);
        } finally { im.close(); }
        BitmapFactory.Options op = new BitmapFactory.Options();
        op.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(datos, 0, datos.length, op);
        int largo = Math.max(op.outWidth, op.outHeight), muestra = 1;
        while (largo / (muestra * 2) >= 2200) muestra *= 2;
        op = new BitmapFactory.Options();
        op.inSampleSize = muestra;
        Bitmap bm = BitmapFactory.decodeByteArray(datos, 0, datos.length, op);
        if (bm == null) { ui.post(() -> { tomando = false; aviso("La foto salió dañada, tome otra"); }); return; }
        if (orientacionSensor != 0) {
            Matrix m = new Matrix();
            m.postRotate(orientacionSensor);
            Bitmap rot = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            if (rot != bm) bm.recycle();
            bm = rot;
        }
        final Bitmap f = bm;
        ui.post(() -> { foto = f; cerrarCamara(); pantallaRecorte(); });
    }

    private void cerrarCamara() {
        try { if (sesion != null) sesion.close(); } catch (Exception ignored) {}
        sesion = null;
        if (camara != null) { camara.close(); camara = null; }
        if (lector != null) { lector.close(); lector = null; }
        if (hilo != null) { hilo.quitSafely(); hilo = null; fondo = null; }
    }

    // ----- Nivel -----

    @Override
    public void onSensorChanged(SensorEvent e) {
        float a = hayDato ? 0.15f : 1f;
        for (int i = 0; i < 3; i++) g[i] += a * (e.values[i] - g[i]);
        hayDato = true;
        if (!enCamara || guia == null) return;
        double x = Math.toDegrees(Math.atan2(g[0], g[2])), y = Math.toDegrees(Math.atan2(g[1], g[2]));
        boolean boca = g[2] > 3; // celular acostado, cámara mirando hacia la hoja
        boolean derecho = boca && Math.abs(x) < DERECHO && Math.abs(y) < DERECHO;
        guia.poner(x, y, derecho, boca);
        long ahora = System.currentTimeMillis();
        if (derecho && !estabaDerecho) {
            derechoDesde = ahora;
            if (tono != null) tono.startTone(ToneGenerator.TONE_PROP_ACK, 150);
        }
        estabaDerecho = derecho;
        if (auto && derecho && !tomando && ahora - derechoDesde > 1300) tomarFoto();
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    /** Marco de la hoja + burbuja en el centro. Verde cuando el celular está derecho. */
    static final class GuiaView extends View {
        private double x, y;
        private boolean ok, boca;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        GuiaView(Context c) { super(c); }
        void poner(double x, double y, boolean ok, boolean boca) { this.x = x; this.y = y; this.ok = ok; this.boca = boca; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = Util.dp(getContext(), 1);
            int color = ok ? 0xFF4CFF4C : 0xFFFFC107;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4 * d);
            p.setColor(color);
            float m = 18 * d, l = 40 * d;
            float[][] esq = {{m, m, 1, 1}, {w - m, m, -1, 1}, {m, h - m, 1, -1}, {w - m, h - m, -1, -1}};
            for (float[] e : esq) {
                c.drawLine(e[0], e[1], e[0] + l * e[2], e[1], p);
                c.drawLine(e[0], e[1], e[0], e[1] + l * e[3], p);
            }
            float cx = w / 2, cy = h / 2, r = 46 * d;
            p.setStrokeWidth(2 * d);
            p.setColor(0xAAFFFFFF);
            c.drawCircle(cx, cy, r, p);
            p.setColor(color);
            c.drawCircle(cx, cy, r * DERECHO / 6f + 10 * d, p);
            double bx = Math.max(-6, Math.min(6, x)), by = Math.max(-6, Math.min(6, y));
            p.setStyle(Paint.Style.FILL);
            p.setColor(ok ? 0xDD4CFF4C : 0xDDFFFFFF);
            c.drawCircle((float) (cx - bx / 6 * r), (float) (cy + by / 6 * r), 9 * d, p);
            p.setColor(0xCC000000);
            c.drawRect(0, h - 40 * d, w, h, p);
            p.setColor(Color.WHITE);
            p.setTextSize(16 * d);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(!boca ? "Acueste el celular encima de la hoja" : ok ? "✔ Derecho: ¡tome la foto!" : "Enderece hasta que la bolita quede en el centro",
                    cx, h - 14 * d, p);
        }
    }

    // ================= 2. Ajustar las 4 esquinas =================

    private RecorteView recorte;

    private void pantallaRecorte() {
        enCamara = false;
        LinearLayout raiz = vertical();
        raiz.setBackgroundColor(0xFF263238);
        TextView ay = new TextView(this);
        ay.setText("Arrastre los 4 círculos a las esquinas de la hoja");
        ay.setTextColor(Color.WHITE);
        ay.setTextSize(16);
        ay.setGravity(Gravity.CENTER);
        ay.setPadding(0, dp(8), 0, dp(4));
        raiz.addView(ay);
        recorte = new RecorteView(this, foto);
        raiz.addView(recorte, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout f1 = horizontal();
        String[] nf = {"🎨 Color", "◐ Gris", "▣ Blanco y negro"};
        Button[] bf = new Button[3];
        for (int i = 0; i < 3; i++) {
            final int k = i;
            bf[i] = boton(nf[i], i == filtro ? 0xFF1565C0 : 0xFF455A64);
            bf[i].setTextSize(13);
            bf[i].setOnClickListener(v -> {
                filtro = k;
                getSharedPreferences("barra", MODE_PRIVATE).edit().putInt("escFiltro", k).apply();
                for (int j = 0; j < 3; j++) bf[j].setBackgroundColor(j == k ? 0xFF1565C0 : 0xFF455A64);
            });
            f1.addView(bf[i], peso(1));
        }
        raiz.addView(f1, ancho(48));
        LinearLayout f2 = horizontal();
        Button otra = boton("↺ Repetir", 0xFF6D4C41);
        otra.setOnClickListener(v -> { foto = null; pantallaCamara(); });
        Button todo = boton("⬚ Toda", 0xFF455A64);
        todo.setOnClickListener(v -> recorte.todaLaFoto());
        Button listo = boton("✔ Listo", 0xFF2E7D32);
        listo.setOnClickListener(v -> procesar());
        f2.addView(otra, peso(1));
        f2.addView(todo, peso(1));
        f2.addView(listo, peso(1.4f));
        raiz.addView(f2, ancho(56));
        setContentView(raiz);
    }

    static final class RecorteView extends View {
        final Bitmap bm;
        final PointF[] p = new PointF[4]; // en pixeles de la foto: arriba-izq, arriba-der, abajo-der, abajo-izq
        private final Matrix m = new Matrix(), inv = new Matrix();
        private final Paint linea = new Paint(Paint.ANTI_ALIAS_FLAG), punto = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int agarrado = -1;

        RecorteView(Context c, Bitmap b) {
            super(c);
            bm = b;
            float w = b.getWidth(), h = b.getHeight(), i = 0.07f;
            p[0] = new PointF(w * i, h * i); p[1] = new PointF(w * (1 - i), h * i);
            p[2] = new PointF(w * (1 - i), h * (1 - i)); p[3] = new PointF(w * i, h * (1 - i));
            linea.setStyle(Paint.Style.STROKE);
            linea.setColor(0xFF4CFF4C);
            linea.setStrokeWidth(Util.dp(c, 3));
        }

        void todaLaFoto() {
            float w = bm.getWidth(), h = bm.getHeight();
            p[0].set(0, 0); p[1].set(w, 0); p[2].set(w, h); p[3].set(0, h);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float pad = Util.dp(getContext(), 20);
            m.setRectToRect(new RectF(0, 0, bm.getWidth(), bm.getHeight()),
                    new RectF(pad, pad, getWidth() - pad, getHeight() - pad), Matrix.ScaleToFit.CENTER);
            m.invert(inv);
            c.drawBitmap(bm, m, null);
            float[] q = new float[8];
            for (int i = 0; i < 4; i++) { q[i * 2] = p[i].x; q[i * 2 + 1] = p[i].y; }
            m.mapPoints(q);
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                c.drawLine(q[i * 2], q[i * 2 + 1], q[j * 2], q[j * 2 + 1], linea);
            }
            float r = Util.dp(getContext(), 16);
            for (int i = 0; i < 4; i++) {
                punto.setColor(i == agarrado ? 0xCCFFEB3B : 0x994CFF4C);
                c.drawCircle(q[i * 2], q[i * 2 + 1], r, punto);
                punto.setColor(Color.WHITE);
                c.drawCircle(q[i * 2], q[i * 2 + 1], r / 4, punto);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float[] t = {e.getX(), e.getY()};
            inv.mapPoints(t);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float mejor = Float.MAX_VALUE;
                    agarrado = -1;
                    float lim = inv.mapRadius(Util.dp(getContext(), 50));
                    for (int i = 0; i < 4; i++) {
                        float dd = (float) Math.hypot(p[i].x - t[0], p[i].y - t[1]);
                        if (dd < mejor && dd < lim) { mejor = dd; agarrado = i; }
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (agarrado >= 0) {
                        p[agarrado].set(Math.max(0, Math.min(bm.getWidth(), t[0])), Math.max(0, Math.min(bm.getHeight(), t[1])));
                        invalidate();
                    }
                    return true;
                default:
                    agarrado = -1;
                    invalidate();
                    return true;
            }
        }
    }

    /** Endereza la hoja (quita la perspectiva) y aplica el filtro. */
    static Bitmap enderezar(Bitmap src, PointF[] p, int filtro) {
        double arriba = Math.hypot(p[1].x - p[0].x, p[1].y - p[0].y), abajo = Math.hypot(p[2].x - p[3].x, p[2].y - p[3].y);
        double izq = Math.hypot(p[3].x - p[0].x, p[3].y - p[0].y), der = Math.hypot(p[2].x - p[1].x, p[2].y - p[1].y);
        double w = Math.max(arriba, abajo), h = Math.max(izq, der);
        double esc = Math.min(1, 2000 / Math.max(w, h));
        int ow = Math.max(10, (int) (w * esc)), oh = Math.max(10, (int) (h * esc));
        Matrix m = new Matrix();
        m.setPolyToPoly(new float[]{p[0].x, p[0].y, p[1].x, p[1].y, p[2].x, p[2].y, p[3].x, p[3].y}, 0,
                new float[]{0, 0, ow, 0, ow, oh, 0, oh}, 0, 4);
        Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        Paint pa = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        if (filtro > 0) {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0);
            if (filtro == 2) {
                // Más contraste: el papel queda blanco y la letra negra
                float k = 2.2f, b = -255f * 0.55f;
                ColorMatrix con = new ColorMatrix(new float[]{k, 0, 0, 0, b, 0, k, 0, 0, b, 0, 0, k, 0, b, 0, 0, 0, 1, 0});
                cm.postConcat(con);
            } else {
                float k = 1.25f, b = -255f * 0.1f;
                cm.postConcat(new ColorMatrix(new float[]{k, 0, 0, 0, b, 0, k, 0, 0, b, 0, 0, k, 0, b, 0, 0, 0, 1, 0}));
            }
            pa.setColorFilter(new ColorMatrixColorFilter(cm));
        }
        c.drawBitmap(src, m, pa);
        return out;
    }

    private void procesar() {
        aviso("✔ Enderezando la hoja…");
        final PointF[] pts = recorte.p.clone();
        final Bitmap src = foto;
        new Thread(() -> {
            try {
                Bitmap hoja = enderezar(src, pts, filtro);
                File f = new File(new File(getCacheDir(), "escaner"), "pag_" + System.currentTimeMillis() + ".jpg");
                try (FileOutputStream o = new FileOutputStream(f)) { hoja.compress(Bitmap.CompressFormat.JPEG, 90, o); }
                hoja.recycle();
                ui.post(() -> { paginas.add(f); foto = null; src.recycle(); pantallaPaginas(); });
            } catch (Throwable t) {
                ui.post(() -> aviso("No se pudo enderezar: " + t.getMessage()));
            }
        }).start();
    }

    // ================= 3. Páginas: guardar PDF, imágenes, WhatsApp =================

    private void pantallaPaginas() {
        enCamara = false;
        cerrarCamara();
        LinearLayout raiz = vertical();
        raiz.setPadding(dp(10), dp(10), dp(10), dp(10));
        raiz.addView(titulo("📄 Escaneo · " + paginas.size() + (paginas.size() == 1 ? " página" : " páginas")));
        LinearLayout tiras = horizontal();
        for (int i = 0; i < paginas.size(); i++) {
            final int k = i;
            LinearLayout c = vertical();
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView iv = new ImageView(this);
            BitmapFactory.Options op = new BitmapFactory.Options();
            op.inSampleSize = 6;
            iv.setImageBitmap(BitmapFactory.decodeFile(paginas.get(i).getAbsolutePath(), op));
            iv.setAdjustViewBounds(true);
            iv.setBackgroundColor(0xFFCFD8DC);
            iv.setPadding(dp(2), dp(2), dp(2), dp(2));
            c.addView(iv, new LinearLayout.LayoutParams(dp(150), dp(200)));
            Button q = boton("🗑 Quitar " + (i + 1), 0xFF8D6E63);
            q.setTextSize(12);
            q.setOnClickListener(v -> { paginas.remove(k).delete(); pantallaPaginas(); });
            c.addView(q, new LinearLayout.LayoutParams(dp(150), dp(42)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            tiras.addView(c, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(tiras);
        raiz.addView(hs);
        // Versión 44: lo principal es mandar FOTOS por WhatsApp (la gente las ve sin abrir nada).
        // El PDF queda para mandar por correo.
        Button wi = boton("📲  WhatsApp · mandar como FOTO" + (paginas.size() == 1 ? "" : "S (" + paginas.size() + ")"), 0xFF25A244);
        wi.setTextSize(19);
        wi.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        wi.setOnClickListener(v -> { Uri[] us = guardarImagenes(); if (us != null) enviar(WHATSAPP, "image/jpeg", us); });
        raiz.addView(wi, ancho(72));
        Button mas = boton("➕  Escanear otra página", 0xFF1565C0);
        mas.setOnClickListener(v -> pantallaCamara());
        raiz.addView(mas, ancho(54));
        Button correo = boton("📧  Correo · mandar en PDF (Gmail u Hotmail)", 0xFFC62828);
        correo.setOnClickListener(v -> {
            Uri u = hacerPdf();
            if (u == null) return;
            Intent i = new Intent(Intent.ACTION_SEND).setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, u)
                    .putExtra(Intent.EXTRA_SUBJECT, "Documento escaneado " + Util.fecha().substring(0, 10))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(Intent.createChooser(i, "¿Con qué correo lo manda?")); } catch (Exception e) { aviso("No hay app de correo"); }
        });
        raiz.addView(correo, ancho(54));
        LinearLayout otros = horizontal();
        Button img = boton("🖼 Guardar fotos", 0xFF00838F);
        img.setOnClickListener(v -> guardarImagenes());
        otros.addView(img, peso(1));
        Button pdf = boton("💾 Guardar PDF", 0xFF2E7D32);
        pdf.setOnClickListener(v -> { Uri u = hacerPdf(); if (u != null) aviso("💾 PDF guardado en Descargas/MiBarra/Escaneos"); });
        otros.addView(pdf, peso(1));
        Button wa = boton("💬 PDF a WhatsApp", 0xFF128C7E);
        wa.setOnClickListener(v -> { Uri u = hacerPdf(); if (u != null) enviar(WHATSAPP, "application/pdf", u); });
        otros.addView(wa, peso(1));
        raiz.addView(otros, ancho(54));
        Button nuevo = boton("✖  Empezar de nuevo", 0xFF6D4C41);
        nuevo.setOnClickListener(v -> { for (File f : paginas) f.delete(); paginas.clear(); pantallaCamara(); });
        raiz.addView(nuevo, ancho(50));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(raiz);
        setContentView(sv);
    }

    private Uri hacerPdf() {
        if (paginas.isEmpty()) { aviso("No hay páginas"); return null; }
        PdfDocument doc = new PdfDocument();
        try {
            for (int i = 0; i < paginas.size(); i++) {
                BitmapFactory.Options op = new BitmapFactory.Options();
                op.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(paginas.get(i).getAbsolutePath(), op);
                op.inJustDecodeBounds = false;
                op.inSampleSize = Math.max(op.outWidth, op.outHeight) > 2600 ? 2 : 1;
                Bitmap b = BitmapFactory.decodeFile(paginas.get(i).getAbsolutePath(), op);
                // Hoja carta (612 x 792 puntos), la imagen centrada
                boolean acostada = b.getWidth() > b.getHeight();
                int pw = acostada ? 792 : 612, ph = acostada ? 612 : 792;
                PdfDocument.Page pg = doc.startPage(new PdfDocument.PageInfo.Builder(pw, ph, i + 1).create());
                Canvas c = pg.getCanvas();
                c.drawColor(Color.WHITE);
                float esc = Math.min((pw - 24f) / b.getWidth(), (ph - 24f) / b.getHeight());
                float w = b.getWidth() * esc, h = b.getHeight() * esc;
                c.drawBitmap(b, null, new RectF((pw - w) / 2, (ph - h) / 2, (pw + w) / 2, (ph + h) / 2),
                        new Paint(Paint.FILTER_BITMAP_FLAG));
                doc.finishPage(pg);
                b.recycle();
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            doc.writeTo(bo);
            return Util.guardarBytes(this, "Escaneo_" + Util.fecha() + ".pdf", "application/pdf", bo.toByteArray());
        } catch (Exception e) {
            aviso("No se pudo hacer el PDF: " + e.getMessage());
            return null;
        } finally {
            doc.close();
        }
    }

    private Uri[] guardarImagenes() {
        if (paginas.isEmpty()) { aviso("No hay páginas"); return null; }
        Uri[] us = new Uri[paginas.size()];
        String f = Util.fecha();
        try {
            for (int i = 0; i < paginas.size(); i++) {
                byte[] d = java.nio.file.Files.readAllBytes(paginas.get(i).toPath());
                us[i] = Util.guardarBytes(this, "Escaneo_" + f + "_p" + (i + 1) + ".jpg", "image/jpeg", d);
            }
            aviso("🖼 Fotos guardadas en la Galería (MiBarra/Escaneos)");
            return us;
        } catch (Exception e) {
            aviso("No se pudieron guardar: " + e.getMessage());
            return null;
        }
    }
}
