package ai.openclaw.app.ui

import ai.openclaw.app.GatewayAgentSummary
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.R
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.BotAppearance
import ai.openclaw.app.ui.design.ClawAgentAvatarFallback
import ai.openclaw.app.ui.design.ClawPlainIconButton
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.CustomBotAvatar
import ai.openclaw.app.ui.design.LocalBotAppearance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun CreateAgentScreen(
  viewModel: MainViewModel,
  name: String,
  onNameChange: (String) -> Unit,
  onBack: () -> Unit,
  onCreated: (GatewayAgentSummary) -> Unit,
  onOpenGateway: () -> Unit,
) {
  val state by viewModel.agentCreationState.collectAsState()
  val connection by viewModel.gatewayConnectionDisplay.collectAsState()
  val appearances = LocalBotAppearance.current
  var avatarDraft by rememberSaveable(stateSaver = BotAppearanceSaver) { mutableStateOf<BotAppearance?>(null) }
  var avatarPickerOpen by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(state.createdAgent) {
    state.createdAgent?.let { created ->
      viewModel.clearAgentCreationResult()
      onCreated(created)
    }
  }
  ClawScaffold {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).testTag("create-agent"), verticalArrangement = Arrangement.spacedBy(24.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ClawPlainIconButton(icon = Icons.Default.Close, contentDescription = nativeString("Close"), onClick = onBack)
        Text(stringResource(R.string.clawnova_new_agent), style = ClawTheme.type.display, color = ClawTheme.colors.text)
      }
      Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val avatar = avatarDraft
        Box(Modifier.testTag(if (avatar == null) "create-agent-default-avatar" else "create-agent-custom-avatar")) {
          if (avatar == null) ClawAgentAvatarFallback(identity = "main", size = 112.dp) else CustomBotAvatar(avatar, 112.dp)
        }
        ClawSecondaryButton(stringResource(R.string.cn_edit_bot_icon), enabled = !state.submitting, onClick = { avatarPickerOpen = true })
      }
      ClawTextField(
        value = name,
        onValueChange = onNameChange,
        placeholder = stringResource(R.string.clawnova_agent_name),
        label = stringResource(R.string.clawnova_agent_name),
        enabled = !state.submitting,
        maxLines = 1,
      )
      Text(stringResource(R.string.clawnova_agent_create_hint), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
      state.error?.let { Text(it, color = ClawTheme.colors.danger, style = ClawTheme.type.body) }
      if (!connection.isConnected) {
        Text(stringResource(R.string.clawnova_connect_create_agent), color = ClawTheme.colors.textMuted, style = ClawTheme.type.body)
        ClawSecondaryButton(nativeString("Gateway"), onClick = onOpenGateway)
      }
      ClawPrimaryButton(
        text = nativeString(if (state.submitting) "Creating…" else "Create"),
        onClick = {
          val chosenAppearance = avatarDraft
          viewModel.beginAgentCreation(name) { agent ->
            if (chosenAppearance != null) appearances.save(agent.id, chosenAppearance)
          }
        },
        enabled = name.isNotBlank() && connection.isConnected && !state.submitting,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
  if (avatarPickerOpen) {
    BotAppearanceDialog(
      initial = avatarDraft ?: BotAppearance(0, 0),
      onSave = {
        avatarDraft = it
        avatarPickerOpen = false
      },
      onDismiss = { avatarPickerOpen = false },
    )
  }
}
