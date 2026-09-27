package com.stepan.skyboundrunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure Java game rules, in logical units on a 540-unit-high world. */
public final class RunnerEngine {
    public static final float WORLD_HEIGHT = 540f;
    public static final float GROUND_Y = 400f;
    public static final float LEDGE_HEIGHT = 68f;
    public static final float PLAYER_WIDTH = 32f;
    public static final float PLAYER_HEIGHT = 46f;

    private static final float START_X = 140f;
    private static final float SPEED = 235f;
    private static final float GRAVITY = 1080f;
    private static final float JUMP_VELOCITY = -490f;

    public enum Mode { TITLE, COUNTDOWN, RUNNING, PAUSED }
    public enum HazardType { HOLE, WALL }

    public static final class Hazard {
        public final HazardType type;
        public final float x;
        public final float width;

        private Hazard(HazardType type, float x, float width) {
            this.type = type;
            this.x = x;
            this.width = width;
        }

        public float end() { return x + width; }
    }

    private final List<Hazard> hazards = new ArrayList<>();
    private Random random;
    private float nextHazardX;
    private long seed;
    private int attempts;
    private float playerX;
    private float playerY;
    private float velocityY;
    private float countdownSeconds;
    private Mode mode = Mode.TITLE;
    private Mode resumeMode = Mode.RUNNING;

    public RunnerEngine() {
        resetCourse(1L);
    }

    public void startNewGame(long courseSeed) {
        attempts = 1;
        resetCourse(courseSeed);
        countdownSeconds = 3f;
        mode = Mode.COUNTDOWN;
    }

    private void resetCourse(long courseSeed) {
        seed = courseSeed;
        random = new Random(seed);
        hazards.clear();
        nextHazardX = 840f;
        playerX = START_X;
        playerY = GROUND_Y - PLAYER_HEIGHT;
        velocityY = 0f;
        generateAhead(1800f);
    }

    public void generateAhead(float worldX) {
        while (nextHazardX < worldX) {
            HazardType type = random.nextBoolean() ? HazardType.HOLE : HazardType.WALL;
            float width = type == HazardType.HOLE
                    ? 92f + random.nextInt(27) : 105f + random.nextInt(38);
            Hazard hazard = new Hazard(type, nextHazardX, width);
            hazards.add(hazard);
            // Clear ground between hazards leaves time for a fresh jump.
            nextHazardX = hazard.end() + 290f + random.nextInt(125);
        }
    }

    public void jump() {
        if (mode == Mode.RUNNING && isStanding()) {
            velocityY = JUMP_VELOCITY;
        }
    }

    private boolean isStanding() {
        float center = playerX + PLAYER_WIDTH / 2f;
        float feet = playerY + PLAYER_HEIGHT;
        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.WALL && playerX < hazard.end()
                    && playerX + PLAYER_WIDTH > hazard.x
                    && Math.abs(feet - (GROUND_Y - LEDGE_HEIGHT)) < 1.2f) return true;
        }
        return !isHoleAt(center) && Math.abs(feet - GROUND_Y) < 1.2f
                && velocityY >= 0f;
    }

    public void update(float seconds) {
        if (mode == Mode.COUNTDOWN) {
            countdownSeconds -= Math.max(0f, seconds);
            if (countdownSeconds <= 0f) {
                countdownSeconds = 0f;
                mode = Mode.RUNNING;
            }
            return;
        }
        if (mode != Mode.RUNNING || seconds <= 0f) return;

        float dt = Math.min(seconds, 1f / 30f);
        generateAhead(playerX + 1600f);
        float oldFeet = playerY + PLAYER_HEIGHT;
        playerX += SPEED * dt;
        playerY += velocityY * dt + .5f * GRAVITY * dt * dt;
        velocityY += GRAVITY * dt;

        float center = playerX + PLAYER_WIDTH / 2f;
        float feet = playerY + PLAYER_HEIGHT;

        // A descending player can land on the top of a raised wall/ledge.
        if (velocityY >= 0f && oldFeet <= GROUND_Y - LEDGE_HEIGHT + 1f
                && feet >= GROUND_Y - LEDGE_HEIGHT) {
            for (Hazard hazard : hazards) {
                if (hazard.type == HazardType.WALL
                        && playerX < hazard.end()
                        && playerX + PLAYER_WIDTH > hazard.x) {
                    landAt(GROUND_Y - LEDGE_HEIGHT);
                    break;
                }
            }
        }

        // The ground supports the runner everywhere except inside holes.
        if (velocityY >= 0f && oldFeet <= GROUND_Y + 1f
                && playerY + PLAYER_HEIGHT >= GROUND_Y && !isHoleAt(center)) {
            landAt(GROUND_Y);
        }

        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.WALL && playerX < hazard.end()
                    && playerX + PLAYER_WIDTH > hazard.x
                    && playerY + PLAYER_HEIGHT > GROUND_Y - LEDGE_HEIGHT + 1f
                    && playerY < GROUND_Y) {
                restartAfterFailure();
                return;
            }
        }
        if (playerY + PLAYER_HEIGHT > GROUND_Y + 75f) {
            restartAfterFailure();
        }
    }

    private void landAt(float top) {
        playerY = top - PLAYER_HEIGHT;
        velocityY = 0f;
    }

    private boolean isHoleAt(float center) {
        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.HOLE
                    && center >= hazard.x && center < hazard.end()) return true;
        }
        return false;
    }

    private void restartAfterFailure() {
        attempts++;
        // Regenerate from the same seed so a failed attempt can be learned.
        resetCourse(seed);
    }

    public void pauseForBackground() {
        if (mode == Mode.RUNNING || mode == Mode.COUNTDOWN) {
            resumeMode = mode;
            mode = Mode.PAUSED;
        }
    }

    public void continueGame() {
        if (mode == Mode.PAUSED) mode = resumeMode;
    }

    public void restore(long courseSeed, int savedAttempts, float x, float y,
                        float vy, float remainingSeconds, Mode savedMode,
                        Mode savedResumeMode) {
        resetCourse(courseSeed);
        generateAhead(x + 1800f);
        attempts = Math.max(1, savedAttempts);
        playerX = x;
        playerY = y;
        velocityY = vy;
        countdownSeconds = remainingSeconds;
        if (savedMode == Mode.TITLE) {
            mode = Mode.TITLE;
        } else {
            mode = Mode.PAUSED;
            resumeMode = savedMode == Mode.PAUSED ? savedResumeMode : savedMode;
            if (resumeMode != Mode.COUNTDOWN) resumeMode = Mode.RUNNING;
        }
    }

    public List<Hazard> getHazards() { return hazards; }
    public Mode getMode() { return mode; }
    public Mode getResumeMode() { return resumeMode; }
    public long getSeed() { return seed; }
    public int getAttempts() { return attempts; }
    public float getPlayerX() { return playerX; }
    public float getPlayerY() { return playerY; }
    public float getVelocityY() { return velocityY; }
    public float getCountdownSeconds() { return countdownSeconds; }
    public int getDistance() { return Math.max(0, (int) (playerX - START_X) / 10); }
}
