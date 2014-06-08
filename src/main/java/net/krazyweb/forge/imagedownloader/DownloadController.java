package net.krazyweb.forge.imagedownloader;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DownloadController {
	
	private static class Edition {
		
		private String setID;
		private String setName;
		
		private Map<String, Integer> cards = new HashMap<>();
		
	}
	
	private static class IntegerContainer {
		private int value;
	}

	private DownloaderService service;

	protected static boolean shouldStop = false;
	
	@FXML
	private Label stageLabel;
	
	@FXML
	private Label itemLabel;
	
	@FXML
	private Label countLabel;
	
	@FXML
	private ProgressBar progressBar;
	
	@FXML
	private void stopDownload() {
		shouldStop = true;
	}
	
	@FXML
	private void initialize() {

		progressBar.setProgress(0);
		
		service = new DownloaderService();
		
		service.setOnFailed(value -> {
			try {
				throw service.getException();
			} catch (Throwable throwable) {
				throwable.printStackTrace();
			}
		});
		
		progressBar.progressProperty().bind(service.progressProperty());
		stageLabel.textProperty().bind(service.titleProperty());
		itemLabel.textProperty().bind(service.messageProperty());
		countLabel.textProperty().bind(service.imageCountProperty());
		
		service.start();
		
	}
	
	private class DownloaderService extends Service<Void> {

		private final StringProperty imageCount = new SimpleStringProperty();

		public StringProperty imageCountProperty() {
			return imageCount;
		}
		
		private void updateImageCount(int count, int total) {
			Platform.runLater(() -> imageCount.set(count + " of " + total));
		}
		
		private void updateImageCount(String message) {
			Platform.runLater(() -> imageCount.set(message));
		}

		@Override
		protected Task<Void> createTask() {
			return new Task<Void>() {
				@Override
				protected Void call() throws Exception {
					
					ForgeProfileProperties.load(Data.editionsFolder.getParent().getParent().resolve("forge.profile.properties"));
					Path cardsFolder = Paths.get(ForgeProfileProperties.getCardPicsDir());
					
					if (Data.cardsFolder != null) {
						cardsFolder = Data.cardsFolder;
						Files.createDirectories(cardsFolder.getParent());
					}

					System.out.println(Data.cardsFolder + "\n" + cardsFolder);
					
					updateTitle("Collecting MTGImage Sets");
					updateProgress(0, 1);

					if (shouldStop) {
						updateProgress(1, 1);
						updateTitle("Download Stopped");
						updateMessage("");
						updateImageCount("");
						return null;
					}

					List<String> setsAvailable = getMTGImageSets();

					updateTitle("Parsing Edition Files");
					updateProgress(1, 1);

					List<Edition> editions = new ArrayList<>();
					List<Path> paths = Files.list(Data.editionsFolder).collect(Collectors.toList());

					IntegerContainer integerContainer = new IntegerContainer();

					for (Path file : paths) {
						if (shouldStop) {
							updateProgress(1, 1);
							updateTitle("Download Stopped");
							updateMessage("");
							updateImageCount("");
							return null;
						}
						updateMessage(file.getFileName().toString());
						Edition edition = parseFile(file);
						if (edition != null && setsAvailable.contains(edition.setName)) {
							editions.add(edition);
						}
						updateProgress(integerContainer.value++, paths.size());
					}

					if (shouldStop) {
						updateProgress(1, 1);
						updateTitle("Download Stopped");
						updateMessage("");
						updateImageCount("");
						return null;
					}

					updateTitle("Downloading Images");

					integerContainer.value = 0;

					int numberOfCards = countCards(editions);

					for (Edition edition : editions) {

						if (shouldStop) {
							updateProgress(1, 1);
							updateTitle("Download Stopped");
							updateMessage("");
							updateImageCount("");
							return null;
						}

						for (String cardName : edition.cards.keySet()) {

							if (shouldStop) {
								updateProgress(1, 1);
								updateTitle("Download Stopped");
								updateMessage("");
								updateImageCount("");
								return null;
							}

							int cardCount = edition.cards.get(cardName);

							if (cardCount > 1) {

								for (int i = 1; i <= cardCount; i++) {

									String newCardName = cardName + "" + i;

									updateMessage(newCardName);
									updateImageCount(integerContainer.value, numberOfCards);

									Path outputPath = cardsFolder.resolve(Paths.get(edition.setName + "/" + newCardName + ".full.jpg"));

									if (Data.highQuality) {
										downloadCard(outputPath, "http://mtgimage.com/set/" + edition.setID + "/" + newCardName.replaceAll(" ", "%20") + ".hq.jpg");
									} else {
										downloadCard(outputPath, "http://mtgimage.com/set/" + edition.setID + "/" + newCardName.replaceAll(" ", "%20") + ".jpg");
									}

									updateProgress(integerContainer.value++, numberOfCards);

								}

							} else {

								updateMessage(cardName);
								updateImageCount(integerContainer.value, numberOfCards);

								Path outputPath = cardsFolder.resolve(Paths.get(edition.setName + "/" + cardName + ".full.jpg"));

								downloadCard(outputPath, "http://mtgimage.com/set/" + edition.setID + "/" + cardName.replaceAll(" ", "%20") + ".hq.jpg");
								
								updateProgress((double) (integerContainer.value++) / numberOfCards, 1.0);

							}

						}

					}

					return null;

				}

			};

		}
		
	}
	
	private Edition parseFile(Path file) {
		
		if (Files.isDirectory(file)) {
			return null;
		}
		
		Edition edition = new Edition();

		try (BufferedReader reader = Files.newBufferedReader(file)) {

			String line;
			boolean readingCards = false;

			while ((line = reader.readLine()) != null) {
				if (line.startsWith("Code=")) {
					edition.setID = line.substring("Code=".length());
				} else if (line.startsWith("Code2=")) {
					edition.setName = line.substring("Code2=".length());
				} else if (line.startsWith("[cards]")) {
					readingCards = true;
				} else if (readingCards) {

					String cardName = line.substring(1).trim().replaceAll(":", "").replaceAll(" // ", "").replaceAll("\\?", "").replaceAll("\"", "");

					if (edition.cards.containsKey(cardName)) {
						edition.cards.put(cardName, edition.cards.get(cardName) + 1);
					} else {
						edition.cards.put(cardName, 1);
					}

				}
			}
			
			if (edition.setName == null) {
				edition.setName = edition.setID;
			}
			
			if (edition.setName.equals("ISD")) {
				edition.cards.put("Bane of Hanweir", 1);
				edition.cards.put("Garruk, the Veil-Cursed", 1);
				edition.cards.put("Gatstaf Howler", 1);
				edition.cards.put("Homicidal Brute", 1);
				edition.cards.put("Howlpack Alpha", 1);
				edition.cards.put("Howlpack of Estwald", 1);
				edition.cards.put("Insectile Aberration", 1);
				edition.cards.put("Ironfang", 1);
				edition.cards.put("Krallenhorde Wantons", 1);
				edition.cards.put("Lord of Lineage", 1);
				edition.cards.put("Ludevic's Abomination", 1);
				edition.cards.put("Merciless Predator", 1);
				edition.cards.put("Nightfall Predator", 1);
				edition.cards.put("Rampaging Werewolf", 1);
				edition.cards.put("Stalking Vampire", 1);
				edition.cards.put("Terror of Kruin Pass", 1);
				edition.cards.put("Thraben Militia", 1);
				edition.cards.put("Ulvenwald Primordials", 1);
				edition.cards.put("Unholy Fiend", 1);
				edition.cards.put("Wildblood Pack", 1);
			}
			
			if (edition.setName.equals("DKA")) {
				edition.cards.put("Archdemon of Greed", 1);
				edition.cards.put("Chalice of Death", 1);
				edition.cards.put("Ghastly Haunting", 1);
				edition.cards.put("Hinterland Scourge", 1);
				edition.cards.put("Krallenhorde Killer", 1);
				edition.cards.put("Markov's Servant", 1);
				edition.cards.put("Moonscarred Werewolf", 1);
				edition.cards.put("Ravager of the Fells", 1);
				edition.cards.put("Silverpelt Werewolf", 1);
				edition.cards.put("Tovolar's Magehunter", 1);
				edition.cards.put("Unhallowed Cathar", 1);
				edition.cards.put("Werewolf Ransacker", 1);
				edition.cards.put("Withengar Unbound", 1);
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}

		return edition;
		
	}
	
	private List<String> getMTGImageSets() throws IOException {

		List<String> output = new ArrayList<>();

		Document doc = Jsoup.connect("http://mtgimage.com/set/").get();
		Elements links = doc.select("a");
		
		links.forEach(link -> {
			if (!link.html().equals("../")) {
				output.add(link.html().substring(0, link.html().length() - 1).toUpperCase());
			}
		});
		
		return output;
		
	}
	
	private int countCards(List<Edition> editions) {
		
		int output = 0;
		
		for (Edition edition : editions) {
			output += edition.cards.size();
		}
		
		return output;
		
	}

	private void downloadCard(final Path outputPath, final String urlString) throws IOException {
		
		if (Files.exists(outputPath) && !Data.overwrite) {
			return;
		}
		
		Files.createDirectories(outputPath.getParent());

		URL url = new URL(urlString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		InputStream stream = connection.getInputStream();

		BufferedOutputStream outs = new BufferedOutputStream(new FileOutputStream(outputPath.toFile()));
		int len;
		byte[] buf = new byte[1024];
		while ((len = stream.read(buf)) > 0) {
			outs.write(buf, 0, len);
		}
		outs.close();

		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}
	
}