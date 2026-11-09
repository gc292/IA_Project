package arcade;

import ai.*;
import java.nio.file.Path;
import utils.GameController;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Desktop entry point. No third-party runtime dependencies. */
public final class Launcher {
    private JFrame frame;
    private JPanel stage;
    private GamePanel board;
    private final JComboBox<String> game = new JComboBox<>(new String[] {"Breakout", "Pac-Man"});
    private final JComboBox<String> mode = new JComboBox<>(new String[] {"Manual", "Neural network", "Heuristic", "Random"});
    private final JSpinner seed = new JSpinner(new SpinnerNumberModel(42, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
    private JLabel status;
    private final java.util.Map<String, Path> models = new java.util.HashMap<>();

    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            if (options.help) { System.out.println(Options.HELP); return; }
            if (options.train) {
                try {
                    Trainer.train(new Trainer.Config(options.game, options.seed, options.episodes, options.epochs, options.steps,
                        options.model == null ? Path.of("models", options.game + ".nn") : options.model), System.out::println);
                } catch (java.io.IOException e) { throw new IllegalArgumentException("Cannot save training output: " + e.getMessage(), e); }
                return;
            }
            if (options.benchmark) {
                NeuralController neural = options.model == null ? ModelStore.bundled(options.game) : ModelStore.load(options.game, options.model);
                System.out.println("game,controller,episodes,first_seed,max_steps,mean_score,wins,mean_steps");
                Experiments.benchmark(options.game, neural.network(), options.episodes, options.seed, options.steps, System.out::println);
                return;
            }
            if (options.headless) {
                GamePanel panel = createBoard(options.game, options.controller, false, options.seed, options.model);
                panel.setMaxSteps(options.steps);
                panel.runSimulation();
                System.out.printf("game=%s controller=%s seed=%d steps=%d score=%d fitness=%.0f%n",
                    options.game, options.controller, options.seed, panel.getSteps(), panel.getScore(), panel.getFitness());
                return;
            }
            if (GraphicsEnvironment.isHeadless()) throw new IllegalArgumentException("No display available. Use --headless or --train.");
            SwingUtilities.invokeLater(() -> new Launcher().open(options));
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage() + "\nUse --help for options.");
            System.exit(2);
        }
    }
    public static GamePanel createBoard(String game, String mode, boolean gui, int seed) {
        return createBoard(game, mode, gui, seed, null);
    }
    public static GamePanel createBoard(String game, String mode, boolean gui, int seed, Path model) {
        GameController controller = GameFactory.controller(game, mode, seed, model);
        if (!gui && controller == null) throw new IllegalArgumentException("Headless mode requires --controller neural, auto or random.");
        return GameFactory.board(game, controller, gui, seed);
    }
    private void open(Options options) {
        UIManager.put("Label.foreground", Theme.TEXT);
        UIManager.put("Panel.background", Theme.BACKGROUND);
        frame = new JFrame("AI Arcade · Breakout & Pac-Man");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent e) { if (board != null) board.dispose(); } });
        JPanel root = new JPanel(new BorderLayout(30, 0));
        root.setBorder(new EmptyBorder(28, 28, 24, 28));
        root.setBackground(Theme.BACKGROUND);
        root.add(sidebar(), BorderLayout.WEST);
        stage = new JPanel(new BorderLayout());
        stage.setBackground(Theme.BACKGROUND);
        root.add(stage, BorderLayout.CENTER);
        frame.setContentPane(root);
        frame.setMinimumSize(new Dimension(920, 810));
        frame.setSize(1000, 810);
        frame.setLocationRelativeTo(null);
        game.setSelectedIndex(options.game.equals("pacman") ? 1 : 0);
        mode.setSelectedIndex(options.controller.equals("neural") ? 1 : options.controller.equals("auto") ? 2 : options.controller.equals("random") ? 3 : 0);
        if (options.model != null) models.put(options.game, options.model);
        seed.setValue(options.seed);
        home();
        frame.setVisible(true);
    }
    private JPanel sidebar() {
        JPanel side = new JPanel(); side.setBackground(Theme.PANEL);
        side.setBorder(new EmptyBorder(25, 24, 25, 24));
        side.setPreferredSize(new Dimension(250, 0));
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.add(label("●  NEURAL CONTROL", 10, Theme.MINT, true));
        side.add(Box.createVerticalStrut(22));
        side.add(label("AI Arcade", 30, Theme.TEXT, true));
        side.add(Box.createVerticalStrut(8));
        side.add(label("Neural policies for classic games.", 12, Theme.MUTED, false));
        side.add(Box.createVerticalStrut(36));
        field(side, "01   GAME", game);
        field(side, "02   CONTROLLER", mode);
        field(side, "03   SEED", seed);
        side.add(Box.createVerticalStrut(6));
        side.add(button("Start game   →", this::start, true));
        side.add(Box.createVerticalStrut(12));
        side.add(button("Train network", this::train, false));
        side.add(Box.createVerticalStrut(12));
        side.add(button("Load model…", this::loadModel, false));
        side.add(Box.createVerticalStrut(12));
        side.add(button("Pause / resume", () -> { if (board != null) board.togglePause(); }, false));
        side.add(Box.createVerticalStrut(12));
        side.add(button("Restart", () -> { if (board != null) board.restart(); }, false));
        side.add(Box.createVerticalStrut(12));
        side.add(button("Main menu", this::home, false));
        side.add(Box.createVerticalGlue());
        status = label("READY", 10, Theme.MINT, true); side.add(status);
        side.add(Box.createVerticalStrut(10));
        side.add(label("Java / Swing  ·  Neural agents", 10, Theme.MUTED, false));
        return side;
    }
    private void field(JPanel panel, String title, JComponent input) {
        panel.add(label(title, 10, Theme.MUTED, true));
        panel.add(Box.createVerticalStrut(9));
        input.setFont(Theme.font(Font.PLAIN, 13));
        input.setBackground(Theme.BACKGROUND); input.setForeground(Theme.TEXT);
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        input.setPreferredSize(new Dimension(202, 36));
        input.setAlignmentX(Component.LEFT_ALIGNMENT);
        input.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        if (input instanceof JComboBox<?>) {
            JComboBox<?> combo = (JComboBox<?>) input;
            combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
                @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean focus) {
                    g.setColor(Theme.BACKGROUND);
                    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                }
                @Override public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean focus) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    Theme.quality(g); g.setFont(comboBox.getFont()); g.setColor(Theme.TEXT);
                    FontMetrics metrics = g.getFontMetrics();
                    g.drawString(String.valueOf(comboBox.getSelectedItem()), bounds.x + 8,
                        bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent());
                    g.dispose();
                }
                @Override protected JButton createArrowButton() {
                    JButton arrow = new JButton("▾");
                    arrow.setBackground(Theme.LINE); arrow.setForeground(Theme.MINT);
                    arrow.setBorder(new EmptyBorder(0, 8, 0, 8)); arrow.setFocusPainted(false);
                    return arrow;
                }
            });
            combo.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                    JLabel item = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                    item.setBackground(selected && index >= 0 ? Theme.LINE : Theme.BACKGROUND);
                    item.setForeground(Theme.TEXT); item.setBorder(new EmptyBorder(6, 8, 6, 8));
                    return item;
                }
            });
        }
        if (input instanceof JSpinner) {
            JFormattedTextField text = ((JSpinner.DefaultEditor) ((JSpinner) input).getEditor()).getTextField();
            text.setBackground(Theme.BACKGROUND); text.setForeground(Theme.TEXT); text.setCaretColor(Theme.MINT);
        }
        panel.add(input); panel.add(Box.createVerticalStrut(22));
    }
    private JLabel label(String value, int size, Color color, boolean bold) {
        JLabel label = new JLabel(value); label.setFont(Theme.font(bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color); label.setAlignmentX(Component.LEFT_ALIGNMENT); return label;
    }
    private JButton button(String title, Runnable action, boolean primary) {
        JButton button = new JButton(title);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 39)); button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setFont(Theme.font(Font.BOLD, 12)); button.setFocusPainted(false);
        button.setBackground(primary ? Theme.MINT : Theme.LINE); button.setForeground(primary ? Theme.BACKGROUND : Theme.TEXT);
        button.setBorder(new EmptyBorder(10, 12, 10, 12)); button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run()); return button;
    }
    private void start() {
        try { seed.commitEdit(); } catch (java.text.ParseException e) {
            status.setText("INVALID SEED"); return;
        }
        String selectedGame = game.getSelectedIndex() == 0 ? "breakout" : "pacman";
        String selectedMode = new String[] {"manual", "neural", "auto", "random"}[mode.getSelectedIndex()];
        try {
            GamePanel next = createBoard(selectedGame, selectedMode, true, ((Number) seed.getValue()).intValue(), models.get(selectedGame));
            if (board != null) board.dispose();
            board = next;
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Cannot start game", JOptionPane.ERROR_MESSAGE); return;
        }
        stage.removeAll(); stage.add(board, BorderLayout.CENTER); stage.revalidate(); stage.repaint();
        status.setText(selectedGame.toUpperCase() + "  /  " + mode.getSelectedItem().toString().toUpperCase());
        board.requestFocusInWindow();
    }
    private void train() {
        String selected = game.getSelectedIndex() == 0 ? "breakout" : "pacman";
        try { seed.commitEdit(); } catch (java.text.ParseException e) { status.setText("INVALID SEED"); return; }
        if (board != null && board.isRunning() && !board.isPaused()) board.togglePause();
        new TrainingDialog(frame, selected, ((Number) seed.getValue()).intValue(), path -> {
            models.put(selected, path); mode.setSelectedIndex(1); status.setText("TRAINED MODEL LOADED");
        }).setVisible(true);
    }
    private void loadModel() {
        String selected = game.getSelectedIndex() == 0 ? "breakout" : "pacman";
        JFileChooser chooser = new JFileChooser("models");
        chooser.setDialogTitle("Load neural model · " + selected);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Neural model (*.nn)", "nn"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            try {
                ModelStore.load(selected, path); models.put(selected, path); mode.setSelectedIndex(1);
                status.setText("MODEL LOADED");
            } catch (IllegalArgumentException e) { JOptionPane.showMessageDialog(frame, e.getMessage(), "Incompatible model", JOptionPane.ERROR_MESSAGE); }
        }
    }
    private void home() {
        if (board != null) { board.dispose(); board = null; }
        stage.removeAll();
        stage.add(new Welcome(), BorderLayout.CENTER);
        stage.revalidate(); stage.repaint();
        status.setText("READY");
    }
    private static final class Welcome extends JPanel {
        Welcome() { setBackground(Theme.BACKGROUND); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create(); Theme.quality(g);
            double s = Math.min(getWidth() / 500.0, getHeight() / 600.0);
            g.translate((getWidth() - 500 * s) / 2, (getHeight() - 600 * s) / 2); g.scale(s, s);
            g.setColor(Theme.MINT); g.setFont(Theme.font(Font.BOLD, 11)); g.drawString("IMITATION LEARNING", 16, 38);
            g.setColor(Theme.TEXT); g.setFont(Theme.font(Font.BOLD, 40)); g.drawString("Neural game agents", 16, 96);
            g.setColor(Theme.MUTED); g.setFont(Theme.font(Font.PLAIN, 14));
            g.drawString("Train and run policies for Breakout and Pac-Man.", 16, 128);
            card(g, 166, "01", "Breakout", "Clear 28 bricks with a learned control policy.", Theme.MINT);
            Color[] colors = {new Color(244, 139, 120), Theme.GOLD, new Color(132, 160, 249)};
            for (int row = 0; row < 3; row++) for (int col = 0; col < 7; col++) {
                g.setColor(colors[row]); g.fillRoundRect(44 + col * 38, 235 + row * 14, 32, 9, 4, 4);
            }
            g.setColor(Theme.TEXT); g.fillOval(354, 248, 9, 9);
            g.setColor(Theme.MINT); g.fillRoundRect(322, 291, 68, 7, 6, 6);
            card(g, 343, "02", "Pac-Man", "Collect pellets and avoid four moving ghosts.", Theme.GOLD);
            g.setColor(Theme.GOLD); g.fillArc(48, 419, 42, 42, 35, 290);
            for (int i = 0; i < 8; i++) { g.setColor(Theme.MUTED); g.fillOval(113 + i * 28, 437, 4, 4); }
            g.setColor(new Color(247, 126, 144)); g.fillRoundRect(367, 421, 34, 36, 18, 18);
            g.setColor(Theme.TEXT); g.fillOval(373, 430, 8, 10); g.fillOval(387, 430, 8, 10);
            g.setColor(Theme.BACKGROUND); g.fillOval(375, 433, 4, 5); g.fillOval(389, 433, 4, 5);
            g.setColor(Theme.MUTED); g.setFont(Theme.font(Font.PLAIN, 12));
            g.drawString("← ↑ ↓ →  move     /     Space  pause     /     R  restart", 16, 554);
            g.setFont(Theme.font(Font.PLAIN, 11)); g.drawString("Select a game and controller, then start a session.", 16, 580);
            g.dispose();
        }
        private void card(Graphics2D g, int y, String number, String title, String description, Color accent) {
            g.setColor(Theme.PANEL); g.fillRoundRect(16, y, 468, 155, 18, 18);
            g.setColor(accent); g.setFont(Theme.font(Font.BOLD, 11)); g.drawString(number, 36, y + 31);
            g.setColor(Theme.TEXT); g.setFont(Theme.font(Font.BOLD, 21)); g.drawString(title, 68, y + 34);
            g.setColor(Theme.MUTED); g.setFont(Theme.font(Font.PLAIN, 12)); g.drawString(description, 36, y + 58);
        }
    }
    static final class Options {
        static final String HELP = "AI Arcade\nUsage: java -jar build/ia-arcade.jar [options]\n"
            + "  --game breakout|pacman              Game (breakout)\n"
            + "  --controller manual|neural|auto|random Controller (neural)\n"
            + "  --model PATH                       External .nn model (optional)\n"
            + "  --seed INTEGER                     Random seed (42)\n"
            + "  --steps POSITIVE_INTEGER           Step limit per episode (20000)\n"
            + "  --headless                         Run without a display\n"
            + "  --train                            Train a policy and save metrics\n"
            + "  --benchmark                        Compare neural, heuristic and random\n"
            + "  --episodes INTEGER                 Episodes per round / benchmark (32)\n"
            + "  --epochs INTEGER                   Training epochs (30)\n"
            + "  --help                             Show help";
        String game = "breakout", controller = "neural";
        Path model;
        int seed = 42, steps = 20_000, episodes = 32, epochs = 30;
        boolean headless, help, train, benchmark;
        static Options parse(String[] args) {
            Options result = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("--headless")) { result.headless = true; continue; }
                if (arg.equals("--train")) { result.train = true; continue; }
                if (arg.equals("--benchmark")) { result.benchmark = true; continue; }
                if (arg.equals("--help") || arg.equals("-h")) { result.help = true; continue; }
                if (!java.util.Set.of("--game", "--controller", "--seed", "--steps", "--model", "--episodes", "--epochs").contains(arg))
                    throw new IllegalArgumentException("Unknown option: " + arg);
                if (++i >= args.length) throw new IllegalArgumentException("Missing value for " + arg);
                String value = args[i];
                switch (arg) {
                    case "--game": result.game = value; break;
                    case "--controller": result.controller = value; break;
                    case "--seed": result.seed = Integer.parseInt(value); break;
                    case "--steps": result.steps = Integer.parseInt(value); break;
                    case "--model": result.model = Path.of(value); break;
                    case "--episodes": result.episodes = Integer.parseInt(value); break;
                    case "--epochs": result.epochs = Integer.parseInt(value); break;
                    default: break;
                }
            }
            if (!java.util.Set.of("breakout", "pacman").contains(result.game)) throw new IllegalArgumentException("Unknown game: " + result.game);
            if (!java.util.Set.of("manual", "neural", "auto", "random").contains(result.controller)) throw new IllegalArgumentException("Unknown controller: " + result.controller);
            if (result.steps < 1 || result.episodes < 1 || result.epochs < 1) throw new IllegalArgumentException("Steps, episodes and epochs must be positive.");
            if (result.train && result.benchmark) throw new IllegalArgumentException("Use --train and --benchmark separately.");
            return result;
        }
    }
}
