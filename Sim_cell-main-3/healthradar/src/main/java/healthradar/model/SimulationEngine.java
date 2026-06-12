package healthradar.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Controls the simulation lifecycle: stepping, pausing, resetting, and
 * collecting per-step statistics.
 *
 * <p>The engine holds a reference to the {@link Grid} and advances it one
 * step at a time. It also maintains a history of {@link StepStats} objects
 * so the UI can draw time-series graphs.</p>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public class SimulationEngine implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The grid being simulated. */
    private Grid grid;

    /** Total number of steps executed since the last reset. */
    private int stepCount;

    /** History of statistics, one entry per completed step. */
    private final List<StepStats> history;

    /** Maximum number of history entries kept in memory. */
    private static final int MAX_HISTORY = 500;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a new engine wrapping the given grid.
     *
     * @param grid the grid to simulate
     */
    public SimulationEngine(Grid grid) {
        this.grid = grid;
        this.stepCount = 0;
        this.history = new ArrayList<>();
        // Record initial state
        recordStats();
    }

    // ── Simulation control ────────────────────────────────────────────────────

    /**
     * Advances the simulation by exactly one step and records statistics.
     */
    public void step() {
        grid.step();
        stepCount++;
        recordStats();
    }

    /**
     * Resets the engine: clears the grid, zeroes the step counter, and
     * clears the history.
     */
    public void reset() {
        grid.clear();
        stepCount = 0;
        history.clear();
        recordStats();
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    /**
     * Records a {@link StepStats} snapshot from the current grid state and
     * appends it to the history list.
     */
    private void recordStats() {
        StepStats s = new StepStats(
                stepCount,
                grid.countState(CellState.SUSCEPTIBLE),
                grid.countState(CellState.VACCINATED),
                grid.countState(CellState.EXPOSED),
                grid.countState(CellState.INFECTED),
                grid.countState(CellState.RECOVERED),
                grid.countState(CellState.DEAD)
        );
        history.add(s);
        // Trim history to avoid unbounded memory growth
        if (history.size() > MAX_HISTORY)
            history.remove(0);
    }

    /**
     * Returns the most recent statistics snapshot.
     *
     * @return latest {@link StepStats}, or null if history is empty
     */
    public StepStats latestStats() {
        if (history.isEmpty()) return null;
        return history.get(history.size() - 1);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** @return the grid managed by this engine */
    public Grid getGrid() { return grid; }

    /** @param grid replacement grid (used when loading a saved simulation) */
    public void setGrid(Grid grid) { this.grid = grid; }

    /** @return number of steps executed since last reset */
    public int getStepCount() { return stepCount; }

    /** @return unmodifiable view of the statistics history */
    public List<StepStats> getHistory() {
        return java.util.Collections.unmodifiableList(history);
    }

    /**
     * Clears the statistics history.
     * Used by {@link healthradar.io.SimulationSerializer} after loading.
     */
    public void clearHistory() { history.clear(); }

    /**
     * Directly sets the step counter.
     * Used by {@link healthradar.io.SimulationSerializer} after loading.
     *
     * @param count the step count to restore
     */
    public void setStepCount(int count) { this.stepCount = count; }

    /**
     * Appends a pre-built {@link StepStats} to the history.
     * Used by {@link healthradar.io.SimulationSerializer} to restore history.
     *
     * @param s the stats snapshot to inject
     */
    public void injectStat(StepStats s) {
        history.add(s);
        if (history.size() > MAX_HISTORY) history.remove(0);
    }

    /**
     * Records a stats snapshot from the current grid state.
     * Used after loading when the saved history was empty.
     */
    public void recordCurrentStats() { recordStats(); }

    // ── Inner record ─────────────────────────────────────────────────────────

    /**
     * Immutable snapshot of the grid state at one simulation step.
     *
     * @param step        step number
     * @param susceptible count of SUSCEPTIBLE cells
     * @param exposed     count of EXPOSED cells
     * @param infected    count of INFECTED cells
     * @param recovered   count of RECOVERED cells
     * @param dead        count of DEAD cells
     */
    public record StepStats(int step, int susceptible, int vaccinated,
                            int exposed, int infected, int recovered, int dead) implements Serializable {

        /** @return total living population (S + V + E + I + R) */
        public int totalLiving() { return susceptible + vaccinated + exposed + infected + recovered; }

        /**
         * Returns the percentage of living cells currently infected.
         *
         * @return infection percentage in [0,100], or 0 if no living cells
         */
        public double infectionPercent() {
            int total = totalLiving() + dead;
            return total == 0 ? 0 : (infected * 100.0 / total);
        }
    }
}
