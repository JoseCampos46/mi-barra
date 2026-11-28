package co.jose.mibarra;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/** Escáner de códigos QR y de barras: apunta la cámara y listo. También lee un código de una foto. */
public class CodigoActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextureView textura;
    private CameraDevice camara;
    private CameraCaptureSession sesion;
    private ImageReader lector;
    private HandlerThread hilo;
    private Handler fondo;
    private CaptureRequest.Builder vista;
    private Size tamVista, tamAnalisis;
    private boolean luz, leido;
    private long ultimo;
    private final MultiFormatReader lectorCodigos = new MultiFormatReader();
    private TextView resultado;
    private LinearLayout tarjeta;
    private static final int FOTO = 3;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void aviso(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        lectorCodigos.setHints(hints);
        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.BLACK);
        textura = new TextureView(this);
        raiz.addView(textura, new FrameLayout.LayoutParams(-1, -1));
        raiz.addView(new Mira(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout arriba = new LinearLayout(this);
        arriba.setPadding(dp(12), dp(36), dp(12), 0);
        arriba.setGravity(Gravity.CENTER_VERTICAL);
        TextView luzB = chip("🔦 Luz");
        luzB.setOnClickListener(v -> { luz = !luz; luzB.setAlpha(luz ? 1f : 0.6f); repetir(); });
        luzB.setAlpha(0.6f);
        arriba.addView(luzB);
        TextView foto = chip("🖼 Desde una foto");
        foto.setOnClickListener(v -> {
            try { startActivityForResult(Intent.createChooser(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), "Foto con el código"), FOTO); }
            catch (Exception e) { aviso("No hay galería"); }
        });
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-2, -2);
        fp.leftMargin = dp(8);
        arriba.addView(foto, fp);
        View esp = new View(this);
        arriba.addView(esp, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView x = chip("✕");
        x.setOnClickListener(v -> finish());
        arriba.addView(x);
        raiz.addView(arriba, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        // Tarjeta del resultado
        tarjeta = new LinearLayout(this);
        tarjeta.setOrientation(LinearLayout.VERTICAL);
        tarjeta.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF2FFFFFF);
        g.setCornerRadius(dp(20));
        tarjeta.setBackground(g);
        resultado = new TextView(this);
        resultado.setTextColor(0xFF111111);
        resultado.setTextSize(18);
        resultado.setTextIsSelectable(true);
        tarjeta.addView(resultado);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        tp.setMargins(dp(12), 0, dp(12), dp(28));
        tarjeta.setVisibility(View.GONE);
        raiz.addView(tarjeta, tp);
        TextView ayuda = new TextView(this);
        ayuda.setText("Ponga el código dentro del cuadro");
        ayuda.setTextColor(Color.WHITE);
        ayuda.setTextSize(16);
        ayuda.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        ap.bottomMargin = dp(40);
        raiz.addView(ayuda, ap);
        setContentView(raiz);
        textura.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { abrir(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
    }

    private TextView chip(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(16);
        v.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x88000000);
        g.setCornerRadius(dp(20));
        v.setBackground(g);
        return v;
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] res) {
        super.onRequestPermissionsResult(r, p, res);
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) { if (textura.isAvailable()) abrir(); } else finish();
    }
    @Override protected void onResume() { super.onResume(); if (textura.isAvailable()) abrir(); }
    @Override protected void onPause() { cerrar(); super.onPause(); }

    /** El cuadro de mira en el centro. */
    static final class Mira extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Mira(android.content.Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), lado = w * 0.72f, l = (w - lado) / 2, t = h * 0.42f - lado / 2;
            p.setColor(0x66000000);
            c.drawRect(0, 0, w, t, p); c.drawRect(0, t + lado, w, h, p);
            c.drawRect(0, t, l, t + lado, p); c.drawRect(l + lado, t, w, t + lado, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            p.setColor(0xFF4CFF4C);
            c.drawRoundRect(new RectF(l, t, l + lado, t + lado), 24, 24, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private static Size mejor(Size[] ops, int maxLado) {
        Size r = null;
        for (Size s : ops) {
            int l = Math.max(s.getWidth(), s.getHeight());
            if (l > maxLado) continue;
            if (r == null || (long) s.getWidth() * s.getHeight() > (long) r.getWidth() * r.getHeight()) r = s;
        }
        return r != null ? r : ops[0];
    }

    private void abrir() {
        if (camara != null || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            CameraManager cm = getSystemService(CameraManager.class);
            String id = null;
            StreamConfigurationMap mapa = null;
            for (String i : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(i);
                Integer lado = c.get(CameraCharacteristics.LENS_FACING);
                if (lado != null && lado == CameraCharacteristics.LENS_FACING_BACK) { id = i; mapa = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP); break; }
            }
            if (id == null) { aviso("No encontré la cámara"); return; }
            tamVista = mejor(mapa.getOutputSizes(SurfaceTexture.class), 1920);
            tamAnalisis = mejor(mapa.getOutputSizes(ImageFormat.YUV_420_888), 1280);
            hilo = new HandlerThread("codigos");
            hilo.start();
            fondo = new Handler(hilo.getLooper());
            lector = ImageReader.newInstance(tamAnalisis.getWidth(), tamAnalisis.getHeight(), ImageFormat.YUV_420_888, 2);
            lector.setOnImageAvailableListener(this::analizar, fondo);
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) { camara = c; sesion(); }
                @Override public void onDisconnected(CameraDevice c) { c.close(); camara = null; }
                @Override public void onError(CameraDevice c, int e) { c.close(); camara = null; ui.post(() -> aviso("La cámara está ocupada")); }
            }, fondo);
        } catch (Exception e) { aviso("No se pudo abrir la cámara"); }
    }

    private void sesion() {
        try {
            SurfaceTexture st = textura.getSurfaceTexture();
            st.setDefaultBufferSize(tamVista.getWidth(), tamVista.getHeight());
            Surface s = new Surface(st);
            vista = camara.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            vista.addTarget(s);
            vista.addTarget(lector.getSurface());
            camara.createCaptureSession(Arrays.asList(s, lector.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession ss) { sesion = ss; repetir(); }
                @Override public void onConfigureFailed(CameraCaptureSession ss) {}
            }, fondo);
        } catch (Exception ignored) {}
    }

    private void repetir() {
        if (sesion == null) return;
        try {
            vista.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            vista.set(CaptureRequest.FLASH_MODE, luz ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            sesion.setRepeatingRequest(vista.build(), null, fondo);
        } catch (Exception ignored) {}
    }

    private void analizar(ImageReader r) {
        Image im = r.acquireLatestImage();
        if (im == null) return;
        try {
            if (leido || System.currentTimeMillis() - ultimo < 200) return;
            ultimo = System.currentTimeMillis();
            Image.Plane y = im.getPlanes()[0];
            int w = im.getWidth(), h = im.getHeight(), paso = y.getRowStride();
            ByteBuffer bb = y.getBuffer();
            byte[] datos = new byte[w * h];
            for (int fila = 0; fila < h; fila++) { bb.position(fila * paso); bb.get(datos, fila * w, w); }
            Result res = null;
            try { res = lectorCodigos.decodeWithState(new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(datos, w, h, 0, 0, w, h, false)))); }
            catch (Exception ignored) {}
            finally { lectorCodigos.reset(); }
            if (res == null) {
                // probar girado (códigos de barras parados)
                byte[] g = new byte[w * h];
                for (int f = 0; f < h; f++) for (int c = 0; c < w; c++) g[c * h + (h - 1 - f)] = datos[f * w + c];
                try { res = lectorCodigos.decodeWithState(new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(g, h, w, 0, 0, h, w, false)))); }
                catch (Exception ignored) {}
                finally { lectorCodigos.reset(); }
            }
            if (res != null) { leido = true; final Result fr = res; ui.post(() -> mostrar(fr.getText(), String.valueOf(fr.getBarcodeFormat()))); }
        } catch (Exception ignored) {
        } finally { im.close(); }
    }

    @Override
    protected void onActivityResult(int r, int res, Intent d) {
        super.onActivityResult(r, res, d);
        if (r != FOTO || res != RESULT_OK || d == null || d.getData() == null) return;
        try {
            Bitmap bm = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(getContentResolver(), d.getData()),
                    (dec, info, src) -> {
                        int w = info.getSize().getWidth(), hh = info.getSize().getHeight(), lado = Math.max(w, hh);
                        if (lado > 1600) dec.setTargetSize(w * 1600 / lado, hh * 1600 / lado);
                        dec.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
            int[] px = new int[bm.getWidth() * bm.getHeight()];
            bm.getPixels(px, 0, bm.getWidth(), 0, 0, bm.getWidth(), bm.getHeight());
            Result rr = lectorCodigos.decodeWithState(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(bm.getWidth(), bm.getHeight(), px))));
            leido = true;
            mostrar(rr.getText(), String.valueOf(rr.getBarcodeFormat()));
        } catch (Exception e) { aviso("No encontré ningún código en esa foto"); }
    }

    private void mostrar(String texto, String tipo) {
        // Versión 18: el buscador de precios pide el código y se lo devuelve
        if (getIntent().getBooleanExtra("devolver", false)) {
            setResult(RESULT_OK, new Intent().putExtra("codigo", texto));
            finish();
            return;
        }
        try { new ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150); } catch (Exception ignored) {}
        resultado.setText((tipo.startsWith("QR") ? "🔳 Código QR" : "▥ Código de barras") + "\n\n" + texto);
        while (tarjeta.getChildCount() > 1) tarjeta.removeViewAt(1);
        LinearLayout f = new LinearLayout(this);
        boolean url = texto.startsWith("http://") || texto.startsWith("https://") || texto.startsWith("www.");
        boolean numero = texto.matches("\\d{8,14}");
        if (url) f.addView(accion("🌐 Abrir", 0xFF1565C0, () -> abrirUrl(texto.startsWith("www.") ? "https://" + texto : texto)), peso());
        if (numero) f.addView(accion("🔎 Buscar producto", 0xFF1565C0, () -> abrirUrl("https://www.google.com/search?q=" + Uri.encode(texto))), peso());
        if (!url && !numero) f.addView(accion("🔎 Buscar", 0xFF1565C0, () -> abrirUrl("https://www.google.com/search?q=" + Uri.encode(texto))), peso());
        f.addView(accion("📋 Copiar", 0xFF455A64, () -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("código", texto));
            aviso("Copiado");
        }), peso());
        f.addView(accion("💬", 0xFF25A244, () -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto);
            try { startActivity(Intent.createChooser(i, "Compartir")); } catch (Exception ignored) {}
        }), peso());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(12);
        tarjeta.addView(f, lp);
        TextView otro = accion("🔄 Leer otro código", 0xFF2E7D32, () -> { leido = false; tarjeta.setVisibility(View.GONE); });
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, dp(50));
        op.topMargin = dp(8);
        tarjeta.addView(otro, op);
        tarjeta.setVisibility(View.VISIBLE);
    }

    private LinearLayout.LayoutParams peso() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private TextView accion(String t, int color, Runnable r) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(15);
        v.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        v.setBackground(g);
        v.setOnClickListener(x -> r.run());
        return v;
    }

    private void abrirUrl(String u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception e) { aviso("No se pudo abrir"); }
    }

    private void cerrar() {
        try { if (sesion != null) sesion.close(); } catch (Exception ignored) {}
        sesion = null;
        if (camara != null) { camara.close(); camara = null; }
        if (lector != null) { lector.close(); lector = null; }
        if (hilo != null) { hilo.quitSafely(); hilo = null; }
    }
}
