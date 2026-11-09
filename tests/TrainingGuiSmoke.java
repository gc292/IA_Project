import ai.ModelStore;
import arcade.GamePanel;
import arcade.Launcher;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Exercises UI training, background execution and loading the resulting checkpoint. */
public final class TrainingGuiSmoke {
    private static JFrame frame;
    private static JDialog dialog;
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("ai-gui-training-");
        Path model = directory.resolve("gui-trained.nn");
        Launcher.main(new String[0]);
        SwingUtilities.invokeAndWait(() -> {
            for (Frame candidate : Frame.getFrames()) if (candidate.isShowing()) frame = (JFrame) candidate;
            if (frame == null) throw new AssertionError("Launcher missing");
            button(frame, "Train network").doClick();
            for (Window child : frame.getOwnedWindows()) if (child instanceof JDialog && child.isVisible()) dialog = (JDialog) child;
            if (dialog == null) throw new AssertionError("Training dialog missing");
            java.util.List<JSpinner> spinners = new ArrayList<>(); collect(dialog, JSpinner.class, spinners);
            spinners.get(0).setValue(4); spinners.get(1).setValue(9);
            java.util.List<JTextField> fields = new ArrayList<>(); collect(dialog, JTextField.class, fields);
            for (JTextField field : fields) if (!(field instanceof JFormattedTextField)) field.setText(model.toString());
            button(dialog, "Train").doClick();
            if (button(dialog, "Train").isEnabled()) throw new AssertionError("Train button must be disabled while running");
        });
        try {
            long heartbeat = System.nanoTime();
            SwingUtilities.invokeAndWait(() -> { if (!frame.isDisplayable()) throw new AssertionError("Main UI closed during training"); });
            if ((System.nanoTime() - heartbeat) > 2_000_000_000L) throw new AssertionError("Training blocks the event thread");
            boolean[] done = {false};
            long deadline = System.nanoTime() + 30_000_000_000L;
            while (!done[0] && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait(() -> {
                    java.util.List<JTextArea> areas = new ArrayList<>(); collect(dialog, JTextArea.class, areas);
                    for (JTextArea area : areas) {
                        if (area.getText().contains("Error:")) throw new AssertionError(area.getText());
                        if (area.getText().contains("Training complete.")) done[0] = true;
                    }
                });
                if (!done[0]) Thread.sleep(50);
            }
            if (!done[0] || !Files.exists(model)) throw new AssertionError("UI training did not finish");
            SwingUtilities.invokeAndWait(() -> {
                try {
                    Files.createDirectories(Path.of("build", "screenshots"));
                    BufferedImage image = new BufferedImage(dialog.getContentPane().getWidth(), dialog.getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = image.createGraphics(); dialog.getContentPane().paint(g); g.dispose();
                    ImageIO.write(image, "png", Path.of("build", "screenshots", "training.png").toFile());
                } catch (java.io.IOException e) { throw new RuntimeException(e); }
                dialog.dispose();
                button(frame, "Start game   →").doClick();
                java.util.List<GamePanel> panels = new ArrayList<>(); collect(frame, GamePanel.class, panels);
                if (panels.size() != 1) throw new AssertionError("Missing board after training");
                GamePanel actual = panels.get(0);
                actual.restart();
                GamePanel expected = new breakout.BreakoutBoard(ModelStore.load("breakout", model), false, 42);
                for (int i = 0; i < 100; i++) { actual.tick(); expected.tick(); }
                if (!Arrays.equals(actual.getState(), expected.getState())) throw new AssertionError("Launcher did not load the trained model");
                actual.togglePause();
            });
            System.out.println("PASS: UI training, responsive event thread, metrics, checkpoint save and immediate model loading.");
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (dialog != null) dialog.dispose(); if (frame != null) frame.dispose(); });
            try (var files = Files.walk(directory)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }
    private static JButton button(Container root, String text) {
        java.util.List<JButton> buttons = new ArrayList<>(); collect(root, JButton.class, buttons);
        for (JButton button : buttons) if (text.equals(button.getText())) return button;
        throw new AssertionError("Missing button: " + text);
    }
    private static <T> void collect(Container root, Class<T> type, java.util.List<T> found) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) found.add(type.cast(child));
            if (child instanceof Container) collect((Container) child, type, found);
        }
    }
}
