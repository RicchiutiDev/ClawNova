package ai.openclaw.app.chat

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentActivityTest {
  @Test
  fun liveSessionChangesWinOverTheInitialSnapshotAndOnlyTheLastRunClearsTheAvatar() {
    val activity = AgentActivity()
    fun observe(payload: String) = activity.observe("sessions.changed", Json.parseToJsonElement(payload).jsonObject, 0)

    val snapshot = activity.beginSnapshot()
    observe("""{"sessionKey":"agent:demo:one","hasActiveRun":false}""")
    observe("""{"session":{"key":"agent:demo:two","activeRunIds":["live"]}}""")
    activity.finishSnapshot(snapshot, Json.parseToJsonElement(
      """[
        {"key":"agent:demo:one","hasActiveRun":true},
        {"key":"agent:demo:three","hasActiveRun":true},
        {"key":"agent:main:one","status":"queued","hasActiveRun":true}
      ]""",
    ).jsonArray)
    assertEquals(setOf("demo"), activity.workingAgents.value)
    observe("""{"sessionKey":"agent:demo:two","reason":"delete"}""")
    assertEquals(setOf("demo"), activity.workingAgents.value)
    observe("""{"session":{"key":"agent:demo:three","hasActiveRun":false,"activeRunIds":null}}""")
    assertTrue(activity.workingAgents.value.isEmpty())
    val retired = activity.beginSnapshot()
    activity.clear()
    activity.finishSnapshot(retired, Json.parseToJsonElement("""[{"key":"agent:demo:two","hasActiveRun":true}]""").jsonArray)
    assertTrue(activity.workingAgents.value.isEmpty())
  }

  @Test
  fun liveAndHistoryReadTheSameForwardedMessageAndIgnoreOrdinaryChat() = runTest {
    val activity = AgentActivity()
    activity.finishSnapshot(activity.beginSnapshot(), null)
    val forwarded = """
      {"role":"assistant","senderSession":{"sessionKey":"agent:main:one"},
      "content":[{"type":"thinking","text":"Hidden"},{"type":"text","text":"Review is ready."}],
      "timestamp":1234,"__openclaw":{"id":"entry-1"}}
    """.trimIndent()
    val normal = """{"role":"assistant","content":"This answer mentions Demo."}"""
    val automation = """{"role":"assistant","senderSession":{"agentId":"main"},"provenance":{"kind":"internal_system"},"content":"Daily report"}"""
    fun observe(message: String) {
      val event = """{"sessionKey":"agent:demo:one","message":$message}"""
      activity.observe("session.message", Json.parseToJsonElement(event).jsonObject, 10)
    }
    observe(normal)
    observe(forwarded)
    observe(forwarded)
    val history = loadAgentExchangeHistory("main") { method, params ->
      when (method) {
        "sessions.list" -> Json.parseToJsonElement("""{"sessions":[{"key":"agent:demo:one"}]}""").jsonObject
        "chat.history" -> {
          assertEquals("demo", params["agentId"]?.jsonPrimitive?.content)
          assertEquals("agent:demo:one", params["sessionKey"]?.jsonPrimitive?.content)
          Json.parseToJsonElement("""{"messages":[$normal,$forwarded,$automation]}""").jsonObject
        }
        else -> error("Unexpected method $method")
      }
    }
    val live = activity.exchanges.value.single().exchange
    assertEquals(live.identity, history.messages.single().identity)
    assertEquals("main", live.senderId)
    assertEquals("demo", live.recipient.agentId)
    assertEquals("Review is ready.", live.text)
    activity.clear()
    observe(forwarded)
    assertTrue(activity.exchanges.value.isEmpty())
  }
}
