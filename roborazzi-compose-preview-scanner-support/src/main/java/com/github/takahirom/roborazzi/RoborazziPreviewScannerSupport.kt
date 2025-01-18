package com.github.takahirom.roborazzi

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.annotations.DelayedPreview
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.robolectric.RuntimeEnvironment.setQualifiers
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.device.domain.RobolectricDeviceQualifierBuilder
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import sergio.sastre.composable.preview.scanner.core.preview.getAnnotation

@ExperimentalRoborazziApi
fun ComposablePreview<AndroidPreviewInfo>.captureRoboImage(
  filePath: String,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  roborazziComposeOptions: RoborazziComposeOptions = this.toRoborazziComposeOptions(),
  doBeforeCaptureRoboImage: () -> Unit = {},
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  val composablePreview = this
  captureRoboImage(filePath, roborazziOptions, roborazziComposeOptions, doBeforeCaptureRoboImage) {
    composablePreview()
  }
}

@ExperimentalRoborazziApi
fun ComposablePreview<AndroidPreviewInfo>.toRoborazziComposeOptions(): RoborazziComposeOptions {
  return RoborazziComposeOptions {
    size(
      widthDp = previewInfo.widthDp, heightDp = previewInfo.heightDp
    )
    background(
      showBackground = previewInfo.showBackground, backgroundColor = previewInfo.backgroundColor
    )
    locale(previewInfo.locale)
    uiMode(previewInfo.uiMode)
    previewDevice(previewInfo.device)
    fontScale(previewInfo.fontScale)

    /*
    We don't specify `inspectionMode` by default.
    The default value for `inspectionMode` in Compose is `false`.
    This is to maintain higher fidelity in tests.
    If you encounter issues integrating the library, you can set `inspectionMode` to `true`.

    inspectionMode(true)
     */
  }
}


@Suppress("UnusedReceiverParameter")
@Deprecated(
  message = "Use previewInfo.toRoborazziComposeOptions().configured(scenario, composeContent) or ComposablePreview<AndroidPreviewInfo>.captureRoboImage() instead",
  replaceWith = ReplaceWith("previewInfo.toRoborazziComposeOptions().configured(scenario, composeContent)"),
  level = DeprecationLevel.ERROR
)
fun ComposablePreview<AndroidPreviewInfo>.applyToRobolectricConfiguration() {
  throw UnsupportedOperationException("Use previewInfo.toRoborazziComposeOptions().configured(scenario, composeContent) or ComposablePreview<AndroidPreviewInfo>.captureRoboImage() instead")
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.previewDevice(previewDevice: String): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposePreviewDeviceOption(previewDevice))
}

@ExperimentalRoborazziApi
data class RoborazziComposePreviewDeviceOption(private val previewDevice: String) :
  RoborazziComposeSetupOption {
  override fun configure() {
    if (previewDevice.isNotBlank()) {
      // Requires `io.github.sergio-sastre.ComposablePreviewScanner:android:0.4.0` or later
      RobolectricDeviceQualifierBuilder.build(previewDevice)?.run {
        setQualifiers(this)
      }
    }
  }
}

/**
 * ComposePreviewTester is an interface that allows you to define a custom test for a Composable preview.
 * The class that implements this interface should have a parameterless constructor.
 * You can set the custom tester class name in the roborazzi.generateComposePreviewRobolectricTests.testerQualifiedClassName property.
 * A new instance of the tester class is created for each test execution and preview generation.
 */
@ExperimentalRoborazziApi
interface ComposePreviewTester<T : Any> {
  data class Options(
    val testLifecycleOptions: TestLifecycleOptions = JUnit4TestLifecycleOptions(),
    val scanOptions: ScanOptions = ScanOptions(emptyList()),
  ) {
    interface TestLifecycleOptions
    data class JUnit4TestLifecycleOptions(
      /**
       * The TestRule factory to be used for the generated tests.
       * You can use this to add custom behavior to the generated tests.
       */
      // Used from generated tests
      @Suppress("unused") val testRuleFactory: () -> TestRule = { object : TestWatcher() {} },
    ) : TestLifecycleOptions

    data class ScanOptions(
      /**
       * The packages to scan for composable previews.
       */
      val packages: List<String>,
      /**
       * Whether to include private previews in the scan.
       */
      val includePrivatePreviews: Boolean = false,
    )
  }

  /**
   * Retrieves the options for the ComposePreviewTester.
   *
   * @return The Options object.
   */
  fun options(): Options = defaultOptionsFromPlugin

  /**
   * Retrieves a list of composable previews from the specified packages.
   *
   * @return A list of ComposablePreview objects of type T.
   */
  fun previews(): List<ComposablePreview<T>>

  /**
   * Performs a test on a single composable preview.
   * Note: This method will not be called as the same instance of previews() method.
   *
   * @param preview The ComposablePreview object to be tested.
   */
  fun test(preview: ComposablePreview<T>)

  companion object {
    // Should be replaced with the actual default options from the plugin.
    @InternalRoborazziApi
    var defaultOptionsFromPlugin = Options()
  }
}

@ExperimentalRoborazziApi
class AndroidComposePreviewTester : ComposePreviewTester<AndroidPreviewInfo> {
  private val composeTestRule by lazy { createAndroidComposeRule<RoborazziActivity>() }

  override fun previews(): List<ComposablePreview<AndroidPreviewInfo>> {
    val options = options()
    return AndroidComposablePreviewScanner().scanPackageTrees(*options.scanOptions.packages.toTypedArray())
      .includeAnnotationInfoForAllOf(DelayedPreview::class.java).let {
        if (options.scanOptions.includePrivatePreviews) {
          it.includePrivatePreviews()
        } else {
          it
        }
      }.getPreviews()
  }

  override fun test(preview: ComposablePreview<AndroidPreviewInfo>) {
    val pathPrefix =
      if (roborazziRecordFilePathStrategy() == RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory) {
        roborazziSystemPropertyOutputDirectory() + java.io.File.separator
      } else {
        ""
      }
    val name = roborazziDefaultNamingStrategy().generateOutputName(
      preview.declaringClass, createScreenshotIdFor(preview)
    )
    val filePath = "$pathPrefix$name.${provideRoborazziContext().imageExtension}"

    preview.getAnnotation<DelayedPreview>()?.let { delayedPreview ->
      val delay = delayedPreview.milliseconds
      when (delay > 0L) {
        true -> {
          composeTestRule.mainClock.autoAdvance = false
          preview.captureRoboImage(filePath = filePath, doBeforeCaptureRoboImage = {
            composeTestRule.mainClock.advanceTimeBy(delay)
          })
          composeTestRule.mainClock.autoAdvance = true
        }
        false -> preview.captureRoboImage(filePath = filePath)
      }
    } ?: preview.captureRoboImage(filePath = filePath)

  }

  override fun options(): ComposePreviewTester.Options {
    // TODO -> Add composeTestRule only if necessary to avoid extra execution time?
    val testLifecycleOptions = ComposePreviewTester.Options.JUnit4TestLifecycleOptions(
      testRuleFactory = { composeTestRule })
    return super.options().copy(
      testLifecycleOptions = testLifecycleOptions
    )
  }

  private fun createScreenshotIdFor(preview: ComposablePreview<AndroidPreviewInfo>) =
    AndroidPreviewScreenshotIdBuilder(preview).ignoreClassName().build()
}

@InternalRoborazziApi
fun getComposePreviewTester(testerQualifiedClassName: String): ComposePreviewTester<Any> {
  val customTesterClass = try {
    Class.forName(testerQualifiedClassName)
  } catch (e: ClassNotFoundException) {
    throw IllegalArgumentException("The class $testerQualifiedClassName not found")
  }
  if (!ComposePreviewTester::class.java.isAssignableFrom(customTesterClass)) {
    throw IllegalArgumentException("The class $testerQualifiedClassName must implement RobolectricPreviewCapturer")
  }
  @Suppress("UNCHECKED_CAST") val composePreviewTester =
    customTesterClass.getDeclaredConstructor().newInstance() as ComposePreviewTester<Any>
  return composePreviewTester
}
