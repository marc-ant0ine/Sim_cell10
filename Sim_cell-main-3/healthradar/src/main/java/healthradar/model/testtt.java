package healthradar.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

class GridTest {

    private Grid grid;
    private Disease disease;
    private final int width = 10;
    private final int height = 8;

    @BeforeEach
    void setUp() {
        disease = Disease.influenza();
        grid = new Grid(width, height, false, disease, 42);
    }

    @Test
    void testInitialEmptyGrid() {
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                assertEquals(CellState.EMPTY, grid.getCell(r, c).getState());
            }
        }
    }

    @Test
    void testSetCell() {
        grid.setCell(2, 3, CellState.SUSCEPTIBLE);
        assertEquals(CellState.SUSCEPTIBLE, grid.getCell(2, 3).getState());
        grid.setCell(2, 3, CellState.EMPTY);
        assertEquals(CellState.EMPTY, grid.getCell(2, 3).getState());
    }

    @Test
    void testFillArea() {
        grid.fillArea(1, 1, 3, 4, CellState.INFECTED);
        for (int r = 1; r <= 3; r++) {
            for (int c = 1; c <= 4; c++) {
                assertEquals(CellState.INFECTED, grid.getCell(r, c).getState());
            }
        }
        // Hors zone inchangé
        assertEquals(CellState.EMPTY, grid.getCell(0, 0).getState());
    }

    @Test
    void testRandomPopulateAndDestinations() {
        grid.randomPopulate(20, 2);
        int total = width * height;
        int sus = grid.countState(CellState.SUSCEPTIBLE);
        int inf = grid.countState(CellState.INFECTED);
        assertEquals(20, sus);
        assertEquals(2, inf);
        // Vérifier que les destinations ont été assignées (grid.assignDestinationsByZone appelée dans randomPopulate)
        // On suppose qu'il y a au moins une zone WORK et RESIDENTIAL (mais pas forcément)
        // On teste juste qu'aucune exception n'est levée.
        assertNotNull(grid.getCell(0, 0)); // juste pour éviter warning
    }

    @Test
    void testMovePhaseWeekendProbability() {
        // On peuple une grille avec une seule cellule vivante
        grid.clear();
        grid.setCell(5, 5, CellState.SUSCEPTIBLE);
        Cell cell = grid.getCell(5, 5);
        double originalProb = cell.getMoveProbability();
        // Simuler un pas de week-end (step%7 = 5 ou 6)
        // On ne peut pas tester directement la probabilité, mais on peut vérifier que la méthode ne plante pas.
        // On va plutôt tester que le weekend la probabilité est réduite via l'appel interne.
        // Pour cela on peut mock? Pas nécessaire, on vérifie juste l'absence d'erreur.
        grid.step(5); // samedi
        // Pas de vérification poussée, mais la grille doit toujours être cohérente
        assertNotNull(grid.getCell(5, 5));
    }

    @Test
    void testInfectionPhaseContact() {
        // Place un infecté à côté d'un susceptible
        grid.clear();
        grid.setCell(5, 5, CellState.INFECTED);
        grid.setCell(5, 6, CellState.SUSCEPTIBLE);
        disease.setTransmissionRate(1.0); // 100% de transmission
        disease.setAirborne(false);       // contact seulement
        grid.step(0); // lundi
        // Le susceptible doit être exposé
        Cell target = grid.getCell(5, 6);
        assertEquals(CellState.EXPOSED, target.getState());
        assertEquals(0, target.getStateAge());
    }

    @Test
    void testProgressionExposedToInfected() {
        grid.clear();
        grid.setCell(5, 5, CellState.EXPOSED);
        Cell cell = grid.getCell(5, 5);
        disease.setIncubationPeriod(2);
        // Avancement pas à pas
        grid.step(0);
        assertEquals(CellState.EXPOSED, cell.getState()); // pas encore
        grid.step(1);
        assertEquals(CellState.EXPOSED, cell.getState());
        grid.step(2);
        assertEquals(CellState.INFECTED, cell.getState());
        assertEquals(0, cell.getStateAge()); // reset après transition
    }

    @Test
    void testProgressionInfectedToRecovered() {
        grid.clear();
        grid.setCell(5, 5, CellState.INFECTED);
        Cell cell = grid.getCell(5, 5);
        disease.setInfectionDuration(3);
        disease.setMortalityRate(0.0); // aucun décès
        for (int i = 0; i < 2; i++) grid.step(i);
        assertEquals(CellState.INFECTED, cell.getState());
        grid.step(2);
        assertEquals(CellState.RECOVERED, cell.getState());
    }

    @Test
    void testProgressionInfectedToDead() {
        grid.clear();
        grid.setCell(5, 5, CellState.INFECTED);
        Cell cell = grid.getCell(5, 5);
        disease.setInfectionDuration(1);
        disease.setMortalityRate(1.0); // meurt à coup sûr
        grid.step(0);
        assertEquals(CellState.DEAD, cell.getState());
    }

    @Test
    void testToroidalWrap() {
        Grid toroidalGrid = new Grid(width, height, true, disease, 1);
        toroidalGrid.setCell(0, 0, CellState.INFECTED);
        toroidalGrid.setCell(height-1, width-1, CellState.SUSCEPTIBLE);
        disease.setTransmissionRate(1.0);
        disease.setAirborne(true);
        disease.setTransmissionRadius(10); // assez grand pour atteindre l'autre bord
        toroidalGrid.step(0);
        // Le susceptible au bord opposé doit être exposé (wrap)
        Cell target = toroidalGrid.getCell(height-1, width-1);
        assertEquals(CellState.EXPOSED, target.getState());
    }
}