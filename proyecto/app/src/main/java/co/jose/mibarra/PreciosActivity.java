package co.jose.mibarra;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versión 18: buscador de precios.
 * Tres formas de buscar: escribir o dictar, código de barras, o compartir un enlace (Temu, Amazon…) a Mi Barra.
 * Muestra los más baratos de Mercado Libre con su enlace, y un botón por tienda que abre la misma búsqueda.
 */
public class PreciosActivity extends Base {
    private static final int VOZ = 11, CODIGO = 12, FOTO = 13;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private EditText campo;
    private LinearLayout resultados, recientes;
    private TextView estado;

    /** Tiendas: nombre, color, dirección de búsqueda (%s = lo que se busca). */
    private static final Object[][] TIENDAS = {
            {"🔎 Todas (Google)", 0xFF1A73E8, "https://www.google.com/search?tbm=shop&gl=co&hl=es&q=%s"},
            {"📍 Cerca de mí (Maps)", 0xFF2E7D32, "geo:0,0?q=%s"},
            {"Mercado Libre", 0xFFFFE600, "https://listado.mercadolibre.com.co/%g_OrderId_PRICE_NoIndex_True"},
            {"Homecenter", 0xFFE65100, "https://www.homecenter.com.co/homecenter-co/search?Ntt=%s"},
            {"Falabella", 0xFF7CB342, "https://www.falabella.com.co/falabella-co/search?Ntt=%s"},
            {"Éxito", 0xFFFDD835, "https://www.exito.com/s?q=%s"},
            {"Alkosto", 0xFF1565C0, "https://www.alkosto.com/search?text=%s"},
            // Versión 25: más almacenes de Colombia
            {"Ktronix", 0xFFE53935, "https://www.ktronix.com/search?text=%s"},
            {"Olímpica", 0xFFD32F2F, "https://www.olimpica.com/%s?_q=%s&map=ft"},
            {"Jumbo", 0xFF43A047, "https://www.tiendasjumbo.co/%s?_q=%s&map=ft"},
            {"Panamericana", 0xFF0D47A1, "https://www.panamericana.com.co/%s?_q=%s&map=ft"},
            {"Amazon", 0xFF232F3E, "https://www.amazon.com/s?k=%s&s=price-asc-rank"},
            {"AliExpress", 0xFFE43225, "https://es.aliexpress.com/w/wholesale-%g.html?SortType=price_asc"},
            {"Temu", 0xFFFB7701, "https://www.temu.com/search_result.html?search_key=%s"},
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Buscar precios");
        LinearLayout l = vertical();
        l.setPadding(dp(12), dp(12), dp(12), dp(24));

        TextView t = titulo("💲 Buscar precios");
        l.addView(t);

        LinearLayout fila = horizontal();
        fila.setGravity(Gravity.CENTER_VERTICAL);
        campo = campoTexto("Producto, código o enlace", "");
        campo.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        campo.setSingleLine(true);
        campo.setOnEditorActionListener((v, a, e) -> { buscar(); return true; });
        fila.addView(campo, peso(1));
        TextView mic = Comun.chip(this, "🎙", 0xFFE53935, Color.WHITE);
        mic.setTextSize(22);
        mic.setOnClickListener(v -> dictar());
        fila.addView(mic, new LinearLayout.LayoutParams(dp(52), dp(52)));
        l.addView(fila);

        LinearLayout formas = horizontal();
        formas.addView(herramienta("🔎 Buscar", 0xFF1565C0, Color.WHITE, true, v -> buscar()), peso(1));
        formas.addView(herramienta("▮▯▮ Código", 0xFFF4511E, Color.WHITE, true, v -> {
            try { startActivityForResult(new Intent(this, CodigoActivity.class).putExtra("devolver", true), CODIGO); }
            catch (Exception e) { aviso("No se pudo abrir el lector de códigos"); }
        }), peso(1));
        // Versión 25: buscar por foto (la busca Google Lens en todas las tiendas)
        formas.addView(herramienta("📷 Foto", 0xFF6A1B9A, Color.WHITE, true, v -> {
            try { startActivityForResult(new Intent(this, CamaraActivity.class).putExtra("devolver", true), FOTO); }
            catch (Exception e) { aviso("No se pudo abrir la cámara"); }
        }), peso(1));
        formas.addView(herramienta("📋 Pegar enlace", 0xFF00897B, Color.WHITE, true, v -> pegar()), peso(1));
        l.addView(formas);

        TextView ayuda = new TextView(this);
        ayuda.setText("Desde Temu, Amazon u otra tienda: toque Compartir y escoja Mi Barra · Buscar precio.");
        ayuda.setTextSize(13);
        ayuda.setTextColor(0xFF616161);
        ayuda.setPadding(0, dp(4), 0, dp(8));
        l.addView(ayuda);

        estado = new TextView(this);
        estado.setTextSize(15);
        estado.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(estado);
        resultados = vertical();
        l.addView(resultados);

        TextView tt = new TextView(this);
        tt.setText("\nBuscar lo mismo en:");
        tt.setTextSize(15);
        tt.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(tt);
        LinearLayout tiendas = vertical();
        LinearLayout f2 = null;
        for (int i = 0; i < TIENDAS.length; i++) {
            if (i % 3 == 0) { f2 = horizontal(); tiendas.addView(f2); }
            Object[] ti = TIENDAS[i];
            int fondo = (int) ti[1];
            boolean claro = fondo == 0xFFFFE600 || fondo == 0xFFFDD835;
            final String plantilla = (String) ti[2];
            f2.addView(herramienta((String) ti[0], fondo, claro ? Color.BLACK : Color.WHITE, true, v -> abrirTienda(plantilla)), peso(1));
        }
        l.addView(tiendas);

        TextView tr = new TextView(this);
        tr.setText("\nBúsquedas guardadas");
        tr.setTextSize(15);
        tr.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(tr);
        recientes = vertical();
        l.addView(recientes);

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
        pintarRecientes();
        recibir(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); recibir(i); }

    /** Llegó un enlace compartido desde otra app (Temu, Amazon…). */
    private void recibir(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
        String txt = i.getStringExtra(Intent.EXTRA_TEXT);
        if (txt == null || txt.trim().isEmpty()) return;
        usarTextoCompartido(txt);
    }

    private void pegar() {
        android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
        CharSequence c = null;
        try { if (cm != null && cm.hasPrimaryClip()) c = cm.getPrimaryClip().getItemAt(0).coerceToText(this); } catch (Exception ignored) {}
        if (c == null || c.toString().trim().isEmpty()) { aviso("No hay nada copiado. Copie el enlace del producto y vuelva a tocar."); return; }
        usarTextoCompartido(c.toString());
    }

    /** Separa el enlace del nombre. Si solo vino el enlace, lee el título de la página. */
    private void usarTextoCompartido(String txt) {
        Matcher m = Pattern.compile("https?://\\S+").matcher(txt);
        String url = m.find() ? m.group() : null;
        String nombre = txt.replaceAll("https?://\\S+", " ")
                .replaceAll("(?i)(mira|echa un vistazo|check this out|encontr[ée]|en temu|on temu|amazon\\.com:?|ver en|compra)[^\\n]*?:", " ")
                .replaceAll("[\\p{So}\\p{Cn}]", " ").replaceAll("\\s+", " ").trim();
        if (nombre.length() >= 6) { campo.setText(acortar(nombre)); buscar(); return; }
        if (url == null) { campo.setText(acortar(txt.trim())); buscar(); return; }
        estado.setText("Leyendo el nombre del producto…");
        new Thread(() -> {
            String titulo = tituloDePagina(url);
            ui.post(() -> {
                if (titulo == null || titulo.isEmpty()) { estado.setText(""); aviso("No pude leer el nombre en ese enlace. Escríbalo usted."); return; }
                campo.setText(acortar(titulo));
                buscar();
            });
        }).start();
    }

    /** Las primeras 8 palabras bastan para buscar algo parecido. */
    private static String acortar(String s) {
        String[] p = s.split("\\s+");
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < Math.min(8, p.length); i++) r.append(i > 0 ? " " : "").append(p[i]);
        return r.toString();
    }

    private static String tituloDePagina(String url) {
        try {
            String html = leer(url, 9000);
            Matcher m = Pattern.compile("property=[\"']og:title[\"'][^>]*content=[\"']([^\"']+)").matcher(html);
            if (m.find()) return limpiarHtml(m.group(1));
            m = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(html);
            if (m.find()) return limpiarHtml(m.group(1)).replaceAll("(?i)\\s*[-|].*(temu|amazon|aliexpress).*$", "");
        } catch (Exception ignored) {}
        return null;
    }

    private static String limpiarHtml(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replaceAll("\\s+", " ").trim();
    }

    private static String leer(String url, int tiempo) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(tiempo);
        c.setReadTimeout(tiempo);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        c.setRequestProperty("Accept-Language", "es-CO,es;q=0.9");
        int code = c.getResponseCode();
        if (code >= 400) throw new Exception("HTTP " + code);
        try (InputStream in = c.getInputStream()) { return Util.leerTodo(in); }
    }

    private void dictar() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga el producto");
        try { startActivityForResult(i, VOZ); } catch (Exception e) { aviso("Este celular no tiene dictado. Escríbalo."); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (res != RESULT_OK || d == null) return;
        if (req == VOZ) {
            List<String> r = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) { campo.setText(r.get(0)); buscar(); }
        } else if (req == FOTO && d.getData() != null) {
            porFoto(d.getData());
        } else if (req == CODIGO) {
            String cod = d.getStringExtra("codigo");
            if (cod != null) porCodigo(cod.trim());
        }
    }

    /** La foto del producto se la pasa a Google Lens, que muestra dónde lo venden y a cuánto. */
    private void porFoto(android.net.Uri u) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, u)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        for (String pq : new String[]{"com.google.android.googlequicksearchbox", "com.google.ar.lens"}) {
            try { startActivity(new Intent(i).setPackage(pq)); return; } catch (Exception ignored) {}
        }
        try { startActivity(Intent.createChooser(i, "Buscar esta foto con…")); }
        catch (Exception e) { aviso("Instale la app de Google para buscar por foto"); }
    }

    /** Con el código de barras busca el nombre (sirve sobre todo con productos de mercado). */
    private void porCodigo(String cod) {
        estado.setText("Buscando el nombre del código " + cod + "…");
        new Thread(() -> {
            String nombre = null;
            try {
                JSONObject j = new JSONObject(leer("https://world.openfoodfacts.org/api/v2/product/" + cod + ".json?fields=product_name,brands", 9000));
                JSONObject p = j.optJSONObject("product");
                if (p != null) {
                    nombre = (p.optString("product_name", "") + " " + p.optString("brands", "").split(",")[0]).trim();
                }
            } catch (Exception ignored) {}
            final String n = nombre;
            ui.post(() -> {
                if (n == null || n.isEmpty()) {
                    aviso("No encontré el nombre de ese código. Lo busco por número; si no sale, escriba el nombre.");
                    campo.setText(cod);
                } else campo.setText(n);
                buscar();
            });
        }).start();
    }

    private String consulta() { return campo.getText().toString().trim(); }

    private void abrirTienda(String plantilla) {
        String q = consulta();
        if (q.isEmpty()) { aviso("Escriba primero qué busca"); return; }
        String url;
        try {
            url = plantilla.replace("%s", URLEncoder.encode(q, "UTF-8"))
                    .replace("%g", URLEncoder.encode(q.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-"), "UTF-8"));
        } catch (Exception e) { return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { aviso("No hay navegador"); }
    }

    /** Mercado Libre Colombia, del más barato al más caro. */
    private void buscar() {
        String q = consulta();
        if (q.isEmpty()) return;
        guardarReciente(q);
        estado.setText("Buscando en Mercado Libre…");
        resultados.removeAllViews();
        new Thread(() -> {
            List<String[]> r = new ArrayList<>();
            String error = null;
            try {
                String json = leer("https://api.mercadolibre.com/sites/MCO/search?limit=15&sort=price_asc&q=" + URLEncoder.encode(q, "UTF-8"), 10000);
                JSONArray a = new JSONObject(json).getJSONArray("results");
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    JSONObject envio = o.optJSONObject("shipping");
                    r.add(new String[]{o.optString("title"), String.valueOf(Math.round(o.optDouble("price", 0))),
                            o.optString("permalink"), envio != null && envio.optBoolean("free_shipping") ? "Envío gratis" : ""});
                }
            } catch (Exception e) { error = e.getMessage(); }
            final String err = error;
            ui.post(() -> pintarResultados(r, err));
        }).start();
    }

    private void pintarResultados(List<String[]> r, String error) {
        resultados.removeAllViews();
        if (r.isEmpty()) {
            estado.setText(error != null
                    ? "Mercado Libre no dejó leer los precios ahora. Toque abajo una tienda para ver sus precios."
                    : "Mercado Libre no tiene ese producto. Pruebe con otras palabras o en otra tienda.");
            return;
        }
        estado.setText("Más baratos en Mercado Libre:");
        for (String[] x : r) {
            LinearLayout c = horizontal();
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPadding(dp(10), dp(8), dp(10), dp(8));
            c.setBackground(Comun.fondo(this, 0xFFF5F5F5, 10));
            TextView n = new TextView(this);
            n.setText(x[0] + (x[3].isEmpty() ? "" : "\n" + x[3]));
            n.setTextSize(14);
            n.setTextColor(0xFF212121);
            c.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView p = new TextView(this);
            p.setText(Util.pesos(Long.parseLong(x[1])) + "\nVer ›");
            p.setTextSize(15);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextColor(0xFF2E7D32);
            p.setGravity(Gravity.END);
            p.setPadding(dp(8), 0, 0, 0);
            c.addView(p);
            c.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(x[2]))); } catch (Exception e) { aviso("No hay navegador"); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            resultados.addView(c, lp);
        }
    }

    // ---------------- Búsquedas guardadas ----------------

    private List<String> leerRecientes() {
        List<String> r = new ArrayList<>();
        String s = getSharedPreferences("barra", MODE_PRIVATE).getString("preciosRecientes", "");
        for (String x : s.split("\n")) if (!x.trim().isEmpty()) r.add(x);
        return r;
    }

    private void guardarReciente(String q) {
        List<String> r = leerRecientes();
        r.remove(q);
        r.add(0, q);
        while (r.size() > 12) r.remove(r.size() - 1);
        getSharedPreferences("barra", MODE_PRIVATE).edit().putString("preciosRecientes", String.join("\n", r)).apply();
        pintarRecientes();
    }

    private void pintarRecientes() {
        recientes.removeAllViews();
        for (String q : leerRecientes()) {
            LinearLayout f = horizontal();
            f.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(this);
            t.setText("🕘 " + q);
            t.setTextSize(15);
            t.setPadding(dp(4), dp(10), dp(4), dp(10));
            t.setOnClickListener(v -> { campo.setText(q); buscar(); });
            f.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView x = new TextView(this);
            x.setText("✕");
            x.setTextSize(16);
            x.setPadding(dp(12), dp(8), dp(12), dp(8));
            x.setOnClickListener(v -> {
                List<String> r = leerRecientes();
                r.remove(q);
                getSharedPreferences("barra", MODE_PRIVATE).edit().putString("preciosRecientes", String.join("\n", r)).apply();
                pintarRecientes();
            });
            f.addView(x);
            recientes.addView(f);
        }
    }
}
