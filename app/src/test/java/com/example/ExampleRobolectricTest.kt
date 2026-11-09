package com.example

import com.example.launcher.model.IconShapePreset
import com.example.launcher.model.LauncherSettings
import com.example.launcher.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit test validating launcher configuration, defaults, and settings models.
 */
class ExampleRobolectricTest {

    @Test
    fun testLauncherSettingsDefaults() {
        val settings = LauncherSettings()
        assertEquals(IconShapePreset.SQUIRCLE, settings.iconShape)
        assertEquals(ThemeMode.AUTO, settings.themeMode)
        assertEquals(5, settings.gridColumns)
        assertEquals(5, settings.gridRows)
        assertEquals(4, settings.dockIconCount)
        assertEquals(false, settings.hasSeenWelcome)
    }

    @Test
    fun testSettingsCopyPreservesValues() {
        val original = LauncherSettings()
        val modified = original.copy(hasSeenWelcome = true, gridColumns = 6)
        assertEquals(true, modified.hasSeenWelcome)
        assertEquals(6, modified.gridColumns)
        assertEquals(4, modified.dockIconCount)
    }
}
