package co.jose.mibarra;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Versión 17: cosas que usan varias pantallas (corregir con el cursor, enviar texto). */
final class Comun {
    private Comun() {}

    static int dp(Context c, float v) { return Util.dp(c, v); }

    /**
     * Una "pantallita" donde se ve la operación con el cursor (la rayita que titila).
     * Se toca con el dedo donde se quiere corregir; no abre el teclado del celular.
     * La letra NO se achica: si es larga, se desliza de lado.
     */
    static EditText pantallita(Context c, float tam) {
        EditText e = new EditText(c);
        e.setShowSoftInputOnFocus(false);
        e.setTextSize(tam);
        e.setSingleLine(true);
        e.setHorizontallyScrolling(true);
        e.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        e.setTextColor(0xFF111111);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setCursorVisible(true);
        e.setBackground(null);
        e.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        e.requestFocus();
        return e;
    }

    /** Escribe donde está el cursor (o reemplaza lo que esté marcado). */
    static void meter(EditText e, String s) {
        Editable t = e.getText();
        int a = Math.max(0, Math.min(e.getSelectionStart(), e.getSelectionEnd()));
        int b = Math.max(0, Math.max(e.getSelectionStart(), e.getSelectionEnd()));
        if (a > t.length()) a = t.length();
        if (b > t.length()) b = t.length();
        t.replace(a, b, s);
        e.setSelection(Math.min(t.length(), a + s.length()));
    }

    /** Borra lo que está a la izquierda del cursor (o lo marcado). */
    static void borrar(EditText e) {
        Editable t = e.getText();
        int a = Math.min(e.getSelectionStart(), e.getSelectionEnd()), b = Math.max(e.getSelectionStart(), e.getSelectionEnd());
        if (a < 0) a = b = t.length();
        if (a != b) { t.delete(a, b); e.setSelection(a); return; }
        if (a > 0) { t.delete(a - 1, a); e.setSelection(a - 1); }
    }

    static void mover(EditText e, int paso) {
        int p = Math.max(0, Math.min(e.getText().length(), e.getSelectionEnd() + paso));
        e.setSelection(p);
    }

    static void poner(EditText e, String s) {
        e.setText(s);
        e.setSelection(e.getText().length());
    }

    // ---------------- Enviar texto ----------------

    static void copiar(Context c, String texto) {
        ClipboardManager cm = c.getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("Mi Barra", texto));
        Toast.makeText(c, "📋 Copiado. Péguelo donde quiera.", Toast.LENGTH_SHORT).show();
    }

    static void whatsapp(Activity a, String texto) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto).setPackage("com.whatsapp");
        try { a.startActivity(i); }
        catch (Exception e) { i.setPackage(null); a.startActivity(Intent.createChooser(i, "Enviar")); }
    }

    /** Crea una hoja nueva en el Cuaderno con este texto y la abre. */
    static void alCuaderno(Activity a, String titulo, String texto) {
        try {
            File carpeta = new File(a.getFilesDir(), "notas");
            carpeta.mkdirs();
            String s = Base.limpio(titulo) + "\n---TEXTO---\n" + texto;
            Files.write(new File(carpeta, System.currentTimeMillis() + ".txt").toPath(), s.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(a, "📝 Quedó como hoja nueva en el Cuaderno", Toast.LENGTH_SHORT).show();
            a.startActivity(new Intent(a, NotasActivity.class));
        } catch (Exception e) {
            Toast.makeText(a, "No se pudo pasar al Cuaderno", Toast.LENGTH_SHORT).show();
        }
    }

    /** Fondo redondeado de un color. */
    static GradientDrawable fondo(Context c, int color, float radio) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radio));
        return g;
    }

    static TextView chip(Context c, String s, int color, int letra) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(letra);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 4), dp(c, 8), dp(c, 4), dp(c, 8));
        t.setBackground(fondo(c, color, 10));
        return t;
    }

    static String nombreArchivo(String s) {
        String n = s.trim().replaceAll("[\\\\/:*?\"<>|\\n\\t]", " ").trim();
        return n.isEmpty() ? "Sin nombre" : n;
    }

    static int blanco() { return Color.WHITE; }
}
