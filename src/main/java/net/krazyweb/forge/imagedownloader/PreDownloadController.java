package net.krazyweb.forge.imagedownloader;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.stage.Stage;

import java.io.IOException;

public class PreDownloadController {
	
	@FXML
	private CheckBox checkBox;
	
	@FXML
	private CheckBox checkBoxOverwrite;
	
	@FXML
	private void downloadButtonPressed(Event event) throws IOException {
		
		Data.highQuality = checkBox.isSelected();
		Data.overwrite = checkBoxOverwrite.isSelected();

		Node node = (Node) event.getSource();
		Stage stage = (Stage) node.getScene().getWindow();
		Parent root = FXMLLoader.load(getClass().getResource("/fxml/downloadscreen.fxml"));
		Scene scene = new Scene(root);
		stage.setScene(scene);
		
	}
	
}
