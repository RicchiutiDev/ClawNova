package ai.openclaw.app.chat

import ai.openclaw.app.asBooleanOrNull
import ai.openclaw.app.asJsonStringOrNull
import ai.openclaw.app.asLongOrNull
import ai.openclaw.app.mapObjects
import ai.openclaw.app.node.asObjectOrNull
import ai.openclaw.app.nonBlankString
import ai.openclaw.app.resolveAgentIdFromMainSessionKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val RECENT_AGENT_MESSAGE_LIMIT = 160

internal data class AgentSessionAddress(val agentId: String, val key: String)

internal data class AgentExchangeIdentity(
  val recipient: AgentSessionAddress,
  val entryId: String?,
  val fallback: List<Any?>? = null,
)

internal data class AgentExchange(
  val senderId: String,
  val recipient: AgentSessionAddress,
  val timestampMs: Long?,
  val text: String,
  val entryId: String?,
) {
  val identity = AgentExchangeIdentity(
    recipient,
    entryId,
    fallback = if (entryId == null) listOf(senderId, timestampMs, text) else null,
  )

  fun involves(agentId: String): Boolean = agentId == senderId || agentId == recipient.agentId
}

internal data class LiveAgentExchange(val exchange: AgentExchange, val receivedAtMs: Long)

internal fun agentSessionAddress(row: JsonObject?, event: JsonObject? = null): AgentSessionAddress? {
  val key = row.nonBlankString("key") ?: event.nonBlankString("sessionKey") ?: return null
  val agentId = row.nonBlankString("agentId") ?: event.nonBlankString("agentId") ?: resolveAgentIdFromMainSessionKey(key) ?: return null
  return AgentSessionAddress(agentId.lowercase(), key)
}

internal fun parseAgentExchange(recipient: AgentSessionAddress, message: JsonObject, entryId: String? = null): AgentExchange? {
  // OpenClaw's display projection provides this address after identifying and sanitizing a forwarded input.
  val sender = message["senderSession"].asObjectOrNull() ?: return null
  if (message.nonBlankString("role") != "assistant" || message["display"].asBooleanOrNull() == false) return null
  val provenance = message["provenance"].asObjectOrNull()
  if (provenance != null && provenance.nonBlankString("kind") != "inter_session") return null
  val senderId = (sender.nonBlankString("agentId") ?: resolveAgentIdFromMainSessionKey(sender.nonBlankString("sessionKey")))?.lowercase() ?: return null
  if (senderId == recipient.agentId) return null
  val content = message["content"]
  val text =
    ((content as? JsonArray).mapObjects {
      if (it.nonBlankString("type") in setOf("text", "input_text")) it["text"].asJsonStringOrNull() else null
    }.joinToString("\n\n").takeIf(String::isNotBlank)
      ?: content.asJsonStringOrNull()
      ?: message["text"].asJsonStringOrNull())
      ?.trim()?.takeIf(String::isNotEmpty) ?: return null
  return AgentExchange(
    senderId,
    recipient,
    message["timestamp"].asLongOrNull(),
    text,
    entryId ?: message["__openclaw"].asObjectOrNull().nonBlankString("id") ?: message.nonBlankString("id"),
  )
}

internal data class AgentExchangeHistory(val messages: List<AgentExchange>, val incomplete: Boolean)

internal suspend fun loadAgentExchangeHistory(
  agentId: String,
  request: suspend (method: String, params: JsonObject) -> JsonObject?,
): AgentExchangeHistory {
  // A send is stored in the recipient's transcript, so outgoing history requires a cross-agent list.
  val list = request("sessions.list", buildJsonObject {
    put("limit", 12)
    put("sortBy", "activity")
    put("includeGlobal", true)
  })
  val sessions = list?.get("sessions") as? JsonArray ?: error("Gateway did not return a session list")
  val addresses = sessions.mapObjects { agentSessionAddress(it) }.distinct()
  val pages = coroutineScope {
    addresses.chunked(3).flatMap { batch ->
      batch.map { address ->
        async {
          try {
            val history = request("chat.history", buildJsonObject {
              put("sessionKey", address.key)
              put("agentId", address.agentId)
              put("limit", 40)
              put("maxBytes", 96_000)
            })
            (history?.get("messages") as? JsonArray)?.mapObjects { parseAgentExchange(address, it) }
          } catch (error: CancellationException) {
            throw error
          } catch (_: Exception) {
            null
          }
        }
      }.awaitAll()
    }
  }
  return AgentExchangeHistory(
    messages = pages.filterNotNull().flatten().filter { it.involves(agentId) },
    incomplete = pages.any { it == null },
  )
}

internal class AgentActivity {
  private val activeSessions = mutableSetOf<AgentSessionAddress>()
  private var observing = false
  private var pendingSnapshot: Snapshot? = null
  private val _workingAgents = MutableStateFlow(emptySet<String>())
  val workingAgents = _workingAgents.asStateFlow()
  private val _exchanges = MutableStateFlow(emptyList<LiveAgentExchange>())
  val exchanges = _exchanges.asStateFlow()
  private val _historyInvalidation = MutableStateFlow(0L)
  val historyInvalidation = _historyInvalidation.asStateFlow()

  internal class Snapshot {
    val changes = mutableMapOf<AgentSessionAddress, Boolean>()
  }

  @Synchronized
  fun beginSnapshot(): Snapshot {
    observing = true
    return Snapshot().also { pendingSnapshot = it }
  }

  @Synchronized
  fun finishSnapshot(snapshot: Snapshot, rows: JsonArray?) {
    if (pendingSnapshot !== snapshot) return
    pendingSnapshot = null
    if (rows == null) return
    activeSessions.clear()
    activeSessions.addAll(rows.mapObjects { row ->
      agentSessionAddress(row)?.takeIf { activeRun(row) == true }
    })
    // The subscription can deliver a newer event before its initial list response arrives.
    snapshot.changes.forEach(::setActive)
    publishWorkingAgents()
  }

  @Synchronized
  fun observe(event: String, payload: JsonObject, nowMs: Long) {
    if (!observing) return
    val row = payload["session"].asObjectOrNull()
    val address = agentSessionAddress(row, payload) ?: return
    val active = when (payload.nonBlankString("reason")) {
      "delete", "reset" -> false
      else -> activeRun(row) ?: activeRun(payload)
    }
    if (active != null) {
      pendingSnapshot?.changes?.set(address, active)
      setActive(address, active)
      publishWorkingAgents()
    }
    if (event == "sessions.changed" && payload.nonBlankString("phase") == "message" && payload.nonBlankString("messageId") == null) {
      _historyInvalidation.value++
    }
    if (event != "session.message") return
    val message = payload["message"].asObjectOrNull() ?: return
    val exchange = parseAgentExchange(address, message, payload.nonBlankString("messageId")) ?: return
    val current = _exchanges.value
    if (current.any { it.exchange.identity == exchange.identity }) return
    _exchanges.value = (listOf(LiveAgentExchange(exchange, nowMs)) + current).take(RECENT_AGENT_MESSAGE_LIMIT)
  }

  @Synchronized
  fun clear() {
    observing = false
    activeSessions.clear()
    pendingSnapshot = null
    _workingAgents.value = emptySet()
    _exchanges.value = emptyList()
  }

  private fun setActive(address: AgentSessionAddress, active: Boolean) {
    if (active) activeSessions.add(address) else activeSessions.remove(address)
  }

  private fun publishWorkingAgents() {
    _workingAgents.value = activeSessions.mapTo(mutableSetOf(), AgentSessionAddress::agentId)
  }

  private fun activeRun(row: JsonObject?): Boolean? {
    if (row == null) return null
    val status = row.nonBlankString("status")
    if (status == "queued") return false
    return row["hasActiveRun"].asBooleanOrNull()
      ?: (row["activeRunIds"] as? JsonArray)?.isNotEmpty()
  }
}
