package com.stepan.skyboundrunner;

/** Stateless, seeded positions for the parallax scenery; no save data is needed. */
final class SceneryLayout {
    static final float BUILDING_STEP = 54f;
    static final float FAR_SCROLL = .22f;
    static final float NEAR_SCROLL = .39f;
    static final float PALM_SCROLL = .64f;

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
