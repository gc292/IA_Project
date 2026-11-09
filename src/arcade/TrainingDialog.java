package arcade;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import ai.Trainer;

/** Background training with progress output and checkpoint loading. */
public final class TrainingDialog extends JDialog {
    private final JSpinner episodes = new JSpinner(new SpinnerNumberModel(32, 1, 512, 8));
    private final JSpinner epochs = new JSpinner(new SpinnerNumberModel(30, 1, 500, 5));
    private final JTextArea log = new JTextArea(17, 64);
    private final JButton start = new JButton("Train");
    private final JButton close = new JButton("Close");
    private final JTextField destination;
    private SwingWorker<Trainer.Result, String> worker;

    public TrainingDialog(JFrame owner, String game, int seed, Consumer<Path> onTrained) {
        super(owner, "Train network · " + game, false);
        destination = new JTextField(Path.of("models", game + "-custom.nn").toString(), 26);
        setContentPane(createContent());
        configureActions(game, seed, onTrained);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setSize(Math.max(780, getWidth()), 520);
        setLocationRelativeTo(owner);
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(20, 20, 20, 20));
        content.add(createExplanation(), BorderLayout.NORTH);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setBackground(Theme.BACKGROUND);
        log.setForeground(Theme.TEXT);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(createFields(), BorderLayout.NORTH);
        center.add(new JScrollPane(log), BorderLayout.CENTER);
        content.add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(close);
        actions.add(start);
        content.add(actions, BorderLayout.SOUTH);
        return content;
    }

    private JTextArea createExplanation() {
        JTextArea explanation =
                new JTextArea(
                        "The policy learns from reference actions and states visited during its own"
                            + " rollouts.\n"
                            + "Training saves a model, per-epoch metrics and a held-out evaluation"
                            + " report.");
        explanation.setEditable(false);
        explanation.setOpaque(false);
        explanation.setFont(Theme.font(Font.PLAIN, 12));
        explanation.setForeground(Theme.TEXT);
        return explanation;
    }

    private JPanel createFields() {
        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        fields.add(new JLabel("Episodes / round"));
        fields.add(episodes);
        fields.add(new JLabel("Epochs"));
        fields.add(epochs);
        fields.add(new JLabel("Output"));
        fields.add(destination);
        return fields;
    }

    private void configureActions(String game, int seed, Consumer<Path> onTrained) {
        start.addActionListener(event -> startTraining(game, seed, onTrained));
        close.addActionListener(
                event -> {
                    if (worker != null && !worker.isDone()) {
                        cancelTraining();
                    } else {
                        dispose();
                    }
                });
        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent event) {
                        cancelTraining();
                    }

                    @Override
                    public void windowClosed(WindowEvent event) {
                        cancelTraining();
                    }
                });
    }

    private Trainer.Config readConfig(String game, int seed) throws ParseException {
        episodes.commitEdit();
        epochs.commitEdit();
        Trainer.Config config =
                new Trainer.Config(
                        game,
                        seed,
                        ((Number) episodes.getValue()).intValue(),
                        ((Number) epochs.getValue()).intValue(),
                        20_000,
                        Path.of(destination.getText().trim()));
        if (destination.getText().isBlank()) {
            throw new IllegalArgumentException("Specify an output model path.");
        }
        return config;
    }

    private void startTraining(String game, int seed, Consumer<Path> onTrained) {
        Trainer.Config config;
        try {
            config = readConfig(game, seed);
        } catch (IllegalArgumentException | ParseException error) {
            JOptionPane.showMessageDialog(
                    this, error.getMessage(), "Invalid options", JOptionPane.ERROR_MESSAGE);
            return;
        }

        setTraining(true);
        log.setText("");
        worker = createWorker(config, onTrained);
        worker.execute();
    }

    private SwingWorker<Trainer.Result, String> createWorker(
            Trainer.Config config, Consumer<Path> onTrained) {
        return new SwingWorker<>() {
            @Override
            protected Trainer.Result doInBackground() throws Exception {
                return Trainer.train(config, this::publish);
            }

            @Override
            protected void process(List<String> lines) {
                for (String line : lines) {
                    log.append(line + "\n");
                }
                log.setCaretPosition(log.getDocument().getLength());
            }

            @Override
            protected void done() {
                setTraining(false);
                try {
                    Trainer.Result result = get();
                    onTrained.accept(result.model());
                    log.append("\nTraining complete. Model loaded; select Start game to run it.\n");
                } catch (CancellationException error) {
                    log.append("\nTraining cancelled.\n");
                } catch (Exception error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    log.append("\nError: " + cause.getMessage() + "\n");
                }
            }
        };
    }

    private void setTraining(boolean training) {
        start.setEnabled(!training);
        episodes.setEnabled(!training);
        epochs.setEnabled(!training);
        destination.setEnabled(!training);
        close.setText(training ? "Cancel training" : "Close");
    }

    private void cancelTraining() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
    }
}
