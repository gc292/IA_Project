package ai;

import utils.GameController;

/** Inference uses only encoded observations and learned weights; no reference controller. */
public final class NeuralController implements GameController {
    private final String game;
    private final NeuralNetwork network;
    private int lastAction;
    private double confidence;

    public NeuralController(String game, NeuralNetwork network) {
        validateNetwork(game, network);
        this.game = game;
        this.network = network;
    }

    static void validateNetwork(String game, NeuralNetwork network) {
        if (network.inputs() != Features.inputs(game)
                || network.outputs() != Features.outputs(game)) {
            throw new IllegalArgumentException("Network is incompatible with " + game + ".");
        }
    }

    public NeuralNetwork network() {
        return network;
    }

    public double confidence() {
        return confidence;
    }

    @Override
    public void reset(int seed) {
        lastAction = 0;
        confidence = 0;
    }

    @Override
    public int nextMove(int[] state) {
        if (!Features.decisionPoint(game, state)) {
            return lastAction;
        }
        double[] probabilities = network.predict(Features.encode(game, state));
        int best = -1;
        double probability = -1;
        for (int i = 0; i < probabilities.length; i++) {
            int action = game.equals("breakout") ? i : i + 1;
            if (Features.legal(game, action, state) && probabilities[i] > probability) {
                best = action;
                probability = probabilities[i];
            }
        }
        confidence = Math.max(0, probability);
        lastAction = best < 0 ? 0 : best;
        return lastAction;
    }
}
