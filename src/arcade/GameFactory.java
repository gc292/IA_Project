package arcade;

import java.nio.file.Path;

import ai.ModelStore;
import breakout.BreakoutBoard;
import breakout.TrackingController;
import pacman.PacmanBoard;
import pacman.PathfindingController;
import pacman.RandomController;
import utils.GameController;

/** Shared game and controller construction for interactive play and experiments. */
public final class GameFactory {
    private GameFactory() {}

    public static GamePanel board(String game, GameController controller, boolean gui, int seed) {
        switch (game) {
            case "breakout":
                return new BreakoutBoard(controller, gui, seed);
            case "pacman":
                return new PacmanBoard(controller, gui, seed);
            default:
                throw new IllegalArgumentException("Unknown game: " + game);
        }
    }

    public static GameController controller(String game, String mode, int seed, Path model) {
        switch (mode) {
            case "manual":
                return null;
            case "neural":
                return model == null ? ModelStore.bundled(game) : ModelStore.load(game, model);
            case "random":
                return new RandomController(seed, game.equals("breakout") ? 3 : 5);
            case "auto":
                return teacher(game);
            default:
                throw new IllegalArgumentException("Unknown controller: " + mode);
        }
    }

    public static GameController teacher(String game) {
        switch (game) {
            case "breakout":
                return new TrackingController();
            case "pacman":
                return new PathfindingController();
            default:
                throw new IllegalArgumentException("Unknown game: " + game);
        }
    }
}
