package arcade;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class Theme {
    public static final Color BACKGROUND = new Color(12, 17, 27);
    public static final Color PANEL = new Color(20, 28, 42);
    public static final Color LINE = new Color(39, 51, 70);
    public static final Color TEXT = new Color(234, 240, 248);
    public static final Color MUTED = new Color(144, 160, 184);
    public static final Color MINT = new Color(111, 235, 191);
    public static final Color GOLD = new Color(255, 203, 104);

    private Theme() {}

    public static Font font(int style, int size) {
        return new Font("SansSerif", style, size);
    }

    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}
