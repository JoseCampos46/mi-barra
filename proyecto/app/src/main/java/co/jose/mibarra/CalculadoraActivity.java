package co.jose.mibarra;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Calculadora de obra: básica con quebrados, mm ↔ pulgadas, concreto y ladrillo/farol. */
public class CalculadoraActivity extends VentanaActivity {

    private FrameLayout contenido;
    private final Button[] pestanas = new Button[4];
    private static final double MEDIO_CUNETE = 9.5;   // litros
    private static final double FACTOR_CONCRETO = 1.54; // volumen seco / volumen húmedo
    private static final double FACTOR_MORTERO = 1.30;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout raiz = vertical();
        raiz.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout tabs = horizontal();
        String[] nombres = {"Básica", "mm ↔ pulg", "Concreto", "Ladrillo"};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            Button t = new Button(this);
            t.setText(nombres[i]);
            t.setAllCaps(false);
            t.setTextSize(13);
            t.setOnClickListener(v -> mostrar(k));
            pestanas[i] = t;
            tabs.addView(t, peso(1));
        }
        raiz.addView(tabs);
        contenido = new FrameLayout(this);
        raiz.addView(contenido, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(raiz);
        mostrar(getIntent().getIntExtra("pestana", getSharedPreferences("barra", MODE_PRIVATE).getInt("pestanaCalc", 0)));
    }

    private void mostrar(int k) {
        getSharedPreferences("barra", MODE_PRIVATE).edit().putInt("pestanaCalc", k).apply();
        for (int i = 0; i < 4; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(i == k ? 0xFF1565C0 : 0xFFCFD8DC);
            g.setCornerRadius(dp(8));
            pestanas[i].setBackground(g);
            pestanas[i].setTextColor(i == k ? Color.WHITE : 0xFF263238);
        }
        contenido.removeAllViews();
        View v;
        if (k == 0) v = basica();
        else if (k == 1) v = conversor();
        else if (k == 2) v = concreto();
        else v = ladrillo();
        contenido.addView(v);
    }

    // =====================================================================
    // 1. BÁSICA (con quebrados exactos y paréntesis)
    // =====================================================================

    // Versión 17: pantallita con cursor (se corrige sin borrar todo) e historia que se guarda con nombre.
    private EditText pant;
    private TextView tvRes, tvNombre;
    private LinearLayout cinta;
    private ScrollView cintaSv;
    private final java.util.List<String[]> historia = new java.util.ArrayList<>();
    private String nombreHistoria = "";

    private View basica() {
        cargarHistoria();
        LinearLayout l = vertical();
        tvNombre = new TextView(this);
        tvNombre.setTextSize(13);
        tvNombre.setTextColor(0xFF455A64);
        tvNombre.setPadding(dp(4), dp(2), 0, dp(2));
        l.addView(tvNombre);
        cinta = vertical();
        cinta.setPadding(dp(8), dp(4), dp(8), dp(4));
        cintaSv = new ScrollView(this);
        cintaSv.addView(cinta);
        GradientDrawable gc = new GradientDrawable();
        gc.setColor(Color.WHITE);
        gc.setStroke(dp(1), 0xFFD5D8CC);
        gc.setCornerRadius(dp(10));
        cintaSv.setBackground(gc);
        l.addView(cintaSv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.3f));
        LinearLayout acc = horizontal();
        String[] ta = {"💾 Guardar", "📂 Abrir", "📋 Copiar", "📝 Cuaderno", "💬 WhatsApp", "🧹 Nueva"};
        Runnable[] ra = {this::guardarHistoria, this::abrirHistorias, () -> Comun.copiar(this, textoHistoria()),
                () -> Comun.alCuaderno(this, tituloHistoria(), textoHistoria()), () -> Comun.whatsapp(this, textoHistoria()), this::nuevaHistoria};
        for (int i = 0; i < ta.length; i++) {
            Runnable r = ra[i];
            TextView c = Comun.chip(this, ta[i], 0xFFE3EAF5, 0xFF0C447C);
            c.setTextSize(11);
            c.setOnClickListener(v -> r.run());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(dp(1), dp(4), dp(1), dp(2));
            acc.addView(c, p);
        }
        l.addView(acc);
        pant = Comun.pantallita(this, 30);
        l.addView(pant, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        tvRes = new TextView(this);
        tvRes.setTextSize(20);
        tvRes.setGravity(Gravity.END);
        tvRes.setTextColor(0xFF1565C0);
        tvRes.setSingleLine(true);
        tvRes.setPadding(dp(8), 0, dp(8), dp(4));
        l.addView(tvRes);
        pant.setShowSoftInputOnFocus(false); // Versión 29: solo la calculadora; el teclado del celular no sale
        pant.addTextChangedListener(alCambiar(this::actualizarBasica));
        String[][] filas = {
                {"C", "◀", "▶", "⌫"},
                {"7", "8", "9", "÷"},
                {"4", "5", "6", "×"},
                {"1", "2", "3", "−"},
                {"0", ",", "/", "+"},
                {"(", ")", "%", "="},
        };
        for (String[] f : filas) {
            LinearLayout r = horizontal();
            for (String s : f) r.addView(tecla(s), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f) {{
                setMargins(dp(3), dp(3), dp(3), dp(3));
            }});
            l.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        pintarCinta();
        actualizarBasica();
        return l;
    }

    private Button tecla(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(22);
        b.setAllCaps(false);
        int color = "=".equals(s) ? 0xFF2E7D32 : ("C".equals(s) ? 0xFFC62828
                : ("÷×−+%()⌫◀▶".contains(s) ? 0xFF455A64 : "/".equals(s) ? 0xFF6A1B9A : 0xFFECEFF1));
        b.setTextColor((color == 0xFFECEFF1) ? 0xFF111111 : Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        if ("⌫".equals(s) || "◀".equals(s) || "▶".equals(s)) {
            // Dejándola presionada, repite
            final android.os.Handler hh = new android.os.Handler(android.os.Looper.getMainLooper());
            final Runnable[] rep = new Runnable[1];
            rep[0] = () -> { pulsar(s); hh.postDelayed(rep[0], 90); };
            b.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) { pulsar(s); hh.postDelayed(rep[0], 450); v.setPressed(true); }
                else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) { hh.removeCallbacks(rep[0]); v.setPressed(false); }
                return true;
            });
        } else b.setOnClickListener(v -> pulsar(s));
        return b;
    }

    private void pulsar(String s) {
        switch (s) {
            case "C": Comun.poner(pant, ""); break;
            case "⌫": Comun.borrar(pant); break;
            case "◀": Comun.mover(pant, -1); break;
            case "▶": Comun.mover(pant, 1); break;
            case "=": {
                String expr = pant.getText().toString();
                if (expr.trim().isEmpty()) return;
                try {
                    Fr r = new Calc(quebrados(expr)).evaluar();
                    String q = r.quebrado();
                    historia.add(new String[]{expr, r.decimal() + (q.isEmpty() ? "" : "  (" + q + ")")});
                    guardarActual();
                    pintarCinta();
                    Comun.poner(pant, r.paraExpresion());
                } catch (Exception e) {
                    tvRes.setText("Revise la operación");
                }
                return;
            }
            default: Comun.meter(pant, s);
        }
    }

    /**
     * Quebrados fáciles: ½ ¼ ¾ ⅛ y la raya "/" (5/16).  "2½" es 2 y medio, "3 5/16" o "3+5/16" es 3 y cinco dieciseisavos.
     */
    static String quebrados(String e) {
        String[][] g = {{"½", "1", "2"}, {"¼", "1", "4"}, {"¾", "3", "4"}, {"⅛", "1", "8"}};
        for (String[] x : g) {
            e = e.replaceAll("(\\d+(?:,\\d+)?)" + x[0], "($1+" + x[1] + "÷" + x[2] + ")");
            e = e.replace(x[0], "(" + x[1] + "÷" + x[2] + ")");
        }
        e = e.replaceAll("(\\d+) (\\d+)/(\\d+)", "($1+$2÷$3)");
        e = e.replaceAll("(\\d+)/(\\d+)", "($1÷$2)");
        return e.replace("/", "÷");
    }

    private void actualizarBasica() {
        if (tvRes == null || pant == null) return;
        String expr = pant.getText().toString();
        if (expr.isEmpty()) { tvRes.setText(""); return; }
        try {
            Fr r = new Calc(quebrados(expr)).evaluar();
            String q = r.quebrado();
            tvRes.setText("= " + r.decimal() + (q.isEmpty() ? "" : "  ·  " + q));
        } catch (Exception e) {
            tvRes.setText("");
        }
    }

    // ---------------- Historia ----------------

    private java.io.File carpetaHistorias() { java.io.File f = new java.io.File(getFilesDir(), "historias"); f.mkdirs(); return f; }

    private void cargarHistoria() {
        historia.clear();
        nombreHistoria = getSharedPreferences("barra", MODE_PRIVATE).getString("calcNombre", "");
        leerHistoria(new java.io.File(getFilesDir(), "historia_actual.tsv"));
    }

    private void leerHistoria(java.io.File f) {
        historia.clear();
        try {
            for (String s : java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                String[] p = s.split("\t", -1);
                if (p.length >= 2) historia.add(new String[]{p[0], p[1]});
            }
        } catch (Exception ignored) {}
    }

    private void escribirHistoria(java.io.File f) {
        StringBuilder s = new StringBuilder();
        for (String[] x : historia) s.append(x[0].replace('\t', ' ')).append('\t').append(x[1].replace('\t', ' ')).append('\n');
        try { java.nio.file.Files.write(f.toPath(), s.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }

    private void guardarActual() {
        escribirHistoria(new java.io.File(getFilesDir(), "historia_actual.tsv"));
        getSharedPreferences("barra", MODE_PRIVATE).edit().putString("calcNombre", nombreHistoria).apply();
        if (!nombreHistoria.isEmpty()) escribirHistoria(new java.io.File(carpetaHistorias(), Comun.nombreArchivo(nombreHistoria) + ".tsv"));
    }

    private void pintarCinta() {
        if (cinta == null) return;
        tvNombre.setText("📜 Historia: " + (nombreHistoria.isEmpty() ? "sin nombre (toque 💾 para guardarla)" : nombreHistoria));
        cinta.removeAllViews();
        if (historia.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Aquí van quedando sus operaciones. Toque una para volver a usarla.");
            t.setTextColor(0xFF9AA3A8);
            t.setTextSize(13);
            cinta.addView(t);
        }
        for (int i = 0; i < historia.size(); i++) {
            String[] x = historia.get(i);
            final int k = i;
            LinearLayout f = horizontal();
            f.setPadding(0, dp(4), 0, dp(4));
            TextView a = new TextView(this);
            a.setText(x[0]);
            a.setTextSize(15);
            a.setTextColor(0xFF455A64);
            TextView b = new TextView(this);
            b.setText("= " + x[1]);
            b.setTextSize(15);
            b.setTextColor(0xFF1565C0);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setGravity(Gravity.END);
            f.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            f.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            f.setOnClickListener(v -> Comun.poner(pant, historia.get(k)[0]));
            f.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(this).setTitle(historia.get(k)[0])
                        .setItems(new String[]{"✎ Corregir esta operación", "➕ Usar el resultado", "🗑 Quitar de la historia"}, (d, w) -> {
                            if (w == 0) Comun.poner(pant, historia.get(k)[0]);
                            else if (w == 1) Comun.meter(pant, historia.get(k)[1].split(" ")[0].replace(".", ""));
                            else { historia.remove(k); guardarActual(); pintarCinta(); }
                        }).show();
                return true;
            });
            cinta.addView(f);
            View raya = new View(this);
            raya.setBackgroundColor(0xFFE3E6DC);
            cinta.addView(raya, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        cintaSv.post(() -> cintaSv.fullScroll(View.FOCUS_DOWN));
    }

    private String tituloHistoria() { return nombreHistoria.isEmpty() ? "Cuentas de la calculadora" : nombreHistoria; }

    private String textoHistoria() {
        StringBuilder s = new StringBuilder(tituloHistoria()).append('\n');
        for (String[] x : historia) s.append(x[0]).append(" = ").append(x[1]).append('\n');
        return s.toString().trim();
    }

    private void guardarHistoria() {
        EditText e = new EditText(this);
        e.setHint("Ejemplo: Muro patio");
        e.setText(nombreHistoria);
        new android.app.AlertDialog.Builder(this).setTitle("💾 Nombre de esta historia").setView(e)
                .setPositiveButton("Guardar", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty()) return;
                    nombreHistoria = n;
                    guardarActual();
                    pintarCinta();
                    android.widget.Toast.makeText(this, "💾 Guardada: " + n, android.widget.Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void abrirHistorias() {
        java.io.File[] fs = carpetaHistorias().listFiles();
        if (fs == null || fs.length == 0) { android.widget.Toast.makeText(this, "Todavía no hay historias guardadas", android.widget.Toast.LENGTH_SHORT).show(); return; }
        java.util.Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        String[] ns = new String[fs.length];
        for (int i = 0; i < fs.length; i++) ns[i] = fs[i].getName().replaceAll("\\.tsv$", "");
        new android.app.AlertDialog.Builder(this).setTitle("📂 Historias guardadas").setItems(ns, (d, w) -> {
            leerHistoria(fs[w]);
            nombreHistoria = ns[w];
            guardarActual();
            pintarCinta();
        }).show();
    }

    private void nuevaHistoria() {
        new android.app.AlertDialog.Builder(this).setMessage(nombreHistoria.isEmpty() && !historia.isEmpty()
                        ? "Esta historia no tiene nombre y se va a perder. ¿Empezar una nueva?" : "¿Empezar una historia nueva? La guardada no se borra.")
                .setPositiveButton("Sí, nueva", (d, w) -> { historia.clear(); nombreHistoria = ""; guardarActual(); pintarCinta(); Comun.poner(pant, ""); })
                .setNegativeButton("No", null).show();
    }

    /** Fracción exacta. */
    static final class Fr {
        final BigInteger n, d;

        Fr(BigInteger n, BigInteger d) {
            if (d.signum() == 0) throw new ArithmeticException("división por cero");
            if (d.signum() < 0) { n = n.negate(); d = d.negate(); }
            BigInteger g = n.gcd(d);
            if (g.signum() == 0) g = BigInteger.ONE;
            this.n = n.divide(g);
            this.d = d.divide(g);
        }

        static Fr de(long v) { return new Fr(BigInteger.valueOf(v), BigInteger.ONE); }
        Fr mas(Fr o) { return new Fr(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d)); }
        Fr menos(Fr o) { return mas(o.neg()); }
        Fr por(Fr o) { return new Fr(n.multiply(o.n), d.multiply(o.d)); }
        Fr entre(Fr o) { return new Fr(n.multiply(o.d), d.multiply(o.n)); }
        Fr neg() { return new Fr(n.negate(), d); }

        String decimal() {
            BigDecimal v = new BigDecimal(n).divide(new BigDecimal(d), 10, RoundingMode.HALF_UP).stripTrailingZeros();
            String s = v.toPlainString();
            boolean negativo = s.startsWith("-");
            if (negativo) s = s.substring(1);
            String ent = s, dec = "";
            int p = s.indexOf('.');
            if (p >= 0) { ent = s.substring(0, p); dec = s.substring(p + 1); }
            StringBuilder g = new StringBuilder();
            for (int i = 0; i < ent.length(); i++) {
                if (i > 0 && (ent.length() - i) % 3 == 0) g.append('.');
                g.append(ent.charAt(i));
            }
            return (negativo ? "−" : "") + g + (dec.isEmpty() ? "" : "," + dec);
        }

        /** Como quebrado mixto (1 3/4). Vacío si es entero o el denominador es enorme. */
        String quebrado() {
            if (d.equals(BigInteger.ONE) || d.compareTo(BigInteger.valueOf(100000)) > 0) return "";
            BigInteger an = n.abs();
            BigInteger[] qr = an.divideAndRemainder(d);
            String signo = n.signum() < 0 ? "−" : "";
            if (qr[0].signum() == 0) return signo + qr[1] + "/" + d;
            return signo + qr[0] + " " + qr[1] + "/" + d;
        }

        /** Para seguir calculando con el resultado sin perder exactitud. */
        String paraExpresion() {
            BigInteger x = d;
            while (x.mod(BigInteger.valueOf(2)).signum() == 0) x = x.divide(BigInteger.valueOf(2));
            while (x.mod(BigInteger.valueOf(5)).signum() == 0) x = x.divide(BigInteger.valueOf(5));
            if (x.equals(BigInteger.ONE)) {
                String s = new BigDecimal(n).divide(new BigDecimal(d)).stripTrailingZeros().toPlainString();
                return s.replace('.', ',').replace("-", "−");
            }
            return "(" + (n.signum() < 0 ? "−" : "") + n.abs() + "÷" + d + ")";
        }
    }

    /** Lee y resuelve la operación respetando paréntesis y prioridad. */
    static final class Calc {
        private final String s;
        private int i;

        private static final class Val {
            final Fr v; final boolean pct;
            Val(Fr v, boolean pct) { this.v = v; this.pct = pct; }
        }

        Calc(String s) { this.s = s.replace(" ", ""); }

        Fr evaluar() {
            Val v = suma();
            if (i != s.length()) throw new IllegalArgumentException("sobra algo");
            return v.v;
        }

        private char ver() { return i < s.length() ? s.charAt(i) : 0; }

        private Val suma() {
            Val a = producto();
            while (true) {
                char c = ver();
                if (c == '+' || c == '−' || c == '-') {
                    i++;
                    Val b = producto();
                    Fr x = b.pct ? a.v.por(b.v) : b.v; // 200 + 10% = 220
                    a = new Val(c == '+' ? a.v.mas(x) : a.v.menos(x), false);
                } else return a;
            }
        }

        private Val producto() {
            Val a = unario();
            while (true) {
                char c = ver();
                if (c == '×' || c == '*') { i++; a = new Val(a.v.por(unario().v), false); }
                else if (c == '÷' || c == '/') { i++; a = new Val(a.v.entre(unario().v), false); }
                else if (c == '(' || Character.isDigit(c)) { a = new Val(a.v.por(unario().v), false); } // 2(3) = 6
                else return a;
            }
        }

        private Val unario() {
            char c = ver();
            if (c == '−' || c == '-') { i++; Val v = unario(); return new Val(v.v.neg(), v.pct); }
            if (c == '+') { i++; return unario(); }
            Val v = primario();
            while (ver() == '%') { i++; v = new Val(v.v.entre(Fr.de(100)), true); }
            return v;
        }

        private Val primario() {
            char c = ver();
            if (c == '(') {
                i++;
                Val v = suma();
                if (ver() == ')') i++; // si falta cerrar, se cierra solo
                return new Val(v.v, false);
            }
            int ini = i;
            while (Character.isDigit(ver()) || ver() == ',' || ver() == '.') i++;
            if (ini == i) throw new IllegalArgumentException("falta un número");
            String num = s.substring(ini, i).replace('.', ',');
            int coma = num.indexOf(',');
            if (coma < 0) return new Val(new Fr(new BigInteger(num), BigInteger.ONE), false);
            String ent = num.substring(0, coma), dec = num.substring(coma + 1).replace(",", "");
            if (ent.isEmpty()) ent = "0";
            BigInteger den = BigInteger.TEN.pow(dec.length());
            return new Val(new Fr(new BigInteger(ent + dec), den), false);
        }
    }

    // =====================================================================
    // 2. CONVERSOR mm ↔ pulgadas fraccionarias
    // =====================================================================

    private boolean ocupado;

    private View conversor() {
        LinearLayout l = vertical();
        l.setPadding(dp(8), dp(8), dp(8), dp(8));
        l.addView(titulo("Milímetros"));
        EditText mm = numero("Escriba milímetros");
        l.addView(mm);
        l.addView(titulo("Pulgadas"));
        EditText pul = new EditText(this);
        pul.setHint("Ej: 3 5/8   ó   7/16   ó   2");
        pul.setTextSize(22);
        pul.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        l.addView(pul);
        TextView res = resultado();
        l.addView(res);

        mm.addTextChangedListener(alCambiar(() -> {
            if (ocupado) return;
            double v = leer(mm);
            ocupado = true;
            if (mm.getText().toString().trim().isEmpty()) { pul.setText(""); res.setText(""); }
            else {
                double in = v / 25.4;
                pul.setText(pulgadas(in, 128).replace("≈ ", ""));
                res.setText(tablaPulgadas(in));
            }
            ocupado = false;
        }));
        pul.addTextChangedListener(alCambiar(() -> {
            if (ocupado) return;
            double in = leerPulgadas(pul.getText().toString());
            ocupado = true;
            if (Double.isNaN(in)) { mm.setText(""); res.setText(pul.getText().length() > 0 ? "Escriba así: 3 5/8" : ""); }
            else {
                double v = in * 25.4;
                mm.setText(dec(v, 2));
                res.setText("= " + dec(v, 2) + " mm\n= " + dec(v / 10, 2) + " cm\n\n" + tablaPulgadas(in));
            }
            ocupado = false;
        }));
        return desplazable(l);
    }

    private String tablaPulgadas(double in) {
        return "Exacta (1/128):  " + pulgadas(in, 128) + "\n"
                + "En 64avos:  " + pulgadas(in, 64) + "\n"
                + "En 32avos:  " + pulgadas(in, 32) + "\n"
                + "En 16avos:  " + pulgadas(in, 16) + "\n"
                + "En decimal:  " + dec(in, 4) + "\"\n\n"
                + "≈ quiere decir que es la medida comercial más cercana.";
    }

    /** Pulgadas en quebrado redondeado a la fracción pedida (128, 64, 32, 16). */
    static String pulgadas(double in, int den) {
        boolean neg = in < 0;
        double a = Math.abs(in);
        long n = Math.round(a * den);
        boolean exacto = Math.abs(a * den - n) < 1e-6;
        long ent = n / den, num = n % den;
        long d = den;
        while (num > 0 && num % 2 == 0) { num /= 2; d /= 2; }
        String s;
        if (num == 0) s = ent + "\"";
        else if (ent == 0) s = num + "/" + d + "\"";
        else s = ent + " " + num + "/" + d + "\"";
        return (exacto ? "" : "≈ ") + (neg ? "−" : "") + s;
    }

    static double leerPulgadas(String t) {
        t = t.replace("\"", "").replace("-", " ").replace(',', '.').trim();
        if (t.isEmpty()) return Double.NaN;
        try {
            String[] p = t.split("\\s+");
            double total = 0;
            for (String x : p) {
                if (x.contains("/")) {
                    String[] f = x.split("/");
                    total += Double.parseDouble(f[0]) / Double.parseDouble(f[1]);
                } else total += Double.parseDouble(x);
            }
            return Double.isInfinite(total) ? Double.NaN : total;
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    // =====================================================================
    // 3. CONCRETO
    // =====================================================================

    private static final String[] MEZCLAS = {"1:2:2", "1:2:3", "1:2:4", "1:3:6"};
    private int mezcla = 1;
    private boolean enMetros = false;

    private View concreto() {
        LinearLayout l = vertical();
        l.setPadding(dp(8), dp(8), dp(8), dp(8));
        TextView ayuda = new TextView(this);
        ayuda.setText("Columna: ancho × fondo × alto.  Zapata: largo × ancho × profundidad.");
        ayuda.setTextSize(14);
        l.addView(ayuda);
        Button unidad = new Button(this);
        unidad.setAllCaps(false);
        unidad.setText(enMetros ? "Medidas en: metros (toque para cambiar)" : "Medidas en: centímetros (toque para cambiar)");
        l.addView(unidad);
        EditText a = fila(l, "Ancho", "");
        EditText f = fila(l, "Fondo / largo", "");
        EditText h = fila(l, "Alto / profundidad", "");
        EditText n = fila(l, "Cuántas (columnas o zapatas)", "1");
        Button bm = new Button(this);
        bm.setAllCaps(false);
        l.addView(bm);
        EditText desp = fila(l, "Desperdicio %", "5");
        EditText lb = fila(l, "Litros por bulto de cemento", "36");
        TextView res = resultado();
        l.addView(res);

        Runnable calc = () -> {
            double k = enMetros ? 1 : 0.01;
            double vol = leer(a) * k * leer(f) * k * leer(h) * k * Math.max(1, leer(n));
            bm.setText("Mezcla " + MEZCLAS[mezcla] + "  (cemento : arena : grava) — toque para cambiar");
            if (vol <= 0) { res.setText("Escriba las medidas."); return; }
            double volD = vol * (1 + leer(desp) / 100);
            int[] p = partes(MEZCLAS[mezcla]);
            int suma = p[0] + p[1] + p[2];
            double litrosCemento = volD * FACTOR_CONCRETO * p[0] / suma * 1000;
            double litrosBulto = leer(lb) > 0 ? leer(lb) : 36;
            long cuartos = (long) Math.ceil(litrosCemento / litrosBulto * 4 - 1e-9);
            long mArena = cuartos * p[1] / p[0], mGrava = cuartos * p[2] / p[0];
            long medAgua = (long) Math.ceil(cuartos * 12.5 * 0.5 / MEDIO_CUNETE - 1e-9);
            res.setText("Volumen: " + dec(vol, 3) + " m³  (con desperdicio " + dec(volD, 3) + " m³)\n\n"
                    + "🟫 CEMENTO: " + bultos(cuartos) + "  (" + dec(cuartos * 12.5, 1) + " kg)\n"
                    + "      = " + cunetes(cuartos) + "\n\n"
                    + "🟨 ARENA: " + cunetes(mArena) + "\n"
                    + "      = " + dec(mArena * MEDIO_CUNETE / 1000, 2) + " m³\n\n"
                    + "⬜ GRAVA: " + cunetes(mGrava) + "\n"
                    + "      = " + dec(mGrava * MEDIO_CUNETE / 1000, 2) + " m³\n\n"
                    + "💧 AGUA aprox.: " + cunetes(medAgua) + "\n\n"
                    + "CÓMO MEDIR CADA MEZCLA\n"
                    + "• Con ½ bulto: 1 cuñete de cemento, " + p[1] + " de arena, " + p[2] + " de grava.\n"
                    + "• Con ¼ bulto: 1 medio cuñete de cemento, " + p[1] + " de arena, " + p[2] + " de grava (medios cuñetes).\n\n"
                    + "Cuñete = balde de 19 L · medio cuñete = 9,5 L. ¼ de bulto ≈ 1 medio cuñete.");
        };
        unidad.setOnClickListener(v -> {
            enMetros = !enMetros;
            unidad.setText(enMetros ? "Medidas en: metros (toque para cambiar)" : "Medidas en: centímetros (toque para cambiar)");
            calc.run();
        });
        bm.setOnClickListener(v -> { mezcla = (mezcla + 1) % MEZCLAS.length; calc.run(); });
        for (EditText e : new EditText[]{a, f, h, n, desp, lb}) e.addTextChangedListener(alCambiar(calc));
        calc.run();
        return desplazable(l);
    }

    // =====================================================================
    // 4. LADRILLO / FAROL
    // =====================================================================

    private static final String[] PEGAS = {"1:3", "1:4", "1:5", "1:6"};
    private int pega = 1;

    private View ladrillo() {
        LinearLayout l = vertical();
        l.setPadding(dp(8), dp(8), dp(8), dp(8));
        l.addView(titulo("Pared (metros)"));
        EditText pl = fila(l, "Largo de la pared", "");
        EditText ph = fila(l, "Alto de la pared", "");
        l.addView(titulo("Farol o ladrillo (cm), como se ve en la pared"));
        EditText ul = fila(l, "Largo", "30");
        EditText ua = fila(l, "Alto", "20");
        EditText ug = fila(l, "Grueso (ancho del muro)", "10");
        EditText uj = fila(l, "Grueso del mortero de pega", "1,5");
        l.addView(titulo("Ventanas (metros)"));
        EditText va = fila(l, "Ancho", "");
        EditText vh = fila(l, "Alto", "");
        EditText vn = fila(l, "Cantidad", "0");
        l.addView(titulo("Puertas (metros)"));
        EditText da = fila(l, "Ancho", "");
        EditText dh = fila(l, "Alto", "");
        EditText dn = fila(l, "Cantidad", "0");
        Button bp = new Button(this);
        bp.setAllCaps(false);
        l.addView(bp);
        EditText dl = fila(l, "Desperdicio de farol %", "5");
        EditText dm = fila(l, "Desperdicio de mortero %", "10");
        EditText lb = fila(l, "Litros por bulto de cemento", "36");
        TextView res = resultado();
        l.addView(res);

        Runnable calc = () -> {
            bp.setText("Mortero de pega " + PEGAS[pega] + "  (cemento : arena) — toque para cambiar");
            double area = leer(pl) * leer(ph)
                    - leer(va) * leer(vh) * leer(vn)
                    - leer(da) * leer(dh) * leer(dn);
            double L = leer(ul) / 100, A = leer(ua) / 100, G = leer(ug) / 100, J = leer(uj) / 100;
            if (area <= 0 || L <= 0 || A <= 0) { res.setText("Escriba las medidas de la pared y del farol."); return; }
            double porM2 = 1 / ((L + J) * (A + J));
            long piezas = (long) Math.ceil(area * porM2 * (1 + leer(dl) / 100) - 1e-9);
            double mortero = area * (1 - (L * A) * porM2) * G * (1 + leer(dm) / 100);
            int r = partes(PEGAS[pega])[1];
            double litrosCemento = mortero * FACTOR_MORTERO / (1 + r) * 1000;
            double litrosBulto = leer(lb) > 0 ? leer(lb) : 36;
            long cuartos = (long) Math.ceil(litrosCemento / litrosBulto * 4 - 1e-9);
            long mArena = cuartos * r;
            res.setText("Área para pegar: " + dec(area, 2) + " m²  (" + dec(porM2, 1) + " piezas por m²)\n\n"
                    + "🧱 FAROLES / LADRILLOS: " + piezas + "\n\n"
                    + "Mortero: " + dec(mortero, 3) + " m³\n\n"
                    + "🟫 CEMENTO: " + bultos(cuartos) + "  (" + dec(cuartos * 12.5, 1) + " kg)\n"
                    + "      = " + cunetes(cuartos) + "\n\n"
                    + "🟨 ARENA: " + cunetes(mArena) + "\n"
                    + "      = " + dec(mArena * MEDIO_CUNETE / 1000, 2) + " m³\n\n"
                    + "CÓMO MEDIR CADA MEZCLA\n"
                    + "• Con ½ bulto: 1 cuñete de cemento y " + r + " cuñetes de arena.\n"
                    + "• Con ¼ bulto: 1 medio cuñete de cemento y " + r + " medios cuñetes de arena.");
        };
        bp.setOnClickListener(v -> { pega = (pega + 1) % PEGAS.length; calc.run(); });
        for (EditText e : new EditText[]{pl, ph, ul, ua, ug, uj, va, vh, vn, da, dh, dn, dl, dm, lb})
            e.addTextChangedListener(alCambiar(calc));
        calc.run();
        return desplazable(l);
    }

    // =====================================================================
    // Ayudas
    // =====================================================================

    static int[] partes(String m) {
        String[] p = m.split(":");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) r[i] = Integer.parseInt(p[i]);
        return r;
    }

    /** Cuartos de bulto → "2 ¾ bultos". */
    static String bultos(long cuartos) {
        long ent = cuartos / 4, r = cuartos % 4;
        String fr = r == 1 ? "¼" : r == 2 ? "½" : r == 3 ? "¾" : "";
        if (ent == 0 && fr.isEmpty()) return "0 bultos";
        if (ent == 0) return fr + " de bulto";
        return ent + (fr.isEmpty() ? "" : " " + fr) + (ent == 1 && fr.isEmpty() ? " bulto" : " bultos");
    }

    /** Medios cuñetes → "5 cuñetes y 1 medio cuñete". */
    static String cunetes(long medios) {
        long ent = medios / 2, r = medios % 2;
        if (ent == 0 && r == 0) return "0 cuñetes";
        if (ent == 0) return "1 medio cuñete";
        String s = ent + (ent == 1 ? " cuñete" : " cuñetes");
        return r == 1 ? s + " y 1 medio cuñete" : s;
    }

    static String dec(double v, int d) {
        return new BigDecimal(v).setScale(d, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString().replace('.', ',');
    }

    private double leer(EditText e) {
        String s = e.getText().toString().trim().replace(',', '.');
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch (Exception x) { return 0; }
    }

    private TextWatcher alCambiar(Runnable r) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { r.run(); }
        };
    }

    private EditText numero(String pista) {
        EditText e = new EditText(this);
        e.setHint(pista);
        e.setTextSize(20);
        e.setSingleLine(true);
        e.setKeyListener(DigitsKeyListener.getInstance("0123456789.,"));
        e.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private EditText fila(LinearLayout padre, String etiqueta, String valor) {
        LinearLayout r = horizontal();
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(etiqueta);
        t.setTextSize(16);
        r.addView(t, peso(1.4f));
        EditText e = numero("");
        e.setText(valor);
        e.setGravity(Gravity.END);
        r.addView(e, peso(1));
        padre.addView(r);
        return e;
    }

    private TextView titulo(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(18);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(12), 0, dp(2));
        return v;
    }

    private TextView resultado() {
        TextView v = new TextView(this);
        v.setTextSize(17);
        v.setTextColor(0xFF111111);
        v.setTextIsSelectable(true);
        int p = dp(12);
        v.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFFFF8E1);
        g.setCornerRadius(dp(10));
        v.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        v.setLayoutParams(lp);
        return v;
    }

    private ScrollView desplazable(View v) {
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        return sv;
    }

    private int dp(float v) { return Util.dp(this, v); }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams peso(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
    }
}
