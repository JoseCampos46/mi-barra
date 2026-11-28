package co.jose.mibarra;

import android.Manifest;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Versión 25: 🚫 Bloquear WhatsApp (tipo "wa") y 📵 Bloquear llamadas (tipo "llamadas"). Quedan así hasta que usted los quite. */
public class BloqueosActivity extends Base {

    private static final int VPN = 81, ROL = 82;
    private String tipo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tipo = getIntent().getStringExtra("tipo");
        if (tipo == null) tipo = "wa";
        // Versión 35: desde el botón flotante llega «elegir»: se pregunta qué bloquear
        if ("elegir".equals(tipo)) {
            tipo = "wa";
            new android.app.AlertDialog.Builder(this).setTitle("🚫 Bloquear")
                    .setItems(new String[]{"🚫  Bloquear WhatsApp", "📵  Bloquear llamadas"}, (d, w) -> { tipo = w == 0 ? "wa" : "llamadas"; pintar(); })
                    .setOnCancelListener(d -> finish()).show();
        }
        pintar();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i.getStringExtra("tipo") != null && !"elegir".equals(i.getStringExtra("tipo"))) tipo = i.getStringExtra("tipo");
        pintar();
    }

    @Override protected void onResume() { super.onResume(); pintar(); }

    private GradientDrawable caja(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(16));
        return g;
    }

    private TextView letra(String s, float tam, int color, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(tam);
        t.setTextColor(color);
        if (negrita) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout tarjeta() {
        LinearLayout c = vertical();
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(caja(Color.WHITE));
        return c;
    }

    private void pintar() {
        LinearLayout l = vertical();
        l.setBackgroundColor(0xFFF6F1EA);
        l.setPadding(dp(14), dp(12), dp(14), dp(20));
        if ("wa".equals(tipo)) pintarWa(l); else pintarLlamadas(l);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF6F1EA);
        sv.addView(l);
        setContentView(sv);
    }

    // ---------------- WhatsApp ----------------

    private void pintarWa(LinearLayout l) {
        boolean on = Bloqueos.activo(this, "wa");
        LinearLayout c = tarjeta();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(letra("💬", 60, Color.BLACK, false));
        TextView est = letra(on ? "BLOQUEADO" : "LIBRE", 36, on ? 0xFFC62828 : 0xFF2E7D32, true);
        c.addView(est);
        TextView d = letra(on ? "WhatsApp no tiene internet.\nNo le llegan mensajes, llamadas ni grupos."
                : "WhatsApp funciona normal.", 15, 0xFF455A64, false);
        d.setGravity(Gravity.CENTER);
        c.addView(d);
        l.addView(c);
        Button b = boton(on ? "✅  Desbloquear WhatsApp" : "🚫  Bloquear WhatsApp", on ? 0xFF2E7D32 : 0xFFC62828);
        b.setTextSize(20);
        b.setOnClickListener(v -> { if (on) pararWa(); else prenderWa(); });
        l.addView(b, ancho(68));
        LinearLayout n = tarjeta();
        n.addView(letra("✔ Datos, Wi‑Fi y llamadas normales siguen funcionando.\n✔ Queda bloqueado hasta que usted lo desbloquee.\n"
                + "✔ Al desbloquear le llega todo lo que le mandaron.", 14, 0xFF37474F, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        l.addView(n, lp);
        TextView p = letra("La primera vez el celular pregunta por una «conexión VPN»: acéptela. Es de Mi Barra, vive dentro de su "
                + "celular y solo le quita el internet a WhatsApp. No manda nada a ninguna parte.", 13, 0xFF6D4C41, false);
        p.setPadding(dp(4), dp(12), dp(4), 0);
        l.addView(p);
    }

    private void prenderWa() {
        Intent prep = VpnService.prepare(this);
        if (prep != null) { startActivityForResult(prep, VPN); return; }
        startService(new Intent(this, BloqueoVpn.class));
        aviso("🚫 WhatsApp bloqueado");
        getWindow().getDecorView().postDelayed(this::pintar, 600);
    }

    private void pararWa() {
        startService(new Intent(this, BloqueoVpn.class).setAction(BloqueoVpn.PARAR));
        aviso("✅ WhatsApp desbloqueado");
        getWindow().getDecorView().postDelayed(this::pintar, 600);
    }

    // ---------------- Llamadas ----------------

    private static final String[] MODOS = {"🔴  Todas las llamadas", "🟠  Solo números que no tengo guardados", "🟢  Todas, menos mis favoritos ⭐"};

    private void pintarLlamadas(LinearLayout l) {
        boolean on = Bloqueos.activo(this, "llamadas");
        LinearLayout c = tarjeta();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(letra("📵", 56, Color.BLACK, false));
        c.addView(letra(on ? "BLOQUEADAS" : "ENTRAN NORMAL", 30, on ? 0xFFC62828 : 0xFF2E7D32, true));
        TextView d = letra(on ? "Se rechazan solas, sin sonar." : "Las llamadas le entran como siempre.", 15, 0xFF455A64, false);
        c.addView(d);
        l.addView(c);
        Button b = boton(on ? "✅  Dejar entrar las llamadas" : "📵  Bloquear llamadas", on ? 0xFF2E7D32 : 0xFFC62828);
        b.setTextSize(20);
        b.setOnClickListener(v -> { if (on) pararLlamadas(); else prenderLlamadas(); });
        l.addView(b, ancho(68));
        // Versión 26: para apagarlo TODO (a los demás les sale "fuera de cobertura")
        Button av = boton("✈️  Apagar todo: modo avión", 0xFF37474F);
        av.setOnClickListener(v -> {
            aviso("Prenda «Modo avión». Para volver, apáguelo en el mismo sitio.");
            try { startActivity(new Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)); }
        });
        l.addView(av, ancho(54));

        LinearLayout m = tarjeta();
        m.addView(letra("¿Cuáles bloquear? (una a la vez)", 16, 0xFF263238, true));
        int modo = Bloqueos.modo(this);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView t = letra((i == modo ? "◉  " : "○  ") + MODOS[i], 16, 0xFF263238, i == modo);
            t.setPadding(0, dp(10), 0, dp(10));
            t.setOnClickListener(v -> {
                Bloqueos.poner(this, "modo", String.valueOf(k));
                if (k == 1 && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 3);
                pintar();
            });
            m.addView(t);
        }
        List<String> fav = Bloqueos.favoritos(this);
        Button bf = boton("⭐  Escoger mis favoritos (" + fav.size() + ")", 0xFFF9A825);
        bf.setOnClickListener(v -> escogerFavoritos());
        m.addView(bf, ancho(52));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        l.addView(m, lp);

        LinearLayout h = tarjeta();
        h.addView(letra("📋 Llamadas que no entraron hoy", 16, 0xFF263238, true));
        List<String[]> hoy = bloqueadasHoy();
        if (hoy.isEmpty()) h.addView(letra("Ninguna todavía.", 14, 0xFF78909C, false));
        SimpleDateFormat hf = new SimpleDateFormat("h:mm a", new Locale("es", "CO"));
        for (String[] x : hoy) {
            TextView t = letra(hf.format(new Date(Long.parseLong(x[0]))) + " · " + (x.length > 2 && !x[2].isEmpty() ? x[2] : x[1]) + "   📞", 15, 0xFF1565C0, false);
            t.setPadding(0, dp(8), 0, dp(8));
            t.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + x[1]))); } catch (Exception ignored) {} });
            h.addView(t);
        }
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, -2);
        lp2.topMargin = dp(10);
        l.addView(h, lp2);
        TextView p = letra("Quedan bloqueadas hasta que usted las deje entrar. La primera vez el celular pregunta si Mi Barra puede ser "
                + "su «app de filtro de llamadas»: acepte. Quien llama oye ocupado o le sale el buzón.", 13, 0xFF6D4C41, false);
        p.setPadding(dp(4), dp(12), dp(4), 0);
        l.addView(p);
    }

    private List<String[]> bloqueadasHoy() {
        List<String[]> r = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0);
        long desde = c.getTimeInMillis();
        try (BufferedReader rd = new BufferedReader(new FileReader(new File(getFilesDir(), "llamadas_bloqueadas.txt"), StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = rd.readLine()) != null) {
                String[] p = ln.split("\t", -1);
                try { if (p.length >= 2 && Long.parseLong(p[0]) >= desde) r.add(0, p); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return r;
    }

    private void prenderLlamadas() {
        RoleManager rm = getSystemService(RoleManager.class);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), ROL);
            return;
        }
        Bloqueos.poner(this, "llamadas", "1");
        Bloqueos.avisoFijo(this, Bloqueos.NOTI_LL, "📵 Llamadas bloqueadas", "Toque aquí para dejarlas entrar", "llamadas");
        aviso("📵 Llamadas bloqueadas");
        pintar();
    }

    private void pararLlamadas() {
        Bloqueos.poner(this, "llamadas", null);
        Bloqueos.quitarAviso(this, Bloqueos.NOTI_LL);
        aviso("✅ Las llamadas vuelven a entrar");
        pintar();
    }

    /** Lista de contactos para marcar los favoritos, y un número escrito a mano si no está guardado. */
    private void escogerFavoritos() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 4);
            return;
        }
        List<String> nombres = new ArrayList<>(), numeros = new ArrayList<>();
        try (Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE")) {
            List<String> vistos = new ArrayList<>();
            while (c != null && c.moveToNext()) {
                String n = c.getString(0), num = c.getString(1);
                String k = Bloqueos.limpio(num);
                if (k.isEmpty() || vistos.contains(k)) continue;
                vistos.add(k);
                nombres.add(n + "  ·  " + num);
                numeros.add(num);
            }
        } catch (Exception ignored) {}
        List<String> fav = Bloqueos.favoritos(this);
        // los escritos a mano también salen en la lista
        for (String f : fav) {
            boolean esta = false;
            for (String n : numeros) if (Bloqueos.limpio(n).equals(Bloqueos.limpio(f))) esta = true;
            if (!esta) { nombres.add(0, "✍ " + f); numeros.add(0, f); }
        }
        boolean[] marcados = new boolean[numeros.size()];
        for (int i = 0; i < numeros.size(); i++)
            for (String f : fav) if (Bloqueos.limpio(f).equals(Bloqueos.limpio(numeros.get(i)))) marcados[i] = true;
        new AlertDialog.Builder(this).setTitle("⭐ Mis favoritos (estos sí entran)")
                .setMultiChoiceItems(nombres.toArray(new String[0]), marcados, (d, w, ok) -> marcados[w] = ok)
                .setPositiveButton("Guardar", (d, w) -> {
                    List<String> nuevo = new ArrayList<>();
                    for (int i = 0; i < marcados.length; i++) if (marcados[i]) nuevo.add(numeros.get(i));
                    Bloqueos.poner(this, "favoritos", String.join("|", nuevo));
                    pintar();
                })
                .setNeutralButton("✍ Escribir número", (d, w) -> escribirNumero())
                .setNegativeButton("Cancelar", null).show();
    }

    private void escribirNumero() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_PHONE);
        e.setHint("Ej: 315 222 1100");
        new AlertDialog.Builder(this).setTitle("Número favorito").setView(e).setPositiveButton("Agregar", (d, w) -> {
            String n = e.getText().toString().trim();
            if (Bloqueos.limpio(n).isEmpty()) return;
            List<String> fav = Bloqueos.favoritos(this);
            fav.add(n);
            Bloqueos.poner(this, "favoritos", String.join("|", fav));
            pintar();
        }).setNegativeButton("Cancelar", null).show();
    }

    @Override
    protected void onActivityResult(int r, int res, Intent d) {
        super.onActivityResult(r, res, d);
        if (r == VPN && res == RESULT_OK) prenderWa();
        else if (r == VPN) aviso("Sin el permiso de VPN no se puede bloquear WhatsApp");
        else if (r == ROL) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) prenderLlamadas();
            else aviso("Sin ese permiso no se pueden bloquear las llamadas");
        }
    }

    @Override
    public void onRequestPermissionsResult(int r, String[] p, int[] res) {
        super.onRequestPermissionsResult(r, p, res);
        if (r == 4 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) escogerFavoritos();
    }
}
