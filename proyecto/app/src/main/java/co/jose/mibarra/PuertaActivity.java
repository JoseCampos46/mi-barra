package co.jose.mibarra;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** Versión 46: puerta invisible de ChpBar. ChpInicio (otra app, firmada igual) la usa para abrir
 *  las herramientas y manejar la grabadora. Se abre y se cierra en el mismo instante. */
public class PuertaActivity extends Activity {

    private static MediaPlayer mp;

    static boolean oyendo() { try { return mp != null && mp.isPlaying(); } catch (Exception e) { return false; } }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { hacer(getIntent()); } catch (Exception e) { Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_SHORT).show(); }
        finish();
        overridePendingTransition(0, 0);
    }

    private void hacer(Intent i) {
        String tipo = i.getStringExtra("puerta_tipo");
        Intent orig = i.getParcelableExtra("puerta_intent");
        if (tipo == null) return;
        switch (tipo) {
            case "actividad":
                if (orig == null) return;
                orig.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(orig);
                break;
            case "servicio":
                if (orig != null) startService(orig);
                break;
            case "servicio_fg":
                if (orig != null) startForegroundService(orig);
                break;
            case "escuchar":
                escuchar();
                break;
            case "parar_escuchar":
                parar();
                break;
        }
    }

    private void escuchar() {
        parar();
        String u = Grabadorita.ultima(this);
        if (u == null) { Toast.makeText(this, "Todavía no hay grabaciones", Toast.LENGTH_SHORT).show(); return; }
        try {
            mp = new MediaPlayer();
            mp.setDataSource(getApplicationContext(), Uri.parse(u));
            mp.setOnCompletionListener(m -> parar());
            mp.prepare();
            mp.start();
        } catch (Exception e) { parar(); Toast.makeText(this, "No se pudo oír", Toast.LENGTH_SHORT).show(); }
    }

    private static void parar() {
        if (mp != null) { try { mp.release(); } catch (Exception ignored) {} mp = null; }
    }
}
