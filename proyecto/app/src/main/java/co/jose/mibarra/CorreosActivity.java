package co.jose.mibarra;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Versión 36: Mis Correos. Todas sus cuentas de Gmail y Hotmail en una lista con nombres fáciles.
 * 📱 abre la app, 🌐 abre esa cuenta exacta en Chrome, ✉ escribe desde esa cuenta (con adjuntos y los destinatarios que ya usó).
 * La app no lee los correos: lo lleva a la cuenta correcta en un toque.
 */
public class CorreosActivity extends Base {

    private static final int REQ_ADJ = 61;
    private static final int ROJO_G = 0xFFE24B4A, AZUL_H = 0xFF185FA5;

    private SharedPreferences prefs;
    private final List<String[]> cuentas = new ArrayList<>(); // {nombre, correo}
    private LinearLayout lista, panel;
    private EditText buscar;
    private String filtro = "todas";
    private TextView fTodas, fGmail, fHot, titulo;
    // escribir
    private String[] desde;
    private AutoCompleteTextView para;
    private EditText asunto, mensaje;
    private final List<Uri> adjuntos = new ArrayList<>();
    private TextView adjTxt;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Mis Correos");
        prefs = getSharedPreferences("barra", MODE_PRIVATE);
        cargar();
        LinearLayout l = vertical();
        l.setPadding(dp(10), dp(10), dp(10), dp(24));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        titulo = titulo("📧 Mis Correos");
        top.addView(titulo, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(herramienta("➕ Cuenta", 0xFF0F6E56, Color.WHITE, true, v -> editar(-1)));
        l.addView(top);

        buscar = new EditText(this);
        buscar.setHint("🔍 Buscar cuenta (ej. trabajo, hija)");
        buscar.setSingleLine(true);
        buscar.setTextSize(16);
        buscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable e) { pintar(); }
        });
        l.addView(buscar);

        LinearLayout fl = horizontal();
        fTodas = herramienta("Todas", 0xFF444441, Color.WHITE, true, v -> { filtro = "todas"; pintar(); });
        fGmail = herramienta("Gmail", ROJO_G, Color.WHITE, true, v -> { filtro = "gmail"; pintar(); });
        fHot = herramienta("Hotmail", AZUL_H, Color.WHITE, true, v -> { filtro = "hotmail"; pintar(); });
        fl.addView(fTodas, peso(1));
        fl.addView(fGmail, peso(1));
        fl.addView(fHot, peso(1));
        l.addView(fl);

        lista = vertical();
        l.addView(lista);
        panel = vertical();
        l.addView(panel);

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
        pintar();
        if (cuentas.isEmpty()) editar(-1);
    }

    // ---------------- guardar cuentas

    private void cargar() {
        cuentas.clear();
        for (String x : prefs.getString("correos", "").split("\n")) {
            String[] p = x.split("\t");
            if (p.length == 2 && p[1].contains("@")) cuentas.add(p);
        }
    }

    private void guardar() {
        StringBuilder sb = new StringBuilder();
        for (String[] c : cuentas) sb.append(c[0].replace("\t", " ")).append('\t').append(c[1].trim()).append('\n');
        prefs.edit().putString("correos", sb.toString()).apply();
    }

    private static boolean esGmail(String correo) {
        String c = correo.toLowerCase(Locale.ROOT);
        return c.endsWith("@gmail.com") || c.endsWith("@googlemail.com");
    }

    private static boolean esMicrosoft(String correo) {
        String c = correo.toLowerCase(Locale.ROOT);
        return c.contains("@hotmail.") || c.contains("@outlook.") || c.contains("@live.") || c.contains("@msn.");
    }

    // ---------------- lista

    private void pintar() {
        lista.removeAllViews();
        int ng = 0, nh = 0;
        for (String[] c : cuentas) { if (esGmail(c[1])) ng++; else nh++; }
        titulo.setText("📧 Mis Correos · " + cuentas.size());
        fTodas.setText("Todas · " + cuentas.size());
        fGmail.setText("Gmail · " + ng);
        fHot.setText("Hotmail · " + nh);
        fTodas.setAlpha("todas".equals(filtro) ? 1f : 0.55f);
        fGmail.setAlpha("gmail".equals(filtro) ? 1f : 0.55f);
        fHot.setAlpha("hotmail".equals(filtro) ? 1f : 0.55f);
        String q = buscar.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < cuentas.size(); i++) {
            final int k = i;
            String[] c = cuentas.get(i);
            boolean g = esGmail(c[1]);
            if ("gmail".equals(filtro) && !g) continue;
            if ("hotmail".equals(filtro) && g) continue;
            if (!q.isEmpty() && !(c[0] + " " + c[1]).toLowerCase(Locale.ROOT).contains(q)) continue;
            LinearLayout fila = horizontal();
            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setPadding(0, dp(6), 0, dp(6));
            View color = new View(this);
            color.setBackground(Comun.fondo(this, g ? ROJO_G : AZUL_H, 3));
            fila.addView(color, new LinearLayout.LayoutParams(dp(7), dp(44)));
            TextView t = new TextView(this);
            t.setText(c[0] + "\n" + c[1]);
            t.setTextSize(15);
            t.setTextColor(Color.BLACK);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(dp(8), 0, dp(4), 0);
            t.setOnLongClickListener(v -> { editar(k); return true; });
            fila.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            fila.addView(chico("📱", g ? 0xFFFCEBEB : 0xFFE6F1FB, v -> abrirApp(c[1])));
            fila.addView(chico("🌐", 0xFFE6F1FB, v -> abrirChrome(c[1])));
            fila.addView(chico("✉", 0xFFEAF3DE, v -> escribirDesde(c)));
            lista.addView(fila);
            View linea = new View(this);
            linea.setBackgroundColor(0xFFE0E0E0);
            lista.addView(linea, new LinearLayout.LayoutParams(-1, 1));
        }
        TextView nota = new TextView(this);
        nota.setText("Deje el dedo sobre una cuenta para cambiarle el nombre o borrarla.");
        nota.setTextSize(13);
        nota.setTextColor(0xFF444441);
        nota.setPadding(0, dp(6), 0, dp(6));
        lista.addView(nota);
    }

    private TextView chico(String s, int fondo, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(22);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Comun.fondo(this, fondo, 10));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(50), dp(50));
        lp.setMarginStart(dp(4));
        t.setLayoutParams(lp);
        return t;
    }

    private void editar(final int i) {
        LinearLayout f = vertical();
        f.setPadding(dp(16), dp(8), dp(16), 0);
        final EditText nom = new EditText(this);
        nom.setHint("Nombre fácil (Trabajo, Clientes, Hija…)");
        final EditText cor = new EditText(this);
        cor.setHint("correo@gmail.com");
        cor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (i >= 0) { nom.setText(cuentas.get(i)[0]); cor.setText(cuentas.get(i)[1]); }
        f.addView(nom);
        f.addView(cor);
        AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle(i >= 0 ? "Cambiar cuenta" : "➕ Agregar cuenta").setView(f)
                .setPositiveButton("Guardar", (x, w) -> {
                    String n = nom.getText().toString().trim(), c = cor.getText().toString().trim();
                    if (!c.contains("@")) { aviso("Escriba el correo completo, con @"); return; }
                    if (n.isEmpty()) n = c.substring(0, c.indexOf('@'));
                    if (i >= 0) cuentas.set(i, new String[]{n, c}); else cuentas.add(new String[]{n, c});
                    guardar();
                    pintar();
                })
                .setNegativeButton("Cancelar", null);
        if (i >= 0) d.setNeutralButton("🗑 Borrar", (x, w) -> { cuentas.remove(i); guardar(); pintar(); });
        d.show();
    }

    // ---------------- abrir

    private boolean instalada(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    private void abrirApp(String correo) {
        String pkg = esGmail(correo) ? "com.google.android.gm" : "com.microsoft.office.outlook";
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null && !esGmail(correo)) i = getPackageManager().getLaunchIntentForPackage("com.google.android.gm");
        if (i == null) { abrirChrome(correo); return; }
        aviso("Si abre otra cuenta, toque su foto arriba a la derecha y escoja " + correo);
        startActivity(i);
    }

    private void abrirChrome(String correo) {
        String e = Uri.encode(correo);
        String url = esGmail(correo) || !esMicrosoft(correo)
                ? "https://mail.google.com/mail/?authuser=" + e
                : "https://outlook.live.com/mail/?login_hint=" + e;
        web(url);
    }

    private void web(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (instalada("com.android.chrome")) i.setPackage("com.android.chrome");
        try { startActivity(i); } catch (Exception ex) {
            i.setPackage(null);
            try { startActivity(i); } catch (Exception ignored) { aviso("No hay navegador para abrir el correo"); }
        }
    }

    // ---------------- escribir

    private void escribirDesde(String[] c) {
        desde = c;
        adjuntos.clear();
        panel.removeAllViews();
        LinearLayout p = vertical();
        p.setPadding(dp(10), dp(10), dp(10), dp(10));
        p.setBackground(Comun.fondo(this, 0xFFEAF3DE, 12));
        TextView t = new TextView(this);
        t.setText("✉ Escribir desde: " + c[0] + "\n" + c[1]);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF27500A);
        p.addView(t);
        para = new AutoCompleteTextView(this);
        para.setHint("Para: correo de la persona");
        para.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        para.setThreshold(1);
        para.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, recientes()));
        p.addView(para);
        asunto = new EditText(this);
        asunto.setHint("Asunto");
        p.addView(asunto);
        mensaje = new EditText(this);
        mensaje.setHint("🎤 Dicte o escriba el mensaje…");
        mensaje.setMinLines(5);
        mensaje.setGravity(Gravity.TOP);
        p.addView(mensaje);
        adjTxt = new TextView(this);
        adjTxt.setTextSize(13);
        adjTxt.setTextColor(0xFF27500A);
        p.addView(adjTxt);
        LinearLayout f = horizontal();
        f.addView(herramienta("📎 Adjuntar", 0xFFFFFFFF, 0xFF27500A, true, v -> adjuntar()), peso(1));
        f.addView(herramienta("Enviar ➜", 0xFF0F6E56, Color.WHITE, true, v -> enviar()), peso(1));
        p.addView(f);
        f.addView(herramienta("✕", 0xFF888780, Color.WHITE, true, v -> panel.removeAllViews()), peso(0.5f));
        panel.addView(p);
        pintarAdj();
        para.requestFocus();
        panel.post(() -> { View sv = (View) panel.getParent().getParent(); if (sv instanceof ScrollView) ((ScrollView) sv).smoothScrollTo(0, panel.getTop()); });
    }

    private List<String> recientes() {
        List<String> r = new ArrayList<>();
        for (String x : prefs.getString("correosPara", "").split("\n")) if (x.contains("@")) r.add(x);
        return r;
    }

    private void recordar(String correo) {
        List<String> r = recientes();
        for (String x : correo.split("[,;\\s]+")) {
            if (!x.contains("@")) continue;
            r.remove(x);
            r.add(0, x);
        }
        while (r.size() > 200) r.remove(r.size() - 1);
        prefs.edit().putString("correosPara", String.join("\n", r)).apply();
    }

    private void adjuntar() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(i, REQ_ADJ); } catch (Exception e) { aviso("No pude abrir sus archivos"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_ADJ || res != RESULT_OK || data == null) return;
        if (data.getClipData() != null) {
            ClipData cd = data.getClipData();
            for (int i = 0; i < cd.getItemCount(); i++) adjuntos.add(cd.getItemAt(i).getUri());
        } else if (data.getData() != null) adjuntos.add(data.getData());
        pintarAdj();
    }

    private void pintarAdj() {
        if (adjTxt == null) return;
        adjTxt.setText(adjuntos.isEmpty() ? "" : "📎 " + adjuntos.size() + (adjuntos.size() == 1 ? " archivo adjunto" : " archivos adjuntos"));
    }

    private void enviar() {
        if (desde == null) return;
        String to = para.getText().toString().trim();
        String su = asunto.getText().toString();
        String body = mensaje.getText().toString();
        if (!to.contains("@")) { aviso("Falta el correo de la persona"); return; }
        recordar(to);
        if (!adjuntos.isEmpty()) {
            // con adjuntos va por la app (Chrome no deja adjuntar desde un enlace)
            Intent i = new Intent(adjuntos.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_EMAIL, to.split("[,;\\s]+"));
            i.putExtra(Intent.EXTRA_SUBJECT, su);
            i.putExtra(Intent.EXTRA_TEXT, body);
            if (adjuntos.size() == 1) i.putExtra(Intent.EXTRA_STREAM, adjuntos.get(0));
            else i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(adjuntos));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String pkg = esGmail(desde[1]) ? "com.google.android.gm" : "com.microsoft.office.outlook";
            if (instalada(pkg)) i.setPackage(pkg);
            aviso("Revise arriba que diga De: " + desde[1] + " y toque enviar");
            try { startActivity(i); } catch (Exception e) {
                i.setPackage(null);
                try { startActivity(Intent.createChooser(i, "Enviar con")); } catch (Exception ignored) {}
            }
            return;
        }
        // sin adjuntos: se abre ya escrito en esa cuenta exacta
        String url;
        if (esGmail(desde[1]) || !esMicrosoft(desde[1]))
            url = "https://mail.google.com/mail/?authuser=" + Uri.encode(desde[1]) + "&view=cm&fs=1&to=" + Uri.encode(to)
                    + "&su=" + Uri.encode(su) + "&body=" + Uri.encode(body);
        else
            url = "https://outlook.live.com/mail/deeplink/compose?login_hint=" + Uri.encode(desde[1]) + "&to=" + Uri.encode(to)
                    + "&subject=" + Uri.encode(su) + "&body=" + Uri.encode(body);
        aviso("Se abre listo para enviar desde " + desde[1]);
        web(url);
    }
}
