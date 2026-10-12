package com.stepan.skyboundrunner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** All drawing is resolution-independent and happens on the UI frame clock. */
public final class RunnerView extends View {
    private static final String BEST_SCORE_KEY = "best_run_distance";
    private static final String BEST_POINTS_KEY = "best_run_score";
    private static final String OLD_SCORES_KEY = "completed_attempt_distances";
    private static final int SKY = Color.rgb(105, 181, 220);
    private static final int SAND_SURFACE = Color.rgb(251, 232, 159);
    private static final int SAND_DEPTH = Color.rgb(239, 180, 114);
    private static final int SAND_GRAIN_LIGHT = Color.argb(88, 255, 246, 199);
    private static final int SAND_GRAIN_DARK = Color.argb(82, 205, 145, 86);
    private static final int GROUND_LINE = Color.rgb(210, 123, 61);
    private static final float GROUND_LINE_WIDTH = RunnerEngine.PLAYER_HEIGHT / 6f;
    private static final int WATER = Color.argb(64, 36, 140, 213);
    private static final int WATER_EDGE = Color.argb(64, 171, 239, 255);
    private static final int FAR_CITY = Color.rgb(119, 160, 187);
    private static final int FAR_CITY_EDGE = Color.rgb(108, 150, 180);
    private static final int FAR_WINDOWS = Color.rgb(147, 183, 203);
    private static final int NEAR_CITY = Color.rgb(83, 133, 165);
    private static final int NEAR_CITY_EDGE = Color.rgb(73, 120, 154);
    private static final int NEAR_WINDOWS = Color.rgb(129, 176, 198);
    private static final int PALM_TRUNK = Color.rgb(57, 104, 124);
    private static final int PALM_LEAF = Color.rgb(43, 123, 119);
    private static final int PALM_LEAF_SHADE = Color.rgb(37, 110, 108);
    private static final int GRASS_BASE = Color.rgb(77, 165, 92);
    private static final int GRASS_TIPS = Color.rgb(64, 154, 85);
    private static final float[][] FROND_SPREAD = {
            {-56f, -45f, -28f, 27f, 44f, 56f},
            {-59f, -40f, -21f, 26f, 39f, 53f},
            {-49f, -36f, -18f, 21f, 43f, 58f},
            {-61f, -47f, -30f, 18f, 36f, 55f},
            {-52f, -38f, -24f, 30f, 48f, 59f}
    };
    private static final float[][] FROND_DROP = {
            {16f, -8f, -24f, -23f, -7f, 17f},
            {22f, 2f, -21f, -27f, -10f, 13f},
            {11f, -15f, -28f, -17f, -3f, 21f},
            {19f, -11f, -19f, -25f, 0f, 23f},
            {15f, -4f, -26f, -29f, -12f, 14f}
    };
    private static final int AVATAR = Color.rgb(35, 57, 126);
    private static final int DAMAGE_BLINK_BLUE = Color.rgb(218, 245, 255);
    private static final int BIKE_ACCENT = Color.rgb(91, 162, 210);
    private static final float HOVER_GAP = 2f;
    private static final int ENEMY_RED = Color.rgb(222, 45, 54);
    private static final int ENEMY_RED_WEAPON = Color.rgb(162, 34, 45);
    private static final int ENEMY_ORANGE = Color.rgb(242, 123, 32);
    private static final int ENEMY_ORANGE_DETAIL = Color.rgb(187, 83, 19);
    private static final int LIFE_RED = Color.rgb(222, 45, 54);
    private static final int LIFE_GRAY = Color.rgb(146, 151, 159);
    private static final int EMPTY_HEALTH = Color.argb(128, 218, 223, 227);
    private static final int INK = Color.rgb(20, 44, 94);
    private static final int JUMP_ICON = Color.rgb(32, 224, 236);
    private static final int SHOOT_ICON = Color.rgb(69, 130, 255);

    private final RunnerEngine engine = new RunnerEngine();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path terrainPath = new Path();
    private final Path heartPath = new Path();
    private final Path turretPath = new Path();
    private final Path bikePath = new Path();
    private final Path palmPath = new Path();
    private final Path grassPath = new Path();
    private final Path waterPath = new Path();
    private final Path starPath = new Path();
    private final Path groundPath = new Path();
    private final Shader sandShader = new LinearGradient(0f, RunnerEngine.GROUND_Y,
            0f, RunnerEngine.WORLD_HEIGHT, SAND_SURFACE, SAND_DEPTH,
            Shader.TileMode.CLAMP);
    private final SharedPreferences scorePreferences;
    private SceneryLayout.GrassPattern grassPattern;
    private long lastFrameNanos;
    private float logicalWidth = 960f;
    private float waveSeconds;
    private float shootPressSeconds;

    public RunnerView(Context context) {
        super(context);
        scorePreferences = context.getSharedPreferences("runner_scores", Context.MODE_PRIVATE);
        int best = scorePreferences.getInt(BEST_SCORE_KEY, 0);
        String storedScores = scorePreferences.getString(OLD_SCORES_KEY, "");
        if (storedScores != null && !storedScores.isEmpty()) {
            for (String value : storedScores.split(",")) {
                try {
                    best = Math.max(best, Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                    // Ignore a corrupt score instead of preventing the game from starting.
                }
            }
        }
        engine.loadBestDistance(best);
        engine.loadBestScore(scorePreferences.getInt(BEST_POINTS_KEY, best));
        setFocusable(true);
        setContentDescription("Skybound Runner game. Tap anywhere to jump while moving.");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = getHeight() / RunnerEngine.WORLD_HEIGHT;
        if (scale <= 0f) return;
        logicalWidth = getWidth() / scale;
        engine.setVisibleWorldWidth(logicalWidth);

        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            float dt = Math.min((now - lastFrameNanos) / 1_000_000_000f, .05f);
            shootPressSeconds = Math.max(0f, shootPressSeconds - dt);
            int previousBest = engine.getBestDistance();
            int previousScore = engine.getBestScore();
            if (engine.getMode() == RunnerEngine.Mode.RUNNING) waveSeconds += dt;
            engine.update(dt);
            if (engine.getBestDistance() > previousBest
                    || engine.getBestScore() > previousScore) saveBestScore();
        }
        lastFrameNanos = now;

        canvas.save();
        canvas.scale(scale, scale);
        canvas.drawColor(SKY);
        float cameraX = Math.max(0f, engine.getPlayerX() - logicalWidth * .30f);
        engine.generateAhead(cameraX + logicalWidth + 550f);
        drawScenery(canvas, cameraX);
        drawWorld(canvas, cameraX);
        drawBonuses(canvas, cameraX);
        drawHud(canvas);
        drawMenu(canvas);
        canvas.restore();

        if (engine.getMode() == RunnerEngine.Mode.RUNNING
                || engine.getMode() == RunnerEngine.Mode.COUNTDOWN
                || engine.getMode() == RunnerEngine.Mode.LIFE_LOSS
                || (engine.getMode() == RunnerEngine.Mode.GAME_OVER
                && (!engine.getBonusPopups().isEmpty()
                || !engine.getStarBursts().isEmpty()
                || !engine.getEnemyBursts().isEmpty()))) {
            postInvalidateOnAnimation();
        }
    }

    private void drawScenery(Canvas canvas, float cameraX) {
        long seed = engine.getSeed();
        drawCity(canvas, cameraX * SceneryLayout.FAR_SCROLL, seed, false);
        drawCity(canvas, cameraX * SceneryLayout.NEAR_SCROLL, seed, true);
        drawPalms(canvas, cameraX * SceneryLayout.PALM_SCROLL, seed);
    }

    private void drawCity(Canvas canvas, float scrollX, long seed, boolean near) {
        canvas.save();
        canvas.translate(-scrollX, 0f);
        paint.setStyle(Paint.Style.FILL);
        int bodyColor = near ? NEAR_CITY : FAR_CITY;
        int edgeColor = near ? NEAR_CITY_EDGE : FAR_CITY_EDGE;
        int windowColor = near ? NEAR_WINDOWS : FAR_WINDOWS;
        float step = SceneryLayout.BUILDING_STEP;
        for (int index = SceneryLayout.firstIndex(scrollX, step);
                index <= SceneryLayout.lastIndex(scrollX + logicalWidth, step); index++) {
            float x = index * step;
            float width = SceneryLayout.buildingWidth(seed, index, near);
            float top = RunnerEngine.GROUND_Y
                    - SceneryLayout.buildingHeight(seed, index, near);
            paint.setColor(bodyColor);
            canvas.drawRect(x, top, x + width, RunnerEngine.WORLD_HEIGHT, paint);
            paint.setColor(edgeColor);
            canvas.drawRect(x + width - 5f, top, x + width,
                    RunnerEngine.WORLD_HEIGHT, paint);
            // A stepped roof and occasional aerial give the dense towers a
            // futuristic silhouette without adding sprites or animation.
            float roof = SceneryLayout.random01(seed, index,
                    near ? 0x753BB37C3E8B7AE1L : 0x236AB634A6FD468BL);
            canvas.drawRect(x + 6f, top - 5f, x + width - 8f, top, paint);
            if (roof > .83f) {
                float aerialX = x + width * .5f;
                canvas.drawRect(aerialX - 1f, top - 20f, aerialX + 1f, top - 5f, paint);
            }
            paint.setColor(windowColor);
            for (float row = top + 17f; row < RunnerEngine.GROUND_Y - 12f; row += 23f) {
                int rowIndex = (int) ((row - top) / 23f);
                for (float column = x + 8f; column < x + width - 7f; column += 13f) {
                    if (SceneryLayout.random01(seed, index + rowIndex * 31,
                            0xE4BCB1D1793873A3L + (long) column * 17L + (near ? 1L : 0L))
                            > .22f) {
                        canvas.drawRect(column, row, column + 4f, row + 7f, paint);
                    }
                }
            }
        }
        canvas.restore();
    }

    private void drawPalms(Canvas canvas, float scrollX, long seed) {
        canvas.save();
        canvas.translate(-scrollX, 0f);
        paint.setStyle(Paint.Style.FILL);
        for (SceneryLayout.Palm palm : SceneryLayout.visiblePalms(seed,
                engine.getHazards(), scrollX, scrollX + logicalWidth)) {
            float x = palm.x;
            int view = palm.view;
            float halfRoot = SceneryLayout.palmTrunkWidth(view) / 2f + 1f;
            float rootY = Math.max(engine.surfaceYAt(x - halfRoot),
                    engine.surfaceYAt(x + halfRoot)) + 2f;
            drawPalm(canvas, x, rootY, view, palm.phase);
        }
        canvas.restore();
    }

    private void drawPalm(Canvas canvas, float x, float rootY, int view, float phase) {
        float height = SceneryLayout.palmHeight(view);
        float lean = SceneryLayout.palmLean(view);
        float halfTrunk = SceneryLayout.palmTrunkWidth(view) / 2f;
        float breeze = (float) Math.sin(waveSeconds * 1.15f + phase) * 2.4f;
        float crownX = x + lean + breeze;
        float crownY = rootY - height;
        palmPath.rewind();
        palmPath.moveTo(x - halfTrunk, rootY);
        palmPath.cubicTo(x - halfTrunk - 1f, rootY - height * .36f,
                crownX - halfTrunk - 3f, crownY + height * .31f,
                crownX - halfTrunk * .7f, crownY);
        palmPath.lineTo(crownX + halfTrunk * .7f, crownY);
        palmPath.cubicTo(crownX - 1f, crownY + height * .36f,
                x + halfTrunk, rootY - height * .28f, x + halfTrunk, rootY);
        palmPath.close();
        paint.setColor(PALM_TRUNK);
        canvas.drawPath(palmPath, paint);
        for (int i = 0; i < FROND_SPREAD[view].length; i++) {
            float flutter = (float) Math.sin(waveSeconds * 1.4f + phase + i * .6f) * 1.8f;
            float dx = FROND_SPREAD[view][i] + flutter;
            float dy = FROND_DROP[view][i] + flutter * .4f;
            palmPath.rewind();
            palmPath.moveTo(crownX, crownY);
            palmPath.quadTo(crownX + dx * .55f, crownY - 18f,
                    crownX + dx, crownY + dy);
            palmPath.quadTo(crownX + dx * .74f, crownY + dy - 9f,
                    crownX + dx * .29f, crownY - 5f);
            palmPath.close();
            paint.setColor(i % 2 == 0 ? PALM_LEAF : PALM_LEAF_SHADE);
            canvas.drawPath(palmPath, paint);
        }
    }

    private void drawWorld(Canvas canvas, float cameraX) {
        canvas.save();
        canvas.translate(-cameraX, 0f);
        float right = cameraX + logicalWidth;
        // Collectibles sit in front of the skyline and palms, but behind
        // solid terrain and platforms.
        drawStars(canvas, cameraX, right);
        drawAbilityIcons(canvas, cameraX, right);
        float cursor = cameraX;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setShader(sandShader);
        for (RunnerEngine.Hazard hazard : engine.getHazards()) {
            if (hazard.type == RunnerEngine.HazardType.WALL) continue;
            if (hazard.end() <= cameraX) continue;
            if (hazard.x >= right) break;
            if (hazard.x > cursor) {
                canvas.drawRect(cursor, RunnerEngine.GROUND_Y,
                        Math.min(hazard.x, right), RunnerEngine.WORLD_HEIGHT, paint);
            }
            if (hazard.type == RunnerEngine.HazardType.DIP) {
                drawTerrainShape(canvas, hazard);
            }
            cursor = Math.max(cursor, hazard.end());
        }
        if (cursor < right) {
            canvas.drawRect(cursor, RunnerEngine.GROUND_Y,
                    right, RunnerEngine.WORLD_HEIGHT, paint);
        }
        for (RunnerEngine.Hazard hazard : engine.getHazards()) {
            if (hazard.type != RunnerEngine.HazardType.WALL) continue;
            if (hazard.end() <= cameraX || hazard.x >= right) continue;
            drawTerrainShape(canvas, hazard);
        }
        paint.setShader(null);
        drawSandGrains(canvas, cameraX, right);
        drawGroundContour(canvas, cameraX, right);
        // Water over the submerged hull is translucent during a crossing.
        if (engine.isInWater()) drawMotorcycleRider(canvas);
        drawWater(canvas, cameraX, right);
        drawGrass(canvas, cameraX, right);
        paint.setStyle(Paint.Style.FILL);
        for (RunnerEngine.Platform platform : engine.getPlatforms()) {
            if (platform.end() <= cameraX) continue;
            if (platform.x >= right) break;
            paint.setColor(Color.WHITE);
            paint.setShader(new LinearGradient(0f, platform.top, 0f, platform.bottom,
                    SAND_SURFACE, SAND_DEPTH, Shader.TileMode.CLAMP));
            canvas.drawRect(platform.x, platform.top, platform.end(), platform.bottom, paint);
            paint.setShader(null);
            drawPlatformGrains(canvas, platform, cameraX, right);
        }
        for (RunnerEngine.Antagonist antagonist : engine.getAntagonists()) {
            if (antagonist.x + RunnerEngine.PLAYER_WIDTH < cameraX) continue;
            if (antagonist.x > right) break;
            if (antagonist.type == RunnerEngine.EnemyType.STATIONARY) {
                drawTurret(canvas, antagonist.x, antagonist.y);
            } else {
                float stride = (float) Math.sin(antagonist.x * .13f);
                drawStickFigure(canvas, antagonist.x, antagonist.y,
                        ENEMY_RED, false, stride, antagonist.isJumping(), true);
            }
        }
        for (RunnerEngine.Projectile shot : engine.getProjectiles()) {
            paint.setColor(enemyColor(shot.type));
            canvas.drawCircle(shot.getX(), shot.getY(), RunnerEngine.PROJECTILE_RADIUS, paint);
        }
        for (RunnerEngine.PlayerShot shot : engine.getPlayerShots()) {
            paint.setColor(AVATAR);
            canvas.drawCircle(shot.getX(), shot.y, RunnerEngine.PLAYER_SHOT_RADIUS, paint);
            paint.setColor(BIKE_ACCENT);
            canvas.drawCircle(shot.getX() - 1f, shot.y - 1f, 1.5f, paint);
        }
        drawStarBursts(canvas, cameraX, right);
        drawEnemyBursts(canvas, cameraX, right);
        if (!engine.isInWater()) drawMotorcycleRider(canvas);
        canvas.restore();
    }

    private void drawSandGrains(Canvas canvas, float left, float right) {
        long seed = engine.getSeed();
        paint.setStyle(Paint.Style.FILL);
        for (int column = (int) Math.floor(left / 13f);
                column <= (int) Math.ceil(right / 13f); column++) {
            float x = column * 13f + 3f * SceneryLayout.random01(seed, column,
                    0x4BD37C9105AA6E28L);
            float surface = engine.surfaceYAt(x);
            if (Float.isNaN(surface)) continue;
            for (int row = (int) Math.ceil((surface + 12f) / 14f);
                    row * 14f < RunnerEngine.WORLD_HEIGHT; row++) {
                int id = column * 1049 + row;
                float y = row * 14f + 4f * SceneryLayout.random01(seed, id,
                        0x1CFB470D9A552E63L);
                if (y <= surface + 12f) continue;
                paint.setColor(SceneryLayout.random01(seed, id,
                        0x7316E9A4B50DC728L) < .5f
                        ? SAND_GRAIN_LIGHT : SAND_GRAIN_DARK);
                canvas.drawCircle(x, y, .7f + SceneryLayout.random01(seed, id,
                        0x6932D5A0EB1478CFL) * 1.1f, paint);
            }
        }
    }

    private void drawPlatformGrains(Canvas canvas, RunnerEngine.Platform platform,
                                    float left, float right) {
        long seed = engine.getSeed() ^ ((long) Float.floatToIntBits(platform.x) << 24);
        for (int column = (int) Math.ceil(Math.max(left, platform.x) / 12f);
                column * 12f < Math.min(right, platform.end()); column++) {
            float x = column * 12f;
            float y = platform.top + 3f + SceneryLayout.random01(seed, column,
                    0x9521FCD7E4B386A0L) * 4f;
            paint.setColor(SAND_GRAIN_DARK);
            canvas.drawCircle(x, y, .7f, paint);
        }
    }

    private void drawGroundContour(Canvas canvas, float left, float right) {
        // Extra off-screen space keeps the seed-based bumps stable at the camera edges.
        float from = Math.max(0f, left - 24f);
        float to = right + 24f;
        float cursor = from;
        long seed = engine.getSeed();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(GROUND_LINE_WIDTH);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(GROUND_LINE);
        for (RunnerEngine.Hazard hazard : engine.getHazards()) {
            if (hazard.end() <= from) continue;
            if (hazard.x >= to) break;
            if (hazard.x > cursor) {
                drawGroundSegment(canvas, cursor, RunnerEngine.GROUND_Y,
                        Math.min(hazard.x, to), RunnerEngine.GROUND_Y, from, to, seed);
            }
            if (hazard.type != RunnerEngine.HazardType.HOLE) {
                float flatY = RunnerEngine.GROUND_Y
                        + (hazard.type == RunnerEngine.HazardType.WALL
                        ? -hazard.height : hazard.height);
                if (hazard.entranceDegrees == 90) {
                    drawGroundFace(canvas, hazard.x, RunnerEngine.GROUND_Y,
                            flatY, hazard.type == RunnerEngine.HazardType.WALL ? 1f : -1f,
                            from, to, seed);
                } else {
                    drawGroundSegment(canvas, hazard.x, RunnerEngine.GROUND_Y,
                            hazard.flatStart, flatY, from, to, seed);
                }
                drawGroundSegment(canvas, hazard.flatStart, flatY,
                        hazard.flatEnd, flatY, from, to, seed);
                if (hazard.exitDegrees == 90) {
                    drawGroundFace(canvas, hazard.end(), flatY,
                            RunnerEngine.GROUND_Y,
                            hazard.type == RunnerEngine.HazardType.WALL ? -1f : 1f,
                            from, to, seed);
                } else {
                    drawGroundSegment(canvas, hazard.flatEnd, flatY,
                            hazard.end(), RunnerEngine.GROUND_Y, from, to, seed);
                }
            }
            cursor = Math.max(cursor, hazard.end());
        }
        if (cursor < to) {
            drawGroundSegment(canvas, cursor, RunnerEngine.GROUND_Y,
                    to, RunnerEngine.GROUND_Y, from, to, seed);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawGroundSegment(Canvas canvas, float startX, float startY,
                                   float endX, float endY, float left, float right,
                                   long seed) {
        if (endX <= left || startX >= right || endX <= startX) return;
        float length = (float) Math.hypot(endX - startX, endY - startY);
        float visibleStart = Math.max(startX, left);
        float visibleEnd = Math.min(endX, right);
        float first = length * (visibleStart - startX) / (endX - startX);
        float last = length * (visibleEnd - startX) / (endX - startX);
        groundPath.rewind();
        groundPath.moveTo(visibleStart, groundLineY(startY, endY, first, length,
                startX, seed));
        for (int sample = (int) Math.floor(first / 8f) + 1;
                sample * 8f < last; sample++) {
            float along = sample * 8f;
            float x = startX + (endX - startX) * along / length;
            groundPath.lineTo(x, groundLineY(startY, endY, along, length,
                    startX, seed));
        }
        groundPath.lineTo(visibleEnd, groundLineY(startY, endY, last, length,
                startX, seed));
        canvas.drawPath(groundPath, paint);
    }

    private float groundLineY(float startY, float endY, float along,
                              float length, float startX, long seed) {
        float taper = Math.min(1f, Math.min(along, length - along) / 14f);
        float bump = SceneryLayout.groundBump(seed, startX + along) * taper;
        return startY + (endY - startY) * along / length
                + GROUND_LINE_WIDTH / 2f - bump;
    }

    private void drawGroundFace(Canvas canvas, float x, float startY, float endY,
                                float solidSide, float left, float right, long seed) {
        if (x < left || x > right) return;
        float height = Math.abs(endY - startY);
        float direction = Math.signum(endY - startY);
        long faceSeed = seed ^ ((long) Float.floatToIntBits(x) << 32);
        groundPath.rewind();
        groundPath.moveTo(x + solidSide * GROUND_LINE_WIDTH / 2f, startY);
        for (float along = 8f; along < height; along += 8f) {
            float taper = Math.min(1f, Math.min(along, height - along) / 14f);
            float bump = SceneryLayout.groundBump(faceSeed, startY + direction * along);
            groundPath.lineTo(x + solidSide * (GROUND_LINE_WIDTH / 2f - bump * taper),
                    startY + direction * along);
        }
        groundPath.lineTo(x + solidSide * GROUND_LINE_WIDTH / 2f, endY);
        canvas.drawPath(groundPath, paint);
    }

    private int starColor(RunnerEngine.StarColor color) {
        switch (color) {
            case RED: return Color.rgb(255, 103, 126);
            case ORANGE: return Color.rgb(255, 176, 55);
            case YELLOW: return Color.rgb(255, 241, 69);
            case GREEN: return Color.rgb(100, 255, 129);
            default: return Color.rgb(90, 194, 255);
        }
    }

    private void drawStars(Canvas canvas, float left, float right) {
        paint.setStyle(Paint.Style.FILL);
        for (RunnerEngine.Star star : engine.getStars()) {
            if (star.x + RunnerEngine.STAR_RADIUS < left) continue;
            if (star.x - RunnerEngine.STAR_RADIUS > right) break;
            paint.setColor(Color.argb(125, 255, 255, 255));
            canvas.drawCircle(star.x, star.y, RunnerEngine.STAR_RADIUS + 3f, paint);
            starPath.rewind();
            for (int point = 0; point < 10; point++) {
                double angle = -Math.PI / 2d + point * Math.PI / 5d;
                float radius = point % 2 == 0 ? RunnerEngine.STAR_RADIUS : 5f;
                float x = star.x + radius * (float) Math.cos(angle);
                float y = star.y + radius * (float) Math.sin(angle);
                if (point == 0) starPath.moveTo(x, y);
                else starPath.lineTo(x, y);
            }
            starPath.close();
            paint.setColor(starColor(star.color));
            canvas.drawPath(starPath, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(star.x, star.y, 2f, paint);
        }
    }

    private void drawAbilityIcons(Canvas canvas, float left, float right) {
        for (RunnerEngine.AbilityIcon icon : engine.getAbilityIcons()) {
            if (icon.x + RunnerEngine.ABILITY_RADIUS < left
                    || icon.x - RunnerEngine.ABILITY_RADIUS > right) continue;
            drawAbilityGlyph(canvas, icon.x, icon.y, RunnerEngine.ABILITY_RADIUS,
                    icon.type, 255);
        }
    }

    private void drawAbilityGlyph(Canvas canvas, float x, float y, float radius,
                                  RunnerEngine.AbilityType type, int alpha) {
        int color = type == RunnerEngine.AbilityType.DOUBLE_JUMP ? JUMP_ICON : SHOOT_ICON;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha * 200 / 255, 13, 45, 105));
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(Color.argb(alpha, Color.red(color), Color.green(color),
                Color.blue(color)));
        canvas.drawCircle(x, y, radius - 1.5f, paint);
        paint.setStrokeWidth(3.3f);
        if (type == RunnerEngine.AbilityType.DOUBLE_JUMP) {
            for (int i = 0; i < 2; i++) {
                float row = y + (i == 0 ? 5f : -3f) * radius / 16.5f;
                canvas.drawLine(x - radius * .45f, row + radius * .12f,
                        x, row - radius * .23f, paint);
                canvas.drawLine(x, row - radius * .23f,
                        x + radius * .45f, row + radius * .12f, paint);
            }
        } else {
            canvas.drawCircle(x - radius * .12f, y, radius * .28f, paint);
            canvas.drawLine(x + radius * .2f, y, x + radius * .63f, y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawStarBursts(Canvas canvas, float left, float right) {
        paint.setStyle(Paint.Style.FILL);
        for (RunnerEngine.StarBurst burst : engine.getStarBursts()) {
            if (burst.x < left - 40f || burst.x > right + 40f) continue;
            drawFirework(canvas, burst.x, burst.y, starColor(burst.color),
                    burst.getProgress());
        }
    }

    private void drawEnemyBursts(Canvas canvas, float left, float right) {
        paint.setStyle(Paint.Style.FILL);
        for (RunnerEngine.EnemyBurst burst : engine.getEnemyBursts()) {
            if (burst.x < left - 40f || burst.x > right + 40f) continue;
            drawFirework(canvas, burst.x, burst.y, enemyColor(burst.type),
                    burst.getProgress());
        }
    }

    private void drawFirework(Canvas canvas, float x, float y, int color, float progress) {
        paint.setColor(Color.argb(Math.round(255f * (1f - progress)),
                Color.red(color), Color.green(color), Color.blue(color)));
        for (int spark = 0; spark < 8; spark++) {
            double angle = spark * Math.PI / 4d;
            float radius = 5f + progress * 28f;
            canvas.drawCircle(x + radius * (float) Math.cos(angle),
                    y + radius * (float) Math.sin(angle),
                    Math.max(1f, 3.5f * (1f - progress)), paint);
        }
    }

    private void drawWater(Canvas canvas, float left, float right) {
        for (RunnerEngine.Hazard hazard : engine.getHazards()) {
            if (hazard.x >= right) break;
            if (hazard.type != RunnerEngine.HazardType.HOLE || hazard.end() <= left) continue;
            waterPath.rewind();
            waterPath.moveTo(hazard.x, RunnerEngine.WORLD_HEIGHT);
            for (float x = hazard.x; x < hazard.end(); x += 4f) {
                waterPath.lineTo(x, waterY(x));
            }
            waterPath.lineTo(hazard.end(), waterY(hazard.end()));
            waterPath.lineTo(hazard.end(), RunnerEngine.WORLD_HEIGHT);
            waterPath.close();
            paint.setColor(WATER);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(waterPath, paint);

            waterPath.rewind();
            waterPath.moveTo(hazard.x, waterY(hazard.x));
            for (float x = hazard.x + 4f; x < hazard.end(); x += 4f) {
                waterPath.lineTo(x, waterY(x));
            }
            waterPath.lineTo(hazard.end(), waterY(hazard.end()));
            paint.setColor(WATER_EDGE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            canvas.drawPath(waterPath, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private float waterY(float x) {
        return RunnerEngine.WATER_SURFACE_Y
                + 2f * (float) Math.sin(x * .115f + waveSeconds * 2.3f);
    }

    private void drawGrass(Canvas canvas, float left, float right) {
        if (grassPattern == null || grassPattern.seed != engine.getSeed()) {
            grassPattern = new SceneryLayout.GrassPattern(engine.getSeed());
        }
        paint.setStyle(Paint.Style.FILL);
        for (SceneryLayout.GrassSegment segment :
                SceneryLayout.visibleGrass(grassPattern, engine.getHazards(), left, right)) {
            // Each patch clips to supported ground and slopes no steeper than 45°.
            grassPath.rewind();
            grassPath.moveTo(segment.fromX, segment.fromY);
            grassPath.lineTo(segment.toX, segment.toY);
            grassPath.lineTo(segment.toX, segment.toY - 2f);
            grassPath.lineTo(segment.fromX, segment.fromY - 2f);
            grassPath.close();
            paint.setColor(GRASS_BASE);
            canvas.drawPath(grassPath, paint);

            for (float x = (float) Math.ceil(segment.fromX / 7f) * 7f;
                    x < segment.toX; x += 7f) {
                int blade = (int) (x / 7f) + segment.patchIndex * 23;
                float width = 2.5f + 2.5f * SceneryLayout.random01(
                        grassPattern.seed, blade, 0xC6E93027BD86154AL);
                float bladeLeft = Math.max(segment.fromX, x - width);
                float bladeRight = Math.min(segment.toX, x + width);
                if (bladeRight <= bladeLeft) continue;
                float height = SceneryLayout.GRASS_HEIGHT * (.3f + .7f
                        * SceneryLayout.random01(grassPattern.seed, blade,
                        0xE284F6C53BD917A0L));
                float lean = (SceneryLayout.random01(grassPattern.seed, blade,
                        0x58F137CB704ADE26L) - .5f) * 3f;
                grassPath.rewind();
                grassPath.moveTo(bladeLeft, segment.surfaceYAt(bladeLeft) - 2f);
                grassPath.lineTo(Math.max(bladeLeft, Math.min(bladeRight, x + lean)),
                        segment.surfaceYAt(x) - height);
                grassPath.lineTo(bladeRight, segment.surfaceYAt(bladeRight) - 2f);
                grassPath.close();
                paint.setColor(SceneryLayout.random01(grassPattern.seed, blade,
                        0x42A73BD861C9E0F5L) < .6f ? GRASS_TIPS : GRASS_BASE);
                canvas.drawPath(grassPath, paint);
            }
        }
    }

    private void drawMotorcycleRider(Canvas canvas) {
        if (!engine.isLifeLossBlinkVisible()) return;
        float pitch = engine.getPlayerPitchDegrees();
        double radians = Math.toRadians(pitch);
        float footX = engine.getPlayerX() + RunnerEngine.PLAYER_WIDTH / 2f;
        float footY = engine.getPlayerY() + RunnerEngine.PLAYER_HEIGHT;
        canvas.save();
        // Preserve the old visual footprint and two-pixel clearance; the
        // collision box, jump, and slope pitch still come from the engine.
        canvas.translate(footX - HOVER_GAP * (float) Math.sin(radians),
                footY - HOVER_GAP * (float) Math.cos(radians));
        canvas.rotate(-pitch);
        boolean blink = engine.isDamageBlinkLight();
        int silhouette = blink ? DAMAGE_BLINK_BLUE : AVATAR;
        int detail = blink ? DAMAGE_BLINK_BLUE : BIKE_ACCENT;

        // Wheels and triangular motorcycle chassis fill the former shuttle width.
        paint.setColor(silhouette);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(3.6f);
        canvas.drawCircle(-20f, -8f, 6f, paint);
        canvas.drawCircle(20f, -8f, 6f, paint);
        bikePath.rewind();
        bikePath.moveTo(-20f, -8f);
        bikePath.lineTo(-9f, -18f);
        bikePath.lineTo(5f, -9f);
        bikePath.lineTo(20f, -8f);
        bikePath.lineTo(10f, -20f);
        bikePath.lineTo(-9f, -18f);
        bikePath.lineTo(-20f, -8f);
        canvas.drawPath(bikePath, paint);
        canvas.drawLine(10f, -20f, 20f, -8f, paint);
        canvas.drawLine(11f, -21f, 23f, -24f, paint);
        canvas.drawLine(21f, -25f, 27f, -25f, paint);
        canvas.drawLine(-9f, -19f, -15f, -22f, paint);

        // A forward-leaning rider: helmet, jacket, arms, and bent leg.
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(-15f, -23f, -3f, -19f, 2f, 2f, paint);
        bikePath.rewind();
        bikePath.moveTo(-8f, -29f);
        bikePath.lineTo(-2f, -34f);
        bikePath.lineTo(5f, -30f);
        bikePath.lineTo(1f, -21f);
        bikePath.lineTo(-8f, -23f);
        bikePath.close();
        canvas.drawPath(bikePath, paint);
        canvas.drawCircle(-1f, -39f, 5f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.4f);
        canvas.drawLine(2f, -30f, 11f, -26f, paint);
        canvas.drawLine(11f, -26f, 22f, -25f, paint);
        canvas.drawLine(-6f, -23f, 4f, -16f, paint);
        canvas.drawLine(4f, -16f, -4f, -10f, paint);

        paint.setColor(detail);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(-20f, -8f, 2f, paint);
        canvas.drawCircle(20f, -8f, 2f, paint);
        canvas.drawRoundRect(-2f, -42f, 3f, -39f, 1f, 1f, paint);
        canvas.drawRoundRect(7f, -20f, 14f, -17f, 1.5f, 1.5f, paint);
        canvas.restore();
    }

    private void drawStickFigure(Canvas canvas, float x, float y, int color,
                                 boolean blinkHead, float stride, boolean jumping,
                                 boolean holdingGun) {
        canvas.save();
        canvas.translate(x, y);
        float swing = jumping ? 0f : stride * 6.5f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(3.6f);
        paint.setColor(color);

        // Two bent legs stay inside the existing collision bounds.
        if (jumping) {
            canvas.drawLine(16f, 28f, 10f, 35f, paint);
            canvas.drawLine(10f, 35f, 5f, 42f, paint);
            canvas.drawLine(16f, 28f, 23f, 33f, paint);
            canvas.drawLine(23f, 33f, 28f, 42f, paint);
        } else {
            canvas.drawLine(16f, 28f, 12f + swing * .45f, 36f, paint);
            canvas.drawLine(12f + swing * .45f, 36f,
                    8f + swing, 44f - Math.max(0f, swing) * .25f, paint);
            canvas.drawLine(16f, 28f, 20f - swing * .45f, 36f, paint);
            canvas.drawLine(20f - swing * .45f, 36f,
                    24f - swing, 44f - Math.max(0f, -swing) * .25f, paint);
        }
        canvas.drawLine(16f, 14f, 16f, 28f, paint);

        if (holdingGun) {
            canvas.drawLine(16f, 18f, 10f, 20f, paint);
            canvas.drawLine(10f, 20f, 7f, RunnerEngine.MUZZLE_Y, paint);
            canvas.drawLine(16f, 18f, 23f, 22f, paint);
            canvas.drawLine(23f, 22f, 25f, 27f - swing * .25f, paint);
            paint.setColor(ENEMY_RED_WEAPON);
            paint.setStrokeCap(Paint.Cap.BUTT);
            canvas.drawLine(RunnerEngine.MUZZLE_X, RunnerEngine.MUZZLE_Y,
                    11f, RunnerEngine.MUZZLE_Y, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(7f, 20f, 14f, 26f, 1.5f, 1.5f, paint);
            canvas.drawRect(8f, 25f, 10.5f, 29f, paint);
        } else if (jumping) {
            canvas.drawLine(16f, 18f, 8f, 17f, paint);
            canvas.drawLine(8f, 17f, 5f, 21f, paint);
            canvas.drawLine(16f, 18f, 24f, 17f, paint);
            canvas.drawLine(24f, 17f, 27f, 21f, paint);
        } else {
            canvas.drawLine(16f, 18f, 10f - swing * .3f, 21f, paint);
            canvas.drawLine(10f - swing * .3f, 21f, 8f - swing, 26f, paint);
            canvas.drawLine(16f, 18f, 22f + swing * .3f, 21f, paint);
            canvas.drawLine(22f + swing * .3f, 21f, 24f + swing, 26f, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blinkHead ? DAMAGE_BLINK_BLUE : color);
        canvas.drawCircle(16f, 7.5f, 5.5f, paint);
        if (blinkHead) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            canvas.drawCircle(16f, 7.5f, 5.5f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
        canvas.restore();
    }

    private void drawTurret(Canvas canvas, float x, float y) {
        canvas.save();
        canvas.translate(x, y);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ENEMY_ORANGE);
        // The barrel tip is exactly where the projectile's near edge starts.
        canvas.drawRoundRect(RunnerEngine.MUZZLE_X, RunnerEngine.MUZZLE_Y - 3f,
                17f, RunnerEngine.MUZZLE_Y + 3f, 2f, 2f, paint);
        turretPath.rewind();
        turretPath.moveTo(5f, 29f);
        turretPath.lineTo(8f, 12f);
        turretPath.quadTo(15f, 3f, 23f, 7f);
        turretPath.lineTo(29f, 15f);
        turretPath.lineTo(29f, 30f);
        turretPath.close();
        canvas.drawPath(turretPath, paint);
        canvas.drawRoundRect(4f, 28f, 29f, 41f, 3f, 3f, paint);
        canvas.drawRoundRect(1f, 38f, 31f, 45f, 3f, 3f, paint);
        paint.setColor(ENEMY_ORANGE_DETAIL);
        canvas.drawCircle(21f, 16f, 2.5f, paint);
        canvas.drawRoundRect(5f, 40f, 27f, 43f, 1f, 1f, paint);
        canvas.restore();
    }

    private int enemyColor(RunnerEngine.EnemyType type) {
        return type == RunnerEngine.EnemyType.MOVING ? ENEMY_RED : ENEMY_ORANGE;
    }

    private void drawBonuses(Canvas canvas, float cameraX) {
        canvas.save();
        canvas.translate(-cameraX, 0f);
        for (RunnerEngine.BonusPopup popup : engine.getBonusPopups()) {
            float progress = popup.getProgress();
            int color = popup.starColor == null ? enemyColor(popup.type)
                    : starColor(popup.starColor);
            int fadedColor = Color.argb(Math.round(255f * (1f - progress)),
                    Color.red(color), Color.green(color), Color.blue(color));
            text(canvas, "+" + popup.amount, popup.x, popup.y - 14f - 40f * progress,
                    25f, fadedColor, Paint.Align.CENTER);
        }
        canvas.restore();
    }

    private void drawTerrainShape(Canvas canvas, RunnerEngine.Hazard hazard) {
        float flatY = hazard.type == RunnerEngine.HazardType.WALL
                ? RunnerEngine.GROUND_Y - hazard.height
                : RunnerEngine.GROUND_Y + hazard.height;
        terrainPath.rewind();
        terrainPath.moveTo(hazard.x, RunnerEngine.GROUND_Y);
        terrainPath.lineTo(hazard.flatStart, flatY);
        terrainPath.lineTo(hazard.flatEnd, flatY);
        terrainPath.lineTo(hazard.end(), RunnerEngine.GROUND_Y);
        terrainPath.lineTo(hazard.end(), RunnerEngine.WORLD_HEIGHT);
        terrainPath.lineTo(hazard.x, RunnerEngine.WORLD_HEIGHT);
        terrainPath.close();
        canvas.drawPath(terrainPath, paint);
    }

    private void drawHud(Canvas canvas) {
        if (engine.getMode() == RunnerEngine.Mode.TITLE) return;
        float x = 24f;
        x = hudItem(canvas, "Score " + engine.getScore(), x);
        x = hudItem(canvas, "Best " + engine.getBestScore(), x);
        hudItem(canvas, String.format(Locale.US, "Speed %.1f m/s",
                engine.getSpeedMetersPerSecond()), x);
        drawLives(canvas, logicalWidth - 133f, 16f, 22f, 5f, -1f);
        drawHealth(canvas);
        if (engine.hasDoubleJump()) {
            drawAbilityGlyph(canvas, 45f, RunnerEngine.WORLD_HEIGHT - 43f, 21f,
                    RunnerEngine.AbilityType.DOUBLE_JUMP, 255);
        }
        if (engine.hasShooting()) {
            drawAbilityGlyph(canvas, engine.hasDoubleJump() ? 96f : 45f,
                    RunnerEngine.WORLD_HEIGHT - 43f, 21f,
                    RunnerEngine.AbilityType.SHOOTING, 255);
            drawShootButton(canvas);
        }
        if (engine.getMode() == RunnerEngine.Mode.RUNNING
                || engine.getMode() == RunnerEngine.Mode.COUNTDOWN
                || engine.getMode() == RunnerEngine.Mode.LIFE_LOSS) {
            drawPauseButton(canvas);
        }
        if (engine.getMode() == RunnerEngine.Mode.RUNNING
                && (engine.shouldShowWaterHint()
                || (!engine.isInWater() && !engine.hasCompletedFirstJump()))) {
            text(canvas, engine.shouldShowWaterHint() ? "Tap repeatedly to stay afloat"
                            : "Tap anywhere to jump", logicalWidth / 2f, 122f,
                    18f, INK, Paint.Align.CENTER);
        }
        if (engine.getMode() == RunnerEngine.Mode.RUNNING
                && engine.getDoubleJumpHintSeconds() > 0f) {
            text(canvas, "Tap mid-air to double jump", logicalWidth / 2f, 155f,
                    20f, INK, Paint.Align.CENTER);
        }
    }

    private void drawShootButton(Canvas canvas) {
        float cx = logicalWidth - 58f;
        float cy = RunnerEngine.WORLD_HEIGHT - 53f;
        float radius = shootPressSeconds > 0f ? 28f : 33f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(shootPressSeconds > 0f ? 190 : 125, 35, 57, 126));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx - 7f, cy, 5f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        canvas.drawLine(cx - 1f, cy, cx + 14f, cy, paint);
        canvas.drawLine(cx + 8f, cy - 7f, cx + 15f, cy, paint);
        canvas.drawLine(cx + 8f, cy + 7f, cx + 15f, cy, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHealth(Canvas canvas) {
        float left = logicalWidth - 133f - 9f * 6f;
        for (int i = 0; i < RunnerEngine.MAX_HEALTH; i++) {
            float ratio = i / (float) (RunnerEngine.MAX_HEALTH - 1);
            int red = Math.round(230f * (1f - ratio) + 45f * ratio);
            int green = Math.round(48f * (1f - ratio) + 181f * ratio);
            int blue = Math.round(53f * (1f - ratio) + 68f * ratio);
            paint.setColor(i < engine.getHealth()
                    ? Color.rgb(red, green, blue) : EMPTY_HEALTH);
            canvas.drawCircle(left + i * 12f, 53f, 4.5f, paint);
        }
    }

    private void drawLives(Canvas canvas, float centerX, float top, float size,
                           float gap, float fadeProgress) {
        float left = centerX - (RunnerEngine.MAX_LIVES * size
                + (RunnerEngine.MAX_LIVES - 1) * gap) / 2f;
        int lives = engine.getLives();
        for (int i = 0; i < RunnerEngine.MAX_LIVES; i++) {
            int color = i < lives ? LIFE_RED : LIFE_GRAY;
            if (i == lives && fadeProgress >= 0f) {
                float progress = Math.min(1f, fadeProgress);
                color = Color.rgb(
                        Math.round(Color.red(LIFE_RED) +
                                (Color.red(LIFE_GRAY) - Color.red(LIFE_RED)) * progress),
                        Math.round(Color.green(LIFE_RED) +
                                (Color.green(LIFE_GRAY) - Color.green(LIFE_RED)) * progress),
                        Math.round(Color.blue(LIFE_RED) +
                                (Color.blue(LIFE_GRAY) - Color.blue(LIFE_RED)) * progress));
            }
            drawHeart(canvas, left + i * (size + gap), top, size, color);
        }
    }

    private void drawHeart(Canvas canvas, float left, float top, float size, int color) {
        heartPath.rewind();
        heartPath.moveTo(left + size * .5f, top + size * .2f);
        heartPath.cubicTo(left + size * .37f, top - size * .03f,
                left + size * .04f, top + size * .04f,
                left + size * .04f, top + size * .32f);
        heartPath.cubicTo(left + size * .04f, top + size * .58f,
                left + size * .30f, top + size * .82f,
                left + size * .5f, top + size * .98f);
        heartPath.cubicTo(left + size * .70f, top + size * .82f,
                left + size * .96f, top + size * .58f,
                left + size * .96f, top + size * .32f);
        heartPath.cubicTo(left + size * .96f, top + size * .04f,
                left + size * .63f, top - size * .03f,
                left + size * .5f, top + size * .2f);
        heartPath.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(heartPath, paint);
    }

    private float hudItem(Canvas canvas, String value, float x) {
        text(canvas, value, x, 38f, 18f, INK, Paint.Align.LEFT);
        return x + paint.measureText(value) + 18f;
    }

    private void drawPauseButton(Canvas canvas) {
        float left = logicalWidth - 57f;
        paint.setColor(AVATAR);
        canvas.drawRoundRect(left, 8f, left + 49f, 57f, 13f, 13f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(left + 15f, 19f, left + 21f, 46f, 2f, 2f, paint);
        canvas.drawRoundRect(left + 28f, 19f, left + 34f, 46f, 2f, 2f, paint);
    }

    private void drawMenu(Canvas canvas) {
        RunnerEngine.Mode mode = engine.getMode();
        if (mode == RunnerEngine.Mode.RUNNING || mode == RunnerEngine.Mode.LIFE_LOSS) return;
        float midX = logicalWidth / 2f;
        float midY = RunnerEngine.WORLD_HEIGHT / 2f;
        if (mode == RunnerEngine.Mode.PAUSED) {
            canvas.drawColor(0x690F2D61);
            text(canvas, "Paused", midX, midY - 115f, 44f,
                    Color.WHITE, Paint.Align.CENTER);
            button(canvas, "Continue", midX, midY - 20f);
            button(canvas, "Restart", midX, midY + 75f);
        } else if (mode == RunnerEngine.Mode.GAME_OVER) {
            canvas.drawColor(0x690F2D61);
            text(canvas, "Game over", midX, midY - 100f, 44f,
                    Color.WHITE, Paint.Align.CENTER);
            text(canvas, "Score " + engine.getScore() + "  |  Best score "
                    + engine.getBestScore(), midX, midY - 45f, 20f,
                    Color.WHITE, Paint.Align.CENTER);
            button(canvas, "Restart", midX, midY);
        } else if (mode == RunnerEngine.Mode.TITLE) {
            text(canvas, "SKYBOUND RUNNER", midX, midY - 100f,
                    42f, INK, Paint.Align.CENTER);
            button(canvas, "Start", midX, midY);
        } else {
            int number = Math.max(1, (int) Math.ceil(engine.getCountdownSeconds()));
            if (engine.getLives() < RunnerEngine.MAX_LIVES) {
                float fadeProgress = Math.max(0f,
                        (3f - engine.getCountdownSeconds()) / .75f);
                drawLives(canvas, midX, midY - 125f, 38f, 10f, fadeProgress);
            } else {
                text(canvas, "Get ready", midX, midY - 85f, 27f,
                        INK, Paint.Align.CENTER);
            }
            text(canvas, String.valueOf(number), midX, midY + 29f, 90f,
                    INK, Paint.Align.CENTER);
        }
    }

    private void button(Canvas canvas, String label, float x, float y) {
        paint.setColor(AVATAR);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(x - 110f, y - 35f, x + 110f, y + 35f,
                19f, 19f, paint);
        text(canvas, label, x, y + 10f, 28f, Color.WHITE, Paint.Align.CENTER);
    }

    private void text(Canvas canvas, String value, float x, float baseline,
                      float size, int color, Paint.Align align) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        canvas.drawText(value, x, baseline, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
        float scale = getHeight() / RunnerEngine.WORLD_HEIGHT;
        if (scale <= 0f) return true;
        float x = event.getX() / scale;
        float y = event.getY() / scale;
        RunnerEngine.Mode mode = engine.getMode();
        if ((mode == RunnerEngine.Mode.RUNNING || mode == RunnerEngine.Mode.COUNTDOWN
                || mode == RunnerEngine.Mode.LIFE_LOSS)
                && x >= logicalWidth - 62f && y >= 0f && y <= 64f) {
            engine.pause();
            lastFrameNanos = 0L;
        } else if (mode == RunnerEngine.Mode.RUNNING && engine.hasShooting()
                && Math.abs(x - (logicalWidth - 58f)) <= 39f
                && Math.abs(y - (RunnerEngine.WORLD_HEIGHT - 53f)) <= 39f) {
            // During a water crossing every tap still helps the motorcycle rise.
            if (engine.isInWater()) engine.jump();
            if (engine.shoot()) shootPressSeconds = .14f;
        } else if (mode == RunnerEngine.Mode.RUNNING) {
            engine.jump();
        } else if (mode == RunnerEngine.Mode.PAUSED
                && Math.abs(x - logicalWidth / 2f) <= 110f
                && Math.abs(y - (RunnerEngine.WORLD_HEIGHT / 2f + 75f)) <= 35f) {
            engine.startNewGame(System.nanoTime());
            lastFrameNanos = 0L;
        } else if (mode == RunnerEngine.Mode.TITLE || mode == RunnerEngine.Mode.PAUSED
                || mode == RunnerEngine.Mode.GAME_OVER) {
            if (Math.abs(x - logicalWidth / 2f) <= 110f
                    && Math.abs(y - RunnerEngine.WORLD_HEIGHT / 2f
                    + (mode == RunnerEngine.Mode.PAUSED ? 20f : 0f)) <= 35f) {
                if (mode == RunnerEngine.Mode.TITLE || mode == RunnerEngine.Mode.GAME_OVER) {
                    engine.startNewGame(System.nanoTime());
                } else {
                    engine.continueGame();
                }
                lastFrameNanos = 0L;
            }
        }
        invalidate();
        return true;
    }

    public void pauseForBackground() {
        engine.pauseForBackground();
        lastFrameNanos = 0L;
        invalidate();
    }

    public void onForeground() {
        lastFrameNanos = 0L;
        invalidate();
    }

    public void save(Bundle out) {
        out.putLong("seed", engine.getSeed());
        out.putInt("lives", engine.getLives());
        out.putFloat("x", engine.getPlayerX());
        out.putFloat("y", engine.getPlayerY());
        out.putFloat("vy", engine.getVelocityY());
        out.putFloat("countdown", engine.getCountdownSeconds());
        out.putDouble("elapsedRun", engine.getElapsedRunSeconds());
        out.putFloat("slopeSpeed", engine.getTerrainSpeedMultiplier());
        out.putBoolean("airborneMomentum", engine.hasAirborneMomentum());
        out.putFloat("airborneSpeed", engine.getAirborneSpeed());
        out.putFloatArray("waterState", engine.getWaterState());
        out.putInt("waterEncounters", engine.getWaterEncounters());
        out.putBoolean("waitingForJump", engine.isWaitingForJump());
        out.putInt("health", engine.getHealth());
        out.putFloat("damageRecovery", engine.getDamageRecoverySeconds());
        out.putFloat("lifeLossRemaining", engine.getLifeLossRemainingSeconds());
        out.putFloatArray("defeatedAntagonists", engine.getDefeatedAntagonists());
        out.putFloat("farthestX", engine.getFarthestX());
        out.putInt("bonusPoints", engine.getBonusPoints());
        out.putFloatArray("collectedStars", engine.getCollectedStars());
        out.putFloatArray("starBursts", engine.getStarBurstState());
        out.putFloatArray("enemyBursts", engine.getEnemyBurstState());
        out.putBoolean("firstJumpCompleted", engine.hasCompletedFirstJump());
        out.putBoolean("jumpInProgress", engine.isControlledJumpInProgress());
        out.putFloat("visibleWidth", engine.getVisibleWorldWidth());
        out.putFloatArray("antagonistTimers", engine.getAntagonistTimers());
        out.putFloatArray("antagonistState", engine.getAntagonistState());
        out.putFloatArray("projectiles", engine.getProjectileState());
        out.putFloatArray("coloredProjectiles", engine.getColoredProjectileState());
        out.putFloatArray("bonusPopups", engine.getBonusPopupState());
        out.putFloatArray("abilities", engine.getAbilityState());
        out.putString("mode", engine.getMode().name());
        out.putString("resumeMode", engine.getResumeMode().name());
    }

    public void restore(Bundle saved) {
        try {
            engine.restore(saved.getLong("seed", 1L),
                    saved.getInt("lives", RunnerEngine.MAX_LIVES),
                    saved.getFloat("x", 140f),
                    saved.getFloat("y", RunnerEngine.GROUND_Y - RunnerEngine.PLAYER_HEIGHT),
                    saved.getFloat("vy", 0f), saved.getFloat("countdown", 3f),
                    saved.getDouble("elapsedRun", 0d),
                    saved.getFloat("slopeSpeed", 1f),
                    RunnerEngine.Mode.valueOf(saved.getString("mode", "TITLE")),
                    RunnerEngine.Mode.valueOf(saved.getString("resumeMode", "RUNNING")),
                    saved.getInt("health", RunnerEngine.MAX_HEALTH),
                    saved.getFloat("damageRecovery", 0f),
                    saved.getFloat("farthestX", saved.getFloat("x", 140f)),
                    saved.getFloat("visibleWidth", 960f),
                    saved.getFloatArray("antagonistTimers"),
                    saved.getFloatArray("projectiles"),
                    saved.getFloatArray("defeatedAntagonists"),
                    saved.getInt("bonusPoints", saved.getInt("bonusMeters", 0)),
                    saved.getBoolean("firstJumpCompleted", false),
                    saved.getBoolean("jumpInProgress", false),
                    saved.getFloatArray("antagonistState"),
                    saved.getFloatArray("coloredProjectiles"));
            engine.restoreLifeLossRemainingSeconds(saved.getFloat("lifeLossRemaining", .5f));
            engine.restoreTerrainStop(saved.getBoolean("waitingForJump", false));
            engine.restoreWaterState(saved.getFloatArray("waterState"));
            engine.restoreAirborneMomentum(saved.getBoolean("airborneMomentum", false),
                    saved.getFloat("airborneSpeed", 0f));
            engine.restoreWaterEncounters(saved.getInt("waterEncounters", 0));
            engine.restoreBonusPopups(saved.getFloatArray("bonusPopups"));
            engine.restoreCollectedStars(saved.getFloatArray("collectedStars"));
            engine.restoreStarBursts(saved.getFloatArray("starBursts"));
            engine.restoreEnemyBursts(saved.getFloatArray("enemyBursts"));
            engine.restoreAbilityState(saved.getFloatArray("abilities"));
        } catch (IllegalArgumentException ignored) {
            // Malformed/stale saved UI state simply starts at the title screen.
        }
        lastFrameNanos = 0L;
    }

    private void saveBestScore() {
        scorePreferences.edit()
                .putInt(BEST_SCORE_KEY, engine.getBestDistance())
                .putInt(BEST_POINTS_KEY, engine.getBestScore()).apply();
    }
}
