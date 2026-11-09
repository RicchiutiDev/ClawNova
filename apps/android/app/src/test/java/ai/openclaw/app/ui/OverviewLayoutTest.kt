package ai.openclaw.app.ui

import ai.openclaw.app.AndroidScreenshotFixture
import ai.openclaw.app.AndroidScreenshotScene
import ai.openclaw.app.AppearanceThemeMode
import ai.openclaw.app.GatewayNodeCapabilityApproval
import ai.openclaw.app.GatewayNodesDevicesSummary
import ai.openclaw.app.GatewayPairedDeviceSummary
import ai.openclaw.app.GatewayPendingDeviceSummary
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.NodeApp
import ai.openclaw.app.NodeRuntime
import ai.openclaw.app.NodeRuntimeMode
import ai.openclaw.app.SecurePrefs
import ai.openclaw.app.bindNodeRuntimeTestFixture
import ai.openclaw.app.closeNodeRuntimeTestFixture
import ai.openclaw.app.ui.design.clawColorsForTheme
import ai.openclaw.app.ui.design.renderedLabelContrast
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.UUID

/** Exercises the real shell -> Overview boundary with an isolated, in-memory runtime. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h800dp-mdpi", instrumentedPackages = ["ai.openclaw.app.AndroidScreenshotFixture"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverviewLayoutTest {
  private val composeRule = createComposeRule()
  private val models = ViewModelStore()
  private lateinit var app: NodeApp
  private lateinit var prefs: SecurePrefs
  private lateinit var runtime: NodeRuntime
  private lateinit var model: MainViewModel
  private var previousRuntime: NodeRuntime? = null
  private var restoreAnimatorScale: (() -> Unit)? = null
  private val evidence by lazy {
    File("build/outputs/overview-layout", UUID.randomUUID().toString()).also { check(it.mkdirs()) }
  }

  @get:Rule
  val fixtureRules: RuleChain =
    RuleChain
      .outerRule(
        object : ExternalResource() {
          override fun after() {
            try {
              models.clear()
            } finally {
              try {
                if (::runtime.isInitialized) closeNodeRuntimeTestFixture(runtime)
              } finally {
                try {
                  if (::app.isInitialized) bindNodeRuntimeTestFixture(app, previousRuntime)
                } finally {
                  AndroidScreenshotFixture.configure(AndroidScreenshotScene.Home)
                  restoreAnimatorScale?.invoke()
                }
              }
            }
          }
        },
      ).around(composeRule)

  @Before
  fun setUp() {
    app = RuntimeEnvironment.getApplication() as NodeApp
    previousRuntime = app.peekRuntime()
    val resolver = app.contentResolver
    val originalScale = Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE)
    restoreAnimatorScale = { Settings.Global.putString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, originalScale) }
    Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    prefs = SecurePrefs(app, app.getSharedPreferences("overview-layout-" + UUID.randomUUID(), Context.MODE_PRIVATE))
    AndroidScreenshotFixture.configure(AndroidScreenshotScene.Home)
    runtime = NodeRuntime(app, prefs, NodeRuntimeMode.ScreenshotFixture)
    bindNodeRuntimeTestFixture(app, runtime)
    model = MainViewModel(app, prefs, SavedStateHandle())
    models.put("overview", model)
    model.enterScreenshotFixtureMode(AndroidScreenshotScene.Home)
  }

  @Test
  fun homeShowsAgentsAndSearchWithoutTheirConversations() {
    composeRule.setContent { ShellScreen(model) }
    composeRule.waitUntil { model.gatewayAgents.value.isNotEmpty() }
    composeRule.onNodeWithTag("home-primary-bot").assertIsDisplayed().assertHasClickAction()
    composeRule.onNodeWithText("Online").assertIsDisplayed().assertHasClickAction()
    composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    composeRule.onNode(hasText(AndroidScreenshotFixture.primarySessionTitle) and hasAnyAncestor(hasTestTag("agents-home"))).assertDoesNotExist()
    capture("home")
    composeRule.onNodeWithContentDescription("Open profile").performClick()
    composeRule.onNodeWithContentDescription("Search settings").assertIsDisplayed()
  }

  @Test
  fun nodesAndDevicesSettingsShowPairingCountsWithoutListedNodes() {
    val pending =
      (1..2).map { index ->
        GatewayPendingDeviceSummary(
          requestId = "request-$index",
          deviceId = "pending-device-$index",
          displayName = "Pending phone $index",
          remoteIp = null,
          roles = listOf("node"),
          scopes = emptyList(),
          requestedAtMs = null,
          repair = false,
        )
      }
    val paired =
      listOf(
        GatewayPairedDeviceSummary(
          deviceId = "paired-device",
          displayName = "Paired phone",
          remoteIp = null,
          roles = listOf("node"),
          scopes = emptyList(),
          tokens = emptyList(),
          approvedAtMs = null,
        ),
      )
    val cases =
      listOf(
        GatewayNodesDevicesSummary(emptyList(), pending, emptyList()),
        GatewayNodesDevicesSummary(emptyList(), emptyList(), paired),
        GatewayNodesDevicesSummary(emptyList(), pending, paired),
        GatewayNodesDevicesSummary(emptyList(), emptyList(), emptyList()),
      )
    composeRule.setContent { ShellScreen(model) }
    composeRule.onNodeWithContentDescription("Show Sidebar").performClick()
    composeRule.onNodeWithContentDescription("Settings").performClick()
    composeRule.onNodeWithText("Nodes & Devices").performScrollTo().performClick()
    val failures = mutableListOf<String>()
    for ((index, summary) in cases.withIndex()) {
      composeRule.runOnIdle {
        ReflectionHelpers.getField<MutableStateFlow<GatewayNodesDevicesSummary>>(runtime, "_nodesDevicesSummary").value = summary
      }
      composeRule.onNodeWithText("Devices").assertIsDisplayed()
      composeRule.onNodeWithText("Pending").assertIsDisplayed()
      capture("pairing-without-nodes-$index")
      val expectedDeviceCount = summary.pairedDevices.size.toString()
      val expectedPendingCount = summary.pendingDevices.size.toString()
      val deviceCountVisible = composeRule.onAllNodesWithText(expectedDeviceCount).fetchSemanticsNodes().isNotEmpty()
      val pendingCountVisible = composeRule.onAllNodesWithText(expectedPendingCount).fetchSemanticsNodes().isNotEmpty()
      if (!deviceCountVisible || !pendingCountVisible) {
        failures += "Expected $expectedDeviceCount paired and $expectedPendingCount pending devices"
      }
    }
    assertTrue(failures.joinToString("\n"), failures.isEmpty())
  }

  @Test
  fun homeQuickActionsUseThemeSurfacesInDarkAndLightModes() {
    composeRule.setContent { ShellScreen(model) }
    val paints = mutableListOf<Pair<Int, Int>>()
    for (dark in listOf(true, false)) {
      composeRule.runOnIdle { prefs.setAppearanceThemeMode(if (dark) AppearanceThemeMode.Dark else AppearanceThemeMode.Light) }
      val newChat =
        composeRule
          .onNodeWithContentDescription("New agent")
          .assertIsDisplayed()
          .assertHasClickAction()
      capture(if (dark) "home-actions-dark" else "home-actions-light")
      val pixels = newChat.captureToImage().toPixelMap()
      paints += clawColorsForTheme(dark = dark, accentArgb = null).surfaceRaised.toArgb() to pixels[8, pixels.height / 2].toArgb()
    }
    paints.forEach { (expected, actual) ->
      assertEquals("Home quick actions must paint the shared theme surface", expected, actual)
    }
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp-mdpi")
  fun homeKeepsItsHeaderAndQuickActionAtWidePhoneWidth() {
    composeRule.setContent { ShellScreen(model) }
    composeRule.waitUntil { model.chatSessions.value.isNotEmpty() }

    for (dark in listOf(false, true)) {
      composeRule.runOnIdle { prefs.setAppearanceThemeMode(if (dark) AppearanceThemeMode.Dark else AppearanceThemeMode.Light) }
      composeRule
        .onNodeWithContentDescription("New agent")
        .assertIsDisplayed()
        .assertHasClickAction()
      capture(if (dark) "home-large-dark" else "home-large-light")
    }
  }

  @Test
  fun homePlusOpensRealAgentCreationFormInsteadOfStartingChat() {
    composeRule.setContent { ShellScreen(model) }
    composeRule.onNodeWithContentDescription("New agent").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("create-agent").assertIsDisplayed()
    composeRule.onNodeWithText("Create").assertIsNotEnabled()
    composeRule.onNode(hasSetTextAction()).performTextReplacement("Research assistant")
    composeRule.onNodeWithText("Create").assertIsEnabled()
    capture("create-agent")
    composeRule.onNodeWithContentDescription("Close").performClick()
    composeRule.onNodeWithTag("agents-home").assertIsDisplayed()
  }

  @Test
  fun botOpensChatAndItsProfileRoutesRemainReachable() {
    composeRule.setContent { ShellScreen(model) }
    composeRule.waitUntil { model.gatewayAgents.value.isNotEmpty() }
    composeRule.onNodeWithTag("home-primary-bot").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("agent-chats").assertIsDisplayed()
    composeRule.onNodeWithText(AndroidScreenshotFixture.primarySessionTitle).performScrollTo().assertIsDisplayed()
    capture("agent-chats")
    composeRule.onNodeWithText(AndroidScreenshotFixture.primarySessionTitle).performClick()
    composeRule.onNodeWithContentDescription("Chat actions").performClick()
    composeRule.onNodeWithText("Profile").performClick()
    composeRule.onNodeWithText("Files").assertIsDisplayed().assertHasClickAction()
    composeRule.onNodeWithText("Automations").assertIsDisplayed().assertHasClickAction()
    capture("bot-profile")
    composeRule.onNodeWithText("Voice").performClick()
    composeRule.onNodeWithText("Configure wake words, talk, and playback.").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Back").performClick()
    composeRule.onNodeWithText("Files").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Back").performClick()
    composeRule.onNodeWithContentDescription("Chat actions").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Back").performClick()
    composeRule.onNodeWithTag("agent-chats").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Back").performClick()
    composeRule.onNodeWithTag("agents-home").assertIsDisplayed()
  }

  @Test
  fun voiceSettingsRemainAccessibleFromProfileSettings() {
    composeRule.setContent { ShellScreen(model) }
    composeRule.onNodeWithContentDescription("Open profile").performClick()
    composeRule.onNodeWithText("Voice").performScrollTo().performClick()
    composeRule.onNodeWithText("Configure wake words, talk, and playback.").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Back").performClick()
    composeRule.onNodeWithText("Settings").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Close").performClick()
    composeRule.onNodeWithText("Search").assertIsDisplayed()
  }

  @Test
  fun overviewStatusKeepsReadableSemanticFillAcrossConnectionStatesAndThemes() {
    composeRule.setContent { ShellScreen(model) }
    val failures = mutableListOf<String>()
    for (label in listOf("Online", "Offline")) {
      composeRule.runOnIdle {
        when (label) {
          "Needs attention" -> {
            // Publish a synthetic pending-node snapshot through the existing runtime state contract.
            val nodes = ReflectionHelpers.getField<MutableStateFlow<GatewayNodesDevicesSummary>>(runtime, "_nodesDevicesSummary")
            nodes.value = nodes.value.copy(nodes = nodes.value.nodes.map { it.copy(approvalState = GatewayNodeCapabilityApproval.Unapproved) })
          }

          "Offline" -> {
            runtime.disconnect()
          }
        }
      }
      for (dark in listOf(true, false)) {
        composeRule.runOnIdle { prefs.setAppearanceThemeMode(if (dark) AppearanceThemeMode.Dark else AppearanceThemeMode.Light) }
        val status = hasText(label) and hasClickAction() and !hasContentDescription("Open Gateway") and !hasContentDescription("Open Files")
        val control = composeRule.onNode(status).assertIsDisplayed().assertHasClickAction()
        val text = composeRule.onNode(hasText(label) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
        val labelBounds = text.fetchSemanticsNode().boundsInRoot
        val controlBounds = control.fetchSemanticsNode().boundsInRoot
        assertTrue("Contrast must measure the dropdown label", labelBounds.top >= controlBounds.top && labelBounds.bottom <= controlBounds.bottom)
        val contrast = renderedLabelContrast(label = text, container = control, scrollIntoView = false)
        assertTrue("Status dropdown must retain its 48dp hit area", control.fetchSemanticsNode().size.height >= 48)
        capture("status-" + label.replace(' ', '-') + if (dark) "-dark" else "-light")
        println("OVERVIEW_STATUS_CONTRAST label=$label dark=$dark ratio=" + contrast.ratio + " background=" + contrast.background.toArgb())
        if (contrast.ratio < 4.5f) failures += "$label dark=$dark: " + contrast.ratio
      }
    }
    composeRule.onNode(hasText("Offline") and hasClickAction() and !hasContentDescription("Open Gateway") and !hasContentDescription("Open Files")).performClick()
    composeRule.onAllNodesWithText("Gateway")[0].assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Back").performClick()
    composeRule.onNode(hasText("Offline") and hasClickAction()).assertIsDisplayed().assertHasClickAction()
    assertTrue("Overview status text must retain at least 4.5:1 rendered contrast: " + failures.joinToString(), failures.isEmpty())
  }

  private fun capture(name: String) {
    val image = composeRule.onRoot().captureToImage().asAndroidBitmap()
    assertTrue("Capture must include the full phone root", image.width >= 360 && image.height >= 700)
    File(evidence, "$name.png").outputStream().use { stream ->
      assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
    }
    println("OVERVIEW_LAYOUT_PROOF " + File(evidence, "$name.png").absolutePath)
  }
}
