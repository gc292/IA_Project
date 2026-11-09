package pacman;

import java.util.Random;

import utils.GameController;

/** Seeded baseline suitable for reproducible comparisons. */
public class RandomController implements GameController {
    private final Random random;
    private final int actions;

    public RandomController() {
        this(42);
    }

    public RandomController(int seed) {
        this(seed, 5);
    }

    public RandomController(int seed, int actions) {
        if (actions < 1 || actions > 5) {
            throw new IllegalArgumentException("Action count must be between 1 and 5.");
        }
        random = new Random(seed);
        this.actions = actions;
    }

    @Override
    public void reset(int seed) {
        random.setSeed(seed);
    }

    @Override
    public int nextMove(int[] state) {
        return random.nextInt(actions);
    }
}
