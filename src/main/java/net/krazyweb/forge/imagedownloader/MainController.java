package net.krazyweb.forge.imagedownloader;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainController {
	
	private static class BooleanValue {
		private boolean value;
	}
	
	@FXML
	private TextField folderPathField;

	@FXML
	private TextField cardsPathField;
	
	@FXML
	private Button okButton;
	
	@FXML
	private void initialize() {
		folderPathField.textProperty().addListener(event -> textFieldChanged());
		cardsPathField.textProperty().addListener(event -> cardTextFieldChanged());
	}
	
	@FXML
	private void chooseFolderButtonPressed() {
		DirectoryChooser chooser = new DirectoryChooser();
		File file = chooser.showDialog(okButton.getScene().getWindow());
		if (file != null) {
			folderPathField.setText(file.getAbsolutePath());
		}
	}
	
	@FXML
	private void chooseCardsFolderButtonPressed() {
		DirectoryChooser chooser = new DirectoryChooser();
		File file = chooser.showDialog(okButton.getScene().getWindow());
		if (file != null) {
			cardsPathField.setText(file.getAbsolutePath());
		}
	}
	
	@FXML
	private void okButtonPressed(Event event) throws IOException {
		
		Node node = (Node) event.getSource();
		Stage stage = (Stage) node.getScene().getWindow();
		Parent root = FXMLLoader.load(getClass().getResource("/fxml/predownloadscreen.fxml"));
		Scene scene = new Scene(root);
		stage.setWidth(300);
		stage.setHeight(450);
		stage.centerOnScreen();
		stage.setScene(scene);
		
	}
	
	private void textFieldChanged() {
		
		Path path = Paths.get(folderPathField.textProperty().get());

		if (Files.exists(path) && Files.isDirectory(path)) {

			if (path.endsWith("res")) {

				BooleanValue found = new BooleanValue();
				found.value = false;

				try {
					Files.list(path).forEach(file -> {
						if (file.getFileName().equals(Paths.get("editions"))) {
							okButton.setDisable(false);
							Data.editionsFolder = file;
							found.value = true;
						}
					});
				} catch (IOException e) {
					e.printStackTrace();
				}

				if (!found.value) {
					okButton.setDisable(true);
				}

			} else {
				okButton.setDisable(true);
			}

		} else {
			okButton.setDisable(true);
		}
		
	}

	private void cardTextFieldChanged() {

		Path path = Paths.get(cardsPathField.textProperty().get());

		if (Files.exists(path) && Files.isDirectory(path)) {
			Data.cardsFolder = path;
		}

	}
	
}
