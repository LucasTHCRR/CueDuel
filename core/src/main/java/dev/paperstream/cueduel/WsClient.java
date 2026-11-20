package dev.paperstream.cueduel;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

// Kleiner WebSocket-Client, nur Textnachrichten
final class WsClient {
    interface Listener {
        void onOpen(WsClient c);

        void onText(WsClient c, String text);

        void onClosed(WsClient c);
    }

    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private final String url;
    private final Listener listener;
    private final SecureRandom rnd = new SecureRandom();
    private final ExecutorService out = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ws-send");
        t.setDaemon(true);
        return t;
    });
    private volatile Socket socket;
    private volatile OutputStream os;
    private volatile boolean closed;
    private boolean reported;

    WsClient(String url, Listener listener) {
        this.url = url;
        this.listener = listener;
    }

    void connect() {
        Thread t = new Thread(this::run, "ws-read");
        t.setDaemon(true);
        t.start();
    }

    private static String base64(byte[] d) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < d.length; i += 3) {
            int v = (d[i] & 255) << 16 | (i + 1 < d.length ? (d[i + 1] & 255) << 8 : 0) | (i + 2 < d.length ? d[i + 2] & 255 : 0);
            b.append(B64.charAt(v >> 18 & 63)).append(B64.charAt(v >> 12 & 63));
            b.append(i + 1 < d.length ? B64.charAt(v >> 6 & 63) : '=');
            b.append(i + 2 < d.length ? B64.charAt(v & 63) : '=');
        }
        return b.toString();
    }

    private void run() {
        try {
            URI u = URI.create(url);
            boolean tls = "wss".equals(u.getScheme());
            int port = u.getPort() > 0 ? u.getPort() : (tls ? 443 : 80);
            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(u.getHost(), port), 8000);
            Socket s = raw;
            if (tls) {
                SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(raw, u.getHost(), port, true);
                SSLParameters p = ssl.getSSLParameters();
                p.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(p);
                ssl.setSoTimeout(10000);
                ssl.startHandshake();
                s = ssl;
            }
            s.setSoTimeout(10000);
            s.setTcpNoDelay(true);
            socket = s;
            if (closed) {
                s.close();
                return;
            }
            byte[] key = new byte[16];
            rnd.nextBytes(key);
            String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
            OutputStream o = s.getOutputStream();
            o.write(("GET " + path + " HTTP/1.1\r\nHost: " + u.getHost() + "\r\nUpgrade: websocket\r\n"
                + "Connection: Upgrade\r\nSec-WebSocket-Key: " + base64(key) + "\r\nSec-WebSocket-Version: 13\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
            o.flush();
            InputStream in = s.getInputStream();
            StringBuilder head = new StringBuilder();
            while (head.indexOf("\r\n\r\n") < 0) {
                int c = in.read();
                if (c < 0 || head.length() > 8192) throw new IOException("bad handshake");
                head.append((char) c);
            }
            if (!head.toString().startsWith("HTTP/1.1 101")) throw new IOException("no upgrade");
            s.setSoTimeout(0);
            os = o;
            listener.onOpen(this);
            DataInputStream d = new DataInputStream(in);
            ByteArrayOutputStream part = new ByteArrayOutputStream();
            while (!closed) {
                int b0 = d.readUnsignedByte();
                int b1 = d.readUnsignedByte();
                long len = b1 & 127;
                if (len == 126) len = d.readUnsignedShort();
                else if (len == 127) len = d.readLong();
                if (len < 0 || len > 1 << 20) throw new IOException("frame too big");
                byte[] mask = null;
                if ((b1 & 128) != 0) {
                    mask = new byte[4];
                    d.readFully(mask);
                }
                byte[] data = new byte[(int) len];
                d.readFully(data);
                if (mask != null) for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
                int op = b0 & 15;
                if (op == 8) break;
                if (op == 9) {
                    frame(10, data);
                } else if (op == 0 || op == 1) {
                    part.write(data);
                    if ((b0 & 128) != 0) {
                        String text = new String(part.toByteArray(), StandardCharsets.UTF_8);
                        part.reset();
                        listener.onText(this, text);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            shutdown();
        }
    }

    private void frame(int op, byte[] data) {
        if (closed) return;
        submit(() -> {
            OutputStream o = os;
            if (o == null) return;
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream(data.length + 14);
                b.write(128 | op);
                if (data.length < 126) {
                    b.write(128 | data.length);
                } else if (data.length < 65536) {
                    b.write(128 | 126);
                    b.write(data.length >> 8);
                    b.write(data.length & 255);
                } else {
                    b.write(128 | 127);
                    for (int i = 7; i >= 0; i--) b.write((int) ((long) data.length >> (8 * i)) & 255);
                }
                byte[] mask = new byte[4];
                rnd.nextBytes(mask);
                b.write(mask);
                for (int i = 0; i < data.length; i++) b.write(data[i] ^ mask[i & 3]);
                o.write(b.toByteArray());
                o.flush();
            } catch (IOException e) {
                shutdown();
            }
        });
    }

    void send(String text) {
        frame(1, text.getBytes(StandardCharsets.UTF_8));
    }

    void ping() {
        frame(9, new byte[0]);
    }

    boolean isOpen() {
        return os != null && !closed;
    }

    void close() {
        if (closed) return;
        frame(8, new byte[0]);
        submit(this::shutdown);
    }

    private void submit(Runnable r) {
        try {
            out.execute(r);
        } catch (RuntimeException ignored) {
        }
    }

    private void shutdown() {
        boolean tell;
        synchronized (this) {
            closed = true;
            tell = !reported;
            reported = true;
        }
        try {
            Socket s = socket;
            if (s != null) s.close();
        } catch (IOException ignored) {
        }
        out.shutdown();
        if (tell) listener.onClosed(this);
    }
}
