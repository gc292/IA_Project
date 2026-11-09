package breakout;

import javax.swing.JFrame;
import utils.GameController;

/** Standalone window retained for custom controller integrations. Construct on the EDT. */
public class Breakout extends JFrame {
    public Breakout(GameController controller, int seed) {
        setTitle("AI Arcade · Breakout");
        add(new BreakoutBoard(controller, true, seed));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack(); setLocationRelativeTo(null); setResizable(false); setVisible(true);
    }
}
