package co.jose.mibarra;

import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

/** Versión 25: rechaza en silencio las llamadas según el modo escogido en Bloquear llamadas. */
public class FiltroLlamadas extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details d) {
        boolean bloquear = false;
        String num = d.getHandle() == null ? "" : d.getHandle().getSchemeSpecificPart();
        try {
            // Versión 37: los números de estafa de Mi Teléfono se cortan siempre, sin sonar
            if (d.getCallDirection() == Call.Details.DIRECTION_INCOMING && co.jose.mibarra.tel.Reglas.esBloqueado(this, num)) {
                bloquear = true;
            } else if (d.getCallDirection() == Call.Details.DIRECTION_INCOMING && Bloqueos.activo(this, "llamadas")) {
                int modo = Bloqueos.modo(this);
                if (modo == 0) bloquear = true;
                else if (modo == 1) bloquear = nombreDe(num) == null;
                else {
                    bloquear = true;
                    String n = Bloqueos.limpio(num);
                    for (String f : Bloqueos.favoritos(this)) if (!n.isEmpty() && Bloqueos.limpio(f).equals(n)) bloquear = false;
                }
            }
        } catch (Exception ignored) {}
        CallResponse.Builder r = new CallResponse.Builder();
        if (bloquear) {
            r.setDisallowCall(true).setRejectCall(true).setSkipNotification(true).setSkipCallLog(false);
            try (FileWriter w = new FileWriter(new java.io.File(getFilesDir(), "llamadas_bloqueadas.txt"), StandardCharsets.UTF_8, true)) {
                String nombre = nombreDe(num);
                w.write(System.currentTimeMillis() + "\t" + num + "\t" + (nombre == null ? "" : nombre) + "\n");
            } catch (Exception ignored) {}
        }
        respondToCall(d, r.build());
    }

    private String nombreDe(String num) {
        if (num == null || num.isEmpty()) return null;
        try (Cursor c = getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num)),
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return null;
    }
}
