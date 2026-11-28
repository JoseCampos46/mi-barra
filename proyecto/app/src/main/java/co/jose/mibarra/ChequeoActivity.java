package co.jose.mibarra;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Antes de salir: lista de revisión (moto, carro o bici), pico y placa de hoy y gasolina para el viaje. */
public class ChequeoActivity extends Base {

    static final String[] VEHICULOS = {"🏍 Moto", "🚗 Carro", "🚲 Bici"};
    static final String[][] LISTAS = {
        {"Llantas: aire y que no estén lisas", "Frenos delantero y trasero", "Luces: delantera, stop y direccionales", "Pito",
         "Cadena: tensión y aceite", "Aceite del motor", "Gasolina", "Espejos", "Casco y chaleco", "SOAT y técnico-mecánica al día",
         "Licencia y tarjeta de propiedad", "Impermeable", "Herramienta y parches", "Celular cargado / cargador"},
        {"Llantas y llanta de repuesto", "Frenos", "Luces y direccionales", "Aceite del motor", "Agua / refrigerante", "Líquido de frenos",
         "Limpiabrisas y agua del limpiabrisas", "Gasolina", "Kit de carretera (gato, cruceta, conos, botiquín, extintor)",
         "SOAT y técnico-mecánica al día", "Licencia y tarjeta de propiedad", "Cinturones", "Celular cargado / cargador"},
        {"Aire de las llantas", "Frenos", "Cadena con aceite", "Casco", "Luz delantera y trasera", "Agua para tomar",
         "Neumático de repuesto o parches", "Bomba de aire", "Candado", "Celular cargado"}};
    static final String[] DIAS = {"Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"};

    private SharedPreferences prefs;
    private LinearLayout l;
    private int veh;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("barra", MODE_PRIVATE);
        veh = prefs.getInt("chequeoVeh", 0);
        pintar();
    }

    private List<String> items() {
        String guardado = prefs.getString("chequeoItems" + veh, "");
        if (guardado.isEmpty()) return new ArrayList<>(Arrays.asList(LISTAS[veh]));
        return new ArrayList<>(Arrays.asList(guardado.split("\n")));
    }

    private void guardarItems(List<String> it) { prefs.edit().putString("chequeoItems" + veh, String.join("\n", it)).apply(); }

    private void pintar() {
        l = vertical();
        l.setPadding(dp(12), dp(10), dp(12), dp(14));
        LinearLayout f = horizontal();
        for (int i = 0; i < 3; i++) {
            final int k = i;
            Button bt = boton(VEHICULOS[i], i == veh ? 0xFF1565C0 : 0xFF546E7A);
            bt.setOnClickListener(v -> { veh = k; prefs.edit().putInt("chequeoVeh", k).apply(); pintar(); });
            f.addView(bt, peso(1));
        }
        l.addView(f, ancho(52));

        // Pico y placa de hoy (cada quien pone sus días, porque las reglas cambian por ciudad y por semestre)
        if (veh < 2) l.addView(picoYPlaca());

        l.addView(titulo("✅ Revisión antes de salir"));
        List<String> it = items();
        final List<CheckBox> cajas = new ArrayList<>();
        for (int i = 0; i < it.size(); i++) {
            final int k = i;
            CheckBox c = new CheckBox(this);
            c.setText(it.get(i));
            c.setTextSize(17);
            c.setPadding(dp(4), dp(6), 0, dp(6));
            c.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle(it.get(k)).setItems(new String[]{"🗑 Quitar de la lista"}, (d, w) -> {
                    it.remove(k); guardarItems(it); pintar();
                }).show();
                return true;
            });
            cajas.add(c);
            l.addView(c);
        }
        TextView ay = new TextView(this);
        ay.setText("Deje presionado un punto para quitarlo.");
        ay.setTextColor(0xFF78909C);
        l.addView(ay);
        LinearLayout f2 = horizontal();
        Button ag = boton("＋ Agregar", 0xFF455A64);
        ag.setOnClickListener(v -> {
            EditText e = campoTexto("Qué revisar", "");
            new AlertDialog.Builder(this).setTitle("Agregar a la lista").setView(e).setPositiveButton("Agregar", (d, w) -> {
                String s = limpio(e.getText().toString());
                if (!s.isEmpty()) { it.add(s); guardarItems(it); pintar(); }
            }).setNegativeButton("Cancelar", null).show();
        });
        Button listo = boton("✔ Revisar", 0xFF2E7D32);
        listo.setOnClickListener(v -> {
            List<String> faltan = new ArrayList<>();
            for (CheckBox c : cajas) if (!c.isChecked()) faltan.add(c.getText().toString());
            if (faltan.isEmpty()) aviso("✅ ¡Todo listo! Buen viaje y con cuidado.");
            else new AlertDialog.Builder(this).setTitle("⚠ Le falta revisar " + faltan.size())
                    .setMessage("• " + String.join("\n• ", faltan)).setPositiveButton("Entendido", null).show();
        });
        Button orig = boton("↺ Lista original", 0xFF6D4C41);
        orig.setTextSize(12);
        orig.setOnClickListener(v -> { prefs.edit().remove("chequeoItems" + veh).apply(); pintar(); });
        f2.addView(ag, peso(1));
        f2.addView(listo, peso(1.3f));
        f2.addView(orig, peso(1));
        l.addView(f2, ancho(54));

        if (veh < 2) l.addView(gasolina());
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private LinearLayout caja(int color) {
        LinearLayout c = vertical();
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, dp(4));
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout picoYPlaca() {
        String dias = prefs.getString("picoDias" + veh, "");
        String horario = prefs.getString("picoHora" + veh, "");
        String placa = prefs.getString("picoPlaca" + veh, "");
        int hoy = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
        boolean tiene = !dias.isEmpty() && dias.contains(String.valueOf(hoy));
        LinearLayout c = caja(dias.isEmpty() ? 0xFFECEFF1 : tiene ? 0xFFFFCDD2 : 0xFFC8E6C9);
        TextView t = new TextView(this);
        t.setTextSize(17);
        t.setTextColor(Color.BLACK);
        if (dias.isEmpty()) t.setText("🚦 Pico y placa: toque aquí para poner sus días.");
        else {
            StringBuilder ds = new StringBuilder();
            for (char ch : dias.toCharArray()) { if (ds.length() > 0) ds.append(", "); ds.append(DIAS[ch - '0']); }
            t.setText((tiene ? "⛔ HOY " + DIAS[hoy].toUpperCase(new Locale("es")) + " TIENE PICO Y PLACA" : "✅ Hoy " + DIAS[hoy].toLowerCase(new Locale("es")) + " puede circular")
                    + (placa.isEmpty() ? "" : "\nPlaca: " + placa) + "\nSus días: " + ds + (horario.isEmpty() ? "" : "\nHorario: " + horario));
        }
        c.addView(t);
        c.setOnClickListener(v -> editarPico());
        return c;
    }

    private void editarPico() {
        LinearLayout f = vertical();
        f.setPadding(dp(16), dp(8), dp(16), 0);
        EditText placa = campoTexto("Placa (opcional)", prefs.getString("picoPlaca" + veh, ""));
        EditText hora = campoTexto("Horario, ej: 6:00 a 20:00", prefs.getString("picoHora" + veh, ""));
        f.addView(placa);
        f.addView(hora);
        String dias = prefs.getString("picoDias" + veh, "");
        final CheckBox[] cs = new CheckBox[7];
        for (int i = 1; i <= 7; i++) {
            int d = i % 7;
            cs[d] = new CheckBox(this);
            cs[d].setText(DIAS[d]);
            cs[d].setChecked(dias.contains(String.valueOf(d)));
            f.addView(cs[d]);
        }
        TextView n = new TextView(this);
        n.setText("Las reglas cambian por ciudad y cada semestre: póngalas según lo que diga la alcaldía.");
        n.setTextColor(0xFF78909C);
        f.addView(n);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        new AlertDialog.Builder(this).setTitle("🚦 Mi pico y placa").setView(sv).setPositiveButton("Guardar", (d, w) -> {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 7; i++) if (cs[i].isChecked()) s.append(i);
            prefs.edit().putString("picoDias" + veh, s.toString()).putString("picoHora" + veh, hora.getText().toString().trim())
                    .putString("picoPlaca" + veh, placa.getText().toString().trim().toUpperCase(new Locale("es"))).apply();
            pintar();
        }).setNegativeButton("Cancelar", null).show();
    }

    private LinearLayout gasolina() {
        LinearLayout c = caja(0xFFFFF3E0);
        TextView t = new TextView(this);
        t.setText("⛽ Gasolina para el viaje");
        t.setTextSize(18);
        t.setTextColor(Color.BLACK);
        c.addView(t);
        EditText km = campo("Kilómetros del viaje", prefs.getString("gasKm", ""));
        EditText rinde = campo(veh == 0 ? "Km por galón (moto, ej. 90)" : "Km por galón (carro, ej. 35)", prefs.getString("gasRinde" + veh, ""));
        EditText precio = campo("Precio del galón ($)", prefs.getString("gasPrecio", ""));
        TextView res = new TextView(this);
        res.setTextSize(17);
        res.setTextColor(0xFF3E2723);
        res.setGravity(Gravity.CENTER);
        c.addView(km); c.addView(rinde); c.addView(precio);
        Button calc = boton("Calcular", 0xFFEF6C00);
        calc.setOnClickListener(v -> {
            prefs.edit().putString("gasKm", km.getText().toString()).putString("gasRinde" + veh, rinde.getText().toString())
                    .putString("gasPrecio", precio.getText().toString()).apply();
            double k = Util.cantidad(km.getText().toString()), r = Util.cantidad(rinde.getText().toString());
            long p = Util.valor(precio.getText().toString());
            if (k <= 0 || r <= 0) { res.setText("Escriba los km y cuánto rinde un galón"); return; }
            double gal = k / r;
            res.setText(String.format(Locale.US, "Necesita unos %s galones%s\n(con 10%% de más por si acaso: %s gal)",
                    CalculadoraActivity.dec(gal, 1), p > 0 ? "\n≈ " + Util.pesos(Math.round(gal * p)) : "", CalculadoraActivity.dec(gal * 1.1, 1)));
        });
        c.addView(calc, ancho(48));
        c.addView(res);
        return c;
    }

    private EditText campo(String pista, String v) {
        EditText e = campoTexto(pista, v);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setTextColor(Color.BLACK);
        return e;
    }
}
