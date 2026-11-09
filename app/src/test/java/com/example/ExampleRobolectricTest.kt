package com.example

import com.example.launcher.model.AppLanguage
import com.example.launcher.model.IconShapePreset
import com.example.launcher.model.LauncherSettings
import com.example.launcher.model.TemperatureUnit
import com.example.launcher.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit test validating launcher configuration, defaults, temperature units, and language settings.
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
        assertEquals(TemperatureUnit.CELSIUS, settings.temperatureUnit)
        assertEquals(AppLanguage.SYSTEM, settings.appLanguage)
    }

    @Test
    fun testSettingsCopyPreservesValues() {
        val original = LauncherSettings()
        val modified = original.copy(hasSeenWelcome = true, gridColumns = 6)
        assertEquals(true, modified.hasSeenWelcome)
        assertEquals(6, modified.gridColumns)
        assertEquals(4, modified.dockIconCount)
    }

    @Test
    fun testTemperatureUnitsConversionAndFormatting() {
        assertEquals("72°F", TemperatureUnit.FAHRENHEIT.formatTemp(72))
        assertEquals("22°C", TemperatureUnit.CELSIUS.formatTemp(72))
        assertEquals("295K", TemperatureUnit.KELVIN.formatTemp(72))
        assertEquals("76°", TemperatureUnit.FAHRENHEIT.formatCompact(76))
        assertEquals("24°", TemperatureUnit.CELSIUS.formatCompact(76))
        assertEquals("298K", TemperatureUnit.KELVIN.formatCompact(76))
    }

    @Test
    fun testAppLanguageOptions() {
        assertTrue(AppLanguage.entries.size >= 8)
        assertEquals("es", AppLanguage.SPANISH.languageTag)
        assertEquals("fr", AppLanguage.FRENCH.languageTag)
        assertEquals("de", AppLanguage.GERMAN.languageTag)
        assertEquals("ja", AppLanguage.JAPANESE.languageTag)
    }
}
