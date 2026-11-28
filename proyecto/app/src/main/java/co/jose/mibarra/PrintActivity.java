package co.jose.mibarra;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.util.Base64;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.nio.file.Files;

/** Muestra lo que se va a imprimir y abre la ventana de impresión (también permite "Guardar como PDF"). */
public class PrintActivity extends Activity {
    private WebView wv;
    private boolean enviado;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        wv = new WebView(this);
        setContentView(wv);
        String titulo = getIntent().getStringExtra("titulo");
        final String nombre = titulo != null ? titulo : "Mi Barra";
        String html = getIntent().getStringExtra("html");
        String img = getIntent().getStringExtra("imagen");
        if (img != null) {
            try {
                byte[] d = Files.readAllBytes(new File(img).toPath());
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(d, 0, d.length, o);
                float relacion = o.outWidth > 0 ? (float) o.outHeight / o.outWidth : 1f;
                int ancho = (int) Math.min(100, 129f / relacion);
                html = "<html><body style='margin:0;text-align:center'><img style='width:" + ancho
                        + "%' src='data:image/png;base64," + Base64.encodeToString(d, Base64.NO_WRAP)
                        + "'></body></html>";
            } catch (Exception e) {
                html = "<p>No se pudo leer la imagen</p>";
            }
        }
        // Versión 18: varias hojas (Imprimir bajando), una por página carta
        String[] paginas = getIntent().getStringArrayExtra("paginas");
        if (paginas != null) {
            StringBuilder sb = new StringBuilder("<html><body style='margin:0'>");
            for (int i = 0; i < paginas.length; i++) {
                try {
                    byte[] d = Files.readAllBytes(new File(paginas[i]).toPath());
                    sb.append("<div style='").append(i < paginas.length - 1 ? "page-break-after:always;" : "")
                      .append("text-align:center'><img style='width:100%' src='data:image/jpeg;base64,")
                      .append(Base64.encodeToString(d, Base64.NO_WRAP)).append("'></div>");
                } catch (Exception ignored) {}
            }
            html = sb.append("</body></html>").toString();
        }
        if (html == null) html = "<p>Nada para imprimir</p>";
        wv.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                if (enviado) return;
                enviado = true;
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                pm.print(nombre, v.createPrintDocumentAdapter(nombre), new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)   // siempre tamaño carta
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build());
            }
        });
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    @Override
    protected void onPostCreate(android.os.Bundle b) {
        super.onPostCreate(b);
        Util.ponerX(this);   // v44: ✕ para cerrar, arriba a la derecha
    }
}
