package it.reveal.relext.annotation;

import java.io.IOException;
import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Simple application for annotating the texts.
 * @author Andrew
 *
 */
public class AppGui extends Application {

    @Override
    public void start(Stage stage) {
        System.out.println("Start");
        Parent root = null;
        try {
            final URL file = this.getClass()
                .getClassLoader()
                .getResource("layout.fxml");
            root = FXMLLoader.load(file);
            Scene scene = new Scene(root, 800, 800);
            stage.setTitle("Relation Extractor | Annotation");
            stage.getIcons()
                .add(new Image(AppGui.class.getClassLoader()
                    .getResourceAsStream("reveal-logo.png")));
            scene.getStylesheets()
                .add("styles.css");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            System.err.println(e);
        }

    }

    public static void main(String[] args) {
        launch();
    }

}
