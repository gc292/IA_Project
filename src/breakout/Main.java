package breakout;

import javax.swing.SwingUtilities;

import utils.GameController;

public class Main {
	public static void main(String[] args) {
GameController gameController = null;
        
        // Crie um novo objeto Breakout e exiba-o na tela
        SwingUtilities.invokeLater(() -> {
            new Breakout(gameController, 123); // Substitua 123 pelo valor real do seed
        });
	}

}
