package co.jose.mibarra;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Un recorrido guardado: puntos, boyas y utilidades (distancia, GPX, enlaces de Google Maps). */
final class Recorrido {
    String nombre = "Recorrido";
    String siguiendo = "";
    final List<double[]> puntos = new ArrayList<>();     // {lat, lon, tiempo, km/h}
    final List<String[]> boyas = new ArrayList<>();      // {lat, lon, tipo, nombre, calles}  (Versión 33: calles y carreras)

    static final String[] TIPOS = {"Parada", "Gasolinera", "Restaurante", "Peligro", "Fotomulta", "Hospital", "Taller", "Otro"};

    static String emoji(String tipo) {
        switch (tipo) {
            case "Gasolinera": return "⛽";
            case "Restaurante": return "🍽";
            case "Peligro": return "⚠️";
            case "Fotomulta": return "📸";
            case "Hospital": return "🏥";
            case "Taller": return "🔧";
            case "Parada": return "🅿️";
            default: return "📌";
        }
    }

    static Recorrido leer(File f) {
        try {
            Recorrido r = new Recorrido();
            for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p.length == 0) continue;
                switch (p[0]) {
                    case "N": if (p.length > 1) r.nombre = p[1]; break;
                    case "S": if (p.length > 1) r.siguiendo = p[1]; break;
                    case "P":
                        if (p.length >= 5) r.puntos.add(new double[]{Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                                Double.parseDouble(p[3]), Double.parseDouble(p[4])});
                        break;
                    case "B":
                        if (p.length >= 6) r.boyas.add(new String[]{p[1], p[2], p[4], p[5], ""});
                        break;
                    case "D":   // Versión 33: la dirección (calle con carrera) de la última boya
                        if (p.length > 1 && !r.boyas.isEmpty()) r.boyas.get(r.boyas.size() - 1)[4] = p[1];
                        break;
                }
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    void guardar(File f) throws Exception {
        StringBuilder s = new StringBuilder("N\t").append(nombre.replace('\t', ' ')).append('\n');
        if (!siguiendo.isEmpty()) s.append("S\t").append(siguiendo).append('\n');
        for (double[] p : puntos)
            s.append(String.format(Locale.US, "P\t%.6f\t%.6f\t%d\t%.1f\n", p[0], p[1], (long) p[2], p[3]));
        for (String[] b : boyas) {
            s.append("B\t").append(b[0]).append('\t').append(b[1]).append("\t0\t")
                    .append(b[2]).append('\t').append(b[3]).append('\n');
            if (b.length > 4 && b[4] != null && !b[4].isEmpty()) s.append("D\t").append(b[4].replace('\t', ' ').replace('\n', ' ')).append('\n');
        }
        Files.write(f.toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Distancia en metros entre dos puntos (fórmula de haversine). */
    static double distancia(double la1, double lo1, double la2, double lo2) {
        double r = 6371000, dla = Math.toRadians(la2 - la1), dlo = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dla / 2) * Math.sin(dla / 2)
                + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2)) * Math.sin(dlo / 2) * Math.sin(dlo / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    double metros() {
        double d = 0;
        for (int i = 1; i < puntos.size(); i++)
            d += distancia(puntos.get(i - 1)[0], puntos.get(i - 1)[1], puntos.get(i)[0], puntos.get(i)[1]);
        return d;
    }

    long duracionMs() {
        if (puntos.size() < 2) return 0;
        return (long) (puntos.get(puntos.size() - 1)[2] - puntos.get(0)[2]);
    }

    double velMax() {
        double m = 0;
        for (double[] p : puntos) m = Math.max(m, p[3]);
        return m;
    }

    static String duracion(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60;
        return h > 0 ? h + " h " + m + " min" : m + " min " + (s % 60) + " s";
    }

    /** Versión 33: enlace de Google Maps que pasa por las paradas con nombre (máx. 10 por tanda). */
    static String enlaceParadas(List<String[]> ps) {
        StringBuilder u = new StringBuilder("https://www.google.com/maps/dir/?api=1");
        String[] fin = ps.get(ps.size() - 1);
        u.append("&destination=").append(fin[0]).append(',').append(fin[1]);
        if (ps.size() > 1) {
            u.append("&waypoints=");
            for (int k = 0; k < ps.size() - 1; k++) {
                if (k > 0) u.append("%7C");
                u.append(ps.get(k)[0]).append(',').append(ps.get(k)[1]);
            }
        }
        u.append("&travelmode=driving");
        return u.toString();
    }

    /** Las boyas que son paradas con nombre (los señores), en el orden en que se marcaron. */
    List<String[]> paradasConNombre() {
        List<String[]> r = new ArrayList<>();
        for (String[] b : boyas) if (!b[3].isEmpty() && !"Salida".equals(b[2])) r.add(b);
        return r;
    }

    private static String ll(double[] p) { return String.format(Locale.US, "%.6f,%.6f", p[0], p[1]); }

    /** Enlace de Google Maps que sigue el recorrido (o al revés para devolverse). Máximo 8 puntos intermedios. */
    String enlaceMaps(boolean alReves, boolean desdeAqui) {
        if (puntos.isEmpty()) return "https://maps.google.com";
        List<double[]> p = new ArrayList<>(puntos);
        if (alReves) java.util.Collections.reverse(p);
        double[] origen = p.get(0), destino = p.get(p.size() - 1);
        StringBuilder u = new StringBuilder("https://www.google.com/maps/dir/?api=1");
        if (!desdeAqui) u.append("&origin=").append(ll(origen));
        u.append("&destination=").append(ll(destino));
        int n = Math.min(8, Math.max(0, p.size() - 2));
        if (n > 0) {
            u.append("&waypoints=");
            for (int k = 1; k <= n; k++) {
                int idx = (int) Math.round(k * (p.size() - 1) / (double) (n + 1));
                if (k > 1) u.append("%7C");
                u.append(ll(p.get(idx)));
            }
        }
        u.append("&travelmode=driving");
        return u.toString();
    }

    /** Archivo GPX: lo abren Google Mis Mapas, Wikiloc, OsmAnd… y Mi Barra de otra persona. */
    String gpx() {
        StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<gpx version=\"1.1\" creator=\"Mi Barra\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        for (String[] b : boyas)
            s.append("<wpt lat=\"").append(b[0]).append("\" lon=\"").append(b[1]).append("\"><name>")
             .append(Util.xml(b[2] + (b[3].isEmpty() ? "" : ": " + b[3]))).append("</name><type>")
             .append(Util.xml(b[2])).append("</type></wpt>\n");
        s.append("<trk><name>").append(Util.xml(nombre)).append("</name><trkseg>\n");
        for (double[] p : puntos)
            s.append(String.format(Locale.US, "<trkpt lat=\"%.6f\" lon=\"%.6f\"></trkpt>\n", p[0], p[1]));
        s.append("</trkseg></trk>\n</gpx>\n");
        return s.toString();
    }

    static Recorrido desdeGpx(String x) {
        Recorrido r = new Recorrido();
        Matcher n = Pattern.compile("<trk>\\s*<name>([^<]*)</name>").matcher(x);
        if (n.find()) r.nombre = n.group(1);
        Matcher pt = Pattern.compile("<(trkpt|rtept)[^>]*?lat=\"([-0-9.]+)\"[^>]*?lon=\"([-0-9.]+)\"").matcher(x);
        long t = 0;
        while (pt.find()) r.puntos.add(new double[]{Double.parseDouble(pt.group(2)), Double.parseDouble(pt.group(3)), t++ * 2000, 0});
        Matcher pt2 = Pattern.compile("<(trkpt|rtept)[^>]*?lon=\"([-0-9.]+)\"[^>]*?lat=\"([-0-9.]+)\"").matcher(x);
        if (r.puntos.isEmpty())
            while (pt2.find()) r.puntos.add(new double[]{Double.parseDouble(pt2.group(3)), Double.parseDouble(pt2.group(2)), t++ * 2000, 0});
        Matcher w = Pattern.compile("<wpt[^>]*?lat=\"([-0-9.]+)\"[^>]*?lon=\"([-0-9.]+)\"[^>]*>(.*?)</wpt>", Pattern.DOTALL).matcher(x);
        while (w.find()) {
            String cuerpo = w.group(3), tipo = "Otro", nombre = "";
            Matcher tm = Pattern.compile("<type>([^<]*)</type>").matcher(cuerpo);
            if (tm.find()) tipo = tm.group(1);
            Matcher nm = Pattern.compile("<name>([^<]*)</name>").matcher(cuerpo);
            if (nm.find()) {
                nombre = nm.group(1);
                if (nombre.startsWith(tipo + ": ")) nombre = nombre.substring(tipo.length() + 2);
                else if (nombre.equals(tipo)) nombre = "";
            }
            r.boyas.add(new String[]{w.group(1), w.group(2), tipo, nombre.replace("&amp;", "&")});
        }
        r.nombre = r.nombre.replace("&amp;", "&");
        return r.puntos.isEmpty() ? null : r;
    }
}
