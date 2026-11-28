package co.jose.mibarra;

import java.util.Calendar;

/** Una frase motivadora distinta cada día del año. */
public final class Frases {
    private Frases() {}

    static final String[] LISTA = {
        "No te detengas: los milagros se hacen cuando uno los desea y trabaja por ellos.",
        "Hoy es un buen día para empezar lo que tanto has soñado.",
        "Paso a paso se llega lejos. Lo importante es no parar.",
        "Lo que hoy siembras con esfuerzo, mañana lo cosechas con alegría.",
        "Dios pone las fuerzas; tú pon las ganas.",
        "La edad no detiene los sueños: los sueños detienen la edad.",
        "Cada día que amanece es una nueva oportunidad.",
        "El que persevera, alcanza.",
        "Las manos que trabajan construyen el futuro.",
        "No cuentes los días: haz que los días cuenten.",
        "Siempre que llueve, escampa. Ánimo.",
        "Tu mejor herramienta es tu actitud.",
        "Aprender algo nuevo cada día mantiene joven el corazón.",
        "Lo difícil se hace ahora; lo imposible, un poquito después.",
        "Con fe, trabajo y paciencia, todo llega.",
        "Hoy es el día más joven que te queda: aprovéchalo.",
        "Una casa se levanta ladrillo a ladrillo; los sueños también.",
        "La constancia vale más que la prisa.",
        "Agradece lo que tienes mientras trabajas por lo que quieres.",
        "El éxito es la suma de pequeños esfuerzos repetidos cada día.",
        "Donde hay voluntad, hay camino.",
        "Sonríe: tu familia necesita verte feliz.",
        "No esperes el momento perfecto: toma el momento y hazlo perfecto.",
        "Lo que haces con amor siempre sale bien.",
        "Levántate con ganas: el mundo necesita lo que sabes hacer.",
        "Un paso hacia adelante, aunque sea pequeño, sigue siendo avanzar.",
        "Grano a grano llena la gallina el buche.",
        "La tecnología es tuya: úsala para ganar tiempo y vivir mejor.",
        "El tiempo bien usado es la mayor riqueza.",
        "Haz hoy lo que otros no quieren, y mañana tendrás lo que otros no tienen.",
        "Cree en ti: ya llegaste muy lejos.",
        "La mejor forma de predecir el futuro es construirlo.",
        "Cada problema trae escondida una solución.",
        "Al que madruga, Dios lo ayuda.",
        "Hoy puedes ser un poco mejor que ayer.",
        "No hay viento favorable para quien no sabe a dónde va: ponte una meta.",
        "Tu experiencia es un tesoro: compártela.",
        "Descansa si te cansas, pero no te rindas.",
        "La alegría es el mejor motor para el trabajo.",
        "Lo que se empieza con fe se termina con gloria.",
        "Un buen día empieza con un buen pensamiento.",
        "Las grandes obras se hacen con paciencia, no con fuerza.",
        "Mientras haya vida, hay esperanza.",
        "Sé la razón por la que alguien sonría hoy.",
        "El esfuerzo de hoy es el orgullo de mañana.",
        "No compitas con nadie: compite con el que fuiste ayer.",
        "Todo lo que necesitas para empezar ya está en tus manos.",
        "La paciencia es amarga, pero su fruto es dulce.",
        "Hoy no es un día más: es un día menos para lograr tu sueño.",
        "Si puedes soñarlo, puedes hacerlo.",
        "Trabaja en silencio y deja que el éxito haga el ruido.",
        "El amor por lo que haces se nota en cada detalle.",
        "Después de la tormenta, siempre sale el sol.",
        "Nunca es tarde para aprender, crear y crecer.",
        "La vida es como andar en moto: para mantener el equilibrio hay que seguir avanzando.",
        "Disfruta el camino: la meta llega sola.",
        "Lo bueno toma tiempo; lo excelente, un poco más.",
        "Hoy, haz algo que tu yo del futuro te agradezca.",
        "Bendecido el que trabaja con alegría.",
        "Tu fuerza es más grande que cualquier obstáculo.",
    };

    static String deHoy() {
        return LISTA[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % LISTA.length];
    }
}
