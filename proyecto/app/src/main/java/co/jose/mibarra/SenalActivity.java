package co.jose.mibarra;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Versión 17 · 📶 Señal y velocidad: datos del celular, Wi‑Fi de la casa y prueba de velocidad. */
public class SenalActivity extends Base {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView tDatos, tWifi, tVel;
    private Button bMedir;
    private volatile boolean midiendo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = vertical();
        l.setPadding(dp(14), dp(10), dp(14), dp(14));
        l.addView(titulo("📶 Señal y velocidad"));
        tDatos = caja(l);
        tWifi = caja(l);
        tVel = caja(l);
        tVel.setTextSize(20);
        tVel.setGravity(Gravity.CENTER);
        tVel.setText("Toque ⚡ Medir velocidad");
        bMedir = boton("⚡  Medir velocidad", 0xFF1565C0);
        bMedir.setOnClickListener(v -> medir());
        l.addView(bMedir, ancho(58));
        Button antena = boton("✈  Buscar otra antena (modo avión 10 s)", 0xFF6A1B9A);
        antena.setOnClickListener(v -> {
            aviso("Prenda el ✈ Modo avión, cuente hasta 10 y apáguelo. El celular se engancha de nuevo, muchas veces a una antena mejor.");
            try { startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        l.addView(antena, ancho(58));
        Button wifi = boton("🛜  Mejorar el Wi‑Fi y redes gratis", 0xFF2E7D32);
        wifi.setOnClickListener(v -> startActivity(new Intent(this, WifiActivity.class)));
        l.addView(wifi, ancho(58));
        TextView n = new TextView(this);
        n.setText("\nGuía: 4 barras = excelente · 3 = buena · 2 = regular · 1 o 0 = mala.\nLa prueba de velocidad gasta unos 10 MB si está con datos.");
        n.setTextColor(0xFF78909C);
        n.setTextSize(13);
        l.addView(n);
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION}, 5);
    }

    private TextView caja(LinearLayout l) {
        TextView t = new TextView(this);
        t.setTextSize(16);
        t.setTextColor(0xFF263238);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(Comun.fondo(this, 0xFFECEFF1, 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        l.addView(t, p);
        return t;
    }

    @Override protected void onResume() { super.onResume(); h.post(tic); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tic); }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) { super.onRequestPermissionsResult(r, p, g); leer(); }

    private final Runnable tic = new Runnable() { @Override public void run() { leer(); h.postDelayed(this, 3000); } };

    static String barras(int nivel) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 4; i++) s.append(i < nivel ? "■" : "□");
        return s + "  " + (nivel >= 4 ? "Excelente" : nivel == 3 ? "Buena" : nivel == 2 ? "Regular" : "Mala");
    }

    @SuppressWarnings("deprecation")
    private void leer() {
        // Datos del celular
        StringBuilder d = new StringBuilder("📱 Datos del celular\n");
        try {
            TelephonyManager tm = getSystemService(TelephonyManager.class);
            SignalStrength ss = tm.getSignalStrength();
            String tipo = "";
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                switch (tm.getDataNetworkType()) {
                    case TelephonyManager.NETWORK_TYPE_NR: tipo = "5G"; break;
                    case TelephonyManager.NETWORK_TYPE_LTE: tipo = "4G"; break;
                    case TelephonyManager.NETWORK_TYPE_HSPAP: case TelephonyManager.NETWORK_TYPE_HSPA: case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_UMTS: tipo = "3G"; break;
                    case TelephonyManager.NETWORK_TYPE_EDGE: case TelephonyManager.NETWORK_TYPE_GPRS: tipo = "2G (muy lenta)"; break;
                    default: tipo = "";
                }
            }
            String op = tm.getNetworkOperatorName();
            d.append(op == null || op.isEmpty() ? "" : op + "  ").append(tipo.isEmpty() ? "" : tipo).append('\n');
            if (ss != null) {
                d.append(barras(ss.getLevel()));
                int dbm = Integer.MAX_VALUE;
                for (android.telephony.CellSignalStrength c : ss.getCellSignalStrengths()) dbm = Math.min(dbm, c.getDbm());
                if (dbm != Integer.MAX_VALUE && dbm < 0) d.append("  (").append(dbm).append(" dBm)");
            } else d.append("Sin señal o sin SIM");
        } catch (Exception e) { d.append("No se pudo leer (¿sin SIM?)"); }
        tDatos.setText(d);

        // Wi‑Fi
        StringBuilder w = new StringBuilder("🛜 Wi‑Fi\n");
        try {
            WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
            WifiInfo wi = wm.getConnectionInfo();
            if (!wm.isWifiEnabled()) w.append("Apagado");
            else if (wi == null || wi.getNetworkId() == -1) w.append("Prendido, pero no está conectado a ninguna red");
            else {
                String ssid = wi.getSSID() == null ? "" : wi.getSSID().replace("\"", "");
                if (ssid.contains("unknown")) ssid = "su red";
                int nivel = WifiManager.calculateSignalLevel(wi.getRssi(), 5);
                w.append(ssid).append(wi.getFrequency() > 4900 ? "  (5 GHz)" : "  (2,4 GHz)").append('\n')
                        .append(barras(Math.min(4, nivel))).append("  (").append(wi.getRssi()).append(" dBm)\n")
                        .append("Velocidad de enlace con el router: ").append(wi.getLinkSpeed()).append(" Mb/s");
            }
        } catch (Exception e) { w.append("No se pudo leer"); }
        tWifi.setText(w);
    }

    // ---------------- Prueba de velocidad ----------------

    private void medir() {
        if (midiendo) return;
        midiendo = true;
        bMedir.setText("⏳  Midiendo… espere");
        tVel.setText("Midiendo la bajada…");
        new Thread(() -> {
            String r;
            try {
                double baja = bajada();
                h.post(() -> tVel.setText("⬇ " + mb(baja) + " Mb/s bajada\nMidiendo la subida…"));
                double sube = subida();
                r = "⬇ " + mb(baja) + " Mb/s bajada\n⬆ " + mb(sube) + " Mb/s subida\n" + opinion(baja);
            } catch (Exception e) {
                r = "No se pudo medir. ¿Hay internet?";
            }
            final String fr = r;
            h.post(() -> { tVel.setText(fr); bMedir.setText("⚡  Medir velocidad otra vez"); midiendo = false; });
        }).start();
    }

    private static String mb(double v) { return String.format(new Locale("es", "CO"), "%.1f", v); }

    private static String opinion(double baja) {
        if (baja >= 20) return "✅ Muy buena: videos y llamadas sin problema";
        if (baja >= 5) return "🙂 Buena para WhatsApp, YouTube y llamadas";
        if (baja >= 1.5) return "😐 Regular: videos pueden cortarse";
        return "⚠ Mala: pruebe ✈ Buscar otra antena o acérquese al router";
    }

    private double bajada() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=8000000").openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        long t0 = System.nanoTime(), total = 0;
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) { total += n; if (System.nanoTime() - t0 > 12_000_000_000L) break; }
        }
        double seg = (System.nanoTime() - t0) / 1e9;
        return total * 8 / 1e6 / Math.max(0.1, seg);
    }

    private double subida() throws Exception {
        byte[] datos = new byte[2_000_000];
        HttpURLConnection c = (HttpURLConnection) new URL("https://speed.cloudflare.com/__up").openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setFixedLengthStreamingMode(datos.length);
        long t0 = System.nanoTime();
        try (OutputStream out = c.getOutputStream()) { out.write(datos); }
        c.getResponseCode();
        double seg = (System.nanoTime() - t0) / 1e9;
        c.disconnect();
        return datos.length * 8 / 1e6 / Math.max(0.1, seg);
    }

    static boolean hayInternet(android.content.Context ctx) {
        ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
