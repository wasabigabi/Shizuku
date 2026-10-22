package moe.shizuku.manager.mcp

import android.os.Parcel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import rikka.shizuku.Shizuku
import rikka.shizuku.server.ServerConstants

/**
 * Manager-side client for the MCP bridge.
 *
 * Talks to the privileged Shizuku server over its binder using the same
 * custom-transaction pattern as [moe.shizuku.manager.authorization.AuthorizationManager]
 * (see [ServerConstants.BINDER_TRANSACTION_getApplications]). The channel is a
 * method name plus a JSON argument string in, and a JSON result string out; the
 * server enforces that only the manager may call it.
 *
 * All calls are synchronous binder transactions and must be made off the main
 * thread.
 */
object McpRepository {

    private const val DESCRIPTOR = "moe.shizuku.server.IShizukuService"

    enum class DangerLevel { READ_ONLY, STATE_CHANGE, SENSITIVE, UNKNOWN }

    data class Agent(
        val id: String,
        val label: String,
        val createdAt: Long,
        val revoked: Boolean,
        val allowedTools: List<String>,
        val trustedTools: List<String>
    )

    data class ToolInfo(
        val name: String,
        val description: String,
        val dangerLevel: DangerLevel,
        val confirmByDefault: Boolean
    )

    data class AuditEntry(
        val timestamp: Long,
        val agentId: String,
        val agentLabel: String,
        val tool: String,
        val argsSummary: String?,
        val event: String,
        val detail: String?
    )

    /** Raw token is returned only here, once, right after pairing. */
    data class Paired(val agentId: String, val rawToken: String)

    data class TransportStatus(val running: Boolean, val port: Int)

    data class PendingRequest(
        val id: String,
        val agentId: String,
        val agentLabel: String,
        val tool: String,
        val argsSummary: String?,
        val createdAt: Long
    )

    class McpException(message: String) : RuntimeException(message)

    private fun call(method: String, args: JsonObject? = null): JsonObject {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeString(method)
            data.writeString(args?.toString())
            val binder = Shizuku.getBinder() ?: throw McpException("Shizuku binder unavailable")
            binder.transact(ServerConstants.BINDER_TRANSACTION_mcpManagerCall, data, reply, 0)
            reply.readException()
            val json = reply.readString() ?: throw McpException("empty reply")
            val obj = JsonParser.parseString(json).asJsonObject
            if (obj.has("error")) {
                throw McpException(obj.get("error").asString)
            }
            obj
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    fun listTools(): List<ToolInfo> =
        call("listTools").getAsJsonArray("data").map { el ->
            val o = el.asJsonObject
            ToolInfo(
                o.get("name").asString,
                o.get("description").asString,
                parseDanger(o.get("dangerLevel").asString),
                o.get("confirmByDefault").asBoolean
            )
        }

    fun listAgents(): List<Agent> =
        call("listAgents").getAsJsonArray("data").map { el ->
            val o = el.asJsonObject
            Agent(
                o.get("id").asString,
                o.get("label").asString,
                o.get("createdAt").asLong,
                o.get("revoked").asBoolean,
                stringList(o.getAsJsonArray("allowedTools")),
                stringList(o.getAsJsonArray("trustedTools"))
            )
        }

    fun recentAudit(): List<AuditEntry> =
        call("recentAudit").getAsJsonArray("data").map { el ->
            val o = el.asJsonObject
            AuditEntry(
                o.get("timestamp").asLong,
                o.get("agentId").asString,
                o.get("agentLabel").asString,
                o.get("tool").asString,
                o.get("argsSummary").takeIf { !it.isJsonNull }?.asString,
                o.get("event").asString,
                o.get("detail").takeIf { !it.isJsonNull }?.asString
            )
        }

    fun pair(label: String): Paired {
        val args = JsonObject().apply { addProperty("label", label) }
        val data = call("pair", args).getAsJsonObject("data")
        return Paired(data.get("agentId").asString, data.get("rawToken").asString)
    }

    fun revoke(agentId: String): Boolean {
        val args = JsonObject().apply { addProperty("agentId", agentId) }
        return call("revoke", args).getAsJsonObject("data").get("ok").asBoolean
    }

    fun setAllowedTools(agentId: String, tools: List<String>): Boolean {
        val arr = JsonArray().apply { tools.forEach { add(it) } }
        val args = JsonObject().apply {
            addProperty("agentId", agentId)
            add("tools", arr)
        }
        return call("setAllowedTools", args).getAsJsonObject("data").get("ok").asBoolean
    }

    fun setTrusted(agentId: String, tool: String, trusted: Boolean): Boolean {
        val args = JsonObject().apply {
            addProperty("agentId", agentId)
            addProperty("tool", tool)
            addProperty("trusted", trusted)
        }
        return call("setTrusted", args).getAsJsonObject("data").get("ok").asBoolean
    }

    fun getTransportStatus(): TransportStatus = parseStatus(call("getTransportStatus"))

    fun setTransportEnabled(enabled: Boolean): TransportStatus {
        val args = JsonObject().apply { addProperty("enabled", enabled) }
        return parseStatus(call("setTransportEnabled", args))
    }

    private fun parseStatus(resp: JsonObject): TransportStatus {
        val data = resp.getAsJsonObject("data")
        return TransportStatus(data.get("running").asBoolean, data.get("port").asInt)
    }

    fun listPending(): List<PendingRequest> =
        call("listPending").getAsJsonArray("data").map { el ->
            val o = el.asJsonObject
            PendingRequest(
                o.get("id").asString,
                o.get("agentId").asString,
                o.get("agentLabel").asString,
                o.get("tool").asString,
                o.get("argsSummary").takeIf { !it.isJsonNull }?.asString,
                o.get("createdAt").asLong
            )
        }

    fun resolveConfirmation(id: String, approved: Boolean): Boolean {
        val args = JsonObject().apply {
            addProperty("id", id)
            addProperty("approved", approved)
        }
        return call("resolveConfirmation", args).getAsJsonObject("data").get("ok").asBoolean
    }

    private fun parseDanger(name: String): DangerLevel = try {
        DangerLevel.valueOf(name)
    } catch (e: IllegalArgumentException) {
        DangerLevel.UNKNOWN
    }

    private fun stringList(arr: JsonArray?): List<String> =
        arr?.map { it.asString } ?: emptyList()
}
