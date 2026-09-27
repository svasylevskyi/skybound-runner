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
        for (long seed = 0; seed < 100; ++seed) {
            RunnerEngine game = started(seed);
            game.generateAhead(50_000f);
            float previousEnd = -1000f;
            for (RunnerEngine.Hazard hazard : game.getHazards()) {
                check(hazard.x - previousEnd >= 289f, "hazards have recovery room");
                check(hazard.width <= 185f, "hazards remain jumpable");
                previousEnd = hazard.end();
            }
        }
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
            while (game.getPlayerX() < first.end() + 50f) {
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
            if (game.getHazards().get(0).type == type) return seed;
        }
        throw new AssertionError("no generated " + type);
    }

    private static long seedForTwoFailures() {
        for (long trial = 0; trial < 200; ++trial) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine game = started(seed);
            if (game.getHazards().get(0).type == RunnerEngine.HazardType.WALL
                    && game.getHazards().get(1).type != RunnerEngine.HazardType.DIP) return seed;
        }
        throw new AssertionError("no suitable course for score checks");
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
                game.getElapsedRunSeconds(), game.getMode(), game.getResumeMode());
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
                0f, 0f, 2.99d, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
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
                0f, 0f, 5.99d, RunnerEngine.Mode.RUNNING, RunnerEngine.Mode.RUNNING);
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
        testFirstHazards();
        testDepression();
        testFailureAndPause();
        testSpeedAndManualPause();
        testLongerCourses();
        System.out.println("RunnerEngine checks passed");
    }
}
