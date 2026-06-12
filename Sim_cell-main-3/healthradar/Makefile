# =============================================================================
#  HealthRadar – Makefile
#  Targets: all, compile, run, jar, doc, clean
# =============================================================================

# ── Toolchain ─────────────────────────────────────────────────────────────────
JAVAC   := javac
JAVA    := java
JAVADOC := javadoc
JAR     := jar

# ── JavaFX library path (Ubuntu openjfx package) ─────────────────────────────
# Adjust JAVAFX_LIB if your JavaFX jars live elsewhere.
JAVAFX_LIB := /usr/share/openjfx/lib

# JavaFX modules used by this project
JAVAFX_MODULES := javafx.controls,javafx.fxml,javafx.graphics,javafx.base

# Classpath: JavaFX jars from system install
JAVAFX_CP := $(JAVAFX_LIB)/javafx.base.jar:$(JAVAFX_LIB)/javafx.controls.jar:$(JAVAFX_LIB)/javafx.graphics.jar:$(JAVAFX_LIB)/javafx.fxml.jar

# ── Project layout ────────────────────────────────────────────────────────────
SRC_DIR  := src/main/java
OUT_DIR  := out
DOC_DIR  := docs
JAR_FILE := HealthRadar.jar

# All .java source files (recursive)
SOURCES  := $(shell find $(SRC_DIR) -name "*.java")

# ── Default target ────────────────────────────────────────────────────────────
.PHONY: all
all: jar

# ── Compile ───────────────────────────────────────────────────────────────────
.PHONY: compile
compile:
	@echo ">>> Compiling sources..."
	@mkdir -p $(OUT_DIR)
	$(JAVAC) \
		--module-path $(JAVAFX_LIB) \
		--add-modules $(JAVAFX_MODULES) \
		-cp $(JAVAFX_CP) \
		-d $(OUT_DIR) \
		-encoding UTF-8 \
		$(SOURCES)
	@echo ">>> Compilation OK"

# ── Package into executable JAR ───────────────────────────────────────────────
.PHONY: jar
jar: compile
	@echo ">>> Packaging JAR..."
	$(JAR) cfm $(JAR_FILE) MANIFEST.MF -C $(OUT_DIR) .
	@echo ">>> JAR ready: $(JAR_FILE)"

# ── Run ───────────────────────────────────────────────────────────────────────
.PHONY: run
run: jar
	@echo ">>> Launching HealthRadar..."
	$(JAVA) \
		--module-path $(JAVAFX_LIB) \
		--add-modules $(JAVAFX_MODULES) \
		-cp $(JAR_FILE):$(JAVAFX_CP) \
		healthradar.App

# ── JavaDoc ───────────────────────────────────────────────────────────────────
.PHONY: doc
doc:
	@echo ">>> Generating JavaDoc..."
	@mkdir -p $(DOC_DIR)
	$(JAVADOC) \
		--module-path $(JAVAFX_LIB) \
		--add-modules $(JAVAFX_MODULES) \
		-cp $(JAVAFX_CP) \
		-d $(DOC_DIR) \
		-encoding UTF-8 \
		-author \
		-version \
		-windowtitle "HealthRadar Documentation" \
		-doctitle "<h1>HealthRadar – Disease Simulation</h1>" \
		$(SOURCES)
	@echo ">>> JavaDoc generated in $(DOC_DIR)/"

# ── Clean ─────────────────────────────────────────────────────────────────────
.PHONY: clean
clean:
	@echo ">>> Cleaning build artifacts..."
	rm -rf $(OUT_DIR) $(JAR_FILE)
	@echo ">>> Clean OK (docs preserved)"

# ── Deep clean (includes docs) ────────────────────────────────────────────────
.PHONY: distclean
distclean: clean
	rm -rf $(DOC_DIR)
	@echo ">>> Full clean OK"

# ── Help ──────────────────────────────────────────────────────────────────────
.PHONY: help
help:
	@echo "Available targets:"
	@echo "  make          – compile + package JAR (default)"
	@echo "  make compile  – compile .java sources only"
	@echo "  make jar      – compile + build HealthRadar.jar"
	@echo "  make run      – compile, package, and launch the app"
	@echo "  make doc      – generate JavaDoc in docs/"
	@echo "  make clean    – remove out/ and HealthRadar.jar"
	@echo "  make distclean– remove everything including docs/"

# ── Terminal version JAR ──────────────────────────────────────────────────────
TERMINAL_JAR     := HealthRadar-Terminal.jar
TERMINAL_MANIFEST := MANIFEST-Terminal.MF

.PHONY: jar-terminal
jar-terminal: compile
	@echo ">>> Packaging terminal JAR..."
	@printf 'Main-Class: healthradar.TerminalApp\n' > $(TERMINAL_MANIFEST)
	$(JAR) cfm $(TERMINAL_JAR) $(TERMINAL_MANIFEST) -C $(OUT_DIR) .
	@echo ">>> Terminal JAR ready: $(TERMINAL_JAR)"

# ── Run terminal version ──────────────────────────────────────────────────────
.PHONY: run-terminal
run-terminal: jar-terminal
	@echo ">>> Launching HealthRadar (terminal mode)..."
	$(JAVA) -cp $(TERMINAL_JAR) healthradar.TerminalApp
