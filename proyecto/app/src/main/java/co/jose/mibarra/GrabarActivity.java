package co.jose.mibarra;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Versión 15: graba SIEMPRE sin sonido, sin preguntar.
 * Pide el permiso de Android (lo exige cada vez) ya escogiendo "pantalla completa",
 * y al terminar vuelve a la app que estaba viendo (no al escritorio).
 */
public class GrabarActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null) return;
        MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent pedir;
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                pedir = m.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } catch (Throwable t) {
                pedir = m.createScreenCaptureIntent();
            }
        } else {
            pedir = m.createScreenCaptureIntent();
        }
        try {
            startActivityForResult(pedir, 1);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo grabar la pantalla", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent datos) {
        super.onActivityResult(pedido, resultado, datos);
        if (resultado == RESULT_OK && datos != null) {
            startForegroundService(new Intent(this, GrabarService.class)
                    .putExtra("codigo", resultado).putExtra("datos", datos).putExtra("audio", false));
        } else {
            Toast.makeText(this, "Grabación cancelada", Toast.LENGTH_SHORT).show();
        }
        finishAndRemoveTask();
        overridePendingTransition(0, 0);
    }
}
