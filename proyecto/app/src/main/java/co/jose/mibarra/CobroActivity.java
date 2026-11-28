package co.jose.mibarra;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Cuenta de cobro, pedido, cotización o remisión, igual al talonario. */
public class CobroActivity extends Base {

    private static final String[] TIPOS = {"CUENTA DE COBRO", "PEDIDO", "COTIZACIÓN", "REMISIÓN"};
    private static final int AZUL = 0xFF1A3E8C, AZUL_CLARO = 0xFFDCE3F3;

    private static class Fila {
        View vista;
        EditText cant, desc, unit;
        TextView total;
    }

    private File carpeta, actual;
    private SharedPreferences prefs;
    private int tipo;
    private final Button[] botonesTipo = new Button[4];
    private EditText numero, dia, mes, ano, cliente, nit, direccion, tel, recibi;
    private LinearLayout filas;
    private TextView granTotal;
    private final List<Fila> lista = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("barra", MODE_PRIVATE);
        carpeta = new File(getFilesDir(), "cobros");
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

    // ---------------- Lista ----------------

    private void mostrarLista() {
        actual = null;
        cliente = null;
        lista.clear();
        LinearLayout l = vertical();
        int p = dp(16);
        l.setPadding(p, p, p, p);
        l.addView(titulo("🧾 Cuentas de cobro"));
        Button nueva = boton("＋  Nueva", 0xFF2E7D32);
        nueva.setOnClickListener(v -> abrir(new File(carpeta, System.currentTimeMillis() + ".txt"), true));
        l.addView(nueva, ancho(56));
        Button datos = boton("✎  Datos de mi negocio (membrete)", 0xFF546E7A);
        datos.setOnClickListener(v -> membrete());
        l.addView(datos, ancho(50));
        for (File f : archivos(carpeta)) {
            Map<String, String> d = leer(f);
            long total = 0;
            for (String[] r : filasDe(f)) total += Math.round(Util.cantidad(r[0]) * Util.valor(r[2]));
            int t = 0;
            try { t = Integer.parseInt(d.getOrDefault("tipo", "0")); } catch (Exception ignored) {}
            String linea = "N.º " + d.getOrDefault("numero", "") + " · " + TIPOS[Math.max(0, Math.min(3, t))]
                    + "\n" + d.getOrDefault("cliente", "") + "  ·  " + Util.pesos(total) + "  ·  "
                    + d.getOrDefault("dia", "") + "/" + d.getOrDefault("mes", "") + "/" + d.getOrDefault("ano", "");
            Button bt = boton(linea, 0xFF37474F);
            bt.setOnClickListener(v -> abrir(f, false));
            l.addView(bt, ancho(72));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private void membrete() {
        LinearLayout l = vertical();
        l.setPadding(dp(16), dp(8), dp(16), 0);
        EditText n = campoTexto("Nombre del negocio", prefs.getString("negocio", ""));
        EditText ni = campoTexto("NIT o C.C.", prefs.getString("nitNegocio", ""));
        EditText t = campoTexto("Teléfono y dirección", prefs.getString("telNegocio", ""));
        l.addView(n); l.addView(ni); l.addView(t);
        new AlertDialog.Builder(this)
                .setTitle("Datos de mi negocio")
                .setMessage("Salen arriba en la imagen y al imprimir. Si los deja vacíos, no salen.")
                .setView(l)
                .setPositiveButton("Guardar", (d, w) -> prefs.edit()
                        .putString("negocio", n.getText().toString().trim())
                        .putString("nitNegocio", ni.getText().toString().trim())
                        .putString("telNegocio", t.getText().toString().trim()).apply())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------------- Editor ----------------

    private void abrir(File f, boolean nueva) {
        actual = f;
        lista.clear();
        LinearLayout c = vertical();
        c.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout arriba = horizontal();
        arriba.setGravity(Gravity.CENTER_VERTICAL);
        Button volver = boton("←", 0xFF546E7A);
        volver.setOnClickListener(v -> onBackPressed());
        arriba.addView(volver, new LinearLayout.LayoutParams(dp(52), dp(46)));
        TextView nt = new TextView(this);
        nt.setText("   N.º");
        nt.setTextSize(18);
        nt.setTypeface(Typeface.DEFAULT_BOLD);
        nt.setTextColor(AZUL);
        arriba.addView(nt);
        numero = numerico("0001", false);
        numero.setTextSize(20);
        arriba.addView(numero, peso(1));
        c.addView(arriba);

        LinearLayout t1 = horizontal(), t2 = horizontal();
        for (int i = 0; i < 4; i++) {
            final int k = i;
            Button b = new Button(this);
            b.setText(TIPOS[i]);
            b.setAllCaps(false);
            b.setTextSize(13);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setOnClickListener(v -> { tipo = k; pintarTipos(); });
            botonesTipo[i] = b;
            (i < 2 ? t1 : t2).addView(b, peso(1));
        }
        c.addView(t1);
        c.addView(t2);

        LinearLayout fe = horizontal();
        fe.setGravity(Gravity.CENTER_VERTICAL);
        fe.addView(etiqueta("FECHA"), peso(1.2f));
        dia = numerico("Día", false);
        mes = numerico("Mes", false);
        ano = numerico("Año", false);
        fe.addView(dia, peso(1)); fe.addView(mes, peso(1)); fe.addView(ano, peso(1.3f));
        c.addView(fe);
        cliente = renglon(c, "CLIENTE");
        nit = renglon(c, "NIT / C.C.");
        direccion = renglon(c, "DIRECCIÓN");
        tel = renglon(c, "TEL.");

        LinearLayout enc = horizontal();
        enc.setBackgroundColor(AZUL_CLARO);
        enc.addView(etiqueta("CANT."), peso(0.8f));
        enc.addView(etiqueta("DESCRIPCIÓN"), peso(2.4f));
        enc.addView(etiqueta("VR. UNIT"), peso(1.5f));
        enc.addView(etiqueta("VR. TOTAL"), peso(1.5f));
        enc.addView(etiqueta(""), new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = dp(10);
        c.addView(enc, ep);
        filas = vertical();
        c.addView(filas);
        Button agregar = boton("＋  Agregar artículo", 0xFF2E7D32);
        agregar.setOnClickListener(v -> agregarFila("", "", "").cant.requestFocus());
        c.addView(agregar, ancho(48));

        granTotal = new TextView(this);
        granTotal.setTextSize(24);
        granTotal.setTypeface(Typeface.DEFAULT_BOLD);
        granTotal.setGravity(Gravity.END);
        granTotal.setTextColor(Color.BLACK);
        granTotal.setPadding(0, dp(10), dp(4), dp(4));
        c.addView(granTotal);
        recibi = renglon(c, "RECIBÍ");

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        LinearLayout raiz = vertical();
        raiz.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        raiz.addView(pie(() -> guardar(true), this::png, this::imprimir, this::whatsapp, this::drive));
        setContentView(raiz);

        if (nueva) {
            int n = prefs.getInt("consecutivo", 0) + 1;
            prefs.edit().putInt("consecutivo", n).apply();
            numero.setText(String.format(Locale.US, "%04d", n));
            Calendar cal = Calendar.getInstance();
            dia.setText(String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH)));
            mes.setText(String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1));
            ano.setText(String.valueOf(cal.get(Calendar.YEAR)));
            tipo = 0;
            for (int i = 0; i < 3; i++) agregarFila("", "", "");
        } else {
            Map<String, String> d = leer(f);
            numero.setText(d.getOrDefault("numero", ""));
            dia.setText(d.getOrDefault("dia", ""));
            mes.setText(d.getOrDefault("mes", ""));
            ano.setText(d.getOrDefault("ano", ""));
            cliente.setText(d.getOrDefault("cliente", ""));
            nit.setText(d.getOrDefault("nit", ""));
            direccion.setText(d.getOrDefault("direccion", ""));
            tel.setText(d.getOrDefault("tel", ""));
            recibi.setText(d.getOrDefault("recibi", ""));
            try { tipo = Integer.parseInt(d.getOrDefault("tipo", "0")); } catch (Exception e) { tipo = 0; }
            for (String[] r : filasDe(f)) agregarFila(r[0], r[1], r[2]);
            if (lista.isEmpty()) agregarFila("", "", "");
        }
        pintarTipos();
        recalcular();
    }

    private void pintarTipos() {
        for (int i = 0; i < 4; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(i == tipo ? AZUL : Color.WHITE);
            g.setStroke(dp(2), AZUL);
            g.setCornerRadius(dp(8));
            botonesTipo[i].setBackground(g);
            botonesTipo[i].setTextColor(i == tipo ? Color.WHITE : AZUL);
            botonesTipo[i].setText((i == tipo ? "✔ " : "") + TIPOS[i]);
        }
    }

    private TextView etiqueta(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(AZUL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(4), dp(6), dp(4), dp(6));
        return v;
    }

    private EditText renglon(LinearLayout padre, String etq) {
        LinearLayout r = horizontal();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(etiqueta(etq), peso(1.2f));
        EditText e = campoTexto("", "");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        r.addView(e, peso(3.3f));
        padre.addView(r);
        return e;
    }

    private EditText numerico(String pista, boolean decimal) {
        EditText e = new EditText(this);
        e.setHint(pista);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setGravity(Gravity.CENTER);
        if (decimal) {
            e.setKeyListener(DigitsKeyListener.getInstance("0123456789.,"));
            e.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        } else {
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
        return e;
    }

    private Fila agregarFila(String cant, String desc, String unit) {
        Fila r = new Fila();
        LinearLayout l = horizontal();
        l.setGravity(Gravity.CENTER_VERTICAL);
        r.cant = numerico("", true);
        r.cant.setText(cant);
        r.desc = campoTexto("Artículo", desc);
        r.desc.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        r.desc.setTextSize(15);
        r.unit = numerico("", false);
        r.unit.setText(unit);
        r.unit.setGravity(Gravity.END);
        r.total = new TextView(this);
        r.total.setTextSize(15);
        r.total.setTypeface(Typeface.DEFAULT_BOLD);
        r.total.setTextColor(Color.BLACK);
        r.total.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button x = new Button(this);
        x.setText("✕");
        x.setTextSize(12);
        x.setMinWidth(0);
        x.setMinimumWidth(0);
        x.setPadding(0, 0, 0, 0);
        x.setOnClickListener(v -> { filas.removeView(l); lista.remove(r); recalcular(); });
        l.addView(r.cant, peso(0.8f));
        l.addView(r.desc, peso(2.4f));
        l.addView(r.unit, peso(1.5f));
        l.addView(r.total, peso(1.5f));
        l.addView(x, new LinearLayout.LayoutParams(dp(34), dp(40)));
        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { recalcular(); }
        };
        r.cant.addTextChangedListener(w);
        r.unit.addTextChangedListener(w);
        r.vista = l;
        filas.addView(l);
        lista.add(r);
        return r;
    }

    private static boolean vacia(Fila r) {
        return r.desc.getText().toString().trim().isEmpty() && r.unit.getText().toString().trim().isEmpty();
    }

    private long total(Fila r) {
        if (r.cant.getText().toString().trim().isEmpty() && r.unit.getText().toString().trim().isEmpty()) return 0;
        return Math.round(Util.cantidad(r.cant.getText().toString()) * Util.valor(r.unit.getText().toString()));
    }

    private long recalcular() {
        if (granTotal == null) return 0;
        long g = 0;
        for (Fila r : lista) {
            long t = total(r);
            g += t;
            r.total.setText(t == 0 ? "" : Util.pesos(t).replace("$ ", ""));
        }
        granTotal.setText("TOTAL " + Util.pesos(g));
        return g;
    }

    // ---------------- Guardar / cargar ----------------

    private void guardar(boolean avisar) {
        if (actual == null || cliente == null) return;
        StringBuilder s = new StringBuilder();
        s.append("tipo\t").append(tipo).append('\n');
        s.append("numero\t").append(limpio(numero.getText().toString())).append('\n');
        s.append("dia\t").append(limpio(dia.getText().toString())).append('\n');
        s.append("mes\t").append(limpio(mes.getText().toString())).append('\n');
        s.append("ano\t").append(limpio(ano.getText().toString())).append('\n');
        s.append("cliente\t").append(limpio(cliente.getText().toString())).append('\n');
        s.append("nit\t").append(limpio(nit.getText().toString())).append('\n');
        s.append("direccion\t").append(limpio(direccion.getText().toString())).append('\n');
        s.append("tel\t").append(limpio(tel.getText().toString())).append('\n');
        s.append("recibi\t").append(limpio(recibi.getText().toString())).append('\n');
        int n = 0;
        for (Fila r : lista) {
            if (vacia(r)) continue;
            s.append("R\t").append(limpio(r.cant.getText().toString())).append('\t')
             .append(limpio(r.desc.getText().toString())).append('\t')
             .append(limpio(r.unit.getText().toString())).append('\n');
            n++;
        }
        if (n == 0 && cliente.getText().toString().trim().isEmpty() && !actual.exists()) return;
        try {
            Files.write(actual.toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
            if (avisar) aviso("💾 Guardada");
        } catch (Exception e) {
            aviso("No se pudo guardar: " + e.getMessage());
        }
    }

    private static Map<String, String> leer(File f) {
        Map<String, String> m = new HashMap<>();
        try {
            for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p.length == 2) m.put(p[0], p[1]);
            }
        } catch (Exception ignored) {}
        return m;
    }

    private static List<String[]> filasDe(File f) {
        List<String[]> r = new ArrayList<>();
        try {
            for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p.length == 4 && "R".equals(p[0])) r.add(new String[]{p[1], p[2], p[3]});
            }
        } catch (Exception ignored) {}
        return r;
    }

    // ---------------- Imagen como el talonario ----------------

    private TextView px(String t, int tam, boolean negrita, int color, int gravedad) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, tam);
        v.setTextColor(color);
        if (negrita) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(gravedad | Gravity.CENTER_VERTICAL);
        v.setPadding(14, 10, 14, 10);
        return v;
    }

    private GradientDrawable borde(int fondo, int radio) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fondo);
        g.setStroke(3, AZUL);
        g.setCornerRadius(radio);
        return g;
    }

    private LinearLayout.LayoutParams anchoTotal(int arriba) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = arriba;
        return p;
    }

    private Uri hacerPng() {
        guardar(false);
        long g = recalcular();
        LinearLayout l = vertical();
        l.setPadding(40, 40, 40, 40);
        l.setBackgroundColor(0xFFF7F3EA);

        String neg = prefs.getString("negocio", ""), nn = prefs.getString("nitNegocio", ""),
                tn = prefs.getString("telNegocio", "");
        if (!neg.isEmpty()) l.addView(px(neg, 46, true, AZUL, Gravity.CENTER_HORIZONTAL));
        String sub = (nn.isEmpty() ? "" : "NIT/C.C. " + nn) + (nn.isEmpty() || tn.isEmpty() ? "" : "  ·  ") + tn;
        if (!sub.isEmpty()) l.addView(px(sub, 28, false, AZUL, Gravity.CENTER_HORIZONTAL));

        LinearLayout cab = horizontal();
        LinearLayout tipos = vertical();
        for (int i = 0; i < 4; i++)
            tipos.addView(px((i == tipo ? "☑  " : "☐  ") + TIPOS[i], 30, true, AZUL, Gravity.START));
        cab.addView(tipos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        LinearLayout fecha = vertical();
        fecha.setBackground(borde(AZUL_CLARO, 20));
        fecha.addView(px("N.º " + numero.getText(), 30, true, AZUL, Gravity.CENTER_HORIZONTAL));
        fecha.addView(px("DÍA  MES  AÑO", 24, true, AZUL, Gravity.CENTER_HORIZONTAL));
        fecha.addView(px(dia.getText() + " / " + mes.getText() + " / " + ano.getText(), 32, false,
                Color.BLACK, Gravity.CENTER_HORIZONTAL));
        cab.addView(fecha, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        l.addView(cab, anchoTotal(neg.isEmpty() && sub.isEmpty() ? 0 : 20));

        LinearLayout datos = vertical();
        datos.setBackground(borde(Color.TRANSPARENT, 20));
        datos.setPadding(10, 6, 10, 6);
        String[][] d = {{"CLIENTE:", cliente.getText().toString(), "NIT/C.C.:", nit.getText().toString()},
                {"DIRECCIÓN:", direccion.getText().toString(), "TEL.:", tel.getText().toString()}};
        for (String[] r : d) {
            LinearLayout f = horizontal();
            f.addView(px(r[0], 26, true, AZUL, Gravity.START));
            f.addView(px(r[1], 30, false, Color.BLACK, Gravity.START), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            f.addView(px(r[2], 26, true, AZUL, Gravity.START));
            f.addView(px(r[3], 30, false, Color.BLACK, Gravity.START), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));
            datos.addView(f);
        }
        l.addView(datos, anchoTotal(20));

        LinearLayout tabla = vertical();
        tabla.setBackground(borde(Color.TRANSPARENT, 16));
        float[] pesos = {0.8f, 3f, 1.4f, 1.5f};
        tabla.addView(filaTabla(new String[]{"CANT.", "DESCRIPCIÓN ARTÍCULO", "VR. UNIT", "VR. TOTAL"}, pesos, true));
        int n = 0;
        for (Fila r : lista) {
            if (vacia(r)) continue;
            long t = total(r);
            tabla.addView(filaTabla(new String[]{r.cant.getText().toString(), r.desc.getText().toString(),
                    Util.pesos(Util.valor(r.unit.getText().toString())).replace("$ ", ""),
                    Util.pesos(t).replace("$ ", "")}, pesos, false));
            n++;
        }
        for (; n < 6; n++) tabla.addView(filaTabla(new String[]{"", "", "", ""}, pesos, false));
        l.addView(tabla, anchoTotal(20));

        LinearLayout pieT = horizontal();
        pieT.setGravity(Gravity.CENTER_VERTICAL);
        pieT.addView(px("RECIBÍ: " + recibi.getText(), 28, true, AZUL, Gravity.START),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView tt = px("TOTAL " + Util.pesos(g), 40, true, Color.BLACK, Gravity.CENTER_HORIZONTAL);
        tt.setBackground(borde(AZUL_CLARO, 16));
        pieT.addView(tt);
        l.addView(pieT, anchoTotal(20));

        String nombre = TIPOS[tipo].replace(" ", "_").replace("Ó", "O") + "_" + numero.getText();
        return guardarPng(aImagen(l, 1080), nombre);
    }

    private LinearLayout filaTabla(String[] t, float[] pesos, boolean cabeza) {
        LinearLayout f = horizontal();
        for (int i = 0; i < t.length; i++) {
            TextView v = px(t[i], cabeza ? 26 : 30, cabeza, cabeza ? AZUL : Color.BLACK,
                    cabeza ? Gravity.CENTER_HORIZONTAL : (i == 1 ? Gravity.START : Gravity.END));
            v.setMinHeight(64);
            GradientDrawable g = new GradientDrawable();
            g.setColor(cabeza ? AZUL_CLARO : Color.TRANSPARENT);
            g.setStroke(2, AZUL);
            v.setBackground(g);
            f.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pesos[i]));
        }
        return f;
    }

    private void png() {
        if (hacerPng() != null) aviso("🖼 Imagen guardada en Descargas/MiBarra");
    }

    private void whatsapp() {
        Uri u = hacerPng();
        if (u != null) enviar(WHATSAPP, "image/png", u);
    }

    private void drive() {
        Uri u = hacerPng();
        if (u != null) enviar(DRIVE, "image/png", u);
    }

    private void imprimir() {
        guardar(false);
        long g = recalcular();
        String neg = prefs.getString("negocio", ""), nn = prefs.getString("nitNegocio", ""),
                tn = prefs.getString("telNegocio", "");
        StringBuilder h = new StringBuilder("<html><head><meta charset='utf-8'><style>"
                + "body{font-family:sans-serif;color:#1A3E8C;font-size:14px}"
                + "table{border-collapse:collapse;width:100%}td,th{border:1.5px solid #1A3E8C;padding:6px}"
                + "th{background:#DCE3F3}.v{color:#000}.n{text-align:right}"
                + "</style></head><body>");
        if (!neg.isEmpty()) h.append("<h2 style='text-align:center;margin:0'>").append(Util.xml(neg)).append("</h2>");
        if (!nn.isEmpty() || !tn.isEmpty())
            h.append("<p style='text-align:center;margin:2px'>").append(nn.isEmpty() ? "" : "NIT/C.C. " + Util.xml(nn))
             .append(" ").append(Util.xml(tn)).append("</p>");
        h.append("<table style='border:none'><tr><td style='border:none'>");
        for (int i = 0; i < 4; i++) h.append(i == tipo ? "☑ " : "☐ ").append("<b>").append(TIPOS[i]).append("</b><br>");
        h.append("</td><td style='border:none;text-align:right'><b>N.º ").append(Util.xml(numero.getText().toString()))
         .append("</b><br>Fecha: <span class='v'>").append(Util.xml(dia.getText() + "/" + mes.getText() + "/" + ano.getText()))
         .append("</span></td></tr></table>");
        h.append("<table><tr><td><b>CLIENTE:</b> <span class='v'>").append(Util.xml(cliente.getText().toString()))
         .append("</span></td><td><b>NIT/C.C.:</b> <span class='v'>").append(Util.xml(nit.getText().toString()))
         .append("</span></td></tr><tr><td><b>DIRECCIÓN:</b> <span class='v'>").append(Util.xml(direccion.getText().toString()))
         .append("</span></td><td><b>TEL.:</b> <span class='v'>").append(Util.xml(tel.getText().toString()))
         .append("</span></td></tr></table><br>");
        h.append("<table><tr><th>CANT.</th><th>DESCRIPCIÓN ARTÍCULO</th><th>VR. UNIT</th><th>VR. TOTAL</th></tr>");
        for (Fila r : lista) {
            if (vacia(r)) continue;
            h.append("<tr class='v'><td class='n'>").append(Util.xml(r.cant.getText().toString()))
             .append("</td><td>").append(Util.xml(r.desc.getText().toString()))
             .append("</td><td class='n'>").append(Util.pesos(Util.valor(r.unit.getText().toString())))
             .append("</td><td class='n'>").append(Util.pesos(total(r))).append("</td></tr>");
        }
        h.append("<tr><td colspan='3' style='text-align:right'><b>TOTAL $</b></td><td class='n v'><b>")
         .append(Util.pesos(g)).append("</b></td></tr></table><p><b>RECIBÍ:</b> <span class='v'>")
         .append(Util.xml(recibi.getText().toString())).append("</span></p></body></html>");
        imprimirHtml(h.toString(), TIPOS[tipo] + " " + numero.getText());
    }
}
