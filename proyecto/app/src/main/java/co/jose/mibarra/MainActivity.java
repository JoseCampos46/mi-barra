package co.jose.mibarra;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Pantalla principal: activar la barra, permisos y herramientas de PDF. */
public class MainActivity extends VentanaActivity {
    private static final int UNIR = 10, SEPARAR = 11, FOTOS = 12;
    private TextView estado;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        l.setPadding(p, p, p, p);

        l.addView(texto("ChpBar v" + Util.version(this), 26, true));
        estado = texto("", 17, true);
        l.addView(estado);

        l.addView(texto("\nPrimera vez", 20, true));
        l.addView(boton("1️⃣  Activar la barra", 0xFF1565C0, () ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        l.addView(texto("Busque «Mi Barra» en la lista (en Xiaomi está en «Aplicaciones descargadas»), "
                + "actívela y acepte. Le aparecerá la pestañita en el borde izquierdo.", 15, false));
        l.addView(boton("2️⃣  Permitir notificaciones y micrófono (para grabar)", 0xFF1565C0, this::pedirPermisos));
        l.addView(boton("3️⃣  Ajustes de esta app", 0xFF1565C0, () ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())))));
        l.addView(texto("Xiaomi: en Ajustes de esta app active «Inicio automático», ponga Ahorro de batería en "
                + "«Sin restricciones» y en Otros permisos permita «Mostrar ventanas emergentes al ejecutarse "
                + "en segundo plano».\n\nMotorola: si al activar dice «Configuración restringida», entre a "
                + "Ajustes de esta app, toque los tres puntos ⋮ arriba a la derecha, escoja «Permitir "
                + "configuración restringida» y vuelva al paso 1.", 15, false));

        l.addView(texto("\n📑 PDF", 20, true));
        l.addView(boton("Unir varios PDF en uno", 0xFF6A1B9A, () -> elegir("application/pdf", true, UNIR)));
        l.addView(boton("Separar un PDF en imágenes PNG", 0xFF6A1B9A, () -> elegir("application/pdf", false, SEPARAR)));
        l.addView(boton("Fotos o pantallazos a un PDF", 0xFF6A1B9A, () -> elegir("image/*", true, FOTOS)));
        l.addView(texto("Los PDF quedan como imágenes de cada página: así la app sigue liviana.", 14, false));

        l.addView(texto("\n🏍️ En la vía (barra derecha)", 20, true));
        l.addView(boton("🛣️ Recorrido", 0xFF2E7D32, () -> startActivity(new Intent(this, RecorridoActivity.class))));
        l.addView(boton("📍 Aquí estoy por WhatsApp", 0xFF25A244, () -> startActivity(new Intent(this, AquiEstoyActivity.class))));

        l.addView(texto("\n🧮 Calculadora de obra", 20, true));
        l.addView(boton("Básica · mm ↔ pulgadas · Concreto · Ladrillo", 0xFF00695C, () ->
                startActivity(new Intent(this, CalculadoraActivity.class))));

        l.addView(texto("\n📝 Cuaderno, cuentas y cobros", 20, true));
        l.addView(boton("Cuaderno (mini Word)", 0xFF1565C0, () -> startActivity(new Intent(this, NotasActivity.class))));
        l.addView(boton("Cuentas (mini Excel)", 0xFF1565C0, () -> startActivity(new Intent(this, HojaActivity.class))));
        l.addView(boton("Cuenta de cobro", 0xFF1565C0, () -> startActivity(new Intent(this, CobroActivity.class))));

        l.addView(texto("\n🛒 Compras", 20, true));
        l.addView(boton("Lista de compras", 0xFF2E7D32, () ->
                startActivity(new Intent(this, ComprasActivity.class))));

        l.addView(texto("\nTodo lo que guarda la barra queda en la carpeta Descargas/MiBarra.", 15, false));

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean activa = s != null && s.contains(getPackageName());
        estado.setText(activa ? "✅ La barra está activa" : "⚠️ La barra está apagada: haga el paso 1");
        estado.setTextColor(activa ? 0xFF2E7D32 : 0xFFC62828);
    }

    private void pedirPermisos() {
        List<String> ps = new ArrayList<>();
        ps.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) ps.add(Manifest.permission.POST_NOTIFICATIONS);
        requestPermissions(ps.toArray(new String[0]), 1);
    }

    // ---------------- PDF ----------------

    private void elegir(String tipo, boolean varios, int codigo) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(tipo);
        if (varios) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, codigo);
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent d) {
        super.onActivityResult(pedido, resultado, d);
        if (resultado != RESULT_OK || d == null) return;
        List<Uri> us = new ArrayList<>();
        if (d.getClipData() != null) {
            for (int i = 0; i < d.getClipData().getItemCount(); i++) us.add(d.getClipData().getItemAt(i).getUri());
        } else if (d.getData() != null) {
            us.add(d.getData());
        }
        if (us.isEmpty()) return;
        aviso("⏳ Procesando, espere un momento…");
        new Thread(() -> {
            String m;
            try {
                if (pedido == UNIR) m = unir(us);
                else if (pedido == SEPARAR) m = separar(us.get(0));
                else m = fotos(us);
            } catch (Throwable e) {
                m = "Error: " + e.getMessage();
            }
            final String msj = m;
            runOnUiThread(() -> aviso(msj));
        }).start();
    }

    private Bitmap pintar(PdfRenderer.Page pg) {
        Bitmap b = Bitmap.createBitmap(pg.getWidth() * 2, pg.getHeight() * 2, Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
        return b;
    }

    private String unir(List<Uri> us) throws IOException {
        PdfDocument doc = new PdfDocument();
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        int n = 0;
        for (Uri u : us) {
            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(u, "r");
                 PdfRenderer pr = new PdfRenderer(pfd)) {
                for (int i = 0; i < pr.getPageCount(); i++) {
                    try (PdfRenderer.Page pg = pr.openPage(i)) {
                        int w = pg.getWidth(), h = pg.getHeight();
                        Bitmap b = pintar(pg);
                        PdfDocument.Page np = doc.startPage(new PdfDocument.PageInfo.Builder(w, h, ++n).create());
                        np.getCanvas().drawBitmap(b, null, new Rect(0, 0, w, h), paint);
                        doc.finishPage(np);
                        b.recycle();
                    }
                }
            }
        }
        escribir(doc, "PDF_unido_" + Util.fecha() + ".pdf");
        return "✅ PDF unido (" + n + " páginas) guardado en Descargas";
    }

    private String separar(Uri u) throws IOException {
        String base = nombreDe(u).replaceAll("(?i)\\.pdf$", "").replaceAll("[^\\p{L}\\p{N}]+", "_");
        int n;
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(u, "r");
             PdfRenderer pr = new PdfRenderer(pfd)) {
            n = pr.getPageCount();
            for (int i = 0; i < n; i++) {
                try (PdfRenderer.Page pg = pr.openPage(i)) {
                    Bitmap b = pintar(pg);
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    b.compress(Bitmap.CompressFormat.PNG, 100, o);
                    b.recycle();
                    Util.guardarBytes(this, base + "_pag" + (i + 1) + ".png", "image/png", o.toByteArray());
                }
            }
        }
        return "✅ " + n + " páginas guardadas como imágenes PNG en Descargas";
    }

    private String fotos(List<Uri> us) throws IOException {
        PdfDocument doc = new PdfDocument();
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        final int W = 595, H = 842, M = 20; // hoja carta/A4 aproximada
        int n = 0;
        for (Uri u : us) {
            Bitmap b = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), u),
                    (dec, info, src) -> {
                        int mx = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                        if (mx > 2000) dec.setTargetSampleSize((int) Math.ceil(mx / 2000.0));
                        dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
            float esc = Math.min((W - 2f * M) / b.getWidth(), (H - 2f * M) / b.getHeight());
            float bw = b.getWidth() * esc, bh = b.getHeight() * esc;
            float x = (W - bw) / 2f, y = (H - bh) / 2f;
            PdfDocument.Page pg = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, ++n).create());
            Canvas c = pg.getCanvas();
            c.drawBitmap(b, null, new RectF(x, y, x + bw, y + bh), paint);
            doc.finishPage(pg);
            b.recycle();
        }
        escribir(doc, "Fotos_" + Util.fecha() + ".pdf");
        return "✅ PDF con " + n + " fotos guardado en Descargas";
    }

    private void escribir(PdfDocument doc, String nombre) throws IOException {
        Uri out = Util.crearEnDescargas(this, nombre, "application/pdf");
        if (out == null) { doc.close(); throw new IOException("No se pudo crear el archivo"); }
        try (OutputStream o = getContentResolver().openOutputStream(out)) {
            doc.writeTo(o);
        } finally {
            doc.close();
        }
        Util.terminar(this, out);
    }

    private String nombreDe(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return "PDF";
    }

    // ---------------- Ayudas ----------------

    private void aviso(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private int dp(float v) { return Util.dp(this, v); }

    private TextView texto(String t, int tam, boolean negrita) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(tam);
        if (negrita) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private Button boton(String t, int color, Runnable r) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        b.setBackground(g);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        p.topMargin = dp(8);
        b.setLayoutParams(p);
        return b;
    }
}
