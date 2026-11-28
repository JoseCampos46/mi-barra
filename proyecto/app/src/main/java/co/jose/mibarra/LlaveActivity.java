package co.jose.mibarra;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Versión 38: se pega una sola vez la llave gratis de Gemini; la usan ✨ Organizar y Mi Voz. */
public class LlaveActivity extends Base {

    private EditText ed;
    private TextView tNota;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("🔑 Llave de Gemini");
        LinearLayout l = vertical();
        l.setPadding(dp(14), dp(10), dp(14), dp(14));
        l.setBackgroundColor(Color.WHITE);

        l.addView(titulo("🔑 Llave gratis de Gemini"));
        TextView pasos = new TextView(this);
        pasos.setTextSize(17);
        pasos.setTextColor(Color.BLACK);
        pasos.setLineSpacing(0, 1.15f);
        pasos.setText("Se pega una sola vez. Es la misma llave que sacó para Mi Catálogo.\n\n"
                + "1. Toque «🌐 Abrir la página de la llave».\n"
                + "2. Entre con su Gmail.\n"
                + "3. Toque el ícono de copiar 📋 al lado de su llave (empieza por AQ. o por AIza).\n"
                + "4. Vuelva aquí y toque «📋 Pegar».\n"
                + "5. Toque «✔ Guardar y probar».");
        pasos.setPadding(0, dp(6), 0, dp(8));
        l.addView(pasos);

        Button abrir = boton("🌐 Abrir la página de la llave", 0xFF1565C0);
        abrir.setTextSize(17);
        abrir.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Gemini.PAGINA_LLAVE))); }
            catch (Exception e) { aviso("No se pudo abrir el navegador"); }
        });
        l.addView(abrir, ancho(56));

        ed = campoTexto("Pegue aquí la llave (AQ.… o AIza…)", Gemini.llave(this));
        ed.setTextSize(17);
        ed.setTextColor(Color.BLACK);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.topMargin = dp(10);
        l.addView(ed, ep);

        LinearLayout f = horizontal();
        Button pegar = boton("📋 Pegar", 0xFF455A64);
        pegar.setTextSize(17);
        pegar.setOnClickListener(v -> pegar());
        Button guardar = boton("✔ Guardar y probar", 0xFF2E7D32);
        guardar.setTextSize(17);
        guardar.setOnClickListener(v -> guardarYProbar());
        f.addView(pegar, peso(1));
        f.addView(guardar, peso(1.4f));
        l.addView(f, ancho(56));

        tNota = new TextView(this);
        tNota.setTextSize(17);
        tNota.setTextColor(Color.BLACK);
        tNota.setPadding(0, dp(12), 0, 0);
        tNota.setText(Gemini.hayLlave(this) ? "✅ Ya hay una llave guardada." : "Todavía no hay llave guardada.");
        l.addView(tNota);

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private void pegar() {
        try {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            ClipData c = cm == null ? null : cm.getPrimaryClip();
            if (c != null && c.getItemCount() > 0 && c.getItemAt(0).coerceToText(this) != null) {
                ed.setText(Gemini.limpiarLlave(c.getItemAt(0).coerceToText(this).toString()));
                ed.setSelection(ed.getText().length());
                return;
            }
        } catch (Exception ignored) {}
        aviso("No hay nada copiado. Copie la llave en la página de Google.");
    }

    private void guardarYProbar() {
        String k = Gemini.limpiarLlave(ed.getText().toString());
        ed.setText(k);
        if (k.length() < 30) { tNota.setText("⚠ Esa llave está incompleta. Debe empezar por AQ. o por AIza y ser larga."); return; }
        if (!Gemini.guardarLlave(this, k)) { tNota.setText("⚠ No se pudo guardar la llave."); return; }
        final boolean[] termino = {false};
        final long inicio = System.currentTimeMillis();
        final android.os.Handler hh = new android.os.Handler(android.os.Looper.getMainLooper());
        hh.post(new Runnable() {
            @Override public void run() {
                if (termino[0]) return;
                tNota.setText("⏳ Guardada. Probando con Gemini… " + ((System.currentTimeMillis() - inicio) / 1000) + " s");
                hh.postDelayed(this, 1000);
            }
        });
        Gemini.organizar(this, "buenas tardes don jose esto es una prueba del boton organizar", false, new Gemini.Respuesta() {
            @Override public void listo(String t) { runOnUiThread(() -> { termino[0] = true; tNota.setText("✅ ¡Funciona! Gemini respondió en " + ((System.currentTimeMillis() - inicio) / 1000) + " s:\n\n" + t); }); }
            @Override public void error(String m) { runOnUiThread(() -> { termino[0] = true; tNota.setText("⚠ " + m); }); }
        });
    }
}
