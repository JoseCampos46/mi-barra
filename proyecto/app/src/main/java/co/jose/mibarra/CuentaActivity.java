package co.jose.mibarra;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Versión 29: 🧾 Cuenta (mini Excel) que se pasa al mensaje o se vuelve recibo.
 * Artículo · Cantidad · Valor unidad · Valor total, y el total abajo.
 * El precio se puede dejar en blanco (lista de compras) o poner uno tentativo.
 */
public class CuentaActivity extends Base {

    private static final class FilaV { EditText art, cant, unit; TextView tot; }

    private SharedPreferences pr, barra;
    private int tipo;
    private final Button[] bTipos = new Button[Recibo.TIPOS.length];
    private EditText para;
    private LinearLayout filas;
    private TextView tTotal;
    private final List<FilaV> lista = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pr = getSharedPreferences("cuenta29", MODE_PRIVATE);
        barra = getSharedPreferences("barra", MODE_PRIVATE);
        tipo = pr.getInt("tipo", 0);
        LinearLayout l = vertical();
        l.setBackgroundColor(0xFFF5F7FA);
        l.setPadding(dp(6), dp(6), dp(6), dp(6));

        // ¿Qué es?
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout tl = horizontal();
        for (int i = 0; i < Recibo.TIPOS.length; i++) {
            final int k = i;
            Button bt = boton(Recibo.TIPOS[i], 0xFF455A64);
            bt.setTextSize(13);
            bt.setOnClickListener(v -> { tipo = k; pintarTipos(); });
            bTipos[i] = bt;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
            lp.setMargins(dp(2), 0, dp(2), 0);
            tl.addView(bt, lp);
        }
        hs.addView(tl);
        l.addView(hs);

        para = campoTexto("Cliente / para quién", pr.getString("para", ""));
        para.setTextColor(Color.BLACK);
        l.addView(para);

        // Títulos de la tabla
        LinearLayout cab = horizontal();
        String[] tt = {"Artículo", "Cant.", "Vr. unidad", "Vr. total"};
        float[] pw = {2.2f, 0.8f, 1.2f, 1.2f};
        for (int i = 0; i < 4; i++) {
            TextView t = new TextView(this);
            t.setText(tt[i]);
            t.setTextColor(Color.BLACK);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextSize(14);
            t.setGravity(i == 0 ? Gravity.START : Gravity.END);
            cab.addView(t, new LinearLayout.LayoutParams(0, -2, pw[i]));
        }
        l.addView(cab);

        ScrollView sv = new ScrollView(this);
        filas = vertical();
        sv.addView(filas);
        l.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        cargar();

        LinearLayout ft = horizontal();
        Button mas = boton("➕ Fila", 0xFF1565C0);
        mas.setOnClickListener(v -> { agregar(new Recibo.Fila()).art.requestFocus(); });
        ft.addView(mas, peso(1));
        tTotal = new TextView(this);
        tTotal.setTextSize(22);
        tTotal.setTypeface(Typeface.DEFAULT_BOLD);
        tTotal.setTextColor(Color.BLACK);
        tTotal.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        ft.addView(tTotal, peso(1.6f));
        l.addView(ft, ancho(50));

        LinearLayout b1 = horizontal();
        b1.addView(accion("↩ Al mensaje", 0xFF6A1B9A, this::alMensaje), peso(1));
        b1.addView(accion("🧾 Hacer recibo", 0xFF2E7D32, this::hacerRecibo), peso(1));
        l.addView(b1, ancho(54));
        LinearLayout b2 = horizontal();
        b2.addView(accion("⚙ Mis datos", 0xFF546E7A, this::misDatos), peso(1));
        b2.addView(accion("🗑 Nueva cuenta", 0xFFC62828, this::nueva), peso(1));
        l.addView(b2, ancho(46));
        setContentView(l);
        pintarTipos();
        recalcular();
    }

    @Override protected void onPause() { super.onPause(); guardar(); }

    private Button accion(String t, int c, Runnable r) {
        Button b = boton(t, c);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private void pintarTipos() {
        for (int i = 0; i < bTipos.length; i++) {
            bTipos[i].setText((i == tipo ? "✔ " : "") + Recibo.TIPOS[i]);
            bTipos[i].setBackground(Comun.fondo(this, i == tipo ? 0xFF1565C0 : 0xFF78909C, 10));
        }
        para.setHint(Recibo.PARA[tipo]);
    }

    private EditText celda(String s, boolean numero, float tam) {
        EditText e = new EditText(this);
        e.setText(s);
        e.setTextSize(tam);
        e.setTextColor(Color.BLACK);
        e.setSingleLine(true);
        e.setSelectAllOnFocus(false);
        if (numero) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            e.setGravity(Gravity.END);
        } else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) {}
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) {}
            @Override public void afterTextChanged(Editable x) { recalcular(); }
        });
        return e;
    }

    private FilaV agregar(Recibo.Fila f) {
        FilaV v = new FilaV();
        LinearLayout r = horizontal();
        v.art = celda(f.art, false, 15);
        v.cant = celda(f.cant, true, 15);
        v.unit = celda(f.unit, true, 15);
        v.unit.setHint("tentativo");
        v.tot = new TextView(this);
        v.tot.setTextColor(Color.BLACK);
        v.tot.setTextSize(15);
        v.tot.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        v.tot.setOnLongClickListener(x -> {
            new AlertDialog.Builder(this).setTitle("¿Borrar esta fila?")
                    .setPositiveButton("🗑 Borrar", (d, w) -> { filas.removeView(r); lista.remove(v); recalcular(); })
                    .setNegativeButton("No", null).show();
            return true;
        });
        r.addView(v.art, new LinearLayout.LayoutParams(0, -2, 2.2f));
        r.addView(v.cant, new LinearLayout.LayoutParams(0, -2, 0.8f));
        r.addView(v.unit, new LinearLayout.LayoutParams(0, -2, 1.2f));
        r.addView(v.tot, new LinearLayout.LayoutParams(0, -1, 1.2f));
        filas.addView(r);
        lista.add(v);
        return v;
    }

    private Recibo recibo() {
        Recibo r = new Recibo();
        r.tipo = Recibo.TIPOS[tipo];
        r.paraEtiq = Recibo.PARA[tipo];
        r.para = para.getText().toString().trim();
        r.fecha = new SimpleDateFormat("d 'de' MMMM 'de' yyyy", new Locale("es", "CO")).format(new Date());
        r.negocio = barra.getString("negocio", "");
        r.nit = barra.getString("nitNegocio", "");
        r.tel = barra.getString("telNegocio", "");
        for (FilaV v : lista) {
            Recibo.Fila f = new Recibo.Fila();
            f.art = v.art.getText().toString().trim();
            f.cant = v.cant.getText().toString().trim();
            f.unit = v.unit.getText().toString().trim();
            if (!f.art.isEmpty() || !f.unit.isEmpty()) r.filas.add(f);
        }
        return r;
    }

    private void recalcular() {
        if (tTotal == null) return;
        for (FilaV v : lista) {
            Recibo.Fila f = new Recibo.Fila();
            f.cant = v.cant.getText().toString();
            f.unit = v.unit.getText().toString();
            long t = Recibo.valor(f);
            v.tot.setText(t > 0 ? Util.pesos(t) : "");
        }
        tTotal.setText("Total " + Util.pesos(recibo().total()));
    }

    private void cargar() {
        String s = pr.getString("filas", "");
        for (String x : s.split("\n")) {
            if (x.isEmpty()) continue;
            String[] p = (x + "\t\t\t").split("\t", -1);
            Recibo.Fila f = new Recibo.Fila();
            f.art = p[0]; f.cant = p[1]; f.unit = p[2];
            agregar(f);
        }
        while (lista.size() < 5) agregar(new Recibo.Fila());
    }

    private void guardar() {
        StringBuilder b = new StringBuilder();
        for (FilaV v : lista)
            b.append(limpio(v.art.getText().toString())).append('\t').append(limpio(v.cant.getText().toString()))
                    .append('\t').append(limpio(v.unit.getText().toString())).append('\n');
        pr.edit().putString("filas", b.toString()).putString("para", para.getText().toString()).putInt("tipo", tipo).apply();
    }

    private void nueva() {
        new AlertDialog.Builder(this).setTitle("¿Empezar una cuenta nueva?").setMessage("Se borra la que está en pantalla.")
                .setPositiveButton("🗑 Nueva", (d, w) -> {
                    filas.removeAllViews(); lista.clear(); para.setText("");
                    for (int i = 0; i < 5; i++) agregar(new Recibo.Fila());
                    recalcular();
                }).setNegativeButton("No", null).show();
    }

    private void misDatos() {
        LinearLayout l = vertical();
        l.setPadding(dp(16), dp(8), dp(16), 0);
        EditText n = campoTexto("Nombre o negocio", barra.getString("negocio", ""));
        EditText ni = campoTexto("NIT o C.C.", barra.getString("nitNegocio", ""));
        EditText t = campoTexto("Teléfono y dirección", barra.getString("telNegocio", ""));
        l.addView(n); l.addView(ni); l.addView(t);
        new AlertDialog.Builder(this).setTitle("⚙ Mis datos (salen arriba en todos los recibos)").setView(l)
                .setPositiveButton("Guardar", (d, w) -> barra.edit()
                        .putString("negocio", n.getText().toString().trim())
                        .putString("nitNegocio", ni.getText().toString().trim())
                        .putString("telNegocio", t.getText().toString().trim()).apply())
                .setNegativeButton("Cancelar", null).show();
    }

    /** Devuelve la cuenta a «Escribir bien» (o la abre allá si vino de otra parte). */
    private void alMensaje() {
        Recibo r = recibo();
        if (r.filas.isEmpty()) { aviso("La cuenta está vacía"); return; }
        guardar();
        Intent i = new Intent().putExtra("texto", r.comoTexto()).putExtra("leer", r.paraLeer());
        if (getCallingActivity() != null) { setResult(RESULT_OK, i); finish(); }
        else { startActivity(i.setClass(this, EscribirActivity.class)); finish(); }
    }

    private void hacerRecibo() {
        Recibo r = recibo();
        if (r.filas.isEmpty()) { aviso("La cuenta está vacía"); return; }
        guardar();
        new AlertDialog.Builder(this).setTitle("🧾 ¿Qué tamaño?").setItems(Recibo.TAMANOS, (d, tam) -> {
            String[] ops = {"🟢 Enviar por WhatsApp", "🖨 Imprimir", "💾 Guardar imagen"};
            new AlertDialog.Builder(this).setTitle(Recibo.TAMANOS[tam]).setItems(ops, (d2, w) -> {
                String nombre = Comun.nombreArchivo(r.tipo + "_" + r.para);
                if (w == 1) imprimir(r.hoja(tam), r.tipo);
                else {
                    Uri u = guardarPng(r.imagen(tam), nombre);
                    if (u == null) return;
                    if (w == 0) enviar(WHATSAPP, "image/png", u);
                    else aviso("🖼 Guardada en Descargas/MiBarra");
                }
            }).show();
        }).show();
    }

    /** Imprime la hoja carta (con su imagen bien nítida). */
    private void imprimir(Bitmap hoja, String titulo) {
        try {
            java.io.File f = new java.io.File(getCacheDir(), "recibo_imprimir.jpg");
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { hoja.compress(Bitmap.CompressFormat.JPEG, 95, o); }
            startActivity(new Intent(this, PrintActivity.class).putExtra("paginas", new String[]{f.getAbsolutePath()}).putExtra("titulo", titulo));
        } catch (Exception e) { aviso("No se pudo preparar la impresión"); }
    }
}
