package com.forgeprom.studio.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignSystemTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun statusChipShowsLabel() {
        compose.setContent {
            MaterialTheme { StatusChip("جاهز: 3 ميزة", ready = true) }
        }
        compose.onNodeWithText("جاهز: 3 ميزة").assertIsDisplayed()
    }

    @Test
    fun statCardShowsValueAndLabel() {
        compose.setContent {
            MaterialTheme { StatCard(Icons.Outlined.Folder, "12", "مشاريع") }
        }
        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithText("مشاريع").assertIsDisplayed()
    }

    @Test
    fun emptyStateActionIsClickable() {
        var clicked = false
        compose.setContent {
            MaterialTheme {
                EmptyState("لا مشاريع", icon = Icons.Outlined.Folder, actionLabel = "إعادة المحاولة") {
                    clicked = true
                }
            }
        }
        compose.onNodeWithText("إعادة المحاولة").performClick()
        assertTrue(clicked)
    }
}
