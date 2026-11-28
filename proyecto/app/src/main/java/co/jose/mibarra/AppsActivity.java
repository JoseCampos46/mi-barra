package co.jose.mibarra;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Todas las apps del celular en orden alfabético, con letras y buscador. Toque una para abrirla. */
public class AppsActivity extends Base {

    static final class App { String nombre, paquete, clase; Drawable icono; }
    private final List<App> apps = new ArrayList<>();
    private LinearLayout lista;
    private EditText buscar;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = vertical();
        l.setPadding(dp(10), dp(8), dp(10), dp(8));
        buscar = campoTexto("🔎 Buscar app…", "");
        buscar.setSingleLine(true);
        buscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { pintar(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        l.addView(buscar);
        lista = vertical();
        l.addView(lista);
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
        TextView cargando = new TextView(this);
        cargando.setText("Cargando apps…");
        lista.addView(cargando);
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ResolveInfo> rs = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            List<App> r = new ArrayList<>();
            for (ResolveInfo ri : rs) {
                App a = new App();
                a.nombre = String.valueOf(ri.loadLabel(pm)).trim();
                a.paquete = ri.activityInfo.packageName;
                a.clase = ri.activityInfo.name;
                try { a.icono = ri.loadIcon(pm); } catch (Exception ignored) {}
                r.add(a);
            }
            r.sort((x, y) -> SaludosActivity.sinTildes(x.nombre).compareTo(SaludosActivity.sinTildes(y.nombre)));
            runOnUiThread(() -> { apps.clear(); apps.addAll(r); pintar(); });
        }).start();
    }

    private void pintar() {
        lista.removeAllViews();
        String q = SaludosActivity.sinTildes(buscar.getText().toString().trim());
        char letra = 0;
        for (App a : apps) {
            String n = SaludosActivity.sinTildes(a.nombre);
            if (!q.isEmpty() && !n.contains(q)) continue;
            char c = n.isEmpty() ? '#' : Character.toUpperCase(n.charAt(0));
            if (!Character.isLetter(c)) c = '#';
            if (c != letra && q.isEmpty()) {
                letra = c;
                TextView t = new TextView(this);
                t.setText(String.valueOf(c));
                t.setTextSize(22);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setTextColor(0xFF1565C0);
                t.setPadding(dp(6), dp(12), 0, dp(2));
                lista.addView(t);
            }
            LinearLayout f = horizontal();
            f.setGravity(Gravity.CENTER_VERTICAL);
            f.setPadding(dp(6), dp(6), dp(6), dp(6));
            ImageView iv = new ImageView(this);
            if (a.icono != null) iv.setImageDrawable(a.icono);
            f.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView t = new TextView(this);
            t.setText(a.nombre);
            t.setTextSize(18);
            t.setTextColor(Color.BLACK);
            t.setPadding(dp(14), 0, 0, 0);
            f.addView(t, peso(1));
            f.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(a.paquete, a.clase).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                } catch (Exception e) { aviso("No se pudo abrir " + a.nombre); }
            });
            f.setOnLongClickListener(v -> {
                try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + a.paquete))); }
                catch (Exception ignored) {}
                return true;
            });
            lista.addView(f);
        }
    }
}
