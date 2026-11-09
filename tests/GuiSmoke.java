import arcade.Launcher;
import arcade.GamePanel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Optional desktop smoke test. Run only with a display; not part of headless CI. */
public final class GuiSmoke {
    private static JFrame frame;
    private static GamePanel board;
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("GUI test requires a display");
        Robot robot = new Robot(); robot.setAutoDelay(100);
        Launcher.main(new String[0]);
        robot.delay(1000);
        SwingUtilities.invokeAndWait(() -> {
            for (Frame candidate : Frame.getFrames()) if (candidate.isShowing()) frame = (JFrame) candidate;
            if (frame == null) throw new AssertionError("Launcher window missing");
        });
        capture("menu");
        SwingUtilities.invokeAndWait(() -> {
            java.util.List<JComboBox> combos = new java.util.ArrayList<>(); collect(frame, JComboBox.class, combos);
            combos.get(1).setSelectedIndex(0);
        });
        try {
            click(robot, "Start game   →"); robot.delay(400);
            findBoard();
            Point[] focus = new Point[1];
            SwingUtilities.invokeAndWait(() -> {
                frame.toFront();
                Point point = board.getLocationOnScreen();
                focus[0] = new Point(point.x + board.getWidth() / 2, point.y + board.getHeight() / 2);
            });
            robot.mouseMove(focus[0].x, focus[0].y);
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(250);
            final int[] before = new int[1];
            SwingUtilities.invokeAndWait(() -> { board.togglePause(); board.restart(); board.requestFocusInWindow(); before[0] = board.getState()[3]; });
            robot.keyPress(KeyEvent.VK_LEFT); robot.delay(150); robot.keyRelease(KeyEvent.VK_LEFT);
            SwingUtilities.invokeAndWait(() -> { if (board.getState()[3] >= before[0]) throw new AssertionError("Arrow key did not move paddle"); });
            robot.keyPress(KeyEvent.VK_SPACE); robot.keyRelease(KeyEvent.VK_SPACE);
            SwingUtilities.invokeAndWait(() -> { if (!board.isPaused()) throw new AssertionError("Space did not pause"); before[0] = board.getSteps(); });
            robot.delay(200);
            SwingUtilities.invokeAndWait(() -> { if (board.getSteps() != before[0]) throw new AssertionError("Pause did not freeze timer"); });
            click(robot, "Restart");
            SwingUtilities.invokeAndWait(() -> { if (board.isPaused() || !board.isRunning()) throw new AssertionError("Restart failed"); });
            SwingUtilities.invokeAndWait(() -> {
                java.util.List<JComboBox> combos = new java.util.ArrayList<>(); collect(frame, JComboBox.class, combos);
                combos.get(1).setSelectedIndex(1);
            });
            click(robot, "Start game   →"); robot.delay(1700); findBoard();
            SwingUtilities.invokeAndWait(() -> { if (board.getScore() <= 0) throw new AssertionError("Breakout neural did not break bricks"); });
            capture("breakout");
            click(robot, "Main menu");
            SwingUtilities.invokeAndWait(() -> {
                java.util.List<JComboBox> combos = new java.util.ArrayList<>();
                collect(frame, JComboBox.class, combos);
                combos.get(0).setSelectedIndex(1); combos.get(1).setSelectedIndex(1);
            });
            click(robot, "Start game   →"); robot.delay(1500); findBoard();
            SwingUtilities.invokeAndWait(() -> { if (board.getScore() <= 0) throw new AssertionError("Pac-Man neural did not collect pellets"); });
            capture("pacman");
            click(robot, "Main menu");
            final int[] stopped = new int[1]; SwingUtilities.invokeAndWait(() -> stopped[0] = board.getSteps());
            robot.delay(200);
            SwingUtilities.invokeAndWait(() -> { if (board.getSteps() != stopped[0]) throw new AssertionError("Timer leaked after returning to menu"); });
            System.out.println("PASS: GUI launcher, manual keyboard, pause, restart, both games, screenshots, timer cleanup.");
        } finally { SwingUtilities.invokeAndWait(() -> frame.dispose()); }
    }
    private static void click(Robot robot, String text) throws Exception {
        Point[] location = new Point[1];
        SwingUtilities.invokeAndWait(() -> {
            java.util.List<JButton> buttons = new java.util.ArrayList<>(); collect(frame, JButton.class, buttons);
            for (JButton button : buttons) if (button.getText().equals(text)) {
                Point p = button.getLocationOnScreen(); location[0] = new Point(p.x + button.getWidth() / 2, p.y + button.getHeight() / 2);
            }
            if (location[0] == null) throw new AssertionError("Missing button: " + text);
        });
        robot.mouseMove(location[0].x, location[0].y);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(150);
    }
    private static void findBoard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            java.util.List<GamePanel> boards = new java.util.ArrayList<>(); collect(frame, GamePanel.class, boards);
            if (boards.size() != 1) throw new AssertionError("Expected one active game"); board = boards.get(0);
        });
    }
    private static <T> void collect(Container root, Class<T> type, java.util.List<T> found) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) found.add(type.cast(child));
            if (child instanceof Container) collect((Container) child, type, found);
        }
    }
    private static void capture(String name) throws Exception {
        Files.createDirectories(Path.of("build", "screenshots"));
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(frame.getContentPane().getWidth(), frame.getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); frame.getContentPane().paint(g); g.dispose();
            try { ImageIO.write(image, "png", Path.of("build", "screenshots", name + ".png").toFile()); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
        });
    }
}
