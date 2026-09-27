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
            float jumpLead = jumpLead(first.type);
            boolean jumped = false;
            for (int step = 0; step < 1500 && game.getPlayerX() < first.end() + 50f; ++step) {
                if (!jumped && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH >= first.x - jumpLead) {
                    game.jump();
                    jumped = true;
                }
                if (first.type == RunnerEngine.HazardType.DIP
                        && game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                        >= RunnerEngine.GROUND_Y + RunnerEngine.DIP_DEPTH - 1f) {
                    game.jump();
                }
                float beforeX = game.getPlayerX();
                float beforeY = game.getPlayerY();
                game.update(1f / 120f);
                check(game.getAttempts() == 1, "well timed jump clears first hazard (seed "
                        + seed + ", " + first.type + ", beforeX=" + beforeX + ", beforeY="
                        + beforeY + ", wall=" + first.x + ".." + first.end() + ")");
                if (first.type != RunnerEngine.HazardType.DIP) {
                    check(game.getHealth() == RunnerEngine.MAX_HEALTH,
                            "timed jump avoids damage on a ledge or hole: seed=" + seed);
                }
            }
            check(jumped, "jump was exercised");
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
            check(game.getAttempts() == 1, "sloped " + type + " traversed without damage");
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
            check(verticalEntry.getAttempts() == 1
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
        restored.restore(savedOnSlope.getSeed(), savedOnSlope.getAttempts(),
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
        check(restored.getAttempts() == 1, "restored slope can be continued");
    }

    private static void testDepression() {
        RunnerEngine game = started(seedFor(RunnerEngine.HazardType.DIP));
        RunnerEngine.Hazard dip = game.getHazards().get(0);
        for (int step = 0; step < 1000 && game.getHealth() == RunnerEngine.MAX_HEALTH;
                ++step) game.update(1f / 120f);
        check(game.getAttempts() == 1 && game.getHealth() == RunnerEngine.MAX_HEALTH - 1,
                "vertical far lip costs one health point without restarting");
        check(game.getPlayerX() < dip.end() - RunnerEngine.PLAYER_WIDTH - 50f,
                "far lip pushes the runner back for a jump");
        check(Math.abs(game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT -
                (RunnerEngine.GROUND_Y + RunnerEngine.DIP_DEPTH)) < .1f,
                "runner stands on the depression floor");
        check(game.getSpeed() == 0f, "depression wall stops the runner after recoil");
        for (int step = 0; step < 62 && game.getRecoilHoldSeconds() > 0f; ++step) {
            game.update(1f / 120f);
        }
        check(game.getRecoilHoldSeconds() == 0f, "depression recoil lasts half a second");
        game.jump();
        for (int step = 0; step < 120; ++step) game.update(1f / 120f);
        check(game.getPlayerX() > dip.end() + 40f && game.getAttempts() == 1
                        && game.getHealth() == RunnerEngine.MAX_HEALTH - 1,
                "a jump from the depression floor gets the runner out");
    }

    private static void testFailureAndPause() {
        RunnerEngine game = started(seedForTwoFailures());
        RunnerEngine.Hazard wall = game.getHazards().get(0);
        for (int i = 0; i < 600 && game.getHealth() == RunnerEngine.MAX_HEALTH; ++i) {
            game.update(1f / 60f);
        }
        check(game.getAttempts() == 1 && game.getHealth() == RunnerEngine.MAX_HEALTH - 1,
                "first wall collision only costs one health point");
        check(game.getPlayerX() < wall.x - RunnerEngine.PLAYER_WIDTH - 50f,
                "ledge collision pushes runner back for a jump");
        int farthest = game.getDistance();
        for (int i = 0; i < 30; ++i) game.update(1f / 120f);
        check(game.getDistance() >= farthest,
                "distance keeps the farthest progress after recoil");
        check(game.getHealth() == RunnerEngine.MAX_HEALTH - 1,
                "brief damage protection avoids multiple hits per collision");
        RunnerEngine woundedRestored = new RunnerEngine();
        woundedRestored.restore(game.getSeed(), game.getAttempts(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode(), game.getHealth(),
                game.getDamageRecoverySeconds(), game.getFarthestX(),
                game.getVisibleWorldWidth(), game.getAntagonistTimers(),
                game.getProjectileState(), game.getRecoilHoldSeconds(),
                game.isRecoveringSpeed(), game.getSpeedBeforeRecoil(),
                game.getRecoveryEndX(), game.getDefeatedAntagonists());
        check(woundedRestored.getMode() == RunnerEngine.Mode.PAUSED
                        && woundedRestored.getHealth() == game.getHealth()
                        && woundedRestored.getDamageRecoverySeconds()
                        == game.getDamageRecoverySeconds()
                        && woundedRestored.getRecoilHoldSeconds()
                        == game.getRecoilHoldSeconds()
                        && woundedRestored.getSpeedBeforeRecoil()
                        == game.getSpeedBeforeRecoil()
                        && woundedRestored.getDistance() == game.getDistance(),
                "pausing and restoring preserves lost health, recoil, and progress");
        for (int i = 0; i < 2000 && game.getAttempts() == 1; ++i) {
            game.update(1f / 120f);
        }
        check(game.getAttempts() == 2, "zero health begins the next attempt");
        check(game.getPlayerX() == 140f, "failure moves player to start");
        check(game.getHealth() == RunnerEngine.MAX_HEALTH,
                "new attempt restores all ten health points");
        check(game.getMode() == RunnerEngine.Mode.RUNNING, "restart resumes running");
        check(game.getCompletedAttemptDistances().size() == 1,
                "failed attempt's run distance is recorded");
        check(game.getBestDistance() == game.getCompletedAttemptDistances().get(0),
                "completed distance becomes best score");
        check(game.getSpeed() == RunnerEngine.BASE_SPEED,
                "speed resets on failure");
        long initialSeed = game.getSeed();
        float x = game.getPlayerX();
        game.pauseForBackground();
        game.update(10f);
        check(game.getMode() == RunnerEngine.Mode.PAUSED, "background pauses");
        check(game.getPlayerX() == x, "paused runner stays still");
        game.continueGame();
        game.update(1f / 60f);
        check(game.getPlayerX() > x, "Continue resumes movement");
        check(game.getSeed() == initialSeed, "retry retains original level");

        RunnerEngine.Hazard first = game.getHazards().get(0);
        boolean jumped = false;
        for (int step = 0; step < 500 && game.getPlayerX() < first.end() + 50f; ++step) {
            if (!jumped && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                    >= first.x - jumpLead(first.type)) {
                game.jump();
                jumped = true;
            }
            game.update(1f / 120f);
        }
        check(game.getAttempts() == 2 && jumped, "second attempt passes the first wall");
        for (int step = 0; step < 1000 && game.getAttempts() == 2; ++step) {
            game.update(1f / 120f);
        }
        check(game.getAttempts() == 3 && game.getCompletedAttemptCount() == 2,
                "each failed attempt records its run distance");
        check(game.getBestDistance() == game.getCompletedAttemptDistances().get(1)
                        && game.getBestDistance() > game.getCompletedAttemptDistances().get(0),
                "longer second attempt becomes the score to beat");

        RunnerEngine duringCountdown = new RunnerEngine();
        duringCountdown.startNewGame(8L);
        duringCountdown.update(1f);
        duringCountdown.pauseForBackground();
        duringCountdown.continueGame();
        check(duringCountdown.getMode() == RunnerEngine.Mode.COUNTDOWN,
                "Continue resumes an interrupted countdown");

        RunnerEngine restored = new RunnerEngine();
        restored.restore(game.getSeed(), game.getAttempts(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode());
        restored.loadAttemptDistances(game.getCompletedAttemptDistances());
        check(restored.getMode() == RunnerEngine.Mode.PAUSED,
                "restored activity requires Continue");
        check(restored.getPlayerX() == game.getPlayerX()
                        && restored.getHazards().get(0).type == game.getHazards().get(0).type,
                "restored level keeps position and layout");
        check(restored.getBestDistance() == game.getBestDistance(),
                "restored result history keeps the score to beat");
        game.startNewGame(456L);
        check(game.getBestDistance() == restored.getBestDistance()
                        && game.getCompletedAttemptCount() == 2,
                "a fresh course keeps prior attempt scores");
    }

    private static void testSpeedAndManualPause() {
        RunnerEngine game = started(12345L);
        game.restore(12345L, 1, 140f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
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

        game.restore(12345L, 1, 140f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 5.99d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        game.continueGame();
        game.update(.02f);
        check(game.getSpeed() > speed, "another increase occurs after six seconds");
    }

    private static void testWallRecoilSpeedRecovery() {
        long seed = seedForSlopes(RunnerEngine.HazardType.WALL, 0, 0);
        RunnerEngine game = started(seed);
        RunnerEngine.Hazard wall = game.getHazards().get(0);
        game.restore(seed, 1, wall.x - RunnerEngine.PLAYER_WIDTH - 1f,
                RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                0f, 0f, 8d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        game.continueGame();
        float previousSpeed = game.getSpeed();
        check(previousSpeed > RunnerEngine.BASE_SPEED, "wall approached at increased speed");
        game.update(1f / 120f);
        check(game.getHealth() == RunnerEngine.MAX_HEALTH - 1
                        && game.isRecoveringSpeed() && game.getSpeed() == 0f,
                "wall impact costs health, pushes back, and stops movement");
        float stoppedX = game.getPlayerX();
        float stoppedY = game.getPlayerY();
        double stoppedTime = game.getElapsedRunSeconds();
        game.jump();
        check(game.getVelocityY() == 0f, "jump input is ignored during recoil hold");

        RunnerEngine restored = new RunnerEngine();
        restored.restore(game.getSeed(), game.getAttempts(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode(), game.getHealth(),
                game.getDamageRecoverySeconds(), game.getFarthestX(),
                game.getVisibleWorldWidth(), game.getAntagonistTimers(),
                game.getProjectileState(), game.getRecoilHoldSeconds(),
                game.isRecoveringSpeed(), game.getSpeedBeforeRecoil(),
                game.getRecoveryEndX(), game.getDefeatedAntagonists());
        check(restored.getMode() == RunnerEngine.Mode.PAUSED
                        && restored.getSpeed() == 0f
                        && restored.getRecoilHoldSeconds() == game.getRecoilHoldSeconds()
                        && restored.getSpeedBeforeRecoil() == previousSpeed,
                "activity restoration keeps the stopped runner and previous speed");
        restored.update(2f);
        check(restored.getPlayerX() == stoppedX
                        && restored.getRecoilHoldSeconds() == game.getRecoilHoldSeconds(),
                "pause does not consume the half-second hold");
        restored.continueGame();
        int holdSteps = 0;
        while (restored.getRecoilHoldSeconds() > 0f && holdSteps < 62) {
            restored.update(1f / 120f);
            check(restored.getPlayerX() == stoppedX && restored.getPlayerY() == stoppedY
                            && restored.getSpeed() <= RunnerEngine.BASE_SPEED
                            && restored.getElapsedRunSeconds() == stoppedTime,
                    "recoil does not move the runner or advance the speed clock");
            holdSteps++;
        }
        check(holdSteps >= 60 && holdSteps <= 61
                        && restored.getRecoilHoldSeconds() == 0f
                        && restored.getSpeed() == RunnerEngine.BASE_SPEED,
                "runner waits half a second, then resumes at the starting speed");
        restored.jump();
        for (int step = 0; step < 250 && restored.isRecoveringSpeed(); ++step) {
            restored.update(1f / 120f);
            if (restored.isRecoveringSpeed()) {
                check(restored.getSpeed() == RunnerEngine.BASE_SPEED
                                && restored.getElapsedRunSeconds() == stoppedTime,
                        "starting speed lasts until the entire ledge is cleared");
            }
        }
        check(!restored.isRecoveringSpeed() && restored.getPlayerX() >= wall.end()
                        && Math.abs(restored.getSpeed() - previousSpeed) < .01f
                        && restored.getHealth() == RunnerEngine.MAX_HEALTH - 1,
                "clearing the ledge restores the precise speed from before impact");
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
            game.restore(seed, 1, 5140f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                    0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
            game.generateAhead(40_000f);
            for (RunnerEngine.Antagonist foe : game.getAntagonists()) {
                if (openGround(game, foe.x - lead - 10f, foe.x + 16f)) {
                    float enemyX = foe.x;
                    game.restore(seed, 1, enemyX - lead,
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
            game.restore(seed, 1, 5139f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
                    0f, 0f, 0d, 1f, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
            game.generateAhead(80_000f);
            check(game.getAntagonists().isEmpty(), "enemies stay absent just before 500 m");
            game.restore(seed, 1, 5140f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
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
    }

    private static void testAntagonistAndProjectiles() {
        RunnerEngine game = enemyOnOpenGround(130f);
        float enemyX = game.getAntagonists().stream()
                .filter(e -> e.x > game.getPlayerX()).findFirst().get().x;
        game.restore(game.getSeed(), game.getAttempts(), game.getPlayerX(),
                game.getPlayerY(), 0f, 0f, 8d, 1f,
                RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
        game.continueGame();
        float previousSpeed = game.getSpeed();
        check(previousSpeed > RunnerEngine.BASE_SPEED,
                "enemy approached faster than the starting speed");
        for (int step = 0; step < 300 && game.getHealth() == RunnerEngine.MAX_HEALTH;
                step++) game.update(1f / 120f);
        check(game.getHealth() == RunnerEngine.MAX_HEALTH - 1 && game.getAttempts() == 1,
                "touching a stationary oval takes one health point without restarting");
        check(game.getPlayerX() < enemyX - RunnerEngine.PLAYER_WIDTH - 50f,
                "oval impact leaves room to jump over it");
        final float struckEnemyX = enemyX;
        check(game.getAntagonists().stream().noneMatch(e -> e.x == struckEnemyX)
                        && game.getDefeatedAntagonists().length == 1,
                "struck oval immediately disappears while health is lost");
        RunnerEngine restoredHit = new RunnerEngine();
        restoredHit.restore(game.getSeed(), game.getAttempts(), game.getPlayerX(),
                game.getPlayerY(), game.getVelocityY(), game.getCountdownSeconds(),
                game.getElapsedRunSeconds(), game.getTerrainSpeedMultiplier(),
                game.getMode(), game.getResumeMode(), game.getHealth(),
                game.getDamageRecoverySeconds(), game.getFarthestX(),
                game.getVisibleWorldWidth(), game.getAntagonistTimers(),
                game.getProjectileState(), game.getRecoilHoldSeconds(),
                game.isRecoveringSpeed(), game.getSpeedBeforeRecoil(),
                game.getRecoveryEndX(), game.getDefeatedAntagonists());
        restoredHit.generateAhead(40_000f);
        check(restoredHit.getAntagonists().stream().noneMatch(e -> e.x == struckEnemyX),
                "struck oval stays gone after activity restoration and level generation");
        for (int step = 0; step < 62 && game.getRecoilHoldSeconds() > 0f; ++step) {
            game.update(1f / 120f);
        }
        check(game.getSpeed() == RunnerEngine.BASE_SPEED,
                "enemy recoil resumes at the original starting speed");
        game.jump();
        for (int step = 0; step < 120 && game.getPlayerX() < enemyX + 50f;
                step++) game.update(1f / 120f);
        check(game.getPlayerX() > enemyX + 40f && game.getAttempts() == 1
                        && game.getHealth() == RunnerEngine.MAX_HEALTH - 1,
                "well timed jump clears the oval after recoil");
        check(!game.isRecoveringSpeed()
                        && Math.abs(game.getSpeed() - previousSpeed) < .01f,
                "passing the oval restores the earlier speed");

        RunnerEngine shooter = enemyOnOpenGround(330f);
        enemyX = shooter.getAntagonists().stream()
                .filter(e -> e.x > shooter.getPlayerX()).findFirst().get().x;
        for (int step = 0; step < 120 && shooter.getProjectiles().isEmpty(); step++) {
            shooter.update(1f / 120f);
        }
        check(!shooter.getProjectiles().isEmpty(),
                "visible enemy fires a moving red dot towards the runner");
        float[] shots = shooter.getProjectileState();
        check(shots[2] < 0f && shots[3] == 0f,
                "projectile travels left with no vertical velocity");
        float[] timers = shooter.getAntagonistTimers();
        RunnerEngine restored = new RunnerEngine();
        restored.restore(shooter.getSeed(), shooter.getAttempts(),
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
        game.restore(seed, 1, 140f, RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT,
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
                float lead = jumpLead(hazard.type);
                if (!jumped && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        >= hazard.x - lead) {
                    game.jump();
                    jumped = true;
                }
                if (hazard.type == RunnerEngine.HazardType.DIP
                        && game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT
                        >= RunnerEngine.GROUND_Y + RunnerEngine.DIP_DEPTH - 1f) {
                    game.jump();
                }
                game.update(1f / 120f);
                check(game.getAttempts() == 1,
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
        testSlopes();
        testFirstHazards();
        testDepression();
        testFailureAndPause();
        testSpeedAndManualPause();
        testWallRecoilSpeedRecovery();
        testAntagonistGeneration();
        testAntagonistAndProjectiles();
        testProjectileTerrainCollisions();
        testLongerCourses();
        System.out.println("RunnerEngine checks passed");
    }
}
