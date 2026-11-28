package co.jose.mibarra;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * ChpRecordatorios: la lista de alarmas y recordatorios (medicamentos, pico y placa, citas…).
 * Arriba: ☰ (ayuda y ajustes), el nombre con su versión y la ✕ para cerrar.
 */
public class AlarmasActivity extends Activity {

    private static final int NEGRO = 0xFF111111, AZUL = 0xFF1565C0, NARANJA = 0xFFEF6C00, VERDE = 0xFF2E7D32, GRIS = 0xFF616161;
    private FrameLayout raiz;
    private List<Alarmas.A> lista = new ArrayList<>();
    private Alarmas.A editando;   // null = viendo la lista

    private int dp(float v) { return Util.dp(this, v); }
    private void aviso(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(NARANJA);
        raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.WHITE);
        setContentView(raiz);
        if (!getSharedPreferences("ajustes", MODE_PRIVATE).getBoolean("trajo47", false)) {
            getSharedPreferences("ajustes", MODE_PRIVATE).edit().putBoolean("trajo47", true).apply();
            int n = Alarmas.traerDeChpBar(this);
            if (n > 0) aviso("✔ Se pasaron " + n + " recordatorios que tenía en ChpBar");
        }
        Alarmas.programar(this);
        pedirPermisos();
    }

    @Override protected void onResume() { super.onResume(); if (editando == null) pintarLista(); }

    @Override
    public void onBackPressed() {
        if (editando != null) { editando = null; pintarLista(); return; }
        super.onBackPressed();
    }

    // ---------------- Barra de arriba: ☰ · nombre · ✕ ----------------

    private LinearLayout barra(String titulo) {
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setBackgroundColor(NARANJA);
        f.setPadding(dp(6), dp(6), dp(6), dp(6));
        TextView menu = texto("☰", 26, Color.WHITE, true);
        menu.setPadding(dp(10), 0, dp(10), 0);
        menu.setOnClickListener(v -> menu());
        f.addView(menu);
        TextView t = texto(titulo, 19, Color.WHITE, true);
        f.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView x = texto("✕", 18, Color.WHITE, true);
        x.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xFFC62828);
        g.setStroke(dp(2), Color.WHITE);
        x.setBackground(g);
        x.setOnClickListener(v -> finishAndRemoveTask());
        f.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return f;
    }

    private void menu() {
        String[] op = {"❓  Cómo funcionan las alarmas", "⏰  Permiso de alarmas exactas", "🔋  Que el celular no la apague (batería)",
                "📥  Traer otra vez los recordatorios de ChpBar", "📄  Escáner", "✉  Escribirle a Claude"};
        new AlertDialog.Builder(this).setTitle("☰  ChpRecordatorios v" + Util.version(this)).setItems(op, (d, w) -> {
            switch (w) {
                case 0: ayuda(); break;
                case 1: permisoExactas(true); break;
                case 2: bateria(); break;
                case 3: { int n = Alarmas.traerDeChpBar(this); aviso(n < 0 ? "No se pudo leer ChpBar (¿está instalada?)" : n == 0 ? "No había recordatorios nuevos" : "✔ Se pasaron " + n); pintarLista(); break; }
                case 4: try { startActivity(new Intent(this, EscanerActivity.class)); } catch (Exception e) { aviso("No se pudo abrir el escáner"); } break;
                case 5: try { startActivity(new Intent(this, ClaudeActivity.class)); } catch (Exception e) { aviso("No se pudo abrir"); } break;
            }
        }).setNegativeButton("Cerrar", null).show();
    }

    private void ayuda() {
        new AlertDialog.Builder(this).setTitle("Cómo funcionan")
                .setMessage("1. Cada alarma tiene sus horas (puede poner varias: 6:00 a. m., 2:00 p. m., 6:00 p. m.).\n\n"
                        + "2. Escoja cómo se repite: todos los días, solo un día, los días que escoja, o cada mes.\n\n"
                        + "3. Al sonar, suena 1 minuto. Si no la apaga, vuelve a sonar a los 5 minutos. Después de 3 veces se cierra sola.\n\n"
                        + "4. Si toca APAGAR, se apaga del todo y solo vuelve a sonar en su próxima hora.\n\n"
                        + "5. Para que no vuelva a sonar nunca, toque el botón verde «Encendida» y queda «Apagada».")
                .setPositiveButton("Entendido", null).show();
    }

    private void pedirPermisos() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        permisoExactas(false);
    }

    private void permisoExactas(boolean siempre) {
        AlarmManager am = getSystemService(AlarmManager.class);
        boolean tiene = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (tiene && !siempre) return;
        if (tiene) { aviso("✔ Ya tiene el permiso de alarmas exactas"); return; }
        try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { aviso("Ábralo en Ajustes → Apps → ChpRecordatorios"); }
    }

    private void bateria() {
        try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) {} }
    }

    // ---------------- La lista ----------------

    private void pintarLista() {
        lista = Alarmas.leer(this);
        raiz.removeAllViews();
        LinearLayout col = vertical();
        col.addView(barra("ChpRecordatorios  v" + Util.version(this)));
        LinearLayout cuerpo = vertical();
        cuerpo.setPadding(dp(10), dp(8), dp(10), dp(16));

        Button nueva = boton("➕  Nueva alarma", NARANJA, 20);
        nueva.setOnClickListener(v -> {
            Alarmas.A a = new Alarmas.A();
            a.id = System.currentTimeMillis();
            a.fecha = Alarmas.iso(Calendar.getInstance());
            editar(a);
        });
        cuerpo.addView(nueva, new LinearLayout.LayoutParams(-1, dp(60)));

        if (lista.isEmpty()) {
            TextView t = texto("No hay alarmas todavía. Toque «Nueva alarma».", 18, NEGRO, false);
            t.setPadding(0, dp(24), 0, 0);
            t.setGravity(Gravity.CENTER);
            cuerpo.addView(t);
        }
        long ahora = System.currentTimeMillis();
        for (Alarmas.A a : lista) cuerpo.addView(tarjeta(a, ahora));

        ScrollView sv = new ScrollView(this);
        sv.addView(cuerpo);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        raiz.addView(col);
    }

    private View tarjeta(Alarmas.A a, long ahora) {
        LinearLayout t = vertical();
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(a.activa ? Color.WHITE : 0xFFF2F2F2);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), 0xFFBDBDBD);
        t.setBackground(g);
        LinearLayout.LayoutParams pt = new LinearLayout.LayoutParams(-1, -2);
        pt.topMargin = dp(10);
        t.setLayoutParams(pt);

        LinearLayout fila = new LinearLayout(this);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = texto(a.nombre.isEmpty() ? "(sin nombre)" : a.nombre, 20, NEGRO, true);
        fila.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView est = texto(a.activa ? "Encendida" : "Apagada", 15, Color.WHITE, true);
        est.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable ge = new GradientDrawable();
        ge.setColor(a.activa ? VERDE : GRIS);
        ge.setCornerRadius(dp(18));
        est.setBackground(ge);
        est.setOnClickListener(v -> {
            a.activa = !a.activa;
            Alarmas.guardar(this, lista);
            aviso(a.activa ? "🔔 " + a.nombre + " encendida" : "🔕 " + a.nombre + " apagada: no suena hasta que la encienda");
            pintarLista();
        });
        fila.addView(est);
        t.addView(fila);
        if (!a.detalle.isEmpty()) t.addView(texto(a.detalle, 16, NEGRO, false));

        StringBuilder hs = new StringBuilder();
        for (String h : a.horas) hs.append(hs.length() > 0 ? "   ·   " : "").append(Alarmas.horaBonita(h));
        TextView horas = texto(hs.length() == 0 ? "Sin horas" : "⏰  " + hs, 18, NEGRO, true);
        horas.setPadding(0, dp(6), 0, 0);
        t.addView(horas);
        t.addView(texto("🔁  " + Alarmas.textoRepetir(a), 16, NEGRO, false));
        long prox = Alarmas.proxima(a, ahora);
        if (a.activa) t.addView(texto(prox > 0 ? "Próxima: " + cuandoBonito(prox) : "Ya no vuelve a sonar", 15, AZUL, false));
        t.setOnClickListener(v -> editar(a));
        return t;
    }

    private String cuandoBonito(long t) {
        Calendar k = Calendar.getInstance(), hoy = Calendar.getInstance();
        k.setTimeInMillis(t);
        String dia;
        if (Alarmas.iso(k).equals(Alarmas.iso(hoy))) dia = "hoy";
        else { hoy.add(Calendar.DAY_OF_MONTH, 1); dia = Alarmas.iso(k).equals(Alarmas.iso(hoy)) ? "mañana" : Alarmas.fechaBonita(k); }
        return dia + " a las " + Alarmas.horaBonita(t);
    }

    // ---------------- Crear o cambiar una alarma ----------------

    private void editar(Alarmas.A original) {
        // Se trabaja sobre una copia: si cancela, no cambia nada
        Alarmas.A a = new Alarmas.A();
        a.id = original.id; a.nombre = original.nombre; a.detalle = original.detalle; a.horas = new ArrayList<>(original.horas);
        a.tipo = original.tipo; a.fecha = original.fecha.isEmpty() ? Alarmas.iso(Calendar.getInstance()) : original.fecha;
        a.dias = original.dias; a.activa = original.activa;
        editando = a;
        pintarEditor(a, Alarmas.buscar(lista, a.id) != null);
    }

    private void pintarEditor(Alarmas.A a, boolean existe) {
        raiz.removeAllViews();
        LinearLayout col = vertical();
        col.addView(barra(existe ? "Cambiar alarma" : "Nueva alarma"));
        LinearLayout c = vertical();
        c.setPadding(dp(12), dp(8), dp(12), dp(16));

        c.addView(etiqueta("Nombre (ej. Sucralfato, Pico y placa)"));
        EditText nombre = campo(a.nombre);
        c.addView(nombre);
        c.addView(etiqueta("Detalle (ej. 1 tableta en ayunas)"));
        EditText detalle = campo(a.detalle);
        c.addView(detalle);

        c.addView(etiqueta("Horas"));
        LinearLayout horas = new LinearLayout(this);
        horas.setOrientation(LinearLayout.VERTICAL);
        c.addView(horas);
        Runnable[] pintarHoras = new Runnable[1];
        pintarHoras[0] = () -> {
            horas.removeAllViews();
            LinearLayout fila = null;
            List<String> hs = new ArrayList<>(a.horas);
            hs.add("+");
            for (int i = 0; i < hs.size(); i++) {
                if (i % 3 == 0) { fila = new LinearLayout(this); horas.addView(fila); }
                String h = hs.get(i);
                TextView ch;
                if ("+".equals(h)) {
                    ch = chip("➕ hora", AZUL, false);
                    ch.setOnClickListener(v -> new TimePickerDialog(this, (tp, hh, mm) -> {
                        String nh = String.format(Locale.US, "%02d:%02d", hh, mm);
                        if (!a.horas.contains(nh)) a.horas.add(nh);
                        java.util.Collections.sort(a.horas);
                        pintarHoras[0].run();
                    }, 6, 0, false).show());
                } else {
                    ch = chip(Alarmas.horaBonita(h) + "  ✕", NEGRO, true);
                    ch.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(Alarmas.horaBonita(h))
                            .setItems(new String[]{"🕑  Cambiar la hora", "🗑  Quitar esta hora"}, (d, w) -> {
                                if (w == 1) { a.horas.remove(h); pintarHoras[0].run(); return; }
                                new TimePickerDialog(this, (tp, hh, mm) -> {
                                    a.horas.remove(h);
                                    String nh = String.format(Locale.US, "%02d:%02d", hh, mm);
                                    if (!a.horas.contains(nh)) a.horas.add(nh);
                                    java.util.Collections.sort(a.horas);
                                    pintarHoras[0].run();
                                }, Integer.parseInt(h.substring(0, 2)), Integer.parseInt(h.substring(3, 5)), false).show();
                            }).setNegativeButton("Cancelar", null).show());
                }
                fila.addView(ch, celda());
            }
            if (fila != null) for (int k = hs.size() % 3; k > 0 && k < 3; k++) fila.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        };
        pintarHoras[0].run();

        c.addView(etiqueta("Repetir"));
        LinearLayout rep = vertical();
        c.addView(rep);
        LinearLayout extra = vertical();
        c.addView(extra);
        Runnable[] pintarRep = new Runnable[1];
        pintarRep[0] = () -> {
            rep.removeAllViews();
            int[] orden = {Alarmas.DIARIO, Alarmas.UN_DIA, Alarmas.DIAS, Alarmas.MES};
            LinearLayout fila = null;
            for (int i = 0; i < orden.length; i++) {
                if (i % 2 == 0) { fila = new LinearLayout(this); rep.addView(fila); }
                int tipo = orden[i];
                TextView ch = chip(Alarmas.REPETIR[tipo], a.tipo == tipo ? Color.WHITE : NEGRO, a.tipo != tipo);
                if (a.tipo == tipo) {
                    GradientDrawable g = new GradientDrawable();
                    g.setColor(AZUL);
                    g.setCornerRadius(dp(10));
                    ch.setBackground(g);
                }
                ch.setOnClickListener(v -> { a.tipo = tipo; pintarRep[0].run(); });
                fila.addView(ch, celda());
            }
            extra.removeAllViews();
            if (a.tipo == Alarmas.UN_DIA || a.tipo == Alarmas.MES) {
                Calendar f = Alarmas.deIso(a.fecha);
                if (f == null) f = Calendar.getInstance();
                final Calendar ff = f;
                TextView fecha = chip("📅  " + (a.tipo == Alarmas.MES ? "Desde el " : "El ") + Alarmas.fechaBonita(ff) + "  (cambiar)", NEGRO, true);
                fecha.setOnClickListener(v -> new DatePickerDialog(this, (dp, y, m, d) -> {
                    Calendar k = Calendar.getInstance();
                    k.set(y, m, d);
                    a.fecha = Alarmas.iso(k);
                    pintarRep[0].run();
                }, ff.get(Calendar.YEAR), ff.get(Calendar.MONTH), ff.get(Calendar.DAY_OF_MONTH)).show());
                LinearLayout.LayoutParams pf = new LinearLayout.LayoutParams(-1, dp(52));
                pf.setMargins(dp(3), dp(3), dp(3), dp(3));
                extra.addView(fecha, pf);
            } else if (a.tipo == Alarmas.DIAS) {
                LinearLayout dias = new LinearLayout(this);
                for (int i = 0; i < 7; i++) {
                    int bit = 1 << i;
                    boolean si = (a.dias & bit) != 0;
                    TextView d = chip(Alarmas.DIAS_CORTOS[i], si ? Color.WHITE : NEGRO, !si);
                    if (si) {
                        GradientDrawable g = new GradientDrawable();
                        g.setColor(VERDE);
                        g.setCornerRadius(dp(10));
                        d.setBackground(g);
                    }
                    d.setOnClickListener(v -> { a.dias ^= bit; pintarRep[0].run(); });
                    dias.addView(d, celda());
                }
                extra.addView(dias);
                extra.addView(texto(Alarmas.textoRepetir(a), 15, NEGRO, false));
            }
        };
        pintarRep[0].run();

        LinearLayout botones = new LinearLayout(this);
        botones.setPadding(0, dp(18), 0, 0);
        Button guardar = boton("💾  Guardar", VERDE, 19);
        guardar.setOnClickListener(v -> {
            a.nombre = nombre.getText().toString().trim();
            a.detalle = detalle.getText().toString().trim();
            if (a.nombre.isEmpty()) { aviso("Escriba el nombre"); return; }
            if (a.horas.isEmpty()) { aviso("Ponga al menos una hora (➕ hora)"); return; }
            if (a.tipo == Alarmas.DIAS && a.dias == 0) { aviso("Escoja al menos un día"); return; }
            Alarmas.A viejo = Alarmas.buscar(lista, a.id);
            if (viejo != null) lista.set(lista.indexOf(viejo), a); else lista.add(a);
            a.activa = true;
            Alarmas.guardar(this, lista);
            long prox = Alarmas.proxima(a, System.currentTimeMillis());
            aviso(prox > 0 ? "✔ Guardada. Suena " + cuandoBonito(prox) : "✔ Guardada, pero esa fecha ya pasó");
            editando = null;
            pintarLista();
        });
        botones.addView(guardar, new LinearLayout.LayoutParams(0, dp(60), 1f));
        if (existe) {
            Button borrar = boton("🗑  Borrar", 0xFFC62828, 19);
            borrar.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("¿Borrar «" + a.nombre + "»?")
                    .setPositiveButton("Borrar", (d, w) -> {
                        Alarmas.A viejo = Alarmas.buscar(lista, a.id);
                        if (viejo != null) lista.remove(viejo);
                        Alarmas.guardar(this, lista);
                        editando = null;
                        pintarLista();
                    }).setNegativeButton("No", null).show());
            LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(0, dp(60), 1f);
            pb.leftMargin = dp(8);
            botones.addView(borrar, pb);
        }
        c.addView(botones);
        Button cancelar = boton("Cancelar", GRIS, 17);
        cancelar.setOnClickListener(v -> { editando = null; pintarLista(); });
        LinearLayout.LayoutParams pc = new LinearLayout.LayoutParams(-1, dp(52));
        pc.topMargin = dp(8);
        c.addView(cancelar, pc);

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        raiz.addView(col);
    }

    // ---------------- Piezas ----------------

    private LinearLayout.LayoutParams celda() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        return p;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView texto(String s, float tam, int color, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(tam);
        t.setTextColor(color);
        if (negrita) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView etiqueta(String s) {
        TextView t = texto(s, 15, NEGRO, true);
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private EditText campo(String v) {
        EditText e = new EditText(this);
        e.setText(v);
        e.setTextSize(19);
        e.setTextColor(NEGRO);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }

    private TextView chip(String s, int color, boolean borde) {
        TextView t = texto(s, 16, color, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(10));
        if (borde) g.setStroke(dp(1), 0xFF9E9E9E);
        t.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f);
        t.setLayoutParams(p);
        return t;
    }

    private Button boton(String s, int color, float tam) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(tam);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        return b;
    }
}
