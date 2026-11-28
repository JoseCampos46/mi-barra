package co.jose.mibarra;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Versión 25: lo que comparten Bloquear WhatsApp y Bloquear llamadas.
 * Se guarda en un archivo (no en preferencias) para que lo lean bien todos los procesos.
 */
final class Bloqueos {
    private Bloqueos() {}

    static final int NOTI_WA = 7101, NOTI_LL = 7102;

    private static File archivo(Context c) { return new File(c.getFilesDir(), "bloqueos.txt"); }

    static synchronized Properties leer(Context c) {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(archivo(c))) { p.load(in); } catch (Exception ignored) {}
        return p;
    }

    static synchronized void poner(Context c, String k, String v) {
        Properties p = leer(c);
        if (v == null) p.remove(k); else p.setProperty(k, v);
        try (FileOutputStream o = new FileOutputStream(archivo(c))) { p.store(o, null); } catch (Exception ignored) {}
    }

    static boolean activo(Context c, String k) { return "1".equals(leer(c).getProperty(k)); }

    /** Modo de llamadas: 0 todas, 1 solo desconocidos, 2 todas menos favoritos. */
    static int modo(Context c) { try { return Integer.parseInt(leer(c).getProperty("modo", "0")); } catch (Exception e) { return 0; } }

    static List<String> favoritos(Context c) {
        List<String> r = new ArrayList<>();
        for (String s : leer(c).getProperty("favoritos", "").split("\\|")) if (!s.trim().isEmpty()) r.add(s.trim());
        return r;
    }

    /** Solo los dígitos finales, para comparar +57 315… con 315… */
    static String limpio(String n) {
        String d = n == null ? "" : n.replaceAll("[^0-9]", "");
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    static void avisoFijo(Context c, int id, String titulo, String texto, String tipo) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("bloqueos", "Bloqueos", NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi = PendingIntent.getActivity(c, id, new Intent(c, BloqueosActivity.class).putExtra("tipo", tipo)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, "bloqueos").setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(titulo).setContentText(texto).setOngoing(true).setContentIntent(pi).build();
        nm.notify(id, n);
    }

    static void quitarAviso(Context c, int id) { c.getSystemService(NotificationManager.class).cancel(id); }
}
