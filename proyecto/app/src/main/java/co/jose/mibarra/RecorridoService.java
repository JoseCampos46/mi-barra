package co.jose.mibarra;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Graba el recorrido con GPS, aunque la pantalla esté apagada. También vigila si se sale de la ruta. */
public class RecorridoService extends Service implements LocationListener {

    static final String INICIAR = "co.jose.mibarra.REC_INICIAR";
    static final String PARAR = "co.jose.mibarra.REC_PARAR";
    static final String BOYA = "co.jose.mibarra.REC_BOYA";

    private LocationManager lm;
    private TextToSpeech voz;
    private boolean vozLista;
    private File archivo;
    private String id;
    private long inicio;
    // Descansos: cada cuántos minutos manejando, y cuánto debe durar
    private int descansoCada, descansoDur;
    private long ultimoMov, ultimoDescanso, ultimoRecordatorio;
    private final android.os.Handler reloj = new android.os.Handler(Looper.getMainLooper());
    private final Runnable revisarDescanso = new Runnable() {
        @Override public void run() {
            long ahora = System.currentTimeMillis();
            boolean manejando = ahora - ultimoMov < 3 * 60000;
            if (archivo != null && descansoCada > 0 && manejando
                    && ahora - ultimoDescanso >= descansoCada * 60000L && ahora - ultimoRecordatorio > 15 * 60000L) {
                ultimoRecordatorio = ahora;
                long min = (ahora - ultimoDescanso) / 60000;
                String t = "Lleva " + (min >= 60 ? (min / 60) + (min / 60 == 1 ? " hora" : " horas") + (min % 60 >= 10 ? " y " + (min % 60) + " minutos" : "") : min + " minutos")
                        + " de viaje. Busque dónde parar y descanse " + descansoDur + " minutos.";
                decir(t);
                try {
                    getSystemService(NotificationManager.class).notify(13, new Notification.Builder(RecorridoService.this, "alertas")
                            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("☕ Hora de descansar")
                            .setContentText(t).setAutoCancel(true).build());
                } catch (Exception ignored) {}
            }
            reloj.postDelayed(this, 60000);
        }
    };
    private double distancia, velMax;
    private Location ultima;
    private int limite;
    private long ultimoAvisoVel;

    // Seguir una ruta guardada
    private List<double[]> ruta;             // {lat, lon}
    private final List<String[]> boyasRuta = new ArrayList<>(); // {lat, lon, tipo, nombre}
    private final Set<Integer> avisadas = new HashSet<>();
    private int lejos;
    private boolean fuera;
    private double[] puntoSalida;

    static File carpeta(android.content.Context c) {
        File f = new File(c.getFilesDir(), "recorridos");
        f.mkdirs();
        return f;
    }

    static File estado(android.content.Context c) { return new File(carpeta(c), "estado.txt"); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String a = i == null ? null : i.getAction();
        if (PARAR.equals(a)) { parar(); return START_NOT_STICKY; }
        if (BOYA.equals(a)) {
            if (archivo != null && ultima != null)
                boya(i.getStringExtra("tipo"), i.getStringExtra("nombre"));
            return START_STICKY;
        }
        if (INICIAR.equals(a) && archivo == null) iniciar(i);
        return START_STICKY;
    }

    private void iniciar(Intent i) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("recorrido", "Recorrido", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("alertas", "Alertas de ruta", NotificationManager.IMPORTANCE_HIGH));
        startForeground(11, aviso("Iniciando…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);

        id = String.valueOf(System.currentTimeMillis());
        archivo = new File(carpeta(this), id + ".txt");
        inicio = System.currentTimeMillis();
        distancia = 0; velMax = 0; ultima = null; fuera = false; lejos = 0;
        limite = i.getIntExtra("limite", 0);
        descansoCada = i.getIntExtra("descanso", 120);
        descansoDur = i.getIntExtra("descansoDur", 15);
        ultimoMov = ultimoDescanso = System.currentTimeMillis();
        ultimoRecordatorio = 0;
        reloj.removeCallbacks(revisarDescanso);
        reloj.postDelayed(revisarDescanso, 60000);
        String nombre = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new java.util.Date());
        escribir("N\tRecorrido " + nombre + "\n");

        ruta = null;
        boyasRuta.clear();
        avisadas.clear();
        String seguir = i.getStringExtra("seguir");
        if (seguir != null) {
            Recorrido r = Recorrido.leer(new File(carpeta(this), seguir + ".txt"));
            if (r != null) {
                ruta = r.puntos;
                boyasRuta.addAll(r.boyas);
                escribir("S\t" + r.nombre + "\n");
            }
        }

        voz = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) {
                voz.setLanguage(new Locale("es", "CO"));
                vozLista = true;
            }
        });
        lm = getSystemService(LocationManager.class);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5, this, Looper.getMainLooper());
        } catch (SecurityException e) {
            parar();
            return;
        }
        guardarEstado();
    }

    private Notification aviso(String texto) {
        PendingIntent abrir = PendingIntent.getActivity(this, 0,
                new Intent(this, RecorridoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent parar = PendingIntent.getService(this, 1,
                new Intent(this, RecorridoService.class).setAction(PARAR), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "recorrido")
                .setContentTitle("🛣️ Grabando recorrido")
                .setContentText(texto)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(abrir)
                .addAction(new Notification.Action.Builder(null, "⏹ Parar y guardar", parar).build())
                .setOngoing(true)
                .build();
    }

    @Override
    public void onLocationChanged(Location l) {
        if (archivo == null) return;
        if (l.hasAccuracy() && l.getAccuracy() > 35) return;
        if (ultima != null) {
            float d = ultima.distanceTo(l);
            if (d < 3) return;
            distancia += d;
        }
        ultima = l;
        double v = l.hasSpeed() ? l.getSpeed() * 3.6 : 0;
        velMax = Math.max(velMax, v);
        if (v > 8) {
            long ahora = System.currentTimeMillis();
            // Si estuvo quieto más de 8 minutos, eso cuenta como descanso
            if (ahora - ultimoMov > Math.max(8, descansoDur / 2) * 60000L) {
                ultimoDescanso = ahora;
                if (descansoCada > 0) decir("Buen descanso. Sigamos con cuidado.");
            }
            ultimoMov = ahora;
        }
        escribir(String.format(Locale.US, "P\t%.6f\t%.6f\t%d\t%.1f\n", l.getLatitude(), l.getLongitude(), l.getTime(), v));

        // Límite de velocidad
        if (limite > 0 && v > limite + 3 && System.currentTimeMillis() - ultimoAvisoVel > 30000) {
            ultimoAvisoVel = System.currentTimeMillis();
            decir("Atención, va a " + Math.round(v) + " kilómetros por hora. El límite es " + limite);
        }
        vigilarRuta(l);
        vigilarBoyas(l);
        getSystemService(NotificationManager.class).notify(11,
                aviso(CalculadoraActivity.dec(distancia / 1000, 2) + " km · " + Math.round(v) + " km/h"));
        guardarEstado();
    }

    private void vigilarRuta(Location l) {
        if (ruta == null || ruta.isEmpty()) return;
        double min = Double.MAX_VALUE;
        double[] cerca = null;
        for (double[] p : ruta) {
            double d = Recorrido.distancia(l.getLatitude(), l.getLongitude(), p[0], p[1]);
            if (d < min) { min = d; cerca = p; }
        }
        if (!fuera) {
            lejos = min > 120 ? lejos + 1 : 0;
            if (lejos >= 3) {
                fuera = true;
                puntoSalida = cerca;
                decir("Atención, se salió de la ruta. Marqué el punto");
                // Versión 29: el punto queda como boya y listo para mandar por WhatsApp
                try { boya("Salida", "Me salí de la ruta"); } catch (Exception ignored) {}
                Direcciones.guardarPuntoSalida(this, cerca[0], cerca[1]);
                alertaFuera(cerca);
            }
        } else if (min < 60) {
            fuera = false;
            lejos = 0;
            decir("Va de nuevo por la ruta");
            getSystemService(NotificationManager.class).cancel(12);
        }
    }

    private void alertaFuera(double[] p) {
        Intent nav = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + p[0] + "," + p[1]));
        nav.setPackage("com.google.android.apps.maps");
        PendingIntent pi = PendingIntent.getActivity(this, 2, nav, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "alertas")
                .setContentTitle("⚠️ Se salió de la ruta")
                .setContentText("Toque para volver con Google Maps al punto donde se salió")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(null, "📤 Enviar el punto por WhatsApp", PendingIntent.getActivity(this, 3,
                        new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.whatsapp")
                                .putExtra(Intent.EXTRA_TEXT, String.format(Locale.US, "📍 Me salí de la ruta aquí:\nhttps://maps.google.com/?q=%.6f,%.6f", p[0], p[1]))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)).build())
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(12, n);
    }

    private void vigilarBoyas(Location l) {
        for (int k = 0; k < boyasRuta.size(); k++) {
            if (avisadas.contains(k)) continue;
            String[] b = boyasRuta.get(k);
            double d = Recorrido.distancia(l.getLatitude(), l.getLongitude(),
                    Double.parseDouble(b[0]), Double.parseDouble(b[1]));
            String tipo = b[2];
            double radio = "Fotomulta".equals(tipo) ? 300 : 200;
            if (d < radio) {
                avisadas.add(k);
                String nombre = b[3].isEmpty() ? "" : ", " + b[3];
                if ("Fotomulta".equals(tipo)) decir("Fotomulta a " + Math.round(d / 50) * 50 + " metros" + nombre);
                else if ("Peligro".equals(tipo)) decir("Peligro adelante" + nombre);
                else if ("Parada".equals(tipo) && !b[3].isEmpty()) decir("Aquí es " + b[3]);   // Versión 33
                else decir(tipo + " cerca" + nombre);
            }
        }
    }

    private void boya(String tipo, String nombre) {
        if (tipo == null) tipo = "Parada";
        if (nombre == null) nombre = "";
        escribir(String.format(Locale.US, "B\t%.6f\t%.6f\t%d\t%s\t%s\n", ultima.getLatitude(), ultima.getLongitude(),
                System.currentTimeMillis(), tipo, nombre.replace('\t', ' ').replace('\n', ' ')));
        decir("Boya marcada");
        // Versión 33: la boya queda con su calle y carrera, para recordar mejor
        final double la = ultima.getLatitude(), lo = ultima.getLongitude();
        new Thread(() -> {
            try {
                String d = Lugar.describir(this, la, lo).split("\n")[0].trim();
                if (!d.isEmpty()) escribir("D\t" + d.replace('\t', ' ') + "\n");
            } catch (Exception ignored) {}
        }, "calles").start();
    }

    private void decir(String s) {
        if (vozLista) voz.speak(s, TextToSpeech.QUEUE_ADD, null, "mibarra" + System.nanoTime());
    }

    private synchronized void escribir(String s) {
        try (FileWriter w = new FileWriter(archivo, StandardCharsets.UTF_8, true)) {
            w.write(s);
        } catch (Exception ignored) {}
    }

    /** La pantalla del recorrido lee este archivo cada segundo. */
    private void guardarEstado() {
        String s = (archivo != null ? 1 : 0) + "\t" + id + "\t" + distancia + "\t" + inicio + "\t"
                + (ultima != null && ultima.hasSpeed() ? ultima.getSpeed() * 3.6 : 0) + "\t" + velMax + "\t"
                + (fuera ? 1 : 0) + "\t"
                + (puntoSalida != null ? puntoSalida[0] + "\t" + puntoSalida[1] : "0\t0") + "\t"
                + (ultima != null ? ultima.getLatitude() + "\t" + ultima.getLongitude() : "0\t0") + "\n";
        try {
            Files.write(estado(this).toPath(), s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private void parar() {
        reloj.removeCallbacks(revisarDescanso);
        if (lm != null) lm.removeUpdates(this);
        if (archivo != null && ultima == null) {
            archivo.delete(); // no alcanzó a tomar ningún punto
            // Versión 41: antes se borraba callado; ahora se avisa para revisar el permiso de ubicación
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> android.widget.Toast.makeText(this,
                    "⚠ El recorrido no se guardó: el celular no dio la ubicación. Revise Ajustes → Aplicaciones → Mi Barra → Ubicación: «Permitir todo el tiempo» y Ahorro de batería: «Sin restricciones».",
                    android.widget.Toast.LENGTH_LONG).show());
        } else if (archivo != null) {
            // Versión 41: copia en Descargas/MiBarra/GPS para que no se pierda y él la vea
            try {
                Recorrido r = Recorrido.leer(archivo);
                Direcciones.copiarRuta(this, archivo, r != null ? r.nombre : "Recorrido");
            } catch (Exception ignored) { Direcciones.copiarRuta(this, archivo, "Recorrido"); }
        }
        archivo = null;
        estado(this).delete();
        if (voz != null) { voz.shutdown(); voz = null; vozLista = false; }
        getSystemService(NotificationManager.class).cancel(12);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (lm != null) lm.removeUpdates(this);
        if (voz != null) voz.shutdown();
        super.onDestroy();
    }

    // Estos tres son obligatorios en Android 10
    @Override public void onStatusChanged(String p, int s, Bundle b) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {
        decir("Se apagó el GPS del celular");
    }
}
