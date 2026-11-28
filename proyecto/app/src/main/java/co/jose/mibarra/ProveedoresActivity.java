package co.jose.mibarra;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Directorio de proveedores: qué venden, precios, y botones para llamar, WhatsApp o cómo llegar. */
public class ProveedoresActivity extends Base {

    private final List<String[]> todos = new ArrayList<>(); // nombre, productos, teléfono, dirección, precios/notas
    private LinearLayout lista;
    private EditText buscar;

    private File archivo() { return new File(getFilesDir(), "proveedores.tsv"); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        leer();
        LinearLayout l = vertical();
        l.setPadding(dp(10), dp(10), dp(10), dp(10));
        l.addView(titulo("📇 Mis proveedores"));
        LinearLayout f = horizontal();
        buscar = campoTexto("🔎 Buscar: cemento, tubo, Pedro…", "");
        buscar.setSingleLine(true);
        buscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { pintar(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        f.addView(buscar, peso(1));
        Button nuevo = boton("＋", 0xFF2E7D32);
        nuevo.setTextSize(22);
        nuevo.setOnClickListener(v -> editar(null));
        f.addView(nuevo, new LinearLayout.LayoutParams(dp(58), dp(54)));
        l.addView(f);
        lista = vertical();
        l.addView(lista);
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
        pintar();
    }

    private void leer() {
        todos.clear();
        try {
            for (String s : Files.readAllLines(archivo().toPath(), StandardCharsets.UTF_8)) {
                String[] p = s.split("\t", -1);
                if (p.length >= 5) todos.add(new String[]{p[0], p[1], p[2], p[3], p[4].replace("\\n", "\n")});
            }
        } catch (Exception ignored) {}
        Collections.sort(todos, (a, b) -> a[0].compareToIgnoreCase(b[0]));
    }

    private void guardar() {
        StringBuilder s = new StringBuilder();
        for (String[] p : todos)
            s.append(limpio(p[0])).append('\t').append(limpio(p[1])).append('\t').append(limpio(p[2])).append('\t')
             .append(limpio(p[3])).append('\t').append(p[4].replace("\t", " ").replace("\r", "").replace("\n", "\\n")).append('\n');
        try { Files.write(archivo().toPath(), s.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { aviso("No se pudo guardar"); }
    }

    private void pintar() {
        lista.removeAllViews();
        String q = SaludosActivity.sinTildes(buscar.getText().toString().trim());
        int n = 0;
        for (String[] p : todos) {
            if (!q.isEmpty() && !SaludosActivity.sinTildes(String.join(" ", p)).contains(q)) continue;
            n++;
            lista.addView(tarjeta(p));
        }
        if (n == 0) {
            TextView t = new TextView(this);
            t.setText(todos.isEmpty() ? "\nToque ＋ para agregar su primer proveedor." : "\nNo encontré nada con eso.");
            t.setTextSize(16);
            lista.addView(t);
        }
    }

    private LinearLayout tarjeta(String[] p) {
        LinearLayout c = vertical();
        c.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFF1F4F7);
        g.setCornerRadius(dp(12));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        c.setLayoutParams(lp);
        TextView n = new TextView(this);
        n.setText(p[0]);
        n.setTextSize(19);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setTextColor(Color.BLACK);
        c.addView(n);
        StringBuilder d = new StringBuilder();
        if (!p[1].isEmpty()) d.append("🛒 ").append(p[1]).append('\n');
        if (!p[2].isEmpty()) d.append("📞 ").append(p[2]).append('\n');
        if (!p[3].isEmpty()) d.append("📍 ").append(p[3]).append('\n');
        if (!p[4].isEmpty()) d.append("💲 ").append(p[4]);
        TextView t = new TextView(this);
        t.setText(d.toString().trim());
        t.setTextSize(15);
        t.setTextColor(0xFF37474F);
        c.addView(t);
        LinearLayout f = horizontal();
        f.addView(chico("📞", 0xFF1565C0, () -> {
            if (p[2].isEmpty()) { aviso("No tiene teléfono"); return; }
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p[2].replaceAll("[^0-9+]", ""))));
        }), peso(1));
        f.addView(chico("💬", 0xFF25A244, () -> {
            String tel = p[2].replaceAll("[^0-9]", "");
            if (tel.isEmpty()) { aviso("No tiene teléfono"); return; }
            if (tel.length() == 10) tel = "57" + tel;
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + tel))); } catch (Exception e) { aviso("No se pudo abrir WhatsApp"); }
        }), peso(1));
        f.addView(chico("🧭", 0xFFEF6C00, () -> {
            if (p[3].isEmpty()) { aviso("No tiene dirección"); return; }
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(p[3])))); }
            catch (Exception e) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(p[3])))); }
        }), peso(1));
        f.addView(chico("📤", 0xFF546E7A, () -> textoWhatsapp("*" + p[0] + "*\n" + d.toString().trim())), peso(1));
        f.addView(chico("✎", 0xFF455A64, () -> editar(p)), peso(1));
        c.addView(f, ancho(46));
        return c;
    }

    private Button chico(String t, int color, Runnable r) {
        Button b = boton(t, color);
        b.setTextSize(18);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private void editar(String[] p) {
        LinearLayout f = vertical();
        f.setPadding(dp(16), dp(8), dp(16), 0);
        EditText nom = campoTexto("Nombre o almacén", p == null ? "" : p[0]);
        EditText pro = campoTexto("Qué vende (cemento, tubería, pintura…)", p == null ? "" : p[1]);
        EditText tel = campoTexto("Teléfono / WhatsApp", p == null ? "" : p[2]);
        tel.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText dir = campoTexto("Dirección y ciudad", p == null ? "" : p[3]);
        EditText pre = campoTexto("Precios y notas (ej: bulto cemento $32.000)", p == null ? "" : p[4]);
        pre.setSingleLine(false);
        pre.setMinLines(3);
        for (EditText e : new EditText[]{nom, pro, tel, dir, pre}) f.addView(e);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(p == null ? "Nuevo proveedor" : "Editar proveedor").setView(sv)
                .setPositiveButton("Guardar", (d, w) -> {
                    String n = limpio(nom.getText().toString());
                    if (n.isEmpty()) { aviso("Ponga al menos el nombre"); return; }
                    String[] q = {n, limpio(pro.getText().toString()), limpio(tel.getText().toString()),
                            limpio(dir.getText().toString()), pre.getText().toString().trim()};
                    if (p != null) todos.remove(p);
                    todos.add(q);
                    Collections.sort(todos, (x, y) -> x[0].compareToIgnoreCase(y[0]));
                    guardar();
                    pintar();
                }).setNegativeButton("Cancelar", null);
        if (p != null) b.setNeutralButton("🗑 Borrar", (d, w) -> new AlertDialog.Builder(this)
                .setMessage("¿Borrar a " + p[0] + "?").setPositiveButton("Sí, borrar", (d2, w2) -> { todos.remove(p); guardar(); pintar(); })
                .setNegativeButton("No", null).show());
        b.show();
    }
}
