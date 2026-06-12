package healthradar;

import healthradar.controller.MainController;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Entry point for the HealthRadar application.
 *
 * <p>JavaFX requires a class that extends {@link Application} to bootstrap the
 * UI toolkit. This class simply delegates scene construction to
 * {@link MainController}.</p>
 *
 * <p>Launch from the command line:</p>
 * <pre>
 *   make run
 * </pre>
 *
 * @author HealthRadar Team
 * @version 1.0
 */
public class App extends Application {

    /**
     * JavaFX lifecycle method – called after the toolkit is initialised.
     *
     * @param stage the primary stage created by the JavaFX runtime
     */
    @Override
    public void start(Stage stage) {
        new MainController().start(stage);
    }

    /**
     * Standard Java entry point. Calls {@link Application#launch} which
     * initialises the JavaFX toolkit and then calls {@link #start(Stage)}.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
