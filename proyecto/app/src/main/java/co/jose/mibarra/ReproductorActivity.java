package co.jose.mibarra;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Versión 17 · 🎧 Reproductor: listas de audios (cuentos, música) que siguen sonando con la pantalla apagada. */
public class ReproductorActivity extends Base {
    private static final int CARPETA = 31, AUDIOS = 32;
    private static final String[] EXT = {".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac", ".amr", ".3gp", ".mp4"};

    private final Handler h = new Handler(Looper.getMainLooper());
    private String nombreLista = "";
    private List<String[]> pistas = new ArrayList<>();
    private LinearLayout lista;
    private TextView tvLista, tvAhora, tvTiempo;
    private SeekBar barra;
    private Button bPausa, bAzar, bRepetir;
    private boolean moviendo;

    private File carpetaListas() { File f = new File(getFilesDir(), "listas"); f.mkdirs(); return f; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { nombreLista = new String(Files.readAllBytes(new File(getFilesDir(), "lista_ultima.txt").toPath()), StandardCharsets.UTF_8).trim(); } catch (Exception ignored) {}
        if (nombreLista.isEmpty()) nombreLista = "Mis audios";
        pistas = SonidoService.leer(archivo());

        LinearLayout raiz = vertical();
        raiz.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout arriba = horizontal();
        tvLista = new TextView(this);
        tvLista.setTextSize(17);
        tvLista.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        arriba.addView(tvLista, peso(1));
        Button listas = boton("📋 Mis listas", 0xFF455A64);
        listas.setOnClickListener(v -> elegirLista());
        arriba.addView(listas, new LinearLayout.LayoutParams(dp(130), dp(46)));
        raiz.addView(arriba);

        LinearLayout agr = horizontal();
        Button c = boton("📂 Agregar carpeta", 0xFF2E7D32);
        c.setOnClickListener(v -> {
            try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), CARPETA); } catch (Exception e) { aviso("No se pudo abrir"); }
        });
        Button a = boton("🎵 Agregar audios", 0xFF2E7D32);
        a.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*")
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try { startActivityForResult(i, AUDIOS); } catch (Exception e) { aviso("No se pudo abrir"); }
        });
        agr.addView(c, peso(1));
        agr.addView(a, peso(1));
        raiz.addView(agr);

        lista = vertical();
        ScrollView sv = new ScrollView(this);
        sv.addView(lista);
        raiz.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Lo que suena
        LinearLayout panel = vertical();
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.setBackground(Comun.fondo(this, 0xFF1C2340, 16));
        tvAhora = new TextView(this);
        tvAhora.setTextColor(0xFFFFFFFF);
        tvAhora.setTextSize(15);
        tvAhora.setGravity(Gravity.CENTER);
        panel.addView(tvAhora);
        barra = new SeekBar(this);
        barra.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean u) {}
            @Override public void onStartTrackingTouch(SeekBar s) { moviendo = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                moviendo = false;
                try { startService(new Intent(ReproductorActivity.this, SonidoService.class).setAction(SonidoService.IR).putExtra("ms", s.getProgress())); } catch (Exception ignored) {}
            }
        });
        panel.addView(barra);
        tvTiempo = new TextView(this);
        tvTiempo.setTextColor(0xFFC9D0F0);
        tvTiempo.setTextSize(12);
        tvTiempo.setGravity(Gravity.CENTER);
        panel.addView(tvTiempo);
        LinearLayout ctl = horizontal();
        Button ant = boton("⏮", 0xFF2B3459), sig = boton("⏭", 0xFF2B3459);
        bPausa = boton("▶", 0xFF1565C0);
        ant.setOnClickListener(v -> SonidoService.accion(this, SonidoService.ANT));
        sig.setOnClickListener(v -> SonidoService.accion(this, SonidoService.SIG));
        bPausa.setOnClickListener(v -> {
            if (SonidoService.activo && !SonidoService.esRadio) SonidoService.accion(this, SonidoService.PAUSA);
            else if (!pistas.isEmpty()) tocarDesde(SonidoService.azar ? -1 : 0);
        });
        ctl.addView(ant, peso(1)); ctl.addView(bPausa, peso(1)); ctl.addView(sig, peso(1));
        panel.addView(ctl);
        LinearLayout ctl2 = horizontal();
        bAzar = boton("", 0xFF2B3459);
        bAzar.setOnClickListener(v -> { SonidoService.azar = !SonidoService.azar; pintarControles(); });
        bRepetir = boton("", 0xFF2B3459);
        bRepetir.setOnClickListener(v -> { SonidoService.repetir = !SonidoService.repetir; pintarControles(); });
        Button dormir = boton("⏲ Apagar", 0xFF2B3459);
        dormir.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("⏲ Apagar en")
                .setItems(new String[]{"15 minutos", "30 minutos", "1 hora", "No apagar"}, (d, w) ->
                        SonidoService.dormir(this, new int[]{15, 30, 60, 0}[w])).show());
        Button parar = boton("⏹", 0xFFC62828);
        parar.setOnClickListener(v -> SonidoService.parar(this));
        ctl2.addView(bAzar, peso(1)); ctl2.addView(bRepetir, peso(1)); ctl2.addView(dormir, peso(1)); ctl2.addView(parar, peso(0.6f));
        panel.addView(ctl2);
        raiz.addView(panel);
        setContentView(raiz);
        pintarLista();
        pintarControles();
        pedirBateria();
    }

    @Override protected void onResume() { super.onResume(); h.post(tic); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tic); }

    private final Runnable tic = new Runnable() {
        @Override public void run() {
            pintarAhora();
            h.postDelayed(this, 500);
        }
    };

    private File archivo() { return new File(carpetaListas(), Comun.nombreArchivo(nombreLista) + ".tsv"); }

    private void guardar() {
        SonidoService.escribir(archivo(), pistas);
        try { Files.write(new File(getFilesDir(), "lista_ultima.txt").toPath(), nombreLista.getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }

    private void tocarDesde(int k) {
        guardar();
        SonidoService.lista(this, pistas, k, SonidoService.azar);
    }

    private void pintarLista() {
        tvLista.setText("🎧 " + nombreLista + " · " + pistas.size() + " audios");
        lista.removeAllViews();
        if (pistas.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Esta lista está vacía. Toque 📂 Agregar carpeta (por ejemplo, su carpeta de Cuentos) o 🎵 Agregar audios.");
            t.setTextSize(15);
            t.setPadding(dp(6), dp(12), dp(6), dp(12));
            lista.addView(t);
        }
        for (int i = 0; i < pistas.size(); i++) {
            final int k = i;
            Button bt = boton((i + 1) + ".  " + pistas.get(i)[0], 0xFF37474F);
            bt.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            bt.setTextSize(14);
            bt.setOnClickListener(v -> tocarDesde(k));
            bt.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle(pistas.get(k)[0])
                        .setItems(new String[]{"⬆ Subir", "⬇ Bajar", "🗑 Quitar de la lista"}, (d, w) -> {
                            if (w == 0 && k > 0) pistas.add(k - 1, pistas.remove(k));
                            else if (w == 1 && k < pistas.size() - 1) pistas.add(k + 1, pistas.remove(k));
                            else if (w == 2) pistas.remove(k);
                            guardar();
                            pintarLista();
                        }).show();
                return true;
            });
            lista.addView(bt, ancho(50));
        }
    }

    private void pintarControles() {
        bAzar.setText(SonidoService.azar ? "🔀 Azar: sí" : "🔀 En orden");
        bRepetir.setText(SonidoService.repetir ? "🔁 Repetir: sí" : "🔁 Repetir: no");
    }

    private void pintarAhora() {
        boolean suena = SonidoService.activo && !SonidoService.esRadio;
        tvAhora.setText(suena ? (SonidoService.cargando ? "⏳ " : SonidoService.pausado ? "⏸ " : "▶ ") + SonidoService.titulo : "Toque un audio para empezar");
        bPausa.setText(suena && !SonidoService.pausado ? "⏸" : "▶");
        try {
            if (suena && SonidoService.mp != null && !SonidoService.cargando) {
                int d = SonidoService.mp.getDuration(), p = SonidoService.mp.getCurrentPosition();
                if (d > 0) {
                    barra.setMax(d);
                    if (!moviendo) barra.setProgress(p);
                    tvTiempo.setText(tiempo(p) + " / " + tiempo(d) + (SonidoService.apagarA > 0 ? "   ⏲ se apaga en "
                            + Math.max(1, (SonidoService.apagarA - System.currentTimeMillis()) / 60000) + " min" : ""));
                    return;
                }
            }
        } catch (Exception ignored) {}
        barra.setProgress(0);
        tvTiempo.setText("");
    }

    private static String tiempo(int ms) {
        int s = ms / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private void elegirLista() {
        File[] fs = carpetaListas().listFiles();
        List<String> ns = new ArrayList<>();
        if (fs != null) for (File f : fs) ns.add(f.getName().replaceAll("\\.tsv$", ""));
        ns.add("➕ Lista nueva…");
        new AlertDialog.Builder(this).setTitle("📋 Mis listas").setItems(ns.toArray(new String[0]), (d, w) -> {
            if (w == ns.size() - 1) {
                EditText e = campoTexto("Ejemplo: Cuentos de la noche", "");
                new AlertDialog.Builder(this).setTitle("Nombre de la lista").setView(e).setPositiveButton("Crear", (d2, w2) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty()) return;
                    guardar();
                    nombreLista = n;
                    pistas = new ArrayList<>();
                    guardar();
                    pintarLista();
                }).setNegativeButton("Cancelar", null).show();
            } else {
                guardar();
                nombreLista = ns.get(w);
                pistas = SonidoService.leer(archivo());
                guardar();
                pintarLista();
            }
        }).show();
    }

    @Override
    protected void onActivityResult(int pedido, int res, Intent d) {
        super.onActivityResult(pedido, res, d);
        if (res != RESULT_OK || d == null) return;
        int antes = pistas.size();
        if (pedido == CARPETA && d.getData() != null) {
            Uri arbol = d.getData();
            try { getContentResolver().takePersistableUriPermission(arbol, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            leerCarpeta(arbol, DocumentsContract.getTreeDocumentId(arbol), 0);
        } else if (pedido == AUDIOS) {
            List<Uri> us = new ArrayList<>();
            if (d.getClipData() != null) for (int i = 0; i < d.getClipData().getItemCount(); i++) us.add(d.getClipData().getItemAt(i).getUri());
            else if (d.getData() != null) us.add(d.getData());
            for (Uri u : us) {
                try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                pistas.add(new String[]{nombreDe(u), u.toString()});
            }
        }
        guardar();
        pintarLista();
        aviso("🎧 " + (pistas.size() - antes) + " audios agregados");
    }

    /** Lee los audios de la carpeta (y de sus subcarpetas, un nivel). */
    private void leerCarpeta(Uri arbol, String docId, int nivel) {
        Uri hijos = DocumentsContract.buildChildDocumentsUriUsingTree(arbol, docId);
        List<String[]> nuevos = new ArrayList<>();
        try (Cursor c = getContentResolver().query(hijos, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String id = c.getString(0), n = c.getString(1), mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) { if (nivel < 1) leerCarpeta(arbol, id, nivel + 1); continue; }
                String nl = n == null ? "" : n.toLowerCase(Locale.ROOT);
                boolean audio = mime != null && mime.startsWith("audio/");
                for (String e : EXT) if (nl.endsWith(e)) audio = true;
                if (!audio) continue;
                Uri u = DocumentsContract.buildDocumentUriUsingTree(arbol, id);
                nuevos.add(new String[]{n.replaceAll("\\.[^.]+$", ""), u.toString()});
            }
        } catch (Exception ignored) {}
        nuevos.sort((x, y) -> x[0].compareToIgnoreCase(y[0]));
        pistas.addAll(nuevos);
    }

    private String nombreDe(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0).replaceAll("\\.[^.]+$", "");
        } catch (Exception ignored) {}
        return "Audio";
    }

    /** Para que el celular no corte el sonido con la pantalla apagada. */
    private void pedirBateria() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        if (getSharedPreferences("barra", MODE_PRIVATE).getBoolean("pidioBateriaAudio", false)) return;
        getSharedPreferences("barra", MODE_PRIVATE).edit().putBoolean("pidioBateriaAudio", true).apply();
        new AlertDialog.Builder(this).setTitle("🔋 Para que no se corte")
                .setMessage("Para que los audios sigan sonando con la pantalla apagada, permita a Mi Barra usar la batería sin restricciones.")
                .setPositiveButton("Permitir", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) {}
                    }
                }).setNegativeButton("Después", null).show();
    }
}
