package co.jose.mibarra;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Teclado Mi Barra: sugerencias arriba (aprende sus palabras), fila de signos a la vista,
 * números del mismo tamaño, tildes y signos dejando presionado, ◀ ▶ para mover el cursor
 * y 🎤 integrado (usa el dictado sin conexión del celular cuando está descargado).
 */
public class TecladoMiBarra extends InputMethodService {

    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout raiz, filas;
    private TextView[] sug = new TextView[3];
    private boolean mayus, bloqueoMayus, simbolos;
    private SpeechRecognizer sr;
    private boolean escuchando;

    // Diccionario: palabra → veces usada; y pares "palabra siguiente"
    private final Map<String, Integer> dic = new HashMap<>();
    private final Map<String, Map<String, Integer>> siguiente = new HashMap<>();
    private final Map<String, Integer> aprendidas = new HashMap<>();
    private String anterior = "";

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    static final String[] SIMBOLOS = {"@", "#", "$", "%", "*", "-", "+", "=", "/", "(", ")"};   // Versión 36: con el igual
    static final String[] NUM = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    static final String[] NUM_ALT = {"!", "\"", "'", "¿", "?", "=", "&", "×", "÷", "°"};
    static final String[][] LETRAS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"},
            {"z", "x", "c", "v", "b", "n", "m"}};
    static final Map<String, String> ALT = new HashMap<>();
    static {
        String[][] a = {{"q", "%"}, {"w", "\\"}, {"e", "é è ê ë €"}, {"r", "="}, {"t", "["}, {"y", "]"}, {"u", "ú ü ù"}, {"i", "í ì ï"},
                {"o", "ó ò ö º"}, {"p", "}"}, {"a", "á à ä ª @"}, {"s", "#"}, {"d", "$"}, {"f", "-"}, {"g", "&"}, {"h", "_"}, {"j", "+"},
                {"k", "("}, {"l", ")"}, {"ñ", "/"}, {"z", "*"}, {"x", "\""}, {"c", "' ç"}, {"v", ":"}, {"b", ";"}, {"n", "! ñ"}, {"m", "?"},
                {",", "; : …"}, {".", "… ? ! ¿ ¡"}};
        for (String[] x : a) ALT.put(x[0], x[1]);
        ALT.put("@", ".com @gmail.com @hotmail.com");   // Versión 34: la @ presionada
        for (int i = 0; i < 10; i++) ALT.put(NUM[i], NUM_ALT[i]);
    }
    static final String[][] PAG_SIMBOLOS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"@", "#", "$", "%", "&", "-", "+", "(", ")", "/"},
            {"*", "\"", "'", ":", ";", "!", "?", "¿", "¡", "="},
            {"€", "°", "×", "÷", "½", "¼", "¾", "_", "~", "|"}};

    // ---------------- Diccionario ----------------

    /** "cancion" → "canción": la forma con tilde más usada de cada palabra. */
    private final Map<String, String> sinTilde = new HashMap<>();
    /** La última corrección automática, para deshacerla con ⌫. */
    private String corregidaDe, corregidaA;

    private static final String ABC = "abcdefghijklmnñopqrstuvwxyzáéíóúü";

    /** Corrector: si la palabra no existe, busca la más usada que se le parezca (una letra de diferencia o una tilde). */
    private String corregir(String palabra) {
        String w = palabra.toLowerCase(new Locale("es"));
        if (w.length() < 3 || dic.containsKey(w) || !w.matches("[\\p{L}]+")) return null;
        String mejor = sinTilde.get(plano(w));
        if (mejor == null || mejor.equals(w)) {
            mejor = null;
            int peso = 4;
            List<String> cand = new ArrayList<>();
            for (int i = 0; i <= w.length(); i++) {
                if (i < w.length()) cand.add(w.substring(0, i) + w.substring(i + 1));                       // sobra una letra
                if (i < w.length() - 1) cand.add(w.substring(0, i) + w.charAt(i + 1) + w.charAt(i) + w.substring(i + 2)); // dos al revés
                for (int k = 0; k < ABC.length(); k++) {
                    char c = ABC.charAt(k);
                    if (i < w.length()) cand.add(w.substring(0, i) + c + w.substring(i + 1));               // una letra cambiada
                    cand.add(w.substring(0, i) + c + w.substring(i));                                       // falta una letra
                }
            }
            for (String c : cand) { int v = dic.getOrDefault(c, 0); if (v > peso) { peso = v; mejor = c; } }
        }
        if (mejor == null) mejor = adivinar(w);   // Versión 36: también errores de dos letras y teclas vecinas
        if (mejor == null) return null;
        if (Character.isUpperCase(palabra.charAt(0))) mejor = Character.toUpperCase(mejor.charAt(0)) + mejor.substring(1);
        return mejor;
    }

    // ---------------- Versión 36: adivinar la palabra mal tecleada ----------------

    private static final String[] FILAS_TECLAS = {"qwertyuiop", "asdfghjklñ", "zxcvbnm"};

    /** ¿Las dos letras están una al lado de la otra en el teclado? (error típico del dedo) */
    private static boolean vecinas(char a, char b) {
        int ra = -1, ca = -1, rb = -1, cb = -1;
        for (int r = 0; r < 3; r++) {
            int x = FILAS_TECLAS[r].indexOf(a), y = FILAS_TECLAS[r].indexOf(b);
            if (x >= 0) { ra = r; ca = x; }
            if (y >= 0) { rb = r; cb = y; }
        }
        return ra >= 0 && rb >= 0 && Math.abs(ra - rb) <= 1 && Math.abs(ca - cb) <= 1;
    }

    /** Distancia entre dos palabras (letras de más, de menos, cambiadas o volteadas). Tecla vecina cuenta medio error. */
    private static float distancia(String a, String b, float tope) {
        int n = a.length(), m = b.length();
        float[][] d = new float[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            float filaMin = 99;
            for (int j = 1; j <= m; j++) {
                char x = a.charAt(i - 1), y = b.charAt(j - 1);
                float c = x == y ? 0 : vecinas(x, y) ? 0.5f : 1f;
                float v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + c);
                if (i > 1 && j > 1 && x == b.charAt(j - 2) && a.charAt(i - 2) == y) v = Math.min(v, d[i - 2][j - 2] + 0.5f);
                d[i][j] = v;
                filaMin = Math.min(filaMin, v);
            }
            if (filaMin > tope) return 99;
        }
        return d[n][m];
    }

    /** Busca en el diccionario la palabra que usted quiso escribir. */
    private String adivinar(String w) {
        String ps = plano(w.toLowerCase(new Locale("es")));
        if (ps.length() < 3) return null;
        float tope = ps.length() <= 4 ? 1f : ps.length() <= 7 ? 1.5f : 2f;
        String[] ks = claves, fs = formas;
        String mejor = null;
        float mejorD = 99;
        int mejorF = -1;
        for (int i = 0; i < ks.length; i++) {
            String k = ks[i];
            if (Math.abs(k.length() - ps.length()) > 2) continue;
            if (k.charAt(0) != ps.charAt(0) && !vecinas(k.charAt(0), ps.charAt(0))) continue;
            float dd = distancia(ps, k, tope);
            if (dd > tope || dd == 0) continue;
            int f = dic.getOrDefault(fs[i], 0) + aprendidas.getOrDefault(fs[i], 0) * 5;
            if (dd < mejorD - 0.01f || (Math.abs(dd - mejorD) < 0.01f && f > mejorF)) { mejorD = dd; mejorF = f; mejor = fs[i]; }
        }
        return mejor;
    }

    private String autoCorr;

    private File archivoAprendido() { return new File(getFilesDir(), "teclado_palabras.txt"); }

    /** Versión 23: quitar tildes rápido (sin Normalizer, que es lento con 30.000 palabras). */
    static String plano(String w) {
        StringBuilder b = new StringBuilder(w.length());
        for (int i = 0; i < w.length(); i++) {
            char c = Character.toLowerCase(w.charAt(i));
            switch (c) {
                case 'á': c = 'a'; break; case 'é': c = 'e'; break; case 'í': c = 'i'; break;
                case 'ó': c = 'o'; break; case 'ú': case 'ü': c = 'u'; break;
                default: break;
            }
            b.append(c);
        }
        return b.toString();
    }

    /** Palabras ordenadas sin tilde, para buscar por las primeras letras en un instante. */
    private volatile String[] claves = new String[0], formas = new String[0];
    private volatile boolean cargando;

    /** Versión 23: el diccionario se carga aparte, sin trabar el teclado. */
    private void cargarDiccionario() {
        if (!dic.isEmpty() || cargando) return;
        cargando = true;
        new Thread(() -> {
            Map<String, Integer> d = new HashMap<>();
            Map<String, Integer> apr = new HashMap<>();
            Map<String, Map<String, Integer>> sig = new HashMap<>();
            try (InputStream in = getAssets().open("palabras.txt")) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                String[] ps = bo.toString("UTF-8").split("\\s+");
                for (int i = 0; i < ps.length; i++) if (!ps[i].isEmpty()) d.put(ps[i], Math.max(1, 60 - (int) (Math.log(i + 2) * 5.5)));
            } catch (Exception ignored) {}
            try {
                for (String l : Files.readAllLines(archivoAprendido().toPath(), StandardCharsets.UTF_8)) {
                    String[] p = l.split("\t");
                    if (p.length == 2) { int v = Integer.parseInt(p[1]); apr.put(p[0], v); d.put(p[0], d.getOrDefault(p[0], 0) + v); }
                    else if (p.length == 3) sig.computeIfAbsent(p[0], k -> new HashMap<>()).put(p[1], Integer.parseInt(p[2]));
                }
            } catch (Exception ignored) {}
            Map<String, String> st = new HashMap<>();
            List<String[]> orden = new ArrayList<>(d.size());
            for (Map.Entry<String, Integer> e : d.entrySet()) {
                String k = plano(e.getKey());
                orden.add(new String[]{k, e.getKey()});
                String y = st.get(k);
                if (y == null || d.getOrDefault(y, 0) < e.getValue()) st.put(k, e.getKey());
            }
            orden.sort((x, y) -> x[0].compareTo(y[0]));
            String[] ks = new String[orden.size()], fs = new String[orden.size()];
            for (int i = 0; i < ks.length; i++) { ks[i] = orden.get(i)[0]; fs[i] = orden.get(i)[1]; }
            h.post(() -> {
                for (Map.Entry<String, Integer> e : d.entrySet()) dic.merge(e.getKey(), e.getValue(), Math::max);
                aprendidas.putAll(apr);
                for (Map.Entry<String, Map<String, Integer>> e : sig.entrySet()) siguiente.computeIfAbsent(e.getKey(), k -> new HashMap<>()).putAll(e.getValue());
                sinTilde.putAll(st);
                claves = ks;
                formas = fs;
                cargando = false;
                actualizarSugerencias();
            });
        }, "diccionario").start();
    }

    private long ultimaGuardada;

    private void aprender(String palabra) {
        String w = palabra.toLowerCase(new Locale("es")).replaceAll("[^\\p{L}0-9]", "");
        if (w.length() < 2) { anterior = ""; return; }
        dic.put(w, dic.getOrDefault(w, 0) + 3);
        aprendidas.put(w, aprendidas.getOrDefault(w, 0) + 3);
        if (!anterior.isEmpty()) {
            Map<String, Integer> m = siguiente.computeIfAbsent(anterior, k -> new HashMap<>());
            m.put(w, m.getOrDefault(w, 0) + 1);
        }
        anterior = w;
        if (System.currentTimeMillis() - ultimaGuardada > 4000) guardarDiccionario(); // Versión 25: guarda casi enseguida
    }

    private void guardarDiccionario() {
        ultimaGuardada = System.currentTimeMillis();
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : aprendidas.entrySet()) b.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        for (Map.Entry<String, Map<String, Integer>> e : siguiente.entrySet())
            for (Map.Entry<String, Integer> f : e.getValue().entrySet())
                b.append(e.getKey()).append('\t').append(f.getKey()).append('\t').append(f.getValue()).append('\n');
        final byte[] datos = b.toString().getBytes(StandardCharsets.UTF_8);
        new Thread(() -> { try { Files.write(archivoAprendido().toPath(), datos); } catch (Exception ignored) {} }).start();
    }

    // ---------------- La vista ----------------

    @Override
    public View onCreateInputView() {
        cargarDiccionario();
        raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable fondo = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1F2738, 0xFF161C2A});
        raiz.setBackground(fondo);
        raiz.setPadding(dp(3), 0, dp(3), dp(6));
        LinearLayout barra = new LinearLayout(this);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 3; i++) {
            TextView t = new TextView(this);
            t.setTextColor(Color.WHITE);
            t.setTextSize(18);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            if (i == 1) t.setTypeface(Typeface.DEFAULT_BOLD);
            final int k = i;
            t.setOnClickListener(v -> elegirSugerencia(k));
            t.setOnLongClickListener(v -> { olvidarSugerencia(k); return true; });   // Versión 34
            sug[i] = t;
            barra.addView(t, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        // Versión 31: la tira de arriba va de punta a punta del teclado. Mientras el micrófono escucha,
        // ahí va saliendo lo que uno dicta (blanca); el resto del tiempo la barra queda gris.
        FrameLayout marco = new FrameLayout(this);
        GradientDrawable gris = new GradientDrawable();
        gris.setColor(0xFF3A4356);
        gris.setCornerRadius(dp(8));
        barra.setBackground(gris);
        marco.addView(barra, new FrameLayout.LayoutParams(-1, dp(42)));
        // Versión 32: botones cuando hay algo seleccionado: Seleccionar todo · Borrar todo · Corregir gramática
        acciones = new LinearLayout(this);
        acciones.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable gAcc = new GradientDrawable();
        gAcc.setColor(0xFF3A4356);
        gAcc.setCornerRadius(dp(8));
        acciones.setBackground(gAcc);
        acciones.addView(botonAccion("Seleccionar todo", 0xFF185FA5, Color.WHITE, this::seleccionarTodo), new LinearLayout.LayoutParams(0, -1, 1f));
        acciones.addView(botonAccion("Borrar todo", 0xFFA32D2D, Color.WHITE, this::borrarTodo), new LinearLayout.LayoutParams(0, -1, 1f));
        acciones.addView(botonAccion("✨ Corregir gramática", Color.WHITE, 0xFF0F6E56, this::corregirSeleccion), new LinearLayout.LayoutParams(0, -1, 1.25f));
        acciones.setVisibility(View.GONE);
        marco.addView(acciones, new FrameLayout.LayoutParams(-1, dp(42)));
        // Versión 32: sin franja blanca. Lo que se dicta sale en letras blancas sobre la misma barra gris,
        // y la tira solo aparece cuando ya hay algo escrito.
        tira = new TextView(this);
        tira.setTextSize(17);
        tira.setTextColor(Color.WHITE);
        tira.setSingleLine(true);
        tira.setEllipsize(android.text.TextUtils.TruncateAt.START);
        tira.setGravity(Gravity.CENTER_VERTICAL);
        tira.setPadding(dp(12), 0, dp(12), 0);
        tiraFondo = new GradientDrawable();
        tiraFondo.setColor(0xFF3A4356);
        tiraFondo.setCornerRadius(dp(8));
        tira.setBackground(tiraFondo);
        tira.setVisibility(View.GONE);
        tira.setOnClickListener(v -> { if (alTocarTira != null) alTocarTira.run(); });
        marco.addView(tira, new FrameLayout.LayoutParams(-1, dp(42)));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(42));
        mp.setMargins(dp(4), dp(4), dp(4), dp(2));
        raiz.addView(marco, mp);
        filas = new LinearLayout(this);
        filas.setOrientation(LinearLayout.VERTICAL);
        raiz.addView(filas);
        armar();
        return raiz;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        simbolos = false;
        int clase = info.inputType & InputType.TYPE_MASK_CLASS;
        anterior = "";
        armar();
        autoMayus();
        actualizarSugerencias();
        if (clase == InputType.TYPE_CLASS_NUMBER || clase == InputType.TYPE_CLASS_PHONE) { simbolos = true; armar(); }
    }

    @Override public void onFinishInputView(boolean f) {
        try { recordarToken(tokenActual()); } catch (Exception ignored) {}   // Versión 34: guarda el correo o teléfono
        super.onFinishInputView(f);
        if (repActivo != null) { h.removeCallbacks(repActivo); repActivo = null; }
        pararVoz();
        guardarDiccionario();
    }

    /** La tecla que se está repitiendo (⌫ o flechas). Si el teclado se redibuja, se para. */
    private Runnable repActivo;

    private void armar() {
        if (filas == null) return;
        if (!escuchando && !despidiendo) { desilenciar(); pantallaPrendida(false); }   // Versión 34
        if (repActivo != null) { h.removeCallbacks(repActivo); repActivo = null; }
        filas.removeAllViews();
        if (simbolos) {
            // Versión 25: en los números también está ⌫ (borrar), al final de la última fila
            for (int k = 0; k < PAG_SIMBOLOS.length - 1; k++) fila(PAG_SIMBOLOS[k], 1, null);
            LinearLayout fu = filaVacia();
            String[] ult = PAG_SIMBOLOS[PAG_SIMBOLOS.length - 1];
            for (int k = 0; k < ult.length - 2; k++) fu.addView(tecla(ult[k]), peso(1));
            fu.addView(repetible("⌫", this::borrar), peso(1.8f));
        } else {
            fila(SIMBOLOS, 0.8f, 0xFF2C3446);
            fila(NUM, 1, null);
            fila(LETRAS[0], 1, null);
            LinearLayout f2 = filaVacia();
            for (String s : LETRAS[1]) f2.addView(tecla(s), peso(1));
            LinearLayout f3 = filaVacia();
            // Versión 42: flecha de Shift dibujada (gruesa y nítida); amarilla en mayúsculas
            TextView sh = especial("", this::shift, false);
            sh.setBackground(fondoTecla(mayus ? 0xFFFFC107 : 0xFF4B5670));
            sh.setForeground(new Icono(bloqueoMayus ? 2 : mayus ? 1 : 0, mayus ? 0xFF000000 : 0xFFFFFFFF));
            f3.addView(sh, peso(1.4f));
            for (String s : LETRAS[2]) f3.addView(tecla(s), peso(1));
            f3.addView(repetible("⌫", this::borrar), peso(1.4f));
        }
        LinearLayout f4 = filaVacia();
        f4.addView(especial(simbolos ? "ABC" : "?123", () -> { simbolos = !simbolos; armar(); }, false), peso(1.2f));
        TextView mic = especial("🎤", this::voz, false);
        GradientDrawable gm = new GradientDrawable();
        // Versión 32: negro cuando está apagado; verde fijo mientras escucha. Al callarse, titila 3 veces y se apaga.
        gm.setColor(escuchando && oidoListo ? 0xFF43A047 : 0xFF000000);   // Versión 36: verde solo si de verdad escucha
        gm.setCornerRadius(dp(9));
        mic.setBackground(gm);
        micFondo = gm;
        if (escuchando) { mostrarTira(); if (acciones != null) acciones.setVisibility(View.GONE); } else ocultarTira();
        f4.addView(mic, peso(1));
        f4.addView(tecla(","), peso(1));
        f4.addView(repetible("◀", () -> mover(KeyEvent.KEYCODE_DPAD_LEFT)), peso(1));
        f4.addView(espacio(), peso(3.2f));
        f4.addView(repetible("▶", () -> mover(KeyEvent.KEYCODE_DPAD_RIGHT)), peso(1));
        f4.addView(tecla("."), peso(1));
        TextView ent = especial("", this::enter, false);
        ent.setForeground(new Icono(3, 0xFFFFFFFF));
        GradientDrawable ge = new GradientDrawable();
        ge.setColor(0xFF1565C0);
        ge.setCornerRadius(dp(9));
        ent.setBackground(ge);
        f4.addView(ent, peso(1.2f));
    }

    /** Versión 34: si el dedo cae en el espacio entre dos teclas, el toque va a la tecla más cercana. */
    private static final class SinHuecos implements View.OnTouchListener {
        private View obj;
        @Override public boolean onTouch(View v, MotionEvent e) {
            LinearLayout f = (LinearLayout) v;
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                obj = null;
                float mejor = Float.MAX_VALUE;
                for (int i = 0; i < f.getChildCount(); i++) {
                    View c = f.getChildAt(i);
                    if (c.getVisibility() != View.VISIBLE) continue;
                    float dx = e.getX() < c.getLeft() ? c.getLeft() - e.getX() : e.getX() > c.getRight() ? e.getX() - c.getRight() : 0;
                    if (dx < mejor) { mejor = dx; obj = c; }
                }
            }
            if (obj == null) return false;
            MotionEvent m = MotionEvent.obtain(e);
            float x = Math.max(1, Math.min(obj.getWidth() - 1, e.getX() - obj.getLeft()));
            float y = Math.max(1, Math.min(obj.getHeight() - 1, e.getY() - obj.getTop()));
            m.setLocation(x, y);
            obj.dispatchTouchEvent(m);
            m.recycle();
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) obj = null;
            return true;
        }
    }

    private LinearLayout.LayoutParams peso(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, w);
        // Versión 30: teclas separadas como un teclado normal
        p.setMargins(dp(4), dp(5), dp(4), dp(5));
        return p;
    }

    private LinearLayout filaVacia() { return filaVacia(dp(56)); }

    private LinearLayout filaVacia(int alto) {
        LinearLayout f = new LinearLayout(this);
        f.setOnTouchListener(new SinHuecos());   // Versión 34
        filas.addView(f, new LinearLayout.LayoutParams(-1, alto));
        return f;
    }

    private void fila(String[] teclas, float alto, Integer color) {
        LinearLayout f = filaVacia(Math.round(dp(50) * alto));
        for (String s : teclas) {
            TextView t = tecla(s);
            if (color != null) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(color);
                g.setCornerRadius(dp(9));
                t.setBackground(g);
                t.setTextColor(0xFFFFD27A);
                t.setTextSize(18);
            }
            f.addView(t, peso(1));
        }
    }

    private GradientDrawable fondoTecla(int c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(dp(9));
        return g;
    }

    private TextView base(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(22);
        t.setGravity(Gravity.CENTER);
        t.setBackground(fondoTecla(0xFF3A4356));
        return t;
    }

    private String visible(String s) { return mayus && s.length() == 1 && Character.isLetter(s.charAt(0)) ? s.toUpperCase(new Locale("es")) : s; }

    /** Tecla normal: un toque escribe; dejándola presionada salen los signos o las tildes. */
    private TextView tecla(String s) {
        FrameLayoutTecla t = new FrameLayoutTecla(this, visible(s), ALT.get(s));
        t.setOnTouchListener(new View.OnTouchListener() {
            boolean largo;
            String escrita = "";
            final Runnable alLargo = () -> {
                if (!t.isAttachedToWindow()) return;
                largo = true;
                // se quita la letra que ya salió y se ofrecen las otras
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && !escrita.isEmpty()) {
                    CharSequence a = ic.getTextBeforeCursor(escrita.length(), 0);
                    if (a != null && a.toString().equals(escrita)) ic.deleteSurroundingText(escrita.length(), 0);
                }
                mostrarAlternos(t, s);
            };
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        // Versión 34: teclado suave: la letra sale apenas se toca (no al levantar el dedo)
                        largo = false;
                        v.setBackground(fondoTecla(0xFF5B6886));
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        escrita = visible(s);
                        escribir(escrita);
                        if (ALT.containsKey(s)) h.postDelayed(alLargo, 420);
                        return true;
                    case MotionEvent.ACTION_UP:
                        h.removeCallbacks(alLargo);
                        v.setBackground(fondoTecla(0xFF3A4356));
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        h.removeCallbacks(alLargo);
                        v.setBackground(fondoTecla(0xFF3A4356));
                        return true;
                }
                return false;
            }
        });
        return t;
    }

    /** Tecla con su signo pequeñito arriba a la derecha. */
    static final class FrameLayoutTecla extends TextView {
        private final String alt;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        FrameLayoutTecla(android.content.Context c, String texto, String alternos) {
            super(c);
            alt = alternos == null ? null : alternos.split(" ")[0];
            setText(texto);
            setTextColor(Color.WHITE);
            setTextSize(22);
            setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFF3A4356);
            g.setCornerRadius(9 * c.getResources().getDisplayMetrics().density);
            setBackground(g);
            p.setColor(0xAAFFFFFF);
            p.setTextSize(11 * c.getResources().getDisplayMetrics().scaledDensity);
            p.setTextAlign(android.graphics.Paint.Align.RIGHT);
        }
        @Override protected void onDraw(android.graphics.Canvas c) {
            super.onDraw(c);
            if (alt != null && !alt.equals(getText().toString())) c.drawText(alt, getWidth() - p.getTextSize() * 0.4f, p.getTextSize() * 1.05f, p);
        }
    }

    private PopupWindow pop;

    private void mostrarAlternos(View ancla, String s) {
        String[] ops = ALT.get(s).split(" ");
        if (ops.length == 1) { escribir(ops[0]); return; }
        LinearLayout l = new LinearLayout(this);
        boolean largas = false;
        for (String o : ops) if (o.length() > 2) largas = true;
        if (largas) l.setOrientation(LinearLayout.VERTICAL);   // Versión 36: correos uno debajo del otro, completos
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(12));
        l.setBackground(g);
        l.setPadding(dp(4), dp(4), dp(4), dp(4));
        for (String o : ops) {
            String m = mayus && o.length() <= 2 ? o.toUpperCase(new Locale("es")) : o;
            TextView t = new TextView(this);
            t.setText(m);
            t.setTextSize(24);
            t.setTextColor(0xFF111111);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> { escribir(m); if (pop != null) pop.dismiss(); });
            t.setPadding(dp(14), 0, dp(14), 0);
            if (largas) {
                t.setTextSize(21);
                t.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                l.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50)));
            } else l.addView(t, new LinearLayout.LayoutParams(dp(46), dp(52)));
        }
        pop = new PopupWindow(l, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pop.setElevation(dp(8));
        int alto = largas ? dp(50) * ops.length + dp(8) : dp(60);
        pop.showAsDropDown(ancla, 0, -ancla.getHeight() - alto);
    }

    /** Versión 42: dibujos gruesos de Shift (0 vacío, 1 lleno, 2 fijo) y Enter (3). */
    private static final class Icono extends android.graphics.drawable.Drawable {
        final int tipo;
        final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        Icono(int t, int color) {
            tipo = t;
            p.setColor(color);
            p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        }
        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            float lado = Math.min(b.width(), b.height()) * 0.52f;
            float x0 = b.exactCenterX() - lado / 2f, y0 = b.exactCenterY() - lado / 2f;
            p.setStrokeWidth(lado * 0.12f);
            android.graphics.Path f = new android.graphics.Path();
            if (tipo == 3) {
                p.setStyle(android.graphics.Paint.Style.STROKE);
                f.moveTo(x0 + lado * 0.86f, y0 + lado * 0.12f);
                f.lineTo(x0 + lado * 0.86f, y0 + lado * 0.60f);
                f.lineTo(x0 + lado * 0.16f, y0 + lado * 0.60f);
                f.moveTo(x0 + lado * 0.40f, y0 + lado * 0.36f);
                f.lineTo(x0 + lado * 0.16f, y0 + lado * 0.60f);
                f.lineTo(x0 + lado * 0.40f, y0 + lado * 0.84f);
                c.drawPath(f, p);
                return;
            }
            float abajo = tipo == 2 ? 0.74f : 0.92f;
            f.moveTo(x0 + lado * 0.50f, y0 + lado * 0.06f);
            f.lineTo(x0 + lado * 0.94f, y0 + lado * 0.50f);
            f.lineTo(x0 + lado * 0.72f, y0 + lado * 0.50f);
            f.lineTo(x0 + lado * 0.72f, y0 + lado * abajo);
            f.lineTo(x0 + lado * 0.28f, y0 + lado * abajo);
            f.lineTo(x0 + lado * 0.28f, y0 + lado * 0.50f);
            f.lineTo(x0 + lado * 0.06f, y0 + lado * 0.50f);
            f.close();
            p.setStyle(tipo == 0 ? android.graphics.Paint.Style.STROKE : android.graphics.Paint.Style.FILL_AND_STROKE);
            c.drawPath(f, p);
            if (tipo == 2) {
                p.setStyle(android.graphics.Paint.Style.FILL);
                c.drawRoundRect(x0 + lado * 0.24f, y0 + lado * 0.86f, x0 + lado * 0.76f, y0 + lado * 0.98f, lado * 0.04f, lado * 0.04f, p);
            }
        }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private TextView especial(String s, Runnable r, boolean activo) {
        TextView t = base(s);
        t.setTextSize(s.length() > 2 ? 15 : 21);
        t.setBackground(fondoTecla(activo ? 0xFF7986CB : 0xFF4B5670));
        t.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (!"🎤".equals(s)) apagarMicPorTecla();   // Versión 33
            r.run();
        });
        return t;
    }

    /** Teclas que repiten mientras se mantienen: borrar y las flechas. */
    private TextView repetible(String s, Runnable r) {
        TextView t = base(s);
        t.setBackground(fondoTecla(0xFF4B5670));
        t.setOnTouchListener(new View.OnTouchListener() {
            int veces;
            long desde;
            // Versión 32: nunca se queda pegada. Si la tecla ya no está en pantalla, o pasan 3 s
            // sin que el celular note que se soltó el dedo, deja de repetir sola.
            final Runnable rep = new Runnable() { @Override public void run() {
                if (!t.isAttachedToWindow() || System.currentTimeMillis() - desde > 3000) {
                    if (repActivo == this) repActivo = null;
                    t.setBackground(fondoTecla(0xFF4B5670));
                    return;
                }
                r.run(); veces++; h.postDelayed(this, veces > 12 ? 35 : 80); } };
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    veces = 0;
                    desde = System.currentTimeMillis();
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (repActivo != null) h.removeCallbacks(repActivo);
                    repActivo = rep;
                    h.postDelayed(rep, 420);
                    r.run();
                    v.setBackground(fondoTecla(0xFF6B7896));
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL
                        || e.getActionMasked() == MotionEvent.ACTION_POINTER_UP || e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    h.removeCallbacks(rep);
                    if (repActivo == rep) repActivo = null;
                    v.setBackground(fondoTecla(0xFF4B5670));
                }
                return true;
            }
        });
        return t;
    }

    /** Barra espaciadora: un toque pone espacio; deslizando el dedo mueve el cursor. */
    private TextView espacio() {
        TextView t = base("");
        t.setOnTouchListener(new View.OnTouchListener() {
            float x0; boolean movio;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: x0 = e.getX(); movio = false; v.setBackground(fondoTecla(0xFF5B6886)); return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getX() - x0;
                        if (Math.abs(dx) > dp(14)) {
                            movio = true;
                            mover(dx > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
                            x0 = e.getX();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.setBackground(fondoTecla(0xFF3A4356));
                        if (!movio) escribir(" ");
                        return true;
                }
                return false;
            }
        });
        return t;
    }

    // ---------------- Escribir ----------------

    private void escribir(String s) {
        apagarMicPorTecla();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (" ".equals(s) || ",".equals(s)) recordarToken(tokenActual());   // Versión 34
        if (" ".equals(s) || ".".equals(s) || ",".equals(s)) {
            String palabra = palabraActual();
            // Versión 22: revisión automática al terminar la palabra (⌫ enseguida la deja como estaba)
            corregidaDe = null;
            String arreglo = palabra.isEmpty() || palabraNueva(palabra) ? null : corregir(palabra);
            if (arreglo != null) {
                ic.deleteSurroundingText(palabra.length(), 0);
                ic.commitText(arreglo, 1);
                corregidaDe = palabra;
                corregidaA = arreglo;
                palabra = arreglo;
            }
            if (!palabra.isEmpty()) aprender(palabra);
            // ". " después de espacio doble
            CharSequence antes = ic.getTextBeforeCursor(2, 0);
            if (" ".equals(s) && antes != null && antes.length() == 2 && antes.charAt(1) == ' ' && Character.isLetterOrDigit(antes.charAt(0))) {
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
            } else if ((".".equals(s) || ",".equals(s)) && antes != null && antes.length() > 0 && antes.charAt(antes.length() - 1) == ' ') {
                ic.deleteSurroundingText(1, 0);
                ic.commitText(s + " ", 1);
            } else ic.commitText(s, 1);
        } else ic.commitText(s, 1);
        if (mayus && !bloqueoMayus && s.length() == 1 && Character.isLetter(s.charAt(0))) { mayus = false; armar(); }
        autoMayus();
        actualizarSugerencias();
    }

    /** Si ya la escribió igualita antes, es una palabra suya: no se corrige. */
    private boolean palabraNueva(String p) { return aprendidas.getOrDefault(p.toLowerCase(new Locale("es")), 0) >= 6; }

    private void borrar() {
        apagarMicPorTecla();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (corregidaDe != null) {
            CharSequence a0 = ic.getTextBeforeCursor(corregidaA.length() + 2, 0);
            String t0 = a0 == null ? "" : a0.toString();
            for (String fin : new String[]{" ", ". ", ",", "."}) {
                if (t0.endsWith(corregidaA + fin)) {
                    ic.deleteSurroundingText(corregidaA.length() + fin.length(), 0);
                    ic.commitText(corregidaDe + fin, 1);
                    aprendidas.put(corregidaDe.toLowerCase(new Locale("es")), 6); // no la vuelve a corregir
                    dic.put(corregidaDe.toLowerCase(new Locale("es")), 6);
                    guardarDiccionario();
                    corregidaDe = null;
                    actualizarSugerencias();
                    return;
                }
            }
            corregidaDe = null;
        }
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else {
            CharSequence a = ic.getTextBeforeCursor(2, 0);
            int n = a != null && a.length() == 2 && Character.isSurrogatePair(a.charAt(0), a.charAt(1)) ? 2 : 1;
            ic.deleteSurroundingText(n, 0);
        }
        autoMayus();
        actualizarSugerencias();
    }

    private void mover(int tecla) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, tecla));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, tecla));
        actualizarSugerencias();
    }

    private long ultimoShift;

    private void shift() {
        long ahora = System.currentTimeMillis();
        if (mayus && ahora - ultimoShift < 400) bloqueoMayus = true;
        else if (bloqueoMayus) { bloqueoMayus = false; mayus = false; }
        else mayus = !mayus;
        ultimoShift = ahora;
        armar();
    }

    private void enter() {
        apagarMicPorTecla();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String p = palabraActual();
        if (!p.isEmpty()) aprender(p);
        anterior = "";
        EditorInfo ei = getCurrentInputEditorInfo();
        int accion = ei == null ? 0 : ei.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean multilinea = ei != null && (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (!multilinea && accion != EditorInfo.IME_ACTION_NONE && accion != EditorInfo.IME_ACTION_UNSPECIFIED
                && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) ic.performEditorAction(accion);
        else { ic.commitText("\n", 1); autoMayus(); }
    }

    private void autoMayus() {
        if (bloqueoMayus) return;
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null || ei == null) return;
        int var = ei.inputType & InputType.TYPE_MASK_VARIATION;
        if ((ei.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT
                || var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || var == InputType.TYPE_TEXT_VARIATION_URI
                || var == InputType.TYPE_TEXT_VARIATION_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) {
            if (mayus) { mayus = false; armar(); }
            return;
        }
        CharSequence a = ic.getTextBeforeCursor(3, 0);
        String t = a == null ? "" : a.toString();
        boolean debe = t.trim().isEmpty() || t.endsWith(". ") || t.endsWith("? ") || t.endsWith("! ") || t.endsWith("\n") || t.endsWith("¿") || t.endsWith("¡");
        if (debe != mayus) { mayus = debe; armar(); }
    }

    // ---------------- Sugerencias ----------------

    private String palabraActual() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence a = ic.getTextBeforeCursor(40, 0);
        if (a == null) return "";
        String t = a.toString();
        int i = t.length();
        while (i > 0 && Character.isLetterOrDigit(t.charAt(i - 1))) i--;
        return t.substring(i);
    }

    private final String[] mostradas = new String[3];

    // ---------------- Versión 34: correos y teléfonos ----------------

    private boolean modoToken;

    /** Lo escrito desde el último espacio (sirve para correos y teléfonos, que llevan @ . _ -). */
    private String tokenActual() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence a = ic.getTextBeforeCursor(80, 0);
        if (a == null) return "";
        String t = a.toString();
        int i = t.length();
        while (i > 0 && !Character.isWhitespace(t.charAt(i - 1))) i--;
        return t.substring(i);
    }

    private boolean campoCorreo() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) return false;
        int v = ei.inputType & android.text.InputType.TYPE_MASK_VARIATION;
        return (ei.inputType & android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_TEXT
                && (v == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || v == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
    }

    private boolean campoTelefono() {
        EditorInfo ei = getCurrentInputEditorInfo();
        return ei != null && (ei.inputType & android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_PHONE;
    }

    private static final java.util.regex.Pattern CORREO = java.util.regex.Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

    private static String soloDigitos(String t) { return t.replaceAll("[^0-9]", ""); }

    private java.util.List<String> lista(String clave) {
        String g = getSharedPreferences("teclado", MODE_PRIVATE).getString(clave, "");
        java.util.List<String> r = new java.util.ArrayList<>();
        for (String x : g.split("\n")) if (!x.isEmpty()) r.add(x);
        return r;
    }

    private void guardarLista(String clave, java.util.List<String> l) {
        getSharedPreferences("teclado", MODE_PRIVATE).edit().putString(clave, String.join("\n", l)).apply();
    }

    /** Guarda el correo o el teléfono que acaba de escribir (el último usado queda de primero). */
    private void recordarToken(String tok) {
        if (tok == null) return;
        tok = tok.trim();
        while (tok.endsWith(".") || tok.endsWith(",")) tok = tok.substring(0, tok.length() - 1);
        String clave = null, valor = null;
        if (CORREO.matcher(tok).matches()) { clave = "correos"; valor = tok.toLowerCase(Locale.ROOT); }
        else {
            String d = soloDigitos(tok);
            if (d.length() >= 7 && d.length() <= 13 && d.length() >= tok.replaceAll("[\\s()+-]", "").length()) { clave = "telefonos"; valor = d; }
        }
        if (clave == null) return;
        java.util.List<String> l = lista(clave);
        l.remove(valor);
        l.add(0, valor);
        while (l.size() > 60) l.remove(l.size() - 1);
        guardarLista(clave, l);
    }

    private void olvidarSugerencia(int k) {
        String w = mostradas[k];
        if (w == null || w.isEmpty()) return;
        String x = w.replaceFirst("^[📧📞⭐] ?", "");
        boolean quitado = false;
        for (String clave : new String[]{"correos", "telefonos"}) {
            java.util.List<String> l = lista(clave);
            if (l.remove(x.toLowerCase(Locale.ROOT)) || l.remove(soloDigitos(x))) { guardarLista(clave, l); quitado = true; }
        }
        String lw = x.toLowerCase(new Locale("es"));
        if (aprendidas.remove(lw) != null) { dic.remove(lw); guardarDiccionario(); quitado = true; }
        if (quitado) { android.widget.Toast.makeText(this, "Olvidé «" + x + "»", android.widget.Toast.LENGTH_SHORT).show(); actualizarSugerencias(); }
    }

    /** Si está escribiendo un correo o un teléfono, sugiere los que ya ha usado. */
    private boolean sugerirToken() {
        String tok = tokenActual();
        boolean correo = campoCorreo() || tok.contains("@");
        boolean tel = campoTelefono() || (tok.length() >= 3 && soloDigitos(tok).length() == tok.replaceAll("[\\s()+-]", "").length());
        if (!correo && !tel) return false;
        String clave = correo ? "correos" : "telefonos";
        String q = correo ? tok.toLowerCase(Locale.ROOT) : soloDigitos(tok);
        java.util.List<String> r = new java.util.ArrayList<>();
        for (String x : lista(clave)) { if (r.size() >= 3) break; if (x.startsWith(q) && !x.equals(q)) r.add(x); }
        if (r.isEmpty() && !correo && q.length() < 3) return false;
        if (r.isEmpty() && correo && !tok.isEmpty()) return false;
        for (int i = 0; i < 3; i++) {
            mostradas[i] = i < r.size() ? (correo ? "📧 " : "📞 ") + r.get(i) : "";
            sug[i].setText(mostradas[i]);
        }
        modoToken = true;
        return true;
    }

    private void actualizarSugerencias() {
        if (sug[0] == null || escuchando) return;
        modoToken = false;
        if (sugerirToken()) return;
        String p = palabraActual().toLowerCase(new Locale("es"));
        List<String> r = new ArrayList<>();
        if (p.isEmpty()) {
            Map<String, Integer> m = siguiente.get(anterior);
            if (m != null) {
                List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
                l.sort((x, y) -> y.getValue() - x.getValue());
                for (Map.Entry<String, Integer> e : l) { if (r.size() >= 3) break; r.add(e.getKey()); }
            }
            for (String x : new String[]{"de", "que", "la", "el", "y", "para"}) { if (r.size() >= 3) break; if (!r.contains(x)) r.add(x); }
        } else {
            String ps = plano(p);
            List<String> cand = new ArrayList<>();
            String[] ks = claves, fs = formas;
            int lo = java.util.Arrays.binarySearch(ks, ps);
            if (lo < 0) lo = -lo - 1;
            for (int i = lo; i < ks.length && i < lo + 4000 && ks[i].startsWith(ps); i++) cand.add(fs[i]);
            for (String w : aprendidas.keySet()) if (plano(w).startsWith(ps) && !cand.contains(w)) cand.add(w);
            // Versión 34: primero las palabras que usted más usa
            cand.sort((x, y) -> {
                int a = aprendidas.getOrDefault(y, 0) - aprendidas.getOrDefault(x, 0);
                return a != 0 ? a : dic.getOrDefault(y, 0) - dic.getOrDefault(x, 0);
            });
            for (String w : cand) { if (r.size() >= 3) break; if (!w.equals(p)) r.add(w); }
            if (r.size() < 3 && !r.contains(p)) r.add(0, p);
            // Versión 36: si la palabra no existe, adivina la que usted quiso escribir y la pone en el centro
            autoCorr = null;
            if (cand.isEmpty() && p.length() >= 3 && !dic.containsKey(p) && !palabraNueva(p) && p.matches("[\\p{L}]+")) {
                String c = corregir(p);
                if (c != null && !c.equalsIgnoreCase(p)) {
                    autoCorr = c.toLowerCase(new Locale("es"));
                    r.clear();
                    r.add(autoCorr);
                    r.add(p);
                }
            }
        }
        boolean cap = !palabraActual().isEmpty() && Character.isUpperCase(palabraActual().charAt(0));
        int[] orden = {1, 0, 2}; // la mejor va en el centro
        for (int i = 0; i < 3; i++) {
            String w = i < r.size() ? r.get(i) : "";
            if (cap && !w.isEmpty()) w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            mostradas[orden[i]] = w;
        }
        for (int i = 0; i < 3; i++) {
            String w = mostradas[i];
            boolean suya = w != null && !w.isEmpty() && aprendidas.getOrDefault(w.toLowerCase(new Locale("es")), 0) >= 6;
            boolean corr = i == 1 && autoCorr != null && w != null && w.equalsIgnoreCase(autoCorr);
            sug[i].setTextColor(corr ? 0xFF7CF29A : Color.WHITE);
            sug[i].setText(corr ? "✔ " + w : suya && i == 1 ? "⭐ " + w : w);
        }
    }

    private void elegirSugerencia(int k) {
        String w = mostradas[k];
        if (w == null || w.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (modoToken) {   // Versión 34: correo o teléfono completo
            String x = w.replaceFirst("^[📧📞] ?", "");
            String tok = tokenActual();
            if (!tok.isEmpty()) ic.deleteSurroundingText(tok.length(), 0);
            ic.commitText(x, 1);
            recordarToken(x);
            actualizarSugerencias();
            return;
        }
        String p = palabraActual();
        if (!p.isEmpty()) ic.deleteSurroundingText(p.length(), 0);
        w = w.replaceFirst("^[✔⭐] ", "");
        ic.commitText(w + " ", 1);
        aprender(w);
        autoMayus();
        actualizarSugerencias();
    }

    // ---------------- Micrófono ----------------

    private TextView tira;
    private Runnable alTocarTira;
    private GradientDrawable micFondo, tiraFondo;
    private LinearLayout acciones;

    /** Versión 32: la tira de dictado es gris con letras blancas, y solo se ve cuando ya hay algo escrito. */
    private void mostrarTira() {
        if (tira == null) return;
        alTocarTira = null;
        tira.setAlpha(1f);
        tira.setTextColor(Color.WHITE);
        if (tiraFondo != null) tiraFondo.setColor(0xFF3A4356);
        if (tira.getVisibility() == View.VISIBLE && tira.getText().length() > 0) return;
        tira.setText("");
        tira.setVisibility(View.GONE);
    }

    private void textoTira(String t) {
        if (tira == null || !escuchando) return;
        if (t == null || t.trim().isEmpty()) { tira.setText(""); tira.setVisibility(View.GONE); return; }
        tira.setTextColor(Color.WHITE);
        tira.setText(t);
        tira.setVisibility(View.VISIBLE);
    }

    // ---------------- Versión 32: el micrófono se apaga solo, como Gboard ----------------

    /** Segundos callado antes de despedirse (luego titila 3 veces, ≈1,5 s, y se apaga). */
    private boolean despidiendo;
    private final Runnable silencio = new Runnable() {
        @Override public void run() {
            if (!escuchando || despidiendo) return;
            if (ultimaVozMs > 0 && System.currentTimeMillis() - ultimaVozMs > ESPERA_MIC) { despedida(); return; }
            h.postDelayed(this, 500);
        }
    };

    private void vigilarSilencio() {
        h.removeCallbacks(silencio);
        h.postDelayed(silencio, 500);
    }

    /** Suelta el micrófono, lo hace titilar 3 veces en verde y lo deja negro. */
    private void despedida() {
        if (despidiendo) return;
        despidiendo = true;
        seguido = false;
        h.removeCallbacks(vigia);
        h.removeCallbacks(vigiaLocal);
        h.removeCallbacks(silencio);
        try { VozLocal.parar(); } catch (Exception ignored) {}
        if (sr != null) { try { sr.cancel(); sr.destroy(); } catch (Exception ignored) {} sr = null; }
        if (tira != null && alTocarTira == null) { tira.setText(""); tira.setVisibility(View.GONE); }
        // Versión 36: sin titilar; se pone negro de una vez
        if (micFondo != null) micFondo.setColor(0xFF000000);
        h.post(new Runnable() {
            @Override public void run() {
                if (!despidiendo) return;
                despidiendo = false;
                escuchando = false;
                ultimaVozMs = 0;
                pantallaPrendida(false);
                desilenciar();
                armar();
                autoMayus();
                actualizarSugerencias();
            }
        });
    }

    private void ocultarTira() { if (tira != null && alTocarTira == null) tira.setVisibility(View.GONE); }

    /** Versión 31: vigía del micrófono. Si a los 3,5 s no empezó a escuchar, se reinicia solo una vez; si sigue pegado, lo suelta. */
    private boolean oidoListo;
    private int reintentosVigia;
    private final Runnable vigia = new Runnable() {
        @Override public void run() {
            if (!escuchando || oidoListo) return;
            if (reintentosVigia++ < 1 && seguido) { reintentar(200); return; }
            pararVoz();
            armar();
            sug[0].setText(""); sug[1].setText("Toque 🎤 de nuevo"); sug[2].setText("");
        }
    };

    private void voz() {
        if (escuchando) { pararVoz(); armar(); actualizarSugerencias(); return; }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, TecladoAjustesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        // Versión 16: si el español está descargado, escucha con el oído propio (sin internet, sin Google)
        // Versión 22: primero el dictado de Google (entiende mucho mejor); el oído propio solo si no hay otro
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            if (VozLocal.listo(this)) { vozLocal(); return; }
            sug[1].setText("Este celular no tiene dictado");
            return;
        }
        seguido = true;
        reintentosVigia = 0;
        escucharGoogle();
    }

    /** Dictado seguido: escribe cada frase y vuelve a escuchar solo, hasta que toque 🎤 otra vez. */
    private boolean seguido;
    private int vaciosSeguidos;
    /** Versión 23: el micrófono solo se apaga después de 3 minutos sin oír nada. */
    // Versión 30: como Gboard, el micrófono se apaga solo unos segundos después de que uno deja de hablar
    private static final long ESPERA_MIC = 4_000L;
    private long ultimaVozMs;

    private int erroresSeguidos;

    /** Versión 36: vuelve a escuchar de una vez con el mismo oído (sin cerrarlo), para no perder palabras entre frases. */
    private Intent intentoOir;

    private void seguirOyendo() {
        if (sr != null && intentoOir != null) {
            try {
                sr.startListening(intentoOir);
                return;
            } catch (Exception ignored) {}
        }
        reintentar(80);
    }

    private void reintentar(long espera) {
        if (sr != null) { try { sr.cancel(); sr.destroy(); } catch (Exception ignored) {} sr = null; }
        h.postDelayed(() -> { if (seguido) escucharGoogle(); }, espera);
    }

    /** Versión 29: en Xiaomi el dictado del sistema a veces es el de Xiaomi, que entiende mal.
     *  Si el celular tiene el de Google, se usa ese directamente (el mismo que usa Gboard). */
    private SpeechRecognizer reconocedor() {
        android.content.ComponentName g = new android.content.ComponentName("com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch.serviceapi.GoogleRecognitionService");
        try {
            for (android.content.pm.ResolveInfo ri : getPackageManager().queryIntentServices(
                    new Intent(android.speech.RecognitionService.SERVICE_INTERFACE), 0))
                if (ri.serviceInfo != null && g.getPackageName().equals(ri.serviceInfo.packageName))
                    return SpeechRecognizer.createSpeechRecognizer(this, new android.content.ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name));
        } catch (Exception ignored) {}
        return SpeechRecognizer.createSpeechRecognizer(this);
    }

    private void escucharGoogle() {
        if (ultimaVozMs == 0) ultimaVozMs = System.currentTimeMillis();
        sr = reconocedor();
        sr.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) { erroresSeguidos = 0; oidoListo = true; reintentosVigia = 0; mostrarTira(); if (micFondo != null) micFondo.setColor(0xFF43A047); }
            @Override public void onBeginningOfSpeech() { ultimaVozMs = System.currentTimeMillis(); }
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int e) {
                boolean sinRed = e == SpeechRecognizer.ERROR_NETWORK || e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT || e == SpeechRecognizer.ERROR_SERVER;
                // Sin internet y con el español descargado: sigue con el oído propio
                if (seguido && sinRed && VozLocal.listo(TecladoMiBarra.this)) {
                    if (sr != null) { try { sr.destroy(); } catch (Exception ignored) {} sr = null; }
                    seguido = false;
                    vozLocal();
                    return;
                }
                boolean callado = e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                // Versión 24: si el dictado de Google falla varias veces seguidas (no por silencio), pasa al oído propio
                if (!callado) erroresSeguidos++;
                if (seguido && !callado && erroresSeguidos >= 3 && VozLocal.listo(TecladoMiBarra.this)) {
                    if (sr != null) { try { sr.cancel(); sr.destroy(); } catch (Exception ignored) {} sr = null; }
                    seguido = false;
                    erroresSeguidos = 0;
                    vozLocal();
                    return;
                }
                // Mientras no pasen 3 minutos callado, vuelve a escuchar solo
                if (seguido && e != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS && !sinRed && erroresSeguidos < 3
                        && System.currentTimeMillis() - ultimaVozMs < ESPERA_MIC && ++vaciosSeguidos < 400) {
                    if (callado) { seguirOyendo(); return; }
                    reintentar(e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || e == SpeechRecognizer.ERROR_CLIENT ? 900 : 150);
                    return;
                }
                // Versión 32: si fue por silencio, se despide titilando 3 veces
                if (callado) { despedida(); return; }
                escuchando = false;
                seguido = false;
                if (sr != null) { try { sr.destroy(); } catch (Exception ignored) {} sr = null; }
                armar();
                sug[1].setText(e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ? ""
                        : e == SpeechRecognizer.ERROR_NETWORK || e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ? "Sin internet: baje el español sin conexión (⚙)"
                        : "Toque 🎤 otra vez");
            }
            @Override public void onResults(Bundle b) {
                escuchando = false;
                ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (r != null && !r.isEmpty()) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        CharSequence a = ic.getTextBeforeCursor(60, 0);
                        String antes = a == null ? "" : a.toString();
                        String t = NotasActivity.signos(r.get(0), antes);
                        if (!antes.isEmpty() && !antes.endsWith(" ") && !antes.endsWith("\n")) t = " " + t;
                        ic.commitText(t + " ", 1);
                        try { for (String pw : t.split("\\s+")) if (pw.length() > 1) aprender(pw); } catch (Exception ignored) {}   // Versión 34
                        for (String w : t.split("\\s+")) aprender(w);
                    }
                }
                if (tira != null) { tira.setText(""); tira.setVisibility(View.GONE); }
                vaciosSeguidos = 0;
                ultimaVozMs = System.currentTimeMillis();
                erroresSeguidos = 0;
                if (seguido) { escuchando = true; seguirOyendo(); return; }
                armar();
                autoMayus();
                actualizarSugerencias();
            }
            @Override public void onPartialResults(Bundle b) {
                ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (r != null && !r.isEmpty()) { textoTira(r.get(0)); ultimaVozMs = System.currentTimeMillis(); }
            }
            @Override public void onEvent(int t, Bundle b) {}
        });
        Intent i = intentoOir = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        boolean ya = escuchando;
        escuchando = true;
        if (!ya) armar();
        oidoListo = false;
        h.removeCallbacks(vigia);
        h.postDelayed(vigia, 3500);
        vigilarSilencio();
        silenciarPito();
        pantallaPrendida(true);
        sr.startListening(i);
    }

    /** Dictado con el oído propio: escucha seguido; cada frase se escribe sola. Toque 🎤 para parar. */
    private void vozLocal() {
        escuchando = true;
        pantallaPrendida(true);
        ultimaVozMs = System.currentTimeMillis();
        armar();
        vigilarSilencio();
        oidoListo = false;
        h.removeCallbacks(vigiaLocal);
        h.postDelayed(vigiaLocal, 8000);
        VozLocal.escuchar(this, new VozLocal.Oido() {
            @Override public void parcial(String t) { oidoListo = true; if (t != null && !t.trim().isEmpty()) ultimaVozMs = System.currentTimeMillis(); if (escuchando) textoTira(t); }
            @Override public void frase(String t) {
                if (!escuchando) return;
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;
                CharSequence a = ic.getTextBeforeCursor(60, 0);
                String antes = a == null ? "" : a.toString();
                String x = NotasActivity.signos(t, antes);
                if (!antes.isEmpty() && !antes.endsWith(" ") && !antes.endsWith("\n")) x = " " + x;
                ic.commitText(x + " ", 1);
                for (String w : x.split("\\s+")) aprender(w);
                oidoListo = true;
                ultimaVozMs = System.currentTimeMillis();
                textoTira("");
            }
            @Override public void error(String txt) { escuchando = false; armar(); sug[1].setText(txt); }
            @Override public void terminado() { if (escuchando) { escuchando = false; armar(); actualizarSugerencias(); } }
        });
    }

    /** Si el oído propio no da señales en 8 s, se suelta (no queda el micrófono prendido sin escuchar). */
    private final Runnable vigiaLocal = () -> {
        if (!escuchando || oidoListo) return;
        pararVoz();
        armar();
        sug[0].setText(""); sug[1].setText("Toque 🎤 de nuevo"); sug[2].setText("");
    };

    // ---------------- Versión 33 ----------------

    /** Al tocar cualquier tecla (espacio, borrar, letras…) el micrófono se apaga de una vez. */
    private void apagarMicPorTecla() {
        if (!escuchando && !despidiendo) return;
        pararVoz();
        if (micFondo != null) micFondo.setColor(0xFF000000);
        if (tira != null && alTocarTira == null) { tira.setText(""); tira.setVisibility(View.GONE); }
    }

    /** Mientras el micrófono escucha, la pantalla no se apaga. */
    private void pantallaPrendida(boolean si) {
        try {
            android.view.Window w = getWindow() != null ? getWindow().getWindow() : null;
            if (w == null) return;
            if (si) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception ignored) {}
    }

    /** Versión 34: el sonido queda bajo todo el rato que el micrófono está en verde, y vuelve
     *  un segundo después de apagarse (así Google no suena ni al prender ni al apagar). */
    private boolean silenciado;
    private final int[] canales = {android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.STREAM_SYSTEM};
    private final boolean[] yaMudo = new boolean[2];
    private final Handler hSonido = new Handler(Looper.getMainLooper());
    private final Runnable devolverSonido = () -> {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            for (int i = 0; i < canales.length; i++)
                if (!yaMudo[i]) am.adjustStreamVolume(canales[i], android.media.AudioManager.ADJUST_UNMUTE, 0);
        } catch (Exception ignored) {}
        silenciado = false;
    };

    private void silenciarPito() {
        hSonido.removeCallbacks(devolverSonido);
        if (silenciado) { hSonido.postDelayed(devolverSonido, 5 * 60_000); return; }
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            for (int i = 0; i < canales.length; i++) {
                yaMudo[i] = am.isStreamMute(canales[i]);
                if (!yaMudo[i]) am.adjustStreamVolume(canales[i], android.media.AudioManager.ADJUST_MUTE, 0);
            }
            silenciado = true;
            hSonido.postDelayed(devolverSonido, 5 * 60_000);   // por si acaso, nunca queda mudo para siempre
        } catch (Exception ignored) { silenciado = false; }
    }

    private void desilenciar() {
        if (!silenciado) return;
        hSonido.removeCallbacks(devolverSonido);
        hSonido.postDelayed(devolverSonido, 1200);
    }

    private void pararVoz() {
        pantallaPrendida(false);
        desilenciar();
        VozLocal.parar();
        despidiendo = false;
        escuchando = false;
        seguido = false;
        vaciosSeguidos = 0;
        erroresSeguidos = 0;
        ultimaVozMs = 0;
        h.removeCallbacksAndMessages(null);
        if (sr != null) { try { sr.stopListening(); sr.destroy(); } catch (Exception ignored) {} sr = null; }
    }

    // ---------------- Versión 31: corregir lo seleccionado ----------------

    private int selIni, selFin;

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candStart, int candEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd);
        selIni = Math.min(newSelStart, newSelEnd);
        selFin = Math.max(newSelStart, newSelEnd);
        if (tira == null || acciones == null || escuchando || corrigiendo) return;
        // Versión 32: con algo seleccionado salen los botones Seleccionar todo · Borrar todo · Corregir gramática
        if (selFin > selIni) {
            if (hayDeshacer) quitarDeshacer.run();
            acciones.setVisibility(View.VISIBLE);
        } else {
            acciones.setVisibility(View.GONE);
        }
    }

    private View botonAccion(String txt, int fondo, int letra, Runnable r) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextColor(letra);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), 0, dp(4), 0);
        // el letrero se achica solo si no cabe: nunca queda cortado
        t.setAutoSizeTextTypeUniformWithConfiguration(10, 15, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fondo);
        g.setCornerRadius(dp(7));
        t.setBackground(g);
        t.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); r.run(); });
        FrameLayout envoltura = new FrameLayout(this);
        envoltura.setPadding(dp(2), dp(3), dp(2), dp(3));
        envoltura.addView(t, new FrameLayout.LayoutParams(-1, -1));
        return envoltura;
    }

    private void seleccionarTodo() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.performContextMenuAction(android.R.id.selectAll);
    }

    /** Borra todo lo escrito y deja «deshacer» 10 segundos, por si fue sin querer. */
    private void borrarTodo() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String antes = "";
        try {
            android.view.inputmethod.ExtractedText et = ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0);
            if (et != null && et.text != null) antes = et.text.toString();
        } catch (Exception ignored) {}
        ic.beginBatchEdit();
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.commitText("", 1);
        ic.endBatchEdit();
        if (acciones != null) acciones.setVisibility(View.GONE);
        if (antes.isEmpty()) return;
        final String guardado = antes;
        hayDeshacer = true;
        tira.setAlpha(1f);
        if (tiraFondo != null) tiraFondo.setColor(0xFF3A4356);
        tira.setTextColor(0xFF90CAF9);
        tira.setText("Se borró todo · toque aquí para ↩ deshacer");
        tira.setVisibility(View.VISIBLE);
        alTocarTira = () -> {
            InputConnection c2 = getCurrentInputConnection();
            if (c2 != null) c2.commitText(guardado, 1);
            quitarDeshacer.run();
        };
        h.removeCallbacks(quitarDeshacer);
        h.postDelayed(quitarDeshacer, 10000);
    }

    private boolean corrigiendo, hayDeshacer;

    private boolean hayInternet() {
        try {
            android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Exception e) { return false; }
    }

    private void corregirSeleccion() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (sel == null || sel.length() == 0) return;
        final String original = sel.toString();
        final int ini = selIni;
        String basico;
        try { basico = Corrector.corregir(original, new HashMap<>()).texto; } catch (Exception e) { basico = original; }
        if (basico == null || basico.isEmpty()) basico = original;
        final String base = basico;
        if (!hayInternet()) {
            ponerCorregido(ini, original, base);
            avisoParpadeante("⚠ No hay internet para corregir la gramática");
            return;
        }
        corrigiendo = true;
        if (acciones != null) acciones.setVisibility(View.GONE);
        tira.setAlpha(1f);
        if (tiraFondo != null) tiraFondo.setColor(0xFF3A4356);
        tira.setTextColor(Color.WHITE);
        tira.setText("✍ Corrigiendo…");
        tira.setVisibility(View.VISIBLE);
        alTocarTira = null;
        new Thread(() -> {
            String bueno = base;
            try { bueno = languageTool(base); } catch (Exception ignored) {}
            final String fin = bueno;
            h.post(() -> { corrigiendo = false; ponerCorregido(ini, original, fin); });
        }, "corregir").start();
    }

    /** Cambia lo seleccionado por lo corregido y deja «Deshacer» un rato en la tira. */
    private void ponerCorregido(int ini, String original, String nuevo) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        ic.setSelection(ini, ini + original.length());
        ic.commitText(nuevo, 1);
        ic.endBatchEdit();
        hayDeshacer = true;
        if (acciones != null) acciones.setVisibility(View.GONE);
        tira.setAlpha(1f);
        if (tiraFondo != null) tiraFondo.setColor(0xFF3A4356);
        tira.setVisibility(View.VISIBLE);
        tira.setTextColor(0xFF90CAF9);
        tira.setText(original.equals(nuevo) ? "✔ Estaba bien escrito" : "✔ Corregido · toque aquí para ↩ deshacer");
        alTocarTira = () -> {
            InputConnection c2 = getCurrentInputConnection();
            if (c2 == null) return;
            c2.beginBatchEdit();
            c2.setSelection(ini, ini + nuevo.length());
            c2.commitText(original, 1);
            c2.endBatchEdit();
            quitarDeshacer.run();
        };
        h.removeCallbacks(quitarDeshacer);
        h.postDelayed(quitarDeshacer, 10000);
    }

    private final Runnable quitarDeshacer = () -> {
        hayDeshacer = false;
        alTocarTira = null;
        if (tira != null && !escuchando) tira.setVisibility(View.GONE);
    };

    /** Un aviso que parpadea en la tira (amarillo) unos segundos. */
    private void avisoParpadeante(String txt) {
        if (tira == null) return;
        final int[] n = {0};
        Runnable r = new Runnable() {
            @Override public void run() {
                if (tira == null || escuchando) return;
                n[0]++;
                // Versión 32: aviso amarillo que titila y luego vuelve a la barra gris
                tira.setVisibility(View.VISIBLE);
                tira.setText(txt);
                if (tiraFondo != null) tiraFondo.setColor(0xFFFAC775);
                tira.setTextColor(0xFF412402);
                tira.setAlpha(n[0] % 2 == 0 ? 1f : 0.45f);
                if (n[0] < 12) h.postDelayed(this, 400);
                else {
                    tira.setAlpha(1f);
                    if (tiraFondo != null) tiraFondo.setColor(0xFF3A4356);
                    tira.setTextColor(0xFF90CAF9);
                    tira.setText("✔ Arreglé lo básico · toque aquí para ↩ deshacer");
                }
            }
        };
        h.postDelayed(r, 300);
    }

    /** Corrector de internet gratuito (LanguageTool) para gramática y puntuación en español. */
    private static String languageTool(String texto) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://api.languagetool.org/v2/check").openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(9000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        String cuerpo = "language=es&text=" + java.net.URLEncoder.encode(texto, "UTF-8");
        c.getOutputStream().write(cuerpo.getBytes(StandardCharsets.UTF_8));
        if (c.getResponseCode() != 200) return texto;
        String json;
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            json = new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
        org.json.JSONArray m = new org.json.JSONObject(json).getJSONArray("matches");
        StringBuilder sb = new StringBuilder(texto);
        // De atrás hacia adelante para no correr las posiciones
        for (int i = m.length() - 1; i >= 0; i--) {
            org.json.JSONObject x = m.getJSONObject(i);
            org.json.JSONArray rs = x.optJSONArray("replacements");
            if (rs == null || rs.length() == 0) continue;
            int off = x.getInt("offset"), len = x.getInt("length");
            if (off < 0 || off + len > sb.length()) continue;
            String mal = sb.substring(off, off + len);
            String cat = x.optJSONObject("rule") != null && x.getJSONObject("rule").optJSONObject("category") != null
                    ? x.getJSONObject("rule").getJSONObject("category").optString("id", "") : "";
            // Nombres propios (con mayúscula) que el corrector no conoce: se dejan quietos
            if ("TYPOS".equals(cat) && !mal.isEmpty() && Character.isUpperCase(mal.charAt(0))) continue;
            sb.replace(off, off + len, rs.getJSONObject(0).getString("value"));
        }
        return sb.toString();
    }

    @Override public void onDestroy() { pararVoz(); guardarDiccionario(); super.onDestroy(); }
}
