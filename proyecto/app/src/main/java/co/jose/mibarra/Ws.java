package co.jose.mibarra;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Conexión WebSocket pequeñita para hablar con el televisor en la red de la casa.
 * (Los televisores usan certificados propios: solo se aceptan para su dirección local.)
 */
final class Ws {
    interface Oyente { void mensaje(String texto); void cerrado(String porque); }

    private Socket s;
    private InputStream in;
    private OutputStream out;
    private volatile boolean abierto;
    private final Oyente oyente;

    Ws(Oyente o) { oyente = o; }

    boolean abierto() { return abierto; }

    void conectar(String url, int espera) throws Exception {
        URI u = new URI(url);
        boolean seguro = "wss".equals(u.getScheme());
        int puerto = u.getPort();
        Socket base = new Socket();
        base.connect(new InetSocketAddress(u.getHost(), puerto), espera);
        base.setSoTimeout(0);
        if (seguro) {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());
            SSLSocket ss = (SSLSocket) ctx.getSocketFactory().createSocket(base, u.getHost(), puerto, true);
            ss.startHandshake();
            s = ss;
        } else s = base;
        in = s.getInputStream();
        out = s.getOutputStream();
        byte[] k = new byte[16];
        new SecureRandom().nextBytes(k);
        String clave = android.util.Base64.encodeToString(k, android.util.Base64.NO_WRAP);
        String ruta = u.getRawPath() + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
        String pedido = "GET " + (ruta.isEmpty() ? "/" : ruta) + " HTTP/1.1\r\nHost: " + u.getHost() + ":" + puerto
                + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + clave + "\r\nSec-WebSocket-Version: 13\r\n\r\n";
        out.write(pedido.getBytes(StandardCharsets.UTF_8));
        out.flush();
        // Leer la respuesta hasta la línea en blanco
        StringBuilder r = new StringBuilder();
        int c, nl = 0;
        while ((c = in.read()) != -1) {
            r.append((char) c);
            if (c == '\n') { if (++nl == 2) break; } else if (c != '\r') nl = 0;
        }
        if (!r.toString().startsWith("HTTP/1.1 101")) throw new Exception("El televisor no aceptó: " + r.toString().split("\r\n")[0]);
        abierto = true;
        Thread t = new Thread(this::leer, "tv");
        t.setDaemon(true);
        t.start();
    }

    private void leer() {
        String porque = "";
        try {
            ByteArrayOutputStream partes = new ByteArrayOutputStream();
            while (abierto) {
                int b0 = in.read(), b1 = in.read();
                if (b0 < 0 || b1 < 0) break;
                int op = b0 & 0x0F;
                long largo = b1 & 0x7F;
                if (largo == 126) largo = (in.read() << 8) | in.read();
                else if (largo == 127) { largo = 0; for (int i = 0; i < 8; i++) largo = (largo << 8) | in.read(); }
                byte[] mask = null;
                if ((b1 & 0x80) != 0) { mask = new byte[4]; leerTodo(mask); }
                byte[] datos = new byte[(int) largo];
                leerTodo(datos);
                if (mask != null) for (int i = 0; i < datos.length; i++) datos[i] ^= mask[i % 4];
                if (op == 8) { porque = "cerrado por el televisor"; break; }
                if (op == 9) { mandar(10, datos); continue; }
                if (op == 1 || op == 0) {
                    partes.write(datos);
                    if ((b0 & 0x80) != 0) { oyente.mensaje(partes.toString("UTF-8")); partes.reset(); }
                }
            }
        } catch (Exception e) { porque = e.getMessage() == null ? "se cortó" : e.getMessage(); }
        abierto = false;
        try { s.close(); } catch (Exception ignored) {}
        oyente.cerrado(porque);
    }

    private void leerTodo(byte[] b) throws Exception {
        int n = 0;
        while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) throw new Exception("fin"); n += r; }
    }

    synchronized void texto(String t) {
        try { mandar(1, t.getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { abierto = false; }
    }

    private synchronized void mandar(int op, byte[] d) throws Exception {
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(0x80 | op);
        if (d.length < 126) f.write(0x80 | d.length);
        else if (d.length < 65536) { f.write(0x80 | 126); f.write(d.length >> 8); f.write(d.length & 255); }
        else { f.write(0x80 | 127); for (int i = 7; i >= 0; i--) f.write((int) ((long) d.length >> (8 * i)) & 255); }
        byte[] m = new byte[4];
        new SecureRandom().nextBytes(m);
        f.write(m);
        for (int i = 0; i < d.length; i++) f.write(d[i] ^ m[i % 4]);
        out.write(f.toByteArray());
        out.flush();
    }

    void cerrar() {
        abierto = false;
        try { mandar(8, new byte[0]); } catch (Exception ignored) {}
        try { s.close(); } catch (Exception ignored) {}
    }
}
