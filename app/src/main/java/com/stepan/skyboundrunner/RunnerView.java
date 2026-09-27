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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** All drawing is resolution-independent and happens on the UI frame clock. */
public final class RunnerView extends View {
    private static final String SCORES_KEY = "completed_attempt_distances";
    private static final int SKY = Color.rgb(132, 211, 246);
    private static final int YELLOW = Color.rgb(250, 210, 66);
    private static final int AVATAR = Color.rgb(35, 57, 126);
    private static final int ENEMY = Color.rgb(222, 45, 54);
    private static final int EMPTY_HEALTH = Color.rgb(131, 142, 151);
    private static final int INK = Color.rgb(20, 44, 94);

    private final RunnerEngine engine = new RunnerEngine();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path terrainPath = new Path();
    private final SharedPreferences scorePreferences;
    private long lastFrameNanos;
    private float logicalWidth = 960f;

    public RunnerView(Context context) {
        super(context);
        scorePreferences = context.getSharedPreferences("runner_scores", Context.MODE_PRIVATE);
        ArrayList<Integer> scores = new ArrayList<>();
        String storedScores = scorePreferences.getString(SCORES_KEY, "");
        if (storedScores != null && !storedScores.isEmpty()) {
            for (String value : storedScores.split(",")) {
                try {
                    scores.add(Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                    // Ignore a corrupt score instead of preventing the game from starting.
                }
            }
        }
        engine.loadAttemptDistances(scores);
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
            int completed = engine.getCompletedAttemptCount();
            engine.update(dt);
            if (engine.getCompletedAttemptCount() != completed) saveScores();
        }
        lastFrameNanos = now;

        canvas.save();
        canvas.scale(scale, scale);
        canvas.drawColor(SKY);
        float cameraX = Math.max(0f, engine.getPlayerX() - logicalWidth * .30f);
        engine.generateAhead(cameraX + logicalWidth + 550f);
        drawWorld(canvas, cameraX);
        drawHud(canvas);
        drawMenu(canvas);
        canvas.restore();

        if (engine.getMode() == RunnerEngine.Mode.RUNNING
                || engine.getMode() == RunnerEngine.Mode.COUNTDOWN) {
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
        paint.setColor(ENEMY);
        for (RunnerEngine.Antagonist antagonist : engine.getAntagonists()) {
            if (antagonist.x + RunnerEngine.PLAYER_WIDTH < cameraX) continue;
            if (antagonist.x > right) break;
            canvas.drawOval(antagonist.x, antagonist.y,
                    antagonist.x + RunnerEngine.PLAYER_WIDTH,
                    antagonist.y + RunnerEngine.PLAYER_HEIGHT, paint);
        }
        for (RunnerEngine.Projectile shot : engine.getProjectiles()) {
            canvas.drawCircle(shot.getX(), shot.getY(), RunnerEngine.PROJECTILE_RADIUS, paint);
        }
        paint.setColor(AVATAR);
        canvas.drawOval(engine.getPlayerX(), engine.getPlayerY(),
                engine.getPlayerX() + RunnerEngine.PLAYER_WIDTH,
                engine.getPlayerY() + RunnerEngine.PLAYER_HEIGHT, paint);
        canvas.restore();
    }

    private void drawTerrainShape(Canvas canvas, RunnerEngine.Hazard hazard) {
        float flatY = hazard.type == RunnerEngine.HazardType.WALL
                ? RunnerEngine.GROUND_Y - RunnerEngine.LEDGE_HEIGHT
                : RunnerEngine.GROUND_Y + RunnerEngine.DIP_DEPTH;
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
        text(canvas, "Attempt " + engine.getAttempts(), logicalWidth - 75f, 38f,
                18f, INK, Paint.Align.RIGHT);
        drawHealth(canvas);
        if (engine.getMode() == RunnerEngine.Mode.RUNNING
                || engine.getMode() == RunnerEngine.Mode.COUNTDOWN) {
            drawPauseButton(canvas);
        }
        if (engine.getMode() == RunnerEngine.Mode.RUNNING) {
            text(canvas, "Tap anywhere to jump", logicalWidth / 2f, 122f,
                    18f, INK, Paint.Align.CENTER);
        }
    }

    private void drawHealth(Canvas canvas) {
        float left = logicalWidth / 2f - 9f * 12f;
        for (int i = 0; i < RunnerEngine.MAX_HEALTH; i++) {
            float ratio = i / (float) (RunnerEngine.MAX_HEALTH - 1);
            int red = Math.round(230f * (1f - ratio) + 45f * ratio);
            int green = Math.round(48f * (1f - ratio) + 181f * ratio);
            int blue = Math.round(53f * (1f - ratio) + 68f * ratio);
            paint.setColor(i < engine.getHealth()
                    ? Color.rgb(red, green, blue) : EMPTY_HEALTH);
            canvas.drawCircle(left + i * 24f, 79f, 9f, paint);
        }
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
        } else if (mode == RunnerEngine.Mode.TITLE) {
            text(canvas, "SKYBOUND RUNNER", midX, midY - 100f,
                    42f, INK, Paint.Align.CENTER);
            button(canvas, "Start", midX, midY);
            text(canvas, "Jump onto ledges, over holes or out of dips", midX, midY + 100f,
                    21f, INK, Paint.Align.CENTER);
        } else {
            int number = Math.max(1, (int) Math.ceil(engine.getCountdownSeconds()));
            text(canvas, "Get ready", midX, midY - 85f, 27f,
                    INK, Paint.Align.CENTER);
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
        } else if (mode == RunnerEngine.Mode.TITLE || mode == RunnerEngine.Mode.PAUSED) {
            if (Math.abs(x - logicalWidth / 2f) <= 110f
                    && Math.abs(y - RunnerEngine.WORLD_HEIGHT / 2f) <= 35f) {
                if (mode == RunnerEngine.Mode.TITLE) {
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
        out.putInt("attempts", engine.getAttempts());
        out.putFloat("x", engine.getPlayerX());
        out.putFloat("y", engine.getPlayerY());
        out.putFloat("vy", engine.getVelocityY());
        out.putFloat("countdown", engine.getCountdownSeconds());
        out.putDouble("elapsedRun", engine.getElapsedRunSeconds());
        out.putFloat("slopeSpeed", engine.getTerrainSpeedMultiplier());
        out.putInt("health", engine.getHealth());
        out.putFloat("damageRecovery", engine.getDamageRecoverySeconds());
        out.putFloat("farthestX", engine.getFarthestX());
        out.putFloat("visibleWidth", engine.getVisibleWorldWidth());
        out.putFloatArray("antagonistTimers", engine.getAntagonistTimers());
        out.putFloatArray("projectiles", engine.getProjectileState());
        out.putString("mode", engine.getMode().name());
        out.putString("resumeMode", engine.getResumeMode().name());
    }

    public void restore(Bundle saved) {
        try {
            engine.restore(saved.getLong("seed", 1L), saved.getInt("attempts", 1),
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
                    saved.getFloatArray("projectiles"));
        } catch (IllegalArgumentException ignored) {
            // Malformed/stale saved UI state simply starts at the title screen.
        }
        lastFrameNanos = 0L;
    }

    private void saveScores() {
        List<Integer> scores = engine.getCompletedAttemptDistances();
        StringBuilder encoded = new StringBuilder();
        for (int score : scores) {
            if (encoded.length() > 0) encoded.append(',');
            encoded.append(score);
        }
        scorePreferences.edit().putString(SCORES_KEY, encoded.toString()).apply();
    }
}
