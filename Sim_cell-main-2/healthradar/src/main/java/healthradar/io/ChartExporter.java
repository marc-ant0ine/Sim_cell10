package healthradar.io;

import healthradar.model.SimulationEngine;
import healthradar.model.SimulationEngine.StepStats;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Exports the simulation statistics history as a PNG chart file.
 *
 * <p>The chart contains one line per cell state (Susceptible, Exposed,
 * Infected, Recovered, Dead), drawn over the full simulation history.
 * The output is a self-contained PNG file that can be opened in any
 * image viewer.</p>
 *
 * <p>Uses only {@code java.awt} and {@code javax.imageio} — no external
 * dependencies required.</p>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public class ChartExporter {

    // ── Chart dimensions ──────────────────────────────────────────────────────

    /** Total image width in pixels. */
    private static final int IMG_W   = 1200;
    /** Total image height in pixels. */
    private static final int IMG_H   = 700;
    /** Left margin (space for Y-axis labels). */
    private static final int PAD_L   = 80;
    /** Right margin. */
    private static final int PAD_R   = 30;
    /** Top margin (space for title). */
    private static final int PAD_T   = 70;
    /** Bottom margin (space for X-axis labels + legend). */
    private static final int PAD_B   = 110;

    /** Width of the plot area. */
    private static final int PLOT_W  = IMG_W - PAD_L - PAD_R;
    /** Height of the plot area. */
    private static final int PLOT_H  = IMG_H - PAD_T - PAD_B;

    // ── Colours matching the JavaFX / terminal palette ────────────────────────

    private static final Color C_BG          = new Color(18,  18,  42);
    private static final Color C_GRID        = new Color(50,  55,  80);
    private static final Color C_AXIS        = new Color(180, 180, 200);
    private static final Color C_SUSCEPTIBLE = new Color(100, 160, 220);
    private static final Color C_EXPOSED     = new Color(255, 165,  30);
    private static final Color C_INFECTED    = new Color(210,  50,  50);
    private static final Color C_RECOVERED   = new Color( 60, 180,  75);
    private static final Color C_DEAD        = new Color(130, 130, 130);
    private static final Color C_WHITE       = Color.WHITE;

    /** Private constructor – utility class. */
    private ChartExporter() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Exports the statistics history of the given engine as a PNG chart.
     *
     * <p>The file name is auto-generated as
     * {@code healthradar_chart_stepNNN.png} if {@code path} is null.</p>
     *
     * @param engine the simulation engine whose history is charted
     * @param path   destination file (PNG); must end in .png
     * @throws IOException if the file cannot be written
     * @throws IllegalArgumentException if the history is empty
     */
    public static void export(SimulationEngine engine, Path path) throws IOException {
        List<StepStats> history = engine.getHistory();
        if (history.isEmpty())
            throw new IllegalArgumentException("No simulation history to chart.");

        BufferedImage img = new BufferedImage(IMG_W, IMG_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawBackground(g);
        drawTitle(g, engine);

        int maxCount = computeMax(history);
        drawGridLines(g, maxCount);
        drawAxes(g, history, maxCount);

        drawSeries(g, history, maxCount, "Susceptible", C_SUSCEPTIBLE, s -> s.susceptible());
        drawSeries(g, history, maxCount, "Exposed",     C_EXPOSED,     s -> s.exposed());
        drawSeries(g, history, maxCount, "Infected",    C_INFECTED,    s -> s.infected());
        drawSeries(g, history, maxCount, "Recovered",   C_RECOVERED,   s -> s.recovered());
        drawSeries(g, history, maxCount, "Dead",        C_DEAD,        s -> s.dead());

        drawLegend(g);
        drawFooter(g, engine);

        g.dispose();
        ImageIO.write(img, "png", path.toFile());
    }

    /**
     * Builds a default output path for a chart file.
     *
     * @param engine the engine (used for step number in the filename)
     * @return a Path like {@code healthradar_chart_step042.png}
     */
    public static Path defaultPath(SimulationEngine engine) {
        return Path.of(String.format("healthradar_chart_step%03d.png",
                engine.getStepCount()));
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    /** Fills the background. */
    private static void drawBackground(Graphics2D g) {
        g.setColor(C_BG);
        g.fillRect(0, 0, IMG_W, IMG_H);
        // Plot area slightly lighter
        g.setColor(new Color(25, 25, 55));
        g.fillRect(PAD_L, PAD_T, PLOT_W, PLOT_H);
    }

    /** Draws the chart title. */
    private static void drawTitle(Graphics2D g, SimulationEngine engine) {
        g.setColor(new Color(100, 200, 220));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        String title = "HealthRadar – Population dynamics";
        g.drawString(title, PAD_L, 40);

        g.setColor(C_AXIS);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        String sub = "Disease: " + engine.getGrid().getDisease().getName()
                + "   |   " + engine.getStepCount() + " days recorded";
        g.drawString(sub, PAD_L, 60);
    }

    /** Draws horizontal grid lines and Y-axis labels. */
    private static void drawGridLines(Graphics2D g, int maxCount) {
        int steps = 5;
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        for (int i = 0; i <= steps; i++) {
            int y  = PAD_T + PLOT_H - (i * PLOT_H / steps);
            int val = i * maxCount / steps;

            g.setColor(C_GRID);
            g.setStroke(new BasicStroke(0.7f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 1f, new float[]{4f, 4f}, 0f));
            g.drawLine(PAD_L, y, PAD_L + PLOT_W, y);

            g.setColor(C_AXIS);
            g.setStroke(new BasicStroke(1f));
            String label = String.valueOf(val);
            int lw = g.getFontMetrics().stringWidth(label);
            g.drawString(label, PAD_L - lw - 6, y + 4);
        }
    }

    /** Draws the X and Y axis borders. */
    private static void drawAxes(Graphics2D g, List<StepStats> history, int maxCount) {
        g.setColor(C_AXIS);
        g.setStroke(new BasicStroke(1.5f));
        // Y axis
        g.drawLine(PAD_L, PAD_T, PAD_L, PAD_T + PLOT_H);
        // X axis
        g.drawLine(PAD_L, PAD_T + PLOT_H, PAD_L + PLOT_W, PAD_T + PLOT_H);

        // X-axis labels (simulated day numbers, up to 10 ticks)
        int n = history.size();
        int tickCount = Math.min(10, n);
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        for (int t = 0; t <= tickCount; t++) {
            int idx = (n - 1) * t / tickCount;
            int x   = PAD_L + idx * PLOT_W / (n - 1 == 0 ? 1 : n - 1);
            int step = history.get(idx).step();
            String label = "" + step;
            int lw = g.getFontMetrics().stringWidth(label);
            g.setColor(C_AXIS);
            g.drawLine(x, PAD_T + PLOT_H, x, PAD_T + PLOT_H + 4);
            g.drawString(label, x - lw / 2, PAD_T + PLOT_H + 16);
        }

        // Axis labels
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(C_AXIS);
        g.drawString("Day", PAD_L + PLOT_W / 2 - 15, PAD_T + PLOT_H + 35);

        // Rotated Y label
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(C_AXIS);
        g2.rotate(-Math.PI / 2, PAD_L - 55, PAD_T + PLOT_H / 2);
        g2.drawString("Cell count", PAD_L - 55, PAD_T + PLOT_H / 2);
        g2.dispose();
    }

    /**
     * Draws one data series as a coloured line.
     *
     * @param g        graphics context
     * @param history  stats history
     * @param maxCount Y-axis maximum
     * @param label    series label (for debug only, legend is drawn separately)
     * @param color    line colour
     * @param accessor lambda extracting the value from a StepStats
     */
    private static void drawSeries(Graphics2D g, List<StepStats> history,
            int maxCount, String label, Color color,
            java.util.function.ToIntFunction<StepStats> accessor) {
        int n = history.size();
        if (n < 2) return;

        g.setColor(color);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            int val = accessor.applyAsInt(history.get(i));
            xs[i] = PAD_L + i * PLOT_W / (n - 1);
            ys[i] = PAD_T + PLOT_H - (maxCount == 0 ? 0 : val * PLOT_H / maxCount);
        }

        for (int i = 0; i < n - 1; i++)
            g.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
    }

    /** Draws the colour legend at the bottom of the chart. */
    private static void drawLegend(Graphics2D g) {
        String[] labels = {"Susceptible", "Exposed", "Infected", "Recovered", "Dead"};
        Color[]  colors = {C_SUSCEPTIBLE, C_EXPOSED, C_INFECTED, C_RECOVERED, C_DEAD};

        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        int totalW = 0;
        FontMetrics fm = g.getFontMetrics();
        for (String l : labels) totalW += fm.stringWidth(l) + 30;

        int x = (IMG_W - totalW) / 2;
        int y = IMG_H - 55;

        for (int i = 0; i < labels.length; i++) {
            g.setColor(colors[i]);
            g.fillRect(x, y - 11, 16, 12);
            g.setColor(C_WHITE);
            g.drawString(labels[i], x + 20, y);
            x += fm.stringWidth(labels[i]) + 30;
        }
    }

    /** Draws a small footer with export metadata. */
    private static void drawFooter(Graphics2D g, SimulationEngine engine) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(100, 100, 130));
        String info = "Exported at day " + engine.getStepCount()
                + "  |  HealthRadar – CY Tech ING1 GI1";
        g.drawString(info, PAD_L, IMG_H - 12);
    }

    /** Computes the maximum count across all series and all steps. */
    private static int computeMax(List<StepStats> history) {
        int max = 1;
        for (StepStats s : history) {
            max = Math.max(max, s.susceptible());
            max = Math.max(max, s.exposed());
            max = Math.max(max, s.infected());
            max = Math.max(max, s.recovered());
            max = Math.max(max, s.dead());
        }
        return max;
    }
}
