package co.jose.mibarra;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Versión 44: botón ✉ Claude (cuaderno tipo correo).
 * Para: Claude · Asunto · Mensaje · adjuntar fotos, audios o archivos (o tomar una foto).
 * La app lo junta en UN solo archivo .zip liviano en Descargas/MiBarra/Claude/Enviados.
 * Él lo sube a mano a un chat dentro del proyecto de la app en Claude (sin el «compartir» de Android).
 */
public class ClaudeActivity extends Base {

    private static final int ADJUNTAR = 71, FOTO = 72;
    private final List<Uri> adjuntos = new ArrayList<>();
    private EditText asunto, mensaje;
    private LinearLayout lista;
    private File fotoTomada;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("✉ Claude");
        LinearLayout l = vertical();
        l.setPadding(dp(12), dp(10), dp(12), dp(12));

        TextView para = new TextView(this);
        para.setText("Para:  Claude  ·  proyecto ChpBar");
        para.setTextSize(17);
        para.setTypeface(Typeface.DEFAULT_BOLD);
        para.setTextColor(Color.BLACK);
        para.setBackgroundColor(0xFFFFE0B2);
        para.setPadding(dp(10), dp(8), dp(10), dp(8));
        l.addView(para);

        asunto = campo("Asunto (ej: Arreglo del pantallazo)", 1);
        l.addView(asunto);
        mensaje = campo("Escriba o dicte aquí lo que me quiere decir…", 8);
        l.addView(mensaje);

        LinearLayout f = horizontal();
        Button adj = boton("📎 Adjuntar", 0xFF455A64);
        adj.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, ADJUNTAR);
        });
        f.addView(adj, peso(1));
        Button foto = boton("📷 Tomar foto", 0xFF1565C0);
        foto.setOnClickListener(v -> tomarFoto());
        f.addView(foto, peso(1));
        Button esc = boton("📄 Escáner", 0xFF8E24AA);
        esc.setOnClickListener(v -> {
            aviso("Escanee, toque «🖼 Guardar fotos» y vuelva aquí a 📎 Adjuntar");
            startActivity(new Intent(this, EscanerActivity.class));
        });
        f.addView(esc, peso(1));
        l.addView(f, ancho(54));

        lista = vertical();
        l.addView(lista);

        Button crear = boton("📦  Crear el paquete para Claude", 0xFFC15F3C);
        crear.setTextSize(18);
        crear.setTypeface(Typeface.DEFAULT_BOLD);
        crear.setOnClickListener(v -> crearPaquete());
        l.addView(crear, ancho(64));

        TextView ay = new TextView(this);
        ay.setText("Cómo se manda:\n1. Toque «Crear el paquete».\n2. Abra la app de Claude.\n3. Entre al proyecto ChpBar y abra un chat.\n4. Toque ➕ y suba el archivo de Descargas/MiBarra/Claude/Enviados.");
        ay.setTextSize(15);
        ay.setTextColor(Color.BLACK);
        ay.setPadding(dp(4), dp(10), dp(4), dp(4));
        l.addView(ay);

        Button borrar = boton("🗑  Borrar paquetes ya enviados (liberar espacio)", 0xFF6D4C41);
        borrar.setOnClickListener(v -> borrarEnviados());
        l.addView(borrar, ancho(50));

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
        pintarLista();
    }

    private EditText campo(String pista, int lineas) {
        EditText e = new EditText(this);
        e.setHint(pista);
        e.setTextSize(17);
        e.setTextColor(Color.BLACK);
        e.setMinLines(lineas);
        e.setGravity(Gravity.TOP | Gravity.START);
        if (lineas == 1) e.setSingleLine(true);
        return e;
    }

    private void tomarFoto() {
        try {
            File d = new File(getCacheDir(), "claude");
            d.mkdirs();
            fotoTomada = new File(d, "foto_" + Util.fecha() + ".jpg");
            Uri u = androidx_fileUri(fotoTomada);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, u)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i, FOTO);
        } catch (Exception e) {
            aviso("No se pudo abrir la cámara: " + e.getMessage());
        }
    }

    /** La foto se guarda primero en la Galería (MiBarra/Claude) y de ahí se adjunta. */
    private Uri androidx_fileUri(File f) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
        v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MiBarra/Claude");
        Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        ultimaFoto = u;
        return u;
    }

    private Uri ultimaFoto;

    @Override
    protected void onActivityResult(int r, int res, Intent d) {
        super.onActivityResult(r, res, d);
        if (res != RESULT_OK) {
            if (r == FOTO && ultimaFoto != null) try { getContentResolver().delete(ultimaFoto, null, null); } catch (Exception ignored) {}
            return;
        }
        if (r == FOTO && ultimaFoto != null) adjuntos.add(ultimaFoto);
        if (r == ADJUNTAR && d != null) {
            if (d.getClipData() != null)
                for (int i = 0; i < d.getClipData().getItemCount(); i++) adjuntos.add(d.getClipData().getItemAt(i).getUri());
            else if (d.getData() != null) adjuntos.add(d.getData());
        }
        pintarLista();
    }

    private void pintarLista() {
        lista.removeAllViews();
        for (int i = 0; i < adjuntos.size(); i++) {
            final int k = i;
            LinearLayout f = horizontal();
            f.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(this);
            t.setText("📎 " + nombre(adjuntos.get(i)));
            t.setTextSize(15);
            t.setTextColor(Color.BLACK);
            f.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            Button q = boton("✕", 0xFFC62828);
            q.setOnClickListener(v -> { adjuntos.remove(k); pintarLista(); });
            f.addView(q, new LinearLayout.LayoutParams(dp(48), dp(42)));
            lista.addView(f);
        }
    }

    private String nombre(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        String s = u.getLastPathSegment();
        return s == null ? "archivo" : s;
    }

    /** Un solo .zip liviano: las fotos se achican (máx. 1600 px, JPG) para no llenar el chat. */
    private void crearPaquete() {
        String as = asunto.getText().toString().trim(), me = mensaje.getText().toString().trim();
        if (as.isEmpty() && me.isEmpty() && adjuntos.isEmpty()) { aviso("Escriba algo o adjunte un archivo"); return; }
        new Thread(() -> {
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (ZipOutputStream z = new ZipOutputStream(bo)) {
                    String txt = "PARA: Claude (proyecto ChpBar)\nDE: Chepe Gallego · ChpBar v" + Util.version(this)
                            + "\nFECHA: " + Util.fecha() + "\nASUNTO: " + as + "\n\n" + me + "\n";
                    z.putNextEntry(new ZipEntry("mensaje.txt"));
                    z.write(txt.getBytes(StandardCharsets.UTF_8));
                    z.closeEntry();
                    int n = 1;
                    for (Uri u : adjuntos) {
                        String nom = nombre(u);
                        String tipo = getContentResolver().getType(u);
                        byte[] datos = (tipo != null && tipo.startsWith("image/")) ? achicar(u) : leer(u);
                        if (datos == null) continue;
                        if (tipo != null && tipo.startsWith("image/")) nom = nom.replaceAll("\\.[^.]+$", "") + ".jpg";
                        z.putNextEntry(new ZipEntry(String.format("%02d_%s", n++, nom)));
                        z.write(datos);
                        z.closeEntry();
                    }
                }
                String base = "Claude_ChpBar_" + Util.fecha() + ".zip";
                Util.guardarBytes(this, base, "application/zip", bo.toByteArray());
                long kb = Math.max(1, bo.size() / 1024);
                runOnUiThread(() -> {
                    aviso("📦 Listo: " + base + " (" + kb + " KB) en Descargas/MiBarra/Claude/Enviados");
                    asunto.setText("");
                    mensaje.setText("");
                    adjuntos.clear();
                    pintarLista();
                    abrirClaude();
                });
            } catch (Exception e) {
                runOnUiThread(() -> aviso("No se pudo crear el paquete: " + e.getMessage()));
            }
        }).start();
    }

    private void abrirClaude() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.anthropic.claude");
        if (i != null) startActivity(i);
    }

    private byte[] leer(Uri u) {
        try (InputStream in = getContentResolver().openInputStream(u)) {
            if (in == null) return null;
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int k;
            while ((k = in.read(buf)) > 0) o.write(buf, 0, k);
            return o.toByteArray();
        } catch (Exception e) { return null; }
    }

    private byte[] achicar(Uri u) {
        byte[] d = leer(u);
        if (d == null) return null;
        BitmapFactory.Options op = new BitmapFactory.Options();
        op.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(d, 0, d.length, op);
        int lado = Math.max(op.outWidth, op.outHeight), s = 1;
        while (lado / (s * 2) >= 1600) s *= 2;
        op = new BitmapFactory.Options();
        op.inSampleSize = s;
        Bitmap b = BitmapFactory.decodeByteArray(d, 0, d.length, op);
        if (b == null) return d;
        float e = Math.min(1f, 1600f / Math.max(b.getWidth(), b.getHeight()));
        if (e < 1f) b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * e), Math.round(b.getHeight() * e), true);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 80, o);
        return o.toByteArray();
    }

    private void borrarEnviados() {
        String rel = Environment.DIRECTORY_DOWNLOADS + "/MiBarra/Claude/Enviados%";
        int n = 0;
        try (Cursor c = getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.MediaColumns._ID}, MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?", new String[]{rel}, null)) {
            if (c != null) while (c.moveToNext()) {
                Uri u = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, String.valueOf(c.getLong(0)));
                try { n += getContentResolver().delete(u, null, null); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        aviso(n == 0 ? "No hay paquetes para borrar" : "🗑 Borrados " + n + " paquetes enviados");
    }
}
