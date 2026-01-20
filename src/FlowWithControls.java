/**
 * @author Sinray Yang
 */

import java.awt.*;
import java.util.*;
import java.util.function.*;
import javax.swing.*;

public class FlowWithControls {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Image iconImage = new ImageIcon(FlowWithControls.class.getResource("icon.png")).getImage();
            Flow engine = new Flow();
            JFrame frame = new JFrame("Flow with Controls");
            frame.setIconImage(iconImage);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            // Simulation canvas
            frame.add(engine, BorderLayout.CENTER);

            // Control panel
            JPanel controls = new JPanel();
            controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
            JScrollPane scroll = new JScrollPane(controls);
            scroll.setPreferredSize(new Dimension(400, 600));
            frame.add(scroll, BorderLayout.EAST);

            // Sliders
            addSlider(
                controls, "Gravity X (m/s²)",
                -20.0, 20.0, 0.1, Flow.Physics.GRAVITY.x, 5.0,
                "%.1f", v -> Flow.Physics.GRAVITY.x = v
            );
            addSlider(
                controls, "Gravity Y (m/s²)",
                -20.0, 20.0, 0.1, Flow.Physics.GRAVITY.y, 5.0,
                "%.1f", v -> Flow.Physics.GRAVITY.y = v
            );
            addSlider(
                controls, "Wall Elasticity (0-1)",
                0.0, 1.0, 0.01, Flow.Physics.boundElasticity, 0.2,
                "%.2f", v -> Flow.Physics.boundElasticity = v
            );
            addSlider(
                controls, "Wall Static Friction (μ)",
                0.0, 5.0, 0.01, Flow.Physics.boundStaticFriction, 1.0,
                "%.2f", v -> Flow.Physics.boundStaticFriction = v
            );
            addSlider(
                controls, "Wall Dynamic Friction (μ)",
                0.0, 5.0, 0.01, Flow.Physics.boundDynamicFriction, 1.0,
                "%.2f", v -> Flow.Physics.boundDynamicFriction = v
            );
            addSlider(
                controls, "Water Level (m)",
                0.0, Flow.Simulation.DIMENSIONS.y, 0.01, Flow.Physics.waterLevel, 5,
                "%.2f", v -> Flow.Physics.waterLevel = v
            );
            addSlider(
                controls, "Time Scale (x)",
                0.0, 10.0, 0.1, Flow.Simulation.timeScale, 1.0,
                "%.1f", v -> Flow.Simulation.timeScale = v
            );

            // Use Water checkbox
            JCheckBox useWater = new JCheckBox("Use Water", Flow.Physics.useWater);
            useWater.addActionListener(e -> Flow.Physics.useWater = useWater.isSelected());
            controls.add(useWater);
            controls.add(Box.createRigidArea(new Dimension(0, 10)));

            // Add Ball panel
            controls.add(createAddBallPanel(engine));

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            // JButton addButton = new JButton("Simulate");
            // controls.add(addButton);
            // addButton.addActionListener(e -> {
            //     engine.quit();
            //     engine.start();
            // });

            engine.start();
        });
    }

    // Helper to create sliders
    private static void addSlider(
        JPanel parent, String labelText, double min, double max, double step, 
        double initial, double majorTickSpacing, String format, DoubleConsumer consumer
    ) {
        DecimalSlider slider = new DecimalSlider(min, max, step, initial, majorTickSpacing, format, consumer);
        parent.add(new JLabel(labelText));
        parent.add(slider.slider);
        parent.add(Box.createRigidArea(new Dimension(0, 5)));
    }

    // Helper class for decimal sliders
    private static class DecimalSlider {
        JSlider slider;
        int scale;

        DecimalSlider(
            double min, double max, double step, double initial, double majorTickSpacing, 
            String labelFormat, java.util.function.DoubleConsumer consumer
        ) {
            scale = (int) Math.round(1.0 / step);
            int intMin = (int) Math.round(min * scale);
            int intMax = (int) Math.round(max * scale);
            int intInit = (int) Math.round(initial * scale);

            slider = new JSlider(intMin, intMax, intInit);
            slider.setPaintTicks(true);
            slider.setMinorTickSpacing(1);

            int major = (int) Math.round(majorTickSpacing * scale);
            slider.setMajorTickSpacing(major);

            // This API does not support anything better than Hashtable; it will continue to yell at me
            Dictionary<Integer, JLabel> labels = new Hashtable<>();
            for (double v = min; v <= max + 1e-6; v += majorTickSpacing) {
                int key = (int) Math.round(v * scale);
                JLabel lbl = new JLabel(String.format(labelFormat, v));
                lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 12f));
                labels.put(key, lbl);
            }
            slider.setLabelTable(labels);
            slider.setPaintLabels(true);

            slider.addChangeListener(e -> consumer.accept(slider.getValue() / (double) scale));
        }
    }

    // Add Ball panel creation
    private static JPanel createAddBallPanel(Flow engine) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        String[] labels = {
            "Radius (m):", "Mass (kg):", "Position X (m):", "Position Y (m):",
            "Velocity X (m/s):", "Velocity Y (m/s):", "Elasticity (0–1):",
            "Static Friction (μ):", "Dynamic Friction (μ):", "Colour (random/#RRGGBB):"
        };

        JTextField[] fields = {
            new JTextField("0.5"),
            new JTextField("1000.0"),
            new JTextField(String.valueOf(Flow.Simulation.DIMENSIONS.x / 2.0)),
            new JTextField(String.valueOf(Flow.Simulation.DIMENSIONS.y * 0.9)),
            new JTextField("0.0"),
            new JTextField("0.0"),
            new JTextField("0.9"),
            new JTextField("1.6"),
            new JTextField("0.9"),
            new JTextField("random")
        };

        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            panel.add(new JLabel(labels[i]), gbc);

            gbc.gridx = 1;
            panel.add(fields[i], gbc);
        }

        JButton addButton = new JButton("Add Ball");
        gbc.gridx = 0;
        gbc.gridy = labels.length;
        gbc.gridwidth = 2;
        panel.add(addButton, gbc);

        addButton.addActionListener(e -> {
            try {
                double r = Double.parseDouble(fields[0].getText());
                double m = Double.parseDouble(fields[1].getText());
                double x = Double.parseDouble(fields[2].getText());
                double y = Double.parseDouble(fields[3].getText());
                double vx = Double.parseDouble(fields[4].getText());
                double vy = Double.parseDouble(fields[5].getText());
                double el = Double.parseDouble(fields[6].getText());
                double muS = Double.parseDouble(fields[7].getText());
                double muD = Double.parseDouble(fields[8].getText());

                Color colour;
                String colStr = fields[9].getText().trim().toLowerCase();
                if (colStr.equals("random")) {
                    colour = new Color((float) Math.random(), (float) Math.random(), (float) Math.random());
                } else {
                    colour = Color.decode(colStr);
                }

                Flow.Ball newBall = engine.new Ball(x, y, vx, vy, m, r, colour);
                newBall.objectElasticity = el;
                newBall.objectStaticFriction = muS;
                newBall.objectDynamicFriction = muD;

                synchronized (engine.queuedBalls) {
                    engine.queuedBalls.add(newBall);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Invalid input! Check numbers and colour format.");
            }
        });

        return panel;
    }
}