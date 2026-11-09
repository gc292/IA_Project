package breakout;

import utils.GameController;

/** A transparent baseline: follows the ball with a small dead zone. */
public final class TrackingController implements GameController {
    @Override public int nextMove(int[] state) {
        int delta = state[0] + 4 - (state[3] + 32);
        return Math.abs(delta) < 5 ? 0 : delta < 0 ? BreakoutBoard.LEFT : BreakoutBoard.RIGHT;
    }
}
