package healthradar.view;

import healthradar.model.Cell;
import healthradar.model.CellState;
import healthradar.model.Grid;
import healthradar.model.ZoneType;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * A JavaFX {@link Canvas} that visually renders the simulation {@link Grid}.
 *
 * <p>Each grid cell is drawn as a coloured square. The colour encodes the
 * cell's {@link CellState}:</p>
 * <ul>
 *   <li>EMPTY       – light grey background</li>
 *   <li>SUSCEPTIBLE – soft blue</li>
 *   <li>EXPOSED     – orange</li>
 *   <li>INFECTED    – red</li>
 *   <li>RECOVERED   – green</li>
 *   <li>DEAD        – dark grey</li>
 * </ul>
 *
 * <p>The view also handles mouse input for the three editing modes defined in
 * {@link EditMode}.</p>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public class GridView extends Canvas {

    // ── Colour palette ────────────────────────────────────────────────────────

    private static final Color COLOR_SUSCEPTIBLE = Color.rgb( 59, 130, 246);
    private static final Color COLOR_VACCINATED  = Color.rgb(167, 139, 250);
    private static final Color COLOR_EXPOSED     = Color.rgb(245, 158,  11);
    private static final Color COLOR_INFECTED    = Color.rgb(239,  68,  68);
    private static final Color COLOR_RECOVERED   = Color.rgb( 34, 197,  94);
    private static final Color COLOR_DEAD        = Color.rgb(100, 116, 139);

    private static final Color COLOR_ZONE_EMPTY_SPACE = Color.rgb(241, 245, 249);
    private static final Color COLOR_ZONE_RESIDENTIAL = Color.rgb(219, 234, 254);
    private static final Color COLOR_ZONE_TRANSPORT   = Color.rgb(203, 213, 225);
    private static final Color COLOR_ZONE_COMMERCIAL  = Color.rgb(207, 250, 254);
    private static final Color COLOR_ZONE_WORK        = Color.rgb(224, 231, 255);
    private static final Color COLOR_ZONE_EDU         = Color.rgb(254, 243, 199);
    private static final Color COLOR_ZONE_HEALTHCARE  = Color.rgb(220, 252, 231);
    
    private static final Color COLOR_GRID_LINE = Color.rgb(203, 213, 225);

    // ── State ─────────────────────────────────────────────────────────────────

    /** The grid to render. */
    private Grid grid;

    /** Pixel width of each cell. */
    private double cellWidth;

    /** Pixel height of each cell. */
    private double cellHeight;

    /** Whether to draw grid lines between cells. */
    private boolean showGridLines = true;

    /** Selected cell shown with a persistent highlight. */
    private int selectedRow = -1;
    private int selectedCol = -1;

    // ── Drag selection for zone mode ──────────────────────────────────────────

    /** Row where the drag started (zone mode). */
    private int dragStartRow = -1;
    /** Column where the drag started (zone mode). */
    private int dragStartCol = -1;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a GridView for the given grid.
     *
     * @param grid     the grid to render
     * @param cellSize initial pixel size per cell
     */
    public GridView(Grid grid, double cellSize) {
        super(grid.getWidth() * cellSize, grid.getHeight() * cellSize);
        this.grid = grid;
        this.cellWidth = cellSize;
        this.cellHeight = cellSize;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * Redraws the entire grid onto this canvas.
     * Should be called after every simulation step or user edit.
     */
    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        // Background
        gc.setFill(Color.rgb(226, 232, 240));
        gc.fillRect(0, 0, w, h);

        // Cells
        for (int r = 0; r < grid.getHeight(); r++) {
            for (int c = 0; c < grid.getWidth(); c++) {
                Cell cell = grid.getCell(r, c);
                double x = c * cellWidth;
                double y = r * cellHeight;

                gc.setFill(getZoneColor(cell.getZoneType()));
                gc.fillRect(x, y, cellWidth, cellHeight);

                if (cell.getState() != CellState.EMPTY) {
                    gc.setFill(stateColor(cell.getState()));
                    
                    double marginX = cellWidth * 0.15;
                    double marginY = cellHeight * 0.15;
                    double circleWidth = cellWidth - (marginX * 2);
                    double circleHeight = cellHeight - (marginY * 2);
                    
                    gc.fillOval(x + marginX, y + marginY, circleWidth, circleHeight);

                    if (cell.isMasked() && cell.isAlive()) {
                        gc.setStroke(Color.BLACK);
                        gc.setLineWidth(Math.max(0.8, Math.min(cellWidth, cellHeight) * 0.1));
                        gc.strokeOval(x + marginX, y + marginY, circleWidth, circleHeight);
                    }
                }
            }
        }

        // Grid lines (only for large enough cells)
        if (showGridLines && Math.min(cellWidth, cellHeight) >= 5) {
            gc.setStroke(COLOR_GRID_LINE);
            gc.setLineWidth(0.5);
            for (int c = 0; c <= grid.getWidth(); c++)
                gc.strokeLine(c * cellWidth, 0, c * cellWidth, h);
            for (int r = 0; r <= grid.getHeight(); r++)
                gc.strokeLine(0, r * cellHeight, w, r * cellHeight);
        }

        drawSelectedCell(gc);
    }

    /**
     * Highlights the currently hovered cell with a white border (brush preview).
     *
     * @param pixelX mouse X in canvas coordinates
     * @param pixelY mouse Y in canvas coordinates
     */
    public void drawHoverHighlight(double pixelX, double pixelY) {
        redraw();
        int col = pixelToCol(pixelX);
        int row = pixelToRow(pixelY);
        if (col < 0 || row < 0 || col >= grid.getWidth() || row >= grid.getHeight()) return;
        GraphicsContext gc = getGraphicsContext2D();
        gc.setStroke(Color.rgb(15, 23, 42));
        gc.setLineWidth(2);
        gc.strokeRect(col * cellWidth + 1, row * cellHeight + 1,
                Math.max(0, cellWidth - 2), Math.max(0, cellHeight - 2));
    }

    private void drawSelectedCell(GraphicsContext gc) {
        if (selectedRow < 0 || selectedCol < 0) return;
        if (selectedRow >= grid.getHeight() || selectedCol >= grid.getWidth()) return;
        gc.setStroke(Color.rgb(250, 204, 21));
        gc.setLineWidth(Math.max(2, Math.min(cellWidth, cellHeight) * 0.18));
        gc.strokeRect(selectedCol * cellWidth + 1, selectedRow * cellHeight + 1,
                Math.max(0, cellWidth - 2), Math.max(0, cellHeight - 2));
    }

    /**
     * Highlights the rectangular selection during a zone-mode drag.
     *
     * @param endRow    current end row
     * @param endCol    current end column
     */
    public void drawZoneSelection(int endRow, int endCol) {
        redraw();
        if (dragStartRow < 0) return;
        int r1 = Math.min(dragStartRow, endRow);
        int c1 = Math.min(dragStartCol, endCol);
        int r2 = Math.max(dragStartRow, endRow);
        int c2 = Math.max(dragStartCol, endCol);
        GraphicsContext gc = getGraphicsContext2D();
        gc.setStroke(Color.YELLOW);
        gc.setLineWidth(2);
        gc.strokeRect(c1 * cellWidth, r1 * cellHeight,
                (c2 - c1 + 1) * cellWidth, (r2 - r1 + 1) * cellHeight);
    }

    private Color getZoneColor(ZoneType type) {
        return switch (type) {
            case RESIDENTIAL -> COLOR_ZONE_RESIDENTIAL;
            case EMPTY_SPACE -> COLOR_ZONE_EMPTY_SPACE;
            case TRANSPORT   -> COLOR_ZONE_TRANSPORT;
            case COMMERCIAL  -> COLOR_ZONE_COMMERCIAL;
            case WORK -> COLOR_ZONE_WORK;
            case EDUCATION -> COLOR_ZONE_EDU;
            case HEALTHCARE  -> COLOR_ZONE_HEALTHCARE;
            default          -> Color.WHITE;
        };
    }

    public static Color stateColor(CellState state) {
        return switch (state) {
            case SUSCEPTIBLE -> COLOR_SUSCEPTIBLE;
            case VACCINATED  -> COLOR_VACCINATED;
            case EXPOSED     -> COLOR_EXPOSED;
            case INFECTED    -> COLOR_INFECTED;
            case RECOVERED   -> COLOR_RECOVERED;
            case DEAD        -> COLOR_DEAD;
            default          -> Color.TRANSPARENT;
        };
    }

    // ── Coordinate conversion ─────────────────────────────────────────────────

    /**
     * Converts a pixel X coordinate to a grid column index.
     *
     * @param px pixel X
     * @return column index, or -1 if out of bounds
     */
    public int pixelToCol(double px) {
        int c = (int)(px / cellWidth);
        return (c >= 0 && c < grid.getWidth()) ? c : -1;
    }

    /**
     * Converts a pixel Y coordinate to a grid row index.
     *
     * @param py pixel Y
     * @return row index, or -1 if out of bounds
     */
    public int pixelToRow(double py) {
        int r = (int)(py / cellHeight);
        return (r >= 0 && r < grid.getHeight()) ? r : -1;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** @param grid the new grid to display (used after load or resize) */
    public void setGrid(Grid grid) {
        this.grid = grid;
        setWidth(grid.getWidth() * cellWidth);
        setHeight(grid.getHeight() * cellHeight);
    }

    /** @return current pixel size of one cell */
    public double getCellSize() { return Math.min(cellWidth, cellHeight); }

    /** @return current pixel width of one cell */
    public double getCellWidth() { return cellWidth; }

    /** @return current pixel height of one cell */
    public double getCellHeight() { return cellHeight; }

    public void setSelectedCell(int row, int col) {
        this.selectedRow = row;
        this.selectedCol = col;
    }

    /**
     * Changes the cell size and resizes the canvas accordingly.
     *
     * @param cellSize new pixel size per cell
     */
    public void setCellSize(double cellSize) {
        setCellSize(cellSize, cellSize);
    }

    /**
     * Changes the cell dimensions and resizes the canvas accordingly.
     *
     * @param cellWidth new pixel width per cell
     * @param cellHeight new pixel height per cell
     */
    public void setCellSize(double cellWidth, double cellHeight) {
        this.cellWidth = Math.max(0.1, cellWidth);
        this.cellHeight = Math.max(0.1, cellHeight);
        setWidth(grid.getWidth() * this.cellWidth);
        setHeight(grid.getHeight() * this.cellHeight);
        redraw();
    }

    /** @param show true to draw grid lines */
    public void setShowGridLines(boolean show) { this.showGridLines = show; }

    /** @return drag start row for zone mode */
    public int getDragStartRow() { return dragStartRow; }

    /** @param dragStartRow drag start row */
    public void setDragStartRow(int dragStartRow) { this.dragStartRow = dragStartRow; }

    /** @return drag start column for zone mode */
    public int getDragStartCol() { return dragStartCol; }

    /** @param dragStartCol drag start column */
    public void setDragStartCol(int dragStartCol) { this.dragStartCol = dragStartCol; }
}
