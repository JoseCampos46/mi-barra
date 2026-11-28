package co.jose.mibarra;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Versión 34 · 🧭 GPS: como Google Maps, pero con lo nuestro.
 * Tres líneas (dirección, barrio, ciudad); resultados completos (en rojo si les falta barrio o ciudad);
 * mapa con la ruta, minutos y kilómetros; Ir, Descargar ruta, Grabar recorrido, Devolverme, Me salí aquí, WhatsApp;
 * direcciones buscadas y rutas guardadas, cada una con su foto del mapa.
 */
public class GpsActivity extends Base {

    private final Handler h = new Handler(Looper.getMainLooper());
    private EditText eDir, eBarrio, eCiudad;
    private LinearLayout resultados, listas;
    private MapaRuta mapa;
    private TextView resumen;

    // destino escogido y la ruta calculada
    private double dLat = Double.NaN, dLon;
    private String dNombre = "", dLugar = "";
    /** Versión 35: cuando nuestro buscador no halla el número exacto, Google Maps busca con este texto. */
    private String textoGoogle = "";
    private final List<double[]> ruta = new ArrayList<>();
    private double km, minutos;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = vertical();
        int p = dp(10);
        l.setPadding(p, p, p, p);
        l.setBackgroundColor(0xFFF0F1F4);

        SharedPreferencesCiudad();
        eDir = campo(l, "🏠 Dirección (Ej: Calle 20 # 25-10)", "");
        eBarrio = campo(l, "🏘 Barrio (Ej: Zamorano)", "");
        eCiudad = campo(l, "🏙 Ciudad (Ej: Palmira)", getSharedPreferences("barra", MODE_PRIVATE).getString("ultimaCiudad", ""));
        Button buscar = boton("🔎  Buscar", 0xFF1565C0);
        buscar.setTextSize(18);
        buscar.setOnClickListener(v -> buscar());
        l.addView(buscar, ancho(54));
        // Versión 35: Google Maps sí encuentra todas las direcciones; y guardar el punto exacto donde uno está
        LinearLayout fg = horizontal();
        Button bg = boton("🗺  Buscar en Google Maps", 0xFF00897B);
        bg.setTextSize(14);
        bg.setOnClickListener(v -> buscarEnGoogle());
        LinearLayout.LayoutParams pg = peso(1);
        pg.setMargins(0, dp(4), dp(3), 0);
        fg.addView(bg, pg);
        Button gp = boton("💾  Guardar donde estoy", 0xFF6A1B9A);
        gp.setTextSize(14);
        gp.setOnClickListener(v -> guardarDondeEstoy());
        LinearLayout.LayoutParams pp = peso(1);
        pp.setMargins(dp(3), dp(4), 0, 0);
        fg.addView(gp, pp);
        l.addView(fg, ancho(54));

        resultados = vertical();
        l.addView(resultados);

        mapa = new MapaRuta(this);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260));
        mp.topMargin = dp(6);
        l.addView(mapa, mp);
        resumen = new TextView(this);
        resumen.setTextSize(17);
        resumen.setTypeface(Typeface.DEFAULT_BOLD);
        resumen.setTextColor(Color.BLACK);
        resumen.setPadding(0, dp(4), 0, dp(4));
        resumen.setText("Escriba la dirección, el barrio y la ciudad, y toque Buscar.");
        l.addView(resumen);

        // los seis botones grandes
        String[][] bs = {{"▶  Ir", "#1565C0"}, {"⬇  Descargar ruta", "#00897B"}, {"🛣  Grabar recorrido", "#546E7A"},
                {"🔙  Devolverme", "#6A1B9A"}, {"📍  Me salí aquí", "#EF6C00"}, {"💬  WhatsApp", "#25A244"}};
        Runnable[] acc = {this::ir, this::descargar, this::grabar, this::devolverme, this::meSali, this::whatsapp};
        for (int f = 0; f < 2; f++) {
            LinearLayout fila = horizontal();
            for (int k = 0; k < 3; k++) {
                int i = f * 3 + k;
                Button bt = boton(bs[i][0], Color.parseColor(bs[i][1]));
                bt.setTextSize(14);
                bt.setOnClickListener(v -> acc[i].run());
                LinearLayout.LayoutParams lp = peso(1);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                fila.addView(bt, lp);
            }
            l.addView(fila, ancho(62));
        }

        listas = vertical();
        l.addView(listas);

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
        pintarListas();
        recibirCompartido(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        recibirCompartido(i);
    }

    private void SharedPreferencesCiudad() { /* la ciudad queda recordada en "ultimaCiudad" */ }

    private EditText campo(LinearLayout l, String pista, String valor) {
        EditText e = new EditText(this);
        e.setHint(pista);
        e.setText(valor);
        e.setTextSize(18);
        e.setSingleLine(true);
        e.setTextColor(Color.BLACK);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp(1), 0xFFB0B0B0);
        g.setCornerRadius(dp(8));
        e.setBackground(g);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(5);
        l.addView(e, lp);
        return e;
    }

    // ================= Buscar =================

    private void buscar() {
        String dir = normalizar(eDir.getText().toString().trim()), bar = eBarrio.getText().toString().trim(), ciu = eCiudad.getText().toString().trim();
        if (dir.isEmpty() && bar.isEmpty()) { aviso("Escriba la dirección o el barrio"); return; }
        if (!dir.isEmpty() && !dir.equals(eDir.getText().toString().trim())) eDir.setText(dir);   // así queda escrita como la entiende el mapa
        if (!ciu.isEmpty()) getSharedPreferences("barra", MODE_PRIVATE).edit().putString("ultimaCiudad", ciu).apply();
        String barrio = bar.isEmpty() ? "" : (plano(bar).startsWith("barrio") ? bar : "Barrio " + bar);
        textoGoogle = unir(dir, bar, ciu);
        List<String> consultas = new ArrayList<>();
        consultas.add(unir(dir, barrio, ciu) + ", Colombia");
        if (!dir.isEmpty() && !ciu.isEmpty()) consultas.add(unir(dir, "", ciu) + ", Colombia");
        if (!dir.isEmpty() && !bar.isEmpty()) consultas.add(unir(dir, bar, "") + ", Colombia");
        resultados.removeAllViews();
        resultados.addView(texto("Buscando…", 15, Color.DKGRAY));
        final String dirF = dir, barF = bar, ciuF = ciu;
        new Thread(() -> {
            List<Address> rs = new ArrayList<>();
            try {
                Geocoder g = new Geocoder(this, new Locale("es", "CO"));
                for (String q : consultas) {
                    List<Address> x = g.getFromLocationName(q, 6);
                    if (x == null) continue;
                    for (Address a : x) {
                        boolean repetida = false;
                        for (Address y : rs) if (Math.abs(y.getLatitude() - a.getLatitude()) < 1e-5 && Math.abs(y.getLongitude() - a.getLongitude()) < 1e-5) repetida = true;
                        if (!repetida) rs.add(a);
                    }
                }
            } catch (Exception ignored) {}
            runOnUiThread(() -> mostrarResultados(rs, dirF, barF, ciuF));
        }, "buscar").start();
    }

    private static String unir(String a, String b, String c) {
        StringBuilder s = new StringBuilder();
        for (String x : new String[]{a, b, c}) if (x != null && !x.isEmpty()) s.append(s.length() > 0 ? ", " : "").append(x);
        return s.toString();
    }

    private static final java.util.regex.Pattern DIR = java.util.regex.Pattern.compile(
            "^\\s*(carrera|carr|cra|kra|kr|cr|era|k|calle|cll|cl|avenida|av|diagonal|dg|transversal|trans|tv)\\.?\\s*" +
            "(\\d+)\\s*([a-z])?\\s*(bis)?\\s*(?:#|no\\.?|n°|nº|nro\\.?|numero|número)?\\s*(\\d+)\\s*([a-z])?\\s*(?:-|–)?\\s*(\\d+)?\\s*(.*)$",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Versión 35: entiende la dirección como la escriba: «Era 38 19 a 30», «cra 38 #19A-30», «K 38 19a 30» → «Carrera 38 # 19A-30». */
    static String normalizar(String d) {
        java.util.regex.Matcher m = DIR.matcher(d);
        if (!m.find()) return d;
        String t = m.group(1).toLowerCase(Locale.ROOT);
        String tipo = t.startsWith("c") && t.contains("l") ? "Calle" : t.startsWith("a") ? "Avenida" : t.startsWith("d") ? "Diagonal"
                : t.startsWith("t") ? "Transversal" : "Carrera";
        StringBuilder s = new StringBuilder(tipo).append(' ').append(m.group(2));
        if (m.group(3) != null) s.append(m.group(3).toUpperCase(Locale.ROOT));
        if (m.group(4) != null) s.append(" Bis");
        s.append(" # ").append(m.group(5));
        if (m.group(6) != null) s.append(m.group(6).toUpperCase(Locale.ROOT));
        if (m.group(7) != null) s.append('-').append(m.group(7));
        String resto = m.group(8) == null ? "" : m.group(8).trim();
        if (!resto.isEmpty()) s.append(' ').append(resto);
        return s.toString();
    }

    /** Los números de la dirección escrita (ej. «38», «19a», «30») para revisar que el resultado sea el mismo sitio. */
    private static String[] numeros(String dir) {
        java.util.regex.Matcher m = DIR.matcher(dir);
        if (!m.find()) return null;
        String a = m.group(2) + (m.group(3) == null ? "" : m.group(3));
        String b = m.group(5) + (m.group(6) == null ? "" : m.group(6));
        return new String[]{a.toLowerCase(Locale.ROOT), b.toLowerCase(Locale.ROOT)};
    }

    private static boolean completa(Address a) {
        return a.getSubLocality() != null && (a.getLocality() != null || a.getSubAdminArea() != null);
    }

    private static String barrioCiudad(Address a) {
        StringBuilder s = new StringBuilder();
        if (a.getSubLocality() != null) s.append("Barrio ").append(a.getSubLocality());
        String ciudad = a.getLocality() != null ? a.getLocality() : a.getSubAdminArea();
        if (ciudad != null) s.append(s.length() > 0 ? " · " : "").append(ciudad);
        return s.length() == 0 ? "(sin barrio ni ciudad)" : s.toString();
    }

    private void mostrarResultados(List<Address> rs, String dirEscrita, String barrioPedido, String ciudadPedida) {
        resultados.removeAllViews();
        String[] nums = numeros(dirEscrita);
        String lugarEscrito = unir(barrioPedido.isEmpty() ? "" : "Barrio " + barrioPedido, ciudadPedida, "");
        Address mejor = null;
        int cuantos = 0;
        for (Address a : rs) {
            if (cuantos >= 6) break;
            cuantos++;
            String linea = a.getMaxAddressLineIndex() >= 0 ? a.getAddressLine(0) : dirEscrita;
            String dir = linea.split(",")[0].trim();
            String lugar = barrioCiudad(a);
            String ciudad = a.getLocality() != null ? a.getLocality() : a.getSubAdminArea() != null ? a.getSubAdminArea() : "";
            String pl = plano(linea).replace(" ", "");
            boolean numeroOk = nums == null || (pl.contains(nums[0]) && pl.contains(nums[1]));
            boolean barrioOk = barrioPedido.isEmpty() || a.getSubLocality() == null || plano(a.getSubLocality()).contains(plano(barrioPedido))
                    || plano(barrioPedido).contains(plano(a.getSubLocality()));
            boolean ciudadOk = ciudadPedida.isEmpty() || ciudad.isEmpty() || plano(ciudad).contains(plano(ciudadPedida)) || plano(ciudadPedida).contains(plano(ciudad));
            boolean bien = completa(a) && numeroOk && barrioOk && ciudadOk;
            String aviso = bien ? "  ✔" : !ciudadOk ? "\n⚠ Es de otra ciudad" : !numeroOk ? "\n⚠ No encontré el número exacto (solo la calle)"
                    : !barrioOk ? "\n⚠ No es el barrio que escribió" : "\n⚠ Dirección incompleta";
            TextView t = texto(dir + "\n" + lugar + aviso, 16, bien ? Color.BLACK : 0xFFD32F2F);
            GradientDrawable g = new GradientDrawable();
            g.setColor(bien ? Color.WHITE : 0xFFFFEBEE);
            g.setCornerRadius(dp(8));
            t.setBackground(g);
            t.setPadding(dp(10), dp(6), dp(10), dp(6));
            t.setOnClickListener(v -> escoger(a.getLatitude(), a.getLongitude(), dir, lugar));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            resultados.addView(t, lp);
            if (bien && mejor == null) mejor = a;
        }
        if (mejor != null) {
            // Versión 35: si hay un resultado bueno, se escoge solo y sale la ruta de una vez
            String linea = mejor.getMaxAddressLineIndex() >= 0 ? mejor.getAddressLine(0) : dirEscrita;
            escoger(mejor.getLatitude(), mejor.getLongitude(), linea.split(",")[0].trim(), barrioCiudad(mejor));
            return;
        }
        // Nuestro buscador no halló el sitio exacto: se guarda lo escrito y Google Maps lo busca
        dLat = Double.NaN;
        ruta.clear();
        mapa.poner(ruta, Double.NaN, 0, Double.NaN, 0);
        dNombre = dirEscrita.isEmpty() ? barrioPedido : dirEscrita;
        dLugar = lugarEscrito;
        guardarBuscada(dNombre, dLugar, Double.NaN, Double.NaN);
        pintarListas();
        resultados.addView(texto(rs.isEmpty() ? "No encontré esa dirección en el buscador del celular." : "Ninguno es el sitio exacto (los de arriba en rojo).", 15, 0xFFD32F2F), 0);
        resumen.setText("⚠ " + dNombre + (dLugar.isEmpty() ? "" : " · " + dLugar)
                + "\n▶ Ir le pregunta a Google Maps, que sí la encuentra. Para traer la ruta aquí: 🗺 Buscar en Google Maps → Compartir → «Mi Barra GPS».");
    }

    private static String plano(String s) {
        return java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Escoge el destino: lo guarda en «buscadas» y calcula la ruta desde donde está usted. */
    private void escoger(double lat, double lon, String nombre, String lugar) {
        dLat = lat; dLon = lon; dNombre = nombre; dLugar = lugar;
        textoGoogle = unir(nombre, lugar.replace("Barrio ", "").replace(" · ", ", "), "");
        resultados.removeAllViews();
        guardarBuscada(nombre, lugar, lat, lon);
        pintarListas();
        calcularRuta();
    }

    // ================= Ruta =================

    private Location dondeEstoy() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return null;
        }
        LocationManager lm = getSystemService(LocationManager.class);
        Location mejor = null;
        try {
            for (String pr : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                Location x = lm.getLastKnownLocation(pr);
                if (x != null && (mejor == null || x.getTime() > mejor.getTime())) mejor = x;
            }
        } catch (SecurityException ignored) {}
        return mejor;
    }

    private void calcularRuta() {
        Location yo = dondeEstoy();
        ruta.clear();
        mapa.poner(ruta, Double.NaN, 0, dLat, dLon);
        if (yo == null) { resumen.setText("📍 " + dNombre + " · " + dLugar + "\n(Prenda la ubicación para ver la ruta desde donde está)"); return; }
        resumen.setText("Calculando la ruta…");
        final double oLat = yo.getLatitude(), oLon = yo.getLongitude();
        new Thread(() -> {
            List<double[]> pts = new ArrayList<>();
            double[] dm = {0, 0};
            boolean ok = false;
            try {
                String u = String.format(Locale.US, "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                        oLon, oLat, dLon, dLat);
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                c.setRequestProperty("User-Agent", "MiBarra/34 (app personal Android)");
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                String js;
                try (java.io.InputStream in = c.getInputStream()) { js = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
                JSONObject r = new JSONObject(js).getJSONArray("routes").getJSONObject(0);
                dm[0] = r.getDouble("distance") / 1000.0;
                dm[1] = r.getDouble("duration") / 60.0;
                JSONArray co = r.getJSONObject("geometry").getJSONArray("coordinates");
                for (int i = 0; i < co.length(); i++) { JSONArray x = co.getJSONArray(i); pts.add(new double[]{x.getDouble(1), x.getDouble(0)}); }
                ok = true;
            } catch (Exception ignored) {}
            final boolean listo = ok;
            runOnUiThread(() -> {
                if (!listo) {
                    resumen.setText("📍 " + dNombre + " · " + dLugar + "\n(Sin internet no pude calcular la ruta; ▶ Ir abre Google Maps)");
                    mapa.poner(ruta, oLat, oLon, dLat, dLon);
                    return;
                }
                ruta.clear();
                ruta.addAll(pts);
                km = dm[0];
                minutos = dm[1];
                resumen.setText(String.format(Locale.US, "📍 %s · %s\n⏱ %s · %.1f km", dNombre, dLugar, tiempo(minutos), km));
                mapa.poner(ruta, oLat, oLon, dLat, dLon);
            });
        }, "ruta").start();
    }

    private static String tiempo(double min) {
        int m = (int) Math.round(min);
        return m < 60 ? m + " min" : (m / 60) + " h " + (m % 60) + " min";
    }

    private boolean hayDestino() {
        if (Double.isNaN(dLat)) {
            aviso(textoGoogle.isEmpty() ? "Primero escriba la dirección y toque Buscar"
                    : "Ese sitio no lo tengo exacto: toque 🗺 Buscar en Google Maps y compártalo a «Mi Barra GPS»");
            return false;
        }
        return true;
    }

    private void ir() {
        if (Double.isNaN(dLat) && !textoGoogle.isEmpty()) {
            // Versión 35: Google Maps busca la dirección tal como usted la escribió
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(textoGoogle + ", Colombia")));
            i.setPackage("com.google.android.apps.maps");
            try { startActivity(i); } catch (ActivityNotFoundException e) { buscarEnGoogle(); }
            return;
        }
        if (!hayDestino()) return;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "google.navigation:q=%.6f,%.6f", dLat, dLon)));
        i.setPackage("com.google.android.apps.maps");
        try { startActivity(i); }
        catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US,
                    "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f", dLat, dLon))));
        }
    }

    /** Guarda el trazado de calles como un recorrido: así sirve sin internet para vigilar, devolverse y compartir. */
    private File descargarArchivo() {
        if (ruta.size() < 2) return null;
        try {
            Recorrido r = new Recorrido();
            r.nombre = "GPS · " + dNombre + " · " + dLugar;
            for (double[] q : ruta) r.puntos.add(new double[]{q[0], q[1], 0, 0});
            r.boyas.add(new String[]{String.format(Locale.US, "%.6f", dLat), String.format(Locale.US, "%.6f", dLon), "Parada", dNombre, dNombre + " · " + dLugar});
            File f = new File(RecorridoService.carpeta(this), "gps_" + System.currentTimeMillis() + ".txt");
            r.guardar(f);
            Direcciones.copiarRuta(this, f, r.nombre);
            return f;
        } catch (Exception e) { return null; }
    }

    private void descargar() {
        if (!hayDestino()) return;
        if (ruta.size() < 2) { aviso("Todavía no hay ruta: necesito internet una vez para bajarla"); calcularRuta(); return; }
        File f = descargarArchivo();
        if (f == null) { aviso("No se pudo guardar la ruta"); return; }
        aviso("⬇ Ruta guardada: ya sirve sin internet (en Rutas guardadas)");
        pintarListas();
    }

    /** Graba el recorrido; si hay ruta, además la vigila (aviso por voz y punto marcado si se sale). */
    private void grabar() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        try {
            android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName()))
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {}
        Intent i = new Intent(this, RecorridoService.class).setAction(RecorridoService.INICIAR)
                .putExtra("limite", getSharedPreferences("barra", MODE_PRIVATE).getInt("limiteVel", 0))
                .putExtra("descanso", getSharedPreferences("barra", MODE_PRIVATE).getInt("descansoCada", 120))
                .putExtra("descansoDur", getSharedPreferences("barra", MODE_PRIVATE).getInt("descansoDur", 15));
        File f = !Double.isNaN(dLat) && ruta.size() >= 2 ? descargarArchivo() : null;
        if (f != null) i.putExtra("seguir", f.getName().replace(".txt", ""));
        try { startForegroundService(i); } catch (Exception e) { aviso("No se pudo empezar a grabar"); return; }
        aviso(f != null ? "🛣 Grabando y vigilando la ruta. Puede apagar la pantalla." : "🛣 Grabando el recorrido. Puede apagar la pantalla.");
        pintarListas();
    }

    /** Devolverse al punto de salida de la ruta (o de la última ruta guardada). */
    private void devolverme() {
        double[] ini = null;
        if (ruta.size() >= 2) ini = ruta.get(0);
        else {
            File[] fs = guardadas();
            if (fs.length > 0) {
                Recorrido r = Recorrido.leer(fs[0]);
                if (r != null && !r.puntos.isEmpty()) ini = r.puntos.get(0);
            }
        }
        if (ini == null) { aviso("No hay una ruta para devolverse"); return; }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "google.navigation:q=%.6f,%.6f", ini[0], ini[1])));
        i.setPackage("com.google.android.apps.maps");
        try { startActivity(i); } catch (ActivityNotFoundException e) { aviso("No encontré Google Maps"); }
    }

    /** Marca el punto donde está con su calle y su foto del mapa, y lo manda por WhatsApp. */
    private void meSali() {
        Location yo = dondeEstoy();
        if (yo == null) { aviso("Prenda la ubicación y vuelva a tocar"); return; }
        Direcciones.guardarPuntoSalida(this, yo.getLatitude(), yo.getLongitude());
        compartirPunto(yo.getLatitude(), yo.getLongitude(), "📍 Me salí de la ruta aquí", null);
    }

    private void whatsapp() {
        if (!hayDestino()) return;
        String extra = ruta.size() >= 2 ? String.format(Locale.US, "\n⏱ %s · %.1f km desde donde estoy", tiempo(minutos), km) : "";
        compartirPunto(dLat, dLon, "📍 " + dNombre + "\n" + dLugar + extra, dNombre);
    }

    private void compartirPunto(double lat, double lon, String texto, String nombre) {
        aviso("Preparando la foto del mapa…");
        new Thread(() -> {
            String calles = "";
            try { calles = Lugar.describir(this, lat, lon).split("\n")[0].trim(); } catch (Exception ignored) {}
            String t = texto + (calles.isEmpty() || texto.contains(calles) ? "" : "\n🧭 " + calles)
                    + String.format(Locale.US, "\nhttps://maps.google.com/?q=%.6f,%.6f", lat, lon);
            Uri foto = null;
            try {
                Bitmap bm = MapaFoto.crear(lat, lon, nombre != null ? "📍 " + nombre : "📍 Aquí estoy",
                        String.format(Locale.US, "GPS: %.6f, %.6f · %s", lat, lon, new java.text.SimpleDateFormat("h:mm a", Locale.US).format(new java.util.Date())));
                if (bm != null) {
                    java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                    bm.compress(Bitmap.CompressFormat.PNG, 100, o);
                    foto = Util.guardarBytes(this, "Ubicacion_" + Util.fecha() + ".png", "image/png", o.toByteArray());
                }
            } catch (Exception ignored) {}
            final Uri f = foto;
            final String txt = t;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt);
                if (f != null) i.setType("image/png").putExtra(Intent.EXTRA_STREAM, f).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                i.setPackage("com.whatsapp");
                try { startActivity(i); }
                catch (ActivityNotFoundException e) { i.setPackage(null); startActivity(Intent.createChooser(i, "Enviar")); }
            });
        }, "compartir").start();
    }

    // ================= Versión 35: Google Maps como ayudante =================

    /** Abre Google Maps con la dirección escrita; allá se toca Compartir → «Mi Barra GPS» y la ruta llega aquí. */
    private void buscarEnGoogle() {
        String q = textoGoogle.isEmpty() ? unir(normalizar(eDir.getText().toString().trim()), eBarrio.getText().toString().trim(), eCiudad.getText().toString().trim()) : textoGoogle;
        if (q.isEmpty()) { aviso("Escriba la dirección primero"); return; }
        aviso("En Google Maps toque «Compartir» y escoja «Mi Barra GPS»");
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q + ", Colombia")));
        i.setPackage("com.google.android.apps.maps");
        try { startActivity(i); }
        catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/" + Uri.encode(q + ", Colombia"))));
        }
    }

    /** Lo que llega compartido desde Google Maps o WhatsApp: se saca el punto y se traza la ruta. */
    private void recibirCompartido(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
        String txt = i.getStringExtra(Intent.EXTRA_TEXT);
        String asunto = i.getStringExtra(Intent.EXTRA_SUBJECT);
        i.setAction(null);
        if (txt == null) { aviso("No llegó ninguna dirección"); return; }
        resumen.setText("📍 Leyendo la dirección que llegó de Google Maps…");
        new Thread(() -> {
            double[] ll = puntoDe(txt);
            String largo = "";
            if (ll == null) { largo = ParadasActivity.abrirEnlaceCorto(txt); ll = puntoDe(largo); }
            if (ll == null) {
                // sin números: se busca el nombre del sitio que trae el enlace
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("/place/([^/@?]+)").matcher(largo);
                String sitio = m.find() ? m.group(1).replace('+', ' ') : "";
                try {
                    if (!sitio.isEmpty()) {
                        List<Address> x = new Geocoder(this, new Locale("es", "CO")).getFromLocationName(sitio, 1);
                        if (x != null && !x.isEmpty()) ll = new double[]{x.get(0).getLatitude(), x.get(0).getLongitude()};
                    }
                } catch (Exception ignored) {}
            }
            String nombre = "";
            for (String linea : txt.split("\n")) {
                String t = linea.trim();
                if (!t.isEmpty() && !t.startsWith("http")) { nombre = t; break; }
            }
            if (nombre.isEmpty() && asunto != null) nombre = asunto;
            String lugar = "";
            if (ll != null) try {
                List<Address> r = new Geocoder(this, new Locale("es", "CO")).getFromLocation(ll[0], ll[1], 1);
                if (r != null && !r.isEmpty()) {
                    lugar = barrioCiudad(r.get(0));
                    if (nombre.isEmpty() && r.get(0).getMaxAddressLineIndex() >= 0) nombre = r.get(0).getAddressLine(0).split(",")[0];
                }
            } catch (Exception ignored) {}
            final double[] fin = ll;
            final String n = nombre.isEmpty() ? "Punto de Google Maps" : nombre, lu = lugar;
            runOnUiThread(() -> {
                if (fin == null) { resumen.setText("No pude leer el punto que llegó. Pruebe otra vez desde Google Maps: Compartir → «Mi Barra GPS»."); return; }
                aviso("✔ Llegó de Google Maps: " + n);
                escoger(fin[0], fin[1], n, lu);
            });
        }, "compartido").start();
    }

    /** Coordenadas en un texto o enlace: «3.45,-76.53», «@3.45,-76.53», «!3d3.45!4d-76.53». */
    private static double[] puntoDe(String s) {
        if (s == null || s.isEmpty()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)").matcher(s);
        if (m.find()) return new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))};
        return ParadasActivity.coordenadas(s);
    }

    /** Llegó a la casa: se guarda el punto exacto de donde está, para siempre, con su foto del mapa. */
    private void guardarDondeEstoy() {
        Location yo = dondeEstoy();
        if (yo == null) { aviso("Prenda la ubicación y vuelva a tocar"); return; }
        EditText nom = new EditText(this);
        String sug = unir(normalizar(eDir.getText().toString().trim()), "", "");
        nom.setText(sug.isEmpty() ? dNombre : sug);
        nom.setHint("Nombre o dirección (ej. Casa de Pedro)");
        nom.setTextSize(18);
        new AlertDialog.Builder(this).setTitle("💾 Guardar donde estoy").setView(nom)
                .setPositiveButton("Guardar", (d, w) -> {
                    String n = nom.getText().toString().trim();
                    if (n.isEmpty()) n = "Punto guardado";
                    final String nn = n;
                    final double la = yo.getLatitude(), lo = yo.getLongitude();
                    new Thread(() -> {
                        String lug = "";
                        try {
                            List<Address> r = new Geocoder(this, new Locale("es", "CO")).getFromLocation(la, lo, 1);
                            if (r != null && !r.isEmpty()) lug = barrioCiudad(r.get(0));
                        } catch (Exception ignored) {}
                        String b = eBarrio.getText().toString().trim(), c = eCiudad.getText().toString().trim();
                        if (!b.isEmpty() || !c.isEmpty()) lug = unir(b.isEmpty() ? "" : "Barrio " + b, c, "").replace(", ", " · ");
                        final String lf = lug;
                        runOnUiThread(() -> {
                            guardarBuscada(nn, lf, la, lo);
                            pintarListas();
                            aviso("💾 Guardado con su punto exacto: " + nn);
                        });
                    }, "guardarpunto").start();
                }).setNegativeButton("Cancelar", null).show();
    }

    // ================= Listas: buscadas y rutas guardadas =================

    private JSONArray buscadas() {
        try { return Direcciones.lista(this); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void guardarBuscadas(JSONArray a) {
        Direcciones.guardar(this, a);
    }

    private void guardarBuscada(String dir, String lugar, double lat, double lon) {
        // Versión 41: la libreta única de direcciones (antes se borraba sola)
        Direcciones.agregar(this, dir, lugar, lat, lon);
    }

    private File[] guardadas() {
        File[] fs = RecorridoService.carpeta(this).listFiles(f -> f.getName().endsWith(".txt")
                && (f.getName().startsWith("gps_") || f.getName().startsWith("ruta_")));
        if (fs == null) return new File[0];
        java.util.Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return fs;
    }

    private void pintarListas() {
        listas.removeAllViews();
        JSONArray a = buscadas();
        if (a.length() > 0) {
            listas.addView(subtitulo("🔎 Direcciones buscadas"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                final int idx = i;
                String lug = o.optString("lugar");
                double lat = o.optDouble("lat"), lon = o.optDouble("lon");
                boolean sinPunto = Double.isNaN(lat);
                boolean ok = !sinPunto && lug.startsWith("Barrio ") && lug.contains("·");
                listas.addView(tarjeta(lat, lon, o.optString("dir") + (lug.isEmpty() ? "" : "\n" + lug)
                                + (sinPunto ? "\n⚠ Sin punto exacto: ▶ la busca en Google Maps" : ok ? "" : "\n⚠ Incompleta: revise barrio o ciudad"),
                        ok ? Color.BLACK : 0xFFD32F2F,
                        () -> {
                            if (!sinPunto) { escoger(lat, lon, o.optString("dir"), lug); return; }
                            dLat = Double.NaN; dNombre = o.optString("dir"); dLugar = lug;
                            textoGoogle = unir(dNombre, lug.replace("Barrio ", ""), "");
                            ir();
                        },
                        () -> { JSONArray bb = buscadas(), n = new JSONArray(); for (int k = 0; k < bb.length(); k++) if (k != idx) n.put(bb.opt(k)); guardarBuscadas(n); pintarListas(); }));
            }
            Button todas = boton("🗑  Borrar todas las direcciones buscadas", 0xFF9E9E9E);
            todas.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("¿Borrar todas las direcciones buscadas?")
                    .setPositiveButton("Sí", (d, w) -> { guardarBuscadas(new JSONArray()); pintarListas(); })
                    .setNegativeButton("No", null).show());
            listas.addView(todas, ancho(46));
        }
        File[] fs = guardadas();
        if (fs.length > 0) {
            listas.addView(subtitulo("📂 Rutas guardadas"));
            for (File f : fs) {
                Recorrido r = Recorrido.leer(f);
                if (r == null || r.puntos.isEmpty()) continue;
                double[] fin = r.puntos.get(r.puntos.size() - 1);
                listas.addView(tarjeta(fin[0], fin[1], r.nombre.replaceFirst("^(GPS|Ruta) · ", "") + "\n" + new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new java.util.Date(f.lastModified())),
                        Color.BLACK, () -> abrirGuardada(r),
                        () -> new AlertDialog.Builder(this).setTitle("¿Borrar esta ruta guardada?")
                                .setPositiveButton("Borrar", (d, w) -> { f.delete(); pintarListas(); })
                                .setNegativeButton("No", null).show()));
            }
        }
    }

    private void abrirGuardada(Recorrido r) {
        ruta.clear();
        for (double[] q : r.puntos) ruta.add(new double[]{q[0], q[1]});
        double[] a = ruta.get(0), z = ruta.get(ruta.size() - 1);
        dLat = z[0]; dLon = z[1];
        dNombre = r.nombre.replaceFirst("^(GPS|Ruta) · ", "");
        dLugar = "";
        km = 0;
        for (int i = 1; i < ruta.size(); i++) km += Recorrido.distancia(ruta.get(i - 1)[0], ruta.get(i - 1)[1], ruta.get(i)[0], ruta.get(i)[1]) / 1000.0;
        minutos = km / 30.0 * 60.0;
        resumen.setText(String.format(Locale.US, "📂 %s\n%.1f km (guardada, sirve sin internet)", dNombre, km));
        mapa.poner(ruta, a[0], a[1], dLat, dLon);
        aviso("Ruta abierta: puede Ir, Grabar (con vigilancia), Devolverse o mandarla por WhatsApp");
    }

    private TextView subtitulo(String s) {
        TextView t = texto("\n" + s, 17, Color.BLACK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView texto(String s, float tam, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(tam);
        t.setTextColor(color);
        return t;
    }

    /** Una fila con la foto del mapa (calles y carreras alrededor), el texto, ▶ y 🗑. */
    private View tarjeta(double lat, double lon, String txt, int color, Runnable abrir, Runnable borrar) {
        LinearLayout c = horizontal();
        c.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(10));
        c.setBackground(g);
        c.setPadding(dp(6), dp(6), dp(6), dp(6));
        ImageView foto = new ImageView(this);
        foto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        foto.setBackgroundColor(0xFFDCEBD5);
        c.addView(foto, new LinearLayout.LayoutParams(dp(92), dp(70)));
        fotoMapa(lat, lon, foto);
        TextView t = texto(txt, 15, color);
        t.setPadding(dp(8), 0, dp(4), 0);
        c.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button ir = boton("▶", 0xFF1565C0);
        ir.setOnClickListener(v -> abrir.run());
        c.addView(ir, new LinearLayout.LayoutParams(dp(48), dp(48)));
        Button del = boton("🗑", 0xFFC62828);
        del.setOnClickListener(v -> borrar.run());
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(dp(48), dp(48));
        dp2.leftMargin = dp(4);
        c.addView(del, dp2);
        c.setOnClickListener(v -> abrir.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(5);
        c.setLayoutParams(lp);
        return c;
    }

    /** La foto del mapa se saca una vez (con internet) y queda guardada para verla sin internet. */
    private void fotoMapa(double lat, double lon, ImageView v) {
        if (Double.isNaN(lat)) { v.setImageDrawable(null); return; }
        File dir = new File(getFilesDir(), "fotosmapa");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, String.format(Locale.US, "%.5f_%.5f.png", lat, lon));
        if (f.exists()) { v.setImageBitmap(BitmapFactory.decodeFile(f.getPath())); return; }
        new Thread(() -> {
            try {
                Bitmap bm = MapaFoto.crear(lat, lon, "", "");
                if (bm == null) return;
                Bitmap peq = Bitmap.createScaledBitmap(bm, bm.getWidth() / 3, bm.getHeight() / 3, true);
                try (FileOutputStream o = new FileOutputStream(f)) { peq.compress(Bitmap.CompressFormat.PNG, 100, o); }
                h.post(() -> v.setImageBitmap(peq));
            } catch (Exception ignored) {}
        }, "fotomapa").start();
    }

    // ================= El mapa con la ruta =================

    /** Mapa de calles (OpenStreetMap, necesita internet) con la ruta en azul, la salida y el destino. */
    static class MapaRuta extends View {
        private List<double[]> pts = new ArrayList<>();
        private double oLat = Double.NaN, oLon, dLat = Double.NaN, dLon;
        private final java.util.Map<String, Bitmap> baldosas = new java.util.HashMap<>();
        private final java.util.Set<String> pedidas = new java.util.HashSet<>();
        private final Paint pLinea = new Paint(Paint.ANTI_ALIAS_FLAG), pPunto = new Paint(Paint.ANTI_ALIAS_FLAG), pTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int z;
        private double cx, cy;   // centro en píxeles del mundo, al zoom z

        MapaRuta(Context c) {
            super(c);
            pLinea.setStyle(Paint.Style.STROKE);
            pLinea.setStrokeCap(Paint.Cap.ROUND);
            pLinea.setStrokeJoin(Paint.Join.ROUND);
            pLinea.setColor(0xFF3B2FD9);
            pTxt.setColor(0xFF555555);
            pTxt.setTextSize(14 * c.getResources().getDisplayMetrics().density);
        }

        void poner(List<double[]> ruta, double oLat, double oLon, double dLat, double dLon) {
            pts = new ArrayList<>(ruta);
            this.oLat = oLat; this.oLon = oLon; this.dLat = dLat; this.dLon = dLon;
            encuadrar();
            invalidate();
        }

        private static double px(double lon, int z) { return (lon + 180) / 360 * 256 * (1 << z); }
        private static double py(double lat, int z) {
            double r = Math.toRadians(lat);
            return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * 256 * (1 << z);
        }

        private void encuadrar() {
            List<double[]> todos = new ArrayList<>(pts);
            if (!Double.isNaN(oLat)) todos.add(new double[]{oLat, oLon});
            if (!Double.isNaN(dLat)) todos.add(new double[]{dLat, dLon});
            if (todos.isEmpty()) return;
            double mnLa = 90, mxLa = -90, mnLo = 180, mxLo = -180;
            for (double[] q : todos) { mnLa = Math.min(mnLa, q[0]); mxLa = Math.max(mxLa, q[0]); mnLo = Math.min(mnLo, q[1]); mxLo = Math.max(mxLo, q[1]); }
            int w = Math.max(getWidth(), 300), hh = Math.max(getHeight(), 200);
            z = 17;
            while (z > 3 && (px(mxLo, z) - px(mnLo, z) > w * 0.85 || py(mnLa, z) - py(mxLa, z) > hh * 0.80)) z--;
            cx = (px(mnLo, z) + px(mxLo, z)) / 2;
            cy = (py(mnLa, z) + py(mxLa, z)) / 2;
        }

        @Override protected void onSizeChanged(int w, int hh, int ow, int oh) { super.onSizeChanged(w, hh, ow, oh); encuadrar(); }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(0xFFDCEBD5);
            float d = getResources().getDisplayMetrics().density;
            if (pts.isEmpty() && Double.isNaN(dLat)) {
                c.drawText("Aquí sale el mapa con la ruta", d * 12, getHeight() / 2f, pTxt);
                return;
            }
            double x0 = cx - getWidth() / 2.0, y0 = cy - getHeight() / 2.0;
            int n = 1 << z;
            for (int tx = (int) Math.floor(x0 / 256); tx <= (int) Math.floor((x0 + getWidth()) / 256); tx++)
                for (int ty = (int) Math.floor(y0 / 256); ty <= (int) Math.floor((y0 + getHeight()) / 256); ty++) {
                    if (ty < 0 || ty >= n) continue;
                    int txx = ((tx % n) + n) % n;
                    String k = z + "/" + txx + "/" + ty;
                    Bitmap b = baldosas.get(k);
                    if (b != null) c.drawBitmap(b, (float) (tx * 256 - x0), (float) (ty * 256 - y0), null);
                    else pedir(k, z, txx, ty);
                }
            if (pts.size() >= 2) {
                Path p = new Path();
                for (int i = 0; i < pts.size(); i++) {
                    float x = (float) (px(pts.get(i)[1], z) - x0), y = (float) (py(pts.get(i)[0], z) - y0);
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                pLinea.setStrokeWidth(6 * d);
                c.drawPath(p, pLinea);
            }
            if (!Double.isNaN(oLat)) {
                pPunto.setColor(Color.WHITE);
                c.drawCircle((float) (px(oLon, z) - x0), (float) (py(oLat, z) - y0), 9 * d, pPunto);
                pPunto.setColor(0xFF1565C0);
                c.drawCircle((float) (px(oLon, z) - x0), (float) (py(oLat, z) - y0), 6.5f * d, pPunto);
            }
            if (!Double.isNaN(dLat)) {
                float x = (float) (px(dLon, z) - x0), y = (float) (py(dLat, z) - y0);
                pPunto.setColor(0xFFE53935);
                Path pin = new Path();
                pin.moveTo(x, y);
                pin.lineTo(x - 9 * d, y - 18 * d);
                pin.lineTo(x + 9 * d, y - 18 * d);
                pin.close();
                c.drawPath(pin, pPunto);
                c.drawCircle(x, y - 20 * d, 9 * d, pPunto);
            }
            c.drawText("© OpenStreetMap", getWidth() - 125 * d, getHeight() - 6 * d, pTxt);
        }

        private void pedir(String k, int zz, int x, int y) {
            if (pedidas.contains(k)) return;
            pedidas.add(k);
            new Thread(() -> {
                Bitmap b = MapaFoto.baldosa(zz, x, y);
                if (b != null) post(() -> { baldosas.put(k, b); invalidate(); });
            }, "baldosa").start();
        }
    }
}
