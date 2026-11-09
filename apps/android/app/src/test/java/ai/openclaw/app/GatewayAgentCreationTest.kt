package ai.openclaw.app

import ai.openclaw.app.gateway.GatewayRequestNotEnqueued
import ai.openclaw.app.gateway.GatewayRequestOutcomeUnknown
import ai.openclaw.app.gateway.GatewaySession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayAgentCreationTest {
  @Test
  fun nameOnlyCreationLeavesWorkspaceDefaultToGatewayAndPublishesCanonicalRoster() =
    runBlocking {
      val calls = mutableListOf<Pair<String, String?>>()
      var published = emptyList<GatewayAgentSummary>()
      val lease =
        lease { method, params ->
          calls += method to params
          if (method == "agents.create") confirmed else """{"agents":[{"id":"analyst","name":"Canonical Analyst"}]}"""
        }

      val agent = createGatewayAgent("  Analyst  ", null, lease, ::admit) { published = it }

      assertEquals(listOf("agents.create", "agents.list"), calls.map { it.first })
      val params = Json.parseToJsonElement(requireNotNull(calls.first().second)).jsonObject
      assertEquals(setOf("name"), params.keys)
      assertEquals("Analyst", params.getValue("name").jsonPrimitive.content)
      assertEquals("analyst", agent.id)
      assertEquals("Canonical Analyst", agent.name)
      assertEquals(listOf(agent), published)
    }

  @Test
  fun successfulMutationIsNotReplayedWhenRosterRefreshFails() =
    runBlocking {
      val calls = mutableListOf<String>()
      val lease =
        lease { method, _ ->
          calls += method
          if (method == "agents.list") error("Roster temporarily unavailable")
          confirmed
        }
      val agent = createGatewayAgent("Analyst", "/workspace/analyst", lease, ::admit) { error("No roster was returned") }
      assertEquals("analyst", agent.id)
      assertEquals("Analyst", agent.name)
      assertEquals(listOf("agents.create", "agents.list"), calls)
    }

  @Test
  fun revokedAuthorityRejectsMutationAtTheTransportEnqueueBoundary() =
    runBlocking {
      var permitted = true
      var enqueued = 0
      val lease =
        GatewaySession.RequestLease(endpointStableId = "gateway-a", advertisedMethods = setOf("agents.create")) { _, _, _, withEnqueue ->
          permitted = false
          withEnqueue { enqueued++ }
          confirmed
        }
      val result =
        runCatching {
          createGatewayAgent("Analyst", null, lease, { block ->
            if (permitted) {
              block()
              true
            } else {
              false
            }
          }) {}
        }
      assertTrue(result.exceptionOrNull() is GatewayRequestNotEnqueued)
      assertEquals(0, enqueued)
    }

  @Test
  fun retiredGatewayCannotPublishConfirmedIdentityOrRoster() =
    runBlocking {
      var current = true
      var publications = 0
      val calls = mutableListOf<String>()
      val lease =
        lease(current = { current }) { method, _ ->
          calls += method
          current = false
          confirmed
        }
      val result = runCatching { createGatewayAgent("Analyst", null, lease, ::admit) { publications++ } }
      assertTrue(result.exceptionOrNull() is CancellationException)
      assertEquals(0, publications)
      assertEquals(listOf("agents.create"), calls)
    }

  @Test
  fun unknownMutationOutcomeRequiresRefreshInsteadOfAutomaticRetry() =
    runBlocking {
      var requests = 0
      val lease =
        lease { _, _ ->
          requests++
          throw GatewayRequestOutcomeUnknown("Socket closed after enqueue")
        }
      val result = runCatching { createGatewayAgent("Analyst", null, lease, ::admit) {} }
      assertEquals(1, requests)
      assertTrue(
        result
          .exceptionOrNull()
          ?.message
          .orEmpty()
          .contains("Refresh the agent list"),
      )
    }

  @Test
  fun unsupportedCreationNeverReachesTransport() =
    runBlocking {
      var requests = 0
      val lease =
        GatewaySession.RequestLease(endpointStableId = "gateway-a") { _, _, _, _ ->
          requests++
          confirmed
        }
      val result = runCatching { createGatewayAgent("Analyst", null, lease, ::admit) {} }
      assertTrue(
        result
          .exceptionOrNull()
          ?.message
          .orEmpty()
          .contains("does not support"),
      )
      assertEquals(0, requests)
    }

  private fun admit(block: () -> Unit): Boolean {
    block()
    return true
  }

  private fun lease(
    current: () -> Boolean = { true },
    request: suspend (String, String?) -> String,
  ) = GatewaySession.RequestLease(endpointStableId = "gateway-a", isCurrentImpl = current, advertisedMethods = setOf("agents.create", "agents.list")) { method, params, _, withEnqueue ->
    withEnqueue {}
    request(method, params)
  }

  private val confirmed = """{"ok":true,"agentId":"analyst","name":"Analyst","workspace":"/gateway/workspaces/analyst"}"""
}
