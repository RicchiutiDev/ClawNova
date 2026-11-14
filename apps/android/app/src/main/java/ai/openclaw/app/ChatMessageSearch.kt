package ai.openclaw.app

import ai.openclaw.app.node.asObjectOrNull
import ai.openclaw.app.node.asStringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal data class ChatMessageMatch(
  val sessionKey: String,
  val agentId: String?,
  val title: String,
  val snippet: String,
)

internal data class ChatMessageSearchResult(
  val matches: List<ChatMessageMatch>,
  val incomplete: Boolean,
)

internal fun parseChatMessageSearchResult(response: JsonObject): ChatMessageSearchResult {
  val sessions =
    (response["sessions"] as? JsonArray).orEmpty().mapNotNull { entry ->
      val session = entry.asObjectOrNull() ?: return@mapNotNull null
      val key = session["key"].asStringOrNull() ?: return@mapNotNull null
      key to session
    }.toMap()
  val matches =
    (response["results"] as JsonArray).mapNotNull { entry ->
      val hit = entry.asObjectOrNull() ?: return@mapNotNull null
      val key = hit["sessionKey"].asStringOrNull()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
      val snippet = hit["snippet"].asStringOrNull()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
      val session = sessions[key]
      val title =
        sequenceOf("label", "displayName", "derivedTitle")
          .mapNotNull { field -> session?.get(field).asStringOrNull()?.takeIf(String::isNotBlank) }
          .firstOrNull() ?: key
      ChatMessageMatch(key, session?.get("agentId").asStringOrNull(), title, snippet)
    }
  return ChatMessageSearchResult(
    matches = matches,
    incomplete = (response["indexing"] as? JsonPrimitive)?.booleanOrNull == true ||
      (response["truncated"] as? JsonPrimitive)?.booleanOrNull == true,
  )
}
