package co.jose.mibarra;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Versión 33 · 📲 Pasar archivos por Bluetooth: audios, documentos, fotos, videos o todos.
 * Se escogen (uno o varios) y se mandan a cualquier celular con Bluetooth; el celular pregunta a cuál.
 */
public class BluetoothActivity extends Base {

    private static final int ESCOGER = 51;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = vertical();
        int p = dp(14);
        l.setPadding(p, p, p, p);
        l.addView(titulo("📲 Pasar por Bluetooth"));
        TextView t = new TextView(this);
        t.setText("Escoja qué quiere mandar. Puede marcar varios archivos (deje el dedo en uno y toque los demás). "
                + "Después el celular le muestra los equipos con Bluetooth: escoja el Motorola o el que sea.");
        t.setTextSize(16);
        t.setPadding(0, dp(4), 0, dp(10));
        l.addView(t);
        enviar(l, "🗂  Todos los archivos", 0xFF3949AB, new String[]{"*/*"});
        enviar(l, "🎵  Audios", 0xFF6A1B9A, new String[]{"audio/*"});
        enviar(l, "📄  Documentos (PDF, Word, Excel)", 0xFF1565C0, new String[]{"application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/*"});
        enviar(l, "🖼  Fotos", 0xFF00897B, new String[]{"image/*"});
        enviar(l, "🎬  Videos", 0xFFEF6C00, new String[]{"video/*"});

        TextView r = new TextView(this);
        r.setText("\n📥 Para recibir: prenda el Bluetooth aquí y mande desde el otro celular. Le llega un aviso para aceptar; "
                + "lo recibido queda en Descargas (o en la carpeta Bluetooth).");
        r.setTextSize(15);
        l.addView(r);
        Button rec = boton("📥  Recibir: prender el Bluetooth", 0xFF43A047);
        rec.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
            catch (Exception e) { aviso("No pude abrir el Bluetooth"); }
        });
        l.addView(rec, ancho(58));
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private void enviar(LinearLayout l, String texto, int color, String[] tipos) {
        Button bt = boton(texto, color);
        bt.setTextSize(18);
        bt.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(tipos.length == 1 ? tipos[0] : "*/*")
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            if (tipos.length > 1) i.putExtra(Intent.EXTRA_MIME_TYPES, tipos);
            try {   // que arranque en Descargas
                i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                        Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"));
            } catch (Exception ignored) {}
            try { startActivityForResult(i, ESCOGER); }
            catch (ActivityNotFoundException e) { aviso("No pude abrir los archivos"); }
        });
        l.addView(bt, ancho(62));
    }

    @Override
    protected void onActivityResult(int pedido, int res, Intent d) {
        super.onActivityResult(pedido, res, d);
        if (pedido != ESCOGER || res != RESULT_OK || d == null) return;
        ArrayList<Uri> us = new ArrayList<>();
        ClipData cd = d.getClipData();
        if (cd != null) for (int k = 0; k < cd.getItemCount(); k++) us.add(cd.getItemAt(k).getUri());
        else if (d.getData() != null) us.add(d.getData());
        if (us.isEmpty()) return;
        Intent s;
        if (us.size() == 1) s = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, us.get(0));
        else s = new Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, us);
        String tipo = getContentResolver().getType(us.get(0));
        s.setType(us.size() == 1 && tipo != null ? tipo : "*/*");
        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        aviso("📲 Escoja el celular al que lo manda (" + us.size() + (us.size() == 1 ? " archivo)" : " archivos)"));
        try { startActivity(new Intent(s).setPackage("com.android.bluetooth")); }
        catch (Exception e) {
            try { startActivity(Intent.createChooser(s, "Enviar por Bluetooth")); }
            catch (Exception e2) { aviso("Este celular no dejó enviar por Bluetooth"); }
        }
    }
}
