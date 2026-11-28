package co.jose.mibarra;

import java.io.File;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Foto con medidas: tome o escoja una foto, arrastre el dedo para poner una flecha de medida
 * y escriba el número. Alto (rojo), ancho (azul) y fondo (verde). Se guarda y se manda por WhatsApp.
 */
public class MedidasActivity extends Base {

    static final String[] TIPOS = {"↕ Alto", "↔ Ancho", "⤢ Fondo", "✎ Otra"};
    static final int[] COLORES = {0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFB8C00};

    static final class Cota { float x1, y1, x2, y2; String texto; int tipo; }

    private static final int FOTO = 91, GALERIA = 92;
    private Bitmap foto;
    private Uri destino;
    private final List<Cota> cotas = new ArrayList<>();
    private Lienzo lienzo;
    private int tipoActual = 0;
    private Button[] bTipos = new Button[4];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        inicio();
    }

    private void inicio() {
        LinearLayout l = vertical();
        l.setPadding(dp(16), dp(20), dp(16), dp(16));
        l.addView(titulo("📐 Foto con medidas"));
        TextView t = new TextView(this);
        t.setText("Tome una foto de la ventana, la puerta o el mueble. Después arrastre el dedo de una punta a la otra y escriba la medida.");
        t.setTextSize(17);
        t.setPadding(0, dp(8), 0, dp(16));
        l.addView(t);
        Button tomar = boton("📷  Tomar foto", 0xFF1565C0);
        tomar.setTextSize(20);
        tomar.setOnClickListener(v -> tomarFoto());
        l.addView(tomar, ancho(70));
        Button gal = boton("🖼  Escoger de la galería", 0xFF455A64);
        gal.setTextSize(18);
        gal.setOnClickListener(v -> {
            try { startActivityForResult(Intent.createChooser(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), "Escoja la foto"), GALERIA); }
            catch (Exception e) { aviso("No hay galería"); }
        });
        l.addView(gal, ancho(64));
        // Versión 26: las fotos guardadas se pueden volver a abrir y mover sus medidas
        File[] trabajos = carpetaTrabajos().listFiles((dir, n) -> n.endsWith(".txt"));
        if (trabajos != null && trabajos.length > 0) {
            java.util.Arrays.sort(trabajos, (a, b2) -> Long.compare(b2.lastModified(), a.lastModified()));
            TextView tt = titulo("📂 Mis fotos con medidas");
            tt.setPadding(0, dp(18), 0, dp(4));
            l.addView(tt);
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd/MM/yyyy h:mm a", new java.util.Locale("es", "CO"));
            for (int i = 0; i < Math.min(30, trabajos.length); i++) {
                File f = trabajos[i];
                Button b = boton("✎  " + df.format(new java.util.Date(f.lastModified())) + "  ·  " + contarCotas(f) + " medidas", 0xFF6D4C41);
                b.setTextSize(15);
                b.setOnClickListener(v -> abrirTrabajo(f));
                b.setOnLongClickListener(v -> {
                    new android.app.AlertDialog.Builder(this).setTitle("¿Borrar este trabajo?")
                            .setPositiveButton("🗑 Borrar", (d, w) -> { f.delete(); new File(f.getPath().replace(".txt", ".jpg")).delete(); inicio(); })
                            .setNegativeButton("No", null).show();
                    return true;
                });
                l.addView(b, ancho(52));
            }
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private String trabajo; // nombre del trabajo abierto (para guardar encima)

    private File carpetaTrabajos() { File d = new File(getFilesDir(), "medidas"); d.mkdirs(); return d; }

    private int contarCotas(File f) {
        try { return Math.max(0, java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8).size()); }
        catch (Exception e) { return 0; }
    }

    /** Guarda la foto limpia y las medidas aparte, para poder moverlas otro día. */
    private void guardarTrabajo() {
        if (foto == null) return;
        if (trabajo == null) trabajo = "trabajo_" + System.currentTimeMillis();
        File jpg = new File(carpetaTrabajos(), trabajo + ".jpg"), txt = new File(carpetaTrabajos(), trabajo + ".txt");
        try {
            if (!jpg.exists()) try (java.io.FileOutputStream o = new java.io.FileOutputStream(jpg)) { foto.compress(Bitmap.CompressFormat.JPEG, 90, o); }
            StringBuilder sb = new StringBuilder();
            for (Cota c : cotas) sb.append(c.x1).append('\t').append(c.y1).append('\t').append(c.x2).append('\t').append(c.y2)
                    .append('\t').append(c.tipo).append('\t').append(c.texto == null ? "" : c.texto.replace('\t', ' ').replace('\n', ' ')).append('\n');
            java.nio.file.Files.write(txt.toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private void abrirTrabajo(File txt) {
        try {
            File jpg = new File(txt.getPath().replace(".txt", ".jpg"));
            foto = android.graphics.BitmapFactory.decodeFile(jpg.getAbsolutePath(), new android.graphics.BitmapFactory.Options() {{ inMutable = true; }});
            if (foto == null) { aviso("No se encontró la foto"); return; }
            cotas.clear();
            for (String l : java.nio.file.Files.readAllLines(txt.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p.length < 6) continue;
                Cota c = new Cota();
                c.x1 = Float.parseFloat(p[0]); c.y1 = Float.parseFloat(p[1]); c.x2 = Float.parseFloat(p[2]); c.y2 = Float.parseFloat(p[3]);
                c.tipo = Integer.parseInt(p[4]); c.texto = p[5];
                cotas.add(c);
            }
            trabajo = txt.getName().replace(".txt", "");
            editor();
        } catch (Exception e) { aviso("No se pudo abrir"); }
    }

    private void tomarFoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 7);
            return;
        }
        // Versión 22: se toma con la cámara de Mi Barra (la otra cámara no podía escribir el archivo y la foto se perdía)
        destino = null;
        try {
            startActivityForResult(new Intent(this, CamaraActivity.class).putExtra("devolver", true), FOTO);
        } catch (Exception e) { aviso("No pude abrir la cámara"); }
    }

    @Override
    public void onRequestPermissionsResult(int r, String[] p, int[] res) {
        super.onRequestPermissionsResult(r, p, res);
        if (r == 7 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) tomarFoto();
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        if (destino != null) o.putString("destino", destino.toString());
    }

    @Override
    protected void onRestoreInstanceState(Bundle b) {
        super.onRestoreInstanceState(b);
        if (b.getString("destino") != null) destino = Uri.parse(b.getString("destino"));
    }

    @Override
    protected void onActivityResult(int r, int res, Intent d) {
        super.onActivityResult(r, res, d);
        Uri u = null;
        if (r == FOTO) {
            if (res == RESULT_OK && d != null && d.getData() != null) u = d.getData();
        } else if (r == GALERIA && res == RESULT_OK && d != null) u = d.getData();
        if (u == null) return;
        try {
            final Uri fu = u;
            android.graphics.ImageDecoder.Source s = android.graphics.ImageDecoder.createSource(getContentResolver(), fu);
            foto = android.graphics.ImageDecoder.decodeBitmap(s, (dec, info, src) -> {
                int w = info.getSize().getWidth(), hh = info.getSize().getHeight(), lado = Math.max(w, hh);
                if (lado > 2000) dec.setTargetSize(w * 2000 / lado, hh * 2000 / lado);
                dec.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                dec.setMutableRequired(true);
            });
            cotas.clear();
            trabajo = null;
            editor();
        } catch (Exception e) { aviso("No se pudo abrir la foto"); }
    }

    // ---------------- Editor ----------------

    private void editor() {
        LinearLayout raiz = vertical();
        raiz.setBackgroundColor(0xFF1B1F24);
        LinearLayout tipos = horizontal();
        for (int i = 0; i < 4; i++) {
            final int k = i;
            bTipos[i] = boton(TIPOS[i], COLORES[i]);
            bTipos[i].setTextSize(14);
            bTipos[i].setOnClickListener(v -> { tipoActual = k; pintarTipos(); });
            tipos.addView(bTipos[i], peso(1));
        }
        raiz.addView(tipos, ancho(48));
        TextView ay = new TextView(this);
        ay.setText("Dibuje de punta a punta. Arrastre una punta para estirarla o el número para moverla. Dos dedos: acercar y correr. Doble toque: foto completa.");
        ay.setTextColor(0xFFCFD8DC);
        ay.setPadding(dp(8), dp(4), dp(8), dp(4));
        raiz.addView(ay);
        lienzo = new Lienzo(this);
        raiz.addView(lienzo, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout f = horizontal();
        Button des = boton("↶ Deshacer", 0xFF546E7A);
        des.setOnClickListener(v -> { if (!cotas.isEmpty()) { cotas.remove(cotas.size() - 1); lienzo.invalidate(); } });
        Button gua = boton("💾 Guardar", 0xFF2E7D32);
        gua.setOnClickListener(v -> { if (guardar() != null) aviso("💾 Guardada en la Galería (Imágenes/MiBarra/Medidas)"); });
        Button wa = boton("💬 WhatsApp", 0xFF25A244);
        wa.setOnClickListener(v -> { Uri u = guardar(); if (u != null) enviar(WHATSAPP, "image/jpeg", u); });
        Button otra = boton("📷 Otra", 0xFF1565C0);
        otra.setOnClickListener(v -> { if (!cotas.isEmpty()) guardarTrabajo(); inicio(); });
        // Versión 19: botones con el ícono arriba y la palabra abajo, que no se corten
        for (Button b : new Button[]{des, gua, wa, otra}) {
            b.setText(b.getText().toString().replaceFirst(" ", "\n"));
            b.setTextSize(13);
            b.setMaxLines(2);
            b.setPadding(dp(2), dp(2), dp(2), dp(2));
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            f.addView(b, new LinearLayout.LayoutParams(0, -1, 1f));
            ((LinearLayout.LayoutParams) b.getLayoutParams()).setMargins(dp(3), dp(3), dp(3), dp(3));
        }
        raiz.addView(f, ancho(70));
        setContentView(raiz);
        pintarTipos();
    }

    private void pintarTipos() {
        for (int i = 0; i < 4; i++) {
            bTipos[i].setAlpha(i == tipoActual ? 1f : 0.45f);
            bTipos[i].setText((i == tipoActual ? "● " : "") + TIPOS[i]);
        }
    }

    private void pedirTexto(Cota c, boolean nueva) {
        EditText e = campoTexto("Ej: 1,20 m  ·  85 cm  ·  2.10", c.texto == null ? "" : c.texto);
        e.setTextSize(22);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(TIPOS[c.tipo] + ": ¿cuánto mide?").setView(e)
                .setPositiveButton("Listo", (d, w) -> {
                    c.texto = e.getText().toString().trim();
                    if (c.texto.isEmpty() && nueva) cotas.remove(c);
                    lienzo.invalidate();
                })
                .setNegativeButton(nueva ? "Cancelar" : "Cerrar", (d, w) -> { if (nueva) { cotas.remove(c); lienzo.invalidate(); } });
        if (!nueva) b.setNeutralButton("🗑 Borrar", (d, w) -> { cotas.remove(c); lienzo.invalidate(); });
        b.setCancelable(false).show();
    }

    /** Dibuja una cota: línea con flecha en las dos puntas y el número en un cuadrito. */
    static void dibujarCota(Canvas cv, Cota c, float escala) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int col = COLORES[c.tipo];
        float grosor = 5 * escala;
        float dx = c.x2 - c.x1, dy = c.y2 - c.y1, largo = (float) Math.hypot(dx, dy);
        if (largo < 1) return;
        float ux = dx / largo, uy = dy / largo, fl = 26 * escala;
        // sombra blanca para que se vea sobre cualquier foto
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xCCFFFFFF);
        p.setStrokeWidth(grosor + 4 * escala);
        cv.drawLine(c.x1, c.y1, c.x2, c.y2, p);
        p.setColor(col);
        p.setStrokeWidth(grosor);
        cv.drawLine(c.x1, c.y1, c.x2, c.y2, p);
        p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 2; k++) {
            float px = k == 0 ? c.x1 : c.x2, py = k == 0 ? c.y1 : c.y2, sx = k == 0 ? ux : -ux, sy = k == 0 ? uy : -uy;
            Path a = new Path();
            a.moveTo(px, py);
            a.lineTo(px + sx * fl - sy * fl * 0.5f, py + sy * fl + sx * fl * 0.5f);
            a.lineTo(px + sx * fl + sy * fl * 0.5f, py + sy * fl - sx * fl * 0.5f);
            a.close();
            cv.drawPath(a, p);
            // rayita de tope
            p.setStrokeWidth(grosor * 0.7f);
            cv.drawLine(px - sy * fl * 0.7f, py + sx * fl * 0.7f, px + sy * fl * 0.7f, py - sx * fl * 0.7f, p);
        }
        if (c.texto == null || c.texto.isEmpty()) return;
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(34 * escala);
        p.setTextAlign(Paint.Align.CENTER);
        float mx = (c.x1 + c.x2) / 2, my = (c.y1 + c.y2) / 2;
        float ang = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (ang > 90) ang -= 180;
        if (ang < -90) ang += 180;
        cv.save();
        cv.rotate(ang, mx, my);
        float tw = p.measureText(c.texto) + 20 * escala, th = 46 * escala;
        RectF caja = new RectF(mx - tw / 2, my - th / 2, mx + tw / 2, my + th / 2);
        p.setColor(Color.WHITE);
        cv.drawRoundRect(caja, 10 * escala, 10 * escala, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * escala);
        p.setColor(col);
        cv.drawRoundRect(caja, 10 * escala, 10 * escala, p);
        p.setStyle(Paint.Style.FILL);
        cv.drawText(c.texto, mx, my + 12 * escala, p);
        cv.restore();
    }

    private Uri guardar() {
        if (foto == null) return null;
        guardarTrabajo();
        Bitmap out = foto.copy(Bitmap.Config.ARGB_8888, true);
        Canvas cv = new Canvas(out);
        float escala = Math.max(out.getWidth(), out.getHeight()) / 1400f;
        for (Cota c : cotas) dibujarCota(cv, c, escala);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            out.compress(Bitmap.CompressFormat.JPEG, 92, bo);
            return Util.guardarBytes(this, "Medidas_" + Util.fecha() + ".jpg", "image/jpeg", bo.toByteArray());
        } catch (Exception e) { aviso("No se pudo guardar"); return null; }
    }

    /**
     * Versión 24: con dos dedos se acerca/aleja y se corre la foto; con un dedo:
     * sobre la punta de una medida la estira, sobre el número la mueve entera (un toque: cambiar el número),
     * en otra parte dibuja una medida nueva. Doble toque: foto completa otra vez.
     */
    final class Lienzo extends View {
        private final Matrix base = new Matrix(), zoom = new Matrix(), m = new Matrix(), inv = new Matrix();
        private Cota nueva, agarrada;
        private int punta; // 1 o 2: punta agarrada; 0: toda la medida
        private float x0, y0, ax, ay;
        private boolean dosDedos, movio;
        private long ultimoToque;
        private float dist0, mx0, my0;

        Lienzo(Context c) { super(c); }

        void reiniciarZoom() { zoom.reset(); invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            if (foto == null) return;
            base.setRectToRect(new RectF(0, 0, foto.getWidth(), foto.getHeight()), new RectF(0, 0, getWidth(), getHeight()), Matrix.ScaleToFit.CENTER);
            m.set(base);
            m.postConcat(zoom);
            m.invert(inv);
            c.drawBitmap(foto, m, new Paint(Paint.FILTER_BITMAP_FLAG));
            c.save();
            c.concat(m);
            float escala = Math.max(foto.getWidth(), foto.getHeight()) / 1400f;
            for (Cota k : cotas) dibujarCota(c, k, escala);
            // puntitos en las puntas para que se vea qué se puede agarrar
            Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
            pp.setColor(0xCCFFFFFF);
            float r = inv.mapRadius(dp(7));
            for (Cota k : cotas) { c.drawCircle(k.x1, k.y1, r, pp); c.drawCircle(k.x2, k.y2, r, pp); }
            c.restore();
        }

        private float[] foto(float x, float y) { float[] q = {x, y}; inv.mapPoints(q); return q; }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    dosDedos = false; movio = false; agarrada = null; nueva = null;
                    x0 = e.getX(); y0 = e.getY();
                    float[] q = foto(x0, y0);
                    float lim = inv.mapRadius(dp(30));
                    double mejor = Double.MAX_VALUE;
                    for (Cota k : cotas) {
                        double d1 = Math.hypot(k.x1 - q[0], k.y1 - q[1]), d2 = Math.hypot(k.x2 - q[0], k.y2 - q[1]);
                        double dm = Math.hypot((k.x1 + k.x2) / 2 - q[0], (k.y1 + k.y2) / 2 - q[1]);
                        if (d1 < lim && d1 < mejor) { mejor = d1; agarrada = k; punta = 1; }
                        if (d2 < lim && d2 < mejor) { mejor = d2; agarrada = k; punta = 2; }
                        if (dm < lim * 1.4 && dm < mejor) { mejor = dm; agarrada = k; punta = 0; }
                    }
                    ax = q[0]; ay = q[1];
                    if (agarrada == null) {
                        nueva = new Cota();
                        nueva.x1 = nueva.x2 = q[0]; nueva.y1 = nueva.y2 = q[1];
                        nueva.tipo = tipoActual;
                        cotas.add(nueva);
                    }
                    return true;
                }
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (e.getPointerCount() == 2) {
                        dosDedos = true;
                        if (nueva != null) { cotas.remove(nueva); nueva = null; }
                        agarrada = null;
                        dist0 = (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
                        mx0 = (e.getX(0) + e.getX(1)) / 2; my0 = (e.getY(0) + e.getY(1)) / 2;
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (dosDedos && e.getPointerCount() >= 2) {
                        float d = (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
                        float mx = (e.getX(0) + e.getX(1)) / 2, my = (e.getY(0) + e.getY(1)) / 2;
                        if (dist0 > 10) {
                            float f = d / dist0;
                            float[] v = new float[9];
                            zoom.getValues(v);
                            float actual = v[Matrix.MSCALE_X];
                            f = Math.max(1f / actual, Math.min(6f / actual, f));
                            zoom.postScale(f, f, mx, my);
                        }
                        zoom.postTranslate(mx - mx0, my - my0);
                        dist0 = d; mx0 = mx; my0 = my;
                        invalidate();
                        return true;
                    }
                    if (dosDedos) return true;
                    if (Math.hypot(e.getX() - x0, e.getY() - y0) > dp(8)) movio = true;
                    float[] q = foto(e.getX(), e.getY());
                    if (nueva != null) { nueva.x2 = q[0]; nueva.y2 = q[1]; }
                    else if (agarrada != null && movio) {
                        if (punta == 1) { agarrada.x1 = q[0]; agarrada.y1 = q[1]; }
                        else if (punta == 2) { agarrada.x2 = q[0]; agarrada.y2 = q[1]; }
                        else {
                            float dx = q[0] - ax, dy = q[1] - ay;
                            agarrada.x1 += dx; agarrada.x2 += dx; agarrada.y1 += dy; agarrada.y2 += dy;
                            ax = q[0]; ay = q[1];
                        }
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    if (dosDedos) { dosDedos = false; return true; }
                    long ahora = System.currentTimeMillis();
                    if (nueva != null) {
                        Cota c = nueva;
                        nueva = null;
                        if (!movio || Math.hypot(e.getX() - x0, e.getY() - y0) < dp(25)) {
                            cotas.remove(c);
                            if (ahora - ultimoToque < 350) reiniciarZoom(); // doble toque: foto completa
                            ultimoToque = ahora;
                            invalidate();
                            return true;
                        }
                        pedirTexto(c, true);
                        return true;
                    }
                    if (agarrada != null && !movio) pedirTexto(agarrada, false); // un toque sobre el número: cambiarlo
                    agarrada = null;
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    if (nueva != null) { cotas.remove(nueva); nueva = null; }
                    agarrada = null; dosDedos = false;
                    invalidate();
                    return true;
            }
            return true;
        }
    }
}
