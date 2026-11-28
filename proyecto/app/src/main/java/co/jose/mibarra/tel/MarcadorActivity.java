package co.jose.mibarra.tel;

import android.Manifest;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Versión 37: Mi Teléfono. Pestañas: Marcar, Recientes, Contactos, Grabadas (llamadas y mensajes del contestador) y ⚙ Ajustes.
 * El bloqueo de estafas está escondido en ⚙ y en el menú de cada número (Más…).
 */
public class MarcadorActivity extends TelBase {

    private static final String[] PESTANAS = {"Marcar", "Recientes", "Contactos", "Grabadas", "⚙"};
    private int pestana = 0;
    private LinearLayout raiz, contenido;
    private EditText numero;
    private TextView coincide;
    private EditText buscar;
    private LinearLayout listaContactos;
    private final List<String[]> contactos = new ArrayList<>();
    private final Runnable miCambio = this::pintar;
    // audio
    private MediaPlayer reproductor;
    private String sonando = "";
    private MediaRecorder grabSaludo;
    private TextToSpeech voz;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(FONDO);
        getWindow().setNavigationBarColor(FONDO);
        raiz = vertical();
        raiz.setBackgroundColor(FONDO);
        setContentView(raiz);

        numero = new EditText(this);
        numero.setShowSoftInputOnFocus(false);
        numero.setTextColor(BLANCO);
        numero.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        numero.setGravity(Gravity.CENTER);
        numero.setSingleLine(true);
        numero.setBackground(null);
        numero.setHint("Escriba el número");
        numero.setHintTextColor(0xFF888780);
        numero.setTextSize(32);
        numero.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) { mirarCoincidencia(); }
        });
        coincide = texto("", 17, true, VERDE_CLARO);
        coincide.setGravity(Gravity.CENTER);

        leerIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        leerIntent(i);
        pintar();
    }

    private void leerIntent(Intent i) {
        if (i == null) return;
        if (i.hasExtra("pestana")) pestana = i.getIntExtra("pestana", 0);
        if (i.getData() != null && "tel".equals(i.getData().getScheme())) {
            numero.setText(i.getData().getSchemeSpecificPart());
            numero.setSelection(numero.getText().length());
            pestana = 0;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LlamadaService.alCambiar = miCambio;
        pintar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (LlamadaService.alCambiar == miCambio) LlamadaService.alCambiar = null;
        pararAudio();
        terminarSaludo(false);
    }

    @Override
    protected void onDestroy() {
        if (voz != null) {
            try { voz.shutdown(); } catch (Exception ignored) {}
            voz = null;
        }
        super.onDestroy();
    }

    // ================= Armado general =================

    private void pintar() {
        raiz.removeAllViews();

        LinearLayout cabeza = horizontal();
        cabeza.setGravity(Gravity.CENTER_VERTICAL);
        cabeza.setPadding(dp(10), dp(6), dp(6), dp(2));
        cabeza.addView(texto("📞 Mi Teléfono", 22, true, BLANCO), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button cerrar = boton("✕", ROJO, BLANCO, 20);
        cerrar.setOnClickListener(v -> finish());
        cabeza.addView(cerrar, new LinearLayout.LayoutParams(dp(56), dp(46)));
        raiz.addView(cabeza, ancho());

        if (!esTelefonoPrincipal() || !permisosOk()) {
            TextView t = texto("⚠ Falta activar Mi Teléfono. Toque aquí.", 17, true, BLANCO);
            t.setBackground(fondo(ROJO, dp(10)));
            t.setPadding(dp(10), dp(10), dp(10), dp(10));
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> {
                pestana = 4;
                pintar();
            });
            raiz.addView(t, ancho());
        }

        LinearLayout tabs = horizontal();
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < PESTANAS.length; i++) {
            final int n = i;
            TextView t = texto(PESTANAS[i], i == 4 ? 20 : 15, true, BLANCO);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(10), 0, dp(10));
            t.setBackground(fondo(i == pestana ? AZUL_VIVO : TARJETA, dp(10)));
            t.setOnClickListener(v -> {
                pestana = n;
                pintar();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, i == 4 ? 0.6f : 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            tabs.addView(t, lp);
        }
        raiz.addView(tabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contenido = vertical();
        raiz.addView(contenido, relleno());
        switch (pestana) {
            case 0: pintarMarcar(); break;
            case 1: pintarRecientes(); break;
            case 2: pintarContactos(); break;
            case 3: pintarGrabadas(); break;
            default: pintarAjustes();
        }
    }

    private static void soltar(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    // ================= Marcar =================

    private void pintarMarcar() {
        soltar(numero);
        soltar(coincide);
        contenido.addView(numero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
        contenido.addView(coincide, ancho());
        mirarCoincidencia();

        String[][] t = {{"1", ""}, {"2", "ABC"}, {"3", "DEF"}, {"4", "GHI"}, {"5", "JKL"}, {"6", "MNO"},
                {"7", "PQRS"}, {"8", "TUV"}, {"9", "WXYZ"}, {"*", ""}, {"0", "+"}, {"#", ""}};
        LinearLayout tecl = vertical();
        tecl.setPadding(dp(4), 0, dp(4), 0);
        for (int f = 0; f < 4; f++) {
            LinearLayout fila = horizontal();
            for (int c = 0; c < 3; c++) {
                String[] k = t[f * 3 + c];
                Button b = boton(k[1].isEmpty() ? k[0] : k[0] + "\n" + k[1], TARJETA, BLANCO, 26);
                b.setOnClickListener(v -> meter(k[0]));
                if ("0".equals(k[0])) b.setOnLongClickListener(v -> {
                    meter("+");
                    return true;
                });
                fila.addView(b, peso(1));
            }
            tecl.addView(fila, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        contenido.addView(tecl, relleno());

        LinearLayout abajo = horizontal();
        abajo.setPadding(dp(4), 0, dp(4), dp(4));
        Button guardar = boton("👤+\nGuardar", NARANJA, BLANCO, 15);
        guardar.setOnClickListener(v -> guardarContacto(numero.getText().toString()));
        abajo.addView(guardar, peso(0.8f));
        List<PhoneAccountHandle> sims = sims();
        if (sims.size() >= 2) {
            for (int i = 0; i < sims.size() && i < 2; i++) {
                PhoneAccountHandle ph = sims.get(i);
                Button b = boton("📞 SIM " + (i + 1) + "\n" + etiqueta(ph), VERDE, BLANCO, 16);
                b.setOnClickListener(v -> llamar(numero.getText().toString(), ph));
                abajo.addView(b, peso(1));
            }
        } else {
            Button b = boton("📞 Llamar", VERDE, BLANCO, 22);
            b.setOnClickListener(v -> llamar(numero.getText().toString(), sims.isEmpty() ? null : sims.get(0)));
            abajo.addView(b, peso(2));
        }
        Button borrar = boton("⌫", ROJO, BLANCO, 26);
        borrar.setOnClickListener(v -> borrar());
        borrar.setOnLongClickListener(v -> {
            numero.setText("");
            return true;
        });
        abajo.addView(borrar, peso(0.7f));
        contenido.addView(abajo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
    }

    /** Escribe donde está el cursor (o reemplaza lo seleccionado). */
    private void meter(String s) {
        int a = Math.max(0, numero.getSelectionStart()), b = Math.max(0, numero.getSelectionEnd());
        numero.getText().replace(Math.min(a, b), Math.max(a, b), s);
    }

    private void borrar() {
        int a = Math.max(0, numero.getSelectionStart()), b = Math.max(0, numero.getSelectionEnd());
        if (a != b) numero.getText().replace(Math.min(a, b), Math.max(a, b), "");
        else if (a > 0) numero.getText().delete(a - 1, a);
    }

    private void mirarCoincidencia() {
        if (coincide == null) return;
        String n = numero.getText().toString();
        String nom = Reglas.digitos(n).length() >= 7 ? Reglas.nombreContacto(this, n) : null;
        String txt = nom == null ? "" : "Coincide: " + nom;
        if (Reglas.esBloqueado(this, n)) txt = (txt.isEmpty() ? "" : txt + "  ·  ") + "🚫 bloqueado";
        coincide.setText(txt);
    }

    private List<PhoneAccountHandle> sims() {
        List<PhoneAccountHandle> l = new ArrayList<>();
        if (!Reglas.puede(this, Manifest.permission.READ_PHONE_STATE)) return l;
        try { l.addAll(getSystemService(TelecomManager.class).getCallCapablePhoneAccounts()); } catch (Exception ignored) {}
        return l;
    }

    private String etiqueta(PhoneAccountHandle ph) {
        try {
            PhoneAccount a = getSystemService(TelecomManager.class).getPhoneAccount(ph);
            if (a != null && a.getLabel() != null) {
                String s = a.getLabel().toString();
                if (s.length() > 10) s = s.substring(0, 10);
                return s;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void llamar(String n, PhoneAccountHandle ph) {
        n = n.trim();
        if (Reglas.digitos(n).isEmpty() && !n.contains("*") && !n.contains("#")) {
            aviso("Escriba un número primero");
            return;
        }
        if (!Reglas.puede(this, Manifest.permission.CALL_PHONE)) {
            aviso("Falta el permiso de llamadas. Toque ⚙ y active los permisos.");
            return;
        }
        try {
            Bundle ex = new Bundle();
            if (ph != null) ex.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, ph);
            getSystemService(TelecomManager.class).placeCall(Uri.fromParts("tel", n, null), ex);
        } catch (Exception e) {
            aviso("No se pudo llamar: " + e.getMessage());
        }
    }

    private void llamarConSim(String num) {
        List<PhoneAccountHandle> s = sims();
        if (s.size() >= 2) {
            new AlertDialog.Builder(this).setTitle("¿Con cuál SIM?")
                    .setItems(new String[]{"SIM 1 · " + etiqueta(s.get(0)), "SIM 2 · " + etiqueta(s.get(1))},
                            (d2, w2) -> llamar(num, s.get(w2))).show();
        } else llamar(num, s.isEmpty() ? null : s.get(0));
    }

    private void abrirWhatsApp(String n) {
        String d = Reglas.digitos(n);
        if (d.length() < 7) {
            aviso("Escriba un número primero");
            return;
        }
        if (d.length() == 10) d = "57" + d;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + d));
        for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            try {
                startActivity(new Intent(i).setPackage(pkg));
                return;
            } catch (Exception ignored) {}
        }
        try { startActivity(i); } catch (Exception e) { aviso("No encontré WhatsApp"); }
    }

    // ================= Guardar contacto =================

    private void guardarContacto(String num) {
        if (Reglas.digitos(num).length() < 7) {
            aviso("Escriba primero el número");
            return;
        }
        if (!Reglas.puede(this, Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS}, 14);
            aviso("Permita los contactos y vuelva a tocar Guardar");
            return;
        }
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(18), dp(8), dp(18), 0);
        EditText nom = new EditText(this);
        nom.setHint("Nombre");
        nom.setTextSize(20);
        nom.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        v.addView(nom);
        EditText nu = new EditText(this);
        nu.setText(num.trim());
        nu.setTextSize(22);
        nu.setInputType(InputType.TYPE_CLASS_PHONE);
        v.addView(nu);
        new AlertDialog.Builder(this).setTitle("👤 Guardar contacto").setView(v)
                .setPositiveButton("Guardar", (d, w) -> {
                    String n = nom.getText().toString().trim();
                    String x = nu.getText().toString().trim();
                    if (n.isEmpty()) {
                        aviso("Escriba el nombre");
                        return;
                    }
                    try {
                        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
                        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, n).build());
                        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, x)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
                        getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                        aviso("✅ " + n + " quedó guardado");
                        contactos.clear();
                        mirarCoincidencia();
                    } catch (Exception e) {
                        aviso("No se pudo guardar: " + e.getMessage());
                    }
                }).setNegativeButton("Cancelar", null).show();
    }

    // ================= Recientes =================

    private void pintarRecientes() {
        ScrollView sc = new ScrollView(this);
        LinearLayout l = vertical();
        sc.addView(l);
        contenido.addView(sc, relleno());
        if (!Reglas.puede(this, Manifest.permission.READ_CALL_LOG)) {
            l.addView(nota("Falta el permiso para ver las llamadas. Toque ⚙ y active los permisos."));
            return;
        }
        try (Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
                        CallLog.Calls.DATE, CallLog.Calls.DURATION},
                null, null, CallLog.Calls.DATE + " DESC")) {
            int n = 0;
            while (c != null && c.moveToNext() && n < 200) {
                String num = c.getString(0) == null ? "" : c.getString(0);
                String nom = c.getString(1);
                int tipo = c.getInt(2);
                long fecha = c.getLong(3);
                long dur = c.getLong(4);
                String sim;
                int col;
                String extra = "";
                switch (tipo) {
                    case CallLog.Calls.INCOMING_TYPE: sim = "↙"; col = VERDE_CLARO; extra = " · entró"; break;
                    case CallLog.Calls.OUTGOING_TYPE: sim = "↗"; col = AZUL_CLARO; extra = " · salió"; break;
                    case CallLog.Calls.MISSED_TYPE: sim = "↙"; col = ROJO; extra = " · perdida"; break;
                    case CallLog.Calls.REJECTED_TYPE:
                    case CallLog.Calls.BLOCKED_TYPE: sim = "🚫"; col = GRIS; extra = " · bloqueada"; break;
                    default: sim = "•"; col = GRIS;
                }
                String titulo = nom != null && !nom.isEmpty() ? nom : (num.isEmpty() ? "Número privado" : Reglas.formato(num));
                String det = Reglas.fecha(fecha) + (dur > 0 ? " · " + (dur / 60) + ":" + String.format(Locale.US, "%02d", dur % 60) : "") + extra;
                final String fn = nom;
                l.addView(fila(sim, col, titulo, det, () -> opciones(fn, num)));
                n++;
            }
            if (n == 0) l.addView(nota("No hay llamadas todavía."));
        } catch (Exception e) {
            l.addView(nota("No se pudo leer el registro de llamadas."));
        }
    }

    private TextView nota(String s) {
        TextView t = texto(s, 17, false, GRIS);
        t.setPadding(dp(14), dp(16), dp(14), dp(16));
        return t;
    }

    private View fila(String simbolo, int color, String titulo, String detalle, Runnable alTocar) {
        LinearLayout f = horizontal();
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(dp(10), dp(9), dp(10), dp(9));
        TextView s = texto(simbolo, 24, true, color);
        s.setGravity(Gravity.CENTER);
        f.addView(s, new LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout v = vertical();
        v.addView(texto(titulo, 19, true, BLANCO));
        v.addView(texto(detalle, 14, false, GRIS));
        f.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        f.setOnClickListener(x -> alTocar.run());
        LinearLayout envol = vertical();
        envol.addView(f);
        envol.addView(separador());
        return envol;
    }

    private void opciones(String nombre, String num) {
        String titulo = nombre != null && !nombre.isEmpty() ? nombre : (Reglas.digitos(num).isEmpty() ? "Número privado" : Reglas.formato(num));
        boolean guardado = Reglas.nombreContacto(this, num) != null;
        List<String> op = new ArrayList<>();
        op.add("📞  Llamar");
        op.add("💬  Escribir por WhatsApp");
        if (!guardado) op.add("👤  Guardar en contactos");
        op.add("📋  Copiar número");
        op.add("⌨  Pasar al marcador");
        op.add("⋯  Más…");
        new AlertDialog.Builder(this).setTitle(titulo).setItems(op.toArray(new String[0]), (d, w) -> {
            String o = op.get(w);
            if (o.contains("Llamar")) llamarConSim(num);
            else if (o.contains("WhatsApp")) abrirWhatsApp(num);
            else if (o.contains("Guardar")) guardarContacto(num);
            else if (o.contains("Copiar")) {
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("número", Reglas.digitos(num)));
                aviso("Número copiado");
            } else if (o.contains("marcador")) {
                numero.setText(Reglas.digitos(num));
                numero.setSelection(numero.getText().length());
                pestana = 0;
                pintar();
            } else mas(nombre, num, titulo);
        }).show();
    }

    /** El bloqueo de estafas va escondido aquí. */
    private void mas(String nombre, String num, String titulo) {
        if (Reglas.digitos(num).isEmpty()) {
            aviso("Los números privados se bloquean en ⚙");
            return;
        }
        boolean bloq = Reglas.esBloqueado(this, num);
        String[] op = {bloq ? "✅  Desbloquear" : "🚫  Bloquear (estafa): no vuelve a sonar"};
        new AlertDialog.Builder(this).setTitle(titulo).setItems(op, (d, w) -> {
            if (bloq) {
                Reglas.quitar(this, num);
                aviso(titulo + " ya puede llamar otra vez");
            } else {
                new AlertDialog.Builder(this).setTitle("¿Bloquear " + titulo + "?")
                        .setMessage("No le sonará, no le aparecerá en pantalla y no podrá dejar mensaje. Lo puede desbloquear en ⚙.")
                        .setPositiveButton("Bloquear", (d2, w2) -> {
                            Reglas.agregar(this, nombre == null ? "" : nombre, num);
                            aviso("🚫 " + titulo + " quedó bloqueado");
                            pintar();
                        }).setNegativeButton("Cancelar", null).show();
            }
            pintar();
        }).show();
    }

    // ================= Contactos =================

    private void pintarContactos() {
        if (!Reglas.puede(this, Manifest.permission.READ_CONTACTS)) {
            contenido.addView(nota("Falta el permiso de contactos. Toque ⚙ y active los permisos."));
            return;
        }
        if (contactos.isEmpty()) cargarContactos();
        if (buscar == null) {
            buscar = new EditText(this);
            buscar.setHint("🔍 Buscar por nombre o número");
            buscar.setHintTextColor(GRIS);
            buscar.setTextSize(19);
            buscar.setTextColor(BLANCO);
            buscar.setSingleLine(true);
            buscar.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) {}
                public void afterTextChanged(Editable e) { llenarContactos(); }
            });
        }
        soltar(buscar);
        contenido.addView(buscar, ancho());
        ScrollView sc = new ScrollView(this);
        listaContactos = vertical();
        sc.addView(listaContactos);
        contenido.addView(sc, relleno());
        llenarContactos();
    }

    private void cargarContactos() {
        Set<String> vistos = new HashSet<>();
        try (Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
            while (c != null && c.moveToNext()) {
                String nom = c.getString(0) == null ? "" : c.getString(0);
                String num = c.getString(1) == null ? "" : c.getString(1);
                String d = Reglas.digitos(num);
                String clave = nom + "|" + (d.length() > 10 ? d.substring(d.length() - 10) : d);
                if (vistos.add(clave)) contactos.add(new String[]{nom, num});
            }
        } catch (Exception ignored) {}
    }

    private void llenarContactos() {
        if (listaContactos == null) return;
        listaContactos.removeAllViews();
        String q = buscar.getText().toString().trim().toLowerCase(Locale.ROOT);
        String qd = Reglas.digitos(q);
        int n = 0;
        for (String[] k : contactos) {
            boolean ok = q.isEmpty() || k[0].toLowerCase(Locale.ROOT).contains(q) || (!qd.isEmpty() && Reglas.digitos(k[1]).contains(qd));
            if (!ok) continue;
            listaContactos.addView(fila("●", AZUL_VIVO, k[0], Reglas.formato(k[1]), () -> opciones(k[0], k[1])));
            if (++n >= 300) break;
        }
        if (n == 0) listaContactos.addView(nota("No encontré contactos con eso."));
    }

    // ================= Grabadas =================

    private void pintarGrabadas() {
        if (reproductor != null) {
            Button parar = boton("⏹  Parar el audio: " + sonando, ROJO, BLANCO, 17);
            parar.setOnClickListener(v -> {
                pararAudio();
                pintar();
            });
            contenido.addView(parar, alto(58));
        }
        TextView donde = texto("Llamadas → Descargas/MiBarra/Llamadas   ·   Mensajes → Descargas/MiBarra/Contestador", 13, false, AMARILLO);
        donde.setPadding(dp(10), dp(6), dp(10), dp(4));
        contenido.addView(donde, ancho());
        ScrollView sc = new ScrollView(this);
        LinearLayout l = vertical();
        sc.addView(l);
        contenido.addView(sc, relleno());
        int n = 0;
        Uri col = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        try (Cursor c = getContentResolver().query(col,
                new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.RELATIVE_PATH},
                MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR " + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                new String[]{"Download/MiBarra/Llamadas%", "Download/MiBarra/Contestador%"},
                MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            while (c != null && c.moveToNext() && n < 300) {
                long id = c.getLong(0);
                String nombre = c.getString(1) == null ? "" : c.getString(1);
                long fecha = c.getLong(2) * 1000L;
                long tam = c.getLong(3);
                String ruta = c.getString(4) == null ? "" : c.getString(4);
                boolean mensaje = ruta.contains("Contestador");
                Uri u = ContentUris.withAppendedId(col, id);
                String limpio = nombre.replace(".m4a", "").replace('_', ' ');
                limpio = limpio.replaceAll(" \\d{4}-\\d{2}-\\d{2} \\d{2}-\\d{2}$", "");
                String det = Reglas.fecha(fecha) + " · " + Math.max(1, tam / 1024) + " KB" + (mensaje ? " · mensaje" : " · llamada");
                String titulo = limpio;
                l.addView(fila(mensaje ? "📼" : "🎙", mensaje ? AMARILLO : AZUL_CLARO, titulo, det, () -> opcionesAudio(u, titulo)));
                n++;
            }
        } catch (Exception e) {
            l.addView(nota("No se pudo leer la carpeta."));
        }
        if (n == 0) l.addView(nota("Todavía no hay llamadas grabadas ni mensajes del contestador."));
    }

    private void opcionesAudio(Uri u, String titulo) {
        String[] op = {"▶  Oír aquí", "💬  Enviar por WhatsApp", "📤  Compartir", "🗑  Borrar"};
        new AlertDialog.Builder(this).setTitle(titulo).setItems(op, (d, w) -> {
            if (w == 0) oir(u, titulo);
            else if (w == 1 || w == 2) {
                Intent s = new Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, u)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (w == 1) {
                    try {
                        startActivity(new Intent(s).setPackage("com.whatsapp"));
                        return;
                    } catch (Exception ignored) {}
                }
                try { startActivity(Intent.createChooser(s, "Enviar audio")); } catch (Exception e) { aviso("No se pudo compartir"); }
            } else {
                new AlertDialog.Builder(this).setTitle("¿Borrar " + titulo + "?")
                        .setPositiveButton("Borrar", (d2, w2) -> {
                            pararAudio();
                            try { getContentResolver().delete(u, null, null); } catch (Exception e) { aviso("No se pudo borrar"); }
                            pintar();
                        }).setNegativeButton("Cancelar", null).show();
            }
        }).show();
    }

    private void oir(Uri u, String titulo) {
        pararAudio();
        try {
            MediaPlayer m = new MediaPlayer();
            m.setDataSource(this, u);
            m.setOnCompletionListener(x -> {
                pararAudio();
                pintar();
            });
            m.prepare();
            m.start();
            reproductor = m;
            sonando = titulo;
        } catch (Exception e) {
            aviso("No se pudo oír");
        }
        pintar();
    }

    private void pararAudio() {
        MediaPlayer m = reproductor;
        reproductor = null;
        sonando = "";
        if (m != null) {
            try { m.stop(); } catch (Exception ignored) {}
            try { m.release(); } catch (Exception ignored) {}
        }
    }

    // ================= Ajustes =================

    private void pintarAjustes() {
        ScrollView sc = new ScrollView(this);
        LinearLayout l = vertical();
        l.setPadding(dp(6), dp(6), dp(6), dp(14));
        sc.addView(l);
        contenido.addView(sc, relleno());
        SharedPreferences sp = Reglas.p(this);

        l.addView(subtitulo("1. Activar Mi Teléfono (una sola vez)"));
        l.addView(permiso("App de teléfono principal",
                "Android le pregunta cuál app usar para llamar: escoja \"Mi Barra\".",
                esTelefonoPrincipal(), this::pedirTelefonoPrincipal));
        l.addView(permiso("Llamadas, contactos y micrófono",
                "Le salen varias ventanas: a todas toque \"Permitir\".",
                permisosOk(), () -> requestPermissions(permisosNecesarios(), 11)));
        l.addView(permiso("Accesibilidad de Mi Barra",
                "Sin esto Android no deja grabar durante la llamada. Busque \"Mi Barra\" y actívelo.",
                accesibilidadOk(), () -> abrirAjuste(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button xi = boton("📱 En Xiaomi: permisos de pantalla de bloqueo", TARJETA, BLANCO, 15);
        xi.setOnClickListener(v -> {
            aviso("Toque \"Otros permisos\" y active: Mostrar en pantalla de bloqueo, y Abrir ventanas en segundo plano");
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
        });
        l.addView(xi, alto(60));

        l.addView(subtitulo("2. Grabar llamadas"));
        boolean todas = sp.getBoolean("grabarTodas", true);
        Button g = boton(todas ? "🔴 Altavoz y grabación automáticos: SÍ" : "⚪ Altavoz y grabación automáticos: NO",
                todas ? ROJO : TARJETA, BLANCO, 16);
        g.setOnClickListener(v -> {
            sp.edit().putBoolean("grabarTodas", !sp.getBoolean("grabarTodas", true)).apply();
            pintar();
        });
        l.addView(g, alto(62));

        l.addView(subtitulo("3. Contestador"));
        boolean cont = sp.getBoolean("contestador", true);
        Button bc = boton(cont ? "📼 Contestador: ENCENDIDO" : "📼 Contestador: APAGADO", cont ? AZUL : TARJETA, BLANCO, 17);
        bc.setOnClickListener(v -> {
            sp.edit().putBoolean("contestador", !sp.getBoolean("contestador", true)).apply();
            pintar();
        });
        l.addView(bc, alto(62));
        int seg = sp.getInt("segContestador", 20);
        Button bs = boton("⏱ Si no contesto en " + seg + " segundos, contesta él (toque para cambiar)", TARJETA, BLANCO, 15);
        bs.setOnClickListener(v -> {
            int[] op = {15, 20, 25, 30};
            int i = 0;
            for (int k = 0; k < op.length; k++) if (op[k] == seg) i = k;
            sp.edit().putInt("segContestador", op[(i + 1) % op.length]).apply();
            pintar();
        });
        l.addView(bs, alto(66));
        int max = sp.getInt("maxMensaje", 60);
        Button bm = boton("⏳ Mensaje de máximo " + (max >= 60 ? (max / 60) + " min" : max + " s") + " (toque para cambiar)", TARJETA, BLANCO, 15);
        bm.setOnClickListener(v -> {
            int[] op = {30, 60, 120};
            int i = 0;
            for (int k = 0; k < op.length; k++) if (op[k] == max) i = k;
            sp.edit().putInt("maxMensaje", op[(i + 1) % op.length]).apply();
            pintar();
        });
        l.addView(bm, alto(60));

        boolean hay = Contestador.saludo(this).exists();
        TextView est = texto(hay ? "Saludo: el suyo, con su voz ✓" : "Saludo: voz automática («Deje su mensaje después del tono»)", 15, true, AMARILLO);
        est.setPadding(dp(8), dp(6), dp(8), dp(4));
        l.addView(est);
        LinearLayout f1 = horizontal();
        Button grab = boton(grabSaludo != null ? "⏹ Terminar" : "⏺ Grabar mi saludo", grabSaludo != null ? ROJO : MORADO, BLANCO, 16);
        grab.setOnClickListener(v -> {
            if (grabSaludo != null) terminarSaludo(true);
            else empezarSaludo();
            pintar();
        });
        Button oirS = boton("▶ Oír saludo", TARJETA, BLANCO, 16);
        oirS.setOnClickListener(v -> oirSaludo());
        f1.addView(grab, peso(1));
        f1.addView(oirS, peso(1));
        l.addView(f1, alto(62));
        if (hay) {
            Button auto = boton("🗑 Borrar mi saludo y usar la voz automática", TARJETA, BLANCO, 15);
            auto.setOnClickListener(v -> {
                Contestador.saludo(this).delete();
                pintar();
            });
            l.addView(auto, alto(56));
        }
        TextView ojo = texto("Ojo: Android no deja meter el saludo directo en la llamada; sale por el altavoz y el micrófono lo pasa. Ensáyelo llamándose desde otro celular.", 13, false, GRIS);
        ojo.setPadding(dp(8), dp(4), dp(8), dp(4));
        l.addView(ojo);

        l.addView(subtitulo("4. Bloqueados (estafa)"));
        List<Reglas.Entrada> lista = Reglas.lista(this);
        if (lista.isEmpty()) l.addView(nota("No hay números bloqueados. Para bloquear: toque el número en Recientes o Contactos → Más…"));
        for (Reglas.Entrada e : lista) {
            l.addView(fila("🚫", ROJO, e.titulo(), Reglas.formato(e.numero) + " · toque para desbloquear", () ->
                    new AlertDialog.Builder(this).setTitle("¿Desbloquear " + e.titulo() + "?")
                            .setPositiveButton("Desbloquear", (d, w) -> {
                                Reglas.quitar(this, e.numero);
                                pintar();
                            }).setNegativeButton("Cancelar", null).show()));
        }
        Button nuevo = boton("➕ Bloquear un número escribiéndolo", TARJETA, BLANCO, 15);
        nuevo.setOnClickListener(v -> {
            EditText et = new EditText(this);
            et.setInputType(InputType.TYPE_CLASS_PHONE);
            et.setTextSize(22);
            new AlertDialog.Builder(this).setTitle("Número de estafa").setView(et)
                    .setPositiveButton("Bloquear", (d, w) -> {
                        Reglas.agregar(this, "", et.getText().toString());
                        pintar();
                    }).setNegativeButton("Cancelar", null).show();
        });
        l.addView(nuevo, alto(58));
        boolean ocultos = sp.getBoolean("ocultos", false);
        Button bo = boton(ocultos ? "Números privados u ocultos: BLOQUEADOS" : "Números privados u ocultos: suenan normal", ocultos ? ROJO : TARJETA, BLANCO, 15);
        bo.setOnClickListener(v -> {
            sp.edit().putBoolean("ocultos", !sp.getBoolean("ocultos", false)).apply();
            pintar();
        });
        l.addView(bo, alto(58));
        List<Reglas.Bloqueada> bl = Reglas.bloqueadas(this);
        if (!bl.isEmpty()) {
            TextView t = texto("Últimas llamadas bloqueadas:", 15, true, GRIS);
            t.setPadding(dp(8), dp(8), dp(8), dp(2));
            l.addView(t);
            for (int i = 0; i < bl.size() && i < 10; i++) {
                Reglas.Bloqueada b = bl.get(i);
                String quien = b.nombre.isEmpty() ? (b.numero.isEmpty() ? "Número privado" : Reglas.formato(b.numero)) : b.nombre;
                l.addView(fila("✕", GRIS, quien, Reglas.fecha(b.cuando) + " · " + b.motivo, () -> {}));
            }
        }
    }

    private TextView subtitulo(String s) {
        TextView t = texto(s, 19, true, AZUL_CLARO);
        t.setPadding(dp(6), dp(16), dp(6), dp(4));
        return t;
    }

    private View permiso(String titulo, String explica, boolean listo, Runnable activar) {
        LinearLayout f = horizontal();
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(dp(6), dp(8), dp(6), dp(8));
        LinearLayout v = vertical();
        v.addView(texto(titulo, 17, true, BLANCO));
        v.addView(texto(explica, 14, false, GRIS));
        f.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (listo) {
            TextView ok = texto("Listo ✓", 16, true, VERDE_CLARO);
            ok.setGravity(Gravity.CENTER);
            f.addView(ok, new LinearLayout.LayoutParams(dp(96), dp(50)));
        } else {
            Button b = boton("Activar", ROJO, BLANCO, 16);
            b.setOnClickListener(x -> activar.run());
            f.addView(b, new LinearLayout.LayoutParams(dp(96), dp(50)));
        }
        LinearLayout envol = vertical();
        envol.addView(f);
        envol.addView(separador());
        return envol;
    }

    // ---------- Saludo del contestador ----------

    private void empezarSaludo() {
        if (!Reglas.puede(this, Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 15);
            aviso("Permita el micrófono y vuelva a tocar Grabar");
            return;
        }
        pararAudio();
        try {
            File tmp = new File(getFilesDir(), "saludo_nuevo.m4a");
            if (tmp.exists()) tmp.delete();
            MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioSamplingRate(44100);
            r.setAudioEncodingBitRate(96000);
            r.setAudioChannels(1);
            r.setMaxDuration(25000);
            r.setOutputFile(tmp.getAbsolutePath());
            r.setOnInfoListener((mr, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    terminarSaludo(true);
                    pintar();
                }
            });
            r.prepare();
            r.start();
            grabSaludo = r;
            aviso("🔴 Hable ahora: diga su saludo y toque Terminar (máximo 25 segundos)");
        } catch (Exception e) {
            grabSaludo = null;
            aviso("No se pudo grabar el saludo");
        }
    }

    private void terminarSaludo(boolean guardar) {
        MediaRecorder r = grabSaludo;
        grabSaludo = null;
        if (r == null) return;
        boolean ok = true;
        try { r.stop(); } catch (Exception e) { ok = false; }
        try { r.release(); } catch (Exception ignored) {}
        File tmp = new File(getFilesDir(), "saludo_nuevo.m4a");
        if (guardar && ok && tmp.length() > 1000) {
            File dest = Contestador.saludo(this);
            if (dest.exists()) dest.delete();
            if (tmp.renameTo(dest)) aviso("✅ Saludo guardado. Tóquelo en «Oír saludo» para revisarlo.");
        } else {
            tmp.delete();
        }
    }

    private void oirSaludo() {
        File f = Contestador.saludo(this);
        if (f.exists()) {
            oir(Uri.fromFile(f), "su saludo");
            return;
        }
        if (voz == null) {
            voz = new TextToSpeech(getApplicationContext(), st -> {
                if (st == TextToSpeech.SUCCESS && voz != null) {
                    voz.setLanguage(new Locale("es", "CO"));
                    voz.speak(Contestador.FRASE, TextToSpeech.QUEUE_FLUSH, null, "prueba");
                }
            });
        } else {
            voz.speak(Contestador.FRASE, TextToSpeech.QUEUE_FLUSH, null, "prueba");
        }
    }

    // ================= Permisos =================

    private boolean esTelefonoPrincipal() {
        try {
            RoleManager rm = getSystemService(RoleManager.class);
            return rm != null && rm.isRoleHeld(RoleManager.ROLE_DIALER);
        } catch (Exception e) {
            return false;
        }
    }

    private void pedirTelefonoPrincipal() {
        try {
            RoleManager rm = getSystemService(RoleManager.class);
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 10);
        } catch (Exception e) {
            abrirAjuste(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
            aviso("Busque \"App de teléfono\" y escoja Mi Barra");
        }
    }

    private String[] permisosNecesarios() {
        List<String> l = new ArrayList<>();
        l.add(Manifest.permission.CALL_PHONE);
        l.add(Manifest.permission.READ_CALL_LOG);
        l.add(Manifest.permission.WRITE_CALL_LOG);
        l.add(Manifest.permission.READ_CONTACTS);
        l.add(Manifest.permission.WRITE_CONTACTS);
        l.add(Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS);
        l.add(Manifest.permission.RECORD_AUDIO);
        return l.toArray(new String[0]);
    }

    private boolean permisosOk() {
        for (String p : permisosNecesarios()) if (!Reglas.puede(this, p)) return false;
        return true;
    }

    private boolean accesibilidadOk() {
        try {
            String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(getPackageName() + "/");
        } catch (Exception e) {
            return false;
        }
    }

    private void abrirAjuste(String accion) {
        try {
            startActivity(new Intent(accion));
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        contactos.clear();
        pintar();
    }

    @Override
    protected void onActivityResult(int code, int res, Intent data) {
        super.onActivityResult(code, res, data);
        pintar();
    }
}
