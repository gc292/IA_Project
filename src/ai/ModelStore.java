package ai;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.Scanner;

/** Small, deterministic text checkpoint with a feature-schema and game identifier. */
public final class ModelStore {
    private static final String HEADER = "IA_ARCADE_MLP_V1";
    private ModelStore() { }
    public static void save(Path path, String game, NeuralNetwork network) throws IOException {
        new NeuralController(game, network); // Reject incompatible shapes before writing anything.
        Path absolute = path.toAbsolutePath(); Files.createDirectories(absolute.getParent());
        StringBuilder text = new StringBuilder(HEADER).append('\n').append(game).append('\n');
        text.append(Features.VERSION).append(' ').append(network.inputs()).append(' ').append(network.hidden()).append(' ').append(network.outputs()).append('\n');
        for (double value : network.weights()) text.append(Double.toString(value)).append('\n');
        Path temporary = Files.createTempFile(absolute.getParent(), ".network-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public static NeuralController load(String game, Path path) {
        try (InputStream input = Files.newInputStream(path)) { return read(game, input); }
        catch (IOException | IllegalArgumentException e) { throw new IllegalArgumentException("Cannot load model " + path + ": " + e.getMessage(), e); }
    }
    public static NeuralController bundled(String game) {
        String resource = "/models/" + game + ".nn";
        try (InputStream input = ModelStore.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalArgumentException("Missing bundled model: " + game + ". Rebuild the project.");
            return read(game, input);
        } catch (IOException e) { throw new IllegalArgumentException("Cannot load bundled model.", e); }
    }
    private static NeuralController read(String expectedGame, InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(1_000_001);
        if (bytes.length > 1_000_000) throw new IllegalArgumentException("Model exceeds the size limit.");
        try (Scanner scanner = new Scanner(new String(bytes, StandardCharsets.UTF_8)).useLocale(Locale.ROOT)) {
            if (!scanner.next().equals(HEADER)) throw new IllegalArgumentException("Unsupported model format.");
            String game = scanner.next();
            if (!game.equals(expectedGame)) throw new IllegalArgumentException("Model game is " + game + ", expected " + expectedGame + ".");
            if (scanner.nextInt() != Features.VERSION) throw new IllegalArgumentException("Incompatible feature version.");
            NeuralNetwork network = new NeuralNetwork(scanner.nextInt(), scanner.nextInt(), scanner.nextInt(), 0);
            if (network.inputs() != Features.inputs(game) || network.outputs() != Features.outputs(game)) throw new IllegalArgumentException("Incompatible model dimensions.");
            double[] weights = new double[network.parameters()];
            for (int i = 0; i < weights.length; i++) weights[i] = scanner.nextDouble();
            if (scanner.hasNext()) throw new IllegalArgumentException("Unexpected trailing model data.");
            network.setWeights(weights);
            return new NeuralController(game, network);
        } catch (java.util.NoSuchElementException e) { throw new IllegalArgumentException("Truncated or invalid model.", e); }
    }
}
