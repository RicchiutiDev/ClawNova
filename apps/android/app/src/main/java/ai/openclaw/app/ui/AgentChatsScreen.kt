package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.R
import ai.openclaw.app.chat.ChatSessionEntry
import ai.openclaw.app.chat.ChatSessionPatch
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.resolveAgentIdFromMainSessionKey
import ai.openclaw.app.ui.design.ClawAgentAvatar
import ai.openclaw.app.ui.design.ClawAgentAvatarFallback
import ai.openclaw.app.ui.design.ClawEmptyState
import ai.openclaw.app.ui.design.ClawLoadingState
import ai.openclaw.app.ui.design.ClawPlainIconButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.agentAvatarSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Agent ownership is explicit; a matching display title never changes a session's owner. */
internal fun agentOwnedSessions(
  sessions: List<ChatSessionEntry>,
  agentId: String,
): List<ChatSessionEntry> =
  sessions
    .filter { (it.ownerAgentId ?: resolveAgentIdFromMainSessionKey(it.key)) == agentId && it.archived != true }
    .sortedWith(compareByDescending<ChatSessionEntry> { it.lastActivityAt ?: it.updatedAtMs ?: Long.MIN_VALUE }.thenBy { it.key })
    .distinctBy(ChatSessionEntry::key)

@Composable
internal fun AgentChatsScreen(
  viewModel: MainViewModel,
  agentId: String,
  onBack: () -> Unit,
  onOpenChat: () -> Unit,
) {
  val agents by viewModel.gatewayAgents.collectAsState()
  val gatewayId by viewModel.activeGatewayStableId.collectAsState()
  val connection by viewModel.gatewayConnectionDisplay.collectAsState()
  val agent = agents.firstOrNull { it.id == agentId }
  var sessions by remember(agentId, gatewayId) { mutableStateOf<List<ChatSessionEntry>>(emptyList()) }
  var loading by remember(agentId, gatewayId) { mutableStateOf(true) }
  var loadFailed by remember(agentId, gatewayId) { mutableStateOf(false) }
  var starting by remember(agentId, gatewayId) { mutableStateOf(false) }
  var startFailed by remember(agentId, gatewayId) { mutableStateOf(false) }
  var refresh by remember(agentId, gatewayId) { mutableIntStateOf(0) }
  var query by rememberSaveable(agentId, gatewayId) { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  var deleteTarget by remember(agentId, gatewayId) { mutableStateOf<ChatSessionEntry?>(null) }
  var deleteFailed by remember(agentId, gatewayId) { mutableStateOf(false) }
  var deleting by remember(agentId, gatewayId) { mutableStateOf(false) }
  LaunchedEffect(agentId, gatewayId, connection.isConnected, refresh) {
    loading = true
    loadFailed = false
    val loaded = viewModel.fetchAgentChatSessions(agentId)
    if (loaded != null) sessions = agentOwnedSessions(loaded, agentId) else loadFailed = true
    loading = false
  }
  val matching =
    sessions.filter {
      query.isBlank() || (sessionPresentationTitle(it) { nativeString("New chat") } + " " + it.label.orEmpty()).contains(query.trim(), ignoreCase = true)
    }
  val rows = overviewRecentSessionRows(matching, channelsSummary = null)
  val startNewChat: () -> Unit = {
    if (!starting && connection.isConnected) {
      starting = true
      startFailed = false
      scope.launch {
        try {
          if (viewModel.startNewChatForAgent(agentId)) onOpenChat() else startFailed = true
        } catch (error: CancellationException) {
          throw error
        } catch (_: Exception) {
          startFailed = true
        } finally {
          starting = false
        }
      }
    }
  }
  ClawScaffold {
    Column(modifier = Modifier.fillMaxSize().testTag("agent-chats"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClawPlainIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nativeString("Back"), onClick = onBack)
        ClawAgentAvatar(source = agent?.let(::agentAvatarSource), size = 32.dp, identity = agentId) { ClawAgentAvatarFallback(identity = agentId, size = 32.dp) }
        Text(agent?.name?.takeIf(String::isNotBlank) ?: agentId, style = ClawTheme.type.title, color = ClawTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        ClawPlainIconButton(icon = Icons.Default.Add, contentDescription = nativeString("New chat"), onClick = startNewChat, enabled = !starting && connection.isConnected)
      }
      ClawTextField(value = query, onValueChange = { query = it }, placeholder = nativeString("Search"), maxLines = 1)
      if (deleteFailed) Text(stringResource(R.string.cn_operation_error), color = ClawTheme.colors.danger, style = ClawTheme.type.caption)
      if (startFailed) Text(stringResource(R.string.clawnova_agent_chat_create_error), color = ClawTheme.colors.danger, style = ClawTheme.type.caption)
      if (loading && sessions.isEmpty()) {
        ClawLoadingState(nativeString("Loading"))
      } else if (loadFailed) {
        ClawEmptyState(
          title = stringResource(R.string.clawnova_agent_chats_error),
          body = "",
          action = { ClawSecondaryButton(nativeString("Try again"), onClick = { refresh++ }) },
        )
      } else if (rows.isEmpty()) {
        ClawEmptyState(
          title = if (query.isBlank()) stringResource(R.string.clawnova_no_agent_chats) else nativeString("No results"),
          body = if (query.isBlank()) stringResource(R.string.clawnova_no_agent_chats_body) else "",
          action = { if (query.isBlank()) ClawSecondaryButton(nativeString("New chat"), onClick = startNewChat, enabled = !starting && connection.isConnected) },
        )
      } else {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
          items(rows, key = { it.key }) { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.weight(1f)) { HomeSessionRow(row = row, agent = agent, onClick = {
                viewModel.switchChatSession(row.key, agentId)
                onOpenChat()
              }) }
              IconButton(enabled = !deleting, onClick = { deleteTarget = sessions.firstOrNull { it.key == row.key } }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cn_delete_chat), tint = if (deleting) ClawTheme.colors.textMuted else ClawTheme.colors.text)
              }
            }
          }
        }
      }
    }
  }
  deleteTarget?.let { session ->
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text(stringResource(R.string.cn_delete_chat)) },
      text = { Text(stringResource(R.string.cn_delete_chat_body)) },
      confirmButton = { TextButton(onClick = {
        deleteTarget = null
        deleting = true
        deleteFailed = false
        val requestGateway = gatewayId
        scope.launch {
          try {
            val archived = viewModel.patchChatSession(ChatSessionPatch(key = session.key, ownerAgentId = agentId, expectedSessionId = session.sessionId, archived = true))
            val deleted = archived && viewModel.activeGatewayStableId.value == requestGateway && viewModel.deleteChatSession(session.key, agentId)
            if (viewModel.activeGatewayStableId.value == requestGateway) {
              if (deleted) sessions = sessions.filterNot { it.key == session.key } else deleteFailed = true
            }
          } catch (error: CancellationException) { throw error } catch (_: Exception) { deleteFailed = true } finally { deleting = false }
        }
      }) { Text(stringResource(R.string.cn_delete), color = ClawTheme.colors.danger) } },
      dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cn_cancel)) } },
    )
  }

}
