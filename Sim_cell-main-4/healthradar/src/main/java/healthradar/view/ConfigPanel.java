package healthradar.view;

import healthradar.io.DiseaseLibrary;
import healthradar.model.Disease;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Consumer;

/**
 * Modal configuration window for the JavaFX version of HealthRadar.
 *
 * <p>Tabs:</p>
 * <ol>
 *   <li><b>Grid</b>  – width, height, toroidal toggle</li>
 *   <li><b>Disease</b> – preset selector + full parameter sliders + library save/load</li>
 *   <li><b>Population</b> – susceptible %, infected %, random seed option</li>
 *   <li><b>Simulation</b> – step delay, cell size</li>
 * </ol>
 *
 * <p>Call {@link #show(Stage, ConfigResult, Consumer)} to open the window.
 * The {@link Consumer} is called with the resulting {@link ConfigResult} when
 * the user clicks Apply.</p>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public class ConfigPanel {

    // ── Result record ─────────────────────────────────────────────────────────

    /**
     * Immutable snapshot of all configuration values chosen by the user.
     *
     * @param gridWidth       grid column count
     * @param gridHeight      grid row count
     * @param toroidal        true for toroidal topology
     * @param disease         selected or custom disease
     * @param susceptiblePct  percentage of cells to populate as susceptible
     * @param infectedPct     percentage of cells to populate as infected
     * @param stepDelayMs     milliseconds between automatic steps
     * @param cellSize        pixel size of each grid cell
     */
    /**
     * Immutable snapshot of all configuration values chosen by the user.
     *
     * @param gridWidth        grid column count
     * @param gridHeight       grid row count
     * @param toroidal         true for toroidal topology
     * @param disease          selected or custom disease
     * @param susceptibleCount exact number of susceptible cells to place
     * @param infectedCount    exact number of infected cells to place
     * @param stepDelayMs      milliseconds between automatic steps
     * @param cellSize         pixel size of each grid cell
     */
    public record ConfigResult(
            int gridWidth, int gridHeight, boolean toroidal,
            Disease disease,
            int susceptibleCount, int infectedCount,
            int stepDelayMs, int cellSize,
            boolean restart) {}

    /** Private constructor – use {@link #show} instead. */
    private ConfigPanel() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Opens the configuration window as a modal dialog.
     *
     * @param owner    the parent stage
     * @param current  current configuration values pre-filled into the controls
     * @param onApply  callback invoked with the new config when the user clicks Apply
     */
    public static void show(Stage owner, ConfigResult current, Consumer<ConfigResult> onApply) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("HealthRadar – Settings");
        dialog.setResizable(false);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        // Full CSS override so tab labels are always white and readable
        tabs.setStyle(
            "-fx-background-color:#111a33;" +
            "-fx-tab-header-background: #111a33;" +
            "-fx-tab-header-area-background-color: #111a33;"
        );
        // Inline stylesheet: selected tab = bright teal bg + white bold text
        //                    unselected tab = mid-blue bg + light grey text
        tabs.getStylesheets().clear();
        javafx.scene.text.Font.getFamilies(); // force toolkit init
        String tabCss =
            ".tab-pane .tab { " +
            "    -fx-background-color: #1e3a5f; " +
            "    -fx-background-insets: 0; " +
            "    -fx-background-radius: 4 4 0 0; " +
            "    -fx-padding: 6 14 6 14; " +
            "} " +
            ".tab-pane .tab:selected { " +
            "    -fx-background-color: #2e7d9e; " +
            "} " +
            ".tab-pane .tab .tab-label { " +
            "    -fx-text-fill: #c8dff0; " +
            "    -fx-font-size: 12px; " +
            "    -fx-font-weight: normal; " +
            "} " +
            ".tab-pane .tab:selected .tab-label { " +
            "    -fx-text-fill: white; " +
            "    -fx-font-weight: bold; " +
            "} " +
            ".tab-pane .tab-header-area { " +
            "    -fx-background-color: #111a33; " +
            "    -fx-padding: 4 0 0 4; " +
            "} " +
            ".tab-pane .tab-header-background { " +
            "    -fx-background-color: #111a33; " +
            "} ";
        // Write CSS to a temp string data URI workaround via stylesheet content
        try {
            java.io.File tmpCss = java.io.File.createTempFile("healthradar_tabs", ".css");
            tmpCss.deleteOnExit();
            java.nio.file.Files.writeString(tmpCss.toPath(), tabCss);
            tabs.getStylesheets().add(tmpCss.toURI().toString());
        } catch (java.io.IOException ignored) {}

        // ── Shared state ──────────────────────────────────────────────────────
        // Grid tab
        Spinner<Integer> widthSpin  = intSpinner(5, 150, current.gridWidth());
        Spinner<Integer> heightSpin = intSpinner(5, 80,  current.gridHeight());
        CheckBox toroidalCB = styledCheck("Toroidal (wrap edges)", current.toroidal());

        // Disease tab
        final Disease[] selectedDisease = {current.disease()};
        // Disease parameter controls (for live editing)
        Slider txSlider    = pSlider(0.01, 1.0,  current.disease().getTransmissionRate());
        Slider mortSlider  = pSlider(0.0,  0.5,  current.disease().getMortalityRate());
        Slider incubSlider = pSlider(1,    30,   current.disease().getIncubationPeriod());
        Slider durSlider   = pSlider(1,    60,   current.disease().getInfectionDuration());
        Slider immunSlider = pSlider(1,    120,  current.disease().getImmunityDuration());
        Slider radSlider   = pSlider(1,    10,   current.disease().getTransmissionRadius());
        CheckBox airborneCB= styledCheck("Airborne", current.disease().isAirborne());
        CheckBox contagiousExposedCB = styledCheck(
                "Contagious during incubation (pre-symptomatic)",
                current.disease().isContagiousInExposed());
        Slider expFactorSlider = pSlider(0.0, 1.0, current.disease().getExposedTransmissionFactor());
        Label  expFactorLbl    = valueLabel(fmt(current.disease().getExposedTransmissionFactor()));
        TextField nameField= styledField(current.disease().getName());

        // Population tab – use spinners with exact cell counts for clarity
        int totalCells = current.gridWidth() * current.gridHeight();
        // Convert stored counts back to counts (ConfigResult now stores counts directly)
        int initS = current.susceptibleCount();
        int initI = current.infectedCount();
        Spinner<Integer> sSpinner = intSpinner(0, totalCells, initS);
        Spinner<Integer> iSpinner = intSpinner(0, totalCells, initI);
        // Live percentage labels
        Label sPctLbl = valueLabel(pctLabel(initS, totalCells));
        Label iPctLbl = valueLabel(pctLabel(initI, totalCells));

        // Simulation tab
        Slider delaySlider    = pSlider(50, 10000, current.stepDelayMs());
        Slider cellSizeSlider = pSlider(4,  24,   current.cellSize());
        Label  delayLbl       = valueLabel(current.stepDelayMs() + " ms");
        Label  cellLbl        = valueLabel(current.cellSize()    + " px");

        // ── Build tabs ────────────────────────────────────────────────────────
        tabs.getTabs().addAll(
                buildGridTab(widthSpin, heightSpin, toroidalCB),
                buildDiseaseTab(selectedDisease, txSlider, mortSlider, incubSlider,
                        durSlider, immunSlider, radSlider, airborneCB, nameField,
                        contagiousExposedCB, expFactorSlider, expFactorLbl),
                buildPopTab(sSpinner, iSpinner, sPctLbl, iPctLbl, totalCells),
                buildSimTab(delaySlider, cellSizeSlider, delayLbl, cellLbl)
        );

        // ── Bind live labels ──────────────────────────────────────────────────
        expFactorSlider.valueProperty().addListener((o,v,n) -> expFactorLbl.setText(fmt(n.doubleValue())));
        sSpinner.valueProperty().addListener((o,v,n) -> sPctLbl.setText(pctLabel(n, totalCells)));
        iSpinner.valueProperty().addListener((o,v,n) -> iPctLbl.setText(pctLabel(n, totalCells)));
        // Also update max of iSpinner when sSpinner changes (sum must not exceed totalCells)
        sSpinner.valueProperty().addListener((o,oldV,newV) -> {
            int maxI = Math.max(0, totalCells - newV);
            iSpinner.setValueFactory(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(
                    0, maxI, Math.min(iSpinner.getValue(), maxI)));
        });
        delaySlider.valueProperty().addListener((o,v,n) -> delayLbl.setText((int)(double)n + " ms"));
        cellSizeSlider.valueProperty().addListener((o,v,n) -> cellLbl.setText((int)(double)n + " px"));

        // ── Buttons ───────────────────────────────────────────────────────────
        Button applyOnlyBtn    = colorButton("Apply",           "#3498db");
        Button applyRestartBtn = colorButton("Apply & Restart", "#2ecc71");
        Button cancelBtn       = colorButton("Cancel",          "#e74c3c");

        applyOnlyBtn.setTooltip(new Tooltip(
            "Apply disease / speed / cell-size without resetting the grid."));
        applyRestartBtn.setTooltip(new Tooltip(
            "Reset the grid and repopulate with the chosen settings."));

        // Logic shared by both buttons — builds Disease + ConfigResult
        java.util.function.Consumer<Boolean> doApply = (restart) -> {
            Disease d = new Disease(
                    nameField.getText().isEmpty() ? "Custom" : nameField.getText(),
                    airborneCB.isSelected(),
                    txSlider.getValue(),
                    (int) incubSlider.getValue(),
                    (int) durSlider.getValue(),
                    mortSlider.getValue(),
                    (int) immunSlider.getValue(),
                    (int) radSlider.getValue(),
                    contagiousExposedCB.isSelected(),
                    expFactorSlider.getValue());
            selectedDisease[0] = d;
            onApply.accept(new ConfigResult(
                    widthSpin.getValue(), heightSpin.getValue(),
                    toroidalCB.isSelected(), d,
                    sSpinner.getValue(), iSpinner.getValue(),
                    (int) delaySlider.getValue(),
                    (int) cellSizeSlider.getValue(),
                    restart));
            dialog.close();
        };

        applyOnlyBtn   .setOnAction(e -> doApply.accept(false));
        applyRestartBtn.setOnAction(e -> doApply.accept(true));
        cancelBtn      .setOnAction(e -> dialog.close());

        HBox buttons = new HBox(10, applyOnlyBtn, applyRestartBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10));
        buttons.setStyle("-fx-background-color:#111a33;");

        VBox root = new VBox(tabs, buttons);
        root.setStyle("-fx-background-color:#111a33;");

        dialog.setScene(new Scene(root, 540, 520));
        dialog.showAndWait();
    }

    // ── Tab builders ──────────────────────────────────────────────────────────

    private static Tab buildGridTab(Spinner<Integer> w, Spinner<Integer> h, CheckBox tor) {
        VBox box = tabBox();
        box.getChildren().addAll(
                sectionLabel("Grid Dimensions"),
                row("Width  (5 – 150 cells):", w),
                row("Height (5 – 80  cells):", h),
                sep(),
                sectionLabel("Topology"),
                tor,
                note("Toroidal: cells on the border connect to the opposite side."));
        return tab("Grid", box);
    }

    private static Tab buildDiseaseTab(Disease[] sel,
            Slider tx, Slider mort, Slider incub, Slider dur,
            Slider immun, Slider rad, CheckBox airborne, TextField name,
            CheckBox contagiousExposedCB, Slider expFactor, Label expFactorLbl) {

        // Preset buttons
        Button btnFlu = colorButton("Influenza",   "#3498db");
        Button btnCov = colorButton("COVID-Like",  "#e67e22");
        btnFlu.setOnAction(e -> applyPresetFull(Disease.influenza(), tx, mort, incub, dur, immun, rad, airborne, name, contagiousExposedCB, expFactor, sel));
        btnCov.setOnAction(e -> applyPresetFull(Disease.covidLike(), tx, mort, incub, dur, immun, rad, airborne, name, contagiousExposedCB, expFactor, sel));

        // Library load/save
        ComboBox<String> libCombo = new ComboBox<>();
        libCombo.setPromptText("Load saved disease…");
        libCombo.setStyle("-fx-background-color:#2c3e50; -fx-text-fill:white;");
        libCombo.setPrefWidth(200);
        refreshLibraryCombo(libCombo);

        Button loadLib = colorButton("Load", "#8e44ad");
        Button saveLib = colorButton("Save to library", "#27ae60");
        Button delLib  = colorButton("Delete", "#c0392b");

        loadLib.setOnAction(e -> {
            int idx = libCombo.getSelectionModel().getSelectedIndex();
            List<Disease> lib = DiseaseLibrary.load();
            if (idx >= 0 && idx < lib.size())
                applyPresetFull(lib.get(idx), tx, mort, incub, dur, immun, rad, airborne, name, contagiousExposedCB, expFactor, sel);
        });
        saveLib.setOnAction(e -> {
            Disease d = buildDisease(name, airborne, tx, mort, incub, dur, immun, rad, contagiousExposedCB, expFactor);
            DiseaseLibrary.save(d);
            refreshLibraryCombo(libCombo);
            sel[0] = d;
        });
        delLib.setOnAction(e -> {
            int idx = libCombo.getSelectionModel().getSelectedIndex();
            if (idx >= 0) { DiseaseLibrary.delete(idx); refreshLibraryCombo(libCombo); }
        });

        Label txLbl    = valueLabel(fmt(tx.getValue()));
        Label mortLbl  = valueLabel(fmt(mort.getValue()));
        Label incLbl   = valueLabel((int)incub.getValue()+"");
        Label durLbl   = valueLabel((int)dur.getValue()+"");
        Label immLbl   = valueLabel((int)immun.getValue()+"");
        Label radLbl   = valueLabel((int)rad.getValue()+"");

        tx.valueProperty()   .addListener((o,v,n)->txLbl  .setText(fmt(n.doubleValue())));
        mort.valueProperty() .addListener((o,v,n)->mortLbl.setText(fmt(n.doubleValue())));
        incub.valueProperty().addListener((o,v,n)->incLbl .setText((int)n.doubleValue()+""));
        dur.valueProperty()  .addListener((o,v,n)->durLbl .setText((int)n.doubleValue()+""));
        immun.valueProperty().addListener((o,v,n)->immLbl .setText((int)n.doubleValue()+""));
        rad.valueProperty()  .addListener((o,v,n)->radLbl .setText((int)n.doubleValue()+""));

        VBox box = tabBox();
        box.getChildren().addAll(
                sectionLabel("Presets"),
                new HBox(8, btnFlu, btnCov),
                sep(),
                sectionLabel("Parameters"),
                row("Name:", name),
                airborne,
                contagiousExposedCB,
                rowWithVal("Exposed transmission factor (0–1):", expFactor, expFactorLbl),
                rowWithVal("Transmission rate (0.01–1.0):", tx, txLbl),
                rowWithVal("Mortality rate    (0.0–0.5):",  mort, mortLbl),
                rowWithVal("Incubation days   (1-30):",     incub, incLbl),
                rowWithVal("Infectious days   (1-60):",     dur,   durLbl),
                rowWithVal("Immunity days     (1-120):",    immun, immLbl),
                rowWithVal("Airborne radius   (1–10):",     rad,   radLbl),
                sep(),
                sectionLabel("Disease Library"),
                new HBox(6, libCombo, loadLib, delLib),
                saveLib);
        return tab("Disease", box);
    }

    private static Tab buildPopTab(Spinner<Integer> s, Spinner<Integer> i,
                                      Label sl, Label il, int totalCells) {
        VBox box = tabBox();
        box.getChildren().addAll(
                sectionLabel("Initial Population"),
                note("Grid total: " + totalCells + " cells."),
                rowWithSpinner("Susceptible (0–" + totalCells + " cells):", s, sl),
                rowWithSpinner("Infected    (0–" + totalCells + " cells):", i, il),
                note("Percentage shown next to each spinner reflects the fraction of total cells."),
                note("Sum of both cannot exceed " + totalCells + " (enforced automatically)."));
        return tab("Population", box);
    }

    private static Tab buildSimTab(Slider delay, Slider cell, Label dLbl, Label cLbl) {
        VBox box = tabBox();
        box.getChildren().addAll(
                sectionLabel("Simulation Speed"),
                rowWithVal("Auto delay ms (50-10000):", delay, dLbl),
                note("50 ms = very fast. 10000 ms = one simulated day every 10 seconds."),
                sep(),
                sectionLabel("Display"),
                rowWithVal("Cell size px  (4–24):",   cell, cLbl),
                note("Change cell size to zoom the grid in or out."));
        return tab("Simulation", box);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void applyPreset(Disease d, Slider tx, Slider mort, Slider incub,
            Slider dur, Slider immun, Slider rad, CheckBox airborne,
            TextField name, Disease[] sel) {
        tx.setValue(d.getTransmissionRate());
        mort.setValue(d.getMortalityRate());
        incub.setValue(d.getIncubationPeriod());
        dur.setValue(d.getInfectionDuration());
        immun.setValue(d.getImmunityDuration());
        rad.setValue(d.getTransmissionRadius());
        airborne.setSelected(d.isAirborne());
        name.setText(d.getName());
        sel[0] = d;
    }

    /**
     * Syncs all controls (including pre-symptomatic fields) from a preset.
     * Called by preset buttons AND by the library load button.
     */
    private static void applyPresetFull(Disease d,
            Slider tx, Slider mort, Slider incub, Slider dur,
            Slider immun, Slider rad, CheckBox airborne, TextField name,
            CheckBox contagiousExposed, Slider expFactor, Disease[] sel) {
        tx.setValue(d.getTransmissionRate());
        mort.setValue(d.getMortalityRate());
        incub.setValue(d.getIncubationPeriod());
        dur.setValue(d.getInfectionDuration());
        immun.setValue(d.getImmunityDuration());
        rad.setValue(d.getTransmissionRadius());
        airborne.setSelected(d.isAirborne());
        contagiousExposed.setSelected(d.isContagiousInExposed());
        expFactor.setValue(d.getExposedTransmissionFactor());
        name.setText(d.getName());
        sel[0] = d;
    }

    private static Disease buildDisease(TextField name, CheckBox airborne,
            Slider tx, Slider mort, Slider incub, Slider dur, Slider immun, Slider rad,
            CheckBox contagiousExposed, Slider expFactor) {
        return new Disease(
                name.getText().isEmpty() ? "Custom" : name.getText(),
                airborne.isSelected(),
                tx.getValue(), (int)incub.getValue(), (int)dur.getValue(),
                mort.getValue(), (int)immun.getValue(), (int)rad.getValue(),
                contagiousExposed.isSelected(), expFactor.getValue());
    }

    private static void refreshLibraryCombo(ComboBox<String> combo) {
        combo.getItems().clear();
        DiseaseLibrary.load().forEach(d -> combo.getItems().add(d.getName()));
    }

    private static String fmt(double v) { return String.format("%.3f", v); }

    /**
     * Returns a label showing count and percentage, e.g. "120  (33.3%)".
     *
     * @param count      cell count
     * @param totalCells total grid cell count
     * @return formatted label string
     */
    private static String pctLabel(int count, int totalCells) {
        double pct = totalCells == 0 ? 0 : count * 100.0 / totalCells;
        return String.format("%d  (%.1f%%)", count, pct);
    }

    // ── Widget factories ──────────────────────────────────────────────────────

    private static Tab tab(String title, VBox content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#1a2744; -fx-background:#1a2744;");
        Tab t = new Tab(title, sp);
        // Text colour and background are handled by the TabPane stylesheet
        return t;
    }

    private static VBox tabBox() {
        VBox b = new VBox(8);
        b.setPadding(new Insets(14));
        b.setStyle("-fx-background-color:#1a2744;");
        return b;
    }

    private static Label sectionLabel(String t) {
        Label l = new Label(t);
        l.setTextFill(Color.web("#7ec8e3"));
        l.setFont(Font.font("System Bold", 13));
        return l;
    }

    private static Label note(String t) {
        Label l = new Label(t);
        l.setTextFill(Color.LIGHTGRAY);
        l.setFont(Font.font(10));
        l.setWrapText(true);
        return l;
    }

    private static Label valueLabel(String t) {
        Label l = new Label(t);
        l.setTextFill(Color.WHITE);
        l.setMinWidth(60);
        l.setFont(Font.font("Monospaced", 11));
        return l;
    }

    private static HBox row(String labelText, javafx.scene.Node ctrl) {
        Label l = new Label(labelText);
        l.setTextFill(Color.WHITE);
        l.setMinWidth(220);
        HBox h = new HBox(10, l, ctrl);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static HBox rowWithSpinner(String labelText, Spinner<Integer> sp, Label val) {
        Label l = new Label(labelText);
        l.setTextFill(Color.WHITE);
        l.setMinWidth(220);
        sp.setPrefWidth(100);
        HBox h = new HBox(8, l, sp, val);
        h.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return h;
    }

    private static HBox rowWithVal(String labelText, Slider sl, Label val) {
        Label l = new Label(labelText);
        l.setTextFill(Color.WHITE);
        l.setMinWidth(220);
        sl.setPrefWidth(160);
        HBox h = new HBox(8, l, sl, val);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static Separator sep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color:#3a5080;");
        return s;
    }

    private static Slider pSlider(double min, double max, double init) {
        Slider s = new Slider(min, max, init);
        s.setPrefWidth(160);
        s.setStyle("-fx-control-inner-background:#3a5080;");
        return s;
    }

    private static Spinner<Integer> intSpinner(int min, int max, int init) {
        Spinner<Integer> sp = new Spinner<>(min, max, init);
        sp.setEditable(true);
        sp.setPrefWidth(90);
        sp.setStyle("-fx-background-color:#2e5080; -fx-text-fill:white;");
        sp.getEditor().setStyle("-fx-background-color:#2e5080; -fx-text-fill:white;");
        return sp;
    }

    private static CheckBox styledCheck(String label, boolean init) {
        CheckBox cb = new CheckBox(label);
        cb.setSelected(init);
        cb.setTextFill(Color.WHITE);
        return cb;
    }

    private static TextField styledField(String init) {
        TextField tf = new TextField(init);
        tf.setStyle("-fx-background-color:#2e5080; -fx-text-fill:white;");
        tf.setPrefWidth(160);
        return tf;
    }

    private static Button colorButton(String text, String hex) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + hex + "; -fx-text-fill:white;"
                + "-fx-font-size:11px; -fx-background-radius:4;");
        return b;
    }
}
