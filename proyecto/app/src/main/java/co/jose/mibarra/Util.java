package co.jose.mibarra;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Funciones de ayuda: guardar en Descargas, pesos, números y archivos Word. */
public final class Util {
    private Util() {}

    /**
     * Fotos e imágenes van a la Galería (Imágenes/MiBarra/…), videos a Películas/MiBarra/…,
     * y lo demás (Word, PDF, audio…) a Descargas/MiBarra/…
     */
    public static Uri crearEnDescargas(Context c, String nombre, String mime) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, nombre);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        String carpeta = carpetaPara(c, nombre);
        Uri donde;
        if (mime.startsWith("image/")) {
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MiBarra/" + carpeta);
            donde = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else if (mime.startsWith("video/")) {
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MiBarra/" + carpeta);
            donde = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else {
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MiBarra/" + carpeta);
            donde = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        }
        return c.getContentResolver().insert(donde, v);
    }

    /** Cada función guarda en su propia carpeta dentro de Descargas/MiBarra. */
    static String carpetaPara(Context c, String nombre) {
        if (c instanceof EscribirActivity) return "MiVoz";
        if (c instanceof RadioRelojActivity) return "Mensajes";
        if (c instanceof CuentaActivity) return "Recibos";
        if (c instanceof NotasActivity) return "Cuaderno";
        if (c instanceof HojaActivity) return "Cuentas";
        if (c instanceof CobroActivity) return "Cobros";
        if (c instanceof ComprasActivity) return "Compras";
        if (c instanceof MainActivity) return "PDF";
        if (c instanceof GrabarService) return "Videos";
        if (c instanceof RecorridoActivity || c instanceof RecorridoService) return "Recorridos";
        if (c instanceof AquiEstoyActivity) return "Ubicacion";
        if (c instanceof GrabadoraService || c instanceof RecortarActivity) return "Grabadora";
        if (c instanceof EscanerActivity) return "Escaneos";
        if (c instanceof ClaudeActivity) return "Claude/Enviados";
        if (c instanceof GastosActivity) return "Gastos";
        if (c instanceof MedidasActivity) return "Medidas";
        if (c instanceof MedirActivity) return "Medir";
        if (c instanceof SaludosActivity) return nombre.startsWith("Meme") ? "Memes" : "Saludos";
        if (c instanceof MiniCamaraService) return nombre.endsWith(".mp4") ? "Videos" : "Fotos";
        if (c instanceof CamaraActivity) return nombre.endsWith(".mp4") ? "Videos" : "Fotos";
        if (nombre.startsWith("Pantallazo")) return "Pantallazos";
        if (nombre.startsWith("Copia_")) return "Word";
        return "Otros";
    }

    public static void terminar(Context c, Uri u) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.IS_PENDING, 0);
        c.getContentResolver().update(u, v, null, null);
    }

    public static Uri guardarBytes(Context c, String nombre, String mime, byte[] datos) throws IOException {
        Uri u = crearEnDescargas(c, nombre, mime);
        if (u == null) throw new IOException("No se pudo crear el archivo en Descargas");
        try (OutputStream o = c.getContentResolver().openOutputStream(u)) {
            if (o == null) throw new IOException("No se pudo escribir el archivo");
            o.write(datos);
        }
        terminar(c, u);
        return u;
    }

    public static String fecha() {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
    }

    public static String pesos(long v) {
        return "$ " + NumberFormat.getIntegerInstance(new Locale("es", "CO")).format(v);
    }

    /** Cantidad: acepta 1,5 o 1.5. Vacío = 1. */
    public static double cantidad(String s) {
        s = s.trim().replace(',', '.');
        if (s.isEmpty()) return 1;
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    /** Valor en pesos: ignora puntos y comas (3.500 = 3500). */
    public static long valor(String s) {
        String d = s.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return 0;
        try { return Long.parseLong(d); } catch (Exception e) { return 0; }
    }

    public static String cant(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.valueOf(d).replace('.', ',');
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static String xml(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                default: if (ch >= 0x20 || ch == '\t') b.append(ch);
            }
        }
        return b.toString();
    }

    /** Arma un archivo Word (.docx) sencillo: un título y un párrafo por línea. */
    public static byte[] crearDocx(String titulo, List<String> lineas) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("<w:p><w:r><w:rPr><w:b/><w:sz w:val=\"28\"/></w:rPr><w:t xml:space=\"preserve\">")
         .append(xml(titulo)).append("</w:t></w:r></w:p>");
        for (String l : lineas) {
            b.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(xml(l)).append("</w:t></w:r></w:p>");
        }
        return docxConCuerpo(b.toString());
    }

    /** Arma el .docx con párrafos ya escritos en el formato de Word. */
    public static byte[] docxConCuerpo(String cuerpo) throws IOException {
        String doc = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
            + cuerpo
            + "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/>"
            + "<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"0\" w:footer=\"0\" w:gutter=\"0\"/>"
            + "</w:sectPr></w:body></w:document>";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            poner(z, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>");
            poner(z, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "</Relationships>");
            poner(z, "word/document.xml", doc);
        }
        return bo.toByteArray();
    }

    private static void poner(ZipOutputStream z, String nombre, String contenido) throws IOException {
        z.putNextEntry(new ZipEntry(nombre));
        z.write(contenido.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    /** Lee todo un flujo como texto (sirve también en Android 11 y 12). */
    static String leerTodo(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return new String(bo.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Número de versión de Mi Barra (ej. "43"). */
    public static String version(android.content.Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    /**
     * Versión 44: toda pantalla, sin excepción, tiene su ✕ arriba a la derecha para cerrar.
     * Se usa en las pantallas que no tienen la barra de arriba (pantalla completa).
     */
    public static void ponerX(final android.app.Activity a) {
        android.view.View raiz = a.findViewById(android.R.id.content);
        if (!(raiz instanceof android.widget.FrameLayout)) return;
        android.widget.TextView x = new android.widget.TextView(a);
        x.setText("✕");
        x.setTextColor(0xFFFFFFFF);
        x.setTextSize(18);
        x.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        x.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(0xFFC62828);
        g.setStroke(dp(a, 2), 0xFFFFFFFF);
        x.setBackground(g);
        x.setElevation(dp(a, 12));
        x.setContentDescription("Cerrar");
        x.setOnClickListener(v -> a.finishAndRemoveTask());
        int lado = dp(a, 38);
        android.widget.FrameLayout.LayoutParams p = new android.widget.FrameLayout.LayoutParams(lado, lado,
                android.view.Gravity.TOP | android.view.Gravity.END);
        p.setMargins(0, dp(a, 6), dp(a, 6), 0);
        ((android.widget.FrameLayout) raiz).addView(x, p);
    }
}
