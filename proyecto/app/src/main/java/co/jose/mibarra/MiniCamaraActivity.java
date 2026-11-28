package co.jose.mibarra;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

/** Puerta invisible: pide el permiso de cámara si falta y abre la mini cámara flotante. */
public class MiniCamaraActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 1);
        } else empezar();
    }

    @Override
    public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) empezar();
        else finish();
    }

    private void empezar() {
        try { startForegroundService(new Intent(this, MiniCamaraService.class)); } catch (Exception ignored) {}
        finish();
    }
}
