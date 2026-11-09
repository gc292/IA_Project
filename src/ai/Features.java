package ai;

import java.util.Arrays;

/** Versioned, normalised observations. Distances describe the board, not an action label. */
public final class Features {
    public static final int VERSION = 1;
    private static final int[] OFFSET = {0, -1, 1, -15, 15};
    private static final int[] WALL = {0, 1, 4, 2, 8};
    private Features() { }
    public static int inputs(String game) {
        if (game.equals("breakout")) return 8;
        if (game.equals("pacman")) return 24;
        throw new IllegalArgumentException("Unknown game: " + game);
    }
    public static int outputs(String game) { inputs(game); return game.equals("breakout") ? 3 : 4; }
    public static double[] encode(String game, int[] state) {
        if (game.equals("breakout")) {
            if (state.length != 8) throw new IllegalArgumentException("Invalid Breakout state.");
            return new double[] {Math.max(-4, Math.min(4, (state[0] + 4 - state[3] - 32) / 32.0)),
                state[1] / 500.0, state[7] / 3.0, state[2], state[0] / 420.0,
                state[3] / 420.0, state[5] / 420.0, state[6] / 500.0};
        }
        if (!game.equals("pacman") || state.length != 476) throw new IllegalArgumentException("Invalid Pac-Man state.");
        double[] features = new double[24];
        int pos = state[451] / 24 * 15 + state[450] / 24;
        boolean[] danger = new boolean[225];
        for (int i = 452; i < state.length; i += 2) if (state[i] >= 0) {
            int x = state[i] / 24, y = state[i + 1] / 24;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (Math.abs(dx) + Math.abs(dy) <= 1 && nx >= 0 && nx < 15 && ny >= 0 && ny < 15) danger[ny * 15 + nx] = true;
            }
        }
        for (int action = 1; action <= 4; action++) {
            int base = (action - 1) * 5;
            boolean open = (state[225 + pos] & WALL[action]) == 0;
            features[base] = open ? 1 : 0;
            if (!open) continue;
            int next = pos + OFFSET[action];
            features[base + 1] = foodDistance(state, pos, next, danger);
            features[base + 2] = foodDistance(state, pos, next, null);
            int closestGhost = 30;
            for (int i = 452; i < state.length; i += 2) if (state[i] >= 0)
                closestGhost = Math.min(closestGhost, Math.abs(next % 15 - state[i] / 24) + Math.abs(next / 15 - state[i + 1] / 24));
            features[base + 3] = 1.0 / (1 + closestGhost);
            features[base + 4] = state[next] == 1 ? 1 : 0;
        }
        features[20] = state[450] / 360.0; features[21] = state[451] / 360.0;
        for (int i = 0; i < 225; i++) if (state[i] == 1) features[22] += 1.0 / 225;
        features[23] = danger[pos] ? 1 : 0;
        return features;
    }
    /** Reciprocal shortest distance to food through one neighbour, excluding the starting cell. */
    private static double foodDistance(int[] state, int start, int next, boolean[] danger) {
        if (danger != null && danger[next]) return 0;
        int[] distance = new int[225]; Arrays.fill(distance, -1);
        int[] queue = new int[225]; int head = 0, tail = 0;
        distance[start] = 0; distance[next] = 1; queue[tail++] = next;
        while (head < tail) {
            int pos = queue[head++];
            if (state[pos] == 1) return 1.0 / distance[pos];
            for (int d = 1; d <= 4; d++) if ((state[225 + pos] & WALL[d]) == 0) {
                int target = pos + OFFSET[d];
                if (target >= 0 && target < 225 && distance[target] < 0 && (danger == null || !danger[target])) {
                    distance[target] = distance[pos] + 1; queue[tail++] = target;
                }
            }
        }
        return 0;
    }
    public static boolean legal(String game, int action, int[] state) {
        if (game.equals("breakout")) return action >= 0 && action <= 2;
        if (action < 1 || action > 4) return false;
        int pos = state[451] / 24 * 15 + state[450] / 24;
        return (state[225 + pos] & WALL[action]) == 0;
    }
    public static boolean decisionPoint(String game, int[] state) {
        return game.equals("breakout") || (state[450] % 24 == 0 && state[451] % 24 == 0);
    }
}
