package co.jose.mibarra.tel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Versión 37 (traído de Mis Chats y mejorado): maneja todas las llamadas cuando Mi Barra es la app de teléfono principal.
 * Al contestar o al llamar: altavoz y grabación solos. Si no contesta a tiempo: contestador.
 * Los números de estafa se cortan sin sonar.
 */
public class LlamadaService extends InCallService {

    static LlamadaService yo;
    static final List<Call> llamadas = new ArrayList<>();
    static final Set<Call> alContestador = new HashSet<>();
    static Runnable alCambiar;

    private static final String CANAL = "llamadas_mt";
    private static final int AVISO = 7;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Call.Callback cb = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            if (state == Call.STATE_ACTIVE) alActivarse(call);
            avisar();
            cambio();
        }
    };

    private void alActivarse(Call call) {
        if (alContestador.contains(call)) {
            Contestador.empezar(this, call);
        } else if (Reglas.p(this).getBoolean("grabarTodas", true) && !Grabador.grabando()) {
            grabar(call);
        }
    }

    /** Pone el altavoz (dos veces, porque a veces Android lo devuelve al auricular al conectar). */
    void altavoz() {
        try { setAudioRoute(CallAudioState.ROUTE_SPEAKER); } catch (Exception ignored) {}
        h.postDelayed(() -> {
            try { setAudioRoute(CallAudioState.ROUTE_SPEAKER); } catch (Exception ignored) {}
        }, 900);
    }

    boolean grabar(Call k) {
        altavoz();
        String num = numero(k);
        String nom = Reglas.nombreContacto(this, num);
        boolean ok = Grabador.empezar(this, nom != null ? nom : num, "Llamadas");
        cambio();
        return ok;
    }

    String pararGrabacion() {
        String n = Grabador.parar(this);
        if (n != null) h.post(() -> Toast.makeText(this, "✅ Llamada grabada en Descargas/MiBarra/Llamadas", Toast.LENGTH_LONG).show());
        cambio();
        return n;
    }

    void avisarCambio() {
        avisar();
        cambio();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        yo = this;
        NotificationChannel ch = new NotificationChannel(CANAL, "Llamadas", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ch.enableVibration(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public void onDestroy() {
        if (yo == this) yo = null;
        super.onDestroy();
    }

    static String numero(Call k) {
        try {
            if (k.getDetails().getHandle() == null) return "";
            return k.getDetails().getHandle().getSchemeSpecificPart();
        } catch (Exception e) {
            return "";
        }
    }

    static boolean visible(Call k) {
        return k.getDetails().getHandlePresentation() == TelecomManager.PRESENTATION_ALLOWED;
    }

    @SuppressWarnings("deprecation")
    static int estado(Call k) {
        return k.getState();
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        if (estado(call) == Call.STATE_RINGING) {
            String num = numero(call);
            Reglas.Decision d = Reglas.telefono(this, num, visible(call));
            if (d.bloquear) {
                callarTimbre();
                try { call.reject(false, null); } catch (Exception ignored) {}
                String nombre = Reglas.nombreContacto(this, num);
                Reglas.anotar(this, nombre == null ? "" : nombre, num, d.motivo);
                return;
            }
        }
        llamadas.add(call);
        call.registerCallback(cb);
        avisar();
        abrirPantalla(false);
        cambio();
        if (estado(call) == Call.STATE_RINGING && Reglas.p(this).getBoolean("contestador", true)) {
            int seg = Reglas.p(this).getInt("segContestador", 20);
            h.postDelayed(() -> {
                if (llamadas.contains(call) && estado(call) == Call.STATE_RINGING) irAlContestador(call);
            }, seg * 1000L);
        }
        if (estado(call) == Call.STATE_ACTIVE) alActivarse(call);
    }

    /** Contesta y deja que el contestador atienda. */
    void irAlContestador(Call call) {
        if (estado(call) != Call.STATE_RINGING) return;
        alContestador.add(call);
        callarTimbre();
        contestar(call);
        avisar();
        cambio();
    }

    /** Usted toma la llamada que estaba atendiendo el contestador. */
    void tomarLlamada(Call call) {
        if (!alContestador.remove(call)) return;
        Contestador.tomar(this);
        if (Reglas.p(this).getBoolean("grabarTodas", true)) grabar(call);
        avisar();
        cambio();
    }

    void callarTimbre() {
        try {
            getSystemService(TelecomManager.class).silenceRinger();
        } catch (Exception ignored) {}
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        call.unregisterCallback(cb);
        llamadas.remove(call);
        if (alContestador.remove(call)) {
            Contestador.terminar(this);
        } else if (Grabador.grabando() && principal() == null) {
            pararGrabacion();
        }
        avisar();
        cambio();
    }

    static Call principal() {
        for (Call k : llamadas) if (estado(k) == Call.STATE_RINGING) return k;
        for (Call k : llamadas) if (estado(k) != Call.STATE_DISCONNECTED) return k;
        return null;
    }

    static void contestar(Call k) {
        try { k.answer(VideoProfile.STATE_AUDIO_ONLY); } catch (Exception ignored) {}
    }

    static void colgar(Call k) {
        try {
            if (estado(k) == Call.STATE_RINGING) k.reject(false, null);
            else k.disconnect();
        } catch (Exception ignored) {}
    }

    private void cambio() {
        Runnable r = alCambiar;
        if (r != null) h.post(r);
    }

    void abrirPantalla(boolean contestar) {
        try {
            Intent i = new Intent(this, LlamadaActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (contestar) i.putExtra("contestar", true);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    void avisar() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        Call k = principal();
        if (k == null) {
            nm.cancel(AVISO);
            return;
        }
        String num = numero(k);
        String nombre = Reglas.nombreContacto(this, num);
        String titulo = nombre != null ? nombre : (num.isEmpty() ? "Número privado" : Reglas.formato(num));
        boolean entrando = estado(k) == Call.STATE_RINGING;
        String texto;
        if (entrando) texto = "Llamada entrante";
        else if (Contestador.atendiendo(k)) texto = "📼 El contestador está atendiendo";
        else if (Grabador.grabando()) texto = "🔴 En llamada · grabando";
        else texto = "En llamada";

        Intent abrir = new Intent(this, LlamadaActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent piAbrir = PendingIntent.getActivity(this, 1, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent cont = new Intent(this, LlamadaActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("contestar", true);
        PendingIntent piCont = PendingIntent.getActivity(this, 2, cont,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent piColgar = PendingIntent.getBroadcast(this, 3,
                new Intent(this, AccionReceiver.class).setAction(AccionReceiver.COLGAR),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent piContestador = PendingIntent.getBroadcast(this, 4,
                new Intent(this, AccionReceiver.class).setAction(AccionReceiver.CONTESTADOR),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CANAL)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setColor(0xFF185FA5)
                .setContentIntent(piAbrir);
        if (entrando) {
            b.setFullScreenIntent(piAbrir, true);
            b.addAction(new Notification.Action.Builder(null, "📼 Contestador", piContestador).build());
            b.addAction(new Notification.Action.Builder(null, "Contestar", piCont).build());
        } else {
            b.setUsesChronometer(true);
            b.addAction(new Notification.Action.Builder(null, "Colgar", piColgar).build());
        }
        try { nm.notify(AVISO, b.build()); } catch (Exception ignored) {}
    }
}
