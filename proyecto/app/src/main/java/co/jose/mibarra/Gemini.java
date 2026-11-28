package co.jose.mibarra;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Versión 38: el arreglador de textos con Gemini (llave gratis de Google AI Studio).
 * Lo usan el botón flotante «✨ Organizar» y Mi Voz, para que los dos arreglen igual.
 * La llave se guarda en un archivo (y no en preferencias) porque Mi Voz y el botón flotante
 * corren en procesos distintos y así los dos leen siempre la misma llave.
 */
final class Gemini {

    static final String PAGINA_LLAVE = "https://aistudio.google.com/apikey";

    // Versión 39: Google retira los modelos viejos (2.0 y 2.5 respondían 404). Se usan los nombres
    // que siempre apuntan al más nuevo y, si fallan, la app le pregunta a Google cuáles hay y escoge un «flash».
    private static final String[] MODELOS = {"gemini-flash-latest", "gemini-flash-lite-latest"};

    private static final String INSTRUCCION =
            "Eres el corrector de mensajes de José, un señor de Colombia que dicta por voz sus mensajes de WhatsApp para clientes y familiares. "
            + "Organiza y corrige el texto que te doy: ordena las ideas para que se entiendan claras y en buen orden, corrige gramática, ortografía, tildes y puntuación, "
            + "quita palabras repetidas y muletillas propias del dictado, escribe el dinero como $350.000 y separa en párrafos cortos solo si hay varias ideas. "
            + "Conserva el sentido, el tono cercano y respetuoso de José, y deja exactos los nombres, cifras, medidas, fechas, horas y direcciones. "
            + "No agregues información nueva, no inventes saludos ni despedidas que no estén, no pongas emojis nuevos, comillas, títulos ni explicaciones. "
            + "Responde únicamente con el mensaje final listo para enviar.";

    interface Respuesta {
        void listo(String texto);
        void error(String mensaje);
    }

    private Gemini() {}

    private static File archivo(Context c) { return new File(c.getFilesDir(), "gemini_llave.txt"); }

    static String llave(Context c) {
        try {
            File f = archivo(c);
            if (!f.exists()) return "";
            return limpiarLlave(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) { return ""; }
    }

    static boolean hayLlave(Context c) { return llave(c).length() > 20; }

    /** Versión 40: las llaves nuevas de Google empiezan por «AQ.» y llevan punto; las viejas por «AIza».
     *  Se quitan solo espacios, saltos de línea y puntos sueltos al final, nunca el punto de adentro. */
    static String limpiarLlave(String pegado) {
        if (pegado == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("AIza[0-9A-Za-z_\\-]{35}").matcher(pegado);
        if (m.find()) return m.group();
        m = java.util.regex.Pattern.compile("AQ\\.[0-9A-Za-z_\\-.]+").matcher(pegado);
        String k = m.find() ? m.group() : pegado.replaceAll("\\s+", "");
        // la llave de la versión 39 quedó sin el punto («AQAb…»): se le devuelve
        if (k.startsWith("AQA") && !k.startsWith("AQ.")) k = "AQ." + k.substring(2);
        while (k.endsWith(".")) k = k.substring(0, k.length() - 1);
        return k;
    }

    static boolean guardarLlave(Context c, String llave) {
        try {
            Files.write(archivo(c).toPath(), limpiarLlave(llave).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) { return false; }
    }

    /** Arregla el texto en otro hilo; la respuesta llega en otro hilo (el que llama la pasa a la pantalla). */
    static void organizar(Context c, String texto, boolean otraVez, Respuesta r) {
        final String llave = llave(c);
        new Thread(() -> {
            if (llave.length() < 20) { r.error("Falta la llave de Gemini"); return; }
            String ultimo = "No se pudo organizar. Intente otra vez.";
            java.util.List<String> modelos = new java.util.ArrayList<>();
            String guardado = modeloGuardado(c);
            if (!guardado.isEmpty()) modelos.add(guardado);
            for (String m : MODELOS) if (!modelos.contains(m)) modelos.add(m);
            boolean buscoLista = false;
            final long inicio = System.currentTimeMillis();
            for (int i = 0; i < modelos.size(); i++) {
                String modelo = modelos.get(i);
                // Versión 40: nunca más de 30 segundos en total
                if (System.currentTimeMillis() - inicio > 30000) {
                    ultimo = "⏳ Gemini se demoró más de 30 segundos. Toque «Otra vez».";
                    break;
                }
                try {
                    String hecho = pedir(llave, modelo, texto, otraVez);
                    if (hecho != null && !hecho.trim().isEmpty()) {
                        guardarModelo(c, modelo);
                        r.listo(hecho.trim());
                        return;
                    }
                } catch (Fallo f) {
                    ultimo = f.getMessage();
                    if (f.parar) break;
                    // si ninguno de los conocidos existe, se pide a Google la lista de modelos de esta llave
                    if (i == modelos.size() - 1 && !buscoLista) {
                        buscoLista = true;
                        try { for (String m : modelosDisponibles(llave)) if (!modelos.contains(m)) modelos.add(m); }
                        catch (Exception ignored) {}
                    }
                } catch (java.net.SocketTimeoutException e) {
                    ultimo = "⏳ Gemini se demoró mucho en contestar. Toque «Otra vez».";
                    break;
                } catch (java.net.UnknownHostException | java.net.ConnectException e) {
                    ultimo = "📶 No hay internet para organizar el texto. Revise el Wi‑Fi o los datos.";
                    break;
                } catch (Exception e) {
                    // Versión 40: el error verdadero, para que Claude sepa qué arreglar
                    String d = e.getMessage() == null ? "" : e.getMessage();
                    if (d.length() > 120) d = d.substring(0, 120);
                    ultimo = "⚠ Falla: " + e.getClass().getSimpleName() + " " + d + " (mándele este pantallazo a Claude)";
                    break;
                }
            }
            r.error(ultimo);
        }).start();
    }

    private static final class Fallo extends Exception {
        final boolean parar;
        Fallo(String m, boolean parar) { super(m); this.parar = parar; }
    }

    private static String pedir(String llave, String modelo, String texto, boolean otraVez) throws Exception {
        boolean pensarPoco = true, enDireccion = false;
        for (int intento = 0; intento < 4; intento++) {
            try {
                return pedir(llave, modelo, texto, otraVez, pensarPoco, enDireccion);
            } catch (Fallo f) {
                String m = f.getMessage() == null ? "" : f.getMessage();
                // si Google no entiende el «pensar poco», se pide sin eso
                if (m.equals("#pensar")) { pensarPoco = false; continue; }
                // las llaves nuevas «AQ.» a veces solo sirven puestas en la dirección
                if (m.equals("#llave")) { enDireccion = true; continue; }
                throw f;
            }
        }
        throw new Fallo("No se pudo organizar. Intente otra vez.", false);
    }

    private static String pedir(String llave, String modelo, String texto, boolean otraVez, boolean pensarPoco, boolean llaveEnDireccion) throws Exception {
        JSONObject cuerpo = new JSONObject();
        cuerpo.put("systemInstruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", INSTRUCCION))));
        cuerpo.put("contents", new JSONArray().put(new JSONObject().put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", texto)))));
        JSONObject gen = new JSONObject().put("temperature", otraVez ? 0.8 : 0.3);
        // Versión 40: que conteste rápido, sin ponerse a «pensar» mucho
        if (pensarPoco) {
            if (modelo.startsWith("gemini-2.")) gen.put("thinkingConfig", new JSONObject().put("thinkingBudget", 0));
            else gen.put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));
        }
        cuerpo.put("generationConfig", gen);

        String dir = "https://generativelanguage.googleapis.com/v1beta/models/" + modelo + ":generateContent";
        if (llaveEnDireccion) dir += "?key=" + java.net.URLEncoder.encode(llave, "UTF-8");
        HttpURLConnection con = (HttpURLConnection) new URL(dir).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(25000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (!llaveEnDireccion) con.setRequestProperty("x-goog-api-key", llave);
        try (OutputStream o = con.getOutputStream()) { o.write(cuerpo.toString().getBytes(StandardCharsets.UTF_8)); }
        int codigo = con.getResponseCode();
        String resp = leer(codigo >= 400 ? con.getErrorStream() : con.getInputStream());
        if (codigo == 400 && pensarPoco && resp.toLowerCase(java.util.Locale.ROOT).contains("thinking")) throw new Fallo("#pensar", false);
        if ((codigo == 400 || codigo == 401 || codigo == 403) && !llaveEnDireccion && llave.startsWith("AQ.")) throw new Fallo("#llave", false);
        if (codigo == 400 && resp.contains("API_KEY_INVALID"))
            throw new Fallo("🔑 La llave de Gemini no sirve. Revísela en ✨ Organizar → 🔑 Llave.", true);
        if (codigo == 401 || codigo == 403)
            throw new Fallo("🔑 Google no aceptó la llave de Gemini. Revísela en ✨ Organizar → 🔑 Llave.", true);
        if (codigo == 429) throw new Fallo("⏳ Gemini está ocupado o se llegó al límite gratis del momento. Espere un minuto e intente otra vez.", false);
        if (codigo == 404) throw new Fallo("No se pudo organizar: Google cambió los modelos de Gemini. Avísele a Claude.", false);
        if (codigo >= 400) {
            String msg = "";
            try { msg = new JSONObject(resp).getJSONObject("error").optString("message", ""); } catch (Exception ignored) {}
            if (msg.length() > 120) msg = msg.substring(0, 120);
            throw new Fallo("No se pudo organizar (Gemini respondió " + codigo + ") " + msg, codigo == 400);
        }

        JSONArray cand = new JSONObject(resp).optJSONArray("candidates");
        if (cand == null || cand.length() == 0) return null;
        JSONObject cont = cand.getJSONObject(0).optJSONObject("content");
        if (cont == null) return null;
        JSONArray partes = cont.optJSONArray("parts");
        if (partes == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length(); i++) {
            JSONObject p = partes.getJSONObject(i);
            if (p.optBoolean("thought", false)) continue;
            sb.append(p.optString("text", ""));
        }
        return sb.toString();
    }

    private static File archivoModelo(Context c) { return new File(c.getFilesDir(), "gemini_modelo.txt"); }

    private static String modeloGuardado(Context c) {
        try {
            File f = archivoModelo(c);
            return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim() : "";
        } catch (Exception e) { return ""; }
    }

    private static void guardarModelo(Context c, String m) {
        try { Files.write(archivoModelo(c).toPath(), m.getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }

    /** Los modelos «flash» de texto que esta llave puede usar, del más nuevo al más viejo (primero los que no son «lite»). */
    private static java.util.List<String> modelosDisponibles(String llave) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(
                "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200").openConnection();
        con.setConnectTimeout(12000);
        con.setReadTimeout(20000);
        con.setRequestProperty("x-goog-api-key", llave);
        if (con.getResponseCode() >= 400) {
            con = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key="
                    + java.net.URLEncoder.encode(llave, "UTF-8")).openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
        }
        JSONArray ms = new JSONObject(leer(con.getInputStream())).optJSONArray("models");
        java.util.List<String> buenos = new java.util.ArrayList<>(), otros = new java.util.ArrayList<>();
        if (ms == null) return buenos;
        for (int i = 0; i < ms.length(); i++) {
            JSONObject m = ms.getJSONObject(i);
            String n = m.optString("name", "").replace("models/", "");
            JSONArray met = m.optJSONArray("supportedGenerationMethods");
            boolean genera = met != null && met.toString().contains("generateContent");
            if (!genera || !n.contains("flash")) continue;
            if (n.matches(".*(image|tts|live|audio|embed|transcribe|translate|computer|omni|veo|banana).*")) continue;
            if (n.contains("preview") || n.contains("exp")) otros.add(n); else buenos.add(n);
        }
        java.util.Comparator<String> orden = (a, b) -> {
            boolean la = a.contains("lite"), lb = b.contains("lite");
            if (la != lb) return la ? 1 : -1;
            return b.compareTo(a);
        };
        buenos.sort(orden);
        otros.sort(orden);
        buenos.addAll(otros);
        return buenos.size() > 6 ? buenos.subList(0, 6) : buenos;
    }

    private static String leer(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream i = in) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) bo.write(buf, 0, n);
        }
        return bo.toString("UTF-8");
    }
}
