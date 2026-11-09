package breakout;

import arcade.GamePanel;
import arcade.Theme;
import java.awt.*;
import utils.Commons;
import utils.GameController;

public class BreakoutBoard extends GamePanel {
    public static final int LEFT = 1;
    public static final int RIGHT = 2;
    private static final int PADDLE_WIDTH = 64;
    private static final int BALL_SIZE = 8;
    private final Rectangle[] bricks = new Rectangle[Commons.N_OF_BRICKS];
    private final boolean[] destroyed = new boolean[Commons.N_OF_BRICKS];
    private double ballX, ballY, vx, vy;
    private int paddleX, direction, hits, lives;

    public BreakoutBoard() { this(null, true, 42); }
    public BreakoutBoard(GameController controller, boolean withGui, int seed) {
        super(controller, withGui, seed);
        resetGame();
    }
    @Override protected void resetGame() {
        hits = 0; lives = 3; direction = 0;
        for (int i = 0; i < bricks.length; i++) {
            bricks[i] = new Rectangle(30 + (i % 7) * 52, 104 + (i / 7) * 24, 46, 16);
            destroyed[i] = false;
        }
        serve();
    }
    private void serve() {
        paddleX = 178; ballX = 206; ballY = 419;
        vx = random.nextBoolean() ? 2 : -2; vy = -3;
    }
    @Override public void makeMove(int action) { direction = action == LEFT ? -1 : action == RIGHT ? 1 : 0; }
    @Override protected void updateGame() {
        paddleX = Math.max(24, Math.min(396 - PADDLE_WIDTH, paddleX + direction * 6));
        double previousY = ballY;
        ballX += vx; ballY += vy;
        if (ballX < 24) { ballX = 24; vx = Math.abs(vx); }
        if (ballX > 396 - BALL_SIZE) { ballX = 396 - BALL_SIZE; vx = -Math.abs(vx); }
        if (ballY < 84) { ballY = 84; vy = Math.abs(vy); }
        Rectangle ball = new Rectangle((int) ballX, (int) ballY, BALL_SIZE, BALL_SIZE);
        if (vy > 0 && ball.intersects(new Rectangle(paddleX, 438, PADDLE_WIDTH, 10))) {
            ballY = 438 - BALL_SIZE;
            vx = ((ballX + BALL_SIZE / 2.0 - paddleX) / PADDLE_WIDTH - 0.5) * 6;
            if (Math.abs(vx) < 0.6) vx = vx < 0 ? -0.6 : 0.6;
            vy = -3;
        }
        for (int i = 0; i < bricks.length; i++) {
            if (!destroyed[i] && ball.intersects(bricks[i])) {
                destroyed[i] = true; hits++;
                if (previousY + BALL_SIZE <= bricks[i].y || previousY >= bricks[i].getMaxY()) vy = -vy;
                else vx = -vx;
                if (hits == bricks.length) win("Level cleared");
                break;
            }
        }
        if (ballY > Commons.BOTTOM_EDGE) {
            if (--lives == 0) finish("Game over"); else serve();
        }
    }
    @Override public int[] getState() {
        Rectangle target = new Rectangle(-1, -1, 0, 0);
        double distance = Double.MAX_VALUE;
        for (int i = 0; i < bricks.length; i++) {
            double candidate = Math.hypot(bricks[i].getCenterX() - ballX, bricks[i].y - ballY);
            if (!destroyed[i] && candidate < distance) { target = bricks[i]; distance = candidate; }
        }
        return new int[] {(int) ballX, (int) ballY, (int) Math.signum(vy), paddleX, 438, target.x, target.y, (int) Math.round(vx)};
    }
    @Override public int getScore() { return hits * 100; }
    @Override public double getFitness() { return getScore() * 1000.0 + steps; }
    @Override protected void drawGame(Graphics2D g) {
        hud(g, "BREAKOUT", getScore(), lives + " lives  /  " + (bricks.length - hits) + " bricks");
        g.setColor(Theme.PANEL); g.fillRoundRect(24, 84, 372, 386, 16, 16);
        for (int x = 42; x < 390; x += 20) for (int y = 96; y < 465; y += 20) {
            g.setColor(Theme.LINE); g.fillOval(x, y, 2, 2);
        }
        Color[] colors = {new Color(244, 139, 120), Theme.GOLD, new Color(132, 160, 249), Theme.MINT};
        for (int i = 0; i < bricks.length; i++) if (!destroyed[i]) {
            Rectangle b = bricks[i]; g.setColor(colors[i / 7]); g.fillRoundRect(b.x, b.y, b.width, b.height, 6, 6);
        }
        g.setColor(new Color(111, 235, 191, 35)); g.fillRoundRect(paddleX - 4, 434, PADDLE_WIDTH + 8, 18, 12, 12);
        g.setColor(Theme.MINT); g.fillRoundRect(paddleX, 438, PADDLE_WIDTH, 10, 10, 10);
        g.setColor(new Color(255, 255, 255, 28)); g.fillOval((int) (ballX - vx * 2), (int) (ballY - vy * 2), 10, 10);
        g.setColor(Theme.TEXT); g.fillOval((int) ballX, (int) ballY, BALL_SIZE, BALL_SIZE);
    }
}
