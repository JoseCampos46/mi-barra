package co.jose.mibarra;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

/** Versión 46: ChpInicio (la pantalla de inicio) es una app aparte de ChpBar.
 *  Así, cerrar una herramienta desde Recientes ya no tumba el inicio (lo del fondo azul).
 *  Esta clase manda a ChpBar lo que ChpInicio no tiene adentro (herramientas, grabadora). */
final class Puerta {
    static final String CHPBAR = "co.jose.mibarra";
    static final String CHPINICIO = "co.jose.chpinicio";
    static final String AUTORIDAD = "co.jose.mibarra.datos";
    static final String PUERTA = "co.jose.mibarra.PuertaActivity";
    /** Versión 47: las alarmas viven en la app aparte ChpRecordatorios. */
    static final String CHPRECORD = "co.jose.chprecordatorios";
    static final String AUTORIDAD_RECORD = "co.jose.chprecordatorios.datos";

    static boolean hayChpRecordatorios(Context c) {
        try { c.getPackageManager().getPackageInfo(CHPRECORD, 0); return true; } catch (Exception e) { return false; }
    }

    /** Abre ChpRecordatorios; si no está instalada, avisa dónde bajarla. */
    static boolean abrirChpRecordatorios(Context c) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(CHPRECORD);
        if (i != null) { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
        android.widget.Toast.makeText(c, "Instale ChpRecordatorios: ahí están ahora todas las alarmas", android.widget.Toast.LENGTH_LONG).show();
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JoseCampos46/mi-barra/releases"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {}
        return false;
    }

    private Puerta() {}

    static boolean esChpInicio(Context c) { return CHPINICIO.equals(c.getPackageName()); }

    /** Si la pantalla es de ChpBar y no está dentro de ChpInicio, devuelve el intent que la abre a través de ChpBar. */
    static Intent desviar(Context c, Intent i, String tipo) {
        if (i == null || !esChpInicio(c)) return null;
        ComponentName cn = i.getComponent();
        if (cn == null || !c.getPackageName().equals(cn.getPackageName())) return null;
        PackageManager pm = c.getPackageManager();
        try {
            if ("actividad".equals(tipo)) pm.getActivityInfo(cn, 0); else pm.getServiceInfo(cn, 0);
            return null;   // está dentro de ChpInicio: se abre normal
        } catch (PackageManager.NameNotFoundException e) { /* es de ChpBar */ }
        Intent orig = new Intent(i);
        orig.setComponent(new ComponentName(CHPBAR, cn.getClassName()));
        return orden(tipo).putExtra("puerta_intent", orig);
    }

    static Intent orden(String tipo) {
        return new Intent().setComponent(new ComponentName(CHPBAR, PUERTA))
                .putExtra("puerta_tipo", tipo)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
    }

    /** Pregunta algo a ChpBar (ej. "grabando", "oyendo"). */
    static boolean pregunta(Context c, String que) {
        try {
            Bundle b = c.getContentResolver().call(Uri.parse("content://" + AUTORIDAD), que, null, null);
            return b != null && b.getBoolean("si");
        } catch (Exception e) { return false; }
    }

    /** Copia un archivo de ChpBar (ej. recordatorios.tsv) a donde lo pide ChpInicio. */
    static boolean traer(Context c, String nombre, java.io.File destino) { return traer(c, AUTORIDAD, nombre, destino); }

    static boolean traer(Context c, String autoridad, String nombre, java.io.File destino) {
        try (java.io.InputStream in = c.getContentResolver().openInputStream(Uri.parse("content://" + autoridad + "/" + nombre))) {
            if (in == null) return false;
            java.io.File tmp = new java.io.File(destino.getPath() + ".tmp");
            if (destino.getParentFile() != null) destino.getParentFile().mkdirs();
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            }
            return tmp.renameTo(destino);
        } catch (Exception e) { return false; }
    }
}
