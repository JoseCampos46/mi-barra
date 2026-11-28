package co.jose.mibarra;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Nivel digital: modo plano (mesa), barra (farol, de canto) y plomada (columna). */
public class NivelActivity extends Base implements SensorEventListener {

    private static final String[] MODOS = {"● Plano", "▬ De lado", "┃ Plomada", "📐 Grados"};
    private static final double VERDE = 0.25; // grados para considerar nivelado

    private SensorManager sm;
    private final float[] g = new float[3];
    private boolean hayDato, congelado, sonido, estabaVerde, estabaVerdeSonido;
    private static final String[] TXT_AVISO = {"🔊 Pitos", "🗣 Voz", "🔇 Callado"};
    private int aviso;
    private android.speech.tts.TextToSpeech voz;
    private boolean vozLista;
    private String ultimoDicho = "";
    private long ultimaVez;
    private TextView tl;

    /** Habla sin amontonar: cada 3 segundos, o antes si es urgente. */
    private void hablar(String t, boolean ya) {
        if (!vozLista || voz == null) return;
        long ahora = System.currentTimeMillis();
        if (!ya && (ahora - ultimaVez < 3000 || (t.equals(ultimoDicho) && ahora - ultimaVez < 7000))) return;
        ultimaVez = ahora;
        ultimoDicho = t;
        voz.speak(t, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "nivel");
    }

    private static String mm(long n) { return n == 1 ? "un milímetro" : n + " milímetros"; }

    private int largoCm() {
        try { return Math.max(1, Integer.parseInt(largo.getText().toString().trim())); } catch (Exception e) { return modo == 2 ? 100 : 30; }
    }

    private void guardarLargo() {
        try { prefs.edit().putInt(modo == 2 ? "nivelAlto" : "nivelLargo", Integer.parseInt(largo.getText().toString().trim())).apply(); } catch (Exception ignored) {}
    }
    private int modo;
    private SharedPreferences prefs;
    private NivelView vista;
    private TextView tA, tB, tAyuda;
    private Button[] bModos = new Button[4];
    private Button bCong, bSon;
    private EditText largo;
    private ToneGenerator tono;
    private boolean canto;
    private LinearLayout filaPlomo;
    private Button bEspalda, bCanto;
    private final Handler h = new Handler(Looper.getMainLooper());
    private long ultimoPito;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("barra", MODE_PRIVATE);
        modo = getIntent().getIntExtra("modo", prefs.getInt("nivelModo", 0));
        // Versión 26: la plomada se usa con el LADO IZQUIERDO del celular contra el muro (sin botones ni cámaras que estorben)
        canto = prefs.getBoolean("plomoCanto2", true);
        sm = getSystemService(SensorManager.class);

        LinearLayout l = vertical();
        l.setBackgroundColor(0xFF10161C);
        l.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout mr = horizontal();
        for (int i = 0; i < 4; i++) {
            final int k = i;
            Button bt = boton(MODOS[i], 0xFF263238);
            bt.setTextSize(13);
            bt.setOnClickListener(v -> { guardarLargo(); modo = k; prefs.edit().putInt("nivelModo", k).apply(); pintarModos(); });
            bModos[i] = bt;
            mr.addView(bt, new LinearLayout.LayoutParams(0, dp(46), 1f) {{ setMargins(dp(3), 0, dp(3), 0); }});
        }
        l.addView(mr);

        vista = new NivelView(this);
        l.addView(vista, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        filaPlomo = horizontal();
        bEspalda = boton("▮ Espalda al muro", 0xFF263238);
        bCanto = boton("▯ Lado izquierdo al muro", 0xFF263238);
        bEspalda.setOnClickListener(v -> { canto = false; prefs.edit().putBoolean("plomoCanto2", false).apply(); pintarModos(); });
        bCanto.setOnClickListener(v -> { canto = true; prefs.edit().putBoolean("plomoCanto2", true).apply(); pintarModos(); });
        filaPlomo.addView(bEspalda, new LinearLayout.LayoutParams(0, dp(44), 1f) {{ setMargins(dp(3), dp(4), dp(3), 0); }});
        filaPlomo.addView(bCanto, new LinearLayout.LayoutParams(0, dp(44), 1f) {{ setMargins(dp(3), dp(4), dp(3), 0); }});
        l.addView(filaPlomo);
        LinearLayout lect = horizontal();
        tA = lectura(lect);
        tB = lectura(lect);
        l.addView(lect);
        tAyuda = new TextView(this);
        tAyuda.setTextSize(18);
        tAyuda.setTextColor(0xFF222222);
        tAyuda.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable ga = new GradientDrawable();
        ga.setColor(0xFFFFF59D);
        ga.setCornerRadius(dp(12));
        tAyuda.setBackground(ga);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(6);
        l.addView(tAyuda, ap);

        LinearLayout fl = horizontal();
        fl.setGravity(Gravity.CENTER_VERTICAL);
        tl = new TextView(this);
        tl.setText("Largo del farol o pieza (cm): ");
        tl.setTextColor(0xFFB0BEC5);
        fl.addView(tl);
        largo = campoTexto("30", String.valueOf(prefs.getInt("nivelLargo", 30)));
        largo.setTextColor(Color.WHITE);
        largo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        fl.addView(largo, new LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT));
        l.addView(fl);

        LinearLayout bot = horizontal();
        Button cal = boton("🎯 Calibrar", 0xFF455A64);
        cal.setOnClickListener(v -> calibrar());
        bCong = boton("🔒 Congelar", 0xFF6A1B9A);
        bCong.setOnClickListener(v -> { congelado = !congelado; bCong.setText(congelado ? "🔓 Seguir" : "🔒 Congelar"); });
        aviso = prefs.getInt("nivelAviso", 1); // 0 pitos, 1 voz, 2 callado
        sonido = aviso == 0;
        bSon = boton(TXT_AVISO[aviso], 0xFF2E7D32);
        bSon.setOnClickListener(v -> {
            aviso = (aviso + 1) % 3;
            sonido = aviso == 0;
            bSon.setText(TXT_AVISO[aviso]);
            prefs.edit().putInt("nivelAviso", aviso).apply();
            if (aviso == 1) hablar("Le voy diciendo cuántos milímetros mover", true);
        });
        voz = new android.speech.tts.TextToSpeech(this, st -> {
            if (st == android.speech.tts.TextToSpeech.SUCCESS) { voz.setLanguage(new Locale("es", "CO")); vozLista = true; }
        });
        for (Button x : new Button[]{cal, bCong, bSon})
            bot.addView(x, new LinearLayout.LayoutParams(0, dp(52), 1f) {{ setMargins(dp(3), dp(6), dp(3), 0); }});
        l.addView(bot);
        setContentView(l);
        pintarModos();
        try { tono = new ToneGenerator(AudioManager.STREAM_MUSIC, 70); } catch (Exception ignored) {}
    }

    private TextView lectura(LinearLayout fila) {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF1E272E);
        g.setCornerRadius(dp(12));
        t.setBackground(g);
        fila.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) {{ setMargins(dp(3), dp(6), dp(3), 0); }});
        return t;
    }

    private void pintarModos() {
        for (int i = 0; i < 4; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(i == modo ? 0xFF2E7D32 : 0xFF263238);
            g.setCornerRadius(dp(20));
            bModos[i].setBackground(g);
        }
        tB.setVisibility(modo == 1 || modo == 3 ? View.GONE : View.VISIBLE);
        filaPlomo.setVisibility(View.GONE);
        tl.setText(modo == 2 ? "Alto del muro o columna (cm): " : "Largo del farol o pieza (cm): ");
        largo.setText(String.valueOf(modo == 2 ? prefs.getInt("nivelAlto", 100) : prefs.getInt("nivelLargo", 30)));
        for (int i = 0; i < 2; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setColor((i == 1) == canto ? 0xFF1565C0 : 0xFF263238);
            g.setCornerRadius(dp(20));
            (i == 0 ? bEspalda : bCanto).setBackground(g);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (s == null) s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
        else aviso("Este celular no tiene sensor de inclinación");
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
        guardarLargo();
    }

    @Override
    protected void onDestroy() {
        if (tono != null) tono.release();
        if (voz != null) voz.shutdown();
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (congelado) return;
        float a = hayDato ? 0.12f : 1f; // suavizado para que no tiemble
        for (int i = 0; i < 3; i++) g[i] += a * (e.values[i] - g[i]);
        hayDato = true;
        actualizar();
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    // ----- Ángulos -----

    /** Plano: {x, y} en grados. x>0 = lado derecho alto; y>0 = lado de arriba alto. */
    private double[] plano() {
        return new double[]{Math.toDegrees(Math.atan2(g[0], g[2])) - off("px"), Math.toDegrees(Math.atan2(g[1], g[2])) - off("py")};
    }

    /** Barra: desviación en grados respecto al eje del celular más cercano a horizontal, y el eje usado. */
    private double[] barra() {
        boolean ejeX = Math.abs(g[0]) < Math.abs(g[1]); // celular parado: la barra va a lo largo de X
        double alto = ejeX ? g[0] : g[1], otro = ejeX ? g[1] : g[0];
        double d = Math.toDegrees(Math.atan2(alto, Math.abs(otro))) - off(ejeX ? "bx" : "by");
        return new double[]{d, ejeX ? 0 : 1, Math.signum(otro)};
    }

    /** Plomada: {giro del celular sobre el muro (no importa), inclinación del muro hacia afuera/adentro}. */
    private double[] plomada() {
        double n = Math.sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]);
        double fuera = n == 0 ? 0 : Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, g[2] / n))));
        return new double[]{Math.toDegrees(Math.atan2(g[0], g[1])), fuera - off("lz")};
    }

    private double crudoPlomada() {
        double n = Math.sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]);
        return n == 0 ? 0 : Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, g[2] / n))));
    }

    private double primeraLectura = Double.NaN;

    private double off(String k) { return prefs.getFloat("cal_" + k, 0f); }

    private void calibrar() {
        if (!hayDato) return;
        SharedPreferences.Editor ed = prefs.edit();
        if (modo == 0) {
            ed.putFloat("cal_px", (float) Math.toDegrees(Math.atan2(g[0], g[2])));
            ed.putFloat("cal_py", (float) Math.toDegrees(Math.atan2(g[1], g[2])));
        } else if (modo == 1 || modo == 3) {
            boolean ejeX = Math.abs(g[0]) < Math.abs(g[1]);
            double alto = ejeX ? g[0] : g[1], otro = ejeX ? g[1] : g[0];
            ed.putFloat(ejeX ? "cal_bx" : "cal_by", (float) Math.toDegrees(Math.atan2(alto, Math.abs(otro))));
        } else if (canto) {
            ed.putFloat("cal_pz", (float) Math.toDegrees(Math.atan2(g[0], g[1])));
        } else {
            // Dos pasos: así se cancela el bulto de la cámara y sirve en cualquier muro.
            if (Double.isNaN(primeraLectura)) {
                primeraLectura = crudoPlomada();
                aviso("Calibrar el muro afuera/adentro, paso 1 de 2 ✔. Voltee el celular DE CABEZA en el mismo sitio y toque 🎯 Calibrar otra vez.");
                return;
            }
            double segunda = crudoPlomada();
            ed.putFloat("cal_lz", (float) ((primeraLectura + segunda) / 2));
            primeraLectura = Double.NaN;
            ed.apply();
            aviso("🎯 Listo: ya está corregido el bulto de la cámara. No hay que volver a calibrar.");
            return;
        }
        ed.apply();
        aviso("🎯 Calibrado. Póngalo sobre una superficie que sepa que está nivelada antes de calibrar.");
    }

    private static String gr(double d) { return CalculadoraActivity.dec(Math.abs(d), 2) + "°"; }
    private static long mmPorMetro(double d) { return Math.round(Math.abs(Math.tan(Math.toRadians(d))) * 1000); }

    private void actualizar() {
        int cm = 30;
        try { cm = Integer.parseInt(largo.getText().toString().trim()); } catch (Exception ignored) {}
        boolean verde;
        double peor;
        if (modo == 0) {
            double[] p = plano();
            tA.setText("Izq ↔ der\n" + gr(p[0]) + "\n" + mmPorMetro(p[0]) + " mm/m");
            tB.setText("Arriba ↔ abajo\n" + gr(p[1]) + "\n" + mmPorMetro(p[1]) + " mm/m");
            verde = Math.abs(p[0]) < VERDE && Math.abs(p[1]) < VERDE;
            peor = Math.max(Math.abs(p[0]), Math.abs(p[1]));
            StringBuilder s = new StringBuilder();
            if (verde) s.append("✅ ¡NIVELADO!");
            else {
                if (Math.abs(p[0]) >= VERDE) s.append("➜ Baje el lado ").append(p[0] > 0 ? "DERECHO " : "IZQUIERDO ").append(mmPorMetro(p[0])).append(" mm por metro");
                if (Math.abs(p[1]) >= VERDE) { if (s.length() > 0) s.append('\n'); s.append("➜ Baje el lado de ").append(p[1] > 0 ? "ARRIBA " : "ABAJO ").append(mmPorMetro(p[1])).append(" mm por metro"); }
            }
            tAyuda.setText(s);
            vista.plano(p[0], p[1], verde);
        } else if (modo == 1 || modo == 3) {
            double[] b = barra();
            long mmPieza = Math.round(Math.abs(Math.tan(Math.toRadians(b[0]))) * cm * 10);
            long pendB = Math.round(Math.abs(Math.tan(Math.toRadians(b[0]))) * 100);
            long mmM = Math.round(Math.abs(Math.tan(Math.toRadians(b[0]))) * 1000);
            // Versión 29: primero los grados y el porcentaje; luego cuántos milímetros subir
            tA.setText(gr(b[0]) + "   ·   " + pendB + " %\n↕ Subir " + mmPieza + " mm en " + cm + " cm   ·   " + mmM + " mm por metro");
            verde = Math.abs(b[0]) < VERDE;
            peor = Math.abs(b[0]);
            tAyuda.setText(verde ? "✅ ¡NIVELADO!" : "Techo, baranda o tubo: apoye el LADO IZQUIERDO del celular (el que no tiene botones) y lea los grados.\n"
                    + "Para nivelar un farol: golpee hacia abajo el extremo donde está la burbuja, unos " + mmPieza + " mm.");
            vista.techo = modo == 3;
            vista.barra(b[0], b[1] == 1, b[2], verde);
            if (modo == 3) tAyuda.setText(verde ? "✅ ¡A NIVEL! 0°" : "Apoye el LADO IZQUIERDO del celular (sin botones) sobre el techo, la baranda o lo inclinado.\nInclinación: " + gr(b[0]) + " · " + mmM + " mm por metro · " + pendB + " %");
        } else {
            double fuera = plomada()[1];
            if (!canto) {
                // Espalda contra el muro: se ve el muro DE LADO; la plomada muestra el desplome afuera/adentro.
                long mm = mmPorMetro(fuera);
                verde = Math.abs(fuera) < VERDE;
                peor = Math.abs(fuera);
                tA.setText("Desplome\n" + gr(fuera) + "\n" + (verde ? "a plomo" : fuera > 0 ? "arriba ADENTRO" : "arriba AFUERA"));
                tB.setText("En milímetros\n" + mm + " mm\npor cada metro de alto");
                tAyuda.setText(verde ? "✅ ¡A PLOMO! 0°"
                        : "Arriba el muro está " + mm + " mm por metro más " + (fuera > 0 ? "ADENTRO (hacia el muro)" : "AFUERA (hacia usted)")
                        + ".\nUse el complemento abajo para que la espalda quede plana.");
                double rad = Math.toRadians(fuera);
                vista.ladoIzq = false;
                vista.plomada(-Math.sin(rad) * 9.8, Math.cos(rad) * 9.8, 0, fuera, verde, true);
            } else {
                // Versión 26: celular PARADO con el lado izquierdo contra el muro (la pantalla mirándolo a usted).
                // Si arriba el celular se recuesta hacia el muro, el muro se va hacia ADENTRO; si se separa, hacia AFUERA.
                double dev = Math.toDegrees(Math.atan2(g[0], g[1])) - off("pz");
                long mm = mmPorMetro(dev);
                verde = Math.abs(dev) < VERDE;
                peor = Math.abs(dev);
                tA.setText("Desplome\n" + gr(dev) + "\n" + (verde ? "a plomo" : dev > 0 ? "arriba ADENTRO" : "arriba AFUERA"));
                tB.setText("En milímetros\n" + mm + " mm\npor cada metro de alto");
                tAyuda.setText(verde ? "✅ ¡A PLOMO! 0°"
                        : "Apoye el LADO IZQUIERDO del celular contra el muro.\nArriba el muro está " + mm + " mm por metro más "
                        + (dev > 0 ? "ADENTRO (hacia el muro)" : "AFUERA (hacia usted)") + ".");
                double rad = Math.toRadians(dev);
                vista.ladoIzq = true;
                vista.plomada(-Math.sin(rad) * 9.8, Math.cos(rad) * 9.8, 0, dev, verde, true);
            }
        }
        if (aviso == 1) {
            if (verde) { if (!estabaVerde) hablar(modo == 2 ? "A plomo" : "Nivelado", true); }
            else hablar(frase(), false);
        }
        if (verde && !estabaVerde) {
            // Versión 29: siempre un pito al llegar a 0,0 (menos en «callado»)
            if (aviso != 2 && tono != null) tono.startTone(ToneGenerator.TONE_PROP_ACK, 260);
            Vibrator vb = getSystemService(Vibrator.class);
            if (vb != null) vb.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE));
        }
        estabaVerde = verde;
        if (sonido && tono != null) {
            // Lejos (más de 3°): callado. Acercándose: pitico corto cada vez más rápido. Nivelado: tono alto doble.
            long ahora = System.currentTimeMillis();
            if (verde) {
                if (!estabaVerdeSonido || ahora - ultimoPito > 900) {
                    ultimoPito = ahora;
                    tono.startTone(ToneGenerator.TONE_PROP_ACK, 220);
                }
            } else if (peor < 3) {
                long cada = (long) (90 + peor * 330);
                if (ahora - ultimoPito > cada) {
                    ultimoPito = ahora;
                    tono.startTone(ToneGenerator.TONE_PROP_BEEP, 45);
                }
            }
            estabaVerdeSonido = verde;
        }
    }

    /** Lo que dice la voz: cuántos milímetros mover, según el largo de la pieza o el alto del muro. */
    private String frase() {
        int cm = largoCm();
        if (modo == 0) {
            double[] p = plano();
            StringBuilder b = new StringBuilder();
            long mx = Math.round(Math.abs(Math.tan(Math.toRadians(p[0]))) * cm * 10);
            long my = Math.round(Math.abs(Math.tan(Math.toRadians(p[1]))) * cm * 10);
            if (Math.abs(p[0]) >= VERDE && mx > 0) b.append("Baje el lado ").append(p[0] > 0 ? "derecho " : "izquierdo ").append(mm(mx)).append(". ");
            if (Math.abs(p[1]) >= VERDE && my > 0) b.append("Baje el lado de ").append(p[1] > 0 ? "arriba " : "abajo ").append(mm(my)).append(".");
            return b.length() == 0 ? "Casi" : b.toString();
        } else if (modo == 1 || modo == 3) {
            double[] b = barra();
            long n = Math.round(Math.abs(Math.tan(Math.toRadians(b[0]))) * cm * 10);
            return n == 0 ? "Casi" : "Baje el lado de la burbuja " + mm(n);
        } else {
            double f = plomada()[1];
            long n = Math.round(Math.abs(Math.tan(Math.toRadians(f))) * cm * 10);
            return n == 0 ? "Casi" : "Arriba está " + mm(n) + " hacia " + (f > 0 ? "adentro. Sáquelo." : "afuera. Métalo.");
        }
    }

    // ----- Dibujo -----

    static final class NivelView extends View {
        private int modo;
        private double a, b, signo;
        private boolean vertical, verde;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        NivelView(Context c) { super(c); }

        void plano(double x, double y, boolean v) { modo = 0; a = x; b = y; verde = v; invalidate(); }
        private double gx, gy, ref;
        private boolean deLado;
        boolean techo, ladoIzq;
        void plomada(double gx, double gy, double ref, double dev, boolean v, boolean deLado) {
            modo = 2; this.gx = gx; this.gy = gy; this.ref = ref; a = dev; verde = v; this.deLado = deLado; invalidate();
        }
        void barra(double d, boolean vert, double sg, boolean v) { modo = 1; a = d; vertical = vert; signo = sg; verde = v; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
            float d = Util.dp(getContext(), 1);
            int verdeLima = verde ? 0xFF7CFF5A : 0xFFC8F040;
            if (modo == 2) { dibujarPlomada(c, w, h, d); return; }
            if (modo == 0) {
                // Círculo grande + barra X abajo + barra Y a la derecha, con numeración en grados
                float t = 46 * d, sep = 10 * d;
                float aw = w - t - sep, ah = h - t - sep - 18 * d;
                float r = Math.min(aw, ah) / 2 - 8 * d;
                cx = aw / 2; cy = ah / 2 + 4 * d;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFE8EEF2);
                c.drawCircle(cx, cy, r + 6 * d, p);
                p.setColor(verdeLima);
                c.drawCircle(cx, cy, r, p);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFF222222);
                p.setStrokeWidth(2 * d);
                float r1 = r / 3f;            // cada aro = 1°
                c.drawCircle(cx, cy, r1, p);
                c.drawCircle(cx, cy, r1 * 2, p);
                c.drawLine(cx - r, cy, cx + r, cy, p);
                c.drawLine(cx, cy - r, cx, cy + r, p);
                p.setColor(0xFF2E7D32);
                p.setStrokeWidth(3 * d);
                c.drawCircle(cx, cy, r1 * 0.7f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF222222);
                p.setTextSize(14 * d);
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText("1°", cx + r1 + 3 * d, cy - 4 * d, p);
                c.drawText("2°", cx + r1 * 2 + 3 * d, cy - 4 * d, p);
                c.drawText("3°", cx + r - 22 * d, cy - 4 * d, p);
                double bx = Math.max(-3, Math.min(3, a)), by = Math.max(-3, Math.min(3, b));
                float px = (float) (cx + bx * r1), py = (float) (cy - by * r1);
                p.setColor(0xEEFFFFFF);
                c.drawCircle(px, py, r1 * 0.45f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFF66BB88);
                p.setStrokeWidth(2 * d);
                c.drawCircle(px, py, r1 * 0.45f, p);
                // Barras
                RectF bxr = new RectF(cx - r, cy + r + sep + 8 * d, cx + r, cy + r + sep + 8 * d + t);
                RectF byr = new RectF(aw + sep, cy - r, aw + sep + t, cy + r);
                tuboNumerado(c, bxr, a, true, d);
                tuboNumerado(c, byr, b, false, d);
                        } else {
                if (techo || Math.abs(a) > 4) { dibujarTecho(c, w, h, d); return; }
                float largo = (vertical ? h : w) - 30 * d, grueso = 70 * d;
                RectF tubo = vertical ? new RectF(cx - grueso / 2, cy - largo / 2, cx + grueso / 2, cy + largo / 2)
                        : new RectF(cx - largo / 2, cy - grueso / 2, cx + largo / 2, cy + grueso / 2);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFE8EEF2);
                c.drawRoundRect(tubo, grueso / 2, grueso / 2, p);
                RectF in = new RectF(tubo.left + 6 * d, tubo.top + 6 * d, tubo.right - 6 * d, tubo.bottom - 6 * d);
                p.setColor(verdeLima);
                c.drawRoundRect(in, grueso / 2, grueso / 2, p);
                p.setColor(0xFF2E7D32);
                p.setStrokeWidth(3 * d);
                float marca = 26 * d;
                if (vertical) { c.drawLine(in.left, cy - marca, in.right, cy - marca, p); c.drawLine(in.left, cy + marca, in.right, cy + marca, p); }
                else { c.drawLine(cx - marca, in.top, cx - marca, in.bottom, p); c.drawLine(cx + marca, in.top, cx + marca, in.bottom, p); }
                // burbuja hacia el extremo alto; 1° = 60 dp
                double desp = Math.max(-4, Math.min(4, a)) * 60 * d;
                float bx = vertical ? cx : (float) (cx + desp);
                float by = vertical ? (float) (cy - desp) : cy;
                p.setColor(0xEEFFFFFF);
                RectF bur = vertical ? new RectF(bx - grueso / 3, by - grueso / 2, bx + grueso / 3, by + grueso / 2)
                        : new RectF(bx - grueso / 2, by - grueso / 3, bx + grueso / 2, by + grueso / 3);
                c.drawOval(bur, p);
                p.setColor(Color.WHITE);
                p.setTextSize(64 * d);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(verde ? 0xFF7CFF5A : 0xFFFFB300);
                String txt = String.format(Locale.US, "%.1f°", Math.abs(a)).replace('.', ',');
                c.drawText(txt, cx, vertical ? cy - largo / 4 : cy - grueso - 20 * d, p);
                p.setTextAlign(Paint.Align.LEFT);
                p.setTypeface(Typeface.DEFAULT);
            }
        }
    
        /** Tubo con burbuja y números de -3° a 3° (0 en el centro). */
        private void tuboNumerado(Canvas c, RectF r, double val, boolean horiz, float d) {
            boolean ok = Math.abs(val) < VERDE;
            float rad = (horiz ? r.height() : r.width()) / 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFE8EEF2);
            c.drawRoundRect(r, rad, rad, p);
            RectF in = new RectF(r.left + 4 * d, r.top + 4 * d, r.right - 4 * d, r.bottom - 4 * d);
            p.setColor(ok ? 0xFF7CFF5A : 0xFFC8F040);
            c.drawRoundRect(in, rad, rad, p);
            float largo = (horiz ? in.width() : in.height()) - 2 * rad;
            float centro = horiz ? in.centerX() : in.centerY();
            float porGrado = largo / 6f;
            p.setColor(0xFF222222);
            p.setTextSize(11 * d);
            p.setTextAlign(Paint.Align.CENTER);
            for (int i = -6; i <= 6; i++) {           // rayita cada medio grado
                float pos = centro + i * porGrado / 2f;
                float lar = (i % 2 == 0 ? 0.32f : 0.18f) * (horiz ? in.height() : in.width());
                p.setStrokeWidth((i == 0 ? 3 : 1.5f) * d);
                if (horiz) c.drawLine(pos, in.top, pos, in.top + lar, p);
                else c.drawLine(in.left, pos, in.left + lar, pos, p);
                if (i % 2 == 0) {
                    String n = String.valueOf(Math.abs(i / 2));
                    if (horiz) c.drawText(n, pos, in.bottom - 4 * d, p);
                    else c.drawText(n, in.right - 9 * d, pos + 4 * d, p);
                }
            }
            double v = Math.max(-3.2, Math.min(3.2, val));
            float bp = (float) (horiz ? centro + v * porGrado : centro - v * porGrado);
            float br = rad * 0.75f;
            p.setColor(0xDDFFFFFF);
            if (horiz) c.drawOval(new RectF(bp - br * 1.4f, in.centerY() - br * 0.8f, bp + br * 1.4f, in.centerY() + br * 0.8f), p);
            else c.drawOval(new RectF(in.centerX() - br * 0.8f, bp - br * 1.4f, in.centerX() + br * 0.8f, bp + br * 1.4f), p);
            // número grande al lado
            p.setTextSize(15 * d);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setColor(ok ? 0xFF7CFF5A : 0xFFFFB300);
            String txt = (horiz ? "X " : "Y ") + String.format(Locale.US, "%.2f°", Math.abs(val)).replace('.', ',');
            if (horiz) { p.setTextAlign(Paint.Align.CENTER); c.drawText(txt, r.centerX(), r.bottom + 16 * d, p); }
            else { p.setTextAlign(Paint.Align.CENTER); c.drawText(horiz ? txt : (ok ? "Y ✔" : "Y"), r.centerX(), r.top - 6 * d, p); }
            p.setTypeface(Typeface.DEFAULT);
            p.setTextAlign(Paint.Align.LEFT);
        }
    
        /** Una plomada de verdad: el hilo siempre cuelga hacia abajo; la raya punteada es el canto del celular. */
        private void dibujarPlomada(Canvas c, float w, float h, float d) {
            if (deLado) { dibujarDesplome(c, w, h, d); return; }
            float ax = w / 2, ay = 26 * d, largo = h * 0.62f;
            double n = Math.hypot(gx, gy);
            float dx = n == 0 ? 0 : (float) (-gx / n), dy = n == 0 ? 1 : (float) (gy / n);
            boolean horizontal = Math.abs(ref) == 90;
            int col = verde ? 0xFF7CFF5A : 0xFFFFB300;
            // Referencia del canto del celular
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(0x88FFFFFF);
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{12 * d, 8 * d}, 0));
            if (horizontal) c.drawLine(w * 0.08f, ay + largo * 0.55f, w * 0.92f, ay + largo * 0.55f, p);
            else c.drawLine(ax, ay, ax, ay + largo + 20 * d, p);
            p.setPathEffect(null);
            if (deLado) {
                // El muro visto de lado: a la derecha de la raya; usted está a la izquierda.
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF5D4037);
                c.drawRect(ax + 34 * d, ay - 10 * d, ax + 70 * d, ay + largo + 30 * d, p);
                p.setColor(0xFF8D6E63);
                p.setStrokeWidth(2 * d);
                for (float yy = ay; yy < ay + largo + 30 * d; yy += 22 * d) c.drawLine(ax + 34 * d, yy, ax + 70 * d, yy + 12 * d, p);
                p.setColor(0xFFB0BEC5);
                p.setTextSize(15 * d);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText("MURO", ax + 52 * d, ay + largo + 48 * d, p);
                c.drawText("← usted", ax - 90 * d, ay + 40 * d, p);
                p.setStyle(Paint.Style.STROKE);
            }
            if (horizontal) {
                // Línea a nivel de verdad (perpendicular al hilo), para ver la inclinación del techo
                float cy = ay + largo * 0.55f, hx = -dy, hy = dx;
                p.setColor(col);
                p.setStrokeWidth(4 * d);
                c.drawLine(ax - hx * w * 0.42f, cy - hy * w * 0.42f, ax + hx * w * 0.42f, cy + hy * w * 0.42f, p);
            }
            // Gancho
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFB0BEC5);
            c.drawRect(ax - 30 * d, ay - 14 * d, ax + 30 * d, ay - 6 * d, p);
            c.drawCircle(ax, ay, 5 * d, p);
            // Hilo
            float bx = ax + dx * largo, by = ay + dy * largo;
            p.setColor(0xFFEEEEEE);
            p.setStrokeWidth(2.5f * d);
            c.drawLine(ax, ay, bx, by, p);
            // El plomo (pesa de bronce en punta)
            c.save();
            c.translate(bx, by);
            c.rotate((float) Math.toDegrees(Math.atan2(-dx, dy)));
            android.graphics.Path pesa = new android.graphics.Path();
            float r = 22 * d;
            pesa.moveTo(-r * 0.35f, 0);
            pesa.lineTo(r * 0.35f, 0);
            pesa.lineTo(r, r * 0.9f);
            pesa.lineTo(r * 0.8f, r * 1.9f);
            pesa.lineTo(0, r * 3.1f);
            pesa.lineTo(-r * 0.8f, r * 1.9f);
            pesa.lineTo(-r, r * 0.9f);
            pesa.close();
            p.setShader(new android.graphics.LinearGradient(-r, 0, r, 0, 0xFFFFD27F, 0xFF9C6B1E, android.graphics.Shader.TileMode.CLAMP));
            c.drawPath(pesa, p);
            p.setShader(null);
            c.restore();
            // El número grande
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(64 * d);
            p.setColor(col);
            String txt = String.format(Locale.US, "%.1f°", Math.abs(a)).replace('.', ',');
            c.drawText(txt, horizontal || deLado ? w * (deLado ? 0.3f : 0.5f) : (a > 0 ? w * 0.27f : w * 0.73f), h - 24 * d, p);
            p.setTextSize(15 * d);
            p.setColor(0xFFB0BEC5);
            p.setTypeface(Typeface.DEFAULT);
            c.drawText(deLado ? (ladoIzq ? "Lado izquierdo del celular contra el muro" : "Espalda del celular contra el muro (visto de lado)")
                    : horizontal ? "Canto del celular sobre el techo o el tubo" : "Canto del celular contra el muro o la columna",
                    w / 2, h - 4 * d, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    
        /**
         * Espalda contra el muro: la plomada cuelga del centro de arriba y la pesa se corre a la izquierda
         * (arriba AFUERA) o a la derecha (arriba ADENTRO). Abajo hay una regla en milímetros por metro.
         * El movimiento va aumentado: todo el ancho son ±30 mm por metro.
         */
        private void dibujarDesplome(Canvas c, float w, float h, float d) {
            double mm = Math.tan(Math.toRadians(a)) * 1000;     // a>0 = arriba adentro
            float ax = w / 2, ay = 26 * d, medio = w * 0.42f;
            float reglaY = h - 70 * d, largo = reglaY - ay - 70 * d;
            int col = verde ? 0xFF7CFF5A : 0xFFFFB300;
            // Regla
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF263238);
            c.drawRoundRect(new RectF(ax - medio - 14 * d, reglaY - 6 * d, ax + medio + 14 * d, reglaY + 34 * d), 8 * d, 8 * d, p);
            p.setTextAlign(Paint.Align.CENTER);
            for (int m = -30; m <= 30; m += 5) {
                float x = ax + m / 30f * medio;
                boolean grande = m % 10 == 0;
                p.setColor(m == 0 ? 0xFF7CFF5A : 0xFFECEFF1);
                p.setStrokeWidth((m == 0 ? 3 : 1.5f) * d);
                c.drawLine(x, reglaY - 6 * d, x, reglaY + (grande ? 12 : 6) * d, p);
                if (grande) {
                    p.setTextSize(12 * d);
                    c.drawText(String.valueOf(Math.abs(m)), x, reglaY + 28 * d, p);
                }
            }
            p.setTextSize(13 * d);
            p.setColor(0xFFB0BEC5);
            c.drawText("← AFUERA (hacia usted)", ax - medio * 0.55f, reglaY + 52 * d, p);
            c.drawText("ADENTRO (hacia el muro) →", ax + medio * 0.55f, reglaY + 52 * d, p);
            c.drawText("mm por metro", ax, reglaY + 52 * d, p);
            // Raya del centro (donde debe caer si está a plomo)
            p.setColor(0x66FFFFFF);
            p.setStrokeWidth(2 * d);
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{12 * d, 8 * d}, 0));
            c.drawLine(ax, ay, ax, reglaY - 8 * d, p);
            p.setPathEffect(null);
            // Gancho, hilo y pesa
            float bx = ax + (float) (Math.max(-1.1, Math.min(1.1, mm / 30.0)) * medio), by = ay + largo;
            p.setColor(0xFFB0BEC5);
            c.drawRect(ax - 30 * d, ay - 14 * d, ax + 30 * d, ay - 6 * d, p);
            c.drawCircle(ax, ay, 5 * d, p);
            p.setColor(0xFFEEEEEE);
            p.setStrokeWidth(2.5f * d);
            c.drawLine(ax, ay, bx, by, p);
            c.save();
            c.translate(bx, by);
            c.rotate((float) Math.toDegrees(Math.atan2(-(bx - ax), largo)));
            android.graphics.Path pesa = new android.graphics.Path();
            float r = 22 * d;
            pesa.moveTo(-r * 0.35f, 0); pesa.lineTo(r * 0.35f, 0); pesa.lineTo(r, r * 0.9f); pesa.lineTo(r * 0.8f, r * 1.9f);
            pesa.lineTo(0, r * 3.1f); pesa.lineTo(-r * 0.8f, r * 1.9f); pesa.lineTo(-r, r * 0.9f); pesa.close();
            p.setShader(new android.graphics.LinearGradient(-r, 0, r, 0, 0xFFFFD27F, 0xFF9C6B1E, android.graphics.Shader.TileMode.CLAMP));
            c.drawPath(pesa, p);
            p.setShader(null);
            c.restore();
            // Número grande en milímetros
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(54 * d);
            p.setColor(col);
            c.drawText(Math.round(Math.abs(mm)) + " mm", ax, ay + largo * 0.45f, p);
            p.setTextSize(15 * d);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(30 * d);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(String.format(Locale.US, "%.2f°", Math.abs(a)).replace('.', ','), ax, ay + largo * 0.45f + 40 * d, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(15 * d);
            c.drawText(verde ? "✔ a plomo" : "milímetros por cada metro de alto", ax, ay + largo * 0.45f + 64 * d, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    
        /** Techo o pasamanos inclinado: dibuja la línea inclinada, el arco del ángulo y los números grandes. */
        private void dibujarTecho(Canvas c, float w, float h, float d) {
            double ang = Math.abs(a);
            double rad = Math.toRadians(ang);
            float ox = w * 0.12f, oy = h * 0.62f, L = w * 0.76f;
            // sube hacia el lado alto (signo de la burbuja)
            float sx = (float) (a * signo >= 0 ? 1 : -1);
            if (sx < 0) ox = w * 0.88f;
            float ex = ox + sx * (float) (Math.cos(rad) * L), ey = oy - (float) (Math.sin(rad) * L);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(0x88FFFFFF);
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{12 * d, 8 * d}, 0));
            c.drawLine(ox, oy, ox + sx * L, oy, p);           // lo horizontal
            p.setPathEffect(null);
            p.setColor(0xFFFFB300);
            p.setStrokeWidth(8 * d);
            c.drawLine(ox, oy, ex, ey, p);                    // el techo
            float r = L * 0.35f;
            RectF arco = new RectF(ox - r, oy - r, ox + r, oy + r);
            p.setStrokeWidth(3 * d);
            p.setColor(0xFF7CFF5A);
            if (sx > 0) c.drawArc(arco, (float) -ang, (float) ang, false, p);
            else c.drawArc(arco, 180f, (float) ang, false, p);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Color.WHITE);
            p.setTextSize(60 * d);
            c.drawText(String.format(Locale.US, "%.1f°", ang).replace('.', ','), w / 2, h * 0.80f, p);
            long mm = Math.round(Math.tan(rad) * 1000);
            p.setTextSize(22 * d);
            p.setColor(0xFFFFD54F);
            c.drawText(mm + " mm por metro  ·  " + Math.round(Math.tan(rad) * 100) + " %", w / 2, h * 0.80f + 34 * d, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(14 * d);
            p.setColor(0xFFB0BEC5);
            c.drawText("Apoye el canto del celular sobre el techo, el pasamanos o el tubo", w / 2, h - 8 * d, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }
}
