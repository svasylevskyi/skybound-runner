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
                    && (second.type == RunnerEngine.HazardType.HOLE
                    || second.type == RunnerEngine.HazardType.WALL
                    && second.entranceDegrees == 0)) return seed;
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
            check(verticalEntry.getAttempts() == 1,
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
        for (int step = 0; step < 1000 && game.getPlayerX() < dip.end() -
                RunnerEngine.PLAYER_WIDTH - 1f; ++step) game.update(1f / 120f);
        float lipX = dip.end() - RunnerEngine.PLAYER_WIDTH;
        for (int step = 0; step < 120; ++step) game.update(1f / 120f);
        check(game.getAttempts() == 1, "falling into a depression causes no damage");
        check(Math.abs(game.getPlayerX() - lipX) < .1f, "runner stops at the far lip");
        check(Math.abs(game.getPlayerY() + RunnerEngine.PLAYER_HEIGHT -
                (RunnerEngine.GROUND_Y + RunnerEngine.DIP_DEPTH)) < .1f,
                "runner stands on the depression floor");
        double waitingTime = game.getElapsedRunSeconds();
        for (int step = 0; step < 600; ++step) game.update(1f / 120f);
        check(game.getElapsedRunSeconds() == waitingTime,
                "waiting for a jump does not increase speed");
        game.jump();
        for (int step = 0; step < 120; ++step) game.update(1f / 120f);
        check(game.getPlayerX() > dip.end() + 40f && game.getAttempts() == 1,
                "a jump from the depression floor gets the runner out");
    }

    private static void testFailureAndPause() {
        RunnerEngine game = started(seedForTwoFailures());
        for (int i = 0; i < 600 && game.getAttempts() == 1; ++i) {
            game.update(1f / 60f);
        }
        check(game.getAttempts() == 2, "failing a hazard starts next attempt");
        check(game.getPlayerX() == 140f, "failure moves player to start");
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

    private static void testLongerCourses() {
        for (long trial = 0; trial < 40; ++trial) {
            RunnerEngine game = started(trial * 0x9E3779B97F4A7C15L);
            int cleared = 0;
            boolean jumped = false;
            for (int step = 0; step < 15_000 && cleared < 12; ++step) {
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
            check(cleared == 12, "twelve obstacles cleared per course");
        }
    }

    public static void main(String[] args) {
        testLayout();
        testSlopes();
        testFirstHazards();
        testDepression();
        testFailureAndPause();
        testSpeedAndManualPause();
        testLongerCourses();
        System.out.println("RunnerEngine checks passed");
    }
}
