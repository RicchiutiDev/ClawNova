package ai.openclaw.app.ui.design

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import org.json.JSONObject

internal data class BotAppearance(val shape: Int, val color: Int)
internal val botColors = listOf(0xFFFF334F, 0xFFFF8A20, 0xFFE1B94A, 0xFF00C978, 0xFF00BFAE, 0xFF559EFF, 0xFF9C68FF, 0xFFEE65B3)
internal class BotAppearanceStore(val styles: Map<String, BotAppearance>, val save: (String, BotAppearance?) -> Unit)
internal val LocalBotAppearance = staticCompositionLocalOf { BotAppearanceStore(emptyMap()) { _, _ -> } }

/** Appearance is device-local and scoped to the active Gateway. */
@Composable
internal fun BotAppearanceProvider(gatewayId: String?, content: @Composable () -> Unit) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("clawnova_bot_appearance", Context.MODE_PRIVATE) }
  var styles by remember(gatewayId) { mutableStateOf(readBotAppearances(prefs.getString(gatewayId ?: "", null))) }
  val store = BotAppearanceStore(styles) { id, appearance ->
    if (!gatewayId.isNullOrBlank()) {
      val updated = if (appearance == null) styles - id else styles + (id to appearance)
      styles = updated
      val json = JSONObject()
      updated.forEach { (agentId, style) -> json.put(agentId, JSONObject().put("shape", style.shape).put("color", style.color)) }
      prefs.edit().putString(gatewayId, json.toString()).apply()
    }
  }
  CompositionLocalProvider(LocalBotAppearance provides store, content = content)
}

internal fun readBotAppearances(raw: String?): Map<String, BotAppearance> = runCatching {
  val json = JSONObject(raw ?: "{}")
  json.keys().asSequence().mapNotNull { id ->
    val value = json.optJSONObject(id) ?: return@mapNotNull null
    val shape = value.optInt("shape", -1)
    val color = value.optInt("color", -1)
    if (shape in 0..3 && color in botColors.indices) id to BotAppearance(shape, color) else null
  }.toMap()
}.getOrDefault(emptyMap())

@Composable
internal fun CustomBotAvatar(appearance: BotAppearance, size: Dp, modifier: Modifier = Modifier) {
  val color = Color(botColors[appearance.color.coerceIn(botColors.indices)])
  Canvas(modifier.size(size)) {
    val unit = this.size.minDimension / 100f
    withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
      when (appearance.shape) {
        1 -> drawCircle(color, radius = 38f, center = Offset(50f, 50f))
        2 -> {
          val path = Path().apply { moveTo(50f, 8f); lineTo(84f, 27f); lineTo(84f, 70f); lineTo(50f, 90f); lineTo(16f, 70f); lineTo(16f, 27f); close() }
          drawPath(path, color)
        }
        3 -> {
          val path = Path().apply { moveTo(50f, 7f); lineTo(61f, 31f); lineTo(88f, 34f); lineTo(68f, 53f); lineTo(73f, 81f); lineTo(50f, 69f); lineTo(27f, 81f); lineTo(32f, 53f); lineTo(12f, 34f); lineTo(39f, 31f); close() }
          drawPath(path, color)
        }
        else -> {
          val tail = Path().apply { moveTo(60f, 69f); lineTo(78f, 88f); lineTo(78f, 65f); close() }
          drawPath(tail, color)
          drawRoundRect(color, topLeft = Offset(12f, 18f), size = Size(76f, 62f), cornerRadius = CornerRadius(23f))
        }
      }
      for (x in listOf(35f, 56f)) drawRoundRect(Color(0xFF171717), topLeft = Offset(x, 39f), size = Size(9f, 18f), cornerRadius = CornerRadius(4.5f))
    }
  }
}
