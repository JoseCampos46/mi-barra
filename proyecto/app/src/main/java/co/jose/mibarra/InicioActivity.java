package co.jose.mibarra;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Mi Barra Inicio: la pantalla principal del celular.
 * Página 1: reloj, batería, clima, recordatorios, almanaque, frase del día, buscador y favoritas.
 * Luego: las herramientas de Mi Barra y todas las apps en orden alfabético, 5 por fila.
 * Deslizando hacia abajo: el panel rápido. Dejando presionado un ícono: moverlo, quitarlo o desinstalarlo.
 */
public class InicioActivity extends Activity {

    // ---------------- Las apps y nuestras herramientas ----------------

    static final class Item {
        String clave, nombre, emoji;
        int color;
        Drawable icono;
        Runnable abrir;
        boolean nuestra;
    }

    private final List<Item> apps = new ArrayList<>(), herramientas = new ArrayList<>();
    private final Map<String, Item> porClave = new LinkedHashMap<>();
    private SharedPreferences prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Locale es = new Locale("es", "CO");

    // Vistas
    private FrameLayout raiz;
    private Fondo fondo;
    private HorizontalScrollView paginador;
    private LinearLayout paginas, puntos;
    private int pagina, anchoPantalla, altoPagina;
    private TextView tHora, tAmPm, tFecha, tBateria, tRecord, tFrase;
    private Estado estado;
    private View avisoBoton;
    private LinearLayout calendario;
    private View panel, busqueda;
    private boolean linterna;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void aviso(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        traerDeChpBar(true);
        prefs = getSharedPreferences("inicio", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(0xFF070A18);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        raiz = new FrameLayout(this);
        fondo = new Fondo(this);
        raiz.addView(fondo, new FrameLayout.LayoutParams(-1, -1));
        setContentView(raiz);
        armarHerramientas();
        raiz.post(this::cargarApps);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(cambioApps, f);
        registerReceiver(bateria, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (!esChpInicio()) Recordatorios.programar(this);   // Versión 46: las alarmas las pone solo ChpBar
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(cambioApps); unregisterReceiver(bateria); } catch (Exception ignored) {}
        super.onDestroy();
    }

    /** Versión 32: si la pantalla de inicio quedó vacía (sin apps), se vuelve a armar sola. */
    private int intentosVigia;
    private final Runnable vigiaInicio = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            if (paginas != null && paginas.getChildCount() > 0 && paginador != null && paginador.isAttachedToWindow()) { intentosVigia = 0; return; }
            if (intentosVigia++ > 6) return;
            esperandoTamano = false;
            forzarTamano = intentosVigia >= 2;
            if (apps.isEmpty()) cargarApps(); else armar();
            h.postDelayed(this, 1500);
        }
    };

    @Override protected void onResume() { super.onResume(); traerDeChpBar(false); intentosVigia = 0; h.removeCallbacks(vigiaInicio); h.postDelayed(vigiaInicio, 1200); pedirNumeroUnaVez(); h.post(reloj); h.post(senal); refrescarRecordatorios(); clima(); revisarBoton(); h.postDelayed(this::pedirUsoUnaVez, 1500); h.post(vigiaRojo); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(reloj); h.removeCallbacks(senal); }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        // Tocó el botón ● de inicio: volver a la página 1 y cerrar lo abierto
        cerrarCapas();
        irA(0, true);
    }

    @Override
    public void onBackPressed() {
        if (cerrarCapas()) return;
        if (pagina != 0) irA(0, true);
    }

    private boolean cerrarCapas() {
        boolean algo = false;
        if (panel != null) { raiz.removeView(panel); panel = null; algo = true; }
        if (busqueda != null) {
            View f = getCurrentFocus();
            if (f != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.getWindowToken(), 0);
            raiz.removeView(busqueda); busqueda = null; algo = true;
        }
        return algo;
    }

    private final BroadcastReceiver cambioApps = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { h.postDelayed(InicioActivity.this::cargarApps, 800); }
    };

    private int nivelBateria = -1;
    private boolean cargando;
    private final BroadcastReceiver bateria = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            nivelBateria = lv < 0 ? -1 : lv * 100 / sc;
            cargando = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            pintarBateria();
        }
    };

    private Item herr(String id, String emoji, String nombre, int color, Runnable r) {
        Item it = new Item();
        color = vivo(color, id);   // versión 18: colores fuertes para encontrarlas rápido
        it.clave = "mb:" + id; it.emoji = emoji; it.nombre = chp(nombre); it.color = color; it.abrir = r; it.nuestra = true;
        it.icono = new BitmapDrawable(getResources(), iconoEmoji(emoji, color));
        herramientas.add(it);
        return it;
    }

    /** Versión 45: todas las herramientas propias empiezan con «Chp» (ChpGrabadora, ChpLinterna…) para encontrarlas juntas. */
    static String chp(String n) {
        if (n.startsWith("Chp")) return n;
        if (n.startsWith("Mis ")) n = n.substring(4); else if (n.startsWith("Mi ")) n = n.substring(3);
        return "Chp" + Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Versión 47: el letrero debajo del ícono, corto para que se lea grande. El nombre completo sirve para buscar. */
    static String corto(String n) {
        if (n == null) return "";
        String r = n.trim();
        if (r.length() > 3 && r.regionMatches(true, 0, "Chp", 0, 3)) r = r.substring(3).trim();
        if (r.isEmpty()) return n;
        String[][] dic = {{"Recordatorios", "Record"}, {"Calculadora", "Calcu"}, {"Grabadora", "Grabar"}, {"Cuenta de cobro", "Cobro"},
                {"Enciclopedia", "Enciclo"}, {"Radio reloj", "Radio R"}, {"Reloj cocina", "Cocina"}, {"Mm ↔ pulgadas", "Pulgadas"},
                {"Grados techo", "Grados"}, {"Foto medidas", "Medidas"}, {"Tiempo pantalla", "Pantalla"}, {"Grabar pantalla", "Video P"},
                {"Escanear QR", "QR"}, {"Antes de salir", "Salir"}, {"Cerca de mí", "Cerca"}, {"Ajustes Inicio", "Ajustes"},
                {"Tabla de obra", "Tabla"}, {"Señal y velocidad", "Señal"}, {"Cerrar apps", "Cerrar"}, {"Buscar precios", "Precios"},
                {"Imprimir bajando", "Imprimir B"}, {"Imprimir pantalla", "Imprimir"}, {"Botón flotante", "Botón"}, {"Escribir bien", "Escribir"},
                {"Gastos hormiga", "Gastos"}, {"Proyectar pantalla", "Proyectar"}, {"Ojo de puerta", "Ojo"}, {"Lista de compras", "Compras"}};
        for (String[] d : dic) if (d[0].equalsIgnoreCase(r)) return d[1];
        if (r.length() > 10) r = r.substring(0, 9).trim() + ".";
        return r;
    }

    private static final int[] VIVOS = {0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1,
            0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFF4511E, 0xFFFB8C00, 0xFFFFB300};

    /** Sube el color a uno fuerte y brillante; a los grises les da un color propio fijo. */
    static int vivo(int c, String id) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        if (hsv[1] < 0.3f) return VIVOS[Math.abs(id.hashCode()) % VIVOS.length];
        hsv[1] = Math.max(hsv[1], 0.9f);
        hsv[2] = Math.max(hsv[2], 0.95f);
        return Color.HSVToColor(hsv);
    }

    /** Versión 41: las herramientas de Mi Barra se abren en su propia tarea. Antes quedaban «encima» del
     *  launcher y, al tocar Inicio, el launcher las cerraba (por eso se cerraban solas al minimizar). */
    @Override
    public void startActivity(Intent i, Bundle opciones) {
        if (i != null && i.getComponent() != null && getPackageName().equals(i.getComponent().getPackageName()))
            // Versión 43: cada herramienta en su propia tarea (como una app de fuera). Si ya estaba abierta,
            // vuelve la MISMA, tal como la dejó; solo se cierra con la ✕.
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        // Versión 46: en ChpInicio, las herramientas se abren en ChpBar (otra app)
        Intent p = Puerta.desviar(this, i, "actividad");
        if (p != null) { abrirPuerta(p); return; }
        super.startActivity(i, opciones);
    }

    @Override
    public android.content.ComponentName startService(Intent s) {
        Intent p = Puerta.desviar(this, s, "servicio");
        if (p != null) { abrirPuerta(p); return null; }
        return super.startService(s);
    }

    @Override
    public android.content.ComponentName startForegroundService(Intent s) {
        Intent p = Puerta.desviar(this, s, "servicio_fg");
        if (p != null) { abrirPuerta(p); return null; }
        return super.startForegroundService(s);
    }

    private void abrirPuerta(Intent p) {
        try { super.startActivity(p, null); }
        catch (Exception e) { aviso("Instale o actualice ChpBar: ahí están las herramientas"); }
    }

    private boolean esChpInicio() { return Puerta.esChpInicio(this); }

    /** Versión 46: ChpInicio trae de ChpBar los recordatorios (y la primera vez, su orden de apps y su fondo). */
    private void traerDeChpBar(boolean primeraVez) {
        if (!esChpInicio()) return;
        if (primeraVez) {
            java.io.File ajustes = new java.io.File(new java.io.File(getApplicationInfo().dataDir, "shared_prefs"), "inicio.xml");
            java.io.File marca = new java.io.File(getFilesDir(), "migrado46");
            if (!marca.exists()) {
                if (!ajustes.exists()) Puerta.traer(this, "inicio.xml", ajustes);
                java.io.File fondoF = new java.io.File(getFilesDir(), "fondo.jpg");
                if (!fondoF.exists()) Puerta.traer(this, "fondo.jpg", fondoF);
                try { marca.createNewFile(); } catch (Exception ignored) {}
            }
        }
        if (Puerta.hayChpRecordatorios(this)) Puerta.traer(this, Puerta.AUTORIDAD_RECORD, "recordatorios.tsv", Recordatorios.archivo(this));
        else Puerta.traer(this, "recordatorios.tsv", Recordatorios.archivo(this));
    }

    /** Versión 46: en ChpInicio la grabadora vive en ChpBar, así que se le pregunta. */
    private boolean grabando() { return esChpInicio() ? Puerta.pregunta(this, "grabando") : GrabadoraService.grabando; }

    private Runnable abrirAct(Class<?> c) { return () -> startActivity(new Intent(this, c)); }
    private Runnable abrirAct(Class<?> c, String extra, int v) { return () -> startActivity(new Intent(this, c).putExtra(extra, v)); }

    private void armarHerramientas() {
        herramientas.clear();
        herr("cuaderno", "📝", "Cuaderno", 0xFF1565C0, abrirAct(NotasActivity.class));
        herr("cobro", "🧾", "Cuenta de cobro", 0xFF6D4C41, abrirAct(CobroActivity.class));
        herr("compras", "🛒", "Compras", 0xFF00838F, abrirAct(ComprasActivity.class));
        herr("proveedores", "📇", "Proveedores", 0xFF455A64, abrirAct(ProveedoresActivity.class));
        herr("escaner", "📄", "Escáner", 0xFF8E24AA, abrirAct(EscanerActivity.class));
        herr("pdf", "📑", "PDF", 0xFFC62828, abrirAct(MainActivity.class));
        herr("calculadora", "🧮", "Calculadora", 0xFF2E7D32, abrirAct(CalculadoraActivity.class, "pestana", 0));
        herr("pulgadas", "📏", "mm ↔ pulgadas", 0xFF3949AB, abrirAct(CalculadoraActivity.class, "pestana", 1));
        herr("concreto", "🏗️", "Concreto", 0xFF607D8B, abrirAct(CalculadoraActivity.class, "pestana", 2));
        herr("ladrillo", "🧱", "Ladrillo", 0xFF795548, abrirAct(CalculadoraActivity.class, "pestana", 3));
        herr("nivel", "📐", "Nivel", 0xFF6A1B9A, abrirAct(NivelActivity.class, "modo", 0));
        herr("grados", "📐", "Grados techo", 0xFF4527A0, abrirAct(NivelActivity.class, "modo", 3));
        herr("plomada", "🎯", "Plomada", 0xFF283593, abrirAct(NivelActivity.class, "modo", 2));
        herr("medidas", "📸", "Foto medidas", 0xFFEF6C00, abrirAct(MedidasActivity.class));
        // Versión 47: Recordatorios salió como app aparte (ChpRecordatorios); si no está instalada, el botón lleva a bajarla
        if (!Puerta.hayChpRecordatorios(this)) herr("recordatorios", "🔔", "Recordatorios", 0xFFEF6C00, () -> Puerta.abrirChpRecordatorios(this));
        herr("reloj", "⏱", "Reloj cocina", 0xFFD84315, abrirAct(RelojActivity.class));
        herr("radioreloj", "⏰", "Radio reloj", 0xFFD50000, abrirAct(RadioRelojActivity.class));
        // Versión 19: apagar la pantalla de un toque y cambiar cuánto dura prendida
        Item apg = herr("apagar", "⏻", "Apagar", 0xFFE53958, () -> accionBarra("bloquear"));
        apg.icono = new BitmapDrawable(getResources(), iconoPoder()); // Versión 22: el símbolo dibujado (la letra ⏻ no existe en todos los celulares)
        herr("tiempoPantalla", "⏳", "Tiempo pantalla", 0xFF5D4037, this::tiempoPantalla);
        herr("saludos", "💌", "Saludos", 0xFFAD1457, abrirAct(SaludosActivity.class));
        herr("enciclopedia", "📚", "Enciclopedia", 0xFF00695C, abrirAct(EnciclopediaActivity.class));
        herr("camara", "📷", "Cámara", 0xFF263238, abrirAct(CamaraActivity.class));
        herr("minicamara", "👁", "Ojo de puerta", 0xFFE53935, abrirAct(MiniCamaraActivity.class));   // Versión 35: antes «Mini cámara»
        herr("grabadora", "🎵", "Grabadora", 0xFF37474F, abrirAct(GrabadoraActivity.class));
        herr("grabarpantalla", "🎥", "Grabar pantalla", 0xFFC62828, abrirAct(GrabarActivity.class));
        herr("codigos", "🔳", "Escanear QR", 0xFF212121, abrirAct(CodigoActivity.class));
        herr("tv", "📺", "Control TV", 0xFF1A237E, abrirAct(ControlTvActivity.class));
        herr("antesdesalir", "🏍", "Antes de salir", 0xFFF57F17, abrirAct(ChequeoActivity.class));
        // Versión 35: Reparto y Recorridos quedan dentro de Rutas; Aquí estoy dentro de Mis lugares
        herr("ruta", "🗺", "Rutas", 0xFF00B8D4, () -> startActivity(new Intent(this, ParadasActivity.class).putExtra("modo", "viaje")));   // Versión 34: antes "GPS"
        herr("gps", "🧭", "GPS", 0xFF00897B, abrirAct(GpsActivity.class));   // Versión 34: el GPS nuevo
        // Versión 35: gasolinera, hospital, taller, cajero, droguería, ferretería y horarios en un solo botón
        herr("cerca", "📍", "Cerca de mí", 0xFFC62828, this::cercaDeMi);
        herr("teclado", "⌨", "Teclado", 0xFF37474F, abrirAct(TecladoAjustesActivity.class));
        herr("ajustes", "⚙", "Ajustes Inicio", 0xFF455A64, this::ajustes);
        // Versión 17
        herr("tabla", "📋", "Tabla de obra", 0xFF00695C, abrirAct(TablaActivity.class));
        herr("lugares", "📍", "Mis lugares", 0xFFD84315, abrirAct(LugaresActivity.class));
        herr("bluetooth", "📲", "Bluetooth", 0xFF1E88E5, abrirAct(BluetoothActivity.class));   // Versión 33
        herr("senal", "📶", "Señal y velocidad", 0xFF1565C0, abrirAct(SenalActivity.class));
        herr("wifi", "🛜", "Wi‑Fi", 0xFF0277BD, abrirAct(WifiActivity.class));
        herr("radio", "📻", "Radio", 0xFFAD1457, abrirAct(RadioActivity.class));
        herr("tvgratis", "📺", "TV gratis", 0xFF4527A0, abrirAct(TvActivity.class));
        herr("audios", "🎧", "Reproductor", 0xFF6A1B9A, abrirAct(ReproductorActivity.class));
        herr("cerrarapps", "🧹", "Cerrar apps", 0xFF546E7A, () -> accionBarra("cerrarApps"));
        // Versión 18
        herr("precios", "💲", "Buscar precios", 0xFF2E7D32, abrirAct(PreciosActivity.class));
        herr("transporte", "🚕", "Transporte", 0xFFFFB300, abrirAct(TransporteActivity.class));
        herr("imprimirlargo", "📜", "Imprimir bajando", 0xFF1E88E5, () -> accionBarra("imprimirLargo"));
        herr("imprimir", "🖨", "Imprimir pantalla", 0xFF3949AB, () -> accionBarra("imprimir"));
        herr("botonflotante", "🟠", "Botón flotante", 0xFFFB8C00, this::reactivarBoton);
        // Versión 22
        herr("linterna", "🔦", "Linterna", 0xFFFBC02D, this::linterna);
        herr("juegos", "🎮", "Play Doly", 0xFF7B1FA2, this::abrirPlayDoly);
        // Versión 27
        herr("escribir", "✍️", "Escribir bien", 0xFF1565C0, abrirAct(EscribirActivity.class));
        // Versión 25
        // Versión 35: un solo botón para bloquear (WhatsApp o llamadas)
        herr("bloqwa", "🚫", "Bloquear", 0xFFC62828, this::bloquear);
        herr("gastos", "🐜", "Gastos hormiga", 0xFFEF6C00, abrirAct(GastosActivity.class));
        herr("proyectar", "📡", "Proyectar pantalla", 0xFF0277BD, () -> proyectar(this));
        // Versión 36
        herr("correos", "📧", "Mis Correos", 0xFFC62828, abrirAct(CorreosActivity.class));
        herr("telefono", "📞", "Mi Teléfono", 0xFF2E7D32, () -> abrirMiTelefono(this));
        // Versión 44: botón Claude (cuaderno tipo correo → paquete para el chat del proyecto)
        herr("claude", "✉", "Carta Claude", 0xFFC15F3C, abrirAct(ClaudeActivity.class));   // versión 47: para no confundirla con la app Claude
    }

    /** Versión 37: Mi Teléfono vive dentro de Mi Barra (marcador, contestador y grabación). Mis Chats queda solo para WhatsApp. */
    static void abrirMiTelefono(android.content.Context c) {
        Intent i = new Intent(c, co.jose.mibarra.tel.MarcadorActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** Versión 27: los juegos se pasaron a la app Play Doly. */
    private void abrirPlayDoly() {
        Intent i = getPackageManager().getLaunchIntentForPackage("co.jose.playdoly");
        if (i != null) { startActivity(i); return; }
        aviso("Instale Play Doly: ahí están ahora los juegos");
        abrirUrl("https://github.com/JoseCampos46/mi-barra/releases");
    }

    /** Versión 35: un solo botón «Cerca de mí» con todo adentro. */
    private void cercaDeMi() {
        String[] n = {"⛽  Gasolinera", "🏥  Hospital", "🔧  Taller de motos", "🏧  Cajero", "💊  Droguería", "🔩  Ferretería", "🏪  Horarios de tiendas"};
        String[] q = {"gasolinera", "hospital", "taller de motos", "cajero automático", "droguería", "ferretería", ""};
        new AlertDialog.Builder(this).setTitle("📍 Cerca de mí").setItems(n, (d, w) -> {
            if (q[w].isEmpty()) startActivity(new Intent(this, GastosActivity.class).putExtra("tiendas", true));
            else cerca(q[w]);
        }).setNegativeButton("Cerrar", null).show();
    }

    /** Versión 35: un solo botón «Bloquear»: WhatsApp o llamadas. */
    private void bloquear() {
        // Versión 37: los números de estafa se bloquean en Mi Teléfono (⚙)
        new AlertDialog.Builder(this).setTitle("🚫 Bloquear").setItems(new String[]{"🚫  Bloquear WhatsApp", "📵  Callar todas las llamadas por un rato", "📞  Números de estafa (Mi Teléfono)"},
                (d, w) -> {
                    if (w == 2) startActivity(new Intent(this, co.jose.mibarra.tel.MarcadorActivity.class).putExtra("pestana", 4));
                    else startActivity(new Intent(this, BloqueosActivity.class).putExtra("tipo", w == 0 ? "wa" : "llamadas"));
                })
                .setNegativeButton("Cerrar", null).show();
    }

    private void cerca(String que) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(que)));
        try { startActivity(i); } catch (Exception e) { abrirUrl("https://www.google.com/maps/search/" + Uri.encode(que + " cerca de mí")); }
    }

    private void abrirUrl(String u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception e) { aviso("No se pudo abrir"); }
    }

    /** Ícono propio: la figurita sobre un cuadro redondeado de color, con brillo suave. */
    private Bitmap iconoEmoji(String emoji, int color) {
        int s = dp(56);
        Bitmap bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Versión 29: colores fuertes y brillantes, con borde claro (nada opaco)
        p.setShader(new LinearGradient(0, 0, s, s, aclarar(color, 0.30f), color, Shader.TileMode.CLAMP));
        c.drawRoundRect(0, 0, s, s, s * 0.27f, s * 0.27f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.045f);
        p.setColor(0x99FFFFFF);
        float m = s * 0.0225f;
        c.drawRoundRect(m, m, s - m, s - m, s * 0.25f, s * 0.25f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setTextSize(s * 0.56f);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(emoji, s / 2f, s / 2f - (fm.ascent + fm.descent) / 2f, p);
        return bm;
    }

    /** Botón de apagar: cuadro rojo-naranja con el símbolo de encendido dibujado a mano. */
    private Bitmap iconoPoder() {
        int s = dp(56);
        Bitmap bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, s, s, 0xFFFF7043, 0xFFE53958, Shader.TileMode.CLAMP));
        c.drawRoundRect(0, 0, s, s, s * 0.27f, s * 0.27f, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.085f);
        p.setStrokeCap(Paint.Cap.ROUND);
        float r = s * 0.26f, cx = s / 2f, cy = s * 0.54f;
        c.drawArc(cx - r, cy - r, cx + r, cy + r, -55, 290, false, p);
        c.drawLine(cx, s * 0.2f, cx, s * 0.5f, p);
        return bm;
    }

    static int aclarar(int c, float f) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        r += (255 - r) * f; g += (255 - g) * f; b += (255 - b) * f;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ---------------- Versión 46: apps gemelas separadas + íconos guardados para que salgan al instante ----------------

    private boolean cacheMostrado;

    private void cargarApps() {
        // 1) Lo guardado de la vez pasada sale de una vez (sin esperar a leer todo el celular)
        if (!cacheMostrado && apps.isEmpty()) {
            cacheMostrado = true;
            new Thread(() -> {
                List<Item> c = leerCache();
                if (c.isEmpty()) return;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !apps.isEmpty()) return;
                    ponerApps(c);
                });
            }).start();
        }
        // 2) Luego se lee el celular de verdad (y se arma de nuevo solo si algo cambió)
        new Thread(() -> {
            List<Item> r = leerAppsDelCelular();
            r.sort((x, y) -> SaludosActivity.sinTildes(x.nombre).compareTo(SaludosActivity.sinTildes(y.nombre)));
            guardarCache(r);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (mismaLista(apps, r)) { for (int i = 0; i < r.size(); i++) apps.get(i).abrir = r.get(i).abrir; return; }
                ponerApps(r);
            });
        }).start();
    }

    private void ponerApps(List<Item> r) {
        apps.clear(); apps.addAll(r);
        porClave.clear();
        for (Item i : herramientas) porClave.put(i.clave, i);
        for (Item i : apps) porClave.put(i.clave, i);
        armar();
    }

    private static boolean mismaLista(List<Item> a, List<Item> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (!a.get(i).clave.equals(b.get(i).clave) || !a.get(i).nombre.equals(b.get(i).nombre)) return false;
        return true;
    }

    /** Lee las apps de todos los perfiles: así salen las gemelas del Xiaomi (WhatsApp, Facebook, Messenger…) cada una con su ícono. */
    private List<Item> leerAppsDelCelular() {
        List<Item> r = new ArrayList<>();
        PackageManager pm = getPackageManager();
        try {
            android.content.pm.LauncherApps la = getSystemService(android.content.pm.LauncherApps.class);
            android.os.UserManager um = getSystemService(android.os.UserManager.class);
            android.os.UserHandle yo = android.os.Process.myUserHandle();
            List<android.os.UserHandle> perfiles = la.getProfiles();
            if (perfiles == null || perfiles.isEmpty()) perfiles = java.util.Collections.singletonList(yo);
            for (android.os.UserHandle u : perfiles) {
                boolean gemela = !u.equals(yo);
                long serie = 0;
                try { serie = um.getSerialNumberForUser(u); } catch (Exception ignored) {}
                List<android.content.pm.LauncherActivityInfo> lista;
                try { lista = la.getActivityList(null, u); } catch (Exception e) { continue; }
                for (android.content.pm.LauncherActivityInfo ai : lista) {
                    try {
                        android.content.ComponentName cn = ai.getComponentName();
                        if (getPackageName().equals(cn.getPackageName())) continue;
                        if (esCompanera(cn.getPackageName())) continue;   // ChpBar ya está como herramientas
                        Item a = new Item();
                        String nom = String.valueOf(ai.getLabel()).trim();
                        a.nombre = gemela ? nom + " 2" : nom;
                        a.clave = cn.getPackageName() + "/" + cn.getClassName() + (gemela ? "@" + serie : "");
                        a.abrir = abridor(cn, gemela ? u : null);
                        try { a.icono = gemela ? ai.getBadgedIcon(0) : ai.getIcon(0); } catch (Exception ignored) {}
                        r.add(a);
                    } catch (Exception ignored) { /* una app dañada no deja la pantalla vacía */ }
                }
            }
        } catch (Exception e) { r.clear(); }
        if (!r.isEmpty()) return r;
        // Plan B: la forma de antes (solo el perfil principal)
        List<ResolveInfo> rs = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        for (ResolveInfo ri : rs) {
            try {
                if (getPackageName().equals(ri.activityInfo.packageName) || esCompanera(ri.activityInfo.packageName)) continue;
                Item a = new Item();
                a.nombre = String.valueOf(ri.loadLabel(pm)).trim();
                a.clave = ri.activityInfo.packageName + "/" + ri.activityInfo.name;
                a.abrir = abridor(new android.content.ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), null);
                try { a.icono = ri.loadIcon(pm); } catch (Exception ignored) {}
                r.add(a);
            } catch (Exception ignored) {}
        }
        return r;
    }

    /** En ChpInicio, la app ChpBar no sale como ícono suelto: sus herramientas ya están en la pantalla. */
    private boolean esCompanera(String paquete) {
        return esChpInicio() && Puerta.CHPBAR.equals(paquete);
    }

    private Runnable abridor(android.content.ComponentName cn, android.os.UserHandle gemela) {
        return () -> {
            try {
                if (gemela != null) {
                    getSystemService(android.content.pm.LauncherApps.class).startMainActivity(cn, gemela, null, null);
                } else {
                    // "Ya escogí cuál": el Xiaomi no vuelve a preguntar entre la app normal y la gemela
                    startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
                            .putExtra("android.intent.extra.xspace_userid_selected", true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                }
            } catch (Exception e) { aviso("No se pudo abrir"); }
        };
    }

    private java.io.File carpetaCache() { return new java.io.File(getCacheDir(), "apps46"); }

    private void guardarCache(List<Item> r) {
        try {
            java.io.File d = carpetaCache();
            d.mkdirs();
            StringBuilder sb = new StringBuilder();
            int tam = dp(56);
            for (int i = 0; i < r.size(); i++) {
                Item a = r.get(i);
                String nombreIcono = "i" + Integer.toHexString(a.clave.hashCode()) + ".png";
                sb.append(a.clave.replace('\t', ' ')).append('\t').append(a.nombre.replace('\t', ' ').replace('\n', ' ')).append('\t').append(nombreIcono).append('\n');
                java.io.File f = new java.io.File(d, nombreIcono);
                if (a.icono != null && !f.exists()) {
                    Bitmap b = Bitmap.createBitmap(tam, tam, Bitmap.Config.ARGB_8888);
                    Canvas cv = new Canvas(b);
                    a.icono.setBounds(0, 0, tam, tam);
                    a.icono.draw(cv);
                    try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
                    b.recycle();
                }
            }
            java.nio.file.Files.write(new java.io.File(d, "lista.tsv").toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }

    private List<Item> leerCache() {
        List<Item> r = new ArrayList<>();
        try {
            java.io.File d = carpetaCache();
            java.io.File l = new java.io.File(d, "lista.tsv");
            if (!l.exists()) return r;
            for (String linea : java.nio.file.Files.readAllLines(l.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                String[] c = linea.split("\t");
                if (c.length < 3) continue;
                Item a = new Item();
                a.clave = c[0];
                a.nombre = c[1];
                Bitmap b = android.graphics.BitmapFactory.decodeFile(new java.io.File(d, c[2]).getPath());
                if (b != null) a.icono = new BitmapDrawable(getResources(), b);
                a.abrir = abrirDesdeClave(a.clave);
                r.add(a);
            }
        } catch (Throwable e) { r.clear(); }
        return r;
    }

    /** Abre una app guardada solo con su clave "paquete/clase" o "paquete/clase@serie" (gemela). */
    private Runnable abrirDesdeClave(String clave) {
        String c = clave;
        long serie = -1;
        int arroba = c.lastIndexOf('@');
        if (arroba > 0) { try { serie = Long.parseLong(c.substring(arroba + 1)); } catch (Exception ignored) {} c = c.substring(0, arroba); }
        int barra = c.indexOf('/');
        android.content.ComponentName cn = new android.content.ComponentName(c.substring(0, barra), c.substring(barra + 1));
        android.os.UserHandle u = null;
        if (serie >= 0) try { u = getSystemService(android.os.UserManager.class).getUserForSerialNumber(serie); } catch (Exception ignored) {}
        return abridor(cn, u);
    }

    // ---------------- Uso, favoritas y quitadas ----------------

    private Set<String> quitadas() { return new HashSet<>(prefs.getStringSet("quitadas", new HashSet<>())); }

    private List<String> favoritas() {
        String s = prefs.getString("favoritas", null);
        List<String> r = new ArrayList<>();
        if (s == null) {
            // Para empezar: lo más común
            for (String q : new String[]{"com.whatsapp", "com.android.chrome", "org.mozilla.firefox", "com.google.android.dialer",
                    "com.android.contacts", "com.google.android.apps.maps", "com.google.android.youtube"})
                for (Item a : apps) if (a.clave.startsWith(q + "/") && !r.contains(a.clave)) { r.add(a.clave); break; }
            for (String q : new String[]{"mb:cuaderno", "mb:calculadora", "mb:recordatorios", "mb:camara", "mb:grabadora"}) r.add(q);
            return r;
        }
        for (String x : s.split("\\|")) if (!x.isEmpty() && porClave.containsKey(x)) r.add(x);
        return r;
    }

    private void guardarFavoritas(List<String> f) { prefs.edit().putString("favoritas", String.join("|", f)).apply(); }

    private void usar(Item it) {
        prefs.edit().putInt("u_" + it.clave, prefs.getInt("u_" + it.clave, 0) + 1).apply();
        anotarUso(it.clave, 1);
        cerrarCapas();
        try { it.abrir.run(); } catch (Exception e) { aviso("No se pudo abrir " + it.nombre); }
    }

    // ---- Versión 28: uso de los últimos 30 días (las más usadas primero, luego de la A a la Z) ----

    private static final long MES_MIN = 30L * 24 * 60;

    private long[] usos(String clave) {
        String s = prefs.getString("h_" + clave, "");
        if (s.isEmpty()) return new long[0];
        String[] p = s.split(",");
        long[] r = new long[p.length];
        int n = 0;
        for (String x : p) try { r[n++] = Long.parseLong(x); } catch (Exception ignored) {}
        return Arrays.copyOf(r, n);
    }

    private void anotarUso(String clave, int veces) {
        long ahora = System.currentTimeMillis() / 60000;
        StringBuilder b = new StringBuilder();
        int n = 0;
        long[] us = usos(clave);
        for (int i = Math.max(0, us.length - 120); i < us.length; i++)
            if (ahora - us[i] < MES_MIN) { b.append(n++ > 0 ? "," : "").append(us[i]); }
        for (int i = 0; i < veces; i++) b.append(n++ > 0 ? "," : "").append(ahora);
        prefs.edit().putString("h_" + clave, b.toString()).apply();
    }

    private int usosMes(String clave) {
        long ahora = System.currentTimeMillis() / 60000;
        int n = 0;
        for (long u : usos(clave)) if (ahora - u < MES_MIN) n++;
        return n;
    }

    private long ultimoUso(String clave) {
        long m = 0;
        for (long u : usos(clave)) m = Math.max(m, u);
        return m;
    }

    /** La primera vez en la versión 28: lo que ya usaba cuenta como usado hoy (hasta 10 veces). */
    private void migrarUsos() {
        if (prefs.getBoolean("migro28", false)) return;
        SharedPreferences.Editor e = prefs.edit();
        long ahora = System.currentTimeMillis() / 60000;
        for (Map.Entry<String, ?> en : prefs.getAll().entrySet()) {
            if (!en.getKey().startsWith("u_") || !(en.getValue() instanceof Integer)) continue;
            int v = Math.min(10, (Integer) en.getValue());
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < v; i++) b.append(i > 0 ? "," : "").append(ahora - i);
            if (v > 0) e.putString("h_" + en.getKey().substring(2), b.toString());
        }
        e.putBoolean("migro28", true).apply();
    }

    /** Versión 29: minutos de uso real de cada app en 30 días (aunque la abra desde otra parte), si dio el permiso. */
    private Map<String, Long> minutosApps() {
        Map<String, Long> m = new HashMap<>();
        try {
            android.app.AppOpsManager ao = (android.app.AppOpsManager) getSystemService(APP_OPS_SERVICE);
            if (ao.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName())
                    != android.app.AppOpsManager.MODE_ALLOWED) return m;
            android.app.usage.UsageStatsManager um = (android.app.usage.UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            long fin = System.currentTimeMillis();
            Map<String, android.app.usage.UsageStats> st = um.queryAndAggregateUsageStats(fin - 30L * 24 * 3600 * 1000, fin);
            for (Map.Entry<String, android.app.usage.UsageStats> e : st.entrySet())
                m.put(e.getKey(), e.getValue().getTotalTimeInForeground() / 60000);
        } catch (Throwable ignored) {}
        return m;
    }

    private boolean tieneUso() {
        try {
            android.app.AppOpsManager ao = (android.app.AppOpsManager) getSystemService(APP_OPS_SERVICE);
            return ao.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName())
                    == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) { return false; }
    }

    /** Una sola vez: pedir «Acceso al uso» para saber de verdad cuáles apps usa más. */
    private void pedirUsoUnaVez() {
        if (tieneUso() || prefs.getBoolean("pidioUso29", false)) return;
        prefs.edit().putBoolean("pidioUso29", true).apply();
        new AlertDialog.Builder(this).setTitle("⭐ Las que más usa, primero")
                .setMessage("Para que las apps que más usa salgan en la primera página (aunque las abra desde otra parte), "
                        + "busque «Mi Barra» en la lista que se abre y active «Permitir acceso al uso».")
                .setPositiveButton("Abrir", (d, w) -> ajuste(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                .setNegativeButton("Ahora no", null).show();
    }

    /** Puestos que él escogió a mano (Mover a otro puesto): clave → puesto (1, 2, 3…). */
    private Map<String, Integer> puestos() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String x : prefs.getString("puestos29", "").split("\\|")) {
            int k = x.lastIndexOf('=');
            if (k > 0) try { m.put(x.substring(0, k), Integer.parseInt(x.substring(k + 1))); } catch (Exception ignored) {}
        }
        return m;
    }

    private void guardarPuestos(Map<String, Integer> m) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) b.append(b.length() > 0 ? "|" : "").append(e.getKey()).append('=').append(e.getValue());
        prefs.edit().putString("puestos29", b.toString()).apply();
    }

    /** Versión 29: primero las que más usa (las abra desde aquí o desde otra parte), luego de la A a la Z.
     *  Las que él movió a mano quedan en el puesto que escogió. */
    private List<Item> ordenTodo() {
        migrarUsos();
        Set<String> q = quitadas();
        Map<String, Long> min = minutosApps();
        List<Item> mucho = new ArrayList<>(), resto = new ArrayList<>();
        Map<String, Long> n = new HashMap<>();
        Map<String, Long> ult = new HashMap<>();
        List<Item> todos = new ArrayList<>(herramientas);
        todos.addAll(apps);
        Set<String> vistas = new HashSet<>();
        for (Item i : todos) {
            if (q.contains(i.clave) || !vistas.add(i.clave)) continue;
            // Versión 41: cuentan los TOQUES. Antes se sumaban los minutos de uso de las otras apps
            // (WhatsApp tiene cientos) y las herramientas de Mi Barra nunca alcanzaban a subir.
            // Con 3 toques en el mes sube a la primera página; los minutos solo ayudan hasta 3
            // (para las apps que él abre desde las notificaciones y no desde aquí).
            long u = usosMes(i.clave);
            // Versión 47: los minutos de uso real también cuentan (cada 10 minutos = 1 toque), así la que más usa
            // (ej. Claude, aunque la abra desde Recientes) queda de primera, la segunda de segunda, y así.
            if (!i.nuestra && !i.clave.contains("@")) { Long mm = min.get(i.clave.split("/")[0]); if (mm != null) u += mm / 10; }
            n.put(i.clave, u);
            ult.put(i.clave, ultimoUso(i.clave));
            (u >= 3 ? mucho : resto).add(i);
        }
        // Versión 47: la que más usa de primera, la segunda de segunda… (si empatan, la que usó de última)
        mucho.sort((a, b) -> {
            int c = Long.compare(n.get(b.clave), n.get(a.clave));
            return c != 0 ? c : Long.compare(ult.get(b.clave), ult.get(a.clave));
        });
        resto.sort((a, b) -> SaludosActivity.sinTildes(a.nombre).compareTo(SaludosActivity.sinTildes(b.nombre)));
        mucho.addAll(resto);
        // Las movidas a mano: se sacan y se ponen en su puesto
        Map<String, Integer> ps = puestos();
        if (!ps.isEmpty()) {
            List<Item> fijas = new ArrayList<>();
            for (Item i : mucho) if (ps.containsKey(i.clave)) fijas.add(i);
            mucho.removeAll(fijas);
            fijas.sort((a, b) -> ps.get(a.clave) - ps.get(b.clave));
            for (Item i : fijas) mucho.add(Math.max(0, Math.min(mucho.size(), ps.get(i.clave) - 1)), i);
        }
        return mucho;
    }

    /** Mover a otro puesto: escoge el número del puesto (1 = arriba a la izquierda). */
    private void moverPuesto(Item it) {
        List<Item> orden = ordenTodo();
        int actual = orden.indexOf(it) + 1;
        android.widget.NumberPicker np = new android.widget.NumberPicker(this);
        np.setMinValue(1);
        np.setMaxValue(Math.max(1, orden.size()));
        np.setValue(Math.max(1, actual));
        np.setWrapSelectorWheel(false);
        new AlertDialog.Builder(this).setTitle("↔ Mover " + it.nombre)
                .setMessage("Escoja el puesto. 1 = arriba a la izquierda; cada fila tiene 5.")
                .setView(np)
                .setPositiveButton("Poner aquí", (d, w) -> {
                    Map<String, Integer> ps = puestos();
                    ps.put(it.clave, np.getValue());
                    guardarPuestos(ps);
                    aviso("✔ " + it.nombre + " quedó en el puesto " + np.getValue());
                    armar();
                })
                .setNeutralButton("Soltar (que se ordene sola)", (d, w) -> {
                    Map<String, Integer> ps = puestos();
                    ps.remove(it.clave);
                    guardarPuestos(ps);
                    armar();
                })
                .setNegativeButton("Cancelar", null).show();
    }

    /** ⬆ Subir al primer puesto: le suma usos de hoy hasta quedar de primera (después baja sola si no la usa). */
    private void subirPrimero(Item it) {
        int max = 0;
        for (Item i : ordenTodo()) if (!i.clave.equals(it.clave)) max = Math.max(max, usosMes(i.clave));
        int falta = Math.max(3, max + 1) - usosMes(it.clave);
        if (falta > 0) anotarUso(it.clave, falta);
        else anotarUso(it.clave, 1);
        aviso("⬆ " + it.nombre + " quedó de primera");
        armar();
    }

    private List<Item> masUsadas(int n, List<String> fuera) {
        List<Item> todos = new ArrayList<>(herramientas);
        todos.addAll(apps);
        todos.removeIf(i -> fuera.contains(i.clave) || prefs.getInt("u_" + i.clave, 0) == 0 || quitadas().contains(i.clave));
        todos.sort((a, b) -> prefs.getInt("u_" + b.clave, 0) - prefs.getInt("u_" + a.clave, 0));
        return todos.subList(0, Math.min(n, todos.size()));
    }

    // ---------------- Armar las páginas ----------------

    private static final int COLUMNAS = 5;
    // Versión 28: alturas fijas de la cabeza de la página 1 y de la página 2 (para llenar sin huecos)
    private static final int ALTO_RELOJ = 200, CABEZA_INICIO = 8 + ALTO_RELOJ + 56 + 54 + 6, CABEZA_HERR = 8 + 68, ALTO_MIN_FILA = 92;
    private int altoCelda;

    private boolean esperandoTamano, forzarTamano;

    /** Versión 32: si algo falla al armar las páginas, no se queda la pantalla vacía: se reintenta. */
    private void armar() {
        try { armarPaginas(); }
        catch (Throwable t) {
            if (intentosVigia++ < 6) h.postDelayed(this::armar, 1000);
        }
    }

    private void armarPaginas() {
        // Versión 31: si la pantalla todavía no tiene su tamaño (o quedó acostada después del radio reloj),
        // no se arma: antes salían muchas páginas vacías con una fila de puntitos. Se arma apenas quede bien parada.
        boolean malTamano = raiz.getHeight() < dp(400) || raiz.getWidth() >= raiz.getHeight();
        // Versión 32: si después de esperar sigue sin el tamaño ideal, se arma igual (antes podía quedarse esperando para siempre)
        if (forzarTamano && raiz.getHeight() > dp(200)) malTamano = false;
        if (malTamano) {
            if (!esperandoTamano) {
                h.postDelayed(() -> { if (esperandoTamano) { esperandoTamano = false; forzarTamano = true; armar(); } }, 2000);
                esperandoTamano = true;
                raiz.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                        if (v.getHeight() >= dp(400) && v.getWidth() < v.getHeight()) {
                            v.removeOnLayoutChangeListener(this);
                            esperandoTamano = false;
                            v.post(() -> armar());
                        }
                    }
                });
                raiz.requestLayout();
            }
            return;
        }
        forzarTamano = false;
        anchoPantalla = raiz.getWidth();
        int idEstado = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int arriba = idEstado > 0 ? getResources().getDimensionPixelSize(idEstado) : dp(24);
        int idNav = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int abajo = idNav > 0 ? getResources().getDimensionPixelSize(idNav) : dp(40);
        altoPagina = raiz.getHeight() - arriba - abajo - dp(26);
        altoCelda = dp(96);
        int volverA = pagina;
        if (paginador != null) raiz.removeView(paginador);
        if (puntos != null) raiz.removeView(puntos);
        paginador = new HorizontalScrollView(this) {
            float x0; int pag0;
            @Override public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0 = e.getX(); pag0 = pagina; }
                return super.onInterceptTouchEvent(e);
            }
            @Override public boolean onTouchEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0 = e.getX(); pag0 = pagina; }
                if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    super.onTouchEvent(e);
                    float dx = e.getX() - x0;
                    int destino = pag0;
                    if (dx < -anchoPantalla * 0.15f) destino = pag0 + 1;
                    else if (dx > anchoPantalla * 0.15f) destino = pag0 - 1;
                    irA(destino, true);
                    return true;
                }
                return super.onTouchEvent(e);
            }
        };
        paginador.setHorizontalScrollBarEnabled(false);
        paginador.setOverScrollMode(View.OVER_SCROLL_NEVER);
        paginas = new LinearLayout(this);
        paginador.addView(paginas);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, altoPagina);
        lp.topMargin = arriba;
        raiz.addView(paginador, 1, lp);

        // Versión 28: nada fijo; las más usadas primero y luego de la A a la Z, páginas llenas hasta abajo
        List<Item> todo = ordenTodo();
        int zona1 = altoPagina - dp(CABEZA_INICIO);
        int filas1 = Math.max(1, zona1 / dp(ALTO_MIN_FILA));
        int n1 = Math.min(todo.size(), filas1 * COLUMNAS);
        paginas.addView(paginaInicio(todo.subList(0, n1), filas1, zona1 / filas1), new LinearLayout.LayoutParams(anchoPantalla, altoPagina));
        int i0 = n1;
        boolean primera = true;
        while (i0 < todo.size() || primera) {
            int zona = altoPagina - dp(primera ? CABEZA_HERR : 12);
            int filas = Math.max(1, zona / dp(ALTO_MIN_FILA));
            int n = Math.min(todo.size(), i0 + filas * COLUMNAS);
            paginas.addView(paginaHerramientas(todo.subList(i0, n), primera, filas, zona / filas), new LinearLayout.LayoutParams(anchoPantalla, altoPagina));
            i0 = n;
            primera = false;
        }
        // Puntitos de página
        puntos = new LinearLayout(this);
        puntos.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, dp(20), Gravity.BOTTOM);
        pp.bottomMargin = abajo + dp(4);
        raiz.addView(puntos, 2, pp);
        pintarPuntos();
        refrescarRecordatorios();
        pintarBateria();
        tic();
        final int v = Math.min(volverA, paginas.getChildCount() - 1);
        paginador.post(() -> irA(v, false));
    }

    private void irA(int p, boolean suave) {
        if (paginas == null) return;
        pagina = Math.max(0, Math.min(paginas.getChildCount() - 1, p));
        if (suave) paginador.smoothScrollTo(pagina * anchoPantalla, 0); else paginador.scrollTo(pagina * anchoPantalla, 0);
        pintarPuntos();
    }

    private void pintarPuntos() {
        if (puntos == null) return;
        puntos.removeAllViews();
        int n = paginas.getChildCount();
        for (int i = 0; i < n; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == pagina ? Color.WHITE : 0x66FFFFFF);
            d.setBackground(g);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(i == pagina ? 9 : 7), dp(i == pagina ? 9 : 7));
            l.setMargins(dp(4), 0, dp(4), 0);
            puntos.addView(d, l);
        }
    }

    private GradientDrawable vidrio(float radio) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xD9050C28);   // versión 28: oscuro y firme, nada pálido
        g.setCornerRadius(dp(radio));
        g.setStroke(dp(2), 0x66FFFFFF);
        return g;
    }

    private TextView texto(String s, float tam, int color, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(tam);
        t.setTextColor(color);
        if (negrita) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setShadowLayer(dp(3), 0, dp(1), 0x66000000);
        return t;
    }

    // ---- Página 1 ----

    private final TextView[] tMundo = new TextView[3];
    private final TextView[] tFranja = new TextView[3];
    private final TextView[] tMundoC = new TextView[3];
    private TextView tAvisoClima;

    static final String[][] CIUDADES = {{"Nueva York", "America/New_York"}, {"Miami", "America/New_York"}, {"Chicago", "America/Chicago"},
            {"Los Ángeles", "America/Los_Angeles"}, {"Canadá", "America/Toronto"}, {"México", "America/Mexico_City"}, {"Panamá", "America/Panama"},
            {"Lima", "America/Lima"}, {"Quito", "America/Guayaquil"}, {"Caracas", "America/Caracas"}, {"Santiago", "America/Santiago"},
            {"Buenos Aires", "America/Argentina/Buenos_Aires"}, {"Brasil", "America/Sao_Paulo"}, {"España", "Europe/Madrid"},
            {"Londres", "Europe/London"}, {"París", "Europe/Paris"}, {"Italia", "Europe/Rome"}, {"Alemania", "Europe/Berlin"},
            {"Israel", "Asia/Jerusalem"}, {"Dubái", "Asia/Dubai"}, {"China", "Asia/Shanghai"}, {"Japón", "Asia/Tokyo"}, {"Australia", "Australia/Sydney"}};

    private String[] ciudadesMundo() {
        String[] c = prefs.getString("mundo", "Nueva York|España|México").split("\\|");
        return c.length == 3 ? c : new String[]{"Nueva York", "España", "México"};
    }

    private static String zonaDe(String ciudad) {
        for (String[] c : CIUDADES) if (c[0].equals(ciudad)) return c[1];
        return "UTC";
    }

    private View paginaInicio(List<Item> items, int filas, int altoFila) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(10), dp(8), dp(10), 0);

        // Versión 29: reloj y almanaque del MISMO tamaño; hora centrada; los 3 relojes del mundo DENTRO del cuadro del reloj
        LinearLayout arriba = new LinearLayout(this);
        LinearLayout rel = new LinearLayout(this);
        rel.setOrientation(LinearLayout.VERTICAL);
        rel.setGravity(Gravity.CENTER_HORIZONTAL);
        rel.setPadding(dp(8), dp(6), dp(8), dp(6));
        rel.setBackground(vidrio(20));
        tHora = texto("", 50, Color.WHITE, true);
        tHora.setTypeface(Typeface.DEFAULT_BOLD);
        tHora.setIncludeFontPadding(false);
        tHora.setGravity(Gravity.CENTER);
        tHora.setMaxLines(1);
        rel.addView(tHora, new LinearLayout.LayoutParams(-1, -2));
        tAmPm = texto("", 14, 0xFFFFD54F, true);
        tAmPm.setGravity(Gravity.CENTER);
        tAmPm.setMaxLines(1);
        rel.addView(tAmPm, new LinearLayout.LayoutParams(-1, -2));
        tFecha = texto("", 11, Color.WHITE, false);
        tBateria = texto("", 13, Color.WHITE, true);
        tBateria.setGravity(Gravity.CENTER);
        tBateria.setMaxLines(1);
        rel.addView(tBateria, new LinearLayout.LayoutParams(-1, -2));
        tAvisoClima = texto("", 13, 0xFFFFD54F, true);
        tAvisoClima.setGravity(Gravity.CENTER);
        tAvisoClima.setMaxLines(1);
        tAvisoClima.setEllipsize(android.text.TextUtils.TruncateAt.END);
        rel.addView(tAvisoClima, new LinearLayout.LayoutParams(-1, -2));
        View raya = new View(this);
        raya.setBackgroundColor(0x55FFFFFF);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(1));
        rp.topMargin = dp(3); rp.bottomMargin = dp(2);
        rel.addView(raya, rp);
        for (int i = 0; i < 3; i++) {
            LinearLayout f = new LinearLayout(this);
            tMundoC[i] = texto("", 12, 0xFFB3E5FC, false);
            tMundoC[i].setMaxLines(1);
            f.addView(tMundoC[i], new LinearLayout.LayoutParams(0, -2, 1f));
            tMundo[i] = texto("", 12, Color.WHITE, true);
            tMundo[i].setMaxLines(1);
            tMundo[i].setGravity(Gravity.END);
            f.addView(tMundo[i], new LinearLayout.LayoutParams(-2, -2));
            final int k = i;
            f.setOnClickListener(v -> escogerCiudad(0));
            rel.addView(f, new LinearLayout.LayoutParams(-1, -2));
        }
        rel.setOnClickListener(v -> menuReloj());
        rel.setOnLongClickListener(v -> { menuReloj(); return true; });
        arriba.addView(rel, new LinearLayout.LayoutParams(0, -1, 1f));
        calendario = new LinearLayout(this);
        calendario.setOrientation(LinearLayout.VERTICAL);
        calendario.setPadding(dp(4), dp(6), dp(4), dp(4));
        calendario.setBackground(vidrio(20));
        calendario.setOnClickListener(v -> Puerta.abrirChpRecordatorios(this));   // versión 47
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1f);
        cp.leftMargin = dp(6);
        arriba.addView(calendario, cp);
        l.addView(arriba, new LinearLayout.LayoutParams(-1, dp(ALTO_RELOJ)));

        // Señal de cada SIM y batería
        estado = new Estado(this);
        estado.setBackground(vidrio(16));
        estado.setOnClickListener(v -> ajuste(Settings.ACTION_WIRELESS_SETTINGS));
        estado.setOnLongClickListener(v -> { ponerNumeros(); return true; });
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(50));
        ep.topMargin = dp(6);
        l.addView(estado, ep);

        // Buscador
        LinearLayout bus = new LinearLayout(this);
        bus.setGravity(Gravity.CENTER_VERTICAL);
        bus.setPadding(dp(16), 0, dp(10), 0);
        bus.setBackground(vidrio(20));
        TextView tb = texto("🔎  Buscar app o herramienta", 16, Color.WHITE, true);
        tb.setMaxLines(1);
        tb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bus.addView(tb, new LinearLayout.LayoutParams(0, -2, 1f));
        bus.addView(texto("v" + Util.version(this), 13, 0xFFFFD54F, true));   // versión siempre a la vista
        bus.setOnClickListener(v -> abrirBusqueda(""));
        // Versión 45: el botón rojo de la grabadora al lado derecho del buscador
        LinearLayout filaBus = new LinearLayout(this);
        filaBus.setGravity(Gravity.CENTER_VERTICAL);
        filaBus.addView(bus, new LinearLayout.LayoutParams(0, -1, 1f));
        botonRojo = new TextView(this);
        botonRojo.setGravity(Gravity.CENTER);
        botonRojo.setTextColor(Color.WHITE);
        botonRojo.setOnClickListener(v -> tocarRojo());
        botonRojo.setOnLongClickListener(v -> { menuRojo(); return true; });
        LinearLayout.LayoutParams rp2 = new LinearLayout.LayoutParams(dp(48), dp(48));
        rp2.leftMargin = dp(6);
        filaBus.addView(botonRojo, rp2);
        pintarRojo();
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(48));
        bp.topMargin = dp(6);
        l.addView(filaBus, bp);

        // Versión 28: nada fijo. Hasta abajo, las que más usa (y luego de la A a la Z)
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, filas * altoFila);
        gp.topMargin = dp(6);
        l.addView(cuadricula(items, false, altoFila), gp);
        pintarCalendario();
        return l;
    }

    /** Busca la herramienta o la app; si Google no está instalado, un botón que abre la página de Google. */
    private Item resolver(String t) {
        if (t == null || t.isEmpty()) return null;
        Item it = porClave.get(t);
        if (it != null) return it;
        for (Item a : apps) if (a.clave.startsWith(t + "/")) return a;
        if (t.contains("googlequicksearchbox")) {
            Item g = new Item();
            g.clave = "web:google"; g.nombre = "Google"; g.nuestra = true;
            g.icono = new BitmapDrawable(getResources(), iconoEmoji("🔍", 0xFF1E88E5));
            g.abrir = () -> abrirUrl("https://www.google.com");
            return g;
        }
        return null;
    }

    // ---- Al tocar el reloj ----

    private void menuReloj() {
        String[] ops = {"⏰  Alarma", "⏳  Temporizador", "⏱  Cronómetro", "🌎  Relojes del mundo", "📻  Radio reloj"};
        new AlertDialog.Builder(this).setTitle("🕐 Reloj").setItems(ops, (d, w) -> {
            if (w == 0) startActivity(new Intent(this, RelojActivity.class).putExtra("pestana", 2));
            else if (w == 1) startActivity(new Intent(this, RelojActivity.class).putExtra("pestana", 1));
            else if (w == 2) startActivity(new Intent(this, RelojActivity.class).putExtra("pestana", 0));
            else if (w == 3) escogerCiudad(0);
            else startActivity(new Intent(this, RadioRelojActivity.class));
        }).show();
    }

    /** Escoge las 3 ciudades, una por una. */
    private void escogerCiudad(int n) {
        if (n > 2) { tic(); aviso("✔ Relojes del mundo listos"); return; }
        String[] nombres = new String[CIUDADES.length];
        for (int i = 0; i < nombres.length; i++) nombres[i] = CIUDADES[i][0];
        new AlertDialog.Builder(this).setTitle("🌎 Reloj " + (n + 1) + " de 3").setItems(nombres, (d, w) -> {
            String[] c = ciudadesMundo();
            c[n] = nombres[w];
            prefs.edit().putString("mundo", String.join("|", c)).apply();
            escogerCiudad(n + 1);
        }).setNegativeButton("Listo", (d, w) -> tic()).show();
    }

    /** Página de herramientas: la primera trae la frase del día arriba (v45: la grabadora pasó al lado del buscador). */
    private View paginaHerramientas(List<Item> items, boolean primera, int filas, int altoFila) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(8), dp(8), dp(8), 0);
        if (primera) {
            tFrase = texto("✨ " + Frases.deHoy(), 16, Color.WHITE, false);
            tFrase.setTypeface(Typeface.create("serif", Typeface.ITALIC));
            tFrase.setGravity(Gravity.CENTER);
            tFrase.setPadding(dp(12), 0, dp(12), 0);
            tFrase.setBackground(vidrio(18));
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, dp(56));
            fp.topMargin = dp(8);
            fp.bottomMargin = dp(4);
            l.addView(tFrase, fp);
        }
        l.addView(cuadricula(items, false, altoFila), new LinearLayout.LayoutParams(-1, filas * altoFila));
        return l;
    }

    private LinearLayout cuadricula(List<Item> items, boolean esFavorita) { return cuadricula(items, esFavorita, altoCelda); }

    private LinearLayout cuadricula(List<Item> items, boolean esFavorita, int altoFila) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        LinearLayout fila = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % COLUMNAS == 0) {
                fila = new LinearLayout(this);
                g.addView(fila, new LinearLayout.LayoutParams(-1, altoFila));
            }
            fila.addView(celda(items.get(i), esFavorita, altoFila), new LinearLayout.LayoutParams(0, -1, 1f));
        }
        if (fila != null) for (int k = items.size() % COLUMNAS; k > 0 && k < COLUMNAS; k++) fila.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f));
        return g;
    }

    /** Versión 28: el ícono crece con la fila para no dejar huecos; nombre en letra gruesa. */
    private View celda(Item it, boolean esFavorita, int altoFila) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        int lado = Math.max(dp(48), Math.min(dp(70), altoFila - dp(34)));
        lado = Math.min(lado, anchoPantalla / COLUMNAS - dp(10));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(it.icono);
        c.addView(iv, new LinearLayout.LayoutParams(lado, lado));
        TextView t = texto(corto(it.nombre), 13, Color.WHITE, true);   // versión 47: nombre corto (ChpRecordatorios → Record)
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(dp(2), dp(4), dp(2), 0);
        c.addView(t, new LinearLayout.LayoutParams(-1, -2));
        c.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90));
            usar(it);
        });
        c.setOnLongClickListener(v -> { opciones(it); return true; });
        return c;
    }

    /** Dejar presionado: subir al primer puesto, quitarla de la pantalla o desinstalar. (Versión 28: sin favoritas ni fijas) */
    private void opciones(Item it) {
        List<String> ops = new ArrayList<>();
        ops.add("↔ Mover a otro puesto");
        ops.add("⬆ Subir a la primera página");
        if ("mb:apagar".equals(it.clave)) ops.add("⏻ Apagar o reiniciar el celular");
        ops.add("🙈 Quitar de la pantalla");
        if (!it.nuestra) { ops.add("🗑 Desinstalar"); ops.add("ℹ Información de la app"); }
        new AlertDialog.Builder(this).setTitle(it.nombre).setItems(ops.toArray(new String[0]), (d, w) -> {
            String o = ops.get(w);
            if (o.startsWith("↔")) moverPuesto(it);
            else if (o.startsWith("⬆")) subirPrimero(it);
            else if (o.startsWith("⏻")) accionBarra("poder");
            else if (o.startsWith("🙈")) {
                Set<String> q = quitadas(); q.add(it.clave);
                prefs.edit().putStringSet("quitadas", q).apply();
                aviso("Quitada. Para volver a ponerla: ⚙ Ajustes Inicio");
                armar();
            } else if (o.startsWith("🗑")) {
                try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + it.clave.split("/")[0]))); } catch (Exception e) { aviso("No se pudo"); }
            } else if (o.startsWith("ℹ")) {
                try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + it.clave.split("/")[0]))); } catch (Exception ignored) {}
            }
        }).show();
    }

    // ---------------- Versión 15: señal de las SIM y batería grande ----------------

    private final int[] nivelSim = {-1, -1};
    private final String[] nombreSim = {"", ""};
    private boolean modoAvion;

    private final Runnable senal = new Runnable() {
        @Override public void run() { leerSenal(); if (estado != null) estado.invalidate(); h.postDelayed(this, 15000); }
    };

    @SuppressWarnings("deprecation")
    private void leerSenal() {
        try { modoAvion = Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0; }
        catch (Exception e) { modoAvion = false; }
        android.telephony.TelephonyManager tm = getSystemService(android.telephony.TelephonyManager.class);
        android.telephony.SubscriptionManager sm = getSystemService(android.telephony.SubscriptionManager.class);
        for (int slot = 0; slot < 2; slot++) {
            nivelSim[slot] = -1;
            nombreSim[slot] = "";
            try {
                if (tm == null) continue;
                if (tm.getSimState(slot) != android.telephony.TelephonyManager.SIM_STATE_READY) continue;
                android.telephony.TelephonyManager t = tm;
                int[] ids = sm == null ? null : sm.getSubscriptionIds(slot);
                if (ids != null && ids.length > 0) t = tm.createForSubscriptionId(ids[0]);
                android.telephony.SignalStrength ss = t.getSignalStrength();
                nivelSim[slot] = ss == null ? 0 : Math.max(0, Math.min(4, ss.getLevel()));
                String n = t.getSimOperatorName();
                // Versión 19: letra del operador + número de la SIM (E 310 123 4567)
                String num = getSharedPreferences("inicio", MODE_PRIVATE).getString("numSim" + slot, "");
                if (num.isEmpty() && ids != null && ids.length > 0) num = numeroDe(sm, ids[0]);
                nombreSim[slot] = etiquetaSim(n == null ? "" : n, num);
            } catch (Throwable ignored) {
                if (nivelSim[slot] < 0) nivelSim[slot] = 0;
            }
        }
    }

    private void pedirNumeroUnaVez() {
        SharedPreferences pr = getSharedPreferences("inicio", MODE_PRIVATE);
        if (pr.getBoolean("pidioNumero", false)) return;
        pr.edit().putBoolean("pidioNumero", true).apply();
        if (checkSelfPermission("android.permission.READ_PHONE_NUMBERS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.READ_PHONE_NUMBERS", "android.permission.READ_PHONE_STATE"}, 19);
    }

    /** E = Éxito, T = Tigo, M = Movistar, W = WOM, C = Claro… seguido del número. */
    static String etiquetaSim(String operador, String numero) {
        String o = operador.toLowerCase(java.util.Locale.ROOT);
        String letra = o.contains("xito") ? "E" : o.contains("tigo") ? "T" : o.contains("movistar") ? "M"
                : o.contains("wom") ? "W" : o.contains("claro") || o.contains("comcel") ? "C" : o.contains("etb") ? "ETB"
                : o.contains("virgin") ? "V" : o.contains("avantel") ? "A" : o.contains("flash") ? "F"
                : operador.isEmpty() ? "" : operador.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        String d = numero.replaceAll("[^0-9]", "");
        if (d.startsWith("57") && d.length() == 12) d = d.substring(2);
        if (d.length() == 10) d = d.substring(0, 3) + " " + d.substring(3, 6) + " " + d.substring(6);
        if (d.isEmpty()) return operador;
        return (letra.isEmpty() ? "" : letra + " ") + d;
    }

    @SuppressWarnings("deprecation")
    private String numeroDe(android.telephony.SubscriptionManager sm, int id) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                String n = sm.getPhoneNumber(id);
                if (n != null && !n.isEmpty()) return n;
            }
            android.telephony.SubscriptionInfo si = sm.getActiveSubscriptionInfo(id);
            if (si != null && si.getNumber() != null) return si.getNumber();
        } catch (Throwable ignored) {}
        return "";
    }

    /** Si el celular no sabe el número de la SIM, se escribe una vez y queda guardado. */
    private void ponerNumeros() {
        SharedPreferences pr = getSharedPreferences("inicio", MODE_PRIVATE);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(8), dp(20), 0);
        android.widget.EditText[] e = new android.widget.EditText[2];
        for (int i = 0; i < 2; i++) {
            TextView t = new TextView(this);
            t.setText("Número de la SIM " + (i + 1) + (nombreSim[i].isEmpty() ? "" : "  (" + nombreSim[i] + ")"));
            t.setTextSize(15);
            l.addView(t);
            e[i] = new android.widget.EditText(this);
            e[i].setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            e[i].setText(pr.getString("numSim" + i, ""));
            e[i].setHint("Ej: 310 123 4567");
            l.addView(e[i]);
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("📶 Números de las SIM")
                .setView(l)
                .setPositiveButton("Guardar", (d, w) -> {
                    pr.edit().putString("numSim0", e[0].getText().toString().trim())
                            .putString("numSim1", e[1].getText().toString().trim()).apply();
                    leerSenal();
                    if (estado != null) estado.invalidate();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** La franja ancha: SIM 1 y SIM 2 con sus barritas a la izquierda, la batería con color a la derecha. */
    private class Estado extends View {
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        Estado(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            // Versión 28: las dos SIM una encima de la otra, números grandes y sin la letra del operador
            float alto = getHeight();
            p.setShadowLayer(dp(2), 0, dp(1), 0x66000000);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            float espacio = getWidth() - dp(150) - dp(40);
            for (int i = 0; i < 2; i++) {
                int nivel = nivelSim[i];
                float base = i == 0 ? alto / 2 - dp(3) : alto - dp(5), x = dp(12), bw = dp(4), sep = dp(2);
                for (int k = 0; k < 4; k++) {
                    float bh = dp(4) + k * dp(3);
                    p.setColor(modoAvion || nivel < 0 ? 0x55FFFFFF : (k < nivel ? Color.WHITE : 0x55FFFFFF));
                    c.drawRoundRect(x + k * (bw + sep), base - bh, x + k * (bw + sep) + bw, base, dp(1), dp(1), p);
                }
                x += 4 * (bw + sep) + dp(8);
                p.setColor(Color.WHITE);
                p.setTextSize(dp(18));
                String et = modoAvion ? "✈ Modo avión" : nivel < 0 ? "Sin SIM" : nombreSim[i].isEmpty() ? "SIM " + (i + 1) : nombreSim[i];
                et = et.replaceFirst("^[A-Z]{1,3} (?=\\d)", "");
                if (et.length() > 18) et = et.substring(0, 18);
                while (p.measureText(et) > espacio && p.getTextSize() > dp(12)) p.setTextSize(p.getTextSize() - 1);
                c.drawText(et, x, base, p);
                if (modoAvion) break;
            }
            // Batería grande a la derecha
            if (nivelBateria < 0) return;
            int col = nivelBateria <= 20 ? 0xFFFF3B30 : 0xFFFFD54F;
            String pct = (cargando ? "⚡" : "") + nivelBateria + " %";
            p.setTextSize(dp(23));
            p.setTypeface(Typeface.DEFAULT_BOLD);
            float tw = p.measureText(pct);
            float right = getWidth() - dp(14);
            p.setColor(col);
            c.drawText(pct, right - tw, alto / 2 + dp(7), p);
            float bR = right - tw - dp(8), bL = bR - dp(44), bT = alto / 2 - dp(10), bB = alto / 2 + dp(10);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(dp(2.5f));
            p.setColor(Color.WHITE);
            c.drawRoundRect(bL, bT, bR, bB, dp(4), dp(4), p);
            p.setStyle(android.graphics.Paint.Style.FILL);
            c.drawRoundRect(bR + dp(1), alto / 2 - dp(4), bR + dp(4), alto / 2 + dp(4), dp(1), dp(1), p);
            p.setColor(col);
            float lleno = (bR - bL - dp(6)) * Math.max(4, nivelBateria) / 100f;
            c.drawRoundRect(bL + dp(3), bT + dp(3), bL + dp(3) + lleno, bB - dp(3), dp(2), dp(2), p);
        }
    }

    /** Si Xiaomi apagó el botón flotante, un aviso rojo arriba para volver a prenderlo de un toque. */
    /** Versión 18: vuelve a poner el botón naranja y, en Xiaomi, abre los permisos para que no lo apaguen. */
    private void reactivarBoton() {
        try { sendBroadcast(new Intent(BarraService.AVISO_BARRA).setPackage(Puerta.CHPBAR).putExtra("boton", true)); } catch (Exception ignored) {}
        boolean activo = false;
        try {
            String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            activo = s != null && s.contains(Puerta.CHPBAR + "/");
        } catch (Exception ignored) {}
        if (!activo) { aviso("Active «Mi Barra» en Accesibilidad"); ajuste(Settings.ACTION_ACCESSIBILITY_SETTINGS); return; }
        if (android.os.Build.MANUFACTURER.equalsIgnoreCase("xiaomi") || android.os.Build.MANUFACTURER.equalsIgnoreCase("redmi")) {
            new android.app.AlertDialog.Builder(this).setTitle("Que Xiaomi no apague el botón")
                    .setMessage("1. Inicio automático: active Mi Barra.\n2. Batería: escoja «Sin restricciones».\n\nEl botón ya quedó puesto abajo a la izquierda.")
                    .setPositiveButton("Inicio automático", (d, w) -> {
                        try {
                            startActivity(new Intent().setComponent(new android.content.ComponentName("com.miui.securitycenter",
                                    "com.miui.permcenter.autostart.AutoStartManagementActivity")));
                        } catch (Exception e) { ajuste(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); }
                    })
                    .setNeutralButton("Batería", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                        } catch (Exception e) { ajuste(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS); }
                    })
                    .setNegativeButton("Listo", null).show();
        } else aviso("🟠 Botón puesto abajo a la izquierda");
    }

    private void revisarBoton() {
        // Versión 47: ya no sale el letrero rojo del botón flotante (él pregunta si lo necesita)
        if (avisoBoton != null) { raiz.removeView(avisoBoton); avisoBoton = null; }
        if (avisoBoton == null) return;
        boolean activo = false;
        try {
            String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            activo = s != null && s.contains(Puerta.CHPBAR + "/");
        } catch (Exception ignored) {}
        if (activo) {
            if (avisoBoton != null) { raiz.removeView(avisoBoton); avisoBoton = null; }
            return;
        }
        if (avisoBoton != null) return;
        TextView t = texto("⚠ El botón flotante se apagó. Toque aquí y active «Mi Barra»", 14, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xEED32F2F);
        g.setCornerRadius(dp(14));
        t.setBackground(g);
        // Versión 31: además de prenderlo, explica cómo hacer que Xiaomi no lo vuelva a apagar
        t.setOnClickListener(v -> new android.app.AlertDialog.Builder(this).setTitle("Que el botón no se vuelva a apagar")
                .setMessage("1. Accesibilidad: active «Mi Barra».\n"
                        + "2. Inicio automático: active Mi Barra.\n"
                        + "3. Batería: escoja «Sin restricciones».\n"
                        + "4. En las apps recientes (botón ■), deje el dedo sobre Mi Barra y toque el candado 🔒.\n\n"
                        + "Con esos 4 pasos el Xiaomi deja de apagarlo.")
                .setPositiveButton("Accesibilidad", (d, w) -> ajuste(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                .setNeutralButton("Inicio automático", (d, w) -> {
                    try {
                        startActivity(new Intent().setComponent(new android.content.ComponentName("com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity")));
                    } catch (Exception e) { ajuste(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); }
                })
                .setNegativeButton("Batería", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) { ajuste(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS); }
                }).show());
        int idEstado = getResources().getIdentifier("status_bar_height", "dimen", "android");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        lp.topMargin = (idEstado > 0 ? getResources().getDimensionPixelSize(idEstado) : dp(24)) + dp(4);
        lp.leftMargin = dp(12); lp.rightMargin = dp(12);
        raiz.addView(t, lp);
        avisoBoton = t;
    }

    // ---------------- Reloj, batería, clima, recordatorios, almanaque ----------------

    private final Runnable reloj = new Runnable() {
        @Override public void run() { tic(); h.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 30); }
    };

    /** Versión 28: si la hora es larga (10:00 a 12:59) se achica la letra para que se vea completa. */
    private void ajustarHora() {
        int ancho = tHora.getWidth() - tHora.getPaddingLeft() - tHora.getPaddingRight();
        if (ancho <= 0) { tHora.post(() -> { if (tHora.getWidth() > 0) ajustarHora(); }); return; }
        float max = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 50, getResources().getDisplayMetrics());
        android.graphics.Paint p = new android.graphics.Paint(tHora.getPaint());
        p.setTextSize(max);
        float w = p.measureText(tHora.getText().toString());
        float tam = w > ancho * 0.92f ? max * ancho * 0.92f / w : max;
        if (Math.abs(tHora.getTextSize() - tam) > 0.5f) tHora.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, tam);
    }

    private int diaPintado = -1;

    private void tic() {
        if (tHora == null) return;
        Date ahora = new Date();
        tHora.setText(new SimpleDateFormat("h:mm", es).format(ahora));
        ajustarHora();
        String[] cm = ciudadesMundo();
        for (int i = 0; i < 3; i++) {
            if (tMundo[i] == null) continue;
            SimpleDateFormat zf = new SimpleDateFormat("h:mm a", Locale.US);
            zf.setTimeZone(java.util.TimeZone.getTimeZone(zonaDe(cm[i])));
            String hz = zf.format(ahora).toLowerCase(Locale.US).replace("am", "a. m.").replace("pm", "p. m.");
            tMundo[i].setText(hz);
            String nc = cm[i].replace("Nueva York", "N. York").replace("Los Ángeles", "L. Ángeles").replace("Buenos Aires", "B. Aires");
            if (tMundoC[i] != null) tMundoC[i].setText(nc);
        }
        String dd = new SimpleDateFormat("EEE d", es).format(ahora).replace(".", "");
        tAmPm.setText(new SimpleDateFormat("a", es).format(ahora).replace(". ", ".").replace("a.m.", "a. m.").replace("p.m.", "p. m.")
                + " · " + Character.toUpperCase(dd.charAt(0)) + dd.substring(1));
        String f = new SimpleDateFormat("EEEE d 'de' MMMM", es).format(ahora);
        tFecha.setText(Character.toUpperCase(f.charAt(0)) + f.substring(1));
        int d = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        if (d != diaPintado) { diaPintado = d; pintarCalendario(); if (tFrase != null) tFrase.setText("✨ " + Frases.deHoy()); refrescarRecordatorios(); }
    }

    private String climaTxt = "";

    private void pintarBateria() {
        if (estado != null) estado.invalidate();
        if (tBateria == null) return;
        tBateria.setText(climaTxt);
        tBateria.setVisibility(climaTxt.isEmpty() ? View.GONE : View.VISIBLE);
        // Versión 22: el pronóstico del día
        String[] dia = prefs.getString("climaDia", "").split("\t");
        if (tAvisoClima != null) {
            tAvisoClima.setText(dia.length >= 4 ? dia[0] : "");
            tAvisoClima.setVisibility(dia.length >= 4 ? View.VISIBLE : View.GONE);
            for (int i = 0; i < 3; i++) if (tFranja[i] != null) tFranja[i].setText(dia.length >= 4 ? dia[i + 1] : "");
        }
    }

    private void refrescarRecordatorios() {
        if (tRecord == null) return;
        SimpleDateFormat hf = new SimpleDateFormat("h:mm a", es);
        StringBuilder s = new StringBuilder();
        int n = 0;
        for (Recordatorios.R r : Recordatorios.deHoy(this)) {
            if (r.hecho) continue;
            if (n++ >= 2) break;
            if (s.length() > 0) s.append('\n');
            s.append("📅 ").append(hf.format(new Date(r.cuando))).append("  ").append(r.texto);
        }
        tRecord.setText(s.length() == 0 ? "📅 Sin recordatorios hoy\nToque para poner uno" : s.toString());
    }

    private void pintarCalendario() {
        if (calendario == null) return;
        calendario.removeAllViews();
        Calendar hoy = Calendar.getInstance(), k = Calendar.getInstance();
        k.set(Calendar.DAY_OF_MONTH, 1);
        TextView m = texto(RecordatoriosActivity.MESES[k.get(Calendar.MONTH)].toUpperCase(es), 15, 0xFFFF5252, true);
        m.setPadding(dp(4), 0, 0, dp(2));
        calendario.addView(m);
        Set<Integer> conRec = new HashSet<>();
        for (Recordatorios.R r : Recordatorios.leer(this)) {
            if (r.hecho) continue;
            Calendar z = Calendar.getInstance();
            z.setTimeInMillis(r.cuando);
            if (z.get(Calendar.YEAR) == hoy.get(Calendar.YEAR) && z.get(Calendar.MONTH) == hoy.get(Calendar.MONTH)) conRec.add(z.get(Calendar.DAY_OF_MONTH));
        }
        String[] dias = {"L", "M", "M", "J", "V", "S", "D"};
        LinearLayout f = new LinearLayout(this);
        for (String d : dias) { TextView t = texto(d, 13, 0xFF90CAF9, true); t.setGravity(Gravity.CENTER); f.addView(t, new LinearLayout.LayoutParams(0, -2, 1f)); }
        calendario.addView(f);
        int primero = (k.get(Calendar.DAY_OF_WEEK) + 5) % 7, total = k.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 0; i < 42; i++) {
            if (i % 7 == 0) {
                if (i >= primero + total) break;
                f = new LinearLayout(this);
                calendario.addView(f, new LinearLayout.LayoutParams(-1, dp(21)));
            }
            int dia = i - primero + 1;
            TextView t = texto(dia >= 1 && dia <= total ? String.valueOf(dia) : "", 15.5f, Color.WHITE, true);
            t.setIncludeFontPadding(false);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(0, 0, 0, 0);
            if (dia == hoy.get(Calendar.DAY_OF_MONTH)) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(0xFFE53935);
                g.setCornerRadius(dp(11));
                t.setBackground(g);
                t.setTypeface(Typeface.DEFAULT_BOLD);
            } else if (conRec.contains(dia)) { t.setTextColor(0xFFFFD54F); t.setTypeface(Typeface.DEFAULT_BOLD); t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG); }
            f.addView(t, new LinearLayout.LayoutParams(0, -1, 1f));
        }
    }

    /** Clima gratis (Open‑Meteo), con la última ubicación conocida; se actualiza cada 30 minutos. */
    private void clima() {
        long antes = prefs.getLong("climaHora", 0);
        if (System.currentTimeMillis() - antes < 30 * 60000) { climaTxt = prefs.getString("clima", ""); pintarBateria(); return; }
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        new Thread(() -> {
            try {
                android.location.LocationManager lm = getSystemService(android.location.LocationManager.class);
                android.location.Location l = null;
                for (String pr : new String[]{"network", "gps", "passive"}) {
                    try { android.location.Location x = lm.getLastKnownLocation(pr); if (x != null && (l == null || x.getTime() > l.getTime())) l = x; } catch (Exception ignored) {}
                }
                if (l == null) return;
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code"
                                + "&hourly=temperature_2m,weather_code,precipitation_probability&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto",
                        l.getLatitude(), l.getLongitude())).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = c.getInputStream()) { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) bo.write(b, 0, n); }
                org.json.JSONObject todo = new org.json.JSONObject(bo.toString("UTF-8"));
                org.json.JSONObject cur = todo.getJSONObject("current");
                String maxMin = "", diaTxt = "";
                try {
                    org.json.JSONObject dly = todo.getJSONObject("daily");
                    maxMin = "   " + Math.round(dly.getJSONArray("temperature_2m_max").getDouble(0)) + "° / "
                            + Math.round(dly.getJSONArray("temperature_2m_min").getDouble(0)) + "°";
                    org.json.JSONObject hr = todo.getJSONObject("hourly");
                    org.json.JSONArray ht = hr.getJSONArray("temperature_2m"), hc = hr.getJSONArray("weather_code"), hp = hr.getJSONArray("precipitation_probability");
                    String[] nom = {"Mañana", "Tarde", "Noche"}, cuando = {"en la mañana", "en la tarde", "en la noche"};
                    int[][] rango = {{6, 11}, {12, 17}, {18, 23}};
                    StringBuilder sb = new StringBuilder();
                    String avisoLluvia = null;
                    boolean sol = true;
                    for (int k = 0; k < 3; k++) {
                        double suma = 0; int n = 0, peor = 0, prob = 0;
                        for (int hh = rango[k][0]; hh <= rango[k][1] && hh < ht.length(); hh++) {
                            suma += ht.getDouble(hh); n++;
                            peor = Math.max(peor, hc.optInt(hh, 0));
                            prob = Math.max(prob, hp.optInt(hh, 0));
                        }
                        boolean llueve = peor >= 51 || prob >= 55;
                        if (llueve && avisoLluvia == null) avisoLluvia = "☔ Lluvia " + cuando[k];
                        if (peor > 2) sol = false;
                        sb.append('\t').append(nom[k]).append("\n").append(llueve ? "🌧" : iconoClima(peor)).append(' ').append(n == 0 ? "" : Math.round(suma / n) + "°");
                    }
                    diaTxt = (avisoLluvia != null ? avisoLluvia : sol ? "☀️ Día soleado" : "⛅ Nublado, sin lluvia") + sb;
                } catch (Exception ignored) {}
                int t = (int) Math.round(cur.getDouble("temperature_2m")), w = cur.getInt("weather_code");
                String ciudad = "";
                try {
                    List<android.location.Address> ad = new android.location.Geocoder(this, es).getFromLocation(l.getLatitude(), l.getLongitude(), 1);
                    if (ad != null && !ad.isEmpty() && ad.get(0).getLocality() != null) ciudad = " " + ad.get(0).getLocality();
                } catch (Exception ignored) {}
                String e = w == 0 ? "☀️" : w <= 2 ? "🌤" : w == 3 ? "☁️" : w <= 48 ? "🌫" : w <= 67 ? "🌧" : w <= 77 ? "🌨" : w <= 82 ? "🌦" : "⛈";
                String txt = e + " " + t + "°" + maxMin;
                prefs.edit().putString("clima", txt).putString("climaDia", diaTxt).putLong("climaHora", System.currentTimeMillis()).apply();
                runOnUiThread(() -> { climaTxt = txt; pintarBateria(); });
            } catch (Exception ignored) {}
        }).start();
    }

    static String iconoClima(int w) {
        return w == 0 ? "☀️" : w <= 2 ? "🌤" : w == 3 ? "☁️" : w <= 48 ? "🌫" : w <= 67 ? "🌧" : w <= 77 ? "🌨" : w <= 82 ? "🌦" : "⛈";
    }

    // ---------------- Buscador ----------------

    private static final int VOZ = 501;

    private void dictarBusqueda() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO").putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try { startActivityForResult(i, VOZ); } catch (Exception e) { abrirBusqueda(""); }
    }

    @Override
    protected void onActivityResult(int r, int res, Intent d) {
        super.onActivityResult(r, res, d);
        if (r == VOZ && res == RESULT_OK && d != null) {
            ArrayList<String> rs = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (rs != null && !rs.isEmpty()) abrirBusqueda(rs.get(0));
        } else if (r == 502 && res == RESULT_OK && d != null && d.getData() != null) {
            try {
                android.graphics.ImageDecoder.Source src = android.graphics.ImageDecoder.createSource(getContentResolver(), d.getData());
                Bitmap bm = android.graphics.ImageDecoder.decodeBitmap(src, (dec, info, s2) -> {
                    int w = info.getSize().getWidth(), hh = info.getSize().getHeight(), lado = Math.max(w, hh);
                    if (lado > 2000) dec.setTargetSize(w * 2000 / lado, hh * 2000 / lado);
                    dec.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                });
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(getFilesDir(), "fondo.jpg"))) {
                    bm.compress(Bitmap.CompressFormat.JPEG, 88, o);
                }
                prefs.edit().putBoolean("fondoFoto", true).apply();
                fondo.cargarFoto();
                fondo.invalidate();
            } catch (Exception e) { aviso("No se pudo poner esa foto"); }
        }
    }

    private void abrirBusqueda(String inicial) {
        cerrarCapas();
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(0xF00B1020);
        int idEstado = getResources().getIdentifier("status_bar_height", "dimen", "android");
        l.setPadding(dp(12), (idEstado > 0 ? getResources().getDimensionPixelSize(idEstado) : 0) + dp(10), dp(12), dp(10));
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setBackground(vidrio(24));
        f.setPadding(dp(14), 0, dp(6), 0);
        EditText e = new EditText(this);
        e.setHint("Buscar…");
        e.setHintTextColor(0x99FFFFFF);
        e.setTextColor(Color.WHITE);
        e.setTextSize(18);
        e.setBackground(null);
        e.setSingleLine(true);
        e.setText(inicial);
        f.addView(e, new LinearLayout.LayoutParams(0, dp(50), 1f));
        TextView x = texto("✕", 20, Color.WHITE, false);
        x.setPadding(dp(10), dp(6), dp(10), dp(6));
        x.setOnClickListener(v -> cerrarCapas());
        f.addView(x);
        l.addView(f);
        LinearLayout res = new LinearLayout(this);
        res.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(res);
        l.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        Runnable buscar = () -> {
            res.removeAllViews();
            String q = SaludosActivity.sinTildes(e.getText().toString().trim());
            List<Item> todos = new ArrayList<>(herramientas);
            todos.addAll(apps);
            // Versión 19: todas las apps y herramientas, por orden alfabético
            todos.sort((a, b) -> SaludosActivity.sinTildes(a.nombre).compareTo(SaludosActivity.sinTildes(b.nombre)));
            Set<String> fuera = quitadas();
            // Versión 22: primero las que EMPIEZAN por lo escrito; después, aparte, las que lo tienen en otra parte
            List<Item> empiezan = new ArrayList<>(), tienen = new ArrayList<>();
            for (Item it : todos) {
                if (fuera.contains(it.clave)) continue;
                String nn = SaludosActivity.sinTildes(it.nombre);
                // Versión 45: «linterna» o «chp» encuentran ChpLinterna; «g» encuentra Google Chrome aunque se llame Chrome
                String sinChp = nn.startsWith("chp") ? nn.substring(3).trim() : nn;
                boolean palabra = false;
                for (String w : (nn + " " + sinChp).split("[\\s·\\-_.]+")) if (!w.isEmpty() && w.startsWith(q)) { palabra = true; break; }
                if (q.isEmpty() || nn.startsWith(q) || sinChp.startsWith(q)) empiezan.add(it);
                else if (palabra || (it.clave + " ").toLowerCase(java.util.Locale.ROOT).contains("." + q) || nn.contains(q)) tienen.add(it);
            }
            // Versión 29: solo íconos, 5 por fila, sin nombres (primero los que empiezan por lo escrito)
            List<Item> lista = new ArrayList<>(empiezan);
            lista.addAll(tienen);
            int lado = (anchoPantalla > 0 ? anchoPantalla : getResources().getDisplayMetrics().widthPixels) / COLUMNAS - dp(10);
            lado = Math.min(lado, dp(64));
            // Versión 47: sin escribir nada, primero el historial: lo que más busca (y si empatan, lo último que buscó)
            if (q.isEmpty()) {
                List<Item> hist = historialBusqueda(lista);
                if (!hist.isEmpty()) {
                    lista.removeAll(hist);
                    TextView th = texto("🕘  Lo que más busca", 15, 0xFFFFD54F, true);
                    th.setPadding(dp(6), dp(10), 0, 0);
                    res.addView(th);
                    rejilla(res, hist, lado);
                    TextView tt = texto("Todas (A–Z)", 15, 0xFFBBDEFB, true);
                    tt.setPadding(dp(6), dp(14), 0, 0);
                    res.addView(tt);
                }
            }
            rejilla(res, lista, lado);
            String crudo = e.getText().toString().trim();
            if (!crudo.isEmpty()) {
                String[][] web = {{"🌐  Buscar en internet: ", "https://www.google.com/search?q="}, {"🗺  Buscar en Maps: ", "geo:0,0?q="},
                        {"▶  Buscar en YouTube: ", "https://www.youtube.com/results?search_query="}};
                for (String[] w : web) {
                    TextView t = texto(w[0] + crudo, 16, 0xFFBBDEFB, false);
                    t.setPadding(dp(10), dp(14), dp(10), dp(14));
                    t.setOnClickListener(v -> { cerrarCapas(); abrirUrl(w[1] + Uri.encode(crudo)); });
                    res.addView(t);
                }
            }
        };
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { buscar.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        e.setOnEditorActionListener((v, a, ev) -> {
            String crudo = e.getText().toString().trim();
            if (!crudo.isEmpty()) { cerrarCapas(); abrirUrl("https://www.google.com/search?q=" + Uri.encode(crudo)); }
            return true;
        });
        buscar.run();
        busqueda = l;
        raiz.addView(l, new FrameLayout.LayoutParams(-1, -1));
        e.requestFocus();
        e.setSelection(e.getText().length());
        h.postDelayed(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(e, InputMethodManager.SHOW_IMPLICIT), 200);
    }

    /** Versión 47: los íconos de la búsqueda, 5 por fila. Lo que toca aquí queda en el historial. */
    private void rejilla(LinearLayout res, List<Item> lista, int lado) {
        LinearLayout fila = null;
        for (int i = 0; i < lista.size(); i++) {
            Item it = lista.get(i);
            if (i % COLUMNAS == 0) {
                fila = new LinearLayout(this);
                fila.setPadding(0, dp(8), 0, dp(8));
                res.addView(fila, new LinearLayout.LayoutParams(-1, -2));
            }
            FrameLayout cel = new FrameLayout(this);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(it.icono);
            cel.addView(iv, new FrameLayout.LayoutParams(lado, lado, Gravity.CENTER));
            cel.setOnClickListener(v -> { anotarBusqueda(it.clave); usar(it); });
            cel.setOnLongClickListener(v -> { opciones(it); return true; });
            fila.addView(cel, new LinearLayout.LayoutParams(0, lado + dp(4), 1f));
        }
        if (fila != null) for (int k = lista.size() % COLUMNAS; k > 0 && k < COLUMNAS; k++) fila.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
    }

    /** Historial de búsqueda: clave=veces:última|… (máximo 30). */
    private void anotarBusqueda(String clave) {
        Map<String, long[]> m = leerHistorial();
        long[] v = m.get(clave);
        if (v == null) v = new long[2];
        v[0]++;
        v[1] = System.currentTimeMillis();
        m.put(clave, v);
        List<Map.Entry<String, long[]>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> a.getValue()[0] != b.getValue()[0] ? Long.compare(b.getValue()[0], a.getValue()[0]) : Long.compare(b.getValue()[1], a.getValue()[1]));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(30, l.size()); i++)
            sb.append(sb.length() > 0 ? "|" : "").append(l.get(i).getKey()).append('=').append(l.get(i).getValue()[0]).append(':').append(l.get(i).getValue()[1]);
        prefs.edit().putString("busq47", sb.toString()).apply();
    }

    private Map<String, long[]> leerHistorial() {
        Map<String, long[]> m = new LinkedHashMap<>();
        for (String x : prefs.getString("busq47", "").split("\\|")) {
            int k = x.lastIndexOf('='), d = x.lastIndexOf(':');
            if (k <= 0 || d <= k) continue;
            try { m.put(x.substring(0, k), new long[]{Long.parseLong(x.substring(k + 1, d)), Long.parseLong(x.substring(d + 1))}); } catch (Exception ignored) {}
        }
        return m;
    }

    /** Las del historial que siguen instaladas, en orden: la más buscada primero (máximo 10). */
    private List<Item> historialBusqueda(List<Item> visibles) {
        List<Item> r = new ArrayList<>();
        for (String clave : leerHistorial().keySet()) {
            for (Item it : visibles) if (it.clave.equals(clave)) { r.add(it); break; }
            if (r.size() >= 10) break;
        }
        return r;
    }

    // ---------------- Deslizar hacia abajo: panel rápido ----------------

    private float yToque, xToque;
    private boolean decidido;

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (panel == null && busqueda == null) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: yToque = e.getRawY(); xToque = e.getRawX(); decidido = false; break;
                case MotionEvent.ACTION_MOVE:
                    float dy = e.getRawY() - yToque, dx = e.getRawX() - xToque;
                    if (!decidido && Math.abs(dy) > dp(90) && Math.abs(dy) > 2 * Math.abs(dx)) {
                        decidido = true;
                        MotionEvent c = MotionEvent.obtain(e);
                        c.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(c);
                        c.recycle();
                        if (dy > 0) abrirPanel(); else abrirBusqueda("");
                        return true;
                    }
                    break;
            }
            if (decidido) return true;
        }
        return super.dispatchTouchEvent(e);
    }

    /** Cambia cuánto tiempo se queda prendida la pantalla sin tocarla. */
    private void tiempoPantalla() {
        if (!Settings.System.canWrite(this)) {
            aviso("Active \"Permitir modificar ajustes\" para Mi Barra y vuelva a tocar");
            try { startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))); }
            catch (Exception ignored) {}
            return;
        }
        String[] nom = {"15 segundos", "30 segundos", "1 minuto", "2 minutos", "5 minutos", "10 minutos", "30 minutos"};
        int[] ms = {15000, 30000, 60000, 120000, 300000, 600000, 1800000};
        int actual = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 30000), sel = -1;
        for (int i = 0; i < ms.length; i++) if (ms[i] == actual) sel = i;
        new AlertDialog.Builder(this).setTitle("⏳ La pantalla se apaga después de…")
                .setSingleChoiceItems(nom, sel, (d, w) -> {
                    try {
                        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, ms[w]);
                        aviso("✔ Pantalla: " + nom[w]);
                    } catch (Exception e) { aviso("No se pudo cambiar"); }
                    d.dismiss();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void accionBarra(String a) {
        // Versión 18: el Inicio vive en otro proceso, así que se pregunta a Android si Mi Barra está activa
        boolean activo = false;
        try {
            String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            activo = s != null && s.contains(Puerta.CHPBAR + "/");
        } catch (Exception ignored) {}
        if (!activo) {
            aviso("Para esto, active Mi Barra en Accesibilidad (una sola vez)");
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception ignored) {}
            return;
        }
        sendBroadcast(new Intent(BarraService.AVISO_BARRA).setPackage(Puerta.CHPBAR).putExtra("accion", a));
    }

    private void abrirPanel() {
        cerrarCapas();
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(0xF2101628);
        int idEstado = getResources().getIdentifier("status_bar_height", "dimen", "android");
        l.setPadding(dp(14), (idEstado > 0 ? getResources().getDimensionPixelSize(idEstado) : 0) + dp(10), dp(14), dp(14));
        LinearLayout cab = new LinearLayout(this);
        cab.setGravity(Gravity.CENTER_VERTICAL);
        cab.addView(texto("⚡ Panel rápido", 22, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView x = texto("✕", 22, Color.WHITE, false);
        x.setPadding(dp(12), dp(4), dp(8), dp(4));
        x.setOnClickListener(v -> cerrarCapas());
        cab.addView(x);
        l.addView(cab);
        Object[][] base = {
                {"📸", "Pantallazo", (Runnable) () -> { cerrarCapas(); accionBarra("pantallazo"); }},
                {"🎥", "Grabar pantalla", (Runnable) () -> startActivity(new Intent(this, GrabarActivity.class))},
                {"📄", "Copiar a Word", (Runnable) () -> { cerrarCapas(); aviso("Abra la app que quiere copiar y use el botón redondo 🔴"); }},
                {"🔦", "Linterna", (Runnable) this::linterna},
                {"🌙", "Luz azul", (Runnable) () -> accionBarra("luz")},
                {"🔄", "Rotar pantalla", (Runnable) this::rotar},
                {"✈", "Modo avión", (Runnable) () -> ajuste(Settings.ACTION_AIRPLANE_MODE_SETTINGS)},
                {"📶", "Wi‑Fi", (Runnable) () -> ajuste(Settings.Panel.ACTION_WIFI)},
                {"📱", "Datos", (Runnable) () -> ajuste(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)},
                {"🔗", "Punto de acceso", (Runnable) this::puntoAcceso},
                // Versión 31: Silenciar (la campana) reemplaza «Vibrar / sonar» y a silenciar WhatsApp y llamadas por aparte
                {"🔕", "Silenciar", (Runnable) this::silenciar},
                {"🌙", "No molestar", (Runnable) this::noMolestar},
                {"🔒", "Bloquear", (Runnable) () -> { cerrarCapas(); accionBarra("bloquear"); }},
                {"📺", "Transmitir a TV", (Runnable) this::transmitir},
                {"🔳", "Escanear QR", (Runnable) () -> startActivity(new Intent(this, CodigoActivity.class))},
                {"🎮", "Control TV", (Runnable) () -> startActivity(new Intent(this, ControlTvActivity.class))},
                // Versión 31: la mini cámara «ojo de puerta» y la mini grabadora de un toque, también aquí
                {"👁", "Ojo de puerta", (Runnable) () -> { cerrarCapas(); startActivity(new Intent(this, MiniCamaraActivity.class)); }},
        };
        // Versión 31: el orden lo escoge él: deja el dedo sobre un botón y lo arrastra a otro puesto
        final List<Object[]> orden = new ArrayList<>();
        for (String k : prefs.getString("ordenPanel31", "").split("\\|"))
            for (Object[] o : base) if (o[1].equals(k) && !orden.contains(o)) orden.add(o);
        for (Object[] o : base) if (!orden.contains(o)) orden.add(o);
        final View[] celdas = new View[orden.size()];
        boolean silencio = false;
        try { silencio = getSystemService(android.media.AudioManager.class).getRingerMode() == android.media.AudioManager.RINGER_MODE_SILENT; } catch (Exception ignored) {}
        boolean grabandoYa = grabando();
        LinearLayout fila = null;
        for (int i = 0; i < orden.size(); i++) {
            if (i % 4 == 0) { fila = new LinearLayout(this); fila.setClipChildren(false); l.addView(fila, new LinearLayout.LayoutParams(-1, dp(98))); }
            Object[] b = orden.get(i);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView r = texto((String) b[0], 26, Color.WHITE, false);
            r.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            boolean prendido = ("Linterna".equals(b[1]) && linterna) || ("Silenciar".equals(b[1]) && silencio);
            g.setColor(prendido ? 0xFF1E88E5 : ("Grabar".equals(b[1]) && grabandoYa) ? 0xFFC62828 : 0x33FFFFFF);
            r.setBackground(g);
            c.addView(r, new LinearLayout.LayoutParams(dp(62), dp(62)));
            TextView t = texto((String) b[1], 12, Color.WHITE, false);
            t.setGravity(Gravity.CENTER);
            t.setMaxLines(2);
            c.addView(t, new LinearLayout.LayoutParams(-1, -2));
            Runnable acc = (Runnable) b[2];
            c.setOnClickListener(v -> acc.run());
            final int idx = i;
            celdas[i] = c;
            final float[] ini = new float[2];
            final boolean[] arrastra = {false};
            c.setOnLongClickListener(v -> {
                arrastra[0] = true;
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                v.setScaleX(1.15f); v.setScaleY(1.15f); v.setAlpha(0.85f);
                ((View) v.getParent()).bringToFront();
                v.bringToFront();
                try { ((android.os.Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(android.os.VibrationEffect.createOneShot(40, 120)); } catch (Exception ignored) {}
                return true;
            });
            c.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { ini[0] = e.getRawX(); ini[1] = e.getRawY(); return false; }
                if (!arrastra[0]) return false;
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    v.setTranslationX(e.getRawX() - ini[0]);
                    v.setTranslationY(e.getRawY() - ini[1]);
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    arrastra[0] = false;
                    int destino = -1;
                    int[] pos = new int[2];
                    for (int k = 0; k < celdas.length; k++) {
                        if (k == idx || celdas[k] == null) continue;
                        celdas[k].getLocationOnScreen(pos);
                        if (e.getRawX() >= pos[0] && e.getRawX() <= pos[0] + celdas[k].getWidth()
                                && e.getRawY() >= pos[1] && e.getRawY() <= pos[1] + celdas[k].getHeight()) { destino = k; break; }
                    }
                    v.setTranslationX(0); v.setTranslationY(0); v.setScaleX(1f); v.setScaleY(1f); v.setAlpha(1f);
                    if (destino >= 0) {
                        Object[] mov = orden.remove(idx);
                        orden.add(destino, mov);
                        StringBuilder sb = new StringBuilder();
                        for (Object[] x2 : orden) sb.append(sb.length() > 0 ? "|" : "").append(x2[1]);
                        prefs.edit().putString("ordenPanel31", sb.toString()).apply();
                        abrirPanel();
                    }
                    return true;
                }
                return true;
            });
            fila.addView(c, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        TextView ayOrden = texto("Deje el dedo en un botón y arrástrelo para cambiarlo de puesto", 11, 0xFFFFD54A, false);
        ayOrden.setGravity(Gravity.CENTER);
        l.addView(ayOrden);
        // Brillo y volumen
        l.addView(barra("☀ Brillo", brilloActual(), 255, v -> brillo(v)));
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        l.addView(barra("🔊 Volumen", am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC), max,
                v -> am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, v, 0)));
        TextView ay = texto("⬆ Deslice hacia arriba o toque ✕ para cerrar", 12, 0x99FFFFFF, false);
        ay.setGravity(Gravity.CENTER);
        ay.setPadding(0, dp(12), 0, 0);
        l.addView(ay);
        final float[] y0 = new float[1];
        l.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) y0[0] = e.getRawY();
            if (e.getActionMasked() == MotionEvent.ACTION_UP && y0[0] - e.getRawY() > dp(80)) cerrarCapas();
            return true;
        });
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        sv.setBackgroundColor(0xF2101628);
        panel = sv;
        raiz.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        sv.setTranslationY(-raiz.getHeight());
        sv.animate().translationY(0).setDuration(220).start();
    }

    interface AlCambiar { void cambio(int v); }

    private View barra(String nombre, int valor, int max, AlCambiar a) {
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(dp(14), 0, dp(8), 0);
        f.setBackground(vidrio(26));
        f.addView(texto(nombre, 15, Color.WHITE, false), new LinearLayout.LayoutParams(dp(110), -2));
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(valor);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean u) { if (u) a.cambio(v); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        f.addView(sb, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.topMargin = dp(10);
        f.setLayoutParams(lp);
        return f;
    }

    private void ajuste(String accion) {
        try { startActivity(new Intent(accion)); } catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {} }
    }

    private boolean puedeEscribirAjustes() {
        if (Settings.System.canWrite(this)) return true;
        aviso("Permita a Mi Barra «Modificar la configuración del sistema» y vuelva a tocar");
        try { startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
        return false;
    }

    private void rotar() {
        if (!puedeEscribirAjustes()) return;
        int v = Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, v == 1 ? 0 : 1);
        aviso(v == 1 ? "🔒 Pantalla fija (no gira)" : "🔄 La pantalla gira sola");
    }

    private int brilloActual() {
        try { return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS); } catch (Exception e) { return 128; }
    }

    private boolean pidioBrillo;

    private void brillo(int v) {
        if (!Settings.System.canWrite(this)) { if (!pidioBrillo) { pidioBrillo = true; puedeEscribirAjustes(); } return; }
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(5, v));
    }

    /** Versión 26: ver la pantalla del celular en el televisor o en el computador. */
    static void proyectar(Context c) {
        String[] ops = {"📺  En el televisor (Smart TV o Chromecast)", "💻  En el computador (Windows)"};
        new AlertDialog.Builder(c).setTitle("📡 Proyectar la pantalla").setItems(ops, (d, w) -> {
            if (w == 0) abrirTransmitir(c);
            else new AlertDialog.Builder(c).setTitle("💻 En el computador")
                    .setMessage("1. En el computador (Windows 10 u 11): Inicio → Configuración → Sistema → «Proyectar en este equipo» y actívelo.\n"
                            + "   (O abra la app «Conectar» / «Connect» del computador.)\n\n"
                            + "2. Celular y computador en el mismo Wi‑Fi.\n\n"
                            + "3. Toque «Seguir» y escoja el nombre del computador en la lista.")
                    .setPositiveButton("Seguir", (d2, w2) -> abrirTransmitir(c)).setNegativeButton("Cancelar", null).show();
        }).show();
    }

    static void abrirTransmitir(Context c) {
        String[] acciones = {android.provider.Settings.ACTION_CAST_SETTINGS, "android.settings.WIFI_DISPLAY_SETTINGS"};
        for (String a : acciones) {
            try { c.startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Exception ignored) {}
        }
        Toast.makeText(c, "Baje la cortina de arriba y toque «Transmitir» o «Duplicar pantalla»", Toast.LENGTH_LONG).show();
    }

    // ---------------- Versión 45: botón rojo de la grabadora ----------------
    // Un toque: empieza a grabar (arriba a la derecha sale el contador; un toque al contador lo para).
    // Toque sostenido: Escuchar la última, Recortar y Abrir grabaciones.

    private TextView botonRojo;
    private android.media.MediaPlayer mpUltima;

    private final Runnable vigiaRojo = new Runnable() {
        @Override public void run() { pintarRojo(); if (grabando()) h.postDelayed(this, 1000); }
    };

    private void pintarRojo() {
        if (botonRojo == null) return;
        boolean g = grabando();
        GradientDrawable o = new GradientDrawable();
        o.setShape(GradientDrawable.OVAL);
        o.setColor(0xFFE53935);
        o.setStroke(dp(g ? 4 : 2), g ? 0xFFFFD54F : Color.WHITE);
        botonRojo.setBackground(o);
        botonRojo.setText(g ? "●" : "⏺");
        botonRojo.setTextSize(g ? 16 : 22);
    }

    private void tocarRojo() {
        if (grabando()) { aviso("🎙 Ya está grabando · toque el contador de arriba para parar"); return; }
        if (!esChpInicio() && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 45);
            aviso("Dele permiso al micrófono y vuelva a tocar el botón rojo");
            return;
        }
        pararUltima();
        try { startForegroundService(new Intent(this, GrabadoraService.class)); }
        catch (Exception e) { aviso("No se pudo grabar"); return; }
        botonRojo.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction(() -> botonRojo.animate().scaleX(1f).scaleY(1f).setDuration(100));
        h.removeCallbacks(vigiaRojo);
        h.postDelayed(vigiaRojo, 600);
        if (esChpInicio()) { h.postDelayed(vigiaRojo, 1500); h.postDelayed(vigiaRojo, 3000); }
    }

    private void menuRojo() {
        boolean oyendo = esChpInicio() ? Puerta.pregunta(this, "oyendo") : mpUltima != null && mpUltima.isPlaying();
        String[] ops = {oyendo ? "⏹  Parar de escuchar" : "▶  Escuchar la última", "✂  Recortar", "📂  Abrir grabaciones"};
        new AlertDialog.Builder(this).setTitle("🎙 Grabadora").setItems(ops, (d, w) -> {
            if (w == 0) { if (oyendo) pararUltima(); else escucharUltima(); }
            else if (grabando()) aviso("Primero pare la grabación (toque el contador de arriba)");
            else if (w == 1) { pararUltima(); startActivity(new Intent(this, RecortarActivity.class)); }
            else { pararUltima(); startActivity(new Intent(this, GrabadoraActivity.class)); }
        }).show();
    }

    private void escucharUltima() {
        if (grabando()) { aviso("Primero pare la grabación"); return; }
        if (esChpInicio()) { abrirPuerta(Puerta.orden("escuchar")); aviso("▶ Escuchando la última · deje el dedo en el botón rojo para parar"); return; }
        String u = Grabadorita.ultima(this);
        if (u == null) { aviso("Todavía no hay grabaciones"); return; }
        pararUltima();
        try {
            mpUltima = new android.media.MediaPlayer();
            mpUltima.setDataSource(this, Uri.parse(u));
            mpUltima.setOnCompletionListener(m -> pararUltima());
            mpUltima.prepare();
            mpUltima.start();
            aviso("▶ Escuchando la última · deje el dedo en el botón rojo para parar");
        } catch (Exception e) { pararUltima(); aviso("No se pudo oír"); }
    }

    private void pararUltima() {
        if (esChpInicio()) { if (Puerta.pregunta(this, "oyendo")) abrirPuerta(Puerta.orden("parar_escuchar")); return; }
        if (mpUltima != null) { try { mpUltima.release(); } catch (Exception ignored) {} mpUltima = null; }
    }

    /** Un toque: empieza a grabar (sale la pastilla roja con el tiempo). Otro toque: para y guarda. */
    private void miniGrabadora() {
        if (grabando()) {
            startService(new Intent(this, GrabadoraService.class).setAction(GrabadoraService.PARAR));
            return;
        }
        if (!esChpInicio() && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 23);
            aviso("Dele permiso al micrófono y vuelva a tocar");
            return;
        }
        try { startForegroundService(new Intent(this, GrabadoraService.class)); aviso("🎙 Grabando… toque otra vez para parar"); }
        catch (Exception e) { aviso("No se pudo grabar"); }
    }

    private void linterna() {
        try {
            android.hardware.camera2.CameraManager cm = getSystemService(android.hardware.camera2.CameraManager.class);
            for (String id : cm.getCameraIdList()) {
                Boolean fl = cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(fl)) { linterna = !linterna; cm.setTorchMode(id, linterna); break; }
            }
            if (panel != null) abrirPanel();
        } catch (Exception e) { aviso("La linterna está ocupada"); }
    }

    private void puntoAcceso() {
        try { startActivity(new Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")); }
        catch (Exception e) { ajuste(Settings.ACTION_WIRELESS_SETTINGS); }
    }

    private void vibrar() {
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        try {
            boolean vib = am.getRingerMode() == android.media.AudioManager.RINGER_MODE_VIBRATE;
            am.setRingerMode(vib ? android.media.AudioManager.RINGER_MODE_NORMAL : android.media.AudioManager.RINGER_MODE_VIBRATE);
            aviso(vib ? "🔔 Con sonido" : "📳 Solo vibra");
        } catch (Exception e) { pedirNoMolestar(); }
    }

    /** Versión 31: 🔕 la campana: sin sonido para llamadas y WhatsApp. Otro toque y vuelve el sonido. Las alarmas sí suenan. */
    private void silenciar() {
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        try {
            boolean ya = am.getRingerMode() == android.media.AudioManager.RINGER_MODE_SILENT;
            am.setRingerMode(ya ? android.media.AudioManager.RINGER_MODE_NORMAL : android.media.AudioManager.RINGER_MODE_SILENT);
            aviso(ya ? "🔔 Con sonido" : "🔕 Silencio: llamadas y WhatsApp sin sonido (las alarmas sí suenan)");
            if (panel != null) abrirPanel();
        } catch (Exception e) { pedirNoMolestar(); }
    }

    private void pedirNoMolestar() {
        aviso("Permita a Mi Barra el acceso a «No molestar» y vuelva a tocar");
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); } catch (Exception ignored) {}
    }

    private void noMolestar() {
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) { pedirNoMolestar(); return; }
        boolean activo = nm.getCurrentInterruptionFilter() != android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
        nm.setInterruptionFilter(activo ? android.app.NotificationManager.INTERRUPTION_FILTER_ALL : android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        aviso(activo ? "🔔 No molestar: apagado" : "🌙 No molestar: prendido (las alarmas sí suenan)");
    }

    private void transmitir() {
        for (String a : new String[]{"android.settings.CAST_SETTINGS", "android.settings.WIFI_DISPLAY_SETTINGS"}) {
            try { startActivity(new Intent(a)); return; } catch (Exception ignored) {}
        }
        try { startActivity(new Intent().setClassName("com.milink.service", "com.xiaomi.mirror.settings.MirrorSettingsActivity")); return; } catch (Exception ignored) {}
        aviso("En Xiaomi: baje la cortina y use «Transmitir» o «Proyectar»");
    }

    // ---------------- Ajustes del Launcher ----------------

    private void ajustes() {
        List<String> ops = new ArrayList<>(Arrays.asList(
                "🏠 Poner Mi Barra como pantalla principal",
                "↩ Volver a poner lo que quité de la pantalla",
                "🖼 Fondo: " + (prefs.getBoolean("fondoFoto", false) ? "mi foto (toque para volver al de Mi Barra)" : "Mi Barra (toque para usar una foto mía)"),
                prefs.getBoolean("botonAyuda", true) ? "🔴 Ocultar el botón redondo de ayudas" : "🔴 Mostrar el botón redondo de ayudas",
                "⌨ Teclado Mi Barra",
                "♿ Activar Mi Barra en Accesibilidad (pantallazo, Word, grabar)",
                (new java.io.File(getFilesDir(), "reloj_bloqueo_no").exists() ? "🌙 Prender" : "🌙 Apagar") + " el reloj en la pantalla de bloqueo"));
        new AlertDialog.Builder(this).setTitle("⚙ Ajustes de Inicio").setItems(ops.toArray(new String[0]), (d, w) -> {
            switch (w) {
                case 0: ajuste(Settings.ACTION_HOME_SETTINGS); aviso("Escoja «Mi Barra Inicio»"); break;
                case 1: restaurar(); break;
                case 2:
                    if (prefs.getBoolean("fondoFoto", false)) { prefs.edit().putBoolean("fondoFoto", false).apply(); fondo.foto = null; fondo.invalidate(); }
                    else try { startActivityForResult(Intent.createChooser(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), "Escoja la foto de fondo"), 502); } catch (Exception ignored) {}
                    break;
                case 3: {
                    boolean v = !prefs.getBoolean("botonAyuda", true);
                    prefs.edit().putBoolean("botonAyuda", v).apply();
                    sendBroadcast(new Intent(BarraService.AVISO_BARRA).setPackage(Puerta.CHPBAR).putExtra("boton", v));
                    break;
                }
                case 4: startActivity(new Intent(this, TecladoAjustesActivity.class)); break;
                case 6: {
                    java.io.File f = new java.io.File(getFilesDir(), "reloj_bloqueo_no");
                    try { if (f.exists()) { f.delete(); aviso("🌙 El reloj saldrá en la pantalla de bloqueo"); } else { f.createNewFile(); aviso("🌙 Listo: ya no sale en la pantalla de bloqueo"); } } catch (Exception ignored) {}
                    break;
                }
                default: ajuste(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            }
        }).show();
    }

    private void restaurar() {
        Set<String> q = quitadas();
        if (q.isEmpty()) { aviso("No hay nada quitado"); return; }
        List<String> claves = new ArrayList<>(q);
        String[] n = new String[claves.size()];
        boolean[] marca = new boolean[claves.size()];
        for (int i = 0; i < n.length; i++) n[i] = porClave.containsKey(claves.get(i)) ? porClave.get(claves.get(i)).nombre : claves.get(i);
        new AlertDialog.Builder(this).setTitle("¿Cuáles vuelvo a poner?").setMultiChoiceItems(n, marca, (d, w, s) -> marca[w] = s)
                .setPositiveButton("Poner", (d, w) -> {
                    for (int i = 0; i < n.length; i++) if (marca[i]) q.remove(claves.get(i));
                    prefs.edit().putStringSet("quitadas", q).apply();
                    armar();
                }).setNegativeButton("Cancelar", null).show();
    }

    // ---------------- El fondo: azul profundo con ondas de luz (o una foto suya) ----------------

    final class Fondo extends View {
        Bitmap foto;
        private final Random r = new Random(7);
        Fondo(Context c) {
            super(c);
            if (prefs.getBoolean("fondoFoto", false)) cargarFoto();
        }
        void cargarFoto() {
            try {
                java.io.File f = new java.io.File(getFilesDir(), "fondo.jpg");
                if (f.exists()) foto = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
            } catch (Throwable ignored) {}
        }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), hh = getHeight();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (foto != null) {
                float esc = Math.max((float) w / foto.getWidth(), (float) hh / foto.getHeight());
                float fw = foto.getWidth() * esc, fh = foto.getHeight() * esc;
                c.drawBitmap(foto, null, new android.graphics.RectF((w - fw) / 2, (hh - fh) / 2, (w + fw) / 2, (hh + fh) / 2), new Paint(Paint.FILTER_BITMAP_FLAG));
                c.drawColor(0x55000000);
                return;
            }
            p.setShader(new LinearGradient(0, 0, w * 0.3f, hh, new int[]{0xFF141B3D, 0xFF0D1230, 0xFF070A1A}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, hh, p);
            p.setShader(new RadialGradient(w * 0.85f, hh * 0.12f, w * 0.9f, 0x664A5FC1, 0x004A5FC1, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, hh, p);
            p.setShader(new RadialGradient(w * 0.1f, hh * 0.85f, w * 0.8f, 0x4426A69A, 0x0026A69A, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, hh, p);
            // Ondas de luz, como seda
            int[][] col = {{0x553F51B5, 0x0000BCD4}, {0x44FFB74D, 0x00FF7043}, {0x335C6BC0, 0x00AB47BC}};
            for (int k = 0; k < 3; k++) {
                Path o = new Path();
                float base = hh * (0.45f + k * 0.12f);
                o.moveTo(0, base);
                o.cubicTo(w * 0.3f, base - hh * (0.18f - k * 0.04f), w * 0.6f, base + hh * 0.16f, w, base - hh * 0.06f);
                o.lineTo(w, base + hh * 0.05f);
                o.cubicTo(w * 0.6f, base + hh * 0.22f, w * 0.3f, base - hh * 0.08f, 0, base + hh * 0.08f);
                o.close();
                p.setShader(new LinearGradient(0, base - hh * 0.2f, w, base + hh * 0.2f, col[k][0], col[k][1], Shader.TileMode.CLAMP));
                c.drawPath(o, p);
            }
            p.setShader(null);
            p.setColor(Color.WHITE);
            for (int i = 0; i < 70; i++) {
                p.setAlpha(40 + r.nextInt(120));
                c.drawCircle(r.nextFloat() * w, r.nextFloat() * hh * 0.6f, dp(0.6f + r.nextFloat() * 1.2f), p);
            }
            r.setSeed(7);
        }
    }

}
