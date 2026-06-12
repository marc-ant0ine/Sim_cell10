package healthradar.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The 2-D grid that holds all {@link Cell} objects and drives the simulation
 * rules at each time step.
 *
 * <h2>Topology</h2>
 * <p>The grid can operate in two modes:</p>
 * <ul>
 *   <li><b>Bounded</b>  – cells on the border have fewer neighbours.</li>
 *   <li><b>Toroidal</b> – the grid wraps around: left edge connects to the
 *       right edge, top to bottom.</li>
 * </ul>
 *
 * <h2>Simulation step</h2>
 * <ol>
 *   <li>Movement phase – alive cells optionally move to an adjacent empty slot.</li>
 *   <li>Infection phase – infected cells attempt to transmit to susceptible
 *       neighbours (radius depends on airborne / contact mode).</li>
 *   <li>Progression phase – each cell ages in its current state and transitions
 *       when the appropriate threshold is reached.</li>
 * </ol>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public class Grid implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Number of columns. */
    private final int width;

    /** Number of rows. */
    private final int height;

    /** Whether the grid wraps around at the edges. */
    private boolean toroidal;

    /** The cell array – row-major: cells[row][col]. */
    private Cell[][] cells;

    /** Shared random number generator for reproducible simulations. */
    private final Random rng;

    /** The active disease applied to the simulation. */
    private Disease disease;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a new Grid filled with EMPTY cells.
     *
     * @param width     number of columns
     * @param height    number of rows
     * @param toroidal  true for toroidal (wrap-around) topology
     * @param disease   disease parameters to use
     * @param seed      random seed (use 0 for a random seed)
     */
    public Grid(int width, int height, boolean toroidal, Disease disease, long seed) {
        this.width = width;
        this.height = height;
        this.toroidal = toroidal;
        this.disease = disease;
        this.rng = (seed == 0) ? new Random() : new Random(seed);
        this.cells = new Cell[height][width];
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++)
                cells[r][c] = new Cell();
    }

    // ── Population helpers ────────────────────────────────────────────────────

    /**
     * Places a single cell of the given state at (row, col).
     *
     * @param row   row index
     * @param col   column index
     * @param state desired state
     */
    public void setCell(int row, int col, CellState state) {
        if (!inBounds(row, col)) return;
        cells[row][col] = (state == CellState.EMPTY) ? new Cell() : new Cell(state, cells[row][col].getZoneType(),rng);
    }

    /**
     * Fills a rectangular area with cells of the given state.
     *
     * @param r1    top-left row (inclusive)
     * @param c1    top-left column (inclusive)
     * @param r2    bottom-right row (inclusive)
     * @param c2    bottom-right column (inclusive)
     * @param state state to assign
     */
    public void fillArea(int r1, int c1, int r2, int c2, CellState state) {
        for (int r = Math.min(r1, r2); r <= Math.max(r1, r2); r++)
            for (int c = Math.min(c1, c2); c <= Math.max(c1, c2); c++)
                setCell(r, c, state);
    }

    /**
     * Randomly populates the grid.
     *
     * @param susceptibleCount  number of SUSCEPTIBLE cells to place
     * @param infectedCount     number of INFECTED cells to place
     */
    public void randomPopulate(int susceptibleCount, int infectedCount) {
        // Build a shuffled list of all positions
        List<int[]> positions = new ArrayList<>(width * height);
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++)
                positions.add(new int[]{r, c});
        Collections.shuffle(positions, rng);

        int idx = 0;
        for (int i = 0; i < susceptibleCount && idx < positions.size(); i++, idx++)
            setCell(positions.get(idx)[0], positions.get(idx)[1], CellState.SUSCEPTIBLE);
        for (int i = 0; i < infectedCount && idx < positions.size(); i++, idx++)
            setCell(positions.get(idx)[0], positions.get(idx)[1], CellState.INFECTED);
    }

    // ── Simulation step ───────────────────────────────────────────────────────

    /**
     * Advances the simulation by one step.
     *
     * <p>The update is performed on a copy of the grid to avoid order-dependent
     * artefacts (all cells read the previous generation).</p>
     */
    public void step() {
        // Deep-copy current grid as the read-source
        Cell[][] next = deepCopy(cells);

        // --- Phase 1: movement ---
        movePhase(next);

        // --- Phase 2: infection spread ---
        infectionPhase(next);

        // --- Phase 3: state progression ---
        progressionPhase(next);

        cells = next;
    }

    public void movePhase(Cell[][] nextCells) {
        List<int[]> coords = shuffledCoords();

        for (int[] coord : coords) {
            int r = coord[0];
            int c = coord[1];
            
            Cell currentAgent = cells[r][c];

            if (currentAgent.getState() == CellState.EMPTY || currentAgent.getState() == CellState.DEAD) {
                continue;
            }

            if (rng.nextDouble() < currentAgent.getMoveProbability()) {
                List<int[]> neighbors = new ArrayList<>();
                for (int rd = -1; rd <= 1; rd++) {
                    for (int cd = -1; cd <= 1; cd++) {
                        if (rd == 0 && cd == 0) continue;

                        int nr = r + rd;
                        int nc = c + cd;

                        if (toroidal) {
                            nr = wrap(nr, height);
                            nc = wrap(nc, width);
                            neighbors.add(new int[]{nr, nc});
                        } else {
                            if (nr >= 0 && nr < height && nc >= 0 && nc < width) {
                                neighbors.add(new int[]{nr, nc});
                            }
                        }
                    }
                }
                
                // Mélange des directions pour un déplacement aléatoire
                Collections.shuffle(neighbors, rng);

                // 4. Tentative de déplacement
                for (int[] nextCoord : neighbors) {
                    int nextR = nextCoord[0];
                    int nextC = nextCoord[1];

                    // On vérifie si la case est libre dans la grille FUTURE (nextCells)
                    if (nextCells[nextR][nextC].getState() == CellState.EMPTY) {
                        
                        Cell futureSource = nextCells[r][c];
                        Cell futureTarget = nextCells[nextR][nextC];

                        // On transfère les attributs de la PERSONNE vers sa nouvelle cellule
                        futureTarget.setState(currentAgent.getState());
                        futureTarget.setStateAge(currentAgent.getStateAge());
                        futureTarget.setResistance(currentAgent.getResistance());
                        futureTarget.setMoveProbability(currentAgent.getMoveProbability());
                        futureTarget.setMasked(currentAgent.isMasked());

                        // L'ancienne cellule redevient VIDE
                        futureSource.setState(CellState.EMPTY);
                        futureSource.setStateAge(0);
                        futureSource.setResistance(0);
                        futureSource.setMoveProbability(0);
                        futureSource.setMasked(false);

                        // Note magique : futureSource.zoneType et futureTarget.zoneType ne sont pas modifiés.
                        // Donc le type de zone (Route, commerce...) reste ancré au sol.
                        break; 
                    }
                }
            }
        }

        // 5. On applique la grille future
        this.cells = nextCells;
    }

    /**
     * Phase 2 – infected (and optionally exposed) cells try to transmit to
     * susceptible, vaccinated, and masked cells within their influence radius.
     *
     * <h3>Vaccine protection (inward)</h3>
     * <p>For VACCINATED targets: effective probability = baseRate
     * × (1 − vaccineEfficacy) × (1 − resistance).</p>
     *
     * <h3>Mask protection</h3>
     * <ul>
     *   <li><b>Outward (source control)</b>: if the spreader is MASKED and INFECTED,
     *       its spread rate is reduced by (1 − maskOutwardEfficacy).</li>
     *   <li><b>Inward</b>: if the target is MASKED, the effective probability is
     *       further multiplied by (1 − maskInwardEfficacy).</li>
     * </ul>
     *
     * @param grid the working copy of the grid
     */
    private void infectionPhase(Cell[][] grid) {
        // Snapshot: effective outward transmission rate per spreader cell.
        // MASKED infected cells emit at reduced rate (source control).
        double[][] spreadRate = new double[height][width];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                CellState st = grid[r][c].getState();
                if (st == CellState.INFECTED) {
                    double zoneMultiplier = grid[r][c].getZoneType().getTransmissionMultiplier();
                    spreadRate[r][c] = disease.getTransmissionRate() * zoneMultiplier;
                } else if (st == CellState.EXPOSED && disease.isContagiousInExposed()) {
                    double zoneMultiplier = grid[r][c].getZoneType().getTransmissionMultiplier();
                    spreadRate[r][c] = disease.getExposedTransmissionRate() * zoneMultiplier;
                }
                // else 0.0 — not a spreader (EMPTY, SUSC, VACC, MASKED-healthy, etc.)
            }
        }

        // Note: a MASKED person who is also INFECTED is tracked as INFECTED in state,
        // but the mask flag is stored in the Cell.  We apply outward reduction here.
        // (Mask flag is checked via cell.isMasked())
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++)
                if (spreadRate[r][c] > 0 && grid[r][c].isMasked())
                    spreadRate[r][c] *= (1.0 - disease.getMaskOutwardEfficacy());

        int radius = disease.isAirborne() ? disease.getTransmissionRadius() : 1;

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                if (spreadRate[r][c] == 0.0) continue;

                for (int dr = -radius; dr <= radius; dr++) {
                    for (int dc = -radius; dc <= radius; dc++) {
                        if (dr == 0 && dc == 0) continue;
                        if (disease.isAirborne() && Math.sqrt(dr*dr + dc*dc) > radius) continue;

                        int nr = r + dr;
                        int nc = c + dc;
                        if (toroidal) { nr = wrap(nr, height); nc = wrap(nc, width); }
                        else if (!inBounds(nr, nc)) continue;

                        Cell target = grid[nr][nc];
                        CellState tst = target.getState();

                        // Only susceptible, vaccinated, and masked-healthy can be exposed
                        if (tst != CellState.SUSCEPTIBLE
                         && tst != CellState.VACCINATED) continue;

                        // Base probability
                        double baseRate = spreadRate[r][c];

                        // Vaccine inward protection
                        if (tst == CellState.VACCINATED)
                            baseRate *= (1.0 - disease.getVaccineEfficacy());

                        // Mask inward protection (flag on any state)
                        if (target.isMasked())
                            baseRate *= (1.0 - disease.getMaskInwardEfficacy());

                        double prob = target.effectiveInfectionProbability(baseRate);
                        if (rng.nextDouble() < prob) {
                            target.setState(CellState.EXPOSED);
                            target.resetStateAge();
                        }
                    }
                }
            }
        }
    }

    /**
     * Phase 3 – advance each cell's state age and trigger transitions when
     * the disease-defined thresholds are reached.
     *
     * @param grid the working copy of the grid
     */
    private void progressionPhase(Cell[][] grid) {
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                Cell cell = grid[r][c];
                switch (cell.getState()) {
                    case EXPOSED -> {
                        cell.incrementStateAge();
                        if (cell.getStateAge() >= disease.getIncubationPeriod()) {
                            cell.setState(CellState.INFECTED);
                            cell.resetStateAge();
                        }
                    }
                    case INFECTED -> {
                        cell.incrementStateAge();
                        if (cell.getStateAge() >= disease.getInfectionDuration()) {
                            if (rng.nextDouble() < disease.getMortalityRate()) {
                                cell.setState(CellState.DEAD);
                            } else {
                                cell.setState(CellState.RECOVERED);
                            }
                            cell.resetStateAge();
                        }
                    }
                    case RECOVERED -> {
                        cell.incrementStateAge();
                        if (cell.getStateAge() >= disease.getImmunityDuration()) {
                            cell.setState(CellState.SUSCEPTIBLE);
                            cell.resetStateAge();
                        }
                    }
                    case VACCINATED -> {
                        cell.incrementStateAge();
                        if (cell.getStateAge() >= disease.getVaccineImmunityDuration()) {
                            cell.setState(CellState.SUSCEPTIBLE);
                            cell.resetStateAge();
                        }
                    }
                    // MASKED stays masked indefinitely (user-controlled)
                    // EMPTY, DEAD, SUSCEPTIBLE – no progression
                    default -> { }
                }
            }
        }
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    /**
     * Counts cells in a given state.
     *
     * @param state the state to count
     * @return number of cells currently in that state
     */
    public int countState(CellState state) {
        int count = 0;
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++)
                if (cells[r][c].getState() == state) count++;
        return count;
    }

    /**
     * Returns the total number of non-empty cells (living + dead).
     *
     * @return total occupied cell count
     */
    public int totalPopulation() {
        int count = 0;
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++)
                if (cells[r][c].getState() != CellState.EMPTY) count++;
        return count;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** @return grid width (columns) */
    public int getWidth() { return width; }

    /** @return grid height (rows) */
    public int getHeight() { return height; }

    /** @return true if toroidal topology is active */
    public boolean isToroidal() { return toroidal; }

    /** @param toroidal true to enable toroidal mode */
    public void setToroidal(boolean toroidal) { this.toroidal = toroidal; }

    /**
     * Returns the cell at the given position.
     *
     * @param row row index
     * @param col column index
     * @return the Cell at that position, or null if out of bounds
     */
    public Cell getCell(int row, int col) {
        if (!inBounds(row, col)) return null;
        return cells[row][col];
    }

    /** @return the disease currently configured for this grid */
    public Disease getDisease() { return disease; }

    /** @param disease new disease to apply */
    public void setDisease(Disease disease) { this.disease = disease; }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Checks if (row, col) is within the grid bounds.
     *
     * @param row row index
     * @param col column index
     * @return true if the position is valid
     */
    private boolean inBounds(int row, int col) {
        return row >= 0 && row < height && col >= 0 && col < width;
    }

    /**
     * Wraps an index to stay within [0, size) for toroidal topology.
     *
     * @param index the raw index (may be negative or ≥ size)
     * @param size  the dimension bound
     * @return wrapped index in [0, size)
     */
    private int wrap(int index, int size) {
        return ((index % size) + size) % size;
    }

    /**
     * Returns a shuffled list of all (row, col) coordinates.
     *
     * @return shuffled coordinate list
     */
    private List<int[]> shuffledCoords() {
        List<int[]> list = new ArrayList<>(width * height);
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++)
                list.add(new int[]{r, c});
        Collections.shuffle(list, rng);
        return list;
    }

    /**
     * Performs a deep copy of the cell grid.
     *
     * @param source the source grid to copy
     * @return a new 2-D array of independent Cell copies
     */
    private Cell[][] deepCopy(Cell[][] source) {
        Cell[][] copy = new Cell[height][width];
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++)
                copy[r][c] = source[r][c].copy();
        return copy;
    }

    /**
     * Clears the entire grid (fills with EMPTY cells).
     */
    public void clear() {
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++)
                cells[r][c] = new Cell();
    }
}
