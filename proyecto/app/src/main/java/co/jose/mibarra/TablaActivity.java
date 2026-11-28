package co.jose.mibarra;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versión 17 · Tabla de obra: una calculadora tipo Excel.
 * Cada fila: Artículo · Cantidad u operación · Total. Con T1, T2… se usa el total de otra fila.
 */
public class TablaActivity extends Base {

    static final class Fila { String nombre = "", formula = ""; }

    private final List<Fila> filas = new ArrayList<>();
    private String nombreTabla = "";
    private int sumaDesde = 0, sumaHasta = 0;   // 0 = todas
    private int sel = 0;
    private LinearLayout tabla;
    private ScrollView tablaSv;
    private TextView tvTitulo, tvFila, tvRes;
    private EditText pant;
    private boolean pintando;

    private static final Pattern REF = Pattern.compile("T(\\d+)");

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cargar(new File(getFilesDir(), "tabla_actual.tsv"));
        nombreTabla = getSharedPreferences("barra", MODE_PRIVATE).getString("tablaNombre", "");
        if (filas.isEmpty()) filas.add(new Fila());
        sel = filas.size() - 1;

        LinearLayout l = vertical();
        l.setPadding(dp(6), dp(6), dp(6), dp(6));
        tvTitulo = new TextView(this);
        tvTitulo.setTextSize(14);
        tvTitulo.setTextColor(0xFF263238);
        tvTitulo.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(tvTitulo);

        tabla = vertical();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(tabla);
        tablaSv = new ScrollView(this);
        tablaSv.addView(hs);
        tablaSv.setBackground(Comun.fondo(this, Color.WHITE, 8));
        l.addView(tablaSv, new LinearLayout.LayoutParams(-1, 0, 1.25f));

        LinearLayout acc = horizontal();
        String[] ta = {"💾 Guardar", "📂 Abrir", "📋 Copiar", "📝 Cuaderno", "💬 WhatsApp", "🧹 Nueva", "📊 Cuentas"};
        // Versión 35: las «Cuentas» (mini Excel) quedan aquí adentro, con todo lo que ya tenían guardado
        Runnable[] ra = {this::guardarComo, this::abrirTablas, () -> Comun.copiar(this, texto()),
                () -> Comun.alCuaderno(this, titulo(), texto()), () -> Comun.whatsapp(this, texto()), this::nueva,
                () -> startActivity(new android.content.Intent(this, HojaActivity.class))};
        for (int i = 0; i < ta.length; i++) {
            Runnable r = ra[i];
            TextView c = Comun.chip(this, ta[i], 0xFFE3EAF5, 0xFF0C447C);
            c.setTextSize(11);
            c.setOnClickListener(v -> r.run());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(dp(1), dp(4), dp(1), dp(4));
            acc.addView(c, p);
        }
        l.addView(acc);

        // Recuadro amarillo: la fila que se está escribiendo
        LinearLayout ed = vertical();
        ed.setPadding(dp(8), dp(4), dp(8), dp(4));
        android.graphics.drawable.GradientDrawable ge = Comun.fondo(this, Color.WHITE, 10);
        ge.setStroke(dp(2), 0xFFF2B62A);
        ed.setBackground(ge);
        tvFila = new TextView(this);
        tvFila.setTextSize(12);
        tvFila.setTextColor(0xFF8D6E00);
        ed.addView(tvFila);
        LinearLayout lin = horizontal();
        lin.setGravity(Gravity.CENTER_VERTICAL);
        pant = Comun.pantallita(this, 24);
        lin.addView(pant, new LinearLayout.LayoutParams(0, dp(48), 1f));
        tvRes = new TextView(this);
        tvRes.setTextSize(18);
        tvRes.setTextColor(0xFF1565C0);
        tvRes.setPadding(dp(6), 0, 0, 0);
        lin.addView(tvRes);
        ed.addView(lin);
        l.addView(ed);
        pant.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void afterTextChanged(Editable e) {
                if (pintando || sel < 0 || sel >= filas.size()) return;
                filas.get(sel).formula = e.toString();
                guardarActual();
                pintarTabla();
            }
        });

        String[][] teclas = {
                {"✏ Nombre", "T# fila", "Σ Sumar", "＋ Fila", "C"},
                {"7", "8", "9", "÷", "⌫"},
                {"4", "5", "6", "×", "("},
                {"1", "2", "3", "−", ")"},
                {"0", ",", "/", "+", "%"},
                {"◀", "▶", "= Listo · siguiente fila"},
        };
        for (String[] f : teclas) {
            LinearLayout r = horizontal();
            for (String s : f) {
                float peso = s.startsWith("= Listo") ? 3f : 1f;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, peso);
                p.setMargins(dp(2), dp(2), dp(2), dp(2));
                r.addView(tecla(s), p);
            }
            l.addView(r, new LinearLayout.LayoutParams(-1, 0, 0.42f));
        }
        setContentView(l);
        elegir(sel);
    }

    private Button tecla(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        boolean accion = s.length() > 2 && !s.startsWith("= ");
        b.setTextSize(accion ? 12 : 20);
        int c = s.startsWith("=") ? 0xFF2E7D32 : "C".equals(s) ? 0xFFC62828 : accion ? 0xFF1565C0
                : "/".equals(s) ? 0xFF6A1B9A : "÷×−+%()⌫◀▶".contains(s) ? 0xFF455A64 : 0xFFECEFF1;
        b.setTextColor(c == 0xFFECEFF1 ? 0xFF111111 : Color.WHITE);
        b.setBackground(Comun.fondo(this, c, 10));
        if ("⌫".equals(s) || "◀".equals(s) || "▶".equals(s)) {
            final android.os.Handler hh = new android.os.Handler(android.os.Looper.getMainLooper());
            final Runnable[] rep = new Runnable[1];
            rep[0] = () -> { pulsar(s); hh.postDelayed(rep[0], 90); };
            b.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { pulsar(s); hh.postDelayed(rep[0], 450); }
                else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) hh.removeCallbacks(rep[0]);
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
            case "✏ Nombre": ponerNombre(); break;
            case "T# fila": elegirReferencia(); break;
            case "Σ Sumar": sumar(); break;
            case "＋ Fila": filas.add(sel + 1, new Fila()); guardarActual(); elegir(sel + 1); break;
            default:
                if (s.startsWith("= Listo")) {
                    if (sel == filas.size() - 1) filas.add(new Fila());
                    guardarActual();
                    elegir(sel + 1);
                } else Comun.meter(pant, s);
        }
    }

    private void elegir(int k) {
        sel = Math.max(0, Math.min(filas.size() - 1, k));
        pintando = true;
        Comun.poner(pant, filas.get(sel).formula);
        pintando = false;
        pintarTabla();
    }

    // ---------------- Cálculo ----------------

    /** El valor de la fila k (0 = primera), siguiendo T1, T2… sin dar vueltas infinitas. */
    private CalculadoraActivity.Fr valor(int k, Set<Integer> visitando) {
        if (k < 0 || k >= filas.size()) throw new IllegalArgumentException("no existe la fila " + (k + 1));
        if (!visitando.add(k)) throw new IllegalArgumentException("se llama a sí misma");
        String f = filas.get(k).formula.trim();
        if (f.isEmpty()) { visitando.remove(k); return CalculadoraActivity.Fr.de(0); }
        Matcher m = REF.matcher(f);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int otra = Integer.parseInt(m.group(1)) - 1;
            CalculadoraActivity.Fr v = valor(otra, visitando);
            m.appendReplacement(sb, Matcher.quoteReplacement("(" + v.paraExpresion() + ")"));
        }
        m.appendTail(sb);
        CalculadoraActivity.Fr r = new CalculadoraActivity.Calc(CalculadoraActivity.quebrados(sb.toString())).evaluar();
        visitando.remove(k);
        return r;
    }

    private String total(int k) {
        if (filas.get(k).formula.trim().isEmpty()) return "";
        try { return valor(k, new HashSet<>()).decimal(); } catch (Exception e) { return "⚠ revisar"; }
    }

    private String sumaTexto() {
        int a = sumaDesde <= 0 ? 1 : sumaDesde, b = sumaHasta <= 0 ? filas.size() : Math.min(sumaHasta, filas.size());
        CalculadoraActivity.Fr t = CalculadoraActivity.Fr.de(0);
        try {
            for (int i = a - 1; i < b; i++) if (!filas.get(i).formula.trim().isEmpty()) t = t.mas(valor(i, new HashSet<>()));
            return t.decimal();
        } catch (Exception e) { return "⚠ revisar"; }
    }

    private String rango() {
        return sumaDesde <= 0 ? "todas las filas" : "filas " + sumaDesde + " a " + (sumaHasta <= 0 ? filas.size() : sumaHasta);
    }

    // ---------------- Pintar ----------------

    private void pintarTabla() {
        tvTitulo.setText("📄 " + (nombreTabla.isEmpty() ? "Tabla sin nombre (toque 💾)" : nombreTabla));
        tabla.removeAllViews();
        int[] anchos = {dp(34), dp(130), dp(150), dp(100)};
        tabla.addView(filaVista(new String[]{"#", "Artículo", "Cantidad / operación", "Total"}, anchos, 0xFF455A64, Color.WHITE, true, -1));
        for (int i = 0; i < filas.size(); i++) {
            Fila f = filas.get(i);
            int fondo = i == sel ? 0xFFFFF8E1 : (i % 2 == 0 ? Color.WHITE : 0xFFF7F8F4);
            tabla.addView(filaVista(new String[]{String.valueOf(i + 1), f.nombre, f.formula, total(i)}, anchos, fondo, 0xFF263238, false, i));
        }
        tabla.addView(filaVista(new String[]{"Σ", "Total general (" + rango() + ")", "", sumaTexto()}, anchos, 0xFFE8F5E9, 0xFF1B5E20, true, -2));
        tvFila.setText("Fila " + (sel + 1) + (filas.get(sel).nombre.isEmpty() ? "" : " · " + filas.get(sel).nombre)
                + "   (toque el número o el signo para corregirlo)");
        String t = total(sel);
        tvRes.setText(t.isEmpty() ? "" : "= " + t);
    }

    private View filaVista(String[] c, int[] anchos, int fondo, int letra, boolean negrita, int indice) {
        LinearLayout f = horizontal();
        f.setBackgroundColor(fondo);
        for (int i = 0; i < c.length; i++) {
            TextView t = new TextView(this);
            t.setText(c[i]);
            t.setTextSize(14);
            t.setTextColor(i == 3 && indice >= 0 ? 0xFF1565C0 : letra);
            if (negrita || i == 3) t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setGravity(i == 3 ? Gravity.END : i == 0 ? Gravity.CENTER : Gravity.START);
            t.setPadding(dp(5), dp(9), dp(5), dp(9));
            t.setSingleLine(true);
            if (i == 0 && indice >= 0) t.setBackgroundColor(indice == sel ? 0xFFFFE082 : 0xFFECEFF1);
            f.addView(t, new LinearLayout.LayoutParams(anchos[i], -2));
        }
        if (indice >= 0) {
            final int k = indice;
            f.setOnClickListener(v -> elegir(k));
            f.setOnLongClickListener(v -> { opcionesFila(k); return true; });
        }
        LinearLayout envoltura = vertical();
        envoltura.addView(f);
        View raya = new View(this);
        raya.setBackgroundColor(0xFFDDE1D6);
        envoltura.addView(raya, new LinearLayout.LayoutParams(-1, 1));
        return envoltura;
    }

    // ---------------- Acciones ----------------

    private void ponerNombre() {
        EditText e = campoTexto("Ejemplo: Ladrillos", filas.get(sel).nombre);
        new AlertDialog.Builder(this).setTitle("✏ Artículo de la fila " + (sel + 1)).setView(e)
                .setPositiveButton("Listo", (d, w) -> { filas.get(sel).nombre = Base.limpio(e.getText().toString()); guardarActual(); pintarTabla(); })
                .setNegativeButton("Cancelar", null).show();
    }

    private void elegirReferencia() {
        List<String> ops = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) if (i != sel)
            ops.add("T" + (i + 1) + "  ·  " + (filas.get(i).nombre.isEmpty() ? "fila " + (i + 1) : filas.get(i).nombre) + "  = " + total(i));
        if (ops.isEmpty()) { aviso("Primero haga otra fila"); return; }
        new AlertDialog.Builder(this).setTitle("¿El total de cuál fila?").setItems(ops.toArray(new String[0]),
                (d, w) -> Comun.meter(pant, ops.get(w).split(" ")[0])).show();
    }

    private void sumar() {
        String[] ops = {"Sumar todas las filas", "Sumar solo unas filas (desde… hasta…)"};
        new AlertDialog.Builder(this).setTitle("Σ Total general").setItems(ops, (d, w) -> {
            if (w == 0) { sumaDesde = 0; sumaHasta = 0; guardarActual(); pintarTabla(); return; }
            LinearLayout l = horizontal();
            EditText a = campoTexto("Desde", ""), b = campoTexto("Hasta", "");
            a.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            b.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            l.addView(a, peso(1)); l.addView(b, peso(1));
            new AlertDialog.Builder(this).setTitle("¿Cuáles filas?").setView(l).setPositiveButton("Sumar", (d2, w2) -> {
                try {
                    sumaDesde = Integer.parseInt(a.getText().toString().trim());
                    sumaHasta = Integer.parseInt(b.getText().toString().trim());
                    if (sumaHasta < sumaDesde) { int x = sumaDesde; sumaDesde = sumaHasta; sumaHasta = x; }
                } catch (Exception e) { sumaDesde = 0; sumaHasta = 0; }
                guardarActual();
                pintarTabla();
            }).setNegativeButton("Cancelar", null).show();
        }).show();
    }

    private void opcionesFila(int k) {
        new AlertDialog.Builder(this).setTitle("Fila " + (k + 1))
                .setItems(new String[]{"✏ Corregir", "➕ Meter una fila arriba", "🗑 Borrar esta fila"}, (d, w) -> {
                    if (w == 0) elegir(k);
                    else if (w == 1) { filas.add(k, new Fila()); guardarActual(); elegir(k); }
                    else {
                        filas.remove(k);
                        if (filas.isEmpty()) filas.add(new Fila());
                        aviso("Ojo: si otras filas usaban T" + (k + 1) + ", revíselas");
                        guardarActual();
                        elegir(Math.min(k, filas.size() - 1));
                    }
                }).show();
    }

    private String titulo() { return nombreTabla.isEmpty() ? "Tabla de obra" : nombreTabla; }

    private String texto() {
        StringBuilder s = new StringBuilder(titulo()).append('\n');
        for (int i = 0; i < filas.size(); i++) {
            Fila f = filas.get(i);
            if (f.formula.trim().isEmpty() && f.nombre.isEmpty()) continue;
            s.append(i + 1).append(". ").append(f.nombre.isEmpty() ? "" : f.nombre + ": ").append(f.formula)
                    .append(" = ").append(total(i)).append('\n');
        }
        s.append("TOTAL (").append(rango()).append("): ").append(sumaTexto());
        return s.toString();
    }

    // ---------------- Guardar ----------------

    private File carpeta() { File f = new File(getFilesDir(), "tablas"); f.mkdirs(); return f; }

    private void cargar(File f) {
        filas.clear();
        sumaDesde = 0; sumaHasta = 0;
        try {
            for (String s : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = s.split("\t", -1);
                if (p.length >= 3 && "S".equals(p[0])) {
                    try { sumaDesde = Integer.parseInt(p[1]); sumaHasta = Integer.parseInt(p[2]); } catch (Exception ignored) {}
                } else if (p.length >= 2) {
                    Fila x = new Fila();
                    x.nombre = p[0]; x.formula = p[1];
                    filas.add(x);
                }
            }
        } catch (Exception ignored) {}
    }

    private void escribir(File f) {
        StringBuilder s = new StringBuilder("S\t").append(sumaDesde).append('\t').append(sumaHasta).append('\n');
        for (Fila x : filas) s.append(Base.limpio(x.nombre)).append('\t').append(x.formula.replace('\t', ' ')).append('\n');
        try { Files.write(f.toPath(), s.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }

    private void guardarActual() {
        escribir(new File(getFilesDir(), "tabla_actual.tsv"));
        getSharedPreferences("barra", MODE_PRIVATE).edit().putString("tablaNombre", nombreTabla).apply();
        if (!nombreTabla.isEmpty()) escribir(new File(carpeta(), Comun.nombreArchivo(nombreTabla) + ".tsv"));
    }

    @Override protected void onPause() { super.onPause(); guardarActual(); }

    private void guardarComo() {
        EditText e = campoTexto("Ejemplo: Muro patio", nombreTabla);
        new AlertDialog.Builder(this).setTitle("💾 Nombre de esta tabla").setView(e)
                .setPositiveButton("Guardar", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty()) return;
                    nombreTabla = n;
                    guardarActual();
                    pintarTabla();
                    aviso("💾 Guardada: " + n);
                }).setNegativeButton("Cancelar", null).show();
    }

    private void abrirTablas() {
        File[] fs = carpeta().listFiles();
        if (fs == null || fs.length == 0) { aviso("Todavía no hay tablas guardadas"); return; }
        java.util.Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        String[] ns = new String[fs.length];
        for (int i = 0; i < fs.length; i++) ns[i] = fs[i].getName().replaceAll("\\.tsv$", "");
        new AlertDialog.Builder(this).setTitle("📂 Tablas guardadas").setItems(ns, (d, w) -> {
            guardarActual();
            cargar(fs[w]);
            if (filas.isEmpty()) filas.add(new Fila());
            nombreTabla = ns[w];
            guardarActual();
            elegir(0);
        }).show();
    }

    private void nueva() {
        new AlertDialog.Builder(this).setMessage(nombreTabla.isEmpty() && filas.size() > 1
                        ? "Esta tabla no tiene nombre y se va a perder. ¿Empezar una nueva?" : "¿Empezar una tabla nueva? La guardada no se borra.")
                .setPositiveButton("Sí, nueva", (d, w) -> {
                    filas.clear(); filas.add(new Fila()); nombreTabla = ""; sumaDesde = 0; sumaHasta = 0;
                    guardarActual(); elegir(0);
                }).setNegativeButton("No", null).show();
    }
}
