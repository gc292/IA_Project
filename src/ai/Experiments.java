package ai;

import java.util.Locale;
import java.util.function.Consumer;

import arcade.GameFactory;
import arcade.GamePanel;
import utils.GameController;

/** Identical environments and held-out seeds for all controller comparisons. */
public final class Experiments {
    private Experiments() {}

    public static GamePanel board(String game, GameController controller, int seed) {
        return GameFactory.board(game, controller, false, seed);
    }

    public static GameController teacher(String game) {
        return GameFactory.teacher(game);
    }

    public static Stats evaluate(
            String game,
            String controller,
            NeuralNetwork network,
            int episodes,
            int seed,
            int steps) {
        double total = 0, totalSteps = 0;
        int wins = 0;
        for (int episode = 0; episode < episodes; episode++) {
            int currentSeed = seed + episode;
            GameController policy;
            switch (controller) {
                case "neural":
                    policy = new NeuralController(game, network);
                    break;
                case "random":
                    policy = GameFactory.controller(game, controller, currentSeed, null);
                    break;
                case "auto":
                    policy = teacher(game);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid evaluation controller.");
            }
            GamePanel panel = board(game, policy, currentSeed);
            panel.setMaxSteps(steps);
            panel.runSimulation();
            total += panel.getScore();
            totalSteps += panel.getSteps();
            if (panel.isWon()) {
                wins++;
            }
        }
        return new Stats(total / episodes, wins, totalSteps / episodes);
    }

    public static String benchmark(
            String game,
            NeuralNetwork network,
            int episodes,
            int seed,
            int steps,
            Consumer<String> log) {
        if (episodes < 1) {
            throw new IllegalArgumentException("Episode count must be positive.");
        }
        StringBuilder csv =
                new StringBuilder(
                        "game,controller,episodes,first_seed,max_steps,"
                                + "mean_score,wins,mean_steps\n");
        for (String controller : new String[] {"random", "auto", "neural"}) {
            Stats stats = evaluate(game, controller, network, episodes, seed, steps);
            String line =
                    String.format(
                            Locale.ROOT,
                            "%s,%s,%d,%d,%d,%.2f,%d,%.2f",
                            game,
                            controller,
                            episodes,
                            seed,
                            steps,
                            stats.meanScore(),
                            stats.wins(),
                            stats.meanSteps());
            csv.append(line).append('\n');
            log.accept(line);
        }
        return csv.toString();
    }

    public record Stats(double meanScore, int wins, double meanSteps) {}
}
