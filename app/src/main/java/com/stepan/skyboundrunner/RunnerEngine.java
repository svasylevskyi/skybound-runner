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
    public static final int MAX_HEALTH = 10;
    public static final float PROJECTILE_RADIUS = 6f;

    private static final float START_X = 140f;
    private static final float ENEMY_START_DISTANCE = 500f * WORLD_UNITS_PER_METER;
    private static final float RECOIL_DISTANCE = 72f;
    private static final float DAMAGE_RECOVERY_SECONDS = .65f;
    private static final float SHOT_INTERVAL_SECONDS = 1.5f;
    private static final float SHOT_SPEED = 380f;
    private static final float SHOT_BOUNCE_VELOCITY = -200f;
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

    public static final class Antagonist {
        public final float x;
        public final float y;
        private float shotTimer = .75f;

        private Antagonist(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class Projectile {
        private float x;
        private float y;
        private final float vx;
        private final float vy;

        private Projectile(float x, float y, float vx, float vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }

        public float getX() { return x; }
        public float getY() { return y; }
    }

    private final List<Hazard> hazards = new ArrayList<>();
    private final List<Antagonist> antagonists = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Integer> completedAttemptDistances = new ArrayList<>();
    private Random random;
    private Random antagonistRandom;
    private float nextHazardX;
    private float nextAntagonistX;
    private float visibleWorldWidth = 960f;
    private long seed;
    private int attempts;
    private float playerX;
    private float farthestX;
    private float playerY;
    private float velocityY;
    private int health = MAX_HEALTH;
    private float damageRecoverySeconds;
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
        antagonistRandom = new Random(seed ^ 0x6A09E667F3BCC909L);
        hazards.clear();
        antagonists.clear();
        projectiles.clear();
        nextHazardX = 840f;
        nextAntagonistX = START_X + ENEMY_START_DISTANCE + 900f
                + antagonistRandom.nextInt(500);
        playerX = START_X;
        farthestX = START_X;
        playerY = GROUND_Y - PLAYER_HEIGHT;
        velocityY = 0f;
        health = MAX_HEALTH;
        damageRecoverySeconds = 0f;
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
        if (getDistance() < 500) return;
        while (nextAntagonistX < worldX) {
            float x = nextAntagonistX;
            float left = surfaceYAt(x + 2f);
            float center = surfaceYAt(x + PLAYER_WIDTH / 2f);
            float right = surfaceYAt(x + PLAYER_WIDTH - 2f);
            if (!Float.isNaN(center) && !Float.isNaN(left) && !Float.isNaN(right)
                    && Math.abs(left - center) <= 20f
                    && Math.abs(right - center) <= 20f) {
                antagonists.add(new Antagonist(x, center - PLAYER_HEIGHT));
            }
            // More than a viewport between candidates guarantees at most one visible enemy.
            nextAntagonistX += Math.max(1400f, visibleWorldWidth + PLAYER_WIDTH + 80f)
                    + antagonistRandom.nextInt(1200);
        }
    }

    public void setVisibleWorldWidth(float width) {
        if (width > 0f && !Float.isInfinite(width)) visibleWorldWidth = width;
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
        damageRecoverySeconds = Math.max(0f, damageRecoverySeconds - dt);
        float oldX = playerX;
        float oldFeet = playerY + PLAYER_HEIGHT;
        float oldSurface = surfaceYForPlayer(oldX, oldFeet);
        boolean wasStanding = isStanding();
        adjustSlopeSpeed(dt, wasStanding, oldX + PLAYER_WIDTH / 2f);
        float pace = getBaseSpeed() / BASE_SPEED;
        playerX += getSpeed() * dt;
        farthestX = Math.max(farthestX, playerX);
        playerY += velocityY * pace * dt + .5f * GRAVITY * pace * pace * dt * dt;
        velocityY += GRAVITY * pace * dt;

        // Both kinds of vertical face hurt, then push the runner back for another jump.
        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.DIP && hazard.exitDegrees == 0
                    && playerX < hazard.end()
                    && playerX + PLAYER_WIDTH > hazard.end()
                    && playerY + PLAYER_HEIGHT > GROUND_Y + 1f
                    && playerY < GROUND_Y + DIP_DEPTH) {
                if (hitSolid(hazard.end(), wasStanding)) return true;
                break;
            }
        }

        float feet = playerY + PLAYER_HEIGHT;
        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.WALL && hazard.entranceDegrees == 0
                    && playerX < hazard.x && playerX + PLAYER_WIDTH > hazard.x
                    && feet > GROUND_Y - LEDGE_HEIGHT + 1f && playerY < GROUND_Y) {
                if (hitSolid(hazard.x, wasStanding)) return true;
                break;
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
        for (Antagonist antagonist : antagonists) {
            if (antagonist.x > playerX + PLAYER_WIDTH) break;
            if (playerX + PLAYER_WIDTH - 4f > antagonist.x + 4f
                    && playerX + 4f < antagonist.x + PLAYER_WIDTH - 4f
                    && playerY + PLAYER_HEIGHT - 5f > antagonist.y + 5f
                    && playerY + 5f < antagonist.y + PLAYER_HEIGHT - 5f) {
                if (hitSolid(antagonist.x, wasStanding)) return true;
                break;
            }
        }
        if (updateProjectiles(dt)) return true;
        // Waiting at a dip's lip is not counted as running time.
        if (playerX > oldX + .01f) elapsedRunSeconds += dt;
        return false;
    }

    private boolean hitSolid(float faceX, boolean wasStanding) {
        playerX = Math.min(playerX, faceX - PLAYER_WIDTH);
        if (damageRecoverySeconds <= 0f) {
            if (takeDamage()) return true;
            playerX = Math.max(START_X, playerX - RECOIL_DISTANCE);
        }
        if (wasStanding) {
            float surface = surfaceYForPlayer(playerX, playerY + PLAYER_HEIGHT);
            if (!Float.isNaN(surface)) landAt(surface);
        }
        return false;
    }

    private boolean takeDamage() {
        if (damageRecoverySeconds > 0f) return false;
        health--;
        if (health <= 0) {
            restartAfterFailure();
            return true;
        }
        damageRecoverySeconds = DAMAGE_RECOVERY_SECONDS;
        return false;
    }

    private boolean updateProjectiles(float dt) {
        for (Antagonist antagonist : antagonists) {
            if (antagonist.x > playerX + PLAYER_WIDTH + 55f
                    && antagonist.x < playerX + visibleWorldWidth * .75f + 80f) {
                antagonist.shotTimer -= dt;
                if (antagonist.shotTimer <= 0f) {
                    float startX = antagonist.x - PROJECTILE_RADIUS - 2f;
                    float startY = antagonist.y + PLAYER_HEIGHT / 2f;
                    float dx = playerX + PLAYER_WIDTH / 2f - startX;
                    float dy = playerY + PLAYER_HEIGHT / 2f - startY;
                    float length = (float) Math.hypot(dx, dy);
                    projectiles.add(new Projectile(startX, startY,
                            SHOT_SPEED * dx / length, SHOT_SPEED * dy / length));
                    antagonist.shotTimer += SHOT_INTERVAL_SECONDS;
                }
            }
        }
        for (int i = projectiles.size() - 1; i >= 0; --i) {
            Projectile shot = projectiles.get(i);
            shot.x += shot.vx * dt;
            shot.y += shot.vy * dt;
            float dx = (shot.x - playerX - PLAYER_WIDTH / 2f)
                    / (PLAYER_WIDTH / 2f + PROJECTILE_RADIUS);
            float dy = (shot.y - playerY - PLAYER_HEIGHT / 2f)
                    / (PLAYER_HEIGHT / 2f + PROJECTILE_RADIUS);
            if (dx * dx + dy * dy <= 1f) {
                projectiles.remove(i);
                if (takeDamage()) return true;
                velocityY = Math.min(velocityY, SHOT_BOUNCE_VELOCITY);
            } else if (shot.x < playerX - 200f || shot.x > playerX + visibleWorldWidth
                    || shot.y < -50f || shot.y > WORLD_HEIGHT + 50f) {
                projectiles.remove(i);
            }
        }
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
        restore(courseSeed, savedAttempts, x, y, vy, remainingSeconds, savedRunSeconds,
                savedTerrainMultiplier, savedMode, savedResumeMode, MAX_HEALTH, 0f,
                x, visibleWorldWidth, null, null);
    }

    public void restore(long courseSeed, int savedAttempts, float x, float y,
                        float vy, float remainingSeconds, double savedRunSeconds,
                        float savedTerrainMultiplier, Mode savedMode,
                        Mode savedResumeMode, int savedHealth, float savedDamageRecovery,
                        float savedFarthestX, float savedVisibleWidth,
                        float[] savedAntagonistTimers, float[] savedProjectiles) {
        setVisibleWorldWidth(savedVisibleWidth);
        resetCourse(courseSeed);
        attempts = Math.max(1, savedAttempts);
        playerX = x;
        farthestX = Math.max(x, savedFarthestX);
        playerY = y;
        velocityY = vy;
        health = Math.max(1, Math.min(MAX_HEALTH, savedHealth));
        damageRecoverySeconds = Math.max(0f, savedDamageRecovery);
        countdownSeconds = remainingSeconds;
        elapsedRunSeconds = Math.max(0d, savedRunSeconds);
        terrainSpeedMultiplier = Math.max(.8f, Math.min(1.2f, savedTerrainMultiplier));
        generateAhead(farthestX + Math.max(1800f, visibleWorldWidth + 550f));
        if (savedAntagonistTimers != null) {
            for (Antagonist antagonist : antagonists) {
                for (int i = 0; i + 1 < savedAntagonistTimers.length; i += 2) {
                    if (Math.abs(antagonist.x - savedAntagonistTimers[i]) < .1f) {
                        antagonist.shotTimer = Math.max(0f, savedAntagonistTimers[i + 1]);
                        break;
                    }
                }
            }
        }
        if (savedProjectiles != null) {
            for (int i = 0; i + 3 < savedProjectiles.length; i += 4) {
                projectiles.add(new Projectile(savedProjectiles[i], savedProjectiles[i + 1],
                        savedProjectiles[i + 2], savedProjectiles[i + 3]));
            }
        }
        if (savedMode == Mode.TITLE) {
            mode = Mode.TITLE;
        } else {
            mode = Mode.PAUSED;
            resumeMode = savedMode == Mode.PAUSED ? savedResumeMode : savedMode;
            if (resumeMode != Mode.COUNTDOWN) resumeMode = Mode.RUNNING;
        }
    }

    public List<Hazard> getHazards() { return hazards; }
    public List<Antagonist> getAntagonists() { return Collections.unmodifiableList(antagonists); }
    public List<Projectile> getProjectiles() { return Collections.unmodifiableList(projectiles); }
    public float[] getAntagonistTimers() {
        float[] state = new float[antagonists.size() * 2];
        for (int i = 0; i < antagonists.size(); i++) {
            state[i * 2] = antagonists.get(i).x;
            state[i * 2 + 1] = antagonists.get(i).shotTimer;
        }
        return state;
    }
    public float[] getProjectileState() {
        float[] state = new float[projectiles.size() * 4];
        for (int i = 0; i < projectiles.size(); i++) {
            Projectile shot = projectiles.get(i);
            state[i * 4] = shot.x;
            state[i * 4 + 1] = shot.y;
            state[i * 4 + 2] = shot.vx;
            state[i * 4 + 3] = shot.vy;
        }
        return state;
    }
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
    public float getFarthestX() { return farthestX; }
    public float getPlayerY() { return playerY; }
    public float getVelocityY() { return velocityY; }
    public int getHealth() { return health; }
    public float getDamageRecoverySeconds() { return damageRecoverySeconds; }
    public float getVisibleWorldWidth() { return visibleWorldWidth; }
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
    public int getDistance() {
        return Math.max(0, (int) ((farthestX - START_X) / WORLD_UNITS_PER_METER));
    }
}
