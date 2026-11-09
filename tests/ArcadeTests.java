import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import arcade.GamePanel;
import arcade.Launcher;
import utils.Commons;

/** Dependency-free behavioral regression checks. Run with ./test.sh. */
public final class ArcadeTests {
    private static int checks;

    public static void main(String[] args) {
        for (String game : new String[] {"breakout", "pacman"}) {
            lifecycle(game);
            for (String mode : new String[] {"auto", "random"}) {
                for (int seed = 0; seed < 12; seed++) {
                    reproducible(game, mode, seed);
                }
            }
            boundedIdle(game);
            renderIsPure(game);
        }
        legalPacmanMovement();
        trackingScores();
        rejectsInvalidInputs();
        System.out.println(
                "PASS: "
                        + checks
                        + " checks: simulation, physics, state, pause, restart and rendering.");
    }

    private static void lifecycle(String game) {
        GamePanel panel = Launcher.createBoard(game, "auto", false, 42);
        int[] initial = panel.getState();
        panel.tick();
        int steps = panel.getSteps();
        panel.togglePause();
        panel.tick();
        check(panel.getSteps() == steps, game + ": pause must freeze simulation");
        panel.togglePause();
        panel.tick();
        check(panel.getSteps() == steps + 1, game + ": resume must advance");
        panel.restart();
        check(
                Arrays.equals(initial, panel.getState())
                        && panel.getScore() == 0
                        && panel.getSteps() == 0,
                game + ": restart resets state");
        panel.setMaxSteps(3);
        panel.runSimulation();
        check(panel.getSteps() == 3 && !panel.isRunning(), game + ": step limit");
        panel.tick();
        check(panel.getSteps() == 3, game + ": terminal state must not advance");
    }

    private static void reproducible(String game, String mode, int seed) {
        GamePanel a = Launcher.createBoard(game, mode, false, seed);
        GamePanel b = Launcher.createBoard(game, mode, false, seed);
        a.setMaxSteps(6000);
        b.setMaxSteps(6000);
        while (a.isRunning()) {
            a.tick();
            b.tick();
            check(
                    Arrays.equals(a.getState(), b.getState()),
                    game + ": deterministic trajectory, " + mode + ", seed " + seed);
            check(
                    a.getState().length
                            == (game.equals("pacman")
                                    ? Commons.PACMAN_STATE_SIZE
                                    : Commons.BREAKOUT_STATE_SIZE),
                    "state size");
            if (game.equals("pacman")) {
                int[] state = a.getState();
                check(
                        state[450] >= 0 && state[450] < 360 && state[451] >= 0 && state[451] < 360,
                        "player stays in maze");
                for (int i = 452; i < state.length && state[i] >= 0; i += 2) {
                    check(
                            state[i] < 360 && state[i + 1] >= 0 && state[i + 1] < 360,
                            "ghost stays in maze");
                }
            } else {
                check(a.getState()[3] >= 24 && a.getState()[3] <= 332, "paddle stays in arena");
            }
        }
        check(
                a.getScore() == b.getScore() && a.getFitness() == b.getFitness() && !b.isRunning(),
                "deterministic result");
        a.restart();
        GamePanel fresh = Launcher.createBoard(game, mode, false, seed);
        for (int t = 0; t < 100; t++) {
            a.tick();
            fresh.tick();
        }
        check(
                Arrays.equals(a.getState(), fresh.getState()),
                "restart resets controller and world random state");
        int[] snapshot = a.getState();
        snapshot[0] = -999;
        check(a.getState()[0] != snapshot[0], "state is an isolated snapshot");
    }

    private static void boundedIdle(String game) {
        GamePanel panel =
                game.equals("pacman")
                        ? new pacman.PacmanBoard(s -> 0, false, 42)
                        : new breakout.BreakoutBoard(s -> 0, false, 42);
        panel.setMaxSteps(200);
        panel.runSimulation();
        check(
                panel.getSteps() <= 200 && !panel.isRunning(),
                "idle controller cannot hang simulation");
    }

    private static void renderIsPure(String game) {
        GamePanel panel = Launcher.createBoard(game, "auto", false, 42);
        panel.setSize(420, 500);
        int[] before = panel.getState();
        BufferedImage image = new BufferedImage(420, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int i = 0; i < 10; i++) {
            panel.paint(g);
        }
        g.dispose();
        check(
                Arrays.equals(before, panel.getState()) && panel.getSteps() == 0,
                "render must not move characters");
    }

    private static void legalPacmanMovement() {
        GamePanel panel = new pacman.PacmanBoard(null, false, 42);
        int[] state = panel.getState();
        int[] bits = {0, 1, 4, 2, 8}, opposite = {0, 2, 1, 4, 3}, offset = {0, -1, 1, -15, 15};
        for (int pos = 0; pos < 225; pos++) {
            for (int d = 1; d <= 4; d++) {
                if ((state[225 + pos] & bits[d]) == 0) {
                    int next = pos + offset[d];
                    check(next >= 0 && next < 225, "closed maze perimeter");
                    check((state[225 + next] & bits[opposite[d]]) == 0, "symmetric maze walls");
                }
            }
        }
        for (int t = 0; t < 2000 && panel.isRunning(); t++) {
            int[] before = panel.getState();
            int action = t / 24 % 4 + 1;
            panel.makeMove(action);
            panel.tick();
            int[] after = panel.getState();
            int distance = Math.abs(after[450] - before[450]) + Math.abs(after[451] - before[451]);
            // A larger displacement can only be a respawn after a collision.
            if (distance <= 4) {
                check(distance == 0 || distance == 4, "smooth player movement");
            }
        }
        panel.restart();
        check(Arrays.equals(state, panel.getState()), "pellets and enemies reset after playing");
    }

    private static void trackingScores() {
        GamePanel breakout = Launcher.createBoard("breakout", "auto", false, 42);
        breakout.runSimulation();
        check(breakout.getScore() >= 1000, "tracking baseline breaks bricks");
        GamePanel pacman = Launcher.createBoard("pacman", "auto", false, 42);
        pacman.setMaxSteps(6000);
        pacman.runSimulation();
        check(pacman.getScore() >= 100, "pathfinding baseline collects pellets");
    }

    private static void rejectsInvalidInputs() {
        try {
            Launcher.createBoard("pacman", "manual", false, 42);
            throw new AssertionError("headless manual accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            Launcher.createBoard("unknown", "auto", false, 42);
            throw new AssertionError("unknown game accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            Launcher.createBoard("breakout", "auto", false, 42).setMaxSteps(0);
            throw new AssertionError("zero limit accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
