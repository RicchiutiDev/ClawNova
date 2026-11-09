package ai.openclaw.app.ui

import ai.openclaw.app.BuildConfig
import ai.openclaw.app.GatewayAgentSummary
import ai.openclaw.app.GatewayChannelsSummary
import ai.openclaw.app.GatewayConnectionDisplay
import ai.openclaw.app.GatewayConnectionProblem
import ai.openclaw.app.GatewayNodesDevicesSummary
import ai.openclaw.app.GatewaySummaryState
import ai.openclaw.app.HomeDestination
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.R
import ai.openclaw.app.chat.ChatSessionEntry
import ai.openclaw.app.currentAppLanguage
import ai.openclaw.app.firstGraphemeOrNull
import ai.openclaw.app.gatewayConnectionStatusForDisplay
import ai.openclaw.app.i18n.NativeText
import ai.openclaw.app.i18n.joinedNativeText
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.i18n.nativeText
import ai.openclaw.app.i18n.resolveNativeTextResource
import ai.openclaw.app.i18n.verbatimText
import ai.openclaw.app.selectableAgents
import ai.openclaw.app.ui.design.ClawAgentAvatar
import ai.openclaw.app.ui.design.ClawAgentAvatarFallback
import ai.openclaw.app.ui.design.BotAppearanceProvider
import ai.openclaw.app.ui.design.ClawDesignTheme
import ai.openclaw.app.ui.design.ClawEmptyState
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPlainIconButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawStatus
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.agentAvatarSource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.layout.DisplayFeature
import kotlinx.coroutines.launch
import java.util.Locale

internal enum class Tab {
  Overview,
  AgentChats,
  CreateAgent,
  Chat,
  Sessions,
  Settings,
  ProvidersModels,
  Files,
  Dashboard,
}

private val overviewListRowMinHeight = 54.dp
private const val overviewRecentSessionLimit = 50

@Composable
fun ShellScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier,
  features: List<DisplayFeature> = emptyList(),
) {
  val appearanceThemeMode by viewModel.appearanceThemeMode.collectAsState()
  val appearanceThemeFamily by viewModel.appearanceThemeFamily.collectAsState()
  val appearanceAccentArgb by viewModel.appearanceAccentArgb.collectAsState()
  val gatewayAccentArgb by viewModel.gatewayAccentArgb.collectAsState()
  val shellDark = appearanceThemeMode.isDark(systemDark = isSystemInDarkTheme())
  OpenClawSystemBarAppearance(lightAppearance = !shellDark)
  val appearanceGatewayId by viewModel.activeGatewayStableId.collectAsState()
  BotAppearanceProvider(appearanceGatewayId) {
  ClawDesignTheme(dark = shellDark, family = appearanceThemeFamily, accentArgb = appearanceAccentArgb ?: gatewayAccentArgb) {
    val nav = rememberSaveable(saver = ShellNavigation.Saver) { ShellNavigation() }
    var commandOpen by rememberSaveable { mutableStateOf(false) }
    var botProfileOpen by rememberSaveable { mutableStateOf(false) }
    var newAgentName by rememberSaveable { mutableStateOf("") }
    var conversationScreenWasActive by rememberSaveable { mutableStateOf(false) }
    val pendingTrust by viewModel.pendingGatewayTrust.collectAsState()
    val gatewayAddition by viewModel.gatewayAdditionRequest.collectAsState()
    FoldAwareContent(
      features = features,
      modifier = modifier.background(ClawTheme.colors.canvas),
      sidebarPanesEnabled = !commandOpen && pendingTrust == null,
      tabletopEnabled = nav.activeTab == Tab.Chat && !commandOpen && pendingTrust == null,
    ) { foldBounds ->
      val sidebarPanes = foldBounds.sidebar
      val permanentSidebar = sidebarPanes != null
      // Mode changes discard modal operations and their drag state, not the two content slots.
      val sidebarDrawerState = key(permanentSidebar) { rememberDrawerState(initialValue = DrawerValue.Closed) }
      var sidebarRowDragging by remember(sidebarDrawerState) { mutableStateOf(false) }
      val drawerScope = key(sidebarDrawerState) { rememberCoroutineScope() }
      val openSidebar: () -> Unit = {
        if (!permanentSidebar) drawerScope.launch { sidebarDrawerState.open() }
      }
      val closeSidebar: () -> Unit = {
        if (!permanentSidebar) drawerScope.launch { sidebarDrawerState.close() }
      }
      val openChat: () -> Unit = {
        botProfileOpen = false
        nav.selectTab(Tab.Chat)
        closeSidebar()
      }
      val requestedHomeDestination by viewModel.requestedHomeDestination.collectAsState()
      val runtimeInitialized by viewModel.runtimeInitialized.collectAsState()
      val gatewayAgents by viewModel.gatewayAgents.collectAsState()
      val gatewayDefaultAgentId by viewModel.gatewayDefaultAgentId.collectAsState()
      val chatSessionOwnerAgentId by viewModel.chatSessionOwnerAgentId.collectAsState()
      val chatSessions by viewModel.chatSessions.collectAsState()
      val chatSessionKey by viewModel.chatSessionKey.collectAsState()
      val gatewayConnectionDisplay by viewModel.gatewayConnectionDisplay.collectAsState()

      LaunchedEffect(requestedHomeDestination) {
        val destination = requestedHomeDestination ?: return@LaunchedEffect
        botProfileOpen = false
        // HomeDestination is a one-shot command from launch intents and settings
        // actions; consume it after translating to local shell state.
        nav.selectTab(
          when (destination) {
            HomeDestination.Connect -> Tab.Overview
            HomeDestination.Chat, HomeDestination.Voice -> Tab.Chat
            HomeDestination.Settings -> Tab.Settings
          },
        )
        // Screenshot scenes can target a settings detail route alongside the tab.
        viewModel.requestedSettingsRoute.value?.let { route ->
          nav.openSettingsRoute(route)
          viewModel.clearRequestedSettingsRoute()
        }
        closeSidebar()
        viewModel.clearRequestedHomeDestination()
      }

      LaunchedEffect(nav.activeTab, runtimeInitialized) {
        val conversationScreenActive = nav.activeTab == Tab.Chat
        if (conversationScreenActive || conversationScreenWasActive || runtimeInitialized) {
          viewModel.setVoiceScreenActive(conversationScreenActive)
        }
        conversationScreenWasActive = conversationScreenActive
      }

      BackHandler(
        enabled =
          (
            permanentSidebar ||
              (
                sidebarDrawerState.currentValue == DrawerValue.Closed &&
                  sidebarDrawerState.targetValue == DrawerValue.Closed
              )
          ) &&
            nav.activeTab != Tab.Overview,
      ) {
        if (nav.activeTab == Tab.Chat) nav.backFromChat(chatSessionOwnerAgentId ?: gatewayDefaultAgentId) else nav.back()
      }

      BackHandler(enabled = commandOpen) {
        commandOpen = false
      }

      BackHandler(enabled = botProfileOpen && nav.activeTab == Tab.Chat) { botProfileOpen = false }

      LaunchedEffect(commandOpen, pendingTrust, sidebarDrawerState) {
        if (commandOpen || pendingTrust != null) closeSidebar()
      }

      val activeSidebarDestination =
        when (nav.activeTab) {
          Tab.Settings -> SidebarDestination.entries.firstOrNull { it.settingsRoute == nav.settingsRoute } ?: SidebarDestination.Settings
          else -> SidebarDestination.entries.firstOrNull { it.tab == nav.activeTab }
        }
      val selectSidebarDestination: (SidebarDestination) -> Unit = { destination ->
        botProfileOpen = false
        val route = destination.settingsRoute
        if (route != null) {
          nav.openSettingsRoute(route)
        } else {
          nav.selectTab(destination.tab ?: error("Missing sidebar route for $destination"))
        }
        closeSidebar()
      }

      Box(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.canvas)) {
        SidebarNavigationShell(
          drawerState = sidebarDrawerState,
          sidebarPanes = sidebarPanes,
          sidebarBand = foldBounds.sidebarBand,
          gesturesEnabled = !sidebarRowDragging,
          drawerContent = {
            OpenClawSidebar(
              viewModel = viewModel,
              agents = gatewayAgents,
              selectedAgentId = chatSessionOwnerAgentId ?: gatewayDefaultAgentId,
              sessions = chatSessions,
              activeSessionKey = chatSessionKey,
              activeDestination = activeSidebarDestination,
              connection = gatewayConnectionDisplay,
              visible =
                !commandOpen && pendingTrust == null &&
                  (permanentSidebar || sidebarDrawerState.isOpen || sidebarDrawerState.targetValue == DrawerValue.Open),
              showCloseButton = !permanentSidebar,
              onClose = closeSidebar,
              onDragActiveChange = { sidebarRowDragging = it },
              onNewSession = {
                viewModel.startNewChat(worktree = false)
                openChat()
              },
              onSelectAgent = { agentId ->
                viewModel.selectChatAgent(agentId)
                openChat()
              },
              onSelectSession = { session ->
                viewModel.switchChatSession(session.key, session.ownerAgentId)
                openChat()
              },
              onSelectCatalogSession = { session ->
                viewModel.continueSessionCatalogEntry(session) { continued ->
                  if (continued) openChat()
                }
              },
              onCreateCatalogSession = { catalogId ->
                openChat()
                viewModel.createSessionCatalogEntry(catalogId)
              },
              onSelectDestination = selectSidebarDestination,
            )
          },
        ) {
          when (nav.activeTab) {
            Tab.Overview -> {
              OverviewScreen(
                viewModel = viewModel,
                showSidebarButton = !permanentSidebar,
                onOpenSidebar = openSidebar,
                onSelectTab = nav::selectTab,
                onOpenSettingsRoute = nav::openSettingsRoute,
                onSelectAgent = nav::openAgentChats,
                onCreateAgent = {
                  viewModel.clearAgentCreationResult()
                  nav.selectTab(Tab.CreateAgent)
                },
              )
            }

            Tab.AgentChats -> {
              AgentChatsScreen(
                viewModel = viewModel,
                agentId = nav.agentChatsId ?: gatewayDefaultAgentId.orEmpty(),
                onBack = { nav.selectTab(Tab.Overview) },
                onOpenChat = openChat,
              )
            }

            Tab.CreateAgent -> {
              CreateAgentScreen(
                viewModel = viewModel,
                name = newAgentName,
                onNameChange = { newAgentName = it },
                onBack = { nav.selectTab(Tab.Overview) },
                onCreated = { agent ->
                  newAgentName = ""
                  nav.openAgentChats(agent.id)
                },
                onOpenGateway = { nav.openSettingsRoute(SettingsRoute.Gateway) },
              )
            }

            Tab.Chat -> {
              if (botProfileOpen) {
                BotProfileScreen(
                  agent = gatewayAgents.firstOrNull { it.id == (chatSessionOwnerAgentId ?: gatewayDefaultAgentId) },
                  onBack = { botProfileOpen = false },
                  onOpenSettingsRoute = {
                    nav.openSettingsRoute(it)
                  },
                  onOpenFiles = {
                    nav.openDetailTab(Tab.Files)
                  },
                )
              } else {
                UnifiedChatShellScreen(
                  viewModel = viewModel,
                  tabletopPanes = foldBounds.tabletop,
                  features = features,
                  showSidebarButton = !permanentSidebar,
                  onOpenSidebar = openSidebar,
                  onNavigateBack = {
                    nav.backFromChat(chatSessionOwnerAgentId ?: gatewayDefaultAgentId)
                  },
                  onOpenAgentProfile = { botProfileOpen = true },
                  onOpenDashboard = nav::openSessionDashboard,
                  onOpenGatewaySettings = { nav.openSettingsRoute(SettingsRoute.Gateway) },
                  onOpenProvidersModels = { nav.openDetailTab(Tab.ProvidersModels) },
                )
              }
            }

            Tab.ProvidersModels -> {
              ProvidersModelsScreen(
                viewModel = viewModel,
                onBack = nav::back,
              )
            }

            Tab.Sessions -> {
              SessionsScreen(
                viewModel = viewModel,
                showSidebarButton = !permanentSidebar,
                onOpenSidebar = openSidebar,
                onOpenChat = openChat,
              )
            }

            Tab.Files -> {
              WorkspaceFilesScreen(
                viewModel = viewModel,
                onBack = nav::back,
              )
            }

            Tab.Dashboard -> {
              SessionDashboardScreen(
                viewModel = viewModel,
                sessionKey = nav.dashboardSessionKey,
                onBack = nav::back,
              )
            }

            Tab.Settings -> {
              SettingsShellScreen(
                viewModel = viewModel,
                route = nav.settingsRoute,
                onRouteChange = nav::openSettingsRouteFromHome,
                onBack = nav::back,
                onOpenCommand = { commandOpen = true },
              )
            }
          }
        }

        if (commandOpen) {
          CommandPalette(
            viewModel = viewModel,
            onDismiss = { commandOpen = false },
            onOpen = { action ->
              when (action) {
                CommandAction.Chat, CommandAction.Voice -> {
                  openChat()
                }

                CommandAction.Sessions -> {
                  nav.openDetailTab(Tab.Sessions)
                }

                is CommandAction.Settings -> {
                  when {
                    // Keep the provider command on its standalone screen; entering
                    // Settings detail would also start the Home summary refreshes.
                    action.route == SettingsRoute.ProvidersModels -> nav.openDetailTab(Tab.ProvidersModels)

                    action.route != SettingsRoute.Home && nav.activeTab == Tab.Settings && nav.settingsRoute == SettingsRoute.Home -> nav.openSettingsRouteFromHome(action.route)

                    else -> nav.openSettingsRoute(action.route)
                  }
                }
              }
              commandOpen = false
            },
            onOpenSession = { sessionKey, ownerAgentId ->
              viewModel.switchChatSession(sessionKey, ownerAgentId)
              openChat()
              commandOpen = false
            },
          )
        }

        gatewayAddition?.let { request ->
          key(request) { GatewayAdditionDialog(viewModel, request) }
        }

        pendingTrust?.let { prompt ->
          // Gateway certificate trust is modal across the shell so navigation
          // cannot hide a changed TLS identity prompt.
          GatewayTrustDialog(
            prompt = prompt,
            confirmLabel = stringResource(R.string.trust_and_continue),
            cancelLabel = stringResource(R.string.cancel),
            onAccept = { viewModel.acceptGatewayTrustPrompt(prompt, it) },
            onUseSystemTrust = { viewModel.useSystemGatewayTrustPrompt(prompt) },
            onDecline = { viewModel.declineGatewayTrustPrompt(prompt) },
          )
        }
      }
    }
  }
}
}

@Composable
private fun OverviewScreen(
  viewModel: MainViewModel,
  showSidebarButton: Boolean,
  onOpenSidebar: () -> Unit,
  onSelectTab: (Tab) -> Unit,
  onOpenSettingsRoute: (SettingsRoute) -> Unit,
  onSelectAgent: (String) -> Unit,
  onCreateAgent: () -> Unit,
) {
  val connection by viewModel.gatewayConnectionDisplay.collectAsState()
  val agents by viewModel.gatewayAgents.collectAsState()
  val defaultAgentId by viewModel.gatewayDefaultAgentId.collectAsState()
  val displayName by viewModel.displayName.collectAsState()
  var query by rememberSaveable { mutableStateOf("") }
  SettingsRefreshOnConnect(connection.isConnected) { viewModel.refreshAgents() }
  val availableAgents = agents.selectableAgents()
  val matchingAgents =
    availableAgents.filter { agent ->
      query.isBlank() || (agent.name.orEmpty() + " " + agent.id).contains(query.trim(), ignoreCase = true)
    }
  ClawScaffold(
    contentPadding = PaddingValues(horizontal = ClawTheme.spacing.xs, vertical = ClawTheme.spacing.xxs),
    contentWindowInsets = WindowInsets.safeDrawing,
  ) {
    Column(modifier = Modifier.fillMaxSize().testTag("agents-home")) {
      HomeHeader(displayName = displayName, onOpenProfile = { onSelectTab(Tab.Settings) }, onCreateAgent = onCreateAgent)
      ManagedAgentList(viewModel, availableAgents, matchingAgents, defaultAgentId, onSelectAgent, onCreateAgent, Modifier.weight(1f))
      HomeSearchDock(
        showSidebarButton = showSidebarButton,
        status = overviewHeaderState(connection.isConnected, hasAttention = false),
        onOpenSidebar = onOpenSidebar,
        onOpenGateway = { onOpenSettingsRoute(SettingsRoute.Gateway) },
        query = query,
        onQueryChange = { query = it },
      )
    }
  }
}

@Composable
private fun HomeHeader(
  displayName: String,
  onOpenProfile: () -> Unit,
  onCreateAgent: () -> Unit,
) {
  val profileDescription = nativeString("Open profile")
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onOpenProfile),
      contentAlignment = Alignment.Center,
    ) {
      Surface(shape = CircleShape, color = ClawTheme.colors.surfaceRaised) {
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
          Text(
            text = localizedInitial(displayName, currentAppLanguage().languageTag) ?: "C",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.text,
            modifier = Modifier.semantics { contentDescription = profileDescription },
          )
        }
      }
    }
    Text(nativeString("Agents"), style = ClawTheme.type.title, color = ClawTheme.colors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
    HomeRoundAction(icon = Icons.Default.Add, description = stringResource(R.string.clawnova_new_agent), onClick = onCreateAgent)
  }
}

@Composable
private fun HomeRoundAction(
  icon: ImageVector,
  description: String,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier
        .size(48.dp)
        .clip(CircleShape)
        .clickable(onClick = onClick)
        .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(ClawTheme.colors.surfaceRaised), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = ClawTheme.colors.text)
    }
  }
}

@Composable
internal fun HomeBotHero(
  agent: GatewayAgentSummary?,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier =
        Modifier
          .testTag("home-primary-bot")
          .widthIn(max = 200.dp)
          .clip(RoundedCornerShape(16.dp))
          .clickable(onClick = onClick)
          .padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ClawAgentAvatar(source = agent?.let(::agentAvatarSource), size = 84.dp, identity = agent?.id) {
        ClawAgentAvatarFallback(identity = agent?.id ?: "main", size = 84.dp)
      }
      Text(
        text = agent?.name?.takeIf { it.isNotBlank() } ?: agent?.id ?: stringResource(R.string.clawnova_app_name),
        style = ClawTheme.type.caption,
        color = ClawTheme.colors.text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
internal fun HomeBotRow(
  agent: GatewayAgentSummary,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    color = Color.Transparent,
    shape = RoundedCornerShape(12.dp),
  ) {
    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      ClawAgentAvatar(source = agentAvatarSource(agent), size = 48.dp, identity = agent.id) { ClawAgentAvatarFallback(identity = agent.id, size = 48.dp) }
      Text(agent.name?.takeIf(String::isNotBlank) ?: agent.id, style = ClawTheme.type.title, color = ClawTheme.colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = ClawTheme.colors.textSubtle)
    }
  }
}

@Composable
private fun HomeSearchDock(
  showSidebarButton: Boolean,
  status: OverviewHeaderState,
  onOpenSidebar: () -> Unit,
  onOpenGateway: () -> Unit,
  query: String,
  onQueryChange: (String) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (showSidebarButton) HomeRoundAction(icon = Icons.Default.GridView, description = nativeString("Show Sidebar"), onClick = onOpenSidebar)
    val searchLabel = nativeString("Search")
    Surface(modifier = Modifier.weight(1f), color = ClawTheme.colors.surfaceRaised, shape = CircleShape) {
      BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = ClawTheme.type.label.copy(color = ClawTheme.colors.text),
        cursorBrush = SolidColor(ClawTheme.colors.accent),
        modifier = Modifier.semantics { contentDescription = searchLabel },
        decorationBox = { editor ->
          Row(modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = ClawTheme.colors.textMuted)
            Box(modifier = Modifier.weight(1f)) {
              if (query.isEmpty()) Text(searchLabel, style = ClawTheme.type.label, color = ClawTheme.colors.textMuted)
              editor()
            }
          }
        },
      )
    }
    Box(
      modifier =
        Modifier
          .heightIn(min = 48.dp)
          .widthIn(min = 48.dp)
          .clip(CircleShape)
          .clickable(onClick = onOpenGateway)
          .padding(horizontal = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(nativeString(status.label), style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    }
  }
}

@Composable
private fun HomeReconnectRow(onClick: () -> Unit) {
  ClawSecondaryButton(text = nativeString("Reconnect gateway"), icon = SettingsRoute.Gateway.icon, onClick = onClick, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun HomeSessionRow(
  row: RecentSessionListItem,
  agent: GatewayAgentSummary?,
  onClick: () -> Unit,
) {
  val identity = agent?.id ?: row.ownerAgentId ?: row.key
  Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp), color = Color.Transparent, shape = RoundedCornerShape(8.dp)) {
    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      ClawAgentAvatar(source = agent?.let(::agentAvatarSource), size = 40.dp, identity = agent?.id ?: row.ownerAgentId) {
        ClawAgentAvatarFallback(identity = identity, size = 40.dp)
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(row.title, style = ClawTheme.type.section, color = ClawTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
          Text(row.metadata, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textSubtle, maxLines = 1)
        }
        Text(row.source, style = ClawTheme.type.caption, color = ClawTheme.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

internal fun localizedUppercase(
  value: String,
  languageTag: String?,
  fallbackLocale: Locale = Locale.getDefault(),
): String = value.uppercase(languageTag?.let(Locale::forLanguageTag) ?: fallbackLocale)

internal fun localizedInitial(
  value: String,
  languageTag: String?,
  fallbackLocale: Locale = Locale.getDefault(),
): String? = value.firstGraphemeOrNull()?.let { localizedUppercase(it, languageTag, fallbackLocale) }

@Composable
internal fun UppercaseSectionLabel(
  title: String,
  verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
  horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
  action: @Composable () -> Unit = {},
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = verticalAlignment,
    horizontalArrangement = horizontalArrangement,
  ) {
    Text(
      text = localizedUppercase(title, currentAppLanguage().languageTag),
      style = ClawTheme.type.caption,
      color = ClawTheme.colors.textMuted,
    )
    action()
  }
}

internal data class OverviewHeaderState(
  val label: String,
  val status: ClawStatus,
)

internal fun overviewHeaderState(
  isConnected: Boolean,
  hasAttention: Boolean,
): OverviewHeaderState =
  when {
    !isConnected -> OverviewHeaderState("Offline", ClawStatus.Neutral)
    hasAttention -> OverviewHeaderState("Needs attention", ClawStatus.Warning)
    else -> OverviewHeaderState("Online", ClawStatus.Success)
  }

internal fun overviewHeaderRoute(attentionRows: List<HomeAttentionRow>): SettingsRoute = attentionRows.firstOrNull()?.settingsRoute ?: SettingsRoute.Gateway

internal fun overviewRecentSessions(sessions: List<ChatSessionEntry>): List<ChatSessionEntry> =
  sessions
    .sortedWith(compareByDescending<ChatSessionEntry> { it.lastActivityAt ?: it.updatedAtMs ?: Long.MIN_VALUE }.thenBy { it.key })
    .distinctBy(ChatSessionEntry::key)
    .take(overviewRecentSessionLimit)

private val sessionSourceLabels =
  mapOf(
    "cron" to "Cron",
    "discord" to "Discord",
    "guildchat" to "Guildchat",
    "imessage" to "iMessage",
    "matrix" to "Matrix",
    "slack" to "Slack",
    "telegram" to "Telegram",
    "whatsapp" to "WhatsApp",
    "workspace" to "Workspace",
  )

internal fun sessionSourceLabel(
  sessionKey: String,
  channelsSummary: GatewayChannelsSummary? = null,
): String {
  val normalized = sessionKey.trim()
  val scopedKey =
    if (normalized.startsWith("agent:", ignoreCase = true)) {
      normalized.substringAfter(':', missingDelimiterValue = "").substringAfter(':', missingDelimiterValue = "")
    } else {
      normalized
    }
  if (!scopedKey.contains(':') && !scopedKey.contains('#')) return nativeString("OpenClaw")
  val source = scopedKey.substringBefore(':').substringBefore('#').lowercase()
  val channelLabel =
    channelsSummary
      ?.channels
      ?.firstOrNull { channel ->
        channel.id.equals(source, ignoreCase = true)
      }?.label
      ?.takeIf { it.isNotBlank() }
  if (channelLabel != null) return channelLabel
  return nativeString(sessionSourceLabels[source] ?: "OpenClaw")
}

internal data class HomeAttentionRow(
  val title: String,
  val subtitle: String,
  val settingsRoute: SettingsRoute,
)

internal fun homeAttentionRows(
  isConnected: Boolean,
  pendingApprovals: Int,
  channelsSummary: GatewayChannelsSummary?,
  nodesDevicesSummary: GatewayNodesDevicesSummary,
  readyProviderCount: Int,
  unknownProviderCount: Int = 0,
): List<HomeAttentionRow> =
  buildList {
    if (!isConnected) {
      add(
        HomeAttentionRow(
          nativeString("Gateway"),
          nativeString("Connect before chat, voice, and live status."),
          SettingsRoute.Gateway,
        ),
      )
    }
    if (pendingApprovals > 0) {
      add(HomeAttentionRow(nativeString("Approvals"), approvalsSummary(pendingApprovals), SettingsRoute.Approvals))
    }
    if (channelsSummary?.channels?.any { it.error != null } == true) {
      add(HomeAttentionRow(nativeString("Channels"), channelsSummaryText(channelsSummary), SettingsRoute.Channels))
    }
    if (nodesDevicesSummary.pendingDevices.isNotEmpty() || nodesDevicesSummary.hasNodeCapabilityApprovalPending()) {
      add(HomeAttentionRow(nativeString("Nodes & Devices"), nodesDevicesSummaryText(nodesDevicesSummary), SettingsRoute.NodesDevices))
    }
    if (isConnected && readyProviderCount == 0 && unknownProviderCount == 0) {
      add(HomeAttentionRow(nativeString("Providers"), nativeString("No ready providers"), SettingsRoute.ProvidersModels))
    }
  }

@Composable
private fun HomeAttentionPanel(
  rows: List<HomeAttentionRow>,
  onOpenSettingsRoute: (SettingsRoute) -> Unit,
) {
  ClawPanel(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(text = nativeString("Needs attention"), style = ClawTheme.type.caption, color = ClawTheme.colors.warning)
      rows.forEach { row ->
        HomeAttentionListRow(
          row = row,
          onClick = { onOpenSettingsRoute(row.settingsRoute) },
        )
      }
    }
  }
}

@Composable
private fun HomeAttentionListRow(
  row: HomeAttentionRow,
  onClick: () -> Unit,
) {
  val localizedTitle = nativeString(row.title)
  Surface(color = Color.Transparent, contentColor = ClawTheme.colors.text) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = 54.dp)
          .clip(RoundedCornerShape(ClawTheme.radii.row))
          .clickable(onClick = onClick)
          .padding(horizontal = 0.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Icon(imageVector = row.settingsRoute.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = ClawTheme.colors.text)
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = localizedTitle,
          style = ClawTheme.type.body,
          color = ClawTheme.colors.text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(text = nativeString(row.subtitle), style = ClawTheme.type.caption, color = ClawTheme.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = settingsRowDisclosureDescription(localizedTitle, opensRoute = true),
        modifier = Modifier.size(17.dp),
        tint = ClawTheme.colors.textMuted,
      )
    }
  }
}

internal data class RecentSessionListItem(
  val key: String,
  val title: String,
  val source: String,
  val metadata: String,
  val ownerAgentId: String? = null,
)

internal fun overviewRecentSessionRows(
  sessions: List<ChatSessionEntry>,
  channelsSummary: GatewayChannelsSummary?,
): List<RecentSessionListItem> =
  sessions
    .map { session ->
      val title = sessionPresentationTitle(session) { nativeString("Main thread") }
      RecentSessionListItem(
        key = session.key,
        ownerAgentId = session.ownerAgentId,
        title = title,
        source = sessionListSubtitle(session, sessionSourceLabel(session.key, channelsSummary).let { if (it == nativeString("OpenClaw")) nativeString("Chat") else it }),
        metadata = (session.lastActivityAt ?: session.updatedAtMs)?.let(::relativeSessionTime) ?: "",
      )
    }

internal fun stableOverviewRecentRows(
  previousRows: List<RecentSessionListItem>,
  candidateRows: List<RecentSessionListItem>,
): List<RecentSessionListItem> {
  val previousRowsByKey = previousRows.associateBy { row -> row.key }
  return candidateRows.map { candidateRow ->
    val previousRow = previousRowsByKey[candidateRow.key]
    if (previousRow == null) {
      candidateRow
    } else {
      candidateRow.copy(
        title = candidateRow.title.ifBlank { previousRow.title },
        source = candidateRow.source.ifBlank { previousRow.source },
        metadata = candidateRow.metadata.ifBlank { previousRow.metadata },
      )
    }
  }
}

@Composable
private fun SettingsShellScreen(
  viewModel: MainViewModel,
  route: SettingsRoute,
  onRouteChange: (SettingsRoute) -> Unit,
  onBack: () -> Unit,
  onOpenCommand: () -> Unit,
) {
  // Detail destinations own their own refreshes; opening an operational page must
  // not start all of the Settings Home subscriptions and requests.
  if (route != SettingsRoute.Home) {
    SettingsDetailScreen(viewModel = viewModel, route = route, onBack = onBack)
    return
  }
  val displayName by viewModel.displayName.collectAsState()
  val gatewayConnectionDisplay by viewModel.gatewayConnectionDisplay.collectAsState()
  val isConnected = gatewayConnectionDisplay.isConnected
  val models by viewModel.providerModelCatalog.collectAsState()
  val providers by viewModel.modelAuthProviders.collectAsState()
  val notificationForwardingEnabled by viewModel.notificationForwardingEnabled.collectAsState()
  val speakerEnabled by viewModel.speakerEnabled.collectAsState()
  val approvalInbox by viewModel.execApprovalInbox.collectAsState()
  val pendingToolCalls by viewModel.chatPendingToolCalls.collectAsState()
  val nodesDevicesSummary by viewModel.nodesDevicesSummary.collectAsState()
  val channelsState by viewModel.channelsState.collectAsState()
  val appearanceThemeMode by viewModel.appearanceThemeMode.collectAsState()
  val providerRows = providerRows(providers = providers, models = models)
  val readyProviderCount = providerRows.count { it.ready }
  val unknownProviderCount = providerRows.count { it.availability == ProviderAvailability.Unknown }
  val pendingApprovalsCount = approvalInbox.approvals.size + pendingToolCalls.size
  val appLanguage = currentAppLanguage()

  SettingsRefreshOnConnect(isConnected) {
    viewModel.refreshModelCatalog()
    viewModel.refreshProviderModels()
    viewModel.refreshNodesDevices()
    viewModel.refreshChannels()
    viewModel.refreshExecApprovals()
  }

  val settingsRows =
    listOf(
      SettingsRow(
        SettingsRoute.Appearance,
        joinedNativeText(
          separator = " · ",
          parts = listOf(verbatimText(appearanceThemeSummary(appearanceThemeMode)), verbatimText(appLanguage.displayName)),
        ),
      ),
      SettingsRow(SettingsRoute.Voice, if (speakerEnabled) nativeText("Speaker on") else nativeText("Speaker muted")),
      SettingsRow(SettingsRoute.Notifications, if (notificationForwardingEnabled) nativeText("Smart delivery") else nativeText("Off")),
      SettingsRow(SettingsRoute.PhoneCapabilities, nativeText("Permissions and shared capabilities")),
      SettingsRow(SettingsRoute.Gateway, verbatimText(gatewaySummary(gatewayConnectionDisplay)), needsAttention = !isConnected),
      SettingsRow(
        SettingsRoute.NodesDevices,
        if (isConnected) verbatimText(nodesDevicesSummaryText(nodesDevicesSummary)) else nativeText("Connect to manage devices"),
        needsAttention = isConnected && (nodesDevicesSummary.pendingDevices.isNotEmpty() || nodesDevicesSummary.hasNodeCapabilityApprovalPending()),
      ),
      SettingsRow(
        SettingsRoute.Channels,
        if (isConnected) channelsState.summaryText(::channelsSummaryText) else nativeText("Connect to manage channels"),
        needsAttention = isConnected && (channelsState.errorText != null || channelsState.summary?.channels?.any { it.error != null } == true),
      ),
      SettingsRow(
        SettingsRoute.ProvidersModels,
        when {
          !isConnected -> nativeText("Connect to manage providers")
          readyProviderCount > 0 -> nativeText("\$readyProviderCount ready", readyProviderCount)
          unknownProviderCount > 0 -> nativeText("Availability unknown")
          else -> nativeText("Review readiness")
        },
        needsAttention = isConnected && readyProviderCount == 0 && unknownProviderCount == 0,
      ),
      SettingsRow(
        SettingsRoute.Approvals,
        if (isConnected) verbatimText(approvalsSummary(pendingApprovalsCount)) else nativeText("Connect to review approvals"),
        needsAttention = isConnected && pendingApprovalsCount > 0,
      ),
      SettingsRow(SettingsRoute.Health, nativeText("Diagnostics and logs")),
      SettingsRow(SettingsRoute.About, nativeText("Version and update")),
    )

  ClawScaffold(
    contentPadding = PaddingValues(horizontal = ClawTheme.spacing.sm, vertical = ClawTheme.spacing.xxs),
    contentWindowInsets = WindowInsets.safeDrawing,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = ClawTheme.spacing.lg),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs),
        ) {
          HomeRoundAction(icon = Icons.Default.Close, description = nativeString("Close"), onClick = onBack)
          Text(text = nativeString("Settings"), style = ClawTheme.type.display, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
          ClawPlainIconButton(icon = Icons.Default.Search, contentDescription = nativeString("Search settings"), onClick = onOpenCommand)
        }
      }
      item {
        Surface(color = ClawTheme.colors.surfaceRaised, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)) {
          SettingsListRow(
            title = verbatimText(displayName.ifBlank { "OpenClaw" }),
            value = nativeText("Device name and identity"),
            actionDescription = nativeString("Open profile"),
            onClick = { onRouteChange(SettingsRoute.Profile) },
          )
        }
      }
      item {
        Surface(color = ClawTheme.colors.surfaceRaised, shape = RoundedCornerShape(16.dp)) {
          SettingsListRow(
            title = SettingsRoute.SystemAgent.title,
            value = nativeText("Setup, status, and repair"),
            onClick = { onRouteChange(SettingsRoute.SystemAgent) },
          )
        }
      }
      settingsSections(settingsRows).forEach { section ->
        item { SettingsSectionTitle(section.title) }
        item {
          Surface(color = ClawTheme.colors.surfaceRaised, shape = RoundedCornerShape(16.dp)) {
            Column {
              section.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = ClawTheme.colors.border, modifier = Modifier.padding(horizontal = 14.dp))
                SettingsListRow(
                  title = row.route.title,
                  value = row.value,
                  needsAttention = row.needsAttention,
                  onClick = { onRouteChange(row.route) },
                )
              }
            }
          }
        }
      }
      item { SettingsSectionTitle(nativeText("Account")) }
      item {
        SettingsListRow(
          title = nativeText("Sign Out"),
          value = nativeText("Return to setup"),
          opensRoute = false,
          onClick = viewModel::returnToGatewaySetup,
        )
      }
      item { SettingsSectionTitle(nativeText("Licenses")) }
      item {
        SettingsListRow(
          title = SettingsRoute.Licenses.title,
          value = verbatimText(""),
          onClick = { onRouteChange(SettingsRoute.Licenses) },
        )
      }
      item {
        Text(
          modifier = Modifier.fillMaxWidth().padding(top = ClawTheme.spacing.lg),
          text = stringResource(R.string.clawnova_app_name) + "\n" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textMuted,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

private fun approvalsSummary(count: Int): String =
  when (count) {
    0 -> nativeString("No pending approvals")
    1 -> nativeString("1 pending")
    else -> nativeString("\$count pending", count)
  }

private fun cronJobsSummary(count: Int): String =
  when (count) {
    0 -> nativeString("No scheduled jobs")
    1 -> nativeString("1 scheduled")
    else -> nativeString("\$count scheduled", count)
  }

private fun <T> GatewaySummaryState<T>.summaryText(format: (T) -> String): NativeText =
  errorText ?: summary?.let { verbatimText(format(it)) }
    ?: if (refreshing) nativeText("Refreshing") else nativeText("Load from gateway")

private fun nodesDevicesSummaryText(summary: GatewayNodesDevicesSummary): String {
  val online = summary.nodes.count { it.connected }
  val devices = summary.pairedDevices.size
  return when {
    summary.pendingDevices.isNotEmpty() -> nativeString("\${summary.pendingDevices.size} pending", summary.pendingDevices.size)
    summary.hasNodeCapabilityApprovalPending() -> nativeString("Node approval pending")
    summary.nodes.isNotEmpty() -> nativeString("\$online/\${summary.nodes.size} online", online, summary.nodes.size)
    devices > 0 -> nativeString("\$devices paired", devices)
    else -> nativeString("No devices")
  }
}

private fun GatewayNodesDevicesSummary.hasNodeCapabilityApprovalPending(): Boolean = nodes.any { node -> nodeCapabilityApprovalNeedsUserAction(node.approvalState) }

internal fun channelsSummaryText(summary: GatewayChannelsSummary): String {
  val connected = summary.channels.count { it.connected }
  val issueCount = summary.channels.count { it.error != null }
  return when {
    issueCount == 1 -> nativeString("1 issue")
    issueCount > 1 -> nativeString("\$issueCount issues", issueCount)
    summary.channels.isNotEmpty() -> nativeString("\$connected/\${summary.channels.size} connected", connected, summary.channels.size)
    else -> nativeString("No channels")
  }
}

internal data class SettingsRow(
  val route: SettingsRoute,
  val value: NativeText,
  val needsAttention: Boolean = false,
)

internal data class SettingsSection(
  val title: NativeText,
  val rows: List<SettingsRow>,
)

internal fun settingsSections(rows: List<SettingsRow>): List<SettingsSection> =
  SettingsCategory.entries.filter { it != SettingsCategory.Workspace }.mapNotNull { category ->
    val sectionRows = rows.filter { row -> row.route.category == category }
    if (sectionRows.isEmpty()) null else SettingsSection(title = category.title, rows = sectionRows)
  }

@Composable
private fun SettingsSectionTitle(title: NativeText) {
  Text(
    text = title.resolveNativeTextResource(),
    modifier = Modifier.padding(top = ClawTheme.spacing.lg, bottom = ClawTheme.spacing.xxs, start = 4.dp),
    style = ClawTheme.type.label,
    color = ClawTheme.colors.textMuted,
  )
}

@Composable
private fun SettingsListRow(
  title: NativeText,
  value: NativeText,
  needsAttention: Boolean = false,
  opensRoute: Boolean = true,
  actionDescription: String? = null,
  onClick: () -> Unit,
) {
  val localizedTitle = title.resolveNativeTextResource()
  val subtitle = value.resolveNativeTextResource().takeIf { it.isNotBlank() }
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = if (subtitle == null) 52.dp else 64.dp)
        .clip(RoundedCornerShape(ClawTheme.radii.row))
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.sm),
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(text = localizedTitle, style = ClawTheme.type.body.copy(fontSize = 16.sp, lineHeight = 22.sp), color = ClawTheme.colors.text)
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = ClawTheme.type.caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
          color = if (needsAttention) ClawTheme.colors.warning else ClawTheme.colors.textMuted,
        )
      }
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = actionDescription ?: settingsRowDisclosureDescription(localizedTitle, opensRoute = opensRoute),
      modifier = Modifier.size(18.dp),
      tint = ClawTheme.colors.textSubtle,
    )
  }
}

internal fun settingsRowDisclosureDescription(
  localizedTitle: String,
  opensRoute: Boolean,
): String = if (opensRoute) nativeString("Open \${row.title}", localizedTitle) else localizedTitle

internal fun gatewaySummary(
  statusText: String,
  isConnected: Boolean,
  gatewayConnectionProblem: GatewayConnectionProblem? = null,
): String {
  if (isConnected) return if (statusText == "Connected (node offline)") gatewayConnectionStatusForDisplay(statusText) else nativeString("Online and ready")
  val status = statusText.trim().lowercase()
  return when {
    status.contains("connecting") -> nativeString("Connecting...")
    status.contains("pairing") -> nativeString("Waiting for pairing")
    status.contains("auth") || status.contains("device identity") -> gatewayAuthRecoveryLabel(gatewayConnectionProblem) ?: nativeString("Authentication needed")
    status.contains("fingerprint verification timed out") -> nativeString("TLS timed out")
    status.contains("no tls endpoint") -> nativeString("No TLS endpoint")
    status.contains("certificate") || status.contains("tls") -> nativeString("Certificate review needed")
    else -> nativeString("Not connected")
  }
}

internal fun gatewaySummary(display: GatewayConnectionDisplay): String = gatewaySummary(display.statusText, display.isConnected, display.problem)
