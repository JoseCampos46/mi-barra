package co.jose.mibarra;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Toma la ubicación actual y la manda por WhatsApp como enlace de Google Maps. */
public class AquiEstoyActivity extends Activity implements LocationListener {

    private LocationManager lm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Location mejor;
    private boolean listo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        } else {
            buscar();
        }
    }

    @Override
    public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) buscar();
        else { aviso("Sin permiso de ubicación no puedo saber dónde está"); finish(); }
    }

    private void buscar() {
        aviso("📍 Buscando su ubicación…");
        lm = getSystemService(LocationManager.class);
        try {
            for (String pr : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                Location l = lm.getLastKnownLocation(pr);
                if (l != null && System.currentTimeMillis() - l.getTime() < 60000) considerar(l);
                if (lm.isProviderEnabled(pr)) lm.requestLocationUpdates(pr, 1000, 0, this, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            finish();
            return;
        }
        // Si en 15 segundos no hay algo muy preciso, manda lo mejor que tenga
        h.postDelayed(() -> { if (!listo) { if (mejor != null) enviar(mejor); else { aviso("No encontré señal. ¿Está prendido el GPS?"); finish(); } } }, 15000);
    }

    private void considerar(Location l) {
        if (mejor == null || (l.hasAccuracy() && l.getAccuracy() < mejor.getAccuracy())) mejor = l;
        if (mejor.hasAccuracy() && mejor.getAccuracy() <= 25) enviar(mejor);
    }

    @Override public void onLocationChanged(Location l) { if (!listo) considerar(l); }
    @Override public void onStatusChanged(String p, int s, Bundle b) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}

    private void enviar(Location l) {
        if (listo) return;
        listo = true;
        if (lm != null) lm.removeUpdates(this);
        String hora = new SimpleDateFormat("hh:mm a", new Locale("es", "CO")).format(new Date());
        aviso("📍 Armando el mapa con la dirección…");
        new Thread(() -> {
            String lugar = Lugar.describir(this, l.getLatitude(), l.getLongitude());
            String txt = "📍 Aquí estoy (" + hora + ")" + (lugar.isEmpty() ? "" : ":\n" + lugar)
                    + String.format(Locale.US, "\nhttps://maps.google.com/?q=%.6f,%.6f", l.getLatitude(), l.getLongitude());
            if (l.hasAccuracy()) txt += "\nPrecisión: ±" + Math.round(l.getAccuracy()) + " m";
            final String t = txt;
            android.net.Uri foto = null;
            try {
                String pie = String.format(Locale.US, "GPS: %.6f, %.6f · %s", l.getLatitude(), l.getLongitude(), hora);
                android.graphics.Bitmap bm = MapaFoto.crear(l.getLatitude(), l.getLongitude(),
                        lugar.isEmpty() ? "📍 Aquí estoy" : "📍 " + lugar, pie);
                if (bm != null) {
                    java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                    bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
                    foto = Util.guardarBytes(this, "Ubicacion_" + Util.fecha() + ".png", "image/png", o.toByteArray());
                }
            } catch (Exception ignored) {}
            final android.net.Uri f = foto;
            runOnUiThread(() -> compartir(t, f));
        }).start();
    }

    private void compartir(String txt, android.net.Uri foto) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt);
        if (foto != null) {
            i.setType("image/png").putExtra(Intent.EXTRA_STREAM, foto).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        i.setPackage("com.whatsapp");
        try { startActivity(i); }
        catch (ActivityNotFoundException e) { i.setPackage(null); startActivity(Intent.createChooser(i, "Enviar ubicación")); }
        finish();
    }

    private void aviso(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override
    protected void onDestroy() {
        if (lm != null) lm.removeUpdates(this);
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
