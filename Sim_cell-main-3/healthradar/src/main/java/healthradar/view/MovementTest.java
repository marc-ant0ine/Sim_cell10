package healthradar;

import healthradar.model.*;
import java.util.Scanner;

/**
 * Test pour observer les mouvements guidés entre zones RESIDENTIAL et WORK,
 * ainsi que l'effet du week-end sur la mobilité.
 * 
 * Lancez le test, appuyez sur Entrée à chaque étape.
 */
public class MovementTest {

    public static void main(String[] args) {
        // Création d'une petite grille (10x10) pour faciliter l'observation
        Disease disease = Disease.influenza(); // paramètres quelconques
        Grid grid = new Grid(10, 10, false, disease, 42); // toroidal = false

        // Nettoyer la grille (par défaut elle est vide)
        grid.clear();

        // Définir manuellement quelques zones RESIDENTIAL et WORK
        // On place des personnes (SUSCEPTIBLE) pour qu'elles soient vivantes
        // et on leur affecte une zone.

        // Deux cellules RESIDENTIAL
        grid.setCell(2, 2, CellState.SUSCEPTIBLE);
        grid.getCell(2, 2).setZoneType(ZoneType.RESIDENTIAL);
        
        grid.setCell(2, 3, CellState.SUSCEPTIBLE);
        grid.getCell(2, 3).setZoneType(ZoneType.RESIDENTIAL);

        // Deux cellules WORK
        grid.setCell(7, 7, CellState.SUSCEPTIBLE);
        grid.getCell(7, 7).setZoneType(ZoneType.WORK);
        
        grid.setCell(7, 8, CellState.SUSCEPTIBLE);
        grid.getCell(7, 8).setZoneType(ZoneType.WORK);

        // Assigner les destinations (chaque RESIDENTIAL pointe vers un WORK aléatoire, et inversement)
        grid.assignDestinationsByZone();

        System.out.println("=== État initial (step 0, lundi) ===");
        printGrid(grid);
        printDestinations(grid);
        System.out.println("\nAppuyez sur ENTER pour commencer la simulation pas à pas...");
        new Scanner(System.in).nextLine();

        // Créer le moteur de simulation
        SimulationEngine engine = new SimulationEngine(grid);

        // Exécuter 14 pas (2 semaines) pour voir plusieurs week-ends
        for (int step = 1; step <= 14; step++) {
            System.out.println("\n--- Step " + step + " (" + dayOfWeek(step) + ") ---");
            engine.step();   // effectue un pas
            printGrid(grid);
            printDestinations(grid);
            
            // Petite pause interactive pour observer
            System.out.print("Appuyez sur ENTER pour continuer...");
            new Scanner(System.in).nextLine();
        }

        System.out.println("\nFin du test.");
    }

    /**
     * Affiche la grille avec les zones et les états.
     * Légende :
     *   R : zone RESIDENTIAL (fond bleu clair dans l'UI, ici lettre)
     *   W : zone WORK
     *   . : vide
     *   Les états de santé ne sont pas affichés pour simplifier, seule la zone compte.
     */
    private static void printGrid(Grid grid) {
        int h = grid.getHeight();
        int w = grid.getWidth();
        System.out.print("  ");
        for (int c = 0; c < w; c++) System.out.print(c % 10);
        System.out.println();
        for (int r = 0; r < h; r++) {
            System.out.print(r % 10 + " ");
            for (int c = 0; c < w; c++) {
                Cell cell = grid.getCell(r, c);
                if (cell.getState() == CellState.EMPTY) {
                    System.out.print(".");
                } else {
                    char zoneChar = switch (cell.getZoneType()) {
                        case RESIDENTIAL -> 'R';
                        case WORK -> 'W';
                        default -> '?';
                    };
                    System.out.print(zoneChar);
                }
            }
            System.out.println();
        }
    }

    /**
     * Affiche les destinations de chaque personne vivante.
     */
    private static void printDestinations(Grid grid) {
        System.out.println("Destinations actives :");
        boolean found = false;
        for (int r = 0; r < grid.getHeight(); r++) {
            for (int c = 0; c < grid.getWidth(); c++) {
                Cell cell = grid.getCell(r, c);
                if (cell.isAlive() && cell.hasDestination()) {
                    found = true;
                    System.out.printf("  (%d,%d) zone %s → destination (%d,%d)%n",
                            r, c, cell.getZoneType(), cell.getDestRow(), cell.getDestCol());
                }
            }
        }
        if (!found) System.out.println("  (aucune destination active)");
    }

    /**
     * Retourne le nom du jour pour un step donné (step 1 = lundi).
     */
    private static String dayOfWeek(int step) {
        // step 1 → lundi (step % 7 : 1=lundi, 2=mardi, 3=mercredi, 4=jeudi, 5=vendredi, 6=samedi, 0=dimanche)
        int d = step % 7;
        return switch (d) {
            case 1 -> "Lundi";
            case 2 -> "Mardi";
            case 3 -> "Mercredi";
            case 4 -> "Jeudi";
            case 5 -> "Vendredi";
            case 6 -> "Samedi";
            default -> "Dimanche";
        };
    }
}