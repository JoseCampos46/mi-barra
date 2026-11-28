package co.jose.mibarra;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Almanaque del mes: toque un día y ponga el recordatorio con su hora. Suena como alarma, aunque esté en silencio. */
public class RecordatoriosActivity extends Base {

    static final String[] MESES = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto",
            "Septiembre", "Octubre", "Noviembre", "Diciembre"};
    private final Calendar mes = Calendar.getInstance();
    private int diaSel;
    private LinearLayout raiz;
    private EditText textoDictado;
    private static final int VOZ = 95;
    private final Locale es = new Locale("es", "CO");

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mes.set(Calendar.DAY_OF_MONTH, 1);
        diaSel = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 4);
        Recordatorios.programar(this);
        pintar();
    }

    @Override protected void onResume() { super.onResume(); if (raiz != null) pintar(); }

    private void pintar() {
        raiz = vertical();
        raiz.setPadding(dp(8), dp(8), dp(8), dp(12));
        raiz.setBackgroundColor(0xFFF7F4EE);
        // Encabezado: ◀  Octubre 2026  ▶
        LinearLayout cab = horizontal();
        cab.setGravity(Gravity.CENTER_VERTICAL);
        Button ant = boton("◀", 0xFF5D4037);
        ant.setOnClickListener(v -> { mes.add(Calendar.MONTH, -1); diaSel = 1; pintar(); });
        TextView t = new TextView(this);
        t.setText(MESES[mes.get(Calendar.MONTH)] + " " + mes.get(Calendar.YEAR));
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF3E2723);
        t.setGravity(Gravity.CENTER);
        Button sig = boton("▶", 0xFF5D4037);
        sig.setOnClickListener(v -> { mes.add(Calendar.MONTH, 1); diaSel = 1; pintar(); });
        cab.addView(ant, new LinearLayout.LayoutParams(dp(56), dp(50)));
        cab.addView(t, peso(1));
        cab.addView(sig, new LinearLayout.LayoutParams(dp(56), dp(50)));
        raiz.addView(cab);
        Button hoy = boton("Hoy", 0xFF8D6E63);
        hoy.setTextSize(13);
        hoy.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            mes.setTimeInMillis(c.getTimeInMillis()); mes.set(Calendar.DAY_OF_MONTH, 1);
            diaSel = c.get(Calendar.DAY_OF_MONTH); pintar();
        });
        raiz.addView(hoy, new LinearLayout.LayoutParams(dp(80), dp(38)) {{ gravity = Gravity.CENTER_HORIZONTAL; }});

        // Días de la semana
        LinearLayout sem = horizontal();
        for (String d : new String[]{"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"}) {
            TextView x = new TextView(this);
            x.setText(d);
            x.setGravity(Gravity.CENTER);
            x.setTextSize(13);
            x.setTextColor(d.equals("Dom") ? 0xFFC62828 : 0xFF6D4C41);
            sem.addView(x, peso(1));
        }
        raiz.addView(sem);

        // Cuadrícula del mes
        List<Recordatorios.R> todos = visibles(Recordatorios.leer(this));
        Calendar k = (Calendar) mes.clone();
        int primero = (k.get(Calendar.DAY_OF_WEEK) + 5) % 7; // lunes = 0
        int dias = k.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar hoyC = Calendar.getInstance();
        boolean esteMes = hoyC.get(Calendar.YEAR) == mes.get(Calendar.YEAR) && hoyC.get(Calendar.MONTH) == mes.get(Calendar.MONTH);
        int celda = 0;
        LinearLayout fila = null;
        for (int i = 0; i < 42; i++) {
            if (i % 7 == 0) {
                if (i >= primero + dias) break;
                fila = horizontal();
                raiz.addView(fila, new LinearLayout.LayoutParams(-1, dp(56)));
            }
            int dia = i - primero + 1;
            TextView c = new TextView(this);
            c.setGravity(Gravity.CENTER);
            c.setTextSize(19);
            if (dia >= 1 && dia <= dias) {
                // Versión 30: los días pasados con recordatorio también quedan resaltados
                int cuantos = 0, pasados = 0;
                Calendar z = Calendar.getInstance();
                for (Recordatorios.R x : todos) {
                    z.setTimeInMillis(x.cuando);
                    if (z.get(Calendar.YEAR) == mes.get(Calendar.YEAR) && z.get(Calendar.MONTH) == mes.get(Calendar.MONTH)
                            && z.get(Calendar.DAY_OF_MONTH) == dia) { if (x.hecho || x.cuando < System.currentTimeMillis()) pasados++; else cuantos++; }
                }
                int tot = cuantos + pasados;
                c.setText(tot > 0 ? dia + "\n" + (tot == 1 ? "●" : tot == 2 ? "●●" : "●●●") : String.valueOf(dia));
                if (tot > 0) c.setTextSize(16);
                boolean esHoy = esteMes && dia == hoyC.get(Calendar.DAY_OF_MONTH);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(10));
                g.setColor(dia == diaSel ? 0xFF1565C0 : cuantos > 0 ? 0xFFFFCC80 : pasados > 0 ? 0xFFFFF59D : Color.WHITE);
                if (esHoy) g.setStroke(dp(3), 0xFFE65100);
                c.setBackground(g);
                c.setTextColor(dia == diaSel ? Color.WHITE : (i % 7 == 6) ? 0xFFC62828 : Color.BLACK);
                final int d = dia;
                c.setOnClickListener(v -> { diaSel = d; pintar(); });
                c.setOnLongClickListener(v -> { diaSel = d; nuevo(); return true; });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            fila.addView(c, lp);
            celda++;
        }

        // Recordatorios del día escogido
        Calendar d = (Calendar) mes.clone();
        d.set(Calendar.DAY_OF_MONTH, diaSel);
        String titulo = new SimpleDateFormat("EEEE d 'de' MMMM", es).format(d.getTime());
        TextView td = new TextView(this);
        td.setText("\n" + Character.toUpperCase(titulo.charAt(0)) + titulo.substring(1));
        td.setTextSize(19);
        td.setTypeface(Typeface.DEFAULT_BOLD);
        td.setTextColor(0xFF3E2723);
        raiz.addView(td);
        List<Recordatorios.R> delDia = visibles(Recordatorios.delDia(this, mes.get(Calendar.YEAR), mes.get(Calendar.MONTH), diaSel));
        if (delDia.isEmpty()) {
            TextView n = new TextView(this);
            n.setText("No hay recordatorios este día.");
            n.setTextColor(0xFF8D6E63);
            raiz.addView(n);
        }
        SimpleDateFormat hf = new SimpleDateFormat("h:mm a", es);
        for (Recordatorios.R x : delDia) {
            Button r = boton((x.hecho ? "✔ " : "⏰ ") + hf.format(new Date(x.cuando)) + "   " + x.texto
                    + (x.repetir > 0 ? "\n🔁 " + Recordatorios.REPETIR[x.repetir] : "")
                    + (ocultos().contains(String.valueOf(x.id)) ? "   🙈" : ""), x.hecho ? 0xFF455A64 : 0xFFEF6C00);
            r.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            r.setTextSize(16);
            r.setOnClickListener(v -> opciones(x));
            raiz.addView(r, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = dp(6); }});
        }
        // Versión 30: tabla de abonos por semana y ver/ocultar los escondidos
        LinearLayout extra = horizontal();
        Button ab = boton("📒 Abonos de la semana", 0xFF6A1B9A);
        ab.setTextSize(15);
        ab.setOnClickListener(v -> startActivity(new Intent(this, AbonosActivity.class)));
        extra.addView(ab, peso(1));
        int nOc = ocultos().size();
        Button vo = boton(verOcultos() ? "🙈 Esconder ocultos" : "👁 Ver ocultos (" + nOc + ")", 0xFF546E7A);
        vo.setTextSize(15);
        vo.setOnClickListener(v -> { getSharedPreferences("barra", MODE_PRIVATE).edit().putBoolean("verOcultos", !verOcultos()).apply(); pintar(); });
        extra.addView(vo, peso(1));
        raiz.addView(extra, new LinearLayout.LayoutParams(-1, dp(54)) {{ topMargin = dp(8); }});
        Button nuevo = boton("＋  Poner recordatorio este día", 0xFF2E7D32);
        nuevo.setTextSize(18);
        nuevo.setOnClickListener(v -> nuevo());
        raiz.addView(nuevo, new LinearLayout.LayoutParams(-1, dp(60)) {{ topMargin = dp(10); }});

        // Versión 22: todos los recordatorios del mes, por día (sin límite)
        List<Recordatorios.R> delMes = new ArrayList<>();
        Calendar zz = Calendar.getInstance();
        for (Recordatorios.R x : todos) {
            zz.setTimeInMillis(x.cuando);
            if (zz.get(Calendar.YEAR) == mes.get(Calendar.YEAR) && zz.get(Calendar.MONTH) == mes.get(Calendar.MONTH)) delMes.add(x);
        }
        delMes.sort((p1, p2) -> Long.compare(p1.cuando, p2.cuando));
        TextView tp = new TextView(this);
        tp.setText("\n📋 Todos los recordatorios de " + MESES[mes.get(Calendar.MONTH)].toLowerCase(es) + " (" + delMes.size() + ")");
        tp.setTextSize(18);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setTextColor(0xFF3E2723);
        raiz.addView(tp);
        if (delMes.isEmpty()) {
            TextView n = new TextView(this);
            n.setText("Este mes no tiene recordatorios.");
            n.setTextColor(0xFF8D6E63);
            raiz.addView(n);
        }
        SimpleDateFormat df = new SimpleDateFormat("EEEE d", es), hf2 = new SimpleDateFormat("h:mm a", es);
        String diaAnt = "";
        for (Recordatorios.R x : delMes) {
            String dd = df.format(new Date(x.cuando));
            if (!dd.equals(diaAnt)) {
                diaAnt = dd;
                TextView tdd = new TextView(this);
                tdd.setText("📅 " + Character.toUpperCase(dd.charAt(0)) + dd.substring(1));
                tdd.setTextSize(16);
                tdd.setTypeface(Typeface.DEFAULT_BOLD);
                tdd.setTextColor(0xFFE65100);
                tdd.setPadding(0, dp(10), 0, dp(2));
                raiz.addView(tdd);
            }
            TextView r = new TextView(this);
            r.setText((x.hecho ? "✔ " : "") + hf2.format(new Date(x.cuando)) + "   " + x.texto
                    + (x.repetir > 0 ? "   🔁 " + Recordatorios.REPETIR[x.repetir] : ""));
            r.setTextSize(16);
            r.setTextColor(Color.BLACK); // Versión 30: siempre en negro nítido, también los pasados
            r.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable gw = new GradientDrawable();
            gw.setColor(Color.WHITE);
            gw.setCornerRadius(dp(12));
            gw.setStroke(dp(1), 0xFFE0D6CC);
            r.setBackground(gw);
            r.setOnClickListener(v -> opciones(x));
            raiz.addView(r, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = dp(4); }});
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(raiz);
        setContentView(sv);
    }

    // ---------- Versión 30: ocultar recordatorios (por ejemplo los de Mi Salud) ----------
    private java.util.Set<String> ocultos() {
        return new java.util.HashSet<>(getSharedPreferences("barra", MODE_PRIVATE).getStringSet("recOcultos", new java.util.HashSet<>()));
    }

    private boolean verOcultos() { return getSharedPreferences("barra", MODE_PRIVATE).getBoolean("verOcultos", false); }

    private List<Recordatorios.R> visibles(List<Recordatorios.R> l) {
        if (verOcultos()) return l;
        java.util.Set<String> o = ocultos();
        List<Recordatorios.R> r = new ArrayList<>();
        for (Recordatorios.R x : l) if (!o.contains(String.valueOf(x.id))) r.add(x);
        return r;
    }

    private void nuevo() { editar(null); }

    private void editar(Recordatorios.R viejo) {
        LinearLayout f = vertical();
        f.setPadding(dp(16), dp(8), dp(16), 0);
        LinearLayout fl = horizontal();
        textoDictado = campoTexto("Ej: recoger la medicina en la EPS", viejo == null ? "" : viejo.texto);
        textoDictado.setTextSize(18);
        fl.addView(textoDictado, peso(1));
        Button mic = boton("🎤", 0xFFD81B60);
        mic.setOnClickListener(v -> {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO");
            try { startActivityForResult(i, VOZ); } catch (Exception e) { aviso("No hay dictado por voz"); }
        });
        fl.addView(mic, new LinearLayout.LayoutParams(dp(54), dp(50)));
        f.addView(fl);
        final Calendar c = Calendar.getInstance();
        if (viejo != null) c.setTimeInMillis(viejo.cuando);
        else {
            c.set(mes.get(Calendar.YEAR), mes.get(Calendar.MONTH), diaSel, 8, 0, 0);
            Calendar ahora = Calendar.getInstance();
            if (c.get(Calendar.DAY_OF_YEAR) == ahora.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == ahora.get(Calendar.YEAR)) {
                c.setTimeInMillis(ahora.getTimeInMillis() + 60 * 60000); c.set(Calendar.SECOND, 0);
            }
        }
        c.set(Calendar.MILLISECOND, 0);
        Button hora = boton("🕗  Hora: " + new SimpleDateFormat("h:mm a", es).format(c.getTime()), 0xFF1565C0);
        hora.setOnClickListener(v -> new TimePickerDialog(this, (tp, hh, mm) -> {
            c.set(Calendar.HOUR_OF_DAY, hh); c.set(Calendar.MINUTE, mm); c.set(Calendar.SECOND, 0);
            hora.setText("🕗  Hora: " + new SimpleDateFormat("h:mm a", es).format(c.getTime()));
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show());
        f.addView(hora, new LinearLayout.LayoutParams(-1, dp(54)) {{ topMargin = dp(8); }});
        final int[] rep = {viejo == null ? 0 : viejo.repetir};
        Button repe = boton("🔁  " + Recordatorios.REPETIR[rep[0]], 0xFF6D4C41);
        repe.setOnClickListener(v -> new AlertDialog.Builder(this).setItems(Recordatorios.REPETIR, (d, w) -> {
            rep[0] = w; repe.setText("🔁  " + Recordatorios.REPETIR[w]);
        }).show());
        f.addView(repe, new LinearLayout.LayoutParams(-1, dp(50)) {{ topMargin = dp(6); }});
        TextView n = new TextView(this);
        n.setText("Suena como alarma y lo dice en voz alta, aunque el celular esté en silencio.");
        n.setTextColor(0xFF8D6E63);
        n.setPadding(0, dp(8), 0, 0);
        f.addView(n);
        new AlertDialog.Builder(this).setTitle(viejo == null ? "📅 Nuevo recordatorio" : "✎ Cambiar recordatorio").setView(f)
                .setPositiveButton("Guardar", (d, w) -> {
                    String t = limpio(textoDictado.getText().toString());
                    if (t.isEmpty()) { aviso("Escriba qué le recuerdo"); return; }
                    List<Recordatorios.R> todos = Recordatorios.leer(this);
                    if (viejo != null) todos.removeIf(x -> x.id == viejo.id);
                    Recordatorios.R r = new Recordatorios.R();
                    r.id = viejo == null ? System.currentTimeMillis() : viejo.id;
                    r.cuando = c.getTimeInMillis(); r.texto = t; r.repetir = rep[0];
                    r.hecho = r.cuando < System.currentTimeMillis() - 60000;
                    todos.add(r);
                    Recordatorios.guardar(this, todos);
                    if (android.os.Build.VERSION.SDK_INT >= 31 && !getSystemService(android.app.AlarmManager.class).canScheduleExactAlarms()) {
                        aviso("Para que suene a la hora exacta, permita «Alarmas y recordatorios» a Mi Barra");
                        try { startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
                    } else aviso("📅 Listo. Le aviso " + new SimpleDateFormat("EEEE d 'a las' h:mm a", es).format(c.getTime()));
                    pintar();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void opciones(Recordatorios.R x) {
        new AlertDialog.Builder(this).setTitle(x.texto)
                .setItems(new String[]{"✎ Cambiar", x.hecho ? "↺ Marcar pendiente" : "✔ Ya lo hice", "🗑 Borrar", "💬 Mandar por WhatsApp",
                        ocultos().contains(String.valueOf(x.id)) ? "👁 Volver a mostrar" : "🙈 Ocultar (no se borra)"}, (d, w) -> {
                    List<Recordatorios.R> todos = Recordatorios.leer(this);
                    if (w == 0) { editar(x); return; }
                    if (w == 4) {
                        java.util.Set<String> o = ocultos();
                        String id = String.valueOf(x.id);
                        if (!o.remove(id)) o.add(id);
                        getSharedPreferences("barra", MODE_PRIVATE).edit().putStringSet("recOcultos", o).apply();
                        pintar();
                        return;
                    }
                    if (w == 3) {
                        textoWhatsapp("📅 Recordatorio: " + x.texto + "\n" + new SimpleDateFormat("EEEE d 'de' MMMM, h:mm a", es).format(new Date(x.cuando)));
                        return;
                    }
                    for (Recordatorios.R r : new ArrayList<>(todos)) if (r.id == x.id) {
                        if (w == 1) r.hecho = !r.hecho; else todos.remove(r);
                    }
                    Recordatorios.guardar(this, todos);
                    pintar();
                }).show();
    }

    @Override
    protected void onActivityResult(int r, int res, Intent d) {
        super.onActivityResult(r, res, d);
        if (r == VOZ && res == RESULT_OK && d != null && textoDictado != null) {
            ArrayList<String> rs = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (rs != null && !rs.isEmpty()) textoDictado.setText(SaludosActivity.arreglar(rs.get(0)).replaceAll("\\.$", ""));
        }
    }
}
