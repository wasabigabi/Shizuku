package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A single executable capability an agent may invoke once authorized.
 *
 * Separated from {@link McpTool} (the static descriptor) so that "what is known
 * and authorized" stays independent from "how it runs". The gate only ever sees
 * descriptors; execution happens here, and only after an {@link McpDecision#ALLOW}.
 */
public interface CapabilityTool {

    @NonNull
    McpTool descriptor();

    /**
     * Runs the tool. For a {@link DangerLevel#READ_ONLY} tool this must not
     * change any device state. {@code args} is the parsed request arguments
     * (possibly empty). The returned element becomes the {@code result} field
     * of the response.
     */
    @NonNull
    JsonElement execute(@NonNull JsonObject args) throws Exception;
}
