package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import rikka.shizuku.server.util.Logger;

/**
 * Append-only audit trail of every gate decision and call outcome.
 *
 * The point of the whole security layer is that nothing privileged happens
 * without a trace. Every authorize check and every confirmation result is
 * recorded here as one JSON line. A bounded in-memory ring buffer holds the
 * most recent entries for the manager UI; the file is rotated once at a size
 * cap so it cannot grow without limit.
 */
public final class McpAuditLog {

    private static final Logger LOGGER = new Logger("ShizukuMcpAudit");

    private static final File FILE = new File("/data/local/tmp/shizuku/mcp-audit.log");
    private static final long MAX_BYTES = 1024 * 1024; // rotate past 1 MiB
    private static final int MEMORY_LIMIT = 200;
    private static final int DETAIL_MAX = 200;

    /** Lifecycle event recorded for a call. */
    public enum Event {
        AUTHORIZED,   // gate returned ALLOW
        DENIED,       // gate returned DENY
        CONFIRM_REQUIRED,
        CONFIRMED,    // user approved a confirmation
        REJECTED,     // user rejected a confirmation
        EXECUTED,     // capability ran successfully
        FAILED        // capability threw / errored
    }

    public static final class Entry {
        public long timestamp;
        public String agentId;
        public String agentLabel;
        public String tool;
        public String argsSummary;
        public String event;
        public String detail;
    }

    private final Gson gson = new Gson();
    private final File file;
    private final Deque<Entry> recent = new ArrayDeque<>();

    public McpAuditLog() {
        this(FILE);
    }

    /** Visible for testing with a temp file. */
    McpAuditLog(@NonNull File file) {
        this.file = file;
    }

    public void record(@NonNull Event event,
                       @Nullable String agentId, @Nullable String agentLabel,
                       @NonNull String tool, @Nullable String argsSummary,
                       @Nullable String detail) {
        Entry entry = new Entry();
        entry.timestamp = System.currentTimeMillis();
        entry.agentId = agentId != null ? agentId : "?";
        entry.agentLabel = agentLabel != null ? agentLabel : "?";
        entry.tool = tool;
        entry.argsSummary = truncate(argsSummary);
        entry.event = event.name();
        entry.detail = truncate(detail);

        synchronized (this) {
            recent.addLast(entry);
            while (recent.size() > MEMORY_LIMIT) {
                recent.removeFirst();
            }
            appendLocked(entry);
        }
    }

    private void appendLocked(Entry entry) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                // Best effort; the shizuku data dir normally already exists.
                parent.mkdirs();
            }
            if (file.length() > MAX_BYTES) {
                File old = new File(file.getParentFile(), file.getName() + ".1");
                //noinspection ResultOfMethodCallIgnored
                old.delete();
                //noinspection ResultOfMethodCallIgnored
                file.renameTo(old);
            }
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.seek(raf.length());
                raf.write((gson.toJson(entry) + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // Never let an audit-write failure break the call path, but make
            // it loud: an untraceable privileged call is the thing we fear.
            LOGGER.e(e, "failed to append audit entry for tool %s", entry.tool);
        }
    }

    /** Most recent entries, oldest first, for display in the manager. */
    @NonNull
    public synchronized List<Entry> recent() {
        return new ArrayList<>(recent);
    }

    @Nullable
    private static String truncate(@Nullable String s) {
        if (s == null) {
            return null;
        }
        if (s.length() <= DETAIL_MAX) {
            return s;
        }
        return s.substring(0, DETAIL_MAX) + "...";
    }
}
