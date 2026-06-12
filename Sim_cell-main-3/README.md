# HealthRadar

HealthRadar is a Java / JavaFX project for the ING1-GI PGL 2D cells assignment.

The project simulates disease propagation on a configurable 2D grid. Each grid
slot can be empty or contain one simulated person. People move, become exposed,
infected, recovered, vaccinated, or dead according to probabilistic rules.
Urban zone types can change local transmission risk.

## Assignment Scope

The official assignment asks for a graphical Java application that simulates
cells in a 2D plane. The simulation must evolve according to time, the
environment, and user actions.

For HealthRadar, the chosen context is disease propagation in a city:

- the grid is the city map;
- each non-empty cell represents one person on that map;
- disease transmission depends on nearby cells, disease parameters,
  protection, movement, and zone risk;
- the user can edit the grid and observe live statistics.

## Current Features

- JavaFX graphical application.
- Command-line application for testing the model without the GUI.
- Configurable grid dimensions.
- Bounded or toroidal topology.
- Cell states:
  - `EMPTY`
  - `SUSCEPTIBLE`
  - `VACCINATED`
  - `EXPOSED`
  - `INFECTED`
  - `RECOVERED`
  - `DEAD`
- Disease parameters:
  - transmission rate;
  - contact or airborne transmission;
  - airborne radius;
  - incubation period;
  - infection duration;
  - mortality rate;
  - temporary immunity;
  - pre-symptomatic contagion;
  - vaccine and mask efficacy.
- Random population insertion.
- Brush, rectangular zone, and individual edit modes.
- Person movement between neighboring empty cells.
- Infection by contact or proximity.
- Zone types with transmission multipliers:
  - residential;
  - work;
  - commercial;
  - education;
  - healthcare;
  - transport;
  - empty space.
- Simulation controls:
  - play;
  - pause;
  - step;
  - reset;
  - step-delay control from 50 ms to 10 seconds.
- Live statistics and chart display.
- Export of statistics charts to PNG.
- Binary save/load of simulation state in `.hrs` files.

## Project Structure

```text
.
├── pom.xml
├── healthradar/
│   ├── src/main/java/healthradar/
│   │   ├── App.java
│   │   ├── Launcher.java
│   │   ├── TerminalApp.java
│   │   ├── controller/
│   │   ├── io/
│   │   ├── model/
│   │   └── view/
│   ├── Makefile
│   └── generate_doc.py
└── README.md
```

`Launcher` is the recommended JavaFX entry point for IDEs and Maven.

## Requirements

- JDK 17 or later.
- Maven.

The project is configured with Maven so JavaFX dependencies are resolved from
the Maven repository.

## Run With Maven

From the repository root:

```bash
mvn clean compile
```

Run the JavaFX application:

```bash
mvn javafx:run
```

Run the command-line version:

```bash
mvn clean compile
java -cp target/classes healthradar.TerminalApp
```

Package the project:

```bash
mvn clean package
```

## IntelliJ Setup

Open the repository root, not the `healthradar` subfolder:

```text
C:\Users\khayem\IdeaProjects\Sim_cell
```

Then reload Maven and run:

```text
healthradar.Launcher
```

## Notes About the Makefile

The Makefile was written for a Linux OpenJFX installation and assumes:

```text
/usr/share/openjfx/lib
```

On Windows, Maven is the safer way to compile and run the project because it
declares JavaFX as a normal dependency.

## Save Format

New `.hrs` files are binary Java-serialized simulation snapshots, matching the
assignment requirement for binary persistence. The loader also keeps a fallback
for older JSON `.hrs` files created by previous development versions.

## Known Gaps

These points still need cleanup before final delivery:

- README and report must stay aligned with the final implementation.
- Generated Javadoc should be committed before delivery.
- Some statistics requested by the assignment, such as progression trends and
  property statistics, are still basic.
- The team must be able to compile and launch the project from the command
  line during the defense.
