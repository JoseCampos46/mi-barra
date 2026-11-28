package co.jose.mibarra;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;

/** Toda pantalla de Mi Barra tiene arriba a la derecha: — minimizar y ✕ cerrar. */
public abstract class VentanaActivity extends Activity {
    private static final int MINIMIZAR = 901, CERRAR = 902;

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, MINIMIZAR, 0, "—").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        m.add(0, CERRAR, 1, "✕").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        if (i.getItemId() == MINIMIZAR) {
            moveTaskToBack(true);
            return true;
        }
        if (i.getItemId() == CERRAR) {
            // Las pantallas guardan solas en onPause. La barra lateral sigue viva.
            finishAndRemoveTask();
            return true;
        }
        return super.onOptionsItemSelected(i);
    }
}
