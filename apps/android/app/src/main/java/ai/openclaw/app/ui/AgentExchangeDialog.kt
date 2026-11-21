package ai.openclaw.app.ui

import ai.openclaw.app.GatewayAgentSummary
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.R
import ai.openclaw.app.chat.AgentExchange
import ai.openclaw.app.chat.AgentExchangeHistory
import ai.openclaw.app.chat.LiveAgentExchange
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawAgentAvatar
import ai.openclaw.app.ui.design.ClawAgentAvatarFallback
import ai.openclaw.app.ui.design.ClawEmptyState
import ai.openclaw.app.ui.design.ClawLoadingState
import ai.openclaw.app.ui.design.ClawPlainIconButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.agentAvatarSource
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val EXCHANGE_BADGE_DURATION_MS = 4_000L

@Composable
internal fun ActivityAgentAvatar(
  agent: GatewayAgentSummary,
  size: Dp,
  working: Boolean,
  latest: LiveAgentExchange?,
) {
  var badgeVisible by remember(latest) { mutableStateOf(false) }
  LaunchedEffect(latest) {
    val remaining = latest?.let { EXCHANGE_BADGE_DURATION_MS - (SystemClock.elapsedRealtime() - it.receivedAtMs) } ?: 0
    if (remaining > 0) {
      badgeVisible = true
      delay(remaining)
      badgeVisible = false
    }
  }
  val sent = latest?.exchange?.senderId == agent.id.lowercase()
  val status = listOfNotNull(
    if (working) stringResource(R.string.cn_agent_working) else null,
    if (badgeVisible) stringResource(if (sent) R.string.cn_agent_message_sent else R.string.cn_agent_message_received) else null,
  ).joinToString(", ")
  val activityRing =
    if (working) {
      val pulse = rememberInfiniteTransition(label = "Agent activity").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "Activity ring",
      )
      val color = ClawTheme.colors.success
      Modifier.drawBehind {
        drawCircle(
          color = color.copy(alpha = 0.68f + 0.32f * pulse.value),
          radius = this.size.minDimension / 2 + (1f + 2f * pulse.value).dp.toPx(),
          style = Stroke(2.dp.toPx()),
        )
      }
    } else {
      Modifier
    }
  Box(
    modifier = Modifier
      .size(size)
      .testTag("agent-avatar-${agent.id}")
      .semantics {
        if (status.isNotEmpty()) stateDescription = status
      }
      .then(activityRing),
  ) {
    ClawAgentAvatar(source = agentAvatarSource(agent), size = size, identity = agent.id) {
      ClawAgentAvatarFallback(identity = agent.id, size = size)
    }
    if (badgeVisible) {
      Box(
        modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).background(ClawTheme.colors.canvas, CircleShape).padding(2.dp)
          .background(ClawTheme.colors.primary, CircleShape).padding(2.dp),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          if (sent) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          tint = ClawTheme.colors.primaryText,
        )
      }
    }
  }
}

@Composable
internal fun AgentExchangeDialog(
  viewModel: MainViewModel,
  agentId: String,
  agents: List<GatewayAgentSummary>,
  connected: Boolean,
  onClose: () -> Unit,
) {
  val normalizedId = agentId.lowercase()
  val names = remember(agents) { agents.associate { it.id.lowercase() to (it.name?.takeIf(String::isNotBlank) ?: it.id) } }
  val live by viewModel.liveAgentExchanges.collectAsState()
  var history by remember(agentId, connected) { mutableStateOf<AgentExchangeHistory?>(null) }
  var failed by remember(agentId, connected) { mutableStateOf(false) }
  var retry by remember { mutableIntStateOf(0) }
  LaunchedEffect(agentId, connected, retry) {
    if (!connected) return@LaunchedEffect
    // StateFlow conflates invalidations while history is loading. Read again if the
    // Gateway coalesced committed messages into a history invalidation.
    viewModel.agentExchangeHistoryInvalidation.collect {
      try {
        failed = false
        history = viewModel.fetchAgentExchangeHistory(normalizedId)
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        failed = true
      }
      delay(1_000)
    }
  }
  val messages = remember(live, history, normalizedId, connected) {
    if (!connected) emptyList() else
      (live.map { it.exchange }.filter { it.involves(normalizedId) } + history?.messages.orEmpty())
        .distinctBy(AgentExchange::identity)
        .sortedByDescending { it.timestampMs ?: Long.MIN_VALUE }
  }
  val context = LocalContext.current
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    ClawScaffold(modifier = Modifier.testTag("agent-exchange-dialog")) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawPlainIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nativeString("Back"), onClick = onClose)
          Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.cn_agent_exchanges), style = ClawTheme.type.title, color = ClawTheme.colors.text)
            Text(names[normalizedId] ?: agentId, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
          Text(stringResource(R.string.cn_agent_exchanges_read_only), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        }
        when {
          !connected -> Text(stringResource(R.string.cn_agent_exchanges_offline), color = ClawTheme.colors.textMuted)
          messages.isEmpty() && failed ->
            ClawEmptyState(title = stringResource(R.string.cn_agent_exchanges_error), body = "", action = {
              ClawSecondaryButton(nativeString("Try again"), onClick = { retry++ })
            })
          messages.isEmpty() && history == null -> ClawLoadingState(nativeString("Loading"))
          else -> {
            if (failed || history?.incomplete == true) {
              Text(stringResource(R.string.cn_agent_exchanges_partial), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            }
            if (messages.isEmpty()) {
              Text(stringResource(R.string.cn_agent_exchanges_empty), color = ClawTheme.colors.textMuted)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
              items(messages) { message ->
                val sender = names[message.senderId] ?: message.senderId
                val recipient = names[message.recipient.agentId] ?: message.recipient.agentId
                val outgoing = message.senderId == normalizedId
                Column(
                  modifier = Modifier.fillMaxWidth().background(ClawTheme.colors.surfaceRaised, RoundedCornerShape(16.dp)).padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  Text(
                    stringResource(R.string.cn_agent_exchange_route, sender, recipient),
                    style = ClawTheme.type.label,
                    color = if (outgoing) ClawTheme.colors.accent else ClawTheme.colors.text,
                  )
                  message.timestampMs?.let { timestamp ->
                    Text(DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                  }
                  Text(message.text, style = ClawTheme.type.body, color = ClawTheme.colors.text)
                }
              }
            }
          }
        }
      }
    }
  }
}
