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
    public static final int MAX_LIVES = 5;
    public static final float PROJECTILE_RADIUS = 6f;

    private static final float START_X = 140f;
    private static final float ENEMY_START_DISTANCE = 500f * WORLD_UNITS_PER_METER;
    private static final float MOVING_ENEMY_START_DISTANCE = 1200f * WORLD_UNITS_PER_METER;
    private static final float ENEMY_PATROL_RANGE = 5f * WORLD_UNITS_PER_METER;
    private static final float ENEMY_PATROL_SPEED = 42f;
    private static final float BONUS_POPUP_SECONDS = .9f;
    private static final float RESPAWN_DISTANCE = 50f * WORLD_UNITS_PER_METER;
    private static final float SAFE_RESPAWN_LEAD = 110f;
    private static final float DAMAGE_RECOVERY_SECONDS = .65f;
    private static final float SHOT_INTERVAL_SECONDS = 1.5f;
    private static final float SHOT_SPEED = 380f;
    private static final float SHOT_BOUNCE_VELOCITY = -200f;
    public static final float BASE_SPEED = 235f;
    private static final float SPEED_STEP = 7f;
    private static final float SPEED_INTERVAL_SECONDS = 3f;
    private static final float GRAVITY = 1080f;
    private static final float JUMP_VELOCITY = -490f;
    private static final float ENEMY_JUMP_VELOCITY =
            JUMP_VELOCITY * .4472136f; // sqrt(.2): 20% of the player's jump height.
    private static final float SLOPE_GRAVITY_RESPONSE_SECONDS = .12f;
    private static final float SLOPE_MOMENTUM_SECONDS = .07f;
    private static final float SLOPE_RECOVERY_PER_SECOND = 1.5f;
    private static final HazardType[] HAZARD_TYPES = HazardType.values();

    public enum Mode { TITLE, COUNTDOWN, RUNNING, PAUSED, GAME_OVER }
    public enum HazardType { HOLE, WALL, DIP }
    public enum EnemyType { STATIONARY, MOVING }

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

    public static final class Antagonist {
        public final float spawnX;
        public final EnemyType type;
        public float x;
        public float y;
        private float shotTimer = .75f;
        private float directionTimer;
        private float jumpTimer;
        private float jumpOffset;
        private float jumpVelocity;
        private int direction;
        private int behaviorStep;

        private Antagonist(float x, float y, EnemyType type) {
            this.spawnX = x;
            this.type = type;
            this.x = x;
            this.y = y;
        }
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
        private float remainingSeconds;

        private BonusPopup(float x, float y, int amount, EnemyType type, float remainingSeconds) {
            this.x = x;
            this.y = y;
            this.amount = amount;
            this.type = type;
            this.remainingSeconds = remainingSeconds;
        }

        public float getProgress() { return 1f - remainingSeconds / BONUS_POPUP_SECONDS; }
    }

    private final List<Hazard> hazards = new ArrayList<>();
    private final List<Antagonist> antagonists = new ArrayList<>();
    private final List<Float> defeatedAntagonists = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<BonusPopup> bonusPopups = new ArrayList<>();
    private Random random;
    private Random antagonistRandom;
    private float nextHazardX;
    private float nextAntagonistX;
    private float visibleWorldWidth = 960f;
    private long seed;
    private int lives = MAX_LIVES;
    private float playerX;
    private float farthestX;
    private int bonusMeters;
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
    private int bestDistance;
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
        antagonistRandom = new Random(seed ^ 0x6A09E667F3BCC909L);
        hazards.clear();
        antagonists.clear();
        defeatedAntagonists.clear();
        projectiles.clear();
        bonusPopups.clear();
        nextHazardX = 840f;
        nextAntagonistX = START_X + ENEMY_START_DISTANCE + 900f
                + antagonistRandom.nextInt(500);
        playerX = START_X;
        farthestX = START_X;
        bonusMeters = 0;
        playerY = GROUND_Y - PLAYER_HEIGHT;
        velocityY = 0f;
        controlledJumpInProgress = false;
        firstJumpCompleted = false;
        health = MAX_HEALTH;
        damageRecoverySeconds = 0f;
        elapsedRunSeconds = 0d;
        terrainSpeedMultiplier = 1f;
        waitingForJump = false;
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
            // A finite height change needs a nonzero angle; flat ground provides 0°.
            int entrance = type == HazardType.HOLE ? 90 : 1 + random.nextInt(90);
            int exit = type == HazardType.HOLE ? 90 : 1 + random.nextInt(90);
            Hazard hazard = new Hazard(type, nextHazardX, flatWidth, entrance, exit);
            hazards.add(hazard);
            // Clear ground between hazards leaves time for a fresh jump.
            nextHazardX = hazard.end() + 290f + random.nextInt(125);
        }
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
            float left = surfaceYAt(x + 2f);
            float center = surfaceYAt(x + PLAYER_WIDTH / 2f);
            float right = surfaceYAt(x + PLAYER_WIDTH - 2f);
            if (!wasDefeated(x) && !Float.isNaN(center)
                    && !Float.isNaN(left) && !Float.isNaN(right)
                    && Math.abs(left - center) <= 20f
                    && Math.abs(right - center) <= 20f) {
                Antagonist antagonist = new Antagonist(x, center - PLAYER_HEIGHT, type);
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

    public void setVisibleWorldWidth(float width) {
        if (width > 0f && !Float.isInfinite(width)) visibleWorldWidth = width;
    }

    public void jump() {
        if (mode == Mode.RUNNING && isStanding()) {
            velocityY = JUMP_VELOCITY;
            controlledJumpInProgress = true;
            if (waitingForJump || terrainSpeedMultiplier < 0f) {
                waitingForJump = false;
                terrainSpeedMultiplier = 1f;
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
        float feet = playerY + PLAYER_HEIGHT;
        return velocityY >= 0f
                && Math.abs(feet - surfaceYForPlayer(playerX, feet)) < 1.2f;
    }

    public void update(float seconds) {
        if (mode == Mode.RUNNING || mode == Mode.COUNTDOWN || mode == Mode.GAME_OVER) {
            updateBonusPopups(Math.max(0f, seconds));
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
        farthestX = Math.max(farthestX, playerX);

        // A jump can land on the near edge before the runner's center reaches it.
        boolean landed = false;
        float feet = playerY + PLAYER_HEIGHT;
        if (velocityY >= 0f && oldFeet <= GROUND_Y - LEDGE_HEIGHT + 1f
                && feet >= GROUND_Y - LEDGE_HEIGHT) {
            for (Hazard hazard : hazards) {
                if (hazard.type == HazardType.WALL && hazard.entranceDegrees == 90
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
            } else if (velocityY >= 0f && !Float.isNaN(surface)
                    && oldFeet <= surface + 1f
                    && playerY + PLAYER_HEIGHT >= surface) {
                landAt(surface);
            }
        }
        if (playerY + PLAYER_HEIGHT > GROUND_Y + 75f) {
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
                defeatedAntagonists.add(antagonist.spawnX);
                antagonists.remove(i);
                int bonus = stomped ? 2 : 1;
                bonusMeters += bonus;
                bonusPopups.add(new BonusPopup(antagonist.x + PLAYER_WIDTH / 2f,
                        antagonist.y, bonus, antagonist.type, BONUS_POPUP_SECONDS));
                if (!stomped && takeDamage(2, true)) return true;
                break;
            }
        }
        if (updateProjectiles(dt)) return true;
        if (playerX > oldX + .01f) elapsedRunSeconds += dt;
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
                top = GROUND_Y - LEDGE_HEIGHT;
            } else if (hazard.type == HazardType.DIP
                    && hazard.end() - hazard.flatEnd < 10f) {
                face = hazard.flatEnd;
                top = GROUND_Y;
            } else continue;
            if (oldX < face && playerX + PLAYER_WIDTH > face
                    && playerY + PLAYER_HEIGHT > top + 1f
                    && playerY < top + (hazard.type == HazardType.WALL
                    ? LEDGE_HEIGHT : DIP_DEPTH)) {
                playerX = face - PLAYER_WIDTH;
                if (wasStanding) stopUntilJump(hazard.type == HazardType.WALL
                        ? GROUND_Y : GROUND_Y + DIP_DEPTH);
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
                ground = GROUND_Y + DIP_DEPTH;
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
            if (Math.abs(nextX - antagonist.spawnX) <= ENEMY_PATROL_RANGE
                    && canStandAt(nextX)) {
                antagonist.x = nextX;
            } else {
                antagonist.direction = -antagonist.direction;
            }

            antagonist.jumpTimer -= dt;
            if (antagonist.jumpTimer <= 0f && antagonist.jumpOffset == 0f) {
                antagonist.jumpVelocity = ENEMY_JUMP_VELOCITY;
                antagonist.jumpTimer = 1f + nextEnemyRandom(antagonist) * 2f;
            }
            if (antagonist.jumpVelocity != 0f) {
                antagonist.jumpOffset += antagonist.jumpVelocity * dt + .5f * GRAVITY * dt * dt;
                antagonist.jumpVelocity += GRAVITY * dt;
                if (antagonist.jumpOffset >= 0f) {
                    antagonist.jumpOffset = 0f;
                    antagonist.jumpVelocity = 0f;
                }
            }
            antagonist.y = surfaceYAt(antagonist.x + PLAYER_WIDTH / 2f)
                    - PLAYER_HEIGHT + antagonist.jumpOffset;
        }
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
                    float startX = antagonist.x - PROJECTILE_RADIUS - 2f;
                    float startY = antagonist.y + PLAYER_HEIGHT / 2f;
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
        if (shot.y + PROJECTILE_RADIUS < GROUND_Y - LEDGE_HEIGHT) return false;
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
            if (hazard.type == HazardType.WALL && hazard.entranceDegrees == 90
                    && left < hazard.x && left + PLAYER_WIDTH > hazard.x
                    && feet <= GROUND_Y - LEDGE_HEIGHT + 1.2f) {
                return GROUND_Y - LEDGE_HEIGHT;
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
        int slope = grounded ? slopeDegreesAt(center) : 0;
        if (slope != 0) {
            double angle = Math.toRadians(Math.abs(slope));
            float gravityEffect = GRAVITY * SLOPE_GRAVITY_RESPONSE_SECONDS
                    / getBaseSpeed() * (float) Math.sin(angle);
            float target = slope > 0
                    ? (float) Math.cos(angle) - gravityEffect
                    : 1f + gravityEffect;
            float response = 1f - (float) Math.exp(-dt / SLOPE_MOMENTUM_SECONDS);
            terrainSpeedMultiplier += (target - terrainSpeedMultiplier) * response;
        } else if (terrainSpeedMultiplier < 1f) {
            terrainSpeedMultiplier = Math.min(1f,
                    terrainSpeedMultiplier + SLOPE_RECOVERY_PER_SECOND * dt);
        } else {
            terrainSpeedMultiplier = Math.max(1f,
                    terrainSpeedMultiplier - SLOPE_RECOVERY_PER_SECOND * dt);
        }
    }

    private void loseLife() {
        lives--;
        controlledJumpInProgress = false;
        projectiles.clear();
        if (lives == 0) {
            bestDistance = Math.max(bestDistance, getDistance());
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
                        float[] savedDefeatedAntagonists, int savedBonusMeters,
                        boolean savedFirstJumpCompleted, boolean savedJumpInProgress) {
        restore(courseSeed, savedLives, x, y, vy, remainingSeconds, savedRunSeconds,
                savedTerrainMultiplier, savedMode, savedResumeMode, savedHealth,
                savedDamageRecovery, savedFarthestX, savedVisibleWidth,
                savedAntagonistTimers, savedProjectiles, savedDefeatedAntagonists,
                savedBonusMeters, savedFirstJumpCompleted, savedJumpInProgress, null, null);
    }

    public void restore(long courseSeed, int savedLives, float x, float y,
                        float vy, float remainingSeconds, double savedRunSeconds,
                        float savedTerrainMultiplier, Mode savedMode,
                        Mode savedResumeMode, int savedHealth, float savedDamageRecovery,
                        float savedFarthestX, float savedVisibleWidth,
                        float[] savedAntagonistTimers, float[] savedProjectiles,
                        float[] savedDefeatedAntagonists, int savedBonusMeters,
                        boolean savedFirstJumpCompleted, boolean savedJumpInProgress,
                        float[] savedEnemyState, float[] savedColoredProjectiles) {
        setVisibleWorldWidth(savedVisibleWidth);
        resetCourse(courseSeed);
        lives = Math.max(0, Math.min(MAX_LIVES, savedLives));
        playerX = x;
        farthestX = Math.max(x, savedFarthestX);
        bonusMeters = Math.max(0, savedBonusMeters);
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
            for (Antagonist antagonist : antagonists) {
                for (int i = 0; i + 8 < savedEnemyState.length; i += 9) {
                    if (Math.abs(antagonist.spawnX - savedEnemyState[i]) >= .1f) continue;
                    float patrol = antagonist.type == EnemyType.MOVING ? ENEMY_PATROL_RANGE : 0f;
                    float savedX = Math.max(antagonist.spawnX - patrol,
                            Math.min(antagonist.spawnX + patrol, savedEnemyState[i + 1]));
                    if (canStandAt(savedX)) antagonist.x = savedX;
                    antagonist.shotTimer = Math.max(0f, savedEnemyState[i + 2]);
                    antagonist.direction = savedEnemyState[i + 3] < 0f ? -1 : 1;
                    antagonist.directionTimer = Math.max(0f, savedEnemyState[i + 4]);
                    antagonist.jumpTimer = Math.max(0f, savedEnemyState[i + 5]);
                    antagonist.jumpOffset = Math.max(-30f, Math.min(0f, savedEnemyState[i + 6]));
                    antagonist.jumpVelocity = savedEnemyState[i + 7];
                    antagonist.behaviorStep = Math.max(0, (int) savedEnemyState[i + 8]);
                    antagonist.y = surfaceYAt(antagonist.x + PLAYER_WIDTH / 2f)
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
        float[] state = new float[antagonists.size() * 9];
        for (int i = 0; i < antagonists.size(); i++) {
            Antagonist foe = antagonists.get(i);
            int offset = i * 9;
            state[offset] = foe.spawnX;
            state[offset + 1] = foe.x;
            state[offset + 2] = foe.shotTimer;
            state[offset + 3] = foe.direction;
            state[offset + 4] = foe.directionTimer;
            state[offset + 5] = foe.jumpTimer;
            state[offset + 6] = foe.jumpOffset;
            state[offset + 7] = foe.jumpVelocity;
            state[offset + 8] = foe.behaviorStep;
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
            state[offset + 3] = popup.type.ordinal();
            state[offset + 4] = popup.remainingSeconds;
        }
        return state;
    }
    public void restoreBonusPopups(float[] state) {
        bonusPopups.clear();
        if (state == null) return;
        for (int i = 0; i + 4 < state.length; i += 5) {
            if (state[i + 4] <= 0f || state[i + 4] > BONUS_POPUP_SECONDS) continue;
            EnemyType type = state[i + 3] == 1f ? EnemyType.MOVING : EnemyType.STATIONARY;
            bonusPopups.add(new BonusPopup(state[i], state[i + 1],
                    (int) state[i + 2], type, state[i + 4]));
        }
    }
    public void loadBestDistance(int distance) { bestDistance = Math.max(0, distance); }
    public Mode getMode() { return mode; }
    public Mode getResumeMode() { return resumeMode; }
    public long getSeed() { return seed; }
    public int getLives() { return lives; }
    public float getPlayerX() { return playerX; }
    public float getFarthestX() { return farthestX; }
    public int getBonusMeters() { return bonusMeters; }
    public float getPlayerY() { return playerY; }
    public float getVelocityY() { return velocityY; }
    public boolean isControlledJumpInProgress() { return controlledJumpInProgress; }
    public boolean hasCompletedFirstJump() { return firstJumpCompleted; }
    public int getHealth() { return health; }
    public float getDamageRecoverySeconds() { return damageRecoverySeconds; }
    public float getVisibleWorldWidth() { return visibleWorldWidth; }
    public float getCountdownSeconds() { return countdownSeconds; }
    public double getElapsedRunSeconds() { return elapsedRunSeconds; }
    public float getTerrainSpeedMultiplier() { return terrainSpeedMultiplier; }
    public boolean isWaitingForJump() { return waitingForJump; }
    public void restoreTerrainStop(boolean stopped) {
        waitingForJump = stopped;
        if (stopped) terrainSpeedMultiplier = 0f;
    }
    public float getBaseSpeed() {
        return BASE_SPEED + SPEED_STEP
                * (int) Math.floor((elapsedRunSeconds + .000001d) / SPEED_INTERVAL_SECONDS);
    }
    public float getSpeed() { return getBaseSpeed() * terrainSpeedMultiplier; }
    public float getSpeedMetersPerSecond() { return getSpeed() / WORLD_UNITS_PER_METER; }
    public int getBestDistance() { return bestDistance; }
    public int getDistance() {
        return getCourseDistance() + bonusMeters;
    }
    private int getCourseDistance() {
        return Math.max(0, (int) ((farthestX - START_X) / WORLD_UNITS_PER_METER));
    }
}
