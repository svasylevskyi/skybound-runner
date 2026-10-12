package com.stepan.skyboundrunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Pure Java game rules, in logical units on a 540-unit-high world. */
public final class RunnerEngine {
    public static final float WORLD_HEIGHT = 540f;
    public static final float GROUND_Y = 400f;
    public static final float MAX_TERRAIN_HEIGHT = 100f;
    public static final float WORLD_UNITS_PER_METER = 10f;
    public static final float PLAYER_WIDTH = 32f;
    public static final float PLAYER_HEIGHT = 46f;
    public static final int MAX_HEALTH = 10;
    public static final int MAX_LIVES = 5;
    public static final float PROJECTILE_RADIUS = 6f;
    public static final float PLATFORM_THICKNESS = 10f;
    public static final float STAR_RADIUS = 11f;
    public static final float WATER_SURFACE_Y = GROUND_Y + 2f;
    // Muzzle position relative to the enemy's 32 x 46 collision bounds.
    public static final float MUZZLE_X = -2f;
    public static final float MUZZLE_Y = PLAYER_HEIGHT / 2f;

    private static final float START_X = 140f;
    private static final float ENEMY_START_DISTANCE = 500f * WORLD_UNITS_PER_METER;
    private static final float MOVING_ENEMY_START_DISTANCE = 1200f * WORLD_UNITS_PER_METER;
    private static final float ENEMY_PATROL_RANGE = 5f * WORLD_UNITS_PER_METER;
    private static final float ENEMY_TERRAIN_CLEARANCE = 10f * WORLD_UNITS_PER_METER;
    private static final float ENEMY_PATROL_SPEED = 42f;
    private static final float BONUS_POPUP_SECONDS = .9f;
    private static final float STAR_BURST_SECONDS = .55f;
    private static final float ENEMY_BURST_SECONDS = .55f;
    private static final float STAR_GROUP_MIN_GAP = 100f * WORLD_UNITS_PER_METER;
    private static final int STAR_GROUP_GAP_VARIATION = 650 * (int) WORLD_UNITS_PER_METER;
    private static final float RESPAWN_DISTANCE = 50f * WORLD_UNITS_PER_METER;
    private static final float SAFE_RESPAWN_LEAD = 110f;
    private static final float DAMAGE_RECOVERY_SECONDS = .65f;
    private static final float DAMAGE_BLINK_SECONDS = .5f;
    private static final float DAMAGE_BLINK_INTERVAL = .06f;
    private static final float SHOT_INTERVAL_SECONDS = 1.5f;
    private static final float SHOT_SPEED = 380f;
    private static final float SHOT_BOUNCE_VELOCITY = -200f;
    public static final float BASE_SPEED = 235f;
    private static final float SPEED_STEP = 7f;
    private static final float SPEED_INTERVAL_SECONDS = 3f;
    private static final float GRAVITY = 1080f;
    private static final float JUMP_VELOCITY = -490f;
    private static final float WATER_SPEED_FRACTION = .25f;
    private static final float WATER_SINK_ACCELERATION_FACTOR = 4f;
    private static final float WATER_TAP_VELOCITY = -70f;
    private static final float WATER_EXIT_JUMP_VELOCITY = -165f;
    public static final float JUMP_HEIGHT = JUMP_VELOCITY * JUMP_VELOCITY / (2f * GRAVITY);
    private static final float ENEMY_JUMP_VELOCITY =
            JUMP_VELOCITY * .4472136f; // sqrt(.2): 20% of the player's jump height.
    private static final float SLOPE_GRAVITY_RESPONSE_SECONDS = .12f;
    private static final float SLOPE_MOMENTUM_SECONDS = .07f;
    private static final float SLOPE_RECOVERY_PER_SECOND = 1.5f;
    private static final HazardType[] HAZARD_TYPES = HazardType.values();

    public enum Mode { TITLE, COUNTDOWN, RUNNING, PAUSED, GAME_OVER }
    public enum HazardType { HOLE, WALL, DIP }
    public enum EnemyType { STATIONARY, MOVING }
    public enum StarColor {
        BLUE(200, 10), GREEN(400, 20), YELLOW(600, 30),
        ORANGE(800, 40), RED(1000, 50);

        public final int unlockMetres;
        public final int points;

        StarColor(int unlockMetres, int points) {
            this.unlockMetres = unlockMetres;
            this.points = points;
        }
    }

    public static final class Hazard {
        public final HazardType type;
        public final float x;
        public final float width;
        public final float height;
        public final int entranceDegrees;
        public final int exitDegrees;
        public final float flatStart;
        public final float flatEnd;

        private Hazard(HazardType type, float x, float flatWidth, float height,
                       int entranceDegrees, int exitDegrees) {
            this.type = type;
            this.x = x;
            this.height = height;
            this.entranceDegrees = entranceDegrees;
            this.exitDegrees = exitDegrees;
            this.flatStart = x + rampRun(height, entranceDegrees);
            this.flatEnd = flatStart + flatWidth;
            this.width = flatEnd + rampRun(height, exitDegrees) - x;
        }

        public float end() { return x + width; }

        public float surfaceYAt(float worldX) {
            if (type == HazardType.HOLE) return Float.NaN;
            float change = type == HazardType.WALL ? -height : height;
            if (worldX < flatStart && entranceDegrees != 90) {
                return GROUND_Y + change * (worldX - x) / (flatStart - x);
            }
            if (worldX < flatEnd) return GROUND_Y + change;
            if (worldX < end() && exitDegrees != 90) {
                return GROUND_Y + change * (end() - worldX) / (end() - flatEnd);
            }
            return GROUND_Y;
        }

        /** Positive means uphill, negative means downhill as the runner moves right. */
        public int slopeDegreesAt(float worldX) {
            if (worldX < x || worldX >= end()) return 0;
            if (worldX < flatStart && entranceDegrees != 90) {
                return type == HazardType.WALL ? entranceDegrees : -entranceDegrees;
            }
            if (worldX >= flatEnd && worldX < end() && exitDegrees != 90) {
                return type == HazardType.WALL ? -exitDegrees : exitDegrees;
            }
            return 0;
        }

        private static float rampRun(float height, int degrees) {
            return degrees == 90 ? 0f
                    : (float) (height / Math.tan(Math.toRadians(degrees)));
        }
    }

    /** A solid overhead surface, including over occasional water holes. */
    public static final class Platform {
        public final float x;
        public final float width;
        public final float top;
        public final float bottom;

        private Platform(float x, float width, float clearance) {
            this.x = x;
            this.width = width;
            bottom = GROUND_Y - clearance;
            top = bottom - PLATFORM_THICKNESS;
        }

        public float end() { return x + width; }
    }

    public static final class Antagonist {
        public final float spawnX;
        public final EnemyType type;
        public final Platform startingPlatform;
        public Platform platform;
        public float x;
        public float y;
        private float shotTimer = .75f;
        private float directionTimer;
        private float jumpTimer;
        private float jumpOffset;
        private float jumpVelocity;
        private int direction;
        private int behaviorStep;

        private Antagonist(float x, float y, EnemyType type, Platform platform) {
            this.spawnX = x;
            this.type = type;
            this.startingPlatform = platform;
            this.platform = platform;
            this.x = x;
            this.y = y;
        }

        public boolean isJumping() { return jumpOffset < -1f; }
    }

    public static final class Projectile {
        private float x;
        private float y;
        private final float vx;
        private final float vy;
        public final EnemyType type;

        private Projectile(float x, float y, float vx, float vy, EnemyType type) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.type = type;
        }

        public float getX() { return x; }
        public float getY() { return y; }
    }

    public static final class BonusPopup {
        public final float x;
        public final float y;
        public final int amount;
        public final EnemyType type;
        public final StarColor starColor;
        private float remainingSeconds;

        private BonusPopup(float x, float y, int amount, EnemyType type, float remainingSeconds) {
            this.x = x;
            this.y = y;
            this.amount = amount;
            this.type = type;
            this.starColor = null;
            this.remainingSeconds = remainingSeconds;
        }

        private BonusPopup(float x, float y, StarColor color, float remainingSeconds) {
            this.x = x;
            this.y = y;
            this.amount = color.points;
            this.type = null;
            this.starColor = color;
            this.remainingSeconds = remainingSeconds;
        }

        public float getProgress() { return 1f - remainingSeconds / BONUS_POPUP_SECONDS; }
    }

    public static final class Star {
        public final float x;
        public final float y;
        public final StarColor color;

        private Star(float x, float y, StarColor color) {
            this.x = x;
            this.y = y;
            this.color = color;
        }

        public float bottom() { return y + STAR_RADIUS; }
    }

    public static final class StarBurst {
        public final float x;
        public final float y;
        public final StarColor color;
        private float remainingSeconds;

        private StarBurst(float x, float y, StarColor color, float remainingSeconds) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.remainingSeconds = remainingSeconds;
        }

        public float getProgress() { return 1f - remainingSeconds / STAR_BURST_SECONDS; }
    }

    public static final class EnemyBurst {
        public final float x;
        public final float y;
        public final EnemyType type;
        private float remainingSeconds;

        private EnemyBurst(float x, float y, EnemyType type, float remainingSeconds) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.remainingSeconds = remainingSeconds;
        }

        public float getProgress() { return 1f - remainingSeconds / ENEMY_BURST_SECONDS; }
    }

    private final List<Hazard> hazards = new ArrayList<>();
    private final List<Platform> platforms = new ArrayList<>();
    private final List<Antagonist> antagonists = new ArrayList<>();
    private final List<Float> defeatedAntagonists = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<BonusPopup> bonusPopups = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final List<Float> collectedStars = new ArrayList<>();
    private final List<StarBurst> starBursts = new ArrayList<>();
    private final List<EnemyBurst> enemyBursts = new ArrayList<>();
    private Random random;
    private Random platformRandom;
    private Random antagonistRandom;
    private Random starRandom;
    private float nextHazardX;
    private float nextAntagonistX;
    private float nextStarGroupX;
    private float lastStarGroupEndX;
    private float visibleWorldWidth = 960f;
    private long seed;
    private int lives = MAX_LIVES;
    private float playerX;
    private float farthestX;
    private int bonusPoints;
    private float playerY;
    private float velocityY;
    private boolean controlledJumpInProgress;
    private boolean firstJumpCompleted;
    private int health = MAX_HEALTH;
    private float damageRecoverySeconds;
    private float countdownSeconds;
    private double elapsedRunSeconds;
    private float terrainSpeedMultiplier = 1f;
    private boolean waitingForJump;
    private boolean inWater;
    private float waterHoleX;
    private float waterEntrySpeed;
    private float waterVerticalSpeed;
    private float waterExitSpeed;
    private int bestDistance;
    private int bestScore;
    private Mode mode = Mode.TITLE;
    private Mode resumeMode = Mode.RUNNING;

    public RunnerEngine() {
        resetCourse(1L);
    }

    public void startNewGame(long courseSeed) {
        lives = MAX_LIVES;
        resetCourse(courseSeed);
        countdownSeconds = 3f;
        mode = Mode.COUNTDOWN;
    }

    private void resetCourse(long courseSeed) {
        seed = courseSeed;
        random = new Random(seed);
        platformRandom = new Random(seed ^ 0xC2B2AE3D27D4EB4FL);
        antagonistRandom = new Random(seed ^ 0x6A09E667F3BCC909L);
        starRandom = new Random(seed ^ 0xBB67AE8584CAA73BL);
        hazards.clear();
        platforms.clear();
        antagonists.clear();
        defeatedAntagonists.clear();
        projectiles.clear();
        bonusPopups.clear();
        stars.clear();
        collectedStars.clear();
        starBursts.clear();
        enemyBursts.clear();
        nextHazardX = 840f;
        nextAntagonistX = START_X + ENEMY_START_DISTANCE + 900f
                + antagonistRandom.nextInt(500);
        nextStarGroupX = START_X + StarColor.BLUE.unlockMetres * WORLD_UNITS_PER_METER
                + starRandom.nextInt(1001);
        lastStarGroupEndX = Float.NaN;
        playerX = START_X;
        farthestX = START_X;
        bonusPoints = 0;
        playerY = GROUND_Y - PLAYER_HEIGHT;
        velocityY = 0f;
        controlledJumpInProgress = false;
        firstJumpCompleted = false;
        health = MAX_HEALTH;
        damageRecoverySeconds = 0f;
        elapsedRunSeconds = 0d;
        terrainSpeedMultiplier = 1f;
        waitingForJump = false;
        inWater = false;
        waterEntrySpeed = 0f;
        waterVerticalSpeed = 0f;
        waterExitSpeed = 0f;
        generateAhead(1800f);
    }

    public void generateAhead(float worldX) {
        // Generate far enough to check the full patrol area and clearance of the last spawn.
        while (nextHazardX < worldX + 600f) {
            HazardType type = HAZARD_TYPES[random.nextInt(HAZARD_TYPES.length)];
            float flatWidth;
            if (type == HazardType.HOLE) {
                flatWidth = 92f + random.nextInt(27);
            } else if (type == HazardType.WALL) {
                flatWidth = 105f + random.nextInt(38);
            } else {
                flatWidth = 155f + random.nextInt(31);
            }
            // A finite height change needs a nonzero angle; flat ground provides 0°.
            int entrance = type == HazardType.HOLE ? 90 : 1 + random.nextInt(90);
            int exit = type == HazardType.HOLE ? 90 : 1 + random.nextInt(90);
            float height = type == HazardType.HOLE ? 0f : 40f + random.nextInt(61);
            Hazard hazard = new Hazard(type, nextHazardX, flatWidth, height, entrance, exit);
            hazards.add(hazard);
            // Clear ground between hazards leaves time for a fresh jump.
            nextHazardX = hazard.end() + 290f + random.nextInt(125);
            // Platforms occupy their own flat-ground intervals. A higher
            // platform is preceded by a lower one within jumping distance.
            float platformRoll = platformRandom.nextFloat();
            if (platformRoll < .28f) {
                float x = type == HazardType.HOLE && platformRandom.nextBoolean()
                        ? hazard.x - 12f : hazard.end() + 160f;
                Platform lower = new Platform(x, 50f + platformRandom.nextInt(401),
                        PLAYER_HEIGHT * (2f + platformRandom.nextFloat() * .12f));
                platforms.add(lower);
                float lastEnd = lower.end();
                if (platformRoll < .10f) {
                    Platform higher = new Platform(lastEnd + 36f,
                            50f + platformRandom.nextInt(401),
                            PLAYER_HEIGHT * (2.7f + platformRandom.nextFloat() * 1.3f));
                    platforms.add(higher);
                    lastEnd = higher.end();
                }
                nextHazardX = Math.max(nextHazardX, lastEnd + 440f);
            }
        }
        generateStarsAhead(worldX);
        if (getCourseDistance() < 500) return;
        while (nextAntagonistX < worldX) {
            float x = nextAntagonistX;
            if (x >= START_X + MOVING_ENEMY_START_DISTANCE
                    && getCourseDistance() < 1200) break;
            // Choose a type without consuming the spawn RNG, so existing enemy locations stay put.
            EnemyType type = x >= START_X + MOVING_ENEMY_START_DISTANCE
                    && new Random(seed ^ 0xA0761D6478BD642FL
                    ^ ((long) Float.floatToIntBits(x) * 0xE7037ED1A0B428DBL)).nextBoolean()
                    ? EnemyType.MOVING : EnemyType.STATIONARY;
            Platform platform = platformForEnemy(x);
            float center = platform == null ? surfaceYAt(x + PLAYER_WIDTH / 2f)
                    : platform.top;
            if (!wasDefeated(x) && !Float.isNaN(center) && canSpawnAt(x, type, platform)) {
                Antagonist antagonist = new Antagonist(x, center - PLAYER_HEIGHT,
                        type, platform);
                if (type == EnemyType.MOVING) {
                    antagonist.directionTimer = .35f + nextEnemyRandom(antagonist) * .9f;
                    antagonist.jumpTimer = .25f + nextEnemyRandom(antagonist) * 1.25f;
                    antagonist.direction = nextEnemyRandom(antagonist) < .5f ? -1 : 1;
                }
                antagonists.add(antagonist);
            }
            // Leave enough room for both enemies to patrol without sharing a screen.
            nextAntagonistX += Math.max(1400f,
                    visibleWorldWidth + PLAYER_WIDTH + 2f * ENEMY_PATROL_RANGE + 20f)
                    + antagonistRandom.nextInt(1200);
        }
    }

    private void generateStarsAhead(float worldX) {
        while (nextStarGroupX < worldX) {
            if (!validStarAnchor(nextStarGroupX)) {
                nextStarGroupX = nearestValidStarAnchor(nextStarGroupX);
                continue;
            }
            int size = 1 + starRandom.nextInt(10);
            float x = nextStarGroupX;
            Platform firstPlatform = platformAt(x);
            boolean onPlatform = firstPlatform != null && !Float.isNaN(surfaceYAt(x))
                    && x - STAR_RADIUS >= firstPlatform.x
                    && x + STAR_RADIUS <= firstPlatform.end()
                    && starRandom.nextBoolean();
            float minBottom = -Float.MAX_VALUE;
            float maxBottom = Float.MAX_VALUE;
            List<Float> positions = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                float ground = surfaceYAt(x);
                Platform overhead = platformAt(x);
                if (onPlatform && (overhead != firstPlatform
                        || x - STAR_RADIUS < overhead.x
                        || x + STAR_RADIUS > overhead.end())) break;
                float support = onPlatform ? overhead.top : ground;
                float low = Float.isNaN(ground) && !onPlatform
                        ? GROUND_Y - JUMP_HEIGHT : support - JUMP_HEIGHT;
                float high = Float.isNaN(ground) && !onPlatform
                        ? low : support - PLAYER_HEIGHT / 2f;
                if (overhead != null && !onPlatform) {
                    low = Math.max(low, overhead.bottom + 2f * STAR_RADIUS + 3f);
                }
                float nextMin = Math.max(minBottom, low);
                float nextMax = Math.min(maxBottom, high);
                if (nextMin > nextMax) break;
                minBottom = nextMin;
                maxBottom = nextMax;
                positions.add(x);
                if (i + 1 < size) x += 36f + starRandom.nextInt(13);
            }
            if (!positions.isEmpty()) {
                float bottom = minBottom + starRandom.nextFloat() * (maxBottom - minBottom);
                for (float position : positions) {
                    StarColor color = randomStarColor(position);
                    if (!wasCollected(position)) {
                        stars.add(new Star(position, bottom - STAR_RADIUS, color));
                    }
                }
                lastStarGroupEndX = positions.get(positions.size() - 1);
            }
            x = positions.isEmpty() ? nextStarGroupX : positions.get(positions.size() - 1);
            nextStarGroupX = x + STAR_GROUP_MIN_GAP
                    + starRandom.nextInt(STAR_GROUP_GAP_VARIATION + 1);
        }
    }

    private boolean validStarAnchor(float x) {
        if (!Float.isNaN(surfaceYAt(x))) return true;
        Platform overhead = platformAt(x);
        return overhead == null || overhead.bottom + 2f * STAR_RADIUS + 3f
                <= GROUND_Y - JUMP_HEIGHT;
    }

    private float nearestValidStarAnchor(float candidate) {
        float min = Float.isNaN(lastStarGroupEndX) ? candidate
                : lastStarGroupEndX + STAR_GROUP_MIN_GAP;
        float max = Float.isNaN(lastStarGroupEndX) ? candidate + 7500f
                : lastStarGroupEndX + 7500f;
        for (float x = candidate - 4f; x >= min; x -= 4f) {
            if (validStarAnchor(x)) return x;
        }
        for (float x = candidate + 4f; x <= max; x += 4f) {
            if (validStarAnchor(x)) return x;
        }
        // Every generated hazard is shorter than the 6500-unit search window.
        return candidate + 4f;
    }

    private StarColor randomStarColor(float x) {
        StarColor[] colors = StarColor.values();
        int available = 0;
        for (StarColor color : colors) {
            if (x >= START_X + color.unlockMetres * WORLD_UNITS_PER_METER) available++;
        }
        return colors[starRandom.nextInt(Math.max(1, available))];
    }

    private Platform platformAt(float x) {
        for (Platform platform : platforms) {
            if (platform.x > x + STAR_RADIUS) break;
            if (x + STAR_RADIUS > platform.x && x - STAR_RADIUS < platform.end()) {
                return platform;
            }
        }
        return null;
    }

    private boolean wasCollected(float starX) {
        for (float x : collectedStars) {
            if (Math.abs(x - starX) < .1f) return true;
        }
        return false;
    }

    private Platform platformForEnemy(float x) {
        for (Platform platform : platforms) {
            if (platform.x > x) break;
            if (x >= platform.x && x + PLAYER_WIDTH <= platform.end()) return platform;
        }
        return null;
    }

    private boolean canSpawnAt(float x, EnemyType type, Platform platform) {
        if (platform != null) return true;
        float patrol = type == EnemyType.MOVING ? ENEMY_PATROL_RANGE : 0f;
        float left = x - patrol;
        float right = x + PLAYER_WIDTH + patrol;
        if (!canStandAt(left) || !canStandAt(right - PLAYER_WIDTH)) return false;
        for (Hazard hazard : hazards) {
            if (hazard.x > right + ENEMY_TERRAIN_CLEARANCE) break;
            if (hazard.end() < left - ENEMY_TERRAIN_CLEARANCE) continue;
            // Keep the entire oval and its possible patrol ten metres clear of edges,
            // vertical faces, holes, and both kinds of slope above 45 degrees.
            if (nearTerrain(left, right, hazard.x, hazard.x)
                    || nearTerrain(left, right, hazard.end(), hazard.end())) return false;
            if (hazard.type == HazardType.HOLE) {
                if (nearTerrain(left, right, hazard.x, hazard.end())) return false;
            } else if ((hazard.entranceDegrees > 45 && nearTerrain(left, right,
                    hazard.x, hazard.flatStart))
                    || (hazard.exitDegrees > 45 && nearTerrain(left, right,
                    hazard.flatEnd, hazard.end()))) return false;
        }
        return true;
    }

    private static boolean nearTerrain(float left, float right, float start, float end) {
        return left < end + ENEMY_TERRAIN_CLEARANCE
                && right > start - ENEMY_TERRAIN_CLEARANCE;
    }

    public void setVisibleWorldWidth(float width) {
        if (width > 0f && !Float.isInfinite(width)) visibleWorldWidth = width;
    }

    public void jump() {
        if (mode == Mode.RUNNING && inWater) {
            // Short upward strokes counter the speed-dependent downward drift.
            waterVerticalSpeed = WATER_TAP_VELOCITY;
            playerY = Math.max(WATER_SURFACE_Y - PLAYER_HEIGHT, playerY - 2f);
            return;
        }
        if (mode == Mode.RUNNING && isStanding()) {
            velocityY = JUMP_VELOCITY;
            controlledJumpInProgress = true;
            if (waitingForJump || terrainSpeedMultiplier < 0f) {
                waitingForJump = false;
                terrainSpeedMultiplier = cruiseSpeed() / getBaseSpeed();
            }
        }
    }

    private boolean wasDefeated(float enemyX) {
        for (float x : defeatedAntagonists) {
            if (Math.abs(x - enemyX) < .1f) return true;
        }
        return false;
    }

    private float nextEnemyRandom(Antagonist antagonist) {
        long behaviorSeed = seed ^ ((long) Float.floatToIntBits(antagonist.spawnX)
                * 0x9E3779B97F4A7C15L)
                ^ ((long) antagonist.behaviorStep++ * 0xD1B54A32D192ED03L);
        return new Random(behaviorSeed).nextFloat();
    }

    private boolean isStanding() {
        if (inWater) return false;
        float feet = playerY + PLAYER_HEIGHT;
        return velocityY >= 0f
                && Math.abs(feet - surfaceYForPlayer(playerX, feet)) < 1.2f;
    }

    public void update(float seconds) {
        if (mode == Mode.RUNNING || mode == Mode.COUNTDOWN || mode == Mode.GAME_OVER) {
            updateBonusPopups(Math.max(0f, seconds));
            updateStarBursts(Math.max(0f, seconds));
            updateEnemyBursts(Math.max(0f, seconds));
        }
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

    /** Returns true when a collision ended this frame with a life loss. */
    private boolean advance(float dt) {
        generateAhead(playerX + 1600f);
        damageRecoverySeconds = Math.max(0f, damageRecoverySeconds - dt);
        if (inWater) return advanceInWater(dt);
        float oldX = playerX;
        float oldFeet = playerY + PLAYER_HEIGHT;
        float oldSurface = surfaceYForPlayer(oldX, oldFeet);
        boolean wasStanding = isStanding();
        adjustSlopeSpeed(dt, wasStanding, oldX + PLAYER_WIDTH / 2f);
        float pace = getBaseSpeed() / BASE_SPEED;
        playerX += getSpeed() * dt;
        playerY += velocityY * pace * dt + .5f * GRAVITY * pace * pace * dt * dt;
        velocityY += GRAVITY * pace * dt;
        stopAtVerticalFace(oldX, wasStanding);
        if (!waitingForJump && wasStanding) stopAtSlopeFoot(oldX);
        bumpPlatformUnderside(oldFeet);

        // A jump can land on the near edge before the runner's center reaches it.
        boolean landed = false;
        float feet = playerY + PLAYER_HEIGHT;
        if (velocityY >= 0f) {
            for (Platform platform : platforms) {
                if (platform.x > playerX + PLAYER_WIDTH) break;
                if (playerX < platform.end() && playerX + PLAYER_WIDTH > platform.x
                        && oldFeet <= platform.top + 1f && feet >= platform.top) {
                    landAt(platform.top);
                    landed = true;
                    break;
                }
            }
        }
        if (!landed && velocityY >= 0f) {
            for (Hazard hazard : hazards) {
                float top = GROUND_Y - hazard.height;
                if (hazard.type == HazardType.WALL
                        && hazard.flatStart - hazard.x < 10f
                        && oldFeet <= top + 1f && feet >= top
                        && playerX < hazard.flatEnd
                        && playerX + PLAYER_WIDTH > hazard.x) {
                    landAt(top);
                    landed = true;
                    break;
                }
            }
        }

        if (!landed) {
            float surface = surfaceYForPlayer(playerX, oldFeet);
            float dx = Math.abs(playerX - oldX);
            int slope = Math.max(Math.abs(slopeDegreesAt(oldX + PLAYER_WIDTH / 2f)),
                    Math.abs(slopeDegreesAt(playerX + PLAYER_WIDTH / 2f)));
            float risePerUnit = slope == 90 ? 1.02f
                    : Math.max(1.02f, (float) Math.tan(Math.toRadians(slope)));
            boolean continuous = !Float.isNaN(oldSurface) && !Float.isNaN(surface)
                    && Math.abs(surface - oldSurface) <= dx * risePerUnit + .8f;
            if (wasStanding && continuous) {
                // Grounded movement follows uphill and downhill planes.
                landAt(surface);
            } else {
                boolean landedOnRamp = velocityY >= 0f && !Float.isNaN(oldSurface)
                        && oldFeet <= oldSurface + 1f
                        && landOnCrossedRisingSlope(oldX, oldFeet, feet);
                if (!landedOnRamp && velocityY >= 0f && !Float.isNaN(surface)
                        && playerY + PLAYER_HEIGHT >= surface
                        && (oldFeet <= surface + 1f
                        // The rising surface can cross the feet during this frame.
                        || (continuous && oldFeet <= oldSurface + 1f))) {
                    landAt(surface);
                }
            }
        }
        stopAtPlatformEdge(oldX);
        farthestX = Math.max(farthestX, playerX);
        Hazard waterHole = touchingWaterHole();
        if (waterHole != null) beginWater(waterHole);
        if (playerY + PLAYER_HEIGHT > GROUND_Y + MAX_TERRAIN_HEIGHT + 17f) {
            loseLife();
            return true;
        }
        updateMovingEnemies(dt);
        for (int i = 0; i < antagonists.size(); i++) {
            Antagonist antagonist = antagonists.get(i);
            if (antagonist.x > playerX + PLAYER_WIDTH) break;
            if (playerX + PLAYER_WIDTH - 4f > antagonist.x + 4f
                    && playerX + 4f < antagonist.x + PLAYER_WIDTH - 4f
                    && playerY + PLAYER_HEIGHT - 5f > antagonist.y + 5f
                    && playerY + 5f < antagonist.y + PLAYER_HEIGHT - 5f) {
                float topQuarterEnd = antagonist.y + PLAYER_HEIGHT / 4f;
                boolean stomped = !wasStanding && velocityY > 0f
                        && oldFeet <= topQuarterEnd
                        && playerY + PLAYER_HEIGHT >= antagonist.y + 5f;
                defeatEnemy(i, stomped);
                if (!stomped && takeDamage(2, true)) return true;
                break;
            }
        }
        if (updateProjectiles(dt)) return true;
        collectStars();
        if (playerX > oldX + .01f) elapsedRunSeconds += dt;
        return false;
    }

    private void defeatEnemy(int index, boolean withoutDamage) {
        Antagonist enemy = antagonists.remove(index);
        defeatedAntagonists.add(enemy.spawnX);
        int bonus = enemy.type == EnemyType.MOVING
                ? (withoutDamage ? 400 : 200) : (withoutDamage ? 300 : 100);
        bonusPoints += bonus;
        bonusPopups.add(new BonusPopup(enemy.x + PLAYER_WIDTH / 2f,
                enemy.y, bonus, enemy.type, BONUS_POPUP_SECONDS));
        enemyBursts.add(new EnemyBurst(enemy.x + PLAYER_WIDTH / 2f,
                enemy.y + PLAYER_HEIGHT / 2f, enemy.type, ENEMY_BURST_SECONDS));
    }

    private Hazard touchingWaterHole() {
        if (playerY + PLAYER_HEIGHT < WATER_SURFACE_Y) return null;
        float center = playerX + PLAYER_WIDTH / 2f;
        for (Hazard hazard : hazards) {
            if (hazard.x > center) break;
            // The middle of the hull must be above the hole; waiting for its
            // rear to clear the bank lets slow cars fall far below the water.
            if (hazard.type == HazardType.HOLE && center >= hazard.x + 4f
                    && center < hazard.end()) return hazard;
        }
        return null;
    }

    private void beginWater(Hazard hole) {
        waterEntrySpeed = Math.max(0f, getSpeed());
        inWater = true;
        waterHoleX = hole.x;
        waterVerticalSpeed = waterEntrySpeed * WATER_SPEED_FRACTION;
        // Surface contact starts the swim; the car has not drowned yet.
        playerY = WATER_SURFACE_Y - PLAYER_HEIGHT;
        velocityY = 0f;
    }

    private boolean advanceInWater(float dt) {
        float oldX = playerX;
        float drift = waterEntrySpeed * WATER_SPEED_FRACTION;
        playerX += drift * dt;
        waterVerticalSpeed = Math.min(drift,
                waterVerticalSpeed + drift * WATER_SINK_ACCELERATION_FACTOR * dt);
        playerY = Math.max(WATER_SURFACE_Y - PLAYER_HEIGHT,
                playerY + waterVerticalSpeed * dt);
        farthestX = Math.max(farthestX, playerX);

        // The top of the hull disappearing beneath the surface is the fail point.
        if (playerY >= WATER_SURFACE_Y) {
            loseLife();
            return true;
        }
        Hazard hole = null;
        for (Hazard hazard : hazards) {
            if (hazard.x > waterHoleX + .1f) break;
            if (hazard.type == HazardType.HOLE && Math.abs(hazard.x - waterHoleX) < .1f) {
                hole = hazard;
                break;
            }
        }
        if (hole != null && playerX + PLAYER_WIDTH / 2f >= hole.end()) {
            inWater = false;
            waterExitSpeed = drift;
            terrainSpeedMultiplier = waterExitSpeed / getBaseSpeed();
            playerY = GROUND_Y - PLAYER_HEIGHT;
            velocityY = WATER_EXIT_JUMP_VELOCITY;
            waterVerticalSpeed = 0f;
        }
        updateMovingEnemies(dt);
        if (updateProjectiles(dt)) return true;
        collectStars();
        if (playerX > oldX + .01f) elapsedRunSeconds += dt;
        return false;
    }

    private void collectStars() {
        for (int i = stars.size() - 1; i >= 0; i--) {
            Star star = stars.get(i);
            if (star.x < playerX - STAR_RADIUS) break;
            if (star.x > playerX + PLAYER_WIDTH + STAR_RADIUS) continue;
            float nearX = Math.max(playerX, Math.min(star.x, playerX + PLAYER_WIDTH));
            float nearY = Math.max(playerY, Math.min(star.y, playerY + PLAYER_HEIGHT));
            float dx = star.x - nearX;
            float dy = star.y - nearY;
            if (dx * dx + dy * dy > STAR_RADIUS * STAR_RADIUS) continue;
            stars.remove(i);
            collectedStars.add(star.x);
            bonusPoints += star.color.points;
            bonusPopups.add(new BonusPopup(star.x, star.y, star.color,
                    BONUS_POPUP_SECONDS));
            starBursts.add(new StarBurst(star.x, star.y, star.color, STAR_BURST_SECONDS));
        }
    }

    private void bumpPlatformUnderside(float oldFeet) {
        float oldHead = oldFeet - PLAYER_HEIGHT;
        if (playerY >= oldHead) return;
        for (Platform platform : platforms) {
            if (platform.x >= playerX + PLAYER_WIDTH) break;
            if (playerX < platform.end() && oldHead >= platform.bottom - .5f
                    && playerY <= platform.bottom && oldFeet > platform.top + 1f) {
                playerY = platform.bottom;
                velocityY = 0f;
                for (int i = antagonists.size() - 1; i >= 0; i--) {
                    Antagonist enemy = antagonists.get(i);
                    float overlap = Math.min(playerX + PLAYER_WIDTH, enemy.x + PLAYER_WIDTH)
                            - Math.max(playerX, enemy.x);
                    if (enemy.platform == platform
                            && enemy.y + PLAYER_HEIGHT >= platform.top - .5f
                            && overlap >= PLAYER_WIDTH / 2f) defeatEnemy(i, true);
                }
                return;
            }
        }
    }

    private void stopAtPlatformEdge(float oldX) {
        if (playerX <= oldX) return;
        for (Platform platform : platforms) {
            if (platform.x > playerX + PLAYER_WIDTH) break;
            if (oldX + PLAYER_WIDTH <= platform.x
                    && playerX + PLAYER_WIDTH > platform.x
                    && playerY < platform.bottom
                    && playerY + PLAYER_HEIGHT > platform.top) {
                // A late approach touches the edge until the car rises above
                // the top or falls low enough to pass beneath it.
                playerX = platform.x - PLAYER_WIDTH;
                return;
            }
        }
    }

    private boolean landOnCrossedRisingSlope(float oldX, float oldFeet, float newFeet) {
        float fromCenter = oldX + PLAYER_WIDTH / 2f;
        float toCenter = playerX + PLAYER_WIDTH / 2f;
        if (toCenter <= fromCenter) return false;
        for (Hazard hazard : hazards) {
            if (hazard.x > toCenter) break;
            if (hazard.type == HazardType.HOLE || hazard.end() <= fromCenter) continue;
            float rampStart = hazard.type == HazardType.WALL
                    ? hazard.x : hazard.flatEnd;
            float rampEnd = hazard.type == HazardType.WALL
                    ? hazard.flatStart : hazard.end();
            if (rampEnd <= rampStart || rampEnd <= fromCenter
                    || rampStart >= toCenter) continue;

            float entry = Math.max(fromCenter, rampStart);
            float exit = Math.min(toCenter, rampEnd);
            float entryFeet = oldFeet + (newFeet - oldFeet)
                    * (entry - fromCenter) / (toCenter - fromCenter);
            float exitFeet = oldFeet + (newFeet - oldFeet)
                    * (exit - fromCenter) / (toCenter - fromCenter);
            float entryGap = entryFeet - hazard.surfaceYAt(entry);
            float exitGap = exitFeet - hazard.surfaceYAt(exit);
            if (exitGap < 0f) continue;

            // If the feet reached ground just before the ramp, stop at its foot.
            // Otherwise solve the intersection of the foot path and rising plane.
            float fraction = entryGap >= 0f ? 0f
                    : -entryGap / (exitGap - entryGap);
            float contactCenter = entry + (exit - entry) * fraction;
            playerX = contactCenter - PLAYER_WIDTH / 2f;
            landAt(hazard.surfaceYAt(contactCenter));
            return true;
        }
        return false;
    }

    private void stopAtVerticalFace(float oldX, boolean wasStanding) {
        if (playerX <= oldX) return;
        for (Hazard hazard : hazards) {
            float face;
            float top;
            // Very narrow ramps act as faces at the runner's scale.
            if (hazard.type == HazardType.WALL
                    && hazard.flatStart - hazard.x < 10f) {
                face = hazard.x;
                top = GROUND_Y - hazard.height;
            } else if (hazard.type == HazardType.DIP
                    && hazard.end() - hazard.flatEnd < 10f) {
                face = hazard.flatEnd;
                top = GROUND_Y;
            } else continue;
            if (oldX < face && playerX + PLAYER_WIDTH > face
                    && playerY + PLAYER_HEIGHT > top + 1f
                    && playerY < top + hazard.height) {
                playerX = face - PLAYER_WIDTH;
                if (wasStanding) stopUntilJump(hazard.type == HazardType.WALL
                        ? GROUND_Y : GROUND_Y + hazard.height);
                return;
            }
        }
    }

    private void stopAtSlopeFoot(float oldX) {
        float oldCenter = oldX + PLAYER_WIDTH / 2f;
        float newCenter = playerX + PLAYER_WIDTH / 2f;
        for (Hazard hazard : hazards) {
            float start;
            float end;
            float ground;
            if (hazard.type == HazardType.WALL && hazard.entranceDegrees < 90) {
                start = hazard.x;
                end = hazard.flatStart;
                ground = GROUND_Y;
            } else if (hazard.type == HazardType.DIP && hazard.exitDegrees < 90) {
                start = hazard.flatEnd;
                end = hazard.end();
                ground = GROUND_Y + hazard.height;
            } else continue;
            boolean skippedRamp = playerX > oldX && oldCenter < end && newCenter >= end
                    && (oldCenter <= start || end - start < PLAYER_WIDTH / 2f);
            boolean slidOff = playerX < oldX && oldCenter >= start
                    && oldCenter < end && newCenter <= start;
            if (skippedRamp || slidOff) {
                playerX = start - PLAYER_WIDTH / 2f;
                stopUntilJump(ground);
                return;
            }
        }
    }

    private void stopUntilJump(float surface) {
        landAt(surface);
        waitingForJump = true;
        terrainSpeedMultiplier = 0f;
    }

    private void updateMovingEnemies(float dt) {
        for (Antagonist antagonist : antagonists) {
            if (antagonist.type != EnemyType.MOVING
                    || antagonist.spawnX < playerX - 300f
                    || antagonist.spawnX > playerX + visibleWorldWidth + 300f) continue;

            antagonist.directionTimer -= dt;
            if (antagonist.directionTimer <= 0f) {
                antagonist.direction = nextEnemyRandom(antagonist) < .5f ? -1 : 1;
                antagonist.directionTimer = .35f + nextEnemyRandom(antagonist) * .9f;
            }
            float nextX = antagonist.x + antagonist.direction * ENEMY_PATROL_SPEED * dt;
            boolean withinPatrol = Math.abs(nextX - antagonist.spawnX) <= ENEMY_PATROL_RANGE;
            boolean supported = canStandAt(nextX, antagonist.platform);
            if (withinPatrol && antagonist.platform != null && !supported
                    && antagonist.jumpVelocity != 0f
                    && safeLandingAfterEdgeJump(antagonist, nextX)) {
                antagonist.platform = null;
                supported = true;
            }
            if (withinPatrol && supported) antagonist.x = nextX;
            else {
                antagonist.direction = -antagonist.direction;
            }

            antagonist.jumpTimer -= dt;
            if (antagonist.jumpTimer <= 0f && antagonist.jumpVelocity == 0f) {
                antagonist.jumpVelocity = ENEMY_JUMP_VELOCITY;
                antagonist.jumpTimer = 1f + nextEnemyRandom(antagonist) * 2f;
            }
            if (antagonist.jumpVelocity != 0f) {
                antagonist.y += antagonist.jumpVelocity * dt + .5f * GRAVITY * dt * dt;
                antagonist.jumpVelocity += GRAVITY * dt;
                Platform landingPlatform = antagonist.platform;
                if (landingPlatform == null && antagonist.startingPlatform != null
                        && antagonist.x + PLAYER_WIDTH / 2f
                        < antagonist.startingPlatform.end()
                        && antagonist.x + PLAYER_WIDTH / 2f
                        >= antagonist.startingPlatform.x
                        && antagonist.y + PLAYER_HEIGHT
                        <= antagonist.startingPlatform.top + 1f) {
                    landingPlatform = antagonist.startingPlatform;
                }
                float support = supportYAt(antagonist.x, landingPlatform);
                if (!Float.isNaN(support) && antagonist.jumpVelocity > 0f
                        && antagonist.y + PLAYER_HEIGHT >= support) {
                    antagonist.y = support - PLAYER_HEIGHT;
                    antagonist.platform = landingPlatform;
                    antagonist.jumpOffset = 0f;
                    antagonist.jumpVelocity = 0f;
                } else {
                    antagonist.jumpOffset = antagonist.y + PLAYER_HEIGHT
                            - (Float.isNaN(support) ? GROUND_Y : support);
                }
            } else {
                float support = supportYAt(antagonist.x, antagonist.platform);
                if (!Float.isNaN(support)) antagonist.y = support - PLAYER_HEIGHT;
            }
        }
    }

    private boolean safeLandingAfterEdgeJump(Antagonist enemy, float nextX) {
        // Start beyond the edge, then predict the grounded footprint. Refuse a
        // leap over water even if the sprite is currently high above its surface.
        if (!canStandAt(nextX)) return false;
        float drop = GROUND_Y - (enemy.y + PLAYER_HEIGHT);
        float time = (-enemy.jumpVelocity + (float) Math.sqrt(
                enemy.jumpVelocity * enemy.jumpVelocity + 2f * GRAVITY * Math.max(0f, drop)))
                / GRAVITY;
        float travel = Math.min(ENEMY_PATROL_SPEED * time,
                ENEMY_PATROL_RANGE - Math.abs(nextX - enemy.spawnX));
        return canStandAt(nextX + enemy.direction * Math.max(0f, travel));
    }

    private float supportYAt(float x, Platform platform) {
        return platform == null ? surfaceYAt(x + PLAYER_WIDTH / 2f) : platform.top;
    }

    private boolean canStandAt(float x, Platform platform) {
        return platform == null ? canStandAt(x)
                : x >= platform.x && x + PLAYER_WIDTH <= platform.end();
    }

    private boolean canStandAt(float x) {
        float left = surfaceYAt(x + 2f);
        float center = surfaceYAt(x + PLAYER_WIDTH / 2f);
        float right = surfaceYAt(x + PLAYER_WIDTH - 2f);
        return !Float.isNaN(left) && !Float.isNaN(center) && !Float.isNaN(right)
                && Math.abs(left - center) <= 20f && Math.abs(right - center) <= 20f;
    }

    private void updateBonusPopups(float dt) {
        for (int i = bonusPopups.size() - 1; i >= 0; i--) {
            BonusPopup popup = bonusPopups.get(i);
            popup.remainingSeconds -= dt;
            if (popup.remainingSeconds <= 0f) bonusPopups.remove(i);
        }
    }

    private void updateStarBursts(float dt) {
        for (int i = starBursts.size() - 1; i >= 0; i--) {
            StarBurst burst = starBursts.get(i);
            burst.remainingSeconds -= dt;
            if (burst.remainingSeconds <= 0f) starBursts.remove(i);
        }
    }

    private void updateEnemyBursts(float dt) {
        for (int i = enemyBursts.size() - 1; i >= 0; i--) {
            EnemyBurst burst = enemyBursts.get(i);
            burst.remainingSeconds -= dt;
            if (burst.remainingSeconds <= 0f) enemyBursts.remove(i);
        }
    }

    private boolean takeDamage(int points, boolean ignoreRecovery) {
        if (!ignoreRecovery && damageRecoverySeconds > 0f) return false;
        health -= points;
        if (health <= 0) {
            loseLife();
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
                    float startX = antagonist.x + MUZZLE_X - PROJECTILE_RADIUS;
                    float startY = antagonist.y + MUZZLE_Y;
                    projectiles.add(new Projectile(startX, startY,
                            -SHOT_SPEED, 0f, antagonist.type));
                    antagonist.shotTimer += SHOT_INTERVAL_SECONDS;
                }
            }
        }
        for (int i = projectiles.size() - 1; i >= 0; --i) {
            Projectile shot = projectiles.get(i);
            shot.x += shot.vx * dt;
            shot.y += shot.vy * dt;
            if (touchesTerrain(shot)) {
                projectiles.remove(i);
                continue;
            }
            float dx = (shot.x - playerX - PLAYER_WIDTH / 2f)
                    / (PLAYER_WIDTH / 2f + PROJECTILE_RADIUS);
            float dy = (shot.y - playerY - PLAYER_HEIGHT / 2f)
                    / (PLAYER_HEIGHT / 2f + PROJECTILE_RADIUS);
            if (dx * dx + dy * dy <= 1f) {
                projectiles.remove(i);
                if (takeDamage(1, false)) return true;
                velocityY = Math.min(velocityY, SHOT_BOUNCE_VELOCITY);
            } else if (shot.x < playerX - 200f || shot.x > playerX + visibleWorldWidth
                    || shot.y < -50f || shot.y > WORLD_HEIGHT + 50f) {
                projectiles.remove(i);
            }
        }
        return false;
    }

    private boolean touchesTerrain(Projectile shot) {
        for (Platform platform : platforms) {
            if (platform.x > shot.x + PROJECTILE_RADIUS) break;
            float nearestX = Math.max(platform.x, Math.min(shot.x, platform.end()));
            float nearestY = Math.max(platform.top, Math.min(shot.y, platform.bottom));
            float dx = shot.x - nearestX;
            float dy = shot.y - nearestY;
            if (dx * dx + dy * dy <= PROJECTILE_RADIUS * PROJECTILE_RADIUS) return true;
        }
        if (shot.y + PROJECTILE_RADIUS < GROUND_Y - MAX_TERRAIN_HEIGHT) return false;
        // Check the circular footprint against the same surfaces used for movement.
        // The outer samples also catch a vertical face before the dot's center crosses it.
        for (int sample = -2; sample <= 2; sample++) {
            float offset = sample * PROJECTILE_RADIUS / 2f;
            float surface = surfaceYAt(shot.x + offset);
            float bottom = shot.y + (float) Math.sqrt(
                    PROJECTILE_RADIUS * PROJECTILE_RADIUS - offset * offset);
            if (!Float.isNaN(surface) && bottom >= surface) return true;
        }
        return false;
    }

    private void landAt(float top) {
        playerY = top - PLAYER_HEIGHT;
        velocityY = 0f;
        if (controlledJumpInProgress) firstJumpCompleted = true;
        controlledJumpInProgress = false;
    }

    // The scenery uses the same terrain surface as movement to plant its roots.
    float surfaceYAt(float center) {
        for (Hazard hazard : hazards) {
            if (center < hazard.x) break;
            if (center >= hazard.x && center < hazard.end()) {
                return hazard.surfaceYAt(center);
            }
        }
        return GROUND_Y;
    }

    private float surfaceYForPlayer(float left, float feet) {
        for (Platform platform : platforms) {
            if (platform.x >= left + PLAYER_WIDTH) break;
            if (left < platform.end() && feet <= platform.top + 1.2f
                    && feet >= platform.top - 1.2f) return platform.top;
        }
        float center = left + PLAYER_WIDTH / 2f;
        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.WALL && hazard.flatStart - hazard.x < 10f
                    && left < hazard.x && left + PLAYER_WIDTH > hazard.x
                    && feet <= GROUND_Y - hazard.height + 1.2f) {
                return GROUND_Y - hazard.height;
            }
            if (hazard.x > left + PLAYER_WIDTH) break;
        }
        return surfaceYAt(center);
    }

    private int slopeDegreesAt(float center) {
        for (Hazard hazard : hazards) {
            if (center < hazard.x) break;
            if (center < hazard.end()) return hazard.slopeDegreesAt(center);
        }
        return 0;
    }

    private void adjustSlopeSpeed(float dt, boolean grounded, float center) {
        if (waitingForJump) {
            terrainSpeedMultiplier = 0f;
            return;
        }
        float base = cruiseSpeed() / getBaseSpeed();
        int slope = grounded ? slopeDegreesAt(center) : 0;
        if (slope != 0) {
            double angle = Math.toRadians(Math.abs(slope));
            float gravityEffect = GRAVITY * SLOPE_GRAVITY_RESPONSE_SECONDS
                    / getBaseSpeed() * (float) Math.sin(angle);
            float target = slope > 0
                    ? base * (float) Math.cos(angle) - gravityEffect
                    : base + gravityEffect;
            float response = 1f - (float) Math.exp(-dt / SLOPE_MOMENTUM_SECONDS);
            terrainSpeedMultiplier += (target - terrainSpeedMultiplier) * response;
        } else if (terrainSpeedMultiplier < base) {
            terrainSpeedMultiplier = Math.min(base,
                    terrainSpeedMultiplier + SLOPE_RECOVERY_PER_SECOND * dt);
        } else {
            terrainSpeedMultiplier = Math.max(base,
                    terrainSpeedMultiplier - SLOPE_RECOVERY_PER_SECOND * dt);
        }
    }

    private float cruiseSpeed() {
        return waterExitSpeed > 0f ? waterExitSpeed : getBaseSpeed();
    }

    private void loseLife() {
        lives--;
        inWater = false;
        waterEntrySpeed = 0f;
        waterVerticalSpeed = 0f;
        waterExitSpeed = 0f;
        controlledJumpInProgress = false;
        projectiles.clear();
        if (lives == 0) {
            bestDistance = Math.max(bestDistance, getDistance());
            bestScore = Math.max(bestScore, getScore());
            mode = Mode.GAME_OVER;
            return;
        }
        playerX = safeRespawnX(Math.max(START_X, playerX - RESPAWN_DISTANCE));
        landAt(surfaceYAt(playerX + PLAYER_WIDTH / 2f));
        health = MAX_HEALTH;
        damageRecoverySeconds = 0f;
        terrainSpeedMultiplier = 1f;
        waitingForJump = false;
        countdownSeconds = 3f;
        mode = Mode.COUNTDOWN;
    }

    private float safeRespawnX(float target) {
        for (float x = target; x > START_X; x = Math.max(START_X, x - 2f)) {
            if (isSafeRespawn(x)) return x;
        }
        return START_X;
    }

    private boolean isSafeRespawn(float x) {
        float center = x + PLAYER_WIDTH / 2f;
        if (Float.isNaN(surfaceYAt(x + 2f)) || Float.isNaN(surfaceYAt(center))
                || Float.isNaN(surfaceYAt(x + PLAYER_WIDTH - 2f))) return false;
        float ahead = x + PLAYER_WIDTH + SAFE_RESPAWN_LEAD;
        for (Hazard hazard : hazards) {
            if (hazard.x > ahead) break;
            if (hazard.type == HazardType.HOLE && hazard.end() > x
                    && hazard.x < ahead) return false;
            if (hazard.type == HazardType.WALL && hazard.entranceDegrees == 90
                    && hazard.x > x && hazard.x < ahead) return false;
            if (hazard.type == HazardType.DIP && hazard.exitDegrees == 90
                    && hazard.end() > x && hazard.end() < ahead
                    && center >= hazard.x) return false;
        }
        for (Antagonist antagonist : antagonists) {
            float patrol = antagonist.type == EnemyType.MOVING ? ENEMY_PATROL_RANGE : 0f;
            if (antagonist.spawnX - patrol > ahead) break;
            if (antagonist.spawnX + patrol + PLAYER_WIDTH > x) return false;
        }
        return true;
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

    public void restore(long courseSeed, int savedLives, float x, float y,
                        float vy, float remainingSeconds, double savedRunSeconds,
                        float savedTerrainMultiplier, Mode savedMode,
                        Mode savedResumeMode) {
        restore(courseSeed, savedLives, x, y, vy, remainingSeconds, savedRunSeconds,
                savedTerrainMultiplier, savedMode, savedResumeMode, MAX_HEALTH, 0f,
                x, visibleWorldWidth, null, null);
    }

    public void restore(long courseSeed, int savedLives, float x, float y,
                        float vy, float remainingSeconds, double savedRunSeconds,
                        float savedTerrainMultiplier, Mode savedMode,
                        Mode savedResumeMode, int savedHealth, float savedDamageRecovery,
                        float savedFarthestX, float savedVisibleWidth,
                        float[] savedAntagonistTimers, float[] savedProjectiles) {
        restore(courseSeed, savedLives, x, y, vy, remainingSeconds, savedRunSeconds,
                savedTerrainMultiplier, savedMode, savedResumeMode, savedHealth,
                savedDamageRecovery, savedFarthestX, savedVisibleWidth,
                savedAntagonistTimers, savedProjectiles, null);
    }

    public void restore(long courseSeed, int savedLives, float x, float y,
                        float vy, float remainingSeconds, double savedRunSeconds,
                        float savedTerrainMultiplier, Mode savedMode,
                        Mode savedResumeMode, int savedHealth, float savedDamageRecovery,
                        float savedFarthestX, float savedVisibleWidth,
                        float[] savedAntagonistTimers, float[] savedProjectiles,
                        float[] savedDefeatedAntagonists) {
        restore(courseSeed, savedLives, x, y, vy, remainingSeconds, savedRunSeconds,
                savedTerrainMultiplier, savedMode, savedResumeMode, savedHealth,
                savedDamageRecovery, savedFarthestX, savedVisibleWidth,
                savedAntagonistTimers, savedProjectiles, savedDefeatedAntagonists,
                0, false, false);
    }

    public void restore(long courseSeed, int savedLives, float x, float y,
                        float vy, float remainingSeconds, double savedRunSeconds,
                        float savedTerrainMultiplier, Mode savedMode,
                        Mode savedResumeMode, int savedHealth, float savedDamageRecovery,
                        float savedFarthestX, float savedVisibleWidth,
                        float[] savedAntagonistTimers, float[] savedProjectiles,
                        float[] savedDefeatedAntagonists, int savedBonusPoints,
                        boolean savedFirstJumpCompleted, boolean savedJumpInProgress) {
        restore(courseSeed, savedLives, x, y, vy, remainingSeconds, savedRunSeconds,
                savedTerrainMultiplier, savedMode, savedResumeMode, savedHealth,
                savedDamageRecovery, savedFarthestX, savedVisibleWidth,
                savedAntagonistTimers, savedProjectiles, savedDefeatedAntagonists,
                savedBonusPoints, savedFirstJumpCompleted, savedJumpInProgress, null, null);
    }

    public void restore(long courseSeed, int savedLives, float x, float y,
                        float vy, float remainingSeconds, double savedRunSeconds,
                        float savedTerrainMultiplier, Mode savedMode,
                        Mode savedResumeMode, int savedHealth, float savedDamageRecovery,
                        float savedFarthestX, float savedVisibleWidth,
                        float[] savedAntagonistTimers, float[] savedProjectiles,
                        float[] savedDefeatedAntagonists, int savedBonusPoints,
                        boolean savedFirstJumpCompleted, boolean savedJumpInProgress,
                        float[] savedEnemyState, float[] savedColoredProjectiles) {
        setVisibleWorldWidth(savedVisibleWidth);
        resetCourse(courseSeed);
        lives = Math.max(0, Math.min(MAX_LIVES, savedLives));
        playerX = x;
        farthestX = Math.max(x, savedFarthestX);
        bonusPoints = Math.max(0, savedBonusPoints);
        playerY = y;
        velocityY = vy;
        firstJumpCompleted = savedFirstJumpCompleted;
        controlledJumpInProgress = savedJumpInProgress && !firstJumpCompleted;
        health = Math.max(0, Math.min(MAX_HEALTH, savedHealth));
        damageRecoverySeconds = Math.max(0f, savedDamageRecovery);
        countdownSeconds = remainingSeconds;
        elapsedRunSeconds = Math.max(0d, savedRunSeconds);
        terrainSpeedMultiplier = Math.max(-.75f, Math.min(1.75f, savedTerrainMultiplier));
        if (savedDefeatedAntagonists != null) {
            for (float enemyX : savedDefeatedAntagonists) {
                if (!Float.isNaN(enemyX) && !Float.isInfinite(enemyX)) {
                    defeatedAntagonists.add(enemyX);
                }
            }
        }
        generateAhead(farthestX + Math.max(1800f, visibleWorldWidth + 550f));
        if (savedEnemyState != null) {
            boolean extended = savedEnemyState.length > 0 && savedEnemyState[0] == -2f;
            int stride = extended ? 11 : 9;
            for (Antagonist antagonist : antagonists) {
                for (int i = extended ? 1 : 0; i + stride - 1 < savedEnemyState.length;
                        i += stride) {
                    if (Math.abs(antagonist.spawnX - savedEnemyState[i]) >= .1f) continue;
                    float patrol = antagonist.type == EnemyType.MOVING ? ENEMY_PATROL_RANGE : 0f;
                    float savedX = Math.max(antagonist.spawnX - patrol,
                            Math.min(antagonist.spawnX + patrol, savedEnemyState[i + 1]));
                    if (extended && savedEnemyState[i + 9] == 0f) {
                        antagonist.platform = null;
                        antagonist.x = savedX;
                    } else if (canStandAt(savedX, antagonist.platform)) {
                        antagonist.x = savedX;
                    }
                    antagonist.shotTimer = Math.max(0f, savedEnemyState[i + 2]);
                    antagonist.direction = savedEnemyState[i + 3] < 0f ? -1 : 1;
                    antagonist.directionTimer = Math.max(0f, savedEnemyState[i + 4]);
                    antagonist.jumpTimer = Math.max(0f, savedEnemyState[i + 5]);
                    antagonist.jumpOffset = Math.max(-WORLD_HEIGHT,
                            Math.min(0f, savedEnemyState[i + 6]));
                    antagonist.jumpVelocity = savedEnemyState[i + 7];
                    antagonist.behaviorStep = Math.max(0, (int) savedEnemyState[i + 8]);
                    antagonist.y = extended && Float.isFinite(savedEnemyState[i + 10])
                            ? savedEnemyState[i + 10]
                            : supportYAt(antagonist.x, antagonist.platform)
                            - PLAYER_HEIGHT + antagonist.jumpOffset;
                    break;
                }
            }
        } else if (savedAntagonistTimers != null) {
            for (Antagonist antagonist : antagonists) {
                for (int i = 0; i + 1 < savedAntagonistTimers.length; i += 2) {
                    if (Math.abs(antagonist.spawnX - savedAntagonistTimers[i]) < .1f) {
                        antagonist.shotTimer = Math.max(0f, savedAntagonistTimers[i + 1]);
                        break;
                    }
                }
            }
        }
        if (savedColoredProjectiles != null) {
            for (int i = 0; i + 4 < savedColoredProjectiles.length; i += 5) {
                EnemyType type = savedColoredProjectiles[i + 4] == 1f
                        ? EnemyType.MOVING : EnemyType.STATIONARY;
                projectiles.add(new Projectile(savedColoredProjectiles[i],
                        savedColoredProjectiles[i + 1], -SHOT_SPEED, 0f, type));
            }
        } else if (savedProjectiles != null) {
            for (int i = 0; i + 3 < savedProjectiles.length; i += 4) {
                projectiles.add(new Projectile(savedProjectiles[i], savedProjectiles[i + 1],
                        -SHOT_SPEED, 0f, EnemyType.STATIONARY));
            }
        }
        if (savedMode == Mode.TITLE) {
            mode = Mode.TITLE;
        } else if (savedMode == Mode.GAME_OVER || lives == 0) {
            lives = 0;
            mode = Mode.GAME_OVER;
        } else {
            mode = Mode.PAUSED;
            resumeMode = savedMode == Mode.PAUSED ? savedResumeMode : savedMode;
            if (resumeMode != Mode.COUNTDOWN) resumeMode = Mode.RUNNING;
        }
    }

    public List<Hazard> getHazards() { return hazards; }
    public List<Platform> getPlatforms() { return Collections.unmodifiableList(platforms); }
    public List<Star> getStars() { return Collections.unmodifiableList(stars); }
    public List<StarBurst> getStarBursts() { return Collections.unmodifiableList(starBursts); }
    public List<EnemyBurst> getEnemyBursts() { return Collections.unmodifiableList(enemyBursts); }
    public float[] getCollectedStars() {
        float[] state = new float[collectedStars.size()];
        for (int i = 0; i < state.length; i++) state[i] = collectedStars.get(i);
        return state;
    }
    public void restoreCollectedStars(float[] state) {
        collectedStars.clear();
        if (state == null) return;
        for (float x : state) {
            if (Float.isNaN(x) || Float.isInfinite(x) || wasCollected(x)) continue;
            collectedStars.add(x);
        }
        for (int i = stars.size() - 1; i >= 0; i--) {
            if (wasCollected(stars.get(i).x)) stars.remove(i);
        }
    }
    public float[] getStarBurstState() {
        float[] state = new float[starBursts.size() * 4];
        for (int i = 0; i < starBursts.size(); i++) {
            StarBurst burst = starBursts.get(i);
            state[i * 4] = burst.x;
            state[i * 4 + 1] = burst.y;
            state[i * 4 + 2] = burst.color.ordinal();
            state[i * 4 + 3] = burst.remainingSeconds;
        }
        return state;
    }
    public void restoreStarBursts(float[] state) {
        starBursts.clear();
        if (state == null) return;
        for (int i = 0; i + 3 < state.length; i += 4) {
            int color = (int) state[i + 2];
            if (color < 0 || color >= StarColor.values().length
                    || state[i + 3] <= 0f || state[i + 3] > STAR_BURST_SECONDS) continue;
            starBursts.add(new StarBurst(state[i], state[i + 1],
                    StarColor.values()[color], state[i + 3]));
        }
    }
    public float[] getEnemyBurstState() {
        float[] state = new float[enemyBursts.size() * 4];
        for (int i = 0; i < enemyBursts.size(); i++) {
            EnemyBurst burst = enemyBursts.get(i);
            state[i * 4] = burst.x;
            state[i * 4 + 1] = burst.y;
            state[i * 4 + 2] = burst.type.ordinal();
            state[i * 4 + 3] = burst.remainingSeconds;
        }
        return state;
    }
    public void restoreEnemyBursts(float[] state) {
        enemyBursts.clear();
        if (state == null) return;
        for (int i = 0; i + 3 < state.length; i += 4) {
            if (state[i + 2] != 0f && state[i + 2] != 1f) continue;
            if (state[i + 3] <= 0f || state[i + 3] > ENEMY_BURST_SECONDS) continue;
            enemyBursts.add(new EnemyBurst(state[i], state[i + 1],
                    EnemyType.values()[(int) state[i + 2]], state[i + 3]));
        }
    }
    public List<Antagonist> getAntagonists() { return Collections.unmodifiableList(antagonists); }
    public List<BonusPopup> getBonusPopups() { return Collections.unmodifiableList(bonusPopups); }
    public float[] getDefeatedAntagonists() {
        float[] state = new float[defeatedAntagonists.size()];
        for (int i = 0; i < state.length; i++) state[i] = defeatedAntagonists.get(i);
        return state;
    }
    public List<Projectile> getProjectiles() { return Collections.unmodifiableList(projectiles); }
    public float[] getAntagonistTimers() {
        float[] state = new float[antagonists.size() * 2];
        for (int i = 0; i < antagonists.size(); i++) {
            state[i * 2] = antagonists.get(i).spawnX;
            state[i * 2 + 1] = antagonists.get(i).shotTimer;
        }
        return state;
    }
    public float[] getAntagonistState() {
        // The leading sentinel distinguishes this layout from older 9-value saves.
        float[] state = new float[1 + antagonists.size() * 11];
        state[0] = -2f;
        for (int i = 0; i < antagonists.size(); i++) {
            Antagonist foe = antagonists.get(i);
            int offset = 1 + i * 11;
            state[offset] = foe.spawnX;
            state[offset + 1] = foe.x;
            state[offset + 2] = foe.shotTimer;
            state[offset + 3] = foe.direction;
            state[offset + 4] = foe.directionTimer;
            state[offset + 5] = foe.jumpTimer;
            state[offset + 6] = foe.jumpOffset;
            state[offset + 7] = foe.jumpVelocity;
            state[offset + 8] = foe.behaviorStep;
            state[offset + 9] = foe.platform == null ? 0f : 1f;
            state[offset + 10] = foe.y;
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
    public float[] getColoredProjectileState() {
        float[] state = new float[projectiles.size() * 5];
        for (int i = 0; i < projectiles.size(); i++) {
            Projectile shot = projectiles.get(i);
            int offset = i * 5;
            state[offset] = shot.x;
            state[offset + 1] = shot.y;
            state[offset + 2] = shot.vx;
            state[offset + 3] = shot.vy;
            state[offset + 4] = shot.type.ordinal();
        }
        return state;
    }
    public float[] getBonusPopupState() {
        float[] state = new float[bonusPopups.size() * 5];
        for (int i = 0; i < bonusPopups.size(); i++) {
            BonusPopup popup = bonusPopups.get(i);
            int offset = i * 5;
            state[offset] = popup.x;
            state[offset + 1] = popup.y;
            state[offset + 2] = popup.amount;
            state[offset + 3] = popup.starColor == null ? popup.type.ordinal()
                    : 2 + popup.starColor.ordinal();
            state[offset + 4] = popup.remainingSeconds;
        }
        return state;
    }
    public void restoreBonusPopups(float[] state) {
        bonusPopups.clear();
        if (state == null) return;
        for (int i = 0; i + 4 < state.length; i += 5) {
            if (state[i + 4] <= 0f || state[i + 4] > BONUS_POPUP_SECONDS) continue;
            int kind = (int) state[i + 3];
            if (kind >= 2 && kind < 2 + StarColor.values().length) {
                bonusPopups.add(new BonusPopup(state[i], state[i + 1],
                        StarColor.values()[kind - 2], state[i + 4]));
            } else if (kind == 0 || kind == 1) {
                bonusPopups.add(new BonusPopup(state[i], state[i + 1],
                        (int) state[i + 2], EnemyType.values()[kind], state[i + 4]));
            }
        }
    }
    public void loadBestDistance(int distance) { bestDistance = Math.max(0, distance); }
    public void loadBestScore(int points) { bestScore = Math.max(0, points); }
    public Mode getMode() { return mode; }
    public Mode getResumeMode() { return resumeMode; }
    public long getSeed() { return seed; }
    public int getLives() { return lives; }
    public float getPlayerX() { return playerX; }
    public float getFarthestX() { return farthestX; }
    public int getBonusPoints() { return bonusPoints; }
    public float getPlayerY() { return playerY; }
    public float getVelocityY() { return velocityY; }
    /** Nose-up angle for drawing the car; grounded motion follows the terrain. */
    public float getPlayerPitchDegrees() {
        if (inWater) return (float) Math.toDegrees(Math.atan2(-waterVerticalSpeed,
                Math.max(1f, getSpeed())));
        if (isStanding()) return slopeDegreesAt(playerX + PLAYER_WIDTH / 2f);
        return (float) Math.toDegrees(Math.atan2(-velocityY, Math.max(1f, Math.abs(getSpeed()))));
    }
    public boolean isControlledJumpInProgress() { return controlledJumpInProgress; }
    public boolean hasCompletedFirstJump() { return firstJumpCompleted; }
    public int getHealth() { return health; }
    public float getDamageRecoverySeconds() { return damageRecoverySeconds; }
    public boolean isDamageBlinkLight() {
        float sinceHit = DAMAGE_RECOVERY_SECONDS - damageRecoverySeconds;
        return sinceHit >= 0f && sinceHit < DAMAGE_BLINK_SECONDS
                && ((int) (sinceHit / DAMAGE_BLINK_INTERVAL) & 1) == 0;
    }
    public float getVisibleWorldWidth() { return visibleWorldWidth; }
    public float getCountdownSeconds() { return countdownSeconds; }
    public double getElapsedRunSeconds() { return elapsedRunSeconds; }
    public float getTerrainSpeedMultiplier() { return terrainSpeedMultiplier; }
    public boolean isInWater() { return inWater; }
    /** Water crossing and exit speed are saved separately from terrain momentum. */
    public float[] getWaterState() {
        return new float[]{inWater ? 1f : 0f, waterHoleX, waterEntrySpeed,
                waterVerticalSpeed, waterExitSpeed};
    }

    public void restoreWaterState(float[] state) {
        if (state == null || state.length < 5) return;
        if (state[4] > 0f && Float.isFinite(state[4])) waterExitSpeed = state[4];
        if (state[0] != 1f) return;
        for (Hazard hazard : hazards) {
            if (hazard.type == HazardType.HOLE && Math.abs(hazard.x - state[1]) < .1f
                    && playerX + PLAYER_WIDTH / 2f >= hazard.x + 4f
                    && playerX + PLAYER_WIDTH / 2f < hazard.end()
                    && playerY < WATER_SURFACE_Y
                    && state[2] > 0f && Float.isFinite(state[2])
                    && Float.isFinite(state[3])) {
                inWater = true;
                waterHoleX = hazard.x;
                waterEntrySpeed = state[2];
                waterVerticalSpeed = state[3];
                return;
            }
        }
    }
    public boolean isWaitingForJump() { return waitingForJump; }
    public void restoreTerrainStop(boolean stopped) {
        waitingForJump = stopped;
        if (stopped) terrainSpeedMultiplier = 0f;
    }
    public float getBaseSpeed() {
        return BASE_SPEED + SPEED_STEP
                * (int) Math.floor((elapsedRunSeconds + .000001d) / SPEED_INTERVAL_SECONDS);
    }
    public float getSpeed() {
        return inWater ? waterEntrySpeed * WATER_SPEED_FRACTION
                : getBaseSpeed() * terrainSpeedMultiplier;
    }
    public float getSpeedMetersPerSecond() { return getSpeed() / WORLD_UNITS_PER_METER; }
    public int getBestDistance() { return bestDistance; }
    public int getBestScore() { return bestScore; }
    public int getDistance() { return getCourseDistance(); }
    public int getScore() { return getCourseDistance() + bonusPoints; }
    private int getCourseDistance() {
        return Math.max(0, (int) ((farthestX - START_X) / WORLD_UNITS_PER_METER));
    }
}
