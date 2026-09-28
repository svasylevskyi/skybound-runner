package com.stepan.skyboundrunner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** All drawing is resolution-independent and happens on the UI frame clock. */
public final class RunnerView extends View {
    private static final String BEST_SCORE_KEY = "best_run_distance";
    private static final String OLD_SCORES_KEY = "completed_attempt_distances";
    private static final int SKY = Color.rgb(132, 211, 246);
    private static final int YELLOW = Color.rgb(250, 210, 66);
    private static final int AVATAR = Color.rgb(35, 57, 126);
    private static final int DAMAGE_BLINK_BLUE = Color.rgb(218, 245, 255);
    private static final int ENEMY_RED = Color.rgb(222, 45, 54);
    private static final int ENEMY_RED_WEAPON = Color.rgb(162, 34, 45);
    private static final int ENEMY_ORANGE = Color.rgb(242, 123, 32);
    private static final int ENEMY_ORANGE_DETAIL = Color.rgb(187, 83, 19);
    private static final int LIFE_RED = Color.rgb(222, 45, 54);
    private static final int LIFE_GRAY = Color.rgb(146, 151, 159);
    private static final int EMPTY_HEALTH = Color.argb(128, 218, 223, 227);
    private static final int INK = Color.rgb(20, 44, 94);

    private final RunnerEngine engine = new RunnerEngine();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path terrainPath = new Path();
    private final Path heartPath = new Path();
    private final Path turretPath = new Path();
    private final SharedPreferences scorePreferences;
    private long lastFrameNanos;
    private float logicalWidth = 960f;

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
        setFocusable(true);
        setContentDescription("Skybound Runner game. Tap anywhere to jump while running.");
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
            int previousBest = engine.getBestDistance();
            engine.update(dt);
            if (engine.getBestDistance() > previousBest) saveBestScore();
        }
        lastFrameNanos = now;

        canvas.save();
        canvas.scale(scale, scale);
        canvas.drawColor(SKY);
        float cameraX = Math.max(0f, engine.getPlayerX() - logicalWidth * .30f);
        engine.generateAhead(cameraX + logicalWidth + 550f);
        drawWorld(canvas, cameraX);
        drawBonuses(canvas, cameraX);
        drawHud(canvas);
        drawMenu(canvas);
        canvas.restore();

        if (engine.getMode() == RunnerEngine.Mode.RUNNING
                || engine.getMode() == RunnerEngine.Mode.COUNTDOWN
                || (engine.getMode() == RunnerEngine.Mode.GAME_OVER
                && !engine.getBonusPopups().isEmpty())) {
            postInvalidateOnAnimation();
        }
    }

    private void drawWorld(Canvas canvas, float cameraX) {
        canvas.save();
        canvas.translate(-cameraX, 0f);
        float right = cameraX + logicalWidth;
        float cursor = cameraX;
        paint.setColor(YELLOW);
        paint.setStyle(Paint.Style.FILL);
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
        float stride = Math.abs(engine.getSpeed()) > 1f
                ? (float) Math.sin(engine.getPlayerX() * .055f) : 0f;
        drawStickFigure(canvas, engine.getPlayerX(), engine.getPlayerY(),
                AVATAR, engine.isDamageBlinkLight(), stride,
                Math.abs(engine.getVelocityY()) > 1f, false);
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
            int color = enemyColor(popup.type);
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
        x = hudItem(canvas, "Distance " + engine.getDistance() + " m", x);
        x = hudItem(canvas, "Best " + engine.getBestDistance() + " m", x);
        hudItem(canvas, String.format(Locale.US, "Speed %.1f m/s",
                engine.getSpeedMetersPerSecond()), x);
        drawLives(canvas, logicalWidth - 133f, 16f, 22f, 5f, -1f);
        drawHealth(canvas);
        if (engine.getMode() == RunnerEngine.Mode.RUNNING
                || engine.getMode() == RunnerEngine.Mode.COUNTDOWN) {
            drawPauseButton(canvas);
        }
        if (engine.getMode() == RunnerEngine.Mode.RUNNING
                && !engine.hasCompletedFirstJump()) {
            text(canvas, "Tap anywhere to jump", logicalWidth / 2f, 122f,
                    18f, INK, Paint.Align.CENTER);
        }
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
        if (mode == RunnerEngine.Mode.RUNNING) return;
        float midX = logicalWidth / 2f;
        float midY = RunnerEngine.WORLD_HEIGHT / 2f;
        if (mode == RunnerEngine.Mode.PAUSED) {
            canvas.drawColor(0x690F2D61);
            text(canvas, "Paused", midX, midY - 90f, 44f,
                    Color.WHITE, Paint.Align.CENTER);
            button(canvas, "Continue", midX, midY);
        } else if (mode == RunnerEngine.Mode.GAME_OVER) {
            canvas.drawColor(0x690F2D61);
            text(canvas, "Game over", midX, midY - 100f, 44f,
                    Color.WHITE, Paint.Align.CENTER);
            text(canvas, "Distance " + engine.getDistance() + " m  |  Best "
                    + engine.getBestDistance() + " m", midX, midY - 52f, 20f,
                    Color.WHITE, Paint.Align.CENTER);
            button(canvas, "Restart", midX, midY);
        } else if (mode == RunnerEngine.Mode.TITLE) {
            text(canvas, "SKYBOUND RUNNER", midX, midY - 100f,
                    42f, INK, Paint.Align.CENTER);
            button(canvas, "Start", midX, midY);
            text(canvas, "Jump onto ledges, over holes or out of dips", midX, midY + 100f,
                    21f, INK, Paint.Align.CENTER);
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
        if ((mode == RunnerEngine.Mode.RUNNING || mode == RunnerEngine.Mode.COUNTDOWN)
                && x >= logicalWidth - 62f && y >= 0f && y <= 64f) {
            engine.pause();
            lastFrameNanos = 0L;
        } else if (mode == RunnerEngine.Mode.RUNNING) {
            engine.jump();
        } else if (mode == RunnerEngine.Mode.TITLE || mode == RunnerEngine.Mode.PAUSED
                || mode == RunnerEngine.Mode.GAME_OVER) {
            if (Math.abs(x - logicalWidth / 2f) <= 110f
                    && Math.abs(y - RunnerEngine.WORLD_HEIGHT / 2f) <= 35f) {
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
        out.putBoolean("waitingForJump", engine.isWaitingForJump());
        out.putInt("health", engine.getHealth());
        out.putFloat("damageRecovery", engine.getDamageRecoverySeconds());
        out.putFloatArray("defeatedAntagonists", engine.getDefeatedAntagonists());
        out.putFloat("farthestX", engine.getFarthestX());
        out.putInt("bonusMeters", engine.getBonusMeters());
        out.putBoolean("firstJumpCompleted", engine.hasCompletedFirstJump());
        out.putBoolean("jumpInProgress", engine.isControlledJumpInProgress());
        out.putFloat("visibleWidth", engine.getVisibleWorldWidth());
        out.putFloatArray("antagonistTimers", engine.getAntagonistTimers());
        out.putFloatArray("antagonistState", engine.getAntagonistState());
        out.putFloatArray("projectiles", engine.getProjectileState());
        out.putFloatArray("coloredProjectiles", engine.getColoredProjectileState());
        out.putFloatArray("bonusPopups", engine.getBonusPopupState());
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
                    saved.getInt("bonusMeters", 0),
                    saved.getBoolean("firstJumpCompleted", false),
                    saved.getBoolean("jumpInProgress", false),
                    saved.getFloatArray("antagonistState"),
                    saved.getFloatArray("coloredProjectiles"));
            engine.restoreTerrainStop(saved.getBoolean("waitingForJump", false));
            engine.restoreBonusPopups(saved.getFloatArray("bonusPopups"));
        } catch (IllegalArgumentException ignored) {
            // Malformed/stale saved UI state simply starts at the title screen.
        }
        lastFrameNanos = 0L;
    }

    private void saveBestScore() {
        scorePreferences.edit().putInt(BEST_SCORE_KEY, engine.getBestDistance()).apply();
    }
}
