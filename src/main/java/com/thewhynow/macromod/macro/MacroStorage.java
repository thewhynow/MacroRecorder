package com.thewhynow.macromod.macro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads and writes macros as one JSON file per macro under {@code config/macromod}. */
public final class MacroStorage {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,32}");
	private static final String EXTENSION = ".json";

	private MacroStorage() {
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve("macromod");
	}

	public static boolean isValidName(String name) {
		return name != null && VALID_NAME.matcher(name).matches();
	}

	public static Path fileOf(String name) {
		return directory().resolve(name + EXTENSION);
	}

	public static boolean exists(String name) {
		return isValidName(name) && Files.isRegularFile(fileOf(name));
	}

	/** Writes {@code macro} out under {@code name}, overwriting any existing macro with that name. */
	public static void save(String name, Macro macro) throws IOException {
		Path file = fileOf(name);
		Files.createDirectories(file.getParent());

		Macro named = new Macro(Macro.CURRENT_VERSION, name, macro.lengthTicks(), macro.look(), macro.frames());

		try (Writer writer = Files.newBufferedWriter(file)) {
			GSON.toJson(named, writer);
		}
	}

	public static Macro load(String name) throws IOException {
		try (Reader reader = Files.newBufferedReader(fileOf(name))) {
			Macro macro = GSON.fromJson(reader, Macro.class);

			if (macro == null || macro.frames() == null) {
				throw new IOException("macro file is empty or malformed");
			}

			if (macro.version() > Macro.CURRENT_VERSION) {
				throw new IOException("macro was saved by a newer version of MacroMod (v" + macro.version() + ")");
			}

			return macro;
		} catch (JsonSyntaxException e) {
			throw new IOException("macro file is not valid JSON: " + e.getMessage(), e);
		}
	}

	/** Names of every saved macro, sorted. Never throws — an unreadable directory yields an empty list. */
	public static List<String> list() {
		Path dir = directory();

		if (!Files.isDirectory(dir)) {
			return List.of();
		}

		try (Stream<Path> files = Files.list(dir)) {
			List<String> names = new ArrayList<>();

			files.filter(Files::isRegularFile)
					.map(path -> path.getFileName().toString())
					.filter(file -> file.endsWith(EXTENSION))
					.map(file -> file.substring(0, file.length() - EXTENSION.length()))
					.filter(MacroStorage::isValidName)
					.forEach(names::add);

			Collections.sort(names);
			return names;
		} catch (IOException e) {
			return List.of();
		}
	}

	/** @return true if a file was actually removed. */
	public static boolean delete(String name) throws IOException {
		return Files.deleteIfExists(fileOf(name));
	}
}
