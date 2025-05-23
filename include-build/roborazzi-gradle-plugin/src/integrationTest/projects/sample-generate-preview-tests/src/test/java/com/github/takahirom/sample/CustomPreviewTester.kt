package com.github.takahirom.sample

import org.junit.Assert.*
import com.github.takahirom.roborazzi.*
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.github.takahirom.roborazzi.*
import androidx.compose.ui.test.onRoot

class CustomPreviewTester : ComposePreviewTester<AndroidPreviewJUnit4TestParameter> by AndroidComposePreviewTester() {
  override fun options(): ComposePreviewTester.Options = super.options().copy(
    testLifecycleOptions = ComposePreviewTester.Options.JUnit4TestLifecycleOptions(
      composeRuleFactory = { createAndroidComposeRule<RoborazziActivity>() as AndroidComposeTestRule<ActivityScenarioRule<out androidx.activity.ComponentActivity>, *> },
      testRuleFactory = { composeTestRule ->
        RuleChain.outerRule(
          object : TestWatcher() {
            override fun starting(description: org.junit.runner.Description?) {
              println("JUnit4TestLifecycleOptions starting")
            }

            override fun finished(description: org.junit.runner.Description?) {
              println("JUnit4TestLifecycleOptions finished")
            }
          }
        )
          .around(object : TestWatcher() {
            override fun starting(description: org.junit.runner.Description?) {
              super.starting(description)
              registerRoborazziActivityToRobolectricIfNeeded()
            }
          })
          .around(composeTestRule)
      }
    )
  )

  override fun test(testParameter: AndroidPreviewJUnit4TestParameter) {
    val preview = testParameter.preview as ComposablePreview
    testParameter.composeTestRule.setContent {
      testParameter.preview()
    }
    testParameter.composeTestRule.onRoot().captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/${preview.methodName}.${provideRoborazziContext().imageExtension}")
  }
}