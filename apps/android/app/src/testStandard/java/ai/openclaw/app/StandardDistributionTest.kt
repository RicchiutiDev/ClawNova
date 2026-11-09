package ai.openclaw.app

import ai.openclaw.app.node.DeviceNotificationListenerService
import ai.openclaw.app.ui.SettingsDetailScreen
import ai.openclaw.app.ui.SettingsRoute
import ai.openclaw.app.ui.design.ClawDesignTheme
import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNotificationManager
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StandardDistributionTest {
  @get:Rule val composeRule = createComposeRule()
  private val app get() = RuntimeEnvironment.getApplication() as NodeApp
  private val models = ViewModelStore()
  private var runtime: NodeRuntime? = null
  private var previousRuntime: NodeRuntime? = null

  @After
  fun cleanUp() {
    models.clear()
    runtime?.let {
      closeNodeRuntimeTestFixture(it)
      bindNodeRuntimeTestFixture(app, previousRuntime)
    }
  }

  @Test
  fun installedManifestOmitsSmsAndSpecialAccessServicesButKeepsChatNotifications() {
    val info =
      app.packageManager.getPackageInfo(
        app.packageName,
        PackageManager.PackageInfoFlags.of((PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES).toLong()),
      )
    val permissions = info.requestedPermissions.orEmpty().toSet()
    for (permission in listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CALL_LOG)) {
      assertFalse("Standard must not request $permission", permission in permissions)
    }
    val servicePermissions =
      info.services
        .orEmpty()
        .mapNotNull { it.permission }
        .toSet()
    assertFalse(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE in servicePermissions)
    assertFalse(Manifest.permission.BIND_ACCESSIBILITY_SERVICE in servicePermissions)
    assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
    assertTrue(Manifest.permission.RECORD_AUDIO in permissions)
    assertFalse(SensitiveFeatureConfig.smsEnabled)
    assertFalse(SensitiveFeatureConfig.accessibilityControlEnabled)
  }

  @Test
  fun notificationAccessRemainsUnavailableEvenWithAnOldSystemGrant() {
    val manager = app.getSystemService(NotificationManager::class.java)
    Shadow.extract<ShadowNotificationManager>(manager).setNotificationListenerAccessGranted(ComponentName(app, DeviceNotificationListenerService::class.java), true)
    assertFalse(DeviceNotificationListenerService.isAccessEnabled(app))
  }

  @Test
  fun notificationSettingsExplainTheEditionInsteadOfRequestingUnavailableAccess() {
    val prefs = SecurePrefs(app, app.getSharedPreferences("standard-" + UUID.randomUUID(), Context.MODE_PRIVATE))
    previousRuntime = app.peekRuntime()
    runtime = NodeRuntime(app, prefs, NodeRuntimeMode.ScreenshotFixture)
    bindNodeRuntimeTestFixture(app, runtime)
    val model = MainViewModel(app, prefs, SavedStateHandle()).also { models.put("standard", it) }
    composeRule.setContent { ClawDesignTheme { SettingsDetailScreen(model, SettingsRoute.Notifications, onBack = {}) } }
    composeRule.onNodeWithText(app.getString(R.string.clawnova_notifications_standard_title)).assertIsDisplayed()
    composeRule.onNodeWithText("Forward Notifications").assertDoesNotExist()
    composeRule.onNodeWithText("Open System Access").assertDoesNotExist()
    val directory = File("build/outputs/standard-distribution/" + UUID.randomUUID())
    check(directory.mkdirs())
    val destination = File(directory, "notifications.png")
    destination.outputStream().use {
      assertTrue(
        composeRule
          .onRoot()
          .captureToImage()
          .asAndroidBitmap()
          .compress(Bitmap.CompressFormat.PNG, 100, it),
      )
    }
    println("STANDARD_DISTRIBUTION_PROOF " + destination.absolutePath)
  }
}
