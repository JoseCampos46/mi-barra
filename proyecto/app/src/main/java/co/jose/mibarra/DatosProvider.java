package co.jose.mibarra;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** Versión 46: ChpInicio lee de aquí lo que vive en ChpBar (recordatorios, sus ajustes del inicio)
 *  y pregunta si la grabadora está grabando. Solo lo pueden usar apps firmadas igual que ChpBar. */
public class DatosProvider extends ContentProvider {

    @Override public boolean onCreate() { return true; }

    private File archivo(String nombre) {
        if (getContext() == null || nombre == null) return null;
        File datos = getContext().getFilesDir();
        switch (nombre) {
            case "recordatorios.tsv": return new File(datos, "recordatorios.tsv");
            case "fondo.jpg": return new File(datos, "fondo.jpg");
            case "inicio.xml": return new File(new File(getContext().getApplicationInfo().dataDir, "shared_prefs"), "inicio.xml");
            default: return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = archivo(uri.getLastPathSegment());
        if (f == null || !f.exists()) throw new FileNotFoundException("no hay");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle b = new Bundle();
        if ("grabando".equals(method)) b.putBoolean("si", GrabadoraService.grabando);
        else if ("oyendo".equals(method)) b.putBoolean("si", PuertaActivity.oyendo());
        return b;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
