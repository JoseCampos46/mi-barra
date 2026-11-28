package co.jose.mibarra;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versión 27: corrector sin internet para "Escribir bien".
 * Arregla espacios, signos ¿? ¡!, mayúsculas, punto final y tildes que no dejan duda.
 */
final class Corrector {
    private Corrector() {}

    /** Un cambio hecho: dónde quedó en el texto nuevo y cómo estaba antes. */
    static final class Cambio { int ini, fin; String antes; Cambio(int i, int f, String a) { ini = i; fin = f; antes = a; } }

    static final class Resultado { String texto; List<Cambio> cambios = new ArrayList<>(); int total; }

    private static final Map<String, String> ARREGLOS = new HashMap<>();
    static {
        String[] p = {"q", "que", "k", "que", "xq", "porque", "pq", "porque", "porq", "porque", "tb", "también", "tmb", "también",
                "aver", "a ver", "haci", "así", "nose", "no sé", "osea", "o sea", "enserio", "en serio", "talvez", "tal vez",
                "aveces", "a veces", "derrepente", "de repente", "porfavor", "por favor", "porfa", "por favor", "hiba", "iba",
                "hiva", "iba", "ojala", "ojalá", "atte", "atentamente", "att", "atentamente", "dl", "del", "xfa", "por favor",
                "almenos", "al menos", "sinembargo", "sin embargo", "envez", "en vez", "deveras", "de veras", "aparte", "aparte",
                "nomas", "no más", "nadamas", "nada más", "asta", "hasta", "ise", "hice", "iso", "hizo", "hechar", "echar",
                "llendo", "yendo", "dijieron", "dijeron", "trajieron", "trajeron",
                "haiga", "haya", "naiden", "nadie", "fuistes", "fuiste", "dijistes", "dijiste", "hicistes", "hiciste"};
        for (int i = 0; i < p.length; i += 2) ARREGLOS.put(p[i], p[i + 1]);
    }

    private static final Map<String, String> PREGUNTA = new HashMap<>();
    static {
        String[] p = {"que", "qué", "como", "cómo", "cuando", "cuándo", "donde", "dónde", "adonde", "adónde", "cuanto", "cuánto",
                "cuanta", "cuánta", "cuantos", "cuántos", "cuantas", "cuántas", "quien", "quién", "quienes", "quiénes", "cual", "cuál",
                "cuales", "cuáles"};
        for (int i = 0; i < p.length; i += 2) PREGUNTA.put(p[i], p[i + 1]);
    }
    private static final Set<String> PREPOSICION = new HashSet<>(Arrays.asList("por", "para", "de", "a", "con", "en", "desde", "hasta", "y", "pero"));
    private static final Set<String> CIERRE = new HashSet<>(Arrays.asList("atentamente", "cordialmente", "saludos", "gracias",
            "bendiciones", "respetuosamente", "sinceramente"));

    private static final Pattern PALABRA = Pattern.compile("[\\p{L}]+");

    /** Pone la palabra nueva con las mismas mayúsculas que la vieja. */
    static String igualQue(String vieja, String nueva) {
        if (vieja.length() > 1 && vieja.equals(vieja.toUpperCase()) && !vieja.equals(vieja.toLowerCase())) return nueva.toUpperCase();
        if (Character.isUpperCase(vieja.charAt(0))) return Character.toUpperCase(nueva.charAt(0)) + nueva.substring(1);
        return nueva;
    }

    static Resultado corregir(String original, Map<String, String> tildes) {
        String t = original.replace("\r", "").replace('\t', ' ');
        // 1. Palabras mal escritas y tildes seguras
        Matcher m = PALABRA.matcher(t);
        StringBuilder b = new StringBuilder();
        int ult = 0;
        while (m.find()) {
            String w = m.group(), lw = w.toLowerCase();
            String n = ARREGLOS.get(lw);
            if (n == null && tildes != null) n = tildes.get(lw);
            // no tocar palabras pegadas a @, / o dentro de direcciones
            int s = m.start(), e = m.end();
            boolean raro = (s > 0 && "@/._-".indexOf(t.charAt(s - 1)) >= 0 && s > 1 && Character.isLetterOrDigit(t.charAt(s - 2)))
                    || (e < t.length() && "@/_".indexOf(t.charAt(e)) >= 0);
            b.append(t, ult, s).append(n != null && !raro ? igualQue(w, n) : w);
            ult = e;
        }
        b.append(t.substring(ult));
        t = b.toString();
        // 2. Espacios
        StringBuilder lineas = new StringBuilder();
        for (String l : t.split("\n", -1)) {
            l = l.replaceAll(" {2,}", " ").trim();
            l = l.replaceAll(" +([,.;:?!)…])", "$1");
            l = l.replaceAll("([¿¡(]) +", "$1");
            l = l.replaceAll(",(?=\\p{L})", ", ");
            l = l.replaceAll("([;:?!])(?=[\\p{L}¿¡])", "$1 ");
            l = l.replaceAll("\\.(?=[\\p{Lu}¿¡])", ". ");
            l = puntoPegado(l);
            lineas.append(frases(l)).append('\n');
        }
        t = lineas.substring(0, lineas.length() - 1);
        t = t.replaceAll("\n{3,}", "\n\n").replaceAll("^\n+", "").replaceAll("\n+$", "");
        Resultado r = new Resultado();
        r.texto = t;
        diferencias(original, t, r);
        return r;
    }

    private static final Pattern PEGADO = Pattern.compile("(\\S*?)(\\p{L}{3,})\\.(\\p{Ll}{3,})(\\S*)");
    private static final Set<String> DOMINIOS = new HashSet<>(Arrays.asList("com", "net", "org", "edu", "gov", "info", "html", "php", "pdf", "jpg", "png", "doc", "docx", "xlsx", "txt", "mp3", "mp4"));

    /** "atento.mire" → "atento. mire" (sin dañar direcciones como www.google.com o correos). */
    private static String puntoPegado(String l) {
        Matcher m = PEGADO.matcher(l);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            String todo = m.group();
            boolean dir = todo.contains("@") || todo.contains("www") || todo.contains("http") || todo.contains("/")
                    || DOMINIOS.contains(m.group(3)) || !m.group(4).isEmpty() || !m.group(1).isEmpty();
            m.appendReplacement(b, Matcher.quoteReplacement(dir ? todo : m.group(2) + ". " + m.group(3)));
        }
        m.appendTail(b);
        return b.toString();
    }

    private static final Set<String> TRATO = new HashSet<>(Arrays.asList("señor", "señora", "señorita", "don", "doña", "sr", "sra", "doctor", "doctora", "dr", "dra", "ingeniero", "ingeniera", "ing"));

    /** Arregla cada frase de una línea: ¿? ¡!, tilde de pregunta, mayúscula; y el punto final. */
    private static String frases(String l) {
        if (l.isEmpty()) return l;
        String[] fs = l.split("(?<=[.?!…])\\s+");
        StringBuilder b = new StringBuilder();
        for (String f : fs) {
            if (f.isEmpty()) continue;
            boolean pregunta = f.endsWith("?"), grito = f.endsWith("!");
            if (pregunta && f.indexOf('¿') < 0) f = "¿" + f;
            if (grito && f.indexOf('¡') < 0) f = "¡" + f;
            if (pregunta) {
                Matcher m = PALABRA.matcher(f);
                for (int k = 0; k < 2 && m.find(); k++) {
                    String w = m.group().toLowerCase();
                    String n = PREGUNTA.get(w);
                    if (n != null) { f = f.substring(0, m.start()) + igualQue(m.group(), n) + f.substring(m.end()); break; }
                    if (!PREPOSICION.contains(w)) break;
                }
            }
            // mayúscula al empezar
            for (int i = 0; i < f.length(); i++) {
                char c = f.charAt(i);
                if (Character.isLetter(c)) { f = f.substring(0, i) + Character.toUpperCase(c) + f.substring(i + 1); break; }
                if (Character.isDigit(c)) break;
            }
            // "señor carlos" → "señor Carlos"
            Matcher mt = PALABRA.matcher(f);
            String antes = null;
            StringBuilder ff = new StringBuilder(); int u0 = 0;
            while (mt.find()) {
                String w = mt.group();
                ff.append(f, u0, mt.start());
                ff.append(antes != null && TRATO.contains(antes) && f.substring(u0, mt.start()).matches("\\.? ") ? Character.toUpperCase(w.charAt(0)) + w.substring(1) : w);
                u0 = mt.end();
                antes = w.toLowerCase();
            }
            ff.append(f.substring(u0));
            f = ff.toString();
            if (b.length() > 0) b.append(' ');
            b.append(f);
        }
        String s = b.toString();
        char u = s.charAt(s.length() - 1);
        if (Character.isLetterOrDigit(u) || u == ')' || u == '"') {
            String[] ps = s.trim().split("\\s+");
            String primera = ps[0].toLowerCase().replaceAll("[^\\p{L}]", "");
            if (ps.length <= 2 && CIERRE.contains(primera)) s += ",";
            else if (ps.length > 2 && !s.matches(".*(https?://|www\\.|@)\\S*$")) s += ".";
        }
        return s;
    }

    // ---------------- Qué cambió (para pintarlo de amarillo) ----------------

    private static final Pattern TROZO = Pattern.compile("\\p{L}+|\\d+|\\S");

    private static List<int[]> trozos(String s) {
        List<int[]> r = new ArrayList<>();
        Matcher m = TROZO.matcher(s);
        while (m.find()) r.add(new int[]{m.start(), m.end()});
        return r;
    }

    private static void diferencias(String a, String b, Resultado r) {
        List<int[]> ta = trozos(a), tb = trozos(b);
        int n = ta.size(), m = tb.size();
        if ((long) n * m > 6_000_000L) { r.total = a.equals(b) ? 0 : 1; return; }
        String[] sa = new String[n], sb = new String[m];
        for (int i = 0; i < n; i++) sa[i] = a.substring(ta.get(i)[0], ta.get(i)[1]);
        for (int j = 0; j < m; j++) sb[j] = b.substring(tb.get(j)[0], tb.get(j)[1]);
        int[][] L = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) for (int j = m - 1; j >= 0; j--)
            L[i][j] = igual(sa[i], sb[j]) ? L[i + 1][j + 1] + 1 : Math.max(L[i + 1][j], L[i][j + 1]);
        int i = 0, j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && igual(sa[i], sb[j])) { i++; j++; continue; }
            int i0 = i, j0 = j;
            while ((i < n || j < m) && !(i < n && j < m && igual(sa[i], sb[j]))) {
                if (j >= m || (i < n && L[i + 1][j] >= L[i][j + 1])) i++; else j++;
            }
            int aIni = i0 < n ? ta.get(i0)[0] : a.length(), aFin = i > 0 && i > i0 ? ta.get(i - 1)[1] : aIni;
            int bIni = j0 < m ? tb.get(j0)[0] : b.length(), bFin = j > 0 && j > j0 ? tb.get(j - 1)[1] : bIni;
            String nuevo = b.substring(bIni, bFin);
            r.total++;
            if (!nuevo.trim().isEmpty()) {
                // quitar espacios de las orillas para pintar solo lo que cambió
                while (bIni < bFin && Character.isWhitespace(b.charAt(bIni))) bIni++;
                while (bFin > bIni && Character.isWhitespace(b.charAt(bFin - 1))) bFin--;
                r.cambios.add(new Cambio(bIni, bFin, a.substring(aIni, aFin).trim()));
            }
        }
    }

    /** Los espacios cuentan como iguales aunque sean distintos (dos espacios → uno no se pinta). */
    private static boolean igual(String x, String y) {
        if (x.equals(y)) return true;
        return x.trim().isEmpty() && y.trim().isEmpty() && x.indexOf('\n') < 0 == (y.indexOf('\n') < 0);
    }

    // ---------------- Organizar ----------------

    private static final Pattern SALUDO = Pattern.compile("(?i)^(buen[oa]s?|hola|señor|señora|sr|sra|don|doña|estimad[oa]|apreciad[oa]|querid[oa]|respetad[oa]|cordial)\\b.*");

    /** Separa el escrito en: saludo (primera línea), cuerpo y despedida (líneas cortas del final). */
    static String[] partes(String t) {
        List<String> ls = new ArrayList<>(Arrays.asList(t.trim().split("\n")));
        String cabeza = "", cola = "";
        if (!ls.isEmpty() && ls.get(0).trim().split("\\s+").length <= 8 && SALUDO.matcher(ls.get(0).trim()).matches()) cabeza = ls.remove(0).trim();
        List<String> c = new ArrayList<>();
        while (!ls.isEmpty()) {
            String u = ls.get(ls.size() - 1).trim();
            if (u.isEmpty()) { ls.remove(ls.size() - 1); continue; }
            if (u.split("\\s+").length <= 3 && !u.endsWith(".") && !u.endsWith("?")) { c.add(0, u); ls.remove(ls.size() - 1); }
            else break;
        }
        cola = String.join("\n", c);
        return new String[]{cabeza, String.join("\n", ls).trim(), cola};
    }

    static List<String> oraciones(String cuerpo) {
        List<String> r = new ArrayList<>();
        for (String s : cuerpo.replaceAll("\\s*\n\\s*", " ").split("(?<=[.?!…])\\s+")) {
            s = s.trim().replaceFirst("^\\d+[.)]\\s+", "");
            if (!s.isEmpty()) r.add(s);
        }
        return r;
    }

    static String juntar(String cabeza, String cuerpo, String cola) {
        StringBuilder b = new StringBuilder();
        if (!cabeza.isEmpty()) b.append(cabeza).append("\n\n");
        b.append(cuerpo);
        if (!cola.isEmpty()) b.append("\n\n").append(cola);
        return b.toString().trim();
    }

    static String enParrafos(String t) {
        String[] p = partes(t);
        List<String> os = oraciones(p[1]);
        StringBuilder b = new StringBuilder();
        int enEste = 0, largo = 0;
        for (String o : os) {
            if (enEste > 0 && (enEste >= 2 || largo + o.length() > 260)) { b.append("\n\n"); enEste = 0; largo = 0; }
            else if (enEste > 0) b.append(' ');
            b.append(o);
            enEste++;
            largo += o.length();
        }
        return juntar(p[0], b.toString(), p[2]);
    }

    static String enLista(String t) {
        String[] p = partes(t);
        List<String> os = oraciones(p[1]);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < os.size(); i++) b.append(i + 1).append(". ").append(os.get(i)).append(i < os.size() - 1 ? "\n" : "");
        return juntar(p[0], b.toString(), p[2]);
    }

    /** Ideas para mover: los párrafos si hay varios; si no, las oraciones. */
    static List<String> ideas(String cuerpo) {
        List<String> r = new ArrayList<>();
        for (String s : cuerpo.split("\n\\s*\n")) if (!s.trim().isEmpty()) r.add(s.trim());
        if (r.size() > 1) return r;
        return oraciones(cuerpo);
    }

    static boolean enParrafosSeparados(String cuerpo) { return cuerpo.split("\n\\s*\n").length > 1; }
}
