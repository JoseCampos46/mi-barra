package co.jose.mibarra;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mini enciclopedia: busca en Wikipedia en español (gratis), muestra el resumen,
 * lo lee en voz alta, y lo guarda para leerlo sin internet.
 */
public class EnciclopediaActivity extends Base {

    private static final int VOZ = 81;
    private EditText q;
    private LinearLayout cuerpo;
    private TextToSpeech voz;
    private boolean leyendo;
    private ScrollView sv;

    private File carpeta() { File f = new File(getFilesDir(), "enciclopedia"); f.mkdirs(); return f; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        voz = new TextToSpeech(this, s -> { if (s == TextToSpeech.SUCCESS) voz.setLanguage(new Locale("es", "CO")); });
        LinearLayout l = vertical();
        l.setPadding(dp(10), dp(8), dp(10), dp(10));
        LinearLayout f = horizontal();
        Button mic = boton("🎤", 0xFFD81B60);
        mic.setTextSize(20);
        mic.setOnClickListener(v -> {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO").putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué quiere saber?");
            try { startActivityForResult(i, VOZ); } catch (Exception e) { aviso("Este celular no tiene dictado por voz"); }
        });
        f.addView(mic, new LinearLayout.LayoutParams(dp(56), dp(54)));
        q = campoTexto("¿Qué quiere saber? Ej: Simón Bolívar, cemento", "");
        q.setSingleLine(true);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setOnEditorActionListener((v, a, e) -> { buscar(q.getText().toString()); return true; });
        f.addView(q, peso(1));
        Button ir = boton("🔎", 0xFF1565C0);
        ir.setOnClickListener(v -> buscar(q.getText().toString()));
        f.addView(ir, new LinearLayout.LayoutParams(dp(56), dp(54)));
        l.addView(f);
        cuerpo = vertical();
        l.addView(cuerpo);
        sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
        guardados();
    }

    @Override protected void onDestroy() { if (voz != null) voz.shutdown(); super.onDestroy(); }

    @Override
    protected void onActivityResult(int r, int res, Intent d) {
        super.onActivityResult(r, res, d);
        if (r == VOZ && res == RESULT_OK && d != null) {
            ArrayList<String> rs = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (rs != null && !rs.isEmpty()) {
                String t = rs.get(0).replaceAll("(?i)^(qué es|que es|quién fue|quien fue|quién es|quien es|qué son|que son|háblame de|hablame de|busca|buscar)\\s+(el |la |los |las |un |una )?", "");
                q.setText(t);
                buscar(t);
            }
        }
    }

    // ---------------- Red ----------------

    private static String bajar(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "MiBarra/1.0 (app personal; Android)");
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        if (c.getResponseCode() != 200) throw new Exception("Wikipedia respondió " + c.getResponseCode());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = c.getInputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        }
        return bo.toString("UTF-8");
    }

    private static final String API = "https://es.wikipedia.org/w/api.php?format=json&utf8=1&";

    private void buscar(String texto) {
        String t = texto.trim();
        if (t.isEmpty()) { guardados(); return; }
        callar();
        cuerpo.removeAllViews();
        cuerpo.addView(nota("🔎 Buscando «" + t + "»…"));
        new Thread(() -> {
            try {
                JSONArray r = new JSONObject(bajar(API + "action=query&list=search&srlimit=8&srsearch=" + URLEncoder.encode(t, "UTF-8")))
                        .getJSONObject("query").getJSONArray("search");
                List<String[]> res = new ArrayList<>();
                for (int i = 0; i < r.length(); i++) {
                    JSONObject o = r.getJSONObject(i);
                    res.add(new String[]{o.getString("title"), o.optString("snippet").replaceAll("<[^>]+>", "").replace("&quot;", "\"")});
                }
                runOnUiThread(() -> resultados(t, res));
            } catch (Exception e) {
                runOnUiThread(() -> { cuerpo.removeAllViews(); cuerpo.addView(nota("No hay internet o Wikipedia no respondió.\nLo guardado sí se puede leer sin internet.")); agregarGuardados(); });
            }
        }).start();
    }

    private void resultados(String t, List<String[]> res) {
        cuerpo.removeAllViews();
        if (res.isEmpty()) { cuerpo.addView(nota("No encontré nada sobre «" + t + "». Pruebe con otras palabras.")); return; }
        if (res.size() == 1) { abrir(res.get(0)[0]); return; }
        cuerpo.addView(nota("Toque el que busca:"));
        for (String[] r : res) {
            Button b = boton(r[0] + (r[1].isEmpty() ? "" : "\n" + (r[1].length() > 90 ? r[1].substring(0, 90) + "…" : r[1])), 0xFF37474F);
            b.setTextSize(14);
            b.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            b.setOnClickListener(v -> abrir(r[0]));
            cuerpo.addView(b, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = dp(6); }});
        }
    }

    private void abrir(String titulo) {
        callar();
        cuerpo.removeAllViews();
        cuerpo.addView(nota("📖 Abriendo «" + titulo + "»…"));
        new Thread(() -> {
            try {
                JSONObject pages = new JSONObject(bajar(API + "action=query&prop=extracts&explaintext=1&redirects=1&titles="
                        + URLEncoder.encode(titulo, "UTF-8"))).getJSONObject("query").getJSONObject("pages");
                JSONObject pg = pages.getJSONObject(pages.keys().next());
                String txt = pg.optString("extract", "");
                String tt = pg.optString("title", titulo);
                runOnUiThread(() -> mostrar(tt, txt, false));
            } catch (Exception e) {
                runOnUiThread(() -> { cuerpo.removeAllViews(); cuerpo.addView(nota("No se pudo abrir. ¿Hay internet?")); });
            }
        }).start();
    }

    // ---------------- Artículo ----------------

    private void mostrar(String titulo, String txt, boolean guardado) {
        cuerpo.removeAllViews();
        sv.scrollTo(0, 0);
        TextView t = titulo("📖 " + titulo);
        cuerpo.addView(t);
        String intro = txt.split("\n==")[0].trim();
        LinearLayout f = horizontal();
        Button oir = boton("🔊 Oír", 0xFF6A1B9A);
        oir.setOnClickListener(v -> {
            if (leyendo) { callar(); oir.setText("🔊 Oír"); return; }
            leer(intro);
            oir.setText("⏹ Callar");
        });
        Button guar = boton(guardado ? "🗑 Borrar" : "💾 Guardar", guardado ? 0xFF8D6E63 : 0xFF2E7D32);
        guar.setOnClickListener(v -> {
            File a = new File(carpeta(), titulo.replaceAll("[\\\\/:*?\"<>|]", " ") + ".txt");
            if (guardado) { a.delete(); aviso("Borrado"); guardados(); return; }
            try { Files.write(a.toPath(), txt.getBytes(StandardCharsets.UTF_8)); aviso("💾 Guardado: se puede leer sin internet"); }
            catch (Exception e) { aviso("No se pudo guardar"); }
        });
        Button wa = boton("💬", 0xFF25A244);
        wa.setOnClickListener(v -> textoWhatsapp("*" + titulo + "*\n" + (intro.length() > 900 ? intro.substring(0, 900) + "…" : intro)
                + "\nhttps://es.wikipedia.org/wiki/" + titulo.replace(' ', '_')));
        f.addView(oir, peso(1.2f));
        f.addView(guar, peso(1.2f));
        f.addView(wa, peso(0.7f));
        cuerpo.addView(f, ancho(50));

        TextView res = new TextView(this);
        res.setText(intro.isEmpty() ? "(Sin resumen)" : intro);
        res.setTextSize(19);
        res.setTextColor(Color.BLACK);
        res.setLineSpacing(0, 1.2f);
        res.setPadding(dp(4), dp(10), dp(4), dp(10));
        res.setTextIsSelectable(true);
        cuerpo.addView(res);

        if (txt.length() > intro.length() + 20) {
            Button todo = boton("📚  Leer todo el artículo (capítulos)", 0xFF1565C0);
            todo.setOnClickListener(v -> {
                todo.setVisibility(android.view.View.GONE);
                TextView c = new TextView(this);
                c.setText(capitulos(txt.substring(intro.length())));
                c.setTextSize(17);
                c.setTextColor(0xFF263238);
                c.setLineSpacing(0, 1.2f);
                c.setTextIsSelectable(true);
                cuerpo.addView(c);
                Button oirTodo = boton("🔊  Oír todo el artículo", 0xFF6A1B9A);
                oirTodo.setOnClickListener(vv -> leer(txt.replaceAll("=+ *([^=\n]+?) *=+", "$1.")));
                cuerpo.addView(oirTodo, ancho(50));
            });
            cuerpo.addView(todo, ancho(52));
        }
        cuerpo.addView(nota("Fuente: Wikipedia en español"));
    }

    /** "== Historia ==" se vuelve un título en negrilla. */
    private static CharSequence capitulos(String s) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        for (String linea : s.split("\n")) {
            String l = linea.trim();
            if (l.isEmpty()) continue;
            if (l.startsWith("=")) {
                String tt = l.replaceAll("=", "").trim();
                if (tt.matches("(?i)(referencias|enlaces externos|véase también|bibliografía|notas)")) break;
                int a = b.length();
                b.append("\n").append(tt).append("\n");
                b.setSpan(new StyleSpan(Typeface.BOLD), a, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                b.setSpan(new RelativeSizeSpan(1.2f), a, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else b.append(l).append("\n\n");
        }
        return b;
    }

    private void leer(String s) {
        callar();
        leyendo = true;
        for (String p : s.split("\n+")) {
            String x = p.trim();
            while (x.length() > 3500) { voz.speak(x.substring(0, 3500), TextToSpeech.QUEUE_ADD, null, "e"); x = x.substring(3500); }
            if (!x.isEmpty()) voz.speak(x, TextToSpeech.QUEUE_ADD, null, "e");
        }
    }

    private void callar() { leyendo = false; if (voz != null) voz.stop(); }

    // ---------------- Guardados (sin internet) ----------------

    private void guardados() {
        cuerpo.removeAllViews();
        cuerpo.addView(nota("Escriba o diga lo que quiere saber. Lo que guarde 💾 se puede leer sin internet."));
        agregarGuardados();
    }

    private void agregarGuardados() {
        File[] fs = carpeta().listFiles();
        if (fs == null || fs.length == 0) return;
        cuerpo.addView(titulo("\n💾 Guardados"));
        java.util.Arrays.sort(fs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File a : fs) {
            String n = a.getName().replaceAll("\\.txt$", "");
            Button b = boton("📖 " + n, 0xFF455A64);
            b.setOnClickListener(v -> {
                try { mostrar(n, new String(Files.readAllBytes(a.toPath()), StandardCharsets.UTF_8), true); }
                catch (Exception e) { aviso("No se pudo abrir"); }
            });
            cuerpo.addView(b, new LinearLayout.LayoutParams(-1, dp(50)) {{ topMargin = dp(4); }});
        }
    }

    private TextView nota(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(0xFF607D8B);
        t.setPadding(dp(4), dp(10), dp(4), dp(6));
        return t;
    }
}
