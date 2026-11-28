package co.jose.mibarra;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Control remoto para televisores Samsung (Tizen) y LG (webOS) por el Wi‑Fi de la casa.
 * Cruceta, volumen, canales, apps, y lo mejor: decir con la voz lo que quiere ver.
 */
public class ControlTvActivity extends Activity {

    private final Handler h = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private JSONArray tvs;
    private int actual;
    private TextView estado;
    private LinearLayout chips;

    // Conexión
    private Ws ws, puntero;
    private boolean esLg;
    private int idMsg;
    private boolean mudo;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void aviso(String s) { runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show()); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tv", MODE_PRIVATE);
        try { tvs = new JSONArray(prefs.getString("tvs", "[]")); } catch (Exception e) { tvs = new JSONArray(); }
        actual = prefs.getInt("actual", 0);
        getWindow().setStatusBarColor(0xFF101524);
        getWindow().setNavigationBarColor(0xFF070A14);
        pintar();
        if (tvs.length() == 0) h.postDelayed(this::buscar, 400); else conectar();
    }

    @Override protected void onDestroy() { desconectar(); super.onDestroy(); }

    private void guardarTvs() { prefs.edit().putString("tvs", tvs.toString()).putInt("actual", actual).apply(); }

    private JSONObject tv() { return tvs.optJSONObject(actual); }

    // ---------------- Pantalla ----------------

    private GradientDrawable forma(int color, float radio) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radio));
        return g;
    }

    private TextView boton(String t, float tam, int color, float radio, Runnable r) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(tam);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setBackground(forma(color, radio));
        v.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); r.run(); });
        return v;
    }

    private void pintar() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(10), dp(14), dp(20));
        GradientDrawable fondo = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1C2546, 0xFF0B0F1E, 0xFF070A14});
        // Arriba: televisores y apagar
        LinearLayout arriba = new LinearLayout(this);
        arriba.setGravity(Gravity.CENTER_VERTICAL);
        chips = new LinearLayout(this);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        arriba.addView(hs, new LinearLayout.LayoutParams(0, dp(52), 1f));
        TextView pw = boton("⏻", 26, 0xFFD32F2F, 28, () -> tecla("POWER"));
        arriba.addView(pw, new LinearLayout.LayoutParams(dp(56), dp(56)));
        l.addView(arriba);
        pintarChips();
        estado = new TextView(this);
        estado.setTextColor(0xFFB0BEC5);
        estado.setTextSize(13);
        estado.setPadding(dp(4), dp(4), 0, 0);
        l.addView(estado);

        // Voz
        TextView voz = boton("🎤  Diga qué quiere ver", 20, 0xFFE53935, 32, this::voz);
        voz.setTypeface(Typeface.DEFAULT_BOLD);
        voz.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFE53935, 0xFFFF7043}) {{ setCornerRadius(dp(32)); }});
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, dp(62));
        vp.topMargin = dp(12);
        l.addView(voz, vp);

        // Cruceta
        FrameLayout pad = new FrameLayout(this);
        GradientDrawable gp = new GradientDrawable();
        gp.setShape(GradientDrawable.OVAL);
        gp.setColor(0xFF1F2A48);
        gp.setStroke(dp(1), 0x33FFFFFF);
        pad.setBackground(gp);
        int t = dp(250);
        String[][] dir = {{"▲", "UP"}, {"▼", "DOWN"}, {"◀", "LEFT"}, {"▶", "RIGHT"}};
        int[][] pos = {{t / 2 - dp(32), dp(6)}, {t / 2 - dp(32), t - dp(70)}, {dp(6), t / 2 - dp(32)}, {t - dp(70), t / 2 - dp(32)}};
        for (int i = 0; i < 4; i++) {
            final String k = dir[i][1];
            TextView d = boton(dir[i][0], 30, 0x00000000, 32, () -> tecla(k));
            repetir(d, () -> tecla(k));
            FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(64), dp(64));
            fl.leftMargin = pos[i][0]; fl.topMargin = pos[i][1];
            pad.addView(d, fl);
        }
        TextView ok = boton("OK", 24, 0xFF3B4A78, 50, () -> tecla("ENTER"));
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams okp = new FrameLayout.LayoutParams(dp(100), dp(100));
        okp.leftMargin = t / 2 - dp(50); okp.topMargin = t / 2 - dp(50);
        pad.addView(ok, okp);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(t, t);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.topMargin = dp(18);
        l.addView(pad, pp);

        // Atrás, Inicio, Menú, Escribir
        LinearLayout f3 = new LinearLayout(this);
        String[][] b3 = {{"↩\nAtrás", "BACK"}, {"🏠\nInicio", "HOME"}, {"☰\nMenú", "MENU"}};
        for (String[] x : b3) f3.addView(boton(x[0], 16, 0x22FFFFFF, 18, () -> tecla(x[1])), peso(1, dp(64)));
        f3.addView(boton("⌨\nEscribir", 16, 0x22FFFFFF, 18, this::escribirTexto), peso(1, dp(64)));
        LinearLayout.LayoutParams f3p = new LinearLayout.LayoutParams(-1, -2);
        f3p.topMargin = dp(16);
        l.addView(f3, f3p);

        // Volumen, centro y canales
        LinearLayout f4 = new LinearLayout(this);
        f4.setGravity(Gravity.CENTER_VERTICAL);
        f4.addView(pastilla("＋", "VOL", "−", () -> tecla("VOLUP"), () -> tecla("VOLDOWN")), new LinearLayout.LayoutParams(dp(76), dp(160)));
        LinearLayout centro = new LinearLayout(this);
        centro.setOrientation(LinearLayout.VERTICAL);
        centro.addView(boton("🔇  Silencio", 15, 0x22FFFFFF, 16, () -> tecla("MUTE")), vertical46());
        centro.addView(boton("🔢  Números", 15, 0x22FFFFFF, 16, this::numeros), vertical46());
        centro.addView(boton("🎬  Entrada", 15, 0x22FFFFFF, 16, this::entrada), vertical46());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.setMargins(dp(12), 0, dp(12), 0);
        f4.addView(centro, cp);
        f4.addView(pastilla("▲", "CANAL", "▼", () -> tecla("CHUP"), () -> tecla("CHDOWN")), new LinearLayout.LayoutParams(dp(76), dp(160)));
        LinearLayout.LayoutParams f4p = new LinearLayout.LayoutParams(-1, -2);
        f4p.topMargin = dp(16);
        l.addView(f4, f4p);

        // Apps
        LinearLayout f5 = new LinearLayout(this);
        Object[][] apps = {{"YouTube", 0xFFFF0000, "youtube"}, {"NETFLIX", 0xFF000000, "netflix"}, {"prime", 0xFF00A8E1, "prime"}, {"Disney+", 0xFF113CCF, "disney"}};
        for (Object[] a : apps) {
            TextView v = boton((String) a[0], 15, (Integer) a[1], 12, () -> app((String) a[2], null));
            v.setTypeface(Typeface.DEFAULT_BOLD);
            if ("NETFLIX".equals(a[0])) v.setTextColor(0xFFE50914);
            f5.addView(v, peso(1, dp(46)));
        }
        LinearLayout.LayoutParams f5p = new LinearLayout.LayoutParams(-1, -2);
        f5p.topMargin = dp(18);
        l.addView(f5, f5p);

        ScrollView sv = new ScrollView(this);
        sv.setBackground(fondo);
        sv.addView(l);
        setContentView(sv);
    }

    private LinearLayout.LayoutParams peso(float w, int alto) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, alto, w);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        return p;
    }

    private LinearLayout.LayoutParams vertical46() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(46));
        p.setMargins(0, dp(4), 0, dp(4));
        return p;
    }

    private View pastilla(String arriba, String nombre, String abajo, Runnable sube, Runnable baja) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(forma(0x22FFFFFF, 38));
        TextView a = boton(arriba, 28, 0x00000000, 38, sube);
        repetir(a, sube);
        TextView n = new TextView(this);
        n.setText(nombre);
        n.setTextColor(0xAAFFFFFF);
        n.setTextSize(12);
        n.setGravity(Gravity.CENTER);
        TextView b = boton(abajo, 28, 0x00000000, 38, baja);
        repetir(b, baja);
        p.addView(a, new LinearLayout.LayoutParams(-1, 0, 1f));
        p.addView(n, new LinearLayout.LayoutParams(-1, -2));
        p.addView(b, new LinearLayout.LayoutParams(-1, 0, 1f));
        return p;
    }

    /** Dejando presionado repite (para subir volumen o recorrer menús rápido). */
    private void repetir(View v, Runnable r) {
        final Runnable[] rep = new Runnable[1];
        rep[0] = () -> { r.run(); h.postDelayed(rep[0], 180); };
        v.setOnLongClickListener(x -> { h.post(rep[0]); return true; });
        v.setOnTouchListener((x, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) h.removeCallbacks(rep[0]);
            return false;
        });
    }

    private void pintarChips() {
        chips.removeAllViews();
        for (int i = 0; i < tvs.length(); i++) {
            final int k = i;
            JSONObject o = tvs.optJSONObject(i);
            TextView c = boton("📺 " + o.optString("n"), 15, i == actual ? 0xFF1565C0 : 0x22FFFFFF, 22, () -> {
                actual = k; guardarTvs(); pintarChips(); conectar();
            });
            c.setPadding(dp(14), 0, dp(14), 0);
            c.setOnLongClickListener(v -> { opcionesTv(k); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
            lp.rightMargin = dp(6);
            chips.addView(c, lp);
        }
        TextView mas = boton("＋", 20, 0x22FFFFFF, 22, this::buscar);
        chips.addView(mas, new LinearLayout.LayoutParams(dp(52), dp(44)));
    }

    private void opcionesTv(int k) {
        JSONObject o = tvs.optJSONObject(k);
        new AlertDialog.Builder(this).setTitle(o.optString("n")).setItems(new String[]{"✎ Cambiar el nombre", "🗑 Quitar este televisor"}, (d, w) -> {
            if (w == 0) {
                EditText e = new EditText(this);
                e.setText(o.optString("n"));
                new AlertDialog.Builder(this).setTitle("Nombre del televisor").setView(e).setPositiveButton("Guardar", (d2, w2) -> {
                    try { o.put("n", e.getText().toString().trim()); } catch (Exception ignored) {}
                    guardarTvs(); pintarChips();
                }).show();
            } else {
                tvs.remove(k);
                actual = 0;
                guardarTvs(); pintarChips(); desconectar();
            }
        }).show();
    }

    private void ponerEstado(String s) { runOnUiThread(() -> { if (estado != null) estado.setText(s); }); }

    // ---------------- Buscar televisores en el Wi‑Fi ----------------

    private void buscar() {
        ponerEstado("🔎 Buscando televisores en el Wi‑Fi…");
        new Thread(() -> {
            Map<String, String[]> encontrados = new LinkedHashMap<>(); // ip → {marca, nombre}
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiManager.MulticastLock ml = wm.createMulticastLock("tv");
            try {
                ml.acquire();
                DatagramSocket s = new DatagramSocket();
                s.setSoTimeout(700);
                String[] st = {"urn:dial-multiscreen-org:service:dial:1", "urn:samsung.com:device:RemoteControlReceiver:1",
                        "urn:lge-com:service:webos-second-screen:1", "ssdp:all"};
                InetAddress grupo = InetAddress.getByName("239.255.255.250");
                for (int vez = 0; vez < 2; vez++) for (String x : st) {
                    String m = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: " + x + "\r\n\r\n";
                    byte[] d = m.getBytes(StandardCharsets.UTF_8);
                    s.send(new DatagramPacket(d, d.length, grupo, 1900));
                }
                long fin = System.currentTimeMillis() + 4000;
                byte[] buf = new byte[4096];
                while (System.currentTimeMillis() < fin) {
                    try {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        s.receive(p);
                        String r = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).toLowerCase();
                        String ip = p.getAddress().getHostAddress();
                        String marca = r.contains("samsung") ? "samsung" : (r.contains("webos") || r.contains("lge") || r.contains("lg electronics")) ? "lg" : null;
                        if (marca != null && !encontrados.containsKey(ip)) encontrados.put(ip, new String[]{marca, (marca.equals("lg") ? "LG" : "Samsung")});
                    } catch (java.net.SocketTimeoutException ignored) {}
                }
                s.close();
            } catch (Exception e) {
                ponerEstado("No pude buscar: " + e.getMessage());
            } finally { try { ml.release(); } catch (Exception ignored) {} }
            runOnUiThread(() -> resultadoBusqueda(encontrados));
        }).start();
    }

    private void resultadoBusqueda(Map<String, String[]> enc) {
        List<String> ops = new ArrayList<>();
        List<String[]> datos = new ArrayList<>();
        for (Map.Entry<String, String[]> e : enc.entrySet()) {
            ops.add("📺 " + e.getValue()[1] + "   (" + e.getKey() + ")");
            datos.add(new String[]{e.getValue()[0], e.getValue()[1], e.getKey()});
        }
        ops.add("✍ Escribir la dirección (IP) a mano");
        ponerEstado(enc.isEmpty() ? "No encontré televisores. ¿Están prendidos y en el mismo Wi‑Fi?" : "Encontré " + enc.size());
        new AlertDialog.Builder(this).setTitle(enc.isEmpty() ? "No encontré televisores prendidos" : "Televisores encontrados")
                .setItems(ops.toArray(new String[0]), (d, w) -> {
                    if (w < datos.size()) agregar(datos.get(w)[0], datos.get(w)[1], datos.get(w)[2]);
                    else aMano();
                }).setNegativeButton("Cerrar", null).show();
    }

    private void aMano() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(18), dp(8), dp(18), 0);
        EditText ip = new EditText(this);
        ip.setHint("Ej: 192.168.1.40");
        ip.setInputType(InputType.TYPE_CLASS_PHONE);
        f.addView(ip);
        TextView n = new TextView(this);
        n.setText("La dirección sale en el televisor: Menú → Red → Estado de la red.");
        f.addView(n);
        new AlertDialog.Builder(this).setTitle("¿Qué marca es?").setView(f)
                .setPositiveButton("Samsung", (d, w) -> agregar("samsung", "Samsung", ip.getText().toString().trim()))
                .setNeutralButton("LG", (d, w) -> agregar("lg", "LG", ip.getText().toString().trim()))
                .setNegativeButton("Cancelar", null).show();
    }

    private void agregar(String marca, String nombre, String ip) {
        if (ip.isEmpty()) return;
        try {
            for (int i = 0; i < tvs.length(); i++) if (ip.equals(tvs.getJSONObject(i).optString("ip"))) { actual = i; guardarTvs(); pintarChips(); conectar(); return; }
            JSONObject o = new JSONObject();
            o.put("n", nombre + (tvs.length() == 0 ? " sala" : " " + (tvs.length() + 1)));
            o.put("m", marca);
            o.put("ip", ip);
            tvs.put(o);
            actual = tvs.length() - 1;
            guardarTvs();
            pintarChips();
            conectar();
            aviso("Mire el televisor: si sale un aviso, acéptelo con el control normal (una sola vez)");
        } catch (Exception ignored) {}
    }

    // ---------------- Conectar ----------------

    private void desconectar() {
        if (ws != null) ws.cerrar();
        if (puntero != null) puntero.cerrar();
        ws = null; puntero = null;
    }

    private void conectar() {
        desconectar();
        JSONObject o = tv();
        if (o == null) { ponerEstado("Toque ＋ para buscar su televisor"); return; }
        esLg = "lg".equals(o.optString("m"));
        String ip = o.optString("ip");
        ponerEstado("Conectando con " + o.optString("n") + "…");
        new Thread(() -> {
            try {
                if (esLg) conectarLg(ip, o); else conectarSamsung(ip, o);
            } catch (Exception e) {
                ponerEstado("❌ No conecta con " + o.optString("n") + ". ¿Está prendido? (" + e.getMessage() + ")");
            }
        }).start();
    }

    private final Ws.Oyente oyente = new Ws.Oyente() {
        @Override public void mensaje(String t) { recibir(t); }
        @Override public void cerrado(String porque) { ponerEstado("Se desconectó. Toque el nombre del TV para volver a conectar."); }
    };

    private void conectarSamsung(String ip, JSONObject o) throws Exception {
        String nombre = android.util.Base64.encodeToString("Mi Barra".getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
        String tok = o.optString("tok");
        ws = new Ws(oyente);
        try {
            ws.conectar("wss://" + ip + ":8002/api/v2/channels/samsung.remote.control?name=" + nombre + (tok.isEmpty() ? "" : "&token=" + tok), 4000);
        } catch (Exception e) {
            ws = new Ws(oyente);
            ws.conectar("ws://" + ip + ":8001/api/v2/channels/samsung.remote.control?name=" + nombre, 4000);
        }
        ponerEstado("✅ Conectado con " + o.optString("n") + (tok.isEmpty() ? "  (si el TV pregunta, toque Permitir)" : ""));
    }

    private void conectarLg(String ip, JSONObject o) throws Exception {
        ws = new Ws(oyente);
        try { ws.conectar("ws://" + ip + ":3000", 4000); }
        catch (Exception e) { ws = new Ws(oyente); ws.conectar("wss://" + ip + ":3001", 4000); }
        JSONObject p = new JSONObject();
        p.put("forcePairing", false);
        p.put("pairingType", "PROMPT");
        if (!o.optString("tok").isEmpty()) p.put("client-key", o.optString("tok"));
        JSONObject man = new JSONObject();
        man.put("manifestVersion", 1);
        man.put("appVersion", "1.1");
        JSONArray perm = new JSONArray();
        for (String x : new String[]{"LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CLOSE", "TEST_OPEN", "TEST_PROTECTED", "CONTROL_AUDIO", "CONTROL_DISPLAY",
                "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_RECORDING", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_POWER",
                "READ_APP_STATUS", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST", "READ_NETWORK_STATE", "READ_RUNNING_APPS", "READ_TV_CHANNEL_LIST",
                "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE", "READ_COUNTRY_INFO", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "READ_INSTALLED_APPS"})
            perm.put(x);
        man.put("permissions", perm);
        p.put("manifest", man);
        JSONObject reg = new JSONObject();
        reg.put("type", "register");
        reg.put("id", "registro");
        reg.put("payload", p);
        ws.texto(reg.toString());
        ponerEstado("Conectando con " + o.optString("n") + "… si el TV pregunta, acepte con el control normal");
    }

    private void recibir(String t) {
        try {
            JSONObject m = new JSONObject(t);
            if (esLg) {
                if ("registered".equals(m.optString("type"))) {
                    String k = m.optJSONObject("payload") == null ? "" : m.optJSONObject("payload").optString("client-key");
                    if (!k.isEmpty()) { tv().put("tok", k); guardarTvs(); }
                    ponerEstado("✅ Conectado con " + tv().optString("n"));
                    pedirLg("ptr", "ssap://com.webos.service.networkinput/getPointerInputSocket", null);
                } else if ("ptr".equals(m.optString("id"))) {
                    String ruta = m.optJSONObject("payload").optString("socketPath");
                    new Thread(() -> {
                        try { puntero = new Ws(new Ws.Oyente() { public void mensaje(String x) {} public void cerrado(String x) {} }); puntero.conectar(ruta, 4000); }
                        catch (Exception e) { ponerEstado("La cruceta no conectó: " + e.getMessage()); }
                    }).start();
                } else if ("entradas".equals(m.optString("id"))) {
                    JSONArray d = m.optJSONObject("payload").optJSONArray("devices");
                    runOnUiThread(() -> elegirEntradaLg(d));
                } else if ("error".equals(m.optString("type"))) {
                    ponerEstado("El TV dijo: " + m.optString("error"));
                }
            } else {
                String ev = m.optString("event");
                if ("ms.channel.connect".equals(ev)) {
                    JSONObject d = m.optJSONObject("data");
                    if (d != null && !d.optString("token").isEmpty()) { tv().put("tok", d.optString("token")); guardarTvs(); }
                    ponerEstado("✅ Conectado con " + tv().optString("n"));
                } else if ("ms.channel.unauthorized".equals(ev)) {
                    ponerEstado("El TV no dio permiso. En el TV: Ajustes → General → Administrador de dispositivos externos → permitir Mi Barra");
                }
            }
        } catch (Exception ignored) {}
    }

    private void pedirLg(String id, String uri, JSONObject payload) {
        if (ws == null || !ws.abierto()) { ponerEstado("No está conectado. Toque el nombre del TV."); return; }
        try {
            JSONObject r = new JSONObject();
            r.put("type", "request");
            r.put("id", id == null ? "m" + (idMsg++) : id);
            r.put("uri", uri);
            if (payload != null) r.put("payload", payload);
            ws.texto(r.toString());
        } catch (Exception ignored) {}
    }

    // ---------------- Teclas ----------------

    private void tecla(String k) {
        if (ws == null || !ws.abierto()) { ponerEstado("No está conectado. Toque el nombre del TV o ＋."); return; }
        new Thread(() -> {
            try {
                if (esLg) {
                    switch (k) {
                        case "VOLUP": pedirLg(null, "ssap://audio/volumeUp", null); return;
                        case "VOLDOWN": pedirLg(null, "ssap://audio/volumeDown", null); return;
                        case "MUTE": mudo = !mudo; pedirLg(null, "ssap://audio/setMute", new JSONObject().put("mute", mudo)); return;
                        case "CHUP": pedirLg(null, "ssap://tv/channelUp", null); return;
                        case "CHDOWN": pedirLg(null, "ssap://tv/channelDown", null); return;
                        case "POWER": pedirLg(null, "ssap://system/turnOff", null); return;
                    }
                    if (puntero != null && puntero.abierto()) puntero.texto("type:button\nname:" + k + "\n\n");
                    else ponerEstado("La cruceta todavía está conectando…");
                } else {
                    String sk = "KEY_" + ("BACK".equals(k) ? "RETURN" : k);
                    JSONObject p = new JSONObject().put("Cmd", "Click").put("DataOfCmd", sk).put("Option", "false").put("TypeOfRemote", "SendRemoteKey");
                    ws.texto(new JSONObject().put("method", "ms.remote.control").put("params", p).toString());
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void numeros() {
        String[] n = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
        new AlertDialog.Builder(this).setTitle("Números").setItems(n, (d, w) -> tecla(n[w])).show();
    }

    private void entrada() {
        if (esLg) pedirLg("entradas", "ssap://tv/getExternalInputList", null);
        else tecla("SOURCE");
    }

    private void elegirEntradaLg(JSONArray d) {
        if (d == null) return;
        String[] n = new String[d.length()];
        for (int i = 0; i < d.length(); i++) n[i] = d.optJSONObject(i).optString("label", d.optJSONObject(i).optString("id"));
        new AlertDialog.Builder(this).setTitle("Entrada").setItems(n, (x, w) -> {
            try { pedirLg(null, "ssap://tv/switchInput", new JSONObject().put("inputId", d.optJSONObject(w).optString("id"))); } catch (Exception ignored) {}
        }).show();
    }

    // ---------------- Apps y voz ----------------

    private void app(String cual, String buscar) {
        if (ws == null || !ws.abierto()) { ponerEstado("No está conectado."); return; }
        new Thread(() -> {
            try {
                if (esLg) {
                    String id = "youtube".equals(cual) ? "youtube.leanback.v4" : "netflix".equals(cual) ? "netflix"
                            : "prime".equals(cual) ? "amazon" : "com.disney.disneyplus-prod";
                    JSONObject p = new JSONObject().put("id", id);
                    if (buscar != null && "youtube".equals(cual))
                        p.put("params", new JSONObject().put("contentTarget", "https://www.youtube.com/tv?#/search?q=" + URLEncoder.encode(buscar, "UTF-8")));
                    pedirLg(null, "ssap://system.launcher/launch", p);
                } else {
                    String id = "youtube".equals(cual) ? "111299001912" : "netflix".equals(cual) ? "3201907018807"
                            : "prime".equals(cual) ? "3201910019365" : "3201901017640";
                    JSONObject d = new JSONObject().put("appId", id).put("action_type", "youtube".equals(cual) ? "DEEP_LINK" : "NATIVE_LAUNCH");
                    if (buscar != null) d.put("metaTag", "search_query=" + URLEncoder.encode(buscar, "UTF-8"));
                    ws.texto(new JSONObject().put("method", "ms.channel.emit")
                            .put("params", new JSONObject().put("event", "ed.apps.launch").put("to", "host").put("data", d)).toString());
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private static final int VOZ = 61;

    private void voz() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué quiere ver?");
        try { startActivityForResult(i, VOZ); } catch (Exception e) { escribirTexto(); }
    }

    @Override
    protected void onActivityResult(int r, int res, Intent d) {
        super.onActivityResult(r, res, d);
        if (r != VOZ || res != RESULT_OK || d == null) return;
        ArrayList<String> rs = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (rs == null || rs.isEmpty()) return;
        String q = rs.get(0);
        new AlertDialog.Builder(this).setTitle("🎤 «" + q + "»")
                .setItems(new String[]{"▶ Buscarlo en YouTube", "⌨ Escribirlo en la búsqueda que está abierta en el TV"}, (x, w) -> {
                    if (w == 0) { app("youtube", q); ponerEstado("Buscando «" + q + "» en YouTube… si no aparece, abra la lupa y use ⌨ Escribir"); }
                    else mandarTexto(q);
                }).show();
    }

    private void escribirTexto() {
        EditText e = new EditText(this);
        e.setHint("Escriba o hable con el 🎤 del teclado");
        new AlertDialog.Builder(this).setTitle("⌨ Escribir en el TV")
                .setMessage("Primero abra en el TV la lupa o el cuadro de búsqueda.")
                .setView(e).setPositiveButton("Mandar", (d, w) -> mandarTexto(e.getText().toString())).setNegativeButton("Cancelar", null).show();
    }

    private void mandarTexto(String t) {
        if (t.trim().isEmpty() || ws == null || !ws.abierto()) { ponerEstado("No está conectado."); return; }
        new Thread(() -> {
            try {
                if (esLg) {
                    pedirLg(null, "ssap://com.webos.service.ime/insertText", new JSONObject().put("text", t).put("replace", 0));
                    Thread.sleep(300);
                    pedirLg(null, "ssap://com.webos.service.ime/sendEnterKey", null);
                } else {
                    String b64 = android.util.Base64.encodeToString(t.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
                    ws.texto(new JSONObject().put("method", "ms.remote.control").put("params",
                            new JSONObject().put("Cmd", b64).put("DataOfCmd", "base64").put("TypeOfRemote", "SendInputString")).toString());
                    Thread.sleep(300);
                    ws.texto(new JSONObject().put("method", "ms.remote.control").put("params", new JSONObject().put("TypeOfRemote", "SendInputEnd")).toString());
                }
                ponerEstado("✅ Escrito en el TV: «" + t + "»");
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    protected void onPostCreate(android.os.Bundle b) {
        super.onPostCreate(b);
        Util.ponerX(this);   // v44: ✕ para cerrar, arriba a la derecha
    }
}
