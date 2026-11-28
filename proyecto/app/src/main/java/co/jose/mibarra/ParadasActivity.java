package co.jose.mibarra;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rutas con paradas: viaje de ciudad a ciudad (orden fijo) y reparto (varias direcciones). */
public class ParadasActivity extends Base {

    static final class Parada {
        double lat, lon;
        String nombre = "", nota = "", tel = "";
        boolean hecha;
    }

    private File carpeta, actual;
    private String nombre = "", modo = "viaje";
    private final List<Parada> paradas = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        carpeta = new File(getFilesDir(), "paradas");
        carpeta.mkdirs();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        String m = getIntent().getStringExtra("modo");
        if (Intent.ACTION_SEND.equals(getIntent().getAction())) { mostrarLista(); recibido(getIntent().getStringExtra(Intent.EXTRA_TEXT)); }
        else if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getData() != null) {
            // geo:3.45,-76.53?q=3.45,-76.53(Nombre)  ← así lo manda WhatsApp al tocar una ubicación
            mostrarLista();
            recibido(Uri.decode(getIntent().getData().toString()));
        }
        else if (m != null) nueva(m); else mostrarLista();
    }

    @Override
    public void onBackPressed() {
        if (actual != null) { guardar(); mostrarLista(); } else super.onBackPressed();
    }

    @Override
    protected void onPause() { super.onPause(); guardar(); }

    /** Un punto que llegó compartido desde WhatsApp o Google Maps: se pregunta a qué lista agregarlo. */
    private void recibido(String txt) {
        if (txt == null) { aviso("No llegó ninguna ubicación"); return; }
        aviso("📍 Leyendo la ubicación…");
        new Thread(() -> {
            double[] ll = coordenadas(txt);
            if (ll == null) ll = coordenadas(abrirEnlaceCorto(txt));
            double[] fin = ll;
            String sitio = fin == null ? "" : Lugar.describir(this, fin[0], fin[1]).split("\n")[0];
            runOnUiThread(() -> {
                if (fin == null) { aviso("Ese mensaje no trae una ubicación que yo pueda leer"); return; }
                List<File> fs = new ArrayList<>();
                List<String> nombres = new ArrayList<>();
                nombres.add("＋ Nuevo recorrido (recoger o entregar)");
                nombres.add("＋ Nuevo viaje GPS");
                for (File f : archivos(carpeta)) if (leer(f)) { fs.add(f); nombres.add(nombre); }
                new AlertDialog.Builder(this).setTitle("¿A qué lista lo agrego?")
                        .setItems(nombres.toArray(new String[0]), (d, w) -> {
                            if (w == 0) nueva("reparto");
                            else if (w == 1) nueva("viaje");
                            else { leer(fs.get(w - 2)); actual = fs.get(w - 2); mostrarEditor(); }
                            agregar(fin[0], fin[1], sitio);
                        }).setNegativeButton("Cancelar", null).show();
            });
        }).start();
    }

    // ================= Lista =================

    private void mostrarLista() {
        actual = null;
        LinearLayout l = vertical();
        int p = dp(14);
        l.setPadding(p, p, p, p);
        l.addView(titulo("viaje".equals(getIntent().getStringExtra("modo")) ? "🗺 Rutas" : "🗺 Rutas y recorridos"));
        Button v = boton("＋  GPS: viaje a una ciudad o dirección", 0xFF1565C0);
        v.setOnClickListener(x -> nueva("viaje"));
        l.addView(v, ancho(58));
        Button r = boton("＋  Recorrido: recoger personal o entregas", 0xFF2E7D32);
        r.setOnClickListener(x -> nueva("reparto"));
        l.addView(r, ancho(58));
        // Versión 33: los recorridos grabados (con las paradas de cada señor) también se ven desde aquí
        Button rg = boton("🛣  Recorridos: grabar uno nuevo o ver los grabados", 0xFF546E7A);
        rg.setOnClickListener(x -> startActivity(new Intent(this, RecorridoActivity.class)));
        l.addView(rg, ancho(54));
        TextView tg = new TextView(this);
        tg.setText("\n📂 Rutas guardadas");
        tg.setTextSize(16);
        tg.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(tg);
        for (File f : archivos(carpeta)) {
            if (!leer(f)) continue;
            int hechas = 0;
            for (Parada q : paradas) if (q.hecha) hechas++;
            String sub = "reparto".equals(modo) ? "📦 " + hechas + " de " + paradas.size() + " entregados" : "🗺 " + paradas.size() + " puntos";
            Button bt = boton(nombre + "\n" + sub, 0xFF37474F);
            bt.setOnClickListener(x -> { leer(f); actual = f; mostrarEditor(); });
            l.addView(bt, ancho(68));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private void nueva(String m) {
        modo = m;
        paradas.clear();
        nombre = "viaje".equals(m) ? "Viaje " : "Recorrido ";
        nombre += new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new java.util.Date());
        actual = new File(carpeta, System.currentTimeMillis() + ".txt");
        mostrarEditor();
    }

    // ================= Editor =================

    private void mostrarEditor() {
        boolean reparto = "reparto".equals(modo);
        LinearLayout l = vertical();
        int p = dp(12);
        l.setPadding(p, p, p, p);

        EditText en = campoTexto("Nombre", nombre);
        en.setTextSize(19);
        en.setTypeface(Typeface.DEFAULT_BOLD);
        en.setOnFocusChangeListener((v, f) -> { if (!f) nombre = en.getText().toString().trim(); });
        l.addView(en);

        if (reparto) {
            int hechas = 0;
            for (Parada q : paradas) if (q.hecha) hechas++;
            TextView pr = new TextView(this);
            pr.setText("📦 " + hechas + " de " + paradas.size() + " entregados");
            pr.setTextSize(18);
            pr.setTypeface(Typeface.DEFAULT_BOLD);
            pr.setTextColor(0xFF2E7D32);
            l.addView(pr);
            Button sig = boton("➡  IR A LA SIGUIENTE PARADA", 0xFF2E7D32);
            sig.setTextSize(18);
            sig.setOnClickListener(v -> siguiente());
            l.addView(sig, ancho(64));
            // Versión 29: recoger personal — el mismo recorrido al revés para llevarlos a la casa
            Button reg = boton("🔄  Regreso: mismo recorrido al revés", 0xFF6A1B9A);
            reg.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("🔄 Regreso")
                    .setMessage("Se hace una copia con los mismos puntos al revés (del último al primero), todos sin marcar.")
                    .setPositiveButton("Hacer el regreso", (d, w) -> regreso()).setNegativeButton("Cancelar", null).show());
            l.addView(reg, ancho(50));
            Button ord = boton("🔀  Ordenar: la más cercana primero", 0xFF546E7A);
            ord.setOnClickListener(v -> ordenarPorCercania());
            l.addView(ord, ancho(50));
        } else {
            Button todo = boton("🗺  Abrir la ruta completa en Google Maps", 0xFF1565C0);
            todo.setTextSize(17);
            todo.setOnClickListener(v -> abrirRuta());
            l.addView(todo, ancho(60));
            // Versión 33: Mi Barra vigila la ruta (aunque la pantalla esté apagada) y marca solo el punto si se sale
            Button vig = boton("🛡  Vigilar la ruta: me avisa y marca el punto si me salgo", 0xFF2E7D32);
            vig.setOnClickListener(v -> vigilarRuta());
            l.addView(vig, ancho(58));
            Button mapa = boton("⬇  Bajar el mapa de la zona en Google Maps (sin internet)", 0xFF00897B);
            mapa.setOnClickListener(v -> bajarMapa());
            l.addView(mapa, ancho(58));
            l.addView(resumenViaje());
            // Versión 29: si se sale de la ruta (a comprar algo, a descansar), se marca el punto y se manda a WhatsApp
            Button sal = boton("📍  Me salí aquí: marcar y enviar", 0xFFEF6C00);
            sal.setTextSize(17);
            sal.setOnClickListener(v -> meSali());
            l.addView(sal, ancho(58));
            String vp = Direcciones.puntoSalida(this);
            if (!vp.isEmpty()) {
                Button vol = boton("↩  Volver al punto donde me salí", 0xFFC62828);
                vol.setTextSize(17);
                vol.setOnClickListener(v -> {
                    String[] ll = vp.split(",");
                    Parada q = new Parada();
                    q.lat = Double.parseDouble(ll[0]); q.lon = Double.parseDouble(ll[1]);
                    navegar(q);
                });
                l.addView(vol, ancho(58));
            }
            Button des = boton(textoDescanso(), 0xFF6D4C41);
            des.setOnClickListener(v -> escogerDescanso());
            l.addView(des, ancho(50));
            Button grab = boton("🛣️  Grabar el recorrido mientras viajo", 0xFF546E7A);
            grab.setOnClickListener(v -> startActivity(new Intent(this, RecorridoActivity.class)));
            l.addView(grab, ancho(50));
        }

        for (int i = 0; i < paradas.size(); i++) l.addView(tarjeta(i, reparto));

        TextView ag = new TextView(this);
        ag.setText("\nAgregar " + (reparto ? "dirección" : "punto (ciudad, parada, destino)") + ":");
        ag.setTextSize(16);
        ag.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(ag);
        Button aqui = boton("📍  Donde estoy ahora", 0xFF37474F);
        aqui.setOnClickListener(v -> agregarAqui());
        l.addView(aqui, ancho(50));
        Button bus = boton("🔎  Escribir dirección o ciudad", 0xFF37474F);
        bus.setOnClickListener(v -> buscarDireccion());
        l.addView(bus, ancho(50));
        Button peg = boton("📋  Pegar enlace de Google Maps o ubicación de WhatsApp", 0xFF37474F);
        peg.setOnClickListener(v -> pegarEnlace());
        l.addView(peg, ancho(58));

        ponerBuscadas(l);

        Button comp = boton("💬  Compartir la lista por WhatsApp", 0xFF25A244);
        comp.setOnClickListener(v -> compartir());
        l.addView(comp, ancho(54));
        Button borrar = boton("🗑  Borrar", 0xFFC62828);
        borrar.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("¿Borrar esta lista?")
                .setPositiveButton("Sí", (d, w) -> { actual.delete(); actual = null; mostrarLista(); })
                .setNegativeButton("No", null).show());
        l.addView(borrar, ancho(50));

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private LinearLayout tarjeta(int i, boolean reparto) {
        Parada q = paradas.get(i);
        LinearLayout c = vertical();
        GradientDrawable g = new GradientDrawable();
        g.setColor(q.hecha ? 0xFFC8E6C9 : 0xFFF1F4F7);
        g.setCornerRadius(dp(10));
        c.setBackground(g);
        c.setPadding(dp(10), dp(6), dp(10), dp(6));
        TextView t = new TextView(this);
        String letra = reparto ? (i + 1) + "." : (i == 0 ? "🟢 Salida:" : i == paradas.size() - 1 ? "🔴 Destino:" : "⚪ " + i + ".");
        t.setText(letra + " " + (q.hecha ? "✔ " : "") + q.nombre + (q.nota.isEmpty() ? "" : "\n   " + q.nota)
                + (q.tel.isEmpty() ? "" : "\n   📞 " + q.tel));
        t.setTextSize(16);
        t.setTextColor(Color.BLACK);
        c.addView(t);
        LinearLayout f = horizontal();
        f.addView(mini("🧭", () -> navegar(q)), peso(1));
        if (!q.tel.isEmpty()) {
            f.addView(mini("📞", () -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + q.tel)))), peso(1));
            f.addView(mini("💬", () -> whatsappA(q.tel)), peso(1));
        }
        if (reparto) f.addView(mini(q.hecha ? "↺" : "✔", () -> { q.hecha = !q.hecha; guardar(); mostrarEditor(); }), peso(1));
        f.addView(mini("✎", () -> editar(q)), peso(1));
        if (i > 0) f.addView(mini("↑", () -> { paradas.add(i - 1, paradas.remove(i)); guardar(); mostrarEditor(); }), peso(1));
        f.addView(mini("✕", () -> { paradas.remove(i); guardar(); mostrarEditor(); }), peso(1));
        c.addView(f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    private Button mini(String t, Runnable r) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(18);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    // ================= Agregar puntos =================

    private void agregar(double lat, double lon, String sugerido) {
        LinearLayout c = vertical();
        c.setPadding(dp(16), 0, dp(16), 0);
        EditText n = campoTexto("Nombre (cliente, ciudad, sitio)", sugerido);
        EditText no = campoTexto("Nota (pedido, dirección, referencia)", "");
        EditText te = campoTexto("Teléfono (opcional)", "");
        te.setInputType(InputType.TYPE_CLASS_PHONE);
        c.addView(n); c.addView(no); c.addView(te);
        new AlertDialog.Builder(this).setTitle("Nuevo punto").setView(c)
                .setPositiveButton("Agregar", (d, w) -> {
                    Parada q = new Parada();
                    q.lat = lat; q.lon = lon;
                    q.nombre = n.getText().toString().trim().isEmpty() ? "Punto " + (paradas.size() + 1) : n.getText().toString().trim();
                    q.nota = no.getText().toString().trim();
                    q.tel = te.getText().toString().trim();
                    paradas.add(q);
                    guardar();
                    mostrarEditor();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void editar(Parada q) {
        LinearLayout c = vertical();
        c.setPadding(dp(16), 0, dp(16), 0);
        EditText n = campoTexto("Nombre", q.nombre);
        EditText no = campoTexto("Nota", q.nota);
        EditText te = campoTexto("Teléfono", q.tel);
        te.setInputType(InputType.TYPE_CLASS_PHONE);
        c.addView(n); c.addView(no); c.addView(te);
        new AlertDialog.Builder(this).setTitle("Editar punto").setView(c)
                .setPositiveButton("Guardar", (d, w) -> {
                    q.nombre = n.getText().toString().trim();
                    q.nota = no.getText().toString().trim();
                    q.tel = te.getText().toString().trim();
                    guardar();
                    mostrarEditor();
                }).setNegativeButton("Cancelar", null).show();
    }

    /** El regreso: copia del recorrido con los puntos al revés, sin marcar. */
    private void regreso() {
        guardar();
        java.util.Collections.reverse(paradas);
        for (Parada q : paradas) q.hecha = false;
        nombre = nombre.replaceFirst("^(Regreso · )?", "Regreso · ");
        actual = new File(carpeta, System.currentTimeMillis() + ".txt");
        guardar();
        mostrarEditor();
        aviso("🔄 Listo el regreso");
    }

    /** Marca el punto donde se sale de la ruta, lo guarda y lo deja listo para enviar por WhatsApp. */
    private void meSali() {
        aviso("📍 Marcando el punto…");
        ubicacion(l -> new Thread(() -> {
            String lugar = Lugar.describir(this, l.getLatitude(), l.getLongitude()).split("\n")[0];
            runOnUiThread(() -> {
                String hora = new java.text.SimpleDateFormat("h:mm a", new Locale("es", "CO")).format(new java.util.Date());
                Direcciones.guardarPuntoSalida(this, l.getLatitude(), l.getLongitude());
                String txt = "📍 Me salí de la ruta aquí (" + hora + ")" + (lugar.isEmpty() ? "" : ": " + lugar)
                        + String.format(Locale.US, "\nhttps://maps.google.com/?q=%.6f,%.6f", l.getLatitude(), l.getLongitude());
                Comun.whatsapp(this, txt);
                mostrarEditor();
            });
        }).start());
    }

    private void agregarAqui() {
        aviso("📍 Buscando su ubicación…");
        ubicacion(l -> new Thread(() -> {
            String lugar = Lugar.describir(this, l.getLatitude(), l.getLongitude());
            String corto = lugar.split("\n")[0];
            runOnUiThread(() -> agregar(l.getLatitude(), l.getLongitude(), corto));
        }).start());
    }

    private void buscarDireccion() {
        // Versión 33: también se puede buscar por barrio (con la ciudad), y así no lo manda a otro sitio
        LinearLayout c = vertical();
        c.setPadding(dp(16), 0, dp(16), 0);
        EditText e = campoTexto("Dirección o sitio (Ej: Calle 5 # 38-20)", "");
        EditText eb = campoTexto("Barrio (opcional)", "");
        EditText ec = campoTexto("Ciudad (Ej: Palmira)", getSharedPreferences("barra", MODE_PRIVATE).getString("ultimaCiudad", ""));
        c.addView(e); c.addView(eb); c.addView(ec);
        new AlertDialog.Builder(this).setTitle("¿Dónde queda?").setView(c)
                .setPositiveButton("Buscar", (d, w) -> {
                    String dir = e.getText().toString().trim(), bar = eb.getText().toString().trim(), ciu = ec.getText().toString().trim();
                    if (!ciu.isEmpty()) getSharedPreferences("barra", MODE_PRIVATE).edit().putString("ultimaCiudad", ciu).apply();
                    StringBuilder qb = new StringBuilder(dir);
                    if (!bar.isEmpty()) qb.append(qb.length() > 0 ? ", " : "").append(bar.toLowerCase(Locale.ROOT).startsWith("barrio") ? bar : "Barrio " + bar);
                    if (!ciu.isEmpty()) qb.append(qb.length() > 0 ? ", " : "").append(ciu);
                    String q = qb.toString().trim();
                    if (q.isEmpty()) return;
                    aviso("🔎 Buscando…");
                    new Thread(() -> {
                        List<Address> rs = null;
                        try {
                            String consulta = q.toLowerCase(Locale.ROOT).contains("colombia") ? q : q + ", Colombia";
                            rs = new Geocoder(this, new Locale("es", "CO")).getFromLocationName(consulta, 8);
                        } catch (Exception ignored) {}
                        List<Address> fin = rs;
                        runOnUiThread(() -> elegirResultado(q, fin));
                    }).start();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void elegirResultado(String q, List<Address> rs) {
        if (rs == null || rs.isEmpty()) {
            aviso("No la encontré. Revise que tenga internet, o use 'Pegar enlace de Google Maps'.");
            return;
        }
        // Versión 33: cada resultado muestra la dirección, el barrio y la ciudad, para no irse a otro sitio
        String[] op = new String[rs.size()];
        CharSequence[] vista = new CharSequence[rs.size()];
        String[] lugares = new String[rs.size()];
        for (int i = 0; i < rs.size(); i++) {
            Address a = rs.get(i);
            String linea = a.getMaxAddressLineIndex() >= 0 ? a.getAddressLine(0) : q;
            String dir = linea.split(",")[0].trim();
            lugares[i] = barrioCiudad(a);
            op[i] = dir + "\n   " + lugares[i];
            // si le falta el barrio o la ciudad, sale en rojo para que lo revise antes de ir
            android.text.SpannableString sp = new android.text.SpannableString(op[i] + (completa(a) ? "" : "\n   ⚠ Dirección incompleta"));
            if (!completa(a)) sp.setSpan(new android.text.style.ForegroundColorSpan(0xFFD32F2F), 0, sp.length(), 0);
            vista[i] = sp;
        }
        new AlertDialog.Builder(this).setTitle("¿Cuál es?")
                .setItems(vista, (d, w) -> {
                    Address a = rs.get(w);
                    guardarBuscada(op[w].split("\n")[0], lugares[w], a.getLatitude(), a.getLongitude());
                    agregar(a.getLatitude(), a.getLongitude(), q);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void pegarEnlace() {
        EditText e = campoTexto("Pegue aquí el enlace o las coordenadas", "");
        android.content.ClipboardManager cb = getSystemService(android.content.ClipboardManager.class);
        if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
            CharSequence t = cb.getPrimaryClip().getItemAt(0).getText();
            if (t != null) e.setText(t);
        }
        new AlertDialog.Builder(this).setTitle("Enlace o ubicación").setView(e)
                .setPositiveButton("Agregar", (d, w) -> {
                    String txt = e.getText().toString();
                    new Thread(() -> {
                        double[] ll = coordenadas(txt);
                        if (ll == null) ll = coordenadas(abrirEnlaceCorto(txt));
                        double[] fin = ll;
                        runOnUiThread(() -> {
                            if (fin == null) aviso("Ese enlace no trae coordenadas. En Google Maps deje presionado el sitio y copie los números que salen arriba.");
                            else agregar(fin[0], fin[1], "");
                        });
                    }).start();
                }).setNegativeButton("Cancelar", null).show();
    }

    /** Los enlaces cortos (maps.app.goo.gl) esconden las coordenadas: se sigue la redirección. */
    static String abrirEnlaceCorto(String txt) {
        Matcher m = Pattern.compile("https?://\\S+").matcher(txt);
        if (!m.find()) return "";
        String url = m.group();
        try {
            for (int k = 0; k < 4; k++) {
                java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                h.setInstanceFollowRedirects(false);
                h.setConnectTimeout(6000);
                h.setReadTimeout(6000);
                String sig = h.getHeaderField("Location");
                h.disconnect();
                if (sig == null) break;
                url = java.net.URLDecoder.decode(sig, "UTF-8");
                if (coordenadas(url) != null) return url;
            }
        } catch (Exception ignored) {}
        return url;
    }

    /** Saca las coordenadas de un texto: "3.45,-76.53", "…@3.45,-76.53,15z", "…q=3.45,-76.53". */
    static double[] coordenadas(String s) {
        Matcher m = Pattern.compile("(-?\\d{1,2}\\.\\d{3,})\\s*,\\s*(-?\\d{1,3}\\.\\d{3,})").matcher(s);
        if (m.find()) {
            double la = Double.parseDouble(m.group(1)), lo = Double.parseDouble(m.group(2));
            if (Math.abs(la) <= 90 && Math.abs(lo) <= 180) return new double[]{la, lo};
        }
        return null;
    }

    // ================= Navegar =================

    private void navegar(Parada q) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "google.navigation:q=%.6f,%.6f", q.lat, q.lon)));
        i.setPackage("com.google.android.apps.maps");
        try { startActivity(i); }
        catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US,
                    "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f", q.lat, q.lon))));
        }
    }

    private void siguiente() {
        for (Parada q : paradas) if (!q.hecha) { aviso("➡ Vamos a: " + q.nombre); navegar(q); return; }
        aviso(paradas.isEmpty() ? "Primero agregue las direcciones" : "🎉 ¡Todo entregado!");
    }

    // ---------------- Descansos del viaje ----------------

    private String textoDescanso() {
        int cada = getSharedPreferences("barra", MODE_PRIVATE).getInt("descansoCada", 120);
        int dur = getSharedPreferences("barra", MODE_PRIVATE).getInt("descansoDur", 15);
        if (cada <= 0) return "☕  Descansos: sin aviso (toque para poner)";
        return "☕  Descanso cada " + horas(cada) + " de " + dur + " min (le aviso por voz)";
    }

    private static String horas(int min) {
        if (min % 60 == 0) return (min / 60) + (min == 60 ? " hora" : " horas");
        return (min / 60) + " h " + (min % 60) + " min";
    }

    /** Distancia aproximada por carretera, tiempo y cuántos descansos tocan. */
    private TextView resumenViaje() {
        TextView t = new TextView(this);
        t.setTextSize(16);
        t.setTextColor(0xFF3E2723);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFFFF3E0);
        g.setCornerRadius(dp(12));
        t.setBackground(g);
        double km = 0;
        for (int i = 1; i < paradas.size(); i++)
            km += Recorrido.distancia(paradas.get(i - 1).lat, paradas.get(i - 1).lon, paradas.get(i).lat, paradas.get(i).lon) / 1000.0;
        km *= 1.3; // las carreteras dan vueltas: más o menos 30 % más que en línea recta
        int vel = getSharedPreferences("barra", MODE_PRIVATE).getInt("velViaje", 60);
        int cada = getSharedPreferences("barra", MODE_PRIVATE).getInt("descansoCada", 120);
        int dur = getSharedPreferences("barra", MODE_PRIVATE).getInt("descansoDur", 15);
        if (paradas.size() < 2) {
            t.setText("Agregue la salida y el destino (y las ciudades del medio) para calcular la distancia, el tiempo y los descansos.");
            return t;
        }
        double horasManejo = km / vel;
        int nDesc = cada > 0 ? (int) Math.floor(horasManejo * 60 / cada) : 0;
        double total = horasManejo + nDesc * dur / 60.0;
        t.setText(String.format(Locale.US, "🧭 Unos %d km por carretera\n⏱ Manejando a %d km/h: %s\n☕ Descansos: %d de %d min\n🏁 Total con descansos: %s",
                Math.round(km), vel, horas((int) Math.round(horasManejo * 60)), nDesc, dur, horas((int) Math.round(total * 60))));
        t.setOnClickListener(v -> {
            String[] ops = {"40 km/h", "50 km/h", "60 km/h", "70 km/h", "80 km/h", "90 km/h"};
            new android.app.AlertDialog.Builder(this).setTitle("¿A qué velocidad promedio va?")
                    .setItems(ops, (d, w) -> { getSharedPreferences("barra", MODE_PRIVATE).edit().putInt("velViaje", 40 + w * 10).apply(); mostrarEditor(); })
                    .show();
        });
        return t;
    }

    private void escogerDescanso() {
        final int[] cadaOps = {0, 60, 90, 120, 150, 180};
        String[] ops = {"Sin aviso de descanso", "Cada 1 hora", "Cada 1 hora y media", "Cada 2 horas", "Cada 2 horas y media", "Cada 3 horas"};
        new android.app.AlertDialog.Builder(this).setTitle("¿Cada cuánto quiere descansar?")
                .setItems(ops, (d, w) -> {
                    getSharedPreferences("barra", MODE_PRIVATE).edit().putInt("descansoCada", cadaOps[w]).apply();
                    if (cadaOps[w] == 0) { mostrarEditor(); return; }
                    final int[] durOps = {10, 15, 20, 30, 45};
                    String[] d2 = {"10 minutos", "15 minutos", "20 minutos", "30 minutos", "45 minutos"};
                    new android.app.AlertDialog.Builder(this).setTitle("¿Cuánto dura cada descanso?")
                            .setItems(d2, (dd, ww) -> {
                                getSharedPreferences("barra", MODE_PRIVATE).edit().putInt("descansoDur", durOps[ww]).apply();
                                aviso("☕ Listo. Cuando grabe el recorrido, le aviso por voz cuándo descansar.");
                                mostrarEditor();
                            }).show();
                }).show();
    }

    private void abrirRuta() {
        if (paradas.size() < 2) { aviso("Agregue al menos la salida y el destino"); return; }
        StringBuilder u = new StringBuilder("https://www.google.com/maps/dir/?api=1");
        Parada a = paradas.get(0), z = paradas.get(paradas.size() - 1);
        u.append(String.format(Locale.US, "&origin=%.6f,%.6f&destination=%.6f,%.6f", a.lat, a.lon, z.lat, z.lon));
        if (paradas.size() > 2) {
            u.append("&waypoints=");
            for (int i = 1; i < paradas.size() - 1 && i <= 9; i++) {
                if (i > 1) u.append("%7C");
                u.append(String.format(Locale.US, "%.6f,%.6f", paradas.get(i).lat, paradas.get(i).lon));
            }
        }
        u.append("&travelmode=driving");
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(u.toString()));
        i.setPackage("com.google.android.apps.maps");
        try { startActivity(i); } catch (ActivityNotFoundException e) { i.setPackage(null); startActivity(i); }
    }

    private void ordenarPorCercania() {
        aviso("📍 Ordenando desde donde está…");
        ubicacion(l -> {
            List<Parada> pend = new ArrayList<>(), hechas = new ArrayList<>(), orden = new ArrayList<>();
            for (Parada q : paradas) (q.hecha ? hechas : pend).add(q);
            double la = l.getLatitude(), lo = l.getLongitude();
            while (!pend.isEmpty()) {
                Parada mejor = pend.get(0);
                double dm = Double.MAX_VALUE;
                for (Parada q : pend) {
                    double d = Recorrido.distancia(la, lo, q.lat, q.lon);
                    if (d < dm) { dm = d; mejor = q; }
                }
                orden.add(mejor);
                pend.remove(mejor);
                la = mejor.lat; lo = mejor.lon;
            }
            paradas.clear();
            paradas.addAll(hechas);
            paradas.addAll(orden);
            guardar();
            mostrarEditor();
            aviso("🔀 Listo: quedó la más cercana primero");
        });
    }

    private void ubicacion(Consumer<Location> listo) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            aviso("Permita la ubicación y vuelva a tocar");
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        LocationManager lm = getSystemService(LocationManager.class);
        boolean[] hecho = {false};
        Location[] mejor = {null};
        LocationListener ll = new LocationListener() {
            @Override public void onLocationChanged(Location l) {
                if (mejor[0] == null || l.getAccuracy() < mejor[0].getAccuracy()) mejor[0] = l;
                if (!hecho[0] && l.getAccuracy() <= 30) { hecho[0] = true; lm.removeUpdates(this); listo.accept(l); }
            }
            @Override public void onStatusChanged(String p, int s, Bundle b) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };
        try {
            for (String pr : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                Location u = lm.getLastKnownLocation(pr);
                if (u != null && System.currentTimeMillis() - u.getTime() < 60000 && u.getAccuracy() <= 30) {
                    listo.accept(u);
                    return;
                }
                if (lm.isProviderEnabled(pr)) lm.requestLocationUpdates(pr, 1000, 0, ll, Looper.getMainLooper());
            }
        } catch (SecurityException e) { return; }
        h.postDelayed(() -> {
            if (hecho[0]) return;
            hecho[0] = true;
            lm.removeUpdates(ll);
            if (mejor[0] != null) listo.accept(mejor[0]); else aviso("No encontré señal de GPS");
        }, 15000);
    }

    private void whatsappA(String tel) {
        String n = tel.replaceAll("[^0-9]", "");
        if (n.length() == 10) n = "57" + n;
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + n)));
    }

    private void compartir() {
        StringBuilder s = new StringBuilder(("reparto".equals(modo) ? "📦 " : "🗺 ") + nombre + "\n\n");
        for (int i = 0; i < paradas.size(); i++) {
            Parada q = paradas.get(i);
            s.append(i + 1).append(". ").append(q.hecha ? "✔ " : "").append(q.nombre).append('\n');
            if (!q.nota.isEmpty()) s.append("   ").append(q.nota).append('\n');
            if (!q.tel.isEmpty()) s.append("   📞 ").append(q.tel).append('\n');
            s.append(String.format(Locale.US, "   https://maps.google.com/?q=%.6f,%.6f\n", q.lat, q.lon));
        }
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s.toString());
        i.setPackage(WHATSAPP);
        try { startActivity(i); } catch (ActivityNotFoundException e) { i.setPackage(null); startActivity(Intent.createChooser(i, "Compartir")); }
    }

    // ================= Guardar / leer =================

    // ================= Versión 33: direcciones buscadas =================

    private static boolean completa(Address a) {
        return a.getSubLocality() != null && (a.getLocality() != null || a.getSubAdminArea() != null);
    }

    private static String barrioCiudad(Address a) {
        StringBuilder s = new StringBuilder();
        if (a.getSubLocality() != null) s.append("Barrio ").append(a.getSubLocality());
        String ciudad = a.getLocality() != null ? a.getLocality() : a.getSubAdminArea();
        if (ciudad != null) s.append(s.length() > 0 ? " · " : "").append(ciudad);
        if (a.getAdminArea() != null) s.append(s.length() > 0 ? ", " : "").append(a.getAdminArea().replace("Valle del Cauca", "Valle"));
        return s.length() == 0 ? "(sin barrio ni ciudad)" : s.toString();
    }

    private org.json.JSONArray buscadas() {
        try { return Direcciones.lista(this); }
        catch (Exception e) { return new org.json.JSONArray(); }
    }

    private void guardarBuscadas(org.json.JSONArray a) {
        Direcciones.guardar(this, a);
    }

    private void guardarBuscada(String dir, String lugar, double lat, double lon) {
        try {
            org.json.JSONArray viejo = buscadas(), nuevo = new org.json.JSONArray();
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("dir", dir); o.put("lugar", lugar); o.put("lat", lat); o.put("lon", lon);
            nuevo.put(o);
            for (int i = 0; i < viejo.length() && nuevo.length() < 30; i++) {
                org.json.JSONObject x = viejo.getJSONObject(i);
                if (Math.abs(x.getDouble("lat") - lat) < 1e-5 && Math.abs(x.getDouble("lon") - lon) < 1e-5) continue;
                nuevo.put(x);
            }
            guardarBuscadas(nuevo);
        } catch (Exception ignored) {}
    }

    /** La lista de direcciones que ha buscado: ▶ ir, ＋ agregar a esta lista, 🗑 borrar. */
    private void ponerBuscadas(LinearLayout l) {
        org.json.JSONArray a = buscadas();
        if (a.length() == 0) return;
        TextView t = new TextView(this);
        t.setText("\n🔎 Direcciones buscadas");
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(t);
        for (int i = 0; i < a.length(); i++) {
            final int idx = i;
            org.json.JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            LinearLayout c = vertical();
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFFF1F4F7);
            g.setCornerRadius(dp(10));
            c.setBackground(g);
            c.setPadding(dp(10), dp(6), dp(10), dp(6));
            TextView d = new TextView(this);
            String lug = o.optString("lugar");
            boolean ok = lug.startsWith("Barrio ") && lug.contains("·");
            d.setText(o.optString("dir") + "\n" + lug + (ok ? "" : "\n⚠ Dirección incompleta: revise el barrio o la ciudad"));
            d.setTextSize(16);
            d.setTextColor(ok ? Color.BLACK : 0xFFD32F2F);
            c.addView(d);
            LinearLayout f = horizontal();
            Parada q = new Parada();
            q.lat = o.optDouble("lat"); q.lon = o.optDouble("lon"); q.nombre = o.optString("dir");
            f.addView(mini("▶ Ir", () -> navegar(q)), peso(1));
            f.addView(mini("＋", () -> agregar(q.lat, q.lon, q.nombre)), peso(1));
            f.addView(mini("🗑", () -> {
                org.json.JSONArray b = buscadas(), n = new org.json.JSONArray();
                for (int k = 0; k < b.length(); k++) if (k != idx) n.put(b.opt(k));
                guardarBuscadas(n);
                mostrarEditor();
            }), peso(1));
            c.addView(f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            l.addView(c, lp);
        }
        Button todas = boton("🗑  Borrar todas las direcciones buscadas", 0xFF9E9E9E);
        todas.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("¿Borrar todas las direcciones buscadas?")
                .setPositiveButton("Sí", (dd, w) -> { guardarBuscadas(new org.json.JSONArray()); mostrarEditor(); })
                .setNegativeButton("No", null).show());
        l.addView(todas, ancho(46));
    }

    // ================= Versión 33: vigilar la ruta y mapa sin conexión =================

    /** Baja una vez el trazado por las calles (con internet) y Mi Barra lo vigila sin internet, con la pantalla apagada. */
    private void vigilarRuta() {
        if (paradas.size() < 2) { aviso("Agregue al menos la salida y el destino"); return; }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            aviso("Permita la ubicación y vuelva a tocar");
            return;
        }
        pedirSinAhorroBateria();
        guardar();
        final String id = "ruta_" + (actual != null ? actual.getName().replace(".txt", "") : String.valueOf(System.currentTimeMillis()));
        final File f = new File(RecorridoService.carpeta(this), id + ".txt");
        final List<Parada> copia = new ArrayList<>(paradas);
        final String nom = nombre;
        aviso("🛡 Preparando la ruta…");
        new Thread(() -> {
            boolean ok = f.exists() && f.length() > 0;
            if (!ok) {
                try {
                    StringBuilder u = new StringBuilder("https://router.project-osrm.org/route/v1/driving/");
                    for (int i = 0; i < copia.size(); i++)
                        u.append(i == 0 ? "" : ";").append(String.format(Locale.US, "%.6f,%.6f", copia.get(i).lon, copia.get(i).lat));
                    u.append("?overview=full&geometries=geojson");
                    java.net.HttpURLConnection h2 = (java.net.HttpURLConnection) new java.net.URL(u.toString()).openConnection();
                    h2.setRequestProperty("User-Agent", "MiBarra/33 (app personal Android)");
                    h2.setConnectTimeout(8000);
                    h2.setReadTimeout(15000);
                    String js;
                    try (java.io.InputStream in = h2.getInputStream()) { js = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
                    org.json.JSONArray co = new org.json.JSONObject(js).getJSONArray("routes").getJSONObject(0)
                            .getJSONObject("geometry").getJSONArray("coordinates");
                    Recorrido r = new Recorrido();
                    r.nombre = "Ruta · " + nom;
                    for (int i = 0; i < co.length(); i++) {
                        org.json.JSONArray c = co.getJSONArray(i);
                        r.puntos.add(new double[]{c.getDouble(1), c.getDouble(0), 0, 0});
                    }
                    for (Parada q : copia)
                        r.boyas.add(new String[]{String.format(Locale.US, "%.6f", q.lat), String.format(Locale.US, "%.6f", q.lon), "Parada", q.nombre, q.nota});
                    r.guardar(f);
                    Direcciones.copiarRuta(this, f, r.nombre);
                    ok = true;
                } catch (Exception ignored) {}
            }
            final boolean listo = ok;
            runOnUiThread(() -> {
                if (!listo) {
                    aviso("Para preparar la vigilancia necesito internet una sola vez. Conéctese y vuelva a tocar.");
                    return;
                }
                Intent i = new Intent(this, RecorridoService.class).setAction(RecorridoService.INICIAR)
                        .putExtra("limite", getSharedPreferences("barra", MODE_PRIVATE).getInt("limiteVel", 0))
                        .putExtra("descanso", getSharedPreferences("barra", MODE_PRIVATE).getInt("descansoCada", 120))
                        .putExtra("descansoDur", getSharedPreferences("barra", MODE_PRIVATE).getInt("descansoDur", 15))
                        .putExtra("seguir", id);
                try { startForegroundService(i); } catch (Exception e) { aviso("No se pudo iniciar la vigilancia"); return; }
                aviso("🛡 Vigilando la ruta. Si se sale, le aviso por voz y marco el punto.");
                abrirRuta();
            });
        }, "vigilar").start();
    }

    /** Para que el Xiaomi no apague la vigilancia cuando la pantalla está apagada. */
    private void pedirSinAhorroBateria() {
        try {
            android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName()))
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {}
    }

    /** Google Maps es el único que puede bajar su propio mapa: se abre y se explica el camino. */
    private void bajarMapa() {
        new AlertDialog.Builder(this).setTitle("⬇ Mapa sin internet")
                .setMessage("Voy a abrir Google Maps. Allá:\n\n1. Toque su foto (arriba a la derecha).\n"
                        + "2. Toque «Mapas sin conexión».\n3. Toque «Seleccionar tu propio mapa».\n"
                        + "4. Encuadre la zona del viaje y toque «Descargar».\n\nHágalo con wifi.")
                .setPositiveButton("Abrir Google Maps", (d, w) -> {
                    Intent i = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
                    if (i != null) startActivity(i); else aviso("No encontré Google Maps en este celular");
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void guardar() {
        if (actual == null) return;
        if (paradas.isEmpty() && !actual.exists()) return;
        StringBuilder s = new StringBuilder("N\t").append(limpio(nombre)).append("\nM\t").append(modo).append('\n');
        for (Parada q : paradas)
            s.append(String.format(Locale.US, "S\t%.6f\t%.6f\t", q.lat, q.lon)).append(limpio(q.nombre)).append('\t')
             .append(limpio(q.nota)).append('\t').append(limpio(q.tel)).append('\t').append(q.hecha ? 1 : 0).append('\n');
        try { Files.write(actual.toPath(), s.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }

    private boolean leer(File f) {
        try {
            paradas.clear();
            nombre = "Lista"; modo = "viaje";
            for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p[0].equals("N") && p.length > 1) nombre = p[1];
                else if (p[0].equals("M") && p.length > 1) modo = p[1];
                else if (p[0].equals("S") && p.length >= 7) {
                    Parada q = new Parada();
                    q.lat = Double.parseDouble(p[1]); q.lon = Double.parseDouble(p[2]);
                    q.nombre = p[3]; q.nota = p[4]; q.tel = p[5]; q.hecha = "1".equals(p[6]);
                    paradas.add(q);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
