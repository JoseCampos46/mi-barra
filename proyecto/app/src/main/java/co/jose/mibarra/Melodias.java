package co.jose.mibarra;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Versión 29: música propia de Mi Barra, compuesta para el radio reloj.
 * Se toca sola, sin internet y sin archivos: el celular arma el sonido nota por nota.
 */
final class Melodias {

    static final String[] NOMBRES = {"🌅 Amanecer en el campo", "🌙 Noche tranquila", "☕ Café de la mañana"};

    // Cada melodía: tempo (negras por minuto) y pares {nota MIDI, duración en negras}. 0 = silencio.
    private static final int[] TEMPO = {84, 60, 112};
    private static final int[][] NOTAS = {
            // Amanecer en el campo: pentatónica en Do, sube como el sol
            {60, 1, 64, 1, 67, 1, 72, 2, 69, 1, 67, 1, 64, 2, 62, 1, 64, 1, 67, 1, 69, 2, 67, 2,
             60, 1, 64, 1, 67, 1, 72, 1, 74, 1, 76, 2, 74, 1, 72, 1, 69, 1, 67, 3, 0, 1},
            // Noche tranquila: canción de cuna lenta en La menor
            {69, 2, 72, 1, 71, 1, 69, 2, 64, 2, 65, 2, 64, 1, 62, 1, 60, 4,
             62, 2, 64, 1, 65, 1, 67, 2, 69, 2, 67, 2, 65, 1, 64, 1, 69, 4, 0, 2},
            // Café de la mañana: alegre, en Sol
            {67, 1, 71, 1, 74, 1, 71, 1, 72, 1, 76, 1, 74, 2, 71, 1, 74, 1, 79, 1, 78, 1, 76, 1, 74, 1, 72, 1, 71, 1,
             69, 1, 72, 1, 76, 1, 72, 1, 71, 1, 74, 1, 79, 2, 78, 1, 76, 1, 74, 1, 71, 1, 67, 3, 0, 1}};

    private static final int TASA = 22050;
    private static Thread hilo;
    private static volatile boolean sonando;
    private static AudioTrack pista;

    static boolean sonando() { return sonando; }

    /** Toca la melodía en repetición. Si «subir» es verdadero, empieza bajito y va subiendo (para despertar). */
    static synchronized void tocar(int cual, boolean subir) {
        parar();
        final int c = Math.max(0, Math.min(NOTAS.length - 1, cual));
        final short[] pcm = armar(c);
        sonando = true;
        hilo = new Thread(() -> {
            AudioTrack t = null;
            try {
                int min = AudioTrack.getMinBufferSize(TASA, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                t = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(subir ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(new AudioFormat.Builder().setSampleRate(TASA)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(Math.max(min, 8192)).build();
                pista = t;
                t.play();
                long inicio = System.currentTimeMillis();
                short[] trozo = new short[2048];
                while (sonando) {
                    for (int i = 0; i < pcm.length && sonando; i += trozo.length) {
                        int n = Math.min(trozo.length, pcm.length - i);
                        float g = 1f;
                        if (subir) g = Math.min(1f, 0.12f + (System.currentTimeMillis() - inicio) / 60000f);
                        for (int k = 0; k < n; k++) trozo[k] = (short) (pcm[i + k] * g);
                        t.write(trozo, 0, n);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try { if (t != null) { t.stop(); t.release(); } } catch (Exception ignored) {}
                pista = null;
            }
        });
        hilo.start();
    }

    static synchronized void parar() {
        sonando = false;
        try { if (pista != null) pista.pause(); } catch (Exception ignored) {}
        if (hilo != null) { try { hilo.join(400); } catch (InterruptedException ignored) {} hilo = null; }
    }

    /** Arma el sonido: cada nota con un timbre suave tipo campanita/marimba (fundamental + armónicos que se apagan). */
    private static short[] armar(int c) {
        int[] n = NOTAS[c];
        double negra = 60.0 / TEMPO[c];
        int total = 0;
        for (int i = 1; i < n.length; i += 2) total += (int) (n[i] * negra * TASA);
        short[] out = new short[total];
        int pos = 0;
        for (int i = 0; i < n.length; i += 2) {
            int largo = (int) (n[i + 1] * negra * TASA);
            if (n[i] > 0) {
                double f = 440.0 * Math.pow(2, (n[i] - 69) / 12.0);
                for (int k = 0; k < largo && pos + k < total; k++) {
                    double t = (double) k / TASA;
                    double ataque = Math.min(1, t / 0.012);
                    double caida = Math.exp(-t * (c == 1 ? 1.6 : 2.6));
                    double v = Math.sin(2 * Math.PI * f * t) * 0.62
                            + Math.sin(2 * Math.PI * f * 2 * t) * 0.22 * Math.exp(-t * 4)
                            + Math.sin(2 * Math.PI * f * 3 * t) * 0.10 * Math.exp(-t * 7);
                    // Un bajo suave una octava abajo, para que suene más lleno
                    v += Math.sin(Math.PI * f * t) * 0.18;
                    int s = (int) (v * ataque * caida * 9000) + out[pos + k];
                    out[pos + k] = (short) Math.max(-32000, Math.min(32000, s));
                }
            }
            pos += largo;
        }
        return out;
    }

    private Melodias() {}
}
