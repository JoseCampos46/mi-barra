package co.jose.mibarra;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Activar el Teclado Mi Barra en 3 pasos, el micrófono y el dictado sin conexión. */
public class TecladoAjustesActivity extends Base {

    @Override protected void onResume() { super.onResume(); pintar(); }

    private boolean activado() {
        InputMethodManager im = getSystemService(InputMethodManager.class);
        for (InputMethodInfo i : im.getEnabledInputMethodList()) if (i.getPackageName().equals(getPackageName())) return true;
        return false;
    }

    private boolean elegido() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return s != null && s.startsWith(getPackageName());
    }

    private void pintar() {
        LinearLayout l = vertical();
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.addView(titulo("⌨ Teclado Mi Barra"));
        TextView t = new TextView(this);
        t.setText("Con sugerencias que aprenden sus palabras, signos a la vista, tildes dejando presionado, ◀ ▶ para mover el cursor y micrófono.");
        t.setTextSize(16);
        t.setPadding(0, dp(6), 0, dp(12));
        l.addView(t);
        boolean a = activado(), e = elegido();
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        Button b1 = boton((a ? "✅" : "1️⃣") + "  Activar el teclado", a ? 0xFF2E7D32 : 0xFF1565C0);
        b1.setOnClickListener(v -> {
            aviso("Prenda «Mi Barra» y acepte el aviso (es el aviso normal de Android para cualquier teclado)");
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        });
        l.addView(b1, ancho(58));
        Button b2 = boton((e ? "✅" : "2️⃣") + "  Escogerlo como teclado del celular", e ? 0xFF2E7D32 : 0xFF1565C0);
        b2.setOnClickListener(v -> getSystemService(InputMethodManager.class).showInputMethodPicker());
        l.addView(b2, ancho(58));
        Button b3 = boton((mic ? "✅" : "3️⃣") + "  Permitir el micrófono 🎤", mic ? 0xFF2E7D32 : 0xFF1565C0);
        b3.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1));
        l.addView(b3, ancho(58));
        // Versión 16: el español propio de Mi Barra (sin internet y sin Google)
        boolean voz = VozLocal.listo(this);
        Button b4 = boton((voz ? "✅  Español sin internet: listo" : "4️⃣  ⬇ Descargar español sin internet (40 MB)"),
                voz ? 0xFF2E7D32 : 0xFF6A1B9A);
        TextView estado = new TextView(this);
        estado.setTextSize(15);
        estado.setPadding(0, dp(4), 0, dp(8));
        estado.setText(voz ? "El micrófono del teclado escucha sin Wi‑Fi, sin datos y sin Google."
                : "Hágalo con Wi‑Fi. Se descarga una sola vez y queda guardado en el celular.");
        android.widget.ProgressBar barra = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barra.setMax(100);
        barra.setVisibility(android.view.View.GONE);
        final boolean[] bajando = {false};
        b4.setOnClickListener(v -> {
            if (bajando[0]) return;
            if (VozLocal.listo(this)) {
                new android.app.AlertDialog.Builder(this).setTitle("Español sin internet")
                        .setMessage("Ya está descargado. ¿Quiere volver a descargarlo (actualizar)?")
                        .setPositiveButton("Actualizar", (d, w) -> bajar(b4, estado, barra, bajando))
                        .setNegativeButton("No", null).show();
            } else bajar(b4, estado, barra, bajando);
        });
        l.addView(b4, ancho(58));
        l.addView(barra);
        l.addView(estado);
        TextView p = new TextView(this);
        p.setText("\nPruébelo aquí:");
        p.setTextSize(16);
        l.addView(p);
        EditText prueba = campoTexto("Escriba o hable…", "");
        prueba.setSingleLine(false);
        prueba.setMinLines(3);
        l.addView(prueba);
        Button borrar = boton("🧹  Olvidar las palabras aprendidas", 0xFF8D6E63);
        borrar.setOnClickListener(v -> { new java.io.File(getFilesDir(), "teclado_palabras.txt").delete(); aviso("Listo"); });
        l.addView(borrar, ancho(50));
        TextView n = new TextView(this);
        n.setText("Para volver al teclado de antes: paso 2 y escoja el otro.");
        n.setTextColor(0xFF78909C);
        l.addView(n);
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private void bajar(Button b, TextView estado, android.widget.ProgressBar barra, boolean[] bajando) {
        bajando[0] = true;
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        barra.setVisibility(android.view.View.VISIBLE);
        barra.setIndeterminate(true);
        b.setText("⏳  Descargando… no cierre esta pantalla");
        VozLocal.descargar(this, new VozLocal.Avance() {
            @Override public void va(int pct, String txt) {
                if (pct >= 0) { barra.setIndeterminate(false); barra.setProgress(pct); }
                estado.setText(txt);
            }
            @Override public void fin(boolean ok, String txt) {
                bajando[0] = false;
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                barra.setVisibility(android.view.View.GONE);
                aviso(txt);
                pintar();
            }
        });
    }
}
