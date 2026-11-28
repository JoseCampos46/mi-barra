package co.jose.mibarra;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Versión 16: el "oído" propio de Mi Barra. Escucha en español SIN internet y SIN Google (Vosk).
 * El idioma (unos 40 MB) se descarga una sola vez y queda guardado en el celular.
 */
public final class VozLocal {

    static final String URL_ES = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip";
    private static final float FRECUENCIA = 16000f;
    private static Model modelo;
    private static SpeechService servicio;
    private static final Handler h = new Handler(Looper.getMainLooper());

    private VozLocal() {}

    static File carpeta(Context c) { return new File(c.getFilesDir(), "voz_es"); }

    /** ¿Ya está descargado el español? */
    static boolean listo(Context c) { return new File(carpeta(c), "listo.txt").exists(); }

    // ---------------- Descargar el idioma ----------------

    interface Avance { void va(int pct, String txt); void fin(boolean ok, String txt); }

    static void descargar(Context c, Avance a) {
        Context app = c.getApplicationContext();
        new Thread(() -> {
            File dest = carpeta(app), tmp = new File(app.getFilesDir(), "voz_es_tmp");
            borrar(tmp);
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(URL_ES).openConnection();
                con.setConnectTimeout(20000);
                con.setReadTimeout(30000);
                long total = con.getContentLengthLong();
                long leido = 0;
                int ultimo = -1;
                tmp.mkdirs();
                try (InputStream in = new ContadorStream(con.getInputStream());
                     ZipInputStream zip = new ZipInputStream(in)) {
                    ZipEntry e;
                    byte[] buf = new byte[64 * 1024];
                    while ((e = zip.getNextEntry()) != null) {
                        String nombre = e.getName();
                        int barra = nombre.indexOf('/');
                        if (barra >= 0) nombre = nombre.substring(barra + 1); // quita la carpeta de arriba
                        if (nombre.isEmpty() || nombre.contains("..")) continue;
                        File f = new File(tmp, nombre);
                        if (e.isDirectory()) { f.mkdirs(); continue; }
                        f.getParentFile().mkdirs();
                        try (OutputStream out = new FileOutputStream(f)) {
                            int n;
                            while ((n = zip.read(buf)) > 0) {
                                out.write(buf, 0, n);
                                leido = ((ContadorStream) in).contados;
                                int pct = total > 0 ? (int) (leido * 100 / total) : -1;
                                if (pct != ultimo) {
                                    ultimo = pct;
                                    final int p = pct;
                                    h.post(() -> a.va(p, p >= 0 ? "Descargando español… " + p + " %" : "Descargando español…"));
                                }
                            }
                        }
                    }
                }
                new FileOutputStream(new File(tmp, "listo.txt")).close();
                cerrarModelo();
                borrar(dest);
                if (!tmp.renameTo(dest)) throw new Exception("no se pudo guardar");
                h.post(() -> a.fin(true, "✅ Español guardado. El micrófono ya funciona sin internet."));
            } catch (Exception ex) {
                borrar(tmp);
                h.post(() -> a.fin(false, "No se pudo descargar. Revise el Wi‑Fi y vuelva a intentar."));
            }
        }).start();
    }

    /** Cuenta los bytes que llegan, para mostrar el porcentaje. */
    private static final class ContadorStream extends java.io.FilterInputStream {
        long contados;
        ContadorStream(InputStream in) { super(in); }
        @Override public int read() throws java.io.IOException { int r = super.read(); if (r >= 0) contados++; return r; }
        @Override public int read(byte[] b, int o, int l) throws java.io.IOException { int r = super.read(b, o, l); if (r > 0) contados += r; return r; }
    }

    static void borrar(File f) {
        if (f == null || !f.exists()) return;
        File[] hijos = f.listFiles();
        if (hijos != null) for (File x : hijos) borrar(x);
        f.delete();
    }

    // ---------------- Escuchar ----------------

    interface Oido {
        void parcial(String texto);
        void frase(String texto);
        void error(String txt);
        void terminado();
    }

    /** Empieza a escuchar. Sigue escuchando hasta que se llame a parar(). */
    static void escuchar(Context c, Oido o) {
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                if (modelo == null) {
                    try { LibVosk.setLogLevel(LogLevel.WARNINGS); } catch (Throwable ignored) {}
                    modelo = new Model(carpeta(app).getAbsolutePath());
                }
                Recognizer r = new Recognizer(modelo, FRECUENCIA);
                SpeechService s = new SpeechService(r, FRECUENCIA);
                h.post(() -> {
                    parar();
                    servicio = s;
                    s.startListening(new RecognitionListener() {
                        @Override public void onPartialResult(String json) { String t = leer(json, "partial"); if (!t.isEmpty()) o.parcial(t); }
                        @Override public void onResult(String json) { String t = leer(json, "text"); if (!t.isEmpty()) o.frase(t); }
                        @Override public void onFinalResult(String json) { String t = leer(json, "text"); if (!t.isEmpty()) o.frase(t); o.terminado(); }
                        @Override public void onError(Exception e) { parar(); o.error("El micrófono falló. Toque 🎤 otra vez"); }
                        @Override public void onTimeout() { parar(); o.terminado(); }
                    }, 120000);
                });
            } catch (Throwable e) {
                h.post(() -> o.error("No se pudo abrir el español. Vuelva a descargarlo en ⚙"));
            }
        }).start();
    }

    static void parar() {
        SpeechService s = servicio;
        servicio = null;
        if (s != null) {
            try { s.stop(); } catch (Throwable ignored) {}
            try { s.shutdown(); } catch (Throwable ignored) {}
        }
    }

    static boolean escuchando() { return servicio != null; }

    private static void cerrarModelo() {
        parar();
        Model m = modelo;
        modelo = null;
        if (m != null) try { m.close(); } catch (Throwable ignored) {}
    }

    private static String leer(String json, String campo) {
        try { return new JSONObject(json).optString(campo, "").trim(); } catch (Exception e) { return ""; }
    }
}
