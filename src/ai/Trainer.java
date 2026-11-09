package ai;

import arcade.GamePanel;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import utils.GameController;

/** Supervised behaviour cloning with dataset aggregation on learner trajectories.
 * The teacher supplies labels only during training; deployed policies use learned weights. */
public final class Trainer {
    private static final int CAPACITY = 24_000;
    private Trainer() { }
    public record Config(String game, int seed, int episodes, int epochs, int steps, Path model) {
        public Config {
            Features.inputs(game);
            if (episodes < 1 || episodes > 512 || epochs < 1 || epochs > 500 || steps < 1)
                throw new IllegalArgumentException("Training requires 1..512 episodes, 1..500 epochs and a positive step limit.");
            Objects.requireNonNull(model, "Model path");
        }
    }
    public record Result(Path model, int samples, double accuracy, double beforeScore, double afterScore) { }
    private record Example(double[] input, int target) { }
    private static final class Dataset {
        final List<Example> values = new ArrayList<>();
        final Random random;
        int seen;
        Dataset(long seed) { random = new Random(seed); }
        void add(Example example) {
            seen++;
            if (values.size() < CAPACITY) values.add(example);
            else { int index = random.nextInt(seen); if (index < CAPACITY) values.set(index, example); }
        }
    }
    public static Result train(Config config, Consumer<String> log) throws IOException {
        Random random = new Random(config.seed());
        NeuralNetwork network = new NeuralNetwork(Features.inputs(config.game()), 32, Features.outputs(config.game()), config.seed());
        log.accept("Behaviour cloning with dataset aggregation | " + config.game() + " | " + network.inputs() + " → 32 → " + network.outputs());
        log.accept("Reference actions provide training labels. Inference uses the learned weights.");
        int evaluationSteps = Math.min(config.steps(), 6000);
        Experiments.Stats before = Experiments.evaluate(config.game(), "neural", network, 8, config.seed() + 100_000, evaluationSteps);
        log.accept(String.format(Locale.ROOT, "Untrained policy: mean score %.2f on 8 validation seeds", before.meanScore()));
        Dataset data = new Dataset(config.seed());
        Dataset validation = new Dataset(config.seed() + 1L);
        collect(config, validation, null, 0, 4, config.seed() + 50_000, random);
        StringBuilder history = new StringBuilder("epoch,round,samples,train_loss,validation_loss,validation_accuracy\n");
        NeuralNetwork best = network.copy();
        double bestScore = -1;
        int completed = 0, rounds = Math.min(3, config.epochs());
        for (int round = 0; round < rounds; round++) {
            log.accept("Collecting training episodes: round " + (round + 1) + "/" + rounds);
            collect(config, data, round == 0 ? null : network, round == 0 ? 1 : 0.15,
                config.episodes(), config.seed() + round * 1000, random);
            int roundEpochs = config.epochs() / rounds + (round < config.epochs() % rounds ? 1 : 0);
            for (int epoch = 0; epoch < roundEpochs; epoch++) {
                double loss = 0;
                double learningRate = 0.0015 / (1 + completed / 10.0);
                for (int update = 0; update < data.values.size(); update++) {
                    checkInterrupted();
                    Example example = data.values.get(random.nextInt(data.values.size()));
                    loss += network.train(example.input(), example.target(), learningRate);
                }
                double[] metrics = metrics(network, validation.values);
                completed++;
                String row = String.format(Locale.ROOT, "%d,%d,%d,%.6f,%.6f,%.6f", completed, round + 1, data.values.size(),
                    loss / data.values.size(), metrics[0], metrics[1]);
                history.append(row).append('\n');
                log.accept(String.format(Locale.ROOT, "Epoch %d/%d | loss %.4f | validation accuracy %.1f%% | %d samples", completed, config.epochs(),
                    loss / data.values.size(), metrics[1] * 100, data.values.size()));
            }
            Experiments.Stats score = Experiments.evaluate(config.game(), "neural", network, 8, config.seed() + 100_000, evaluationSteps);
            log.accept(String.format(Locale.ROOT, "Round %d | validation mean score %.2f | wins %d/8", round + 1, score.meanScore(), score.wins()));
            if (score.meanScore() > bestScore) { bestScore = score.meanScore(); best = network.copy(); }
        }
        double accuracy = metrics(best, validation.values)[1];
        checkInterrupted();
        ModelStore.save(config.model(), config.game(), best);
        Path historyPath = Path.of(config.model().toString() + ".training.csv");
        Files.writeString(historyPath, history);
        log.accept("Evaluating 20 held-out test seeds...");
        String benchmark = Experiments.benchmark(config.game(), best, 20, config.seed() + 1_000_000, config.steps(), log);
        Files.writeString(Path.of(config.model().toString() + ".benchmark.csv"), benchmark);
        String report = String.format(Locale.ROOT,
            "method=supervised_behavior_cloning_with_dataset_aggregation%nfeature_version=%d%ngame=%s%nseed=%d%nepisodes_per_round=%d%nepochs=%d%nmax_steps=%d%n"
            + "architecture=%d,32,%d%nparameters=%d%nsamples=%d%nvalidation_accuracy=%.6f%nvalidation_initial_score=%.2f%nvalidation_selected_score=%.2f%n"
            + "validation_policy_seeds=%d..%d%ntest_seeds=%d..%d%nselection=highest_mean_validation_score_after_each_round%n",
            Features.VERSION, config.game(), config.seed(), config.episodes(), config.epochs(), config.steps(), best.inputs(), best.outputs(), best.parameters(),
            data.values.size(), accuracy, before.meanScore(), bestScore, config.seed() + 100_000, config.seed() + 100_007, config.seed() + 1_000_000, config.seed() + 1_000_019);
        Files.writeString(Path.of(config.model().toString() + ".report.txt"), report);
        log.accept("Model saved: " + config.model().toAbsolutePath());
        return new Result(config.model(), data.values.size(), accuracy, before.meanScore(), bestScore);
    }
    private static void collect(Config config, Dataset data, NeuralNetwork learner, double teacherProbability,
                                int episodes, int firstSeed, Random random) {
        for (int episode = 0; episode < episodes; episode++) {
            checkInterrupted();
            GameController teacher = Experiments.teacher(config.game());
            NeuralController student = learner == null ? null : new NeuralController(config.game(), learner);
            int[] ticks = {0};
            GameController recorder = state -> {
                int target = teacher.nextMove(state);
                int prediction = student == null ? target : student.nextMove(state);
                if (Features.decisionPoint(config.game(), state) && (!config.game().equals("breakout") || ticks[0] % 3 == 0)) {
                    int label = config.game().equals("breakout") ? target : target - 1;
                    if (label >= 0 && label < Features.outputs(config.game())) data.add(new Example(Features.encode(config.game(), state), label));
                }
                ticks[0]++;
                return student == null || random.nextDouble() < teacherProbability ? target : prediction;
            };
            GamePanel panel = Experiments.board(config.game(), recorder, firstSeed + episode);
            panel.setMaxSteps(config.steps()); panel.runSimulation();
        }
        if (data.values.isEmpty()) throw new IllegalArgumentException("No training samples collected.");
    }
    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Training cancelled.");
    }
    private static double[] metrics(NeuralNetwork network, List<Example> data) {
        double loss = 0; int correct = 0;
        for (Example example : data) {
            double[] probabilities = network.predict(example.input()); int best = 0;
            for (int i = 1; i < probabilities.length; i++) if (probabilities[i] > probabilities[best]) best = i;
            if (best == example.target()) correct++;
            loss -= Math.log(Math.max(1e-12, probabilities[example.target()]));
        }
        return new double[] {loss / data.size(), correct / (double) data.size()};
    }
}
