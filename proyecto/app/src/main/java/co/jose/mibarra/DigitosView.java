package co.jose.mibarra;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Versión 30: números de radio reloj (siete segmentos, como los Sony), en azul cian suave.
 * Los segmentos apagados se ven muy tenues, como en los relojes de verdad. Se agrandan solos para llenar el espacio.
 */
public class DigitosView extends View {

    // Segmentos encendidos por número: a b c d e f g
    private static final int[] SEG = {0b1111110, 0b0110000, 0b1101101, 0b1111001, 0b0110011,
            0b1011011, 0b1011111, 0b1110000, 0b1111111, 0b1111011};

    private String texto = "12:00";
    private final Paint luz = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint apagado = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean dosPuntos = true;

    public DigitosView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // para el brillo suave alrededor
        luz.setColor(0xFF7FDBFF);
        apagado.setColor(0x147FDBFF);
    }

    void poner(String t, boolean puntos) {
        texto = t;
        dosPuntos = puntos;
        invalidate();
    }

    void color(int c) {
        luz.setColor(c);
        apagado.setColor((c & 0x00FFFFFF) | 0x14000000);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        // Versión 33: la hora un poco más pequeña (para que los letreros y botones crezcan),
        // el 1 angosto (solo su rayita) y la misma separación entre todos los números y los dos puntos.
        String t = texto;
        float alto = getHeight() * 0.74f;   // Versión 34: de verdad más pequeña
        float anchoDig = alto * 0.58f;
        float g = alto * 0.11f;
        float sep = g + alto * 0.13f;       // Versión 34: la luz visible entre números es igual (cuenta el grueso de las rayitas)
        float anchoUno = g * 1.2f, anchoPuntos = g * 1.4f;
        float total = 0;
        int n = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            total += ch == ':' ? anchoPuntos : ch == '1' ? anchoUno : anchoDig;
            n++;
        }
        total += Math.max(0, n - 1) * sep;
        float esc = Math.min(1f, getWidth() * 0.84f / Math.max(1f, total));
        alto *= esc; anchoDig *= esc; g *= esc; sep *= esc; anchoUno *= esc; anchoPuntos *= esc; total *= esc;
        float x = (getWidth() - total) / 2f;
        float y = (getHeight() - alto) / 2f;
        luz.setShadowLayer(g * 0.9f, 0, 0, (luz.getColor() & 0x00FFFFFF) | 0x88000000);
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == ':') {
                float cx = x + anchoPuntos / 2f;
                Paint p = dosPuntos ? luz : apagado;
                c.drawCircle(cx, y + alto * 0.30f, g * 0.62f, p);
                c.drawCircle(cx, y + alto * 0.70f, g * 0.62f, p);
                x += anchoPuntos + sep;
                continue;
            }
            if (ch == '1') {
                // solo la rayita derecha, centrada en su espacio angosto
                float xr = x + anchoUno / 2f;
                float mid = y + alto / 2f;
                segmento(c, new float[]{xr, y + g * 0.6f, xr, mid - g * 0.3f, 1}, g, luz);
                segmento(c, new float[]{xr, mid + g * 0.3f, xr, y + alto - g * 0.6f, 1}, g, luz);
                x += anchoUno + sep;
                continue;
            }
            int m = ch >= '0' && ch <= '9' ? SEG[ch - '0'] : -1;
            digito(c, x, y, anchoDig, alto, g, m);
            x += anchoDig + sep;
        }
    }

    /** Dibuja un dígito; m = -1 no dibuja nada (el 1 de las horas cuando son 9:00, por ejemplo). */
    private void digito(Canvas c, float x, float y, float w, float h, float g, int m) {
        if (m < 0) return;
        float mid = y + h / 2f;
        // a, b, c, d, e, f, g
        float[][] s = {
                {x + g * 0.6f, y, x + w - g * 0.6f, y, 0},                 // a (horizontal)
                {x + w, y + g * 0.6f, x + w, mid - g * 0.3f, 1},          // b (vertical)
                {x + w, mid + g * 0.3f, x + w, y + h - g * 0.6f, 1},      // c
                {x + g * 0.6f, y + h, x + w - g * 0.6f, y + h, 0},         // d
                {x, mid + g * 0.3f, x, y + h - g * 0.6f, 1},               // e
                {x, y + g * 0.6f, x, mid - g * 0.3f, 1},                   // f
                {x + g * 0.6f, mid, x + w - g * 0.6f, mid, 0}};            // g
        for (int k = 0; k < 7; k++) {
            boolean on = (m >> (6 - k) & 1) == 1;
            segmento(c, s[k], g, on ? luz : apagado);
        }
    }

    /** Un segmento con puntas en diagonal, como los de LED. */
    private void segmento(Canvas c, float[] s, float g, Paint p) {
        Path q = new Path();
        float h = g / 2f;
        if (s[4] == 0) { // horizontal
            q.moveTo(s[0], s[1]);
            q.lineTo(s[0] + h, s[1] - h);
            q.lineTo(s[2] - h, s[3] - h);
            q.lineTo(s[2], s[3]);
            q.lineTo(s[2] - h, s[3] + h);
            q.lineTo(s[0] + h, s[1] + h);
        } else {
            q.moveTo(s[0], s[1]);
            q.lineTo(s[0] + h, s[1] + h);
            q.lineTo(s[2] + h, s[3] - h);
            q.lineTo(s[2], s[3]);
            q.lineTo(s[2] - h, s[3] - h);
            q.lineTo(s[0] - h, s[1] + h);
        }
        q.close();
        c.drawPath(q, p);
    }
}
