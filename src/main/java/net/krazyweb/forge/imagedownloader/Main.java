package net.krazyweb.forge.imagedownloader;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/firstscreen.fxml"));
        primaryStage.setTitle("Forge Card Image Downloader");
        primaryStage.setScene(new Scene(root, 750, 250));
		primaryStage.setResizable(false);
		primaryStage.getIcons().add(new Image("/package/forge.png"));
		primaryStage.setOnCloseRequest(event -> {
			DownloadController.shouldStop = true;
			Platform.exit();
		});
        primaryStage.show();
    }
	
	

    public static void main(String[] args) {
        launch(args);
    }
	
}
