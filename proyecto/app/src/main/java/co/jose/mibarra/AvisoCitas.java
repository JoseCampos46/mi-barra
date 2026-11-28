package co.jose.mibarra;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Versión 17: el día ANTES de una cita avisa tres veces (7 a. m., 12 m. y 6 p. m.),
 * y a las 12 de la noche, si el celular está cargando, prende el reloj de noche.
 */
public class AvisoCitas extends BroadcastReceiver {
    static final int[] HORAS = {7, 12, 18, 0};

    static void programar(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        Calendar ahora = Calendar.getInstance(), prox = null;
        int horaProx = -1;
        for (int h : HORAS) {
            Calendar k = Calendar.getInstance();
            k.set(Calendar.HOUR_OF_DAY, h);
            k.set(Calendar.MINUTE, 0);
            k.set(Calendar.SECOND, 0);
            k.set(Calendar.MILLISECOND, 0);
            if (!k.after(ahora)) k.add(Calendar.DAY_OF_MONTH, 1);
            if (prox == null || k.before(prox)) { prox = k; horaProx = h; }
        }
        PendingIntent pi = PendingIntent.getBroadcast(c, 91, new Intent(c, AvisoCitas.class).putExtra("hora", horaProx),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prox.getTimeInMillis(), pi); }
        catch (Exception e) { am.set(AlarmManager.RTC_WAKEUP, prox.getTimeInMillis(), pi); }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)) { programar(c); return; }
        int hora = i.getIntExtra("hora", -1);
        programar(c);
        if (hora == 0) { medianoche(c); return; }
        if (Puerta.hayChpRecordatorios(c)) return;   // versión 47: los avisos los da ChpRecordatorios
        if (hora < 0) return;
        Calendar k = Calendar.getInstance();
        k.add(Calendar.DAY_OF_MONTH, 1);
        List<Recordatorios.R> m = Recordatorios.delDia(c, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH));
        if (m.isEmpty()) return;
        SimpleDateFormat hf = new SimpleDateFormat("h:mm a", new Locale("es", "CO"));
        StringBuilder s = new StringBuilder();
        for (Recordatorios.R x : m) s.append("• ").append(hf.format(new Date(x.cuando))).append(" — ").append(x.texto).append('\n');
        String titulo = (hora == 7 ? "🌅 Mañana tiene " : hora == 12 ? "☀ Recuerde: mañana tiene " : "🌇 Último aviso: mañana tiene ")
                + m.size() + (m.size() == 1 ? " cita" : " citas");
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("citas", "Citas de mañana", NotificationManager.IMPORTANCE_HIGH));
        PendingIntent ver = PendingIntent.getActivity(c, 92, new Intent(c, RecordatoriosActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, "citas")
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(titulo)
                .setContentText(s.toString().trim())
                .setStyle(new Notification.BigTextStyle().bigText(s.toString().trim()))
                .setContentIntent(ver)
                .setAutoCancel(true)
                .build();
        try { nm.notify(93, n); } catch (Exception ignored) {}
    }

    /** A medianoche: si está cargando y nadie lo usa, prende el reloj de noche. */
    private void medianoche(Context c) {
        if (new java.io.File(c.getFilesDir(), "reloj_bloqueo_no").exists()) return;
        Intent bat = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        boolean carga = bat != null && bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        android.os.PowerManager pm = c.getSystemService(android.os.PowerManager.class);
        if (!carga || (pm != null && pm.isInteractive())) return;
        try {
            c.startActivity(new Intent(c, NocheActivity.class).putExtra("bloqueo", true).putExtra("medianoche", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Exception ignored) {}
    }
}
