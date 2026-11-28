package co.jose.mibarra;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.DhcpInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Versión 17 · 🛜 Wi‑Fi: qué canal está más libre para su router, y redes abiertas (sin clave) cerca.
 * No adivina claves de redes ajenas.
 */
public class WifiActivity extends Base {
    private LinearLayout l;
    private WifiManager wm;
    private boolean registrado;

    private final BroadcastReceiver alTerminar = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { pintar(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        wm = getApplicationContext().getSystemService(WifiManager.class);
        l = vertical();
        l.setPadding(dp(14), dp(10), dp(14), dp(14));
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 6);
        registerReceiver(alTerminar, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        registrado = true;
        buscar();
    }

    @Override protected void onDestroy() { if (registrado) try { unregisterReceiver(alTerminar); } catch (Exception ignored) {} super.onDestroy(); }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) { super.onRequestPermissionsResult(r, p, g); buscar(); }

    @SuppressWarnings("deprecation")
    private void buscar() {
        try { wm.startScan(); } catch (Exception ignored) {}
        pintar();
    }

    static int canal(int mhz) {
        if (mhz == 2484) return 14;
        if (mhz >= 2412 && mhz <= 2472) return (mhz - 2407) / 5;
        if (mhz >= 5000) return (mhz - 5000) / 5;
        return 0;
    }

    @SuppressWarnings("deprecation")
    private void pintar() {
        l.removeAllViews();
        l.addView(titulo("🛜 Wi‑Fi"));
        List<ScanResult> rs;
        try { rs = wm.getScanResults(); } catch (Exception e) { rs = new ArrayList<>(); }
        if (rs == null) rs = new ArrayList<>();
        if (!wm.isWifiEnabled()) { texto("El Wi‑Fi está apagado. Préndalo para revisar.", 0xFFC62828); boton2("🛜 Abrir el Wi‑Fi", () -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS))); return; }
        if (rs.isEmpty()) texto("Buscando redes… Si no aparece nada, revise que la Ubicación del celular esté prendida.", 0xFF78909C);

        // Su red
        WifiInfo wi = wm.getConnectionInfo();
        String suSsid = wi == null || wi.getSSID() == null ? "" : wi.getSSID().replace("\"", "");
        int suFrec = wi == null ? 0 : wi.getFrequency(), suCanal = canal(suFrec);

        // Cuántas redes hay en cada canal de 2,4 GHz (cada red "pisa" 2 canales a cada lado)
        Map<Integer, Integer> peso = new HashMap<>();
        for (ScanResult r : rs) {
            if (r.frequency > 2500) continue;
            if (wi != null && r.BSSID != null && r.BSSID.equalsIgnoreCase(wi.getBSSID())) continue;
            int c = canal(r.frequency);
            for (int k = c - 2; k <= c + 2; k++) peso.put(k, peso.getOrDefault(k, 0) + 1);
        }
        int mejor = 1;
        for (int c : new int[]{1, 6, 11}) if (peso.getOrDefault(c, 0) < peso.getOrDefault(mejor, 0)) mejor = c;

        StringBuilder s = new StringBuilder("📊 Su red: ").append(suSsid.isEmpty() || suSsid.contains("unknown") ? "(no conectado)" : suSsid);
        if (suCanal > 0) {
            s.append("\nUsa el canal ").append(suCanal).append(suFrec > 4900 ? " (5 GHz: casi siempre libre ✅)" : "");
            if (suFrec < 2500) {
                int en = peso.getOrDefault(suCanal, 0), enMejor = peso.getOrDefault(mejor, 0);
                s.append(", donde se cruzan ").append(en).append(en == 1 ? " red vecina." : " redes vecinas.");
                if (mejor != suCanal && enMejor < en)
                    s.append("\n✅ Mejor cámbielo al canal ").append(mejor).append(" (").append(enMejor).append(enMejor == 1 ? " vecina)." : " vecinas).");
                else s.append("\n✅ Su canal está bien escogido.");
            }
        }
        texto(s.toString(), 0xFF263238);

        DhcpInfo dh = wm.getDhcpInfo();
        String ip = dh == null || dh.gateway == 0 ? "192.168.1.1" : String.format(Locale.US, "%d.%d.%d.%d",
                dh.gateway & 0xff, dh.gateway >> 8 & 0xff, dh.gateway >> 16 & 0xff, dh.gateway >> 24 & 0xff);
        boton2("🔧 Abrir el router (" + ip + ")", () -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + ip))); } catch (Exception e) { aviso("No se pudo abrir"); }
        });
        texto("Cómo cambiar el canal: entre con el usuario y la clave que vienen en la etiqueta de debajo del router → "
                + "busque «Wi‑Fi», «Inalámbrico» o «WLAN» → «Canal» → escoja el " + mejor + " → Guardar. "
                + "Otros consejos: ponga el router alto y en el centro de la casa, lejos del microondas y de paredes gruesas. "
                + "Si el router tiene red de 5 GHz, úsela cerca del router: es más rápida.", 0xFF546E7A);

        // Redes abiertas
        texto("\n🔓 Redes abiertas cerca (sin clave)", 0xFF1B5E20);
        List<ScanResult> abiertas = new ArrayList<>();
        for (ScanResult r : rs) {
            String cap = r.capabilities == null ? "" : r.capabilities.toUpperCase(Locale.ROOT);
            if (r.SSID == null || r.SSID.isEmpty()) continue;
            if (cap.contains("WPA") || cap.contains("WEP") || cap.contains("SAE") || cap.contains("PSK") || cap.contains("EAP") || cap.contains("OWE")) continue;
            boolean repetida = false;
            for (ScanResult x : abiertas) if (x.SSID.equals(r.SSID)) { repetida = true; break; }
            if (!repetida) abiertas.add(r);
        }
        abiertas.sort((a, b2) -> b2.level - a.level);
        if (abiertas.isEmpty()) texto("No hay redes abiertas cerca en este momento.", 0xFF78909C);
        for (ScanResult r : abiertas) {
            int nivel = Math.min(4, WifiManager.calculateSignalLevel(r.level, 5));
            boton2("🛜 " + r.SSID + "   " + SenalActivity.barras(nivel), () -> {
                aviso("Toque la red «" + r.SSID + "» para conectarse");
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            });
        }
        texto("⚠ En redes abiertas no use el banco, Nequi ni claves: otros podrían ver lo que envía.", 0xFFE65100);
        boton2("🔄 Buscar otra vez", this::buscar);
    }

    private void texto(String s, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(color);
        t.setPadding(dp(4), dp(8), dp(4), dp(4));
        l.addView(t);
    }

    private void boton2(String s, Runnable r) {
        Button b = boton(s, 0xFF37474F);
        b.setOnClickListener(v -> r.run());
        l.addView(b, ancho(54));
    }
}
