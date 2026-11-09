package ai.openclaw.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BotAppearanceTest {
  @Test fun readsPerAgentAppearancesAndIgnoresInvalidEntries() {
    val styles = readBotAppearances("""{"alpha":{"shape":2,"color":5},"beta":{"shape":3,"color":1},"invalid":{"shape":9,"color":0}}""")
    assertEquals(BotAppearance(2, 5), styles["alpha"])
    assertEquals(BotAppearance(3, 1), styles["beta"])
    assertEquals(2, styles.size)
  }

  @Test fun damagedOrMissingPreferencesRestoreDefault() {
    assertTrue(readBotAppearances(null).isEmpty())
    assertTrue(readBotAppearances("not json").isEmpty())
    assertTrue(readBotAppearances("""{"bot":{"shape":1,"color":99}}""").isEmpty())
  }
}
