package ai.openclaw.app.ui

import ai.openclaw.app.GatewayAgentSummary
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.i18n.resolveNativeText
import ai.openclaw.app.ui.design.ClawAgentAvatar
import ai.openclaw.app.ui.design.ClawAgentAvatarFallback
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.agentAvatarSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun BotProfileScreen(
  agent: GatewayAgentSummary?,
  onBack: () -> Unit,
  onOpenSettingsRoute: (SettingsRoute) -> Unit,
  onOpenFiles: () -> Unit,
) {
  ClawScaffold(contentWindowInsets = WindowInsets.safeDrawing) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = ClawTheme.spacing.lg),
      verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.lg),
    ) {
      item {
        Box(modifier = Modifier.fillMaxWidth()) {
          ClawIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = nativeString("Back"),
            onClick = onBack,
          )
        }
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = ClawTheme.spacing.xs, bottom = ClawTheme.spacing.sm),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xs),
        ) {
          ClawAgentAvatar(source = agent?.let(::agentAvatarSource), size = 88.dp, identity = agent?.id) {
            ClawAgentAvatarFallback(identity = agent?.id ?: "main", size = 88.dp)
          }
          Text(
            text = agent?.name?.takeIf { it.isNotBlank() } ?: agent?.id ?: nativeString("Profile"),
            style = ClawTheme.type.title,
            color = ClawTheme.colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      item {
        BotProfileGroup {
          BotProfileRouteRow(SettingsRoute.CronJobs, onOpenSettingsRoute)
          BotProfileDivider()
          BotProfileRow(title = nativeString("Files"), icon = Icons.Outlined.Folder, onClick = onOpenFiles)
          BotProfileDivider()
          BotProfileRouteRow(SettingsRoute.Desktop, onOpenSettingsRoute)
        }
      }
      item {
        BotProfileGroup {
          BotProfileRouteRow(SettingsRoute.Notifications, onOpenSettingsRoute)
          BotProfileDivider()
          BotProfileRouteRow(SettingsRoute.Voice, onOpenSettingsRoute)
          BotProfileDivider()
          BotProfileRouteRow(SettingsRoute.ProvidersModels, onOpenSettingsRoute)
        }
      }
      item {
        BotProfileGroup {
          BotProfileRouteRow(SettingsRoute.Agents, onOpenSettingsRoute)
        }
      }
    }
  }
}

@Composable
private fun BotProfileGroup(content: @Composable ColumnScope.() -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(ClawTheme.radii.panel),
    color = ClawTheme.colors.surface,
    contentColor = ClawTheme.colors.text,
  ) {
    Column(content = content)
  }
}

@Composable
private fun BotProfileDivider() {
  HorizontalDivider(modifier = Modifier.padding(start = 48.dp), color = ClawTheme.colors.border)
}

@Composable
private fun BotProfileRouteRow(
  route: SettingsRoute,
  onOpenSettingsRoute: (SettingsRoute) -> Unit,
) {
  BotProfileRow(title = route.title.resolveNativeText(), icon = route.icon, onClick = { onOpenSettingsRoute(route) })
}

@Composable
private fun BotProfileRow(
  title: String,
  icon: ImageVector,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    color = Color.Transparent,
    contentColor = ClawTheme.colors.text,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = ClawTheme.spacing.sm, vertical = ClawTheme.spacing.xs),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xs),
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = ClawTheme.colors.textMuted)
      Text(
        text = title,
        modifier = Modifier.weight(1f),
        style = ClawTheme.type.body,
        color = ClawTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = ClawTheme.colors.textSubtle)
    }
  }
}
