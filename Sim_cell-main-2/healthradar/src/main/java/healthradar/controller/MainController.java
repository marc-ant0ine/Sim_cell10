package healthradar.controller;

import healthradar.io.ChartExporter;
import healthradar.io.SimulationSerializer;
import healthradar.model.*;
import healthradar.view.ConfigPanel;
import healthradar.view.EditMode;
import healthradar.view.GridView;
import healthradar.view.StatsPanel;
import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Main application controller.
 *
 * <p>Builds the complete JavaFX scene graph, wires all event handlers, and
 * drives the simulation loop via a {@link AnimationTimer}.</p>
 *
 * <h2>Layout</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────┐
 * │  Top toolbar (disease selector, mode, controls)      │
 * ├───────────────────────────────────┬──────────────────┤
 * │  ScrollPane → GridView (canvas)   │  StatsPanel      │
 * └───────────────────────────────────┴──────────────────┘
 * </pre>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public class MainController {

    // ── Simulation objects ────────────────────────────────────────────────────

    private Grid             grid;
    private SimulationEngine engine;

    // ── View objects ──────────────────────────────────────────────────────────

    private GridView   gridView;
    private StatsPanel statsPanel;
    private Stage      primaryStage;
    private ScrollPane gridScrollPane;
    private BorderPane rootLayout;
    private VBox       toolRail;
    private VBox       sidebarContent;

    // ── Animation loop ────────────────────────────────────────────────────────

    private AnimationTimer animationTimer;
    /** Nanoseconds between automatic steps (default = 200 ms). */
    private long stepIntervalNanos = 200_000_000L;
    private long lastStepTime = 0;
    private boolean running = false;

    // ── Edit state ────────────────────────────────────────────────────────────

    private EditMode   editMode       = EditMode.BRUSH;
    private CellState  paintState     = CellState.SUSCEPTIBLE;
    /** When true, painting toggles the mask flag instead of changing the state. */
    private boolean    maskMode       = false;

    // ── UI controls (kept as fields for cross-method access) ──────────────────

    private Label      statusLabel;
    private Slider     speedSlider;
    private Label      stepDelayValueLabel;
    private MenuButton delayMenuButton;
    private ComboBox<String> diseaseCombo;
    private ComboBox<String> paintStateCombo;
    private ComboBox<ZoneType> zoneTypeCombo;
    private Label inspectorPositionValue;
    private Label inspectorStateValue;
    private Label inspectorZoneValue;
    private Label inspectorAgeValue;
    private Label inspectorResistanceValue;
    private Label inspectorMoveValue;
    private Label inspectorMaskValue;
    private Label inspectorRiskValue;
    private CheckBox toroidalSetupCheck;
    private int selectedRow = -1;
    private int selectedCol = -1;

    // ── Disease presets ───────────────────────────────────────────────────────

    private Disease currentDisease = Disease.influenza();

    private static final int MIN_STEP_DELAY_MS = 50;
    private static final int MAX_STEP_DELAY_MS = 10_000;
    private static final double DEFAULT_CELL_SIZE = 14.0;
    private double configuredCellSize = DEFAULT_CELL_SIZE;

    // ── Random populate sliders ───────────────────────────────────────────────

    private Slider susceptibleSlider;
    private Slider infectedSlider;
    private Slider moveProbSlider;

    // ── Disease parameter sliders ─────────────────────────────────────────────

    private Slider transmissionSlider;
    private Slider mortalitySlider;
    private Slider radiusSlider;
    private Slider incubationSlider;
    private Slider infectionDurSlider;
    private Slider immunitySlider;
    private Slider vaccineEfficacySlider;
    private Slider vaccineImmunitySlider;
    private Slider maskInwardSlider;
    private Slider maskOutwardSlider;
    private CheckBox airborneCheck;

    // ─────────────────────────────────────────────────────────────────────────
    //  Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds and displays the main application window.
     *
     * @param stage the primary JavaFX stage provided by the Application class
     */
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Default grid: 60 × 45 cells, bounded, Influenza
        grid   = new Grid(60, 45, false, currentDisease, 0);
        engine = new SimulationEngine(grid);

        gridView   = new GridView(grid, configuredCellSize);
        statsPanel = new StatsPanel(engine);

        // ── Top toolbar ───────────────────────────────────────────────────────
        HBox toolbar = buildToolbar();
        toolRail = buildToolRail();
// ── Centre: scrollable grid canvas ───────────────────────────────────
        StackPane gridViewport = new StackPane(gridView);
        gridViewport.getStyleClass().add("grid-viewport");
        gridScrollPane = new ScrollPane(gridViewport);
        gridScrollPane.getStyleClass().add("grid-scroll");
        gridScrollPane.setFitToWidth(true);
        gridScrollPane.setFitToHeight(true);
        gridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        gridScrollPane.setPannable(false);

        gridView.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (e.isMiddleButtonDown()) {
                gridScrollPane.setPannable(true);
            }
        });
        gridView.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            gridScrollPane.setPannable(false);
        });
        setupResponsiveGrid();
        BorderPane gridSurface = buildGridSurface(gridScrollPane);

        // ── Right sidebar ────────────────────────────────────────────────────
        sidebarContent = buildSidebar();

        // ── Status bar ────────────────────────────────────────────────────────
        statusLabel = new Label("Ready – draw cells, then press Play.");
        statusLabel.setFont(Font.font("Monospaced", 11));
        statusLabel.setTextFill(Color.LIGHTGRAY);
        HBox statusBar = new HBox(statusLabel);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(4, 8, 4, 8));

        // ── Root layout ───────────────────────────────────────────────────────
        rootLayout = new BorderPane();
        rootLayout.setTop(toolbar);
        rootLayout.setLeft(buildToolRailScroll(toolRail));
        rootLayout.setCenter(gridSurface);
        rootLayout.setRight(sidebarContent);
        rootLayout.setBottom(statusBar);
        rootLayout.getStyleClass().add("app-root");

        // ── Mouse events on grid canvas ───────────────────────────────────────
        wireMouseEvents();

        // ── Animation timer ───────────────────────────────────────────────────
        buildAnimationTimer();

        Scene scene = new Scene(rootLayout, 1280, 780);
        scene.setFill(Color.rgb(18, 18, 42));
        var stylesheet = getClass().getResource("/healthradar/app.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        stage.setTitle("HealthRadar - Disease Propagation Simulator");
        stage.setScene(scene);
        stage.show();

        refitGridToViewport();
        gridView.redraw();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI builders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the top toolbar containing simulation controls and mode selectors.
     *
     * @return the constructed HBox toolbar
     */
    private HBox buildToolbar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.getStyleClass().add("top-bar");

        Label appTitle = new Label("HealthRadar");
        appTitle.getStyleClass().add("app-title");
        Label appSubtitle = new Label("Disease spread simulation");
        appSubtitle.getStyleClass().add("app-subtitle");
        VBox brand = new VBox(1, appTitle, appSubtitle);
        brand.setMinWidth(190);

        // ── Play / Pause ──────────────────────────────────────────────────────
        Button playBtn  = styledButton("▶ Play",  "#2ecc71");
        Button pauseBtn = styledButton("⏸ Pause", "#e67e22");
        Button stepBtn  = styledButton("⏭ Day",   "#3498db");
        Button resetBtn = styledButton("↺ Reset", "#e74c3c");

        playBtn.setOnAction(e  -> startSimulation());
        pauseBtn.setOnAction(e -> pauseSimulation());
        stepBtn.setOnAction(e  -> { pauseSimulation(); doStep(); });
        resetBtn.setOnAction(e -> resetSimulation());

        // ── Speed ─────────────────────────────────────────────────────────────
        MenuButton delayMenu = buildDelayMenu();

        // ── Disease selector ──────────────────────────────────────────────────
        Label diseaseLbl = whiteLabel("Disease:");
        diseaseCombo = new ComboBox<>();
        diseaseCombo.getItems().addAll("Influenza", "COVID-Like", "Custom");
        diseaseCombo.setValue("Influenza");
        diseaseCombo.setOnAction(e -> onDiseaseSelected());

        Button saveBtn = styledButton("Save", "#8e44ad");
        Button loadBtn = styledButton("Load", "#2980b9");
        saveBtn.setOnAction(e -> saveSimulation());
        loadBtn.setOnAction(e -> loadSimulation());

        // ── Settings button ───────────────────────────────────────────────────
        Button settingsBtn = styledButton("⚙ Settings", "#34495e");
        settingsBtn.setOnAction(e -> openSettings());

        Button printBtn = styledButton("📊 Print chart", "#16a085");
        printBtn.setOnAction(e -> exportChart());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(
                brand,
                playBtn, pauseBtn, stepBtn, resetBtn,
                new Separator(Orientation.VERTICAL),
                delayMenu,
                new Separator(Orientation.VERTICAL),
                diseaseLbl, diseaseCombo,
                spacer,
                new Separator(Orientation.VERTICAL),
                saveBtn, loadBtn,
                settingsBtn,
                new Separator(Orientation.VERTICAL),
                printBtn
        );
        return bar;
    }

    private VBox buildToolRail() {
        VBox rail = new VBox(14);
        rail.setPrefWidth(230);
        rail.setMinWidth(220);
        rail.setPadding(new Insets(14));
        rail.getStyleClass().add("tool-rail");

        Label title = new Label("Draw tools");
        title.getStyleClass().add("panel-title");
        Label hint = new Label("Choose a map editing mode.");
        hint.getStyleClass().add("muted-label");
        hint.setWrapText(true);

        ToggleGroup modeGroup = new ToggleGroup();
        ToggleButton brushBtn = modeToggle("Brush cells", EditMode.BRUSH, modeGroup);
        ToggleButton zoneBtn = modeToggle("Fill rectangle", EditMode.ZONE, modeGroup);
        ToggleButton indivBtn = modeToggle("Single cell", EditMode.INDIVIDUAL, modeGroup);
        ToggleButton zoneTypeBtn = modeToggle("Zone layer", EditMode.ZONETYPE, modeGroup);
        brushBtn.setTooltip(new Tooltip("Paint the selected health state while dragging."));
        zoneBtn.setTooltip(new Tooltip("Drag a rectangle and fill it with the selected health state."));
        indivBtn.setTooltip(new Tooltip("Edit one cell per click."));
        zoneTypeBtn.setTooltip(new Tooltip("Paint the urban zone layer. Zones affect transmission risk without changing health state."));
        brushBtn.setSelected(true);
        makeFullWidth(brushBtn, zoneBtn, indivBtn, zoneTypeBtn);

        paintStateCombo = new ComboBox<>();
        paintStateCombo.getItems().addAll("Susceptible", "Vaccinated", "Exposed", "Infected", "Recovered", "Dead", "Empty");
        paintStateCombo.setValue("Susceptible");
        paintStateCombo.setMaxWidth(Double.MAX_VALUE);
        paintStateCombo.setOnAction(e -> onPaintStateSelected());

        zoneTypeCombo = new ComboBox<>();
        zoneTypeCombo.getItems().addAll(ZoneType.values());
        zoneTypeCombo.setValue(ZoneType.RESIDENTIAL);
        zoneTypeCombo.setMaxWidth(Double.MAX_VALUE);
        zoneTypeCombo.setTooltip(new Tooltip("Zone layer used by the Zone layer tool."));

        ToggleButton maskBtn = new ToggleButton("Mask paint mode");
        maskBtn.setMaxWidth(Double.MAX_VALUE);
        maskBtn.getStyleClass().add("tool-toggle");
        maskBtn.selectedProperty().addListener((obs, oldValue, selected) -> {
            maskMode = selected;
            setStatus(selected
                    ? "Mask mode ON - paint to toggle masks on cells"
                    : "Mask mode OFF");
        });

        VBox setupBox = toolGroup("Setup");
        Label setupHint = new Label("Initial population and grid options.");
        setupHint.getStyleClass().add("muted-label");
        setupHint.setWrapText(true);
        setupBox.getChildren().add(setupHint);

        susceptibleSlider = labelledSlider("Susceptible %", 0, 100, 40, setupBox);
        infectedSlider = labelledSlider("Infected %", 0, 100, 5, setupBox);

        Button populateBtn = styledButton("Populate", "#16a085");
        Button clearBtn = styledButton("Clear", "#c0392b");
        populateBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        populateBtn.setOnAction(e -> randomPopulate());
        clearBtn.setOnAction(e -> clearGrid());
        HBox setupActions = new HBox(8, populateBtn, clearBtn);
        HBox.setHgrow(populateBtn, Priority.ALWAYS);
        HBox.setHgrow(clearBtn, Priority.ALWAYS);

        toroidalSetupCheck = new CheckBox("Toroidal topology");
        toroidalSetupCheck.setSelected(grid.isToroidal());
        toroidalSetupCheck.setOnAction(e -> {
            grid.setToroidal(toroidalSetupCheck.isSelected());
            setStatus("Topology: " + (grid.isToroidal() ? "Toroidal" : "Bounded"));
        });

        Button settingsBtn = styledButton("Full settings", "#334155");
        settingsBtn.setMaxWidth(Double.MAX_VALUE);
        settingsBtn.setOnAction(e -> openSettings());

        setupBox.getChildren().addAll(setupActions, toroidalSetupCheck, settingsBtn);

        rail.getChildren().addAll(
                title,
                hint,
                toolGroup("Mode", brushBtn, zoneBtn, indivBtn, zoneTypeBtn),
                toolGroup("Cell state", paintStateCombo),
                toolGroup("Zone risk layer", zoneTypeCombo),
                toolGroup("Protection", maskBtn),
                setupBox
        );
        return rail;
    }

    private ScrollPane buildToolRailScroll(VBox rail) {
        ScrollPane scrollPane = new ScrollPane(rail);
        scrollPane.getStyleClass().add("tool-rail-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setMinWidth(232);
        scrollPane.setPrefWidth(244);
        return scrollPane;
    }

    private BorderPane buildGridSurface(ScrollPane scrollPane) {
        BorderPane surface = new BorderPane(scrollPane);
        surface.getStyleClass().add("grid-surface");
        return surface;
    }

    private void setupResponsiveGrid() {
        if (gridScrollPane == null) return;
        gridScrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) ->
                fitGridToViewport(newBounds));
    }

    private void refitGridToViewport() {
        if (gridScrollPane == null) {
            gridView.setCellSize(configuredCellSize);
            return;
        }
        fitGridToViewport(gridScrollPane.getViewportBounds());
    }

    private void fitGridToViewport(Bounds viewport) {
        if (viewport == null || grid == null || grid.getWidth() <= 0 || grid.getHeight() <= 0) return;
        double availableWidth = viewport.getWidth();
        double availableHeight = viewport.getHeight();
        if (availableWidth <= 0 || availableHeight <= 0) return;

        double targetCellSize = Math.min(availableWidth / grid.getWidth(),
                availableHeight / grid.getHeight());
        if (Double.isNaN(targetCellSize) || Double.isInfinite(targetCellSize)) return;
        targetCellSize = Math.max(4.0, targetCellSize);

        if (Math.abs(gridView.getCellSize() - targetCellSize) > 0.2) {
            gridView.setCellSize(targetCellSize);
        }
    }

    private VBox toolGroup(String title, Node... controls) {
        VBox group = new VBox(7);
        group.setMinWidth(0);
        group.setMaxWidth(Double.MAX_VALUE);
        group.getStyleClass().add("tool-group");

        Label label = new Label(title);
        label.getStyleClass().add("tool-group-title");
        group.getChildren().add(label);
        group.getChildren().addAll(controls);
        return group;
    }

    private HBox legendItem(String label, Color color) {
        Region swatch = new Region();
        swatch.setPrefSize(10, 10);
        swatch.setMinSize(10, 10);
        swatch.setMaxSize(10, 10);
        swatch.setStyle("-fx-background-color:" + toCss(color) + "; -fx-background-radius:2;");

        Label text = new Label(label);
        text.getStyleClass().add("legend-label");
        HBox item = new HBox(5, swatch, text);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private void makeFullWidth(Control... controls) {
        for (Control control : controls) {
            control.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private String toCss(Color color) {
        return String.format(Locale.ROOT, "rgb(%d,%d,%d)",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    /**
     * Builds the right sidebar containing disease parameters and random
     * population controls.
     *
     * @return the constructed VBox sidebar
     */
    private VBox buildSidebar() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12, 18, 12, 12));
        box.setMinWidth(340);
        box.setPrefWidth(360);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().add("side-panel");

        Label title = new Label("HealthRadar");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font("System Bold", 19));

        Label subtitle = new Label("Simulation controls");
        subtitle.setTextFill(Color.rgb(140, 165, 190));
        subtitle.setFont(Font.font(11));

        VBox header = new VBox(1, title, subtitle);
        header.setPadding(new Insets(0, 0, 4, 0));
        box.getChildren().add(header);

        TabPane sideTabs = new TabPane();
        sideTabs.getStyleClass().add("side-tabs");
        sideTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(sideTabs, Priority.ALWAYS);

        VBox monitorTab = new VBox(10);
        monitorTab.setPadding(new Insets(0, 4, 0, 0));
        VBox diseaseTab = new VBox(10);
        diseaseTab.setPadding(new Insets(0, 4, 0, 0));

        monitorTab.getChildren().add(buildCellInspector());

        // ── Stats panel ───────────────────────────────────────────────────────
        VBox statsBox = sidebarSectionBox();
        statsBox.getChildren().add(sectionLabel("Live Statistics"));
        statsBox.getChildren().add(statsPanel);
        monitorTab.getChildren().add(statsBox);

        // ── Disease parameters ────────────────────────────────────────────────
        VBox diseaseBox = sidebarSectionBox();
        diseaseBox.getChildren().add(sectionLabel("Disease Parameters"));

        airborneCheck = new CheckBox("Airborne transmission");
        airborneCheck.setTextFill(Color.WHITE);
        airborneCheck.setFont(Font.font(12));
        airborneCheck.setSelected(currentDisease.isAirborne());
        airborneCheck.setOnAction(e -> applyDiseaseParams());

        transmissionSlider = labelledSlider("Transmission (0.01-1.0)",
                0.01, 1.0, currentDisease.getTransmissionRate(), diseaseBox);
        mortalitySlider    = labelledSlider("Mortality (0.0-0.5)",
                0.0,  0.5, currentDisease.getMortalityRate(),    diseaseBox);
        radiusSlider       = labelledSlider("Airborne radius (1-10 cells)",
                1, 10, currentDisease.getTransmissionRadius(),   diseaseBox);
        incubationSlider   = labelledSlider("Incubation days (1-30)",
                1, 30, currentDisease.getIncubationPeriod(),     diseaseBox);
        infectionDurSlider = labelledSlider("Infectious days (1-60)",
                1, 60, currentDisease.getInfectionDuration(),    diseaseBox);
        immunitySlider     = labelledSlider("Immunity days (1-120)",
                1, 120, currentDisease.getImmunityDuration(),    diseaseBox);

        transmissionSlider.valueProperty().addListener((o, v1, v2) -> applyDiseaseParams());
        mortalitySlider   .valueProperty().addListener((o, v1, v2) -> applyDiseaseParams());
        radiusSlider      .valueProperty().addListener((o, v1, v2) -> applyDiseaseParams());
        incubationSlider  .valueProperty().addListener((o, v1, v2) -> applyDiseaseParams());
        infectionDurSlider.valueProperty().addListener((o, v1, v2) -> applyDiseaseParams());
        immunitySlider    .valueProperty().addListener((o, v1, v2) -> applyDiseaseParams());

        diseaseBox.getChildren().add(airborneCheck);
        diseaseTab.getChildren().add(diseaseBox);

        VBox protectionBox = sidebarSectionBox();
        protectionBox.getChildren().add(sectionLabel("Vaccine & Mask Parameters"));
        vaccineEfficacySlider = labelledSlider("Vaccine efficacy (0.0-1.0)",
                0.0, 1.0, currentDisease.getVaccineEfficacy(), protectionBox);
        vaccineImmunitySlider = labelledSlider("Vaccine immunity days (1-500)",
                1, 500, currentDisease.getVaccineImmunityDuration(), protectionBox);
        maskInwardSlider  = labelledSlider("Mask inward (0.0-1.0)",
                0.0, 1.0, currentDisease.getMaskInwardEfficacy(), protectionBox);
        maskOutwardSlider = labelledSlider("Mask outward (0.0-1.0)",
                0.0, 1.0, currentDisease.getMaskOutwardEfficacy(), protectionBox);

        vaccineEfficacySlider.valueProperty().addListener((o, v1, v2) -> applyDiseaseParams());
        vaccineImmunitySlider.valueProperty().addListener((o, v1, v2) -> applyDiseaseParams());
        maskInwardSlider     .valueProperty().addListener((o, v1, v2) -> applyDiseaseParams());
        maskOutwardSlider    .valueProperty().addListener((o, v1, v2) -> applyDiseaseParams());
        diseaseTab.getChildren().add(protectionBox);

        VBox movementBox = sidebarSectionBox();
        movementBox.getChildren().add(sectionLabel("Movement Parameters"));
        moveProbSlider = labelledSlider("Global movement probability (0.0-1.0)",
                0.0, 1.0, 0.25, movementBox);
        moveProbSlider.valueProperty().addListener((o, v1, v2) ->
                applyMovementProbability(v2.doubleValue()));
        diseaseTab.getChildren().add(movementBox);

        sideTabs.getTabs().addAll(
                sidebarTab("Monitor", monitorTab),
                sidebarTab("Disease", diseaseTab)
        );
        sideTabs.getSelectionModel().selectFirst();
        box.getChildren().add(sideTabs);

        return box;
    }

    private Tab sidebarTab(String title, Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("right-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        Tab tab = new Tab(title, scrollPane);
        tab.setClosable(false);
        return tab;
    }

    private VBox sidebarSectionBox() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setMinWidth(0);
        section.setMaxWidth(Double.MAX_VALUE);
        section.getStyleClass().add("section-card");
        return section;
    }

    private TitledPane sectionPane(String title, VBox content, boolean expanded) {
        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(expanded);
        pane.setAnimated(true);
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setTextFill(Color.WHITE);
        pane.getStyleClass().add("section-pane");
        return pane;
    }

    private VBox buildCellInspector() {
        VBox panel = new VBox(7);
        panel.setPadding(new Insets(10));
        panel.getStyleClass().add("inspector-card");

        Label title = sectionLabel("Cell Inspector");
        Label hint = new Label("Click a grid cell to inspect its current data.");
        hint.setTextFill(Color.rgb(170, 190, 205));
        hint.setFont(Font.font(10));

        inspectorPositionValue = inspectorValue("None");
        inspectorStateValue = inspectorValue("-");
        inspectorZoneValue = inspectorValue("-");
        inspectorAgeValue = inspectorValue("-");
        inspectorResistanceValue = inspectorValue("-");
        inspectorMoveValue = inspectorValue("-");
        inspectorMaskValue = inspectorValue("-");
        inspectorRiskValue = inspectorValue("-");

        panel.getChildren().addAll(
                title,
                hint,
                inspectorRow("Position", inspectorPositionValue),
                inspectorRow("State", inspectorStateValue),
                inspectorRow("Zone", inspectorZoneValue),
                inspectorRow("State age", inspectorAgeValue),
                inspectorRow("Resistance", inspectorResistanceValue),
                inspectorRow("Movement", inspectorMoveValue),
                inspectorRow("Mask", inspectorMaskValue),
                inspectorRow("Zone risk", inspectorRiskValue)
        );
        return panel;
    }

    private HBox inspectorRow(String name, Label value) {
        Label key = new Label(name);
        key.setTextFill(Color.rgb(145, 165, 185));
        key.setFont(Font.font("System Bold", 11));
        key.setMinWidth(92);
        HBox row = new HBox(8, key, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label inspectorValue(String text) {
        Label value = new Label(text);
        value.setTextFill(Color.WHITE);
        value.setFont(Font.font("Monospaced", 11));
        return value;
    }

    private void updateCellInspector(int row, int col) {
        if (row < 0 || col < 0 || row >= grid.getHeight() || col >= grid.getWidth()) return;
        selectedRow = row;
        selectedCol = col;
        gridView.setSelectedCell(row, col);

        healthradar.model.Cell cell = grid.getCell(row, col);
        if (cell == null) return;

        inspectorPositionValue.setText(row + ", " + col);
        inspectorStateValue.setText(cell.getState().name());
        inspectorZoneValue.setText(cell.getZoneType().name());
        inspectorRiskValue.setText(String.format(Locale.ROOT, "x%.2f",
                cell.getZoneType().getTransmissionMultiplier()));

        if (cell.getState() == CellState.EMPTY) {
            inspectorAgeValue.setText("-");
            inspectorResistanceValue.setText("-");
            inspectorMoveValue.setText("-");
            inspectorMaskValue.setText("-");
            return;
        }

        inspectorAgeValue.setText(String.valueOf(cell.getStateAge()));
        inspectorResistanceValue.setText(String.format(Locale.ROOT, "%.0f%%",
                cell.getResistance() * 100));
        inspectorMoveValue.setText(String.format(Locale.ROOT, "%.0f%%",
                cell.getMoveProbability() * 100));
        inspectorMaskValue.setText(cell.isMasked() ? "Yes" : "No");
    }

    private void refreshSelectedCellInspector() {
        if (selectedRow >= 0 && selectedCol >= 0) {
            updateCellInspector(selectedRow, selectedCol);
        }
    }

    private void clearCellInspector() {
        selectedRow = -1;
        selectedCol = -1;
        gridView.setSelectedCell(-1, -1);

        if (inspectorPositionValue == null) return;
        inspectorPositionValue.setText("None");
        inspectorStateValue.setText("-");
        inspectorZoneValue.setText("-");
        inspectorAgeValue.setText("-");
        inspectorResistanceValue.setText("-");
        inspectorMoveValue.setText("-");
        inspectorMaskValue.setText("-");
        inspectorRiskValue.setText("-");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Mouse event wiring
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attaches all mouse event handlers to the {@link GridView} canvas.
     */
    private void wireMouseEvents() {
        gridView.setOnMousePressed(this::onMousePressed);
        gridView.setOnMouseDragged(this::onMouseDragged);
        gridView.setOnMouseReleased(this::onMouseReleased);
        gridView.setOnMouseMoved(e -> gridView.drawHoverHighlight(e.getX(), e.getY()));
        gridView.setOnMouseExited(e -> gridView.redraw());
    }

    /**
     * Handles a mouse-pressed event on the grid canvas.
     *
     * @param e the mouse event
     */
    private void onMousePressed(MouseEvent e) {
        int row = gridView.pixelToRow(e.getY());
        int col = gridView.pixelToCol(e.getX());
        if (row < 0 || col < 0) return;

        switch (editMode) {
            case BRUSH, INDIVIDUAL -> {
                if (!maskMode) {
                    grid.setCell(row, col, paintState);
                } else {
                    if (paintState != CellState.EMPTY) {
                        grid.setCell(row, col, paintState);
                    }
                }
                if (maskMode) {
                    var cell = grid.getCell(row, col);
                    if (cell != null && cell.isAlive()) {
                        cell.setMasked(!cell.isMasked()); // Toggle le masque au clic
                    }
                }
                gridView.redraw();
            }
            case ZONE -> {
                gridView.setDragStartRow(row);
                gridView.setDragStartCol(col);
            }
            case ZONETYPE -> { // <-- Nouveau cas
                var cell = grid.getCell(row, col);
                ZoneType selectedZone = zoneTypeCombo.getValue();
                if (cell != null && selectedZone != null) {
                    cell.setZoneType(selectedZone);
                }
                gridView.redraw();
            }
        }
        updateCellInspector(row, col);
        gridView.redraw();
    }

    /**
     * Handles a mouse-dragged event on the grid canvas.
     *
     * @param e the mouse event
     */private void onMouseDragged(MouseEvent e) {
        int row = gridView.pixelToRow(e.getY());
        int col = gridView.pixelToCol(e.getX());
        if (row < 0 || col < 0) return;

        switch (editMode) {
            case BRUSH -> {
                // 1. Applique d'abord l'état si nécessaire
                if (!maskMode) {
                    grid.setCell(row, col, paintState);
                } else {
                    if (paintState != CellState.EMPTY) {
                        grid.setCell(row, col, paintState);
                    }
                }

                // 2. Force le masque à 'true' pendant le glisser de souris (drag)
                if (maskMode) {
                    var cell = grid.getCell(row, col);
                    if (cell != null && cell.isAlive()) {
                        cell.setMasked(true); 
                    }
                }
                updateCellInspector(row, col);
                gridView.redraw();
            }
            case ZONE -> {
                updateCellInspector(row, col);
                gridView.drawZoneSelection(row, col);
            }
            case INDIVIDUAL -> { /* handled on press only */ }
            case ZONETYPE -> { // <-- Nouveau cas
                var cell = grid.getCell(row, col);
                ZoneType selectedZone = zoneTypeCombo.getValue();
                if (cell != null && selectedZone != null) {
                    cell.setZoneType(selectedZone);
                }
                updateCellInspector(row, col);
                gridView.redraw();
            }
        }
    }
/**
     * Handles a mouse-released event on the grid canvas (finalises zone fill).
     *
     * @param e the mouse event
     */
    private void onMouseReleased(MouseEvent e) {
        if (editMode != EditMode.ZONE) return;
        int row = gridView.pixelToRow(e.getY());
        int col = gridView.pixelToCol(e.getX());
        if (row < 0 || col < 0) return;
        
        int r1 = gridView.getDragStartRow(), c1 = gridView.getDragStartCol();
        if (r1 < 0) return;
        
        // 1. On remplit d'abord la zone avec l'état sélectionné (ex: Susceptible, Infected...)
        // Si on veut juste mettre un masque sur des cellules existantes sans changer leur état,
        // on évite de vider la grille en vérifiant si paintState n'est pas "Empty" par défaut.
        if (!maskMode || paintState != CellState.EMPTY) {
            grid.fillArea(r1, c1, row, col, paintState);
        }
        
        // 2. Si le mode masque est activé, on applique le masque sur toute la zone sélectionnée
        if (maskMode) {
            // Détermination des bornes min/max pour gérer le glisser de souris dans tous les sens
            int startRow = Math.min(r1, row);
            int endRow   = Math.max(r1, row);
            int startCol = Math.min(c1, col);
            int endCol   = Math.max(c1, col);
            
            for (int r = startRow; r <= endRow; r++) {
                for (int c = startCol; c <= endCol; c++) {
                    var cell = grid.getCell(r, c);
                    if (cell != null && cell.isAlive()) {
                        cell.setMasked(true); // Applique le masque dans la zone
                    }
                }
            }
        }
        
        gridView.setDragStartRow(-1);
        updateCellInspector(row, col);
        gridView.redraw();
        
        // Mise à jour du message de statut pour être plus précis
        if (maskMode && paintState != CellState.EMPTY) {
            setStatus("Zone filled with " + paintState + " and masked.");
        } else if (maskMode) {
            setStatus("Masks applied to the zone.");
        } else {
            setStatus("Zone filled with " + paintState);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Simulation control
    // ─────────────────────────────────────────────────────────────────────────

    /** Starts the automatic simulation loop. */
    private void startSimulation() {
        running = true;
        animationTimer.start();
        setStatus("Simulation running…");
    }

    /** Pauses the automatic simulation loop. */
    private void pauseSimulation() {
        running = false;
        animationTimer.stop();
        setStatus("Paused at day " + engine.getStepCount());
    }

    /** Advances one step and refreshes the UI. */
    private void doStep() {
        engine.step();
        gridView.redraw();
        statsPanel.refresh();
        refreshSelectedCellInspector();
        setStatus("Day " + engine.getStepCount());
    }

    /** Resets the simulation to an empty grid. */
    private void resetSimulation() {
        pauseSimulation();
        engine.reset();
        clearCellInspector();
        gridView.redraw();
        statsPanel.refresh();
        setStatus("Reset. Draw cells and press Play.");
    }

    /**
     * Builds the animation timer that drives automatic stepping.
     */
    private void buildAnimationTimer() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastStepTime >= stepIntervalNanos) {
                    lastStepTime = now;
                    doStep();
                }
            }
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Disease / paint state callbacks
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Guard flag: true while a preset is being loaded into the UI controls.
     * Prevents the slider/checkbox listeners from overwriting the preset values
     * before they are fully applied.
     */
    private boolean updatingControls = false;

    /** Reacts to disease preset selection. */
    private void onDiseaseSelected() {
        String sel = diseaseCombo.getValue();
        currentDisease = switch (sel) {
            case "COVID-Like" -> Disease.covidLike();
            default           -> Disease.influenza();
        };
        grid.setDisease(currentDisease);
        // Block applyDiseaseParams() while we sync the controls to the preset
        updatingControls = true;
        transmissionSlider   .setValue(currentDisease.getTransmissionRate());
        mortalitySlider      .setValue(currentDisease.getMortalityRate());
        radiusSlider         .setValue(currentDisease.getTransmissionRadius());
        incubationSlider     .setValue(currentDisease.getIncubationPeriod());
        infectionDurSlider   .setValue(currentDisease.getInfectionDuration());
        immunitySlider       .setValue(currentDisease.getImmunityDuration());
        vaccineEfficacySlider.setValue(currentDisease.getVaccineEfficacy());
        vaccineImmunitySlider.setValue(currentDisease.getVaccineImmunityDuration());
        maskInwardSlider     .setValue(currentDisease.getMaskInwardEfficacy());
        maskOutwardSlider    .setValue(currentDisease.getMaskOutwardEfficacy());
        airborneCheck        .setSelected(currentDisease.isAirborne());
        updatingControls = false;
        setStatus("Disease set to: " + currentDisease);
    }

    /** Applies slider/checkbox values back to the current disease (Custom mode). */
    private void applyDiseaseParams() {
        // Skip if we are in the middle of loading a preset into the controls
        if (updatingControls) return;
        currentDisease.setTransmissionRate(transmissionSlider.getValue());
        currentDisease.setMortalityRate(mortalitySlider.getValue());
        currentDisease.setTransmissionRadius((int) radiusSlider.getValue());
        currentDisease.setIncubationPeriod((int) incubationSlider.getValue());
        currentDisease.setInfectionDuration((int) infectionDurSlider.getValue());
        currentDisease.setImmunityDuration((int) immunitySlider.getValue());
        currentDisease.setVaccineEfficacy(vaccineEfficacySlider.getValue());
        currentDisease.setVaccineImmunityDuration((int) vaccineImmunitySlider.getValue());
        currentDisease.setMaskInwardEfficacy(maskInwardSlider.getValue());
        currentDisease.setMaskOutwardEfficacy(maskOutwardSlider.getValue());
        currentDisease.setAirborne(airborneCheck.isSelected());
        grid.setDisease(currentDisease);
    }

    private void applyMovementProbability(double newProb) {
        if (grid == null) return;

        int width = grid.getWidth();
        int height = grid.getHeight();

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                healthradar.model.Cell cell = grid.getCell(r, c);
                if (cell != null && cell.isAlive()) {
                    cell.setMoveProbability(newProb);
                }
            }
        }
        setStatus("Movement probability updated to "
                + String.format(Locale.ROOT, "%.2f", newProb));
    }

    /** Reacts to paint state combo selection. */
    private void onPaintStateSelected() {
        paintState = switch (paintStateCombo.getValue()) {
            case "Vaccinated" -> CellState.VACCINATED;
            case "Exposed"    -> CellState.EXPOSED;
            case "Infected"   -> CellState.INFECTED;
            case "Recovered"  -> CellState.RECOVERED;
            case "Dead"       -> CellState.DEAD;
            case "Empty"      -> CellState.EMPTY;
            default           -> CellState.SUSCEPTIBLE;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Random populate
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Randomly fills the grid based on the susceptible/infected percentage sliders.
     */
    private void randomPopulate() {
        int total  = grid.getWidth() * grid.getHeight();
        int sCount = (int)(total * susceptibleSlider.getValue() / 100.0);
        int iCount = (int)(total * infectedSlider   .getValue() / 100.0);
        grid.clear();
        grid.randomPopulate(sCount, iCount);
        applyMovementProbability(moveProbSlider.getValue());
        engine = new SimulationEngine(grid);
        statsPanel.setEngine(engine);
        gridView.redraw();
        statsPanel.refresh();
        refreshSelectedCellInspector();
        setStatus("Grid randomly populated: " + sCount + " susceptible, " + iCount + " infected.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Save / Load
    // ─────────────────────────────────────────────────────────────────────────

    private void clearGrid() {
        grid.clear();
        engine = new SimulationEngine(grid);
        statsPanel.setEngine(engine);
        clearCellInspector();
        gridView.redraw();
        statsPanel.refresh();
        setStatus("Grid cleared.");
    }

    private void refreshSetupControls() {
        if (toroidalSetupCheck != null) {
            toroidalSetupCheck.setSelected(grid.isToroidal());
        }
    }

    /** Saves the current simulation to a binary file chosen by the user. */
    private void saveSimulation() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Simulation");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("HealthRadar save (*.hrs)", "*.hrs"));
        File file = fc.showSaveDialog(primaryStage);
        if (file == null) return;
        try {
            SimulationSerializer.save(engine, file.toPath());
            setStatus("Saved to " + file.getName());
        } catch (IOException ex) {
            alert("Save error", ex.getMessage());
        }
    }

    /** Loads a simulation from a binary file chosen by the user. */
    private void loadSimulation() {
        pauseSimulation();
        FileChooser fc = new FileChooser();
        fc.setTitle("Load Simulation");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("HealthRadar save (*.hrs)", "*.hrs"));
        File file = fc.showOpenDialog(primaryStage);
        if (file == null) return;
        try {
            engine = SimulationSerializer.load(file.toPath());
            grid   = engine.getGrid();
            gridView.setGrid(grid);
            refitGridToViewport();
            statsPanel.setEngine(engine);
            clearCellInspector();
            gridView.redraw();
            statsPanel.refresh();
            refreshSetupControls();
            setStatus("Loaded from " + file.getName() + " (day " + engine.getStepCount() + ")");
        } catch (IOException ex) {
            alert("Load error", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a styled action button.
     *
     * @param text  button label
     * @param color CSS hex background colour
     * @return styled Button
     */
    private Button styledButton(String text, String color) {
        Button b = new Button(text);
        b.setMinHeight(28);
        b.setPadding(new Insets(5, 10, 5, 10));
        b.getStyleClass().add("action-button");
        b.setStyle("-fx-background-color:" + color + "; -fx-text-fill:white;"
                + "-fx-font-size:11px; -fx-font-weight:bold;"
                + "-fx-background-radius:5; -fx-cursor:hand;");
        return b;
    }

    /**
     * Creates a white-text label.
     *
     * @param text label text
     * @return styled Label
     */
    private Label whiteLabel(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.WHITE);
        l.setFont(Font.font(11));
        return l;
    }

    private MenuButton buildDelayMenu() {
        delayMenuButton = new MenuButton("Delay: " + formatStepDelay(currentStepDelayMs()));
        delayMenuButton.getStyleClass().add("delay-menu");
        delayMenuButton.setTooltip(new Tooltip("Delay between automatic simulation days. Higher means slower."));

        Label caption = new Label("Auto delay");
        caption.getStyleClass().add("menu-caption");
        stepDelayValueLabel = new Label(formatStepDelay(currentStepDelayMs()));
        stepDelayValueLabel.getStyleClass().add("menu-value");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, caption, spacer, stepDelayValueLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        speedSlider = new Slider(MIN_STEP_DELAY_MS, MAX_STEP_DELAY_MS, currentStepDelayMs());
        speedSlider.setPrefWidth(190);
        speedSlider.setShowTickMarks(false);
        speedSlider.setShowTickLabels(false);
        speedSlider.setMajorTickUnit(1000);
        speedSlider.setBlockIncrement(250);
        speedSlider.valueProperty().addListener((obs, oldValue, newValue) ->
                setStepDelayMs(newValue.intValue()));

        VBox content = new VBox(8, header, speedSlider);
        content.setPadding(new Insets(8, 10, 8, 10));
        CustomMenuItem sliderItem = new CustomMenuItem(content, false);

        delayMenuButton.getItems().addAll(
                sliderItem,
                new SeparatorMenuItem(),
                delayPresetItem(50),
                delayPresetItem(200),
                delayPresetItem(500),
                delayPresetItem(1000),
                delayPresetItem(2000),
                delayPresetItem(5000),
                delayPresetItem(10000)
        );

        return delayMenuButton;
    }

    private MenuItem delayPresetItem(int delayMs) {
        MenuItem item = new MenuItem(formatStepDelay(delayMs));
        item.setOnAction(e -> setStepDelayMs(delayMs));
        return item;
    }

    private void setStepDelayMs(int delayMs) {
        int clamped = Math.max(MIN_STEP_DELAY_MS, Math.min(MAX_STEP_DELAY_MS, delayMs));
        stepIntervalNanos = clamped * 1_000_000L;
        if (stepDelayValueLabel != null) {
            stepDelayValueLabel.setText(formatStepDelay(clamped));
        }
        if (delayMenuButton != null) {
            delayMenuButton.setText("Delay: " + formatStepDelay(clamped));
        }
        if (speedSlider != null && Math.abs(speedSlider.getValue() - clamped) > 0.5) {
            speedSlider.setValue(clamped);
        }
    }

    private int currentStepDelayMs() {
        return (int) Math.max(MIN_STEP_DELAY_MS, Math.min(MAX_STEP_DELAY_MS,
                Math.round(stepIntervalNanos / 1_000_000.0)));
    }

    private String formatStepDelay(int delayMs) {
        if (delayMs < 1000) return delayMs + " ms";
        return String.format(Locale.ROOT, "%.1f s", delayMs / 1000.0);
    }

    /**
     * Creates a section header label for the sidebar.
     *
     * @param text section title
     * @return styled Label
     */
    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.rgb(125, 211, 252));
        l.setFont(Font.font("System Bold", 13));
        return l;
    }

    /**
     * Creates a styled toggle button bound to an {@link EditMode}.
     *
     * @param text  button label
     * @param mode  the EditMode this button represents
     * @param group the ToggleGroup to add this button to
     * @return styled ToggleButton
     */
    private ToggleButton modeToggle(String text, EditMode mode, ToggleGroup group) {
        ToggleButton tb = new ToggleButton(text);
        tb.setToggleGroup(group);
        tb.setMinHeight(28);
        tb.setPadding(new Insets(5, 9, 5, 9));
        tb.getStyleClass().add("tool-toggle");
        tb.selectedProperty().addListener((obs, o, selected) -> {
            if (selected) {
                editMode = mode;
                if (statusLabel != null) {
                    setStatus(modeStatus(mode));
                }
            }
        });
        return tb;
    }

    private String modeStatus(EditMode mode) {
        return switch (mode) {
            case BRUSH -> "Brush mode: drag to paint health states.";
            case ZONE -> "Fill rectangle mode: drag an area, then release.";
            case INDIVIDUAL -> "Single cell mode: click one cell at a time.";
            case ZONETYPE -> "Zone layer mode: paint urban zone risk without changing health state.";
        };
    }

    /**
     * Adds a labelled slider to a VBox and returns the slider.
     *
     * @param label label text
     * @param min   minimum slider value
     * @param max   maximum slider value
     * @param init  initial value
     * @param box   VBox to add the control to
     * @return the created Slider
     */
    private Slider labelledSlider(String label, double min, double max, double init, VBox box) {
        Label lbl = new Label(label);
        lbl.setTextFill(Color.rgb(190, 205, 220));
        lbl.setFont(Font.font(11));
        lbl.setWrapText(true);
        lbl.setMinWidth(0);
        lbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lbl, Priority.ALWAYS);

        Label value = new Label(formatSliderValue(label, init, max));
        value.setTextFill(Color.WHITE);
        value.setFont(Font.font("Monospaced", 11));
        value.setMinWidth(52);
        value.setPrefWidth(52);
        value.setMaxWidth(52);
        value.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(8, lbl, value);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMinWidth(0);

        Slider s = new Slider(min, max, init);
        s.setShowTickMarks(false);
        s.setMinWidth(0);
        s.setMaxWidth(Double.MAX_VALUE);
        s.getStyleClass().add("card-slider");
        s.valueProperty().addListener((obs, oldValue, newValue) ->
                value.setText(formatSliderValue(label, newValue.doubleValue(), max)));

        StackPane sliderFrame = new StackPane(s);
        sliderFrame.getStyleClass().add("slider-frame");
        sliderFrame.setMinWidth(0);
        sliderFrame.setMaxWidth(Double.MAX_VALUE);

        VBox control = new VBox(4, header, sliderFrame);
        control.setMinWidth(0);
        control.setMaxWidth(Double.MAX_VALUE);
        control.setFillWidth(true);
        box.getChildren().add(control);
        return s;
    }

    private String formatSliderValue(String label, double value, double max) {
        if (label.contains("%")) {
            return String.format(Locale.ROOT, "%.0f%%", value);
        }
        if (max <= 1.0) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }

    /** @return a thin horizontal separator styled for dark background */
    private Separator separator() {
        Separator sep = new Separator();
        sep.getStyleClass().add("panel-separator");
        return sep;
    }

    /**
     * Updates the bottom status bar text.
     *
     * @param msg status message
     */
    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }

    /**
     * Exports the simulation statistics history as a PNG chart file.
     * The file is saved in the working directory with a name based on the
     * current step count. A FileChooser lets the user pick a custom location.
     */
    private void exportChart() {
        if (engine == null || engine.getHistory().isEmpty()) {
            alert("No data", "Start and run the simulation first to generate chart data.");
            return;
        }
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export chart as PNG");
        fc.setInitialFileName(ChartExporter.defaultPath(engine).getFileName().toString());
        fc.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PNG image (*.png)", "*.png"));
        java.io.File file = fc.showSaveDialog(primaryStage);
        if (file == null) return;
        try {
            ChartExporter.export(engine, file.toPath());
            setStatus("Chart saved: " + file.getName());
            // Offer to open the file
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Chart exported");
            info.setHeaderText(null);
            info.setContentText("Chart saved to:\n" + file.getAbsolutePath());
            info.showAndWait();
        } catch (java.io.IOException ex) {
            alert("Export error", ex.getMessage());
        }
    }

    /**
     * Opens the Settings modal and applies changes if the user clicks Apply.
     * Pauses the simulation while the dialog is open.
     */
    private void openSettings() {
        pauseSimulation();
        // Convert sidebar % sliders to actual counts for the dialog
        int totalCells = grid.getWidth() * grid.getHeight();
        int curS = (int)(totalCells * susceptibleSlider.getValue() / 100.0);
        int curI = (int)(totalCells * infectedSlider.getValue()    / 100.0);
        ConfigPanel.ConfigResult current = new ConfigPanel.ConfigResult(
                grid.getWidth(), grid.getHeight(), grid.isToroidal(),
                currentDisease,
                curS, curI,
                (int)(stepIntervalNanos / 1_000_000),
                (int) configuredCellSize,
                false);

        ConfigPanel.show(primaryStage, current, result -> {
            currentDisease = result.disease();

            if (result.restart()) {
                // ── Apply & Restart : recrée la grille et repeuple ────────
                grid = new Grid(result.gridWidth(), result.gridHeight(),
                        result.toroidal(), currentDisease, 0);
                grid.randomPopulate(result.susceptibleCount(), result.infectedCount());
                engine = new SimulationEngine(grid);
                gridView.setGrid(grid);
                refitGridToViewport();
                statsPanel.setEngine(engine);
                setStatus("Grid restarted: " + result.gridWidth() + "×"
                        + result.gridHeight() + "  " + currentDisease.getName());
            } else {
                // ── Apply Only : garde la grille actuelle ─────────────────
                if (result.gridWidth()  != grid.getWidth()
                 || result.gridHeight() != grid.getHeight()
                 || result.toroidal()   != grid.isToroidal()) {
                    // Dimensions changées : nouvelle grille vide
                    grid = new Grid(result.gridWidth(), result.gridHeight(),
                            result.toroidal(), currentDisease, 0);
                    engine = new SimulationEngine(grid);
                    gridView.setGrid(grid);
                    refitGridToViewport();
                    statsPanel.setEngine(engine);
                    setStatus("Grid resized to " + result.gridWidth() + "×"
                            + result.gridHeight() + " — use Populate to fill it.");
                } else {
                    // Mêmes dimensions : applique la maladie sur la grille en cours
                    grid.setDisease(currentDisease);
                    setStatus("Settings applied (grid preserved): "
                            + currentDisease.getName());
                }
            }

            // ── Toujours : taille cellule + vitesse + sync sliders ────────
            configuredCellSize = result.cellSize();
            refitGridToViewport();
            if (selectedRow >= grid.getHeight() || selectedCol >= grid.getWidth()) {
                clearCellInspector();
            }
            setStepDelayMs(result.stepDelayMs());

            updatingControls = true;
            transmissionSlider   .setValue(result.disease().getTransmissionRate());
            mortalitySlider      .setValue(result.disease().getMortalityRate());
            radiusSlider         .setValue(result.disease().getTransmissionRadius());
            incubationSlider     .setValue(result.disease().getIncubationPeriod());
            infectionDurSlider   .setValue(result.disease().getInfectionDuration());
            immunitySlider       .setValue(result.disease().getImmunityDuration());
            vaccineEfficacySlider.setValue(result.disease().getVaccineEfficacy());
            vaccineImmunitySlider.setValue(result.disease().getVaccineImmunityDuration());
            maskInwardSlider     .setValue(result.disease().getMaskInwardEfficacy());
            maskOutwardSlider    .setValue(result.disease().getMaskOutwardEfficacy());
            airborneCheck        .setSelected(result.disease().isAirborne());
            updatingControls = false;

            int _total = result.gridWidth() * result.gridHeight();
            susceptibleSlider.setValue(_total == 0 ? 0 : result.susceptibleCount() * 100.0 / _total);
            infectedSlider   .setValue(_total == 0 ? 0 : result.infectedCount()    * 100.0 / _total);

            gridView.redraw();
            statsPanel.refresh();
            refreshSelectedCellInspector();
            refreshSetupControls();
        });
    }

    /**
     * Shows a simple error alert dialog.
     *
     * @param title   dialog title
     * @param message error message
     */
    private void alert(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setContentText(message);
        a.showAndWait();
    }
}
