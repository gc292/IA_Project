package arcade;

import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import javax.swing.*;
import utils.Commons;
import utils.GameController;

/** Fixed-step simulation shared by interactive play and bounded, reproducible runs. */
public abstract class GamePanel extends JPanel {
    protected final GameController controller;
    protected final boolean withGui;
    protected final Random random = new Random();
    protected boolean running = true;
    protected boolean paused;
    protected boolean won;
    protected String result = "Game over";
    protected int steps;
    protected int seed;
    private int maxSteps = 20_000;
    private int horizontal;
    private final boolean[] held = new boolean[5];
    private final Timer timer;

    protected GamePanel(GameController controller, boolean withGui, int seed) {
        this.controller = controller;
        this.withGui = withGui;
        this.seed = seed;
        random.setSeed(seed);
        setPreferredSize(new Dimension(Commons.WIDTH, Commons.HEIGHT));
        setBackground(Theme.BACKGROUND);
        setFocusable(true);
        timer = new Timer(Commons.PERIOD, e -> { tick(); repaint(); });
        bind("LEFT", 1); bind("A", 1); bind("RIGHT", 2); bind("D", 2);
        bind("UP", 3); bind("W", 3); bind("DOWN", 4); bind("S", 4);
        action("SPACE", "pause", this::togglePause);
        action("P", "pause-p", this::togglePause);
        action("R", "restart", this::restart);
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { clearInput(); }
        });
    }

    private void bind(String key, int move) {
        action("pressed " + key, "press-" + key, () -> {
            held[move] = true;
            if (move <= 2) horizontal = move;
            makeMove(move);
        });
        action("released " + key, "release-" + key, () -> {
            held[move] = false;
            if (move <= 2) {
                horizontal = held[1] ? 1 : held[2] ? 2 : 0;
                makeMove(horizontal);
            }
        });
    }

    private void action(String key, String name, Runnable callback) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (controller == null || name.startsWith("pause") || name.equals("restart")) callback.run();
            }
        });
    }

    private void clearInput() {
        java.util.Arrays.fill(held, false);
        horizontal = 0;
        if (controller == null) makeMove(0);
    }

    public final void tick() {
        if (!running || paused) return;
        if (controller != null) makeMove(controller.nextMove(getState()));
        updateGame();
        steps++;
        if (steps >= maxSteps && running) finish("Step limit reached");
    }

    public final void restart() {
        steps = 0;
        running = true;
        paused = false;
        won = false;
        random.setSeed(seed);
        if (controller != null) controller.reset(seed);
        clearInput();
        resetGame();
        repaint();
        requestFocusInWindow();
    }

    public final void togglePause() { if (running) paused = !paused; repaint(); }
    public final void setSeed(int seed) { this.seed = seed; random.setSeed(seed); }
    public final void setMaxSteps(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Step limit must be positive.");
        maxSteps = limit;
    }
    public final int getSteps() { return steps; }
    public final boolean isRunning() { return running; }
    public final boolean isPaused() { return paused; }
    public final boolean isWon() { return won; }
    protected final void win(String message) { won = true; finish(message); }
    public final void runSimulation() {
        if (withGui) throw new IllegalStateException("Use withGui=false for simulation.");
        if (controller == null) throw new IllegalStateException("Simulation requires a controller.");
        if (paused) throw new IllegalStateException("Resume the simulation before running it.");
        while (running) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Simulation cancelled.");
            tick();
        }
    }
    protected final void finish(String message) { running = false; result = message; }
    public final void dispose() { timer.stop(); }
    @Override public void addNotify() { super.addNotify(); if (withGui) timer.start(); }
    @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

    @Override protected final void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        Theme.quality(g);
        double scale = Math.min(getWidth() / (double) Commons.WIDTH, getHeight() / (double) Commons.HEIGHT);
        g.translate((getWidth() - Commons.WIDTH * scale) / 2, (getHeight() - Commons.HEIGHT * scale) / 2);
        g.scale(scale, scale);
        drawGame(g);
        if (paused || !running) {
            g.setColor(new Color(12, 17, 27, 215));
            g.fillRoundRect(40, 195, 340, 114, 20, 20);
            g.setFont(Theme.font(Font.BOLD, 24));
            g.setColor(Theme.TEXT);
            center(g, paused ? "Paused" : result, 240);
            g.setFont(Theme.font(Font.PLAIN, 12));
            g.setColor(Theme.MUTED);
            center(g, paused ? "Press Space to resume" : "Press R to restart", 272);
        }
        g.dispose();
    }

    protected final void hud(Graphics2D g, String title, int score, String detail) {
        g.setFont(Theme.font(Font.BOLD, 12)); g.setColor(Theme.MUTED); g.drawString(title, 24, 25);
        g.setFont(Theme.font(Font.BOLD, 25)); g.setColor(Theme.TEXT); g.drawString(String.format("%04d", score), 24, 56);
        g.setFont(Theme.font(Font.PLAIN, 11)); g.setColor(Theme.MUTED); g.drawString(detail, 230, 48);
        g.setColor(Theme.LINE); g.drawLine(24, 72, 396, 72);
        g.setFont(Theme.font(Font.PLAIN, 10)); g.setColor(Theme.MUTED);
        center(g, controller == null ? "ARROWS / WASD   ·   SPACE pause   ·   R restart" : controller instanceof ai.NeuralController ? "NEURAL POLICY   ·   SPACE pause   ·   R restart" : "BASELINE POLICY   ·   SPACE pause   ·   R restart", 492);
    }
    private void center(Graphics2D g, String text, int y) {
        g.drawString(text, (Commons.WIDTH - g.getFontMetrics().stringWidth(text)) / 2, y);
    }
    public abstract void makeMove(int action);
    public abstract int[] getState();
    public abstract int getScore();
    public abstract double getFitness();
    protected abstract void resetGame();
    protected abstract void updateGame();
    protected abstract void drawGame(Graphics2D g);
}
