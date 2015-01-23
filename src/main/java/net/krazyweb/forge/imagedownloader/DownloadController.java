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
		private String alias;

		private List<Card> cards = new ArrayList<>();

	}

	private static class Card {
		private String cardName;
		private String url;
		private String setName;
		private int size = 1;
	}

	private static class IntegerContainer {
		private int value;
	}

	private static class LongContainer {
		private long value;
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
	private Label speedLabel;

	@FXML
	private Label timeLabel;

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

		service.setOnSucceeded(event -> {

			progressBar.progressProperty().unbind();
			stageLabel.textProperty().unbind();
			itemLabel.textProperty().unbind();
			countLabel.textProperty().unbind();
			speedLabel.textProperty().unbind();
			timeLabel.textProperty().unbind();

			progressBar.setProgress(1.0);
			stageLabel.setText("Download Complete");
			itemLabel.setText("");
			countLabel.setText("");
			speedLabel.setText("");
			timeLabel.setText("");

		});

		progressBar.progressProperty().bind(service.progressProperty());
		stageLabel.textProperty().bind(service.titleProperty());
		itemLabel.textProperty().bind(service.messageProperty());
		countLabel.textProperty().bind(service.imageCountProperty());
		speedLabel.textProperty().bind(service.speedProperty());
		timeLabel.textProperty().bind(service.timeProperty());


		service.start();

	}

	private class DownloaderService extends Service<Void> {

		private final StringProperty imageCount = new SimpleStringProperty();
		private final StringProperty speed = new SimpleStringProperty();
		private final StringProperty time = new SimpleStringProperty();

		public StringProperty imageCountProperty() {
			return imageCount;
		}

		public StringProperty speedProperty() {
			return speed;
		}

		public StringProperty timeProperty() {
			return time;
		}

		private void updateImageCount(int count, int total) {
			Platform.runLater(() -> imageCount.set(count + " of " + total));
		}

		private void updateImageCount(String message) {
			Platform.runLater(() -> imageCount.set(message));
		}

		private void updateSpeed(String message) {
			Platform.runLater(() -> speed.set(message));
		}

		private void updateTime(String message) {
			Platform.runLater(() -> time.set(message));
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

					updateTitle("Parsing Edition Files");
					updateProgress(0, 1);

					List<Edition> editions = new ArrayList<>();
					List<Path> paths = Files.list(Data.editionsFolder).collect(Collectors.toList());

					IntegerContainer currentProgress = new IntegerContainer();
					final IntegerContainer editionFileCount = new IntegerContainer();
					editionFileCount.value = 1;

					paths.parallelStream().forEach(file -> {

						if (!file.getFileName().toString().endsWith(".txt")) {
							editionFileCount.value++;
							return;
						}

						if (shouldStop) {
							updateProgress(1, 1);
							updateTitle("Download Stopped");
							updateMessage("");
							updateImageCount("");
							return;
						}

						String fileName = file.getFileName().toString();
						fileName = fileName.substring(0, fileName.lastIndexOf(".txt"));
						updateMessage(fileName);
						updateImageCount(editionFileCount.value++ + " of " + paths.size());

						Edition edition = parseFile(file);
						if (edition != null) {
							editions.add(edition);
						}

						updateProgress(currentProgress.value++, paths.size());

					});

					if (shouldStop) {
						updateProgress(1, 1);
						updateTitle("Download Stopped");
						updateMessage("");
						updateImageCount("");
						return null;
					}

					List<Card> cards = new ArrayList<>();
					editions.forEach(edition -> cards.addAll(edition.cards));

					updateTitle("Downloading Images");

					currentProgress.value = 0;

					int numberOfCards = cards.size();

					final IntegerContainer downloadedThisSecond = new IntegerContainer();
					downloadedThisSecond.value = 0;
					final LongContainer timeUpdated = new LongContainer();
					timeUpdated.value = System.currentTimeMillis();

					final Path finalCardsFolder = cardsFolder;

					cards.parallelStream().forEach(card -> {

						if (shouldStop) {
							updateProgress(1, 1);
							updateTitle("Download Stopped");
							updateMessage("");
							updateImageCount("");
							updateSpeed("");
							updateTime("");
							return;
						}

						updateMessage(card.cardName);
						updateImageCount(currentProgress.value, numberOfCards);

						Path outputPath = finalCardsFolder.resolve(Paths.get(card.setName + "/" + card.cardName + ".full.jpg"));

						try {
							downloadCard(outputPath, card.url, card.size);
						} catch (IOException e) {
							e.printStackTrace();
						}

						downloadedThisSecond.value += card.size;

						if (System.currentTimeMillis() - timeUpdated.value >= 1000) {

							synchronized (this) {
								timeUpdated.value = System.currentTimeMillis();

								int speed = (int) ((double) downloadedThisSecond.value / 1024);
								updateSpeed(speed + " KB/s");

								downloadedThisSecond.value = 0;
							}

						}

						updateProgress(currentProgress.value++, numberOfCards);

					});

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

		Map<String, Integer> cards = new HashMap<>();

		try (BufferedReader reader = Files.newBufferedReader(file)) {

			String line;
			boolean readingCards = false;

			while ((line = reader.readLine()) != null) {
				if (line.startsWith("Code=")) {
					edition.setID = line.substring("Code=".length());
				} else if (line.startsWith("Code2=")) {
					edition.setName = line.substring("Code2=".length());
				} else if (line.startsWith("Alias=")) {
					edition.alias = line.substring("Alias=".length());
				} else if (line.startsWith("[cards]")) {
					readingCards = true;
				} else if (readingCards) {

					String cardName = line.substring(1).trim().replaceAll(":", "").replaceAll(" // ", "").replaceAll("\\?", "").replaceAll("\"", "");

					if (cards.containsKey(cardName)) {
						cards.put(cardName, cards.get(cardName) + 1);
					} else {
						cards.put(cardName, 1);
					}

				}
			}

			if (edition.setName == null) {
				edition.setName = edition.setID;
			}

			if (edition.setName.equals("ISD")) {
				cards.put("Bane of Hanweir", 1);
				cards.put("Garruk, the Veil-Cursed", 1);
				cards.put("Gatstaf Howler", 1);
				cards.put("Homicidal Brute", 1);
				cards.put("Howlpack Alpha", 1);
				cards.put("Howlpack of Estwald", 1);
				cards.put("Insectile Aberration", 1);
				cards.put("Ironfang", 1);
				cards.put("Krallenhorde Wantons", 1);
				cards.put("Lord of Lineage", 1);
				cards.put("Ludevic's Abomination", 1);
				cards.put("Merciless Predator", 1);
				cards.put("Nightfall Predator", 1);
				cards.put("Rampaging Werewolf", 1);
				cards.put("Stalking Vampire", 1);
				cards.put("Terror of Kruin Pass", 1);
				cards.put("Thraben Militia", 1);
				cards.put("Ulvenwald Primordials", 1);
				cards.put("Unholy Fiend", 1);
				cards.put("Wildblood Pack", 1);
			}

			if (edition.setName.equals("DKA")) {
				cards.put("Archdemon of Greed", 1);
				cards.put("Chalice of Death", 1);
				cards.put("Ghastly Haunting", 1);
				cards.put("Hinterland Scourge", 1);
				cards.put("Krallenhorde Killer", 1);
				cards.put("Markov's Servant", 1);
				cards.put("Moonscarred Werewolf", 1);
				cards.put("Ravager of the Fells", 1);
				cards.put("Silverpelt Werewolf", 1);
				cards.put("Tovolar's Magehunter", 1);
				cards.put("Unhallowed Cathar", 1);
				cards.put("Werewolf Ransacker", 1);
				cards.put("Withengar Unbound", 1);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		Document pageSetName = null;
		Document pageSetID = null;
		Document pageSetAlias = null;

		try {
			pageSetName = Jsoup.connect("http://mtgimage.com/set/" + edition.setName).get();
		} catch (Exception e) {
			//e.printStackTrace();
		}

		try {
			pageSetID = Jsoup.connect("http://mtgimage.com/set/" + edition.setID).get();
		} catch (Exception e) {
			//e.printStackTrace();
		}

		try {
			pageSetAlias = Jsoup.connect("http://mtgimage.com/set/" + edition.alias).get();
		} catch (Exception e) {
			//e.printStackTrace();
		}

		for (String cardName : cards.keySet()) {

			int cardCount = cards.get(cardName);

			if (cardCount > 1) {

				for (int i = 1; i <= cardCount; i++) {

					String newCardName = cardName + "" + i;

					Card cardInfo = getCardInfo(newCardName, edition, pageSetName, pageSetID, pageSetAlias);
					if (cardInfo != null) {
						edition.cards.add(cardInfo);
					}

				}

			} else {

				Card cardInfo = getCardInfo(cardName, edition, pageSetName, pageSetID, pageSetAlias);
				if (cardInfo != null) {
					edition.cards.add(cardInfo);
				}

			}

		}

		return edition;

	}

	private Card getCardInfo(String cardName, Edition edition, Document pageSetName, Document pageSetID, Document pageSetAlias) {

		if (pageSetName == null && pageSetID == null && pageSetAlias == null) {
			System.out.println("Set not found, skipping: " + edition.setName);
			return null;
		}

		String url;
		String cardFileName;

		boolean pageName = false;
		boolean pageID = false;
		boolean pageAlias = false;

		if (Data.highQuality) {
			cardFileName = cardName + ".hq.jpg";
		} else {
			cardFileName = cardName + ".jpg";
		}

		if (pageSetName != null) {
			Elements e = pageSetName.select("a:contains(" + cardFileName + ")");
			if (!e.isEmpty()) {
				pageName = true;
			}
		}

		if (pageSetID != null) {
			Elements e = pageSetID.select("a:contains(" + cardFileName + ")");
			if (!e.isEmpty()) {
				pageID = true;
			}
		}

		if (pageSetAlias != null) {
			Elements e = pageSetAlias.select("a:contains(" + cardFileName + ")");
			if (!e.isEmpty()) {
				pageAlias = true;
			}
		}

		String setID;
		Document pageForSize;

		if (pageName) {
			setID = edition.setName;
			pageForSize = pageSetName;
		} else if (pageID) {
			setID = edition.setID;
			pageForSize = pageSetID;
		} else if (pageAlias) {
			setID = edition.alias;
			pageForSize = pageSetAlias;
		} else {
			return null;
		}

		if (Data.highQuality) {
			url = "http://mtgimage.com/set/" + setID + "/" + cardName.replaceAll(" ", "%20") + ".hq.jpg";
		} else {
			url = "http://mtgimage.com/set/" + setID + "/" + cardName.replaceAll(" ", "%20") + ".jpg";
		}

		int size;

		if (Data.highQuality) {
			size = getFileSize(cardFileName, pageForSize);
		} else {
			size = getFileSize(cardFileName, pageForSize);
		}

		Card card = new Card();
		card.cardName = cardName;
		card.url = url;
		card.size = size;
		card.setName = edition.setName;

		return card;

	}

	private int getFileSize(String cardFileName, Document page) {

		Elements e = page.select("a:contains(" + cardFileName + ")");

		if (e.size() == 0) {
			return -1;
		}

		String sizeString = e.get(0).nextSibling().toString();
		sizeString = sizeString.substring(sizeString.lastIndexOf(' ') + 1).trim();

		return Integer.parseInt(sizeString);

	}

	private void downloadCard(Path outputPath, String urlString, int expectedSize) throws IOException {

		if (Files.exists(outputPath) && (!Data.overwrite || outputPath.toFile().length() == expectedSize)) {
			return;
		}

		Files.createDirectories(outputPath.getParent());

		URL url = new URL(urlString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		InputStream stream;

		try {
			stream = connection.getInputStream();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		BufferedOutputStream outs = new BufferedOutputStream(new FileOutputStream(outputPath.toFile()));
		int len;
		byte[] buf = new byte[1024];
		while ((len = stream.read(buf)) > 0) {
			outs.write(buf, 0, len);
		}
		outs.close();

	}

}