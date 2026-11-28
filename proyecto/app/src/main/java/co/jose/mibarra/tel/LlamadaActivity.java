package co.jose.mibarra.tel;

import android.Manifest;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Versión 37: pantalla de llamada (entrante, marcando, en curso y contestador). Fondo oscuro, botones grandes. */
public class LlamadaActivity extends TelBase {

    private LinearLayout raiz;
    private TextView tiempo;
    private boolean teclado = false;
    private long inicioActiva = 0;
    private Call vista;
    private final Runnable miCambio = this::pintar;
    private PowerManager.WakeLock proximidad;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable reloj = new Runnable() {
        @Override
        public void run() {
            if (tiempo != null && inicioActiva > 0) {
                long s = (System.currentTimeMillis() - inicioActiva) / 1000;
                tiempo.setText(String.format("%d:%02d", s / 60, s % 60));
            }
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(FONDO);
        getWindow().setNavigationBarColor(FONDO);
        raiz = vertical();
        raiz.setBackgroundColor(FONDO);
        raiz.setPadding(dp(12), dp(16), dp(12), dp(16));
        setContentView(raiz);
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximidad = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "mibarra:oreja");
        }
        revisarContestar(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        revisarContestar(i);
    }

    private void revisarContestar(Intent i) {
        if (i != null && i.getBooleanExtra("contestar", false)) {
            Call k = LlamadaService.principal();
            if (k != null && LlamadaService.estado(k) == Call.STATE_RINGING) LlamadaService.contestar(k);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LlamadaService.alCambiar = miCambio;
        pintar();
        h.post(reloj);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(reloj);
        if (LlamadaService.alCambiar == miCambio) LlamadaService.alCambiar = null;
    }

    @Override
    protected void onDestroy() {
        ponerOreja(false);
        super.onDestroy();
    }

    private void ponerOreja(boolean si) {
        if (proximidad == null) return;
        try {
            if (si && !proximidad.isHeld()) proximidad.acquire(3 * 60 * 60 * 1000L);
            if (!si && proximidad.isHeld()) proximidad.release();
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private CallAudioState audio() {
        return LlamadaService.yo == null ? null : LlamadaService.yo.getCallAudioState();
    }

    @SuppressWarnings("deprecation")
    private void ruta(int r) {
        if (LlamadaService.yo != null) LlamadaService.yo.setAudioRoute(r);
    }

    private void pintar() {
        Call k = LlamadaService.principal();
        if (k == null) {
            ponerOreja(false);
            finish();
            return;
        }
        if (k != vista) {
            vista = k;
            inicioActiva = 0;
            teclado = false;
        }
        int st = LlamadaService.estado(k);
        if (st == Call.STATE_ACTIVE && inicioActiva == 0) {
            long c = k.getDetails().getConnectTimeMillis();
            inicioActiva = c > 0 ? c : System.currentTimeMillis();
        }
        if (Grabador.grabando() && st == Call.STATE_ACTIVE) Grabador.ponerAviso(this);
        raiz.removeAllViews();

        String num = LlamadaService.numero(k);
        String nombre = Reglas.nombreContacto(this, num);
        String titulo = nombre != null ? nombre : (num.isEmpty() ? "Número privado" : Reglas.formato(num));
        boolean contestador = Contestador.atendiendo(k);

        String estadoTxt;
        int colorEstado = VERDE_CLARO;
        switch (st) {
            case Call.STATE_RINGING: estadoTxt = "Llamada entrante"; break;
            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING: estadoTxt = "Llamando…"; break;
            case Call.STATE_HOLDING: estadoTxt = "En espera"; break;
            case Call.STATE_ACTIVE: estadoTxt = "En llamada"; break;
            case Call.STATE_SELECT_PHONE_ACCOUNT: estadoTxt = "¿Con cuál SIM llama?"; break;
            default: estadoTxt = "Terminando…";
        }
        if (contestador) {
            estadoTxt = Contestador.grabandoMensaje(k) ? "📼 Grabando el mensaje…" : "📼 Contestador: poniendo su saludo";
            colorEstado = AMARILLO;
        } else if (Grabador.grabando() && st == Call.STATE_ACTIVE) {
            estadoTxt = "🔴 Grabando en altavoz";
            colorEstado = 0xFFF09595;
        }

        if (st == Call.STATE_RINGING) {
            boolean grabara = Reglas.p(this).getBoolean("grabarTodas", true);
            TextView arriba = texto((grabara ? "🎙 Grabará al contestar   ·   🔊 Altavoz: sí" : "🎙 Sin grabación automática"), 15, true, AMARILLO);
            arriba.setGravity(Gravity.CENTER);
            raiz.addView(arriba, ancho());
        }

        TextView est = texto(estadoTxt, 19, true, colorEstado);
        est.setGravity(Gravity.CENTER);
        raiz.addView(est, ancho());

        TextView avatar = texto(iniciales(nombre != null ? nombre : ""), 36, true, BLANCO);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(fondo(AZUL_VIVO, dp(50)));
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(dp(100), dp(100));
        pa.gravity = Gravity.CENTER_HORIZONTAL;
        pa.setMargins(0, dp(14), 0, dp(10));
        raiz.addView(avatar, pa);

        TextView nom = texto(titulo, 30, true, BLANCO);
        nom.setGravity(Gravity.CENTER);
        raiz.addView(nom, ancho());
        if (nombre != null) {
            TextView n2 = texto(Reglas.formato(num), 22, false, AZUL_CLARO);
            n2.setGravity(Gravity.CENTER);
            raiz.addView(n2, ancho());
        }
        tiempo = texto(st == Call.STATE_ACTIVE ? "0:00" : "", 24, true, BLANCO);
        tiempo.setGravity(Gravity.CENTER);
        raiz.addView(tiempo, ancho());

        LinearLayout medio = vertical();
        medio.setGravity(Gravity.CENTER);
        raiz.addView(medio, relleno());

        if (st == Call.STATE_SELECT_PHONE_ACCOUNT) {
            ponerOreja(false);
            pintarSims(k, medio);
            Button c = boton("Cancelar", ROJO, BLANCO, 22);
            c.setOnClickListener(v -> k.disconnect());
            raiz.addView(c, alto(76));
            return;
        }

        if (st == Call.STATE_RINGING) {
            ponerOreja(false);
            Button silenciar = boton("🔕  Silenciar timbre (solo esta vez)", MORADO, BLANCO, 19);
            silenciar.setOnClickListener(v -> {
                if (LlamadaService.yo != null) LlamadaService.yo.callarTimbre();
                aviso("Timbre callado. Si no contesta, pasa al contestador.");
            });
            medio.addView(silenciar, alto(72));

            LinearLayout abajo = horizontal();
            Button cont = boton("📼\nContestador", AZUL, BLANCO, 21);
            cont.setOnClickListener(v -> {
                if (LlamadaService.yo != null) LlamadaService.yo.irAlContestador(k);
            });
            Button contestar = boton("📞\nContestar", VERDE, BLANCO, 23);
            contestar.setOnClickListener(v -> LlamadaService.contestar(k));
            abajo.addView(cont, peso(1));
            abajo.addView(contestar, peso(1));
            raiz.addView(abajo, alto(120));
            return;
        }

        if (contestador) {
            ponerOreja(false);
            TextView exp = texto("La persona está oyendo su saludo y dejando el mensaje. Si quiere hablar con ella, toque \"Tomar la llamada\".", 17, false, GRIS);
            exp.setGravity(Gravity.CENTER);
            exp.setPadding(dp(10), dp(6), dp(10), dp(10));
            medio.addView(exp, ancho());
            Button tomar = boton("📞  Tomar la llamada", VERDE, BLANCO, 22);
            tomar.setOnClickListener(v -> {
                if (LlamadaService.yo != null) LlamadaService.yo.tomarLlamada(k);
                h.postDelayed(this::pintar, 400);
            });
            medio.addView(tomar, alto(80));
            Button colgar = boton("Colgar y guardar el mensaje", ROJO, BLANCO, 22);
            colgar.setOnClickListener(v -> LlamadaService.colgar(k));
            raiz.addView(colgar, alto(84));
            return;
        }

        CallAudioState au = audio();
        boolean altavoz = au != null && au.getRoute() == CallAudioState.ROUTE_SPEAKER;
        boolean callado = au != null && au.isMuted();
        ponerOreja(!altavoz && !teclado);

        if (teclado) {
            pintarTeclado(k, medio);
        } else {
            LinearLayout f1 = horizontal();
            Button bAlt = boton(altavoz ? "🔊 Altavoz: SÍ" : "🔈 Altavoz: NO", altavoz ? AZUL_VIVO : TARJETA, BLANCO, 18);
            bAlt.setOnClickListener(v -> {
                if (altavoz && Grabador.grabando()) aviso("Ojo: sin altavoz la grabación casi no capta a la otra persona");
                ruta(altavoz ? CallAudioState.ROUTE_WIRED_OR_EARPIECE : CallAudioState.ROUTE_SPEAKER);
                h.postDelayed(this::pintar, 400);
            });
            Button bMic = boton(callado ? "🎤 Mudo: SÍ" : "🎤 Mudo", callado ? ROJO : TARJETA, BLANCO, 18);
            bMic.setOnClickListener(v -> {
                if (LlamadaService.yo != null) LlamadaService.yo.setMuted(!callado);
                h.postDelayed(this::pintar, 400);
            });
            f1.addView(bAlt, peso(1));
            f1.addView(bMic, peso(1));
            medio.addView(f1, alto(76));

            LinearLayout f2 = horizontal();
            Button bTec = boton("⌨ Teclado", TARJETA, BLANCO, 18);
            bTec.setOnClickListener(v -> {
                teclado = true;
                pintar();
            });
            boolean espera = st == Call.STATE_HOLDING;
            Button bEsp = boton(espera ? "Quitar espera" : "Poner en espera", espera ? NARANJA : TARJETA, BLANCO, 18);
            bEsp.setOnClickListener(v -> {
                try {
                    if (espera) k.unhold(); else k.hold();
                } catch (Exception ignored) {}
            });
            f2.addView(bTec, peso(1));
            f2.addView(bEsp, peso(1));
            medio.addView(f2, alto(76));

            boolean gr = Grabador.grabando();
            Button bRec = boton(gr ? "⏹ Parar grabación" : "⏺ Grabar (altavoz)", gr ? ROJO : TARJETA, BLANCO, 18);
            bRec.setOnClickListener(v -> {
                if (LlamadaService.yo == null) return;
                if (Grabador.grabando()) LlamadaService.yo.pararGrabacion();
                else if (!Grabador.puede(this)) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 21);
                    aviso("Permita el micrófono y vuelva a tocar Grabar");
                } else if (!LlamadaService.yo.grabar(k)) aviso("No se pudo grabar");
                h.postDelayed(this::pintar, 500);
            });
            medio.addView(bRec, alto(70));
        }

        Button colgar = boton("Colgar", ROJO, BLANCO, 26);
        colgar.setOnClickListener(v -> LlamadaService.colgar(k));
        raiz.addView(colgar, alto(84));
    }

    private void pintarTeclado(Call k, LinearLayout medio) {
        String[] t = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (int f = 0; f < 4; f++) {
            LinearLayout fila = horizontal();
            for (int c = 0; c < 3; c++) {
                String s = t[f * 3 + c];
                Button b = boton(s, TARJETA, BLANCO, 28);
                b.setOnClickListener(v -> {
                    try {
                        k.playDtmfTone(s.charAt(0));
                        h.postDelayed(k::stopDtmfTone, 180);
                    } catch (Exception ignored) {}
                });
                fila.addView(b, peso(1));
            }
            medio.addView(fila, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        Button ocultar = boton("Ocultar teclado", AZUL, BLANCO, 18);
        ocultar.setOnClickListener(v -> {
            teclado = false;
            pintar();
        });
        medio.addView(ocultar, alto(60));
    }

    @SuppressWarnings("deprecation")
    private void pintarSims(Call k, LinearLayout medio) {
        List<PhoneAccountHandle> l = null;
        try {
            l = k.getDetails().getExtras() == null ? null
                    : k.getDetails().getExtras().getParcelableArrayList(Call.AVAILABLE_PHONE_ACCOUNTS);
        } catch (Exception ignored) {}
        TelecomManager tm = getSystemService(TelecomManager.class);
        if ((l == null || l.isEmpty()) && Reglas.puede(this, Manifest.permission.READ_PHONE_STATE)) {
            try { l = tm.getCallCapablePhoneAccounts(); } catch (Exception ignored) {}
        }
        if (l == null) l = new ArrayList<>();
        int i = 1;
        for (PhoneAccountHandle ph : l) {
            String nom = "SIM " + i;
            try {
                PhoneAccount a = tm.getPhoneAccount(ph);
                if (a != null && a.getLabel() != null) nom += "  ·  " + a.getLabel();
            } catch (Exception ignored) {}
            Button b = boton(nom, AZUL_VIVO, BLANCO, 22);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setOnClickListener(v -> k.phoneAccountSelected(ph, false));
            medio.addView(b, alto(80));
            i++;
        }
    }
}
