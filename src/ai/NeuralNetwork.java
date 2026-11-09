package ai;

import java.util.Random;

/** A fully connected tanh/softmax policy, trained by backpropagation and Adam.
 * No game-specific decisions or external ML dependencies live in this class. */
public final class NeuralNetwork {
    private final int inputs, hidden, outputs;
    private final double[] weights, firstMoment, secondMoment;
    private final int b1, w2, b2;
    private int updates;

    public NeuralNetwork(int inputs, int hidden, int outputs, long seed) {
        if (inputs < 1 || hidden < 1 || outputs < 2 || inputs > 128 || hidden > 256 || outputs > 8)
            throw new IllegalArgumentException("Invalid network dimensions.");
        this.inputs = inputs; this.hidden = hidden; this.outputs = outputs;
        b1 = inputs * hidden; w2 = b1 + hidden; b2 = w2 + hidden * outputs;
        weights = new double[b2 + outputs];
        firstMoment = new double[weights.length]; secondMoment = new double[weights.length];
        Random random = new Random(seed);
        double inputScale = Math.sqrt(2.0 / (inputs + hidden));
        double outputScale = Math.sqrt(2.0 / (hidden + outputs));
        for (int i = 0; i < b1; i++) weights[i] = random.nextGaussian() * inputScale;
        for (int i = w2; i < b2; i++) weights[i] = random.nextGaussian() * outputScale;
    }
    public int inputs() { return inputs; }
    public int hidden() { return hidden; }
    public int outputs() { return outputs; }
    public int parameters() { return weights.length; }
    public double[] weights() { return weights.clone(); }
    public void setWeights(double[] values) {
        if (values.length != weights.length) throw new IllegalArgumentException("Incorrect weight count.");
        for (double value : values) if (!Double.isFinite(value)) throw new IllegalArgumentException("Weights must be finite.");
        System.arraycopy(values, 0, weights, 0, values.length);
        java.util.Arrays.fill(firstMoment, 0); java.util.Arrays.fill(secondMoment, 0); updates = 0;
    }
    public NeuralNetwork copy() {
        NeuralNetwork copy = new NeuralNetwork(inputs, hidden, outputs, 0); copy.setWeights(weights); return copy;
    }
    public double[] predict(double[] input) { return forward(input, new double[hidden]); }
    private double[] forward(double[] input, double[] activation) {
        if (input.length != inputs) throw new IllegalArgumentException("Incorrect input count.");
        for (int j = 0; j < hidden; j++) {
            double sum = weights[b1 + j];
            for (int i = 0; i < inputs; i++) sum += weights[j * inputs + i] * input[i];
            activation[j] = Math.tanh(sum);
        }
        double[] probabilities = new double[outputs];
        double max = -Double.MAX_VALUE;
        for (int k = 0; k < outputs; k++) {
            double sum = weights[b2 + k];
            for (int j = 0; j < hidden; j++) sum += weights[w2 + k * hidden + j] * activation[j];
            probabilities[k] = sum; max = Math.max(max, sum);
        }
        double total = 0;
        for (int k = 0; k < outputs; k++) { probabilities[k] = Math.exp(probabilities[k] - max); total += probabilities[k]; }
        for (int k = 0; k < outputs; k++) probabilities[k] /= total;
        return probabilities;
    }
    /** One supervised update; returns cross entropy before updating the weights. */
    public double train(double[] input, int target, double learningRate) {
        if (target < 0 || target >= outputs || !Double.isFinite(learningRate) || learningRate <= 0)
            throw new IllegalArgumentException("Invalid training parameters.");
        double[] activation = new double[hidden];
        double[] error = forward(input, activation);
        double loss = -Math.log(Math.max(1e-12, error[target]));
        error[target] -= 1;
        double[] hiddenError = new double[hidden];
        for (int j = 0; j < hidden; j++) {
            for (int k = 0; k < outputs; k++) hiddenError[j] += error[k] * weights[w2 + k * hidden + j];
            hiddenError[j] *= 1 - activation[j] * activation[j];
        }
        updates++;
        double rate = learningRate * Math.sqrt(1 - Math.pow(0.999, updates)) / (1 - Math.pow(0.9, updates));
        for (int j = 0; j < hidden; j++) {
            for (int i = 0; i < inputs; i++) update(j * inputs + i, hiddenError[j] * input[i], rate);
            update(b1 + j, hiddenError[j], rate);
        }
        for (int k = 0; k < outputs; k++) {
            for (int j = 0; j < hidden; j++) update(w2 + k * hidden + j, error[k] * activation[j], rate);
            update(b2 + k, error[k], rate);
        }
        return loss;
    }
    private void update(int index, double gradient, double rate) {
        firstMoment[index] = 0.9 * firstMoment[index] + 0.1 * gradient;
        secondMoment[index] = 0.999 * secondMoment[index] + 0.001 * gradient * gradient;
        weights[index] -= rate * firstMoment[index] / (Math.sqrt(secondMoment[index]) + 1e-8);
    }
}
