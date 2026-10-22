package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.server.util.Logger;

/**
 * Minimal agent transport: a newline-delimited JSON request/response server
 * bound to loopback only.
 *
 * Intended to be reached from a developer machine via {@code adb forward
 * tcp:PORT tcp:PORT}. It never binds a routable address. Every request must
 * carry a pairing token and is routed through {@link McpExecutor}, so the gate
 * authorizes each call independently; the transport itself grants nothing.
 *
 * Disabled by default. Only start it on explicit user action.
 *
 * Request (one JSON object per line):
 * <pre>
 *   {"token":"...","tool":"packages.list","args":{}}
 *   {"token":"...","op":"list"}
 * </pre>
 * Response: one JSON object line, {@code {"result":...}} or {@code {"error":...}}.
 */
public final class McpTransportServer {

    private static final Logger LOGGER = new Logger("ShizukuMcpTransport");

    public static final int DEFAULT_PORT = 7231;

    private static final int MAX_LINE = 64 * 1024;
    private static final int READ_TIMEOUT_MS = 30 * 1000;
    private static final int MAX_WORKERS = 4;

    private final McpExecutor executor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService workers;
    private int port;

    public McpTransportServer(@NonNull McpExecutor executor) {
        this.executor = executor;
    }

    public synchronized boolean isRunning() {
        return running.get();
    }

    public synchronized int getPort() {
        return port;
    }

    /** Starts listening on loopback:{@code port}. Idempotent. */
    public synchronized void start(int port) {
        if (running.get()) {
            return;
        }
        try {
            // Loopback only, never a routable interface. Backlog small on purpose.
            serverSocket = new ServerSocket(port, 8, InetAddress.getLoopbackAddress());
            this.port = serverSocket.getLocalPort();
            workers = Executors.newFixedThreadPool(MAX_WORKERS, r -> {
                Thread t = new Thread(r, "mcp-transport-worker");
                t.setDaemon(true);
                return t;
            });
            running.set(true);
            acceptThread = new Thread(this::acceptLoop, "mcp-transport-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
            LOGGER.i("MCP transport listening on 127.0.0.1:%d", this.port);
        } catch (IOException e) {
            LOGGER.e(e, "failed to start MCP transport on port %d", port);
            running.set(false);
            closeQuietly();
        }
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        closeQuietly();
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
        LOGGER.i("MCP transport stopped");
    }

    private void closeQuietly() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            final Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.w("accept failed: " + e.getMessage());
                }
                break;
            }
            try {
                workers.execute(() -> handle(socket));
            } catch (Throwable tr) {
                closeSocket(socket);
            }
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(READ_TIMEOUT_MS);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8), MAX_LINE);
            OutputStream out = socket.getOutputStream();

            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.length() > MAX_LINE) {
                    writeLine(out, "{\"error\":\"request too large\",\"code\":\"too_large\"}");
                    break;
                }
                writeLine(out, dispatch(line));
            }
        } catch (IOException e) {
            // Normal on client disconnect / timeout.
        } finally {
            closeSocket(socket);
        }
    }

    private String dispatch(String line) {
        try {
            JsonObject req = JsonParser.parseString(line).getAsJsonObject();
            String token = req.has("token") ? req.get("token").getAsString() : null;
            if (token == null || token.isEmpty()) {
                return "{\"error\":\"missing token\",\"code\":\"unauthenticated\"}";
            }
            if (req.has("op") && "list".equals(req.get("op").getAsString())) {
                return executor.listForToken(token);
            }
            if (req.has("tool")) {
                String tool = req.get("tool").getAsString();
                String argsJson = req.has("args") && !req.get("args").isJsonNull()
                        ? req.get("args").toString() : null;
                return executor.run(token, tool, argsJson);
            }
            return "{\"error\":\"request must set 'tool' or 'op'\",\"code\":\"bad_request\"}";
        } catch (Throwable tr) {
            return "{\"error\":\"malformed request\",\"code\":\"bad_request\"}";
        }
    }

    private static void writeLine(OutputStream out, String json) throws IOException {
        out.write(json.getBytes(StandardCharsets.UTF_8));
        out.write('\n');
        out.flush();
    }

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
