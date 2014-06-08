package net.krazyweb.forge.imagedownloader;


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ForgeProfileProperties {

	private static String cardPicsDir;

	private static final String CACHE_DIR_KEY     = "cacheDir";
	private static final String CARD_PICS_DIR_KEY = "cardPicsDir";

	private ForgeProfileProperties() {
		//prevent initializing static class
	}

	public static void load(Path path) {
		
		Properties props = new Properties();
		File propFile = path.toFile();
		try {
			if (propFile.canRead()) {
				props.load(new FileInputStream(propFile));
			} else {
				System.out.println("Can't read");
			}
		}
		catch (IOException e) {
			System.err.println("error while reading from profile properties file");
		}

		Pair<String, String> defaults = getDefaultDirs();
		String cacheDir = getDir(props, CACHE_DIR_KEY, defaults.getRight());
		cardPicsDir = getDir(props, CARD_PICS_DIR_KEY, cacheDir + "pics" + File.separator + "cards" + File.separator);
		
		//ensure directories exist
		try {
			Files.createDirectories(Paths.get(cardPicsDir));
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static String getCardPicsDir() {
		return cardPicsDir;
	}

	private static String getDir(Properties props, String propertyKey, String defaultVal) {
		String retDir = props.getProperty(propertyKey, defaultVal).trim();
		if (retDir.isEmpty()) {
			// use default if dir is "defined" as an empty string in the properties file
			retDir = defaultVal;
		}

		// canonicalize
		retDir = new File(retDir).getAbsolutePath();

		// ensure path ends in a slash
		if (File.separatorChar == retDir.charAt(retDir.length() - 1)) {
			return retDir;
		}
		return retDir + File.separatorChar;
	}

	// returns a pair <userDir, cacheDir>
	private static Pair<String, String> getDefaultDirs() {

		String osName = System.getProperty("os.name");
		String homeDir = System.getProperty("user.home");

		if (StringUtils.isEmpty(osName) || StringUtils.isEmpty(homeDir)) {
			throw new RuntimeException("cannot determine OS and user home directory");
		}

		String fallbackDataDir = String.format("%s/.forge", homeDir);

		if (StringUtils.containsIgnoreCase(osName, "windows")) {
			// the split between appdata and localappdata on windows is relatively recent.  If
			// localappdata is not defined, use appdata for both.  and if appdata is not defined,
			// fall back to a linux-style dot dir in the home directory
			String appRoot = System.getenv().get("APPDATA");
			if (StringUtils.isEmpty(appRoot)) {
				appRoot = fallbackDataDir;
			}
			String cacheRoot = System.getenv().get("LOCALAPPDATA");
			if (StringUtils.isEmpty(cacheRoot)) {
				cacheRoot = appRoot;
			}
			// the cache dir is Forge/Cache instead of just Forge since appRoot and cacheRoot might be the
			// same directory on windows and we need to distinguish them.
			return Pair.of(appRoot + File.separator + "Forge", cacheRoot + File.separator + "Forge" + File.separator + "Cache");
		}
		else if (StringUtils.containsIgnoreCase(osName, "mac os x")) {
			return Pair.of(String.format("%s/Library/Application Support/Forge", homeDir),
					String.format("%s/Library/Caches/Forge", homeDir));
		}

		// Linux and everything else
		return Pair.of(fallbackDataDir, String.format("%s/.cache/forge", homeDir));
	}

}