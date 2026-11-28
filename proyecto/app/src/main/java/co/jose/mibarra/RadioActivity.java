package co.jose.mibarra;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Versión 17 · 📻 Radio por internet: emisoras colombianas de la lista pública y gratuita radio-browser.info. */
public class RadioActivity extends CanalesBase {

    private static final String[] SERVIDORES = {"de1.api.radio-browser.info", "nl1.api.radio-browser.info", "at1.api.radio-browser.info"};

    @Override boolean esTv() { return false; }
    @Override String tituloPantalla() { return "📻 Radio"; }

    @Override
    List<String[]> catalogo() throws Exception {
        for (String s : SERVIDORES) {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL("https://" + s
                        + "/json/stations/bycountrycodeexact/CO?hidebroken=true&order=votes&reverse=true&limit=150").openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(15000);
                con.setRequestProperty("User-Agent", "MiBarra/17");
                byte[] b;
                try (InputStream in = con.getInputStream()) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    b = bo.toByteArray();
                }
                JSONArray a = new JSONArray(new String(b, StandardCharsets.UTF_8));
                List<String[]> r = new ArrayList<>();
                Set<String> vistos = new HashSet<>();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    String n = o.optString("name", "").trim(), u = o.optString("url_resolved", "");
                    if (u.isEmpty()) u = o.optString("url", "");
                    if (n.isEmpty() || !u.startsWith("http") || !vistos.add(n.toLowerCase())) continue;
                    String ciudad = o.optString("state", "").trim();
                    r.add(new String[]{n + (ciudad.isEmpty() ? "" : " · " + ciudad), u});
                }
                if (!r.isEmpty()) return r;
            } catch (Exception ignored) {}
        }
        return new ArrayList<>();
    }

    @Override
    void tocar(String nombre, String url) {
        aviso("📻 Conectando con " + nombre + "…");
        SonidoService.radio(this, nombre, url);
    }
}
