package com.kitakkun.jetwhale.host.data.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kitakkun.jetwhale.host.data.ThemeDataStoreQualifier
import com.kitakkun.jetwhale.host.model.AppAppearanceRepository
import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.JetWhaleColorScheme
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId
import com.kitakkun.jetwhale.host.model.ThemeColorTokens
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DefaultAppAppearanceRepository(
    @param:ThemeDataStoreQualifier
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : AppAppearanceRepository {
    override val languageFlow: Flow<AppLanguage> = dataStore.data.map { preferences ->
        preferences[appLanguagePreferencesKey]?.let { languageCode ->
            AppLanguage.entries.find { it.code == languageCode }
        } ?: AppLanguage.English
    }

    override val preferredColorSchemeIdFlow: Flow<JetWhaleColorSchemeId> = dataStore.data.map { preferences ->
        preferences[preferredColorSchemeIdPreferencesKey]?.let { idString ->
            JetWhaleColorSchemeId.BuiltIns.firstOrNull { it.id == idString } ?: JetWhaleColorSchemeId.custom(idString)
        } ?: JetWhaleColorSchemeId.BuiltInDynamic
    }

    override val preferredColorSchemeFlow: Flow<JetWhaleColorScheme> = preferredColorSchemeIdFlow.map { id ->
        resolveColorScheme(id)
    }

    internal val customColorSchemesFlow: Flow<CustomThemes> = dataStore.data.map {
        it[customThemesPreferencesKey]?.let {
            json.decodeFromString<CustomThemes>(it)
        } ?: CustomThemes(emptyList())
    }

    internal val dynamicColorSchemesFlow: Flow<DynamicThemes> = dataStore.data.map {
        it[dynamicThemesPreferencesKey]?.let {
            json.decodeFromString<DynamicThemes>(it)
        } ?: DynamicThemes(emptyList())
    }

    override suspend fun setPreferredColorSchemeId(id: JetWhaleColorSchemeId) {
        dataStore.edit { prefs ->
            prefs[preferredColorSchemeIdPreferencesKey] = id.id
        }
    }

    override suspend fun saveCustomTheme(id: JetWhaleColorSchemeId, theme: JetWhaleColorScheme.Static.Custom) {
        dataStore.edit { prefs ->
            val customThemeDefinition = CustomThemeDefinition(
                id = id,
                colors = theme.colors.mapKeys { entry -> entry.key.name }
            )

            val currentThemeDefinitions = prefs[customThemesPreferencesKey]?.let { serialized ->
                json.decodeFromString<CustomThemes>(serialized)
            } ?: CustomThemes(emptyList())

            prefs[customThemesPreferencesKey] = json.encodeToString(
                currentThemeDefinitions.copy(
                    colorSchemes = currentThemeDefinitions.colorSchemes.filter { it.id != id } + customThemeDefinition
                )
            )
        }
    }

    override suspend fun saveDynamicTheme(
        id: JetWhaleColorSchemeId,
        lightThemeId: JetWhaleColorSchemeId,
        darkThemeId: JetWhaleColorSchemeId,
    ) {
        dataStore.edit { prefs ->
            val dynamicThemeDefinition = DynamicThemeDefinition(
                id = id,
                lightThemeKey = lightThemeId,
                darkThemeKey = darkThemeId,
            )

            val currentDynamicThemes = prefs[dynamicThemesPreferencesKey]?.let { serialized ->
                json.decodeFromString<DynamicThemes>(serialized)
            } ?: DynamicThemes(emptyList())

            prefs[dynamicThemesPreferencesKey] = json.encodeToString(
                currentDynamicThemes.copy(
                    colorSchemes = currentDynamicThemes.colorSchemes.filter { it.id != id } + dynamicThemeDefinition
                )
            )
        }
    }

    override suspend fun updateAppLanguage(language: AppLanguage) {
        dataStore.edit { prefs ->
            prefs[appLanguagePreferencesKey] = language.code
        }
    }

    private suspend fun resolveColorScheme(id: JetWhaleColorSchemeId): JetWhaleColorScheme {
        return when (id) {
            JetWhaleColorSchemeId.BuiltInLight -> {
                return JetWhaleColorScheme.Static.Light
            }

            JetWhaleColorSchemeId.BuiltInDark -> {
                return JetWhaleColorScheme.Static.Dark
            }

            JetWhaleColorSchemeId.BuiltInDynamic -> {
                return JetWhaleColorScheme.Dynamic.BuiltIn
            }

            else -> {
                dynamicColorSchemesFlow.first().colorSchemes.firstOrNull { it.id == id }?.let {
                    val lightTheme = resolveColorScheme(it.lightThemeKey)
                    val darkTheme = resolveColorScheme(it.darkThemeKey)

                    check(lightTheme is JetWhaleColorScheme.Static && darkTheme is JetWhaleColorScheme.Static) {
                        "Dynamic themes can only reference static themes"
                    }

                    JetWhaleColorScheme.Dynamic(
                        lightColorScheme = lightTheme,
                        darkColorScheme = darkTheme,
                    )
                } ?: customColorSchemesFlow.first().colorSchemes.firstOrNull { it.id == id }?.let {
                    JetWhaleColorScheme.Static.Custom(
                        colors = it.colors.mapKeys { (key, _) -> ThemeColorTokens.valueOf(key) },
                    )
                } ?: JetWhaleColorScheme.Dynamic.BuiltIn
            }
        }

    }

    // TODO: Expose as public API if needed
    private suspend fun saveCustomColorScheme(id: String, colorScheme: JetWhaleColorScheme.Static.Custom) {
        saveCustomTheme(JetWhaleColorSchemeId.custom(id), colorScheme)
    }

    // TODO: Expose as public API if needed
    private suspend fun saveDynamicColorScheme(id: String, lightColorSchemeId: String, darkColorSchemeId: String) {
        saveDynamicTheme(
            id = JetWhaleColorSchemeId.custom(id),
            lightThemeId = JetWhaleColorSchemeId.BuiltIns.firstOrNull {
                it.id == lightColorSchemeId
            } ?: JetWhaleColorSchemeId.custom(lightColorSchemeId),
            darkThemeId = JetWhaleColorSchemeId.BuiltIns.firstOrNull {
                it.id == darkColorSchemeId
            } ?: JetWhaleColorSchemeId.custom(darkColorSchemeId),
        )
    }

    companion object Companion {
        private val customThemesPreferencesKey = stringPreferencesKey("custom_themes")
        private val dynamicThemesPreferencesKey = stringPreferencesKey("dynamic_themes")
        private val appLanguagePreferencesKey = stringPreferencesKey("app_language")
        private val preferredColorSchemeIdPreferencesKey = stringPreferencesKey("preferred_color_scheme_id")
    }
}

@Serializable
internal data class CustomThemes(val colorSchemes: List<CustomThemeDefinition>)

@Serializable
internal data class CustomThemeDefinition(
    val id: JetWhaleColorSchemeId,
    val colors: Map<String, Int>,
)

@Serializable
internal data class DynamicThemes(
    val colorSchemes: List<DynamicThemeDefinition>
)

@Serializable
internal data class DynamicThemeDefinition(
    val id: JetWhaleColorSchemeId,
    val lightThemeKey: JetWhaleColorSchemeId,
    val darkThemeKey: JetWhaleColorSchemeId,
)
