package co.jose.mibarra;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versión 18: pedir transporte.
 * Toque una ubicación en WhatsApp → "Abrir con Mi Barra · Transporte" → escoja Uber, DiDi, inDrive, Cabify…
 * Uber recibe el destino directo; a las demás se les copia el destino para pegarlo.
 * También guarda destinos fijos (Casa, Trabajo…).
 */
public class TransporteActivity extends Base {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView destinoTxt;
    private LinearLayout apps, guardados;
    private double lat = Double.NaN, lng = Double.NaN;
    private String nombre = "";

    /** Nombre, paquete, color. Se muestran solo las que estén instaladas (más "Agregar otra"). */
    private static final Object[][] APPS = {
            {"Uber", "com.ubercab", 0xFF000000},
            {"DiDi", "com.didiglobal.passenger", 0xFFFF7A00},
            {"inDrive", "sinet.startup.inDriver", 0xFF2E7D32},
            {"Cabify", "com.cabify.rider", 0xFF7145D6},
            {"Picap", "com.picap.pasajero", 0xFFE91E63},
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Transporte");
        LinearLayout l = vertical();
        l.setPadding(dp(12), dp(12), dp(12), dp(24));
        l.addView(titulo("🚕 Pedir transporte"));

        destinoTxt = new TextView(this);
        destinoTxt.setTextSize(17);
        destinoTxt.setTypeface(Typeface.DEFAULT_BOLD);
        destinoTxt.setPadding(dp(12), dp(12), dp(12), dp(12));
        destinoTxt.setBackground(Comun.fondo(this, 0xFFE3F2FD, 12));
        destinoTxt.setTextColor(0xFF0D47A1);
        l.addView(destinoTxt);

        LinearLayout f = horizontal();
        f.addView(herramienta("📋 Pegar ubicación", 0xFF00897B, Color.WHITE, true, v -> pegar()), peso(1));
        f.addView(herramienta("⭐ Guardar destino", 0xFFF9A825, Color.BLACK, true, v -> guardarDestino()), peso(1));
        l.addView(f);

        TextView t1 = new TextView(this);
        t1.setText("\nIr con:");
        t1.setTextSize(16);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(t1);
        apps = vertical();
        l.addView(apps);

        TextView nota = new TextView(this);
        nota.setText("Para ver cuál sale más barato, toque cada una y mire el precio. Uber recibe el destino solo; "
                + "en las otras el destino queda copiado: toque \"¿A dónde vas?\" y péguelo.");
        nota.setTextSize(13);
        nota.setTextColor(0xFF616161);
        nota.setPadding(0, dp(8), 0, 0);
        l.addView(nota);

        TextView t2 = new TextView(this);
        t2.setText("\nMis destinos");
        t2.setTextSize(16);
        t2.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(t2);
        guardados = vertical();
        l.addView(guardados);

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
        pintarApps();
        pintarGuardados();
        pintarDestino();
        recibir(getIntent());
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); recibir(i); }

    private void recibir(Intent i) {
        if (i == null) return;
        String txt = null;
        if (Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) txt = i.getData().toString();
        else if (Intent.ACTION_SEND.equals(i.getAction())) txt = i.getStringExtra(Intent.EXTRA_TEXT);
        if (txt != null) leerUbicacion(txt);
    }

    private void pegar() {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        CharSequence c = null;
        try { if (cm != null && cm.hasPrimaryClip()) c = cm.getPrimaryClip().getItemAt(0).coerceToText(this); } catch (Exception ignored) {}
        if (c == null || c.toString().trim().isEmpty()) { aviso("No hay ubicación copiada. En WhatsApp mantenga presionada la ubicación y cópiela."); return; }
        leerUbicacion(c.toString());
    }

    /** Saca latitud y longitud de: geo:…, enlaces de Google Maps, o enlaces cortos (los abre para ver a dónde van). */
    private void leerUbicacion(String txt) {
        if (sacarCoordenadas(txt)) { pintarDestino(); return; }
        Matcher m = Pattern.compile("https?://\\S+").matcher(txt);
        if (!m.find()) { aviso("No encontré una ubicación en eso"); return; }
        String url = m.group();
        destinoTxt.setText("Leyendo la ubicación…");
        new Thread(() -> {
            String fin = seguir(url);
            ui.post(() -> {
                if (fin != null && sacarCoordenadas(fin)) pintarDestino();
                else { destinoTxt.setText("No pude leer esa ubicación"); }
            });
        }).start();
    }

    private boolean sacarCoordenadas(String s) {
        try { s = URLDecoder.decode(s, "UTF-8"); } catch (Exception ignored) {}
        Pattern par = Pattern.compile("(-?\\d{1,2}\\.\\d{3,})\\s*,\\s*(-?\\d{1,3}\\.\\d{3,})");
        String[] pistas = {"@", "q=", "ll=", "query=", "destination=", "geo:", "loc:", ""};
        for (String p : pistas) {
            int k = p.isEmpty() ? 0 : s.indexOf(p);
            if (k < 0) continue;
            Matcher m = par.matcher(s.substring(k));
            if (m.find()) {
                lat = Double.parseDouble(m.group(1));
                lng = Double.parseDouble(m.group(2));
                Matcher n = Pattern.compile("\\(([^)]+)\\)").matcher(s);
                nombre = n.find() ? n.group(1) : "";
                return true;
            }
        }
        return false;
    }

    /** Sigue las redirecciones de un enlace corto (maps.app.goo.gl) y devuelve la dirección final. */
    private static String seguir(String url) {
        try {
            String u = url;
            for (int i = 0; i < 6; i++) {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)");
                int code = c.getResponseCode();
                String loc = c.getHeaderField("Location");
                if (code >= 300 && code < 400 && loc != null) { u = new URL(new URL(u), loc).toString(); continue; }
                return u;
            }
            return u;
        } catch (Exception e) { return null; }
    }

    private boolean hay() { return !Double.isNaN(lat); }

    private String coords() { return String.format(Locale.US, "%.6f,%.6f", lat, lng); }

    private void pintarDestino() {
        if (!hay()) { destinoTxt.setText("📍 Toque una ubicación en WhatsApp y escoja Mi Barra, o use Mis destinos"); return; }
        destinoTxt.setText("📍 Destino: " + (nombre.isEmpty() ? coords() : nombre + "\n" + coords()));
    }

    private boolean instalada(String pk) {
        try { getPackageManager().getPackageInfo(pk, 0); return true; } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private void pintarApps() {
        apps.removeAllViews();
        List<Object[]> lista = new ArrayList<>();
        for (Object[] a : APPS) if (instalada((String) a[1])) lista.add(a);
        // Apps que él agregó a mano
        for (String x : getSharedPreferences("barra", MODE_PRIVATE).getString("transporteExtra", "").split("\n")) {
            String[] p = x.split("\t");
            if (p.length == 2 && instalada(p[1])) lista.add(new Object[]{p[0], p[1], 0xFF455A64});
        }
        if (lista.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("No encontré apps de transporte instaladas (Uber, DiDi, inDrive, Cabify, Picap).");
            apps.addView(t);
        }
        LinearLayout fila = null;
        for (int i = 0; i < lista.size(); i++) {
            if (i % 2 == 0) { fila = horizontal(); apps.addView(fila); }
            Object[] a = lista.get(i);
            String n = (String) a[0], pk = (String) a[1];
            android.widget.Button bt = herramienta(n, (int) a[2], Color.WHITE, true, v -> irCon(n, pk));
            bt.setTextSize(18);
            LinearLayout.LayoutParams lp = peso(1);
            lp.height = dp(64);
            fila.addView(bt, lp);
        }
        apps.addView(herramienta("➕ Agregar otra app de transporte", 0xFFECEFF1, 0xFF263238, false, v -> agregarApp()),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    }

    private void irCon(String n, String pk) {
        if (!hay()) { aviso("Primero escoja el destino"); return; }
        if (pk.equals("com.ubercab")) {
            String u = "uber://?action=setPickup&pickup=my_location&dropoff[latitude]=" + lat + "&dropoff[longitude]=" + lng
                    + "&dropoff[nickname]=" + Uri.encode(nombre.isEmpty() ? "Destino" : nombre);
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); return; } catch (Exception ignored) {}
        }
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        String copia = nombre.isEmpty() ? coords() : nombre;
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("destino", copia));
        Intent i = getPackageManager().getLaunchIntentForPackage(pk);
        if (i == null) { aviso("No pude abrir " + n); return; }
        aviso("📋 Destino copiado. En " + n + " toque \"¿A dónde vas?\" y péguelo.");
        startActivity(i);
    }

    /** Escoger cualquier app instalada como app de transporte. */
    private void agregarApp() {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> todas = getPackageManager().queryIntentActivities(q, 0);
        List<String[]> ops = new ArrayList<>();
        for (android.content.pm.ResolveInfo r : todas)
            ops.add(new String[]{String.valueOf(r.loadLabel(getPackageManager())), r.activityInfo.packageName});
        ops.sort((a, c) -> a[0].compareToIgnoreCase(c[0]));
        String[] nombres = new String[ops.size()];
        for (int i = 0; i < ops.size(); i++) nombres[i] = ops.get(i)[0];
        new android.app.AlertDialog.Builder(this).setTitle("¿Cuál app de transporte?")
                .setItems(nombres, (d, w) -> {
                    android.content.SharedPreferences p = getSharedPreferences("barra", MODE_PRIVATE);
                    String ya = p.getString("transporteExtra", "");
                    p.edit().putString("transporteExtra", (ya.isEmpty() ? "" : ya + "\n") + ops.get(w)[0] + "\t" + ops.get(w)[1]).apply();
                    pintarApps();
                }).setNegativeButton("Cancelar", null).show();
    }

    // ---------------- Mis destinos ----------------

    private List<String[]> destinos() {
        List<String[]> r = new ArrayList<>();
        for (String x : getSharedPreferences("barra", MODE_PRIVATE).getString("transporteDestinos", "").split("\n")) {
            String[] p = x.split("\t");
            if (p.length == 3) r.add(p);
        }
        return r;
    }

    private void escribirDestinos(List<String[]> r) {
        StringBuilder s = new StringBuilder();
        for (String[] x : r) s.append(x[0]).append('\t').append(x[1]).append('\t').append(x[2]).append('\n');
        getSharedPreferences("barra", MODE_PRIVATE).edit().putString("transporteDestinos", s.toString()).apply();
    }

    private void guardarDestino() {
        if (!hay()) { aviso("Primero escoja una ubicación"); return; }
        EditText e = campoTexto("Nombre (Casa, Trabajo…)", nombre);
        new android.app.AlertDialog.Builder(this).setTitle("Guardar destino").setView(e)
                .setPositiveButton("Guardar", (d, w) -> {
                    String n = limpio(e.getText().toString());
                    if (n.isEmpty()) n = "Destino";
                    List<String[]> r = destinos();
                    r.add(new String[]{n, String.valueOf(lat), String.valueOf(lng)});
                    escribirDestinos(r);
                    pintarGuardados();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void pintarGuardados() {
        guardados.removeAllViews();
        List<String[]> r = destinos();
        if (r.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Aquí quedan Casa, Trabajo y los sitios que guarde.");
            t.setTextColor(0xFF757575);
            guardados.addView(t);
        }
        for (String[] x : r) {
            LinearLayout f = horizontal();
            f.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(this);
            t.setText("🏠 " + x[0]);
            t.setTextSize(17);
            t.setPadding(dp(6), dp(12), dp(6), dp(12));
            t.setOnClickListener(v -> {
                lat = Double.parseDouble(x[1]); lng = Double.parseDouble(x[2]); nombre = x[0];
                pintarDestino();
            });
            f.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView del = new TextView(this);
            del.setText("✕");
            del.setTextSize(16);
            del.setPadding(dp(12), dp(8), dp(12), dp(8));
            del.setOnClickListener(v -> { List<String[]> rr = destinos(); rr.removeIf(y -> y[0].equals(x[0]) && y[1].equals(x[1])); escribirDestinos(rr); pintarGuardados(); });
            f.addView(del);
            guardados.addView(f);
        }
    }
}
