package healthradar.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

class CellTest {

    @Test
    void testCellCreationAndState() {
        Random rng = new Random(42);
        ZoneType zone = ZoneType.RESIDENTIAL;
        Cell cell = new Cell(CellState.SUSCEPTIBLE, zone, rng);
        assertEquals(CellState.SUSCEPTIBLE, cell.getState());
        assertTrue(cell.isAlive());
        assertFalse(cell.isEmpty());
        assertEquals(zone, cell.getZoneType());
        assertEquals(0, cell.getStateAge());
        // resistance et moveProbability sont dans les bornes attendues
        assertTrue(cell.getResistance() >= 0 && cell.getResistance() <= 0.6);
        assertTrue(cell.getMoveProbability() >= 0.1 && cell.getMoveProbability() <= 0.4);
    }

    @Test
    void testEmptyCell() {
        Cell empty = new Cell();
        assertEquals(CellState.EMPTY, empty.getState());
        assertFalse(empty.isAlive());
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.getResistance());
        assertEquals(0, empty.getMoveProbability());
    }

    @Test
    void testStateAgeIncrement() {
        Random rng = new Random();
        Cell cell = new Cell(CellState.EXPOSED, ZoneType.WORK, rng);
        cell.incrementStateAge();
        assertEquals(1, cell.getStateAge());
        cell.resetStateAge();
        assertEquals(0, cell.getStateAge());
    }

    @Test
    void testEffectiveInfectionProbability() {
        Random rng = new Random();
        Cell cell = new Cell(CellState.SUSCEPTIBLE, ZoneType.RESIDENTIAL, rng);
        cell.setResistance(0.2);
        double base = 0.5;
        double expected = base * (1 - 0.2);
        assertEquals(expected, cell.effectiveInfectionProbability(base), 1e-9);
    }

    @Test
    void testMasking() {
        Random rng = new Random();
        Cell cell = new Cell(CellState.INFECTED, ZoneType.TRANSPORT, rng);
        assertFalse(cell.isMasked());
        cell.setMasked(true);
        assertTrue(cell.isMasked());
    }

    @Test
    void testDestination() {
        Random rng = new Random();
        Cell cell = new Cell(CellState.SUSCEPTIBLE, ZoneType.WORK, rng);
        assertFalse(cell.hasDestination());
        cell.setDestination(5, 10);
        assertTrue(cell.hasDestination());
        assertEquals(5, cell.getDestRow());
        assertEquals(10, cell.getDestCol());
    }

    @Test
    void testCopy() {
        Random rng = new Random(1);
        Cell original = new Cell(CellState.INFECTED, ZoneType.HEALTHCARE, rng);
        original.setStateAge(3);
        original.setResistance(0.5);
        original.setMoveProbability(0.3);
        original.setMasked(true);
        original.setDestination(7, 8);

        Cell copy = original.copy();
        assertEquals(original.getState(), copy.getState());
        assertEquals(original.getZoneType(), copy.getZoneType());
        assertEquals(original.getStateAge(), copy.getStateAge());
        assertEquals(original.getResistance(), copy.getResistance(), 1e-9);
        assertEquals(original.getMoveProbability(), copy.getMoveProbability(), 1e-9);
        assertEquals(original.isMasked(), copy.isMasked());
        assertEquals(original.getDestRow(), copy.getDestRow());
        assertEquals(original.getDestCol(), copy.getDestCol());
        // Vérifier que c'est une vraie copie (pas de référence partagée)
        copy.setResistance(0.9);
        assertNotEquals(original.getResistance(), copy.getResistance());
    }
}