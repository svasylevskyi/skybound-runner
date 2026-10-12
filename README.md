# Skybound Runner

A lightweight Android side scroller prototype. The dark blue hovercar moves automatically; tap anywhere while moving to jump. Raised yellow walls act as ledges, water-filled holes can be jumped over or crossed by tapping repeatedly to stay afloat, and depressions can be jumped over or escaped after landing inside. Fully submerging in the gently waving water costs a life. Suspended yellow platforms leave two to four car heights of space over flat ground. The car can run underneath, jump onto a low platform, or jump from it to a higher one; hitting a platform from below stops the upward jump without damage. Ledges and depressions vary from 4 to 10 m high or deep, below the car's 11.1 m jump height. Flat ground is 0°; height-changing ledge and depression edges have random inclines from 1° to 90°, where 90° is vertical. Tapping Start on a fresh launch generates a new course.

The dimmed background has a dense, two-layer futuristic skyline with a few small gaps between skyscrapers. Five palm shapes with slightly different heights, leaves, and trunks appear in front of the buildings, with three to eight visible on each screen. The skyline scrolls more slowly for depth; palms share the terrain's position so their roots touch its surface and their leaves stay clear of holes. A green grass layer with blades one quarter of the hovercar's height covers flat ground, ledges, depressions, and slopes up to 45°. It stops at holes and steeper slopes. The scenery is generated from the course seed and remains in the same places after pausing or restoring a run.

## Playing

1. Install the debug APK on an Android 6.0 (API 23) or newer device and launch **Skybound Runner**.
2. Tap **Start**. The three second countdown begins.
3. Tap anywhere to jump. The jump hint disappears after your first jump lands. A jump is available when standing on ground, a ledge, a suspended platform, or the floor of a depression. A sloped entrance or exit can be run along. Steep climbs can reverse the runner and stop it at the bottom; a vertical ledge face or the far wall of a depression also stops the runner. Tap to jump past either obstacle without losing a life. In a water hole, keep tapping to bounce back toward the surface as the car drifts across; it hops onto the far bank automatically.
4. Tap the pause button in the top-right corner or send the app to the background. Tap **Continue** to resume the run or remaining countdown.

The top row shows actual distance travelled, the best **completed** distance to beat, current speed in metres per second, and five hearts for lives. The row below shows the current score and best completed score. Distance contributes one point per metre; stars and defeated enemies add bonus points without changing distance. Red hearts are available lives and gray hearts are spent lives. Ten small dots beneath the hearts show health from red on the left to green on the right; spent health turns pale gray with 50% transparency from right to left. The hovercar floats two world pixels above the terrain and tips its nose with slopes and jumps. Its cockpit blinks light blue for half a second after taking damage.

After 500 m of actual running, stationary orange turrets may appear on flat ground, gentle slopes, ledges, in depressions, or on suspended platforms, with empty stretches between them. Enemies spawn at least 10 m away from terrain and platform edges, vertical faces, holes, and slopes steeper than 45°; the full 5 m patrol range of moving enemies also respects this clearance. After 1200 m, red stick figures with simple guns may also appear; they move randomly within 5 m of their spawn point and make small jumps reaching 20% of the runner's regular jump height, including while on a platform. Turrets fire orange projectiles from their barrels; red figures fire red projectiles from their guns, including mid-jump. Each projectile travels horizontally at the height where it was fired and disappears when it meets yellow terrain or a platform. Landing on the upper quarter of an orange enemy defeats it without losing health for 300 points; other contact costs two health and awards 100 points. Red enemies award 400 points for a clean landing or 200 points with two health lost. The reward appears as rising text in the enemy's color. Touching a projectile costs one health point and causes a small upward bounce.

Bright collectible stars appear singly or in groups of up to ten, separated by 100–750 m. Their bottom points sit between half a car height and one jump height above the terrain or a platform. Stars over holes sit at maximum jump height, and stars beneath platforms fit below their undersides. Blue stars unlock after 200 m for 10 points, green after 400 m for 20, yellow after 600 m for 30, orange after 800 m for 40, and red after 1000 m for 50. Groups can mix unlocked colors. Touching a star removes it and creates a brief firework in its color.

Every three seconds spent moving increases base speed slightly. Uphill slopes reduce horizontal speed according to incline and gravity; downhill slopes increase it. The car keeps some momentum while entering a slope, and speed gradually returns to its base value after leaving it. Water entry slows horizontal travel and begins sinking at one quarter of entry speed. Each tap gives a small upward bounce; faster entry means faster sinking and calls for quicker taps. The far bank launches the car onto land at one quarter of its entry speed. Fully drowning or losing all ten health points costs one life. With lives remaining, the car moves back 50 m (or to the start), health refills, and a three-second countdown starts before continuing on the same course. Five hearts appear in the middle during the countdown; the newly spent heart fades from red to gray. If that spot is unsafe, the car moves a little farther back to safe ground. The longest distance reached, earned points, collected stars, and defeated enemies remain part of the run. After the fifth life is lost, the run ends; new best distance and score are saved, and **Restart** starts a new course with five lives. Both records remain available after restarting the app.

The game is designed for landscape orientation. It does not require internet access or any runtime permissions.

## Editing and building

Open this directory as a project in Android Studio. It uses the Android Gradle plugin 8.7.3, Java 8 source compatibility, compile SDK 35, and no external runtime libraries. Run `./gradlew assembleDebug` (or `gradlew.bat assembleDebug` on Windows) to build a new debug APK. Android Studio may need to download Gradle and the Android SDK on first use. The checked-in source does not include a private signing key; new debug builds may need the previously installed prototype removed before installation.

### Downloading a test APK from GitHub

Every update to `main` runs [Build Android APK](https://github.com/svasylevskyi/skybound-runner/actions/workflows/build-apk.yml). To build the current `main` again, open that page, select **Run workflow**, choose `main`, and start the run. When it succeeds, open the run and download **SkyboundRunner-debug.apk** from **Artifacts**. The APK is ready to install on Android without extracting a ZIP; GitHub retains it for 30 days.

Each run signs the APK with a new debug key. If an older build of the game is already installed, uninstall it before installing the new APK. Uninstalling also clears the saved best distance.

If you have Android SDK platform 35 and build tools 35.0.0 but no Gradle, you can also run `ANDROID_HOME=/path/to/android-sdk bash tools/build_with_sdk.sh` from this directory. This creates `SkyboundRunner-debug.apk` at the project root.

The gameplay is in `app/src/main/java/com/stepan/skyboundrunner/RunnerEngine.java`, independently of Android APIs; `SceneryLayout.java` generates deterministic background geometry, and `RunnerView.java` handles drawing and touch. The executable core checks are in `tools/RunnerEngineChecks.java` and can run with:

```bash
mkdir -p /tmp/skybound-checks
javac -d /tmp/skybound-checks app/src/main/java/com/stepan/skyboundrunner/RunnerEngine.java app/src/main/java/com/stepan/skyboundrunner/SceneryLayout.java tools/RunnerEngineChecks.java
java -cp /tmp/skybound-checks com.stepan.skyboundrunner.RunnerEngineChecks
```

This is a test build signed with a debug key. For Play Store distribution it needs a release signing key and a publication review.
