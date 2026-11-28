package co.jose.mibarra;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Versión 48: la puerta de entrada de cada app suelta (ChpCalcu, ChpNivel, ChpGPS…).
 * Si la app tiene una sola herramienta, la abre de una. Si tiene varias, muestra botones grandes de colores.
 * Arriba: ☰ (escáner, Claude, ayuda), el nombre con su versión y la ✕ para cerrar.
 */
public class GrupoActivity extends Activity {

    private int dp(float v) { return Util.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (abrirPedida(getIntent())) { finish(); return; }
        Grupos.Grupo g = Grupos.propio(this);
        if (g == null) { finish(); return; }
        if (g.botones.length == 1) { abrir(g.botones[0]); finish(); return; }
        pintar(g);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        abrirPedida(i);
    }

    /** Otra app (ChpBar o ChpInicio) pidió una pantalla de esta app. */
    private boolean abrirPedida(Intent i) {
        String clase = i == null ? null : i.getStringExtra("abrir_clase");
        if (clase == null) return false;
        Intent a = new Intent();
        if (i.getExtras() != null) a.putExtras(i.getExtras());
        a.removeExtra("abrir_clase");
        a.setClassName(this, clase);
        try { startActivity(a); } catch (Exception e) { Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_SHORT).show(); }
        return true;
    }

    private void abrir(Grupos.Boton x) {
        Intent a = new Intent().setClassName(this, x.clase);
        if (x.extra != null) a.putExtra(x.extra, x.valor);
        try { startActivity(a); } catch (Exception e) { Toast.makeText(this, "No se pudo abrir " + x.nombre, Toast.LENGTH_SHORT).show(); }
    }

    private TextView texto(String s, float tam, int color, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(tam);
        t.setTextColor(color);
        if (negrita) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void pintar(Grupos.Grupo g) {
        getWindow().setStatusBarColor(g.color);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Color.WHITE);

        LinearLayout barra = new LinearLayout(this);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.setBackgroundColor(g.color);
        barra.setPadding(dp(6), dp(6), dp(6), dp(6));
        TextView menu = texto("☰", 26, Color.WHITE, true);
        menu.setPadding(dp(10), 0, dp(10), 0);
        menu.setOnClickListener(v -> menu(g));
        barra.addView(menu);
        barra.addView(texto(g.nombre + "  v" + Util.version(this), 20, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView x = texto("✕", 18, Color.WHITE, true);
        x.setGravity(Gravity.CENTER);
        GradientDrawable gx = new GradientDrawable();
        gx.setShape(GradientDrawable.OVAL);
        gx.setColor(0xFFC62828);
        gx.setStroke(dp(2), Color.WHITE);
        x.setBackground(gx);
        x.setOnClickListener(v -> finishAndRemoveTask());
        barra.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
        col.addView(barra);

        // Botones grandes, 2 por fila, que llenan la pantalla (sin espacios vacíos)
        LinearLayout cuerpo = new LinearLayout(this);
        cuerpo.setOrientation(LinearLayout.VERTICAL);
        cuerpo.setPadding(dp(8), dp(8), dp(8), dp(8));
        int filas = (g.botones.length + 1) / 2;
        int alto = Math.max(dp(130), (getResources().getDisplayMetrics().heightPixels - dp(140)) / Math.max(1, filas));
        LinearLayout fila = null;
        for (int i = 0; i < g.botones.length; i++) {
            if (i % 2 == 0) { fila = new LinearLayout(this); cuerpo.addView(fila, new LinearLayout.LayoutParams(-1, alto)); }
            Grupos.Boton bt = g.botones[i];
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            GradientDrawable gb = new GradientDrawable();
            gb.setColor(bt.color);
            gb.setCornerRadius(dp(18));
            c.setBackground(gb);
            c.addView(texto(bt.emoji, 44, Color.WHITE, false));
            TextView n = texto(bt.nombre, 19, Color.WHITE, true);
            n.setGravity(Gravity.CENTER);
            n.setPadding(dp(6), dp(6), dp(6), 0);
            c.addView(n);
            c.setOnClickListener(v -> abrir(bt));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
            p.setMargins(dp(5), dp(5), dp(5), dp(5));
            fila.addView(c, p);
        }
        if (g.botones.length % 2 == 1 && fila != null) {
            // el último ocupa todo el ancho para no dejar hueco
            android.view.View ultimo = fila.getChildAt(fila.getChildCount() - 1);
            ((LinearLayout.LayoutParams) ultimo.getLayoutParams()).weight = 2f;
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(cuerpo);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        FrameLayout raiz = new FrameLayout(this);
        raiz.addView(col);
        setContentView(raiz);
    }

    private void menu(Grupos.Grupo g) {
        String[] op = {"📄  Escáner", "✉  Escribirle a Claude", "❓  Ayuda"};
        new AlertDialog.Builder(this).setTitle("☰  " + g.nombre + " v" + Util.version(this)).setItems(op, (d, w) -> {
            try {
                if (w == 0) startActivity(new Intent(this, EscanerActivity.class));
                else if (w == 1) startActivity(new Intent(this, ClaudeActivity.class));
                else new AlertDialog.Builder(this).setTitle("Ayuda")
                        .setMessage("Toque el botón de la herramienta que necesita.\n\nCada herramienta tiene su ✕ arriba a la derecha para cerrar.\n"
                                + "Lo que guarde queda en Descargas/MiBarra, en la carpeta de cada herramienta.")
                        .setPositiveButton("Entendido", null).show();
            } catch (Exception e) { Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_SHORT).show(); }
        }).setNegativeButton("Cerrar", null).show();
    }
}
