package ai.openclaw.app.ui

import ai.openclaw.app.R
import ai.openclaw.app.ui.design.BotAppearance
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.CustomBotAvatar
import ai.openclaw.app.ui.design.botColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val BotAppearanceSaver =
  listSaver<BotAppearance?, Int>(
    save = { appearance -> appearance?.let { listOf(it.shape, it.color) } ?: emptyList() },
    restore = { indices -> if (indices.isEmpty()) null else BotAppearance(indices[0], indices[1]) },
  )

@Composable
internal fun BotAppearanceDialog(
  initial: BotAppearance,
  onSave: (BotAppearance) -> Unit,
  onDismiss: () -> Unit,
  onRestore: (() -> Unit)? = null,
) {
  var shape by rememberSaveable(initial.shape) { mutableStateOf(initial.shape) }
  var colorIndex by rememberSaveable(initial.color) { mutableStateOf(initial.color) }
  val shapeNames = listOf(R.string.cn_shape_bubble, R.string.cn_shape_round, R.string.cn_shape_hexagon, R.string.cn_shape_star)
  val colorNames =
    listOf(
      R.string.cn_color_red,
      R.string.cn_color_orange,
      R.string.cn_color_yellow,
      R.string.cn_color_green,
      R.string.cn_color_teal,
      R.string.cn_color_blue,
      R.string.cn_color_purple,
      R.string.cn_color_pink,
    )
  val draft = BotAppearance(shape, colorIndex)
  AlertDialog(
    modifier = Modifier.testTag("bot-appearance-dialog"),
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.cn_edit_bot_icon)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CustomBotAvatar(draft, 72.dp) }
        Text(stringResource(R.string.cn_shape), style = ClawTheme.type.section)
        shapeNames.forEachIndexed { index, label ->
          val selected = shape == index
          Row(
            Modifier.fillMaxWidth().testTag("bot-appearance-shape-$index")
              .selectable(selected = selected, role = Role.RadioButton, onClick = { shape = index })
              .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = selected, onClick = null)
            Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
          }
        }
        Text(stringResource(R.string.cn_color), style = ClawTheme.type.section)
        botColors.indices.chunked(4).forEach { indices ->
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            indices.forEach { index ->
              val label = stringResource(colorNames[index])
              val selected = colorIndex == index
              Box(
                Modifier
                  .size(48.dp)
                  .semantics { contentDescription = label }
                  .selectable(selected = selected, role = Role.RadioButton, onClick = { colorIndex = index })
                  .padding(5.dp)
                  .background(Color(botColors[index]), CircleShape)
                  .border(if (selected) 3.dp else 0.dp, ClawTheme.colors.text, CircleShape),
              )
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.cn_save)) } },
    dismissButton = {
      Row {
        if (onRestore != null) TextButton(onClick = onRestore) { Text(stringResource(R.string.cn_restore_icon)) }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cn_cancel)) }
      }
    },
  )
}
