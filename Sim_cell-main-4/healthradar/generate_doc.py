"""
Generates the HealthRadar user/technical documentation PDF.
"""
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.lib import colors
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
    HRFlowable, PageBreak
)

W, H = A4
TEAL   = colors.HexColor("#1abc9c")
DARK   = colors.HexColor("#16213e")
BLUE   = colors.HexColor("#3498db")
ORANGE = colors.HexColor("#e67e22")
RED    = colors.HexColor("#e74c3c")
GREEN  = colors.HexColor("#2ecc71")
GREY   = colors.HexColor("#7f8c8d")
WHITE  = colors.white

styles = getSampleStyleSheet()

def H1(text):
    s = ParagraphStyle("h1", parent=styles["Normal"],
        fontSize=22, textColor=TEAL, spaceAfter=6, spaceBefore=16,
        fontName="Helvetica-Bold")
    return Paragraph(text, s)

def H2(text):
    s = ParagraphStyle("h2", parent=styles["Normal"],
        fontSize=15, textColor=BLUE, spaceAfter=4, spaceBefore=10,
        fontName="Helvetica-Bold")
    return Paragraph(text, s)

def H3(text):
    s = ParagraphStyle("h3", parent=styles["Normal"],
        fontSize=12, textColor=ORANGE, spaceAfter=3, spaceBefore=6,
        fontName="Helvetica-Bold")
    return Paragraph(text, s)

def P(text):
    s = ParagraphStyle("p", parent=styles["Normal"],
        fontSize=10, leading=15, spaceAfter=4)
    return Paragraph(text, s)

def BULLET(text):
    s = ParagraphStyle("bullet", parent=styles["Normal"],
        fontSize=10, leading=14, leftIndent=16,
        bulletIndent=4, spaceAfter=2)
    return Paragraph(f"• {text}", s)

def CODE(text):
    s = ParagraphStyle("code", parent=styles["Normal"],
        fontSize=9, fontName="Courier", backColor=colors.HexColor("#f0f0f0"),
        leftIndent=12, spaceAfter=4, spaceBefore=4, leading=13)
    return Paragraph(text, s)

def rule():
    return HRFlowable(width="100%", thickness=1, color=TEAL, spaceAfter=6, spaceBefore=6)

def color_table(rows, col_widths):
    t = Table(rows, colWidths=col_widths)
    style = TableStyle([
        ("BACKGROUND",  (0,0), (-1,0), DARK),
        ("TEXTCOLOR",   (0,0), (-1,0), WHITE),
        ("FONTNAME",    (0,0), (-1,0), "Helvetica-Bold"),
        ("FONTSIZE",    (0,0), (-1,-1), 9),
        ("ROWBACKGROUNDS", (0,1), (-1,-1), [colors.white, colors.HexColor("#f5f5f5")]),
        ("GRID",        (0,0), (-1,-1), 0.4, GREY),
        ("ALIGN",       (0,0), (-1,-1), "LEFT"),
        ("VALIGN",      (0,0), (-1,-1), "MIDDLE"),
        ("LEFTPADDING", (0,0), (-1,-1), 6),
        ("TOPPADDING",  (0,0), (-1,-1), 4),
        ("BOTTOMPADDING",(0,0),(-1,-1), 4),
    ])
    t.setStyle(style)
    return t

# ── Content ───────────────────────────────────────────────────────────────────
story = []

# ── Cover ─────────────────────────────────────────────────────────────────────
cover_title = ParagraphStyle("ct", parent=styles["Normal"],
    fontSize=32, textColor=TEAL, fontName="Helvetica-Bold",
    alignment=1, spaceAfter=8)
cover_sub = ParagraphStyle("cs", parent=styles["Normal"],
    fontSize=16, textColor=BLUE, fontName="Helvetica",
    alignment=1, spaceAfter=4)
cover_body = ParagraphStyle("cb", parent=styles["Normal"],
    fontSize=11, textColor=GREY, alignment=1, spaceAfter=4)

story += [
    Spacer(1, 3*cm),
    Paragraph("HealthRadar", cover_title),
    Paragraph("Disease Propagation Simulator", cover_sub),
    Spacer(1, 0.5*cm),
    HRFlowable(width="60%", thickness=2, color=TEAL, hAlign="CENTER"),
    Spacer(1, 0.5*cm),
    Paragraph("P.G.L. Cellules 2D – ING1 GI1 – CY Tech 2025-2026", cover_body),
    Paragraph("Java 21 + JavaFX 11 | MVC Architecture | SEIRD Model", cover_body),
    Spacer(1, 5*cm),
    Paragraph("Authors: ABAL Marc-Antoine, BEN GHORBEL Khayem, TRAN TU THIEN Thémis,", cover_body),
    Paragraph("COLLIN Gweltaz, SAAD Mohamed, GHOMMAM Mahdi", cover_body),
    Spacer(1, 0.3*cm),
    Paragraph("Teachers: Eva ANSERMIN – Romuald GRIGNON", cover_body),
    PageBreak(),
]

# ── 1. Overview ───────────────────────────────────────────────────────────────
story += [
    H1("1. Application Overview"),
    rule(),
    P("HealthRadar is a 2D cellular automaton that simulates the propagation of infectious "
      "diseases across an urban grid. Each cell in the grid represents one person. "
      "The simulation follows an <b>SEIRD</b> epidemiological model: "
      "Susceptible → Exposed → Infected → Recovered → (back to Susceptible), "
      "with a probability of dying at the end of the infectious period."),
    P("The application is built with <b>Java 21</b> and <b>JavaFX 11</b>, structured "
      "following the <b>Model-View-Controller (MVC)</b> pattern, and is compiled and "
      "launched via a <b>Makefile</b>."),
    Spacer(1, 0.3*cm),
]

# ── 2. Quick Start ────────────────────────────────────────────────────────────
story += [
    H1("2. Quick Start"),
    rule(),
    H2("Requirements"),
    BULLET("Java 21 JDK (javac, java, jar, javadoc)"),
    BULLET("OpenJFX 11 installed at <i>/usr/share/openjfx/lib/</i>  (Ubuntu: apt install openjfx)"),
    BULLET("GNU Make"),
    Spacer(1, 0.2*cm),
    H2("Available Make targets"),
    color_table(
        [["Target", "Action"],
         ["make",          "Compile + package HealthRadar.jar (default)"],
         ["make compile",  "Compile .java sources to out/"],
         ["make jar",      "Compile + build HealthRadar.jar"],
         ["make run",      "Compile, package, and launch the application"],
         ["make doc",      "Generate JavaDoc in docs/"],
         ["make clean",    "Delete out/ and HealthRadar.jar"],
         ["make distclean","Delete everything including docs/"]],
        [4*cm, 11*cm]
    ),
    Spacer(1, 0.3*cm),
    H2("First run"),
    CODE("git clone &lt;your-repo&gt; healthradar &amp;&amp; cd healthradar"),
    CODE("make run"),
    P("On the presentation day you must <b>not</b> use an IDE: clone, compile, and run "
      "entirely from the terminal with the Makefile targets above."),
    Spacer(1, 0.3*cm),
]

# ── 3. Disease model ──────────────────────────────────────────────────────────
story += [
    H1("3. Disease Model (SEIRD)"),
    rule(),
    P("Each person on the grid transitions through the following states:"),
    color_table(
        [["State", "Colour", "Description"],
         ["EMPTY",       "Light grey",  "No person occupies this cell."],
         ["SUSCEPTIBLE", "Blue",        "Healthy; can be infected."],
         ["EXPOSED",     "Orange",      "Incubating; not yet contagious (duration: incubationPeriod steps)."],
         ["INFECTED",    "Red",         "Contagious; spreads disease to neighbours."],
         ["RECOVERED",   "Green",       "Immune for immunityDuration steps, then returns to Susceptible."],
         ["DEAD",        "Dark grey",   "Died from the disease; no longer active."]],
        [3*cm, 2.5*cm, 9*cm]
    ),
    Spacer(1, 0.3*cm),
    H2("Transmission modes"),
    H3("Contact mode"),
    P("An INFECTED cell can only transmit to cells in its immediate neighbourhood "
      "(radius = 1, up to 8 neighbours). This models diseases spread by physical contact "
      "or short-range droplets (e.g. Influenza)."),
    H3("Airborne mode"),
    P("An INFECTED cell transmits to all SUSCEPTIBLE cells within a configurable Euclidean "
      "radius (default 3). This models airborne pathogens (e.g. COVID-like)."),
    H2("Probability model"),
    P("The effective infection probability for each susceptible target is:"),
    CODE("P(infection) = disease.transmissionRate × (1 - cell.resistance)"),
    P("Each cell has a personal <i>resistance</i> drawn from a normal distribution "
      "(mean 0.2, σ 0.1, clamped to [0, 0.6]). This models individual immune differences."),
    Spacer(1, 0.3*cm),
]

# ── 4. Preset diseases ────────────────────────────────────────────────────────
story += [
    H1("4. Preset Diseases"),
    rule(),
    color_table(
        [["Parameter",         "Influenza",  "COVID-Like"],
         ["Mode",              "Contact",    "Airborne (r=3)"],
         ["Transmission rate", "30%",        "20%"],
         ["Incubation",        "3 steps",    "5 steps"],
         ["Infection duration","7 steps",    "14 steps"],
         ["Mortality rate",    "1%",         "2%"],
         ["Immunity duration", "30 steps",   "60 steps"]],
        [5*cm, 4*cm, 4*cm]
    ),
    P("A <b>Custom</b> mode lets you override every parameter with the sliders in "
      "the right sidebar."),
    Spacer(1, 0.3*cm),
]

# ── 5. Grid ───────────────────────────────────────────────────────────────────
story += [
    H1("5. The Grid"),
    rule(),
    H2("Dimensions"),
    P("Default: <b>60 columns × 45 rows</b> (configurable in code or via future settings). "
      "Each cell is rendered as a 14×14 pixel square on the canvas."),
    H2("Topology"),
    BULLET("<b>Bounded</b> (default): cells at the border have fewer neighbours; "
           "the simulation 'ends' at the edges."),
    BULLET("<b>Toroidal</b>: the left edge connects to the right, top to bottom. "
           "Toggle with the checkbox in the sidebar."),
    H2("Step mechanics (per simulation tick)"),
    BULLET("<b>Phase 1 – Movement</b>: each alive cell attempts to move to a random "
           "adjacent empty cell with its personal moveProbability (0.1–0.4 per step). "
           "Cells are processed in random order to avoid directional bias."),
    BULLET("<b>Phase 2 – Infection</b>: each INFECTED cell tries to transmit to "
           "SUSCEPTIBLE cells within its range. A double-buffer snapshot ensures "
           "all cells read the previous generation."),
    BULLET("<b>Phase 3 – Progression</b>: state ages are incremented; "
           "EXPOSED cells become INFECTED after incubationPeriod steps; "
           "INFECTED cells either die (with mortalityRate) or recover; "
           "RECOVERED cells become SUSCEPTIBLE again after immunityDuration steps."),
    Spacer(1, 0.3*cm),
]

# ── 6. UI controls ────────────────────────────────────────────────────────────
story += [
    H1("6. User Interface"),
    rule(),
    H2("Top toolbar"),
    color_table(
        [["Control", "Action"],
         ["▶ Play",       "Start the automatic simulation loop."],
         ["⏸ Pause",      "Pause at the current step."],
         ["⏭ Step",       "Advance exactly one step (pauses first)."],
         ["↺ Reset",      "Clear the grid and reset the step counter."],
         ["Speed slider", "Sets steps per second (1–20)."],
         ["Disease",      "Select Influenza, COVID-Like, or Custom preset."],
         ["Mode",         "Switch between Brush / Zone / Individual edit modes."],
         ["Paint",        "Choose the state painted by mouse actions."],
         ["💾 Save",      "Serialise the full simulation to a .hrs binary file."],
         ["📂 Load",      "Restore a previously saved .hrs file."]],
        [4*cm, 11*cm]
    ),
    Spacer(1, 0.3*cm),
    H2("Edit modes"),
    H3("Brush mode"),
    P("Click or drag on the canvas to paint cells one by one with the selected state."),
    H3("Zone mode"),
    P("Click and drag to draw a rectangle; all cells inside are filled with the "
      "selected state when you release the mouse. A yellow outline shows the selection."),
    H3("Individual mode"),
    P("Single click on one cell to change it to the selected paint state."),
    Spacer(1, 0.3*cm),
    H2("Right sidebar"),
    H3("Disease Parameters panel"),
    P("Four sliders let you override the active disease parameters in real time:"),
    BULLET("Transmission rate (0.01–1.0)"),
    BULLET("Mortality rate (0–0.5)"),
    BULLET("Airborne radius (1–8 cells)"),
    BULLET("Airborne checkbox: toggles contact vs. airborne mode"),
    H3("Random Populate"),
    BULLET("Susceptible %: fraction of cells to fill with SUSCEPTIBLE people."),
    BULLET("Infected %: fraction to fill with INFECTED people."),
    BULLET("🌐 Random Populate: clears the grid and fills it with these ratios."),
    BULLET("🗑 Clear Grid: empties all cells."),
    H3("Toroidal topology checkbox"),
    P("When checked, the grid wraps around at all four edges."),
    Spacer(1, 0.3*cm),
]

# ── 7. Statistics ─────────────────────────────────────────────────────────────
story += [
    H1("7. Live Statistics"),
    rule(),
    P("The stats panel (top of the right sidebar) updates after every step and shows:"),
    BULLET("<b>Step counter</b> and total population."),
    BULLET("<b>Per-state counts and percentages</b> (Susceptible, Exposed, Infected, "
           "Recovered, Dead)."),
    BULLET("<b>Stacked percentage bar</b> showing the current population split."),
    BULLET("<b>Time-series line chart</b> with one coloured line per state, covering "
           "up to the last 500 steps."),
    BULLET("<b>Colour legend</b> matching the grid and chart colours."),
    Spacer(1, 0.3*cm),
]

# ── 8. Save / Load ────────────────────────────────────────────────────────────
story += [
    H1("8. Save / Load"),
    rule(),
    P("At any moment you can save the complete simulation state to disk:"),
    BULLET("Press <b>💾 Save</b> and choose a file name (extension <i>.hrs</i>)."),
    BULLET("The entire engine (grid, cell states, disease parameters, step history) "
           "is written as a binary Java-serialised object."),
    BULLET("Press <b>📂 Load</b> to restore any previously saved file and resume "
           "exactly where you left off."),
    Spacer(1, 0.3*cm),
]

# ── 9. Architecture ───────────────────────────────────────────────────────────
story += [
    H1("9. Code Architecture (MVC)"),
    rule(),
    color_table(
        [["Package", "Class", "Role"],
         ["healthradar",            "App",                  "JavaFX entry point; launches MainController."],
         ["healthradar.model",      "CellState",            "Enum: EMPTY, SUSCEPTIBLE, EXPOSED, INFECTED, RECOVERED, DEAD."],
         ["healthradar.model",      "Cell",                 "One person: state, stateAge, resistance, moveProbability."],
         ["healthradar.model",      "Disease",              "All epidemiological parameters; two factory presets."],
         ["healthradar.model",      "Grid",                 "2-D array of Cells; runs the 3-phase step loop."],
         ["healthradar.model",      "SimulationEngine",     "Owns the Grid; drives stepping; records StepStats history."],
         ["healthradar.view",       "GridView",             "JavaFX Canvas; renders cells, hover, zone selection."],
         ["healthradar.view",       "StatsPanel",           "JavaFX VBox; summary text, line chart, legend."],
         ["healthradar.view",       "EditMode",             "Enum: BRUSH, ZONE, INDIVIDUAL."],
         ["healthradar.controller", "MainController",       "Builds the scene; wires all events; drives AnimationTimer."],
         ["healthradar.io",         "SimulationSerializer", "Binary save/load via Java ObjectOutputStream."]],
        [4.5*cm, 4.5*cm, 6*cm]
    ),
    Spacer(1, 0.4*cm),
    P("The double-buffer pattern in <i>Grid.step()</i> deep-copies the cell array before "
      "each tick, so the infection and movement phases always read the <i>previous</i> "
      "generation and write to a fresh copy, avoiding order-dependent artefacts."),
    Spacer(1, 0.3*cm),
]

# ── 10. Project structure ─────────────────────────────────────────────────────
story += [
    H1("10. Project File Structure"),
    rule(),
    CODE("healthradar/"),
    CODE("├── Makefile"),
    CODE("├── MANIFEST.MF"),
    CODE("├── HealthRadar.jar           ← built by 'make jar'"),
    CODE("├── docs/                     ← JavaDoc (built by 'make doc')"),
    CODE("└── src/main/java/healthradar/"),
    CODE("    ├── App.java"),
    CODE("    ├── controller/"),
    CODE("    │   └── MainController.java"),
    CODE("    ├── model/"),
    CODE("    │   ├── Cell.java"),
    CODE("    │   ├── CellState.java"),
    CODE("    │   ├── Disease.java"),
    CODE("    │   ├── Grid.java"),
    CODE("    │   └── SimulationEngine.java"),
    CODE("    ├── view/"),
    CODE("    │   ├── EditMode.java"),
    CODE("    │   ├── GridView.java"),
    CODE("    │   └── StatsPanel.java"),
    CODE("    └── io/"),
    CODE("        └── SimulationSerializer.java"),
    Spacer(1, 0.3*cm),
]

# ── 11. Ethical considerations ────────────────────────────────────────────────
story += [
    H1("11. Ethical Considerations"),
    rule(),
    P("HealthRadar was designed with the following ethical principles:"),
    BULLET("<b>Fairness</b>: the simulation treats all grid cells equally; "
           "no zone is structurally advantaged."),
    BULLET("<b>Transparency</b>: all parameters are visible and adjustable in the UI; "
           "no hidden mechanics."),
    BULLET("<b>Privacy</b>: the application does not collect, transmit, or store any "
           "real personal health data. Simulated data remains local."),
    BULLET("<b>Responsibility</b>: the simulation is a simplified model for educational "
           "purposes only and must not be used to make real public-health decisions."),
    BULLET("<b>Accessibility</b>: the interface is usable with keyboard shortcuts "
           "and the colour palette has been chosen for basic colour-blind readability."),
    Spacer(1, 0.3*cm),
]

# ── Build PDF ─────────────────────────────────────────────────────────────────
doc = SimpleDocTemplate(
    "/home/claude/healthradar/HealthRadar_Documentation.pdf",
    pagesize=A4,
    leftMargin=2*cm, rightMargin=2*cm,
    topMargin=2*cm, bottomMargin=2*cm,
    title="HealthRadar Documentation",
    author="HealthRadar Team – CY Tech ING1 GI1"
)
doc.build(story)
print("PDF generated: HealthRadar_Documentation.pdf")
