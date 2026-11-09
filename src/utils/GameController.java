package utils;

/** Chooses an action from a snapshot. 0 = idle, 1 = left, 2 = right,
 * 3 = up and 4 = down (the latter two only apply to Pac-Man). */
@FunctionalInterface
public interface GameController {
    int nextMove(int[] currentState);

    /** Reset any internal random state when an episode restarts. */
    default void reset(int seed) { }
}
