package co.jose.mibarra.tel;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Versión 37: graba la llamada en altavoz con el micrófono (Android no deja grabar la línea por dentro).
 * Como Mi Barra tiene servicio de accesibilidad, Android sí le deja usar el micrófono durante la llamada.
 * Llamadas → Descargas/MiBarra/Llamadas. Mensajes del contestador → Descargas/MiBarra/Contestador.
 */
final class Grabador {

    private static MediaRecorder rec;
    private static File tmp;
    private static String nombre;
    private static String carpeta = "Llamadas";

    private static boolean avisoPuesto;

    static boolean grabando() { return rec != null; }

    /** Solo desde la pantalla de llamada (que está a la vista): ahí Android sí deja poner el aviso del micrófono. */
    static void ponerAviso(android.app.Activity a) {
        if (rec == null || avisoPuesto) return;
        avisoPuesto = true;
        try { a.startForegroundService(new Intent(a, GrabandoService.class)); } catch (Exception ignored) {}
    }

    static boolean puede(Context c) {
        return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /** quien: nombre del contacto o número. carpeta: "Llamadas" o "Contestador". */
    static synchronized boolean empezar(Context c, String quien, String dondeGuardar) {
        if (rec != null) return true;
        if (!puede(c)) return false;
        try {
            tmp = new File(c.getCacheDir(), "llamada_grabando.m4a");
            if (tmp.exists()) tmp.delete();
            MediaRecorder r = android.os.Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(c) : new MediaRecorder();
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioSamplingRate(44100);
            r.setAudioEncodingBitRate(96000);
            r.setAudioChannels(1);
            r.setOutputFile(tmp.getAbsolutePath());
            r.prepare();
            r.start();
            rec = r;
            carpeta = dondeGuardar == null ? "Llamadas" : dondeGuardar;
            String q = quien == null || quien.trim().isEmpty() ? "Privado" : quien.trim();
            nombre = q.replaceAll("[^\\p{L}\\p{N} ]", "").trim().replace(' ', '_') + "_"
                    + new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date()) + ".m4a";
            avisoPuesto = false;
            return true;
        } catch (Exception e) {
            rec = null;
            return false;
        }
    }

    /** Para y guarda. Devuelve el nombre del archivo guardado o null. */
    static synchronized String parar(Context c) {
        MediaRecorder r = rec;
        rec = null;
        try { c.stopService(new Intent(c, GrabandoService.class)); } catch (Exception ignored) {}
        if (r == null) return null;
        boolean ok = true;
        try { r.stop(); } catch (Exception e) { ok = false; }
        try { r.release(); } catch (Exception ignored) {}
        if (!ok || tmp == null || tmp.length() < 2000) return null;
        try {
            ContentResolver cr = c.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
            v.put(MediaStore.Downloads.MIME_TYPE, "audio/mp4");
            v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/MiBarra/" + carpeta);
            Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) return null;
            try (InputStream in = new FileInputStream(tmp); OutputStream o = cr.openOutputStream(u)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
            }
            tmp.delete();
            return nombre;
        } catch (Exception e) {
            return null;
        }
    }

    private Grabador() {}
}
