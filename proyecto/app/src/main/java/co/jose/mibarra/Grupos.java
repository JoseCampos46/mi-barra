package co.jose.mibarra;

import android.content.Context;
import android.content.Intent;

/**
 * Versión 48: las herramientas de ChpBar salen como apps sueltas «Chp…».
 * Aquí está el reparto: qué app tiene cada pantalla y qué botones muestra al abrirla.
 * Todas salen del mismo proyecto; el taller (GitHub) arma cada una con su propio nombre e ícono.
 */
public final class Grupos {
    private Grupos() {}

    /** Un botón dentro de la app: emoji, nombre, color, pantalla y (si hace falta) un dato extra. */
    static final class Boton {
        final String emoji, nombre, clase, extra; final int color, valor;
        Boton(String emoji, String nombre, int color, String clase, String extra, int valor) {
            this.emoji = emoji; this.nombre = nombre; this.color = color; this.clase = clase; this.extra = extra; this.valor = valor;
        }
    }

    static final class Grupo {
        final String id, paquete, nombre; final int color; final Boton[] botones;
        Grupo(String id, String nombre, int color, Boton... b) {
            this.id = id; this.paquete = "co.jose.chp" + id; this.nombre = nombre; this.color = color; this.botones = b;
        }
    }

    private static final String P = "co.jose.mibarra.";
    private static Boton b(String e, String n, int c, String clase) { return new Boton(e, n, c, P + clase, null, 0); }
    private static Boton b(String e, String n, int c, String clase, String extra, int v) { return new Boton(e, n, c, P + clase, extra, v); }

    static final Grupo[] TODOS = {
            new Grupo("calcu", "ChpCalcu", 0xFF2E7D32,
                    b("🧮", "Calculadora", 0xFF2E7D32, "CalculadoraActivity", "pestana", 0),
                    b("📏", "mm ↔ pulgadas", 0xFF3949AB, "CalculadoraActivity", "pestana", 1),
                    b("🏗", "Concreto", 0xFF607D8B, "CalculadoraActivity", "pestana", 2),
                    b("🧱", "Ladrillo", 0xFF795548, "CalculadoraActivity", "pestana", 3),
                    b("📋", "Tabla de obra", 0xFF00695C, "TablaActivity"),
                    b("📸", "Foto con medidas", 0xFFEF6C00, "MedidasActivity")),
            new Grupo("nivel", "ChpNivel", 0xFF6A1B9A,
                    b("📐", "Nivel", 0xFF6A1B9A, "NivelActivity", "modo", 0),
                    b("🏠", "Grados del techo", 0xFF4527A0, "NivelActivity", "modo", 3),
                    b("🎯", "Plomada", 0xFF283593, "NivelActivity", "modo", 2)),
            new Grupo("radio", "ChpRadio", 0xFFD50000,
                    b("⏰", "Radio reloj", 0xFFD50000, "RadioRelojActivity"),
                    b("⏱", "Reloj de cocina", 0xFFD84315, "RelojActivity"),
                    b("📻", "Radio", 0xFFAD1457, "RadioActivity"),
                    b("🎧", "Reproductor", 0xFF6A1B9A, "ReproductorActivity"),
                    b("📺", "TV gratis", 0xFF4527A0, "TvActivity")),
            new Grupo("grabar", "ChpGrabar", 0xFF37474F,
                    b("🎙", "Grabadora", 0xFF37474F, "GrabadoraActivity")),
            new Grupo("escan", "ChpEscán", 0xFF8E24AA,
                    b("📄", "Escáner de documentos", 0xFF8E24AA, "EscanerActivity"),
                    b("🔳", "Leer código QR", 0xFF212121, "CodigoActivity")),
            new Grupo("gps", "ChpGPS", 0xFF00897B,
                    b("🧭", "GPS", 0xFF00897B, "GpsActivity"),
                    b("🗺", "Rutas y recorridos", 0xFF00B8D4, "ParadasActivity"),
                    b("📍", "Mis lugares", 0xFFD84315, "LugaresActivity"),
                    b("🙋", "Aquí estoy", 0xFFC62828, "AquiEstoyActivity"),
                    b("🏍", "Antes de salir", 0xFFF57F17, "ChequeoActivity"),
                    b("🚕", "Transporte", 0xFFFFB300, "TransporteActivity")),
            new Grupo("telefono", "ChpTeléfono", 0xFF2E7D32,
                    b("📞", "Mi Teléfono", 0xFF2E7D32, "tel.MarcadorActivity")),
            new Grupo("correo", "ChpCorreo", 0xFFC62828,
                    b("📧", "Mis Correos", 0xFFC62828, "CorreosActivity")),
            new Grupo("cobro", "ChpCobro", 0xFF6D4C41,
                    b("🧾", "Cuenta de cobro", 0xFF6D4C41, "CobroActivity"),
                    b("💵", "Cuenta y recibos", 0xFF2E7D32, "CuentaActivity"),
                    b("📇", "Proveedores", 0xFF455A64, "ProveedoresActivity"),
                    b("🗓", "Abonos de la semana", 0xFFEF6C00, "AbonosActivity")),
            new Grupo("notas", "ChpNotas", 0xFF1565C0,
                    b("📝", "Cuaderno", 0xFF1565C0, "NotasActivity"),
                    b("🛒", "Lista de compras", 0xFF00838F, "ComprasActivity"),
                    b("✍", "Escribir bien", 0xFF1565C0, "EscribirActivity"),
                    b("📊", "Cuentas (mini Excel)", 0xFF2E7D32, "HojaActivity")),
            new Grupo("medir", "ChpMedir", 0xFF00695C,
                    b("📏", "Tomar medidas", 0xFF00695C, "MedirActivity")),
    };

    /** El grupo de esta app (si es una de las apps sueltas). */
    static Grupo propio(Context c) { return porPaquete(c.getPackageName()); }

    static Grupo porPaquete(String p) {
        for (Grupo g : TODOS) if (g.paquete.equals(p)) return g;
        return null;
    }

    /** ¿Qué app suelta tiene esta pantalla? */
    static Grupo deClase(String clase) {
        if (clase == null) return null;
        for (Grupo g : TODOS) for (Boton x : g.botones) if (x.clase.equals(clase)) return g;
        return null;
    }

    static boolean instalada(Context c, String paquete) {
        try { c.getPackageManager().getPackageInfo(paquete, 0); return true; } catch (Exception e) { return false; }
    }

    /**
     * Si la pantalla pedida ya vive en su propia app (instalada), devuelve cómo abrirla allá
     * (por la puerta de entrada de esa app, con los mismos datos). Si no, null: se abre aquí como siempre.
     */
    static Intent desviar(Context c, Intent i) {
        if (i == null || i.getComponent() == null) return null;
        String clase = i.getComponent().getClassName();
        Grupo g = deClase(clase);
        if (g == null || g.paquete.equals(c.getPackageName()) || !instalada(c, g.paquete)) return null;
        Intent r = new Intent(Intent.ACTION_MAIN)
                .setClassName(g.paquete, P + "GrupoActivity")
                .putExtra("abrir_clase", clase)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (i.getExtras() != null) r.putExtras(i.getExtras());
        return r;
    }

    /** Las herramientas del Inicio que ya salieron como app suelta (para no mostrarlas dos veces). */
    static String paqueteDeHerramienta(String id) {
        switch (id) {
            case "calculadora": case "pulgadas": case "concreto": case "ladrillo": case "tabla": case "medidas": return "co.jose.chpcalcu";
            case "nivel": case "grados": case "plomada": return "co.jose.chpnivel";
            case "radioreloj": case "reloj": case "radio": case "audios": case "tvgratis": return "co.jose.chpradio";
            case "grabadora": return "co.jose.chpgrabar";
            case "escaner": case "codigos": return "co.jose.chpescan";
            case "ruta": case "gps": case "lugares": case "antesdesalir": case "transporte": return "co.jose.chpgps";
            case "telefono": return "co.jose.chptelefono";
            case "correos": return "co.jose.chpcorreo";
            case "cobro": case "proveedores": return "co.jose.chpcobro";
            case "cuaderno": case "compras": case "escribir": return "co.jose.chpnotas";
            case "tv": return "co.jose.micontrol";
            default: return null;
        }
    }
}
