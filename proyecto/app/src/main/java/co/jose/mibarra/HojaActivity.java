package co.jose.mibarra;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mini Excel: 6 columnas, 100 filas, fórmulas sencillas, colores y quebrados. */
public class HojaActivity extends Base {

    static final int FILAS = 100, COLS = 6;

    private File carpeta, actual;
    private String[][] raw;
    private boolean[][] negrita, rojo, frac;
    private int[][] fondo;
    private TextView[][] celdas;
    private int selF = 0, selC = 0;
    private EditText entrada, nombre;
    private TextView etiqueta;
    private boolean cargando;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        carpeta = new File(getFilesDir(), "hojas");
        carpeta.mkdirs();
        mostrarLista();
    }

    @Override
    protected void onPause() {
        super.onPause();
        guardar(false);
    }

    @Override
    public void onBackPressed() {
        if (actual != null) { guardar(false); mostrarLista(); }
        else super.onBackPressed();
    }

    // ---------------- Lista de hojas ----------------

    private void mostrarLista() {
        actual = null;
        nombre = null;
        LinearLayout l = vertical();
        int p = dp(16);
        l.setPadding(p, p, p, p);
        l.addView(titulo("📊 Mis cuentas"));
        Button nueva = boton("＋  Nueva hoja", 0xFF2E7D32);
        nueva.setOnClickListener(v -> abrir(new File(carpeta, System.currentTimeMillis() + ".txt"), true));
        l.addView(nueva, ancho(56));
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        for (File f : archivos(carpeta)) {
            String n = "Hoja";
            try {
                List<String> ls = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                if (!ls.isEmpty() && !ls.get(0).trim().isEmpty()) n = ls.get(0);
            } catch (Exception ignored) {}
            Button bt = boton(n + "\n" + df.format(new Date(f.lastModified())), 0xFF37474F);
            bt.setOnClickListener(v -> abrir(f, false));
            l.addView(bt, ancho(68));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    // ---------------- Editor ----------------

    private void abrir(File f, boolean nueva) {
        actual = f;
        raw = new String[FILAS][COLS];
        negrita = new boolean[FILAS][COLS];
        rojo = new boolean[FILAS][COLS];
        frac = new boolean[FILAS][COLS];
        fondo = new int[FILAS][COLS];
        celdas = new TextView[FILAS][COLS];
        selF = 0; selC = 0;

        LinearLayout raiz = vertical();
        raiz.setPadding(dp(6), dp(6), dp(6), 0);

        LinearLayout arriba = horizontal();
        arriba.setGravity(Gravity.CENTER_VERTICAL);
        Button volver = boton("←", 0xFF546E7A);
        volver.setOnClickListener(v -> onBackPressed());
        arriba.addView(volver, new LinearLayout.LayoutParams(dp(52), dp(46)));
        nombre = campoTexto("Nombre de la hoja", "");
        nombre.setTextSize(18);
        nombre.setTypeface(Typeface.DEFAULT_BOLD);
        arriba.addView(nombre, peso(1));
        raiz.addView(arriba);

        // Herramientas
        LinearLayout h = horizontal();
        h.addView(herramienta("B", Color.WHITE, Color.BLACK, true, v -> { negrita[selF][selC] = !negrita[selF][selC]; pintar(selF, selC); }));
        h.addView(herramienta("A", Color.WHITE, ROJO, true, v -> { rojo[selF][selC] = !rojo[selF][selC]; pintar(selF, selC); }));
        h.addView(herramienta("  ", AMARILLO, Color.BLACK, false, v -> colorear(AMARILLO)));
        h.addView(herramienta("  ", VERDE, Color.BLACK, false, v -> colorear(VERDE)));
        h.addView(herramienta("  ", ROSADO, Color.BLACK, false, v -> colorear(ROSADO)));
        h.addView(herramienta("✕ color", Color.WHITE, Color.BLACK, false, v -> {
            negrita[selF][selC] = rojo[selF][selC] = false; fondo[selF][selC] = 0; pintar(selF, selC);
        }));
        h.addView(herramienta("½", Color.WHITE, 0xFF1565C0, true, v -> { frac[selF][selC] = !frac[selF][selC]; pintar(selF, selC); }));
        h.addView(herramienta("Σ", Color.WHITE, 0xFF1565C0, true, v -> sumaArriba()));
        h.addView(herramienta("⬇ Copiar abajo", Color.WHITE, 0xFF1565C0, false, v -> copiarAbajo()));
        h.addView(herramienta("🗑 Borrar", Color.WHITE, ROJO, false, v -> {
            raw[selF][selC] = null; negrita[selF][selC] = rojo[selF][selC] = frac[selF][selC] = false;
            fondo[selF][selC] = 0; seleccionar(selF, selC); recalcular();
        }));
        for (int i = 0; i < h.getChildCount(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            h.getChildAt(i).setLayoutParams(lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(h);
        raiz.addView(hs);

        // Barra de fórmula
        LinearLayout fb = horizontal();
        fb.setGravity(Gravity.CENTER_VERTICAL);
        etiqueta = new TextView(this);
        etiqueta.setTextSize(16);
        etiqueta.setTypeface(Typeface.DEFAULT_BOLD);
        etiqueta.setTextColor(Color.WHITE);
        etiqueta.setGravity(Gravity.CENTER);
        GradientDrawable ge = new GradientDrawable();
        ge.setColor(0xFF1565C0);
        ge.setCornerRadius(dp(6));
        etiqueta.setBackground(ge);
        fb.addView(etiqueta, new LinearLayout.LayoutParams(dp(50), dp(40)));
        entrada = new EditText(this);
        entrada.setTextSize(18);
        entrada.setSingleLine(true);
        entrada.setHint("Escriba aquí, o una fórmula: =B2*C2");
        entrada.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        entrada.setImeOptions(EditorInfo.IME_ACTION_DONE);
        entrada.setOnEditorActionListener((v, a, e) -> { bajar(); return true; });
        entrada.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void afterTextChanged(Editable e) {
                if (cargando) return;
                String t = e.toString();
                raw[selF][selC] = t.trim().isEmpty() ? null : t;
                recalcular();
            }
        });
        fb.addView(entrada, peso(1));
        Button ok = boton("✓", 0xFF2E7D32);
        ok.setOnClickListener(v -> bajar());
        fb.addView(ok, new LinearLayout.LayoutParams(dp(50), dp(44)));
        raiz.addView(fb);

        // Cuadrícula
        LinearLayout grilla = vertical();
        LinearLayout enc = horizontal();
        enc.addView(cabecera(""), new LinearLayout.LayoutParams(dp(40), dp(34)));
        for (int c = 0; c < COLS; c++)
            enc.addView(cabecera(String.valueOf((char) ('A' + c))), new LinearLayout.LayoutParams(dp(anchoCol(c)), dp(34)));
        grilla.addView(enc);
        for (int r = 0; r < FILAS; r++) {
            LinearLayout fila = horizontal();
            fila.addView(cabecera(String.valueOf(r + 1)), new LinearLayout.LayoutParams(dp(40), dp(46)));
            for (int c = 0; c < COLS; c++) {
                TextView t = new TextView(this);
                t.setTextSize(17);
                t.setTextColor(Color.BLACK);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(dp(6), 0, dp(6), 0);
                t.setSingleLine(true);
                final int rr = r, cc = c;
                t.setOnClickListener(v -> tocar(rr, cc));
                celdas[r][c] = t;
                fila.addView(t, new LinearLayout.LayoutParams(dp(anchoCol(c)), dp(46)));
            }
            grilla.addView(fila);
        }
        HorizontalScrollView hz = new HorizontalScrollView(this);
        hz.addView(grilla);
        ScrollView vs = new ScrollView(this);
        vs.addView(hz);
        raiz.addView(vs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        raiz.addView(pie(() -> guardar(true), this::png, this::imprimir, this::whatsapp, null));
        setContentView(raiz);

        if (!nueva) cargar(f);
        if (nombre.getText().length() == 0)
            nombre.setText("Cuentas " + new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date()));
        recalcular();
        seleccionar(0, 0);
    }

    private static int anchoCol(int c) { return c == 0 ? 150 : 100; }

    private TextView cabecera(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(0xFF455A64);
        v.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFECEFF1);
        g.setStroke(1, 0xFFB0BEC5);
        v.setBackground(g);
        return v;
    }

    private void tocar(int r, int c) {
        String t = entrada.getText().toString();
        if (entrada.hasFocus() && t.startsWith("=") && !(r == selF && c == selC)) {
            // Enlazar: pone el nombre de la celda tocada dentro de la fórmula
            int pos = Math.max(entrada.getSelectionStart(), 0);
            entrada.getText().insert(pos, nombreCelda(r, c));
            return;
        }
        seleccionar(r, c);
    }

    private void seleccionar(int r, int c) {
        int vf = selF, vc = selC;
        selF = r; selC = c;
        pintar(vf, vc);
        pintar(r, c);
        etiqueta.setText(nombreCelda(r, c));
        cargando = true;
        entrada.setText(raw[r][c] == null ? "" : raw[r][c]);
        entrada.setSelection(entrada.getText().length());
        cargando = false;
    }

    private void bajar() {
        if (selF < FILAS - 1) seleccionar(selF + 1, selC);
    }

    static String nombreCelda(int r, int c) { return (char) ('A' + c) + String.valueOf(r + 1); }

    private void colorear(int color) {
        fondo[selF][selC] = fondo[selF][selC] == color ? 0 : color;
        pintar(selF, selC);
    }

    private void sumaArriba() {
        if (selF == 0) { aviso("Ponga el Σ debajo de los números que quiere sumar"); return; }
        int ini = selF - 1;
        while (ini > 0 && raw[ini - 1][selC] != null && Motor.numero(raw[ini - 1][selC]) != null) ini--;
        if (raw[ini][selC] != null && Motor.numero(raw[ini][selC]) == null && ini < selF - 1) ini++;
        String f = "=SUMA(" + nombreCelda(ini, selC) + ":" + nombreCelda(selF - 1, selC) + ")";
        entrada.setText(f);
        negrita[selF][selC] = true;
        pintar(selF, selC);
    }

    private void copiarAbajo() {
        if (selF >= FILAS - 1) return;
        String s = raw[selF][selC];
        int r = selF, c = selC;
        raw[r + 1][c] = s == null ? null : Motor.correrFilas(s, 1);
        negrita[r + 1][c] = negrita[r][c];
        rojo[r + 1][c] = rojo[r][c];
        frac[r + 1][c] = frac[r][c];
        fondo[r + 1][c] = fondo[r][c];
        recalcular();
        seleccionar(r + 1, c);
    }

    private void recalcular() {
        if (celdas == null) return;
        Motor m = new Motor(raw);
        for (int r = 0; r < FILAS; r++)
            for (int c = 0; c < COLS; c++) {
                String s = m.mostrar(r, c, frac[r][c]);
                celdas[r][c].setText(s);
                boolean num = raw[r][c] != null && (raw[r][c].startsWith("=") || Motor.numero(raw[r][c]) != null);
                celdas[r][c].setGravity(Gravity.CENTER_VERTICAL | (num ? Gravity.END : Gravity.START));
            }
        for (int r = 0; r < FILAS; r++) for (int c = 0; c < COLS; c++) pintar(r, c);
    }

    private void pintar(int r, int c) {
        TextView t = celdas[r][c];
        t.setTypeface(negrita[r][c] ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setTextColor(rojo[r][c] ? ROJO : Color.BLACK);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fondo[r][c] != 0 ? fondo[r][c] : Color.WHITE);
        if (r == selF && c == selC) g.setStroke(dp(3), 0xFF1565C0);
        else g.setStroke(1, 0xFFB0BEC5);
        t.setBackground(g);
    }

    // ---------------- Guardar / cargar ----------------

    private void guardar(boolean avisar) {
        if (actual == null || raw == null || nombre == null) return;
        StringBuilder s = new StringBuilder(limpio(nombre.getText().toString())).append('\n');
        int n = 0;
        for (int r = 0; r < FILAS; r++)
            for (int c = 0; c < COLS; c++) {
                boolean algo = raw[r][c] != null || negrita[r][c] || rojo[r][c] || frac[r][c] || fondo[r][c] != 0;
                if (!algo) continue;
                s.append("C\t").append(r).append('\t').append(c).append('\t')
                 .append(negrita[r][c] ? 1 : 0).append('\t').append(rojo[r][c] ? 1 : 0).append('\t')
                 .append(fondo[r][c]).append('\t').append(frac[r][c] ? 1 : 0).append('\t')
                 .append(raw[r][c] == null ? "" : limpio(raw[r][c])).append('\n');
                n++;
            }
        if (n == 0 && !actual.exists()) return;
        try {
            Files.write(actual.toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
            if (avisar) aviso("💾 Hoja guardada");
        } catch (Exception e) {
            aviso("No se pudo guardar: " + e.getMessage());
        }
    }

    private void cargar(File f) {
        try {
            List<String> ls = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            if (ls.isEmpty()) return;
            nombre.setText(ls.get(0));
            for (int i = 1; i < ls.size(); i++) {
                String[] p = ls.get(i).split("\t", -1);
                if (p.length < 8 || !"C".equals(p[0])) continue;
                int r = Integer.parseInt(p[1]), c = Integer.parseInt(p[2]);
                if (r >= FILAS || c >= COLS) continue;
                negrita[r][c] = "1".equals(p[3]);
                rojo[r][c] = "1".equals(p[4]);
                fondo[r][c] = Integer.parseInt(p[5]);
                frac[r][c] = "1".equals(p[6]);
                raw[r][c] = p[7].isEmpty() ? null : p[7];
            }
        } catch (Exception e) {
            aviso("No se pudo abrir la hoja");
        }
    }

    // ---------------- PNG, imprimir, Drive ----------------

    /** Hasta dónde hay datos: {última fila, última columna}. */
    private int[] usado() {
        int uf = -1, uc = -1;
        for (int r = 0; r < FILAS; r++)
            for (int c = 0; c < COLS; c++)
                if (raw[r][c] != null || fondo[r][c] != 0) { uf = Math.max(uf, r); uc = Math.max(uc, c); }
        return new int[]{uf, uc};
    }

    private void png() {
        guardar(false);
        Uri u = hacerPng();
        if (u != null) aviso("🖼 Imagen guardada en Descargas/MiBarra");
    }

    private Uri hacerPng() {
        int[] us = usado();
        if (us[0] < 0) { aviso("La hoja está vacía"); return null; }
        Motor m = new Motor(raw);
        int anchoPx = 1080;
        LinearLayout l = vertical();
        l.setPadding(30, 30, 30, 30);
        l.setBackgroundColor(Color.WHITE);
        TextView ti = new TextView(this);
        ti.setText(nombre.getText().toString());
        ti.setTextSize(TypedValue.COMPLEX_UNIT_PX, 46);
        ti.setTypeface(Typeface.DEFAULT_BOLD);
        ti.setTextColor(Color.BLACK);
        ti.setPadding(0, 0, 0, 20);
        l.addView(ti);
        for (int r = 0; r <= us[0]; r++) {
            LinearLayout fila = horizontal();
            for (int c = 0; c <= us[1]; c++) {
                TextView t = new TextView(this);
                t.setText(m.mostrar(r, c, frac[r][c]));
                t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 36);
                t.setTextColor(rojo[r][c] ? ROJO : Color.BLACK);
                if (negrita[r][c]) t.setTypeface(Typeface.DEFAULT_BOLD);
                boolean num = raw[r][c] != null && (raw[r][c].startsWith("=") || Motor.numero(raw[r][c]) != null);
                t.setGravity(Gravity.CENTER_VERTICAL | (num ? Gravity.END : Gravity.START));
                t.setPadding(14, 12, 14, 12);
                GradientDrawable g = new GradientDrawable();
                g.setColor(fondo[r][c] != 0 ? fondo[r][c] : Color.WHITE);
                g.setStroke(2, 0xFF90A4AE);
                t.setBackground(g);
                fila.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, c == 0 ? 2f : 1f));
            }
            l.addView(fila, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return guardarPng(aImagen(l, anchoPx), "Cuentas");
    }

    private void imprimir() {
        guardar(false);
        int[] us = usado();
        if (us[0] < 0) { aviso("La hoja está vacía"); return; }
        Motor m = new Motor(raw);
        StringBuilder h = new StringBuilder("<html><head><meta charset='utf-8'><style>body{font-family:sans-serif}"
                + "table{border-collapse:collapse;width:100%;font-size:15px}td{border:1px solid #90a4ae;padding:6px}"
                + "</style></head><body><h2>").append(Util.xml(nombre.getText().toString())).append("</h2><table>");
        for (int r = 0; r <= us[0]; r++) {
            h.append("<tr>");
            for (int c = 0; c <= us[1]; c++) {
                boolean num = raw[r][c] != null && (raw[r][c].startsWith("=") || Motor.numero(raw[r][c]) != null);
                StringBuilder st = new StringBuilder();
                if (fondo[r][c] != 0) st.append("background:").append(hex(fondo[r][c])).append(';');
                if (rojo[r][c]) st.append("color:#C62828;");
                if (negrita[r][c]) st.append("font-weight:bold;");
                if (num) st.append("text-align:right;");
                h.append("<td style='").append(st).append("'>")
                 .append(Util.xml(m.mostrar(r, c, frac[r][c]))).append("</td>");
            }
            h.append("</tr>");
        }
        h.append("</table></body></html>");
        imprimirHtml(h.toString(), nombre.getText().toString());
    }

    /** La tabla va a WhatsApp en letra de máquina para que las columnas queden derechas. */
    private void whatsapp() {
        guardar(false);
        int[] us = usado();
        if (us[0] < 0) { aviso("La hoja está vacía"); return; }
        Motor m = new Motor(raw);
        String[][] v = new String[us[0] + 1][us[1] + 1];
        int[] anchoC = new int[us[1] + 1];
        for (int r = 0; r <= us[0]; r++)
            for (int c = 0; c <= us[1]; c++) {
                v[r][c] = m.mostrar(r, c, frac[r][c]);
                anchoC[c] = Math.max(anchoC[c], v[r][c].length());
            }
        StringBuilder s = new StringBuilder("*").append(nombre.getText().toString().trim()).append("*\n```\n");
        for (int r = 0; r <= us[0]; r++) {
            boolean vacia = true;
            for (int c = 0; c <= us[1]; c++) if (!v[r][c].isEmpty()) vacia = false;
            if (vacia) { s.append('\n'); continue; }
            StringBuilder linea = new StringBuilder();
            for (int c = 0; c <= us[1]; c++) {
                boolean num = raw[r][c] != null && (raw[r][c].startsWith("=") || Motor.numero(raw[r][c]) != null);
                String x = v[r][c];
                String pad = new String(new char[Math.max(0, anchoC[c] - x.length())]).replace('\0', ' ');
                if (c > 0) linea.append("  ");
                linea.append(num ? pad + x : x + pad);
            }
            s.append(linea.toString().replaceAll("\\s+$", "")).append('\n');
        }
        s.append("```");
        textoWhatsapp(s.toString());
    }

    private void drive() {
        guardar(false);
        Uri img = hacerPng();
        if (img == null) return;
        int[] us = usado();
        Motor m = new Motor(raw);
        StringBuilder s = new StringBuilder("﻿");
        for (int r = 0; r <= us[0]; r++) {
            for (int c = 0; c <= us[1]; c++) {
                if (c > 0) s.append(';');
                String v = m.mostrar(r, c, false);
                s.append(v.contains(";") ? "\"" + v.replace("\"", "\"\"") + "\"" : v);
            }
            s.append('\n');
        }
        Uri csv = null;
        try {
            String base = nombre.getText().toString().trim().replaceAll("[^\\p{L}\\p{N}]+", "_");
            csv = Util.guardarBytes(this, (base.isEmpty() ? "Cuentas" : base) + "_" + Util.fecha() + ".csv", "text/csv",
                    s.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        enviar(DRIVE, "image/png", img, csv);
    }

    // =====================================================================
    // Motor de fórmulas
    // =====================================================================

    static final class Motor {
        private final String[][] raw;
        private final Double[][] cache;
        private final boolean[][] visitando;

        Motor(String[][] raw) {
            this.raw = raw;
            cache = new Double[raw.length][raw[0].length];
            visitando = new boolean[raw.length][raw[0].length];
        }

        static final class Error extends RuntimeException {
            Error(String m) { super(m); }
        }

        String mostrar(int r, int c, boolean enQuebrado) {
            String s = raw[r][c];
            if (s == null || s.trim().isEmpty()) return "";
            boolean formula = s.startsWith("=");
            if (!formula && numero(s) == null) return s;
            // =A1 que apunta a un texto: muestra el mismo texto
            Matcher ref = Pattern.compile("=\\s*([A-Fa-f])(\\d+)\\s*").matcher(s);
            if (ref.matches()) {
                int rf = Integer.parseInt(ref.group(2)) - 1, cf = Character.toUpperCase(ref.group(1).charAt(0)) - 'A';
                if (rf >= 0 && rf < raw.length && cf < raw[0].length && !(rf == r && cf == c)) {
                    String t = raw[rf][cf];
                    if (t != null && !t.startsWith("=") && numero(t) == null) return t;
                }
            }
            try {
                double v = valor(r, c);
                return enQuebrado ? quebrado(v) : formato(v);
            } catch (Error e) {
                return e.getMessage();
            } catch (Exception e) {
                return "#ERROR";
            }
        }

        double valor(int r, int c) {
            if (cache[r][c] != null) return cache[r][c];
            if (visitando[r][c]) throw new Error("#CICLO");
            visitando[r][c] = true;
            try {
                String s = raw[r][c];
                double v;
                if (s == null || s.trim().isEmpty()) v = 0;
                else if (s.startsWith("=")) v = new Expr(this, s.substring(1)).evaluar();
                else { Double n = numero(s); v = n == null ? 0 : n; }
                cache[r][c] = v;
                return v;
            } finally {
                visitando[r][c] = false;
            }
        }

        /** Lee un número escrito a mano: 280.000 · 2,5 · 1 1/2 · 3/8 · 10% */
        static Double numero(String s) {
            s = s.trim().replace("$", "").replace(" ", " ").trim();
            if (s.isEmpty()) return null;
            boolean neg = s.startsWith("-") || s.startsWith("−");
            if (neg) s = s.substring(1).trim();
            double mult = 1;
            if (s.endsWith("%")) { mult = 0.01; s = s.substring(0, s.length() - 1).trim(); }
            Double v;
            if (s.matches("\\d+\\s+\\d+/\\d+")) {
                String[] p = s.split("\\s+");
                String[] f = p[1].split("/");
                double d = Double.parseDouble(f[1]);
                if (d == 0) return null;
                v = Double.parseDouble(p[0]) + Double.parseDouble(f[0]) / d;
            } else if (s.matches("\\d+/\\d+")) {
                String[] f = s.split("/");
                double d = Double.parseDouble(f[1]);
                if (d == 0) return null;
                v = Double.parseDouble(f[0]) / d;
            } else if (s.matches("[\\d.]*,\\d*") && s.matches(".*\\d.*")) {
                v = Double.parseDouble(s.replace(".", "").replace(',', '.'));
            } else if (s.matches("\\d{1,3}(\\.\\d{3})+")) {
                v = Double.parseDouble(s.replace(".", ""));
            } else if (s.matches("\\d*\\.?\\d+") || s.matches("\\d+\\.")) {
                v = Double.parseDouble(s);
            } else return null;
            return (neg ? -v : v) * mult;
        }

        static String formato(double v) {
            if (Double.isNaN(v) || Double.isInfinite(v)) return "#ERROR";
            String s = CalculadoraActivity.dec(v, 2);
            boolean neg = s.startsWith("-");
            if (neg) s = s.substring(1);
            String ent = s, dec = "";
            int p = s.indexOf(',');
            if (p >= 0) { ent = s.substring(0, p); dec = s.substring(p); }
            StringBuilder g = new StringBuilder();
            for (int i = 0; i < ent.length(); i++) {
                if (i > 0 && (ent.length() - i) % 3 == 0) g.append('.');
                g.append(ent.charAt(i));
            }
            return (neg ? "−" : "") + g + dec;
        }

        /** En quebrado a la dieciseisava más cercana: 1 3/8, ½… */
        static String quebrado(double v) {
            String s = CalculadoraActivity.pulgadas(v, 16).replace("\"", "");
            return s;
        }

        /** Corre las referencias de fila: =B2*C2 → =B3*C3 */
        static String correrFilas(String s, int n) {
            if (!s.startsWith("=")) return s;
            Matcher m = Pattern.compile("(?<![A-Za-z])([A-Fa-f])(\\d+)").matcher(s);
            StringBuffer out = new StringBuffer();
            while (m.find()) {
                int fila = Integer.parseInt(m.group(2)) + n;
                m.appendReplacement(out, m.group(1).toUpperCase() + Math.max(1, fila));
            }
            m.appendTail(out);
            return out.toString();
        }
    }

    /** + − × ÷ ( ) %  referencias A1  rangos A1:A9  SUMA PROMEDIO MIN MAX */
    static final class Expr {
        private final Motor m;
        private final String s;
        private int i;

        Expr(Motor m, String s) {
            this.m = m;
            this.s = s.toUpperCase(Locale.ROOT).replace(" ", "").replace('×', '*').replace('÷', '/')
                    .replace('−', '-');
        }

        double evaluar() {
            double v = suma();
            if (i != s.length()) throw new Motor.Error("#ERROR");
            return v;
        }

        private char ver() { return i < s.length() ? s.charAt(i) : 0; }

        private double suma() {
            double a = producto();
            while (ver() == '+' || ver() == '-') {
                char c = s.charAt(i++);
                double b = producto();
                a = c == '+' ? a + b : a - b;
            }
            return a;
        }

        private double producto() {
            double a = unario();
            while (ver() == '*' || ver() == '/') {
                char c = s.charAt(i++);
                double b = unario();
                if (c == '/') {
                    if (b == 0) throw new Motor.Error("#DIV/0");
                    a = a / b;
                } else a = a * b;
            }
            return a;
        }

        private double unario() {
            if (ver() == '-') { i++; return -unario(); }
            if (ver() == '+') { i++; return unario(); }
            double v = primario();
            while (ver() == '%') { i++; v = v / 100; }
            return v;
        }

        private double primario() {
            char c = ver();
            if (c == '(') {
                i++;
                double v = suma();
                if (ver() == ')') i++;
                return v;
            }
            if (Character.isDigit(c) || c == ',' || c == '.') {
                int ini = i;
                while (Character.isDigit(ver()) || ver() == ',' || ver() == '.') i++;
                Double n = Motor.numero(s.substring(ini, i));
                if (n == null) throw new Motor.Error("#ERROR");
                return n;
            }
            if (Character.isLetter(c)) {
                int ini = i;
                while (Character.isLetter(ver())) i++;
                String letras = s.substring(ini, i);
                if (ver() == '(') {
                    i++;
                    return funcion(letras);
                }
                int[] ref = referencia(letras);
                return m.valor(ref[0], ref[1]);
            }
            throw new Motor.Error("#ERROR");
        }

        /** Después de las letras ya leídas, lee los números de la fila: A + 12 */
        private int[] referencia(String letras) {
            if (letras.length() != 1) throw new Motor.Error("#ERROR");
            int col = letras.charAt(0) - 'A';
            int ini = i;
            while (Character.isDigit(ver())) i++;
            if (ini == i) throw new Motor.Error("#ERROR");
            int fila = Integer.parseInt(s.substring(ini, i)) - 1;
            if (col < 0 || col >= HojaActivity.COLS || fila < 0 || fila >= HojaActivity.FILAS)
                throw new Motor.Error("#REF");
            return new int[]{fila, col};
        }

        private double funcion(String nombre) {
            double suma = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            int cuenta = 0;
            while (ver() != ')' && i < s.length()) {
                int guarda = i;
                boolean rango = false;
                if (Character.isLetter(ver())) {
                    int ini = i;
                    while (Character.isLetter(ver())) i++;
                    String l1 = s.substring(ini, i);
                    if (l1.length() == 1 && Character.isDigit(ver())) {
                        int[] a = referencia(l1);
                        if (ver() == ':') {
                            i++;
                            int ini2 = i;
                            while (Character.isLetter(ver())) i++;
                            int[] b = referencia(s.substring(ini2, i));
                            rango = true;
                            for (int r = Math.min(a[0], b[0]); r <= Math.max(a[0], b[0]); r++)
                                for (int c = Math.min(a[1], b[1]); c <= Math.max(a[1], b[1]); c++) {
                                    double v = m.valor(r, c);
                                    suma += v; min = Math.min(min, v); max = Math.max(max, v); cuenta++;
                                }
                        }
                    }
                    if (!rango) i = guarda;
                }
                if (!rango) {
                    double v = suma();
                    suma += v; min = Math.min(min, v); max = Math.max(max, v); cuenta++;
                }
                if (ver() == ';' || ver() == ',') i++;
                else break;
            }
            if (ver() == ')') i++;
            switch (nombre) {
                case "SUMA": case "SUM": return suma;
                case "PROMEDIO": case "AVERAGE":
                    if (cuenta == 0) throw new Motor.Error("#DIV/0");
                    return suma / cuenta;
                case "MIN": return cuenta == 0 ? 0 : min;
                case "MAX": return cuenta == 0 ? 0 : max;
                default: throw new Motor.Error("#NOMBRE");
            }
        }
    }
}
