package co.jose.mibarra;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Reloj de noche (versión 17): también sale en la pantalla de bloqueo.
 * Luz muy suave; si la sube con la barrita, a los 10 segundos vuelve sola a la suave.
 * Muestra las citas de hoy y de mañana, y tiene Radio y "Mi carpeta" (audios al azar).
 * Versión 18: quieto y de un solo tono suave. La opacidad la escoge él con la barrita y queda guardada;
 * de medianoche a las 6 a. m. se pone todavía más opaco. Tocar la pantalla: pide clave, patrón o huella.
 */
public class NocheActivity extends Activity {
    /** Tonos suaves: ámbar, rojo apagado, verde apagado, gris. Solo cambian con el botón 🎨. */
    static final int[] COLORES = {0xFFC08A4A, 0xFFB05A50, 0xFF6E9A6E, 0xFF9A9A9A};
    static final float LUZ_SUAVE = 0.02f;
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView hora, fecha, extra, citas;
    private LinearLayout caja;
    private SeekBar luz;
    private int color;
    private boolean bloqueo;
    private final Random r = new Random();
    private final Locale es = new Locale("es", "CO");

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        bloqueo = getIntent().getBooleanExtra("bloqueo", false);
        setShowWhenLocked(true);
        if (getIntent().getBooleanExtra("medianoche", false)) setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        android.content.SharedPreferences p = getSharedPreferences("barra", MODE_PRIVATE);
        color = Math.min(p.getInt("nocheTono", 0), COLORES.length - 1);

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.BLACK);
        caja = new LinearLayout(this);
        caja.setOrientation(LinearLayout.VERTICAL);
        caja.setGravity(Gravity.CENTER_HORIZONTAL);
        hora = texto(92, true);
        fecha = texto(22, false);
        extra = texto(17, false);
        citas = texto(17, false);
        citas.setPadding(dp(20), dp(14), dp(20), 0);
        caja.addView(hora); caja.addView(fecha); caja.addView(extra); caja.addView(citas);
        raiz.addView(caja, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        // Abajo: luz, radio, mi carpeta, desbloquear
        LinearLayout abajo = new LinearLayout(this);
        abajo.setOrientation(LinearLayout.VERTICAL);
        abajo.setPadding(dp(16), 0, dp(16), dp(24));
        LinearLayout fl = new LinearLayout(this);
        fl.setGravity(Gravity.CENTER_VERTICAL);
        fl.addView(texto(18, false, "◐"));
        luz = new SeekBar(this);
        luz.setMax(95);
        luz.setProgress(p.getInt("nocheOpaco", 45) - 5);
        luz.setAlpha(0.5f);
        luz.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean u) { if (u) { p.edit().putInt("nocheOpaco", v + 5).apply(); pintar(); } }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        fl.addView(luz, new LinearLayout.LayoutParams(0, dp(44), 1f));
        abajo.addView(fl);
        LinearLayout bs = new LinearLayout(this);
        String[] tx = {"📻 Radio", "🎧 Mi carpeta", "⏹", "🎨 Tono"};
        Runnable[] ac = {
                () -> SonidoService.radioFavorita(this),
                () -> SonidoService.miCarpeta(this),
                () -> SonidoService.parar(this),
                () -> { color = (color + 1) % COLORES.length; p.edit().putInt("nocheTono", color).apply(); pintar(); }};
        for (int i = 0; i < tx.length; i++) {
            Runnable rr = ac[i];
            TextView t = texto(15, false, tx[i]);
            t.setPadding(dp(4), dp(10), dp(4), dp(10));
            t.setBackground(Comun.fondo(this, 0xFF151515, 12));
            t.setOnClickListener(v -> rr.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, i == 2 ? 0.6f : 1f);
            lp.setMargins(dp(3), dp(6), dp(3), 0);
            bs.addView(t, lp);
        }
        abajo.addView(bs);
        raiz.addView(abajo, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // Un toque en cualquier parte (fuera de los botones): pide clave, patrón o huella
        raiz.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) desbloquear();
            return true;
        });
        setContentView(raiz);
        pintar();
        ponerLuz(LUZ_SUAVE);
    }

    private int dp(float v) { return Util.dp(this, v); }

    private TextView texto(float t, boolean fina) { return texto(t, fina, ""); }

    private TextView texto(float t, boolean fina, String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(t);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(0xFF8A8A8A);
        if (fina) v.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        return v;
    }

    private final Runnable suave = () -> { if (luz != null) luz.setProgress(0); ponerLuz(LUZ_SUAVE); };

    private void volverSuave() { h.removeCallbacks(suave); h.postDelayed(suave, 10000); }

    private void ponerLuz(float f) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.01f, Math.min(1f, f));
        getWindow().setAttributes(lp);
    }

    /** La pantalla queda prendida solo si está cargando (o si lo abrió usted a propósito). */
    private void pantallaPrendida() {
        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        boolean carga = bat != null && bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        if (carga || !bloqueo) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void desbloquear() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked()) {
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { finish(); }
            });
        } else finish();
    }

    @Override protected void onResume() { super.onResume(); h.post(tic); botonFlotante(true); pantallaPrendida(); ponerLuz(LUZ_SUAVE); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tic); h.removeCallbacks(suave); botonFlotante(false); }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        bloqueo = i.getBooleanExtra("bloqueo", false);
    }

    /** El botón naranja no estorba sobre el reloj de noche. */
    private void botonFlotante(boolean ocultar) {
        try { sendBroadcast(new Intent(BarraService.AVISO_BARRA).setPackage(getPackageName()).putExtra("ocultarTemporal", ocultar)); }
        catch (Exception ignored) {}
    }

    private final Runnable tic = new Runnable() {
        @Override public void run() {
            pintar();
            pantallaPrendida();
            h.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50);
        }
    };

    private void pintar() {
        hora.setText(new SimpleDateFormat("h:mm", es).format(new Date()));
        String f = new SimpleDateFormat("EEEE d 'de' MMMM", es).format(new Date());
        fecha.setText(Character.toUpperCase(f.charAt(0)) + f.substring(1));
        StringBuilder s = new StringBuilder();
        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat != null) {
            int lv = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            boolean carga = bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            if (lv >= 0) s.append(carga ? "⚡ " : "🔋 ").append(lv * 100 / sc).append(" %");
        }
        AlarmManager am = getSystemService(AlarmManager.class);
        if (am != null && am.getNextAlarmClock() != null)
            s.append("   ⏰ ").append(new SimpleDateFormat("EEE h:mm a", es).format(new Date(am.getNextAlarmClock().getTriggerTime())));
        extra.setText(s);
        citas.setText(textoCitas());
        int c = COLORES[color];
        float o = getSharedPreferences("barra", MODE_PRIVATE).getInt("nocheOpaco", 45) / 100f;
        int hh = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hh < 6) o *= 0.5f;                       // de medianoche a las 6: más opaco
        for (TextView t : new TextView[]{hora, fecha, extra, citas}) { t.setTextColor(c); t.setAlpha(Math.max(0.05f, o)); }
        // Solo una vez por hora se corre unos pocos puntos (no se nota) para no marcar la pantalla
        int paso = hh % 4;
        caja.setTranslationX((paso - 1.5f) * dp(2));
        caja.setTranslationY((paso % 2 == 0 ? -1 : 1) * dp(2));
    }

    /** Las citas que quedan hoy y las de mañana. */
    private String textoCitas() {
        SimpleDateFormat hf = new SimpleDateFormat("h:mm a", es);
        StringBuilder s = new StringBuilder();
        long ahora = System.currentTimeMillis();
        for (Recordatorios.R x : Recordatorios.deHoy(this))
            if (!x.hecho && x.cuando > ahora) s.append("📅 Hoy ").append(hf.format(new Date(x.cuando))).append(" · ").append(x.texto).append('\n');
        Calendar k = Calendar.getInstance();
        k.add(Calendar.DAY_OF_MONTH, 1);
        List<Recordatorios.R> m = Recordatorios.delDia(this, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH));
        for (Recordatorios.R x : m) s.append("📅 Mañana ").append(hf.format(new Date(x.cuando))).append(" · ").append(x.texto).append('\n');
        return s.toString().trim();
    }

    @Override
    protected void onPostCreate(android.os.Bundle b) {
        super.onPostCreate(b);
        Util.ponerX(this);   // v44: ✕ para cerrar, arriba a la derecha
    }
}
