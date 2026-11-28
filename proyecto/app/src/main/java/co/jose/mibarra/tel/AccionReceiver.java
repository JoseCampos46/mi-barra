package co.jose.mibarra.tel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.Call;

/** Versión 37: botones "Colgar" y "Contestador" del aviso de llamada. */
public class AccionReceiver extends BroadcastReceiver {
    static final String COLGAR = "co.jose.mibarra.tel.COLGAR";
    static final String CONTESTADOR = "co.jose.mibarra.tel.CONTESTADOR";

    @Override
    public void onReceive(Context c, Intent i) {
        Call k = LlamadaService.principal();
        if (k == null) return;
        if (COLGAR.equals(i.getAction())) LlamadaService.colgar(k);
        else if (CONTESTADOR.equals(i.getAction()) && LlamadaService.yo != null) LlamadaService.yo.irAlContestador(k);
    }
}
