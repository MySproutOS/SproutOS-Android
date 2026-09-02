package com.sproutos.store

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class StoreNavigationInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun storeSearchDetailPersonalAndSettingsAreDistinctDestinations() {
        val state = CatalogueState()
        state.accept(parseCatalogue(catalogueJson()))

        compose.setContent {
            SproutTheme {
                SproutStoreApp(
                    state = state,
                    onSignIn = {},
                    onSignOut = {},
                    onRefresh = {},
                    onInstall = {},
                    onClientAutomaticUpdates = {},
                    onAppAutomaticUpdates = {},
                    onEnableUpdateNotifications = {},
                )
            }
        }

        compose.onNodeWithTag("sprout:nav:store").assertIsDisplayed()
        compose.onNodeWithTag("sprout:store:search-launcher").performClick()
        compose.onNodeWithTag("sprout:store:search-field").performTextInput("field")
        compose.onNodeWithText("Field Notes").performClick()
        compose.onNodeWithText("Verification").assertIsDisplayed()
        compose.onNodeWithTag("sprout:detail:install").assertIsDisplayed()

        compose.onNodeWithTag("sprout:nav:personal").performClick()
        compose.onNodeWithText("Your SproutOS library").assertIsDisplayed()
        compose.onNodeWithTag("sprout:personal:signin").assertIsDisplayed()

        compose.onNodeWithTag("sprout:nav:settings").performClick()
        compose.onNodeWithText("Automatic updates").assertIsDisplayed()
        compose.onNodeWithTag("sprout:settings:client-switch").assertIsDisplayed()
        compose.onNodeWithTag("sprout:settings:apps-switch").assertIsDisplayed()
    }

    @Test
    fun completedSignInRecomposesSettingsWithoutRestartingTheApp() {
        val state = CatalogueState()
        val store = SessionStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear()

        compose.setContent {
            SproutTheme {
                SproutStoreApp(
                    state = state,
                    onSignIn = {},
                    onSignOut = {},
                    onRefresh = {},
                    onInstall = {},
                    onClientAutomaticUpdates = {},
                    onAppAutomaticUpdates = {},
                    onEnableUpdateNotifications = {},
                )
            }
        }

        compose.onNodeWithTag("sprout:nav:settings").performClick()
        compose.onNodeWithText("Not signed in").assertIsDisplayed()

        compose.runOnIdle {
            state.acceptSession(
                OAuthSession(
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    expiresAtEpochSeconds = Long.MAX_VALUE,
                    scopes = setOf("project:read"),
                ),
                store,
            )
        }

        compose.onNodeWithText("Signed in").assertIsDisplayed()
        compose.onNodeWithText("Sign out").assertIsDisplayed()
        store.clear()
    }

    @Test
    fun duplicatePersonalSiteUrlsDoNotCrashTheLibrary() {
        val state = CatalogueState()
        val store = SessionStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear()
        state.acceptSession(
            OAuthSession(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                expiresAtEpochSeconds = Long.MAX_VALUE,
                scopes = setOf("project:read"),
            ),
            store,
        )
        state.accept(parseCatalogue(duplicateSitesCatalogueJson()))

        compose.setContent {
            SproutTheme {
                SproutStoreApp(
                    state = state,
                    onSignIn = {},
                    onSignOut = {},
                    onRefresh = {},
                    onInstall = {},
                    onClientAutomaticUpdates = {},
                    onAppAutomaticUpdates = {},
                    onEnableUpdateNotifications = {},
                )
            }
        }

        compose.onNodeWithTag("sprout:nav:personal").performClick()
        compose.onAllNodesWithText("Duplicate Site").assertCountEquals(1)
        store.clear()
    }

    private fun catalogueJson(): String =
        """
        {
          "version": 2,
          "generatedAt": "2026-09-02T00:00:00Z",
          "expiresAt": "2026-09-02T01:00:00Z",
          "public": {"apps": [{
            "androidAppId": "11900000-0000-7000-8000-000000000001",
            "projectId": "21900000-0000-7000-8000-000000000001",
            "packageName": "me.sproutos.app.p21900000000070008000000000000001",
            "label": "Field Notes",
            "summary": "Private-first notes for research and field work.",
            "versionName": "1.4.2",
            "versionCode": 14,
            "sha256": "${"1".repeat(64)}",
            "sizeBytes": 8420000,
            "certificateSha256": "${"2".repeat(64)}",
            "downloadUrl": "https://fixtures.invalid/field-notes.apk",
            "category": "Personal tools"
          }]},
          "personal": {"apps": [], "sites": []}
        }
        """.trimIndent()

    private fun duplicateSitesCatalogueJson(): String =
        """
        {
          "version": 2,
          "generatedAt": "2026-09-02T00:00:00Z",
          "expiresAt": "2026-09-02T01:00:00Z",
          "public": {"apps": []},
          "personal": {"apps": [], "sites": [
            {"name": "Duplicate Site", "url": "https://duplicate.sproutos.run", "summary": ""},
            {"name": "Duplicate Site", "url": "https://duplicate.sproutos.run", "summary": ""}
          ]}
        }
        """.trimIndent()
}
