package co.jose.mibarra;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Ayudas comunes para las pantallas del cuaderno, la hoja de cuentas y la cuenta de cobro. */
public abstract class Base extends VentanaActivity {

    static final int AMARILLO = 0xFFFFF59D, VERDE = 0xFFC8E6C9, ROSADO = 0xFFFFCDD2, ROJO = 0xFFC62828;

    void aviso(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    int dp(float v) { return Util.dp(this, v); }

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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
    }

    LinearLayout.LayoutParams ancho(int altoDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(altoDp));
        p.topMargin = dp(6);
        return p;
    }

    Button boton(String t, int color) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        b.setBackground(g);
        return b;
    }

    /** Botón pequeño de la barra de herramientas (B, A roja, colores). */
    Button herramienta(String t, int fondo, int letra, boolean negrita, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(letra);
        if (negrita) b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fondo);
        g.setStroke(dp(1), 0xFFB0BEC5);
        g.setCornerRadius(dp(8));
        b.setBackground(g);
        b.setOnClickListener(l);
        return b;
    }

    TextView titulo(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(22);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    /** Barra de abajo: Guardar, PNG, Imprimir, Drive (y WhatsApp si se pide). */
    LinearLayout pie(Runnable guardar, Runnable png, Runnable imprimir, Runnable whatsapp, Runnable drive) {
        LinearLayout p = horizontal();
        p.setPadding(dp(4), dp(4), dp(4), dp(4));
        p.addView(accion("💾 Guardar", 0xFF1565C0, guardar), peso(1));
        p.addView(accion("🖼 PNG", 0xFF455A64, png), peso(1));
        p.addView(accion("🖨 Imprimir", 0xFF6A1B9A, imprimir), peso(1));
        if (whatsapp != null) p.addView(accion("📤 WhatsApp", 0xFF25A244, whatsapp), peso(1));
        if (drive != null) p.addView(accion("☁️ Drive", 0xFF2E7D32, drive), peso(1));
        return p;
    }

    private Button accion(String t, int c, Runnable r) {
        Button b = boton(t, c);
        b.setTextSize(13);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    /** Dibuja una vista armada fuera de pantalla y la convierte en imagen. */
    Bitmap aImagen(View v, int anchoPx) {
        v.measure(View.MeasureSpec.makeMeasureSpec(anchoPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int alto = Math.min(Math.max(v.getMeasuredHeight(), 10), 30000);
        v.layout(0, 0, anchoPx, alto);
        Bitmap b = Bitmap.createBitmap(anchoPx, alto, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        v.draw(c);
        return b;
    }

    /** Guarda la imagen en Descargas/MiBarra y devuelve su dirección (o null). */
    Uri guardarPng(Bitmap b, String nombre) {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
            return Util.guardarBytes(this, nombre + "_" + Util.fecha() + ".png", "image/png", o.toByteArray());
        } catch (Exception e) {
            aviso("No se pudo guardar: " + e.getMessage());
            return null;
        }
    }

    void imprimirHtml(String html, String titulo) {
        startActivity(new Intent(this, PrintActivity.class).putExtra("html", html).putExtra("titulo", titulo));
    }

    /** Envía archivos a otra app (Drive o WhatsApp). Si no está, deja escoger. */
    void enviar(String paquete, String mime, Uri... archivos) {
        List<Uri> us = new ArrayList<>();
        for (Uri u : archivos) if (u != null) us.add(u);
        if (us.isEmpty()) return;
        Intent i;
        if (us.size() == 1) {
            i = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, us.get(0));
        } else {
            // v44: con el tipo real (image/jpeg) WhatsApp las manda como FOTOS, no como documentos
            i = new Intent(Intent.ACTION_SEND_MULTIPLE).setType(mime == null ? "*/*" : mime)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(us));
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setPackage(paquete);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            i.setPackage(null);
            startActivity(Intent.createChooser(i, "Enviar a…"));
        }
    }

    /** Manda texto directo a WhatsApp (si no está instalado, deja escoger). */
    void textoWhatsapp(String txt) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt);
        i.setPackage(WHATSAPP);
        try { startActivity(i); }
        catch (ActivityNotFoundException e) { i.setPackage(null); startActivity(Intent.createChooser(i, "Enviar")); }
    }

    static final String DRIVE = "com.google.android.apps.docs";
    static final String WHATSAPP = "com.whatsapp";

    /** Archivos guardados de una carpeta interna, del más nuevo al más viejo. */
    File[] archivos(File carpeta) {
        File[] fs = carpeta.listFiles();
        if (fs == null) return new File[0];
        Arrays.sort(fs, (a, c) -> Long.compare(c.lastModified(), a.lastModified()));
        return fs;
    }

    static String limpio(String s) { return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim(); }

    static String hex(int color) { return String.format("#%06X", color & 0xFFFFFF); }

    EditText campoTexto(String pista, String texto) {
        EditText e = new EditText(this);
        e.setHint(pista);
        e.setText(texto);
        e.setTextSize(16);
        e.setSingleLine(true);
        return e;
    }
}
