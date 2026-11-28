package co.jose.mibarra;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Versión 41: una sola libreta de direcciones para todo Mi Barra (GPS, Rutas, lo que llega compartido).
 * Antes se guardaba en las preferencias «barra», que también escriben otras partes de Mi Barra
 * que corren aparte (recorrido, mini cámara) y se borraban unas a otras. Ahora es un archivo propio,
 * con copia legible en Descargas/MiBarra/GPS/Mis direcciones.txt.
 */
final class Direcciones {

    private Direcciones() {}

    private static File archivo(Context c) { return new File(c.getFilesDir(), "gps_direcciones.json"); }

    static synchronized JSONArray lista(Context c) {
        try {
            File f = archivo(c);
            if (f.exists()) return new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            // la primera vez: se pasa lo que había en la versión vieja
            JSONArray viejo = new JSONArray(c.getSharedPreferences("barra", Context.MODE_PRIVATE).getString("buscadas", "[]"));
            guardar(c, viejo);
            return viejo;
        } catch (Exception e) { return new JSONArray(); }
    }

    static synchronized void guardar(Context c, JSONArray a) {
        try {
            Files.write(archivo(c).toPath(), a.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        copiaEnDescargas(c, a);
    }

    private static String plano(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** La pone de primera; si ya estaba (mismo punto o misma dirección), no se repite. Máximo 60. */
    static synchronized void agregar(Context c, String dir, String lugar, double lat, double lon) {
        try {
            JSONArray viejo = lista(c), nuevo = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("dir", dir == null ? "" : dir);
            o.put("lugar", lugar == null ? "" : lugar);
            if (!Double.isNaN(lat)) { o.put("lat", lat); o.put("lon", lon); }
            o.put("cuando", System.currentTimeMillis());
            nuevo.put(o);
            for (int i = 0; i < viejo.length() && nuevo.length() < 60; i++) {
                JSONObject x = viejo.getJSONObject(i);
                double xl = x.optDouble("lat"), xo = x.optDouble("lon");
                if (!Double.isNaN(lat) && !Double.isNaN(xl) && Math.abs(xl - lat) < 1e-5 && Math.abs(xo - lon) < 1e-5) continue;
                if (plano(x.optString("dir")).equals(plano(dir)) && plano(x.optString("lugar")).equals(plano(lugar))) continue;
                nuevo.put(x);
            }
            guardar(c, nuevo);
        } catch (Exception ignored) {}
    }

    /** Copia legible para él (y por si reinstala): Descargas/MiBarra/GPS/Mis direcciones.txt */
    private static void copiaEnDescargas(Context c, JSONArray a) {
        StringBuilder sb = new StringBuilder("MIS DIRECCIONES (Mi Barra)\n\n");
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x == null) continue;
            sb.append(i + 1).append(". ").append(x.optString("dir"));
            if (!x.optString("lugar").isEmpty()) sb.append(" · ").append(x.optString("lugar"));
            double la = x.optDouble("lat");
            if (!Double.isNaN(la)) sb.append("\n   https://maps.google.com/?q=")
                    .append(String.format(Locale.US, "%.6f,%.6f", la, x.optDouble("lon")));
            sb.append("\n\n");
        }
        escribirEnDescargas(c, "GPS", "Mis direcciones.txt", "text/plain", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Escribe (o reescribe, sin hacer copias repetidas) un archivo en Descargas/MiBarra/<carpeta>. */
    static void escribirEnDescargas(Context c, String carpeta, String nombre, String mime, byte[] datos) {
        try {
            String rel = Environment.DIRECTORY_DOWNLOADS + "/MiBarra/" + carpeta + "/";
            Uri donde = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri u = null;
            try (Cursor cu = c.getContentResolver().query(donde, new String[]{MediaStore.MediaColumns._ID},
                    MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                    new String[]{nombre, rel}, null)) {
                if (cu != null && cu.moveToFirst()) u = android.content.ContentUris.withAppendedId(donde, cu.getLong(0));
            }
            if (u == null) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, nombre);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
                u = c.getContentResolver().insert(donde, v);
            }
            if (u == null) return;
            try (OutputStream o = c.getContentResolver().openOutputStream(u, "wt")) {
                if (o != null) o.write(datos);
            }
        } catch (Exception ignored) {}
    }

    /** El punto donde se salió de la ruta: en un archivo, porque lo escriben partes que corren aparte. */
    static void guardarPuntoSalida(Context c, double lat, double lon) {
        try {
            Files.write(new File(c.getFilesDir(), "punto_salida.txt").toPath(),
                    String.format(Locale.US, "%.6f,%.6f", lat, lon).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    static String puntoSalida(Context c) {
        try {
            File f = new File(c.getFilesDir(), "punto_salida.txt");
            if (f.exists()) return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {}
        return c.getSharedPreferences("barra", Context.MODE_PRIVATE).getString("puntoSalida", "");
    }

    /** Copia de una ruta o recorrido en Descargas/MiBarra/GPS (para que no se pierda y él la vea). */
    static void copiarRuta(Context c, File f, String nombreBonito) {
        try {
            String n = (nombreBonito == null || nombreBonito.trim().isEmpty() ? f.getName() : nombreBonito)
                    .replaceAll("[\\\\/:*?\"<>|]", " ").trim();
            if (n.length() > 60) n = n.substring(0, 60);
            escribirEnDescargas(c, "GPS", n + " (" + f.getName().replace(".txt", "") + ").txt", "text/plain", Files.readAllBytes(f.toPath()));
        } catch (Exception ignored) {}
    }
}
