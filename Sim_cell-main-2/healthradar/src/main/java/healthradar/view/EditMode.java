package healthradar.view;

/**
 * The three editing modes available in the HealthRadar UI.
 *
 * <ul>
 *   <li>{@link #BRUSH}      – click or drag to paint cells one by one</li>
 *   <li>{@link #ZONE}       – drag to select a rectangle, filled on release</li>
 *   <li>{@link #INDIVIDUAL} – single click on one cell to cycle its state</li>
 * </ul>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public enum EditMode {
    /** Paint cells while dragging (like a paintbrush). */
    BRUSH,
    /** Select a rectangular region and fill it. */
    ZONE,
    /** Click a single cell to change its state. */
    INDIVIDUAL,
    ZONETYPE
}
