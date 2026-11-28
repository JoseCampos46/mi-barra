package co.jose.mibarra;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;

/**
 * Versión 25: "VPN" que vive solo dentro del celular y por la que pasa únicamente WhatsApp.
 * Todo lo que WhatsApp manda se bota, así queda sin internet; las demás apps navegan normal.
 */
public class BloqueoVpn extends VpnService {
    static final String PARAR = "co.jose.mibarra.VPN_PARAR";
    private static ParcelFileDescriptor tun;
    private static Thread hilo;

    static boolean prendido() { return tun != null; }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        if (i != null && PARAR.equals(i.getAction())) { parar(); stopSelf(); return START_NOT_STICKY; }
        if (tun != null) return START_STICKY;
        try {
            Builder b = new Builder().setSession("Mi Barra · WhatsApp bloqueado").setBlocking(true)
                    .addAddress("10.111.0.1", 32).addRoute("0.0.0.0", 0)
                    .addAddress("fd00:111::1", 128).addRoute("::", 0);
            int apps = 0;
            for (String pq : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
                try { b.addAllowedApplication(pq); apps++; } catch (Exception ignored) {}
            }
            if (apps == 0) { Bloqueos.poner(this, "wa", null); stopSelf(); return START_NOT_STICKY; }
            tun = b.establish();
            if (tun == null) { stopSelf(); return START_NOT_STICKY; }
            final ParcelFileDescriptor t = tun;
            hilo = new Thread(() -> {
                byte[] buf = new byte[32767];
                try (FileInputStream in = new FileInputStream(t.getFileDescriptor())) {
                    while (!Thread.interrupted() && in.read(buf) >= 0) { /* se bota: WhatsApp sin internet */ }
                } catch (Exception ignored) {}
            }, "bloqueo-wa");
            hilo.start();
            Bloqueos.poner(this, "wa", "1");
            Bloqueos.avisoFijo(this, Bloqueos.NOTI_WA, "🚫 WhatsApp bloqueado", "Toque aquí para desbloquearlo", "wa");
        } catch (Exception e) { parar(); stopSelf(); }
        return START_STICKY;
    }

    private void parar() {
        try { if (hilo != null) hilo.interrupt(); } catch (Exception ignored) {}
        try { if (tun != null) tun.close(); } catch (Exception ignored) {}
        tun = null; hilo = null;
        Bloqueos.poner(this, "wa", null);
        Bloqueos.quitarAviso(this, Bloqueos.NOTI_WA);
    }

    @Override public void onRevoke() { parar(); stopSelf(); }
    @Override public void onDestroy() { parar(); super.onDestroy(); }
}
