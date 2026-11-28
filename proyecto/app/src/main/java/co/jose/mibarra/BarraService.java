package co.jose.mibarra;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** La barrita lateral izquierda con todos los atajos. */
public class BarraService extends AccessibilityService {

    private WindowManager wm;
    private Handler h;
    private SharedPreferences prefs;
    private View pestana, pestanaDer, panel, filtro, velo;
    private int veloNivel;
    private TextView botonDetener;

    // Copiar a Word
    private boolean copiando, bajando;
    private List<String> acumulado = new ArrayList<>();
    private int sinNuevas, vueltas;
    private String origen = "";
    private final Runnable pasoR = this::paso;

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        h = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("barra_servicio", MODE_PRIVATE);
        // Versión 48: la luz azul (pantalla amarilla) nunca se prende sola al arrancar; solo cuando él la toca
        prefs.edit().putInt("luz", 0).apply();
        aplicarFiltro(0);
        instancia = this;
        // Versión 34: el radio reloj (que corre aparte) avisa por aquí que esconda o muestre el botón flotante
        try {
            android.content.IntentFilter fr = new android.content.IntentFilter(ACCION_RELOJ);
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(avisoReloj, fr, android.content.Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(avisoReloj, fr);
        } catch (Exception ignored) {}
        if (prefs.getBoolean("barraVisible", false)) pestana = crearPestana(false);
        // Versión 18: el botón pasa abajo a la izquierda (una sola vez; después queda donde él lo deje)
        if (!prefs.getBoolean("v18abajo", false)) {
            prefs.edit().remove("botonX").remove("botonY").putBoolean("v18abajo", true).putBoolean("botonAyuda", true).apply();
        }
        if (prefs.getBoolean("botonAyuda", true)) crearBotonAyuda();
        try {
            android.content.IntentFilter f = new android.content.IntentFilter(AVISO_BARRA);
            // Versión 46: ChpInicio (otra app, firmada igual) también le habla al botón
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(receptor, f, "co.jose.mibarra.PUERTA", null, RECEIVER_EXPORTED);
            else registerReceiver(receptor, f, "co.jose.mibarra.PUERTA", null);
        } catch (Exception ignored) {}
        mantenerViva();
        // El reloj de noche sale en la pantalla de bloqueo al prender la pantalla, solo de 12 a 6 de la mañana
        try { registerReceiver(pantallaPrendida, new android.content.IntentFilter(Intent.ACTION_SCREEN_ON)); } catch (Exception ignored) {}
    }

    private final android.content.BroadcastReceiver pantallaPrendida = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            if (new java.io.File(getFilesDir(), "reloj_bloqueo_no").exists()) return;
            // Versión 19: solo de noche (de 12 de la noche a 5:59 de la mañana); de día no estorba
            int hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (hora >= 6) return;
            android.app.KeyguardManager km = getSystemService(android.app.KeyguardManager.class);
            if (km == null || !km.isKeyguardLocked()) return;
            try {
                startActivity(new Intent(BarraService.this, NocheActivity.class).putExtra("bloqueo", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
            } catch (Exception ignored) {}
        }
    };

    /** Un aviso fijo en notificaciones hace que Xiaomi y otros no maten la barra. */
    private void mantenerViva() {
        try {
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            nm.createNotificationChannel(new android.app.NotificationChannel("barra", "ChpBar activa",
                    android.app.NotificationManager.IMPORTANCE_MIN));
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    android.app.PendingIntent.FLAG_IMMUTABLE);
            android.app.Notification n = new android.app.Notification.Builder(this, "barra")
                    .setContentTitle("ChpBar está activa")
                    .setContentText("Deslice desde el borde izquierdo o derecho")
                    .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build();
            if (android.os.Build.VERSION.SDK_INT >= 34)
                startForeground(21, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else
                startForeground(21, n);
        } catch (Throwable ignored) {
            // Si el celular no lo permite, la barra sigue funcionando igual
        }
    }

    static final String AVISO_BARRA = "co.jose.mibarra.BARRA";
    static volatile BarraService instancia;

    /** El Launcher (en otro proceso) le pide mostrar u ocultar la barrita, o una acción rápida. */
    private final android.content.BroadcastReceiver receptor = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            if (i.hasExtra("visible")) {
                boolean v = i.getBooleanExtra("visible", true);
                prefs.edit().putBoolean("barraVisible", v).apply();
                if (v && pestana == null) pestana = crearPestana(false);
                if (pestana != null) pestana.setVisibility(v ? View.VISIBLE : View.GONE);
            }
            if (i.hasExtra("boton")) {
                boolean v = i.getBooleanExtra("boton", true);
                prefs.edit().putBoolean("botonAyuda", v).apply();
                if (v && botonAyuda != null && lpBoton != null) {
                    // Versión 18: si estaba perdido, vuelve abajo a la izquierda y visible
                    escondido = false;
                    prefs.edit().remove("botonX").remove("botonY").apply();
                    lpBoton.x = Util.dp(BarraService.this, 6);
                    lpBoton.y = yAbajo();
                    int t = Util.dp(BarraService.this, prefs.getInt("botonTam", 50));
                    lpBoton.width = t; lpBoton.height = t;
                    try { wm.updateViewLayout(botonAyuda, lpBoton); } catch (Exception ignored) {}
                    botonAyuda.setVisibility(View.VISIBLE);
                    pintarBotonAyuda();
                } else if (v) crearBotonAyuda(); else { quitar(botonAyuda); botonAyuda = null; quitar(menuAyuda); menuAyuda = null; }
            }
            if (i.hasExtra("ocultarTemporal") && botonAyuda != null) {
                boolean o = i.getBooleanExtra("ocultarTemporal", false);
                botonAyuda.setVisibility(o ? View.GONE : View.VISIBLE);
                if (o) { quitar(menuAyuda); menuAyuda = null; }
            }
            if (i.hasExtra("grabEstado")) {
                estGrab = i.getIntExtra("grabEstado", 0);
                segGrab = i.getLongExtra("grabSeg", 0);
                pintarPastilla();
            }
            String a = i.getStringExtra("accion");
            if (a != null) accionRapida(a);
            String pg = i.getStringExtra("pegar");
            if (pg != null) h.postDelayed(() -> pegarEnChat(pg, 0), 900);
        }
    };

    /** Botones de la cortina de arriba y del Launcher: primero se quita la cortina y luego se hace. */
    void accionRapida(String a) {
        if (android.os.Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
        else try { sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)); } catch (Exception ignored) {}
        h.postDelayed(() -> {
            switch (a) {
                case "pantallazo": pantallazo(); break;
                case "word": copiar(true); break;
                case "wordArriba": copiar(false); break;
                case "imprimir": imprimirPantalla(); break;
                case "imprimirLargo": imprimirLargo(); break;
                case "luz": {
                    int actual = prefs.getInt("luz", 0);
                    int nuevo = actual > 0 ? 0 : Math.max(30, prefs.getInt("luzUltima", 40));
                    if (actual > 0) prefs.edit().putInt("luzUltima", actual).apply();
                    prefs.edit().putInt("luz", nuevo).apply();
                    aplicarFiltro(nuevo);
                    break;
                }
                case "menu": abrirMenuAyuda(); break;
                case "luzApagar": prefs.edit().putInt("luz", 0).apply(); aplicarFiltro(0); break;   // versión 48
                case "cerrarApps": cerrarApps(); break;
                case "bloquear": if (android.os.Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break;
                case "poder": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break;
            }
        }, 700);
    }

    // ---------------- Botón redondo de ayudas (arriba a la izquierda) ----------------
    // Un toque: pantallazo, copiar a Word, calculadora, grabar pantalla.
    // Mientras graba la pantalla, el mismo botón se vuelve ⏹ rojo: un toque y para.
    // Dejándolo presionado se puede correr a otro lado.

    private TextView botonAyuda;
    private View menuAyuda;
    private WindowManager.LayoutParams lpBoton;
    private android.animation.ObjectAnimator latido;

    private void crearBotonAyuda() {
        if (botonAyuda != null) return;
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        int tam = Util.dp(this, prefs.getInt("botonTam", 50));
        lpBoton = lp(tam, tam,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        lpBoton.gravity = Gravity.TOP | Gravity.START;
        lpBoton.x = prefs.getInt("botonX", Util.dp(this, 6));
        lpBoton.y = prefs.getInt("botonY", yAbajo());
        final float[] t0 = new float[4];
        final boolean[] mov = new boolean[1];
        t.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    t0[0] = e.getRawX(); t0[1] = e.getRawY(); t0[2] = lpBoton.x; t0[3] = lpBoton.y; mov[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - t0[0], dy = e.getRawY() - t0[1];
                    if (!mov[0] && !prefs.getBoolean("botonFijo", false) && Math.hypot(dx, dy) > Util.dp(this, 12) && e.getEventTime() - e.getDownTime() > 350) mov[0] = true;
                    if (escondido && dx > Util.dp(this, 20)) { esconderBoton(false); return true; }
                    if (mov[0]) {
                        lpBoton.x = (int) (t0[2] + dx); lpBoton.y = (int) Math.max(0, t0[3] + dy);
                        try { wm.updateViewLayout(v, lpBoton); } catch (Exception ignored) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (mov[0]) prefs.edit().putInt("botonX", lpBoton.x).putInt("botonY", lpBoton.y).apply();
                    else if (Math.hypot(e.getRawX() - t0[0], e.getRawY() - t0[1]) < Util.dp(this, 12)) {
                        if (escondido) esconderBoton(false);
                        else if (GrabarService.grabando) startService(new Intent(this, GrabarService.class).setAction(GrabarService.PARAR));
                        else if (copiando) terminarCopia();
                        else abrirMenuAyuda();
                    }
                    return true;
            }
            return false;
        });
        try { wm.addView(t, lpBoton); botonAyuda = t; } catch (Exception ignored) {}
        pintarBotonAyuda();
    }

    private void pintarBotonAyuda() {
        if (botonAyuda == null) return;
        boolean graba = GrabarService.grabando;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                graba ? new int[]{0xFFFF1744, 0xFFB71C1C} : new int[]{0xFFFF9800, 0xFFE91E63});
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(Util.dp(this, 2), Color.WHITE);
        botonAyuda.setBackground(g);
        botonAyuda.setText(graba ? "⏹" : filtro != null ? "🌙" : "✦");   // versión 48: 🌙 = luz azul prendida
        botonAyuda.setTextSize(graba ? 22 : 24);
        botonAyuda.setAlpha(escondido ? 0.85f : prefs.getInt("botonOpaco", 92) / 100f);
        // Versión 18: mientras graba la pantalla, el botón no sale en el video (se para desde la barra de arriba)
        botonAyuda.setVisibility(graba && !prefs.getBoolean("botonEnVideo", false) ? View.INVISIBLE : View.VISIBLE);
        if (escondido && !graba) {
            botonAyuda.setText("");
            GradientDrawable r = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFF9800, 0xFFE91E63});
            r.setCornerRadius(Util.dp(this, 5));
            botonAyuda.setBackground(r);
        } else if (!graba) {
            botonAyuda.setTextSize(prefs.getInt("botonTam", 50) * 0.48f);
        }
        if (latido != null) { latido.cancel(); latido = null; botonAyuda.setScaleX(1f); botonAyuda.setScaleY(1f); }
        if (graba) {
            latido = android.animation.ObjectAnimator.ofPropertyValuesHolder(botonAyuda,
                    android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.15f),
                    android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.15f));
            latido.setDuration(600);
            latido.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            latido.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            latido.start();
        }
    }

    /** Versión 17: menú compacto (cabe completo en el Motorola). Los ajustes del botón van aparte en ⚙ Botón. */
    /** Versión 23: cada toque cambia: automático → vertical fija → horizontal fija → automático. */
    private void girarPantalla() {
        if (!android.provider.Settings.System.canWrite(this)) {
            android.widget.Toast.makeText(this, "Active «Permitir modificar ajustes» para Mi Barra y vuelva a tocar", android.widget.Toast.LENGTH_LONG).show();
            abrir(new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + getPackageName())));
            return;
        }
        android.content.ContentResolver cr = getContentResolver();
        int auto = android.provider.Settings.System.getInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION, 1);
        int rot = android.provider.Settings.System.getInt(cr, android.provider.Settings.System.USER_ROTATION, 0);
        String msg;
        try {
            if (auto == 1) {
                android.provider.Settings.System.putInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION, 0);
                android.provider.Settings.System.putInt(cr, android.provider.Settings.System.USER_ROTATION, android.view.Surface.ROTATION_0);
                msg = "📱 Vertical fija";
            } else if (rot == android.view.Surface.ROTATION_0) {
                android.provider.Settings.System.putInt(cr, android.provider.Settings.System.USER_ROTATION, android.view.Surface.ROTATION_90);
                msg = "📺 Horizontal fija (toque otra vez para volver a automático)";
            } else {
                android.provider.Settings.System.putInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION, 1);
                msg = "🔄 La pantalla gira sola";
            }
        } catch (Exception e) { msg = "No se pudo girar"; }
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    /** Versión 29: abre «Escribir bien / Mi Voz»; si estaba en WhatsApp, al enviar se pega solo en ese chat. */
    private void abrirEscribir() {
        boolean wa = false;
        try {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            wa = r != null && r.getPackageName() != null && r.getPackageName().toString().startsWith("com.whatsapp");
        } catch (Exception ignored) {}
        abrir(new Intent(this, EscribirActivity.class).putExtra("desdeWa", wa));
    }

    /** Pega el escrito en la casilla del chat abierto (WhatsApp). Si no la encuentra, queda copiado para pegarlo a mano. */
    private void pegarEnChat(String texto, int intento) {
        try {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            AccessibilityNodeInfo campo = r == null ? null : r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if ((campo == null || !campo.isEditable()) && r != null) {
                java.util.List<AccessibilityNodeInfo> l = r.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry");
                if (l == null || l.isEmpty()) l = r.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/entry");
                campo = l != null && !l.isEmpty() ? l.get(0) : null;
            }
            if (campo == null) {
                if (intento < 3) { h.postDelayed(() -> pegarEnChat(texto, intento + 1), 600); return; }
                aviso("📋 Quedó copiado: deje el dedo en la casilla y toque Pegar");
                return;
            }
            CharSequence ya = campo.getText();
            String antes = ya == null || campo.isShowingHintText() ? "" : ya.toString();
            android.os.Bundle b = new android.os.Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, antes.isEmpty() ? texto : antes + " " + texto);
            if (!campo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) {
                campo.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                campo.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            }
        } catch (Exception e) {
            aviso("📋 Quedó copiado: deje el dedo en la casilla y toque Pegar");
        }
    }

    // ---------------- Versión 38: ✨ Organizar (Gemini arregla lo dictado sin salir de WhatsApp) ----------------

    private View panelOrganizar;
    private String appOrganizar = "";

    /** Busca la casilla donde está escribiendo (WhatsApp u otra app). */
    private AccessibilityNodeInfo casillaActual() {
        try {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r == null) return null;
            AccessibilityNodeInfo campo = r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (campo != null && campo.isEditable()) return campo;
            java.util.List<AccessibilityNodeInfo> l = r.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry");
            if (l == null || l.isEmpty()) l = r.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/entry");
            return l != null && !l.isEmpty() ? l.get(0) : null;
        } catch (Exception e) { return null; }
    }

    private void organizarCasilla() {
        if (!Gemini.hayLlave(this)) {
            aviso("🔑 Primero pegue la llave gratis de Gemini (una sola vez)");
            abrir(new Intent(this, LlaveActivity.class));
            return;
        }
        AccessibilityNodeInfo campo = casillaActual();
        CharSequence ya = campo == null ? null : campo.getText();
        String texto = ya == null || campo.isShowingHintText() ? "" : ya.toString().trim();
        if (texto.isEmpty()) { aviso("✨ Primero dicte o escriba el mensaje en la casilla del chat, y luego toque Organizar"); return; }
        String paq = "";
        try { CharSequence pq = campo.getPackageName(); paq = pq == null ? "" : pq.toString(); } catch (Exception ignored) {}
        appOrganizar = paq;
        // Versión 41: se organiza en una pantallita «de verdad» (el Xiaomi le cortaba el internet a la ventana flotante)
        abrir(new Intent(this, OrganizarActivity.class).putExtra("texto", texto).putExtra("wa", paq.startsWith("com.whatsapp")));
    }

    /** Versión 41: lo llama la ventanita de Organizar al tocar «Poner en WhatsApp». */
    void ponerOrganizado(String texto) {
        h.postDelayed(() -> ponerEnCasilla(texto, 0), 700);
    }

    private void mostrarOrganizar(String original, boolean esWa, boolean otraVez) {
        quitar(panelOrganizar);
        LinearLayout l = panelMenu();
        int pad = Util.dp(this, 14);
        l.setPadding(pad, pad, pad, pad);

        TextView tit = new TextView(this);
        tit.setText("✨ Organizar");
        tit.setTextSize(20);
        tit.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tit.setTextColor(0xFF3C3489);
        l.addView(tit);

        TextView antes = new TextView(this);
        antes.setText("Antes: " + (original.length() > 140 ? original.substring(0, 140) + "…" : original));
        antes.setTextSize(14);
        antes.setTextColor(0xFF616161);
        antes.setPaintFlags(antes.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        antes.setPadding(0, Util.dp(this, 6), 0, Util.dp(this, 8));
        l.addView(antes);

        TextView nuevo = new TextView(this);
        nuevo.setText("Organizando con Gemini… ⏳");
        nuevo.setTextSize(18);
        nuevo.setTextColor(Color.BLACK);
        nuevo.setLineSpacing(0, 1.1f);
        nuevo.setPadding(0, 0, 0, Util.dp(this, 10));
        l.addView(nuevo);

        final String[] listo = {null};
        TextView poner = botonOrganizar(esWa ? "✔ Poner en WhatsApp" : "✔ Poner en la casilla", 0xFF1B8E3E, 18);
        poner.setAlpha(0.45f);
        poner.setOnClickListener(v -> {
            if (listo[0] == null) return;
            quitar(panelOrganizar); panelOrganizar = null;
            ponerEnCasilla(listo[0], 0);
        });
        l.addView(poner, new LinearLayout.LayoutParams(-1, Util.dp(this, 56)));

        LinearLayout fila = new LinearLayout(this);
        TextView otra = botonOrganizar("↻ Otra vez", 0xFF534AB7, 16);
        otra.setOnClickListener(v -> mostrarOrganizar(original, esWa, true));
        TextView cancelar = botonOrganizar("✖ Cancelar", 0xFF616161, 16);
        cancelar.setOnClickListener(v -> { quitar(panelOrganizar); panelOrganizar = null; });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Util.dp(this, 50), 1f);
        a.setMargins(0, Util.dp(this, 8), Util.dp(this, 4), 0);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, Util.dp(this, 50), 1f);
        b.setMargins(Util.dp(this, 4), Util.dp(this, 8), 0, 0);
        fila.addView(otra, a);
        fila.addView(cancelar, b);
        l.addView(fila);

        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(Util.dp(this, 2), 0xFF534AB7);
        g.setCornerRadius(Util.dp(this, 16));
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int tope = dm.heightPixels * 3 / 4;
        ScrollView sv = new ScrollView(this) {
            @Override protected void onMeasure(int w, int hm) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(tope, MeasureSpec.AT_MOST)); }
        };
        sv.setBackground(g);
        sv.addView(l);
        WindowManager.LayoutParams p = lp(dm.widthPixels - Util.dp(this, 20), ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = Util.dp(this, 70);
        try { wm.addView(sv, p); panelOrganizar = sv; } catch (Exception e) { aviso("No se pudo abrir Organizar"); return; }

        final View este = sv;
        // Versión 40: el contador de segundos mientras Gemini contesta
        final boolean[] termino = {false};
        final long inicio = System.currentTimeMillis();
        Runnable reloj = new Runnable() {
            @Override public void run() {
                if (termino[0] || panelOrganizar != este) return;
                long sg = (System.currentTimeMillis() - inicio) / 1000;
                nuevo.setText("Organizando con Gemini… " + sg + " s ⏳");
                h.postDelayed(this, 1000);
            }
        };
        h.post(reloj);
        Gemini.organizar(this, original, otraVez, new Gemini.Respuesta() {
            @Override public void listo(String t) {
                h.post(() -> {
                    termino[0] = true;
                    if (panelOrganizar != este) return;
                    listo[0] = t;
                    nuevo.setText(t);
                    poner.setAlpha(1f);
                });
            }
            @Override public void error(String m) {
                h.post(() -> {
                    termino[0] = true;
                    if (panelOrganizar != este) return;
                    nuevo.setText(m);
                    nuevo.setTextColor(0xFFB71C1C);
                    if (m.contains("llave")) {
                        otra.setText("🔑 Llave");
                        otra.setOnClickListener(v -> { quitar(panelOrganizar); panelOrganizar = null; abrir(new Intent(BarraService.this, LlaveActivity.class)); });
                    }
                });
            }
        });
    }

    private TextView botonOrganizar(String texto, int color, int tam) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(tam);
        t.setTextColor(Color.WHITE);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(Util.dp(this, 10));
        t.setBackground(g);
        return t;
    }

    /** Reemplaza lo dictado por lo organizado (no lo suma). Si no puede, lo deja copiado para pegar a mano. */
    private void ponerEnCasilla(String texto, int intento) {
        try {
            AccessibilityNodeInfo campo = casillaActual();
            if (campo == null) {
                if (intento < 3) { h.postDelayed(() -> ponerEnCasilla(texto, intento + 1), 400); return; }
                copiarAlPortapapeles(texto);
                aviso("📋 Quedó copiado: deje el dedo en la casilla y toque Pegar");
                return;
            }
            android.os.Bundle b = new android.os.Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto);
            if (!campo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) {
                copiarAlPortapapeles(texto);
                aviso("📋 Quedó copiado: deje el dedo en la casilla y toque Pegar");
            }
        } catch (Exception e) {
            copiarAlPortapapeles(texto);
            aviso("📋 Quedó copiado: deje el dedo en la casilla y toque Pegar");
        }
    }

    private void copiarAlPortapapeles(String texto) {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Organizado", texto));
        } catch (Exception ignored) {}
    }

    private void abrirMenuAyuda() {
        if (menuAyuda != null) { quitar(menuAyuda); menuAyuda = null; return; }
        LinearLayout l = panelMenu();
        // Versión 43: el número de versión siempre a la vista
        TextView ver = new TextView(this);
        ver.setText("ChpBar v" + Util.version(this));
        ver.setTextColor(0xFFFFD54A);
        ver.setTextSize(13);
        ver.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        ver.setGravity(Gravity.END);
        l.addView(ver);
        // Versión 32: Silenciar, No molestar, Ojo de puerta y Grabar van de primeros en el botón flotante
        android.media.AudioManager amR = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        boolean enSilencio = amR != null && amR.getRingerMode() == android.media.AudioManager.RINGER_MODE_SILENT;
        android.app.NotificationManager nmR = getSystemService(android.app.NotificationManager.class);
        boolean dndActivo = nmR != null && nmR.getCurrentInterruptionFilter() != android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
        Object[][] ops = {
                {enSilencio ? "🔕" : "🔔", "Silenciar", (Runnable) this::silenciarCampana},
                {dndActivo ? "🌙" : "☾", "No\nmolestar", (Runnable) this::noMolestar},
                {"👁", "Ojo de\npuerta", (Runnable) this::miniCamara},
                {"✨", "Organizar", (Runnable) this::organizarCasilla},
                {"✍️", "Escribir\nMi Voz", (Runnable) this::abrirEscribir},
                {"🧾", "Cuenta\nRecibo", (Runnable) () -> abrir(new Intent(this, CuentaActivity.class))},
                {"📸", "Pantallazo", (Runnable) this::pantallazo},
                {"🖨", "Imprimir\npantalla", (Runnable) this::imprimirPantalla},
                {"📜", "Imprimir\nbajando", (Runnable) this::imprimirLargo},
                {"🎥", "Grabar\npantalla", (Runnable) () -> abrir(new Intent(this, GrabarActivity.class))},
                {"📄", "Word", (Runnable) () -> copiar(true)},
                {"🧮", "Calcular", (Runnable) () -> abrir(new Intent(this, CalculadoraActivity.class).putExtra("pestana", 0))},
                {"🚕", "Transporte", (Runnable) () -> abrir(new Intent(this, TransporteActivity.class))},
                {"💲", "Precios", (Runnable) () -> abrir(new Intent(this, PreciosActivity.class))},
                {"🧹", "Cerrar apps", (Runnable) this::cerrarApps},
                {"🔒", "Apagar\npantalla", (Runnable) () -> { if (android.os.Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); }},
                {"🔄", "Girar\npantalla", (Runnable) this::girarPantalla},
                {"📡", "Proyectar", (Runnable) () -> InicioActivity.abrirTransmitir(this)},
                {"🐜", "Gastos\nhormiga", (Runnable) () -> abrir(new Intent(this, GastosActivity.class))},
                // Versión 35: un solo «Bloquear» (WhatsApp o llamadas) para que el menú quepa sin desplazar
                {"🚫", "Bloquear", (Runnable) () -> abrir(new Intent(this, BloqueosActivity.class).putExtra("tipo", "elegir"))},
                {"⚙", "Botón", (Runnable) this::abrirAjustesBoton},
        };
        // Versión 30: el orden lo escoge él (deja el dedo y arrastra el botón a donde quiera)
        java.util.List<Object[]> lista = new java.util.ArrayList<>(java.util.Arrays.asList(ops));
        String guardado = prefs.getString("ordenMenu30", "");
        // los nuevos de la versión 32 entran de primeros aunque ya tuviera un orden guardado
        if (!guardado.isEmpty() && !guardado.contains("Silenciar")) guardado = "Silenciar|No\nmolestar|Ojo de\npuerta|" + guardado;
        // Versión 38: «Organizar» entra justo antes de «Escribir Mi Voz» aunque ya tuviera un orden guardado
        if (!guardado.isEmpty() && !guardado.contains("Organizar"))
            guardado = guardado.contains("Escribir\nMi Voz") ? guardado.replace("Escribir\nMi Voz", "Organizar|Escribir\nMi Voz") : "Organizar|" + guardado;
        if (!guardado.isEmpty()) {
            java.util.List<Object[]> ord = new java.util.ArrayList<>();
            for (String k : guardado.split("\\|"))
                for (Object[] o : lista) if (o[1].equals(k) && !ord.contains(o)) ord.add(o);
            for (Object[] o : lista) if (!ord.contains(o)) ord.add(o);
            lista = ord;
        }
        final java.util.List<Object[]> orden = lista;
        final TextView[] fichas = new TextView[orden.size()];
        l.setClipChildren(false);
        LinearLayout fila = null;
        for (int i = 0; i < orden.size(); i++) {
            if (i % 3 == 0) { fila = new LinearLayout(this); fila.setClipChildren(false); l.addView(fila); }
            // colores fuertes para encontrarlos rápido
            Object[] o = orden.get(i);
            Runnable r = (Runnable) o[2];
            TextView t = new TextView(this);
            t.setText(o[0] + "\n" + o[1]);
            t.setTextColor(Color.WHITE);
            // Versión 35: botones más bajitos para que todo el menú quepa en la pantalla sin desplazar
            t.setTextSize(12);
            t.setGravity(Gravity.CENTER);
            t.setLineSpacing(0, 0.9f);
            t.setPadding(0, Util.dp(this, 4), 0, Util.dp(this, 4));
            GradientDrawable g = new GradientDrawable();
            g.setColor(COLORES_MENU[i % COLORES_MENU.length]);
            g.setCornerRadius(Util.dp(this, 12));
            t.setBackground(g);
            t.setOnClickListener(v -> { quitar(menuAyuda); menuAyuda = null; h.postDelayed(r, 250); });
            final int idx = i;
            fichas[i] = t;
            final float[] ini = new float[2];
            final boolean[] arrastra = {false};
            t.setOnLongClickListener(v -> {
                arrastra[0] = true;
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                v.setScaleX(1.12f); v.setScaleY(1.12f); v.setAlpha(0.85f);
                v.bringToFront();
                ((View) v.getParent()).bringToFront();
                try { ((android.os.Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(android.os.VibrationEffect.createOneShot(40, 120)); } catch (Exception ignored) {}
                return true;
            });
            t.setOnTouchListener((v, e) -> {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) { ini[0] = e.getRawX(); ini[1] = e.getRawY(); return false; }
                if (!arrastra[0]) return false;
                if (e.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                    v.setTranslationX(e.getRawX() - ini[0]);
                    v.setTranslationY(e.getRawY() - ini[1]);
                    return true;
                }
                if (e.getAction() == android.view.MotionEvent.ACTION_UP || e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    arrastra[0] = false;
                    int destino = -1;
                    int[] pos = new int[2];
                    for (int k = 0; k < fichas.length; k++) {
                        if (k == idx || fichas[k] == null) continue;
                        fichas[k].getLocationOnScreen(pos);
                        if (e.getRawX() >= pos[0] && e.getRawX() <= pos[0] + fichas[k].getWidth()
                                && e.getRawY() >= pos[1] && e.getRawY() <= pos[1] + fichas[k].getHeight()) { destino = k; break; }
                    }
                    v.setTranslationX(0); v.setTranslationY(0); v.setScaleX(1f); v.setScaleY(1f); v.setAlpha(1f);
                    if (destino >= 0) {
                        Object[] mov = orden.remove(idx);
                        orden.add(destino, mov);
                        StringBuilder sb = new StringBuilder();
                        for (Object[] x : orden) sb.append(sb.length() > 0 ? "|" : "").append(x[1]);
                        prefs.edit().putString("ordenMenu30", sb.toString()).apply();
                        quitar(menuAyuda); menuAyuda = null;
                        h.post(this::abrirMenuAyuda);
                    }
                    return true;
                }
                return true;
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(Util.dp(this, 2), Util.dp(this, 2), Util.dp(this, 2), Util.dp(this, 2));
            fila.addView(t, p);
        }
        if (orden.size() % 3 != 0) for (int k = orden.size() % 3; k < 3; k++) fila.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView ayudaOrden = new TextView(this);
        ayudaOrden.setText("Deje el dedo 2 segundos en un botón y arrástrelo para cambiarlo de puesto");
        ayudaOrden.setTextColor(0xFFFFD54A);
        ayudaOrden.setTextSize(11);
        ayudaOrden.setGravity(Gravity.CENTER);
        l.addView(ayudaOrden);
        // Volumen y brillo: una barrita cada uno, bien separadas de los botones y entre ellas (versión 30)
        espacio(l, 26);
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final TextView[] ic = new TextView[1];
        ic[0] = barraLinea(l, am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 ? "🔇" : "🔊", maxVol,
                am.getStreamVolume(AudioManager.STREAM_MUSIC), v -> {
                    try { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); } catch (Exception ignored) {}
                    ic[0].setText(v == 0 ? "🔇" : "🔊");
                });
        // Versión 43: un toque al parlante = silencio; otro toque = vuelve al volumen que tenía
        final SeekBar sbVol = ultimaBarra;
        ic[0].setOnClickListener(x -> {
            int ahora = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int v = ahora > 0 ? 0 : Math.max(1, prefs.getInt("volAntes", maxVol / 2));
            if (ahora > 0) prefs.edit().putInt("volAntes", ahora).apply();
            try { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); } catch (Exception ignored) {}
            sbVol.setProgress(v);
            ic[0].setText(v == 0 ? "🔇" : "🔊");
        });
        espacio(l, 6);
        escala(l, sbVol, 0, maxVol, v -> {
            try { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); } catch (Exception ignored) {}
            ic[0].setText(v == 0 ? "🔇" : "🔊");
        }, null, null);
        int brillo;
        try { brillo = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS); } catch (Exception e) { brillo = 128; }
        espacio(l, 34);
        barraLinea(l, "☀", 255, !Settings.System.canWrite(this) ? 255 - veloNivel * 255 / VELO_SIN
                : veloNivel > 0 ? Math.max(0, 40 - veloNivel * 40 / VELO_CON) : Math.min(255, Math.max(40, brillo)), this::ponerBrillo);
        espacio(l, 6);
        // "0" no deja la pantalla a oscuras: queda en el mínimo cómodo para leer
        escala(l, ultimaBarra, 24, 255, this::ponerBrillo, "☀ Auto", this::brilloAuto);
        espacio(l, 24);
        // Versión 46: sin botón Cerrar — se cierra deslizando de lado o tocando por fuera
        mostrarMenu(l, 270);
    }

    private void espacio(LinearLayout l, int alto) {
        l.addView(new View(this), new LinearLayout.LayoutParams(1, Util.dp(this, alto)));
    }

    /** ⚙ Botón: tamaño, transparencia, fijar, esconder y volver a la esquina. */
    private void abrirAjustesBoton() {
        if (menuAyuda != null) { quitar(menuAyuda); menuAyuda = null; }
        LinearLayout l = panelMenu();
        TextView aj = new TextView(this);
        aj.setText("⚙ Ajustes del botón");
        aj.setTextColor(0xFFFFD54A);
        aj.setTextSize(15);
        aj.setPadding(Util.dp(this, 6), Util.dp(this, 4), 0, Util.dp(this, 4));
        l.addView(aj);
        barrita(l, "Tamaño", 40, prefs.getInt("botonTam", 50) - 34, v -> { prefs.edit().putInt("botonTam", v + 34).apply(); aplicarTamano(); });
        barrita(l, "Transparencia", 75, 100 - prefs.getInt("botonOpaco", 92), v -> {
            prefs.edit().putInt("botonOpaco", 100 - v).apply();
            if (botonAyuda != null) botonAyuda.setAlpha((100 - v) / 100f);
        });
        LinearLayout fila3 = new LinearLayout(this);
        boolean fijo = prefs.getBoolean("botonFijo", false);
        String[] tx = {fijo ? "🔓 Soltar" : "📌 Fijar", "🙈 Esconder", "↙ Esquina"};
        Runnable[] ac = {
                () -> { boolean f = !prefs.getBoolean("botonFijo", false); prefs.edit().putBoolean("botonFijo", f).apply();
                        aviso(f ? "📌 Botón fijo: ya no se mueve sin querer" : "🔓 Ahora lo puede mover dejándolo presionado"); },
                () -> esconderBoton(true),
                () -> {
                    prefs.edit().remove("botonX").remove("botonY").apply();
                    if (lpBoton != null) {
                        lpBoton.x = Util.dp(this, 6);
                        lpBoton.y = yAbajo();
                        try { wm.updateViewLayout(botonAyuda, lpBoton); } catch (Exception ignored) {}
                    }
                }};
        for (int i = 0; i < 3; i++) {
            Runnable r = ac[i];
            Button b = new Button(this);
            b.setText(tx[i]);
            b.setAllCaps(false);
            b.setTextSize(13);
            b.setTextColor(Color.WHITE);
            b.setPadding(0, 0, 0, 0);
            GradientDrawable gb = new GradientDrawable();
            gb.setColor(0xFF2B3459);
            gb.setCornerRadius(Util.dp(this, 10));
            b.setBackground(gb);
            b.setOnClickListener(v -> { quitar(menuAyuda); menuAyuda = null; r.run(); });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, Util.dp(this, 48), 1f);
            bp.setMargins(Util.dp(this, 2), Util.dp(this, 8), Util.dp(this, 2), 0);
            fila3.addView(b, bp);
        }
        l.addView(fila3);
        boolean enVideo = prefs.getBoolean("botonEnVideo", false);
        boton(l, enVideo ? "🎥 Al grabar pantalla: el botón SÍ sale en el video" : "🎥 Al grabar pantalla: el botón NO sale en el video", () -> {
            boolean v = !prefs.getBoolean("botonEnVideo", false);
            prefs.edit().putBoolean("botonEnVideo", v).apply();
            aviso(v ? "El botón se verá en las grabaciones de pantalla" : "El botón se esconde mientras graba; para parar use la barra de arriba");
            quitar(menuAyuda); menuAyuda = null;
        });
        // Versión 46: sin botón Cerrar — se cierra deslizando de lado o tocando por fuera
        mostrarMenu(l, 270);
    }

    private static final int[] COLORES_MENU = {0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA,
            0xFF3949AB, 0xFFF4511E, 0xFFFB8C00, 0xFF6D4C41, 0xFF546E7A, 0xFF5D4037};

    /** Abajo a la izquierda, encima de los botones del celular. */
    private int yAbajo() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int t = Util.dp(this, prefs.getInt("botonTam", 50));
        return Math.max(0, dm.heightPixels - t - Util.dp(this, 120));
    }

    // ---------------- Versión 18: grabar audio y pastilla con el tiempo ----------------

    /** Versión 30: mini grabadora de un toque: si no graba, empieza; si está grabando, para y guarda. */
    private void grabarAudio() {
        if (estGrab != 0) {
            startService(new Intent(this, GrabadoraService.class).setAction(GrabadoraService.PARAR));
            aviso("⏹ Grabación guardada");
            return;
        }
        abrir(new Intent(this, GrabadoraActivity.class).putExtra("empezar", true));
    }

    /** Versión 32: 🔕 la campana: sin sonido para llamadas y WhatsApp. Otro toque y vuelve el sonido. */
    private void silenciarCampana() {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            boolean ya = am.getRingerMode() == android.media.AudioManager.RINGER_MODE_SILENT;
            am.setRingerMode(ya ? android.media.AudioManager.RINGER_MODE_NORMAL : android.media.AudioManager.RINGER_MODE_SILENT);
            aviso(ya ? "🔔 Con sonido" : "🔕 Silencio: llamadas y WhatsApp sin sonido (las alarmas sí suenan)");
        } catch (Exception e) { pedirPermisoNoMolestar(); }
    }

    /** Versión 32: 🌙 No molestar prendido/apagado (las alarmas sí suenan). */
    private void noMolestar() {
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        if (nm == null) return;
        if (!nm.isNotificationPolicyAccessGranted()) { pedirPermisoNoMolestar(); return; }
        boolean activo = nm.getCurrentInterruptionFilter() != android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
        nm.setInterruptionFilter(activo ? android.app.NotificationManager.INTERRUPTION_FILTER_ALL : android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        aviso(activo ? "🔔 No molestar: apagado" : "🌙 No molestar: prendido (las alarmas sí suenan)");
    }

    private void pedirPermisoNoMolestar() {
        aviso("Permita a Mi Barra el acceso a «No molestar» y vuelva a tocar");
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
    }

    private void miniCamara() {
        if (MiniCamaraService.activa) { startService(new Intent(this, MiniCamaraService.class).setAction("cerrar")); return; }
        abrir(new Intent(this, MiniCamaraActivity.class));
    }

    private TextView pastilla;
    /** Lo que avisa la grabadora (vive en otro proceso): 0 parada, 1 grabando, 2 pausa por silencio, 3 pausa. */
    private int estGrab;
    private long segGrab;

    /** Versión 45: contador de la grabadora bien arriba a la derecha, color nítido.
     *  Un toque: para y guarda. Toque sostenido: Soltar (se puede arrastrar) o Amarrar (queda fijo). */
    private void pintarPastilla() {
        if (estGrab == 0) { quitar(pastilla); pastilla = null; quitar(menuPastilla); menuPastilla = null; return; }
        if (pastilla == null) {
            pastilla = contadorFlotante("pastilla45", Util.dp(this, 2), () -> {
                startService(new Intent(this, GrabadoraService.class).setAction(GrabadoraService.PARAR));
                aviso("⏹ Grabación guardada");
            });
            if (pastilla == null) return;
        }
        long s = segGrab;
        String hora = s >= 3600 ? String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Util.dp(this, 20));
        g.setStroke(Util.dp(this, 2), Color.WHITE);
        if (estGrab == 1) { g.setColor(0xFFD32F2F); pastilla.setText("● " + hora); }
        else if (estGrab == 2) { g.setColor(0xFFEF6C00); pastilla.setText("⏸ " + hora); }
        else { g.setColor(0xFF616161); pastilla.setText("⏸ " + hora); }
        pastilla.setBackground(g);
    }

    private View menuPastilla;

    /** Arma un contador flotante (el de la voz o el de la pantalla). Arranca amarrado arriba a la derecha.
     *  clave = dónde se guarda su sitio; alto = cuánto baja desde arriba; alParar = lo que hace un toque. */
    private TextView contadorFlotante(String clave, int alto, Runnable alParar) {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        int p = Util.dp(this, 7);
        t.setPadding(p + p, p, p + p, p);
        WindowManager.LayoutParams lpp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        lpp.gravity = Gravity.TOP | Gravity.END;
        lpp.x = prefs.getInt(clave + "X", Util.dp(this, 4));
        lpp.y = prefs.getInt(clave + "Y", alto);
        final float[] ini = new float[4];
        final boolean[] movio = {false}, largo = {false};
        final Runnable[] sostenido = new Runnable[1];
        t.setOnTouchListener((v, e) -> {
            boolean suelto = prefs.getBoolean(clave + "Suelto", false);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    ini[0] = e.getRawX(); ini[1] = e.getRawY(); ini[2] = lpp.x; ini[3] = lpp.y; movio[0] = false; largo[0] = false;
                    sostenido[0] = () -> { if (!movio[0]) { largo[0] = true; vibrarCorto(); menuContador(clave, v, lpp); } };
                    h.postDelayed(sostenido[0], 600);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - ini[0], dy = e.getRawY() - ini[1];
                    if (Math.abs(dx) + Math.abs(dy) > Util.dp(this, 10)) { movio[0] = true; h.removeCallbacks(sostenido[0]); }
                    if (movio[0] && suelto && !largo[0]) {
                        lpp.x = (int) Math.max(0, ini[2] - dx);   // va pegado a la derecha: al mover a la derecha baja x
                        lpp.y = (int) Math.max(0, ini[3] + dy);
                        try { wm.updateViewLayout(v, lpp); } catch (Exception ignored) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    h.removeCallbacks(sostenido[0]);
                    if (largo[0]) return true;
                    if (movio[0]) { if (suelto) prefs.edit().putInt(clave + "X", lpp.x).putInt(clave + "Y", lpp.y).apply(); }
                    else if (e.getActionMasked() == MotionEvent.ACTION_UP) alParar.run();
                    return true;
            }
            return false;
        });
        try { wm.addView(t, lpp); return t; } catch (Exception e) { return null; }
    }

    /** Toque sostenido en un contador: solo dos opciones, Soltar y Amarrar. */
    private void menuContador(String clave, View contador, WindowManager.LayoutParams lpc) {
        quitar(menuPastilla);
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Util.dp(this, 6);
        m.setPadding(pad, pad, pad, pad);
        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(0xFF15193A);
        fondo.setCornerRadius(Util.dp(this, 14));
        fondo.setStroke(Util.dp(this, 2), Color.WHITE);
        m.setBackground(fondo);
        boolean suelto = prefs.getBoolean(clave + "Suelto", false);
        String[][] ops = {{"🔓 Soltar", "1"}, {"🔒 Amarrar", "0"}};
        for (String[] o : ops) {
            TextView b = new TextView(this);
            b.setText(o[0]);
            b.setTextSize(16);
            b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            b.setTextColor(Color.WHITE);
            b.setGravity(Gravity.CENTER);
            b.setPadding(Util.dp(this, 14), Util.dp(this, 10), Util.dp(this, 14), Util.dp(this, 10));
            GradientDrawable gb = new GradientDrawable();
            gb.setCornerRadius(Util.dp(this, 10));
            boolean activo = suelto == o[1].equals("1");
            gb.setColor(activo ? 0xFF2E7D32 : 0xFF37474F);
            b.setBackground(gb);
            b.setOnClickListener(v -> {
                boolean s2 = o[1].equals("1");
                prefs.edit().putBoolean(clave + "Suelto", s2).putInt(clave + "X", lpc.x).putInt(clave + "Y", lpc.y).apply();
                aviso(s2 ? "🔓 Suelto: arrástrelo con el dedo a donde quiera" : "🔒 Amarrado: ya no se mueve");
                quitar(menuPastilla); menuPastilla = null;
            });
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.setMargins(Util.dp(this, 3), 0, Util.dp(this, 3), 0);
            m.addView(b, bl);
        }
        WindowManager.LayoutParams lm = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        lm.gravity = Gravity.TOP | Gravity.END;
        lm.x = lpc.x;
        lm.y = lpc.y + (contador.getHeight() > 0 ? contador.getHeight() : Util.dp(this, 36)) + Util.dp(this, 6);
        m.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) { quitar(menuPastilla); menuPastilla = null; return true; }
            return false;
        });
        try { wm.addView(m, lm); menuPastilla = m; } catch (Exception ignored) {}
        h.postDelayed(() -> { if (menuPastilla == m) { quitar(m); menuPastilla = null; } }, 6000);
    }

    private void vibrarCorto() {
        try {
            android.os.Vibrator v = getSystemService(android.os.Vibrator.class);
            if (v != null) v.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {}
    }

    // ---- Versión 45: cuadrito ■ para parar la grabación de pantalla, con el tiempo (sí sale en el video, nítido) ----
    private TextView cuadritoPantalla;
    private long inicioPantalla;

    private final Runnable relojPantalla = new Runnable() {
        @Override public void run() {
            if (!GrabarService.grabando) { quitar(cuadritoPantalla); cuadritoPantalla = null; quitar(menuPastilla); menuPastilla = null; return; }
            if (cuadritoPantalla == null) {
                cuadritoPantalla = contadorFlotante("cuadrito45", Util.dp(BarraService.this, 48), () ->
                        startService(new Intent(BarraService.this, GrabarService.class).setAction(GrabarService.PARAR)));
            }
            if (cuadritoPantalla != null) {
                long s = (System.currentTimeMillis() - inicioPantalla) / 1000;
                cuadritoPantalla.setText(String.format(java.util.Locale.US, "■ %02d:%02d", s / 60, s % 60));
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(Util.dp(BarraService.this, 10));
                g.setColor(0xFFB71C1C);
                g.setStroke(Util.dp(BarraService.this, 2), Color.WHITE);
                cuadritoPantalla.setBackground(g);
            }
            h.postDelayed(this, 1000);
        }
    };

    private GradientDrawable fondoRojo() {
        GradientDrawable gc = new GradientDrawable();
        gc.setColor(0xFF5A2A2A);
        gc.setCornerRadius(Util.dp(this, 10));
        return gc;
    }

    private LinearLayout panelMenu() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int pad = Util.dp(this, 8);
        l.setPadding(pad, pad, pad, pad);
        return l;
    }

    /** Ícono + barrita en una sola línea. */
    private TextView barraLinea(LinearLayout l, String icono, int max, int valor, AlMover m) {
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(icono);
        t.setTextSize(28);
        t.setPadding(Util.dp(this, 4), 0, 0, 0);
        f.addView(t, new LinearLayout.LayoutParams(Util.dp(this, 48), ViewGroup.LayoutParams.WRAP_CONTENT));
        SeekBar sb = new SeekBar(this);
        // Versión 41: barra gruesa y bolita grande con borde, para agarrarla bien con el dedo
        boolean esBrillo = "☀".equals(icono);
        int lleno = esBrillo ? 0xFFF57F17 : 0xFF1565C0, vacio = esBrillo ? 0xFFFFE082 : 0xFF90CAF9, bola = esBrillo ? 0xFFE65100 : 0xFF0D47A1;
        GradientDrawable fondoB = new GradientDrawable();
        fondoB.setColor(vacio);
        fondoB.setCornerRadius(Util.dp(this, 8));
        GradientDrawable llenoB = new GradientDrawable();
        llenoB.setColor(lleno);
        llenoB.setCornerRadius(Util.dp(this, 8));
        android.graphics.drawable.LayerDrawable capas = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                fondoB, new android.graphics.drawable.ClipDrawable(llenoB, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)});
        capas.setId(0, android.R.id.background);
        capas.setId(1, android.R.id.progress);
        sb.setProgressDrawable(capas);
        sb.setMinHeight(Util.dp(this, 16));
        sb.setMaxHeight(Util.dp(this, 16));
        GradientDrawable pulgar = new GradientDrawable();
        pulgar.setShape(GradientDrawable.OVAL);
        pulgar.setColor(bola);
        pulgar.setStroke(Util.dp(this, 3), Color.WHITE);
        pulgar.setSize(Util.dp(this, 36), Util.dp(this, 36));
        sb.setThumb(pulgar);
        sb.setPadding(Util.dp(this, 20), 0, Util.dp(this, 20), 0);
        sb.setMax(max);
        sb.setProgress(valor);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean u) { if (u) m.valor(v); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        f.addView(sb, new LinearLayout.LayoutParams(0, Util.dp(this, 60), 1f));
        l.addView(f);
        ultimaBarra = sb;
        return t;
    }

    private SeekBar ultimaBarra;

    /** Versión 43: escala debajo de la barrita (0, ¼, ½, ¾, Máx) con un toque; y un botón extra (Auto). */
    private void escala(LinearLayout l, SeekBar sb, int min0, int max, AlMover m, String extra, Runnable alExtra) {
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(Util.dp(this, 48), 0, Util.dp(this, 6), 0);
        String[] n = {"0", "¼", "½", "¾", "Máx"};
        for (int i = 0; i < 5; i++) {
            final int v = i == 0 ? min0 : max * i / 4;
            TextView t = new TextView(this);
            t.setText(n[i]);
            t.setTextColor(Color.WHITE);
            t.setTextSize(16);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            t.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFF455A64);
            g.setCornerRadius(Util.dp(this, 8));
            t.setBackground(g);
            t.setOnClickListener(x -> { sb.setProgress(v); m.valor(v); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Util.dp(this, 40), 1f);
            p.setMargins(Util.dp(this, 3), 0, Util.dp(this, 3), 0);
            f.addView(t, p);
        }
        if (extra != null) {
            TextView t = new TextView(this);
            t.setText(extra);
            t.setTextColor(Color.BLACK);
            t.setTextSize(16);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            t.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFFFFD54A);
            g.setCornerRadius(Util.dp(this, 8));
            t.setBackground(g);
            t.setOnClickListener(x -> alExtra.run());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Util.dp(this, 40), 1.3f);
            p.setMargins(Util.dp(this, 3), 0, Util.dp(this, 3), 0);
            f.addView(t, p);
        }
        l.addView(f);
    }

    private void brilloAuto() {
        ponerVelo(0);
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            aviso("☀ Brillo automático: se acomoda solo a la luz. Mueva la barrita para bajarlo a mano.");
        } catch (Exception e) {
            aviso("Para el brillo automático permita a Mi Barra «Modificar la configuración del sistema».");
        }
    }

    // Versión 41: el oscurecido máximo baja (antes 200 y 170): en la calle la pantalla ya no queda a oscuras
    private static final int VELO_SIN = 120, VELO_CON = 80;

    private void ponerBrillo(int v) {
        // Versión 30: si el celular (Motorola) no deja cambiar el brillo, la barrita oscurece con el velo en todo su recorrido
        if (!Settings.System.canWrite(this)) {
            if (!prefs.getBoolean("pidioBrillo30", false)) {
                prefs.edit().putBoolean("pidioBrillo30", true).apply();
                aviso("Para el brillo de verdad: permita a Mi Barra «Modificar la configuración del sistema». Mientras tanto, la barrita oscurece la pantalla.");
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            android.net.Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {}
            }
            ponerVelo(v >= 250 ? 0 : (255 - v) * VELO_SIN / 255);
            return;
        }
        // En el Motorola el brillo mínimo del celular todavía es alto:
        // la parte baja de la barrita (0 a 40) deja el celular en su mínimo y pone un velo oscuro encima.
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, v < 40 ? 1 : v);
            ponerVelo(v < 40 ? (40 - v) * VELO_CON / 40 : 0);
        } catch (Exception e) {
            ponerVelo(v >= 250 ? 0 : (255 - v) * VELO_SIN / 255);
        }
    }

    private void ponerVelo(int alfa) {
        veloNivel = alfa;
        if (alfa <= 0) { quitar(velo); velo = null; return; }
        if (velo == null) {
            velo = new View(this);
            WindowManager.LayoutParams p = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            try { wm.addView(velo, p); } catch (Exception e) { velo = null; return; }
        }
        velo.setBackgroundColor(alfa << 24);
    }

    /** Versión 46: el menú se acomoda solo según dónde esté el botón (arriba/abajo, izquierda/derecha),
     *  nunca se sale de la pantalla, y se cierra deslizando de lado o tocando por fuera. */
    private void mostrarMenu(LinearLayout l, int anchoDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF0182032);
        g.setCornerRadius(Util.dp(this, 20));
        g.setStroke(Util.dp(this, 1), 0x44FFFFFF);
        DisplayMetrics dm = new DisplayMetrics();
        try { wm.getDefaultDisplay().getRealMetrics(dm); } catch (Exception e) { dm = getResources().getDisplayMetrics(); }
        int anchoPant = dm.widthPixels, altoPant = dm.heightPixels;
        int margen = Util.dp(this, 6), arriba = Util.dp(this, 30), abajo = Util.dp(this, 52);
        // Versión 47: la barra de arriba (hora, batería) y la de abajo (botones del celular) medidas de verdad
        try {
            int idA = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (idA > 0) arriba = getResources().getDimensionPixelSize(idA) + Util.dp(this, 4);
            int idB = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (idB > 0) abajo = getResources().getDimensionPixelSize(idB) + Util.dp(this, 6);
        } catch (Exception ignored) {}
        int ancho = Math.min(Util.dp(this, anchoDp), anchoPant - 2 * margen);
        // Medir cuánto mide el menú completo
        l.measure(View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int altoMenu = l.getMeasuredHeight();
        int bx = lpBoton == null ? margen : lpBoton.x, by = lpBoton == null ? Util.dp(this, 60) : lpBoton.y;
        int bt = botonAyuda != null && botonAyuda.getWidth() > 0 ? botonAyuda.getWidth() : Util.dp(this, prefs.getInt("botonTam", 50));
        // Versión 47: esté donde esté el botón (arriba, en el medio o abajo), el menú sale siempre desde arriba
        // de la pantalla y baja completo; así nunca queda nada por fuera (ni los deslizadores ni lo último).
        int alto = Math.min(altoMenu, altoPant - arriba - abajo);
        int y = arriba;
        int x = (bx + bt / 2 > anchoPant / 2) ? bx + bt - ancho : bx;           // botón a la derecha → abre hacia la izquierda
        x = Math.max(margen, Math.min(x, anchoPant - ancho - margen));
        WindowManager.LayoutParams p = lp(ancho, alto,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = x;
        p.y = y;
        final int umbral = Util.dp(this, 70);
        final float[] ini = new float[2];
        final boolean[] enBarra = new boolean[1];
        ScrollView sv = new ScrollView(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        ini[0] = e.getRawX(); ini[1] = e.getRawY();
                        enBarra[0] = sobreBarra(this, e.getRawX(), e.getRawY());
                        break;
                    case MotionEvent.ACTION_MOVE:
                    case MotionEvent.ACTION_UP:
                        if (!enBarra[0]) {
                            float dx = e.getRawX() - ini[0], dy = e.getRawY() - ini[1];
                            if (Math.abs(dx) > umbral && Math.abs(dx) > 2 * Math.abs(dy)) {
                                View yo = this;
                                yo.animate().translationX(dx > 0 ? anchoPant : -anchoPant).alpha(0f).setDuration(160)
                                        .withEndAction(() -> { quitar(yo); if (menuAyuda == yo) menuAyuda = null; });
                                enBarra[0] = true;   // no repetir
                                return true;
                            }
                        }
                        break;
                }
                return super.dispatchTouchEvent(e);
            }
        };
        sv.setBackground(g);
        sv.setVerticalScrollBarEnabled(true);
        sv.addView(l);
        sv.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) { quitar(menuAyuda); menuAyuda = null; return true; }
            return false;
        });
        try { wm.addView(sv, p); menuAyuda = sv; } catch (Exception ignored) {}
    }

    /** Versión 46: ¿el dedo cayó sobre un deslizador (volumen/brillo)? Ahí deslizar de lado no cierra el menú. */
    private static boolean sobreBarra(View v, float rx, float ry) {
        if (v.getVisibility() != View.VISIBLE) return false;
        int[] pos = new int[2];
        v.getLocationOnScreen(pos);
        boolean dentro = rx >= pos[0] && rx <= pos[0] + v.getWidth() && ry >= pos[1] && ry <= pos[1] + v.getHeight();
        if (!dentro) return false;
        if (v instanceof SeekBar) return true;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) if (sobreBarra(vg.getChildAt(i), rx, ry)) return true;
        }
        return false;
    }

    // ---------------- Cerrar apps (abre Recientes y toca "Borrar todo") ----------------

    private static final String[] BORRAR_TODO = {"borrar todo", "cerrar todo", "borrar todas", "cerrar todas", "limpiar todo",
            "eliminar todo", "clear all", "close all", "limpiar"};

    void cerrarApps() {
        performGlobalAction(GLOBAL_ACTION_RECENTS);
        h.postDelayed(() -> { if (!tocarBorrarTodo()) h.postDelayed(() -> {
            if (!tocarBorrarTodo()) aviso("🧹 Toque «Borrar todo» o deslice las apps hacia arriba");
        }, 900); }, 900);
    }

    private boolean tocarBorrarTodo() {
        try {
            for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && buscarYTocar(r, 0)) return true;
            }
            AccessibilityNodeInfo r = getRootInActiveWindow();
            return r != null && buscarYTocar(r, 0);
        } catch (Exception e) { return false; }
    }

    private boolean buscarYTocar(AccessibilityNodeInfo n, int prof) {
        if (n == null || prof > 30) return false;
        CharSequence t = n.getText() != null ? n.getText() : n.getContentDescription();
        if (t != null) {
            String s = t.toString().toLowerCase(Locale.ROOT).trim();
            for (String b : BORRAR_TODO) if (s.equals(b) || s.startsWith(b)) {
                AccessibilityNodeInfo x = n;
                while (x != null && !x.isClickable()) x = x.getParent();
                if (x != null && x.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) if (buscarYTocar(n.getChild(i), prof + 1)) return true;
        return false;
    }

    private boolean pidioBrillo;
    private boolean escondido;

    private void aplicarTamano() {
        if (botonAyuda == null || lpBoton == null || escondido) return;
        int t = Util.dp(this, prefs.getInt("botonTam", 50));
        lpBoton.width = t; lpBoton.height = t;
        botonAyuda.setTextSize(t / getResources().getDisplayMetrics().density * 0.45f);
        try { wm.updateViewLayout(botonAyuda, lpBoton); } catch (Exception ignored) {}
    }

    static final String ACCION_RELOJ = "co.jose.mibarra.BOTON_RELOJ";
    private final android.content.BroadcastReceiver avisoReloj = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            boolean si = i.getBooleanExtra("esconder", false);
            if (botonAyuda != null) try { botonAyuda.setVisibility(si ? View.GONE : View.VISIBLE); } catch (Exception ignored) {}
        }
    };

    /** Versión 33: mientras el radio reloj está abierto, el botón flotante no estorba (no tapa la batería). */
    static void ocultarParaReloj(boolean si) {
        BarraService b = instancia;
        if (b == null || b.botonAyuda == null) return;
        try { b.botonAyuda.setVisibility(si ? View.GONE : View.VISIBLE); } catch (Exception ignored) {}
    }

    /** Esconder: queda una rayita en el borde izquierdo. Tocarla o deslizarla hacia adentro lo trae de vuelta. */
    private void esconderBoton(boolean si) {
        if (botonAyuda == null || lpBoton == null) return;
        escondido = si;
        if (si) {
            lpBoton.x = 0;
            lpBoton.width = Util.dp(this, 10);
            lpBoton.height = Util.dp(this, 56);
            aviso("🙈 Botón escondido: toque o deslice la rayita del borde para traerlo");
        } else {
            int t = Util.dp(this, prefs.getInt("botonTam", 50));
            lpBoton.width = t; lpBoton.height = t;
            lpBoton.x = prefs.getInt("botonX", Util.dp(this, 6));
        }
        try { wm.updateViewLayout(botonAyuda, lpBoton); } catch (Exception ignored) {}
        pintarBotonAyuda();
        if (!si) aplicarTamano();
    }

    interface AlMover { void valor(int v); }

    private String nombreVolumen(int v) { return v == 0 ? "🔇  Volumen (silencio)" : "🔊  Volumen"; }

    /** Una barrita para deslizar con el dedo: a la izquierda baja, a la derecha sube. */
    private TextView barrita(LinearLayout l, String nombre, int max, int valor, AlMover m) {
        TextView t = new TextView(this);
        t.setText(nombre);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setPadding(Util.dp(this, 6), Util.dp(this, 10), 0, 0);
        l.addView(t);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(valor);
        sb.setPadding(Util.dp(this, 16), Util.dp(this, 8), Util.dp(this, 16), Util.dp(this, 8));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean u) { if (u) m.valor(v); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        l.addView(sb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Util.dp(this, 44)));
        return t;
    }

    /** Números grandes 3, 2, 1 en el centro; luego empieza a grabar. No estorba los toques. */
    void cuentaRegresiva(Runnable alTerminar) {
        h.post(() -> {
            TextView n = new TextView(this);
            n.setGravity(Gravity.CENTER);
            n.setTextColor(Color.WHITE);
            n.setTextSize(90);
            n.setShadowLayer(Util.dp(this, 8), 0, 0, Color.BLACK);
            WindowManager.LayoutParams p = lp(Util.dp(this, 200), Util.dp(this, 200),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            p.gravity = Gravity.CENTER;
            boolean puesto;
            try { wm.addView(n, p); puesto = true; } catch (Exception e) { puesto = false; }
            final boolean ok = puesto;
            for (int i = 0; i < 3; i++) {
                final String txt = String.valueOf(3 - i);
                h.postDelayed(() -> { if (ok) n.setText(txt); }, i * 1000L);
            }
            h.postDelayed(() -> {
                if (ok) quitar(n);
                h.postDelayed(alTerminar, 150);
            }, 3000);
        });
    }

    /** La grabación avisa cuando empieza y cuando para: el botón redondo cambia a ⏹ y vuelve. */
    void mostrarPararGrabacion(boolean si) {
        h.post(() -> {
            h.removeCallbacks(relojPantalla);
            if (si) { inicioPantalla = System.currentTimeMillis(); h.post(relojPantalla); }
            else { quitar(cuadritoPantalla); cuadritoPantalla = null; }
            if (botonAyuda == null) crearBotonAyuda();
            pintarBotonAyuda();
        });
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        // Versión 40: si él se sale de la app donde abrió ✨ Organizar, la ventana se cierra sola
        try {
            if (panelOrganizar == null || e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
            CharSequence pq = e.getPackageName();
            if (pq == null) return;
            String p = pq.toString();
            if (p.equals(appOrganizar) || p.equals(getPackageName()) || p.equals("com.android.systemui") || p.contains("inputmethod")) return;
            quitar(panelOrganizar);
            panelOrganizar = null;
        } catch (Exception ignored) {}
    }
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        copiando = false;
        instancia = null;
        try { unregisterReceiver(avisoReloj); } catch (Exception ignored) {}
        try { unregisterReceiver(receptor); } catch (Exception ignored) {}
        try { unregisterReceiver(pantallaPrendida); } catch (Exception ignored) {}
        quitar(botonAyuda); quitar(menuAyuda); quitar(pastilla); quitar(marcoLargo); quitar(cuadritoPantalla); quitar(menuPastilla);
        quitar(pestana); quitar(pestanaDer); quitar(panel); quitar(filtro); quitar(velo); quitar(botonDetener); quitar(panelOrganizar);
        super.onDestroy();
    }

    private void quitar(View v) {
        if (v != null && v.isAttachedToWindow()) {
            try { wm.removeView(v); } catch (Exception ignored) {}
        }
    }

    private WindowManager.LayoutParams lp(int w, int alto, int flags) {
        return new WindowManager.LayoutParams(w, alto,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
    }

    private void aviso(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dimSistema(String nombre) {
        int id = getResources().getIdentifier(nombre, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    // ---------------- Pestañitas (izquierda: herramientas · derecha: en la vía) ----------------

    /**
     * Rayita casi invisible en el borde (2 dp a la vista, 12 dp para tocar).
     * Deslice el dedo hacia adentro: abre. Déjela presionada 1 segundo y arrástrela arriba o abajo: queda fija donde la suelte.
     */
    private View crearPestana(boolean derecha) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(derecha ? 0x7743A047 : 0x77909CAA);
        g.setCornerRadius(Util.dp(this, 2));
        int borde = Util.dp(this, 10);
        v.setBackground(new android.graphics.drawable.InsetDrawable(g, derecha ? borde : 0, 0, derecha ? 0 : borde, 0));
        WindowManager.LayoutParams p = lp(Util.dp(this, 12), Util.dp(this, 56),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        p.gravity = (derecha ? Gravity.END : Gravity.START) | Gravity.TOP;
        p.x = 0;
        int idEstado = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int arriba = (idEstado > 0 ? getResources().getDimensionPixelSize(idEstado) : Util.dp(this, 28)) + Util.dp(this, 4);
        p.y = prefs.getInt(derecha ? "pestYDer" : "pestYIzq", arriba);
        v.addOnLayoutChangeListener((vv, l, t, rr, b, ol, ot, or, ob) ->
                vv.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, rr - l, b - t))));
        final float[] x0 = new float[1], y0 = new float[1];
        final int[] yIni = new int[1];
        final boolean[] moviendo = new boolean[1], abrio = new boolean[1];
        final android.os.Handler hh = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable largo = () -> {
            moviendo[0] = true;
            g.setColor(0xFFFFC107);
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            Toast.makeText(this, "Arrástrela arriba o abajo y suéltela donde no estorbe", Toast.LENGTH_SHORT).show();
        };
        v.setOnTouchListener((vv, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0[0] = ev.getRawX(); y0[0] = ev.getRawY(); yIni[0] = p.y;
                    moviendo[0] = false; abrio[0] = false;
                    hh.postDelayed(largo, 900);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = ev.getRawX() - x0[0], dy = ev.getRawY() - y0[0];
                    if (moviendo[0]) {
                        int alto = getResources().getDisplayMetrics().heightPixels;
                        p.y = Math.max(0, Math.min(alto - Util.dp(this, 80), yIni[0] + (int) dy));
                        try { wm.updateViewLayout(v, p); } catch (Exception ignored) {}
                        return true;
                    }
                    if (Math.abs(dx) > Util.dp(this, 8) || Math.abs(dy) > Util.dp(this, 8)) hh.removeCallbacks(largo);
                    if (!abrio[0] && (derecha ? -dx : dx) > Util.dp(this, 16)) { abrio[0] = true; abrirPanel(derecha); }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    hh.removeCallbacks(largo);
                    if (moviendo[0]) {
                        moviendo[0] = false;
                        g.setColor(derecha ? 0x7743A047 : 0x77909CAA);
                        prefs.edit().putInt(derecha ? "pestYDer" : "pestYIzq", p.y).apply();
                        Toast.makeText(this, "📌 Quedó fija aquí", Toast.LENGTH_SHORT).show();
                    }
                    // Un toque suelto NO abre: solo deslizando el dedo hacia adentro, para no estorbar a otras apps.
                    return true;
            }
            return false;
        });
        wm.addView(v, p);
        return v;
    }

    // ---------------- Paneles con secciones ----------------

    private static final String[] SEC_IZQ = {"⚡\nRápido", "📝\nOficina", "🧮\nObra", "🏠\nCasa", "🛣️\nViaje", "📷\nMedios"};
    private static final String[] SEC_DER = SEC_IZQ;
    private boolean ladoDerecha;
    private LinearLayout contenido;
    private Button[] pestanasSec;

    private void abrirPanel(boolean derecha) {
        if (panel != null) return;
        ladoDerecha = derecha;
        int pad = Util.dp(this, 6);
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(pad, pad, pad, pad);
        GradientDrawable g = new GradientDrawable();
        g.setColor(derecha ? 0xF01B2A20 : 0xF0202830);
        float r = Util.dp(this, 16);
        g.setCornerRadii(derecha ? new float[]{r, r, 0, 0, 0, 0, r, r} : new float[]{0, 0, r, r, r, r, 0, 0});
        raiz.setBackground(g);

        String[] secs = derecha ? SEC_DER : SEC_IZQ;
        LinearLayout tabs = new LinearLayout(this);
        pestanasSec = new Button[secs.length];
        for (int i = 0; i < secs.length; i++) {
            final int k = i;
            Button b = new Button(this);
            b.setText(secs[i]);
            b.setAllCaps(false);
            b.setTextSize(10);
            b.setTextColor(Color.WHITE);
            b.setPadding(0, Util.dp(this, 4), 0, Util.dp(this, 4));
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setOnClickListener(v -> ponerSeccion(k));
            pestanasSec[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Util.dp(this, 58), 1f);
            lp.setMargins(Util.dp(this, 1), 0, Util.dp(this, 1), 0);
            tabs.addView(b, lp);
        }
        raiz.addView(tabs);

        ScrollView sv = new ScrollView(this);
        contenido = new LinearLayout(this);
        contenido.setOrientation(LinearLayout.VERTICAL);
        sv.addView(contenido);
        raiz.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout pie = new LinearLayout(this);
        pie.setOrientation(LinearLayout.VERTICAL);
        boton(pie, "✕  Cerrar", this::cerrarPanel);
        raiz.addView(pie);

        raiz.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) { cerrarPanel(); return true; }
            return false;
        });
        // No tapa la barra de estado de arriba ni los botones ■ ● ◀ de abajo
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        int arriba = dimSistema("status_bar_height"), abajo = dimSistema("navigation_bar_height");
        WindowManager.LayoutParams p = lp(Util.dp(this, 300), dm.heightPixels - arriba - abajo,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        p.gravity = (derecha ? Gravity.END : Gravity.START) | Gravity.TOP;
        p.y = arriba;
        wm.addView(raiz, p);
        panel = raiz;
        ponerSeccion(Math.min(prefs.getInt(derecha ? "secDer" : "secIzq", 0), secs.length - 1));
    }

    private void ponerSeccion(int k) {
        prefs.edit().putInt(ladoDerecha ? "secDer" : "secIzq", k).apply();
        for (int i = 0; i < pestanasSec.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(i == k ? (ladoDerecha ? 0xFF2E7D32 : 0xFF1565C0) : 0xFF34404C);
            g.setCornerRadius(Util.dp(this, 10));
            pestanasSec[i].setBackground(g);
        }
        contenido.removeAllViews();
        switch (k) {
            case 0: seccionAtajos(contenido, Util.dp(this, 8)); break;
            case 1: seccionOficina(contenido); break;
            case 2: seccionCalculo(contenido); break;
            case 3: seccionCasa(contenido); break;
            case 4: seccionViaje(contenido); seccionBuscar(contenido); break;
            default: seccionCamara(contenido);
        }
    }

    /** Fila de botones pequeños (para volumen y "buscar cerca"). */
    private void fila(LinearLayout l, String[] textos, Runnable[] acciones) {
        LinearLayout f = new LinearLayout(this);
        for (int i = 0; i < textos.length; i++) {
            Button b = boton(new LinearLayout(this), textos[i], acciones[i]);
            ((ViewGroup) b.getParent()).removeView(b);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, 0, 0, 0);
            b.setTextSize(14);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Util.dp(this, 50), 1f);
            p.setMargins(i == 0 ? 0 : Util.dp(this, 3), Util.dp(this, 6), 0, 0);
            f.addView(b, p);
        }
        l.addView(f);
    }

    private void titulito(LinearLayout l, String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(0xFF9FB3C8);
        v.setTextSize(13);
        v.setPadding(Util.dp(this, 6), Util.dp(this, 12), 0, 0);
        l.addView(v);
    }

    private void seccionCasa(LinearLayout lista) {
        boton(lista, "🔔  Recordatorios y alarmas", () -> { cerrarPanel(); Puerta.abrirChpRecordatorios(this); });   // versión 47: app aparte
        boton(lista, "⏱  Reloj de cocina y recetas", () -> abrir(new Intent(this, RelojActivity.class)));
        boton(lista, "📻  Radio reloj", () -> abrir(new Intent(this, RadioRelojActivity.class)));
        boton(lista, "💌  Saludos y memes", () -> abrir(new Intent(this, SaludosActivity.class)));
        boton(lista, "📚  Enciclopedia", () -> abrir(new Intent(this, EnciclopediaActivity.class)));
        boton(lista, "🏠  Pantalla de inicio", () -> abrir(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)));
    }

    private void seccionAtajos(LinearLayout lista, int pad) {
        boton(lista, linternaPrendida ? "🔦  Apagar linterna" : "🔦  Linterna", this::linterna);
        boton(lista, "📸  Pantallazo", this::pantallazo);
        boton(lista, GrabarService.grabando ? "⏹  Parar grabación de pantalla" : "🎥  Grabar la pantalla", this::grabar);
        boton(lista, "📄  Copiar a Word ⬇ bajando", () -> copiar(true));
        boton(lista, "📄  Copiar a Word ⬆ subiendo", () -> copiar(false));
        boton(lista, "🖨  Imprimir pantalla", this::imprimirPantalla);
        titulito(lista, "Volumen");
        fila(lista, new String[]{"🔊 +", "🔉 −", "🔇"}, new Runnable[]{() -> volumen(AudioManager.ADJUST_RAISE),
                () -> volumen(AudioManager.ADJUST_LOWER), () -> volumen(AudioManager.ADJUST_TOGGLE_MUTE)});
        fila(lista, new String[]{"📶 Wi‑Fi", "📱 Datos"}, new Runnable[]{() -> abrir(new Intent(Settings.Panel.ACTION_WIFI)),
                () -> abrir(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))});
        boton(lista, "📴  Apagar / Reiniciar", () -> {
            cerrarPanel();
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        });

        final TextView tl = new TextView(this);
        tl.setTextColor(Color.WHITE);
        tl.setTextSize(16);
        tl.setPadding(pad, Util.dp(this, 12), pad, 0);
        int luz = prefs.getInt("luz", 0);
        tl.setText("🌙  Luz azul: " + luz + "%");
        lista.addView(tl);
        SeekBar sb = new SeekBar(this);
        sb.setMax(80);
        sb.setProgress(luz);
        sb.setPadding(Util.dp(this, 16), Util.dp(this, 14), Util.dp(this, 16), Util.dp(this, 14));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                tl.setText("🌙  Luz azul: " + p + "%");
                aplicarFiltro(p);
                prefs.edit().putInt("luz", p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        lista.addView(sb);
    }

    private void seccionOficina(LinearLayout lista) {
        boton(lista, "📝  Cuaderno (mini Word)", () -> abrir(new Intent(this, NotasActivity.class)));
        boton(lista, "📊  Cuentas (mini Excel)", () -> abrir(new Intent(this, HojaActivity.class)));
        boton(lista, "🧾  Cuenta de cobro", () -> abrir(new Intent(this, CobroActivity.class)));
        boton(lista, "🛒  Lista de compras", () -> abrir(new Intent(this, ComprasActivity.class)));
        boton(lista, "📇  Proveedores", () -> abrir(new Intent(this, ProveedoresActivity.class)));
        boton(lista, "📄  Escáner con nivel", () -> abrir(new Intent(this, EscanerActivity.class)));
        boton(lista, "📑  PDF: unir, separar, fotos", () -> abrir(new Intent(this, MainActivity.class)));
    }

    private void seccionCalculo(LinearLayout lista) {
        boton(lista, "🧮  Calculadora", () -> abrir(new Intent(this, CalculadoraActivity.class).putExtra("pestana", 0)));
        boton(lista, "📏  mm ↔ pulgadas", () -> abrir(new Intent(this, CalculadoraActivity.class).putExtra("pestana", 1)));
        boton(lista, "🏗️  Concreto", () -> abrir(new Intent(this, CalculadoraActivity.class).putExtra("pestana", 2)));
        boton(lista, "🧱  Ladrillo / farol", () -> abrir(new Intent(this, CalculadoraActivity.class).putExtra("pestana", 3)));
        boton(lista, "📸  Foto con medidas", () -> abrir(new Intent(this, MedidasActivity.class)));
        boton(lista, "📐  Nivel y plomada", () -> abrir(new Intent(this, NivelActivity.class)));
        boton(lista, "📐  Grados: techo, pasamanos, tubo", () -> abrir(new Intent(this, NivelActivity.class).putExtra("modo", 3)));
    }

    private void seccionCamara(LinearLayout lista) {
        boton(lista, "📷  Cámara Mi Barra (foto)", () -> abrir(new Intent(this, CamaraActivity.class)));
        boton(lista, "🎬  Cámara Mi Barra (video)", () -> abrir(new Intent(this, CamaraActivity.class).putExtra("video", true)));
        boton(lista, "🎵  Grabadora de voz", () -> abrir(new Intent(this, GrabadoraActivity.class)));
        boton(lista, GrabarService.grabando ? "⏹  Parar grabación de pantalla" : "🎥  Grabar la pantalla", this::grabar);
    }

    private boolean grabandoRecorrido() { return RecorridoService.estado(this).exists(); }

    private void seccionViaje(LinearLayout lista) {
        boolean rec = grandandoSeguro();
        boton(lista, "📍  Aquí estoy (mapa + dirección)", () -> abrir(new Intent(this, AquiEstoyActivity.class)));
        boton(lista, rec ? "🔴  Recorrido (grabando)" : "🛣️  Recorridos: grabar y ver", () -> abrir(new Intent(this, RecorridoActivity.class)));
        if (rec) boton(lista, "📌  Marcar boya aquí", () -> abrir(new Intent(this, RecorridoActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("boya", true)));
        boton(lista, "🏍  Antes de salir (revisión, pico y placa)", () -> abrir(new Intent(this, ChequeoActivity.class)));
        boton(lista, "🗺  Ruta de ciudad a ciudad", () -> abrir(new Intent(this, ParadasActivity.class).putExtra("modo", "viaje")));
        boton(lista, "📦  Reparto (varias paradas)", () -> abrir(new Intent(this, ParadasActivity.class).putExtra("modo", "reparto")));
        boton(lista, "📂  Mis rutas y repartos", () -> abrir(new Intent(this, ParadasActivity.class)));
    }

    private void seccionBuscar(LinearLayout lista) {
        titulito(lista, "🔎 Buscar cerca");
        fila(lista, new String[]{"⛽ Gasolina", "🍽 Comida"}, new Runnable[]{() -> buscarCerca("gasolinera"), () -> buscarCerca("restaurante")});
        fila(lista, new String[]{"🏥 Hospital", "💊 Droguería"}, new Runnable[]{() -> buscarCerca("hospital"), () -> buscarCerca("droguería")});
        fila(lista, new String[]{"🔧 Motos", "🚗 Carros"}, new Runnable[]{() -> buscarCerca("taller de motos"), () -> buscarCerca("taller mecánico")});
        fila(lista, new String[]{"🏧 Cajero", "🔩 Ferretería"}, new Runnable[]{() -> buscarCerca("cajero automático"), () -> buscarCerca("ferretería")});
    }

    private boolean grandandoSeguro() {
        try { return grabandoRecorrido(); } catch (Exception e) { return false; }
    }

    private void buscarCerca(String que) {
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(que)));
        i.setPackage("com.google.android.apps.maps");
        if (i.resolveActivity(getPackageManager()) == null) {
            i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/maps/search/"
                    + android.net.Uri.encode(que + " cerca de mí")));
        }
        abrir(i);
    }

    private void cerrarPanel() {
        quitar(panel);
        panel = null;
    }

    private Button boton(LinearLayout l, String t, Runnable accion) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(Util.dp(this, 14), 0, Util.dp(this, 8), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF34404C);
        g.setCornerRadius(Util.dp(this, 10));
        b.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Util.dp(this, 50));
        p.topMargin = Util.dp(this, 6);
        b.setOnClickListener(v -> accion.run());
        l.addView(b, p);
        return b;
    }

    // ---------------- Linterna (no necesita permiso de cámara) ----------------

    private boolean linternaPrendida;
    private android.hardware.camera2.CameraManager.TorchCallback vigiaLinterna;

    private String camaraConFlash(android.hardware.camera2.CameraManager cm) throws Exception {
        for (String id : cm.getCameraIdList()) {
            android.hardware.camera2.CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            Boolean f = ch.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer lado = ch.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
            if (Boolean.TRUE.equals(f) && lado != null && lado == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        return null;
    }

    private void linterna() {
        try {
            android.hardware.camera2.CameraManager cm = getSystemService(android.hardware.camera2.CameraManager.class);
            String id = camaraConFlash(cm);
            if (id == null) { Toast.makeText(this, "Este celular no tiene linterna", Toast.LENGTH_SHORT).show(); return; }
            if (vigiaLinterna == null) {
                final String cid = id;
                vigiaLinterna = new android.hardware.camera2.CameraManager.TorchCallback() {
                    @Override public void onTorchModeChanged(String camId, boolean prendida) {
                        if (camId.equals(cid)) linternaPrendida = prendida;
                    }
                };
                cm.registerTorchCallback(vigiaLinterna, new android.os.Handler(android.os.Looper.getMainLooper()));
            }
            cm.setTorchMode(id, !linternaPrendida);
            linternaPrendida = !linternaPrendida;
            cerrarPanel();
        } catch (Exception e) {
            Toast.makeText(this, "La linterna está ocupada (¿cámara abierta?)", Toast.LENGTH_SHORT).show();
        }
    }

    private void abrir(Intent i) {
        cerrarPanel();
        // Versión 48: si la herramienta ya salió como app suelta e instalada, se abre allá
        Intent gr = Grupos.desviar(this, i);
        if (gr != null) { try { startActivity(gr); } catch (Exception e) { aviso("No se pudo abrir"); } return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (i.getComponent() != null && getPackageName().equals(i.getComponent().getPackageName()))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);   // v43: vuelve la misma herramienta, no una nueva
        try { startActivity(i); } catch (Exception e) { aviso("No se pudo abrir"); }
    }

    // ---------------- Acciones sencillas ----------------

    private void volumen(int dir) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            am.adjustSuggestedStreamVolume(dir, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
        } catch (Exception e) {
            try { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI); }
            catch (Exception ignored) { aviso("El celular no permitió cambiar el volumen"); }
        }
    }

    private void grabar() {
        cerrarPanel();
        if (GrabarService.grabando) {
            startService(new Intent(this, GrabarService.class).setAction(GrabarService.PARAR));
        } else {
            abrir(new Intent(this, GrabarActivity.class));
        }
    }

    // ---------------- Luz azul ----------------

    private void aplicarFiltro(int pct) {
        if (pct <= 0) {
            quitar(filtro);
            filtro = null;
            pintarBotonAyuda();
            return;
        }
        int a = Math.round(pct / 100f * 200);
        int color = (a << 24) | 0x00FF8A20;
        if (filtro == null) {
            filtro = new View(this);
            WindowManager.LayoutParams p = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            wm.addView(filtro, p);
        }
        filtro.setBackgroundColor(color);
        pintarBotonAyuda();   // versión 48: el botón muestra la 🌙 mientras está prendida
    }

    // ---------------- Pantallazo e imprimir ----------------

    private interface AlCapturar { void listo(Bitmap b); }

    private void capturar(AlCapturar cb) {
        cerrarPanel();
        final boolean habiaFiltro = filtro != null;
        if (habiaFiltro) filtro.setVisibility(View.GONE);
        if (velo != null) velo.setVisibility(View.GONE);
        if (pestana != null) pestana.setVisibility(View.GONE);
        if (pestanaDer != null) pestanaDer.setVisibility(View.GONE);
        if (botonAyuda != null) botonAyuda.setVisibility(View.INVISIBLE);
        h.postDelayed(() -> takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult r) {
                restaurar(habiaFiltro);
                HardwareBuffer hb = r.getHardwareBuffer();
                Bitmap b = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                Bitmap c = b == null ? null : b.copy(Bitmap.Config.ARGB_8888, false);
                hb.close();
                if (c == null) { aviso("No se pudo tomar el pantallazo"); return; }
                cb.listo(c);
            }
            @Override public void onFailure(int codigo) {
                restaurar(habiaFiltro);
                // Plan B: el pantallazo normal del celular
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
            }
        }), 450);
    }

    private void restaurar(boolean habiaFiltro) {
        if (habiaFiltro && filtro != null) filtro.setVisibility(View.VISIBLE);
        if (velo != null) velo.setVisibility(View.VISIBLE);
        if (pestana != null && prefs.getBoolean("barraVisible", false)) pestana.setVisibility(View.VISIBLE);
        if (pestanaDer != null) pestanaDer.setVisibility(View.VISIBLE);
        if (botonAyuda != null) botonAyuda.setVisibility(View.VISIBLE);
    }

    private View vistaPantallazo;

    /**
     * Versión 44: el pantallazo se guarda de una y se muestra 2 segundos en una tarjetica abajo.
     * Si él la toca en esos 2 segundos, se abre para recortar, marcar o mandar por WhatsApp.
     * Si no la toca, se cierra sola y el pantallazo queda guardado como siempre.
     */
    private void pantallazo() {
        capturar(b -> new Thread(() -> {
            try {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.PNG, 100, o);
                byte[] datos = o.toByteArray();
                android.net.Uri u = Util.guardarBytes(this, "Pantallazo_" + Util.fecha() + ".png", "image/png", datos);
                File f = new File(getCacheDir(), "pantallazo_editar.png");
                try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(datos); }
                int ancho = getResources().getDisplayMetrics().widthPixels / 3;
                Bitmap mini = Bitmap.createScaledBitmap(b, ancho, Math.max(1, b.getHeight() * ancho / b.getWidth()), true);
                h.post(() -> mostrarTarjeta(mini, f, u));
            } catch (Exception e) {
                h.post(() -> aviso("Error al guardar: " + e.getMessage()));
            }
        }).start());
    }

    private void mostrarTarjeta(Bitmap mini, File f, android.net.Uri original) {
        quitar(vistaPantallazo);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        int pad = Util.dp(this, 4);
        t.setPadding(pad, pad, pad, pad);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(Util.dp(this, 12));
        g.setStroke(Util.dp(this, 3), 0xFFFFC107);
        t.setBackground(g);
        t.setElevation(Util.dp(this, 10));
        android.widget.ImageView im = new android.widget.ImageView(this);
        im.setImageBitmap(mini);
        im.setAdjustViewBounds(true);
        t.addView(im, new LinearLayout.LayoutParams(mini.getWidth(), mini.getHeight()));
        TextView et = new TextView(this);
        et.setText("✂ Toque para\nrecortar o enviar");
        et.setTextColor(Color.BLACK);
        et.setTextSize(13);
        et.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        et.setGravity(Gravity.CENTER);
        et.setBackgroundColor(0xFFFFEB3B);
        t.addView(et, new LinearLayout.LayoutParams(mini.getWidth(), -2));
        WindowManager.LayoutParams p = lp(-2, -2,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        p.gravity = Gravity.BOTTOM | Gravity.START;
        p.x = Util.dp(this, 14);
        p.y = Util.dp(this, 70);
        t.setOnClickListener(v -> {
            quitar(t);
            vistaPantallazo = null;
            abrir(new Intent(this, PantallazoActivity.class)
                    .putExtra("imagen", f.getAbsolutePath())
                    .putExtra("original", original == null ? null : original.toString()));
        });
        try { wm.addView(t, p); vistaPantallazo = t; } catch (Exception e) { aviso("📸 Pantallazo guardado"); return; }
        h.postDelayed(() -> {
            if (vistaPantallazo == t) { quitar(t); vistaPantallazo = null; }
        }, 2000);
    }

    private void imprimirPantalla() {
        capturar(b -> {
            try {
                File f = new File(getCacheDir(), "imprimir.png");
                try (FileOutputStream o = new FileOutputStream(f)) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, o);
                }
                Intent i = new Intent(this, PrintActivity.class)
                        .putExtra("imagen", f.getAbsolutePath())
                        .putExtra("titulo", "Pantalla " + Util.fecha());
                abrir(i);
            } catch (Exception e) {
                aviso("Error: " + e.getMessage());
            }
        });
    }

    // ---------------- Versión 18: Imprimir bajando (une la pantalla mientras él baja) ----------------

    private boolean largo;
    private TextView marcoLargo;
    private final List<Bitmap> franjas = new ArrayList<>();
    private int[][] firmaPrev;
    private int altoTotal, sinCambio;
    private boolean avisoRapido, primeraRecortada;
    private static final int COLS = 32;
    private static final int MAX_ALTO_HOJAS = 20;

    private void imprimirLargo() {
        if (largo) { terminarLargo(); return; }
        cerrarPanel();
        largo = true;
        franjas.clear();
        firmaPrev = null;
        altoTotal = 0;
        sinCambio = 0;
        avisoRapido = false;
        primeraRecortada = false;
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        int p = Util.dp(this, 10);
        t.setPadding(p * 2, p, p * 2, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xEE1565C0);
        g.setCornerRadius(Util.dp(this, 24));
        t.setBackground(g);
        t.setText("📜 Baje despacio · toque aquí al terminar");
        t.setOnClickListener(v -> terminarLargo());
        WindowManager.LayoutParams lpp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        lpp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lpp.y = Util.dp(this, 70);
        try { wm.addView(t, lpp); marcoLargo = t; } catch (Exception ignored) {}
        if (botonAyuda != null) botonAyuda.setVisibility(View.INVISIBLE);
        aviso("📜 Vaya bajando el correo o la página con el dedo, sin afán. Al final toque el letrero azul.");
        h.postDelayed(tomaLargo, 900);
    }

    private final Runnable tomaLargo = new Runnable() {
        @Override public void run() {
            if (!largo) return;
            if (marcoLargo != null) marcoLargo.setVisibility(View.INVISIBLE);
            h.postDelayed(() -> takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult r) {
                    if (marcoLargo != null) marcoLargo.setVisibility(View.VISIBLE);
                    HardwareBuffer hb = r.getHardwareBuffer();
                    Bitmap b = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                    Bitmap c = b == null ? null : b.copy(Bitmap.Config.ARGB_8888, false);
                    hb.close();
                    if (c != null) new Thread(() -> { agregarToma(c); h.post(() -> { pintarMarco(); h.postDelayed(tomaLargo, 1100); }); }).start();
                    else h.postDelayed(tomaLargo, 1100);
                }
                @Override public void onFailure(int codigo) {
                    if (marcoLargo != null) marcoLargo.setVisibility(View.VISIBLE);
                    h.postDelayed(tomaLargo, 1100);
                }
            }), 120);
        }
    };

    private void pintarMarco() {
        if (marcoLargo == null) return;
        int ancho = franjas.isEmpty() ? 1 : franjas.get(0).getWidth();
        int hojas = Math.max(1, (int) Math.ceil(altoTotal / (ancho * 1.29)));
        marcoLargo.setText("📜 Bajando… " + hojas + (hojas == 1 ? " hoja" : " hojas") + " · ✔ Listo");
        if (avisoRapido) { avisoRapido = false; aviso("Bajó muy rápido: suba un poquito y baje más despacio"); }
    }

    /** Firma de cada fila: 32 promedios de brillo. Sirve para saber cuánto bajó la pantalla. */
    private static int[][] firma(Bitmap b, int y0, int y1) {
        int w = b.getWidth();
        int[] fila = new int[w];
        int[][] f = new int[y1 - y0][COLS];
        int bloque = Math.max(1, w / COLS);
        for (int y = y0; y < y1; y++) {
            b.getPixels(fila, 0, w, 0, y, w, 1);
            for (int c = 0; c < COLS; c++) {
                int suma = 0, n = 0;
                for (int x = c * bloque; x < Math.min(w, (c + 1) * bloque); x += 2) {
                    int px = fila[x];
                    suma += ((px >> 16) & 255) + ((px >> 8) & 255) + (px & 255);
                    n++;
                }
                f[y - y0][c] = n == 0 ? 0 : suma / n;
            }
        }
        return f;
    }

    private static long dif(int[] a, int[] b) {
        long s = 0;
        for (int c = 0; c < COLS; c++) s += Math.abs(a[c] - b[c]);
        return s;
    }

    private void agregarToma(Bitmap b) {
        int arriba = dimSistema("status_bar_height");
        int abajo = b.getHeight() - dimSistema("navigation_bar_height");
        if (abajo <= arriba + 100) abajo = b.getHeight();
        int[][] f = firma(b, arriba, abajo);
        int H = f.length;
        if (firmaPrev == null || firmaPrev.length != H) {
            if (firmaPrev == null) {
                franjas.add(Bitmap.createBitmap(b, 0, arriba, b.getWidth(), H).copy(Bitmap.Config.RGB_565, false));
                altoTotal += H;
            }
            firmaPrev = f;
            b.recycle();
            return;
        }
        // Lo que no se mueve arriba (encabezado) y abajo (botones de la app)
        int T = 0;
        while (T < H && dif(f[T], firmaPrev[T]) < COLS * 6) T++;
        if (T >= H - 10) { sinCambio++; b.recycle(); return; }   // no bajó
        int B = 0;
        while (B < H - T && dif(f[H - 1 - B], firmaPrev[H - 1 - B]) < COLS * 6) B++;
        int ini = T, fin = H - B, alto = fin - ini;
        if (alto < 40) { b.recycle(); return; }
        // Buscar cuánto bajó: primero a saltos de 4 filas, luego fino
        int mejorD = -1;
        long mejor = Long.MAX_VALUE;
        int minSolape = Math.max(30, alto / 5);
        for (int d = 4; d <= alto - minSolape; d += 4) {
            long s = 0; int n = 0;
            for (int y = ini; y + d < fin; y += 8) { s += dif(firmaPrev[y + d], f[y]); n++; }
            if (n > 0 && s / n < mejor) { mejor = s / n; mejorD = d; }
        }
        if (mejorD > 0) {
            int base = mejorD;
            for (int d = Math.max(1, base - 4); d <= Math.min(alto - minSolape, base + 4); d++) {
                long s = 0; int n = 0;
                for (int y = ini; y + d < fin; y++) { s += dif(firmaPrev[y + d], f[y]); n++; }
                if (n > 0 && s / n < mejor) { mejor = s / n; mejorD = d; }
            }
        }
        int nuevas;
        if (mejorD > 0 && mejor < COLS * 14) nuevas = mejorD;          // empató: solo lo nuevo
        else { nuevas = alto; avisoRapido = true; }                      // bajó demasiado rápido
        // La primera toma traía los botones de abajo de la app: se le quitan para que no queden en medio
        if (!primeraRecortada && franjas.size() == 1) {
            primeraRecortada = true;
            Bitmap p0 = franjas.get(0);
            if (fin < p0.getHeight()) {
                franjas.set(0, Bitmap.createBitmap(p0, 0, 0, p0.getWidth(), fin));
                altoTotal -= p0.getHeight() - fin;
            }
        }
        if (altoTotal < b.getWidth() * 1.29 * MAX_ALTO_HOJAS) {
            franjas.add(Bitmap.createBitmap(b, 0, arriba + fin - nuevas, b.getWidth(), nuevas).copy(Bitmap.Config.RGB_565, false));
            altoTotal += nuevas;
        }
        firmaPrev = f;
        sinCambio = 0;
        b.recycle();
    }

    private void terminarLargo() {
        if (!largo) return;
        largo = false;
        h.removeCallbacks(tomaLargo);
        quitar(marcoLargo);
        marcoLargo = null;
        if (botonAyuda != null) pintarBotonAyuda();
        if (franjas.isEmpty()) { aviso("No alcancé a tomar nada"); return; }
        aviso("🖨 Armando las hojas carta…");
        new Thread(() -> {
            try {
                int w = franjas.get(0).getWidth();
                int altoHoja = (int) (w * 1.29);
                List<String> rutas = new ArrayList<>();
                int y = 0, k = 0;
                Bitmap hoja = null;
                android.graphics.Canvas cv = null;
                int yHoja = 0;
                for (Bitmap fr : franjas) {
                    int usado = 0;
                    while (usado < fr.getHeight()) {
                        if (hoja == null) {
                            hoja = Bitmap.createBitmap(w, altoHoja, Bitmap.Config.RGB_565);
                            hoja.eraseColor(Color.WHITE);
                            cv = new android.graphics.Canvas(hoja);
                            yHoja = 0;
                        }
                        int cabe = Math.min(fr.getHeight() - usado, altoHoja - yHoja);
                        cv.drawBitmap(fr, new Rect(0, usado, w, usado + cabe), new Rect(0, yHoja, w, yHoja + cabe), null);
                        usado += cabe;
                        yHoja += cabe;
                        if (yHoja >= altoHoja) { rutas.add(guardarHoja(hoja, k++)); hoja.recycle(); hoja = null; }
                    }
                    fr.recycle();
                }
                if (hoja != null) {
                    Bitmap recorte = Bitmap.createBitmap(hoja, 0, 0, w, Math.max(1, yHoja));
                    rutas.add(guardarHoja(recorte, k));
                    hoja.recycle();
                }
                franjas.clear();
                h.post(() -> abrir(new Intent(this, PrintActivity.class)
                        .putExtra("paginas", rutas.toArray(new String[0]))
                        .putExtra("titulo", "Impresion " + Util.fecha())));
            } catch (Throwable e) {
                franjas.clear();
                h.post(() -> aviso("No se pudo armar la impresión: " + e.getMessage()));
            }
        }).start();
    }

    private String guardarHoja(Bitmap b, int k) throws java.io.IOException {
        File f = new File(getCacheDir(), "hoja_" + k + ".jpg");
        try (FileOutputStream o = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.JPEG, 90, o); }
        return f.getAbsolutePath();
    }

    // ---------------- Copiar a Word con desplazamiento ----------------

    private void copiar(boolean abajo) {
        if (copiando) return;
        cerrarPanel();
        copiando = true;
        bajando = abajo;
        acumulado = new ArrayList<>();
        sinNuevas = 0;
        vueltas = 0;
        origen = "";
        mostrarDetener();
        h.postDelayed(pasoR, 600);
    }

    private void mostrarDetener() {
        TextView t = new TextView(this);
        t.setText("⏹ Detener y guardar");
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        int p = Util.dp(this, 12);
        t.setPadding(p * 2, p, p * 2, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xEEC62828);
        g.setCornerRadius(Util.dp(this, 24));
        t.setBackground(g);
        t.setOnClickListener(v -> terminarCopia());
        WindowManager.LayoutParams lp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = Util.dp(this, 36);
        wm.addView(t, lp);
        botonDetener = t;
    }

    private void paso() {
        if (!copiando) return;
        AccessibilityNodeInfo raiz = getRootInActiveWindow();
        List<String> nuevas = new ArrayList<>();
        if (raiz != null) {
            if (origen.isEmpty() && raiz.getPackageName() != null) origen = raiz.getPackageName().toString();
            AccessibilityNodeInfo zona = desplazable(raiz);
            juntar(zona != null ? zona : raiz, nuevas);
        }
        int antes = acumulado.size();
        unir(nuevas);
        sinNuevas = (acumulado.size() == antes) ? sinNuevas + 1 : 0;
        vueltas++;
        // No para solo: sigue hasta que usted toque Detener. Si ya no sale nada nuevo, avisa que parece el final.
        boolean finalPagina = sinNuevas >= 3;
        if (botonDetener != null) botonDetener.setText(finalPagina
                ? "✅ Parece el final · toque para guardar (" + acumulado.size() + " líneas)"
                : "⏹ Detener y guardar (" + acumulado.size() + " líneas)");
        if (vueltas > 5000) { terminarCopia(); return; }
        deslizar();
        h.postDelayed(pasoR, finalPagina ? 1500 : 850);
    }

    /** Busca la zona que se desplaza (la conversación o la página), la más grande. */
    private AccessibilityNodeInfo desplazable(AccessibilityNodeInfo raiz) {
        AccessibilityNodeInfo[] mejor = new AccessibilityNodeInfo[1];
        long[] area = new long[1];
        buscar(raiz, mejor, area, 0);
        return mejor[0];
    }

    private void buscar(AccessibilityNodeInfo n, AccessibilityNodeInfo[] mejor, long[] area, int nivel) {
        if (n == null || nivel > 60) return;
        if (n.isScrollable()) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            long a = (long) r.width() * r.height();
            if (a > area[0]) { area[0] = a; mejor[0] = n; }
        }
        for (int i = 0; i < n.getChildCount(); i++) buscar(n.getChild(i), mejor, area, nivel + 1);
    }

    private void juntar(AccessibilityNodeInfo n, List<String> out) {
        if (n == null) return;
        CharSequence pk = n.getPackageName();
        if (pk != null && getPackageName().contentEquals(pk)) return;
        if (!n.isEditable()) {
            CharSequence t = n.getText();
            if (t != null) {
                for (String l : t.toString().split("\n")) {
                    l = l.trim();
                    if (!l.isEmpty()) out.add(l);
                }
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) juntar(n.getChild(i), out);
    }

    private void unir(List<String> nuevas) {
        if (nuevas.isEmpty()) return;
        if (acumulado.isEmpty()) { acumulado.addAll(nuevas); return; }
        if (bajando) {
            int k = solape(acumulado, nuevas);
            acumulado.addAll(nuevas.subList(k, nuevas.size()));
        } else {
            int k = solape(nuevas, acumulado);
            acumulado.addAll(0, nuevas.subList(0, nuevas.size() - k));
        }
    }

    /** Cuántas líneas del final de "a" coinciden con el comienzo de "b". */
    private static int solape(List<String> a, List<String> b) {
        int max = Math.min(a.size(), b.size());
        for (int k = max; k > 0; k--) {
            boolean ok = true;
            for (int i = 0; i < k; i++) {
                if (!a.get(a.size() - k + i).equals(b.get(i))) { ok = false; break; }
            }
            if (ok) return k;
        }
        return 0;
    }

    /** Desliza el dedo despacio (sin "lanzar") para avanzar menos de una pantalla. */
    private void deslizar() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float x = dm.widthPixels * 0.6f;
        float y1 = dm.heightPixels * 0.70f, y2 = dm.heightPixels * 0.35f;
        Path p = new Path();
        if (bajando) { p.moveTo(x, y1); p.lineTo(x, y2); }
        else { p.moveTo(x, y2); p.lineTo(x, y1); }
        dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 600)).build(), null, null);
    }

    private void terminarCopia() {
        if (!copiando) return;
        copiando = false;
        h.removeCallbacks(pasoR);
        quitar(botonDetener);
        botonDetener = null;
        final List<String> l = new ArrayList<>(acumulado);
        if (l.isEmpty()) { aviso("No encontré texto para copiar en esta pantalla"); return; }
        final String titulo = "Copiado de " + nombreApp(origen) + " — "
                + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
        aviso("⏳ Guardando el Word…");
        new Thread(() -> {
            try {
                Util.guardarBytes(this, "Copia_" + Util.fecha() + ".docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        Util.crearDocx(titulo, l));
                h.post(() -> aviso("📄 Word guardado en Descargas (" + l.size() + " líneas)"));
            } catch (Exception e) {
                h.post(() -> aviso("Error al guardar: " + e.getMessage()));
            }
        }).start();
    }

    private static String nombreApp(String pk) {
        if (pk == null || pk.isEmpty()) return "la pantalla";
        if (pk.startsWith("com.whatsapp")) return "WhatsApp";
        if (pk.equals("com.android.chrome")) return "Chrome";
        if (pk.contains("browser")) return "el navegador";
        return pk;
    }
}
