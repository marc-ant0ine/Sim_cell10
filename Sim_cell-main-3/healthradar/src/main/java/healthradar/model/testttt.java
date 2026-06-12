package healthradar.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {

    private Grid grid;
    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        grid = new Grid(10, 10, false, Disease.influenza(), 0);
        grid.randomPopulate(50, 5);
        engine = new SimulationEngine(grid);
    }

    @Test
    void testStepCountIncrement() {
        assertEquals(0, engine.getStepCount());
        engine.step();
        assertEquals(1, engine.getStepCount());
        engine.step();
        assertEquals(2, engine.getStepCount());
    }

    @Test
    void testReset() {
        engine.step();
        engine.step();
        assertTrue(engine.getStepCount() > 0);
        engine.reset();
        assertEquals(0, engine.getStepCount());
        // La grille doit être vide
        for (int r = 0; r < grid.getHeight(); r++) {
            for (int c = 0; c < grid.getWidth(); c++) {
                assertEquals(CellState.EMPTY, grid.getCell(r, c).getState());
            }
        }
        assertTrue(engine.getHistory().isEmpty());
    }

    @Test
    void testLatestStats() {
        SimulationEngine.StepStats stats = engine.latestStats();
        assertNotNull(stats);
        assertEquals(0, stats.step());
        engine.step();
        stats = engine.latestStats();
        assertEquals(1, stats.step());
        int totalLiving = stats.totalLiving();
        int total = totalLiving + stats.dead();
        assertEquals(grid.totalPopulation(), total);
    }

    @Test
    void testHistorySizeLimit() {
        // Par défaut MAX_HISTORY = 500
        for (int i = 0; i < 550; i++) {
            engine.step();
        }
        assertTrue(engine.getHistory().size() <= 500);
    }

    @Test
    void testInjectStat() {
        SimulationEngine.StepStats mockStat = new SimulationEngine.StepStats(100, 10, 5, 2, 1, 3, 0);
        engine.clearHistory();
        engine.injectStat(mockStat);
        assertEquals(1, engine.getHistory().size());
        assertEquals(mockStat, engine.getHistory().get(0));
    }
}