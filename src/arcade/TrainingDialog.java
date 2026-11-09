package arcade;

import ai.Trainer;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Runs real training off the Swing event thread, with progress and a reusable checkpoint. */
public final class TrainingDialog extends JDialog {
    private final JSpinner episodes = new JSpinner(new SpinnerNumberModel(32, 1, 512, 8));
    private final JSpinner epochs = new JSpinner(new SpinnerNumberModel(30, 1, 500, 5));
    private final JTextArea log = new JTextArea(17, 64);
    private final JTextField destination;
    private SwingWorker<Trainer.Result, String> worker;
    public TrainingDialog(JFrame owner, String game, int seed, Consumer<Path> onTrained) {
        super(owner, "Train network · " + game, false);
        destination = new JTextField(Path.of("models", game + "-custom.nn").toString(), 26);
        JPanel content = new JPanel(new BorderLayout(12, 12)); content.setBorder(new EmptyBorder(20, 20, 20, 20));
        JTextArea explanation = new JTextArea("The policy learns from reference actions and states visited during its own rollouts.\n"
            + "Training saves a model, per-epoch metrics and a held-out evaluation report.");
        explanation.setEditable(false); explanation.setOpaque(false); explanation.setFont(Theme.font(Font.PLAIN, 12));
        explanation.setForeground(Theme.TEXT); content.add(explanation, BorderLayout.NORTH);
        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        fields.add(new JLabel("Episodes / round")); fields.add(episodes); fields.add(new JLabel("Epochs")); fields.add(epochs);
        fields.add(new JLabel("Output")); fields.add(destination);
        JPanel center = new JPanel(new BorderLayout(8, 8)); center.add(fields, BorderLayout.NORTH);
        log.setEditable(false); log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setBackground(Theme.BACKGROUND); log.setForeground(Theme.TEXT);
        center.add(new JScrollPane(log), BorderLayout.CENTER); content.add(center, BorderLayout.CENTER);
        JButton start = new JButton("Train"), close = new JButton("Close");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.add(close); actions.add(start); content.add(actions, BorderLayout.SOUTH);
        start.addActionListener(e -> {
            Trainer.Config config;
            try {
                episodes.commitEdit(); epochs.commitEdit();
                config = new Trainer.Config(game, seed, ((Number) episodes.getValue()).intValue(), ((Number) epochs.getValue()).intValue(), 20_000,
                    Path.of(destination.getText().trim()));
                if (destination.getText().isBlank()) throw new IllegalArgumentException("Specify an output model path.");
            } catch (IllegalArgumentException | java.text.ParseException invalid) {
                JOptionPane.showMessageDialog(this, invalid.getMessage(), "Invalid options", JOptionPane.ERROR_MESSAGE); return;
            }
            start.setEnabled(false); episodes.setEnabled(false); epochs.setEnabled(false); destination.setEnabled(false);
            close.setText("Cancel training"); log.setText("");
            worker = new SwingWorker<>() {
                @Override protected Trainer.Result doInBackground() throws Exception { return Trainer.train(config, this::publish); }
                @Override protected void process(List<String> lines) { for (String line : lines) log.append(line + "\n"); log.setCaretPosition(log.getDocument().getLength()); }
                @Override protected void done() {
                    close.setText("Close"); start.setEnabled(true); episodes.setEnabled(true); epochs.setEnabled(true); destination.setEnabled(true);
                    try {
                        Trainer.Result result = get(); onTrained.accept(result.model());
                        log.append("\nTraining complete. Model loaded; select Start game to run it.\n");
                    } catch (CancellationException cancelled) { log.append("\nTraining cancelled.\n"); }
                    catch (Exception failure) { log.append("\nError: " + (failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage()) + "\n"); }
                }
            };
            worker.execute();
        });
        close.addActionListener(e -> { if (worker != null && !worker.isDone()) worker.cancel(true); else dispose(); });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { cancel(); }
            @Override public void windowClosed(WindowEvent e) { cancel(); }
            private void cancel() { if (worker != null && !worker.isDone()) worker.cancel(true); }
        });
        setContentPane(content); setDefaultCloseOperation(DISPOSE_ON_CLOSE); pack(); setSize(Math.max(780, getWidth()), 520); setLocationRelativeTo(owner);
    }
}
