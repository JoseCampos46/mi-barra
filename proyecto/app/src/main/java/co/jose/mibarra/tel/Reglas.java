package co.jose.mibarra.tel;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Versión 37 (traído de Mis Chats): lista de números bloqueados (estafa), ajustes de Mi Teléfono
 * y registro de llamadas bloqueadas. La lista va en un archivo para que la lean todos los procesos de Mi Barra.
 */
public final class Reglas {

    static final String CARPETA = Environment.DIRECTORY_DOWNLOADS + "/MiBarra/MiTelefono/";

    static class Entrada {
        String nombre = "";
        String numero = "";

        String titulo() {
            if (!nombre.isEmpty()) return nombre;
            return formato(numero);
        }
    }

    static class Decision {
        boolean bloquear;
        String motivo = "";
    }

    static class Bloqueada {
        long cuando;
        String nombre = "", numero = "", motivo = "";
    }

    private Reglas() {}

    static SharedPreferences p(Context c) {
        return c.getSharedPreferences("mitelefono", Context.MODE_PRIVATE);
    }

    // ---------- Lista de bloqueados (archivo) ----------

    private static File archivo(Context c) {
        return new File(c.getFilesDir(), "tel_bloqueados.txt");
    }

    static synchronized List<Entrada> lista(Context c) {
        List<Entrada> l = new ArrayList<>();
        String t = "";
        try (FileInputStream in = new FileInputStream(archivo(c))) {
            byte[] b = new byte[(int) Math.min(archivo(c).length(), 2_000_000)];
            int n = in.read(b);
            if (n > 0) t = new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        for (String linea : t.split("\n")) {
            if (linea.trim().isEmpty()) continue;
            String[] f = linea.split("\t", -1);
            Entrada e = new Entrada();
            e.nombre = f.length > 0 ? f[0] : "";
            e.numero = f.length > 1 ? f[1] : "";
            if (!e.numero.isEmpty()) l.add(e);
        }
        return l;
    }

    static synchronized void guardarLista(Context c, List<Entrada> l) {
        StringBuilder b = new StringBuilder();
        StringBuilder respaldo = new StringBuilder("MI TELÉFONO - Números bloqueados (estafa)\n\n");
        for (Entrada e : l) {
            b.append(limpio(e.nombre)).append('\t').append(digitos(e.numero)).append('\n');
            respaldo.append(e.titulo()).append(" | ").append(formato(e.numero)).append('\n');
        }
        try (FileOutputStream o = new FileOutputStream(archivo(c))) {
            o.write(b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        escribirDescarga(c, "Bloqueados.txt", respaldo.toString());
    }

    static void agregar(Context c, String nombre, String numero) {
        List<Entrada> l = lista(c);
        numero = digitos(numero);
        if (numero.isEmpty()) return;
        for (Entrada e : l) if (mismoNumero(e.numero, numero)) return;
        Entrada e = new Entrada();
        e.nombre = limpio(nombre);
        e.numero = numero;
        l.add(0, e);
        guardarLista(c, l);
    }

    static void quitar(Context c, String numero) {
        List<Entrada> q = new ArrayList<>();
        for (Entrada e : lista(c)) if (!mismoNumero(e.numero, numero)) q.add(e);
        guardarLista(c, q);
    }

    /** Lo usa también el filtro de llamadas de Mi Barra (otro proceso). */
    public static boolean esBloqueado(Context c, String numero) {
        if (digitos(numero).isEmpty()) return false;
        for (Entrada e : lista(c)) if (mismoNumero(e.numero, numero)) return true;
        return false;
    }

    // ---------- Números ----------

    static String limpio(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').trim();
    }

    static String digitos(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    static boolean mismoNumero(String a, String b) {
        a = digitos(a);
        b = digitos(b);
        if (a.isEmpty() || b.isEmpty()) return false;
        int k = Math.min(10, Math.min(a.length(), b.length()));
        if (k < 7) return a.equals(b);
        return a.substring(a.length() - k).equals(b.substring(b.length() - k));
    }

    static String formato(String n) {
        String d = digitos(n);
        if (d.length() == 12 && d.startsWith("57")) d = d.substring(2);
        if (d.length() == 10) return d.substring(0, 3) + " " + d.substring(3, 6) + " " + d.substring(6);
        if (d.length() == 7) return d.substring(0, 3) + " " + d.substring(3);
        return n == null ? "" : n;
    }

    // ---------- Contactos ----------

    static boolean puede(Context c, String permiso) {
        return c.checkSelfPermission(permiso) == PackageManager.PERMISSION_GRANTED;
    }

    static String nombreContacto(Context c, String numero) {
        if (digitos(numero).length() < 3 || !puede(c, Manifest.permission.READ_CONTACTS)) return null;
        try {
            Uri u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(numero));
            try (Cursor cur = c.getContentResolver().query(u,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
                if (cur != null && cur.moveToFirst()) return cur.getString(0);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ---------- Decisión al entrar una llamada ----------

    static Decision telefono(Context c, String numero, boolean visible) {
        Decision d = new Decision();
        if (!visible || digitos(numero).isEmpty()) {
            if (p(c).getBoolean("ocultos", false)) {
                d.bloquear = true;
                d.motivo = "Número oculto o privado";
            }
            return d;
        }
        if (esBloqueado(c, numero)) {
            d.bloquear = true;
            d.motivo = "Bloqueado (estafa)";
        }
        return d;
    }

    // ---------- Registro de bloqueadas ----------

    static void anotar(Context c, String nombre, String numero, String motivo) {
        String linea = System.currentTimeMillis() + "\t" + limpio(nombre) + "\t" + digitos(numero) + "\t" + limpio(motivo);
        String viejo = p(c).getString("bloqueadas", "");
        String[] lineas = viejo.split("\n");
        StringBuilder b = new StringBuilder(linea).append('\n');
        for (int i = 0; i < lineas.length && i < 499; i++) {
            if (!lineas[i].trim().isEmpty()) b.append(lineas[i]).append('\n');
        }
        p(c).edit().putString("bloqueadas", b.toString()).apply();
    }

    static List<Bloqueada> bloqueadas(Context c) {
        List<Bloqueada> l = new ArrayList<>();
        for (String linea : p(c).getString("bloqueadas", "").split("\n")) {
            String[] f = linea.split("\t", -1);
            if (f.length < 4) continue;
            Bloqueada m = new Bloqueada();
            try { m.cuando = Long.parseLong(f[0]); } catch (Exception e) { continue; }
            m.nombre = f[1];
            m.numero = f[2];
            m.motivo = f[3];
            l.add(m);
        }
        return l;
    }

    static String fecha(long t) {
        return new SimpleDateFormat("dd/MM/yyyy h:mm a", new Locale("es", "CO")).format(new Date(t));
    }

    // ---------- Guardar en Descargas/MiBarra/MiTelefono ----------

    static void escribirDescarga(Context c, String nombre, String texto) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                ContentResolver r = app.getContentResolver();
                Uri col = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri uri = null;
                try (Cursor cur = r.query(col, new String[]{MediaStore.MediaColumns._ID},
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                        new String[]{nombre, CARPETA}, null)) {
                    if (cur != null && cur.moveToFirst()) uri = ContentUris.withAppendedId(col, cur.getLong(0));
                }
                if (uri == null) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, nombre);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, CARPETA);
                    uri = r.insert(col, v);
                }
                if (uri == null) return;
                try (OutputStream o = r.openOutputStream(uri, "wt")) {
                    if (o != null) o.write(texto.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {}
        }).start();
    }
}
