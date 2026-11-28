package co.jose.mibarra;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** Los recordatorios guardados y la alarma que los hace sonar (aunque el celular esté en silencio). */
public final class Recordatorios {
    private Recordatorios() {}

    static final String[] REPETIR = {"Una sola vez", "Todos los días", "Cada semana", "Cada mes", "Cada año"};

    static final class R {
        long id, cuando;   // cuándo suena (milisegundos)
        String texto;
        int repetir;       // 0 no, 1 diario, 2 semanal, 3 mensual, 4 anual
        boolean hecho;
    }

    static File archivo(Context c) { return new File(c.getFilesDir(), "recordatorios.tsv"); }

    static synchronized List<R> leer(Context c) {
        List<R> r = new ArrayList<>();
        try {
            for (String l : Files.readAllLines(archivo(c).toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p.length < 5) continue;
                R x = new R();
                x.id = Long.parseLong(p[0]); x.cuando = Long.parseLong(p[1]); x.texto = p[2];
                x.repetir = Integer.parseInt(p[3]); x.hecho = "1".equals(p[4]);
                r.add(x);
            }
        } catch (Exception ignored) {}
        Collections.sort(r, (a, b) -> Long.compare(a.cuando, b.cuando));
        return r;
    }

    static synchronized void guardar(Context c, List<R> lista) {
        StringBuilder s = new StringBuilder();
        for (R x : lista)
            s.append(x.id).append('\t').append(x.cuando).append('\t').append(x.texto.replace('\t', ' ').replace('\n', ' '))
             .append('\t').append(x.repetir).append('\t').append(x.hecho ? 1 : 0).append('\n');
        try { Files.write(archivo(c).toPath(), s.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
        programar(c);
    }

    /** Los del día (para el almanaque y para el reloj del Launcher). */
    static List<R> delDia(Context c, int anio, int mes, int dia) {
        List<R> r = new ArrayList<>();
        Calendar k = Calendar.getInstance();
        for (R x : leer(c)) {
            k.setTimeInMillis(x.cuando);
            if (k.get(Calendar.YEAR) == anio && k.get(Calendar.MONTH) == mes && k.get(Calendar.DAY_OF_MONTH) == dia) r.add(x);
        }
        return r;
    }

    static List<R> deHoy(Context c) {
        Calendar k = Calendar.getInstance();
        return delDia(c, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH));
    }

    static long siguiente(R x) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(x.cuando);
        switch (x.repetir) {
            case 1: k.add(Calendar.DAY_OF_MONTH, 1); break;
            case 2: k.add(Calendar.WEEK_OF_YEAR, 1); break;
            case 3: k.add(Calendar.MONTH, 1); break;
            case 4: k.add(Calendar.YEAR, 1); break;
            default: return 0;
        }
        return k.getTimeInMillis();
    }

    /** Programa la alarma del próximo recordatorio pendiente. */
    static void programar(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 77, new Intent(c, Alarma.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        // Versión 47: las alarmas ahora las hace sonar ChpRecordatorios (app aparte). Aquí ya no suena nada.
        if (!Puerta.CHPBAR.equals(c.getPackageName())) return;
        try { AvisoCitas.programar(c); } catch (Exception ignored) {}   // versión 17: avisos del día antes (y medianoche)
        if (Puerta.hayChpRecordatorios(c)) return;
        long ahora = System.currentTimeMillis(), prox = 0;
        for (R x : leer(c)) if (!x.hecho && x.cuando > ahora - 60000 && (prox == 0 || x.cuando < prox)) prox = x.cuando;
        if (prox == 0) return;
        try {
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                PendingIntent ver = PendingIntent.getActivity(c, 78, new Intent(c, RecordatoriosActivity.class), PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(Math.max(prox, ahora + 1000), ver), pi);
            } else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.max(prox, ahora + 1000), pi);
        } catch (Exception e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.max(prox, ahora + 1000), pi);
        }
    }

    /** Suena la alarma: avisa los que tocan ahora y deja listo el siguiente. */
    public static class Alarma extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)) { programar(c); return; }
            if (Puerta.hayChpRecordatorios(c)) { programar(c); return; }   // versión 47: suena en ChpRecordatorios
            long ahora = System.currentTimeMillis();
            List<R> todos = leer(c);
            StringBuilder texto = new StringBuilder();
            List<R> nuevos = new ArrayList<>();
            for (R x : todos) {
                if (!x.hecho && x.cuando <= ahora + 30000) {
                    if (texto.length() > 0) texto.append(". ");
                    texto.append(x.texto);
                    long sig = siguiente(x);
                    x.hecho = true;
                    if (sig > 0) {
                        R n = new R();
                        n.id = System.currentTimeMillis() + nuevos.size(); n.cuando = sig; n.texto = x.texto; n.repetir = x.repetir;
                        nuevos.add(n);
                        x.repetir = 0;
                    }
                }
            }
            todos.addAll(nuevos);
            guardar(c, todos);
            if (texto.length() == 0) return;
            // Que se escuche: si el volumen de alarma está en cero, se sube
            try {
                android.media.AudioManager au = c.getSystemService(android.media.AudioManager.class);
                int max = au.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM);
                if (au.getStreamVolume(android.media.AudioManager.STREAM_ALARM) < max / 2)
                    au.setStreamVolume(android.media.AudioManager.STREAM_ALARM, max * 3 / 4, 0);
            } catch (Exception ignored) {}
            try {
                c.startForegroundService(new Intent(c, RelojService.class).setAction(RelojService.RECORDAR).putExtra("texto", texto.toString()));
            } catch (Exception ignored) {}
        }
    }
}
