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
                check(hazard.width <= 142f, "hazards remain jumpable");
                previousEnd = hazard.end();
            }
        }
    }

    private static void testFirstHazards() {
        boolean sawHole = false;
        boolean sawWall = false;
        for (long trial = 0; trial < 30; ++trial) {
            long seed = trial * 0x9E3779B97F4A7C15L;
            RunnerEngine game = started(seed);
            RunnerEngine.Hazard first = game.getHazards().get(0);
            sawHole |= first.type == RunnerEngine.HazardType.HOLE;
            sawWall |= first.type == RunnerEngine.HazardType.WALL;
            float jumpLead = first.type == RunnerEngine.HazardType.HOLE ? 45f : 88f;
            boolean jumped = false;
            while (game.getPlayerX() < first.end() + 50f) {
                if (!jumped && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH >= first.x - jumpLead) {
                    game.jump();
                    jumped = true;
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
        check(sawHole && sawWall, "generator emits both holes and walls");
    }

    private static void testFailureAndPause() {
        RunnerEngine game = started(7L);
        for (int i = 0; i < 600 && game.getAttempts() == 1; ++i) {
            game.update(1f / 60f);
        }
        check(game.getAttempts() == 2, "failing a hazard starts next attempt");
        check(game.getPlayerX() == 140f, "failure moves player to start");
        check(game.getMode() == RunnerEngine.Mode.RUNNING, "restart resumes running");
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
                game.getMode(), game.getResumeMode());
        check(restored.getMode() == RunnerEngine.Mode.PAUSED,
                "restored activity requires Continue");
        check(restored.getPlayerX() == game.getPlayerX()
                        && restored.getHazards().get(0).type == game.getHazards().get(0).type,
                "restored level keeps position and layout");
    }

    private static void testLongerCourses() {
        for (long trial = 0; trial < 40; ++trial) {
            RunnerEngine game = started(trial * 0x9E3779B97F4A7C15L);
            int cleared = 0;
            boolean jumped = false;
            for (int step = 0; step < 15_000 && cleared < 12; ++step) {
                RunnerEngine.Hazard hazard = game.getHazards().get(cleared);
                float lead = hazard.type == RunnerEngine.HazardType.HOLE ? 45f : 88f;
                if (!jumped && game.getPlayerX() + RunnerEngine.PLAYER_WIDTH
                        >= hazard.x - lead) {
                    game.jump();
                    jumped = true;
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
        testFailureAndPause();
        testLongerCourses();
        System.out.println("RunnerEngine checks passed");
    }
}
