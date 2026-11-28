package co.jose.mibarra;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/** Botones de Mi Barra en la cortina de arriba (donde están el Wi‑Fi y la linterna). Funcionan dentro de cualquier app. */
public final class Teselas {
    private Teselas() {}

    abstract static class Base extends TileService {
        abstract String accion();

        @Override public void onStartListening() {
            Tile t = getQsTile();
            if (t == null) return;
            t.setState(BarraService.instancia == null ? Tile.STATE_UNAVAILABLE : Tile.STATE_INACTIVE);
            t.updateTile();
        }

        void abrir(Class<?> c) {
            Intent i = new Intent(this, c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (android.os.Build.VERSION.SDK_INT >= 34)
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
            else startActivityAndCollapse(i);
        }

        @Override public void onClick() {
            BarraService b = BarraService.instancia;
            if (b == null) {
                Toast.makeText(this, "Primero active Mi Barra en Accesibilidad", Toast.LENGTH_LONG).show();
                abrir(MainActivity.class);
                return;
            }
            b.accionRapida(accion());
        }
    }

    public static class Pantallazo extends Base { String accion() { return "pantallazo"; } }
    public static class Word extends Base { String accion() { return "word"; } }
    public static class Luz extends Base { String accion() { return "luz"; } }
    public static class Menu extends Base { String accion() { return "menu"; } }

    public static class Grabar extends Base {
        String accion() { return ""; }
        @Override public void onStartListening() {
            Tile t = getQsTile();
            if (t == null) return;
            t.setState(GrabarService.grabando ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            t.updateTile();
        }
        @Override public void onClick() {
            if (GrabarService.grabando) startService(new Intent(this, GrabarService.class).setAction(GrabarService.PARAR));
            else abrir(GrabarActivity.class);
        }
    }
}
