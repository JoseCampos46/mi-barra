package co.jose.mibarra;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;

/** ChpRecordatorios: la pantalla de la alarma sonando. Un solo botón grande: apagar (del todo). */
public class SuenaActivity extends Activity {

    private static WeakReference<SuenaActivity> abierta = new WeakReference<>(null);

    static void cerrarSiAbierta() {
        SuenaActivity a = abierta.get();
        if (a != null && !a.isFinishing()) a.runOnUiThread(a::finish);
    }

    private int dp(float v) { return Util.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        abierta = new WeakReference<>(this);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        pintar();
    }

    @Override
    protected void onNewIntent(android.content.Intent i) {
        super.onNewIntent(i);
        pintar();
    }

    private TextView texto(String s, float tam, int color, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(tam);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (negrita) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void pintar() {
        List<long[]> suenan = Alarmas.sonandoAhora(this);
        List<Alarmas.A> todas = Alarmas.leer(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(Color.WHITE);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(20), dp(56), dp(20), dp(24));

        l.addView(texto("⏰", 44, Color.BLACK, false));
        long hora = suenan.isEmpty() ? System.currentTimeMillis() : suenan.get(0)[1];
        l.addView(texto(Alarmas.horaBonita(hora), 44, Color.BLACK, true));

        int vez = 1;
        for (long[] p : suenan) {
            Alarmas.A a = Alarmas.buscar(todas, p[0]);
            if (a == null) continue;
            vez = (int) Math.max(vez, Math.min(p[2], Alarmas.VECES));
            TextView n = texto(a.nombre, 28, Color.BLACK, true);
            n.setPadding(0, dp(18), 0, 0);
            l.addView(n);
            if (!a.detalle.isEmpty()) l.addView(texto(a.detalle, 22, Color.BLACK, false));
        }
        if (suenan.isEmpty()) l.addView(texto("Alarma", 28, Color.BLACK, true));

        TextView aviso = texto(vez < Alarmas.VECES
                ? "Si no la apaga, vuelve a sonar en 5 minutos · vez " + vez + " de " + Alarmas.VECES
                : "Última vez (" + vez + " de " + Alarmas.VECES + "): después se cierra sola", 17, 0xFF5D4000, true);
        aviso.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable ga = new GradientDrawable();
        ga.setColor(0xFFFFE082);
        ga.setCornerRadius(dp(10));
        aviso.setBackground(ga);
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(-1, -2);
        pa.topMargin = dp(24);
        l.addView(aviso, pa);

        Button apagar = new Button(this);
        apagar.setText("✅  APAGAR");
        apagar.setAllCaps(false);
        apagar.setTextSize(30);
        apagar.setTypeface(Typeface.DEFAULT_BOLD);
        apagar.setTextColor(Color.WHITE);
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0xFF2E7D32);
        gb.setCornerRadius(dp(16));
        apagar.setBackground(gb);
        apagar.setOnClickListener(v -> { Alarmas.apagarTodo(this); finishAndRemoveTask(); });
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(-1, dp(96));
        pb.topMargin = dp(20);
        l.addView(apagar, pb);

        // La próxima toma, para saber qué sigue
        long ahora = System.currentTimeMillis() + 2 * Alarmas.MINUTO, prox = 0;
        for (Alarmas.A a : todas) { long t = Alarmas.proxima(a, ahora); if (t > 0 && (prox == 0 || t < prox)) prox = t; }
        if (prox > 0) {
            TextView p = texto("Próxima alarma: " + Alarmas.horaBonita(prox), 18, Color.BLACK, false);
            p.setPadding(0, dp(16), 0, 0);
            l.addView(p);
        }
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.WHITE);
        sv.addView(l);
        setContentView(sv);
    }

    @Override
    protected void onDestroy() {
        if (abierta.get() == this) abierta = new WeakReference<>(null);
        super.onDestroy();
    }
}
