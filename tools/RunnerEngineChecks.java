package com.stepan.skyboundrunner;

/** Run with plain javac/java; verifies geometry and the core input/collision rules. */
public final class RunnerEngineChecks {
    private static void check(boolean good, String message) {
        if (!good) throw new AssertionError(message);
    }

    private static RunnerEngine started(long seed) {
        RunnerEngine game = new RunnerEngine();
        game.startNewGame(seed);
        check(game.getMode() == RunnerEngine.Mode.COUNTDOWN, "countdown begins");
        for (int i = 0; i < 181; ++i) game.update(1f / 60f);
        check(game.getMode() == RunnerEngine.Mode.RUNNING, "countdown finishes");
        return game;
    }

    private static void testFirstJumpHint() {
        RunnerEngine game = started(1L);
        check(!game.hasCompletedFirstJump(), "jump hint starts visible");
        game.jump();
        check(game.isControlledJumpInProgress() && !game.hasCompletedFirstJump(),
                "jump hint remains visible while first controlled jump is in the air");

        RunnerEngine restored = new RunnerEngine();
        restored.restore(game.getSeed(), game.getLives(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode(), game.getHealth(),
                game.getDamageRecoverySeconds(), game.getFarthestX(),
                game.getVisibleWorldWidth(), game.getAntagonistTimers(),
                game.getProjectileState(), game.getDefeatedAntagonists(),
                game.getBonusPoints(), game.hasCompletedFirstJump(),
                game.isControlledJumpInProgress());
        check(restored.getMode() == RunnerEngine.Mode.PAUSED
                        && restored.isControlledJumpInProgress()
                        && !restored.hasCompletedFirstJump(),
                "activity recreation retains an unfinished first jump");
        restored.continueGame();
        for (int step = 0; step < 160 && !restored.hasCompletedFirstJump(); ++step) {
            restored.update(1f / 120f);
        }
        check(restored.hasCompletedFirstJump() && !restored.isControlledJumpInProgress()
                        && restored.getLives() == RunnerEngine.MAX_LIVES,
                "hint disappears after the first controlled jump lands");
        restored.startNewGame(2L);
        check(!restored.hasCompletedFirstJump(), "new run shows jump hint again");
    }

    private static void testLayout() {
        boolean sawGentle = false;
        boolean sawModerate = false;
        boolean sawSteep = false;
        boolean sawVertical = false;
        boolean sawHeightVariety = false;
        float previousHeight = -1f;
        for (long seed = 0; seed < 100; ++seed) {
            RunnerEngine game = started(seed);
            game.generateAhead(50_000f);
            float previousEnd = -1000f;
            for (RunnerEngine.Hazard hazard : game.getHazards()) {
                check(hazard.x - previousEnd >= 289f, "hazards have recovery room");
                check(hazard.width <= 12000f, "shallow ramps remain finite");
                check(hazard.x <= hazard.flatStart && hazard.flatStart < hazard.flatEnd
                        && hazard.flatEnd <= hazard.end(), "ramp and flat sections are ordered");
                if (hazard.type != RunnerEngine.HazardType.HOLE) {
                    float height = hazard.height;
                    check(height >= 40f && height <= RunnerEngine.MAX_TERRAIN_HEIGHT
                                    && height < RunnerEngine.JUMP_HEIGHT,
                            "every elevation and depression is lower than a regular jump");
                    sawHeightVariety |= previousHeight >= 0f && previousHeight != height;
                    previousHeight = height;
                    check(hazard.entranceDegrees >= 1 && hazard.entranceDegrees <= 90
                                    && hazard.exitDegrees >= 1 && hazard.exitDegrees <= 90,
                            "height-changing edges range from 1 to 90 degrees");
                    check(Math.abs(hazard.flatStart - hazard.x -
                            horizontalRun(height, hazard.entranceDegrees)) < .12f,
                            "entrance has its stated incline");
                    check(Math.abs(hazard.end() - hazard.flatEnd -
                            horizontalRun(height, hazard.exitDegrees)) < .12f,
                            "exit has its stated incline");
                    float top = hazard.type == RunnerEngine.HazardType.WALL
                            ? RunnerEngine.GROUND_Y - height : RunnerEngine.GROUND_Y + height;
                    check(Math.abs(hazard.surfaceYAt((hazard.flatStart + hazard.flatEnd) / 2f)
                            - top) < .01f, "flat section uses its generated height");
                    sawGentle |= hazard.entranceDegrees <= 10 || hazard.exitDegrees <= 10;
                    sawModerate |= hazard.entranceDegrees >= 25 && hazard.entranceDegrees <= 55;
                    sawSteep |= hazard.entranceDegrees >= 70 && hazard.entranceDegrees < 90;
                    sawVertical |= hazard.entranceDegrees == 90 || hazard.exitDegrees == 90;
                }
                previousEnd = hazard.end();
            }
        }
        check(sawGentle && sawModerate && sawSteep && sawVertical && sawHeightVariety,
                "generation includes varied heights, gentle, moderate, steep and vertical edges");
    }

    private static void testSceneryLayout() {
        long[] seeds = {1L, 328493L, Long.MIN_VALUE, Long.MAX_VALUE};
        float[] widths = {540f, 720f, 960f, 1170f, 1440f};
        boolean[] viewsSeen = new boolean[5];
        boolean movedPalmAwayFromHole = false;
        for (long seed : seeds) {
            RunnerEngine course = new RunnerEngine();
            course.startNewGame(seed);
            course.generateAhead(75_000f);
            for (float width : widths) {
                float step = SceneryLayout.palmStep(width);
                for (int frame = 0; frame < 400; frame++) {
                    float left = frame * width / 9f * SceneryLayout.PALM_SCROLL;
                    int visible = 0;
                    for (int index = SceneryLayout.firstIndex(left, step);
                            index <= SceneryLayout.lastIndex(left + width, step); index++) {
                        float x = SceneryLayout.groundedPalmX(seed, index, width,
                                course.getHazards());
                        movedPalmAwayFromHole |= x != SceneryLayout.palmX(seed, index, width);
                        if (x + SceneryLayout.PALM_EXTENT >= left
                                && x - SceneryLayout.PALM_EXTENT <= left + width) visible++;
                        check(x == SceneryLayout.groundedPalmX(seed, index, width,
                                        course.getHazards()),
                                "palm positions repeat for the same course");
                        for (RunnerEngine.Hazard hazard : course.getHazards()) {
                            if (hazard.type == RunnerEngine.HazardType.HOLE) {
                                check(x + SceneryLayout.PALM_EXTENT <= hazard.x
                                                || x - SceneryLayout.PALM_EXTENT >= hazard.end(),
                                        "palm canopy stays clear of holes");
                            }
                        }
                        int view = SceneryLayout.palmView(seed, index);
                        viewsSeen[view] = true;
                        float halfRoot = SceneryLayout.palmTrunkWidth(view) / 2f + 1f;
                        check(!Float.isNaN(course.surfaceYAt(x - halfRoot))
                                        && !Float.isNaN(course.surfaceYAt(x + halfRoot)),
                                "palm trunk is supported by terrain");
                    }
                    check(visible >= 3 && visible <= 8,
                            "each scrolling screen contains three to eight palm trees");
                }
            }
            for (int index = -20; index < 200; index++) {
                for (boolean near : new boolean[]{false, true}) {
                    float gap = SceneryLayout.BUILDING_STEP
                            - SceneryLayout.buildingWidth(seed, index, near);
                    check(gap >= 1f && gap <= 7f,
                            "skyscrapers have only narrow intermittent gaps");
                    float height = SceneryLayout.buildingHeight(seed, index, near);
                    check(height >= (near ? 125f : 85f)
                                    && height < (near ? 260f : 210f),
                            "seeded skyscrapers have bounded heights");
                }
            }
        }
        float shortest = Float.MAX_VALUE;
        float tallest = 0f;
        for (int view = 0; view < viewsSeen.length; view++) {
            check(viewsSeen[view], "all five seeded palm shapes appear");
            shortest = Math.min(shortest, SceneryLayout.palmHeight(view));
            tallest = Math.max(tallest, SceneryLayout.palmHeight(view));
        }
        check(tallest - shortest == 5f
                        && SceneryLayout.palmTrunkWidth(0) != SceneryLayout.palmTrunkWidth(1),
                "the five palm shapes vary in height by at most five pixels and in trunk width");
        check(movedPalmAwayFromHole, "hole avoidance is exercised by a generated course");
    }

    private static SceneryLayout.GrassSegment grassAt(
            java.util.List<SceneryLayout.GrassSegment> segments, float x) {
        for (SceneryLayout.GrassSegment segment : segments) {
            if (segment.fromX <= x && x < segment.toX) return segment;
        }
        return null;
    }

    private static void testGrassCoverage() {
        check(SceneryLayout.GRASS_HEIGHT == RunnerEngine.PLAYER_HEIGHT / 4f,
                "grass blades are one quarter of the hovercar height");
        boolean sawHole = false;
        boolean sawGentle = false;
        boolean sawSteep = false;
        boolean sawWall = false;
        boolean sawDip = false;
        for (long seed = 0; seed < 40; seed++) {
            RunnerEngine course = new RunnerEngine();
            course.startNewGame(seed);
            course.generateAhead(20_000f);
            for (RunnerEngine.Hazard hazard : course.getHazards()) {
                java.util.List<SceneryLayout.GrassSegment> grass =
                        SceneryLayout.visibleGrass(course.getHazards(),
                                hazard.x - 20f, hazard.end() + 20f);
                check(grassAt(grass, hazard.x - 10f) != null
                                && grassAt(grass, hazard.end() + 10f) != null,
                        "grass resumes on flat ground beside obstacles");
                if (hazard.type == RunnerEngine.HazardType.HOLE) {
                    sawHole = true;
                    check(grassAt(grass, (hazard.x + hazard.end()) / 2f) == null,
                            "grass does not bridge holes");
                    continue;
                }
                sawWall |= hazard.type == RunnerEngine.HazardType.WALL;
                sawDip |= hazard.type == RunnerEngine.HazardType.DIP;
                float flatMiddle = (hazard.flatStart + hazard.flatEnd) / 2f;
                check(grassAt(grass, flatMiddle) != null,
                        "grass covers raised ledges and depression floors");
                for (int side = 0; side < 2; side++) {
                    int degrees = side == 0 ? hazard.entranceDegrees : hazard.exitDegrees;
                    float from = side == 0 ? hazard.x : hazard.flatEnd;
                    float to = side == 0 ? hazard.flatStart : hazard.end();
                    if (to - from < .5f) continue;
                    float middle = (from + to) / 2f;
                    SceneryLayout.GrassSegment segment = grassAt(grass, middle);
                    if (degrees > 45) {
                        sawSteep = true;
                        check(segment == null, "grass stops on slopes over 45 degrees");
                    } else {
                        sawGentle = true;
                        check(segment != null
                                        && Math.abs(segment.surfaceYAt(middle)
                                        - hazard.surfaceYAt(middle)) < .1f,
                                "grass follows gentle slopes at the terrain surface");
                    }
                }
            }
        }
        check(sawHole && sawGentle && sawSteep && sawWall && sawDip,
                "grass rules cover holes, both terrain types, and both incline ranges");
    }

    private static float horizontalRun(float height, int degrees) {
        return degrees == 90 ? 0f
                : (float) (height / Math.tan(Math.toRadians(degrees)));
    }

    private static void testFirstHazards() {
        boolean sawHole = false;
        boolean sawWall = false;
        boolean sawDip = false;
        for (long trial = 0; trial < 30; ++trial) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine game = started(seed);
            RunnerEngine.Hazard first = game.getHazards().get(0);
            // Exceptionally shallow ramps can extend past the 500 m enemy unlock.
            if (first.end() > 5900f) continue;
            sawHole |= first.type == RunnerEngine.HazardType.HOLE;
            sawWall |= first.type == RunnerEngine.HazardType.WALL;
            sawDip |= first.type == RunnerEngine.HazardType.DIP;
            boolean jumped = false;
            for (int step = 0; step < 7000 && game.getPlayerX() < first.end() + 50f; ++step) {
                if (game.isWaitingForJump()) game.jump();
                if (first.type != RunnerEngine.HazardType.DIP && !jumped
                        && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        >= first.x - jumpLead(first.type)) {
                    game.jump();
                    jumped = true;
                }
                if (first.type == RunnerEngine.HazardType.DIP && first.exitDegrees == 90
                        && !jumped && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        >= first.end() - 120f
                        && game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                        >= RunnerEngine.GROUND_Y + first.height - 1f) {
                    game.jump();
                    jumped = true;
                }
                float beforeX = game.getPlayerX();
                float beforeY = game.getPlayerY();
                game.update(1f / 120f);
                check(game.getLives() == RunnerEngine.MAX_LIVES, "well timed jump clears first hazard (seed "
                        + seed + ", " + first.type + ", beforeX=" + beforeX + ", beforeY="
                        + beforeY + ", wall=" + first.x + ".." + first.end() + ")");
                if (first.type != RunnerEngine.HazardType.DIP) {
                    check(game.getHealth() == RunnerEngine.MAX_HEALTH,
                            "timed jump avoids damage on a ledge or hole: seed=" + seed
                                    + ", type=" + first.type + ", end=" + first.end()
                                    + ", player=" + game.getPlayerX());
                }
            }
            if (first.type != RunnerEngine.HazardType.DIP || first.exitDegrees == 90) {
                check(jumped, "jump was exercised where needed");
            }
            check(game.getPlayerX() > first.end() + 50f, "first obstacle cleared");
        }
        check(sawHole && sawWall && sawDip, "generator emits every obstacle type");
    }

    private static float jumpLead(RunnerEngine.HazardType type) {
        if (type == RunnerEngine.HazardType.HOLE) return 45f;
        if (type == RunnerEngine.HazardType.WALL) return 88f;
        return 0f;
    }

    private static long seedFor(RunnerEngine.HazardType type) {
        for (long trial = 0; trial < 3000; ++trial) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine game = started(seed);
            if (game.getHazards().get(0).type == type
                    && (type != RunnerEngine.HazardType.DIP
                    || game.getHazards().get(0).exitDegrees == 90)) return seed;
        }
        throw new AssertionError("no generated " + type);
    }

    private static long seedForBlockingWall() {
        return seedForSlopes(RunnerEngine.HazardType.WALL, 90, -1);
    }

    private static long seedForSteepWall(int minAngle, int maxAngle) {
        for (long trial = 0; trial < 3000; ++trial) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine.Hazard first = started(seed).getHazards().get(0);
            if (first.type == RunnerEngine.HazardType.WALL
                    && first.entranceDegrees >= minAngle
                    && first.entranceDegrees <= maxAngle) return seed;
        }
        throw new AssertionError("no wall between " + minAngle + " and " + maxAngle + " degrees");
    }

    private static long seedForTall(RunnerEngine.HazardType type, int minEntranceAngle) {
        for (long trial = 0; trial < 10_000; ++trial) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine game = new RunnerEngine();
            game.startNewGame(seed);
            RunnerEngine.Hazard first = game.getHazards().get(0);
            if (first.type == type && first.height >= 98f
                    && first.entranceDegrees >= minEntranceAngle) return seed;
        }
        throw new AssertionError("no tall " + type + " generated");
    }

    private static void testTallTerrainIsPlayable() {
        RunnerEngine wallRun = started(seedForTall(RunnerEngine.HazardType.WALL, 85));
        RunnerEngine.Hazard wall = wallRun.getHazards().get(0);
        for (int i = 0; i < 600 && !wallRun.isWaitingForJump(); i++) {
            wallRun.update(1f / 120f);
        }
        check(wallRun.isWaitingForJump() && wallRun.getLives() == RunnerEngine.MAX_LIVES,
                "almost maximum-height ledge stops the runner without a lost life");
        wallRun.jump();
        for (int i = 0; i < 160 && wallRun.getPlayerX() <= wall.flatStart + 10f; i++) {
            wallRun.update(1f / 120f);
        }
        check(wallRun.getPlayerX() > wall.flatStart + 10f
                        && wallRun.getLives() == RunnerEngine.MAX_LIVES,
                "normal jump clears an almost maximum-height ledge");

        RunnerEngine dipRun = started(seedForTall(RunnerEngine.HazardType.DIP, 45));
        RunnerEngine.Hazard dip = dipRun.getHazards().get(0);
        dipRun.restore(dipRun.getSeed(), RunnerEngine.MAX_LIVES,
                dip.flatStart + 20f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 0d, 0f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        dipRun.continueGame();
        boolean landed = false;
        for (int i = 0; i < 160 && !landed; i++) {
            dipRun.update(1f / 120f);
            landed = dipRun.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                    >= RunnerEngine.GROUND_Y + dip.height - 1f;
        }
        check(landed && dipRun.getLives() == RunnerEngine.MAX_LIVES
                        && dipRun.getMode() == RunnerEngine.Mode.RUNNING,
                "deep depression floor is safe even below the old hole threshold");
        dipRun.jump();
        float highestFeet = Float.POSITIVE_INFINITY;
        for (int i = 0; i < 75; i++) {
            dipRun.update(1f / 120f);
            highestFeet = Math.min(highestFeet,
                    dipRun.getPlayerY() + RunnerEngine.PLAYER_HEIGHT);
        }
        check(highestFeet < RunnerEngine.GROUND_Y
                        && dipRun.getLives() == RunnerEngine.MAX_LIVES,
                "normal jump reaches above the lip of a deep depression");
    }

    private static long seedForSlopes(RunnerEngine.HazardType type,
                                      int entrance, int exit) {
        for (long trial = 0; trial < 10000; ++trial) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine.Hazard first = started(seed).getHazards().get(0);
            boolean entryMatches = entrance == 90 ? first.entranceDegrees == 90
                    : Math.abs(first.entranceDegrees - entrance) <= 8;
            boolean exitMatches = exit < 0 || (exit == 90 ? first.exitDegrees == 90
                    : Math.abs(first.exitDegrees - exit) <= 8);
            if (first.type == type && entryMatches && exitMatches) return seed;
        }
        throw new AssertionError("no course with " + type + " " + entrance + "/" + exit);
    }

    private static void checkSlopeRun(RunnerEngine.HazardType type,
                                      int entrance, int exit) {
        RunnerEngine game = started(seedForSlopes(type, entrance, exit));
        RunnerEngine.Hazard first = game.getHazards().get(0);
        boolean enteredFirstSlope = false;
        boolean enteredLastSlope = false;
        boolean sawRecovery = false;
        boolean sawBaseAgain = false;
        float initialRecoveryDeviation = Float.NaN;
        for (int step = 0; step < 1500 && !sawBaseAgain; ++step) {
            game.update(1f / 120f);
            check(game.getLives() == RunnerEngine.MAX_LIVES, "sloped " + type + " traversed without damage");
            float center = game.getPlayerX() + RunnerEngine.PLAYER_WIDTH / 2f;
            float feet = game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT;
            if (center > first.x + 5f && center < first.flatStart - 5f) {
                check(Math.abs(feet - first.surfaceYAt(center)) < 1.5f,
                        "runner follows first incline");
                check(Math.abs(game.getPlayerPitchDegrees() - first.slopeDegreesAt(center)) < .01f,
                        "hovercar tips with the first incline");
                if (type == RunnerEngine.HazardType.WALL) {
                    check(game.getSpeed() < game.getBaseSpeed(), "uphill slows runner");
                } else {
                    check(game.getSpeed() > game.getBaseSpeed(), "downhill speeds runner");
                }
                enteredFirstSlope = true;
            }
            if (center > first.flatEnd + 5f && center < first.end() - 5f) {
                check(Math.abs(feet - first.surfaceYAt(center)) < 1.5f,
                        "runner follows exit incline");
                check(Math.abs(game.getPlayerPitchDegrees() - first.slopeDegreesAt(center)) < .01f,
                        "hovercar tips with the exit incline");
                if (type == RunnerEngine.HazardType.WALL) {
                    check(game.getSpeed() > game.getBaseSpeed(), "downhill speeds runner");
                } else {
                    check(game.getSpeed() < game.getBaseSpeed(), "uphill slows runner");
                }
                enteredLastSlope = true;
            }
            if (center > first.end() + 5f && center < first.end() + 30f
                    && Float.isNaN(initialRecoveryDeviation)) {
                initialRecoveryDeviation = Math.abs(game.getSpeed() - game.getBaseSpeed());
                check(initialRecoveryDeviation > .01f,
                        "slope speed persists briefly beyond the incline");
            }
            if (center > first.end() + 60f && center < first.end() + 100f) {
                check(Math.abs(game.getSpeed() - game.getBaseSpeed())
                                <= initialRecoveryDeviation + .01f,
                        "slope effect decays toward base speed");
                sawRecovery = true;
            }
            if (center > first.end() + 220f) {
                check(Math.abs(game.getSpeed() - game.getBaseSpeed()) < .01f,
                        "speed returns to current base speed");
                sawBaseAgain = true;
            }
        }
        check(enteredFirstSlope && enteredLastSlope && sawRecovery && sawBaseAgain,
                "both ramps and their recovery were observed");
        check(Math.abs(game.getSpeedMetersPerSecond() * RunnerEngine.WORLD_UNITS_PER_METER
                - game.getSpeed()) < .01f, "displayed metres per second match world movement");
    }

    private static void testSlopes() {
        checkSlopeRun(RunnerEngine.HazardType.WALL, 30, 45);
        checkSlopeRun(RunnerEngine.HazardType.WALL, 45, 30);
        checkSlopeRun(RunnerEngine.HazardType.DIP, 30, 45);
        checkSlopeRun(RunnerEngine.HazardType.DIP, 45, 30);

        RunnerEngine verticalEntry = started(seedForSlopes(RunnerEngine.HazardType.DIP, 90, 30));
        RunnerEngine.Hazard dip = verticalEntry.getHazards().get(0);
        boolean climbedOut = false;
        for (int step = 0; step < 1500 && !climbedOut; ++step) {
            verticalEntry.update(1f / 120f);
            check(verticalEntry.getLives() == RunnerEngine.MAX_LIVES
                            && verticalEntry.getHealth() == RunnerEngine.MAX_HEALTH,
                    "vertical drop into a depression remains harmless");
            if (verticalEntry.getPlayerX() + RunnerEngine.PLAYER_WIDTH / 2f
                    > dip.end() + 40f) climbedOut = true;
        }
        check(climbedOut, "the sloped far side lets the runner leave without jumping");

        RunnerEngine savedOnSlope = started(seedForSlopes(RunnerEngine.HazardType.WALL, 45, 30));
        RunnerEngine.Hazard ledge = savedOnSlope.getHazards().get(0);
        for (int step = 0; step < 1500
                && savedOnSlope.getPlayerX() + RunnerEngine.PLAYER_WIDTH / 2f
                < ledge.x + 20f; ++step) savedOnSlope.update(1f / 120f);
        check(savedOnSlope.getSpeed() < savedOnSlope.getBaseSpeed(),
                "saved run is on an uphill slope");
        RunnerEngine restored = new RunnerEngine();
        restored.restore(savedOnSlope.getSeed(), savedOnSlope.getLives(),
                savedOnSlope.getPlayerX(), savedOnSlope.getPlayerY(),
                savedOnSlope.getVelocityY(), savedOnSlope.getCountdownSeconds(),
                savedOnSlope.getElapsedRunSeconds(),
                savedOnSlope.getTerrainSpeedMultiplier(),
                savedOnSlope.getMode(), savedOnSlope.getResumeMode());
        check(restored.getMode() == RunnerEngine.Mode.PAUSED
                && Math.abs(restored.getSpeed() - savedOnSlope.getSpeed()) < .01f,
                "restored run keeps its slope speed while paused");
        restored.continueGame();
        restored.update(1f / 120f);
        check(restored.getLives() == RunnerEngine.MAX_LIVES, "restored slope can be continued");
    }

    private static void testHovercarJumpPitch() {
        RunnerEngine game = started(1L);
        check(game.getPlayerPitchDegrees() == 0f, "hovercar is level on flat ground");
        game.jump();
        check(game.getPlayerPitchDegrees() > 0f, "hovercar tips nose up while rising");
        for (int step = 0; step < 65; step++) game.update(1f / 120f);
        check(game.getVelocityY() > 0f && game.getPlayerPitchDegrees() < 0f,
                "hovercar tips nose down while descending");
    }

    private static void testLandingOnSteepRisingSlopes() {
        for (RunnerEngine.HazardType type : new RunnerEngine.HazardType[]{
                RunnerEngine.HazardType.WALL, RunnerEngine.HazardType.DIP}) {
            boolean checked = false;
            for (long trial = 0; trial < 1000 && !checked; trial++) {
                long seed = trial * 0x9E3779B97F4A7C15L;
                RunnerEngine layout = new RunnerEngine();
                layout.startNewGame(seed);
                RunnerEngine.Hazard slope = layout.getHazards().get(0);
                int angle = type == RunnerEngine.HazardType.WALL
                        ? slope.entranceDegrees : slope.exitDegrees;
                float start = type == RunnerEngine.HazardType.WALL
                        ? slope.x : slope.flatEnd;
                float end = type == RunnerEngine.HazardType.WALL
                        ? slope.flatStart : slope.end();
                if (slope.type != type || angle < 64 || angle > 68
                        || end - start < 25f) continue;

                float center = (start + end) / 2f;
                float x = center - RunnerEngine.PLAYER_WIDTH / 2f;
                float feet = slope.surfaceYAt(center) - 2f;
                RunnerEngine game = new RunnerEngine();
                game.restore(seed, RunnerEngine.MAX_LIVES, x,
                        feet - RunnerEngine.PLAYER_HEIGHT, 180f, 0f, 0d, 1f,
                        RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
                game.continueGame();
                game.update(1f / 120f);
                float afterCenter = game.getPlayerX() + RunnerEngine.PLAYER_WIDTH / 2f;
                float surface = slope.surfaceYAt(afterCenter);
                check(Math.abs(game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT - surface) < .15f
                                && game.getVelocityY() == 0f
                                && game.getLives() == RunnerEngine.MAX_LIVES,
                        "descending runner lands on steep " + type + " slope: seed=" + seed);
                checked = true;
            }
            check(checked, "found a reproducible steep " + type + " landing");
        }
    }

    private static void testLandingAcrossShortRisingSlopes() {
        for (RunnerEngine.HazardType type : new RunnerEngine.HazardType[]{
                RunnerEngine.HazardType.WALL, RunnerEngine.HazardType.DIP}) {
            boolean checked = false;
            for (long trial = 0; trial < 5000 && !checked; trial++) {
                long seed = trial * 0x9E3779B97F4A7C15L;
                RunnerEngine layout = new RunnerEngine();
                layout.startNewGame(seed);
                RunnerEngine.Hazard slope = layout.getHazards().get(0);
                float start = type == RunnerEngine.HazardType.WALL
                        ? slope.x : slope.flatEnd;
                float end = type == RunnerEngine.HazardType.WALL
                        ? slope.flatStart : slope.end();
                if (slope.type != type || end - start < 10f || end - start > 11.5f)
                    continue;

                float center = start - 2f;
                float feet = (type == RunnerEngine.HazardType.WALL
                        ? RunnerEngine.GROUND_Y : RunnerEngine.GROUND_Y + slope.height) - 4f;
                RunnerEngine game = new RunnerEngine();
                game.restore(seed, RunnerEngine.MAX_LIVES,
                        center - RunnerEngine.PLAYER_WIDTH / 2f,
                        feet - RunnerEngine.PLAYER_HEIGHT, 180f, 0f, 600d, 1f,
                        RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
                game.continueGame();
                game.update(1f / 120f);
                float afterCenter = game.getPlayerX() + RunnerEngine.PLAYER_WIDTH / 2f;
                check(game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                                <= slope.surfaceYAt(afterCenter) + .15f
                                && game.getVelocityY() == 0f
                                && game.getLives() == RunnerEngine.MAX_LIVES,
                        "runner cannot skip a short rising " + type + " slope: seed=" + seed);
                checked = true;
            }
            check(checked, "found a short rising " + type + " slope");
        }
    }

    private static void testSteepSlopeSlideAndJump() {
        RunnerEngine game = started(seedForSteepWall(70, 78));
        RunnerEngine.Hazard ledge = game.getHazards().get(0);
        boolean slidBackward = false;
        boolean restoredSlide = false;
        for (int step = 0; step < 2000 && !game.isWaitingForJump(); step++) {
            float beforeX = game.getPlayerX();
            game.update(1f / 120f);
            if (game.getSpeed() < 0f) {
                slidBackward = true;
                check(game.getPlayerX() < beforeX,
                        "steep uphill reverses horizontal movement");
                if (!restoredSlide) {
                    RunnerEngine resumed = new RunnerEngine();
                    resumed.restore(game.getSeed(), game.getLives(), game.getPlayerX(),
                            game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                            game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                            game.getMode(), game.getResumeMode());
                    check(resumed.getSpeed() < 0f && resumed.getMode() == RunnerEngine.Mode.PAUSED,
                            "paused run retains a backward slope speed");
                    float savedX = resumed.getPlayerX();
                    resumed.continueGame();
                    resumed.update(1f / 120f);
                    check(resumed.getPlayerX() < savedX,
                            "restored runner keeps sliding until reaching the foot");
                    restoredSlide = true;
                }
            }
        }
        check(slidBackward && restoredSlide && game.isWaitingForJump()
                        && game.getSpeed() == 0f
                        && game.getLives() == RunnerEngine.MAX_LIVES
                        && game.getHealth() == RunnerEngine.MAX_HEALTH
                        && Math.abs(game.getPlayerX() + RunnerEngine.PLAYER_WIDTH / 2f
                        - ledge.x) < .1f,
                "runner slides back to the foot and waits there without damage");
        game.jump();
        for (int step = 0; step < 180 && game.getPlayerX() <= ledge.flatStart + 10f;
                step++) game.update(1f / 120f);
        check(game.getPlayerX() > ledge.flatStart + 10f
                        && game.getLives() == RunnerEngine.MAX_LIVES,
                "jump from the stopped slope reaches the elevated surface");

        RunnerEngine nearVertical = started(seedForSteepWall(88, 89));
        RunnerEngine.Hazard narrow = nearVertical.getHazards().get(0);
        for (int step = 0; step < 1000 && !nearVertical.isWaitingForJump(); step++) {
            nearVertical.update(1f / 120f);
        }
        check(nearVertical.isWaitingForJump()
                        && nearVertical.getPlayerX() <= narrow.flatStart
                        && nearVertical.getLives() == RunnerEngine.MAX_LIVES,
                "near-vertical ramp cannot be crossed in one update step");
    }

    private static void testDepression() {
        RunnerEngine game = started(seedFor(RunnerEngine.HazardType.DIP));
        RunnerEngine.Hazard dip = game.getHazards().get(0);
        for (int step = 0; step < 5000 && !game.isWaitingForJump();
                ++step) game.update(1f / 120f);
        check(game.isWaitingForJump() && game.getLives() == RunnerEngine.MAX_LIVES
                        && game.getMode() == RunnerEngine.Mode.RUNNING
                        && game.getHealth() == RunnerEngine.MAX_HEALTH
                        && game.getSpeed() == 0f
                        && Math.abs(game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        - dip.end()) < .1f,
                "vertical depression wall stops the runner without damage");
        game.jump();
        for (int step = 0; step < 200 && game.getPlayerX() <= dip.end() + 10f;
                ++step) game.update(1f / 120f);
        check(game.getPlayerX() > dip.end() + 10f
                        && game.getLives() == RunnerEngine.MAX_LIVES,
                "jump from depression wall clears the obstacle");
    }

    private static void testHoleAndExhaustedHealth() {
        RunnerEngine holeRun = started(seedFor(RunnerEngine.HazardType.HOLE));
        RunnerEngine.Hazard hole = holeRun.getHazards().get(0);
        for (int step = 0; step < 1000 && holeRun.getLives() == RunnerEngine.MAX_LIVES;
                ++step) holeRun.update(1f / 120f);
        check(holeRun.getLives() == RunnerEngine.MAX_LIVES - 1
                        && holeRun.getMode() == RunnerEngine.Mode.COUNTDOWN
                        && holeRun.getFarthestX() >= hole.x
                        && holeRun.getHealth() == RunnerEngine.MAX_HEALTH,
                "falling into a hole costs one life and restores full health");
        check(holeRun.getPlayerX() >= 140f
                        && Math.abs(holeRun.getFarthestX() - holeRun.getPlayerX() - 500f) < .1f,
                "falling in the first hole respawns 50 metres back");

        long seed = seedFor(RunnerEngine.HazardType.HOLE);
        RunnerEngine projectileRun = new RunnerEngine();
        projectileRun.restore(seed, RunnerEngine.MAX_LIVES, 180f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING,
                1, 0f, 180f, 960f, null,
                new float[]{196f, RunnerEngine.GROUND_Y - 23f, -380f, 0f});
        projectileRun.continueGame();
        projectileRun.update(1f / 120f);
        check(projectileRun.getLives() == RunnerEngine.MAX_LIVES - 1
                        && projectileRun.getMode() == RunnerEngine.Mode.COUNTDOWN
                        && projectileRun.getPlayerX() == 140f
                        && projectileRun.getHealth() == RunnerEngine.MAX_HEALTH
                        && projectileRun.getProjectiles().isEmpty(),
                "projectile that empties health costs one life and respawns at the start");
    }

    private static void testSafeRespawn() {
        for (long trial = 0; trial < 200; trial++) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine layout = started(seed);
            RunnerEngine.Hazard first = layout.getHazards().get(0);
            if (first.type != RunnerEngine.HazardType.HOLE) continue;
            float collisionX = first.x + 500f + first.width / 2f;
            layout.generateAhead(collisionX + 100f);
            if (!openGround(layout, collisionX - 30f, collisionX + 50f)) continue;
            RunnerEngine game = new RunnerEngine();
            game.restore(seed, RunnerEngine.MAX_LIVES, collisionX,
                    RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                    0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING,
                    1, 0f, collisionX, 960f, null,
                    new float[]{collisionX + 16f, RunnerEngine.GROUND_Y - 23f, -380f, 0f});
            game.continueGame();
            game.update(1f / 120f);
            check(game.getLives() == RunnerEngine.MAX_LIVES - 1
                            && game.getMode() == RunnerEngine.Mode.COUNTDOWN
                            && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH + 110f
                            <= first.x + 2f
                            && game.getFarthestX() - game.getPlayerX() > 500f
                            && game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                            == RunnerEngine.GROUND_Y,
                    "50-metre target inside a hole moves back to safe ground");
            game.update(3f);
            game.update(1f / 120f);
            check(game.getLives() == RunnerEngine.MAX_LIVES - 1,
                    "safe respawn does not immediately cost another life");
            return;
        }
        throw new AssertionError("no course with open ground 50 metres after a hole");
    }

    private static void testFailureAndPause() {
        RunnerEngine game = started(seedFor(RunnerEngine.HazardType.HOLE));
        RunnerEngine.Hazard hole = game.getHazards().get(0);
        game.loadBestDistance(25);
        for (int i = 0; i < 600 && game.getLives() == RunnerEngine.MAX_LIVES; ++i) {
            game.update(1f / 60f);
        }
        check(game.getLives() == RunnerEngine.MAX_LIVES - 1
                        && game.getHealth() == RunnerEngine.MAX_HEALTH
                        && game.getMode() == RunnerEngine.Mode.COUNTDOWN,
                "falling through a hole costs one life and restores full health");
        check(game.getPlayerX() < hole.x - 350f,
                "falling through a hole sends the runner back about 50 metres");
        int farthest = game.getDistance();
        check(game.getBestDistance() == 25 && farthest > 25,
                "life loss does not record a new high score before game over");
        long initialSeed = game.getSeed();
        float x = game.getPlayerX();
        game.pauseForBackground();
        game.update(10f);
        check(game.getMode() == RunnerEngine.Mode.PAUSED && game.getPlayerX() == x,
                "background pauses the respawn countdown and runner");
        RunnerEngine restored = new RunnerEngine();
        restored.restore(game.getSeed(), game.getLives(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode(), game.getHealth(),
                game.getDamageRecoverySeconds(), game.getFarthestX(),
                game.getVisibleWorldWidth(), game.getAntagonistTimers(),
                game.getProjectileState(), game.getDefeatedAntagonists());
        check(restored.getMode() == RunnerEngine.Mode.PAUSED
                        && restored.getResumeMode() == RunnerEngine.Mode.COUNTDOWN
                        && restored.getLives() == game.getLives()
                        && restored.getDistance() == farthest,
                "activity restoration preserves countdown, lives, and farthest distance");
        game.continueGame();
        game.jump();
        check(game.getVelocityY() == 0f, "jump input is ignored during countdown");
        game.update(2f);
        check(game.getMode() == RunnerEngine.Mode.COUNTDOWN && game.getPlayerX() == x,
                "countdown holds the respawn position");
        game.update(1f);
        check(game.getMode() == RunnerEngine.Mode.RUNNING, "countdown resumes the run");
        game.update(1f / 60f);
        check(game.getPlayerX() > x && game.getDistance() == farthest
                        && game.getSeed() == initialSeed,
                "run continues on the same course with the previous distance");

        while (game.getLives() > 0) {
            int before = game.getLives();
            if (game.getMode() == RunnerEngine.Mode.COUNTDOWN) game.update(3f);
            for (int step = 0; step < 1500 && game.getMode() == RunnerEngine.Mode.RUNNING;
                    ++step) game.update(1f / 120f);
            check(game.getLives() == before - 1,
                    "each unavoided hole costs exactly one life");
            if (game.getLives() > 0) {
                check(game.getMode() == RunnerEngine.Mode.COUNTDOWN
                                && game.getBestDistance() == 25,
                        "each remaining life starts countdown without recording a score");
            }
        }
        check(game.getMode() == RunnerEngine.Mode.GAME_OVER
                        && game.getBestDistance() == game.getDistance()
                        && game.getDistance() >= farthest,
                "fifth life ends the run and saves a new high score");
        float stoppedX = game.getPlayerX();
        game.pauseForBackground();
        game.update(10f);
        game.jump();
        check(game.getMode() == RunnerEngine.Mode.GAME_OVER
                        && game.getPlayerX() == stoppedX,
                "game-over state cannot move or pause into Continue");

        RunnerEngine savedOver = new RunnerEngine();
        savedOver.loadBestDistance(game.getBestDistance());
        savedOver.restore(game.getSeed(), game.getLives(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode(), game.getHealth(),
                game.getDamageRecoverySeconds(), game.getFarthestX(),
                game.getVisibleWorldWidth(), game.getAntagonistTimers(),
                game.getProjectileState(), game.getDefeatedAntagonists());
        check(savedOver.getMode() == RunnerEngine.Mode.GAME_OVER
                        && savedOver.getLives() == 0
                        && savedOver.getBestDistance() == game.getBestDistance(),
                "recreating the activity keeps the finished run and best score");

        RunnerEngine duringCountdown = new RunnerEngine();
        duringCountdown.startNewGame(8L);
        duringCountdown.update(1f);
        duringCountdown.pauseForBackground();
        duringCountdown.continueGame();
        check(duringCountdown.getMode() == RunnerEngine.Mode.COUNTDOWN,
                "Continue resumes an interrupted countdown");

        int best = game.getBestDistance();
        game.startNewGame(456L);
        check(game.getLives() == RunnerEngine.MAX_LIVES
                        && game.getMode() == RunnerEngine.Mode.COUNTDOWN
                        && game.getHealth() == RunnerEngine.MAX_HEALTH
                        && game.getDistance() == 0 && game.getBestDistance() == best
                        && game.getSeed() == 456L,
                "Restart starts a new course with five lives while retaining the best score");

        RunnerEngine lowerScore = started(seedFor(RunnerEngine.HazardType.HOLE));
        lowerScore.loadBestDistance(999);
        RunnerEngine.Hazard lowerHole = lowerScore.getHazards().get(0);
        lowerScore.restore(lowerScore.getSeed(), 1,
                lowerHole.x + lowerHole.width / 2f,
                RunnerEngine.GROUND_Y + RunnerEngine.MAX_TERRAIN_HEIGHT + 17f
                        - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        lowerScore.continueGame();
        lowerScore.update(1f / 120f);
        check(lowerScore.getMode() == RunnerEngine.Mode.GAME_OVER
                        && lowerScore.getLives() == 0
                        && lowerScore.getDistance() < 999
                        && lowerScore.getBestDistance() == 999,
                "a finished run below the best distance does not replace the record");
    }

    private static void testSpeedAndManualPause() {
        RunnerEngine game = started(12345L);
        game.restore(12345L, RunnerEngine.MAX_LIVES, 140f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 2.99d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        game.continueGame();
        check(game.getSpeed() == RunnerEngine.BASE_SPEED, "initial speed before three seconds");
        game.update(.02f);
        check(game.getSpeed() > RunnerEngine.BASE_SPEED, "speed rises after three seconds");
        float x = game.getPlayerX();
        float speed = game.getSpeed();
        game.pause();
        game.jump();
        game.update(8f);
        check(game.getMode() == RunnerEngine.Mode.PAUSED && game.getPlayerX() == x
                && game.getSpeed() == speed, "manual pause stops movement, jumps and timer");
        game.continueGame();
        game.update(1f / 60f);
        check(game.getPlayerX() > x, "Continue resumes manual pause");

        game.restore(12345L, RunnerEngine.MAX_LIVES, 140f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 5.99d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        game.continueGame();
        game.update(.02f);
        check(game.getSpeed() > speed, "another increase occurs after six seconds");
    }

    private static void testWallStopAndJump() {
        long seed = seedForBlockingWall();
        RunnerEngine game = started(seed);
        RunnerEngine.Hazard wall = game.getHazards().get(0);
        game.restore(seed, RunnerEngine.MAX_LIVES, wall.x - RunnerEngine.PLAYER_WIDTH - 1f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 8d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        game.continueGame();
        float previousSpeed = game.getSpeed();
        check(previousSpeed > RunnerEngine.BASE_SPEED, "wall approached at increased speed");
        game.update(1f / 120f);
        check(game.getLives() == RunnerEngine.MAX_LIVES
                        && game.getMode() == RunnerEngine.Mode.RUNNING
                        && game.getHealth() == RunnerEngine.MAX_HEALTH
                        && game.isWaitingForJump() && game.getSpeed() == 0f
                        && Math.abs(game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        - wall.x) < .1f,
                "vertical ledge face stops the runner without health or life loss");
        float stoppedX = game.getPlayerX();
        double stoppedTime = game.getElapsedRunSeconds();
        game.update(.05f);
        check(game.getPlayerX() == stoppedX && game.getElapsedRunSeconds() == stoppedTime,
                "runner waits at the face until the player jumps");
        game.pauseForBackground();
        RunnerEngine restored = new RunnerEngine();
        restored.restore(game.getSeed(), game.getLives(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode());
        restored.restoreTerrainStop(game.isWaitingForJump());
        restored.continueGame();
        check(restored.isWaitingForJump() && restored.getSpeed() == 0f,
                "wall stop survives pause and activity recreation");
        restored.jump();
        check(restored.getVelocityY() < 0f && restored.getSpeed() > 0f,
                "jump from the wall restores forward movement");
        for (int step = 0; step < 180 && restored.getPlayerX() <= wall.x + 10f;
                ++step) restored.update(1f / 120f);
        check(restored.getPlayerX() > wall.x + 10f
                        && restored.getLives() == RunnerEngine.MAX_LIVES
                        && restored.getHealth() == RunnerEngine.MAX_HEALTH,
                "jump clears the vertical ledge without damage");
    }

    private static float surfaceAt(RunnerEngine game, float x) {
        for (RunnerEngine.Hazard hazard : game.getHazards()) {
            if (hazard.x > x) break;
            if (x < hazard.end()) return hazard.surfaceYAt(x);
        }
        return RunnerEngine.GROUND_Y;
    }

    private static boolean openGround(RunnerEngine game, float from, float to) {
        for (RunnerEngine.Hazard hazard : game.getHazards()) {
            if (hazard.x >= to) break;
            if (hazard.end() > from) return false;
        }
        for (RunnerEngine.Platform platform : game.getPlatforms()) {
            if (platform.x >= to) break;
            if (platform.end() > from) return false;
        }
        return true;
    }

    private static RunnerEngine enemyOnOpenGround(float lead) {
        for (long trial = 0; trial < 200; trial++) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine game = new RunnerEngine();
            game.restore(seed, RunnerEngine.MAX_LIVES, 5140f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                    0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
            game.generateAhead(40_000f);
            for (RunnerEngine.Antagonist foe : game.getAntagonists()) {
                if (foe.platform == null
                        && openGround(game, foe.x - lead - 10f, foe.x + 16f)) {
                    float enemyX = foe.x;
                    game.restore(seed, RunnerEngine.MAX_LIVES, enemyX - lead,
                            RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                            0f, 0f, 0d, 1f,
                            RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
                    game.continueGame();
                    check(game.getAntagonists().stream().anyMatch(e -> e.x == enemyX),
                            "enemy stays at the same position after restoration");
                    return game;
                }
            }
        }
        throw new AssertionError("no enemy found with room for a collision check");
    }

    private static RunnerEngine movingEnemyOnOpenGround(float lead, boolean jumpingAtFirstShot) {
        for (long trial = 0; trial < 300; trial++) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine game = new RunnerEngine();
            game.restore(seed, RunnerEngine.MAX_LIVES, 12140f,
                    RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                    0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
            game.generateAhead(45_000f);
            for (RunnerEngine.Antagonist foe : game.getAntagonists()) {
                if (foe.platform != null || foe.type != RunnerEngine.EnemyType.MOVING
                        || foe.spawnX - lead < 12140f) continue;
                if (!openGround(game, foe.spawnX - 80f, foe.spawnX + 80f)
                        || (!jumpingAtFirstShot && !openGround(game,
                        foe.spawnX - lead - 10f, foe.spawnX - lead + 20f))) continue;
                if (jumpingAtFirstShot) {
                    float[] state = enemyStateFor(game, foe.spawnX);
                    if (state[5] < .4f || state[5] > .6f) continue;
                }
                game.restore(seed, RunnerEngine.MAX_LIVES, foe.spawnX - lead,
                        jumpingAtFirstShot ? -150f
                                : RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                        0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
                game.continueGame();
                check(game.getAntagonists().stream().anyMatch(e -> e.spawnX == foe.spawnX
                                && e.type == RunnerEngine.EnemyType.MOVING),
                        "same moving enemy appears after course restoration");
                return game;
            }
        }
        throw new AssertionError("no moving enemy found on open ground");
    }

    private static float[] enemyStateFor(RunnerEngine game, float spawnX) {
        float[] state = game.getAntagonistState();
        for (int i = 0; i + 8 < state.length; i += 9) {
            if (Math.abs(state[i] - spawnX) < .1f) {
                float[] oneEnemy = new float[9];
                System.arraycopy(state, i, oneEnemy, 0, 9);
                return oneEnemy;
            }
        }
        throw new AssertionError("moving enemy state not found");
    }

    private static void testMovingEnemyGenerationAndBehavior() {
        boolean sawMoving = false;
        boolean sawStationary = false;
        for (long seed = 0; seed < 30; seed++) {
            RunnerEngine game = new RunnerEngine();
            game.restore(seed, RunnerEngine.MAX_LIVES, 12139f,
                    RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                    0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
            game.generateAhead(50_000f);
            check(game.getAntagonists().stream().noneMatch(e ->
                            e.type == RunnerEngine.EnemyType.MOVING || e.spawnX >= 12140f),
                    "moving enemies do not appear before 1200 physical metres");

            game.restore(seed, RunnerEngine.MAX_LIVES, 12140f,
                    RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                    0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
            game.generateAhead(50_000f);
            float lastSpawnX = Float.NEGATIVE_INFINITY;
            for (RunnerEngine.Antagonist foe : game.getAntagonists()) {
                sawMoving |= foe.type == RunnerEngine.EnemyType.MOVING;
                sawStationary |= foe.type == RunnerEngine.EnemyType.STATIONARY
                        && foe.spawnX >= 12140f;
                check(foe.type != RunnerEngine.EnemyType.MOVING || foe.spawnX >= 12140f,
                        "moving enemy spawns beyond the unlock point");
                check(foe.spawnX - lastSpawnX > game.getVisibleWorldWidth()
                                + RunnerEngine.PLAYER_WIDTH + 100f,
                        "enemies remain spaced even when both patrol towards each other");
                lastSpawnX = foe.spawnX;
            }
        }
        check(sawMoving && sawStationary, "both enemy types appear after 1200 metres");

        RunnerEngine game = movingEnemyOnOpenGround(350f, true);
        RunnerEngine.Antagonist foe = game.getAntagonists().stream()
                .filter(e -> e.type == RunnerEngine.EnemyType.MOVING
                        && e.spawnX > game.getPlayerX()).findFirst().get();
        float spawnX = foe.spawnX;
        float maxRise = 0f;
        boolean moved = false;
        for (int step = 0; step < 120 && game.getProjectiles().isEmpty(); step++) {
            game.update(1f / 120f);
            moved |= Math.abs(foe.x - spawnX) > .1f;
            check(Math.abs(foe.x - spawnX) <= 50.01f,
                    "moving enemy stays within five metres of its spawn point");
            float rise = RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT - foe.y;
            maxRise = Math.max(maxRise, rise);
            check(rise <= 22.4f, "moving enemy jump stays at 20% of player jump height");
        }
        check(moved && maxRise > 10f,
                "moving enemy patrols and makes a small random jump");
        check(!game.getProjectiles().isEmpty(), "moving enemy shoots while jumping");
        RunnerEngine.Projectile shot = game.getProjectiles().get(0);
        check(shot.type == RunnerEngine.EnemyType.MOVING
                        && shot.getY() < RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT / 2f,
                "red projectile launches horizontally at the airborne enemy's height");
        checkShotAtMuzzle(shot, foe);

        RunnerEngine restored = new RunnerEngine();
        restored.restore(game.getSeed(), game.getLives(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode(), game.getHealth(),
                game.getDamageRecoverySeconds(), game.getFarthestX(),
                game.getVisibleWorldWidth(), game.getAntagonistTimers(),
                game.getProjectileState(), game.getDefeatedAntagonists(),
                game.getBonusPoints(), game.hasCompletedFirstJump(),
                game.isControlledJumpInProgress(), game.getAntagonistState(),
                game.getColoredProjectileState());
        float[] before = enemyStateFor(game, spawnX);
        float[] after = enemyStateFor(restored, spawnX);
        check(restored.getMode() == RunnerEngine.Mode.PAUSED
                        && Math.abs(before[1] - after[1]) < .01f
                        && Math.abs(before[6] - after[6]) < .01f
                        && restored.getProjectiles().get(0).type == RunnerEngine.EnemyType.MOVING
                        && restored.getProjectiles().get(0).getY() == shot.getY(),
                "restoration preserves patrol position, jump and red projectile color");
        restored.update(.5f);
        check(enemyStateFor(restored, spawnX)[1] == after[1],
                "paused moving enemy stays in place");
        restored.continueGame();
        restored.update(1f / 120f);
        check(restored.getProjectiles().get(0).getY() == shot.getY(),
                "mid-jump projectile keeps its original height after resuming");
    }

    private static void testMovingEnemyCollisions() {
        RunnerEngine hit = movingEnemyOnOpenGround(25f, false);
        RunnerEngine.Antagonist foe = hit.getAntagonists().stream()
                .filter(e -> e.type == RunnerEngine.EnemyType.MOVING
                        && e.spawnX > hit.getPlayerX()).findFirst().get();
        float contactX = foe.spawnX - 20f;
        hit.restore(hit.getSeed(), RunnerEngine.MAX_LIVES, contactX,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        hit.continueGame();
        hit.update(1f / 120f);
        check(hit.getHealth() == RunnerEngine.MAX_HEALTH - 2
                        && hit.getLives() == RunnerEngine.MAX_LIVES
                        && hit.getBonusPoints() == 200
                        && hit.getDefeatedAntagonists()[0] == foe.spawnX
                        && hit.getBonusPopups().get(0).type == RunnerEngine.EnemyType.MOVING,
                "body contact removes red enemy, costs two health and shows red +200");
        RunnerEngine restoredHit = new RunnerEngine();
        restoredHit.restore(hit.getSeed(), hit.getLives(), hit.getPlayerX(),
                hit.getPlayerY(), hit.getVelocityY(), hit.getCountdownSeconds(),
                hit.getElapsedRunSeconds(), hit.getTerrainSpeedMultiplier(),
                hit.getMode(), hit.getResumeMode(), hit.getHealth(),
                hit.getDamageRecoverySeconds(), hit.getFarthestX(),
                hit.getVisibleWorldWidth(), hit.getAntagonistTimers(),
                hit.getProjectileState(), hit.getDefeatedAntagonists(),
                hit.getBonusPoints(), hit.hasCompletedFirstJump(),
                hit.isControlledJumpInProgress(), hit.getAntagonistState(),
                hit.getColoredProjectileState());
        restoredHit.generateAhead(45_000f);
        check(restoredHit.getAntagonists().stream().noneMatch(e -> e.spawnX == foe.spawnX),
                "defeated moving enemy does not return after activity restoration");

        RunnerEngine stomp = movingEnemyOnOpenGround(25f, false);
        RunnerEngine.Antagonist stompFoe = stomp.getAntagonists().stream()
                .filter(e -> e.type == RunnerEngine.EnemyType.MOVING
                        && e.spawnX > stomp.getPlayerX()).findFirst().get();
        float stompX = stompFoe.spawnX - 20f;
        float feet = stompFoe.y + RunnerEngine.PLAYER_HEIGHT / 4f - .5f;
        stomp.restore(stomp.getSeed(), RunnerEngine.MAX_LIVES, stompX,
                feet - RunnerEngine.PLAYER_HEIGHT, 150f, 0f, 0d, 1f,
                RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        stomp.continueGame();
        stomp.update(1f / 120f);
        check(stomp.getHealth() == RunnerEngine.MAX_HEALTH
                        && stomp.getLives() == RunnerEngine.MAX_LIVES
                        && stomp.getBonusPoints() == 400
                        && stomp.getBonusPopups().get(0).amount == 400
                        && stomp.getBonusPopups().get(0).type == RunnerEngine.EnemyType.MOVING,
                "landing on red enemy gives red +400 without damage");
        float[] popupState = stomp.getBonusPopupState();
        RunnerEngine restored = new RunnerEngine();
        restored.restoreBonusPopups(popupState);
        check(restored.getBonusPopups().size() == 1
                        && restored.getBonusPopups().get(0).getProgress() == 0f,
                "rising bonus label survives activity recreation");
    }

    private static void testAntagonistGeneration() {
        boolean sawGround = false;
        boolean sawLedge = false;
        boolean sawDepression = false;
        boolean sawSlope = false;
        boolean sawPlatform = false;
        for (long seed = 0; seed < 35; seed++) {
            RunnerEngine game = started(seed);
            game.setVisibleWorldWidth(1240f);
            game.generateAhead(80_000f);
            check(game.getAntagonists().isEmpty(), "enemies stay absent before 500 m");
            game.restore(seed, RunnerEngine.MAX_LIVES, 5139f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                    0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
            game.generateAhead(80_000f);
            check(game.getAntagonists().isEmpty(), "enemies stay absent just before 500 m");
            game.restore(seed, RunnerEngine.MAX_LIVES, 5140f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                    0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
            game.generateAhead(80_000f);
            float lastX = Float.NEGATIVE_INFINITY;
            for (RunnerEngine.Antagonist foe : game.getAntagonists()) {
                check(foe.x >= 5140f, "enemy first appears beyond 500 m");
                check(foe.x - lastX > game.getVisibleWorldWidth() + RunnerEngine.PLAYER_WIDTH,
                        "at most one enemy fits on screen at a time");
                float center = foe.x + RunnerEngine.PLAYER_WIDTH / 2f;
                float surface = surfaceAt(game, center);
                float support = foe.platform == null ? surface : foe.platform.top;
                check(!Float.isNaN(surface)
                                && Math.abs(foe.y + RunnerEngine.PLAYER_HEIGHT - support) < .1f,
                        "enemy stands on ground or a suspended platform, not in a hole");
                if (foe.platform != null) {
                    sawPlatform = true;
                    float patrol = foe.type == RunnerEngine.EnemyType.MOVING ? 50f : 0f;
                    check(foe.spawnX - patrol >= foe.platform.x + 100f
                                    && foe.spawnX + RunnerEngine.PLAYER_WIDTH + patrol
                                    <= foe.platform.end() - 100f,
                            "platform enemy and patrol stay ten metres from both edges");
                    lastX = foe.x;
                    continue;
                }
                if (Math.abs(surface - RunnerEngine.GROUND_Y) < .1f) sawGround = true;
                for (RunnerEngine.Hazard hazard : game.getHazards()) {
                    if (hazard.x > center) break;
                    if (center < hazard.end()) {
                        sawLedge |= hazard.type == RunnerEngine.HazardType.WALL;
                        sawDepression |= hazard.type == RunnerEngine.HazardType.DIP;
                        sawSlope |= hazard.slopeDegreesAt(center) != 0;
                        break;
                    }
                }
                lastX = foe.x;
            }
        }
        check(sawGround && sawLedge && sawDepression && sawSlope && sawPlatform,
                "enemies appear on ground, ledges, depressions, inclines and platforms");

        RunnerEngine bonusRun = new RunnerEngine();
        bonusRun.restore(1L, RunnerEngine.MAX_LIVES, 5130f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT, 0f, 0f, 0d, 1f,
                RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING,
                RunnerEngine.MAX_HEALTH, 0f, 5130f, 960f, null, null, null,
                3, false, false);
        bonusRun.generateAhead(80_000f);
        check(bonusRun.getDistance() == 499 && bonusRun.getScore() == 502
                        && bonusRun.getAntagonists().isEmpty(),
                "bonus points do not change physical distance or unlock enemies early");
    }

    private static boolean withinTenMetres(float left, float right, float start, float end) {
        float clearance = 10f * RunnerEngine.WORLD_UNITS_PER_METER;
        return left < end + clearance && right > start - clearance;
    }

    private static void testEnemyTerrainClearance() {
        int checkedStationary = 0;
        int checkedMoving = 0;
        for (long seed = 0; seed < 30; seed++) {
            RunnerEngine game = new RunnerEngine();
            game.restore(seed, RunnerEngine.MAX_LIVES, 12140f,
                    RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                    0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
            game.generateAhead(80_000f);
            for (RunnerEngine.Antagonist foe : game.getAntagonists()) {
                float patrol = foe.type == RunnerEngine.EnemyType.MOVING
                        ? 5f * RunnerEngine.WORLD_UNITS_PER_METER : 0f;
                if (patrol > 0f) checkedMoving++;
                else checkedStationary++;
                float left = foe.spawnX - patrol;
                float right = foe.spawnX + RunnerEngine.PLAYER_WIDTH + patrol;
                for (RunnerEngine.Hazard hazard : game.getHazards()) {
                    check(!withinTenMetres(left, right, hazard.x, hazard.x)
                                    && !withinTenMetres(left, right, hazard.end(), hazard.end()),
                            "enemy and full patrol stay ten metres from terrain edges");
                    if (hazard.type == RunnerEngine.HazardType.HOLE) {
                        check(!withinTenMetres(left, right, hazard.x, hazard.end()),
                                "enemy and full patrol stay clear of holes");
                    } else {
                        if (hazard.entranceDegrees > 45) {
                            check(!withinTenMetres(left, right, hazard.x, hazard.flatStart),
                                    "enemy stays clear of a steep entrance or vertical wall");
                        }
                        if (hazard.exitDegrees > 45) {
                            check(!withinTenMetres(left, right, hazard.flatEnd, hazard.end()),
                                    "enemy stays clear of a steep exit or vertical wall");
                        }
                    }
                }
            }
        }
        check(checkedStationary > 10 && checkedMoving > 10,
                "both enemy types spawn despite terrain clearance constraints");
    }

    private static void testDamageBlink() {
        RunnerEngine game = withProjectile(123L, 140f + RunnerEngine.PLAYER_WIDTH / 2f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT / 2f);
        check(!game.isDamageBlinkLight(), "runner begins in its normal color");
        game.update(1f / 120f);
        check(game.getHealth() == RunnerEngine.MAX_HEALTH - 1
                        && game.isDamageBlinkLight(),
                "projectile damage starts a light blue blink");
        for (int i = 0; i < 8; i++) game.update(1f / 120f);
        check(!game.isDamageBlinkLight(), "blink alternates with the original color");
        game.pause();
        float savedRecovery = game.getDamageRecoverySeconds();
        game.update(1f);
        check(game.getDamageRecoverySeconds() == savedRecovery,
                "pause freezes the blink phase");
        RunnerEngine restored = new RunnerEngine();
        restored.restore(game.getSeed(), game.getLives(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode(), game.getHealth(),
                savedRecovery, game.getFarthestX(), game.getVisibleWorldWidth(),
                game.getAntagonistTimers(), game.getProjectileState());
        check(restored.isDamageBlinkLight() == game.isDamageBlinkLight(),
                "activity recreation retains the blink phase");
        restored.continueGame();
        boolean sawLight = false;
        boolean sawNormal = false;
        for (int i = 0; i < 65; i++) {
            restored.update(1f / 120f);
            sawLight |= restored.isDamageBlinkLight();
            sawNormal |= !restored.isDamageBlinkLight();
        }
        check(sawLight && sawNormal && !restored.isDamageBlinkLight()
                        && restored.getDamageRecoverySeconds() > 0f,
                "blink alternates rapidly for half a second then returns to normal");
        for (int i = 0; i < 10; i++) restored.update(1f / 120f);
        check(restored.getDamageRecoverySeconds() == 0f
                        && !restored.isDamageBlinkLight(),
                "the existing damage recovery finishes without restarting the blink");
    }

    private static void testEnemyStomp() {
        RunnerEngine stomp = enemyOnOpenGround(180f);
        RunnerEngine.Antagonist foe = stomp.getAntagonists().stream()
                .filter(e -> e.x > stomp.getPlayerX()).findFirst().get();
        float startingSpeed = stomp.getSpeed();
        stomp.jump();
        float feetBeforeContact = Float.NaN;
        float feetAfterContact = Float.NaN;
        for (int step = 0; step < 150 && stomp.getDefeatedAntagonists().length == 0;
                ++step) {
            float previousFeet = stomp.getPlayerY() + RunnerEngine.PLAYER_HEIGHT;
            stomp.update(1f / 120f);
            if (stomp.getDefeatedAntagonists().length > 0) {
                feetBeforeContact = previousFeet;
                feetAfterContact = stomp.getPlayerY() + RunnerEngine.PLAYER_HEIGHT;
            }
        }
        check(stomp.getAntagonists().stream().noneMatch(e -> e.x == foe.x)
                        && stomp.getDefeatedAntagonists().length == 1,
                "landing on an enemy removes it");
        check(feetBeforeContact <= foe.y + RunnerEngine.PLAYER_HEIGHT / 4f
                        && feetAfterContact >= foe.y + 5f,
                "stomp crosses the enemy's upper quarter during a frame");
        check(stomp.getHealth() == RunnerEngine.MAX_HEALTH
                        && stomp.getLives() == RunnerEngine.MAX_LIVES
                        && stomp.getDamageRecoverySeconds() == 0f
                        && stomp.getMode() == RunnerEngine.Mode.RUNNING
                        && stomp.getSpeed() == startingSpeed
                        && stomp.getBonusPoints() == 300
                        && stomp.getDistance() == (int) ((stomp.getFarthestX() - 140f)
                        / RunnerEngine.WORLD_UNITS_PER_METER)
                        && stomp.getScore() == stomp.getDistance() + 300,
                "stomp awards 300 points without changing distance, health, lives or speed");
        check(stomp.getBonusPopups().size() == 1
                        && stomp.getBonusPopups().get(0).amount == 300
                        && stomp.getBonusPopups().get(0).type == RunnerEngine.EnemyType.STATIONARY
                        && Math.abs(stomp.getBonusPopups().get(0).x
                        - foe.x - RunnerEngine.PLAYER_WIDTH / 2f) < .01f,
                "stomp shows a +300 bonus at the orange enemy's position");
        stomp.pause();
        stomp.update(.5f);
        check(stomp.getBonusPopups().get(0).getProgress() == 0f,
                "bonus label freezes while paused");
        stomp.continueGame();
        stomp.update(.3f);
        check(stomp.getBonusPopups().get(0).getProgress() > .3f,
                "bonus label animates when play resumes");
        stomp.update(1f);
        check(stomp.getBonusPopups().isEmpty(), "bonus label disappears after rising");

        RunnerEngine justBelow = enemyOnOpenGround(130f);
        RunnerEngine.Antagonist lowerFoe = justBelow.getAntagonists().stream()
                .filter(e -> e.x > justBelow.getPlayerX()).findFirst().get();
        float initialX = lowerFoe.x - 25f;
        float initialFeet = lowerFoe.y + RunnerEngine.PLAYER_HEIGHT / 4f + 1f;
        justBelow.restore(justBelow.getSeed(), RunnerEngine.MAX_LIVES, initialX,
                initialFeet - RunnerEngine.PLAYER_HEIGHT, 10f, 0f, 0d, 1f,
                RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        justBelow.continueGame();
        justBelow.update(1f / 120f);
        check(justBelow.getHealth() == RunnerEngine.MAX_HEALTH - 2
                        && justBelow.getLives() == RunnerEngine.MAX_LIVES
                        && justBelow.getMode() == RunnerEngine.Mode.RUNNING
                        && justBelow.getPlayerX() > initialX
                        && justBelow.getBonusPoints() == 100
                        && justBelow.getAntagonists().stream().noneMatch(e -> e.x == lowerFoe.x),
                "descending contact below the upper quarter costs two health and gives 100 points");
    }

    private static void testAntagonistAndProjectiles() {
        RunnerEngine game = enemyOnOpenGround(130f);
        float enemyX = game.getAntagonists().stream()
                .filter(e -> e.x > game.getPlayerX()).findFirst().get().x;
        game.restore(game.getSeed(), game.getLives(), game.getPlayerX(),
                game.getPlayerY(), 0f, 0f, 8d, 1f,
                RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        game.continueGame();
        float previousSpeed = game.getSpeed();
        check(previousSpeed > RunnerEngine.BASE_SPEED,
                "enemy approached faster than the starting speed");
        float approachX = game.getPlayerX();
        for (int step = 0; step < 300 && game.getDefeatedAntagonists().length == 0;
                step++) game.update(1f / 120f);
        check(game.getLives() == RunnerEngine.MAX_LIVES
                        && game.getHealth() == RunnerEngine.MAX_HEALTH - 2
                        && game.getMode() == RunnerEngine.Mode.RUNNING
                        && game.getBonusPoints() == 100,
                "running into an orange enemy costs two health and awards 100 points");
        check(game.getBonusPopups().size() == 1
                        && game.getBonusPopups().get(0).amount == 100
                        && game.getBonusPopups().get(0).type == RunnerEngine.EnemyType.STATIONARY,
                "collision shows a +100 label in the orange enemy's color");
        check(game.getPlayerX() > approachX
                        && game.getSpeed() == previousSpeed,
                "enemy collision does not interrupt movement or reset speed");
        final float struckEnemyX = enemyX;
        check(game.getAntagonists().stream().noneMatch(e -> e.x == struckEnemyX)
                        && game.getDefeatedAntagonists().length == 1,
                "struck oval immediately disappears while health is lost");
        RunnerEngine restoredHit = new RunnerEngine();
        restoredHit.restore(game.getSeed(), game.getLives(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode(), game.getHealth(),
                game.getDamageRecoverySeconds(), game.getFarthestX(),
                game.getVisibleWorldWidth(), game.getAntagonistTimers(),
                game.getProjectileState(), game.getDefeatedAntagonists(),
                game.getBonusPoints(), game.hasCompletedFirstJump(),
                game.isControlledJumpInProgress());
        restoredHit.generateAhead(40_000f);
        check(restoredHit.getMode() == RunnerEngine.Mode.PAUSED
                        && restoredHit.getResumeMode() == RunnerEngine.Mode.RUNNING
                        && restoredHit.getBonusPoints() == 100
                        && restoredHit.getDistance() == game.getDistance()
                        && restoredHit.getScore() == game.getScore()
                        && restoredHit.getAntagonists().stream().noneMatch(e -> e.x == struckEnemyX),
                "enemy removal and bonus score persist through activity restoration");
        float afterHitX = game.getPlayerX();
        game.update(1f / 120f);
        check(game.getMode() == RunnerEngine.Mode.RUNNING
                        && game.getPlayerX() > afterHitX,
                "run resumes normally after an enemy contact");

        RunnerEngine lastHealth = enemyOnOpenGround(130f);
        float nextEnemyX = lastHealth.getAntagonists().stream()
                .filter(e -> e.x > lastHealth.getPlayerX()).findFirst().get().x;
        float closeX = nextEnemyX - RunnerEngine.PLAYER_WIDTH + 10f;
        lastHealth.restore(lastHealth.getSeed(), RunnerEngine.MAX_LIVES, closeX,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT, 0f, 0f, 0d, 1f,
                RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING,
                2, .3f, closeX, 960f, null, null);
        lastHealth.continueGame();
        lastHealth.update(1f / 120f);
        check(lastHealth.getLives() == RunnerEngine.MAX_LIVES - 1
                        && lastHealth.getMode() == RunnerEngine.Mode.COUNTDOWN
                        && lastHealth.getHealth() == RunnerEngine.MAX_HEALTH
                        && lastHealth.getBonusPoints() == 100,
                "enemy collision depletes health despite projectile recovery and awards its bonus");

        RunnerEngine finalLife = enemyOnOpenGround(130f);
        float finalEnemyX = finalLife.getAntagonists().stream()
                .filter(e -> e.x > finalLife.getPlayerX()).findFirst().get().x;
        float finalContactX = finalEnemyX - RunnerEngine.PLAYER_WIDTH + 10f;
        finalLife.restore(finalLife.getSeed(), 1, finalContactX,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT, 0f, 0f, 0d, 1f,
                RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING,
                2, 0f, finalContactX, 960f, null, null);
        finalLife.continueGame();
        finalLife.update(1f / 120f);
        check(finalLife.getMode() == RunnerEngine.Mode.GAME_OVER
                        && finalLife.getBonusPoints() == 100
                        && finalLife.getBestDistance() == finalLife.getDistance()
                        && finalLife.getBestScore() == finalLife.getScore(),
                "bonus from the final collision counts toward the saved best score");
        finalLife.startNewGame(99L);
        check(finalLife.getBonusPoints() == 0 && finalLife.getDistance() == 0,
                "new run resets bonus points");

        RunnerEngine shooter = enemyOnOpenGround(260f);
        enemyX = shooter.getAntagonists().stream()
                .filter(e -> e.x > shooter.getPlayerX()).findFirst().get().x;
        float[] earlyShotTimers = shooter.getAntagonistTimers();
        for (int i = 0; i < earlyShotTimers.length; i += 2) {
            if (earlyShotTimers[i] == enemyX) earlyShotTimers[i + 1] = .2f;
        }
        shooter.restore(shooter.getSeed(), shooter.getLives(), shooter.getPlayerX(),
                shooter.getPlayerY(), shooter.getVelocityY(), shooter.getCountdownSeconds(),
                shooter.getElapsedRunSeconds(), shooter.getTerrainSpeedMultiplier(),
                RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING, shooter.getHealth(),
                shooter.getDamageRecoverySeconds(), shooter.getFarthestX(),
                shooter.getVisibleWorldWidth(), earlyShotTimers, null);
        shooter.continueGame();
        for (int step = 0; step < 120 && shooter.getProjectiles().isEmpty(); step++) {
            shooter.update(1f / 120f);
        }
        check(!shooter.getProjectiles().isEmpty(),
                "visible stationary enemy fires a moving orange dot towards the runner");
        check(shooter.getProjectiles().get(0).type == RunnerEngine.EnemyType.STATIONARY,
                "stationary enemy projectile keeps its orange type");
        final float firingTurretX = enemyX;
        RunnerEngine.Antagonist firingTurret = shooter.getAntagonists().stream()
                .filter(e -> e.x == firingTurretX).findFirst().get();
        checkShotAtMuzzle(shooter.getProjectiles().get(0), firingTurret);
        float[] shots = shooter.getProjectileState();
        check(shots[2] < 0f && shots[3] == 0f,
                "projectile travels left with no vertical velocity");
        float[] timers = shooter.getAntagonistTimers();
        RunnerEngine restored = new RunnerEngine();
        restored.restore(shooter.getSeed(), shooter.getLives(),
                shooter.getPlayerX(), shooter.getPlayerY(), shooter.getVelocityY(),
                shooter.getCountdownSeconds(), shooter.getElapsedRunSeconds(),
                shooter.getTerrainSpeedMultiplier(), shooter.getMode(), shooter.getResumeMode(),
                shooter.getHealth(), shooter.getDamageRecoverySeconds(),
                shooter.getFarthestX(), shooter.getVisibleWorldWidth(), timers, shots);
        check(restored.getMode() == RunnerEngine.Mode.PAUSED
                        && restored.getProjectileState().length == shots.length
                        && Math.abs(restored.getProjectileState()[0] - shots[0]) < .01f,
                "pause restoration keeps in-flight projectiles");
        restored.update(1f);
        check(restored.getProjectileState()[0] == shots[0],
                "paused projectiles do not move");
        restored.continueGame();
        restored.jump();
        restored.update(1f / 120f);
        shooter.update(1f / 120f);
        check(Math.abs(restored.getProjectileState()[0]
                - shooter.getProjectileState()[0]) < .01f,
                "restored projectile continues on the original trajectory");
        check(restored.getProjectileState()[1] == shots[1]
                        && shooter.getProjectileState()[1] == shots[1],
                "jumping does not steer a projectile vertically");

        for (int step = 0; step < 240 && shooter.getHealth() == RunnerEngine.MAX_HEALTH;
                step++) shooter.update(1f / 120f);
        check(shooter.getHealth() == RunnerEngine.MAX_HEALTH - 1
                        && shooter.getLives() == RunnerEngine.MAX_LIVES
                        && shooter.getPlayerX() < enemyX - RunnerEngine.PLAYER_WIDTH - 30f,
                "a projectile costs one health point before the oval is reached");
        check(shooter.getVelocityY() < 0f && shooter.getVelocityY() > -300f,
                "projectile hit starts a small bounce rather than a full jump");
        float hitY = shooter.getPlayerY();
        float highestY = hitY;
        for (int step = 0; step < 26; step++) {
            shooter.update(1f / 120f);
            highestY = Math.min(highestY, shooter.getPlayerY());
        }
        check(hitY - highestY > 10f && hitY - highestY < 30f,
                "projectile bounce rises much less than a controlled jump");
    }

    private static void checkShotAtMuzzle(RunnerEngine.Projectile shot,
                                          RunnerEngine.Antagonist enemy) {
        float muzzleX = enemy.x + RunnerEngine.MUZZLE_X;
        float shotRight = shot.getX() + RunnerEngine.PROJECTILE_RADIUS;
        check(muzzleX - shotRight > 0f && muzzleX - shotRight < 4f
                        && Math.abs(shot.getY() - enemy.y - RunnerEngine.MUZZLE_Y) < .1f,
                "projectile emerges from the weapon muzzle at the enemy's current height");
    }

    private static RunnerEngine withProjectile(long seed, float x, float y) {
        RunnerEngine game = new RunnerEngine();
        game.restore(seed, RunnerEngine.MAX_LIVES, 140f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING,
                RunnerEngine.MAX_HEALTH, 0f, 140f, 2000f, null,
                new float[]{x, y, -380f, 0f});
        game.continueGame();
        return game;
    }

    private static void checkTerrainAbsorbsShot(RunnerEngine game, String description) {
        check(game.getProjectiles().size() == 1, "test starts with one " + description);
        for (int step = 0; step < 120 && !game.getProjectiles().isEmpty(); step++) {
            game.update(1f / 120f);
        }
        check(game.getProjectiles().isEmpty() && game.getHealth() == RunnerEngine.MAX_HEALTH,
                description + " absorbs a projectile before it reaches the runner");
    }

    private static void testProjectileTerrainCollisions() {
        long verticalSeed = seedForBlockingWall();
        RunnerEngine vertical = started(verticalSeed);
        float verticalEdge = vertical.getHazards().get(0).end();
        checkTerrainAbsorbsShot(withProjectile(verticalSeed, verticalEdge + 25f,
                RunnerEngine.GROUND_Y - 24f), "vertical ledge face");

        long slopeSeed = seedForSlopes(RunnerEngine.HazardType.WALL, 30, 45);
        RunnerEngine sloped = started(slopeSeed);
        float slopeEdge = sloped.getHazards().get(0).end();
        checkTerrainAbsorbsShot(withProjectile(slopeSeed, slopeEdge + 25f,
                RunnerEngine.GROUND_Y - 24f), "45-degree ledge slope");

        long dipSeed = seedForSlopes(RunnerEngine.HazardType.DIP, 30, -1);
        RunnerEngine dip = started(dipSeed);
        float dipEntry = dip.getHazards().get(0).flatStart;
        checkTerrainAbsorbsShot(withProjectile(dipSeed, dipEntry + 25f,
                RunnerEngine.GROUND_Y + 18f), "30-degree depression slope");

        checkTerrainAbsorbsShot(withProjectile(verticalSeed, 500f,
                RunnerEngine.GROUND_Y - 3f), "flat ground");

        RunnerEngine highShot = withProjectile(verticalSeed, verticalEdge + 25f,
                RunnerEngine.GROUND_Y - RunnerEngine.MAX_TERRAIN_HEIGHT - 25f);
        float startingY = highShot.getProjectiles().get(0).getY();
        for (int step = 0; step < 60; step++) highShot.update(1f / 120f);
        check(highShot.getProjectiles().size() == 1
                        && highShot.getProjectiles().get(0).getY() == startingY,
                "a projectile entirely above the wall keeps flying horizontally");
    }

    private static void testSuspendedPlatforms() {
        RunnerEngine course = new RunnerEngine();
        course.startNewGame(1L);
        course.generateAhead(40_000f);
        check(!course.getPlatforms().isEmpty(), "seeded course includes suspended platforms");
        RunnerEngine.Platform lower = course.getPlatforms().get(0);
        RunnerEngine.Platform higher = null;
        boolean sawHigh = false;
        for (int i = 0; i < course.getPlatforms().size(); i++) {
            RunnerEngine.Platform platform = course.getPlatforms().get(i);
            float clearance = RunnerEngine.GROUND_Y - platform.bottom;
            check(clearance >= 2f * RunnerEngine.PLAYER_HEIGHT
                            && clearance <= 4f * RunnerEngine.PLAYER_HEIGHT
                            && Math.abs(platform.bottom - platform.top
                            - RunnerEngine.PLATFORM_THICKNESS) < .01f,
                    "platform underside leaves two to four car heights over ground");
            check(openGroundUnderPlatform(course, platform),
                    "platform leaves intact flat ground beneath it");
            if (i > 0) {
                RunnerEngine.Platform previous = course.getPlatforms().get(i - 1);
                check(previous.end() < platform.x, "platforms do not overlap");
                if (clearance > 2.6f * RunnerEngine.PLAYER_HEIGHT) {
                    sawHigh = true;
                    check(Math.abs(platform.x - previous.end() - 36f) < .1f
                                    && previous.top - platform.top < RunnerEngine.JUMP_HEIGHT,
                            "high platforms have a reachable lower stepping platform");
                    if (higher == null) {
                        lower = previous;
                        higher = platform;
                    }
                }
            }
        }
        check(sawHigh && higher != null, "generated course includes a reachable high platform");

        RunnerEngine land = placedAt(1L, lower.x - 135f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT, 0f);
        land.jump();
        boolean reachedLower = false;
        for (int step = 0; step < 140; step++) {
            land.update(1f / 120f);
            if (land.getPlayerX() >= lower.x && land.getPlayerX() < lower.end()
                    && Math.abs(land.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                    - lower.top) < .1f) {
                reachedLower = true;
                break;
            }
        }
        check(reachedLower && land.getLives() == RunnerEngine.MAX_LIVES,
                "timed jump lands on a low suspended platform without damage");
        land.jump();
        check(land.getVelocityY() < 0f, "car can jump again from the platform top");

        RunnerEngine stepUp = placedAt(1L, lower.end() - 135f,
                lower.top - RunnerEngine.PLAYER_HEIGHT, 0f);
        stepUp.jump();
        boolean reachedHigher = false;
        for (int step = 0; step < 160; step++) {
            stepUp.update(1f / 120f);
            if (stepUp.getPlayerX() >= higher.x && stepUp.getPlayerX() < higher.end()
                    && Math.abs(stepUp.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                    - higher.top) < .1f) {
                reachedHigher = true;
                break;
            }
        }
        check(reachedHigher, "a regular jump reaches the higher platform from the lower one");

        RunnerEngine below = placedAt(1L, lower.x + 55f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT, 0f);
        float before = below.getPlayerX();
        below.jump();
        boolean bumped = false;
        for (int step = 0; step < 90; step++) {
            below.update(1f / 120f);
            check(below.getPlayerY() >= lower.bottom - .1f,
                    "an upward jump never passes through the platform underside");
            bumped |= Math.abs(below.getPlayerY() - lower.bottom) < .1f
                    && below.getVelocityY() == 0f;
        }
        check(bumped && below.getPlayerX() > before + 90f
                        && below.getHealth() == RunnerEngine.MAX_HEALTH
                        && below.getLives() == RunnerEngine.MAX_LIVES,
                "underside bump ends the rise without damage or stopping the run");

        RunnerEngine late = placedAt(1L, lower.x - RunnerEngine.PLAYER_WIDTH - 2f,
                lower.bottom - 15f, -180f);
        late.update(1f / 60f);
        check(late.getPlayerX() + RunnerEngine.PLAYER_WIDTH <= lower.x
                        && late.getHealth() == RunnerEngine.MAX_HEALTH,
                "a late jump cannot pass sideways through the platform edge");

        RunnerEngine restored = new RunnerEngine();
        restored.restore(stepUp.getSeed(), stepUp.getLives(), stepUp.getPlayerX(),
                stepUp.getPlayerY(), stepUp.getVelocityY(), 0f,
                stepUp.getElapsedRunSeconds(), stepUp.getTerrainSpeedMultiplier(),
                RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        check(Math.abs(restored.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                - higher.top) < .1f, "activity restore keeps the car on a platform");
        restored.continueGame();
        restored.jump();
        check(restored.getVelocityY() < 0f,
                "a restored car can still jump from a suspended platform");

        RunnerEngine shot = new RunnerEngine();
        shot.restore(1L, RunnerEngine.MAX_LIVES, 140f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING,
                RunnerEngine.MAX_HEALTH, 0f, lower.end() + 100f,
                lower.end() + 500f, null,
                new float[]{lower.end() + 20f, (lower.top + lower.bottom) / 2f,
                        -380f, 0f});
        shot.continueGame();
        checkTerrainAbsorbsShot(shot, "suspended platform side");
    }

    private static void testPlatformEnemyPatrolAndRestore() {
        RunnerEngine course = null;
        RunnerEngine.Antagonist platformEnemy = null;
        for (long seed = 0; seed < 80 && platformEnemy == null; seed++) {
            RunnerEngine candidate = new RunnerEngine();
            candidate.restore(seed, RunnerEngine.MAX_LIVES, 12140f,
                    RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                    0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
            candidate.generateAhead(80_000f);
            for (RunnerEngine.Antagonist foe : candidate.getAntagonists()) {
                if (foe.platform != null && foe.type == RunnerEngine.EnemyType.MOVING) {
                    course = candidate;
                    platformEnemy = foe;
                    break;
                }
            }
        }
        check(platformEnemy != null, "moving enemies also spawn on suspended platforms");
        float enemyX = platformEnemy.spawnX;
        RunnerEngine.Platform platform = platformEnemy.platform;
        RunnerEngine active = placedAt(course.getSeed(), enemyX - 300f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT, 0f);
        RunnerEngine.Antagonist foe = active.getAntagonists().stream()
                .filter(e -> e.spawnX == enemyX).findFirst().get();
        check(Math.abs(foe.y + RunnerEngine.PLAYER_HEIGHT - platform.top) < .1f,
                "moving enemy starts on the platform top");
        for (int step = 0; step < 95; step++) {
            active.update(1f / 120f);
            check(foe.x >= platform.x && foe.x + RunnerEngine.PLAYER_WIDTH <= platform.end()
                            && foe.y + RunnerEngine.PLAYER_HEIGHT <= platform.top + .1f,
                    "enemy patrol and jump remain supported by its platform");
        }
        RunnerEngine saved = new RunnerEngine();
        saved.restore(active.getSeed(), active.getLives(), active.getPlayerX(),
                active.getPlayerY(), active.getVelocityY(), 0f,
                active.getElapsedRunSeconds(), active.getTerrainSpeedMultiplier(),
                active.getMode(), active.getResumeMode(), active.getHealth(),
                active.getDamageRecoverySeconds(), active.getFarthestX(),
                active.getVisibleWorldWidth(), active.getAntagonistTimers(),
                active.getProjectileState(), active.getDefeatedAntagonists(),
                active.getBonusPoints(), active.hasCompletedFirstJump(),
                active.isControlledJumpInProgress(), active.getAntagonistState(),
                active.getColoredProjectileState());
        RunnerEngine.Antagonist same = saved.getAntagonists().stream()
                .filter(e -> e.spawnX == enemyX).findFirst().get();
        check(same.platform != null && Math.abs(same.x - foe.x) < .01f
                        && Math.abs(same.y - foe.y) < .01f,
                "activity restore retains an enemy's platform and patrol position");
    }

    private static boolean openGroundUnderPlatform(RunnerEngine course,
                                                    RunnerEngine.Platform platform) {
        for (RunnerEngine.Hazard hazard : course.getHazards()) {
            if (hazard.x >= platform.end()) break;
            if (hazard.end() > platform.x) return false;
        }
        return Math.abs(course.surfaceYAt(platform.x + platform.width / 2f)
                - RunnerEngine.GROUND_Y) < .01f;
    }

    private static RunnerEngine placedAt(long seed, float x, float y, float vy) {
        RunnerEngine game = new RunnerEngine();
        game.restore(seed, RunnerEngine.MAX_LIVES, x, y, vy,
                0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        game.continueGame();
        return game;
    }

    private static void testWaterAndStars() {
        long waterSeed = seedFor(RunnerEngine.HazardType.HOLE);
        RunnerEngine course = started(waterSeed);
        RunnerEngine.Hazard hole = course.getHazards().get(0);
        float middle = (hole.x + hole.end()) / 2f - RunnerEngine.PLAYER_WIDTH / 2f;
        RunnerEngine above = placedAt(waterSeed, middle,
                RunnerEngine.WATER_SURFACE_Y - RunnerEngine.PLAYER_HEIGHT - 2f, 0f);
        above.update(1f / 120f);
        check(above.getLives() == RunnerEngine.MAX_LIVES,
                "hovercar above the waterline remains safe");
        RunnerEngine drown = placedAt(waterSeed, middle,
                RunnerEngine.WATER_SURFACE_Y - RunnerEngine.PLAYER_HEIGHT - .1f, 100f);
        drown.update(1f / 120f);
        check(drown.getLives() == RunnerEngine.MAX_LIVES - 1
                        && drown.getMode() == RunnerEngine.Mode.COUNTDOWN,
                "touching the waterline immediately costs one life");
        RunnerEngine jumpOver = placedAt(waterSeed, hole.x - 100f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT, 0f);
        jumpOver.jump();
        for (int step = 0; step < 180 && jumpOver.getPlayerX() < hole.end() + 40f;
                step++) jumpOver.update(1f / 120f);
        check(jumpOver.getPlayerX() > hole.end() + 40f
                        && jumpOver.getLives() == RunnerEngine.MAX_LIVES,
                "a timed jump clears the water-filled hole");

        RunnerEngine layout = new RunnerEngine();
        layout.startNewGame(19L);
        layout.generateAhead(200_000f);
        int[] unlocks = {200, 400, 600, 800, 1000};
        int[] points = {10, 20, 30, 40, 50};
        for (RunnerEngine.StarColor color : RunnerEngine.StarColor.values()) {
            check(color.unlockMetres == unlocks[color.ordinal()]
                            && color.points == points[color.ordinal()],
                    "star color has its requested unlock distance and point value");
        }
        boolean[] sawColor = new boolean[RunnerEngine.StarColor.values().length];
        boolean sawHoleStar = false;
        boolean sawPlatformStar = false;
        boolean sawUnderPlatform = false;
        boolean sawMixedGroup = false;
        int groupSize = 0;
        float previousX = Float.NaN;
        RunnerEngine.StarColor previousColor = null;
        for (RunnerEngine.Star star : layout.getStars()) {
            check(star.x >= 140f + 200f * RunnerEngine.WORLD_UNITS_PER_METER,
                    "stars begin after 200 physical metres");
            check(star.x >= 140f + star.color.unlockMetres
                            * RunnerEngine.WORLD_UNITS_PER_METER,
                    "each color appears only after its unlock distance");
            sawColor[star.color.ordinal()] = true;
            if (!Float.isNaN(previousX)) {
                float gap = star.x - previousX;
                if (gap > 50f) {
                    check(groupSize >= 1 && groupSize <= 10
                                    && gap >= 100f * RunnerEngine.WORLD_UNITS_PER_METER
                                    && gap <= 750f * RunnerEngine.WORLD_UNITS_PER_METER,
                            "one to ten stars form groups spaced 100 to 750 metres apart");
                    groupSize = 0;
                } else {
                    check(gap >= 36f && gap <= 48f, "stars in a group have visible spacing");
                    sawMixedGroup |= previousColor != star.color;
                }
            }
            groupSize++;
            previousX = star.x;
            previousColor = star.color;

            float ground = layout.surfaceYAt(star.x);
            if (Float.isNaN(ground)) {
                sawHoleStar = true;
                check(Math.abs(star.bottom() - (RunnerEngine.GROUND_Y
                                - RunnerEngine.JUMP_HEIGHT)) < .1f,
                        "star over a hole sits at maximum jump height");
                continue;
            }
            RunnerEngine.Platform overhead = null;
            for (RunnerEngine.Platform platform : layout.getPlatforms()) {
                if (platform.x > star.x + RunnerEngine.STAR_RADIUS) break;
                if (star.x + RunnerEngine.STAR_RADIUS > platform.x
                        && star.x - RunnerEngine.STAR_RADIUS < platform.end()) {
                    overhead = platform;
                    break;
                }
            }
            if (overhead != null && star.bottom() < overhead.top) {
                sawPlatformStar = true;
                float height = overhead.top - star.bottom();
                check(height >= RunnerEngine.PLAYER_HEIGHT / 2f - .1f
                                && height <= RunnerEngine.JUMP_HEIGHT + .1f,
                        "star above a platform uses its top as the height reference");
            } else {
                float height = ground - star.bottom();
                check(height >= RunnerEngine.PLAYER_HEIGHT / 2f - .1f
                                && height <= RunnerEngine.JUMP_HEIGHT + .1f,
                        "star bottom is between half a car and one jump above terrain");
                if (overhead != null) {
                    sawUnderPlatform = true;
                    check(star.y - RunnerEngine.STAR_RADIUS >= overhead.bottom + 2.9f,
                            "star beneath a platform fits below its underside");
                }
            }
        }
        check(groupSize >= 1 && groupSize <= 10, "last star group is bounded");
        for (boolean seen : sawColor) check(seen, "all five star colors appear after unlock");
        check(sawHoleStar && sawPlatformStar && sawUnderPlatform && sawMixedGroup,
                "stars occur over water, above and below platforms, and in mixed groups");

        RunnerEngine incremental = new RunnerEngine();
        incremental.startNewGame(19L);
        for (int x = 1000; x <= 200_000; x += 500) incremental.generateAhead(x);
        check(incremental.getStars().size() == layout.getStars().size(),
                "generation count does not depend on frame-by-frame lookahead");
        for (int i = 0; i < layout.getStars().size(); i++) {
            RunnerEngine.Star a = layout.getStars().get(i);
            RunnerEngine.Star b = incremental.getStars().get(i);
            check(a.x == b.x && a.y == b.y && a.color == b.color,
                    "seed fixes each star's place, height, and color");
        }

        RunnerEngine.Star target = layout.getStars().stream()
                .filter(star -> star.color == RunnerEngine.StarColor.BLUE)
                .findFirst().get();
        RunnerEngine pickup = placedAt(19L, target.x - RunnerEngine.PLAYER_WIDTH / 2f,
                target.y - RunnerEngine.PLAYER_HEIGHT / 2f, 0f);
        int beforeScore = pickup.getScore();
        int beforeDistance = pickup.getDistance();
        pickup.update(1f / 120f);
        check(pickup.getCollectedStars().length >= 1
                        && pickup.getStarBursts().size() >= 1
                        && pickup.getBonusPoints() >= target.color.points
                        && pickup.getScore() > beforeScore
                        && pickup.getDistance() == beforeDistance,
                "touching a star removes it, creates a matching firework, and adds points only");
        check(pickup.getStarBursts().get(0).color == target.color,
                "firework uses the collected star's color");
        float[] collected = pickup.getCollectedStars();
        RunnerEngine resumed = new RunnerEngine();
        resumed.restore(pickup.getSeed(), pickup.getLives(), pickup.getPlayerX(),
                pickup.getPlayerY(), pickup.getVelocityY(), pickup.getCountdownSeconds(),
                pickup.getElapsedRunSeconds(), pickup.getTerrainSpeedMultiplier(),
                pickup.getMode(), pickup.getResumeMode(), pickup.getHealth(),
                pickup.getDamageRecoverySeconds(), pickup.getFarthestX(),
                pickup.getVisibleWorldWidth(), pickup.getAntagonistTimers(),
                pickup.getProjectileState(), pickup.getDefeatedAntagonists(),
                pickup.getBonusPoints(), pickup.hasCompletedFirstJump(),
                pickup.isControlledJumpInProgress(), pickup.getAntagonistState(),
                pickup.getColoredProjectileState());
        resumed.restoreCollectedStars(collected);
        resumed.restoreStarBursts(pickup.getStarBurstState());
        check(resumed.getScore() == pickup.getScore()
                        && resumed.getStars().stream().noneMatch(s -> s.x == target.x)
                        && resumed.getStarBursts().size() == pickup.getStarBursts().size(),
                "score, collected stars, and in-flight fireworks survive restoration");
        resumed.continueGame();
        for (int step = 0; step < 100; step++) resumed.update(1f / 120f);
        check(resumed.getStarBursts().isEmpty(), "firework particles fade away");
    }

    private static void testLongerCourses() {
        int checkedCourses = 0;
        for (long trial = 0; trial < 100 && checkedCourses < 40; ++trial) {
            RunnerEngine game = started(trial * 0x9E3779B97F4A7C15L);
            game.generateAhead(5100f);
            if (game.getHazards().size() < 4 || game.getHazards().get(3).end() >= 5000f) {
                continue;
            }
            int cleared = 0;
            boolean jumped = false;
            // Stay below 500 m so this checks terrain before enemy encounters begin.
            for (int step = 0; step < 30_000 && cleared < 4; ++step) {
                RunnerEngine.Hazard hazard = game.getHazards().get(cleared);
                if (game.isWaitingForJump()) game.jump();
                if (hazard.type != RunnerEngine.HazardType.DIP && !jumped
                        && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        >= hazard.x - jumpLead(hazard.type)) {
                    game.jump();
                    jumped = true;
                }
                if (hazard.type == RunnerEngine.HazardType.DIP && hazard.exitDegrees == 90
                        && !jumped && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        >= hazard.end() - 120f
                        && game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                        >= RunnerEngine.GROUND_Y + hazard.height - 1f) {
                    game.jump();
                    jumped = true;
                }
                game.update(1f / 120f);
                check(game.getLives() == RunnerEngine.MAX_LIVES,
                        "generated course remains playable: trial=" + trial + " hazard=" + cleared);
                if (game.getPlayerX() > hazard.end() + 50f) {
                    cleared++;
                    jumped = false;
                }
            }
            check(cleared == 4, "four early obstacles cleared per course: trial="
                    + trial + ", cleared=" + cleared + ", x=" + game.getPlayerX());
            checkedCourses++;
        }
        check(checkedCourses == 40, "checked forty courses before enemy spawns");
    }

    public static void main(String[] args) {
        testLayout();
        testSceneryLayout();
        testGrassCoverage();
        testFirstJumpHint();
        testSlopes();
        testHovercarJumpPitch();
        testLandingOnSteepRisingSlopes();
        testLandingAcrossShortRisingSlopes();
        testTallTerrainIsPlayable();
        testSteepSlopeSlideAndJump();
        testFirstHazards();
        testDepression();
        testHoleAndExhaustedHealth();
        testSafeRespawn();
        testFailureAndPause();
        testSpeedAndManualPause();
        testWallStopAndJump();
        testAntagonistGeneration();
        testEnemyTerrainClearance();
        testDamageBlink();
        testMovingEnemyGenerationAndBehavior();
        testEnemyStomp();
        testMovingEnemyCollisions();
        testAntagonistAndProjectiles();
        testProjectileTerrainCollisions();
        testSuspendedPlatforms();
        testPlatformEnemyPatrolAndRestore();
        testWaterAndStars();
        testLongerCourses();
        System.out.println("RunnerEngine checks passed");
    }
}
