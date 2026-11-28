package co.jose.mibarra;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** Convierte un punto GPS en palabras: calle, barrio y ciudad, o la vía y un punto de referencia. */
final class Lugar {
    private Lugar() {}

    /** Se debe llamar fuera del hilo principal (usa internet). Devuelve "" si no hay datos. */
    static String describir(Context c, double lat, double lon) {
        String ref = referenciaPropia(c, lat, lon);
        try {
            if (!Geocoder.isPresent()) return ref;
            List<Address> rs = new Geocoder(c, new Locale("es", "CO")).getFromLocation(lat, lon, 5);
            if (rs == null || rs.isEmpty()) return ref;
            Address a = rs.get(0);
            String calle = a.getThoroughfare(), num = a.getSubThoroughfare(), barrio = a.getSubLocality(),
                    ciudad = a.getLocality(), depto = a.getAdminArea();
            for (Address x : rs) {
                if (barrio == null) barrio = x.getSubLocality();
                if (ciudad == null) ciudad = x.getLocality();
                if (ciudad == null) ciudad = x.getSubAdminArea();
            }
            boolean enVia = barrio == null && (num == null || calle == null || pareceVia(calle));
            StringBuilder s = new StringBuilder();
            if (!enVia) {
                if (calle != null) s.append(calle).append(num != null ? " # " + num : "");
                if (barrio != null) s.append(s.length() > 0 ? ", barrio " : "Barrio ").append(barrio);
                if (ciudad != null) s.append(", ").append(ciudad);
            } else {
                s.append("En la vía");
                if (calle != null) s.append(" ").append(calle);
                String altura = null;
                for (Address x : rs) {
                    String f = x.getFeatureName();
                    if (f != null && !f.matches("[0-9 +#-]+") && !f.equals(calle) && !f.equals(ciudad)) { altura = f; break; }
                }
                if (ref.isEmpty() && altura != null) s.append(", a la altura de ").append(altura);
                if (ciudad != null) s.append(", cerca de ").append(ciudad);
                if (depto != null) s.append(" (").append(depto).append(")");
            }
            if (!ref.isEmpty()) s.append("\n").append(ref);
            return s.toString();
        } catch (Exception e) {
            return ref;
        }
    }

    private static boolean pareceVia(String calle) {
        String c = calle.toLowerCase(Locale.ROOT);
        return c.contains("vía") || c.contains("via ") || c.contains("autopista") || c.contains("carretera")
                || c.contains("troncal") || c.contains("panamericana") || c.contains(" - ");
    }

    /** Busca el punto con nombre más cercano entre sus boyas y paradas guardadas (hasta 3 km). */
    private static String referenciaPropia(Context c, double lat, double lon) {
        String mejor = null;
        double dMin = 3000;
        for (String dir : new String[]{"recorridos", "paradas"}) {
            File[] fs = new File(c.getFilesDir(), dir).listFiles();
            if (fs == null) continue;
            for (File f : fs) {
                try {
                    for (String l : java.nio.file.Files.readAllLines(f.toPath())) {
                        String[] p = l.split("\t", -1);
                        String nombre; double la, lo;
                        if (p[0].equals("B") && p.length >= 6 && !p[5].isEmpty()) { la = Double.parseDouble(p[1]); lo = Double.parseDouble(p[2]); nombre = p[5]; }
                        else if (p[0].equals("S") && p.length >= 4 && !p[3].isEmpty()) { la = Double.parseDouble(p[1]); lo = Double.parseDouble(p[2]); nombre = p[3]; }
                        else continue;
                        double d = Recorrido.distancia(lat, lon, la, lo);
                        if (d < dMin) { dMin = d; mejor = nombre; }
                    }
                } catch (Exception ignored) {}
            }
        }
        if (mejor == null) return "";
        return dMin < 150 ? "Junto a " + mejor : "A " + (dMin < 1000 ? Math.round(dMin / 50) * 50 + " m" : CalculadoraActivity.dec(dMin / 1000, 1) + " km") + " de " + mejor;
    }
}
