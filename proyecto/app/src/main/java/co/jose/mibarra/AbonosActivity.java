package co.jose.mibarra;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Versión 30: 📒 Abonos de la semana. Una tarjeta por persona con los 7 días (L M M J V S D):
 * en cada día se escribe lo que le abonó o pagó (o una ✔ si vino, como el control del aseo), y sale el total.
 * Se pasa de semana con ◀ ▶ y nada se borra.
 */
public class AbonosActivity extends Base {

    private static final String[] DIAS = {"L", "M", "M", "J", "V", "S", "D"};
    private final Calendar lunes = Calendar.getInstance();
    private SharedPreferences pr;
    private final Locale es = new Locale("es", "CO");

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pr = getSharedPreferences("abonos30", MODE_PRIVATE);
        lunes.setFirstDayOfWeek(Calendar.MONDAY);
        lunes.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        if (lunes.after(Calendar.getInstance())) lunes.add(Calendar.DAY_OF_MONTH, -7);
        pintar();
    }

    private String semana() { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(lunes.getTime()); }

    private List<String> personas() {
        List<String> l = new ArrayList<>();
        for (String x : pr.getString("personas", "Aseo").split("\\|")) if (!x.trim().isEmpty()) l.add(x);
        return l;
    }

    private void guardarPersonas(List<String> l) { pr.edit().putString("personas", String.join("|", l)).apply(); }

    private String clave(String persona, int dia) { return semana() + "|" + persona + "|" + dia; }

    private void pintar() {
        LinearLayout l = vertical();
        l.setBackgroundColor(0xFFF7F4EE);
        l.setPadding(dp(8), dp(8), dp(8), dp(12));
        LinearLayout cab = horizontal();
        cab.setGravity(Gravity.CENTER_VERTICAL);
        Button ant = boton("◀", 0xFF5D4037);
        ant.setOnClickListener(v -> { lunes.add(Calendar.DAY_OF_MONTH, -7); pintar(); });
        Calendar dom = (Calendar) lunes.clone();
        dom.add(Calendar.DAY_OF_MONTH, 6);
        TextView t = new TextView(this);
        SimpleDateFormat f = new SimpleDateFormat("d MMM", es);
        t.setText("Semana del " + f.format(lunes.getTime()) + " al " + f.format(dom.getTime()));
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.BLACK);
        t.setGravity(Gravity.CENTER);
        Button sig = boton("▶", 0xFF5D4037);
        sig.setOnClickListener(v -> { lunes.add(Calendar.DAY_OF_MONTH, 7); pintar(); });
        cab.addView(ant, new LinearLayout.LayoutParams(dp(52), dp(48)));
        cab.addView(t, peso(1));
        cab.addView(sig, new LinearLayout.LayoutParams(dp(52), dp(48)));
        l.addView(cab);
        TextView ay = new TextView(this);
        ay.setText("Escriba en cada día lo que abonó o pagó (o ✔ si vino). Deje presionado el nombre para quitarlo.");
        ay.setTextColor(0xFF5D4037);
        ay.setTextSize(13);
        ay.setPadding(0, dp(4), 0, dp(4));
        l.addView(ay);

        for (String p : personas()) l.addView(tarjeta(p));

        Button mas = boton("＋  Agregar persona", 0xFF2E7D32);
        mas.setOnClickListener(v -> {
            EditText e = campoTexto("Nombre (ej: Carlos, Aseo)", "");
            new AlertDialog.Builder(this).setTitle("＋ Persona").setView(e).setPositiveButton("Agregar", (d, w) -> {
                String n = e.getText().toString().trim().replace("|", " ");
                if (n.isEmpty()) return;
                List<String> ps = personas();
                if (!ps.contains(n)) ps.add(n);
                guardarPersonas(ps);
                pintar();
            }).setNegativeButton("Cancelar", null).show();
        });
        l.addView(mas, new LinearLayout.LayoutParams(-1, dp(54)) {{ topMargin = dp(10); }});
        Button wa = boton("💬  Mandar la semana por WhatsApp", 0xFF25A244);
        wa.setOnClickListener(v -> textoWhatsapp(resumen()));
        l.addView(wa, new LinearLayout.LayoutParams(-1, dp(54)) {{ topMargin = dp(6); }});
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private LinearLayout tarjeta(String p) {
        LinearLayout c = vertical();
        c.setBackground(Comun.fondo(this, Color.WHITE, 12));
        c.setPadding(dp(8), dp(6), dp(8), dp(8));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.topMargin = dp(8);
        c.setLayoutParams(cl);
        LinearLayout tit = horizontal();
        TextView n = new TextView(this);
        n.setText(p);
        n.setTextSize(18);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setTextColor(Color.BLACK);
        n.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("¿Quitar a " + p + " de la tabla?")
                    .setMessage("Lo escrito en semanas pasadas no se borra.")
                    .setPositiveButton("Quitar", (d, w) -> { List<String> ps = personas(); ps.remove(p); guardarPersonas(ps); pintar(); })
                    .setNegativeButton("No", null).show();
            return true;
        });
        tit.addView(n, peso(1));
        TextView tot = new TextView(this);
        tot.setTextSize(18);
        tot.setTypeface(Typeface.DEFAULT_BOLD);
        tot.setTextColor(0xFF1B5E20);
        tit.addView(tot);
        c.addView(tit);
        LinearLayout dias = horizontal();
        LinearLayout vals = horizontal();
        EditText[] es7 = new EditText[7];
        Runnable sumar = () -> {
            long s = 0;
            for (EditText e : es7) if (e != null) s += Util.valor(e.getText().toString());
            tot.setText(s > 0 ? "Total " + Util.pesos(s) : "");
        };
        for (int i = 0; i < 7; i++) {
            TextView d = new TextView(this);
            d.setText(DIAS[i]);
            d.setGravity(Gravity.CENTER);
            d.setTextColor(i == 6 ? 0xFFC62828 : Color.BLACK);
            d.setTypeface(Typeface.DEFAULT_BOLD);
            dias.addView(d, peso(1));
            EditText e = new EditText(this);
            e.setText(pr.getString(clave(p, i), ""));
            e.setTextSize(13);
            e.setTextColor(Color.BLACK);
            e.setGravity(Gravity.CENTER);
            e.setSingleLine(true);
            e.setPadding(dp(2), dp(6), dp(2), dp(6));
            e.setBackground(Comun.fondo(this, 0xFFF1F4F7, 6));
            final int k = i;
            e.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence a, int b, int c2, int d2) {}
                @Override public void onTextChanged(CharSequence a, int b, int c2, int d2) {}
                @Override public void afterTextChanged(Editable x) { pr.edit().putString(clave(p, k), x.toString().trim()).apply(); sumar.run(); }
            });
            es7[i] = e;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.setMargins(dp(2), dp(2), dp(2), 0);
            vals.addView(e, lp);
        }
        c.addView(dias);
        c.addView(vals);
        sumar.run();
        return c;
    }

    private String resumen() {
        SimpleDateFormat f = new SimpleDateFormat("d MMM", es);
        Calendar dom = (Calendar) lunes.clone();
        dom.add(Calendar.DAY_OF_MONTH, 6);
        StringBuilder b = new StringBuilder("📒 *Abonos " + f.format(lunes.getTime()) + " – " + f.format(dom.getTime()) + "*\n");
        String[] nombres = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
        for (String p : personas()) {
            b.append("\n*").append(p).append("*: ");
            long s = 0;
            List<String> partes = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                String v = pr.getString(clave(p, i), "");
                if (v.isEmpty()) continue;
                partes.add(nombres[i] + " " + v);
                s += Util.valor(v);
            }
            b.append(partes.isEmpty() ? "—" : String.join(", ", partes));
            if (s > 0) b.append(" · Total ").append(Util.pesos(s));
        }
        return b.toString();
    }
}
