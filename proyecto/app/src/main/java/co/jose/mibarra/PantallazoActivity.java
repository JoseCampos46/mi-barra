package co.jose.mibarra;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;

/**
 * Versión 41: el pantallazo se puede marcar antes de mandarlo:
 * 🖍 resaltar en amarillo, ▒ dejar borroso, ❓ poner un signo (? ! ✓), ✂ recortar, ↩ deshacer,
 * y luego WhatsApp o Guardar (queda en la Galería, álbum MiBarra/Pantallazos).
 */
public class PantallazoActivity extends Base {

    static final int RESALTAR = 0, BORROSO = 1, SIGNO = 2, RECORTAR = 3;

    private Lienzo lienzo;
    private final TextView[] herr = new TextView[4];
    private final int[] colores = {0xFFFBC02D, 0xFF78909C, 0xFFD50000, 0xFFFF8F00};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("📸 Pantallazo");
        String ruta = getIntent().getStringExtra("imagen");
        Bitmap bm = ruta == null ? null : BitmapFactory.decodeFile(ruta);
        if (bm == null) { aviso("No se pudo abrir el pantallazo"); finish(); return; }

        LinearLayout l = vertical();
        l.setBackgroundColor(0xFF263238);
        l.setPadding(dp(6), dp(6), dp(6), dp(6));

        lienzo = new Lienzo(this, bm.copy(Bitmap.Config.ARGB_8888, true));
        l.addView(lienzo, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout f1 = horizontal();
        String[] nombres = {"🖍 Resaltar", "▒ Borroso", "❓ Signo", "✂ Recortar"};
        for (int i = 0; i < 4; i++) {
            final int m = i;
            herr[i] = ficha(nombres[i], colores[i], i == RESALTAR ? 0xFF3E2700 : Color.WHITE);
            herr[i].setOnClickListener(v -> modo(m));
            f1.addView(herr[i], peso(1));
        }
        l.addView(f1, ancho(54));

        LinearLayout f2 = horizontal();
        TextView deshacer = ficha("↩ Deshacer", 0xFF455A64, Color.WHITE);
        deshacer.setOnClickListener(v -> { if (!lienzo.deshacer()) aviso("No hay nada que deshacer"); });
        TextView wa = ficha("🟢 WhatsApp", 0xFF25D366, 0xFF073B1C);
        wa.setOnClickListener(v -> sacar(true));
        TextView gu = ficha("💾 Guardar", 0xFF1565C0, Color.WHITE);
        gu.setOnClickListener(v -> sacar(false));
        f2.addView(deshacer, peso(1));
        f2.addView(wa, peso(1));
        f2.addView(gu, peso(1));
        l.addView(f2, ancho(54));

        setContentView(l);
        modo(RESALTAR);
    }

    private TextView ficha(String t, int fondo, int letra) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(15);
        v.setTextColor(letra);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(48));
        GradientDrawable g = new GradientDrawable();
        g.setColor(fondo);
        g.setCornerRadius(dp(10));
        v.setBackground(g);
        return v;
    }

    private void modo(int m) {
        lienzo.modo = m;
        for (int i = 0; i < 4; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(colores[i]);
            g.setCornerRadius(dp(10));
            if (i == m) g.setStroke(dp(4), Color.WHITE);
            herr[i].setBackground(g);
        }
        lienzo.invalidate();
        String[] ayuda = {"🖍 Pase el dedo sobre lo que quiere resaltar",
                "▒ Pase el dedo sobre lo que no quiere que se lea",
                "❓ Toque donde quiere el signo; tóquelo otra vez para cambiarlo (? ! ✓)",
                "✂ Mueva las esquinas o los lados amarillos"};
        android.widget.Toast.makeText(this, ayuda[m], android.widget.Toast.LENGTH_SHORT).show();
    }

    private void sacar(boolean aWhatsapp) {
        Bitmap fin = lienzo.resultado();
        new Thread(() -> {
            try {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                fin.compress(Bitmap.CompressFormat.PNG, 100, o);
                Uri u = Util.guardarBytes(this, "Pantallazo_" + Util.fecha() + ".png", "image/png", o.toByteArray());
                // v44: queda solo el recortado/marcado; el original se borra para no llenar la carpeta de copias
                String orig = getIntent().getStringExtra("original");
                if (orig != null && !orig.equals(u.toString())) {
                    try { getContentResolver().delete(Uri.parse(orig), null, null); } catch (Exception ignored) {}
                }
                getIntent().putExtra("original", u.toString());   // si lo vuelve a guardar, reemplaza este
                runOnUiThread(() -> {
                    if (aWhatsapp) enviar(WHATSAPP, "image/png", u);
                    else aviso("💾 Guardado en la Galería (MiBarra/Pantallazos)");
                });
            } catch (Exception e) {
                runOnUiThread(() -> aviso("No se pudo guardar: " + e.getMessage()));
            }
        }).start();
    }

    /** La foto con sus marcas. Cada marca se pinta de una vez sobre la foto; «Deshacer» vuelve a la copia anterior. */
    static final class Lienzo extends View {
        int modo = RESALTAR;
        private Bitmap foto;
        private final ArrayDeque<Bitmap> atras = new ArrayDeque<>();
        private final Matrix m = new Matrix(), inv = new Matrix();
        private final RectF caja = new RectF();
        private final Path trazo = new Path();
        private final Paint pResaltar = new Paint(Paint.ANTI_ALIAS_FLAG), pMarco = new Paint(Paint.ANTI_ALIAS_FLAG),
                pSombra = new Paint(), pEsquina = new Paint(Paint.ANTI_ALIAS_FLAG), pSigno = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float x0, y0, x1, y1;
        private boolean tocando;
        // recorte en coordenadas de la foto
        private final RectF corte = new RectF();
        private int esquina = -1;
        // signos puestos (para cambiar ? → ! → ✓ al tocarlos otra vez)
        private final java.util.List<float[]> signos = new java.util.ArrayList<>();
        private static final String[] SIGNOS = {"?", "!", "✓"};

        Lienzo(Context c, Bitmap b) {
            super(c);
            foto = b;
            corte.set(0, 0, b.getWidth(), b.getHeight());
            pResaltar.setColor(0x88FFEB3B);
            pResaltar.setStyle(Paint.Style.STROKE);
            pResaltar.setStrokeCap(Paint.Cap.SQUARE);
            pResaltar.setStrokeJoin(Paint.Join.ROUND);
            pMarco.setColor(0xFFFFC107);
            pMarco.setStyle(Paint.Style.STROKE);
            pMarco.setStrokeWidth(Util.dp(c, 3));
            pSombra.setColor(0x99000000);
            pEsquina.setColor(0xFFFFC107);
            pSigno.setColor(0xFFD50000);
            pSigno.setTypeface(Typeface.DEFAULT_BOLD);
            pSigno.setTextAlign(Paint.Align.CENTER);
            setBackgroundColor(0xFF263238);
        }

        private void ubicar() {
            // v44: margen alrededor de la foto, así los puntos amarillos de abajo
            // nunca quedan metidos debajo de los botones y se pueden agarrar
            float mg = Util.dp(getContext(), 24);
            float w = getWidth() - 2 * mg, h = getHeight() - 2 * mg;
            if (w <= 0 || h <= 0) return;
            float e = Math.min(w / foto.getWidth(), h / foto.getHeight());
            float dx = mg + (w - foto.getWidth() * e) / 2f, dy = mg + (h - foto.getHeight() * e) / 2f;
            m.reset();
            m.postScale(e, e);
            m.postTranslate(dx, dy);
            m.invert(inv);
            caja.set(dx, dy, dx + foto.getWidth() * e, dy + foto.getHeight() * e);
        }

        private float escala() { return caja.width() / foto.getWidth(); }

        private float[] aFoto(float x, float y) { float[] p = {x, y}; inv.mapPoints(p); return p; }

        @Override
        protected void onDraw(Canvas c) {
            ubicar();
            c.drawBitmap(foto, m, null);
            if (tocando && modo == RESALTAR) {
                pResaltar.setStrokeWidth(grosor() * escala());
                c.drawPath(trazo, pResaltar);
            }
            if (tocando && modo == BORROSO) {
                Paint p = new Paint();
                p.setColor(0x6678909C);
                c.drawRect(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), p);
            }
            if (modo == RECORTAR) {
                RectF r = new RectF(corte);
                m.mapRect(r);
                c.drawRect(caja.left, caja.top, caja.right, r.top, pSombra);
                c.drawRect(caja.left, r.bottom, caja.right, caja.bottom, pSombra);
                c.drawRect(caja.left, r.top, r.left, r.bottom, pSombra);
                c.drawRect(r.right, r.top, caja.right, r.bottom, pSombra);
                c.drawRect(r, pMarco);
                float rad = Util.dp(getContext(), 11);
                c.drawCircle(r.left, r.top, rad, pEsquina);
                c.drawCircle(r.right, r.top, rad, pEsquina);
                c.drawCircle(r.left, r.bottom, rad, pEsquina);
                c.drawCircle(r.right, r.bottom, rad, pEsquina);
                // v44: también se agarra por la mitad de cada lado
                float lg = Util.dp(getContext(), 16), gr = Util.dp(getContext(), 5);
                c.drawRoundRect(r.centerX() - lg, r.top - gr, r.centerX() + lg, r.top + gr, gr, gr, pEsquina);
                c.drawRoundRect(r.centerX() - lg, r.bottom - gr, r.centerX() + lg, r.bottom + gr, gr, gr, pEsquina);
                c.drawRoundRect(r.left - gr, r.centerY() - lg, r.left + gr, r.centerY() + lg, gr, gr, pEsquina);
                c.drawRoundRect(r.right - gr, r.centerY() - lg, r.right + gr, r.centerY() + lg, gr, gr, pEsquina);
            }
        }

        private float grosor() { return foto.getWidth() / 22f; }

        private void guardarAtras() {
            atras.push(foto.copy(Bitmap.Config.ARGB_8888, true));
            // pocas copias para no llenar la memoria del celular
            while (atras.size() > 4) atras.removeLast();
        }

        boolean deshacer() {
            if (atras.isEmpty()) return false;
            foto = atras.pop();
            if (!signos.isEmpty()) signos.remove(signos.size() - 1);
            invalidate();
            return true;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    tocando = true;
                    x0 = x1 = x; y0 = y1 = y;
                    trazo.reset();
                    trazo.moveTo(x, y);
                    if (modo == RECORTAR) esquina = esquinaCerca(x, y);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    x1 = x; y1 = y;
                    if (modo == RESALTAR) trazo.lineTo(x, y);
                    if (modo == RECORTAR && esquina >= 0) moverEsquina(x, y);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    tocando = false;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP) terminar(x, y);
                    esquina = -1;
                    invalidate();
                    return true;
            }
            return false;
        }

        private void terminar(float x, float y) {
            if (modo == RESALTAR) {
                guardarAtras();
                Path enFoto = new Path();
                trazo.transform(inv, enFoto);
                Paint p = new Paint(pResaltar);
                p.setStrokeWidth(grosor());
                new Canvas(foto).drawPath(enFoto, p);
                signos.add(null);
            } else if (modo == BORROSO) {
                float[] a = aFoto(Math.min(x0, x1), Math.min(y0, y1)), b = aFoto(Math.max(x0, x1), Math.max(y0, y1));
                Rect r = new Rect((int) Math.max(0, a[0]), (int) Math.max(0, a[1]),
                        (int) Math.min(foto.getWidth(), b[0]), (int) Math.min(foto.getHeight(), b[1]));
                // un toque sin arrastrar empaña una franja del ancho de un renglón
                if (r.width() < 12 || r.height() < 12) {
                    float[] c = aFoto(x, y);
                    int alto = (int) (foto.getWidth() / 14f);
                    r.set(0, (int) Math.max(0, c[1] - alto / 2f), foto.getWidth(), (int) Math.min(foto.getHeight(), c[1] + alto / 2f));
                }
                if (r.width() < 4 || r.height() < 4) return;
                guardarAtras();
                empanar(r);
                signos.add(null);
            } else if (modo == SIGNO) {
                float[] c = aFoto(x, y);
                float tam = foto.getWidth() / 9f;
                // ¿tocó un signo que ya estaba? se cambia por el siguiente
                // (solo si el signo es lo último que puso, para no borrar otras marcas)
                for (int i = signos.size() - 1; i >= signos.size() - 1 && i >= 0; i--) {
                    float[] s = signos.get(i);
                    if (s != null && Math.abs(s[0] - c[0]) < tam * 0.7f && Math.abs(s[1] - c[1]) < tam * 0.7f && atras.size() > 0) {
                        // se vuelve a la foto de antes de ese signo y se pinta el siguiente
                        int sig = ((int) s[2] + 1) % SIGNOS.length;
                        deshacerHasta(i);
                        ponerSigno(s[0], s[1], sig);
                        return;
                    }
                }
                ponerSigno(c[0], c[1], 0);
            }
        }

        private void deshacerHasta(int i) {
            while (signos.size() > i && !atras.isEmpty()) { foto = atras.pop(); signos.remove(signos.size() - 1); }
        }

        private void ponerSigno(float fx, float fy, int cual) {
            guardarAtras();
            Paint p = new Paint(pSigno);
            p.setTextSize(foto.getWidth() / 9f);
            Paint borde = new Paint(p);
            borde.setStyle(Paint.Style.STROKE);
            borde.setStrokeWidth(foto.getWidth() / 120f);
            borde.setColor(Color.WHITE);
            Canvas c = new Canvas(foto);
            float base = fy + p.getTextSize() / 3f;
            c.drawText(SIGNOS[cual], fx, base, borde);
            c.drawText(SIGNOS[cual], fx, base, p);
            signos.add(new float[]{fx, fy, cual});
        }

        /** Borroso fuerte: se achica el pedazo y se vuelve a agrandar (cuadritos), así no se puede leer. */
        private void empanar(Rect r) {
            Bitmap pedazo = Bitmap.createBitmap(foto, r.left, r.top, r.width(), r.height());
            int lado = Math.max(1, foto.getWidth() / 60);
            Bitmap chico = Bitmap.createScaledBitmap(pedazo, Math.max(1, r.width() / lado), Math.max(1, r.height() / lado), true);
            Bitmap grande = Bitmap.createScaledBitmap(chico, r.width(), r.height(), true);
            new Canvas(foto).drawBitmap(grande, r.left, r.top, null);
        }

        private int esquinaCerca(float x, float y) {
            RectF r = new RectF(corte);
            m.mapRect(r);
            float[][] es = {{r.left, r.top}, {r.right, r.top}, {r.left, r.bottom}, {r.right, r.bottom},
                    {r.centerX(), r.top}, {r.centerX(), r.bottom}, {r.left, r.centerY()}, {r.right, r.centerY()}};
            float lim = Util.dp(getContext(), 44);
            int mejor = -1;
            float dmin = Float.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                float d = (float) Math.hypot(es[i][0] - x, es[i][1] - y);
                if (d < lim && d < dmin) { dmin = d; mejor = i; }
            }
            return mejor;
        }

        private void moverEsquina(float x, float y) {
            float[] p = aFoto(x, y);
            float px = Math.max(0, Math.min(foto.getWidth(), p[0])), py = Math.max(0, Math.min(foto.getHeight(), p[1]));
            float min = foto.getWidth() / 10f;
            boolean izq = esquina == 0 || esquina == 2 || esquina == 6;
            boolean der = esquina == 1 || esquina == 3 || esquina == 7;
            boolean arr = esquina == 0 || esquina == 1 || esquina == 4;
            boolean aba = esquina == 2 || esquina == 3 || esquina == 5;
            if (izq) corte.left = Math.min(px, corte.right - min);
            if (der) corte.right = Math.max(px, corte.left + min);
            if (arr) corte.top = Math.min(py, corte.bottom - min);
            if (aba) corte.bottom = Math.max(py, corte.top + min);
        }

        Bitmap resultado() {
            Rect r = new Rect((int) corte.left, (int) corte.top, (int) corte.right, (int) corte.bottom);
            if (r.left <= 0 && r.top <= 0 && r.right >= foto.getWidth() && r.bottom >= foto.getHeight()) return foto;
            return Bitmap.createBitmap(foto, r.left, r.top, Math.max(1, r.width()), Math.max(1, r.height()));
        }
    }
}
