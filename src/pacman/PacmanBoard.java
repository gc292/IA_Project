package pacman;

import arcade.GamePanel;
import arcade.Theme;
import java.awt.*;
import java.util.Arrays;
import utils.Commons;
import utils.GameController;

/** Original 15 x 15 level, with symmetric walls and continuous movement. */
public class PacmanBoard extends GamePanel {
    public static final int N_BLOCKS = 15;
    public static final int MAX_GHOSTS = 12;
    private static final int TILE = 24;
    private static final int[] DX = {0, -1, 1, 0, 0};
    private static final int[] DY = {0, 0, 0, -1, 1};
    private static final int[] WALL = {0, 1, 4, 2, 8};
    private static final int[] OPPOSITE = {0, 2, 1, 4, 3};
    private static final int[] LEVEL = {
        19, 26, 26, 26, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 22,
        21, 0, 0, 0, 17, 16, 16, 16, 16, 16, 16, 16, 16, 16, 20,
        21, 0, 0, 0, 17, 16, 16, 16, 16, 16, 16, 16, 16, 16, 20,
        21, 0, 0, 0, 17, 16, 16, 24, 16, 16, 16, 16, 16, 16, 20,
        17, 18, 18, 18, 16, 16, 20, 0, 17, 16, 16, 16, 16, 16, 20,
        17, 16, 16, 16, 16, 16, 20, 0, 17, 16, 16, 16, 16, 24, 20,
        25, 16, 16, 16, 24, 24, 28, 0, 25, 24, 24, 16, 20, 0, 21,
        1, 17, 16, 20, 0, 0, 0, 0, 0, 0, 0, 17, 20, 0, 21,
        1, 17, 16, 16, 18, 18, 22, 0, 19, 18, 18, 16, 20, 0, 21,
        1, 17, 16, 16, 16, 16, 20, 0, 17, 16, 16, 16, 20, 0, 21,
        1, 17, 16, 16, 16, 16, 20, 0, 17, 16, 16, 16, 20, 0, 21,
        1, 17, 16, 16, 16, 16, 16, 18, 16, 16, 16, 16, 20, 0, 21,
        1, 17, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 20, 0, 21,
        1, 25, 24, 24, 24, 24, 24, 24, 24, 24, 16, 16, 16, 18, 20,
        9, 8, 8, 8, 8, 8, 8, 8, 8, 8, 25, 24, 24, 24, 28
    };
    private final int[] walls = new int[225];
    private final boolean[] pellets = new boolean[225];
    private final int[] ghostX = new int[MAX_GHOSTS];
    private final int[] ghostY = new int[MAX_GHOSTS];
    private final int[] ghostDirection = new int[MAX_GHOSTS];
    private static final int GHOSTS = 4;
    private int playerX, playerY, direction, requested, score, lives, remaining;

    public PacmanBoard(GameController controller, boolean withGui, int seed) {
        super(controller, withGui, seed);
        resetGame();
    }
    @Override protected void resetGame() {
        score = 0; lives = 3; remaining = 0;
        for (int i = 0; i < 225; i++) walls[i] = LEVEL[i] == 0 ? 15 : LEVEL[i] & 15;
        // Seal the perimeter and both sides of every wall, including solid cells.
        for (int i = 0; i < 225; i++) for (int d = 1; d <= 4; d++) {
            int x = i % 15 + DX[d], y = i / 15 + DY[d];
            if (x < 0 || x >= 15 || y < 0 || y >= 15) walls[i] |= WALL[d];
            else if ((walls[i] & WALL[d]) != 0 || (walls[y * 15 + x] & WALL[OPPOSITE[d]]) != 0) {
                walls[i] |= WALL[d]; walls[y * 15 + x] |= WALL[OPPOSITE[d]];
            }
        }
        // Only place pellets in the connected, playable region.
        boolean[] reachable = new boolean[225];
        int[] queue = new int[225]; int head = 0, tail = 0;
        queue[tail++] = 172; reachable[172] = true;
        while (head < tail) {
            int pos = queue[head++];
            for (int d = 1; d <= 4; d++) if ((walls[pos] & WALL[d]) == 0) {
                int next = pos + DX[d] + 15 * DY[d];
                if (!reachable[next]) { reachable[next] = true; queue[tail++] = next; }
            }
        }
        for (int i = 0; i < 225; i++) { pellets[i] = reachable[i] && (LEVEL[i] & 16) != 0; if (pellets[i]) remaining++; }
        spawn();
    }
    private void spawn() {
        playerX = 7 * TILE; playerY = 11 * TILE; direction = 0; requested = 0;
        Arrays.fill(ghostX, -TILE); Arrays.fill(ghostY, -TILE);
        for (int i = 0; i < GHOSTS; i++) {
            ghostX[i] = (9 + i) * TILE; ghostY[i] = 2 * TILE; ghostDirection[i] = i % 2 + 1;
        }
    }
    @Override public void makeMove(int action) { requested = action >= 0 && action <= 4 ? action : 0; }
    @Override protected void updateGame() {
        if (playerX % TILE == 0 && playerY % TILE == 0) {
            int pos = playerY / TILE * 15 + playerX / TILE;
            if (pellets[pos]) { pellets[pos] = false; remaining--; score += 10; }
            if (remaining == 0) { win("Maze cleared"); return; }
            if (requested != 0 && (walls[pos] & WALL[requested]) == 0) direction = requested;
            if ((walls[pos] & WALL[direction]) != 0) direction = 0;
        }
        playerX += DX[direction] * 4; playerY += DY[direction] * 4;
        boolean caught = false;
        for (int i = 0; i < GHOSTS; i++) {
            caught |= nearGhost(i);
            if (ghostX[i] % TILE == 0 && ghostY[i] % TILE == 0) {
                int pos = ghostY[i] / TILE * 15 + ghostX[i] / TILE;
                int[] choices = new int[4]; int count = 0;
                for (int d = 1; d <= 4; d++) if ((walls[pos] & WALL[d]) == 0 && d != OPPOSITE[ghostDirection[i]]) choices[count++] = d;
                if (count == 0) ghostDirection[i] = OPPOSITE[ghostDirection[i]];
                else ghostDirection[i] = choices[random.nextInt(count)];
                if ((walls[pos] & WALL[ghostDirection[i]]) != 0) ghostDirection[i] = 0;
            }
            ghostX[i] += DX[ghostDirection[i]] * 2; ghostY[i] += DY[ghostDirection[i]] * 2;
            caught |= nearGhost(i);
        }
        if (caught) { if (--lives == 0) finish("Game over"); else spawn(); }
    }
    private boolean nearGhost(int i) { return Math.abs(playerX - ghostX[i]) < 16 && Math.abs(playerY - ghostY[i]) < 16; }
    @Override public int[] getState() {
        int[] state = new int[Commons.PACMAN_STATE_SIZE];
        for (int i = 0; i < 225; i++) { state[i] = walls[i] == 15 ? -1 : pellets[i] ? 1 : 0; state[225 + i] = walls[i]; }
        for (int i = 0; i < GHOSTS; i++) state[ghostY[i] / TILE * 15 + ghostX[i] / TILE] = 2;
        state[playerY / TILE * 15 + playerX / TILE] = 3;
        state[450] = playerX; state[451] = playerY;
        Arrays.fill(state, 452, state.length, -1);
        for (int i = 0; i < GHOSTS; i++) { state[452 + i * 2] = ghostX[i]; state[453 + i * 2] = ghostY[i]; }
        return state;
    }
    @Override public int getScore() { return score; }
    @Override public double getFitness() { return score * 1000.0 + steps; }
    @Override protected void drawGame(Graphics2D g) {
        hud(g, "PAC-MAN", score, lives + " lives  /  " + remaining + " pellets");
        g.translate(30, 94);
        g.setColor(Theme.PANEL); g.fillRoundRect(-6, -6, 372, 372, 14, 14);
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 225; i++) {
            int x = i % 15 * TILE, y = i / 15 * TILE;
            if (LEVEL[i] == 0) { g.setColor(new Color(28, 40, 63)); g.fillRoundRect(x + 2, y + 2, 20, 20, 5, 5); }
            g.setColor(new Color(91, 124, 218));
            if ((walls[i] & 1) != 0) g.drawLine(x, y, x, y + TILE);
            if ((walls[i] & 2) != 0) g.drawLine(x, y, x + TILE, y);
            if ((walls[i] & 4) != 0) g.drawLine(x + TILE, y, x + TILE, y + TILE);
            if ((walls[i] & 8) != 0) g.drawLine(x, y + TILE, x + TILE, y + TILE);
            if (pellets[i]) { g.setColor(new Color(237, 215, 166)); g.fillOval(x + 10, y + 10, 4, 4); }
        }
        int facing = direction == 1 ? 180 : direction == 3 ? 90 : direction == 4 ? 270 : 0;
        int mouth = 15 + (steps % 12) * 3;
        g.setColor(Theme.GOLD); g.fillArc(playerX + 2, playerY + 2, 20, 20, facing + mouth, 360 - mouth * 2);
        Color[] colors = {new Color(247, 126, 144), new Color(139, 171, 255), Theme.MINT, new Color(204, 151, 240)};
        for (int i = 0; i < GHOSTS; i++) {
            int x = ghostX[i], y = ghostY[i];
            g.setColor(colors[i]); g.fillRoundRect(x + 2, y + 2, 20, 20, 12, 12);
            g.setColor(Theme.TEXT); g.fillOval(x + 5, y + 7, 6, 7); g.fillOval(x + 13, y + 7, 6, 7);
            g.setColor(Theme.BACKGROUND); g.fillOval(x + 7, y + 9, 3, 4); g.fillOval(x + 15, y + 9, 3, 4);
        }
        g.translate(-30, -94);
    }
}
