package ai.openclaw.app.ui

import ai.openclaw.app.ChatMessageMatch
import ai.openclaw.app.ChatMessageSearchResult
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.R
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun HomeMessageSearch(
  viewModel: MainViewModel,
  query: String,
  connected: Boolean,
  onOpenMessage: (ChatMessageMatch) -> Unit,
) {
  val gatewayId by viewModel.activeGatewayStableId.collectAsState()
  var result by remember(query, gatewayId, connected) { mutableStateOf<ChatMessageSearchResult?>(null) }
  var status by remember(query, gatewayId, connected) {
    mutableStateOf(if (connected) R.string.cn_searching_messages else R.string.cn_search_messages_offline)
  }
  LaunchedEffect(query, gatewayId, connected) {
    if (!connected) return@LaunchedEffect
    delay(300)
    try {
      val response = viewModel.searchChatMessages(query)
      result = response
      status =
        when {
          response == null -> R.string.cn_search_messages_unavailable
          response.incomplete -> R.string.cn_search_messages_partial
          response.matches.isEmpty() -> R.string.cn_search_messages_empty
          else -> 0
        }
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      status = R.string.cn_search_messages_failed
    }
  }

  Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(stringResource(R.string.cn_search_messages_heading), style = ClawTheme.type.section, color = ClawTheme.colors.text)
    if (status != 0) {
      Text(stringResource(status), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    }
    result?.matches?.forEach { match ->
      Surface(
        onClick = {
          if (viewModel.activeGatewayStableId.value == gatewayId) onOpenMessage(match)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ClawTheme.colors.surfaceRaised,
      ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(match.title, style = ClawTheme.type.label, color = ClawTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(match.snippet, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
