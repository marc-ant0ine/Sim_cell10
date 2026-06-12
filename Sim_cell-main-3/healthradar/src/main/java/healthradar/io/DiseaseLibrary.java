package healthradar.io;

import healthradar.model.Disease;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists custom {@link Disease} objects to a plain-text file
 * ({@code diseases.csv} in the working directory).
 *
 * <p>Format – one disease per line, fields separated by {@code ;}:</p>
 * <pre>
 * name;airborne;transmissionRate;incubationPeriod;infectionDuration;mortalityRate;immunityDuration;transmissionRadius
 * </pre>
 *
 * <p>This format is human-readable and editable in any text editor.</p>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public class DiseaseLibrary {

    /** Path to the disease library file. */
    private static final Path FILE = Paths.get("diseases.csv");

    /** Field separator used in the CSV. */
    private static final String SEP = ";";

    /** Private constructor – utility class. */
    private DiseaseLibrary() {}

    /**
     * Loads all saved diseases from the library file.
     * Returns an empty list if the file does not exist or cannot be read.
     *
     * @return mutable list of saved diseases (may be empty, never null)
     */
    public static List<Disease> load() {
        List<Disease> list = new ArrayList<>();
        if (!Files.exists(FILE)) return list;
        try (BufferedReader r = Files.newBufferedReader(FILE)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                Disease d = fromCsv(line);
                if (d != null) list.add(d);
            }
        } catch (IOException ignored) {}
        return list;
    }

    /**
     * Saves a disease to the library, replacing any existing entry with the
     * same name (case-insensitive). This prevents duplicates when the same
     * custom disease is saved multiple times.
     *
     * @param d the disease to save or update
     */
    public static void save(Disease d) {
        List<Disease> list = load();
        // Replace existing entry with same name, or append
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getName().equalsIgnoreCase(d.getName())) {
                list.set(i, d);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(d);
        // Rewrite whole file
        try (BufferedWriter w = Files.newBufferedWriter(FILE,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Disease e : list) { w.write(toCsv(e)); w.newLine(); }
        } catch (IOException ignored) {}
    }

    /**
     * Removes the disease at the given index from the library.
     *
     * @param index 0-based index into the list returned by {@link #load()}
     */
    public static void delete(int index) {
        List<Disease> list = load();
        if (index < 0 || index >= list.size()) return;
        list.remove(index);
        try (BufferedWriter w = Files.newBufferedWriter(FILE,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Disease d : list) { w.write(toCsv(d)); w.newLine(); }
        } catch (IOException ignored) {}
    }

    // ── CSV helpers ───────────────────────────────────────────────────────────

    /**
     * Serialises a disease to a single CSV line.
     *
     * @param d the disease
     * @return CSV string
     */
    private static String toCsv(Disease d) {
        return String.join(SEP,
                d.getName().replace(SEP, "_"),
                String.valueOf(d.isAirborne()),
                String.valueOf(d.getTransmissionRate()),
                String.valueOf(d.getIncubationPeriod()),
                String.valueOf(d.getInfectionDuration()),
                String.valueOf(d.getMortalityRate()),
                String.valueOf(d.getImmunityDuration()),
                String.valueOf(d.getTransmissionRadius()),
                String.valueOf(d.isContagiousInExposed()),
                String.valueOf(d.getExposedTransmissionFactor()));
    }

    /**
     * Parses a CSV line back into a Disease.
     * Returns null if the line is malformed.
     *
     * @param line the CSV line
     * @return parsed Disease, or null on error
     */
    private static Disease fromCsv(String line) {
        try {
            String[] f = line.split(SEP, -1);
            if (f.length < 8) return null;
            // Fields 8 and 9 are optional for backward compatibility
            boolean contagiousInExposed    = f.length > 8 && Boolean.parseBoolean(f[8]);
            double  exposedTransmFactor    = f.length > 9 ? Double.parseDouble(f[9]) : 0.5;
            return new Disease(
                    f[0],
                    Boolean.parseBoolean(f[1]),
                    Double.parseDouble(f[2]),
                    Integer.parseInt(f[3]),
                    Integer.parseInt(f[4]),
                    Double.parseDouble(f[5]),
                    Integer.parseInt(f[6]),
                    Integer.parseInt(f[7]),
                    contagiousInExposed,
                    exposedTransmFactor);
        } catch (Exception e) { return null; }
    }
}
