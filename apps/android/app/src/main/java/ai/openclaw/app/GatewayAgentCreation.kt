package ai.openclaw.app

import ai.openclaw.app.gateway.GatewayRequestNotEnqueued
import ai.openclaw.app.gateway.GatewayRequestOutcomeUnknown
import ai.openclaw.app.gateway.GatewaySession
import ai.openclaw.app.node.asObjectOrNull
import ai.openclaw.app.node.asStringOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class AgentCreationState(
  val submitting: Boolean = false,
  val createdAgent: GatewayAgentSummary? = null,
  val error: String? = null,
)

/** One confirmed create response is authoritative even when the subsequent roster read fails. */
internal suspend fun createGatewayAgent(
  name: String,
  workspace: String?,
  lease: GatewaySession.RequestLease,
  commitIfCurrent: ((() -> Unit) -> Boolean),
  publishRoster: (List<GatewayAgentSummary>) -> Unit,
): GatewayAgentSummary {
  val normalizedName = name.trim()
  require(normalizedName.isNotEmpty()) { "Enter a name for the agent." }
  check(lease.supportsMethod("agents.create")) { "This gateway does not support creating agents." }
  val params =
    buildJsonObject {
      put("name", JsonPrimitive(normalizedName))
      workspace?.trim()?.takeIf(String::isNotEmpty)?.let { put("workspace", JsonPrimitive(it)) }
    }

  fun enqueueIfCurrent(enqueue: () -> Unit) {
    if (!commitIfCurrent(enqueue)) throw GatewayRequestNotEnqueued("The gateway or permission changed. Open agent creation again.")
  }
  val payload =
    try {
      lease.request("agents.create", params.toString(), timeoutMs = 60_000, withEnqueue = ::enqueueIfCurrent)
    } catch (error: GatewayRequestOutcomeUnknown) {
      throw IllegalStateException("Agent creation was not confirmed. Refresh the agent list before trying again.", error)
    }
  currentCoroutineContext().ensureActive()
  if (!lease.isCurrent() || !commitIfCurrent {}) throw CancellationException("Agent creation gateway changed")
  val result =
    Json.parseToJsonElement(payload).asObjectOrNull()
      ?: error("The gateway did not confirm agent creation. Refresh the agent list before trying again.")
  check((result["ok"] as? JsonPrimitive)?.booleanOrNull == true) { "The gateway did not confirm agent creation. Refresh the agent list before trying again." }
  val id =
    result["agentId"].asStringOrNull()?.trim()?.takeIf(String::isNotEmpty)
      ?: error("The gateway created an agent without returning its identity. Refresh the agent list before trying again.")
  val created = GatewayAgentSummary(id = id, name = result["name"].asStringOrNull()?.takeIf(String::isNotBlank) ?: normalizedName, emoji = null)
  var canonical: GatewayAgentSummary? = null
  try {
    val rosterPayload = lease.request("agents.list", "{}", withEnqueue = ::enqueueIfCurrent)
    val roster = parseGatewayAgentSummaries(Json.parseToJsonElement(rosterPayload).asObjectOrNull() ?: error("Invalid agent roster"))
    lease.commitIfCurrent {
      commitIfCurrent {
        publishRoster(roster)
        canonical = roster.firstOrNull { it.id == id }
      }
    }
  } catch (error: CancellationException) {
    throw error
  } catch (_: Throwable) {
    // Never replay a successful mutation because an optional readback was unavailable.
  }
  currentCoroutineContext().ensureActive()
  if (!lease.isCurrent() || !commitIfCurrent {}) throw CancellationException("Agent creation gateway changed")
  return canonical ?: created
}
