# Skybound Runner

A lightweight Android side scroller prototype. The oval runner moves automatically; tap anywhere while running to jump. Raised yellow walls act as ledges, holes must be cleared, and shallow depressions can be jumped over or escaped after landing inside. Ledges and depressions may have vertical, 30°, or 45° entrances and exits. Tapping Start on a fresh launch generates a new course.

## Playing

1. Install the debug APK on an Android 6.0 (API 23) or newer device and launch **Skybound Runner**.
2. Tap **Start**. The three second countdown begins.
3. Tap anywhere to jump. A jump is available when standing on ground, a ledge, or the floor of a depression. A sloped entrance or exit can be run along. A vertical ledge face or far lip of a depression costs one health point and pushes the runner back to make room for another jump.
4. Tap the pause button in the top-right corner or send the app to the background. Tap **Continue** to resume the run or remaining countdown.

The top row shows current distance, the best **completed** attempt to beat, current speed in metres per second, and attempt number. Ten dots below it show health from red on the left to green on the right; lost points turn pale gray with 50% transparency from right to left. After 500 m, stationary red ovals may appear on flat ground, slopes, ledges, or in depressions, with empty stretches between them. Jump over them and the small red dots they shoot horizontally at regular intervals. Projectiles disappear when they hit yellow terrain. Touching an oval costs one health point, removes that oval, and pushes the runner back; touching a projectile costs one point and causes a small upward bounce. Reaching zero health or falling into a hole starts another attempt on the **same** course.

Every three seconds spent moving increases base speed slightly. Uphill slopes temporarily slow the runner and downhill slopes speed them up; speed gradually returns to its base value after leaving a slope. After a wall or oval knocks the runner back, they stop for half a second and resume at the starting speed until they have passed that obstacle. The speed from before the impact then returns. A failed attempt records its farthest distance and resets speed and health. Completed distances and the best score remain available after restarting the app.

The game is designed for landscape orientation. It does not require internet access or any runtime permissions.

## Editing and building

Open this directory as a project in Android Studio. It uses the Android Gradle plugin 8.7.3, Java 8 source compatibility, compile SDK 35, and no external runtime libraries. Run `./gradlew assembleDebug` (or `gradlew.bat assembleDebug` on Windows) to build a new debug APK. Android Studio may need to download Gradle and the Android SDK on first use. The checked-in source does not include a private signing key; new debug builds may need the previously installed prototype removed before installation.

### Downloading a test APK from GitHub

Every update to `main` runs [Build Android APK](https://github.com/svasylevskyi/skybound-runner/actions/workflows/build-apk.yml). To build the current `main` again, open that page, select **Run workflow**, choose `main`, and start the run. When it succeeds, open the run and download **SkyboundRunner-debug.apk** from **Artifacts**. The APK is ready to install on Android without extracting a ZIP; GitHub retains it for 30 days.

Each run signs the APK with a new debug key. If an older build of the game is already installed, uninstall it before installing the new APK. Uninstalling also clears the saved best distance.

If you have Android SDK platform 35 and build tools 35.0.0 but no Gradle, you can also run `ANDROID_HOME=/path/to/android-sdk bash tools/build_with_sdk.sh` from this directory. This creates `SkyboundRunner-debug.apk` at the project root.

The gameplay is in `app/src/main/java/com/stepan/skyboundrunner/RunnerEngine.java`, independently of Android APIs; `RunnerView.java` handles drawing and touch. The executable core checks are in `tools/RunnerEngineChecks.java` and can run with:

```bash
mkdir -p /tmp/skybound-checks
javac -d /tmp/skybound-checks app/src/main/java/com/stepan/skyboundrunner/RunnerEngine.java tools/RunnerEngineChecks.java
java -cp /tmp/skybound-checks com.stepan.skyboundrunner.RunnerEngineChecks
```

This is a test build signed with a debug key. For Play Store distribution it needs a release signing key and a publication review.
