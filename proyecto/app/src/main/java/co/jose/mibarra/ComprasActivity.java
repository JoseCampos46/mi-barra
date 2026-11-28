package co.jose.mibarra;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Calculadora de compras: listas guardadas dentro de la app, sin límite. */
public class ComprasActivity extends VentanaActivity {

    private static class Fila {
        View vista;
        EditText art, cant, valor;
        Button unidad;
        TextView total;
    }

    private File carpeta, actual;
    private LinearLayout filas;
    private EditText nombre;
    private TextView granTotal;
    private final List<Fila> lista = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        carpeta = new File(getFilesDir(), "listas");
        carpeta.mkdirs();
        mostrarListas();
    }

    @Override
    protected void onPause() {
        super.onPause();
        guardar(false);
    }

    @Override
    public void onBackPressed() {
        if (actual != null) {
            guardar(false);
            mostrarListas();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------- Pantalla: mis listas ----------------

    private void mostrarListas() {
        actual = null;
        lista.clear();
        LinearLayout l = vertical();
        int p = dp(16);
        l.setPadding(p, p, p, p);
        l.addView(titulo("🛒 Mis listas de compras"));
        Button nueva = boton("＋  Nueva lista", 0xFF2E7D32);
        nueva.setOnClickListener(v -> abrirLista(new File(carpeta, System.currentTimeMillis() + ".txt"), true));
        l.addView(nueva, ancho(56));

        File[] fs = carpeta.listFiles();
        if (fs == null || fs.length == 0) {
            TextView t = new TextView(this);
            t.setText("\nTodavía no hay listas guardadas.");
            t.setTextSize(16);
            l.addView(t);
        } else {
            Arrays.sort(fs, (a, c) -> Long.compare(c.lastModified(), a.lastModified()));
            SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            for (File f : fs) {
                String[] r = resumen(f);
                Button bt = boton(r[0] + "\n" + r[1] + "   ·   " + df.format(new Date(f.lastModified())), 0xFF37474F);
                bt.setOnClickListener(v -> abrirLista(f, false));
                l.addView(bt, ancho(72));
            }
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private String[] resumen(File f) {
        try {
            List<String> ls = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            String n = ls.isEmpty() ? "Lista" : ls.get(0);
            long g = 0;
            for (int i = 1; i < ls.size(); i++) {
                String[] p = ls.get(i).split("\t", -1);
                if (p.length >= 4) g += Math.round(Util.cantidad(p[1]) * Util.valor(p[3]));
            }
            return new String[]{n.isEmpty() ? "Lista" : n, Util.pesos(g)};
        } catch (Exception e) {
            return new String[]{"Lista", ""};
        }
    }

    // ---------------- Pantalla: una lista ----------------

    private void abrirLista(File f, boolean nueva) {
        actual = f;
        lista.clear();
        int p = dp(10);

        LinearLayout raiz = vertical();
        raiz.setPadding(p, p, p, p);

        Button volver = boton("←  Mis listas", 0xFF546E7A);
        volver.setOnClickListener(v -> onBackPressed());
        raiz.addView(volver, ancho(48));

        nombre = new EditText(this);
        nombre.setHint("Nombre de la lista");
        nombre.setTextSize(18);
        nombre.setTypeface(Typeface.DEFAULT_BOLD);
        nombre.setSingleLine(true);
        raiz.addView(nombre);

        filas = vertical();
        ScrollView sv = new ScrollView(this);
        sv.addView(filas);
        raiz.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button agregar = boton("＋  Agregar artículo", 0xFF2E7D32);
        agregar.setOnClickListener(v -> {
            Fila r = agregarFila("", "", "u", "");
            r.art.requestFocus();
            sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
        });
        raiz.addView(agregar, ancho(50));

        granTotal = new TextView(this);
        granTotal.setTextSize(24);
        granTotal.setTypeface(Typeface.DEFAULT_BOLD);
        granTotal.setGravity(Gravity.END);
        granTotal.setPadding(0, dp(6), dp(4), dp(6));
        raiz.addView(granTotal);

        LinearLayout a1 = horizontal();
        a1.addView(accion("💾 Guardar", 0xFF1565C0, v -> guardar(true)), peso(1));
        a1.addView(accion("📤 WhatsApp", 0xFF25A244, v -> compartir()), peso(1));
        a1.addView(accion("🖨 Imprimir", 0xFF6A1B9A, v -> imprimir()), peso(1));
        raiz.addView(a1);
        LinearLayout a2 = horizontal();
        a2.addView(accion("⬇ Excel (CSV)", 0xFF00695C, v -> exportar()), peso(1));
        a2.addView(accion("🗑 Borrar lista", 0xFFC62828, v -> borrar()), peso(1));
        raiz.addView(a2);

        setContentView(raiz);

        boolean cargada = false;
        if (!nueva) cargada = cargar(f);
        if (!cargada) {
            if (nombre.getText().length() == 0)
                nombre.setText("Compras " + new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date()));
            agregarFila("", "", "u", "");
        }
        recalcular();
    }

    private Fila agregarFila(String art, String cant, String uni, String val) {
        Fila r = new Fila();
        LinearLayout card = vertical();
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFF1F4F7);
        g.setCornerRadius(dp(10));
        card.setBackground(g);
        card.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(8);

        LinearLayout l1 = horizontal();
        r.art = campo("Artículo (con pelos y señales)", art,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        l1.addView(r.art, peso(1));
        Button x = new Button(this);
        x.setText("✕");
        x.setOnClickListener(v -> {
            filas.removeView(card);
            lista.remove(r);
            recalcular();
        });
        l1.addView(x, new LinearLayout.LayoutParams(dp(52), dp(48)));
        card.addView(l1);

        LinearLayout l2 = horizontal();
        l2.setGravity(Gravity.CENTER_VERTICAL);
        r.cant = campo("Cant.", cant, InputType.TYPE_CLASS_NUMBER);
        r.cant.setKeyListener(DigitsKeyListener.getInstance("0123456789.,"));
        r.cant.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        l2.addView(r.cant, peso(1.1f));

        r.unidad = new Button(this);
        r.unidad.setAllCaps(false);
        ponerUnidad(r, "k".equalsIgnoreCase(uni) ? "k" : "u");
        r.unidad.setOnClickListener(v -> {
            ponerUnidad(r, "k".equals(r.unidad.getTag()) ? "u" : "k");
            recalcular();
        });
        l2.addView(r.unidad, peso(0.9f));

        r.valor = campo("Valor u/kg", val, InputType.TYPE_CLASS_NUMBER);
        l2.addView(r.valor, peso(1.8f));

        r.total = new TextView(this);
        r.total.setTextSize(16);
        r.total.setTypeface(Typeface.DEFAULT_BOLD);
        r.total.setGravity(Gravity.END);
        l2.addView(r.total, peso(1.8f));
        card.addView(l2);

        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { recalcular(); }
        };
        r.cant.addTextChangedListener(w);
        r.valor.addTextChangedListener(w);

        filas.addView(card, cp);
        r.vista = card;
        lista.add(r);
        return r;
    }

    private void ponerUnidad(Fila r, String u) {
        r.unidad.setTag(u);
        r.unidad.setText("k".equals(u) ? "kg" : "u");
    }

    private String uni(Fila r) { return "k".equals(r.unidad.getTag()) ? "k" : "u"; }
    private String uniTxt(Fila r) { return "k".equals(r.unidad.getTag()) ? "kg" : "u"; }

    private long total(Fila r) {
        return Math.round(Util.cantidad(r.cant.getText().toString()) * Util.valor(r.valor.getText().toString()));
    }

    private boolean vacia(Fila r) {
        return r.art.getText().toString().trim().isEmpty() && r.valor.getText().toString().trim().isEmpty();
    }

    private void recalcular() {
        if (granTotal == null) return;
        long g = 0;
        for (Fila r : lista) {
            long t = total(r);
            g += t;
            r.total.setText(Util.pesos(t));
        }
        granTotal.setText("TOTAL: " + Util.pesos(g));
    }

    // ---------------- Guardar / cargar ----------------

    private static String limpio(String s) { return s.replace('\t', ' ').replace('\n', ' ').trim(); }

    private void guardar(boolean avisar) {
        if (actual == null || nombre == null) return;
        StringBuilder s = new StringBuilder(limpio(nombre.getText().toString())).append('\n');
        int n = 0;
        for (Fila r : lista) {
            if (vacia(r)) continue;
            s.append(limpio(r.art.getText().toString())).append('\t')
             .append(limpio(r.cant.getText().toString())).append('\t')
             .append(uni(r)).append('\t')
             .append(limpio(r.valor.getText().toString())).append('\n');
            n++;
        }
        if (n == 0 && !actual.exists()) {
            if (avisar) aviso("La lista está vacía");
            return;
        }
        try {
            Files.write(actual.toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
            if (avisar) aviso("💾 Lista guardada");
        } catch (Exception e) {
            aviso("No se pudo guardar: " + e.getMessage());
        }
    }

    private boolean cargar(File f) {
        try {
            List<String> ls = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            if (ls.isEmpty()) return false;
            nombre.setText(ls.get(0));
            int n = 0;
            for (int i = 1; i < ls.size(); i++) {
                String[] p = ls.get(i).split("\t", -1);
                if (p.length < 4) continue;
                agregarFila(p[0], p[1], p[2], p[3]);
                n++;
            }
            return n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- Compartir, imprimir, CSV, borrar ----------------

    private String textoLista() {
        StringBuilder s = new StringBuilder("🛒 ").append(nombre.getText().toString().trim()).append("\n\n");
        long g = 0;
        for (Fila r : lista) {
            if (vacia(r)) continue;
            long t = total(r);
            g += t;
            s.append("• ").append(r.art.getText().toString().trim()).append(" — ")
             .append(Util.cant(Util.cantidad(r.cant.getText().toString()))).append(' ').append(uniTxt(r))
             .append(" × ").append(Util.pesos(Util.valor(r.valor.getText().toString())))
             .append(" = ").append(Util.pesos(t)).append('\n');
        }
        s.append("\nTOTAL: ").append(Util.pesos(g));
        return s.toString();
    }

    private void compartir() {
        guardar(false);
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, textoLista());
        i.setPackage("com.whatsapp");
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            i.setPackage(null);
            startActivity(Intent.createChooser(i, "Compartir lista"));
        }
    }

    private void imprimir() {
        guardar(false);
        StringBuilder h = new StringBuilder("<html><head><meta charset='utf-8'><style>")
                .append("body{font-family:sans-serif;font-size:14px}table{border-collapse:collapse;width:100%}")
                .append("td,th{border:1px solid #888;padding:6px}th{background:#eee}.n{text-align:right}")
                .append("</style></head><body><h2>").append(Util.xml(nombre.getText().toString())).append("</h2>")
                .append("<table><tr><th>Artículo</th><th>Cant.</th><th>Unidad</th><th>Valor unidad</th><th>Total</th></tr>");
        long g = 0;
        for (Fila r : lista) {
            if (vacia(r)) continue;
            long t = total(r);
            g += t;
            h.append("<tr><td>").append(Util.xml(r.art.getText().toString())).append("</td><td class='n'>")
             .append(Util.cant(Util.cantidad(r.cant.getText().toString()))).append("</td><td>").append(uniTxt(r))
             .append("</td><td class='n'>").append(Util.pesos(Util.valor(r.valor.getText().toString())))
             .append("</td><td class='n'>").append(Util.pesos(t)).append("</td></tr>");
        }
        h.append("<tr><th colspan='4' style='text-align:right'>TOTAL</th><th class='n'>")
         .append(Util.pesos(g)).append("</th></tr></table></body></html>");
        startActivity(new Intent(this, PrintActivity.class)
                .putExtra("html", h.toString())
                .putExtra("titulo", nombre.getText().toString()));
    }

    private static String csv(String s) {
        if (s.contains(";") || s.contains("\"")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private void exportar() {
        guardar(false);
        StringBuilder s = new StringBuilder("﻿Artículo;Cantidad;Unidad;Valor unidad;Total\n");
        long g = 0;
        for (Fila r : lista) {
            if (vacia(r)) continue;
            long t = total(r);
            g += t;
            s.append(csv(r.art.getText().toString().trim())).append(';')
             .append(Util.cant(Util.cantidad(r.cant.getText().toString()))).append(';')
             .append(uniTxt(r)).append(';')
             .append(Util.valor(r.valor.getText().toString())).append(';')
             .append(t).append('\n');
        }
        s.append("TOTAL;;;;").append(g).append('\n');
        String base = nombre.getText().toString().trim().replaceAll("[^\\p{L}\\p{N}]+", "_");
        if (base.isEmpty()) base = "Compras";
        try {
            Util.guardarBytes(this, base + "_" + Util.fecha() + ".csv", "text/csv",
                    s.toString().getBytes(StandardCharsets.UTF_8));
            aviso("⬇ Lista guardada en Descargas (se abre con Excel)");
        } catch (Exception e) {
            aviso("No se pudo guardar: " + e.getMessage());
        }
    }

    private void borrar() {
        new AlertDialog.Builder(this)
                .setTitle("¿Borrar esta lista?")
                .setMessage("Se borra de la aplicación. Los archivos que ya guardó en Descargas no se tocan.")
                .setPositiveButton("Sí, borrar", (d, w) -> {
                    if (actual != null) actual.delete();
                    actual = null;
                    nombre = null;
                    mostrarListas();
                })
                .setNegativeButton("No", null)
                .show();
    }

    // ---------------- Ayudas de diseño ----------------

    private void aviso(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
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

    private LinearLayout.LayoutParams ancho(int altoDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(altoDp));
        p.topMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams peso(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
    }

    private TextView titulo(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(22);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button boton(String t, int color) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        b.setBackground(g);
        return b;
    }

    private Button accion(String t, int color, View.OnClickListener l) {
        Button b = boton(t, color);
        b.setTextSize(14);
        b.setOnClickListener(l);
        return b;
    }

    private EditText campo(String pista, String texto, int tipo) {
        EditText e = new EditText(this);
        e.setHint(pista);
        e.setText(texto);
        e.setInputType(tipo);
        e.setTextSize(16);
        e.setSingleLine(true);
        return e;
    }
}
