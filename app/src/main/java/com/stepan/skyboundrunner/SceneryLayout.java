package com.stepan.skyboundrunner;

import java.util.List;

/** Stateless, seeded positions for the scenery; no save data is needed. */
final class SceneryLayout {
    static final float BUILDING_STEP = 54f;
    static final float FAR_SCROLL = .22f;
    static final float NEAR_SCROLL = .39f;
    static final float PALM_SCROLL = 1f; // Roots must scroll with the terrain.
    static final float PALM_EXTENT = 70f;
    private static final float HOLE_CLEARANCE = PALM_EXTENT + 2f;
    private static final float[] PALM_HEIGHTS = {111f, 112f, 113f, 114f, 116f};
    private static final float[] PALM_TRUNKS = {5f, 6.5f, 4.5f, 7f, 5.5f};
    private static final float[] PALM_LEANS = {-7f, 8f, -3f, 10f, 0f};

    private SceneryLayout() { }

    static int firstIndex(float left, float step) {
        return (int) Math.floor(left / step) - 2;
    }

    static int lastIndex(float right, float step) {
        return (int) Math.ceil(right / step) + 2;
    }

    static float palmStep(float screenWidth) {
        return screenWidth * .54f;
    }

    static float palmX(long seed, int index, float screenWidth) {
        float jitter = (random01(seed, index, 0x2B992DDFA23249D6L) - .5f) * .24f;
        return (index + jitter) * palmStep(screenWidth);
    }

    static float groundedPalmX(long seed, int index, float screenWidth,
                               List<RunnerEngine.Hazard> hazards) {
        float x = palmX(seed, index, screenWidth);
        for (RunnerEngine.Hazard hazard : hazards) {
            if (hazard.type != RunnerEngine.HazardType.HOLE) continue;
            float left = hazard.x - HOLE_CLEARANCE;
            if (left >= x) break;
            float right = hazard.end() + HOLE_CLEARANCE;
            if (x < right) {
                // Move the whole crown clear of the hole, without dropping a
                // tree from its slot and leaving an empty screen behind.
                return x - left <= right - x ? left : right;
            }
        }
        return x;
    }

    static int palmView(long seed, int index) {
        return (int) (random01(seed, index, 0xA9BC27D4E5168073L) * 5f);
    }

    static float palmHeight(int view) { return PALM_HEIGHTS[view]; }
    static float palmTrunkWidth(int view) { return PALM_TRUNKS[view]; }
    static float palmLean(int view) { return PALM_LEANS[view]; }

    static float buildingWidth(long seed, int index, boolean near) {
        int gap = 1 + (int) (random01(seed, index,
                near ? 0x1D8E4E27C47D124FL : 0xCE49A36E93C54769L) * 7f);
        return BUILDING_STEP - gap;
    }

    static float buildingHeight(long seed, int index, boolean near) {
        float variation = random01(seed, index,
                near ? 0x72A52448D4755C19L : 0xB5D61D16492B57DDL);
        return (near ? 125f : 85f) + variation * (near ? 135f : 125f);
    }

    static float random01(long seed, int index, long salt) {
        long mixed = seed ^ salt ^ ((long) index * 0x9E3779B97F4A7C15L);
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return ((mixed ^ (mixed >>> 31)) >>> 40) / 16777216f;
    }
}
