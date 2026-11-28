package co.jose.mibarra;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

/** Versión 16: Mi Barra muestra sus propias fotos y videos, sin preguntar "Abrir con". */
public class VerActivity extends Activity {

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Uri u = getIntent().getData();
        String tipo = getIntent().getType();
        if (u == null) { finish(); return; }
        boolean esVideo = tipo != null && tipo.startsWith("video");
        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.BLACK);

        if (esVideo) {
            VideoView vv = new VideoView(this);
            MediaController mc = new MediaController(this);
            mc.setAnchorView(vv);
            vv.setMediaController(mc);
            vv.setVideoURI(u);
            vv.setOnPreparedListener(mp -> vv.start());
            raiz.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        } else {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            try {
                Bitmap bm = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), u),
                        (dec, info, src) -> {
                            int lado = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                            if (lado > 2400) dec.setTargetSampleSize(Math.max(1, lado / 2400));
                        });
                iv.setImageBitmap(bm);
            } catch (Exception e) {
                Toast.makeText(this, "No se pudo abrir la foto", Toast.LENGTH_SHORT).show();
            }
            raiz.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        }

        // Abajo: compartir, WhatsApp, borrar, volver a la cámara
        LinearLayout abajo = new LinearLayout(this);
        abajo.setGravity(Gravity.CENTER);
        abajo.setPadding(dp(10), dp(10), dp(10), dp(28));
        abajo.setBackgroundColor(0x88000000);
        String t = esVideo ? "video/*" : "image/*";
        boton(abajo, "📤 Compartir", () -> {
            try { startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType(t)
                    .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartir")); }
            catch (Exception ignored) {}
        });
        boton(abajo, "💬 WhatsApp", () -> {
            try { startActivity(new Intent(Intent.ACTION_SEND).setType(t).setPackage("com.whatsapp")
                    .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
            catch (Exception e) { Toast.makeText(this, "No encontré WhatsApp", Toast.LENGTH_SHORT).show(); }
        });
        boton(abajo, "🗑 Borrar", () -> new AlertDialog.Builder(this)
                .setMessage(esVideo ? "¿Borrar este video?" : "¿Borrar esta foto?")
                .setPositiveButton("Borrar", (d, w) -> {
                    try { getContentResolver().delete(u, null, null); } catch (Exception ignored) {}
                    setResult(RESULT_FIRST_USER);
                    finish();
                })
                .setNegativeButton("No", null).show());
        boton(abajo, "📷 Volver", this::finish);
        raiz.addView(abajo, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(raiz);
    }

    private void boton(LinearLayout l, String s, Runnable r) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x55FFFFFF);
        g.setCornerRadius(dp(14));
        t.setBackground(g);
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        l.addView(t, p);
    }

    @Override
    protected void onPostCreate(android.os.Bundle b) {
        super.onPostCreate(b);
        Util.ponerX(this);   // v44: ✕ para cerrar, arriba a la derecha
    }
}
