package com.stepan.skyboundrunner;

import java.util.ArrayList;
import java.util.List;

/** Seeded scenery geometry; the grass cache can be rebuilt from a course seed. */
final class SceneryLayout {
    static final float BUILDING_STEP = 54f;
    static final float FAR_SCROLL = .22f;
    static final float NEAR_SCROLL = .39f;
    static final float PALM_SCROLL = 1f; // Roots must scroll with the terrain.
    static final float PALM_EXTENT = 75f;
    static final float PALM_GROUP_STEP = 98f;
    static final float GRASS_HEIGHT = RunnerEngine.PLAYER_HEIGHT / 3f;
    static final float GROUND_BUMP_MAX = RunnerEngine.PLAYER_HEIGHT / 10f;
    private static final float HOLE_CLEARANCE = PALM_EXTENT + 2f;
    private static final float[] PALM_HEIGHTS = {111f, 112f, 113f, 114f, 116f};
    private static final float[] PALM_TRUNKS = {5f, 6.5f, 4.5f, 7f, 5.5f};
    private static final float[] PALM_LEANS = {-7f, 8f, -3f, 10f, 0f};

    static final class GrassSegment {
        final float fromX;
        final float toX;
        final float fromY;
        final float toY;
        final int patchIndex;

        private GrassSegment(float fromX, float toX, float fromY, float toY,
                             int patchIndex) {
            this.fromX = fromX;
            this.toX = toX;
            this.fromY = fromY;
            this.toY = toY;
            this.patchIndex = patchIndex;
        }

        float surfaceYAt(float x) {
            return fromY + (toY - fromY) * (x - fromX) / (toX - fromX);
        }
    }

    static final class GrassPatch {
        final float fromX;
        final float toX;
        final int index;

        private GrassPatch(float fromX, float toX, int index) {
            this.fromX = fromX;
            this.toX = toX;
            this.index = index;
        }
    }

    static final class GrassPattern {
        final long seed;
        final List<GrassPatch> patches = new ArrayList<>();
        private float nextStart;

        GrassPattern(long seed) {
            this.seed = seed;
            nextStart = 10f + (int) (random01(seed, 0, 0x87E0BD8C9F16C2A3L) * 91f);
        }

        void generateAhead(float right) {
            while (nextStart < right) {
                int index = patches.size();
                float width = 10f + (int) (random01(seed, index,
                        0xC1BFA18F49D246B7L) * 141f);
                float end = nextStart + width;
                patches.add(new GrassPatch(nextStart, end, index));
                float gap = 10f + (int) (random01(seed, index,
                        0xEA3924B6D18F13C7L) * 91f);
                nextStart = end + gap;
            }
        }
    }

    static final class Palm {
        final int groupIndex;
        final int ordinal;
        final float x;
        final int view;
        final float phase;

        private Palm(int groupIndex, int ordinal, float x, int view, float phase) {
            this.groupIndex = groupIndex;
            this.ordinal = ordinal;
            this.x = x;
            this.view = view;
            this.phase = phase;
        }
    }

    private SceneryLayout() { }

    static int firstIndex(float left, float step) {
        return (int) Math.floor(left / step) - 2;
    }

    static int lastIndex(float right, float step) {
        return (int) Math.ceil(right / step) + 2;
    }

    static List<GrassSegment> visibleGrass(GrassPattern pattern,
                                           List<RunnerEngine.Hazard> hazards,
                                           float left, float right) {
        pattern.generateAhead(right);
        List<GrassSegment> eligible = new ArrayList<>();
        float cursor = left;
        for (RunnerEngine.Hazard hazard : hazards) {
            if (hazard.end() <= left) continue;
            if (hazard.x >= right) break;
            addGrass(eligible, cursor, hazard.x, RunnerEngine.GROUND_Y,
                    RunnerEngine.GROUND_Y, left, right);
            if (hazard.type != RunnerEngine.HazardType.HOLE) {
                float flatY = RunnerEngine.GROUND_Y
                        + (hazard.type == RunnerEngine.HazardType.WALL
                        ? -hazard.height : hazard.height);
                if (hazard.entranceDegrees <= 45) {
                    addGrass(eligible, hazard.x, hazard.flatStart,
                            RunnerEngine.GROUND_Y, flatY, left, right);
                }
                addGrass(eligible, hazard.flatStart, hazard.flatEnd,
                        flatY, flatY, left, right);
                if (hazard.exitDegrees <= 45) {
                    addGrass(eligible, hazard.flatEnd, hazard.end(),
                            flatY, RunnerEngine.GROUND_Y, left, right);
                }
            }
            cursor = Math.max(cursor, hazard.end());
        }
        addGrass(eligible, cursor, right, RunnerEngine.GROUND_Y,
                RunnerEngine.GROUND_Y, left, right);
        List<GrassSegment> patches = new ArrayList<>();
        int low = 0;
        int high = pattern.patches.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (pattern.patches.get(middle).toX <= left) low = middle + 1;
            else high = middle;
        }
        for (int index = low; index < pattern.patches.size(); index++) {
            GrassPatch patch = pattern.patches.get(index);
            if (patch.fromX >= right) break;
            for (GrassSegment surface : eligible) {
                if (surface.toX <= patch.fromX) continue;
                if (surface.fromX >= patch.toX) break;
                float from = Math.max(surface.fromX, patch.fromX);
                float to = Math.min(surface.toX, patch.toX);
                if (to > from) {
                    patches.add(new GrassSegment(from, to,
                            surface.surfaceYAt(from), surface.surfaceYAt(to), patch.index));
                }
            }
        }
        return patches;
    }

    private static void addGrass(List<GrassSegment> grass, float start, float end,
                                 float startY, float endY, float left, float right) {
        float from = Math.max(start, left);
        float to = Math.min(end, right);
        if (to <= from) return;
        float fromY = startY + (endY - startY) * (from - start) / (end - start);
        float toY = startY + (endY - startY) * (to - start) / (end - start);
        grass.add(new GrassSegment(from, to, fromY, toY, -1));
    }

    static float palmGroupStart(long seed, int groupIndex) {
        return groupIndex * PALM_GROUP_STEP
                + (random01(seed, groupIndex, 0x2B992DDFA23249D6L) - .5f) * 42f;
    }

    static int palmGroupSize(long seed, int groupIndex) {
        float roll = random01(seed, groupIndex, 0x12D4C9148EAB5F03L);
        if (roll < .4f) return 1;
        if (roll < .7f) return 2;
        if (roll < .87f) return 3;
        if (roll < .96f) return 4;
        return 5;
    }

    static float palmTrunkSpacing(long seed, int groupIndex) {
        return 7.5f + random01(seed, groupIndex, 0x7E986DC83BA73E51L) * 1.5f;
    }

    static List<Palm> visiblePalms(long seed, List<RunnerEngine.Hazard> hazards,
                                   float left, float right) {
        List<Palm> palms = new ArrayList<>();
        for (int group = firstIndex(left - PALM_EXTENT - 36f, PALM_GROUP_STEP);
                group <= lastIndex(right + PALM_EXTENT, PALM_GROUP_STEP); group++) {
            float first = palmGroupStart(seed, group);
            int count = palmGroupSize(seed, group);
            float spacing = palmTrunkSpacing(seed, group);
            float last = first + (count - 1) * spacing;
            if (first > right + PALM_EXTENT || last < left - PALM_EXTENT
                    || groupOverHole(first, last, hazards)) continue;
            for (int ordinal = 0; ordinal < count; ordinal++) {
                float x = first + ordinal * spacing;
                int treeIndex = group * 7 + ordinal;
                palms.add(new Palm(group, ordinal, x, palmView(seed, treeIndex),
                        random01(seed, treeIndex, 0x498D631AF042E7CBL)
                                * (float) (2d * Math.PI)));
            }
        }
        return palms;
    }

    private static boolean groupOverHole(float first, float last,
                                         List<RunnerEngine.Hazard> hazards) {
        for (RunnerEngine.Hazard hazard : hazards) {
            if (hazard.type != RunnerEngine.HazardType.HOLE) continue;
            if (hazard.x - HOLE_CLEARANCE >= last) break;
            if (hazard.end() + HOLE_CLEARANCE > first) return true;
        }
        return false;
    }

    static int palmView(long seed, int index) {
        return (int) (random01(seed, index, 0xA9BC27D4E5168073L) * 5f);
    }

    static float palmHeight(int view) { return PALM_HEIGHTS[view]; }
    static float palmTrunkWidth(int view) { return PALM_TRUNKS[view]; }
    static float palmLean(int view) { return PALM_LEANS[view]; }

    /** Smooth seeded surface variation, always within one tenth of the car height. */
    static float groundBump(long seed, float along) {
        float sample = along / 19f;
        int index = (int) Math.floor(sample);
        float blend = sample - index;
        blend = blend * blend * (3f - 2f * blend);
        float a = random01(seed, index, 0xC19B28D37F5A904EL);
        float b = random01(seed, index + 1, 0xC19B28D37F5A904EL);
        return GROUND_BUMP_MAX * (a + (b - a) * blend);
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
