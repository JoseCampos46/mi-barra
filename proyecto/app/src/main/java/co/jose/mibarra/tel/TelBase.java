package co.jose.mibarra.tel;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Versión 37: colores, letras grandes y botones de Mi Teléfono (fondo oscuro, botones de colores fuertes). */
public abstract class TelBase extends Activity {

    static final int FONDO = 0xFF101827, TARJETA = 0xFF2C2C2A, BLANCO = 0xFFFFFFFF, GRIS = 0xFFB4B2A9;
    static final int VERDE = 0xFF639922, AZUL = 0xFF185FA5, AZUL_VIVO = 0xFF378ADD, AZUL_CLARO = 0xFFB5D4F4;
    static final int MORADO = 0xFF534AB7, ROJO = 0xFFE24B4A, NARANJA = 0xFFBA7517, AMARILLO = 0xFFFAC775;
    static final int VERDE_CLARO = 0xFF97C459;

    int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    void aviso(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    static GradientDrawable fondo(int color, float radioPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radioPx);
        return g;
    }

    TextView texto(String s, float sp, boolean negrilla, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (negrilla) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    Button boton(String s, int bg, int fg, float sp) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(sp);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(fondo(bg, dp(14)));
        b.setPadding(dp(4), dp(6), dp(4), dp(6));
        b.setStateListAnimator(null);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    LinearLayout.LayoutParams peso(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        return p;
    }

    LinearLayout.LayoutParams ancho() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(4), dp(3), dp(4), dp(3));
        return p;
    }

    LinearLayout.LayoutParams alto(int dpAlto) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dpAlto));
        p.setMargins(dp(4), dp(3), dp(4), dp(3));
        return p;
    }

    LinearLayout.LayoutParams relleno() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    static String iniciales(String s) {
        if (s == null) return "?";
        String[] p = s.trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String x : p) {
            if (!x.isEmpty() && Character.isLetter(x.charAt(0))) b.append(Character.toUpperCase(x.charAt(0)));
            if (b.length() == 2) break;
        }
        return b.length() == 0 ? "#" : b.toString();
    }

    View separador() {
        View v = new View(this);
        v.setBackgroundColor(0x33FFFFFF);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }
}
