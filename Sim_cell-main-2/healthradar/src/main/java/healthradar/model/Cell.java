package healthradar.model;

import java.io.Serializable;
import java.util.Random;

/**
 * Represents one cell (i.e. one person or empty slot) on the simulation grid.
 *
 * <p>Each cell tracks its current {@link CellState}, its age in the current
 * state (number of steps spent in this state), an immunity level, and a
 * personal resistance factor that modulates the global transmission rate.</p>
 *
 * <p>Movement: at each step a cell may move to an adjacent empty cell with
 * probability {@code moveProbability}. Cells in state DEAD do not move.</p>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public class Cell implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── State ─────────────────────────────────────────────────────────────────

    /** Current epidemiological state of this cell. */
    private CellState state;

    /**
     * Number of simulation steps spent in the current state.
     * Used to trigger transitions (e.g. exposed → infected after incubation).
     */
    private int stateAge;

    /**
     * Personal resistance factor in [0,1].
     * A higher value means the individual is harder to infect.
     * Effective transmission = globalRate * (1 - resistance).
     */
    private double resistance;

    /**
     * Probability per step that this cell attempts to move to an adjacent
     * empty cell.
     */
    private double moveProbability;
    private double baseMoveProbability;

    /**
     * True if this person is wearing a mask.
     * The mask flag is independent of the cell state — a SUSCEPTIBLE, INFECTED,
     * or VACCINATED cell can all wear a mask simultaneously.
     * It is set via {@link #setMasked(boolean)} and persists until removed.
     */
    private boolean masked = false;

    private ZoneType zoneType;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a cell with the given state and randomised personal attributes.
     *
     * @param state           initial state
     * @param rng             random number generator shared by the simulation
     */
    public Cell(CellState state, ZoneType zoneType, Random rng) {
        this.state = state;
        this.zoneType = zoneType;
        this.stateAge = 0;
        // Personal resistance drawn from a normal distribution, clamped to [0,0.6]
        this.resistance = Math.max(0, Math.min(0.6, rng.nextGaussian() * 0.1 + 0.2));
        // Movement probability between 0.1 and 0.4
        this.baseMoveProbability = 0.1 + rng.nextDouble() * 0.3;
        this.moveProbability = this.baseMoveProbability;
    }

    /**
     * Creates an EMPTY cell placeholder (no person).
     */
    public Cell() {
        this.state = CellState.EMPTY;
        this.zoneType = ZoneType.EMPTY_SPACE;
        this.stateAge = 0;
        this.resistance = 0;
        this.baseMoveProbability = 0;
        this.moveProbability = 0;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** @return current state of this cell */
    public CellState getState() { return state; }

    /** @param state new state */
    public void setState(CellState state) { this.state = state; }

    /** @return number of steps spent in the current state */
    public int getStateAge() { return stateAge; }

    /** Increments the state age counter by 1. */
    public void incrementStateAge() { stateAge++; }

    /** Resets the state age counter to 0 (called on every state transition). */
    public void resetStateAge() { stateAge = 0; }

    /** @return personal resistance factor [0,1] */
    public double getResistance() { return resistance; }

    /** @param resistance new resistance value, clamped to [0,1] */
    public void setResistance(double resistance) {
        this.resistance = Math.max(0, Math.min(1, resistance));
    }

    /** @return probability per step of attempting to move */
    public double getMoveProbability() { return moveProbability; }

    /** @param p new move probability, clamped to [0,1] */
    public void setMoveProbability(double p) {
        this.moveProbability = Math.max(0.0, Math.min(1.0, p));
    }

    public void setStateAge(int a) {
        this.stateAge = Math.max(0, a);
    }

    public ZoneType getZoneType() { return zoneType; }
    public void setZoneType(ZoneType zoneType) { this.zoneType = zoneType; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Ajuste la probabilité de mouvement finale en multipliant la valeur de base.
     * @param factor 1.0 = normal (0.1 à 0.4), 0.0 = confinement total (0.0 partout)
     */
    public void adjustMovementWithFactor(double factor) {
        // Permet de descendre à 0.0 sans restriction et de brider au maximum à 1.0
        this.moveProbability = Math.max(0.0, Math.min(1.0, this.baseMoveProbability * factor));
    }

    /** @return true if the cell is occupied by a person (not EMPTY or DEAD) */
    public boolean isAlive() {
        return state != CellState.EMPTY && state != CellState.DEAD;
    }

    /** @return true if this cell slot is free */
    public boolean isEmpty() { return state == CellState.EMPTY; }

    /**
     * Computes the effective infection probability when this cell is exposed to
     * a globally set transmission rate.
     *
     * @param baseRate the disease's base transmission rate
     * @return effective probability after applying personal resistance
     */
    public double effectiveInfectionProbability(double baseRate) {
        return baseRate * (1.0 - resistance);
    }

    /** @return true if this person is wearing a mask */
    public boolean isMasked() { return masked; }

    /** @param masked true to equip a mask on this person */
    public void setMasked(boolean masked) { this.masked = masked; }

    /**
     * Produces a deep copy of this cell. Used by the double-buffering mechanism
     * in the {@link Grid} to compute the next generation without aliasing.
     *
     * @return a new Cell with identical attribute values
     */
    public Cell copy() {
        Cell c = new Cell();
        c.state = this.state;
        c.zoneType = this.zoneType;
        c.stateAge = this.stateAge;
        c.resistance = this.resistance;
        c.baseMoveProbability = this.baseMoveProbability;
        c.moveProbability = this.moveProbability;
        c.masked = this.masked;
        return c;
    }

    @Override
    public String toString() {
        return state.name().charAt(0) + "";
    }
}
