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
    public static final float WORLD_UNITS_PER_METER = 10f;
    public static final float PLAYER_WIDTH = 32f;
    public static final float PLAYER_HEIGHT = 46f;

    private static final float START_X = 140f;
    public static final float BASE_SPEED = 235f;
    private static final float SPEED_STEP = 7f;
    private static final float SPEED_INTERVAL_SECONDS = 3f;
    private static final float GRAVITY = 1080f;
    private static final float JUMP_VELOCITY = -490f;
    private static final float THIRTY_DEGREE_RUN = 1.7320508f;
    private static final float SLOPE_RECOVERY_PER_SECOND = .25f;
    private static final HazardType[] HAZARD_TYPES = HazardType.values();
    private static final int[] EDGE_ANGLES = {0, 30, 45};

    public enum Mode { TITLE, COUNTDOWN, RUNNING, PAUSED }
    public enum HazardType { HOLE, WALL, DIP }

    public static final class Hazard {
        public final HazardType type;
        public final float x;
        public final float width;
        public final int entranceDegrees;
        public final int exitDegrees;
        public final float flatStart;
        public final float flatEnd;

        private Hazard(HazardType type, float x, float flatWidth,
                       int entranceDegrees, int exitDegrees) {
            this.type = type;
            this.x = x;
            this.entranceDegrees = entranceDegrees;
            this.exitDegrees = exitDegrees;
            float height = type == HazardType.WALL ? LEDGE_HEIGHT : DIP_DEPTH;
            this.flatStart = x + rampRun(height, entranceDegrees);
            this.flatEnd = flatStart + flatWidth;
            this.width = flatEnd + rampRun(height, exitDegrees) - x;
        }

        public float end() { return x + width; }

        public float surfaceYAt(float worldX) {
            if (type == HazardType.HOLE) return Float.NaN;
            float change = type == HazardType.WALL ? -LEDGE_HEIGHT : DIP_DEPTH;
            if (worldX < flatStart && entranceDegrees != 0) {
                return GROUND_Y + change * (worldX - x) / (flatStart - x);
            }
            if (worldX < flatEnd) return GROUND_Y + change;
            if (worldX < end() && exitDegrees != 0) {
                return GROUND_Y + change * (end() - worldX) / (end() - flatEnd);
            }
            return GROUND_Y;
        }

        /** Positive means uphill, negative means downhill as the runner moves right. */
        public int slopeDegreesAt(float worldX) {
            if (worldX < x || worldX >= end()) return 0;
            if (worldX < flatStart && entranceDegrees != 0) {
                return type == HazardType.WALL ? entranceDegrees : -entranceDegrees;
            }
            if (worldX >= flatEnd && worldX < end() && exitDegrees != 0) {
                return type == HazardType.WALL ? -exitDegrees : exitDegrees;
            }
            return 0;
        }

        private static float rampRun(float height, int degrees) {
            if (degrees == 30) return height * THIRTY_DEGREE_RUN;
            return degrees == 45 ? height : 0f;
        }
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
    private float terrainSpeedMultiplier = 1f;
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
        terrainSpeedMultiplier = 1f;
        generateAhead(1800f);
    }

    public void generateAhead(float worldX) {
        while (nextHazardX < worldX) {
            HazardType type = HAZARD_TYPES[random.nextInt(HAZARD_TYPES.length)];
            float flatWidth;
            if (type == HazardType.HOLE) {
                flatWidth = 92f + random.nextInt(27);
            } else if (type == HazardType.WALL) {
                flatWidth = 105f + random.nextInt(38);
            } else {
                flatWidth = 155f + random.nextInt(31);
            }
            int entrance = type == HazardType.HOLE ? 0 : EDGE_ANGLES[random.nextInt(3)];
            int exit = type == HazardType.HOLE ? 0 : EDGE_ANGLES[random.nextInt(3)];
            Hazard hazard = new Hazard(type, nextHazardX, flatWidth, entrance, exit);
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
        float feet = playerY + PLAYER_HEIGHT;
        return velocityY >= 0f
                && Math.abs(feet - surfaceYForPlayer(playerX, feet)) < 1.2f;
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
        float oldSurface = surfaceYForPlayer(oldX, oldFeet);
        boolean wasStanding = isStanding();
        adjustSlopeSpeed(dt, wasStanding, oldX + PLAYER_WIDTH / 2f);
        float pace = getBaseSpeed() / BASE_SPEED;
        playerX += getSpeed() * dt;
        playerY += velocityY * pace * dt + .5f * GRAVITY * pace * pace * dt * dt;
        velocityY += GRAVITY * pace * dt;

        // Vertical depression exits still require a jump from the lower floor.
        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.DIP && hazard.exitDegrees == 0
                    && playerX < hazard.end()
                    && playerX + PLAYER_WIDTH > hazard.end()
                    && playerY + PLAYER_HEIGHT > GROUND_Y + 1f
                    && playerY < GROUND_Y + DIP_DEPTH) {
                playerX = hazard.end() - PLAYER_WIDTH;
                break;
            }
        }

        float feet = playerY + PLAYER_HEIGHT;
        // A vertical ledge entrance remains a damaging face below its top.
        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.WALL && hazard.entranceDegrees == 0
                    && playerX < hazard.x && playerX + PLAYER_WIDTH > hazard.x
                    && feet > GROUND_Y - LEDGE_HEIGHT + 1f && playerY < GROUND_Y) {
                restartAfterFailure();
                return true;
            }
        }

        // A jump can land on the near edge before the runner's center reaches it.
        boolean landed = false;
        if (velocityY >= 0f && oldFeet <= GROUND_Y - LEDGE_HEIGHT + 1f
                && feet >= GROUND_Y - LEDGE_HEIGHT) {
            for (Hazard hazard : hazards) {
                if (hazard.type == HazardType.WALL && hazard.entranceDegrees == 0
                        && playerX < hazard.flatEnd
                        && playerX + PLAYER_WIDTH > hazard.x) {
                    landAt(GROUND_Y - LEDGE_HEIGHT);
                    landed = true;
                    break;
                }
            }
        }

        if (!landed) {
            float surface = surfaceYForPlayer(playerX, oldFeet);
            float dx = Math.max(0f, playerX - oldX);
            boolean continuous = !Float.isNaN(oldSurface) && !Float.isNaN(surface)
                    && Math.abs(surface - oldSurface) <= dx * 1.02f + .8f;
            if (wasStanding && continuous) {
                // Grounded movement follows uphill and downhill planes.
                landAt(surface);
            } else if (velocityY >= 0f && !Float.isNaN(surface)
                    && oldFeet <= surface + 1f
                    && playerY + PLAYER_HEIGHT >= surface) {
                landAt(surface);
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
            if (center < hazard.x) break;
            if (center >= hazard.x && center < hazard.end()) {
                return hazard.surfaceYAt(center);
            }
        }
        return GROUND_Y;
    }

    private float surfaceYForPlayer(float left, float feet) {
        float center = left + PLAYER_WIDTH / 2f;
        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.WALL && hazard.entranceDegrees == 0
                    && left < hazard.x && left + PLAYER_WIDTH > hazard.x
                    && feet <= GROUND_Y - LEDGE_HEIGHT + 1.2f) {
                return GROUND_Y - LEDGE_HEIGHT;
            }
            if (hazard.x > left + PLAYER_WIDTH) break;
        }
        return surfaceYAt(center);
    }

    private void adjustSlopeSpeed(float dt, boolean grounded, float center) {
        int slope = 0;
        if (grounded) {
            for (Hazard hazard : hazards) {
                if (center < hazard.x) break;
                if (center < hazard.end()) {
                    slope = hazard.slopeDegreesAt(center);
                    break;
                }
            }
        }
        if (slope != 0) {
            float change = Math.abs(slope) == 30 ? .12f : .20f;
            terrainSpeedMultiplier = slope > 0 ? 1f - change : 1f + change;
        } else if (terrainSpeedMultiplier < 1f) {
            terrainSpeedMultiplier = Math.min(1f,
                    terrainSpeedMultiplier + SLOPE_RECOVERY_PER_SECOND * dt);
        } else {
            terrainSpeedMultiplier = Math.max(1f,
                    terrainSpeedMultiplier - SLOPE_RECOVERY_PER_SECOND * dt);
        }
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
                        float vy, float remainingSeconds, double savedRunSeconds,
                        float savedTerrainMultiplier, Mode savedMode,
                        Mode savedResumeMode) {
        resetCourse(courseSeed);
        generateAhead(x + 1800f);
        attempts = Math.max(1, savedAttempts);
        playerX = x;
        playerY = y;
        velocityY = vy;
        countdownSeconds = remainingSeconds;
        elapsedRunSeconds = Math.max(0d, savedRunSeconds);
        terrainSpeedMultiplier = Math.max(.8f, Math.min(1.2f, savedTerrainMultiplier));
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
    public float getTerrainSpeedMultiplier() { return terrainSpeedMultiplier; }
    public float getBaseSpeed() {
        return BASE_SPEED + SPEED_STEP
                * (int) Math.floor((elapsedRunSeconds + .000001d) / SPEED_INTERVAL_SECONDS);
    }
    public float getSpeed() {
        return getBaseSpeed() * terrainSpeedMultiplier;
    }
    public float getSpeedMetersPerSecond() { return getSpeed() / WORLD_UNITS_PER_METER; }
    public int getBestDistance() { return bestDistance; }
    public int getDistance() { return Math.max(0, (int) (playerX - START_X) / 10); }
}
