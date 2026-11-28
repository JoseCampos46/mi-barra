package co.jose.mibarra;

import java.util.ArrayList;
import java.util.List;

/**
 * Versión 17 · 📺 TV gratis por internet: solo las transmisiones oficiales y gratuitas de cada canal.
 * "Buscar señales" prueba cuáles están contestando ahora. Usted puede agregar otros con su enlace.
 */
public class TvActivity extends CanalesBase {

    static final String[][] CANALES = {
            {"Señal Colombia", "https://www.rtvcplay.co/en-vivo/senal-colombia"},
            {"Canal Institucional", "https://www.rtvcplay.co/en-vivo/canal-institucional"},
            {"Telepacífico", "https://www.telepacifico.com/en-vivo"},
            {"Canal Capital", "https://www.canalcapital.gov.co/envivo"},
            {"Teleantioquia", "https://www.teleantioquia.co/en-vivo/"},
            {"Canal Trece", "https://canaltrece.com.co/en-vivo/"},
            {"Caracol TV en vivo", "https://www.caracoltv.com/senal-vivo"},
            {"Noticias Caracol en vivo", "https://www.noticiascaracol.com/en-vivo"},
            {"Canal RCN en vivo", "https://www.canalrcn.com/senal-en-vivo"},
            {"Noticias RCN en vivo", "https://www.noticiasrcn.com/en-vivo"},
            {"Canal 1", "https://canal1.com.co/senal-en-vivo/"},
            {"RTVC Noticias", "https://www.rtvcplay.co/en-vivo/rtvc-noticias"},
    };

    @Override boolean esTv() { return true; }
    @Override String tituloPantalla() { return "📺 TV gratis"; }

    @Override
    List<String[]> catalogo() {
        List<String[]> r = new ArrayList<>();
        for (String[] c : CANALES) r.add(new String[]{c[0], c[1]});
        return r;
    }

    @Override
    void tocar(String nombre, String url) { abrirEnlace(url); }
}
