import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

import ai.Experiments;
import ai.Features;
import ai.ModelStore;
import ai.NeuralController;
import ai.NeuralNetwork;
import ai.Trainer;
import arcade.GamePanel;
import arcade.Launcher;

/** Verifies actual learning, model integrity, independent inference and reproducible training. */
public final class NeuralTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        learnsNonlinearFunction();
        modelRoundTripAndValidation();
        trainedPoliciesBeatRandom();
        trainingIsReproducible();
        System.out.println(
                "PASS: "
                        + checks
                        + " checks: learning, checkpoints, reproducible training and evaluation.");
    }

    private static void learnsNonlinearFunction() {
        NeuralNetwork network = new NeuralNetwork(2, 8, 2, 7);
        double[][] x = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
        int[] labels = {0, 1, 1, 0};
        double before = loss(network, x, labels);
        Random random = new Random(7);
        for (int i = 0; i < 6000; i++) {
            int index = random.nextInt(4);
            network.train(x[index], labels[index], 0.01);
        }
        double after = loss(network, x, labels);
        check(
                after < 0.02 && after < before / 10,
                "Backpropagation must learn XOR, not just memorise a linear decision rule");
        for (int i = 0; i < x.length; i++) {
            double[] p = network.predict(x[i]);
            check(p[labels[i]] > 0.98, "Correct nonlinear prediction");
            check(Math.abs(p[0] + p[1] - 1) < 1e-10, "Probabilities sum to one");
        }
        double[] snapshot = network.weights();
        snapshot[0] = 123456;
        check(network.weights()[0] != 123456, "Weight snapshots cannot mutate the model");
    }

    private static double loss(NeuralNetwork network, double[][] x, int[] labels) {
        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            sum -= Math.log(network.predict(x[i])[labels[i]]);
        }
        return sum / x.length;
    }

    private static void modelRoundTripAndValidation() throws IOException {
        Path directory = Files.createTempDirectory("ia-model-test-");
        try {
            NeuralController policy = ModelStore.bundled("breakout");
            Path file = directory.resolve("model.nn");
            ModelStore.save(file, "breakout", policy.network());
            NeuralController loaded = ModelStore.load("breakout", file);
            GamePanel panel = Launcher.createBoard("breakout", "auto", false, 42);
            double[] input = Features.encode("breakout", panel.getState());
            check(
                    Arrays.equals(policy.network().predict(input), loaded.network().predict(input)),
                    "Saved model preserves predictions exactly");
            expectInvalid(
                    () -> ModelStore.load("pacman", file),
                    "Wrong-game checkpoint must be rejected");
            Files.writeString(file, "IA_ARCADE_MLP_V1\nbreakout\n1 8 32 3\nNaN\n");
            expectInvalid(
                    () -> ModelStore.load("breakout", file),
                    "Truncated checkpoint must be rejected");
            Files.writeString(file, "not-a-model");
            expectInvalid(
                    () -> ModelStore.load("breakout", file), "Unknown format must be rejected");
            double[] zeros = new double[policy.network().parameters()];
            policy.network().setWeights(zeros);
            check(
                    policy.nextMove(panel.getState()) == 0,
                    "Inference must obey the weights; no hidden fallback to expert");
            Arrays.fill(zeros, Double.NaN);
            expectInvalid(
                    () -> policy.network().setWeights(zeros),
                    "Non-finite weights must be rejected");
        } finally {
            deleteTree(directory);
        }
    }

    private static void trainedPoliciesBeatRandom() {
        for (String game : new String[] {"breakout", "pacman"}) {
            NeuralController neural = ModelStore.bundled(game);
            Experiments.Stats trained =
                    Experiments.evaluate(game, "neural", neural.network(), 8, 700_000, 20_000);
            Experiments.Stats random =
                    Experiments.evaluate(game, "random", null, 8, 700_000, 20_000);
            check(
                    trained.meanScore() > random.meanScore() * 1.5,
                    game + ": trained network must beat random on held-out seeds");
            check(
                    trained.wins() >= 6,
                    game + ": trained network must finish at least 6 of 8 evaluation games");
            GamePanel a = Launcher.createBoard(game, "neural", false, 42),
                    b = Launcher.createBoard(game, "neural", false, 42);
            for (int i = 0; i < 250; i++) {
                a.tick();
                b.tick();
            }
            check(Arrays.equals(a.getState(), b.getState()), "Loaded policies are deterministic");
            a.restart();
            b = Launcher.createBoard(game, "neural", false, 42);
            for (int i = 0; i < 250; i++) {
                a.tick();
                b.tick();
            }
            check(
                    Arrays.equals(a.getState(), b.getState()),
                    "Restart resets recurrent controller bookkeeping");
            for (double feature : Features.encode(game, a.getState())) {
                check(Double.isFinite(feature), "Encoded observations are finite");
            }
        }
    }

    private static void trainingIsReproducible() throws IOException {
        Path directory = Files.createTempDirectory("ia-train-test-");
        try {
            Path a = directory.resolve("first.nn"), b = directory.resolve("second.nn");
            Trainer.Config configA = new Trainer.Config("breakout", 123, 4, 9, 800, a);
            Trainer.Result result = Trainer.train(configA, line -> {});
            Trainer.train(new Trainer.Config("breakout", 123, 4, 9, 800, b), line -> {});
            check(
                    Files.mismatch(a, b) == -1,
                    "Same seed and options produce identical learned weights");
            check(
                    Files.mismatch(Path.of(a + ".training.csv"), Path.of(b + ".training.csv"))
                            == -1,
                    "Training history is reproducible");
            check(
                    result.samples() > 0 && result.accuracy() > 0.8,
                    "End-to-end training collects data and learns expert labels");
            check(
                    Files.readString(Path.of(a + ".training.csv")).lines().count() == 10,
                    "One real metric row per epoch");
            check(
                    Files.readString(Path.of(a + ".benchmark.csv")).contains("neural"),
                    "Evaluation report includes learned policy");
            GamePanel panel = Launcher.createBoard("breakout", "neural", false, 42, a);
            panel.setMaxSteps(20);
            panel.runSimulation();
            check(panel.getSteps() == 20, "Newly trained model is playable without rebuilding");
        } finally {
            deleteTree(directory);
        }
    }

    private static void expectInvalid(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void deleteTree(Path path) throws IOException {
        try (var paths = Files.walk(path)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
