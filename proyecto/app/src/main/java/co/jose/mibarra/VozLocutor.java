package co.jose.mibarra;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.util.Locale;

/**
 * Versión 29: la voz de Mi Voz. Usa Acapela (Antonio) si está instalada; si no, la voz del celular.
 * Tono y velocidad graduables, y «acento latino»: cambia por dentro la z y la c suave por s
 * para que Antonio no diga «th».
 */
final class VozLocutor {

    static final String ACAPELA = "com.acapelagroup.android.tts";

    interface Listo { void listo(boolean bien); }

    private TextToSpeech tts;
    private boolean lista;
    private final SharedPreferences p;
    private Runnable alTerminar;

    VozLocutor(Context c, Listo cb) { this(c, null, cb); }

    /** Versión 30: «motor» es la app de voz escogida (Acapela, Google…); si no se escogió, Acapela si está, o la del celular. */
    VozLocutor(Context c, String motor, Listo cb) {
        p = c.getSharedPreferences("mivoz", Context.MODE_PRIVATE);
        if (motor == null) motor = p.getString("motor", null);
        if (motor == null) {
            try { c.getPackageManager().getPackageInfo(ACAPELA, 0); motor = ACAPELA; } catch (Exception e) { motor = null; }
        }
        this.motor = motor;
        TextToSpeech.OnInitListener init = s -> {
            lista = s == TextToSpeech.SUCCESS;
            if (lista) {
                tts.setLanguage(new Locale("es", "ES"));
                String vn = p.getString("vozNombre", null);
                if (vn != null) try { for (android.speech.tts.Voice v : tts.getVoices()) if (v.getName().equals(vn)) { tts.setVoice(v); break; } } catch (Exception ignored) {}
                ajustar();
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) { Runnable r = alTerminar; alTerminar = null; if (r != null) r.run(); }
                    @Override public void onError(String id) { Runnable r = alTerminar; alTerminar = null; if (r != null) r.run(); }
                });
            }
            if (cb != null) cb.listo(lista);
        };
        tts = motor != null ? new TextToSpeech(c.getApplicationContext(), init, motor) : new TextToSpeech(c.getApplicationContext(), init);
    }

    final String motor;

    /** Las apps de voz instaladas en el celular: {nombre que se ve, paquete}. */
    java.util.List<String[]> motores() {
        java.util.List<String[]> l = new java.util.ArrayList<>();
        try { for (TextToSpeech.EngineInfo e : tts.getEngines()) l.add(new String[]{e.label, e.name}); } catch (Exception ignored) {}
        return l;
    }

    /** Las voces en español de la app de voz actual: {nombre bonito, nombre interno}. */
    java.util.List<String[]> vocesEspanol() {
        java.util.List<String[]> l = new java.util.ArrayList<>();
        try {
            // Versión 38: Acapela marca sus voces con el código largo «spa» (y no «es»); por eso Antonio no salía
            java.util.Set<android.speech.tts.Voice> todas = tts.getVoices();
            boolean hayEspanol = false;
            for (android.speech.tts.Voice v : todas) if (esEspanol(v)) { hayEspanol = true; break; }
            for (android.speech.tts.Voice v : todas) {
                if (v.getLocale() == null) continue;
                // si la app de voz no dice el idioma, se muestran todas sus voces para que él escoja (Antonio)
                if (hayEspanol && !esEspanol(v)) continue;
                String n = v.getName();
                String pais = v.getLocale().getDisplayCountry(new Locale("es"));
                l.add(new String[]{n.replace("_", " ") + (pais.isEmpty() ? "" : " · " + pais) + (v.isNetworkConnectionRequired() ? " (con internet)" : ""), n});
            }
        } catch (Exception ignored) {}
        l.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        return l;
    }

    private static boolean esEspanol(android.speech.tts.Voice v) {
        Locale l = v.getLocale();
        if (l == null) return false;
        String a = l.getLanguage();
        String b;
        try { b = l.getISO3Language(); } catch (Exception e) { b = ""; }
        String n = v.getName() == null ? "" : v.getName().toLowerCase(Locale.ROOT);
        return "es".equals(a) || "spa".equals(a) || "spa".equals(b) || n.contains("antonio");
    }

    void escogerVoz(String motorPaquete, String vozNombre) {
        p.edit().putString("motor", motorPaquete).putString("vozNombre", vozNombre).apply();
        if (vozNombre != null && lista) try { for (android.speech.tts.Voice v : tts.getVoices()) if (v.getName().equals(vozNombre)) { tts.setVoice(v); break; } } catch (Exception ignored) {}
    }

    float tono() { return p.getInt("tono", 85) / 100f; }
    float velocidad() { return p.getInt("vel", 92) / 100f; }
    boolean latino() { return p.getBoolean("latino", true); }

    void guardar(int tono, int vel, boolean latino) {
        p.edit().putInt("tono", tono).putInt("vel", vel).putBoolean("latino", latino).apply();
        ajustar();
    }

    private void ajustar() {
        if (!lista) return;
        tts.setPitch(Math.max(0.5f, tono()));
        tts.setSpeechRate(Math.max(0.5f, velocidad()));
    }

    /** Lo que de verdad se le pasa a la voz: sin símbolos, con «pesos», y con acento latino si está prendido. */
    String preparar(String t) {
        String s = t.replace("*", "").replace("•", "").replace("×", " a ").replace("=", ", son ")
                .replaceAll("\\$\\s?([\\d.]+)", "$1 pesos").replace("_", " ");
        if (latino()) {
            s = s.replaceAll("z", "s").replaceAll("Z", "S")
                    .replaceAll("c([eéií])", "s$1").replaceAll("C([eEéÉiIíÍ])", "S$1");
        }
        return s;
    }

    boolean lista() { return lista; }

    void hablar(String texto) {
        if (!lista) return;
        tts.stop();
        tts.speak(preparar(texto), TextToSpeech.QUEUE_FLUSH, null, "leer");
    }

    void callar() { if (tts != null) tts.stop(); }

    /** Graba el escrito en un archivo de audio; avisa cuando termina. */
    void aArchivo(String texto, File f, Runnable listo) {
        if (!lista) { listo.run(); return; }
        alTerminar = listo;
        Bundle b = new Bundle();
        int r = tts.synthesizeToFile(preparar(texto), b, f, "archivo");
        if (r != TextToSpeech.SUCCESS) { alTerminar = null; listo.run(); }
    }

    void cerrar() { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {} }
}
