package co.jose.mibarra;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Versión 41: una sola opción «📍 Mi Barra» al compartir (antes salían 5: Rutas, GPS, Mis lugares,
 * Buscar precio y Pedir transporte). Lo que llega se guarda en Mis direcciones y él escoge para qué es.
 */
public class CompartirActivity extends Base {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("📍 Mi Barra");
        Intent llega = getIntent();
        String txt = llega == null ? null : llega.getStringExtra(Intent.EXTRA_TEXT);
        if (txt == null || txt.trim().isEmpty()) { aviso("No llegó nada para Mi Barra"); finish(); return; }

        boolean esMapa = txt.contains("maps.") || txt.contains("goo.gl") || txt.contains("geo:")
                || ParadasActivity.coordenadas(txt) != null;
        String primera = "";
        for (String linea : txt.split("\n")) {
            String t = linea.trim();
            if (!t.isEmpty() && !t.startsWith("http")) { primera = t; break; }
        }
        if (primera.isEmpty()) primera = esMapa ? "Punto de Google Maps" : txt.trim();
        if (primera.length() > 90) primera = primera.substring(0, 90) + "…";

        // se guarda de una vez en la libreta única de direcciones
        if (esMapa) {
            double[] ll = ParadasActivity.coordenadas(txt);
            Direcciones.agregar(this, primera, "", ll != null ? ll[0] : Double.NaN, ll != null ? ll[1] : Double.NaN);
        }

        LinearLayout l = vertical();
        l.setPadding(dp(16), dp(14), dp(16), dp(16));
        l.setBackgroundColor(Color.WHITE);
        TextView t = new TextView(this);
        t.setText("📍 Llegó: " + primera);
        t.setTextSize(19);
        t.setTextColor(Color.BLACK);
        l.addView(t);
        TextView n = new TextView(this);
        n.setText(esMapa ? "✔ Guardada en Mis direcciones (GPS). ¿Para qué es?" : "¿Qué quiere hacer con esto?");
        n.setTextSize(16);
        n.setTextColor(Color.BLACK);
        n.setPadding(0, dp(6), 0, dp(10));
        l.addView(n);

        LinearLayout f1 = horizontal();
        f1.addView(opcion("🧭 Ir (GPS)", 0xFF1565C0, GpsActivity.class, llega), peso(1));
        f1.addView(opcion("🗺 A Rutas", 0xFF00838F, ParadasActivity.class, llega), peso(1));
        l.addView(f1, ancho(60));
        LinearLayout f2 = horizontal();
        f2.addView(opcion("⭐ Mis lugares", 0xFF6A1B9A, LugaresActivity.class, llega), peso(1));
        f2.addView(opcion("🚕 Transporte", 0xFF2E7D32, TransporteActivity.class, llega), peso(1));
        l.addView(f2, ancho(60));
        Button precio = opcion("💲 Buscar precio de un producto", 0xFFEF6C00, PreciosActivity.class, llega);
        l.addView(precio, ancho(60));
        Button cerrar = boton(esMapa ? "✔ Solo guardar" : "✖ Cerrar", 0xFF616161);
        cerrar.setTextSize(17);
        cerrar.setOnClickListener(v -> finish());
        l.addView(cerrar, ancho(56));

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private Button opcion(String texto, int color, Class<?> destino, Intent llega) {
        Button b = boton(texto, color);
        b.setTextSize(17);
        b.setOnClickListener(v -> {
            // se pasa tal cual llegó, como si lo hubiera compartido directo a esa pantalla
            Intent i = new Intent(llega);
            i.setComponent(null);
            i.setClass(this, destino);
            i.setAction(Intent.ACTION_SEND);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception e) { aviso("No se pudo abrir"); }
            finish();
        });
        return b;
    }
}
