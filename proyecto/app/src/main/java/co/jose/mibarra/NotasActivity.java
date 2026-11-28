package co.jose.mibarra;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Mini Word: cuaderno de hojas sin límite, con negrilla, letra roja y resaltado suave. */
public class NotasActivity extends Base {

    private static final String SEPARADOR = "---TEXTO---";
    /** Colores de letra intensos. */
    static final int AZUL_I = 0xFF0050FF, VERDE_I = 0xFF00A020, ROJO_I = 0xFFE00000, NARANJA_I = 0xFFFF6A00;
    private File carpeta, actual;
    private EditText nombre, texto;
    private final ArrayDeque<CharSequence> historial = new ArrayDeque<>();
    private long ultimoGuardadoHistorial;
    private boolean restaurando;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        carpeta = new File(getFilesDir(), "notas");
        carpeta.mkdirs();
        mostrarLista();
    }

    @Override
    protected void onPause() {
        super.onPause();
        guardar(false);
    }

    @Override
    public void onBackPressed() {
        if (actual != null) { guardar(false); mostrarLista(); }
        else super.onBackPressed();
    }

    // ---------------- Lista ----------------

    private void mostrarLista() {
        actual = null;
        nombre = null;
        texto = null;
        LinearLayout l = vertical();
        int p = dp(16);
        l.setPadding(p, p, p, p);
        l.addView(titulo("📝 Mi cuaderno"));
        Button nueva = boton("＋  Nueva hoja", 0xFF2E7D32);
        nueva.setOnClickListener(v -> abrir(new File(carpeta, System.currentTimeMillis() + ".txt"), true));
        l.addView(nueva, ancho(56));
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        for (File f : archivos(carpeta)) {
            String n = "Hoja", primera = "";
            try {
                List<String> ls = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                if (!ls.isEmpty() && !ls.get(0).trim().isEmpty()) n = ls.get(0);
                int k = ls.indexOf(SEPARADOR);
                for (int i = k + 1; k >= 0 && i < ls.size(); i++)
                    if (!ls.get(i).trim().isEmpty()) { primera = ls.get(i).trim(); break; }
            } catch (Exception ignored) {}
            if (primera.length() > 40) primera = primera.substring(0, 40) + "…";
            Button bt = boton(n + "  ·  " + df.format(new Date(f.lastModified())) + (primera.isEmpty() ? "" : "\n" + primera), 0xFF37474F);
            bt.setOnClickListener(v -> abrir(f, false));
            l.addView(bt, ancho(72));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    // ---------------- Editor ----------------

    private void abrir(File f, boolean nueva) {
        actual = f;
        historial.clear();
        LinearLayout raiz = vertical();
        raiz.setPadding(dp(6), dp(6), dp(6), 0);

        LinearLayout arriba = horizontal();
        arriba.setGravity(Gravity.CENTER_VERTICAL);
        Button volver = boton("←", 0xFF546E7A);
        volver.setOnClickListener(v -> onBackPressed());
        arriba.addView(volver, new LinearLayout.LayoutParams(dp(52), dp(46)));
        nombre = campoTexto("Título de la hoja", "");
        nombre.setTextSize(18);
        nombre.setTypeface(Typeface.DEFAULT_BOLD);
        arriba.addView(nombre, peso(1));
        raiz.addView(arriba);

        LinearLayout h = horizontal();
        h.addView(herramienta("B", Color.WHITE, Color.BLACK, true, v -> negrilla()));
        h.addView(herramienta("A", Color.WHITE, AZUL_I, true, v -> letraColor(AZUL_I)));
        h.addView(herramienta("A", Color.WHITE, VERDE_I, true, v -> letraColor(VERDE_I)));
        h.addView(herramienta("A", Color.WHITE, ROJO_I, true, v -> letraColor(ROJO_I)));
        h.addView(herramienta("A", Color.WHITE, NARANJA_I, true, v -> letraColor(NARANJA_I)));
        h.addView(herramienta("  ", AMARILLO, Color.BLACK, false, v -> resaltar(AMARILLO)));
        h.addView(herramienta("  ", VERDE, Color.BLACK, false, v -> resaltar(VERDE)));
        h.addView(herramienta("  ", ROSADO, Color.BLACK, false, v -> resaltar(ROSADO)));
        h.addView(herramienta("✕ color", Color.WHITE, Color.BLACK, false, v -> quitarFormato()));
        h.addView(herramienta("↶ Deshacer", Color.WHITE, 0xFF1565C0, false, v -> deshacer()));
        h.addView(herramienta("✔ Corregir", 0xFF2E7D32, Color.WHITE, true, v -> corregir()));
        h.addView(herramienta("🎤 Dictar", 0xFFD81B60, Color.WHITE, true, v -> dictar()), 0);
        for (int i = 0; i < h.getChildCount(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            h.getChildAt(i).setLayoutParams(lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(h);
        raiz.addView(hs);

        texto = new EditText(this);
        texto.setTextSize(18);
        texto.setGravity(Gravity.TOP | Gravity.START);
        texto.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        texto.setSingleLine(false);
        texto.setHint("Escriba aquí… Para dar formato, seleccione el texto y toque B, una A de color o un resaltador.");
        texto.setBackgroundColor(Color.WHITE);
        texto.setPadding(dp(10), dp(10), dp(10), dp(10));
        texto.setVerticalScrollBarEnabled(true);
        raiz.addView(texto, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        raiz.addView(pie(() -> guardar(true), this::png, this::imprimir, this::whatsapp, null));
        setContentView(raiz);

        if (!nueva) cargar(f);
        if (nombre.getText().length() == 0)
            nombre.setText("Notas " + new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date()));

        texto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                if (restaurando) return;
                long ahora = System.currentTimeMillis();
                if (ahora - ultimoGuardadoHistorial > 1500) { recordar(); ultimoGuardadoHistorial = ahora; }
            }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {}
        });
    }

    // ---------------- Formato ----------------

    private boolean haySeleccion() {
        if (texto.getSelectionStart() == texto.getSelectionEnd()) {
            aviso("Primero seleccione el texto: déjelo presionado y arrastre");
            return false;
        }
        return true;
    }

    private int ini() { return Math.min(texto.getSelectionStart(), texto.getSelectionEnd()); }
    private int fin() { return Math.max(texto.getSelectionStart(), texto.getSelectionEnd()); }

    private static boolean esNegrilla(Object o) { return o instanceof StyleSpan && ((StyleSpan) o).getStyle() == Typeface.BOLD; }
    private static boolean esRojo(Object o) { return o instanceof ForegroundColorSpan; }
    private static boolean esResaltado(Object o) { return o instanceof BackgroundColorSpan; }

    private static Object copia(Object o) {
        if (o instanceof StyleSpan) return new StyleSpan(Typeface.BOLD);
        if (o instanceof ForegroundColorSpan) return new ForegroundColorSpan(((ForegroundColorSpan) o).getForegroundColor());
        return new BackgroundColorSpan(((BackgroundColorSpan) o).getBackgroundColor());
    }

    /** ¿Algún formato de este tipo cubre toda la selección? */
    private boolean cubre(Editable e, int s, int f, Predicate<Object> tipo) {
        for (Object o : e.getSpans(s, f, Object.class))
            if (tipo.test(o) && e.getSpanStart(o) <= s && e.getSpanEnd(o) >= f) return true;
        return false;
    }

    /** Quita un formato solo en la parte seleccionada (lo de afuera se conserva). */
    private void quitar(Editable e, int s, int f, Predicate<Object> tipo) {
        for (Object o : e.getSpans(s, f, Object.class)) {
            if (!tipo.test(o)) continue;
            int st = e.getSpanStart(o), en = e.getSpanEnd(o);
            if (en <= s || st >= f) continue;
            e.removeSpan(o);
            if (st < s) e.setSpan(copia(o), st, s, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (en > f) e.setSpan(copia(o), f, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void negrilla() {
        if (!haySeleccion()) return;
        recordar();
        Editable e = texto.getText();
        int s = ini(), f = fin();
        boolean ya = cubre(e, s, f, NotasActivity::esNegrilla);
        quitar(e, s, f, NotasActivity::esNegrilla);
        if (!ya) e.setSpan(new StyleSpan(Typeface.BOLD), s, f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Letra de color. Si ya tiene ese mismo color, se lo quita. */
    private void letraColor(int color) {
        if (!haySeleccion()) return;
        recordar();
        Editable e = texto.getText();
        int s = ini(), f = fin();
        boolean ya = cubre(e, s, f, o -> o instanceof ForegroundColorSpan
                && ((ForegroundColorSpan) o).getForegroundColor() == color);
        quitar(e, s, f, NotasActivity::esRojo);
        if (!ya) e.setSpan(new ForegroundColorSpan(color), s, f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void resaltar(int color) {
        if (!haySeleccion()) return;
        recordar();
        Editable e = texto.getText();
        int s = ini(), f = fin();
        boolean ya = cubre(e, s, f, o -> o instanceof BackgroundColorSpan
                && ((BackgroundColorSpan) o).getBackgroundColor() == color);
        quitar(e, s, f, NotasActivity::esResaltado);
        if (!ya) e.setSpan(new BackgroundColorSpan(color), s, f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void quitarFormato() {
        if (!haySeleccion()) return;
        recordar();
        Editable e = texto.getText();
        int s = ini(), f = fin();
        quitar(e, s, f, o -> esNegrilla(o) || esRojo(o) || esResaltado(o));
    }

    private void recordar() {
        historial.push(new SpannableStringBuilder(texto.getText()));
        while (historial.size() > 50) historial.removeLast();
    }

    private void deshacer() {
        if (historial.isEmpty()) { aviso("No hay nada más para deshacer"); return; }
        restaurando = true;
        CharSequence c = historial.pop();
        texto.setText(c);
        texto.setSelection(Math.min(c.length(), texto.getText().length()));
        restaurando = false;
    }

    // ---------------- Guardar / cargar ----------------

    private void guardar(boolean avisar) {
        if (actual == null || texto == null || nombre == null) return;
        Editable e = texto.getText();
        if (e.length() == 0 && !actual.exists()) return;
        StringBuilder s = new StringBuilder(limpio(nombre.getText().toString())).append('\n');
        for (Object o : e.getSpans(0, e.length(), Object.class)) {
            int a = e.getSpanStart(o), b = e.getSpanEnd(o);
            if (a < 0 || b <= a) continue;
            if (esNegrilla(o)) s.append("S\tB\t").append(a).append('\t').append(b).append("\t0\n");
            else if (esRojo(o)) s.append("S\tF\t").append(a).append('\t').append(b).append('\t')
                    .append(((ForegroundColorSpan) o).getForegroundColor()).append('\n');
            else if (esResaltado(o)) s.append("S\tH\t").append(a).append('\t').append(b).append('\t')
                    .append(((BackgroundColorSpan) o).getBackgroundColor()).append('\n');
        }
        s.append(SEPARADOR).append('\n').append(e.toString());
        try {
            Files.write(actual.toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
            if (avisar) aviso("💾 Hoja guardada");
        } catch (Exception x) {
            aviso("No se pudo guardar: " + x.getMessage());
        }
    }

    private void cargar(File f) {
        try {
            String todo = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            int k = todo.indexOf("\n" + SEPARADOR + "\n");
            if (k < 0) return;
            String cabeza = todo.substring(0, k);
            String cuerpo = todo.substring(k + SEPARADOR.length() + 2);
            String[] ls = cabeza.split("\n");
            nombre.setText(ls[0]);
            SpannableStringBuilder sb = new SpannableStringBuilder(cuerpo);
            for (int i = 1; i < ls.length; i++) {
                String[] p = ls[i].split("\t");
                if (p.length < 5 || !"S".equals(p[0])) continue;
                int a = Integer.parseInt(p[2]), b = Integer.parseInt(p[3]), c = Integer.parseInt(p[4]);
                if (a < 0 || b > sb.length() || b <= a) continue;
                Object o = "B".equals(p[1]) ? new StyleSpan(Typeface.BOLD)
                        : "F".equals(p[1]) ? new ForegroundColorSpan(c) : new BackgroundColorSpan(c);
                sb.setSpan(o, a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            texto.setText(sb);
        } catch (Exception e) {
            aviso("No se pudo abrir la hoja");
        }
    }

    // ---------------- Recorrer el texto por tramos con el mismo formato ----------------

    /** Si la hoja tiene colores, pregunta: ¿texto (solo negrilla) o imagen (con todos los colores)? */
    private void whatsapp() {
        guardar(false);
        if (texto.getText().length() == 0) { aviso("La hoja está vacía"); return; }
        Editable e = texto.getText();
        boolean colores = false;
        for (Object o : e.getSpans(0, e.length(), Object.class)) if (esRojo(o) || esResaltado(o)) { colores = true; break; }
        if (!colores) { whatsappTexto(); return; }
        new android.app.AlertDialog.Builder(this)
                .setTitle("¿Cómo lo mando a WhatsApp?")
                .setMessage("WhatsApp no deja pasar colores ni resaltado en el texto, solo la negrilla.\n\n"
                        + "🖼 Como imagen: sale igualita, con colores.\n✍ Como texto: se puede copiar, pero sin colores.")
                .setPositiveButton("🖼 Imagen", (d, w) -> {
                    Uri img = hacerPng();
                    if (img != null) enviar(WHATSAPP, "image/png", img);
                })
                .setNegativeButton("✍ Texto", (d, w) -> whatsappTexto())
                .show();
    }

    /** El texto va a WhatsApp con las negrillas (*así*). */
    private void whatsappTexto() {
        StringBuilder s = new StringBuilder();
        String t = nombre.getText().toString().trim();
        if (!t.isEmpty() && !t.startsWith("Notas ")) s.append('*').append(t).append("*\n\n");
        recorrer(texto.getText(), (tr, b, color, fondo) -> {
            if (!b) { s.append(tr); return; }
            String[] lineas = tr.split("\n", -1);
            for (int k = 0; k < lineas.length; k++) {
                if (k > 0) s.append('\n');
                String linea = lineas[k], core = linea.trim();
                if (core.isEmpty()) { s.append(linea); continue; }
                int ini = linea.indexOf(core);
                s.append(linea, 0, ini).append('*').append(core).append('*').append(linea.substring(ini + core.length()));
            }
        });
        textoWhatsapp(s.toString().trim());
    }


    // ---------------- Dictado: usted habla y se escribe donde está el cursor ----------------

    private static final int DICTADO = 61;

    private void dictar() {
        Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Hable. Diga \"punto\", \"coma\" o \"nueva línea\"");
        try { startActivityForResult(i, DICTADO); }
        catch (Exception e) { aviso("Este celular no tiene el dictado por voz de Google"); }
    }

    @Override
    protected void onActivityResult(int r, int res, Intent d) {
        super.onActivityResult(r, res, d);
        if (r != DICTADO || res != RESULT_OK || d == null || texto == null) return;
        java.util.ArrayList<String> rs = d.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
        if (rs == null || rs.isEmpty()) return;
        recordar();
        Editable e = texto.getText();
        int pos = Math.max(0, texto.getSelectionStart());
        String antes = e.subSequence(0, pos).toString();
        String t = signos(rs.get(0), antes);
        if (!antes.isEmpty() && !antes.endsWith(" ") && !antes.endsWith("\n") && !t.startsWith("\n") && !t.matches("^[.,;:?!].*")) t = " " + t;
        e.insert(pos, t);
        texto.setSelection(Math.min(e.length(), pos + t.length()));
    }

    /** Cambia las palabras dichas por signos y pone mayúscula después de punto. */
    static String signos(String s, String antes) {
        String t = " " + s.trim() + " ";
        String[][] c = {{" punto y aparte ", ".\n"}, {" punto aparte ", ".\n"}, {" nueva línea ", "\n"}, {" nuevo renglón ", "\n"},
                {" punto y coma ", "; "}, {" dos puntos ", ": "}, {" punto ", ". "}, {" coma ", ", "},
                {" signo de pregunta ", "? "}, {" signo de admiración ", "! "}};
        for (String[] x : c) t = t.replace(x[0], x[1]).replace(x[0].toUpperCase(new Locale("es")), x[1]);
        t = t.replaceAll(" +([.,;:?!])", "$1").replaceAll("\n +", "\n").trim();
        StringBuilder b = new StringBuilder(t);
        String ant = antes.trim();
        boolean mayus = ant.isEmpty() || ant.endsWith(".") || ant.endsWith("?") || ant.endsWith("!") || antes.endsWith("\n");
        for (int i = 0; i < b.length(); i++) {
            char ch = b.charAt(i);
            if (Character.isLetter(ch)) { if (mayus) b.setCharAt(i, Character.toUpperCase(ch)); mayus = false; }
            else if (ch == '.' || ch == '?' || ch == '!' || ch == '\n') mayus = true;
        }
        return b.toString();
    }

    // ---------------- Corregir ortografía, tildes y puntuación (LanguageTool, gratis) ----------------

    private void corregir() {
        guardar(false);
        final String t = texto.getText().toString();
        if (t.trim().isEmpty()) { aviso("La hoja está vacía"); return; }
        if (t.length() > 18000) { aviso("La hoja es muy larga para corregirla de una vez"); return; }
        aviso("✔ Revisando ortografía y puntuación…");
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL("https://api.languagetool.org/v2/check").openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                String cuerpo = "language=es&text=" + java.net.URLEncoder.encode(t, "UTF-8");
                c.getOutputStream().write(cuerpo.getBytes(StandardCharsets.UTF_8));
                if (c.getResponseCode() != 200) throw new Exception("el corrector respondió " + c.getResponseCode());
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                }
                org.json.JSONArray m = new org.json.JSONObject(bo.toString("UTF-8")).getJSONArray("matches");
                runOnUiThread(() -> aplicarCorrecciones(t, m));
            } catch (Exception x) {
                runOnUiThread(() -> aviso("No se pudo corregir (¿hay internet?): " + x.getMessage()));
            }
        }).start();
    }

    private void aplicarCorrecciones(String original, org.json.JSONArray m) {
        if (texto == null || !texto.getText().toString().equals(original)) {
            aviso("La hoja cambió mientras revisaba. Toque ✔ Corregir otra vez.");
            return;
        }
        recordar();
        Editable e = texto.getText();
        int hechas = 0, fin = Integer.MAX_VALUE;
        // De atrás hacia adelante para que las posiciones no se corran.
        for (int i = m.length() - 1; i >= 0; i--) {
            try {
                org.json.JSONObject o = m.getJSONObject(i);
                org.json.JSONArray r = o.getJSONArray("replacements");
                if (r.length() == 0) continue;
                int a = o.getInt("offset"), b = a + o.getInt("length");
                if (a < 0 || b > e.length() || b > fin) continue;
                e.replace(a, b, r.getJSONObject(0).getString("value"));
                fin = a;
                hechas++;
            } catch (Exception ignored) {}
        }
        if (hechas == 0) { historial.pop(); aviso("✔ No encontré nada que corregir"); }
        else aviso("✔ Corregí " + hechas + (hechas == 1 ? " cosa" : " cosas") + ". Si algo no le gusta, toque ↶ Deshacer");
    }

    private interface Tramo { void tramo(String t, boolean negrita, int color, int fondo); }

    private static void recorrer(Spanned sp, Tramo t) {
        int i = 0, n = sp.length();
        while (i < n) {
            int j = sp.nextSpanTransition(i, n, Object.class);
            boolean b = false;
            int color = 0, fondo = 0;
            for (Object o : sp.getSpans(i, j, Object.class)) {
                if (sp.getSpanStart(o) > i || sp.getSpanEnd(o) < j) continue;
                if (esNegrilla(o)) b = true;
                else if (esRojo(o)) color = ((ForegroundColorSpan) o).getForegroundColor();
                else if (esResaltado(o)) fondo = ((BackgroundColorSpan) o).getBackgroundColor();
            }
            t.tramo(sp.subSequence(i, j).toString(), b, color, fondo);
            i = j;
        }
    }

    // ---------------- PNG, imprimir, Drive ----------------

    private Uri hacerPng() {
        if (texto.getText().length() == 0) { aviso("La hoja está vacía"); return null; }
        LinearLayout l = vertical();
        l.setPadding(50, 50, 50, 50);
        l.setBackgroundColor(Color.WHITE);
        TextView ti = new TextView(this);
        ti.setText(nombre.getText().toString());
        ti.setTextSize(TypedValue.COMPLEX_UNIT_PX, 50);
        ti.setTypeface(Typeface.DEFAULT_BOLD);
        ti.setTextColor(Color.BLACK);
        ti.setPadding(0, 0, 0, 24);
        l.addView(ti);
        TextView cu = new TextView(this);
        cu.setText(new SpannableStringBuilder(texto.getText()));
        cu.setTextSize(TypedValue.COMPLEX_UNIT_PX, 38);
        cu.setTextColor(Color.BLACK);
        cu.setLineSpacing(8, 1f);
        l.addView(cu);
        return guardarPng(aImagen(l, 1080), "Cuaderno");
    }

    private void png() {
        guardar(false);
        if (hacerPng() != null) aviso("🖼 Imagen guardada en Descargas/MiBarra");
    }

    private void imprimir() {
        guardar(false);
        StringBuilder h = new StringBuilder("<html><head><meta charset='utf-8'><style>body{font-family:sans-serif;"
                + "font-size:16px;line-height:1.5}</style></head><body><h2>")
                .append(Util.xml(nombre.getText().toString())).append("</h2><p>");
        recorrer(texto.getText(), (t, b, color, fondo) -> {
            StringBuilder st = new StringBuilder();
            if (b) st.append("font-weight:bold;");
            if (color != 0) st.append("color:").append(hex(color)).append(';');
            if (fondo != 0) st.append("background:").append(hex(fondo)).append(';');
            h.append("<span style='").append(st).append("'>")
             .append(Util.xml(t).replace("\n", "<br>")).append("</span>");
        });
        h.append("</p></body></html>");
        imprimirHtml(h.toString(), nombre.getText().toString());
    }

    private void drive() {
        guardar(false);
        Uri img = hacerPng();
        if (img == null) return;
        StringBuilder cuerpo = new StringBuilder("<w:p><w:r><w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr><w:t xml:space=\"preserve\">")
                .append(Util.xml(nombre.getText().toString())).append("</w:t></w:r></w:p><w:p>");
        recorrer(texto.getText(), (t, b, color, fondo) -> {
            String[] partes = t.split("\n", -1);
            for (int k = 0; k < partes.length; k++) {
                if (k > 0) cuerpo.append("</w:p><w:p>");
                if (partes[k].isEmpty()) continue;
                cuerpo.append("<w:r><w:rPr>");
                if (b) cuerpo.append("<w:b/>");
                if (color != 0) cuerpo.append("<w:color w:val=\"").append(hex(color).substring(1)).append("\"/>");
                if (fondo != 0) cuerpo.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"")
                        .append(hex(fondo).substring(1)).append("\"/>");
                cuerpo.append("<w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">")
                      .append(Util.xml(partes[k])).append("</w:t></w:r>");
            }
        });
        cuerpo.append("</w:p>");
        Uri doc = null;
        try {
            String base = nombre.getText().toString().trim().replaceAll("[^\\p{L}\\p{N}]+", "_");
            doc = Util.guardarBytes(this, (base.isEmpty() ? "Cuaderno" : base) + "_" + Util.fecha() + ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    Util.docxConCuerpo(cuerpo.toString()));
        } catch (Exception ignored) {}
        enviar(DRIVE, "image/png", img, doc);
    }
}
