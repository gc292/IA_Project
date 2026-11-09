package ai;

import java.util.Arrays;
import java.util.Random;

/** Fully connected tanh/softmax policy trained with backpropagation and Adam. */
public final class NeuralNetwork {
    private static final double FIRST_MOMENT_DECAY = 0.9;
    private static final double SECOND_MOMENT_DECAY = 0.999;
    private static final double ADAM_EPSILON = 1e-8;

    private final int inputCount;
    private final int hiddenCount;
    private final int outputCount;
    private final int hiddenBiasOffset;
    private final int outputWeightOffset;
    private final int outputBiasOffset;
    private final double[] weights;
    private final double[] firstMoment;
    private final double[] secondMoment;
    private int updates;

    public NeuralNetwork(int inputs, int hidden, int outputs, long seed) {
        if (inputs < 1
                || inputs > 128
                || hidden < 1
                || hidden > 256
                || outputs < 2
                || outputs > 8) {
            throw new IllegalArgumentException("Invalid network dimensions.");
        }

        inputCount = inputs;
        hiddenCount = hidden;
        outputCount = outputs;
        hiddenBiasOffset = inputs * hidden;
        outputWeightOffset = hiddenBiasOffset + hidden;
        outputBiasOffset = outputWeightOffset + hidden * outputs;
        weights = new double[outputBiasOffset + outputs];
        firstMoment = new double[weights.length];
        secondMoment = new double[weights.length];

        initializeWeights(new Random(seed));
    }

    public int inputs() {
        return inputCount;
    }

    public int hidden() {
        return hiddenCount;
    }

    public int outputs() {
        return outputCount;
    }

    public int parameters() {
        return weights.length;
    }

    public double[] weights() {
        return weights.clone();
    }

    public void setWeights(double[] values) {
        if (values.length != weights.length) {
            throw new IllegalArgumentException("Incorrect weight count.");
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Weights must be finite.");
            }
        }

        System.arraycopy(values, 0, weights, 0, values.length);
        Arrays.fill(firstMoment, 0);
        Arrays.fill(secondMoment, 0);
        updates = 0;
    }

    public NeuralNetwork copy() {
        NeuralNetwork copy = new NeuralNetwork(inputCount, hiddenCount, outputCount, 0);
        copy.setWeights(weights);
        return copy;
    }

    public double[] predict(double[] input) {
        return forward(input, new double[hiddenCount]);
    }

    /** Applies one supervised update and returns the pre-update cross-entropy loss. */
    public double train(double[] input, int target, double learningRate) {
        if (target < 0
                || target >= outputCount
                || !Double.isFinite(learningRate)
                || learningRate <= 0) {
            throw new IllegalArgumentException("Invalid training parameters.");
        }

        double[] hiddenActivation = new double[hiddenCount];
        double[] outputGradient = forward(input, hiddenActivation);
        double loss = -Math.log(Math.max(1e-12, outputGradient[target]));
        outputGradient[target] -= 1;

        // Compute both layer gradients before changing any weights.
        double[] hiddenGradient = hiddenGradient(hiddenActivation, outputGradient);
        double correctedRate = correctedLearningRate(learningRate);
        updateHiddenLayer(input, hiddenGradient, correctedRate);
        updateOutputLayer(hiddenActivation, outputGradient, correctedRate);
        return loss;
    }

    private void initializeWeights(Random random) {
        double inputScale = Math.sqrt(2.0 / (inputCount + hiddenCount));
        double outputScale = Math.sqrt(2.0 / (hiddenCount + outputCount));

        for (int index = 0; index < hiddenBiasOffset; index++) {
            weights[index] = random.nextGaussian() * inputScale;
        }
        for (int index = outputWeightOffset; index < outputBiasOffset; index++) {
            weights[index] = random.nextGaussian() * outputScale;
        }
    }

    private double[] forward(double[] input, double[] hiddenActivation) {
        if (input.length != inputCount) {
            throw new IllegalArgumentException("Incorrect input count.");
        }

        for (int hidden = 0; hidden < hiddenCount; hidden++) {
            double sum = weights[hiddenBiasOffset + hidden];
            for (int feature = 0; feature < inputCount; feature++) {
                sum += weights[hidden * inputCount + feature] * input[feature];
            }
            hiddenActivation[hidden] = Math.tanh(sum);
        }

        double[] logits = new double[outputCount];
        for (int output = 0; output < outputCount; output++) {
            double sum = weights[outputBiasOffset + output];
            for (int hidden = 0; hidden < hiddenCount; hidden++) {
                sum +=
                        weights[outputWeightOffset + output * hiddenCount + hidden]
                                * hiddenActivation[hidden];
            }
            logits[output] = sum;
        }
        return softmax(logits);
    }

    private static double[] softmax(double[] logits) {
        double maximum = -Double.MAX_VALUE;
        for (double logit : logits) {
            maximum = Math.max(maximum, logit);
        }

        double total = 0;
        for (int index = 0; index < logits.length; index++) {
            logits[index] = Math.exp(logits[index] - maximum);
            total += logits[index];
        }
        for (int index = 0; index < logits.length; index++) {
            logits[index] /= total;
        }
        return logits;
    }

    private double[] hiddenGradient(double[] activation, double[] outputGradient) {
        double[] gradient = new double[hiddenCount];
        for (int hidden = 0; hidden < hiddenCount; hidden++) {
            for (int output = 0; output < outputCount; output++) {
                gradient[hidden] +=
                        outputGradient[output]
                                * weights[outputWeightOffset + output * hiddenCount + hidden];
            }
            gradient[hidden] *= 1 - activation[hidden] * activation[hidden];
        }
        return gradient;
    }

    private double correctedLearningRate(double learningRate) {
        updates++;
        double firstMomentCorrection = 1 - Math.pow(FIRST_MOMENT_DECAY, updates);
        double secondMomentCorrection = 1 - Math.pow(SECOND_MOMENT_DECAY, updates);
        return learningRate * Math.sqrt(secondMomentCorrection) / firstMomentCorrection;
    }

    private void updateHiddenLayer(double[] input, double[] gradient, double learningRate) {
        for (int hidden = 0; hidden < hiddenCount; hidden++) {
            for (int feature = 0; feature < inputCount; feature++) {
                update(
                        hidden * inputCount + feature,
                        gradient[hidden] * input[feature],
                        learningRate);
            }
            update(hiddenBiasOffset + hidden, gradient[hidden], learningRate);
        }
    }

    private void updateOutputLayer(double[] activation, double[] gradient, double learningRate) {
        for (int output = 0; output < outputCount; output++) {
            for (int hidden = 0; hidden < hiddenCount; hidden++) {
                int index = outputWeightOffset + output * hiddenCount + hidden;
                update(index, gradient[output] * activation[hidden], learningRate);
            }
            update(outputBiasOffset + output, gradient[output], learningRate);
        }
    }

    private void update(int index, double gradient, double learningRate) {
        firstMoment[index] = FIRST_MOMENT_DECAY * firstMoment[index] + 0.1 * gradient;
        secondMoment[index] =
                SECOND_MOMENT_DECAY * secondMoment[index] + 0.001 * gradient * gradient;
        weights[index] -=
                learningRate * firstMoment[index] / (Math.sqrt(secondMoment[index]) + ADAM_EPSILON);
    }
}
