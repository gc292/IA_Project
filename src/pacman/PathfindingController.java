package pacman;

import java.util.Arrays;
import utils.GameController;

/** Breadth-first search for pellets; avoids cells occupied by or next to ghosts. */
public final class PathfindingController implements GameController {
    private static final int[] OFFSET = {0, -1, 1, -15, 15};
    private static final int[] WALL = {0, 1, 4, 2, 8};
    @Override public int nextMove(int[] state) {
        int start = state[451] / 24 * 15 + state[450] / 24;
        int action = search(state, start, true);
        return action != 0 ? action : search(state, start, false);
    }
    private int search(int[] state, int start, boolean safe) {
        int[] first = new int[225]; Arrays.fill(first, -1); first[start] = 0;
        int[] queue = new int[225]; int head = 0, tail = 0; queue[tail++] = start;
        int fallback = 0;
        while (head < tail) {
            int pos = queue[head++];
            if (pos != start && state[pos] == 1) return first[pos];
            for (int d = 1; d <= 4; d++) if ((state[225 + pos] & WALL[d]) == 0) {
                int next = pos + OFFSET[d];
                if (next < 0 || next >= 225 || first[next] != -1 || (safe && danger(state, next))) continue;
                first[next] = pos == start ? d : first[pos];
                if (fallback == 0) fallback = first[next];
                queue[tail++] = next;
            }
        }
        return fallback;
    }
    private boolean danger(int[] state, int pos) {
        for (int i = 452; i < state.length; i += 2) if (state[i] >= 0) {
            int x = state[i] / 24, y = state[i + 1] / 24;
            if (Math.abs(pos % 15 - x) + Math.abs(pos / 15 - y) <= 1) return true;
        }
        return false;
    }
}
