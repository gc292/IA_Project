package ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Versioned text checkpoints with atomic replacement and validated network dimensions. */
public final class ModelStore {
    private static final String HEADER = "IA_ARCADE_MLP_V1";
    private static final int MAX_MODEL_BYTES = 1_000_000;

    private ModelStore() {}

    public static void save(Path path, String game, NeuralNetwork network) throws IOException {
        NeuralController.validateNetwork(game, network);

        Path destination = path.toAbsolutePath();
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".network-", ".tmp");

        try {
            Files.writeString(temporary, serialize(game, network), StandardCharsets.UTF_8);
            replaceCheckpoint(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static NeuralController load(String game, Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return read(game, input);
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Cannot load model " + path + ": " + error.getMessage(), error);
        }
    }

    public static NeuralController bundled(String game) {
        String resource = "/models/" + game + ".nn";

        try (InputStream input = ModelStore.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException(
                        "Missing bundled model: " + game + ". Rebuild the project.");
            }
            return read(game, input);
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot load bundled model.", error);
        }
    }

    private static String serialize(String game, NeuralNetwork network) {
        StringBuilder text = new StringBuilder();
        text.append(HEADER).append('\n');
        text.append(game).append('\n');
        text.append(Features.VERSION).append(' ');
        text.append(network.inputs()).append(' ');
        text.append(network.hidden()).append(' ');
        text.append(network.outputs()).append('\n');

        for (double weight : network.weights()) {
            text.append(Double.toString(weight)).append('\n');
        }
        return text.toString();
    }

    private static void replaceCheckpoint(Path temporary, Path destination) throws IOException {
        try {
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static NeuralController read(String game, InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_MODEL_BYTES + 1);
        if (bytes.length > MAX_MODEL_BYTES) {
            throw new IllegalArgumentException("Model exceeds the size limit.");
        }

        String content = new String(bytes, StandardCharsets.UTF_8);
        try (Scanner scanner = new Scanner(content).useLocale(Locale.ROOT)) {
            validateHeader(scanner, game);
            NeuralNetwork network = readNetwork(scanner, game);
            return new NeuralController(game, network);
        } catch (NoSuchElementException error) {
            throw new IllegalArgumentException("Truncated or invalid model.", error);
        }
    }

    private static void validateHeader(Scanner scanner, String expectedGame) {
        if (!scanner.next().equals(HEADER)) {
            throw new IllegalArgumentException("Unsupported model format.");
        }

        String game = scanner.next();
        if (!game.equals(expectedGame)) {
            throw new IllegalArgumentException(
                    "Model game is " + game + ", expected " + expectedGame + ".");
        }
        if (scanner.nextInt() != Features.VERSION) {
            throw new IllegalArgumentException("Incompatible feature version.");
        }
    }

    private static NeuralNetwork readNetwork(Scanner scanner, String game) {
        int inputs = scanner.nextInt();
        int hidden = scanner.nextInt();
        int outputs = scanner.nextInt();
        NeuralNetwork network = new NeuralNetwork(inputs, hidden, outputs, 0);

        if (inputs != Features.inputs(game) || outputs != Features.outputs(game)) {
            throw new IllegalArgumentException("Incompatible model dimensions.");
        }

        double[] weights = new double[network.parameters()];
        for (int index = 0; index < weights.length; index++) {
            weights[index] = scanner.nextDouble();
        }
        if (scanner.hasNext()) {
            throw new IllegalArgumentException("Unexpected trailing model data.");
        }

        network.setWeights(weights);
        return network;
    }
}
