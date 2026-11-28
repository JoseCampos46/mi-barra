package co.jose.mibarra.tel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

/** Versión 37: aviso "Grabando llamada" para que Android no le quite el micrófono a la grabación. */
public class GrabandoService extends Service {

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("grabando_llamada", "Grabando llamada", NotificationManager.IMPORTANCE_LOW));
            Notification n = new Notification.Builder(this, "grabando_llamada")
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle("🔴 Grabando la llamada")
                    .setContentText("Se guarda sola al colgar")
                    .setOngoing(true)
                    .build();
            startForeground(78, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } catch (Exception e) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }
}
