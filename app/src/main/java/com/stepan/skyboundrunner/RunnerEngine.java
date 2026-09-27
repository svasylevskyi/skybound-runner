package com.stepan.skyboundrunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Pure Java game rules, in logical units on a 540-unit-high world. */
public final class RunnerEngine {
    public static final float WORLD_HEIGHT = 540f;
    public static final float GROUND_Y = 400f;
    public static final float LEDGE_HEIGHT = 68f;
    public static final float DIP_DEPTH = 58f;
    public static final float PLAYER_WIDTH = 32f;
    public static final float PLAYER_HEIGHT = 46f;

    private static final float START_X = 140f;
    public static final float BASE_SPEED = 235f;
    private static final float SPEED_STEP = 7f;
    private static final float SPEED_INTERVAL_SECONDS = 3f;
    private static final float GRAVITY = 1080f;
    private static final float JUMP_VELOCITY = -490f;

    public enum Mode { TITLE, COUNTDOWN, RUNNING, PAUSED }
    public enum HazardType { HOLE, WALL, DIP }

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
    private final List<Integer> completedAttemptDistances = new ArrayList<>();
    private Random random;
    private float nextHazardX;
    private long seed;
    private int attempts;
    private float playerX;
    private float playerY;
    private float velocityY;
    private float countdownSeconds;
    private double elapsedRunSeconds;
    private int bestDistance;
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
        elapsedRunSeconds = 0d;
        generateAhead(1800f);
    }

    public void generateAhead(float worldX) {
        while (nextHazardX < worldX) {
            HazardType type = HazardType.values()[random.nextInt(HazardType.values().length)];
            float width;
            if (type == HazardType.HOLE) {
                width = 92f + random.nextInt(27);
            } else if (type == HazardType.WALL) {
                width = 105f + random.nextInt(38);
            } else {
                width = 155f + random.nextInt(31);
            }
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
                    && Math.abs(feet - (GROUND_Y - LEDGE_HEIGHT)) < 1.2f
                    && velocityY >= 0f) return true;
        }
        return Math.abs(feet - surfaceYAt(center)) < 1.2f && velocityY >= 0f;
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

        float remaining = Math.min(seconds, .05f);
        while (remaining > .000001f) {
            float dt = Math.min(remaining, 1f / 120f);
            if (advance(dt)) return;
            remaining -= dt;
        }
    }

    /** Returns true when a collision ended this attempt. */
    private boolean advance(float dt) {
        generateAhead(playerX + 1600f);
        float oldX = playerX;
        float oldFeet = playerY + PLAYER_HEIGHT;
        float pace = getSpeed() / BASE_SPEED;
        playerX += getSpeed() * dt;
        playerY += velocityY * pace * dt + .5f * GRAVITY * pace * pace * dt * dt;
        velocityY += GRAVITY * pace * dt;

        // The far lip of a depression blocks forward motion without ending the run.
        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.DIP && playerX < hazard.end()
                    && playerX + PLAYER_WIDTH > hazard.end()
                    && playerY + PLAYER_HEIGHT > GROUND_Y + 1f
                    && playerY < GROUND_Y + DIP_DEPTH) {
                playerX = hazard.end() - PLAYER_WIDTH;
                break;
            }
        }

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

        float surfaceY = surfaceYAt(center);
        if (velocityY >= 0f && oldFeet <= surfaceY + 1f
                && playerY + PLAYER_HEIGHT >= surfaceY) {
            landAt(surfaceY);
        }

        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.WALL && playerX < hazard.end()
                    && playerX + PLAYER_WIDTH > hazard.x
                    && playerY + PLAYER_HEIGHT > GROUND_Y - LEDGE_HEIGHT + 1f
                    && playerY < GROUND_Y) {
                restartAfterFailure();
                return true;
            }
        }
        if (playerY + PLAYER_HEIGHT > GROUND_Y + 75f) {
            restartAfterFailure();
            return true;
        }
        // Waiting at a dip's lip is not counted as running time.
        if (playerX > oldX + .01f) elapsedRunSeconds += dt;
        return false;
    }

    private void landAt(float top) {
        playerY = top - PLAYER_HEIGHT;
        velocityY = 0f;
    }

    private float surfaceYAt(float center) {
        for (Hazard hazard : hazards) {
            if (center >= hazard.x && center < hazard.end()) {
                if (hazard.type == HazardType.HOLE) return Float.NaN;
                if (hazard.type == HazardType.DIP) return GROUND_Y + DIP_DEPTH;
            }
        }
        return GROUND_Y;
    }

    private void restartAfterFailure() {
        int distance = getDistance();
        completedAttemptDistances.add(distance);
        bestDistance = Math.max(bestDistance, distance);
        attempts++;
        // Regenerate from the same seed so a failed attempt can be learned.
        resetCourse(seed);
    }

    public void pauseForBackground() {
        pause();
    }

    public void pause() {
        if (mode == Mode.RUNNING || mode == Mode.COUNTDOWN) {
            resumeMode = mode;
            mode = Mode.PAUSED;
        }
    }

    public void continueGame() {
        if (mode == Mode.PAUSED) mode = resumeMode;
    }

    public void restore(long courseSeed, int savedAttempts, float x, float y,
                        float vy, float remainingSeconds, double savedRunSeconds, Mode savedMode,
                        Mode savedResumeMode) {
        resetCourse(courseSeed);
        generateAhead(x + 1800f);
        attempts = Math.max(1, savedAttempts);
        playerX = x;
        playerY = y;
        velocityY = vy;
        countdownSeconds = remainingSeconds;
        elapsedRunSeconds = Math.max(0d, savedRunSeconds);
        if (savedMode == Mode.TITLE) {
            mode = Mode.TITLE;
        } else {
            mode = Mode.PAUSED;
            resumeMode = savedMode == Mode.PAUSED ? savedResumeMode : savedMode;
            if (resumeMode != Mode.COUNTDOWN) resumeMode = Mode.RUNNING;
        }
    }

    public List<Hazard> getHazards() { return hazards; }
    public List<Integer> getCompletedAttemptDistances() {
        return Collections.unmodifiableList(completedAttemptDistances);
    }
    public int getCompletedAttemptCount() { return completedAttemptDistances.size(); }
    public void loadAttemptDistances(List<Integer> distances) {
        completedAttemptDistances.clear();
        bestDistance = 0;
        for (int distance : distances) {
            if (distance >= 0) {
                completedAttemptDistances.add(distance);
                bestDistance = Math.max(bestDistance, distance);
            }
        }
    }
    public Mode getMode() { return mode; }
    public Mode getResumeMode() { return resumeMode; }
    public long getSeed() { return seed; }
    public int getAttempts() { return attempts; }
    public float getPlayerX() { return playerX; }
    public float getPlayerY() { return playerY; }
    public float getVelocityY() { return velocityY; }
    public float getCountdownSeconds() { return countdownSeconds; }
    public double getElapsedRunSeconds() { return elapsedRunSeconds; }
    public float getSpeed() {
        return BASE_SPEED + SPEED_STEP
                * (int) Math.floor((elapsedRunSeconds + .000001d) / SPEED_INTERVAL_SECONDS);
    }
    public int getBestDistance() { return bestDistance; }
    public int getDistance() { return Math.max(0, (int) (playerX - START_X) / 10); }
}
