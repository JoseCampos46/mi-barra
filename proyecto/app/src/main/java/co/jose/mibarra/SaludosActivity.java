package co.jose.mibarra;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Saludos y memes: usted dice "buenas noches papá" o "no estés triste, vendrán días mejores",
 * la app busca frases y refranes de ese tema, les pone una figurita y un fondo bonito,
 * y con un toque se van por WhatsApp. Todo viene guardado en la app: sin internet y sin costo.
 */
public class SaludosActivity extends Base {

    // ---------------- Temas: título, figuritas, palabras que lo activan, frases y refranes ----------------

    static final class Tema {
        final String nombre, titulo; final String[] figuras, claves, frases; final int[] fondos;
        Tema(String nombre, String titulo, String[] figuras, String[] claves, int[] fondos, String... frases) {
            this.nombre = nombre; this.titulo = titulo; this.figuras = figuras; this.claves = claves;
            this.fondos = fondos; this.frases = frases;
        }
    }

    static String[] a(String... s) { return s; }
    static int[] f(int... s) { return s; }

    // Fondos: 0 noche, 1 amanecer, 2 atardecer, 3 flores, 4 corazones, 5 fiesta, 6 elegante, 7 luces, 8 cielo
    static final Tema[] TEMAS = {
        new Tema("🌙 Buenas noches", "Buenas noches", a("🌙", "⭐", "😴", "🦉", "🌛"),
            a("noche", "dormir", "descans", "suenos", "sueño"), f(0, 7, 6, 3, 4),
            "Que descanses y sueñes bonito. Mañana será un gran día.",
            "Dios te cuide esta noche y te regale un amanecer lleno de bendiciones.",
            "Deja las preocupaciones a un lado y descansa. Te lo mereces.",
            "Que la luna te acompañe y las estrellas cuiden tus sueños.",
            "Gracias por este día. Que tengas una noche de paz.",
            "Un abrazo antes de dormir. Dulces sueños.",
            "La almohada es buena consejera: descansa y mañana lo ves más claro."),
        new Tema("☀ Buenos días", "Buenos días", a("☀️", "☕", "🌻", "🐦", "🌞"),
            a("buenos dias", "dia", "manana", "despert", "madrug", "cafe"), f(1, 8, 3, 6, 7),
            "Que hoy todo te salga bien. ¡Arriba ese ánimo!",
            "Un nuevo día, una nueva oportunidad. Dios te bendiga.",
            "Que tu café esté caliente y tu día lleno de cosas buenas.",
            "Hoy es un buen día para sonreír. ¡Feliz {dia}!",
            "Despierta con alegría: la vida es un regalo.",
            "Al que madruga, Dios lo ayuda. ¡Feliz {dia}!",
            "Te mando un abrazo para empezar el día con toda."),
        new Tema("🌇 Buenas tardes", "Buenas tardes", a("🌇", "☕", "🌺", "🌤️"),
            a("tarde"), f(2, 1, 3, 6),
            "Que lo que queda del día sea tan bonito como tú.",
            "Una pausa, un café y una sonrisa. ¡Feliz tarde!",
            "Paso a saludarte y desearte una tarde tranquila.",
            "Que la tarde te traiga paz y buenas noticias.",
            "Ya casi termina el día: ¡tú puedes con todo!",
            "Te mando un saludo y un abrazo en esta tarde."),
        new Tema("💪 Ánimo", "¡Ánimo!", a("🌻", "🌈", "💪", "🕊️", "🤗", "☀️"),
            a("triste", "animo", "mejores", "fuerza", "dificil", "problema", "llor", "rend", "mal", "depre",
              "solo", "sola", "preocup", "aguanta", "adelante", "esperanza", "levanta"), f(8, 1, 7, 2, 3),
            "No estés triste: vendrán días mejores.",
            "Siempre que llueve, escampa.",
            "No hay mal que dure cien años.",
            "Después de la tormenta viene la calma.",
            "Al mal tiempo, buena cara.",
            "Dios aprieta, pero no ahorca.",
            "No hay mal que por bien no venga.",
            "No hay noche tan larga que no llegue el amanecer.",
            "La esperanza es lo último que se pierde.",
            "Lo que no te mata, te hace más fuerte.",
            "Eres más fuerte de lo que crees. ¡Tú puedes!",
            "Después de la tormenta siempre sale el sol.",
            "Paso a paso se llega lejos. Aquí estoy para lo que necesites.",
            "Dios no te suelta la mano. Fuerza y fe.",
            "Respira hondo, levanta la cabeza y sigue adelante.",
            "Mañana será otro día."),
        new Tema("🐢 Paciencia", "Paciencia", a("🐢", "🌱", "⏳", "🍃"),
            a("pacien", "espera", "calma", "tranquil", "despacio", "afan", "demora"), f(8, 3, 6, 7),
            "Con paciencia se gana el cielo.",
            "Vísteme despacio, que voy de afán.",
            "Poco a poco se anda lejos.",
            "Grano a grano llena la gallina el buche.",
            "Todo llega a su tiempo.",
            "El que espera lo mucho, espera lo poco.",
            "Dios tarda, pero no olvida."),
        new Tema("⚒ Trabajo", "¡Con toda!", a("💪", "🐓", "🔨", "🚀", "⭐"),
            a("trabaj", "esfuerz", "lucha", "meta", "exito", "negocio", "chamba", "berraco", "verraco"), f(1, 8, 7, 5),
            "El que persevera, alcanza.",
            "Al que madruga, Dios lo ayuda.",
            "Camarón que se duerme se lo lleva la corriente.",
            "Querer es poder.",
            "El trabajo duro siempre tiene su recompensa.",
            "Hoy siembras, mañana cosechas.",
            "Más vale paso que dure y no trote que canse."),
        new Tema("🙏 Bendiciones", "Bendiciones", a("🙏", "🕊️", "🌹", "✨", "⛪"),
            a("bendic", "dios", "fe", "orar", "rezo", "amen", "senor", "virgen"), f(8, 6, 3, 1),
            "Que Dios guíe tus pasos y te llene de paz.",
            "Hoy pido a Dios por ti: salud, trabajo y amor.",
            "Que nunca te falte la fe ni la esperanza.",
            "Dios está contigo en cada paso que das.",
            "Que la luz de Dios ilumine tu camino hoy y siempre.",
            "Bendiciones para ti y para los tuyos.",
            "A Dios rogando y con el mazo dando."),
        new Tema("🎂 Cumpleaños", "¡Feliz cumpleaños!", a("🎂", "🎈", "🎁", "🥳", "🎉"),
            a("cumple", "aniversario", "anos", "torta"), f(5, 4, 3, 6, 8),
            "Que Dios te regale muchos años más de vida, salud y alegría.",
            "Hoy celebro la vida de una persona maravillosa: ¡tú!",
            "Que todos tus sueños se cumplan en este nuevo año.",
            "Un año más de bendiciones. Disfruta tu día al máximo.",
            "Que la torta esté rica y los abrazos sean muchos.",
            "Gracias por existir. Que este año sea el mejor de todos."),
        new Tema("💐 Gracias", "Gracias", a("💐", "🙏", "🤗", "🌷"),
            a("gracias", "agradec", "favor", "ayuda"), f(3, 6, 4, 8),
            "Gracias por estar siempre ahí. Vales oro.",
            "Gracias de corazón por tu ayuda.",
            "Personas como tú hacen la vida más bonita. ¡Gracias!",
            "No tengo cómo pagarte. Dios te lo multiplique.",
            "Gracias por tu cariño y tu tiempo.",
            "Es de bien nacido ser agradecido."),
        new Tema("🤝 Amistad", "Amigos", a("🤝", "🤗", "🌻", "☕"),
            a("amig", "parce", "compadre", "hermano", "amistad"), f(3, 8, 6, 7),
            "Quien tiene un amigo tiene un tesoro.",
            "Amigo en la adversidad es amigo de verdad.",
            "Dime con quién andas y te diré quién eres.",
            "Los amigos son la familia que uno escoge.",
            "Gracias por ser ese amigo que siempre está."),
        new Tema("❤ Te quiero", "Te quiero", a("❤️", "🌹", "💕", "🧸", "💌"),
            a("quiero", "amo", "amor", "extran", "corazon", "carino", "beso"), f(4, 3, 6, 7, 0),
            "Solo pasaba a decirte que te quiero mucho.",
            "Eres una de las personas más importantes de mi vida.",
            "Gracias por existir. Te llevo en el corazón.",
            "No necesito una fecha especial para decirte que te quiero.",
            "Contigo la vida es más bonita.",
            "Un abrazo fuerte desde aquí hasta donde estés."),
        new Tema("🌷 Mejórate", "Que te mejores", a("🌷", "🍵", "🤗", "🍀"),
            a("enferm", "mejor", "salud", "hospital", "clinica", "operac", "recupera", "medico"), f(3, 8, 6, 1),
            "Que te mejores pronto. Dios es el médico de los médicos.",
            "Pronto estarás como nuevo. Mucha fuerza.",
            "Te mando salud, ánimo y un abrazo grande.",
            "Descansa y cuídate mucho. Aquí estamos para lo que necesites."),
        new Tema("🎉 Fin de semana", "¡Feliz fin de semana!", a("🎉", "😎", "🏖️", "🎶"),
            a("fin de semana", "finde", "viernes", "sabado", "domingo", "puente", "festivo"), f(5, 2, 8, 3),
            "Que disfrutes este fin de semana con los que más quieres.",
            "Llegó el fin de semana: a descansar y a gozar.",
            "Desconéctate, ríe y recarga energías.",
            "Que no falte la alegría ni la buena comida.",
            "Descansa, que te lo ganaste."),
        new Tema("🕊 Condolencias", "Mi más sentido pésame", a("🕊️", "🌹", "🕯️"),
            a("pesame", "fallec", "muri", "murio", "condolen", "luto", "descanse en paz", "partio"), f(6, 0, 7),
            "Que Dios le dé fortaleza en este momento tan difícil.",
            "Lo acompaño en su dolor. Que descanse en paz.",
            "Su recuerdo vivirá siempre en nuestros corazones.",
            "No hay palabras para este dolor, pero aquí estoy para lo que necesite."),
    };

    static final String[] DESTINOS = {"General", "Papá", "Mamá", "Amigo", "Amiga", "Mi amor", "Hijo", "Hija", "Familia", "Cliente"};
    static final String[] SALUDO = {"", "Papá querido", "Mamita linda", "Querido amigo", "Querida amiga", "Mi amor",
            "Hijo querido", "Hija querida", "Querida familia", "Estimado cliente"};
    static final String[] CIERRE = {"", "Gracias por todo, papá. Te quiero mucho.", "Eres mi bendición más grande.",
            "Un abrazo grande, parcero.", "Un abrazo con mucho cariño.", "Te amo con todo mi corazón.",
            "Estoy muy orgulloso de ti.", "Estoy muy orgulloso de ti.", "Los quiero mucho a todos.",
            "Gracias por su confianza."};

    // ---------------- Pantalla ----------------

    private static final int VOZ = 71, FOTO = 72;
    private EditText pedido, para, arriba, abajo;
    private LinearLayout grilla, cuerpo, chipsTemas, chipsDest;
    private ImageView vistaMeme;
    private int tema = 0, destino = 0, vuelta = 0;
    private String propia = "";
    private Bitmap fotoMeme;
    private boolean enMemes;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout raiz = vertical();
        raiz.setBackgroundColor(0xFFF6F3FA);
        LinearLayout pest = horizontal();
        Button p1 = boton("💌 Saludos", 0xFF7B1FA2), p2 = boton("😂 Memes", 0xFF455A64);
        p1.setOnClickListener(v -> { enMemes = false; p1.setBackgroundColor(0xFF7B1FA2); p2.setBackgroundColor(0xFF455A64); pintarSaludos(); });
        p2.setOnClickListener(v -> { enMemes = true; p2.setBackgroundColor(0xFF7B1FA2); p1.setBackgroundColor(0xFF455A64); pintarMemes(); });
        pest.addView(p1, peso(1));
        pest.addView(p2, peso(1));
        raiz.addView(pest, ancho(50));
        cuerpo = vertical();
        ScrollView sv = new ScrollView(this);
        sv.addView(cuerpo);
        raiz.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(raiz);
        int t = getSharedPreferences("barra", MODE_PRIVATE).getInt("saludoTema", -1);
        tema = t >= 0 && t < TEMAS.length ? t : temaPorHora();
        pintarSaludos();
    }

    private static int temaPorHora() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h < 12 ? 1 : h < 18 ? 2 : 0;
    }

    private TextView chip(String t, boolean activo, Runnable r) {
        TextView c = new TextView(this);
        c.setText(t);
        c.setTextSize(15);
        c.setPadding(dp(14), dp(8), dp(14), dp(8));
        c.setTextColor(activo ? Color.WHITE : 0xFF4A148C);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(activo ? 0xFF7B1FA2 : Color.WHITE);
        g.setStroke(dp(1), 0xFFCE93D8);
        c.setBackground(g);
        c.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        c.setLayoutParams(lp);
        return c;
    }

    private HorizontalScrollView fila(LinearLayout l) {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(l);
        return h;
    }

    private void pintarSaludos() {
        cuerpo.removeAllViews();
        int p = dp(8);
        cuerpo.setPadding(p, p, p, p);

        LinearLayout busca = horizontal();
        busca.setGravity(Gravity.CENTER_VERTICAL);
        Button mic = boton("🎤", 0xFFD81B60);
        mic.setTextSize(22);
        mic.setOnClickListener(v -> hablar());
        busca.addView(mic, new LinearLayout.LayoutParams(dp(58), dp(54)));
        pedido = campoTexto("Diga o escriba: buenas noches papá…", propia);
        pedido.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        pedido.setSingleLine(true);
        pedido.setOnEditorActionListener((v, id, e) -> { buscar(pedido.getText().toString()); return true; });
        busca.addView(pedido, peso(1));
        Button ir = boton("🔎", 0xFF7B1FA2);
        ir.setOnClickListener(v -> buscar(pedido.getText().toString()));
        busca.addView(ir, new LinearLayout.LayoutParams(dp(54), dp(54)));
        cuerpo.addView(busca);

        chipsTemas = horizontal();
        for (int i = 0; i < TEMAS.length; i++) {
            final int k = i;
            chipsTemas.addView(chip(TEMAS[i].nombre, i == tema, () -> { tema = k; propia = ""; vuelta = 0; pintarSaludos(); }));
        }
        cuerpo.addView(fila(chipsTemas));
        chipsDest = horizontal();
        for (int i = 0; i < DESTINOS.length; i++) {
            final int k = i;
            chipsDest.addView(chip(DESTINOS[i], i == destino, () -> { destino = k; pintarSaludos(); }));
        }
        cuerpo.addView(fila(chipsDest));

        LinearLayout nom = horizontal();
        para = campoTexto("Nombre (opcional): Pedro", getSharedPreferences("barra", MODE_PRIVATE).getString("saludoPara", ""));
        para.setSingleLine(true);
        nom.addView(para, peso(1));
        Button listo = boton("✔", 0xFF2E7D32);
        listo.setOnClickListener(v -> {
            getSharedPreferences("barra", MODE_PRIVATE).edit().putString("saludoPara", para.getText().toString().trim()).apply();
            llenarGrilla();
        });
        nom.addView(listo, new LinearLayout.LayoutParams(dp(54), dp(50)));
        cuerpo.addView(nom);

        TextView ayuda = new TextView(this);
        ayuda.setText("Toque la que más le guste y se va directo a WhatsApp.");
        ayuda.setTextColor(0xFF6A1B9A);
        ayuda.setPadding(dp(4), dp(6), 0, dp(4));
        cuerpo.addView(ayuda);

        grilla = vertical();
        cuerpo.addView(grilla);
        Button mas = boton("🔀  Ver otras", 0xFF8E24AA);
        mas.setOnClickListener(v -> { vuelta++; llenarGrilla(); });
        cuerpo.addView(mas, ancho(54));
        llenarGrilla();
    }

    /** Cada opción: frase + figurita + fondo. */
    static final class Opcion { String frase; String figura; int fondo; long semilla; }

    private List<Opcion> opciones() {
        Tema t = TEMAS[tema];
        List<String> frases = new ArrayList<>();
        if (!propia.isEmpty()) frases.add(propia);
        for (String s : t.frases) if (!s.equalsIgnoreCase(propia)) frases.add(s);
        List<Opcion> r = new ArrayList<>();
        int n = Math.min(8, frases.size() + (frases.size() < 8 ? 2 : 0));
        int base = vuelta * 8;
        for (int i = 0; i < n; i++) {
            Opcion o = new Opcion();
            int k = base + i;
            o.frase = frases.get((propia.isEmpty() || i > 0 ? k : i) % frases.size());
            if (!propia.isEmpty() && i == 0) o.frase = propia;
            o.figura = t.figuras[(k + vuelta) % t.figuras.length];
            o.fondo = t.fondos[(k + vuelta / 2) % t.fondos.length];
            o.semilla = k * 7919L + tema * 31L;
            r.add(o);
        }
        return r;
    }

    private void llenarGrilla() {
        grilla.removeAllViews();
        final List<Opcion> ops = opciones();
        final String nombre = para == null ? "" : para.getText().toString().trim();
        final int ancho = (getResources().getDisplayMetrics().widthPixels - dp(28)) / 2;
        LinearLayout filaActual = null;
        final ImageView[] vistas = new ImageView[ops.size()];
        for (int i = 0; i < ops.size(); i++) {
            if (i % 2 == 0) {
                filaActual = horizontal();
                grilla.addView(filaActual);
            }
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setBackgroundColor(0xFFE1D5EA);
            final Opcion o = ops.get(i);
            iv.setOnClickListener(v -> mandar(o, nombre));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ancho, ancho * 5 / 4);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            filaActual.addView(iv, lp);
            vistas[i] = iv;
        }
        new Thread(() -> {
            for (int i = 0; i < ops.size(); i++) {
                final Bitmap bm = tarjeta(ops.get(i), nombre, 432);
                final int k = i;
                runOnUiThread(() -> vistas[k].setImageBitmap(bm));
            }
        }).start();
    }

    private void mandar(Opcion o, String nombre) {
        aviso("💌 Preparando la tarjeta…");
        new Thread(() -> {
            try {
                Bitmap bm = tarjeta(o, nombre, 1080);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, 92, bo);
                Uri u = Util.guardarBytes(this, "Saludo_" + Util.fecha() + ".jpg", "image/jpeg", bo.toByteArray());
                runOnUiThread(() -> enviar(WHATSAPP, "image/jpeg", u));
            } catch (Exception e) {
                runOnUiThread(() -> aviso("No se pudo hacer la tarjeta: " + e.getMessage()));
            }
        }).start();
    }

    // ---------------- Voz y búsqueda ----------------

    private void hablar() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga qué quiere mandar");
        try { startActivityForResult(i, VOZ); }
        catch (Exception e) { aviso("Este celular no tiene el dictado por voz de Google. Escríbalo."); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (res != RESULT_OK || d == null) return;
        if (req == VOZ) {
            ArrayList<String> r = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) buscar(r.get(0));
        } else if (req == FOTO && d.getData() != null) {
            try {
                fotoMeme = cargarFoto(d.getData());
                dibujarMeme();
            } catch (Exception e) { aviso("No se pudo abrir la foto"); }
        }
    }

    static String sinTildes(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Decide el tema por las palabras y, si la frase es propia, la pone de primera. */
    private void buscar(String dicho) {
        String d = dicho.trim();
        String n = " " + sinTildes(d) + " ";
        int mejor = -1, puntos = 0;
        for (int i = 0; i < TEMAS.length; i++) {
            int p = 0;
            for (String c : TEMAS[i].claves) if (n.contains(sinTildes(c))) p += c.length();
            if (p > puntos) { puntos = p; mejor = i; }
        }
        // "buenos días / buenas tardes / buenas noches" mandan sobre lo demás
        if (n.contains("buenas noches")) mejor = 0;
        else if (n.contains("buenos dias")) mejor = 1;
        else if (n.contains("buenas tardes")) mejor = 2;
        for (int i = 1; i < DESTINOS.length; i++) {
            String dd = sinTildes(DESTINOS[i]).replace("mi ", "");
            if (n.contains(" " + dd + " ") || n.contains(" " + dd + ",") || n.contains(" " + dd + "s ")) destino = i;
        }
        if (n.contains(" mami ") || n.contains(" mamita ")) destino = 2;
        if (n.contains(" papi ") || n.contains(" papito ")) destino = 1;
        if (n.contains(" amor ") || n.contains(" esposa ") || n.contains(" esposo ") || n.contains(" novia ")) destino = 5;
        if (mejor < 0) mejor = 3; // sin pista: frases de ánimo
        tema = mejor;
        getSharedPreferences("barra", MODE_PRIVATE).edit().putInt("saludoTema", tema).apply();
        // Si dijo algo más que el saludo corto, su frase también sale como tarjeta.
        String corta = n.replaceAll("[^a-z ]", " ").trim().replaceAll(" +", " ");
        boolean soloSaludo = corta.matches("(buenas noches|buenos dias|buenas tardes|feliz cumpleanos|gracias|bendiciones|animo)"
                + "( (a )?(mi )?(papa|mama|amigo|amiga|amor|hijo|hija|familia|cliente|papi|mami))?");
        propia = soloSaludo || d.isEmpty() ? "" : arreglar(d);
        vuelta = 0;
        pintarSaludos();
    }

    /** Mayúscula al principio y punto al final. */
    static String arreglar(String s) {
        s = s.trim();
        if (s.isEmpty()) return s;
        s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        char u = s.charAt(s.length() - 1);
        if (u != '.' && u != '!' && u != '?' && u != '…') s += ".";
        return s;
    }

    // ---------------- Dibujo de la tarjeta (4:5, como foto de WhatsApp) ----------------

    static final String[] DIAS = {"domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"};

    Bitmap tarjeta(Opcion o, String nombre, int w) {
        int h = w * 5 / 4;
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Random r = new Random(o.semilla);
        boolean oscuro = fondo(c, o.fondo, w, h, r);
        Tema t = TEMAS[tema];
        int texto = oscuro ? Color.WHITE : 0xFF3E2723;
        int sombra = oscuro ? 0xAA000000 : 0x55FFFFFF;

        // Figurita grande con un halo blanco, como sticker
        float fy = h * 0.20f, fr = w * 0.17f;
        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setShader(new RadialGradient(w / 2f, fy, fr * 1.25f, new int[]{0xDDFFFFFF, 0x66FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(w / 2f, fy, fr * 1.25f, halo);
        Paint em = new Paint(Paint.ANTI_ALIAS_FLAG);
        em.setTextSize(fr * 1.35f);
        em.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = em.getFontMetrics();
        c.drawText(o.figura, w / 2f, fy - (fm.ascent + fm.descent) / 2f, em);

        // Título en letra de mano
        String titulo = t.titulo;
        if (destino == 9 && tema == 10) titulo = "Con aprecio";
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(Typeface.create("cursive", Typeface.BOLD));
        tp.setColor(oscuro ? 0xFFFFF59D : 0xFFAD1457);
        tp.setShadowLayer(w * 0.008f, 0, w * 0.004f, sombra);
        float y = h * 0.39f;
        y = parrafo(c, titulo, tp, w * 0.115f, w, y, h * 0.16f) + h * 0.01f;

        // A quién va
        boolean conPalabra = destino == 3 || destino == 4 || destino == 8 || destino == 9;
        String quien = nombre.isEmpty() ? SALUDO[destino]
                : conPalabra ? SALUDO[destino].split(" ")[0] + " " + nombre : nombre;
        if (!quien.isEmpty()) {
            TextPaint qp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            qp.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
            qp.setColor(texto);
            qp.setShadowLayer(w * 0.006f, 0, w * 0.003f, sombra);
            y = parrafo(c, quien, qp, w * 0.058f, w, y, h * 0.08f) + h * 0.015f;
        }

        // La frase
        String frase = o.frase.replace("{dia}", DIAS[java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1]);
        boolean refran = !frase.contains(" te ") && !frase.contains(" tu ") && !frase.contains(" ti") && frase.length() < 60;
        if (refran && tema >= 3 && tema <= 6) frase = "«" + frase + "»";
        if (destino > 0 && (o.semilla / 7919) % 2 == 1) frase += "\n" + CIERRE[destino];
        TextPaint fp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        fp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        fp.setColor(texto);
        fp.setShadowLayer(w * 0.007f, 0, w * 0.003f, sombra);
        parrafo(c, frase, fp, w * 0.066f, w, y, h * 0.90f - y);

        // Pie: la fecha y la firma
        String firma = getSharedPreferences("barra", MODE_PRIVATE).getString("saludoFirma", "");
        String fecha = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "CO")).format(new Date());
        fecha = Character.toUpperCase(fecha.charAt(0)) + fecha.substring(1);
        Paint pie = new Paint(Paint.ANTI_ALIAS_FLAG);
        pie.setTextSize(w * 0.034f);
        pie.setTextAlign(Paint.Align.CENTER);
        pie.setColor(oscuro ? 0xCCFFFFFF : 0xAA3E2723);
        c.drawText(firma.isEmpty() ? fecha : fecha + "  ·  " + firma, w / 2f, h * 0.955f, pie);
        return bm;
    }

    /** Escribe un párrafo centrado, achicando la letra hasta que quepa. Devuelve dónde termina. */
    static float parrafo(Canvas c, String s, TextPaint p, float tam, int w, float y, float alto) {
        int anchoTxt = (int) (w * 0.84f);
        StaticLayout l;
        do {
            p.setTextSize(tam);
            l = StaticLayout.Builder.obtain(s, 0, s.length(), p, anchoTxt)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0, 1.12f).build();
            tam *= 0.92f;
        } while (l.getHeight() > alto && tam > w * 0.025f);
        c.save();
        c.translate((w - anchoTxt) / 2f, y);
        l.draw(c);
        c.restore();
        return y + l.getHeight();
    }

    private static Paint degradado(float x0, float y0, float x1, float y1, int... colores) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(x0, y0, x1, y1, colores, null, Shader.TileMode.CLAMP));
        return p;
    }

    /** Dibuja el fondo. Devuelve true si es oscuro (letra blanca). */
    static boolean fondo(Canvas c, int tipo, int w, int h, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        switch (tipo) {
            case 0: { // Noche con luna y estrellas
                c.drawRect(0, 0, w, h, degradado(0, 0, 0, h, 0xFF0B1033, 0xFF2B2A6B, 0xFF5B3C88));
                p.setColor(Color.WHITE);
                for (int i = 0; i < 90; i++) {
                    p.setAlpha(80 + r.nextInt(175));
                    c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, w * (0.002f + r.nextFloat() * 0.004f), p);
                }
                p.setAlpha(255);
                Path luna = new Path(), corte = new Path();
                float lx = w * 0.82f, ly = h * 0.09f, lr = w * 0.08f;
                luna.addCircle(lx, ly, lr, Path.Direction.CW);
                corte.addCircle(lx + lr * 0.45f, ly - lr * 0.25f, lr * 0.85f, Path.Direction.CW);
                luna.op(corte, Path.Op.DIFFERENCE);
                Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.setColor(0x55FFF59D);
                g.setMaskFilter(new BlurMaskFilter(w * 0.03f, BlurMaskFilter.Blur.NORMAL));
                c.drawPath(luna, g);
                p.setColor(0xFFFFF3B0);
                c.drawPath(luna, p);
                return true;
            }
            case 1: { // Amanecer con sol
                c.drawRect(0, 0, w, h, degradado(0, 0, 0, h, 0xFFFFE08A, 0xFFFFB25B, 0xFFFF7E5F));
                float sx = w * 0.85f, sy = h * 0.07f;
                p.setColor(0x66FFFFFF);
                p.setStrokeWidth(w * 0.012f);
                for (int i = 0; i < 16; i++) {
                    double ang = Math.PI * 2 * i / 16;
                    c.drawLine(sx + (float) Math.cos(ang) * w * 0.12f, sy + (float) Math.sin(ang) * w * 0.12f,
                            sx + (float) Math.cos(ang) * w * 0.2f, sy + (float) Math.sin(ang) * w * 0.2f, p);
                }
                p.setColor(0xFFFFF176);
                c.drawCircle(sx, sy, w * 0.1f, p);
                return true;
            }
            case 2: { // Atardecer con montañas
                c.drawRect(0, 0, w, h, degradado(0, 0, 0, h, 0xFFFF9A8B, 0xFFFF6A88, 0xFF8E54E9));
                p.setColor(0x88FFF59D);
                c.drawCircle(w * 0.5f, h * 0.86f, w * 0.22f, p);
                Path m = new Path();
                m.moveTo(0, h);
                m.lineTo(0, h * 0.86f);
                m.lineTo(w * 0.25f, h * 0.8f);
                m.lineTo(w * 0.5f, h * 0.88f);
                m.lineTo(w * 0.78f, h * 0.79f);
                m.lineTo(w, h * 0.86f);
                m.lineTo(w, h);
                m.close();
                p.setColor(0xCC3A1C5C);
                c.drawPath(m, p);
                return true;
            }
            case 3: { // Flores en las esquinas
                c.drawRect(0, 0, w, h, degradado(0, 0, w, h, 0xFFFFF4F7, 0xFFFDE7EF, 0xFFF3E5F5));
                int[] colores = {0xFFF48FB1, 0xFFCE93D8, 0xFFFFAB91, 0xFF90CAF9, 0xFFFFF176};
                float[][] sitios = {{0.08f, 0.06f}, {0.92f, 0.08f}, {0.06f, 0.94f}, {0.94f, 0.92f}, {0.2f, 0.98f}, {0.8f, 0.02f}};
                for (float[] s : sitios) flor(c, s[0] * w, s[1] * h, w * (0.07f + r.nextFloat() * 0.04f), colores[r.nextInt(colores.length)], r);
                return false;
            }
            case 4: { // Corazones
                c.drawRect(0, 0, w, h, degradado(0, 0, w, h, 0xFFFF9AA2, 0xFFFF5E7E, 0xFFD81B60));
                for (int i = 0; i < 22; i++) {
                    p.setColor(Color.WHITE);
                    p.setAlpha(30 + r.nextInt(70));
                    corazon(c, r.nextFloat() * w, r.nextFloat() * h, w * (0.03f + r.nextFloat() * 0.06f), p);
                }
                return true;
            }
            case 5: { // Fiesta: confeti y bombas
                c.drawRect(0, 0, w, h, degradado(0, 0, 0, h, 0xFF1E1E5A, 0xFF5A1E78));
                int[] col = {0xFFFFEB3B, 0xFFFF4081, 0xFF40C4FF, 0xFF69F0AE, 0xFFFF9100};
                for (int i = 0; i < 70; i++) {
                    p.setColor(col[r.nextInt(col.length)]);
                    c.save();
                    float x = r.nextFloat() * w, y = r.nextFloat() * h;
                    c.rotate(r.nextInt(180), x, y);
                    c.drawRect(x, y, x + w * 0.018f, y + w * 0.008f, p);
                    c.restore();
                }
                for (int i = 0; i < 3; i++) {
                    float x = w * (0.1f + i * 0.08f), y = h * (0.1f + (i % 2) * 0.05f);
                    p.setColor(col[i]);
                    c.drawOval(new RectF(x - w * 0.045f, y - w * 0.06f, x + w * 0.045f, y + w * 0.06f), p);
                    p.setColor(0x88FFFFFF);
                    p.setStrokeWidth(w * 0.003f);
                    c.drawLine(x, y + w * 0.06f, x + w * 0.02f, y + w * 0.2f, p);
                }
                return true;
            }
            case 6: { // Elegante con marco dorado
                c.drawColor(0xFFFFF8E7);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFFC9A227);
                p.setStrokeWidth(w * 0.008f);
                c.drawRect(w * 0.04f, w * 0.04f, w * 0.96f, h - w * 0.04f, p);
                p.setStrokeWidth(w * 0.003f);
                c.drawRect(w * 0.06f, w * 0.06f, w * 0.94f, h - w * 0.06f, p);
                p.setStyle(Paint.Style.FILL);
                float[][] es = {{0.06f, 0.06f}, {0.94f, 0.06f}, {0.06f, -0.06f}, {0.94f, -0.06f}};
                for (float[] e : es) {
                    float x = e[0] * w, y = e[1] > 0 ? e[1] * w : h + e[1] * w;
                    c.drawCircle(x, y, w * 0.018f, p);
                }
                return false;
            }
            case 7: { // Luces borrosas
                c.drawRect(0, 0, w, h, degradado(0, 0, w, h, 0xFF0F2027, 0xFF203A43, 0xFF2C5364));
                int[] col = {0xFFFFD54F, 0xFFFF8A65, 0xFF4DD0E1, 0xFFBA68C8, 0xFFFFFFFF};
                for (int i = 0; i < 26; i++) {
                    Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
                    b.setColor(col[r.nextInt(col.length)]);
                    b.setAlpha(40 + r.nextInt(70));
                    float rad = w * (0.03f + r.nextFloat() * 0.08f);
                    b.setMaskFilter(new BlurMaskFilter(rad * 0.4f, BlurMaskFilter.Blur.NORMAL));
                    c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, rad, b);
                }
                return true;
            }
            default: { // Cielo con nubes y rayos de luz
                c.drawRect(0, 0, w, h, degradado(0, 0, 0, h, 0xFF4FA3F7, 0xFF89C8FF, 0xFFE3F2FD));
                Paint rayo = new Paint(Paint.ANTI_ALIAS_FLAG);
                rayo.setColor(0x22FFFFFF);
                for (int i = 0; i < 6; i++) {
                    Path ry = new Path();
                    ry.moveTo(w * 0.5f, -h * 0.05f);
                    ry.lineTo(w * (i * 0.2f - 0.1f), h);
                    ry.lineTo(w * (i * 0.2f + 0.02f), h);
                    ry.close();
                    c.drawPath(ry, rayo);
                }
                p.setColor(0xEEFFFFFF);
                nube(c, w * 0.15f, h * 0.88f, w * 0.12f, p);
                nube(c, w * 0.75f, h * 0.93f, w * 0.15f, p);
                nube(c, w * 0.85f, h * 0.06f, w * 0.08f, p);
                return true;
            }
        }
    }

    static void flor(Canvas c, float x, float y, float r, int color, Random rnd) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF81C784);
        c.save();
        c.rotate(rnd.nextInt(360), x, y);
        c.drawOval(new RectF(x + r * 0.6f, y - r * 0.25f, x + r * 1.9f, y + r * 0.25f), p);
        c.drawOval(new RectF(x - r * 1.9f, y - r * 0.25f, x - r * 0.6f, y + r * 0.25f), p);
        p.setColor(color);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            c.drawCircle(x + (float) Math.cos(a) * r * 0.6f, y + (float) Math.sin(a) * r * 0.6f, r * 0.5f, p);
        }
        p.setColor(0xFFFFD54F);
        c.drawCircle(x, y, r * 0.35f, p);
        c.restore();
    }

    static void corazon(Canvas c, float x, float y, float s, Paint p) {
        Path h = new Path();
        h.moveTo(x, y + s * 0.9f);
        h.cubicTo(x - s * 1.6f, y - s * 0.2f, x - s * 0.6f, y - s * 1.2f, x, y - s * 0.35f);
        h.cubicTo(x + s * 0.6f, y - s * 1.2f, x + s * 1.6f, y - s * 0.2f, x, y + s * 0.9f);
        c.drawPath(h, p);
    }

    static void nube(Canvas c, float x, float y, float s, Paint p) {
        c.drawCircle(x, y, s, p);
        c.drawCircle(x - s * 0.9f, y + s * 0.25f, s * 0.7f, p);
        c.drawCircle(x + s * 0.9f, y + s * 0.2f, s * 0.75f, p);
    }

    // ---------------- Memes: su foto con texto arriba y abajo ----------------

    static final String[][] IDEAS = {
        {"Yo a las 6 de la mañana", "Sin tinto no funciono"},
        {"Cuando es lunes", "y el fin de semana no alcanzó"},
        {"El cliente: ¿me hace una rebajita?", "Yo:"},
        {"Cuando por fin llega la quincena", "y ya está toda gastada"},
        {"Yo diciendo que voy a dormir temprano", "También yo a la 1 de la mañana:"},
        {"Cuando el GPS dice 5 minutos", "y van 40"},
    };

    private void pintarMemes() {
        cuerpo.removeAllViews();
        int p = dp(10);
        cuerpo.setPadding(p, p, p, p);
        Button foto = boton("🖼  Escoger foto de la galería", 0xFF1565C0);
        foto.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
            try { startActivityForResult(Intent.createChooser(i, "Escoja la foto"), FOTO); } catch (Exception e) { aviso("No hay galería"); }
        });
        cuerpo.addView(foto, ancho(54));
        arriba = campoTexto("Texto de arriba", "");
        abajo = campoTexto("Texto de abajo", "");
        cuerpo.addView(arriba);
        cuerpo.addView(abajo);
        TextView ti = new TextView(this);
        ti.setText("Ideas (toque una):");
        ti.setTextColor(0xFF455A64);
        cuerpo.addView(ti);
        LinearLayout ideas = horizontal();
        for (String[] d : IDEAS) ideas.addView(chip(d[0], false, () -> { arriba.setText(d[0]); abajo.setText(d[1]); dibujarMeme(); }));
        cuerpo.addView(fila(ideas));
        Button ver = boton("👁  Ver cómo queda", 0xFF6A1B9A);
        ver.setOnClickListener(v -> dibujarMeme());
        cuerpo.addView(ver, ancho(50));
        vistaMeme = new ImageView(this);
        vistaMeme.setAdjustViewBounds(true);
        cuerpo.addView(vistaMeme, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button wa = boton("💬  Mandar por WhatsApp", 0xFF2E7D32);
        wa.setOnClickListener(v -> {
            Bitmap m = meme();
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                m.compress(Bitmap.CompressFormat.JPEG, 92, bo);
                enviar(WHATSAPP, "image/jpeg", Util.guardarBytes(this, "Meme_" + Util.fecha() + ".jpg", "image/jpeg", bo.toByteArray()));
            } catch (Exception e) { aviso("No se pudo guardar el meme"); }
        });
        cuerpo.addView(wa, ancho(56));
        dibujarMeme();
    }

    private Bitmap cargarFoto(Uri u) throws Exception {
        android.graphics.ImageDecoder.Source s = android.graphics.ImageDecoder.createSource(getContentResolver(), u);
        return android.graphics.ImageDecoder.decodeBitmap(s, (dec, info, src) -> {
            int w = info.getSize().getWidth(), hh = info.getSize().getHeight();
            if (w > 1080) dec.setTargetSize(1080, Math.max(1, hh * 1080 / w));
            dec.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }

    private void dibujarMeme() { if (vistaMeme != null) vistaMeme.setImageBitmap(meme()); }

    private Bitmap meme() {
        Bitmap bm;
        if (fotoMeme != null) bm = fotoMeme.copy(Bitmap.Config.ARGB_8888, true);
        else {
            bm = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888);
            new Canvas(bm).drawPaint(degradado(0, 0, 1080, 1080, 0xFF8E24AA, 0xFF3949AB));
        }
        Canvas c = new Canvas(bm);
        int w = bm.getWidth(), h = bm.getHeight();
        String t1 = arriba == null ? "" : arriba.getText().toString().trim().toUpperCase(new Locale("es"));
        String t2 = abajo == null ? "" : abajo.getText().toString().trim().toUpperCase(new Locale("es"));
        if (t1.isEmpty() && t2.isEmpty() && fotoMeme == null) t1 = "ESCOJA UNA FOTO Y ESCRIBA";
        if (!t1.isEmpty()) textoMeme(c, t1, w, h, true);
        if (!t2.isEmpty()) textoMeme(c, t2, w, h, false);
        return bm;
    }

    /** Letra blanca gruesa con borde negro, como los memes de siempre. */
    static void textoMeme(Canvas c, String s, int w, int h, boolean arriba) {
        int anchoTxt = (int) (w * 0.94f);
        float tam = w * 0.095f;
        TextPaint borde = new TextPaint(Paint.ANTI_ALIAS_FLAG), relleno = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Typeface tf = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        borde.setTypeface(tf);
        relleno.setTypeface(tf);
        borde.setStyle(Paint.Style.STROKE);
        borde.setStrokeJoin(Paint.Join.ROUND);
        borde.setColor(Color.BLACK);
        relleno.setColor(Color.WHITE);
        StaticLayout lb, lr;
        do {
            borde.setTextSize(tam);
            borde.setStrokeWidth(tam * 0.14f);
            relleno.setTextSize(tam);
            lb = StaticLayout.Builder.obtain(s, 0, s.length(), borde, anchoTxt).setAlignment(Layout.Alignment.ALIGN_CENTER).build();
            tam *= 0.92f;
        } while (lb.getHeight() > h * 0.3f && tam > w * 0.03f);
        lr = StaticLayout.Builder.obtain(s, 0, s.length(), relleno, anchoTxt).setAlignment(Layout.Alignment.ALIGN_CENTER).build();
        float y = arriba ? h * 0.025f : h - lb.getHeight() - h * 0.025f;
        c.save();
        c.translate((w - anchoTxt) / 2f, y);
        lb.draw(c);
        lr.draw(c);
        c.restore();
    }
}
