package pacman;

import javax.swing.JFrame;
import utils.GameController;

/** Standalone window retained for custom controller integrations. Construct on the EDT. */
public class Pacman extends JFrame {
    public Pacman(GameController controller, boolean withGui, int seed) {
        if (!withGui) throw new IllegalArgumentException("Use PacmanBoard for headless simulation.");
        setTitle("AI Arcade · Pac-Man");
        add(new PacmanBoard(controller, true, seed));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack(); setLocationRelativeTo(null); setResizable(false); setVisible(true);
    }
    public static void main(String[] args) {
        String[] forwarded = new String[args.length + 2];
        forwarded[0] = "--game"; forwarded[1] = "pacman";
        System.arraycopy(args, 0, forwarded, 2, args.length);
        arcade.Launcher.main(forwarded);
    }
}
