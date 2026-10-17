package com.petcare.app.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.User
import com.petcare.app.ui.theme.PetCareTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileSummaryCardRobolectricTest {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * The host activity is registered explicitly so this suite also runs for the
     * release variant, whose manifest does not include the debug-only Compose
     * test activity.
     */
    @get:Rule
    val rule: TestRule = RuleChain.outerRule(RegisterHostActivityRule()).around(composeRule)

    @Test
    fun `user summary exposes identity pet count and accessible profile action`() {
        var clicked = false
        val user = User(
            id = 1,
            name = "Emily Stone",
            email = "emily@example.com",
            passwordHash = "hash",
            passwordSalt = "salt"
        )

        composeRule.setContent {
            PetCareTheme {
                UserProfileSummaryCard(user = user, petCount = 2, onViewProfile = { clicked = true })
            }
        }

        composeRule.onNodeWithText("Emily Stone").assertIsDisplayed()
        composeRule.onNodeWithText("emily@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("2 pets").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("View Emily Stone's profile")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertTrue(clicked)
    }

    @Test
    fun `pet summary exposes species tasks and accessible profile action`() {
        var clicked = false
        val pet = Pet(
            id = 2,
            ownerId = 1,
            name = "Milo",
            species = Species.DOG.name,
            breed = "Beagle"
        )

        composeRule.setContent {
            PetCareTheme {
                PetSummaryCard(
                    pet = pet,
                    completedToday = 1,
                    totalToday = 3,
                    onViewProfile = { clicked = true }
                )
            }
        }

        composeRule.onNodeWithText("Milo").assertIsDisplayed()
        composeRule.onNodeWithText("Beagle").assertIsDisplayed()
        composeRule.onNodeWithText("1/3 tasks done today").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("View Milo's profile")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertTrue(clicked)
    }
}

private class RegisterHostActivityRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Application>()
                val packageManager = shadowOf(context.packageManager)
                val component = ComponentName(context, ComponentActivity::class.java)
                packageManager.addActivityIfNotPresent(component)
                try {
                    base.evaluate()
                } finally {
                    packageManager.removeActivity(component)
                }
            }
        }
}
