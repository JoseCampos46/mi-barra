package co.jose.mibarra;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Versión 25: 🐜 Gastos hormiga. Artículo, cantidad, valor y tienda; totales del día, el mes y el año.
 * Arriba, los horarios de las tiendas (abre, descansa, cierra) y si están abiertas ahora.
 */
public class GastosActivity extends Base {

    private static final Locale ES = new Locale("es", "CO");
    private static final int VOZ = 61;
    private final SimpleDateFormat dia = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    // Gasto: fecha (ms), artículo, cantidad, valor, tienda
    static final class Gasto { long cuando; String art, tienda; double cant; long valor; long total() { return Math.round(cant * valor); } }
    // Tienda: nombre, abre, descansa desde, descansa hasta, cierra (minutos del día; -1 = no descansa)
    static final class Tienda { String nombre; int abre = 7 * 60, desDe = -1, desHa = -1, cierra = 20 * 60; }

    private final List<Gasto> gastos = new ArrayList<>();
    private final List<Tienda> tiendas = new ArrayList<>();
    private EditText eArt, eCant, eValor;
    private AutoCompleteTextView eTienda;
    private TextView tTotal;
    private boolean verTiendas;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        leer();
        verTiendas = getIntent().getBooleanExtra("tiendas", false);
        pintar();
    }

    @Override public void onBackPressed() { if (verTiendas) { verTiendas = false; pintar(); } else super.onBackPressed(); }

    // ---------------- Guardar y leer ----------------

    private File fGastos() { return new File(getFilesDir(), "gastos_hormiga.txt"); }
    private File fTiendas() { return new File(getFilesDir(), "tiendas_horarios.txt"); }

    private void leer() {
        gastos.clear();
        tiendas.clear();
        try {
            for (String l : Files.readAllLines(fGastos().toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p.length < 5) continue;
                Gasto g = new Gasto();
                g.cuando = Long.parseLong(p[0]); g.art = p[1]; g.cant = Util.cantidad(p[2]); g.valor = Util.valor(p[3]); g.tienda = p[4];
                gastos.add(g);
            }
        } catch (Exception ignored) {}
        try {
            for (String l : Files.readAllLines(fTiendas().toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p.length < 5) continue;
                Tienda t = new Tienda();
                t.nombre = p[0]; t.abre = Integer.parseInt(p[1]); t.desDe = Integer.parseInt(p[2]); t.desHa = Integer.parseInt(p[3]); t.cierra = Integer.parseInt(p[4]);
                tiendas.add(t);
            }
        } catch (Exception ignored) {}
    }

    private void guardar() {
        StringBuilder b = new StringBuilder();
        for (Gasto g : gastos) b.append(g.cuando).append('\t').append(g.art.replace('\t', ' ')).append('\t').append(Util.cant(g.cant))
                .append('\t').append(g.valor).append('\t').append(g.tienda.replace('\t', ' ')).append('\n');
        StringBuilder t = new StringBuilder();
        for (Tienda x : tiendas) t.append(x.nombre.replace('\t', ' ')).append('\t').append(x.abre).append('\t').append(x.desDe)
                .append('\t').append(x.desHa).append('\t').append(x.cierra).append('\n');
        try {
            Files.write(fGastos().toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            Files.write(fTiendas().toPath(), t.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { aviso("No se pudo guardar"); }
    }

    // ---------------- Ayudas de dibujo ----------------

    private GradientDrawable caja(int color, int borde) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        if (borde != 0) g.setStroke(dp(2), borde);
        return g;
    }

    private TextView letra(String s, float tam, int color, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(tam);
        t.setTextColor(color);
        if (negrita) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams arriba(int m) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(m);
        return lp;
    }

    static String hora(int min) {
        if (min < 0) return "—";
        int h = min / 60, m = min % 60;
        return (h % 12 == 0 ? 12 : h % 12) + ":" + String.format(Locale.US, "%02d", m) + (h < 12 ? " a. m." : " p. m.");
    }

    /** "🟢 Abierta · cierra a las 8:00 p. m." */
    static String estado(Tienda t) {
        Calendar c = Calendar.getInstance();
        int ahora = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        boolean descansa = t.desDe >= 0 && t.desHa > t.desDe && ahora >= t.desDe && ahora < t.desHa;
        if (ahora < t.abre) return "🔴 Cerrada · abre a las " + hora(t.abre);
        if (ahora >= t.cierra) return "🔴 Cerrada · abre mañana a las " + hora(t.abre);
        if (descansa) return "🟠 En descanso · vuelve a las " + hora(t.desHa);
        if (t.desDe >= 0 && ahora < t.desDe) return "🟢 Abierta · descansa a las " + hora(t.desDe);
        return "🟢 Abierta · cierra a las " + hora(t.cierra);
    }

    // ---------------- Pantalla principal ----------------

    private long sumar(String desde, int largo) {
        long s = 0;
        for (Gasto g : gastos) if (dia.format(new Date(g.cuando)).substring(0, largo).equals(desde.substring(0, largo))) s += g.total();
        return s;
    }

    private void pintar() {
        if (verTiendas) { pintarTiendas(); return; }
        LinearLayout l = vertical();
        l.setBackgroundColor(0xFFF6F1EA);
        l.setPadding(dp(12), dp(10), dp(12), dp(24));

        // Horarios de las tiendas, arriba
        for (Tienda t : tiendas) {
            TextView x = letra("🏪 " + t.nombre + ":  " + estado(t), 14, 0xFF263238, true);
            x.setPadding(dp(10), dp(6), dp(10), dp(6));
            x.setBackground(caja(Color.WHITE, 0));
            x.setOnClickListener(v -> { verTiendas = true; pintar(); });
            l.addView(x, arriba(4));
        }

        // Totales
        String hoy = dia.format(new Date());
        LinearLayout tot = horizontal();
        String[][] tt = {{"Hoy", Util.pesos(sumar(hoy, 10))}, {new SimpleDateFormat("MMMM", ES).format(new Date()), Util.pesos(sumar(hoy, 7))},
                {hoy.substring(0, 4), Util.pesos(sumar(hoy, 4))}};
        int[] colores = {0xFFEF6C00, 0xFF6A1B9A, 0xFF1565C0};
        for (int i = 0; i < 3; i++) {
            LinearLayout c = vertical();
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.setPadding(dp(4), dp(8), dp(4), dp(8));
            c.setBackground(caja(Color.WHITE, colores[i]));
            TextView a = letra(tt[i][0].substring(0, 1).toUpperCase(ES) + tt[i][0].substring(1), 12, 0xFF757575, true);
            c.addView(a);
            c.addView(letra(tt[i][1], 16, 0xFF212121, true));
            final int k = i;
            c.setOnClickListener(v -> { if (k == 1) porMes(); else if (k == 2) porAnio(); });
            tot.addView(c, peso(1));
        }
        l.addView(tot, arriba(8));

        // Anotar
        LinearLayout f = vertical();
        f.setPadding(dp(12), dp(10), dp(12), dp(12));
        f.setBackground(caja(Color.WHITE, 0xFFEF6C00));
        f.addView(letra("➕ Anotar gasto", 16, 0xFF263238, true));
        eArt = campo("Artículo (ej: gaseosa)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        f.addView(eArt);
        LinearLayout r = horizontal();
        eCant = campo("Cantidad", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        eCant.setText("1");
        eValor = campo("Valor de cada uno $", InputType.TYPE_CLASS_NUMBER);
        r.addView(eCant, peso(0.8f));
        r.addView(eValor, peso(1.4f));
        f.addView(r);
        eTienda = new AutoCompleteTextView(this);
        eTienda.setHint("Tienda donde compró");
        eTienda.setTextSize(16);
        eTienda.setThreshold(1);
        List<String> nombres = new ArrayList<>();
        for (Tienda t : tiendas) nombres.add(t.nombre);
        for (Gasto g : gastos) if (!g.tienda.isEmpty() && !nombres.contains(g.tienda)) nombres.add(g.tienda);
        eTienda.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, nombres));
        if (!gastos.isEmpty()) eTienda.setText(gastos.get(gastos.size() - 1).tienda);
        f.addView(eTienda);
        tTotal = letra("Total: $ 0", 18, 0xFF2E7D32, true);
        tTotal.setGravity(Gravity.END);
        f.addView(tTotal);
        TextWatcher tw = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) {
                tTotal.setText("Total: " + Util.pesos(Math.round(Util.cantidad(eCant.getText().toString()) * Util.valor(eValor.getText().toString()))));
            }
        };
        eCant.addTextChangedListener(tw);
        eValor.addTextChangedListener(tw);
        LinearLayout bs = horizontal();
        Button gu = boton("💾 Guardar", 0xFFEF6C00);
        gu.setOnClickListener(v -> anotar());
        Button di = boton("🎤 Dictar", 0xFF455A64);
        di.setOnClickListener(v -> {
            try {
                startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga, por ejemplo: dos gaseosas a 3500"), VOZ);
            } catch (Exception e) { aviso("Este celular no tiene dictado"); }
        });
        bs.addView(gu, peso(1.3f));
        bs.addView(di, peso(1));
        f.addView(bs, ancho(54));
        l.addView(f, arriba(10));

        // Hoy
        LinearLayout tb = vertical();
        tb.setPadding(dp(10), dp(8), dp(10), dp(8));
        tb.setBackground(caja(Color.WHITE, 0));
        String titulo = new SimpleDateFormat("EEEE d 'de' MMMM", ES).format(new Date());
        tb.addView(letra("Hoy, " + titulo, 15, 0xFF263238, true));
        tb.addView(filaTabla(new String[]{"Artículo", "Cant", "Valor", "Total", "Tienda"}, true, null));
        long suma = 0;
        for (int i = gastos.size() - 1; i >= 0; i--) {
            Gasto g = gastos.get(i);
            if (!dia.format(new Date(g.cuando)).equals(hoy)) continue;
            suma += g.total();
            tb.addView(filaTabla(new String[]{g.art, Util.cant(g.cant), miles(g.valor), miles(g.total()), g.tienda}, false, g));
        }
        TextView ts = letra("Total del día: " + Util.pesos(suma), 16, 0xFFEF6C00, true);
        ts.setGravity(Gravity.END);
        ts.setPadding(0, dp(6), 0, 0);
        tb.addView(ts);
        tb.addView(letra("Toque un gasto para corregirlo o borrarlo", 12, 0xFF9E9E9E, false));
        l.addView(tb, arriba(10));

        LinearLayout b2 = horizontal();
        Button pm = boton("📊 Por mes", 0xFF6A1B9A); pm.setTextSize(13); pm.setOnClickListener(v -> porMes());
        Button pa = boton("📅 Por año", 0xFF1565C0); pa.setTextSize(13); pa.setOnClickListener(v -> porAnio());
        Button ti = boton("🏪 Horarios", 0xFF2E7D32); ti.setTextSize(13); ti.setOnClickListener(v -> { verTiendas = true; pintar(); });
        b2.addView(pm, peso(1)); b2.addView(pa, peso(1)); b2.addView(ti, peso(1));
        l.addView(b2, ancho(52));
        LinearLayout b3 = horizontal();
        Button ex = boton("📗 Excel", 0xFF2E7D32); ex.setTextSize(13); ex.setOnClickListener(v -> exportarExcel());
        Button pd = boton("📄 PDF carta", 0xFFC62828); pd.setTextSize(13); pd.setOnClickListener(v -> exportarPdf());
        b3.addView(ex, peso(1)); b3.addView(pd, peso(1));
        l.addView(b3, ancho(52));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF6F1EA);
        sv.addView(l);
        setContentView(sv);
    }

    private static String miles(long v) { return java.text.NumberFormat.getIntegerInstance(ES).format(v); }

    private EditText campo(String pista, int tipo) {
        EditText e = new EditText(this);
        e.setHint(pista);
        e.setTextSize(16);
        e.setInputType(tipo);
        e.setSingleLine(true);
        return e;
    }

    private LinearLayout filaTabla(String[] c, boolean titulo, Gasto g) {
        LinearLayout f = horizontal();
        float[] pesos = {2.2f, 0.8f, 1.3f, 1.4f, 1.7f};
        for (int i = 0; i < 5; i++) {
            TextView t = letra(c[i], titulo ? 12 : 14, titulo ? 0xFF6D4C41 : 0xFF212121, titulo || i == 3);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (i == 2 || i == 3) t.setGravity(Gravity.END);
            t.setPadding(dp(2), dp(6), dp(4), dp(6));
            f.addView(t, new LinearLayout.LayoutParams(0, -2, pesos[i]));
        }
        if (g != null) f.setOnClickListener(v -> editar(g));
        return f;
    }

    private void anotar() {
        String art = eArt.getText().toString().trim();
        long valor = Util.valor(eValor.getText().toString());
        if (art.isEmpty() || valor <= 0) { aviso("Escriba el artículo y el valor"); return; }
        Gasto g = new Gasto();
        g.cuando = System.currentTimeMillis();
        g.art = art;
        g.cant = Math.max(0.01, Util.cantidad(eCant.getText().toString()));
        g.valor = valor;
        g.tienda = eTienda.getText().toString().trim();
        gastos.add(g);
        guardar();
        aviso("💾 " + art + ": " + Util.pesos(g.total()));
        pintar();
    }

    /** Se corrige poniendo el cursor donde quiera (campos normales). */
    private void editar(Gasto g) {
        LinearLayout l = vertical();
        l.setPadding(dp(20), dp(8), dp(20), 0);
        EditText a = campo("Artículo", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); a.setText(g.art);
        EditText c = campo("Cantidad", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); c.setText(Util.cant(g.cant));
        EditText v = campo("Valor", InputType.TYPE_CLASS_NUMBER); v.setText(String.valueOf(g.valor));
        EditText t = campo("Tienda", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS); t.setText(g.tienda);
        l.addView(a); l.addView(c); l.addView(v); l.addView(t);
        new AlertDialog.Builder(this).setTitle("✎ Corregir gasto").setView(l)
                .setPositiveButton("Guardar", (d, w) -> {
                    g.art = a.getText().toString().trim(); g.cant = Util.cantidad(c.getText().toString());
                    g.valor = Util.valor(v.getText().toString()); g.tienda = t.getText().toString().trim();
                    guardar(); pintar();
                })
                .setNeutralButton("🗑 Borrar", (d, w) -> { gastos.remove(g); guardar(); pintar(); })
                .setNegativeButton("Cancelar", null).show();
    }

    private void porMes() {
        String mes = dia.format(new Date()).substring(0, 7);
        Map<String, Long> m = new LinkedHashMap<>();
        for (Gasto g : gastos) {
            String d = dia.format(new Date(g.cuando));
            if (d.startsWith(mes)) m.merge(d, g.total(), Long::sum);
        }
        StringBuilder s = new StringBuilder();
        long tot = 0;
        SimpleDateFormat f = new SimpleDateFormat("EEE d", ES);
        for (Map.Entry<String, Long> e : m.entrySet()) {
            try { s.append(f.format(dia.parse(e.getKey()))).append(":   ").append(Util.pesos(e.getValue())).append('\n'); } catch (Exception ignored) {}
            tot += e.getValue();
        }
        s.append("\nTOTAL DEL MES: ").append(Util.pesos(tot));
        new AlertDialog.Builder(this).setTitle("📊 " + new SimpleDateFormat("MMMM yyyy", ES).format(new Date()))
                .setMessage(m.isEmpty() ? "Todavía no hay gastos este mes." : s.toString()).setPositiveButton("Listo", null).show();
    }

    private void porAnio() {
        String anio = dia.format(new Date()).substring(0, 4);
        long[] meses = new long[12];
        for (Gasto g : gastos) {
            String d = dia.format(new Date(g.cuando));
            if (d.startsWith(anio)) meses[Integer.parseInt(d.substring(5, 7)) - 1] += g.total();
        }
        StringBuilder s = new StringBuilder();
        long tot = 0;
        for (int i = 0; i < 12; i++) {
            if (meses[i] == 0) continue;
            s.append(RecordatoriosActivity.MESES[i]).append(":   ").append(Util.pesos(meses[i])).append('\n');
            tot += meses[i];
        }
        s.append("\nTOTAL DEL AÑO: ").append(Util.pesos(tot));
        new AlertDialog.Builder(this).setTitle("📅 Año " + anio).setMessage(tot == 0 ? "Todavía no hay gastos este año." : s.toString())
                .setPositiveButton("Listo", null).show();
    }

    private void exportarExcel() {
        StringBuilder b = new StringBuilder("﻿Fecha;Hora;Artículo;Cantidad;Valor;Total;Tienda\n");
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy;HH:mm", Locale.US);
        for (Gasto g : gastos) b.append(f.format(new Date(g.cuando))).append(';').append(g.art.replace(';', ',')).append(';')
                .append(Util.cant(g.cant)).append(';').append(g.valor).append(';').append(g.total()).append(';').append(g.tienda.replace(';', ',')).append('\n');
        try {
            Uri u = Util.guardarBytes(this, "Gastos_" + Util.fecha() + ".csv", "text/csv", b.toString().getBytes(StandardCharsets.UTF_8));
            aviso("📗 Guardado en Descargas/MiBarra/Gastos (se abre con Excel)");
            enviar(null, "text/csv", u);
        } catch (Exception e) { aviso("No se pudo guardar"); }
    }

    /** PDF tamaño carta (612 x 792) con los gastos del mes, letra negra nítida. */
    private void exportarPdf() {
        String mes = dia.format(new Date()).substring(0, 7);
        List<Gasto> lista = new ArrayList<>();
        for (Gasto g : gastos) if (dia.format(new Date(g.cuando)).startsWith(mes)) lista.add(g);
        PdfDocument doc = new PdfDocument();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        int y = 0, pag = 0;
        PdfDocument.Page pg = null;
        Canvas c = null;
        long tot = 0;
        SimpleDateFormat f = new SimpleDateFormat("dd/MM", Locale.US);
        float[] xs = {40, 90, 290, 340, 420, 500};
        for (int i = 0; i <= lista.size(); i++) {
            if (pg == null || y > 740) {
                if (pg != null) doc.finishPage(pg);
                pg = doc.startPage(new PdfDocument.PageInfo.Builder(612, 792, ++pag).create());
                c = pg.getCanvas();
                p.setTextSize(18); p.setTypeface(Typeface.DEFAULT_BOLD);
                c.drawText("Gastos hormiga · " + new SimpleDateFormat("MMMM yyyy", ES).format(new Date()), 40, 50, p);
                p.setTextSize(11);
                String[] h = {"Fecha", "Artículo", "Cant", "Valor", "Total", "Tienda"};
                for (int k = 0; k < 6; k++) c.drawText(h[k], xs[k], 80, p);
                c.drawLine(40, 85, 572, 85, p);
                p.setTypeface(Typeface.DEFAULT);
                y = 102;
            }
            if (i == lista.size()) break;
            Gasto g = lista.get(i);
            tot += g.total();
            String[] v = {f.format(new Date(g.cuando)), corto(g.art, 32), Util.cant(g.cant), miles(g.valor), miles(g.total()), corto(g.tienda, 14)};
            for (int k = 0; k < 6; k++) c.drawText(v[k], xs[k], y, p);
            y += 17;
        }
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(13);
        c.drawLine(40, y - 10, 572, y - 10, p);
        c.drawText("TOTAL DEL MES: " + Util.pesos(tot), 40, y + 8, p);
        doc.finishPage(pg);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            doc.writeTo(bo);
            Uri u = Util.guardarBytes(this, "Gastos_" + Util.fecha() + ".pdf", "application/pdf", bo.toByteArray());
            aviso("📄 PDF guardado en Descargas/MiBarra/Gastos");
            enviar(null, "application/pdf", u);
        } catch (Exception e) { aviso("No se pudo hacer el PDF"); }
        finally { doc.close(); }
    }

    private static String corto(String s, int n) { return s.length() > n ? s.substring(0, n - 1) + "…" : s; }

    // ---------------- Horarios de las tiendas ----------------

    private void pintarTiendas() {
        LinearLayout l = vertical();
        l.setBackgroundColor(0xFFF6F1EA);
        l.setPadding(dp(12), dp(10), dp(12), dp(24));
        l.addView(letra("🏪 Horarios de mis tiendas", 20, 0xFF263238, true));
        if (tiendas.isEmpty()) l.addView(letra("Todavía no hay tiendas. Toque ➕ para agregar la primera.", 15, 0xFF757575, false));
        for (Tienda t : tiendas) {
            LinearLayout c = vertical();
            c.setPadding(dp(14), dp(10), dp(14), dp(10));
            c.setBackground(caja(Color.WHITE, 0));
            c.addView(letra(t.nombre, 18, 0xFF212121, true));
            String e = estado(t);
            c.addView(letra(e, 15, e.startsWith("🟢") ? 0xFF2E7D32 : e.startsWith("🟠") ? 0xFFEF6C00 : 0xFFC62828, true));
            c.addView(letra("Abre: " + hora(t.abre) + "    Cierra: " + hora(t.cierra)
                    + "\nDescansa: " + (t.desDe < 0 ? "no descansa" : hora(t.desDe) + " a " + hora(t.desHa)), 15, 0xFF37474F, false));
            c.setOnClickListener(v -> editarTienda(t));
            l.addView(c, arriba(8));
        }
        Button n = boton("➕ Agregar tienda", 0xFF2E7D32);
        n.setOnClickListener(v -> editarTienda(null));
        l.addView(n, ancho(56));
        l.addView(letra("Toque una tienda para cambiar sus horas o borrarla", 13, 0xFF9E9E9E, false));
        Button vol = boton("🐜 Volver a gastos", 0xFFEF6C00);
        vol.setOnClickListener(v -> { verTiendas = false; pintar(); });
        l.addView(vol, ancho(52));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF6F1EA);
        sv.addView(l);
        setContentView(sv);
    }

    private void editarTienda(Tienda original) {
        Tienda t = new Tienda();
        if (original != null) { t.nombre = original.nombre; t.abre = original.abre; t.desDe = original.desDe; t.desHa = original.desHa; t.cierra = original.cierra; }
        LinearLayout l = vertical();
        l.setPadding(dp(20), dp(8), dp(20), 0);
        EditText nom = campo("Nombre de la tienda", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (t.nombre != null) nom.setText(t.nombre);
        l.addView(nom);
        String[] et = {"🟢 Abre", "🟠 Descansa desde", "🟠 Descansa hasta", "🔴 Cierra"};
        Button[] bs = new Button[4];
        for (int i = 0; i < 4; i++) {
            final int k = i;
            bs[i] = boton("", 0xFF455A64);
            bs[i].setTextSize(15);
            Runnable pintarB = () -> {
                int v = k == 0 ? t.abre : k == 1 ? t.desDe : k == 2 ? t.desHa : t.cierra;
                bs[k].setText(et[k] + ":  " + (v < 0 ? "no descansa" : hora(v)));
            };
            pintarB.run();
            bs[i].setOnClickListener(v -> {
                int actual = k == 0 ? t.abre : k == 1 ? t.desDe : k == 2 ? t.desHa : t.cierra;
                if (actual < 0) actual = 12 * 60;
                new TimePickerDialog(this, (tp, hh, mm) -> {
                    int val = hh * 60 + mm;
                    if (k == 0) t.abre = val; else if (k == 1) t.desDe = val; else if (k == 2) t.desHa = val; else t.cierra = val;
                    if (k == 1 && t.desHa < 0) t.desHa = val + 90;
                    for (int j = 0; j < 4; j++) { int v2 = j == 0 ? t.abre : j == 1 ? t.desDe : j == 2 ? t.desHa : t.cierra; bs[j].setText(et[j] + ":  " + (v2 < 0 ? "no descansa" : hora(v2))); }
                }, actual / 60, actual % 60, false).show();
            });
            l.addView(bs[i], ancho(48));
        }
        Button sin = boton("☕ No descansa", 0xFF8D6E63);
        sin.setTextSize(13);
        sin.setOnClickListener(v -> { t.desDe = -1; t.desHa = -1; bs[1].setText(et[1] + ":  no descansa"); bs[2].setText(et[2] + ":  no descansa"); });
        l.addView(sin, ancho(44));
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(original == null ? "➕ Nueva tienda" : "✎ " + original.nombre).setView(l)
                .setPositiveButton("Guardar", (d, w) -> {
                    t.nombre = nom.getText().toString().trim();
                    if (t.nombre.isEmpty()) { aviso("Escriba el nombre de la tienda"); return; }
                    if (original != null) tiendas.set(tiendas.indexOf(original), t); else tiendas.add(t);
                    guardar(); pintar();
                })
                .setNegativeButton("Cancelar", null);
        if (original != null) b.setNeutralButton("🗑 Borrar", (d, w) -> { tiendas.remove(original); guardar(); pintar(); });
        b.show();
    }

    // ---------------- Dictado: "dos gaseosas a 3500" ----------------

    @Override
    protected void onActivityResult(int r, int res, Intent d) {
        super.onActivityResult(r, res, d);
        if (r != VOZ || res != RESULT_OK || d == null) return;
        ArrayList<String> rs = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (rs == null || rs.isEmpty()) return;
        String t = rs.get(0).toLowerCase(ES).trim();
        String[] numeros = {"un", "una", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez"};
        int[] valores = {1, 1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        String cant = "1";
        String[] p = t.split("\\s+");
        int ini = 0;
        if (p.length > 0) {
            if (p[0].matches("\\d+([.,]\\d+)?")) { cant = p[0]; ini = 1; }
            else for (int i = 0; i < numeros.length; i++) if (p[0].equals(numeros[i])) { cant = String.valueOf(valores[i]); ini = 1; }
        }
        String resto = String.join(" ", java.util.Arrays.copyOfRange(p, ini, p.length));
        String valor = "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:a|en|por|de|\\$)\\s*\\$?\\s*([\\d.,]+)\\s*(mil)?\\s*(?:pesos)?$").matcher(resto);
        if (m.find()) {
            long v = Util.valor(m.group(1));
            if (m.group(2) != null) v *= 1000;
            valor = String.valueOf(v);
            resto = resto.substring(0, m.start()).trim();
        }
        if (!resto.isEmpty()) resto = Character.toUpperCase(resto.charAt(0)) + resto.substring(1);
        eArt.setText(resto);
        eCant.setText(cant);
        if (!valor.isEmpty()) eValor.setText(valor);
        eArt.setSelection(eArt.getText().length());
    }
}
