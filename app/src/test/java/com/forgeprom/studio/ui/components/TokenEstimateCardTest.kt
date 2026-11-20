package com.forgeprom.studio.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forgeprom.studio.domain.TokenEstimate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TokenEstimateCardTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersInputAndOutputEstimates() {
        compose.setContent {
            MaterialTheme {
                TokenEstimateCard(TokenEstimate(inputTokens = 240, maxOutputTokens = 800))
            }
        }
        compose.onNodeWithText("تقدير الطلب: ~240 رمز إدخال + حتى 800 إخراج.")
            .assertIsDisplayed()
    }

    @Test
    fun honoursCustomLabel() {
        compose.setContent {
            MaterialTheme {
                TokenEstimateCard(TokenEstimate(1, 2), label = "الطلب:")
            }
        }
        compose.onNodeWithText("الطلب: ~1 رمز إدخال + حتى 2 إخراج.").assertIsDisplayed()
    }
}
