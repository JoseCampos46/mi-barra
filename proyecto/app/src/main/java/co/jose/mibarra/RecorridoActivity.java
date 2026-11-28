package co.jose.mibarra;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Pantalla del recorrido: grabar, boyas, devolverse, seguir una ruta y compartir. */
public class RecorridoActivity extends Base {

    private static final int IMPORTAR = 31;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable refresco;
    private File carpeta;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        carpeta = RecorridoService.carpeta(this);
        pedirPermisos();
        mostrar();
        if (getIntent().getBooleanExtra("boya", false)) h.postDelayed(this::preguntarBoya, 600);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        mostrar();
        if (i.getBooleanExtra("boya", false)) h.postDelayed(this::preguntarBoya, 400);
    }

    @Override
    protected void onDestroy() {
        if (refresco != null) h.removeCallbacks(refresco);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (refresco == null && enDetalle) { enDetalle = false; mostrar(); }
        else super.onBackPressed();
    }

    private boolean enDetalle;

    private void pedirPermisos() {
        List<String> p = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            p.add(Manifest.permission.ACCESS_FINE_LOCATION);
            p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), 1);
    }

    private boolean hayGps() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** {grabando, id, metros, inicio, vel, velMax, fuera, salidaLat, salidaLon, lat, lon} o null */
    private String[] estado() {
        try {
            File f = RecorridoService.estado(this);
            if (!f.exists()) return null;
            String[] p = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim().split("\t");
            return p.length >= 11 && "1".equals(p[0]) ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void mostrar() {
        if (refresco != null) { h.removeCallbacks(refresco); refresco = null; }
        enDetalle = false;
        if (estado() != null) mostrarVivo();
        else mostrarLista();
    }

    // ================= Lista =================

    private void mostrarLista() {
        LinearLayout l = vertical();
        int p = dp(14);
        l.setPadding(p, p, p, p);
        l.addView(titulo("🛣️ Mis recorridos"));

        Button nuevo = boton("▶  Grabar nuevo recorrido", 0xFF2E7D32);
        nuevo.setTextSize(18);
        nuevo.setOnClickListener(v -> iniciar(null));
        l.addView(nuevo, ancho(64));

        int lim = getSharedPreferences("barra", MODE_PRIVATE).getInt("limiteVel", 0);
        Button limite = boton("🚦  Aviso de velocidad: " + (lim == 0 ? "apagado" : lim + " km/h"), 0xFF546E7A);
        limite.setOnClickListener(v -> pedirLimite());
        l.addView(limite, ancho(50));

        Button imp = boton("📥  Abrir recorrido que me enviaron (GPX)", 0xFF546E7A);
        imp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            startActivityForResult(i, IMPORTAR);
        });
        l.addView(imp, ancho(50));

        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        for (File f : archivos(carpeta)) {
            if (f.getName().equals("estado.txt")) continue;
            Recorrido r = Recorrido.leer(f);
            if (r == null || r.puntos.isEmpty()) continue;
            Button bt = boton(r.nombre + "\n" + CalculadoraActivity.dec(r.metros() / 1000, 1) + " km · "
                    + r.boyas.size() + " boyas · " + df.format(new Date(f.lastModified())), 0xFF37474F);
            bt.setOnClickListener(v -> mostrarDetalle(f));
            l.addView(bt, ancho(72));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private void pedirLimite() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setHint("Ejemplo: 80 (0 = apagado)");
        int lim = getSharedPreferences("barra", MODE_PRIVATE).getInt("limiteVel", 0);
        if (lim > 0) e.setText(String.valueOf(lim));
        new AlertDialog.Builder(this)
                .setTitle("Avisarme por voz si paso de… (km/h)")
                .setView(e)
                .setPositiveButton("Guardar", (d, w) -> {
                    int v = 0;
                    try { v = Integer.parseInt(e.getText().toString().trim()); } catch (Exception ignored) {}
                    getSharedPreferences("barra", MODE_PRIVATE).edit().putInt("limiteVel", v).apply();
                    mostrar();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void iniciar(String seguir) {
        if (!hayGps()) { pedirPermisos(); aviso("Primero permita la ubicación y vuelva a tocar"); return; }
        // Versión 33: que el Xiaomi no apague la grabación con la pantalla apagada
        try {
            android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName()))
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {}
        Intent i = new Intent(this, RecorridoService.class).setAction(RecorridoService.INICIAR)
                .putExtra("limite", getSharedPreferences("barra", MODE_PRIVATE).getInt("limiteVel", 0))
                .putExtra("descanso", getSharedPreferences("barra", MODE_PRIVATE).getInt("descansoCada", 120))
                .putExtra("descansoDur", getSharedPreferences("barra", MODE_PRIVATE).getInt("descansoDur", 15));
        if (seguir != null) i.putExtra("seguir", seguir);
        startForegroundService(i);
        aviso(seguir == null ? "🛣️ Grabando. Puede apagar la pantalla." : "🛣️ Siguiendo el recorrido. Le aviso por voz si se sale.");
        h.postDelayed(this::mostrar, 800);
    }

    // ================= En vivo =================

    private TextView tKm, tTiempo, tVel, tMax, tFuera;
    private Button bVolver;
    private RutaView mapa;
    private String idVivo;

    private void mostrarVivo() {
        String[] e = estado();
        if (e == null) { mostrarLista(); return; }
        idVivo = e[1];
        LinearLayout l = vertical();
        int p = dp(12);
        l.setPadding(p, p, p, p);
        l.setBackgroundColor(0xFF10161C);

        TextView t = new TextView(this);
        t.setText("🔴 GRABANDO");
        t.setTextColor(0xFFFF5252);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(18);
        l.addView(t);

        tKm = grande(l, 54, Color.WHITE);
        tTiempo = grande(l, 22, 0xFFB0BEC5);
        LinearLayout fila = horizontal();
        tVel = dato(fila, "Velocidad");
        tMax = dato(fila, "Máxima");
        l.addView(fila);

        tFuera = new TextView(this);
        tFuera.setText("⚠️ SE SALIÓ DE LA RUTA");
        tFuera.setTextSize(20);
        tFuera.setTypeface(Typeface.DEFAULT_BOLD);
        tFuera.setGravity(Gravity.CENTER);
        tFuera.setTextColor(Color.WHITE);
        tFuera.setBackgroundColor(0xFFC62828);
        tFuera.setPadding(0, dp(8), 0, dp(8));
        tFuera.setVisibility(View.GONE);
        l.addView(tFuera);
        bVolver = boton("🧭  Volver a la ruta con Google Maps", 0xFFC62828);
        bVolver.setVisibility(View.GONE);
        l.addView(bVolver, ancho(56));

        mapa = new RutaView(this);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        mp.topMargin = dp(8);
        l.addView(mapa, mp);

        LinearLayout bot = horizontal();
        Button boya = boton("📌 Boya", 0xFF1565C0);
        boya.setTextSize(18);
        boya.setOnClickListener(v -> preguntarBoya());
        Button aqui = boton("📍 Aquí estoy", 0xFF25A244);
        aqui.setTextSize(16);
        aqui.setOnClickListener(v -> startActivity(new Intent(this, AquiEstoyActivity.class)));
        bot.addView(boya, new LinearLayout.LayoutParams(0, dp(64), 1f));
        bot.addView(aqui, new LinearLayout.LayoutParams(0, dp(64), 1f));
        l.addView(bot);
        Button parar = boton("⏹  Parar y guardar", 0xFF455A64);
        parar.setTextSize(18);
        parar.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("¿Terminar el recorrido?")
                .setPositiveButton("Sí, guardar", (d, w) -> {
                    startService(new Intent(this, RecorridoService.class).setAction(RecorridoService.PARAR));
                    h.postDelayed(this::mostrar, 700);
                })
                .setNegativeButton("Seguir grabando", null).show());
        l.addView(parar, ancho(60));
        setContentView(l);

        refresco = new Runnable() {
            @Override public void run() {
                actualizarVivo();
                h.postDelayed(this, 1000);
            }
        };
        h.post(refresco);
    }

    private TextView grande(LinearLayout l, int tam, int color) {
        TextView t = new TextView(this);
        t.setTextSize(tam);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        return t;
    }

    private TextView dato(LinearLayout fila, String etq) {
        LinearLayout c = vertical();
        c.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF1E272E);
        g.setCornerRadius(dp(12));
        c.setBackground(g);
        c.setPadding(0, dp(6), 0, dp(6));
        TextView v = new TextView(this);
        v.setTextSize(30);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.WHITE);
        c.addView(v);
        TextView e = new TextView(this);
        e.setText(etq);
        e.setTextColor(0xFF90A4AE);
        c.addView(e);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(4), dp(6), dp(4), dp(6));
        fila.addView(c, p);
        return v;
    }

    private void actualizarVivo() {
        String[] e = estado();
        if (e == null) { mostrar(); return; }
        double m = Double.parseDouble(e[2]);
        long ini = Long.parseLong(e[3]);
        tKm.setText(CalculadoraActivity.dec(m / 1000, 2) + " km");
        tTiempo.setText("⏱ " + Recorrido.duracion(System.currentTimeMillis() - ini));
        tVel.setText(Math.round(Double.parseDouble(e[4])) + " km/h");
        tMax.setText(Math.round(Double.parseDouble(e[5])) + " km/h");
        boolean fuera = "1".equals(e[6]);
        tFuera.setVisibility(fuera ? View.VISIBLE : View.GONE);
        bVolver.setVisibility(fuera ? View.VISIBLE : View.GONE);
        if (fuera) {
            String lat = e[7], lon = e[8];
            bVolver.setOnClickListener(v -> navegar(lat + "," + lon));
        }
        Recorrido r = Recorrido.leer(new File(carpeta, idVivo + ".txt"));
        if (r != null) mapa.poner(r, Double.parseDouble(e[9]), Double.parseDouble(e[10]));
    }

    private void preguntarBoya() {
        if (estado() == null) { aviso("Primero inicie un recorrido para marcar boyas"); return; }
        GridLayout g = new GridLayout(this);
        g.setColumnCount(2);
        g.setPadding(dp(12), dp(8), dp(12), 0);
        EditText nombre = campoTexto("Nombre o nota (opcional)", "");
        AlertDialog[] d = new AlertDialog[1];
        for (String tipo : Recorrido.TIPOS) {
            Button b = boton(Recorrido.emoji(tipo) + " " + tipo, 0xFF37474F);
            b.setOnClickListener(v -> {
                startService(new Intent(this, RecorridoService.class).setAction(RecorridoService.BOYA)
                        .putExtra("tipo", tipo).putExtra("nombre", nombre.getText().toString().trim()));
                aviso(Recorrido.emoji(tipo) + " Boya marcada");
                d[0].dismiss();
            });
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = dp(140);
            p.height = dp(54);
            p.setMargins(dp(4), dp(4), dp(4), dp(4));
            g.addView(b, p);
        }
        LinearLayout c = vertical();
        c.addView(g);
        c.addView(nombre);
        c.setPadding(dp(8), 0, dp(8), 0);
        d[0] = new AlertDialog.Builder(this).setTitle("📌 ¿Qué hay aquí?").setView(c)
                .setNegativeButton("Cancelar", null).show();
    }

    // ================= Versión 33: paradas con nombre =================

    /** Abre Google Maps con las paradas en orden (o al revés), de a 10 por tanda, y Mi Barra avisa "Aquí es el señor…". */
    private void irParando(String id, List<String[]> ps, boolean alReves) {
        List<String[]> orden = new ArrayList<>(ps);
        if (alReves) java.util.Collections.reverse(orden);
        if (estado() == null) iniciar(id);   // vigila y dice el nombre al llegar a cada casa
        if (orden.size() <= 10) { abrirMaps(Recorrido.enlaceParadas(orden)); return; }
        int tandas = (orden.size() + 9) / 10;
        String[] ops = new String[tandas];
        for (int t = 0; t < tandas; t++) {
            int a = t * 10, b = Math.min(orden.size(), a + 10);
            ops[t] = "Tanda " + (t + 1) + ": " + orden.get(a)[3] + " … " + orden.get(b - 1)[3];
        }
        new AlertDialog.Builder(this).setTitle("Son " + orden.size() + " paradas: Google Maps acepta 10 por vez")
                .setItems(ops, (d, w) -> {
                    int a = w * 10, b = Math.min(orden.size(), a + 10);
                    abrirMaps(Recorrido.enlaceParadas(orden.subList(a, b)));
                }).setNegativeButton("Cancelar", null).show();
    }

    // ================= Detalle =================

    private void mostrarDetalle(File f) {
        Recorrido r = Recorrido.leer(f);
        if (r == null) return;
        enDetalle = true;
        LinearLayout l = vertical();
        int p = dp(12);
        l.setPadding(p, p, p, p);
        TextView ti = titulo(r.nombre);
        ti.setTextSize(19);
        l.addView(ti);
        TextView res = new TextView(this);
        res.setTextSize(16);
        res.setText(CalculadoraActivity.dec(r.metros() / 1000, 2) + " km · " + Recorrido.duracion(r.duracionMs())
                + " · máx. " + Math.round(r.velMax()) + " km/h");
        l.addView(res);

        RutaView mv = new RutaView(this);
        mv.poner(r, 0, 0);
        l.addView(mv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        if (!r.boyas.isEmpty()) {
            StringBuilder s = new StringBuilder();
            for (String[] b : r.boyas)
                s.append(Recorrido.emoji(b[2])).append(' ').append(b[2]).append(b[3].isEmpty() ? "" : ": " + b[3])
                        .append(b.length > 4 && !b[4].isEmpty() ? "\n      📍 " + b[4] : "").append('\n');
            TextView tb = new TextView(this);
            tb.setText(s.toString().trim());
            tb.setTextSize(15);
            tb.setPadding(0, dp(6), 0, dp(6));
            l.addView(tb);
        }

        // Versión 33: ir o devolverse parando en cada señor (las boyas con nombre)
        List<String[]> ps = r.paradasConNombre();
        if (!ps.isEmpty()) {
            String id = f.getName().replace(".txt", "");
            accion(l, "▶  Ir parando en cada señor (" + ps.size() + ")", 0xFF6A1B9A, () -> irParando(id, ps, false));
            accion(l, "🔙  Devolverme parando del último al primero", 0xFF6A1B9A, () -> irParando(id, ps, true));
        }
        accion(l, "🔙  Devolverme por la misma ruta", 0xFF1565C0, () -> abrirMaps(r.enlaceMaps(true, true)));
        accion(l, "▶  Seguir este recorrido (aviso si me salgo)", 0xFF2E7D32,
                () -> iniciar(f.getName().replace(".txt", "")));
        accion(l, "🧭  Llevarme al punto de inicio", 0xFF546E7A,
                () -> navegar(r.puntos.get(0)[0] + "," + r.puntos.get(0)[1]));
        accion(l, "🗺  Ver en Google Maps", 0xFF546E7A, () -> abrirMaps(r.enlaceMaps(false, false)));
        accion(l, "💬  Compartir por WhatsApp (enlace)", 0xFF25A244, () -> {
            String txt = "🛣️ " + r.nombre + "\n" + CalculadoraActivity.dec(r.metros() / 1000, 1) + " km\n\n"
                    + "Ábralo en Google Maps:\n" + r.enlaceMaps(false, false);
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt);
            i.setPackage(WHATSAPP);
            try { startActivity(i); } catch (ActivityNotFoundException e) { i.setPackage(null); startActivity(Intent.createChooser(i, "Compartir")); }
        });
        accion(l, "📎  Compartir archivo completo (GPX, con boyas)", 0xFF25A244, () -> {
            try {
                String base = r.nombre.replaceAll("[^\\p{L}\\p{N}]+", "_");
                Uri u = Util.guardarBytes(this, base + ".gpx", "application/gpx+xml", r.gpx().getBytes(StandardCharsets.UTF_8));
                enviar(WHATSAPP, "application/gpx+xml", u);
            } catch (Exception e) {
                aviso("No se pudo crear el archivo: " + e.getMessage());
            }
        });
        accion(l, "✎  Cambiar nombre", 0xFF546E7A, () -> {
            EditText e = campoTexto("Nombre", r.nombre);
            new AlertDialog.Builder(this).setTitle("Nombre del recorrido").setView(e)
                    .setPositiveButton("Guardar", (d, w) -> {
                        r.nombre = e.getText().toString().trim().isEmpty() ? r.nombre : e.getText().toString().trim();
                        try { r.guardar(f); } catch (Exception ignored) {}
                        mostrarDetalle(f);
                    }).setNegativeButton("Cancelar", null).show();
        });
        accion(l, "🗑  Borrar", 0xFFC62828, () -> new AlertDialog.Builder(this)
                .setTitle("¿Borrar este recorrido?")
                .setPositiveButton("Sí, borrar", (d, w) -> { f.delete(); mostrar(); })
                .setNegativeButton("No", null).show());

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private void accion(LinearLayout l, String t, int color, Runnable r) {
        Button b = boton(t, color);
        b.setOnClickListener(v -> r.run());
        l.addView(b, ancho(54));
    }

    private void abrirMaps(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.setPackage("com.google.android.apps.maps");
        try { startActivity(i); } catch (ActivityNotFoundException e) { i.setPackage(null); startActivity(i); }
    }

    private void navegar(String latLon) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + latLon));
        i.setPackage("com.google.android.apps.maps");
        try { startActivity(i); }
        catch (ActivityNotFoundException e) { abrirMaps("https://www.google.com/maps/dir/?api=1&destination=" + latLon); }
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent d) {
        super.onActivityResult(pedido, resultado, d);
        if (pedido != IMPORTAR || resultado != RESULT_OK || d == null || d.getData() == null) return;
        try (InputStream in = getContentResolver().openInputStream(d.getData())) {
            String x = Util.leerTodo(in);
            Recorrido r = Recorrido.desdeGpx(x);
            if (r == null) { aviso("Ese archivo no tiene un recorrido"); return; }
            File f = new File(carpeta, System.currentTimeMillis() + ".txt");
            r.guardar(f);
            aviso("📥 Recorrido guardado");
            mostrarDetalle(f);
        } catch (Exception e) {
            aviso("No se pudo abrir: " + e.getMessage());
        }
    }

    // ================= Dibujo de la ruta =================

    static final class RutaView extends View {
        private Recorrido r;
        private double latAqui, lonAqui;
        private final Paint linea = new Paint(Paint.ANTI_ALIAS_FLAG), punto = new Paint(Paint.ANTI_ALIAS_FLAG),
                texto = new Paint(Paint.ANTI_ALIAS_FLAG);

        RutaView(Context c) {
            super(c);
            linea.setStyle(Paint.Style.STROKE);
            linea.setStrokeCap(Paint.Cap.ROUND);
            linea.setStrokeJoin(Paint.Join.ROUND);
            linea.setColor(0xFF2196F3);
            linea.setStrokeWidth(Util.dp(c, 5));
            texto.setTextSize(Util.dp(c, 18));
            texto.setTextAlign(Paint.Align.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFFE8EEF2);
            g.setCornerRadius(Util.dp(c, 14));
            setBackground(g);
        }

        void poner(Recorrido r, double lat, double lon) {
            this.r = r;
            latAqui = lat;
            lonAqui = lon;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (r == null || r.puntos.isEmpty()) {
                texto.setColor(0xFF607D8B);
                c.drawText("Esperando señal del GPS…", getWidth() / 2f, getHeight() / 2f, texto);
                return;
            }
            double minLa = 90, maxLa = -90, minLo = 180, maxLo = -180;
            for (double[] p : r.puntos) {
                minLa = Math.min(minLa, p[0]); maxLa = Math.max(maxLa, p[0]);
                minLo = Math.min(minLo, p[1]); maxLo = Math.max(maxLo, p[1]);
            }
            double k = Math.cos(Math.toRadians((minLa + maxLa) / 2));
            double w = Math.max((maxLo - minLo) * k, 1e-5), hh = Math.max(maxLa - minLa, 1e-5);
            float pad = Util.dp(getContext(), 26);
            double esc = Math.min((getWidth() - 2 * pad) / w, (getHeight() - 2 * pad) / hh);
            double ox = (getWidth() - w * esc) / 2, oy = (getHeight() - hh * esc) / 2;
            final double fMinLo = minLo, fMaxLa = maxLa;
            java.util.function.BiFunction<Double, Double, float[]> xy = (la, lo) -> new float[]{
                    (float) (ox + (lo - fMinLo) * k * esc), (float) (oy + (fMaxLa - la) * esc)};

            Path path = new Path();
            for (int i = 0; i < r.puntos.size(); i++) {
                float[] q = xy.apply(r.puntos.get(i)[0], r.puntos.get(i)[1]);
                if (i == 0) path.moveTo(q[0], q[1]); else path.lineTo(q[0], q[1]);
            }
            c.drawPath(path, linea);
            float rad = Util.dp(getContext(), 8);
            float[] a = xy.apply(r.puntos.get(0)[0], r.puntos.get(0)[1]);
            punto.setColor(0xFF2E7D32);
            c.drawCircle(a[0], a[1], rad, punto);
            float[] z = xy.apply(r.puntos.get(r.puntos.size() - 1)[0], r.puntos.get(r.puntos.size() - 1)[1]);
            punto.setColor(0xFFC62828);
            c.drawCircle(z[0], z[1], rad, punto);
            texto.setColor(Color.BLACK);
            for (String[] b : r.boyas) {
                float[] q = xy.apply(Double.parseDouble(b[0]), Double.parseDouble(b[1]));
                c.drawText(Recorrido.emoji(b[2]), q[0], q[1] - rad, texto);
            }
            if (latAqui != 0 || lonAqui != 0) {
                float[] q = xy.apply(latAqui, lonAqui);
                punto.setColor(0xFF1565C0);
                c.drawCircle(q[0], q[1], rad * 1.3f, punto);
                punto.setColor(Color.WHITE);
                c.drawCircle(q[0], q[1], rad * 0.6f, punto);
            }
            texto.setColor(0xFF455A64);
            texto.setTextSize(Util.dp(getContext(), 13));
            c.drawText("🟢 inicio   🔴 final   🔵 usted", getWidth() / 2f, getHeight() - Util.dp(getContext(), 8), texto);
            texto.setTextSize(Util.dp(getContext(), 18));
        }
    }
}
