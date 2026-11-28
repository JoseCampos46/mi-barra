package co.jose.mibarra;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Arma una imagen con el mapa (OpenStreetMap, gratis) y un pin rojo, y debajo la dirección. */
final class MapaFoto {
    private MapaFoto() {}

    static Bitmap crear(double lat, double lon, String lugar, String pie) {
        final int W = 768, H = 576, Z = 16;
        double n = Math.pow(2, Z);
        double xt = (lon + 180) / 360 * n;
        double rad = Math.toRadians(lat);
        double yt = (1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2 * n;
        double px = xt * 256, py = yt * 256;          // centro en pixeles del mundo
        double x0 = px - W / 2.0, y0 = py - H / 2.0;   // esquina superior izquierda

        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.BLACK);
        tp.setTextSize(30);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        StaticLayout txt = StaticLayout.Builder.obtain(lugar, 0, lugar.length(), tp, W - 40)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
        TextPaint tp2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp2.setColor(0xFF37474F);
        tp2.setTextSize(24);
        StaticLayout txt2 = StaticLayout.Builder.obtain(pie, 0, pie.length(), tp2, W - 40).build();
        int altoPie = txt.getHeight() + txt2.getHeight() + 50;

        Bitmap b = Bitmap.createBitmap(W, H + altoPie, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFFE0E0E0);
        int tx0 = (int) Math.floor(x0 / 256), ty0 = (int) Math.floor(y0 / 256);
        int tx1 = (int) Math.floor((x0 + W) / 256), ty1 = (int) Math.floor((y0 + H) / 256);
        boolean alguno = false;
        for (int tx = tx0; tx <= tx1; tx++)
            for (int ty = ty0; ty <= ty1; ty++) {
                Bitmap t = baldosa(Z, tx, ty);
                if (t == null) continue;
                alguno = true;
                c.drawBitmap(t, (float) (tx * 256 - x0), (float) (ty * 256 - y0), null);
            }
        if (!alguno) return null;

        // Pin rojo en el centro
        float cx = W / 2f, cy = H / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x55000000);
        c.drawOval(cx - 14, cy - 5, cx + 14, cy + 5, p);
        Path pin = new Path();
        pin.moveTo(cx, cy);
        pin.cubicTo(cx - 34, cy - 40, cx - 26, cy - 78, cx, cy - 78);
        pin.cubicTo(cx + 26, cy - 78, cx + 34, cy - 40, cx, cy);
        p.setColor(0xFFE53935);
        c.drawPath(pin, p);
        p.setColor(Color.WHITE);
        c.drawCircle(cx, cy - 52, 11, p);

        Paint at = new Paint(Paint.ANTI_ALIAS_FLAG);
        at.setTextSize(16);
        at.setColor(0xCC000000);
        c.drawText("© OpenStreetMap", W - 140, H - 8, at);

        Paint blanco = new Paint();
        blanco.setColor(Color.WHITE);
        c.drawRect(0, H, W, H + altoPie, blanco);
        c.save();
        c.translate(20, H + 20);
        txt.draw(c);
        c.translate(0, txt.getHeight() + 10);
        txt2.draw(c);
        c.restore();
        return b;
    }

    static Bitmap baldosa(int z, int x, int y) {   // Versión 34: también la usa el GPS
        try {
            HttpURLConnection h = (HttpURLConnection) new URL("https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png").openConnection();
            h.setRequestProperty("User-Agent", "MiBarra/2.0 (app personal Android)");
            h.setConnectTimeout(6000);
            h.setReadTimeout(8000);
            try (InputStream in = h.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
