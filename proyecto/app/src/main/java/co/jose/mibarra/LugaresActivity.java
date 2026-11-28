package co.jose.mibarra;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versión 17 · 📍 Mis lugares: puntos con nombre, foto y dirección.
 * Se guardan aquí mismo o compartiendo un punto desde WhatsApp / Google Maps a "Mis lugares".
 */
public class LugaresActivity extends Base {

    static final class L { String id = "", nombre = "", link = "", dir = "", foto = ""; double lat = Double.NaN, lon = Double.NaN; }

    private static final int FOTO = 41;
    private final List<L> lugares = new ArrayList<>();
    private LinearLayout lista;
    private L esperandoFoto;

    private File archivo() { return new File(getFilesDir(), "lugares.tsv"); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        leer();
        LinearLayout raiz = vertical();
        raiz.setPadding(dp(10), dp(8), dp(10), dp(8));
        raiz.addView(titulo("📍 Mis lugares"));
        LinearLayout acc = horizontal();
        Button aqui = boton("📍 Guardar donde estoy", 0xFF2E7D32);
        aqui.setOnClickListener(v -> guardarAqui());
        acc.addView(aqui, peso(1));
        // Versión 35: «Aquí estoy» (mandar la ubicación por WhatsApp) quedó aquí adentro
        Button env = boton("💬 Aquí estoy: enviar", 0xFF25A244);
        env.setOnClickListener(v -> startActivity(new android.content.Intent(this, AquiEstoyActivity.class)));
        LinearLayout.LayoutParams ep = peso(1);
        ep.leftMargin = dp(6);
        acc.addView(env, ep);
        raiz.addView(acc);
        TextView ay = new TextView(this);
        ay.setText("También: en WhatsApp, deje presionada una ubicación → Compartir → Mi Barra «Guardar en Mis lugares».");
        ay.setTextSize(13);
        ay.setTextColor(0xFF78909C);
        ay.setPadding(dp(4), dp(4), dp(4), dp(6));
        raiz.addView(ay);
        lista = vertical();
        ScrollView sv = new ScrollView(this);
        sv.addView(lista);
        raiz.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(raiz);
        recibir(getIntent());
        pintar();
    }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); recibir(i); }

    /** Llega un punto compartido desde WhatsApp, Google Maps u otra app. */
    private void recibir(Intent i) {
        if (i == null) return;
        String texto = null;
        if (Intent.ACTION_SEND.equals(i.getAction())) texto = i.getStringExtra(Intent.EXTRA_TEXT);
        else if (Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) texto = i.getData().toString();
        if (texto == null || texto.trim().isEmpty()) return;
        setIntent(new Intent());
        L x = desdeTexto(texto);
        String sugerido = i.getStringExtra(Intent.EXTRA_SUBJECT);
        if (sugerido == null || sugerido.trim().isEmpty()) {
            String primera = texto.trim().split("\n")[0];
            sugerido = primera.startsWith("http") || primera.startsWith("geo:") ? "" : primera;
        }
        pedirNombre(x, sugerido, true);
    }

    static L desdeTexto(String t) {
        L x = new L();
        x.id = String.valueOf(System.currentTimeMillis());
        Matcher u = Pattern.compile("(https?://\\S+|geo:\\S+)").matcher(t);
        if (u.find()) x.link = u.group(1);
        Matcher m = Pattern.compile("(-?\\d{1,2}\\.\\d{3,})\\s*,\\s*(-?\\d{1,3}\\.\\d{3,})").matcher(t);
        if (m.find()) {
            try { x.lat = Double.parseDouble(m.group(1)); x.lon = Double.parseDouble(m.group(2)); } catch (Exception ignored) {}
        }
        if (x.link.isEmpty() && !Double.isNaN(x.lat)) x.link = mapa(x.lat, x.lon);
        String resto = t.replace(x.link, "").trim();
        if (!resto.isEmpty() && !resto.startsWith("http")) x.dir = resto.replace('\n', ' ');
        return x;
    }

    static String mapa(double lat, double lon) { return String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon); }

    private void pedirNombre(L x, String sugerido, boolean nuevo) {
        EditText e = campoTexto("Ejemplo: Lote señora Doly", sugerido == null ? "" : sugerido);
        new AlertDialog.Builder(this).setTitle(nuevo ? "📍 ¿Cómo se llama este lugar?" : "✏ Nombre").setView(e)
                .setPositiveButton("Guardar", (d, w) -> {
                    String n = Base.limpio(e.getText().toString());
                    x.nombre = n.isEmpty() ? "Lugar " + new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date()) : n;
                    if (nuevo) lugares.add(0, x);
                    escribir();
                    pintar();
                    if (nuevo) preguntarFoto(x);
                }).setNegativeButton("Cancelar", null).show();
    }

    private void preguntarFoto(L x) {
        new AlertDialog.Builder(this).setMessage("¿Le pone una foto para reconocerlo?")
                .setPositiveButton("📷 Escoger foto", (d, w) -> escogerFoto(x)).setNegativeButton("Ahora no", null).show();
    }

    private void escogerFoto(L x) {
        esperandoFoto = x;
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), FOTO);
        } catch (Exception e) { aviso("No se pudo abrir la galería"); }
    }

    @Override
    protected void onActivityResult(int p, int r, Intent d) {
        super.onActivityResult(p, r, d);
        if (p != FOTO || r != RESULT_OK || d == null || d.getData() == null || esperandoFoto == null) return;
        try { getContentResolver().takePersistableUriPermission(d.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        esperandoFoto.foto = d.getData().toString();
        esperandoFoto = null;
        escribir();
        pintar();
    }

    @SuppressWarnings({"MissingPermission", "deprecation"})
    private void guardarAqui() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 7);
            return;
        }
        LocationManager lm = getSystemService(LocationManager.class);
        aviso("📍 Buscando dónde está…");
        java.util.function.Consumer<Location> listo = loc -> {
            if (loc == null) { aviso("No se encontró la ubicación. Salga a cielo abierto y pruebe otra vez."); return; }
            L x = new L();
            x.id = String.valueOf(System.currentTimeMillis());
            x.lat = loc.getLatitude(); x.lon = loc.getLongitude();
            x.link = mapa(x.lat, x.lon);
            new Thread(() -> {
                String dir = Lugar.describir(this, x.lat, x.lon);
                runOnUiThread(() -> { x.dir = dir == null ? "" : dir.replace('\n', ' '); pedirNombre(x, "", true); });
            }).start();
        };
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30)
                lm.getCurrentLocation(LocationManager.GPS_PROVIDER, null, getMainExecutor(), loc -> {
                    if (loc != null) listo.accept(loc);
                    else listo.accept(lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
                });
            else lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listo::accept, getMainLooper());
        } catch (Exception e) {
            try { listo.accept(lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)); } catch (Exception e2) { aviso("Prenda la Ubicación del celular"); }
        }
    }

    @Override
    public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if (r == 7 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) guardarAqui();
    }

    // ---------------- Lista ----------------

    private void pintar() {
        lista.removeAllViews();
        if (lugares.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Todavía no hay lugares guardados.");
            t.setTextSize(15);
            t.setPadding(dp(6), dp(16), dp(6), dp(6));
            lista.addView(t);
        }
        for (L x : lugares) {
            LinearLayout f = horizontal();
            f.setGravity(Gravity.CENTER_VERTICAL);
            f.setPadding(dp(8), dp(8), dp(8), dp(8));
            f.setBackground(Comun.fondo(this, 0xFFECEFF1, 12));
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setBackground(Comun.fondo(this, 0xFFCFD8DC, 10));
            if (!x.foto.isEmpty()) {
                try {
                    Bitmap bm = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), Uri.parse(x.foto)),
                            (dec, info, src) -> dec.setTargetSize(160, 160 * info.getSize().getHeight() / Math.max(1, info.getSize().getWidth())));
                    iv.setImageBitmap(bm);
                } catch (Exception ignored) { iv.setImageResource(android.R.drawable.ic_menu_mylocation); }
            } else iv.setImageResource(android.R.drawable.ic_menu_mylocation);
            f.addView(iv, new LinearLayout.LayoutParams(dp(56), dp(56)));
            TextView t = new TextView(this);
            t.setText(x.nombre + (x.dir.isEmpty() ? "" : "\n" + x.dir));
            t.setTextSize(15);
            t.setTextColor(0xFF263238);
            t.setMaxLines(3);
            t.setPadding(dp(10), 0, dp(6), 0);
            f.addView(t, peso(1));
            Button ir = boton("🧭", 0xFF1565C0);
            ir.setOnClickListener(v -> irA(x));
            f.addView(ir, new LinearLayout.LayoutParams(dp(54), dp(50)));
            f.setOnClickListener(v -> opciones(x));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            lista.addView(f, lp);
        }
    }

    private void opciones(L x) {
        new AlertDialog.Builder(this).setTitle(x.nombre)
                .setItems(new String[]{"🧭 Ir con Google Maps", "💬 Enviar por WhatsApp", "📷 Poner o cambiar la foto", "✏ Cambiar el nombre", "🗑 Borrar"}, (d, w) -> {
                    if (w == 0) irA(x);
                    else if (w == 1) Comun.whatsapp(this, "📍 " + x.nombre + (x.dir.isEmpty() ? "" : "\n" + x.dir) + "\n" + x.link);
                    else if (w == 2) escogerFoto(x);
                    else if (w == 3) pedirNombre(x, x.nombre, false);
                    else new AlertDialog.Builder(this).setMessage("¿Borrar «" + x.nombre + "»?")
                                .setPositiveButton("Borrar", (d2, w2) -> { lugares.remove(x); escribir(); pintar(); })
                                .setNegativeButton("No", null).show();
                }).show();
    }

    private void irA(L x) {
        Intent i;
        if (!Double.isNaN(x.lat)) i = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "google.navigation:q=%.6f,%.6f", x.lat, x.lon)));
        else i = new Intent(Intent.ACTION_VIEW, Uri.parse(x.link));
        try { startActivity(i); }
        catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(x.link))); } catch (Exception e2) { aviso("No se pudo abrir el mapa"); }
        }
    }

    // ---------------- Guardar ----------------

    private void leer() {
        lugares.clear();
        try {
            for (String s : Files.readAllLines(archivo().toPath(), StandardCharsets.UTF_8)) {
                String[] p = s.split("\t", -1);
                if (p.length < 7) continue;
                L x = new L();
                x.id = p[0]; x.nombre = p[1];
                try { x.lat = Double.parseDouble(p[2]); x.lon = Double.parseDouble(p[3]); } catch (Exception ignored) {}
                x.link = p[4]; x.dir = p[5]; x.foto = p[6];
                lugares.add(x);
            }
        } catch (Exception ignored) {}
    }

    private void escribir() {
        StringBuilder s = new StringBuilder();
        for (L x : lugares)
            s.append(x.id).append('\t').append(Base.limpio(x.nombre)).append('\t').append(Double.isNaN(x.lat) ? "" : String.valueOf(x.lat))
                    .append('\t').append(Double.isNaN(x.lon) ? "" : String.valueOf(x.lon)).append('\t').append(x.link)
                    .append('\t').append(Base.limpio(x.dir)).append('\t').append(x.foto).append('\n');
        try { Files.write(archivo().toPath(), s.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }
}
