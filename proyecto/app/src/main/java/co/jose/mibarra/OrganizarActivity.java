package co.jose.mibarra;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Versión 41: ✨ Organizar en ventanita. Es una pantalla «de verdad» (no una ventana flotante),
 * así el Xiaomi no le corta el internet. Al tocar «Poner en WhatsApp» se cierra y el botón
 * flotante (BarraService) pone el texto organizado en la casilla del chat.
 */
public class OrganizarActivity extends Activity {

    private final Handler h = new Handler(Looper.getMainLooper());
    private String original;
    private boolean esWa;
    private TextView tNuevo, bPoner, bOtra;
    private String listo;
    private int vuelta;

    private int dp(float v) { return Util.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        original = getIntent().getStringExtra("texto");
        esWa = getIntent().getBooleanExtra("wa", false);
        if (original == null || original.trim().isEmpty()) { finish(); return; }

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(16));

        TextView tit = new TextView(this);
        tit.setText("✨ Organizar");
        tit.setTextSize(21);
        tit.setTypeface(Typeface.DEFAULT_BOLD);
        tit.setTextColor(0xFF3C3489);
        l.addView(tit);

        TextView antes = new TextView(this);
        antes.setText("Antes: " + (original.length() > 160 ? original.substring(0, 160) + "…" : original));
        antes.setTextSize(14);
        antes.setTextColor(0xFF555555);
        antes.setPaintFlags(antes.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        antes.setPadding(0, dp(6), 0, dp(8));
        l.addView(antes);

        tNuevo = new TextView(this);
        tNuevo.setTextSize(18);
        tNuevo.setTextColor(Color.BLACK);
        tNuevo.setLineSpacing(0, 1.1f);
        tNuevo.setTextIsSelectable(true);
        tNuevo.setPadding(0, 0, 0, dp(12));
        l.addView(tNuevo);

        bPoner = boton(esWa ? "✔ Poner en WhatsApp" : "✔ Poner en la casilla", 0xFF1B8E3E, 19);
        bPoner.setOnClickListener(v -> poner());
        l.addView(bPoner, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout fila = new LinearLayout(this);
        bOtra = boton("↻ Otra vez", 0xFF534AB7, 17);
        bOtra.setOnClickListener(v -> pedir(true));
        TextView bCancelar = boton("✖ Cancelar", 0xFF616161, 17);
        bCancelar.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(52), 1f);
        a.setMargins(0, dp(8), dp(4), 0);
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0, dp(52), 1f);
        c.setMargins(dp(4), dp(8), 0, 0);
        fila.addView(bOtra, a);
        fila.addView(bCancelar, c);
        l.addView(fila);

        ScrollView sv = new ScrollView(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp(2), 0xFF534AB7);
        g.setCornerRadius(dp(16));
        sv.setBackground(g);
        sv.addView(l);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setGravity(Gravity.TOP);
        raiz.setPadding(dp(10), dp(60), dp(10), dp(10));
        raiz.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // tocar fuera de la ventanita la cierra
        raiz.setOnClickListener(v -> finish());
        sv.setOnClickListener(v -> {});
        setContentView(raiz);

        pedir(false);
    }

    private TextView boton(String t, int color, int tam) {
        TextView b = new TextView(this);
        b.setText(t);
        b.setTextSize(tam);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        b.setBackground(g);
        return b;
    }

    private void pedir(boolean otraVez) {
        if (!Gemini.hayLlave(this)) {
            tNuevo.setText("🔑 Falta la llave de Gemini. Toque «🔑 Llave» y péguela una sola vez.");
            tNuevo.setTextColor(0xFFB71C1C);
            bOtra.setText("🔑 Llave");
            bOtra.setOnClickListener(v -> { startActivity(new android.content.Intent(this, LlaveActivity.class)); finish(); });
            return;
        }
        final int esta = ++vuelta;
        listo = null;
        bPoner.setAlpha(0.45f);
        tNuevo.setTextColor(Color.BLACK);
        final long inicio = System.currentTimeMillis();
        h.post(new Runnable() {
            @Override public void run() {
                if (esta != vuelta || listo != null || isFinishing()) return;
                tNuevo.setText("Organizando con Gemini… " + ((System.currentTimeMillis() - inicio) / 1000) + " s ⏳");
                h.postDelayed(this, 1000);
            }
        });
        Gemini.organizar(this, original, otraVez, new Gemini.Respuesta() {
            @Override public void listo(String t) {
                h.post(() -> {
                    if (esta != vuelta || isFinishing()) return;
                    listo = t;
                    tNuevo.setText(t);
                    bPoner.setAlpha(1f);
                });
            }
            @Override public void error(String m) {
                h.post(() -> {
                    if (esta != vuelta || isFinishing()) return;
                    vuelta++;
                    tNuevo.setText(m);
                    tNuevo.setTextColor(0xFFB71C1C);
                    if (m.contains("llave")) {
                        bOtra.setText("🔑 Llave");
                        bOtra.setOnClickListener(v -> { startActivity(new android.content.Intent(OrganizarActivity.this, LlaveActivity.class)); finish(); });
                    }
                });
            }
        });
    }

    private void poner() {
        if (listo == null) return;
        BarraService s = BarraService.instancia;
        final String t = listo;
        finish();
        if (s != null) s.ponerOrganizado(t);
        else {
            try {
                android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Organizado", t));
            } catch (Exception ignored) {}
            android.widget.Toast.makeText(this, "📋 Quedó copiado: deje el dedo en la casilla y toque Pegar", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        vuelta++;
        super.onDestroy();
    }
}
