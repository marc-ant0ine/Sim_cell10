package healthradar;

import healthradar.io.ChartExporter;
import healthradar.io.DiseaseLibrary;
import healthradar.model.*;
import healthradar.io.SimulationSerializer;

import java.io.*;
import java.nio.file.Paths;

/**
 * Terminal (command-line) version of HealthRadar.
 *
 * <p>Uses simple numbered menus: type a number + ENTER to choose.
 * A single {@link java.io.BufferedReader} on {@code System.in} is used
 * everywhere (no Scanner + BufferedReader mix) to avoid the double-ENTER bug.</p>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public class TerminalApp {

    // ── ANSI colour codes ─────────────────────────────────────────────────────

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String DIM    = "\u001B[2m";
    private static final String CLEAR  = "\u001B[2J\u001B[H";

    private static final String BG_EMPTY       = "\u001B[48;5;235m";
    private static final String BG_SUSCEPTIBLE = "\u001B[48;5;33m";
    private static final String BG_VACCINATED  = "\u001B[48;5;93m";   // purple
    private static final String BG_EXPOSED     = "\u001B[48;5;214m";
    private static final String BG_INFECTED    = "\u001B[48;5;160m";
    private static final String BG_RECOVERED   = "\u001B[48;5;34m";
    private static final String BG_DEAD        = "\u001B[48;5;240m";

    private static final String FG_CYAN   = "\u001B[38;5;51m";
    private static final String FG_ORANGE = "\u001B[38;5;214m";
    private static final String FG_GREY   = "\u001B[38;5;244m";
    private static final String FG_WHITE  = "\u001B[97m";
    private static final String FG_GREEN  = "\u001B[38;5;82m";
    private static final String FG_RED    = "\u001B[38;5;196m";
    private static final String FG_YELLOW = "\u001B[38;5;226m";

    // ── Single reader for all stdin ───────────────────────────────────────────

    /**
     * The one and only reader on System.in.
     * Using both Scanner and BufferedReader on the same stream causes the
     * double-ENTER bug because each has its own internal buffer.
     */
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

    // ── Simulation state ──────────────────────────────────────────────────────

    private Grid             grid;
    private SimulationEngine engine;
    private Disease          disease = Disease.influenza();

    // ── User configuration ────────────────────────────────────────────────────

    private int     gridWidth      = 40;
    private int     gridHeight     = 20;
    private boolean toroidal       = false;
    private int     stepDelayMs    = 200;
    private int     susceptiblePct = 40;
    private int     infectedPct    = 5;

    /**
     * When true, a grid is already prepared (loaded or manually edited).
     * The main menu will offer to start directly without random populate.
     */
    private boolean gridReady = false;

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Application entry point.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        new TerminalApp().run();
    }

    private void run() { mainMenu(); }

    // ── Main menu ─────────────────────────────────────────────────────────────

    /** Displays the main menu and dispatches user choices. */
    private void mainMenu() {
        while (true) {
            clear();
            banner();
            configLine();
            header("MAIN MENU");
            option("1", "Start simulation");
            option("2", "Configure grid");
            option("3", "Configure disease");
            option("4", "Configure population");
            option("5", "Load saved simulation");
            option("0", "Quit");
            rule();
            switch (prompt()) {
                case "1" -> { initSimulation(); simulationLoop(); }
                case "2" -> configureGrid();
                case "3" -> configureDisease();
                case "4" -> configurePopulation();
                case "5" -> loadSimulation();
                case "0" -> { System.out.println("\n  Goodbye!\n"); System.exit(0); }
                default  -> error("Unknown option, try again.");
            }
        }
    }

    // ── Pause menu ────────────────────────────────────────────────────────────

    /**
     * Pause menu shown below the grid.
     *
     * @return true = resume, false = back to main menu
     */
    private boolean pauseMenu() {
        System.out.println();
        header("PAUSED  –  Step " + engine.getStepCount());
        option("1", "Resume  (auto-play)");
        option("2", "Step-by-step mode");
        option("3", "Restart  –  same settings");
        option("4", "Restart  –  reconfigure everything");
        option("5", "Change speed            (current: " + stepDelayMs + " ms/step)");
        option("6", "Change disease");
        option("7", "Toggle topology         (current: " + (toroidal ? "Toroidal" : "Bounded") + ")");
        option("8", "Save simulation");
        option("P", "Print chart  (export PNG)");
        option("L", "Load simulation");
        option("0", "Quit to main menu");
        rule();
        switch (prompt()) {
            case "1"      -> { return true; }
            case "2"      -> { stepByStepMode(); return true; }
            case "3"      -> {
                // If grid was loaded or manually edited, restart from that exact state
                // instead of random populate
                if (gridReady) {
                    ok("Restarting from saved/edited grid layout...");
                    waitEnter();
                } else {
                    initSimulation();
                }
                return true;
            }
            case "4"      -> { configureGrid(); configureDisease();
                               configurePopulation(); initSimulation(); return true; }
            case "5"      -> changeSpeed();
            case "6"      -> { configureDisease(); grid.setDisease(disease); }
            case "7"      -> { toroidal = !toroidal; grid.setToroidal(toroidal);
                               ok("Topology: " + (toroidal ? "Toroidal" : "Bounded")); }
            case "8"      -> saveSimulation();
            case "p","P"  -> exportChart();
            case "l","L"  -> loadSimulation();
            case "0"      -> { return false; }
            default       -> error("Unknown option.");
        }
        return true;
    }

    // ── Simulation ────────────────────────────────────────────────────────────

    /** Creates a fresh grid and engine from the current configuration. */
    private void initSimulation() {
        grid = new Grid(gridWidth, gridHeight, toroidal, disease, 0);
        int total  = gridWidth * gridHeight;
        int sCount = (int)(total * susceptiblePct / 100.0);
        int iCount = (int)(total * infectedPct    / 100.0);
        grid.randomPopulate(sCount, iCount);
        engine = new SimulationEngine(grid);
    }

    /**
     * Main simulation loop.
     * Runs in the main thread; a daemon thread watches stdin for ENTER.
     * Both threads share only the volatile {@code pauseReq} flag — the
     * daemon never touches the {@link #in} reader, avoiding buffer conflicts.
     */
    /**
     * Flag shared between the main thread and the pause-watcher thread.
     * Volatile so changes are visible across threads without synchronisation.
     */
    private volatile boolean pauseRequested = false;

    /**
     * Set to true by {@link #stepByStepMode()} when the user opens the pause
     * menu from within step-by-step mode and then chooses "Quit to main menu".
     * The simulation loop checks this flag to propagate the exit correctly.
     */
    private boolean exitToMainRequested = false;

    /**
     * When true the watcher thread must not read from stdin.
     * The main thread sets this before calling the pause menu and clears it
     * after the menu returns, guaranteeing the watcher never steals bytes
     * that belong to menu input.
     */
    private volatile boolean watcherSuspended = false;

    private void simulationLoop() {
        pauseRequested  = false;
        watcherSuspended = false;

        // Watcher: reads ONE byte at a time from stdin.
        // It only sets the flag — it never reads a second byte while
        // watcherSuspended is true, so it cannot steal menu input.
        Thread watcher = new Thread(() -> {
            try {
                while (true) {
                    if (watcherSuspended) {
                        // Spin-wait (the menu is active — stay out of stdin)
                        Thread.sleep(10);
                        continue;
                    }
                    // available() > 0 means a byte is ready without blocking
                    // If nothing is there yet, sleep briefly to avoid busy-loop
                    if (System.in.available() == 0) {
                        Thread.sleep(20);
                        continue;
                    }
                    int b = System.in.read();
                    if ((b == '\n' || b == '\r') && !watcherSuspended) {
                        pauseRequested = true;
                    }
                }
            } catch (IOException | InterruptedException ignored) {}
        });
        watcher.setDaemon(true);
        watcher.start();

        while (true) {
            clear();
            printGridAndStats();
            System.out.println();
            System.out.println("  " + DIM + "Press ENTER to pause" + RESET);
            engine.step();

            if (pauseRequested) {
                pauseRequested   = false;
                // Suspend watcher BEFORE showing the menu so it cannot
                // consume any keystroke meant for the menu prompts
                watcherSuspended = true;
                clear();
                printGridAndStats();
                boolean cont = pauseMenu();
                // Re-enable watcher only after menu has fully returned
                watcherSuspended = false;
                // stepByStepMode may have set this if user chose "Quit to main menu"
                if (exitToMainRequested) { exitToMainRequested = false; watcher.interrupt(); return; }
                if (!cont) { watcher.interrupt(); return; }
            }

            try { Thread.sleep(stepDelayMs); } catch (InterruptedException ignored) {}
        }
    }

    // ── Step-by-step mode ─────────────────────────────────────────────────────

    /**
     * Step-by-step mode.
     * <ul>
     *   <li>ENTER – advance one cycle</li>
     *   <li>p     – open the pause menu directly</li>
     *   <li>q     – return to auto-play</li>
     * </ul>
     */
    private void stepByStepMode() {
        clear();
        printGridAndStats();
        printStepByStepHint();
        while (true) {
            System.out.print("  [step " + engine.getStepCount() + "] > ");
            String line = readLine();
            if (line.equalsIgnoreCase("q")) {
                ok("Returning to auto-play.");
                return;
            }
            if (line.equalsIgnoreCase("p")) {
                // Open pause menu inline; if user quits to main menu propagate that
                clear();
                printGridAndStats();
                boolean cont = pauseMenu();
                if (!cont) {
                    // Signal caller (simulationLoop) that we want to exit to main menu
                    // by setting a flag the loop will check
                    exitToMainRequested = true;
                    return;
                }
                // After pause menu, re-print grid and stay in step-by-step
                clear();
                printGridAndStats();
                printStepByStepHint();
                continue;
            }
            // Any other input (including empty ENTER) → advance one step
            engine.step();
            clear();
            printGridAndStats();
            printStepByStepHint();
        }
    }

    /**
     * Prints the step-by-step control hint line.
     */
    private void printStepByStepHint() {
        System.out.println();
        System.out.println("  " + FG_YELLOW + BOLD + "STEP-BY-STEP MODE" + RESET
                + "  –  ENTER = next step   "
                + FG_YELLOW + BOLD + "p" + RESET + " = pause menu   "
                + FG_YELLOW + BOLD + "q" + RESET + " = auto-play");
    }

    // ── Grid rendering ────────────────────────────────────────────────────────

    /** Prints the grid and the statistics block. */
    private void printGridAndStats() {
        System.out.print("  +" + "--".repeat(gridWidth) + "+\n");
        for (int r = 0; r < gridHeight; r++) {
            System.out.print("  |");
            for (int c = 0; c < gridWidth; c++) {
                CellState st = grid.getCell(r, c).getState();
                System.out.print(cellBg(st) + cellCh(st) + RESET);
            }
            System.out.println("|");
        }
        System.out.print("  +" + "--".repeat(gridWidth) + "+\n\n");

        SimulationEngine.StepStats s = engine.latestStats();
        if (s == null) return;
        int total = s.totalLiving() + s.dead();

        System.out.printf("  Step: %s%d%s   Pop: %d   Disease: %s%s%s   %s%s%s%n",
                FG_CYAN+BOLD, s.step(), RESET, total,
                FG_ORANGE, disease.getName(), RESET,
                FG_GREY, toroidal ? "Toroidal" : "Bounded", RESET);

        statLine("Susceptible", BG_SUSCEPTIBLE, s.susceptible(), total);
        statLine("Vaccinated ", BG_VACCINATED,  s.vaccinated(),  total);
        statLine("Exposed    ", BG_EXPOSED,      s.exposed(),     total);
        statLine("Infected   ", BG_INFECTED,     s.infected(),    total);
        statLine("Recovered  ", BG_RECOVERED,    s.recovered(),   total);
        statLine("Dead       ", BG_DEAD,          s.dead(),       total);

        int bw = gridWidth * 2;
        System.out.print("  ");
        int[] counts = {s.susceptible(), s.vaccinated(), s.exposed(), s.infected(), s.recovered(), s.dead()};
        String[] bgs  = {BG_SUSCEPTIBLE, BG_VACCINATED, BG_EXPOSED, BG_INFECTED, BG_RECOVERED, BG_DEAD};
        for (int i = 0; i < counts.length; i++) {
            int w = total == 0 ? 0 : (int) Math.round(counts[i] * bw / (double) total);
            if (w > 0) System.out.print(bgs[i] + " ".repeat(w) + RESET);
        }
        System.out.println();
    }

    private void statLine(String label, String bg, int count, int total) {
        double pct = total == 0 ? 0 : count * 100.0 / total;
        int barW   = total == 0 ? 0 : (int)(count * 20.0 / total);
        System.out.printf("  %s %-11s %s %5d  %5.1f%%  %s%s%s%n",
                bg+FG_WHITE, label, RESET, count, pct,
                bg, " ".repeat(barW), RESET);
    }

    // ── Configuration forms ───────────────────────────────────────────────────

    /**
     * Interactive grid editor — lets the user place and remove cells manually.
     * Changes are applied to a live grid; the result can be saved or used
     * directly as the simulation starting point.
     */
    private void editGridMenu() {
        // Ensure a grid exists to edit
        if (grid == null || grid.getWidth() != gridWidth || grid.getHeight() != gridHeight) {
            grid   = new Grid(gridWidth, gridHeight, toroidal, disease, 0);
            engine = new SimulationEngine(grid);
        }
        gridReady = true;

        while (true) {
            clear();
            banner();
            printGridAndStats();
            System.out.println();
            header("GRID EDITOR");
            option("P", "Place cell        — type state, row, col");
            option("R", "Remove cell       — type row, col");
            option("F", "Fill rectangle    — type state, r1,c1,r2,c2");
            option("C", "Clear entire grid");
            option("V", "Vaccinate all susceptible cells");
            option("M", "Toggle mask on a cell  — type row, col");
            option("S", "Save current layout to file");
            option("0", "Done — back to main menu");
            rule();
            String choice = prompt().toUpperCase();
            switch (choice) {
                case "P" -> placeCellInteractive();
                case "R" -> removeCellInteractive();
                case "F" -> fillRectInteractive();
                case "C" -> {
                    grid.clear();
                    engine = new SimulationEngine(grid);
                    ok("Grid cleared.");
                    waitEnter();
                }
                case "V" -> {
                    int count = 0;
                    for (int r = 0; r < grid.getHeight(); r++)
                        for (int col = 0; col < grid.getWidth(); col++)
                            if (grid.getCell(r,col).getState() == CellState.SUSCEPTIBLE) {
                                grid.setCell(r, col, CellState.VACCINATED);
                                count++;
                            }
                    ok("Vaccinated " + count + " susceptible cells.");
                    engine = new SimulationEngine(grid);
                    waitEnter();
                }
                case "M" -> toggleMaskInteractive();
                case "S" -> saveSimulation();
                case "0" -> { return; }
                default  -> error("Unknown option.");
            }
        }
    }

    /**
     * Prompts for state + row + col and places one cell.
     */
    private void placeCellInteractive() {
        System.out.println();
        System.out.println("  States: S=Susceptible  V=Vaccinated  E=Exposed  I=Infected  R=Recovered  D=Dead");
        System.out.print("  State (S/V/E/I/R/D): ");
        String stateCode = readLine().toUpperCase();
        CellState state = switch (stateCode) {
            case "V" -> CellState.VACCINATED;
            case "E" -> CellState.EXPOSED;
            case "I" -> CellState.INFECTED;
            case "R" -> CellState.RECOVERED;
            case "D" -> CellState.DEAD;
            default  -> CellState.SUSCEPTIBLE;
        };
        int row = readInt("  Row    (0-" + (grid.getHeight()-1) + "): ", 0, 0, grid.getHeight()-1);
        int col = readInt("  Column (0-" + (grid.getWidth()-1)  + "): ", 0, 0, grid.getWidth()-1);
        grid.setCell(row, col, state);
        engine = new SimulationEngine(grid);
        ok("Placed " + state + " at (" + row + ", " + col + ")");
    }

    /**
     * Prompts for row + col and removes one cell (sets to EMPTY).
     */
    private void removeCellInteractive() {
        System.out.println();
        int row = readInt("  Row    (0-" + (grid.getHeight()-1) + "): ", 0, 0, grid.getHeight()-1);
        int col = readInt("  Column (0-" + (grid.getWidth()-1)  + "): ", 0, 0, grid.getWidth()-1);
        grid.setCell(row, col, CellState.EMPTY);
        engine = new SimulationEngine(grid);
        ok("Removed cell at (" + row + ", " + col + ")");
    }

    /**
     * Prompts for state + two corners and fills a rectangle.
     */
    private void fillRectInteractive() {
        System.out.println();
        System.out.println("  States: S  V  E  I  R  D  or EMPTY to clear");
        System.out.print("  State: ");
        String stateCode = readLine().toUpperCase();
        CellState state = switch (stateCode) {
            case "V"     -> CellState.VACCINATED;
            case "E"     -> CellState.EXPOSED;
            case "I"     -> CellState.INFECTED;
            case "R"     -> CellState.RECOVERED;
            case "D"     -> CellState.DEAD;
            case "EMPTY" -> CellState.EMPTY;
            default      -> CellState.SUSCEPTIBLE;
        };
        int r1 = readInt("  Top-left row    (0-" + (grid.getHeight()-1) + "): ", 0, 0, grid.getHeight()-1);
        int c1 = readInt("  Top-left col    (0-" + (grid.getWidth()-1)  + "): ", 0, 0, grid.getWidth()-1);
        int r2 = readInt("  Bottom-right row: ", r1, r1, grid.getHeight()-1);
        int c2 = readInt("  Bottom-right col: ", c1, c1, grid.getWidth()-1);
        grid.fillArea(r1, c1, r2, c2, state);
        engine = new SimulationEngine(grid);
        ok("Filled rectangle (" + r1 + "," + c1 + ")→(" + r2 + "," + c2 + ") with " + state);
    }

    /**
     * Prompts for row + col and toggles the mask flag on that cell.
     */
    private void toggleMaskInteractive() {
        System.out.println();
        int row = readInt("  Row    (0-" + (grid.getHeight()-1) + "): ", 0, 0, grid.getHeight()-1);
        int col = readInt("  Column (0-" + (grid.getWidth()-1)  + "): ", 0, 0, grid.getWidth()-1);
        Cell cell = grid.getCell(row, col);
        if (cell == null || !cell.isAlive()) { error("No living cell at that position."); return; }
        cell.setMasked(!cell.isMasked());
        ok("Mask " + (cell.isMasked() ? "ON" : "OFF") + " at (" + row + ", " + col + ")");
    }

    /** Grid configuration form. */
    private void configureGrid() {
        clear(); banner();
        header("CONFIGURE GRID");
        gridWidth  = readInt ("  Grid width          (5-120,   current=" + gridWidth  + "): ", gridWidth,  5,  120);
        gridHeight = readInt ("  Grid height         (5-50,    current=" + gridHeight + "): ", gridHeight, 5,  50);
        toroidal   = readBool("  Toroidal topology?  y/n       (current=" + (toroidal?"y":"n") + "): ", toroidal);
        ok("Grid: " + gridWidth + "x" + gridHeight + "  topology=" + (toroidal ? "Toroidal" : "Bounded"));
        waitEnter();
    }

    /** Disease selection and configuration. */
    private void configureDisease() {
        while (true) {
            clear(); banner();
            header("CONFIGURE DISEASE");

            // Show saved diseases from library
            java.util.List<Disease> library = DiseaseLibrary.load();
            option("1", "Influenza     (contact,  tx=30%, incub=3,  dur=7,  mort=1%,  immun=30)");
            option("2", "COVID-Like    (airborne, tx=20%, incub=5,  dur=14, mort=2%,  immun=60, r=3)");
            option("3", "Create custom disease");
            if (!library.isEmpty()) {
                System.out.println();
                System.out.println("  " + FG_CYAN + "── Saved diseases ──" + RESET);
                for (int i = 0; i < library.size(); i++)
                    option(String.valueOf(10 + i), library.get(i).getName()
                            + "  (" + (library.get(i).isAirborne() ? "airborne" : "contact")
                            + ", tx=" + String.format("%.0f%%", library.get(i).getTransmissionRate()*100)
                            + ", mort=" + String.format("%.0f%%", library.get(i).getMortalityRate()*100) + ")");
            }
            option("D", "Delete a saved disease");
            option("0", "Cancel");
            rule();

            String choice = prompt();
            switch (choice) {
                case "0" -> { return; }
                case "1" -> { disease = Disease.influenza(); ok("Disease: " + disease); waitEnter(); return; }
                case "2" -> { disease = Disease.covidLike(); ok("Disease: " + disease); waitEnter(); return; }
                case "3" -> { disease = customDisease(); return; }
                case "d","D" -> deleteSavedDisease(library);
                default -> {
                    try {
                        int idx = Integer.parseInt(choice) - 10;
                        if (idx >= 0 && idx < library.size()) {
                            disease = library.get(idx);
                            ok("Disease: " + disease); waitEnter(); return;
                        }
                    } catch (NumberFormatException ignored) {}
                    error("Unknown option.");
                }
            }
        }
    }

    /**
     * Custom disease creation form with option to save to library.
     *
     * @return the configured Disease
     */
    private Disease customDisease() {
        clear(); banner();
        header("CREATE CUSTOM DISEASE");
        boolean airborne = readBool  ("  Airborne?              y/n       (default=n    ): ", false);
        double  txRate   = readDouble("  Transmission rate      0.01-1.0  (default=0.25 ): ", 0.25, 0.01, 1.0);
        int     incub    = readInt   ("  Incubation steps       1-100     (default=3    ): ", 3,    1,   100);
        int     infDur   = readInt   ("  Infection dur. steps   1-200     (default=7    ): ", 7,    1,   200);
        double  mort     = readDouble("  Mortality rate         0.0-0.5   (default=0.01 ): ", 0.01, 0.0, 0.5);
        int     immun    = readInt   ("  Immunity steps         1-500     (default=30   ): ", 30,   1,   500);
        int     radius   = 1;
        if (airborne)
            radius = readInt("  Airborne radius cells  1-10      (default=3    ): ", 3, 1, 10);
        System.out.print("  Disease name  (default=Custom): ");
        String name = readLine();
        if (name.isEmpty()) name = "Custom";
        Disease d = new Disease(name, airborne, txRate, incub, infDur, mort, immun, radius);
        ok("Disease created: " + d);

        if (readBool("  Save to disease library?  y/n: ", false)) {
            DiseaseLibrary.save(d);
            ok("Saved to library.");
        }
        waitEnter();
        return d;
    }

    /**
     * Deletes a saved disease from the library.
     *
     * @param library current list of saved diseases
     */
    private void deleteSavedDisease(java.util.List<Disease> library) {
        if (library.isEmpty()) { error("No saved diseases."); return; }
        System.out.println();
        for (int i = 0; i < library.size(); i++)
            option(String.valueOf(i + 1), library.get(i).getName());
        option("0", "Cancel");
        rule();
        int idx = readInt("  Delete number: ", 0, 0, library.size()) - 1;
        if (idx >= 0) {
            String removed = library.get(idx).getName();
            DiseaseLibrary.delete(idx);
            ok("Deleted: " + removed);
        }
        waitEnter();
    }

    /** Population configuration form. */
    private void configurePopulation() {
        clear(); banner();
        header("CONFIGURE POPULATION");
        susceptiblePct = readInt("  Susceptible %  0-100  (current=" + susceptiblePct + "): ", susceptiblePct, 0, 100);
        infectedPct    = readInt("  Infected %     0-100  (current=" + infectedPct    + "): ", infectedPct,    0, 100);
        if (susceptiblePct + infectedPct > 100)
            warn("Sum > 100%. Will be normalised at runtime.");
        ok(susceptiblePct + "% susceptible,  " + infectedPct + "% infected");
        waitEnter();
    }

    /** Speed configuration. */
    private void changeSpeed() {
        System.out.println();
        stepDelayMs = readInt("  Step delay ms  50-10000  (current=" + stepDelayMs + ",  50=fast  10000=very slow): ",
                stepDelayMs, 50, 10000);
        ok("Speed: " + stepDelayMs + " ms/step");
        waitEnter();
    }

    // ── Save / Load ───────────────────────────────────────────────────────────

    /**
     * Exports the simulation statistics history as a PNG chart file.
     * The file is saved in the current working directory.
     */
    private void exportChart() {
        if (engine == null || engine.getHistory().isEmpty()) {
            error("No simulation data to chart. Run the simulation first.");
            waitEnter();
            return;
        }
        java.nio.file.Path defaultPath = ChartExporter.defaultPath(engine);
        System.out.print("  Save chart as  (default=" + defaultPath.getFileName() + "): ");
        String input = readLine();
        java.nio.file.Path path = input.isEmpty() ? defaultPath : java.nio.file.Paths.get(input);
        // Ensure .png extension
        if (!path.getFileName().toString().toLowerCase().endsWith(".png"))
            path = java.nio.file.Paths.get(path + ".png");
        try {
            ChartExporter.export(engine, path);
            ok("Chart saved: " + path.toAbsolutePath());
        } catch (java.io.IOException e) {
            error("Export failed: " + e.getMessage());
        }
        waitEnter();
    }

    /** Saves the engine to a .hrs file. */
    private void saveSimulation() {
        System.out.println();
        System.out.print("  Save file path  (default=simulation.hrs): ");
        String path = readLine();
        if (path.isEmpty()) path = "simulation.hrs";
        try {
            SimulationSerializer.save(engine, Paths.get(path));
            ok("Saved to " + path);
        } catch (IOException e) { error("Save failed: " + e.getMessage()); }
        waitEnter();
    }

    /** Loads an engine from a .hrs file. */
    private void loadSimulation() {
        System.out.println();
        System.out.print("  Load file path  (default=simulation.hrs): ");
        String path = readLine();
        if (path.isEmpty()) path = "simulation.hrs";
        try {
            engine     = SimulationSerializer.load(Paths.get(path));
            grid       = engine.getGrid();
            disease    = grid.getDisease();
            gridWidth  = grid.getWidth();
            gridHeight = grid.getHeight();
            toroidal   = grid.isToroidal();
            gridReady = true;
            ok("Loaded " + path + "  (step " + engine.getStepCount() + ")");
        } catch (IOException e) { error("Load failed: " + e.getMessage()); }
        waitEnter();
    }

    // ── Input helpers ─────────────────────────────────────────────────────────

    /**
     * Reads one line from stdin using the shared {@link #in} reader.
     * Never returns null; returns empty string on error.
     *
     * @return trimmed line
     */
    private String readLine() {
        try {
            String s = in.readLine();
            return s == null ? "" : s.trim();
        } catch (IOException e) { return ""; }
    }

    /** Prints "> " and returns the trimmed user input. */
    private String prompt() {
        System.out.print("  > ");
        System.out.flush();
        return readLine();
    }

    private int readInt(String label, int def, int min, int max) {
        while (true) {
            System.out.print(label);
            System.out.flush();
            String s = readLine();
            if (s.isEmpty()) { System.out.println("  " + DIM + "Using default: " + def + RESET); return def; }
            try {
                int v = Integer.parseInt(s);
                if (v < min || v > max) { error("Must be between " + min + " and " + max + "."); continue; }
                return v;
            } catch (NumberFormatException e) { error("Not a valid number."); }
        }
    }

    private double readDouble(String label, double def, double min, double max) {
        while (true) {
            System.out.print(label);
            System.out.flush();
            String s = readLine();
            if (s.isEmpty()) { System.out.println("  " + DIM + "Using default: " + def + RESET); return def; }
            try {
                double v = Double.parseDouble(s);
                if (v < min || v > max) { error("Must be between " + min + " and " + max + "."); continue; }
                return v;
            } catch (NumberFormatException e) { error("Not a valid number."); }
        }
    }

    private boolean readBool(String label, boolean def) {
        while (true) {
            System.out.print(label);
            System.out.flush();
            String s = readLine().toLowerCase();
            if (s.isEmpty()) { System.out.println("  " + DIM + "Using default: " + (def?"y":"n") + RESET); return def; }
            if (s.equals("y") || s.equals("yes")) return true;
            if (s.equals("n") || s.equals("no"))  return false;
            error("Please type y or n.");
        }
    }

    // ── UI primitives ─────────────────────────────────────────────────────────

    private void clear()  { System.out.print(CLEAR); System.out.flush(); }
    private void rule()   { System.out.println("  " + FG_GREY + "─".repeat(52) + RESET); }

    private void banner() {
        System.out.println();
        System.out.println("  " + FG_CYAN+BOLD + "+----------------------------------------------------+" + RESET);
        System.out.println("  " + FG_CYAN+BOLD + "|   HealthRadar  –  Disease Propagation Simulator   |" + RESET);
        System.out.println("  " + FG_CYAN+BOLD + "+----------------------------------------------------+" + RESET);
        System.out.println();
    }

    private void header(String t) {
        System.out.println("  " + FG_CYAN+BOLD + "[ " + t + " ]" + RESET);
        System.out.println("  " + FG_CYAN + "─".repeat(t.length() + 4) + RESET);
    }

    private void option(String k, String label) {
        System.out.println("  " + FG_YELLOW+BOLD + "  " + k + RESET + "  " + label);
    }

    private void ok(String m)   { System.out.println("  " + FG_GREEN  + "✔  " + m + RESET); }
    private void error(String m){ System.out.println("  " + FG_RED    + "✘  " + m + RESET); }
    private void warn(String m) { System.out.println("  " + FG_ORANGE + "⚠  " + m + RESET); }

    private void waitEnter() {
        System.out.println();
        System.out.print("  " + DIM + "Press ENTER to continue…" + RESET);
        System.out.flush();
        readLine();
    }

    private void configLine() {
        System.out.println("  " + FG_GREY
                + "Grid: "      + FG_CYAN   + gridWidth+"x"+gridHeight + FG_GREY
                + "  Topo: "    + FG_CYAN   + (toroidal?"Toroidal":"Bounded") + FG_GREY
                + "  Disease: " + FG_ORANGE + disease.getName() + FG_GREY
                + "  Pop: "     + FG_CYAN   + susceptiblePct+"%S  "+infectedPct+"%I" + FG_GREY
                + "  Speed: "   + FG_CYAN   + stepDelayMs+"ms" + RESET);
        System.out.println();
    }

    // ── Cell colours ──────────────────────────────────────────────────────────

    private String cellBg(CellState s) {
        return switch (s) {
            case SUSCEPTIBLE -> BG_SUSCEPTIBLE+FG_WHITE;
            case VACCINATED  -> BG_VACCINATED+FG_WHITE;
            case EXPOSED     -> BG_EXPOSED+FG_WHITE;
            case INFECTED    -> BG_INFECTED+FG_WHITE;
            case RECOVERED   -> BG_RECOVERED+FG_WHITE;
            case DEAD        -> BG_DEAD+FG_WHITE;
            default          -> BG_EMPTY+FG_GREY;
        };
    }

    private String cellCh(CellState s) {
        return switch (s) {
            case SUSCEPTIBLE -> " S";
            case VACCINATED  -> " V";
            case EXPOSED     -> " E";
            case INFECTED    -> " I";
            case RECOVERED   -> " R";
            case DEAD        -> " D";
            default          -> "  ";
        };
    }
}
