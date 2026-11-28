package co.jose.mibarra;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;

/**
 * Versión 29: dibuja la cuenta como recibo, cotización, lista de compras… en letra negra nítida.
 * Tamaños: ¼ carta, ½ carta, carta en 3 columnas (corre hacia abajo y sigue en la otra) y carta completa.
 */
final class Recibo {

    static final String[] TIPOS = {"COTIZACIÓN", "CUENTA DE COBRO", "LISTA DE COMPRAS", "RECIBO DE ARRIENDO", "REMISIÓN / ENVÍO", "PEDIDO", "RECIBO DE PAGO"};
    static final String[] PARA = {"Cliente", "Cliente", "Para", "Inquilino", "Destinatario", "Proveedor", "Recibí de"};
    static final String[] TAMANOS = {"¼ carta (4 por hoja)", "½ carta (2 por hoja)", "Carta en 3 columnas", "Carta completa"};

    static final class Fila { String art = "", cant = "", unit = ""; }

    String tipo = TIPOS[0], para = "", paraEtiq = "Cliente", fecha = "", nota = "";
    String negocio = "", nit = "", tel = "";
    final List<Fila> filas = new ArrayList<>();

    // 150 puntos por pulgada: hoja carta = 1275 × 1650
    static final int CARTA_W = 1275, CARTA_H = 1650;

    static long valor(Fila f) {
        long u = Util.valor(f.unit);
        if (u <= 0) return 0;
        double c = f.cant.trim().isEmpty() ? 1 : Util.cantidad(f.cant);
        return Math.round(c * u);
    }

    long total() { long t = 0; for (Fila f : filas) t += valor(f); return t; }

    boolean hayPrecios() { for (Fila f : filas) if (Util.valor(f.unit) > 0) return true; return false; }

    /** La imagen para WhatsApp, del tamaño escogido. */
    Bitmap imagen(int tam) {
        int w, h;
        if (tam == 0) { w = CARTA_W / 2; h = CARTA_H / 2; }
        else if (tam == 1) { w = CARTA_W; h = CARTA_H / 2; }
        else { w = CARTA_W; h = CARTA_H; }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        dibujar(c, 0, 0, w, h, tam == 2 ? 3 : 1);
        return b;
    }

    /** La hoja carta para imprimir: ¼ = 4 copias, ½ = 2 copias, con rayas de corte. */
    Bitmap hoja(int tam) {
        if (tam >= 2) return imagen(tam);
        Bitmap b = Bitmap.createBitmap(CARTA_W, CARTA_H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        Paint corte = new Paint();
        corte.setColor(Color.BLACK);
        corte.setStrokeWidth(2);
        corte.setPathEffect(new android.graphics.DashPathEffect(new float[]{14, 10}, 0));
        if (tam == 1) {
            dibujar(c, 0, 0, CARTA_W, CARTA_H / 2, 1);
            dibujar(c, 0, CARTA_H / 2, CARTA_W, CARTA_H / 2, 1);
            c.drawLine(0, CARTA_H / 2f, CARTA_W, CARTA_H / 2f, corte);
        } else {
            for (int i = 0; i < 4; i++) dibujar(c, (i % 2) * CARTA_W / 2, (i / 2) * CARTA_H / 2, CARTA_W / 2, CARTA_H / 2, 1);
            c.drawLine(0, CARTA_H / 2f, CARTA_W, CARTA_H / 2f, corte);
            c.drawLine(CARTA_W / 2f, 0, CARTA_W / 2f, CARTA_H, corte);
        }
        return b;
    }

    /** Arma todo dentro del recuadro; si son varias columnas, al llenarse una sigue en la siguiente. */
    void dibujar(Canvas c, int x0, int y0, int w, int h, int cols) {
        int m = Math.max(18, w / 28);
        int colW = (w - 2 * m - (cols - 1) * m) / cols;
        int alto = h - 2 * m;
        boolean precios = hayPrecios();
        int lineasCab = 4 + (negocio.isEmpty() ? 0 : 1) + (nit.isEmpty() && tel.isEmpty() ? 0 : 1) + (nota.isEmpty() ? 0 : 1);
        int lineas = lineasCab + filas.size() + 1 + (precios ? 2 : 0);
        // Letra: lo más grande posible sin salirse (máximo 34 en carta, proporcional en tamaños chicos)
        float maxT = Math.min(34f, colW / 17f);
        float t = Math.min(maxT, (float) alto * cols / (lineas * 1.55f + cols));
        t = Math.max(9f, t);
        float lh = t * 1.55f;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        Paint raya = new Paint();
        raya.setColor(Color.BLACK);
        raya.setStrokeWidth(Math.max(1.5f, t / 12));

        int col = 0;
        float x = x0 + m, y = y0 + m + t;
        float fondo = y0 + m + alto;

        // Cabeza
        if (!negocio.isEmpty()) { p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(t * 1.05f); texto(c, negocio, x, y, colW, p); y += lh; }
        if (!nit.isEmpty() || !tel.isEmpty()) {
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(t * 0.85f);
            texto(c, (nit.isEmpty() ? "" : "NIT/C.C. " + nit) + (nit.isEmpty() || tel.isEmpty() ? "" : "  ·  ") + tel, x, y, colW, p);
            y += lh;
        }
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(t * 1.35f);
        texto(c, tipo, x, y + t * 0.2f, colW, p);
        y += lh * 1.25f;
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(t);
        texto(c, "Fecha: " + fecha, x, y, colW, p);
        y += lh;
        texto(c, paraEtiq + ": " + para, x, y, colW, p);
        y += lh;
        if (!nota.isEmpty()) { texto(c, nota, x, y, colW, p); y += lh; }

        // Columnas de la tabla
        float[] pc = precios ? new float[]{0.46f, 0.12f, 0.21f, 0.21f} : new float[]{0.80f, 0.20f};
        String[] tit = precios ? new String[]{"Artículo", "Cant.", "Vr. unidad", "Vr. total"} : new String[]{"Artículo", "Cant."};
        y = cabezaTabla(c, p, raya, tit, pc, x, y, colW, t, lh);
        for (int i = 0; i < filas.size(); i++) {
            if (y + lh * 0.2f > fondo) {
                if (col + 1 >= cols) break;
                col++;
                x = x0 + m + col * (colW + m);
                y = y0 + m + t;
                y = cabezaTabla(c, p, raya, tit, pc, x, y, colW, t, lh);
            }
            Fila f = filas.get(i);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(t);
            float cx = x;
            String[] v = precios
                    ? new String[]{f.art, f.cant, Util.valor(f.unit) > 0 ? Util.pesos(Util.valor(f.unit)) : "", valor(f) > 0 ? Util.pesos(valor(f)) : ""}
                    : new String[]{f.art, f.cant};
            for (int k = 0; k < v.length; k++) {
                float cw = colW * pc[k];
                if (k == 0) texto(c, v[k], cx + t * 0.2f, y, cw - t * 0.4f, p);
                else derecha(c, v[k], cx + cw - t * 0.2f, y, cw - t * 0.4f, p);
                cx += cw;
            }
            c.drawLine(x, y + t * 0.45f, x + colW, y + t * 0.45f, raya);
            y += lh;
        }
        if (precios) {
            if (y + lh > fondo && col + 1 < cols) { col++; x = x0 + m + col * (colW + m); y = y0 + m + t; }
            y += lh * 0.3f;
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(t * 1.25f);
            derecha(c, "TOTAL  " + Util.pesos(total()), x + colW - t * 0.2f, y + t * 0.2f, colW, p);
        }
    }

    private float cabezaTabla(Canvas c, Paint p, Paint raya, String[] tit, float[] pc, float x, float y, int colW, float t, float lh) {
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(t * 0.9f);
        float cx = x;
        for (int k = 0; k < tit.length; k++) {
            float cw = colW * pc[k];
            if (k == 0) texto(c, tit[k], cx + t * 0.2f, y, cw, p);
            else derecha(c, tit[k], cx + cw - t * 0.2f, y, cw, p);
            cx += cw;
        }
        c.drawLine(x, y + t * 0.45f, x + colW, y + t * 0.45f, raya);
        return y + lh;
    }

    private static void texto(Canvas c, String s, float x, float y, float ancho, Paint p) {
        c.drawText(recortar(s, ancho, p), x, y, p);
    }

    private static void derecha(Canvas c, String s, float xDer, float y, float ancho, Paint p) {
        String r = recortar(s, ancho, p);
        c.drawText(r, xDer - p.measureText(r), y, p);
    }

    private static String recortar(String s, float ancho, Paint p) {
        if (s == null) return "";
        if (p.measureText(s) <= ancho) return s;
        while (s.length() > 1 && p.measureText(s + "…") > ancho) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** La cuenta como texto, para pegarla en el mensaje de WhatsApp. */
    String comoTexto() {
        StringBuilder b = new StringBuilder();
        b.append("*").append(tipo).append("*");
        if (!para.isEmpty()) b.append("\n").append(paraEtiq).append(": ").append(para);
        b.append("\n");
        for (Fila f : filas) {
            if (f.art.trim().isEmpty()) continue;
            b.append("\n• ");
            if (!f.cant.trim().isEmpty()) b.append(f.cant.trim()).append(" ");
            b.append(f.art.trim());
            long u = Util.valor(f.unit);
            if (u > 0) b.append(" × ").append(Util.pesos(u)).append(" = ").append(Util.pesos(valor(f)));
        }
        if (hayPrecios()) b.append("\n\n*TOTAL: ").append(Util.pesos(total())).append("*");
        return b.toString();
    }

    /** Para que la voz lo lea bonito. */
    String paraLeer() {
        StringBuilder b = new StringBuilder(tipo.toLowerCase(new java.util.Locale("es")).replace("/", "o")).append(". ");
        for (Fila f : filas) {
            if (f.art.trim().isEmpty()) continue;
            if (!f.cant.trim().isEmpty()) b.append(f.cant.trim()).append(" ");
            b.append(f.art.trim());
            long u = Util.valor(f.unit);
            if (u > 0) b.append(", a ").append(u).append(" pesos, son ").append(valor(f)).append(" pesos");
            b.append(". ");
        }
        if (hayPrecios()) b.append("Total de la cuenta: ").append(total()).append(" pesos.");
        return b.toString();
    }
}
