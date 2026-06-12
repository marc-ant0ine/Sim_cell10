package healthradar.view;

import healthradar.model.CellState;
import healthradar.model.SimulationEngine;
import healthradar.model.SimulationEngine.StepStats;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.List;

/**
 * A JavaFX {@link VBox} that displays live statistics about the running
 * simulation.
 *
 * <p>It contains:</p>
 * <ol>
 *   <li>A text summary (counts and percentages per state).</li>
 *   <li>A line chart showing the evolution of each state over time.</li>
 *   <li>A colour legend.</li>
 * </ol>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public class StatsPanel extends VBox {

    // ── Layout constants ──────────────────────────────────────────────────────

    private static final int PANEL_WIDTH  = 280;
    private static final int CHART_HEIGHT = 180;
    private static final int SUMMARY_H    = 210;
    private static final int LEGEND_H     = 80;

    // ── Canvas layers ─────────────────────────────────────────────────────────

    /** Canvas for the time-series line chart. */
    private final Canvas chartCanvas;

    /** Canvas for the text summary and bar. */
    private final Canvas summaryCanvas;

    /** Canvas for the colour legend. */
    private final Canvas legendCanvas;

    /** Reference to the engine to read history from. */
    private SimulationEngine engine;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a StatsPanel bound to the given simulation engine.
     *
     * @param engine the simulation engine providing statistics
     */
    public StatsPanel(SimulationEngine engine) {
        super(8);
        this.engine = engine;
        setPadding(new Insets(8));
        setPrefWidth(PANEL_WIDTH);
        getStyleClass().add("stats-panel");

        summaryCanvas = new Canvas(PANEL_WIDTH - 16, SUMMARY_H);
        chartCanvas   = new Canvas(PANEL_WIDTH - 16, CHART_HEIGHT);
        legendCanvas  = new Canvas(PANEL_WIDTH - 16, LEGEND_H);

        getChildren().addAll(summaryCanvas, chartCanvas, legendCanvas);
        drawLegend();
        refresh();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Redraws all statistics panels using the latest data from the engine.
     * Call this after each simulation step.
     */
    public void refresh() {
        drawSummary();
        drawChart();
    }

    /** @param engine new engine (after load or reset) */
    public void setEngine(SimulationEngine engine) {
        this.engine = engine;
        refresh();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    /**
     * Draws the text summary and a stacked percentage bar onto {@code summaryCanvas}.
     */
    private void drawSummary() {
        GraphicsContext gc = summaryCanvas.getGraphicsContext2D();
        double w = summaryCanvas.getWidth();
        double h = summaryCanvas.getHeight();

        gc.setFill(Color.rgb(17, 24, 39));
        gc.fillRect(0, 0, w, h);

        StepStats s = engine.latestStats();
        if (s == null) return;

        gc.setFont(Font.font("Monospaced", 13));

        // Title
        gc.setFill(Color.WHITE);
        gc.fillText("Day: " + s.step(), 8, 18);

        int total = s.totalLiving() + s.dead();
        int peakInfected = engine.getHistory().stream()
                .mapToInt(StepStats::infected)
                .max()
                .orElse(s.infected());

        gc.setFill(Color.rgb(203, 213, 225));
        gc.fillText("Population: " + total, 8, 35);
        gc.fillText("Peak infected: " + peakInfected, 8, 52);

        // Per-state lines — 6 states, 16px per line, starting at y=52
        String[] labels  = { "Susceptible", "Vaccinated", "Exposed", "Infected", "Recovered", "Dead" };
        int[]    counts  = { s.susceptible(), s.vaccinated(), s.exposed(), s.infected(), s.recovered(), s.dead() };
        Color[]  colours = {
            GridView.stateColor(CellState.SUSCEPTIBLE),
            GridView.stateColor(CellState.VACCINATED),
            GridView.stateColor(CellState.EXPOSED),
            GridView.stateColor(CellState.INFECTED),
            GridView.stateColor(CellState.RECOVERED),
            GridView.stateColor(CellState.DEAD)
        };

        gc.setFont(Font.font("Monospaced", 10));
        double lineH = 16;   // pixels per state row
        double y0    = 72;   // first row baseline
        for (int i = 0; i < labels.length; i++) {
            double pct = total == 0 ? 0 : counts[i] * 100.0 / total;
            double y = y0 + i * lineH;
            gc.setFill(colours[i]);
            gc.fillRect(8, y - 9, 9, 9);
            gc.setFill(Color.rgb(241, 245, 249));
            gc.fillText(String.format("%-12s %5d  %5.1f%%", labels[i], counts[i], pct),
                        22, y);
        }

        // Stacked bar — always 14px above the bottom of the canvas
        double barY = h - 16;
        double barH = 10;
        double x = 8;
        double barW = w - 16;
        for (int i = 0; i < counts.length; i++) {
            double segW = total == 0 ? 0 : counts[i] * barW / total;
            gc.setFill(colours[i]);
            gc.fillRect(x, barY, segW, barH);
            x += segW;
        }
    }

    /**
     * Draws the time-series line chart of all states onto {@code chartCanvas}.
     */
    private void drawChart() {
        GraphicsContext gc = chartCanvas.getGraphicsContext2D();
        double w = chartCanvas.getWidth();
        double h = chartCanvas.getHeight();

        gc.setFill(Color.rgb(15, 23, 42));
        gc.fillRect(0, 0, w, h);

        List<StepStats> history = engine.getHistory();
        if (history.size() < 2) return;

        // Find max count to normalise Y axis
        int maxCount = 1;
        for (StepStats s : history) {
            maxCount = Math.max(maxCount, s.susceptible());
            maxCount = Math.max(maxCount, s.vaccinated());
            maxCount = Math.max(maxCount, s.infected());
            maxCount = Math.max(maxCount, s.recovered());
            maxCount = Math.max(maxCount, s.exposed());
        }

        double padL = 5, padR = 5, padT = 8, padB = 8;
        double chartW = w - padL - padR;
        double chartH = h - padT - padB;

        // Grid lines
        gc.setStroke(Color.rgb(51, 65, 85));
        gc.setLineWidth(0.5);
        for (int i = 1; i < 4; i++) {
            double yy = padT + chartH * i / 4.0;
            gc.strokeLine(padL, yy, padL + chartW, yy);
        }

        // Draw each series
        drawSeries(gc, history, "susceptible", GridView.stateColor(CellState.SUSCEPTIBLE), padL, padT, chartW, chartH, maxCount);
        drawSeries(gc, history, "vaccinated",  GridView.stateColor(CellState.VACCINATED), padL, padT, chartW, chartH, maxCount);
        drawSeries(gc, history, "exposed",     GridView.stateColor(CellState.EXPOSED), padL, padT, chartW, chartH, maxCount);
        drawSeries(gc, history, "infected",    GridView.stateColor(CellState.INFECTED), padL, padT, chartW, chartH, maxCount);
        drawSeries(gc, history, "recovered",   GridView.stateColor(CellState.RECOVERED), padL, padT, chartW, chartH, maxCount);
        drawSeries(gc, history, "dead",        GridView.stateColor(CellState.DEAD), padL, padT, chartW, chartH, maxCount);
    }

    /**
     * Draws a single time-series line for one state.
     *
     * @param gc       graphics context
     * @param history  list of stats snapshots
     * @param series   which series to plot ("susceptible", "infected", etc.)
     * @param color    line colour
     * @param padL     left padding in pixels
     * @param padT     top padding in pixels
     * @param chartW   chart area width
     * @param chartH   chart area height
     * @param maxCount maximum count value (for Y normalisation)
     */
    private void drawSeries(GraphicsContext gc, List<StepStats> history,
                            String series, Color color,
                            double padL, double padT,
                            double chartW, double chartH, int maxCount) {
        gc.setStroke(color);
        gc.setLineWidth(1.5);
        gc.beginPath();
        int n = history.size();
        for (int i = 0; i < n; i++) {
            StepStats s = history.get(i);
            int count = switch (series) {
                case "susceptible" -> s.susceptible();
                case "vaccinated"  -> s.vaccinated();
                case "exposed"     -> s.exposed();
                case "infected"    -> s.infected();
                case "recovered"   -> s.recovered();
                default            -> s.dead();
            };
            double x = padL + i * chartW / (n - 1);
            double y = padT + chartH - (count * chartH / (double) maxCount);
            if (i == 0) gc.moveTo(x, y); else gc.lineTo(x, y);
        }
        gc.stroke();
    }

    /**
     * Draws the static colour legend onto {@code legendCanvas}.
     */
    private void drawLegend() {
        GraphicsContext gc = legendCanvas.getGraphicsContext2D();
        double w = legendCanvas.getWidth();
        gc.setFill(Color.rgb(17, 24, 39));
        gc.fillRect(0, 0, w, LEGEND_H);

        gc.setFont(Font.font("Monospaced", 10));
        String[] names = {"Susceptible","Vaccinated","Exposed","Infected","Recovered","Dead"};
        CellState[] states = {CellState.SUSCEPTIBLE, CellState.VACCINATED,
                              CellState.EXPOSED, CellState.INFECTED,
                              CellState.RECOVERED, CellState.DEAD};
        // 2 columns of 3 rows, fixed positions
        double[] xs = {4,   140, 4,   140, 4,   140};
        double[] ys = {14,  14,  30,  30,  46,  46};
        for (int i = 0; i < names.length; i++) {
            gc.setFill(GridView.stateColor(states[i]));
            gc.fillRect(xs[i], ys[i] - 9, 9, 9);
            gc.setFill(Color.rgb(241, 245, 249));
            gc.fillText(names[i], xs[i] + 13, ys[i]);
        }
    }
}
