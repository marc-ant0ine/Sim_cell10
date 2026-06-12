package healthradar.model;

/**
 * Enumeration of all possible states a cell (person) can be in.
 *
 * <p>The simulation follows an extended SVIRD epidemiological model.
 * Mask-wearing is modelled as a boolean flag on {@link healthradar.model.Cell}
 * rather than a separate state, so any state (SUSCEPTIBLE, VACCINATED, INFECTED…)
 * can simultaneously carry a mask.</p>
 * <ul>
 *   <li>EMPTY        – no person occupies this cell</li>
 *   <li>SUSCEPTIBLE  – healthy, can be infected</li>
 *   <li>VACCINATED   – immunised; strongly reduced infection probability</li>
 *   <li>EXPOSED      – incubating, not yet contagious</li>
 *   <li>INFECTED     – contagious, showing symptoms</li>
 *   <li>RECOVERED    – immune for a limited time</li>
 *   <li>DEAD         – no longer active</li>
 * </ul>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public enum CellState {
    /** No person in this cell. */
    EMPTY,
    /** Healthy person who can be infected. */
    SUSCEPTIBLE,
    /**
     * Vaccinated person.
     * Infection probability is multiplied by (1 - vaccineEfficacy).
     * After vaccineImmunityDuration ticks the person returns to SUSCEPTIBLE
     * (waning immunity).
     */
    VACCINATED,
    /** Person who has been exposed but is not yet contagious (incubation). */
    EXPOSED,
    /** Contagious person, actively spreading the disease. */
    INFECTED,
    /** Person who recovered and has temporary immunity. */
    RECOVERED,
    /** Person who died from the disease. */
    DEAD
}
