package co.jose.mibarra;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Versión 17: base de 📻 Radio y 📺 TV gratis.
 * ⭐ Favoritas · 🔎 Buscar señales (prueba cuáles responden y usted marca las que quiere) · ➕ Agregar con enlace.
 */
abstract class CanalesBase extends Base {

    abstract boolean esTv();
    abstract String tituloPantalla();
    /** Carga la lista de dónde buscar (en un hilo aparte). Cada elemento: {nombre, enlace}. */
    abstract List<String[]> catalogo() throws Exception;
    /** Tocar un canal. */
    abstract void tocar(String nombre, String url);

    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout lista;
    private TextView estado;
    private volatile boolean buscando;
    private int pestana;

    File archivoFavoritas() { return new File(getFilesDir(), esTv() ? "tv_favoritas.tsv" : "radio_favoritas.tsv"); }
    List<String[]> favoritas() { return SonidoService.leer(archivoFavoritas()); }
    void guardarFavoritas(List<String[]> l) { SonidoService.escribir(archivoFavoritas(), l); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout raiz = vertical();
        raiz.setPadding(dp(10), dp(8), dp(10), dp(8));
        raiz.addView(titulo(tituloPantalla()));
        LinearLayout tabs = horizontal();
        Button f = boton("⭐ Favoritas", 0xFF1565C0), s = boton("🔎 Buscar señales", 0xFF546E7A);
        f.setOnClickListener(v -> { pestana = 0; pintar(); });
        s.setOnClickListener(v -> { pestana = 1; pintar(); });
        tabs.addView(f, peso(1));
        tabs.addView(s, peso(1));
        raiz.addView(tabs);
        estado = new TextView(this);
        estado.setTextSize(14);
        estado.setPadding(dp(4), dp(6), dp(4), dp(6));
        raiz.addView(estado);
        lista = vertical();
        ScrollView sv = new ScrollView(this);
        sv.addView(lista);
        raiz.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout abajo = horizontal();
        if (!esTv()) {
            Button parar = boton("⏹ Parar", 0xFFC62828);
            parar.setOnClickListener(v -> SonidoService.parar(this));
            abajo.addView(parar, peso(1));
            Button dormir = boton("⏲ Apagar en…", 0xFF455A64);
            dormir.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("⏲ Apagar la radio en")
                    .setItems(new String[]{"15 minutos", "30 minutos", "1 hora", "No apagar"}, (d, w) ->
                            SonidoService.dormir(this, new int[]{15, 30, 60, 0}[w])).show());
            abajo.addView(dormir, peso(1));
        }
        Button mas = boton("➕ Agregar", 0xFF2E7D32);
        mas.setOnClickListener(v -> agregarManual());
        abajo.addView(mas, peso(1));
        raiz.addView(abajo);
        setContentView(raiz);
        pintar();
    }

    @Override protected void onDestroy() { buscando = false; super.onDestroy(); }

    private void pintar() {
        lista.removeAllViews();
        if (pestana == 0) {
            List<String[]> fav = favoritas();
            estado.setText(fav.isEmpty() ? "Todavía no tiene favoritas. Toque 🔎 Buscar señales." :
                    "Toque para " + (esTv() ? "ver" : "escuchar") + ". Deje presionado para quitar o subir en la lista.");
            for (int i = 0; i < fav.size(); i++) {
                String[] c = fav.get(i);
                final int k = i;
                Button bt = boton((esTv() ? "📺  " : "📻  ") + c[0], i == 0 ? 0xFF1565C0 : 0xFF37474F);
                bt.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                bt.setOnClickListener(v -> tocar(c[0], c[1]));
                bt.setOnLongClickListener(v -> { opcionesFavorita(k); return true; });
                lista.addView(bt, ancho(56));
            }
        } else buscar();
    }

    private void opcionesFavorita(int k) {
        List<String[]> fav = favoritas();
        new AlertDialog.Builder(this).setTitle(fav.get(k)[0])
                .setItems(new String[]{"⬆ Ponerla de primera", "🗑 Quitar de favoritas"}, (d, w) -> {
                    String[] x = fav.remove(k);
                    if (w == 0) fav.add(0, x);
                    guardarFavoritas(fav);
                    pintar();
                }).show();
    }

    // ---------------- Buscar señales ----------------

    private void buscar() {
        if (buscando) return;
        buscando = true;
        estado.setText("🔎 Buscando la lista…");
        new Thread(() -> {
            List<String[]> cat;
            try { cat = catalogo(); } catch (Exception e) { cat = new ArrayList<>(); }
            final List<String[]> todos = cat;
            if (todos.isEmpty()) {
                h.post(() -> { estado.setText("No se pudo traer la lista. Revise el internet y vuelva a tocar 🔎."); buscando = false; });
                return;
            }
            final List<String[]> buenas = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger hechas = new AtomicInteger();
            ExecutorService ex = Executors.newFixedThreadPool(8);
            for (String[] c : todos) ex.submit(() -> {
                if (!buscando) return;
                boolean ok = responde(c[1]);
                int n = hechas.incrementAndGet();
                if (ok) { buenas.add(c); h.post(() -> agregarFila(c)); }
                h.post(() -> estado.setText("🔎 Revisando " + n + " de " + todos.size() + " · " + buenas.size() + " con buena señal"
                        + (n == todos.size() ? "\nMarque ☑ las que quiere en sus favoritas." : "")));
                if (n == todos.size()) buscando = false;
            });
            ex.shutdown();
        }).start();
    }

    private void agregarFila(String[] c) {
        if (pestana != 1) return;
        LinearLayout f = horizontal();
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(dp(6), dp(4), dp(6), dp(4));
        CheckBox cb = new CheckBox(this);
        boolean ya = false;
        for (String[] x : favoritas()) if (x[1].equals(c[1])) { ya = true; break; }
        cb.setChecked(ya);
        cb.setOnCheckedChangeListener((v, si) -> {
            List<String[]> fav = favoritas();
            fav.removeIf(x -> x[1].equals(c[1]));
            if (si) fav.add(c);
            guardarFavoritas(fav);
        });
        f.addView(cb);
        TextView t = new TextView(this);
        t.setText(c[0] + "\n● buena señal");
        t.setTextSize(15);
        t.setTextColor(0xFF263238);
        f.addView(t, peso(1));
        Button p = boton(esTv() ? "📺" : "▶", 0xFF1565C0);
        p.setOnClickListener(v -> tocar(c[0], c[1]));
        f.addView(p, new LinearLayout.LayoutParams(dp(56), dp(48)));
        lista.addView(f);
    }

    /** ¿El enlace contesta ahora? */
    static boolean responde(String url) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(4000);
            con.setReadTimeout(4000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) MiBarra");
            int c = con.getResponseCode();
            if (c < 200 || c >= 400) return false;
            con.getInputStream().read(new byte[512]);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private void agregarManual() {
        LinearLayout l = vertical();
        l.setPadding(dp(16), dp(8), dp(16), 0);
        EditText n = campoTexto(esTv() ? "Nombre del canal" : "Nombre de la emisora", "");
        EditText u = campoTexto("Enlace (https://…)", "");
        l.addView(n); l.addView(u);
        new AlertDialog.Builder(this).setTitle("➕ Agregar a favoritas").setView(l)
                .setPositiveButton("Agregar", (d, w) -> {
                    String nn = Base.limpio(n.getText().toString()), uu = u.getText().toString().trim();
                    if (nn.isEmpty() || !uu.startsWith("http")) { aviso("Escriba el nombre y un enlace que empiece por http"); return; }
                    List<String[]> fav = favoritas();
                    fav.add(new String[]{nn, uu});
                    guardarFavoritas(fav);
                    pestana = 0;
                    pintar();
                }).setNegativeButton("Cancelar", null).show();
    }

    void abrirEnlace(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { aviso("No se pudo abrir"); }
    }

    static int blanco() { return Color.WHITE; }
    static Typeface negrita() { return Typeface.DEFAULT_BOLD; }
}
