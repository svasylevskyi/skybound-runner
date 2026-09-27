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
                game.getBonusMeters(), game.hasCompletedFirstJump(),
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
        boolean sawThirty = false;
        boolean sawFortyFive = false;
        boolean sawVertical = false;
        for (long seed = 0; seed < 100; ++seed) {
            RunnerEngine game = started(seed);
            game.generateAhead(50_000f);
            float previousEnd = -1000f;
            for (RunnerEngine.Hazard hazard : game.getHazards()) {
                check(hazard.x - previousEnd >= 289f, "hazards have recovery room");
                check(hazard.width <= 390f, "obstacles remain spaced and bounded");
                check(hazard.x <= hazard.flatStart && hazard.flatStart < hazard.flatEnd
                        && hazard.flatEnd <= hazard.end(), "ramp and flat sections are ordered");
                if (hazard.type != RunnerEngine.HazardType.HOLE) {
                    float height = hazard.type == RunnerEngine.HazardType.WALL
                            ? RunnerEngine.LEDGE_HEIGHT : RunnerEngine.DIP_DEPTH;
                    check(Math.abs(hazard.flatStart - hazard.x -
                            horizontalRun(height, hazard.entranceDegrees)) < .02f,
                            "entrance has its stated incline");
                    check(Math.abs(hazard.end() - hazard.flatEnd -
                            horizontalRun(height, hazard.exitDegrees)) < .02f,
                            "exit has its stated incline");
                    sawThirty |= hazard.entranceDegrees == 30 || hazard.exitDegrees == 30;
                    sawFortyFive |= hazard.entranceDegrees == 45 || hazard.exitDegrees == 45;
                    sawVertical |= hazard.entranceDegrees == 0 || hazard.exitDegrees == 0;
                }
                previousEnd = hazard.end();
            }
        }
        check(sawThirty && sawFortyFive && sawVertical,
                "generation includes both slope angles and right angle edges");
    }

    private static float horizontalRun(float height, int degrees) {
        if (degrees == 30) return (float) (height / Math.tan(Math.PI / 6d));
        if (degrees == 45) return height;
        return 0f;
    }

    private static void testFirstHazards() {
        boolean sawHole = false;
        boolean sawWall = false;
        boolean sawDip = false;
        for (long trial = 0; trial < 30; ++trial) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine game = started(seed);
            RunnerEngine.Hazard first = game.getHazards().get(0);
            sawHole |= first.type == RunnerEngine.HazardType.HOLE;
            sawWall |= first.type == RunnerEngine.HazardType.WALL;
            sawDip |= first.type == RunnerEngine.HazardType.DIP;
            boolean jumped = false;
            for (int step = 0; step < 1500 && game.getPlayerX() < first.end() + 50f; ++step) {
                if (first.type != RunnerEngine.HazardType.DIP && !jumped
                        && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        >= first.x - jumpLead(first.type)) {
                    game.jump();
                    jumped = true;
                }
                if (first.type == RunnerEngine.HazardType.DIP && first.exitDegrees == 0
                        && !jumped && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        >= first.end() - 120f
                        && game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                        >= RunnerEngine.GROUND_Y + RunnerEngine.DIP_DEPTH - 1f) {
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
                            "timed jump avoids damage on a ledge or hole: seed=" + seed);
                }
            }
            if (first.type != RunnerEngine.HazardType.DIP || first.exitDegrees == 0) {
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
        for (long trial = 0; trial < 200; ++trial) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine game = started(seed);
            if (game.getHazards().get(0).type == type
                    && (type != RunnerEngine.HazardType.DIP
                    || game.getHazards().get(0).exitDegrees == 0)) return seed;
        }
        throw new AssertionError("no generated " + type);
    }

    private static long seedForTwoFailures() {
        for (long trial = 0; trial < 200; ++trial) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine game = started(seed);
            RunnerEngine.Hazard first = game.getHazards().get(0);
            RunnerEngine.Hazard second = game.getHazards().get(1);
            if (first.type == RunnerEngine.HazardType.WALL && first.entranceDegrees == 0
                    && second.type == RunnerEngine.HazardType.HOLE) return seed;
        }
        throw new AssertionError("no suitable course for score checks");
    }

    private static long seedForSlopes(RunnerEngine.HazardType type,
                                      int entrance, int exit) {
        for (long trial = 0; trial < 1000; ++trial) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine.Hazard first = started(seed).getHazards().get(0);
            if (first.type == type && first.entranceDegrees == entrance
                    && first.exitDegrees == exit) return seed;
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
        for (int step = 0; step < 1500 && !sawBaseAgain; ++step) {
            game.update(1f / 120f);
            check(game.getLives() == RunnerEngine.MAX_LIVES, "sloped " + type + " traversed without damage");
            float center = game.getPlayerX() + RunnerEngine.PLAYER_WIDTH / 2f;
            float feet = game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT;
            if (center > first.x + 5f && center < first.flatStart - 5f) {
                check(Math.abs(feet - first.surfaceYAt(center)) < 1.5f,
                        "runner follows first incline");
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
                if (type == RunnerEngine.HazardType.WALL) {
                    check(game.getSpeed() > game.getBaseSpeed(), "downhill speeds runner");
                } else {
                    check(game.getSpeed() < game.getBaseSpeed(), "uphill slows runner");
                }
                enteredLastSlope = true;
            }
            if (center > first.end() + 60f && center < first.end() + 100f) {
                check(Math.abs(game.getSpeed() - game.getBaseSpeed()) > .01f,
                        "slope effect remains briefly after the incline");
                check(Math.abs(game.getSpeed() - game.getBaseSpeed())
                        < game.getBaseSpeed() * .20f,
                        "slope effect gradually decays");
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

        RunnerEngine verticalEntry = started(seedForSlopes(RunnerEngine.HazardType.DIP, 0, 30));
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

    private static void testDepression() {
        RunnerEngine game = started(seedFor(RunnerEngine.HazardType.DIP));
        RunnerEngine.Hazard dip = game.getHazards().get(0);
        for (int step = 0; step < 1000 && game.getLives() == RunnerEngine.MAX_LIVES;
                ++step) game.update(1f / 120f);
        check(game.getLives() == RunnerEngine.MAX_LIVES - 1
                        && game.getMode() == RunnerEngine.Mode.COUNTDOWN
                        && game.getHealth() == RunnerEngine.MAX_HEALTH,
                "vertical depression wall costs one life and begins countdown");
        check(game.getFarthestX() > dip.x
                        && Math.abs(game.getFarthestX() - game.getPlayerX() - 500f) < .1f,
                "depression collision respawns 50 metres behind the hit");
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
        RunnerEngine game = started(seedForTwoFailures());
        RunnerEngine.Hazard wall = game.getHazards().get(0);
        game.loadBestDistance(25);
        for (int i = 0; i < 600 && game.getLives() == RunnerEngine.MAX_LIVES; ++i) {
            game.update(1f / 60f);
        }
        check(game.getLives() == RunnerEngine.MAX_LIVES - 1
                        && game.getHealth() == RunnerEngine.MAX_HEALTH
                        && game.getMode() == RunnerEngine.Mode.COUNTDOWN,
                "wall collision costs one life and restores full health");
        check(game.getPlayerX() < wall.x - RunnerEngine.PLAYER_WIDTH - 400f,
                "wall collision sends runner back about 50 metres");
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
                    "each collision with the wall costs exactly one life");
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

        RunnerEngine lowerScore = started(seedForTwoFailures());
        lowerScore.loadBestDistance(999);
        float wallFace = lowerScore.getHazards().get(0).x;
        lowerScore.restore(lowerScore.getSeed(), 1,
                wallFace - RunnerEngine.PLAYER_WIDTH - 1f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
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

    private static void testWallLifeLossAndCountdown() {
        long seed = seedForSlopes(RunnerEngine.HazardType.WALL, 0, 0);
        RunnerEngine game = started(seed);
        RunnerEngine.Hazard wall = game.getHazards().get(0);
        game.restore(seed, RunnerEngine.MAX_LIVES, wall.x - RunnerEngine.PLAYER_WIDTH - 1f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 8d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        game.continueGame();
        float previousSpeed = game.getSpeed();
        check(previousSpeed > RunnerEngine.BASE_SPEED, "wall approached at increased speed");
        game.update(1f / 120f);
        check(game.getLives() == RunnerEngine.MAX_LIVES - 1
                        && game.getMode() == RunnerEngine.Mode.COUNTDOWN
                        && game.getHealth() == RunnerEngine.MAX_HEALTH,
                "vertical wall costs a life and refills health");
        check(Math.abs(game.getFarthestX() - game.getPlayerX() - 500f) < .1f
                        && game.getPlayerX() > 140f,
                "wall respawn moves exactly 50 metres back when ground is clear");
        check(game.getElapsedRunSeconds() == 8d
                        && game.getSpeed() == previousSpeed
                        && game.getBestDistance() == 0,
                "life loss preserves speed progression and records no score yet");
        float respawnX = game.getPlayerX();
        game.update(2f);
        check(game.getMode() == RunnerEngine.Mode.COUNTDOWN
                        && game.getPlayerX() == respawnX,
                "respawn waits through the three-second countdown");
        game.update(1f);
        check(game.getMode() == RunnerEngine.Mode.RUNNING,
                "runner resumes automatically after countdown");
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
                if (openGround(game, foe.x - lead - 10f, foe.x + 16f)) {
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
                if (foe.type != RunnerEngine.EnemyType.MOVING
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
                        && Math.abs(shot.getY() - foe.y - RunnerEngine.PLAYER_HEIGHT / 2f) < .1f
                        && shot.getY() < RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT / 2f,
                "red projectile launches horizontally at the airborne enemy's height");

        RunnerEngine restored = new RunnerEngine();
        restored.restore(game.getSeed(), game.getLives(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode(), game.getHealth(),
                game.getDamageRecoverySeconds(), game.getFarthestX(),
                game.getVisibleWorldWidth(), game.getAntagonistTimers(),
                game.getProjectileState(), game.getDefeatedAntagonists(),
                game.getBonusMeters(), game.hasCompletedFirstJump(),
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
                        && hit.getBonusMeters() == 1
                        && hit.getDefeatedAntagonists()[0] == foe.spawnX
                        && hit.getBonusPopups().get(0).type == RunnerEngine.EnemyType.MOVING,
                "body contact removes red enemy, costs two health and shows red +1");
        RunnerEngine restoredHit = new RunnerEngine();
        restoredHit.restore(hit.getSeed(), hit.getLives(), hit.getPlayerX(),
                hit.getPlayerY(), hit.getVelocityY(), hit.getCountdownSeconds(),
                hit.getElapsedRunSeconds(), hit.getTerrainSpeedMultiplier(),
                hit.getMode(), hit.getResumeMode(), hit.getHealth(),
                hit.getDamageRecoverySeconds(), hit.getFarthestX(),
                hit.getVisibleWorldWidth(), hit.getAntagonistTimers(),
                hit.getProjectileState(), hit.getDefeatedAntagonists(),
                hit.getBonusMeters(), hit.hasCompletedFirstJump(),
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
                        && stomp.getBonusMeters() == 2
                        && stomp.getBonusPopups().get(0).amount == 2
                        && stomp.getBonusPopups().get(0).type == RunnerEngine.EnemyType.MOVING,
                "landing on red enemy gives red +2 without damage");
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
                check(!Float.isNaN(surface)
                                && Math.abs(foe.y + RunnerEngine.PLAYER_HEIGHT - surface) < .1f,
                        "enemy stands on ground, not in a hole");
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
        check(sawGround && sawLedge && sawDepression && sawSlope,
                "enemies can appear on ground, ledges, depressions, and inclines");

        RunnerEngine bonusRun = new RunnerEngine();
        bonusRun.restore(1L, RunnerEngine.MAX_LIVES, 5130f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT, 0f, 0f, 0d, 1f,
                RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING,
                RunnerEngine.MAX_HEALTH, 0f, 5130f, 960f, null, null, null,
                3, false, false);
        bonusRun.generateAhead(80_000f);
        check(bonusRun.getDistance() == 502 && bonusRun.getAntagonists().isEmpty(),
                "bonus meters do not unlock enemies before 500 physical metres");
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
                        && stomp.getBonusMeters() == 2
                        && stomp.getDistance() == (int) ((stomp.getFarthestX() - 140f)
                        / RunnerEngine.WORLD_UNITS_PER_METER) + 2,
                "stomp awards two bonus metres without changing health, lives or speed");
        check(stomp.getBonusPopups().size() == 1
                        && stomp.getBonusPopups().get(0).amount == 2
                        && stomp.getBonusPopups().get(0).type == RunnerEngine.EnemyType.STATIONARY
                        && Math.abs(stomp.getBonusPopups().get(0).x
                        - foe.x - RunnerEngine.PLAYER_WIDTH / 2f) < .01f,
                "stomp shows a +2 bonus at the orange enemy's position");
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
                        && justBelow.getBonusMeters() == 1
                        && justBelow.getAntagonists().stream().noneMatch(e -> e.x == lowerFoe.x),
                "descending contact below the upper quarter costs two health and gives one bonus metre");
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
                        && game.getBonusMeters() == 1,
                "running into an oval costs two health and awards one bonus metre");
        check(game.getBonusPopups().size() == 1
                        && game.getBonusPopups().get(0).amount == 1
                        && game.getBonusPopups().get(0).type == RunnerEngine.EnemyType.STATIONARY,
                "collision shows a +1 label in the orange enemy's color");
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
                game.getBonusMeters(), game.hasCompletedFirstJump(),
                game.isControlledJumpInProgress());
        restoredHit.generateAhead(40_000f);
        check(restoredHit.getMode() == RunnerEngine.Mode.PAUSED
                        && restoredHit.getResumeMode() == RunnerEngine.Mode.RUNNING
                        && restoredHit.getBonusMeters() == 1
                        && restoredHit.getDistance() == game.getDistance()
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
                        && lastHealth.getBonusMeters() == 1,
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
                        && finalLife.getBonusMeters() == 1
                        && finalLife.getBestDistance() == finalLife.getDistance(),
                "bonus from the final collision counts toward the saved best score");
        finalLife.startNewGame(99L);
        check(finalLife.getBonusMeters() == 0 && finalLife.getDistance() == 0,
                "new run resets bonus metres");

        RunnerEngine shooter = enemyOnOpenGround(330f);
        enemyX = shooter.getAntagonists().stream()
                .filter(e -> e.x > shooter.getPlayerX()).findFirst().get().x;
        for (int step = 0; step < 120 && shooter.getProjectiles().isEmpty(); step++) {
            shooter.update(1f / 120f);
        }
        check(!shooter.getProjectiles().isEmpty(),
                "visible stationary enemy fires a moving orange dot towards the runner");
        check(shooter.getProjectiles().get(0).type == RunnerEngine.EnemyType.STATIONARY,
                "stationary enemy projectile keeps its orange type");
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
        long verticalSeed = seedForSlopes(RunnerEngine.HazardType.WALL, 0, 0);
        RunnerEngine vertical = started(verticalSeed);
        float verticalEdge = vertical.getHazards().get(0).end();
        checkTerrainAbsorbsShot(withProjectile(verticalSeed, verticalEdge + 25f,
                RunnerEngine.GROUND_Y - 24f), "vertical ledge face");

        long slopeSeed = seedForSlopes(RunnerEngine.HazardType.WALL, 30, 45);
        RunnerEngine sloped = started(slopeSeed);
        float slopeEdge = sloped.getHazards().get(0).end();
        checkTerrainAbsorbsShot(withProjectile(slopeSeed, slopeEdge + 25f,
                RunnerEngine.GROUND_Y - 24f), "45-degree ledge slope");

        long dipSeed = seedForSlopes(RunnerEngine.HazardType.DIP, 30, 0);
        RunnerEngine dip = started(dipSeed);
        float dipEntry = dip.getHazards().get(0).flatStart;
        checkTerrainAbsorbsShot(withProjectile(dipSeed, dipEntry + 25f,
                RunnerEngine.GROUND_Y + 18f), "30-degree depression slope");

        checkTerrainAbsorbsShot(withProjectile(verticalSeed, 500f,
                RunnerEngine.GROUND_Y - 3f), "flat ground");

        RunnerEngine highShot = withProjectile(verticalSeed, verticalEdge + 25f,
                RunnerEngine.GROUND_Y - RunnerEngine.LEDGE_HEIGHT - 25f);
        float startingY = highShot.getProjectiles().get(0).getY();
        for (int step = 0; step < 60; step++) highShot.update(1f / 120f);
        check(highShot.getProjectiles().size() == 1
                        && highShot.getProjectiles().get(0).getY() == startingY,
                "a projectile entirely above the wall keeps flying horizontally");
    }

    private static void testLongerCourses() {
        for (long trial = 0; trial < 40; ++trial) {
            RunnerEngine game = started(trial * 0x9E3779B97F4A7C15L);
            int cleared = 0;
            boolean jumped = false;
            // Stay below 500 m so this checks terrain before enemy encounters begin.
            for (int step = 0; step < 15_000 && cleared < 6; ++step) {
                RunnerEngine.Hazard hazard = game.getHazards().get(cleared);
                if (hazard.type != RunnerEngine.HazardType.DIP && !jumped
                        && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        >= hazard.x - jumpLead(hazard.type)) {
                    game.jump();
                    jumped = true;
                }
                if (hazard.type == RunnerEngine.HazardType.DIP && hazard.exitDegrees == 0
                        && !jumped && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        >= hazard.end() - 120f
                        && game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                        >= RunnerEngine.GROUND_Y + RunnerEngine.DIP_DEPTH - 1f) {
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
            check(cleared == 6, "six early obstacles cleared per course");
        }
    }

    public static void main(String[] args) {
        testLayout();
        testFirstJumpHint();
        testSlopes();
        testFirstHazards();
        testDepression();
        testHoleAndExhaustedHealth();
        testSafeRespawn();
        testFailureAndPause();
        testSpeedAndManualPause();
        testWallLifeLossAndCountdown();
        testAntagonistGeneration();
        testMovingEnemyGenerationAndBehavior();
        testEnemyStomp();
        testMovingEnemyCollisions();
        testAntagonistAndProjectiles();
        testProjectileTerrainCollisions();
        testLongerCourses();
        System.out.println("RunnerEngine checks passed");
    }
}
