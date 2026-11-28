package co.jose.mibarra;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ChpRecordatorios (versión 47): todas las alarmas en una sola app.
 * Cada alarma tiene sus horas (ej. 6:00, 14:00 y 18:00) y su forma de repetir:
 * todos los días, solo un día, los días que él escoja, o cada mes.
 * Al sonar: suena 1 minuto; si no la apaga, vuelve a sonar a los 5 minutos;
 * después de 3 veces se cierra del todo. Si la apaga, se apaga del todo hasta su próxima hora.
 */
public final class Alarmas {
    private Alarmas() {}

    static final int UN_DIA = 0, DIARIO = 1, DIAS = 2, MES = 3;
    static final String[] REPETIR = {"Solo un día", "Todos los días", "Días que escoja", "Cada mes"};
    static final String[] DIAS_CORTOS = {"L", "M", "M", "J", "V", "S", "D"};
    static final String[] DIAS_LARGOS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};
    static final long MINUTO = 60_000L, SUENA = MINUTO, ESPERA = 5 * MINUTO;
    static final int VECES = 3;

    static final class A {
        long id;
        String nombre = "", detalle = "";
        List<String> horas = new ArrayList<>();   // "HH:mm"
        int tipo = DIARIO;
        String fecha = "";                        // yyyy-MM-dd (solo un día / cada mes)
        int dias;                                 // bit 0 = lunes … bit 6 = domingo
        boolean activa = true;
    }

    // ---------------- Guardar y leer ----------------

    static File archivo(Context c) { return new File(c.getFilesDir(), "alarmas.tsv"); }

    static synchronized List<A> leer(Context c) {
        List<A> r = new ArrayList<>();
        try {
            for (String l : Files.readAllLines(archivo(c).toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p.length < 8) continue;
                A a = new A();
                a.id = Long.parseLong(p[0]);
                a.nombre = p[1];
                a.detalle = p[2];
                for (String h : p[3].split(",")) if (h.matches("\\d\\d:\\d\\d")) a.horas.add(h);
                a.tipo = Integer.parseInt(p[4]);
                a.fecha = p[5];
                a.dias = Integer.parseInt(p[6]);
                a.activa = "1".equals(p[7]);
                r.add(a);
            }
        } catch (Exception ignored) {}
        Collections.sort(r, (x, y) -> primeraHora(x).compareTo(primeraHora(y)));
        return r;
    }

    private static String primeraHora(A a) { return a.horas.isEmpty() ? "99:99" : a.horas.get(0); }

    static synchronized void guardar(Context c, List<A> lista) {
        StringBuilder s = new StringBuilder();
        for (A a : lista) {
            Collections.sort(a.horas);
            s.append(a.id).append('\t').append(limpio(a.nombre)).append('\t').append(limpio(a.detalle)).append('\t')
             .append(String.join(",", a.horas)).append('\t').append(a.tipo).append('\t').append(a.fecha).append('\t')
             .append(a.dias).append('\t').append(a.activa ? 1 : 0).append('\n');
        }
        try { Files.write(archivo(c).toPath(), s.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
        // Lo que se crea o cambia ahora no hace sonar horas que ya pasaron
        SharedPreferences sp = estado(c);
        if (sp.getLong("revisado", 0) < System.currentTimeMillis()) sp.edit().putLong("revisado", System.currentTimeMillis()).apply();
        programar(c);
    }

    private static String limpio(String s) { return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').trim(); }

    static SharedPreferences estado(Context c) { return c.getSharedPreferences("sonando", Context.MODE_PRIVATE); }

    // ---------------- Cuándo suena ----------------

    /** ¿Esta alarma suena el día k? */
    static boolean tocaElDia(A a, Calendar k) {
        if (!a.activa) return false;
        switch (a.tipo) {
            case DIARIO: return true;
            case DIAS: {
                int dow = k.get(Calendar.DAY_OF_WEEK);               // domingo = 1
                int bit = dow == Calendar.SUNDAY ? 6 : dow - 2;
                return (a.dias & (1 << bit)) != 0;
            }
            case UN_DIA: return a.fecha.equals(iso(k));
            case MES: {
                Calendar f = deIso(a.fecha);
                if (f == null) return false;
                int dia = f.get(Calendar.DAY_OF_MONTH);
                int ultimo = k.getActualMaximum(Calendar.DAY_OF_MONTH);
                return k.get(Calendar.DAY_OF_MONTH) == Math.min(dia, ultimo) && !k.before(sinHora(f));
            }
        }
        return false;
    }

    /** Todas las veces que suena entre «desde» (sin incluir) y «hasta» (incluido): pares {hora en ms, id}. */
    static List<long[]> entre(List<A> lista, long desde, long hasta) {
        List<long[]> r = new ArrayList<>();
        Calendar dia = Calendar.getInstance();
        dia.setTimeInMillis(desde);
        dia = sinHora(dia);
        for (int n = 0; n < 400 && dia.getTimeInMillis() <= hasta; n++) {
            for (A a : lista) {
                if (!tocaElDia(a, dia)) continue;
                for (String h : a.horas) {
                    Calendar k = (Calendar) dia.clone();
                    k.set(Calendar.HOUR_OF_DAY, Integer.parseInt(h.substring(0, 2)));
                    k.set(Calendar.MINUTE, Integer.parseInt(h.substring(3, 5)));
                    long t = k.getTimeInMillis();
                    if (t > desde && t <= hasta) r.add(new long[]{t, a.id});
                }
            }
            dia.add(Calendar.DAY_OF_MONTH, 1);
        }
        Collections.sort(r, (x, y) -> Long.compare(x[0], y[0]));
        return r;
    }

    /** La próxima vez que suena esta alarma (0 si ya no suena más). */
    static long proxima(A a, long desde) {
        List<A> una = new ArrayList<>();
        una.add(a);
        List<long[]> r = entre(una, desde, desde + 400L * 24 * 3600 * 1000);
        return r.isEmpty() ? 0 : r.get(0)[0];
    }

    static String textoRepetir(A a) {
        switch (a.tipo) {
            case DIARIO: return "Todos los días";
            case UN_DIA: { Calendar f = deIso(a.fecha); return f == null ? "Solo un día" : "Solo el " + fechaBonita(f); }
            case MES: { Calendar f = deIso(a.fecha); return f == null ? "Cada mes" : "Cada mes, el día " + f.get(Calendar.DAY_OF_MONTH); }
            case DIAS: {
                if (a.dias == 0) return "Ningún día escogido";
                if (a.dias == 0x7F) return "Todos los días";
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < 7; i++) if ((a.dias & (1 << i)) != 0) s.append(s.length() > 0 ? ", " : "").append(DIAS_LARGOS[i]);
                return "Cada " + s;
            }
        }
        return "";
    }

    static String horaBonita(String h) {
        int hh = Integer.parseInt(h.substring(0, 2)), mm = Integer.parseInt(h.substring(3, 5));
        String ap = hh < 12 ? "a. m." : "p. m.";
        int h12 = hh % 12 == 0 ? 12 : hh % 12;
        return h12 + ":" + String.format(Locale.US, "%02d", mm) + " " + ap;
    }

    static String horaBonita(long t) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(t);
        return horaBonita(String.format(Locale.US, "%02d:%02d", k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE)));
    }

    static String fechaBonita(Calendar k) {
        return new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "CO")).format(k.getTime());
    }

    static String iso(Calendar k) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(k.getTime()); }

    static Calendar deIso(String s) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s);
            Calendar k = Calendar.getInstance();
            k.setTime(d);
            return k;
        } catch (Exception e) { return null; }
    }

    static Calendar sinHora(Calendar k) {
        Calendar r = (Calendar) k.clone();
        r.set(Calendar.HOUR_OF_DAY, 0);
        r.set(Calendar.MINUTE, 0);
        r.set(Calendar.SECOND, 0);
        r.set(Calendar.MILLISECOND, 0);
        return r;
    }

    static A buscar(List<A> l, long id) { for (A a : l) if (a.id == id) return a; return null; }

    // ---------------- Lo que está sonando (o por repetir) ----------------
    // Cada línea: id|horaOriginal(ms)|veces que ya sonó|próxima vez que suena(ms)

    static synchronized List<long[]> pendientes(Context c) {
        List<long[]> r = new ArrayList<>();
        for (String x : estado(c).getString("lista", "").split(";")) {
            String[] p = x.split("\\|");
            if (p.length < 4) continue;
            try { r.add(new long[]{Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2]), Long.parseLong(p[3])}); }
            catch (Exception ignored) {}
        }
        return r;
    }

    static synchronized void guardarPendientes(Context c, List<long[]> l) {
        StringBuilder s = new StringBuilder();
        for (long[] x : l) s.append(s.length() > 0 ? ";" : "").append(x[0]).append('|').append(x[1]).append('|').append(x[2]).append('|').append(x[3]);
        estado(c).edit().putString("lista", s.toString()).apply();
    }

    /** Él tocó «Apagar»: las que estaban sonando se apagan del todo (no repiten). */
    static void apagarTodo(Context c) {
        guardarPendientes(c, new ArrayList<>());
        c.stopService(new Intent(c, Sonido.class));
        try { c.getSystemService(NotificationManager.class).cancel(NOTI); } catch (Exception ignored) {}
        programar(c);
    }

    // ---------------- Programar el despertador del sistema ----------------

    static void programar(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 4701, new Intent(c, Receptor.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        long ahora = System.currentTimeMillis();
        long revisado = Math.max(estado(c).getLong("revisado", ahora), ahora - 10 * MINUTO);
        long prox = 0;
        List<long[]> sig = entre(leer(c), revisado, ahora + 400L * 24 * 3600 * 1000);
        if (!sig.isEmpty()) prox = sig.get(0)[0];
        for (long[] p : pendientes(c)) if (prox == 0 || p[3] < prox) prox = p[3];
        if (prox == 0) return;
        long cuando = Math.max(prox, ahora + 1000);
        try {
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                PendingIntent ver = PendingIntent.getActivity(c, 4702, new Intent(c, AlarmasActivity.class), PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(cuando, ver), pi);
            } else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi);
        } catch (Exception e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi);
        }
    }

    /** Llega la hora: decide qué suena ahora y deja listo lo siguiente. */
    public static class Receptor extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            String accion = i.getAction();
            if (APAGAR.equals(accion)) { apagarTodo(c); return; }
            long ahora = System.currentTimeMillis();
            SharedPreferences sp = estado(c);
            List<long[]> pend = pendientes(c);
            // 1) Las horas nuevas que llegaron (si el celular estuvo apagado, solo las de los últimos 10 minutos)
            long revisado = Math.max(sp.getLong("revisado", ahora), ahora - 10 * MINUTO);
            for (long[] e : entre(leer(c), revisado, ahora + 30_000)) {
                boolean ya = false;
                for (long[] p : pend) if (p[0] == e[1] && p[1] == e[0]) { ya = true; break; }
                if (!ya) pend.add(new long[]{e[1], e[0], 0, e[0]});
            }
            sp.edit().putLong("revisado", ahora + 30_000).apply();
            if (Intent.ACTION_BOOT_COMPLETED.equals(accion) || "android.intent.action.MY_PACKAGE_REPLACED".equals(accion)) {
                // al prender el celular solo se reprograma; lo que ya estaba pendiente sigue su curso
            }
            // 2) Las que tocan sonar ahora
            boolean suena = false;
            List<long[]> quedan = new ArrayList<>();
            for (long[] p : pend) {
                if (p[3] <= ahora + 30_000) {
                    p[2]++;
                    suena = true;
                    if (p[2] < VECES) { p[3] = ahora + SUENA + ESPERA; quedan.add(p); }
                    else { p[3] = Long.MAX_VALUE; quedan.add(p); }   // tercera vez: suena y después se quita
                } else quedan.add(p);
            }
            guardarPendientes(c, quedan);
            if (suena) {
                try { c.startForegroundService(new Intent(c, Sonido.class)); } catch (Exception e) { mostrar(c); }
            }
            programar(c);
        }
    }

    static final String APAGAR = "co.jose.chprecordatorios.APAGAR";
    static final int NOTI = 4703;

    /** Lo que suena ahora (para la pantalla): las que ya sonaron al menos una vez y siguen pendientes. */
    static List<long[]> sonandoAhora(Context c) {
        List<long[]> r = new ArrayList<>();
        for (long[] p : pendientes(c)) if (p[2] > 0) r.add(p);
        return r;
    }

    static void mostrar(Context c) {
        try {
            c.startActivity(new Intent(c, SuenaActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Exception ignored) {}
    }

    static String resumen(Context c) {
        List<A> l = leer(c);
        StringBuilder s = new StringBuilder();
        for (long[] p : sonandoAhora(c)) {
            A a = buscar(l, p[0]);
            if (a == null) continue;
            if (s.length() > 0) s.append(" · ");
            s.append(a.nombre);
        }
        return s.length() == 0 ? "Alarma" : s.toString();
    }

    /** Suena 1 minuto (como alarma, aunque el celular esté en silencio) y muestra la pantalla. */
    public static class Sonido extends Service {
        private MediaPlayer mp;
        private final Handler h = new Handler(Looper.getMainLooper());
        private final Runnable fin = () -> {
            // pasó el minuto: deja de sonar; si no la apagó, vuelve a sonar a los 5 minutos
            List<long[]> quedan = new ArrayList<>();
            for (long[] p : pendientes(this)) if (p[3] != Long.MAX_VALUE) quedan.add(p);   // la tercera vez ya no vuelve
            guardarPendientes(this, quedan);
            programar(this);
            SuenaActivity.cerrarSiAbierta();
            stopSelf();
        };

        @Override public IBinder onBind(Intent i) { return null; }

        @Override
        public int onStartCommand(Intent i, int f, int id) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel canal = new NotificationChannel("alarmas47", "Alarmas", NotificationManager.IMPORTANCE_HIGH);
            canal.setSound(null, null);
            canal.setBypassDnd(true);
            nm.createNotificationChannel(canal);
            PendingIntent ver = PendingIntent.getActivity(this, 4704, new Intent(this, SuenaActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent apagar = PendingIntent.getBroadcast(this, 4705, new Intent(this, Receptor.class).setAction(APAGAR),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(this, "alarmas47")
                    .setSmallIcon(R.drawable.ic_record)
                    .setContentTitle("⏰ " + horaBonita(System.currentTimeMillis()))
                    .setContentText(resumen(this))
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setFullScreenIntent(ver, true)
                    .setContentIntent(ver)
                    .setOngoing(true)
                    .addAction(new Notification.Action.Builder(null, "✅ Apagar", apagar).build())
                    .build();
            try {
                if (android.os.Build.VERSION.SDK_INT >= 34)
                    startForeground(NOTI, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                else startForeground(NOTI, n);
            } catch (Exception e) { try { nm.notify(NOTI, n); } catch (Exception ignored) {} }
            mostrar(this);
            sonar();
            h.removeCallbacks(fin);
            h.postDelayed(fin, SUENA);
            return START_NOT_STICKY;
        }

        private void sonar() {
            try {
                AudioManager au = getSystemService(AudioManager.class);
                int max = au.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                if (au.getStreamVolume(AudioManager.STREAM_ALARM) < max / 2) au.setStreamVolume(AudioManager.STREAM_ALARM, max * 3 / 4, 0);
            } catch (Exception ignored) {}
            if (mp == null) {
                try {
                    Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                    mp = new MediaPlayer();
                    mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                    mp.setDataSource(this, u);
                    mp.setLooping(true);
                    mp.prepare();
                    mp.start();
                } catch (Exception e) { mp = null; }
            }
            try {
                Vibrator v = getSystemService(Vibrator.class);
                if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 600}, 0));
            } catch (Exception ignored) {}
        }

        @Override
        public void onDestroy() {
            h.removeCallbacks(fin);
            try { if (mp != null) { mp.stop(); mp.release(); } } catch (Exception ignored) {}
            mp = null;
            try { getSystemService(Vibrator.class).cancel(); } catch (Exception ignored) {}
            try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Exception ignored) {}
            SuenaActivity.cerrarSiAbierta();
            super.onDestroy();
        }
    }

    // ---------------- Para el almanaque de ChpInicio (formato viejo de recordatorios) ----------------

    /** Arma el archivo que lee el almanaque del inicio: lo de 40 días atrás y 120 adelante. */
    static File paraElInicio(Context c) {
        File f = new File(c.getCacheDir(), "recordatorios.tsv");
        long ahora = System.currentTimeMillis();
        List<A> l = leer(c);
        StringBuilder s = new StringBuilder();
        long n = 1;
        for (long[] e : entre(l, ahora - 40L * 24 * 3600 * 1000, ahora + 120L * 24 * 3600 * 1000)) {
            A a = buscar(l, e[1]);
            if (a == null) continue;
            String t = a.nombre + (a.detalle.isEmpty() ? "" : " · " + a.detalle);
            s.append(n++).append('\t').append(e[0]).append('\t').append(limpio(t)).append('\t').append(0).append('\t')
             .append(e[0] < ahora ? 1 : 0).append('\n');
        }
        try { Files.write(f.toPath(), s.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
        return f;
    }

    // ---------------- Pasar los recordatorios viejos de ChpBar ----------------

    /** Trae los recordatorios que tenía ChpBar y los vuelve alarmas. Devuelve cuántos pasó. */
    static int traerDeChpBar(Context c) {
        File tmp = new File(c.getCacheDir(), "viejos.tsv");
        if (!Puerta.traer(c, "recordatorios.tsv", tmp)) return -1;
        List<A> lista = leer(c);
        long ahora = System.currentTimeMillis();
        int n = 0;
        try {
            for (String l : Files.readAllLines(tmp.toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p.length < 5 || "1".equals(p[4])) continue;
                long cuando = Long.parseLong(p[1]);
                int rep = Integer.parseInt(p[3]);
                if (rep == 0 && cuando < ahora) continue;
                Calendar k = Calendar.getInstance();
                k.setTimeInMillis(cuando);
                A a = new A();
                a.id = ahora + n;
                a.nombre = p[2];
                a.horas.add(String.format(Locale.US, "%02d:%02d", k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE)));
                a.fecha = iso(k);
                if (rep == 1) a.tipo = DIARIO;
                else if (rep == 2) { a.tipo = DIAS; int dow = k.get(Calendar.DAY_OF_WEEK); a.dias = 1 << (dow == Calendar.SUNDAY ? 6 : dow - 2); }
                else if (rep == 3) a.tipo = MES;
                else a.tipo = UN_DIA;   // una sola vez (o cada año): queda para ese día
                boolean repetida = false;
                for (A x : lista) if (x.nombre.equals(a.nombre) && x.horas.equals(a.horas) && x.tipo == a.tipo) { repetida = true; break; }
                if (repetida) continue;
                lista.add(a);
                n++;
            }
        } catch (Exception ignored) {}
        if (n > 0) guardar(c, lista);
        return n;
    }
}
