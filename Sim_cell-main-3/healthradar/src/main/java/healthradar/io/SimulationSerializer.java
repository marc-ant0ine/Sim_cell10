package healthradar.io;

import healthradar.model.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Saves and loads the simulation state as a binary .hrs file.
 *
 * <p>The assignment requires binary persistence. New saves use Java object
 * serialization, while loading still accepts the older JSON text format used
 * by previous development builds.</p>
 *
 * @author HealthRadar Team
 * @version 3.0
 */
public class SimulationSerializer {

    /** Legacy JSON format version. New saves use Java binary serialization. */
    private static final int FORMAT_VERSION = 2;

    /** Private constructor — utility class. */
    private SimulationSerializer() {}

    /**
     * Saves the given engine to a binary file.
     *
     * @param engine the engine to save
     * @param path   destination file (will be created or overwritten)
     * @throws IOException if the file cannot be written
     */
    public static void save(SimulationEngine engine, Path path) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(path))) {
            out.writeObject(engine);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Saves the given engine to a JSON text file.
     *
     * @param engine the engine to save
     * @param path   destination file (will be created or overwritten)
     * @throws IOException if the file cannot be written
     */
    private static void saveLegacyJson(SimulationEngine engine, Path path) throws IOException {
        Grid    grid    = engine.getGrid();
        Disease disease = grid.getDisease();

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": ").append(FORMAT_VERSION).append(",\n");
        sb.append("  \"stepCount\": ").append(engine.getStepCount()).append(",\n");
        sb.append("  \"gridWidth\": ").append(grid.getWidth()).append(",\n");
        sb.append("  \"gridHeight\": ").append(grid.getHeight()).append(",\n");
        sb.append("  \"toroidal\": ").append(grid.isToroidal()).append(",\n");

        // Disease block
        sb.append("  \"disease\": {\n");
        sb.append("    \"name\": ").append(jsonStr(disease.getName())).append(",\n");
        sb.append("    \"airborne\": ").append(disease.isAirborne()).append(",\n");
        sb.append("    \"transmissionRate\": ").append(disease.getTransmissionRate()).append(",\n");
        sb.append("    \"incubationPeriod\": ").append(disease.getIncubationPeriod()).append(",\n");
        sb.append("    \"infectionDuration\": ").append(disease.getInfectionDuration()).append(",\n");
        sb.append("    \"mortalityRate\": ").append(disease.getMortalityRate()).append(",\n");
        sb.append("    \"immunityDuration\": ").append(disease.getImmunityDuration()).append(",\n");
        sb.append("    \"transmissionRadius\": ").append(disease.getTransmissionRadius()).append(",\n");
        sb.append("    \"contagiousInExposed\": ").append(disease.isContagiousInExposed()).append(",\n");
        sb.append("    \"exposedTransmissionFactor\": ").append(disease.getExposedTransmissionFactor()).append(",\n");
        sb.append("    \"vaccineEfficacy\": ").append(disease.getVaccineEfficacy()).append(",\n");
        sb.append("    \"vaccineImmunityDuration\": ").append(disease.getVaccineImmunityDuration()).append(",\n");
        sb.append("    \"maskInwardEfficacy\": ").append(disease.getMaskInwardEfficacy()).append(",\n");
        sb.append("    \"maskOutwardEfficacy\": ").append(disease.getMaskOutwardEfficacy()).append("\n");
        sb.append("  },\n");

        // Cells (only non-EMPTY cells to keep file compact)
        sb.append("  \"cells\": [\n");
        boolean firstCell = true;
        for (int r = 0; r < grid.getHeight(); r++) {
            for (int c = 0; c < grid.getWidth(); c++) {
                Cell cell = grid.getCell(r, c);
                if (cell.getState() == CellState.EMPTY && !cell.isMasked()) continue;
                if (!firstCell) sb.append(",\n");
                firstCell = false;
                sb.append("    {\"r\":").append(r)
                  .append(",\"c\":").append(c)
                  .append(",\"state\":").append(jsonStr(cell.getState().name()))
                  .append(",\"stateAge\":").append(cell.getStateAge())
                  .append(",\"resistance\":").append(round6(cell.getResistance()))
                  .append(",\"moveProbability\":").append(round6(cell.getMoveProbability()))
                  .append(",\"masked\":").append(cell.isMasked())
                  .append("}");
            }
        }
        sb.append("\n  ],\n");

        // History (compact — one object per line)
        sb.append("  \"history\": [\n");
        List<SimulationEngine.StepStats> hist = engine.getHistory();
        for (int i = 0; i < hist.size(); i++) {
            SimulationEngine.StepStats s = hist.get(i);
            sb.append("    {\"step\":").append(s.step())
              .append(",\"susceptible\":").append(s.susceptible())
              .append(",\"vaccinated\":").append(s.vaccinated())
              .append(",\"exposed\":").append(s.exposed())
              .append(",\"infected\":").append(s.infected())
              .append(",\"recovered\":").append(s.recovered())
              .append(",\"dead\":").append(s.dead())
              .append("}");
            if (i < hist.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");

        Files.writeString(path, sb.toString());
    }

    /**
     * Loads a simulation from a binary .hrs file.
     *
     * <p>If the file is not a Java serialized stream, the loader falls back to
     * the older JSON text format used by previous development builds.</p>
     *
     * @param path source file path
     * @return a fully restored {@link SimulationEngine}
     * @throws IOException if the file cannot be read or parsed
     */
    public static SimulationEngine load(Path path) throws IOException {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(path))) {
            Object saved = in.readObject();
            if (saved instanceof SimulationEngine engine) {
                if (engine.getHistory().isEmpty()) engine.recordCurrentStats();
                return engine;
            }
            if (saved instanceof Grid grid) {
                return new SimulationEngine(grid);
            }
            throw new IOException("Unsupported save file content: " + saved.getClass().getName());
        } catch (StreamCorruptedException | OptionalDataException e) {
            return loadLegacyJson(path);
        } catch (ClassNotFoundException e) {
            throw new IOException("Unsupported class found in save file.", e);
        }
    }

    /**
     * Loads a simulation from a JSON text file.
     * Silently ignores unknown fields; missing fields receive defaults.
     *
     * @param path source file path
     * @return a fully restored {@link SimulationEngine}
     * @throws IOException if the file cannot be read or parsed
     */
    private static SimulationEngine loadLegacyJson(Path path) throws IOException {
        String json = Files.readString(path);
        JsonObj root = parseObj(json, 0, json.length());

        int gridW    = root.intVal("gridWidth",  60);
        int gridH    = root.intVal("gridHeight", 45);
        boolean tor  = root.boolVal("toroidal",  false);
        int stepCount= root.intVal("stepCount",  0);

        // Disease
        Disease disease = new Disease(
            "Influenza", false, 0.30, 3, 7, 0.01, 30, 1);
        JsonObj d = root.objVal("disease");
        if (d != null) {
            disease = new Disease(
                d.strVal("name",  "Custom"),
                d.boolVal("airborne", false),
                d.dblVal("transmissionRate",  0.30),
                d.intVal("incubationPeriod",  3),
                d.intVal("infectionDuration", 7),
                d.dblVal("mortalityRate",     0.01),
                d.intVal("immunityDuration",  30),
                d.intVal("transmissionRadius",1),
                d.boolVal("contagiousInExposed", false),
                d.dblVal("exposedTransmissionFactor", 0.5)
            );
            disease.setVaccineEfficacy(d.dblVal("vaccineEfficacy", 0.85));
            disease.setVaccineImmunityDuration(d.intVal("vaccineImmunityDuration", 180));
            disease.setMaskInwardEfficacy(d.dblVal("maskInwardEfficacy", 0.50));
            disease.setMaskOutwardEfficacy(d.dblVal("maskOutwardEfficacy", 0.55));
        }

        // Build grid
        Grid grid = new Grid(gridW, gridH, tor, disease, 0);

        

        // Restore cells
        String cellsArr = root.rawArr("cells");
        if (cellsArr != null) {
            for (JsonObj co : parseArr(cellsArr)) {
                int r = co.intVal("r", -1);
                int c = co.intVal("c", -1);
                if (r < 0 || c < 0 || r >= gridH || c >= gridW) continue;
                String stateName = co.strVal("state", "SUSCEPTIBLE");
                CellState state;
                try { state = CellState.valueOf(stateName); }
                catch (IllegalArgumentException e) { state = CellState.SUSCEPTIBLE; }
                grid.setCell(r, c, state);
                Cell cell = grid.getCell(r, c);
                if (cell != null && state != CellState.EMPTY) {
                    cell.resetStateAge();
                    // restore stateAge via reflection-free approach:
                    // increment manually
                    int age = co.intVal("stateAge", 0);
                    for (int i = 0; i < age; i++) cell.incrementStateAge();
                    cell.setResistance(co.dblVal("resistance", 0.20));
                    cell.setMoveProbability(co.dblVal("moveProbability", 0.25));
                    cell.setMasked(co.boolVal("masked", false));
                }
            }
        }

        grid.assignDestinationsByZone();

        // Build engine and restore history
        SimulationEngine engine = new SimulationEngine(grid);
        // Clear auto-recorded initial stat, then inject saved history
        engine.clearHistory();
        engine.setStepCount(stepCount);

        String histArr = root.rawArr("history");
        if (histArr != null) {
            for (JsonObj ho : parseArr(histArr)) {
                engine.injectStat(new SimulationEngine.StepStats(
                    ho.intVal("step",        0),
                    ho.intVal("susceptible", 0),
                    ho.intVal("vaccinated",  0),
                    ho.intVal("exposed",     0),
                    ho.intVal("infected",    0),
                    ho.intVal("recovered",   0),
                    ho.intVal("dead",        0)
                ));
            }
        }
        // If history was empty, record current state
        if (engine.getHistory().isEmpty()) engine.recordCurrentStats();

        return engine;
    }

    // ── Minimal JSON parser ───────────────────────────────────────────────────

    /** Parses a JSON object literal and returns a map of raw value strings. */
    private static JsonObj parseObj(String json, int start, int end) {
        Map<String,String> map = new LinkedHashMap<>();
        // Strip outer braces
        int i = start;
        while (i < end && json.charAt(i) != '{') i++;
        i++; // skip '{'
        while (i < end) {
            // skip whitespace
            while (i < end && " \t\n\r".indexOf(json.charAt(i)) >= 0) i++;
            if (i >= end || json.charAt(i) == '}') break;
            // key
            if (json.charAt(i) != '"') { i++; continue; }
            int ks = i + 1;
            i = nextQuote(json, ks);
            String key = json.substring(ks, i);
            i++; // closing quote
            // colon
            while (i < end && json.charAt(i) != ':') i++;
            i++; // skip ':'
            // skip whitespace
            while (i < end && " \t\n\r".indexOf(json.charAt(i)) >= 0) i++;
            // value
            char vc = json.charAt(i);
            String raw;
            if (vc == '"') {
                int vs = i + 1;
                i = nextQuote(json, vs);
                raw = json.substring(vs, i);
                i++;
            } else if (vc == '{') {
                int depth = 0, vs = i;
                while (i < end) {
                    char ch = json.charAt(i);
                    if (ch == '{') depth++;
                    else if (ch == '}') { depth--; if (depth == 0) { i++; break; } }
                    i++;
                }
                raw = json.substring(vs, i);
            } else if (vc == '[') {
                int depth = 0, vs = i;
                while (i < end) {
                    char ch = json.charAt(i);
                    if (ch == '[') depth++;
                    else if (ch == ']') { depth--; if (depth == 0) { i++; break; } }
                    i++;
                }
                raw = json.substring(vs, i);
            } else {
                int vs = i;
                while (i < end && ",}\n".indexOf(json.charAt(i)) < 0) i++;
                raw = json.substring(vs, i).trim();
            }
            map.put(key, raw);
            // skip comma
            while (i < end && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
            if (i < end && json.charAt(i) == ',') i++;
        }
        return new JsonObj(map);
    }

    /** Splits a JSON array literal into a list of JsonObj elements. */
    private static List<JsonObj> parseArr(String arr) {
        List<JsonObj> list = new ArrayList<>();
        int i = 0;
        while (i < arr.length() && arr.charAt(i) != '[') i++;
        i++;
        while (i < arr.length()) {
            while (i < arr.length() && " \t\n\r,".indexOf(arr.charAt(i)) >= 0) i++;
            if (i >= arr.length() || arr.charAt(i) == ']') break;
            if (arr.charAt(i) == '{') {
                int depth = 0, start = i;
                while (i < arr.length()) {
                    char ch = arr.charAt(i);
                    if (ch == '{') depth++;
                    else if (ch == '}') { depth--; if (depth == 0) { i++; break; } }
                    i++;
                }
                list.add(parseObj(arr, start, i));
            } else i++;
        }
        return list;
    }

    /** Returns the index of the next unescaped quote after {@code from}. */
    private static int nextQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i-1) != '\\')) return i;
        }
        return s.length();
    }

    /** Rounds a double to 6 significant decimals for compact JSON. */
    private static double round6(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    /** Wraps a string in JSON double quotes. */
    private static String jsonStr(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"") + "\"";
    }

    // ── Inner helper class ────────────────────────────────────────────────────

    /** Thin wrapper around a parsed JSON object map. */
    private static class JsonObj {
        private final Map<String,String> m;
        JsonObj(Map<String,String> m) { this.m = m; }

        String  strVal (String k, String  def) { return m.containsKey(k) ? m.get(k)          : def; }
        boolean boolVal(String k, boolean def) { return m.containsKey(k) ? Boolean.parseBoolean(m.get(k)) : def; }
        int     intVal (String k, int     def) {
            try { return m.containsKey(k) ? Integer.parseInt(m.get(k).trim()) : def; }
            catch (NumberFormatException e) { return def; }
        }
        double  dblVal (String k, double  def) {
            try { return m.containsKey(k) ? Double.parseDouble(m.get(k).trim()) : def; }
            catch (NumberFormatException e) { return def; }
        }
        JsonObj objVal (String k) {
            if (!m.containsKey(k)) return null;
            String raw = m.get(k);
            return raw.startsWith("{") ? parseObj(raw, 0, raw.length()) : null;
        }
        String rawArr(String k) { return m.get(k); }
    }
}
