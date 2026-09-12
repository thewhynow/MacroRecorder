package com.thewhynow.macromod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.thewhynow.macromod.MacroModClient;
import com.thewhynow.macromod.macro.Macro;
import com.thewhynow.macromod.macro.MacroStorage;
import com.thewhynow.macromod.play.MacroPlayer;
import com.thewhynow.macromod.record.MacroRecorder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

/** The {@code /macro} client command tree. */
public final class MacroCommands {
	private static final SuggestionProvider<FabricClientCommandSource> SAVED_MACROS =
			(context, builder) -> SharedSuggestionProvider.suggest(MacroStorage.list(), builder);

	private MacroCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
		dispatcher.register(ClientCommands.literal("macro")
				.executes(ctx -> usage(ctx.getSource()))
				.then(ClientCommands.literal("record")
						.executes(ctx -> record(ctx.getSource())))
				.then(ClientCommands.literal("stop")
						.executes(ctx -> stop(ctx.getSource())))
				.then(ClientCommands.literal("save")
						.then(ClientCommands.argument("name", StringArgumentType.word())
								.executes(ctx -> save(ctx.getSource(), name(ctx)))))
				.then(ClientCommands.literal("play")
						.then(ClientCommands.argument("name", StringArgumentType.word())
								.suggests(SAVED_MACROS)
								.executes(ctx -> play(ctx.getSource(), name(ctx), true))
								.then(ClientCommands.literal("--no-look")
										.executes(ctx -> play(ctx.getSource(), name(ctx), false)))))
				.then(ClientCommands.literal("list")
						.executes(ctx -> list(ctx.getSource())))
				.then(ClientCommands.literal("delete")
						.then(ClientCommands.argument("name", StringArgumentType.word())
								.suggests(SAVED_MACROS)
								.executes(ctx -> delete(ctx.getSource(), name(ctx))))));
	}

	private static String name(CommandContext<FabricClientCommandSource> ctx) {
		return StringArgumentType.getString(ctx, "name");
	}

	private static int usage(FabricClientCommandSource source) {
		feedback(source, "MacroMod commands:", ChatFormatting.GOLD);
		feedback(source, "  /macro record - start recording", ChatFormatting.GRAY);
		feedback(source, "  /macro stop - stop recording or playback", ChatFormatting.GRAY);
		feedback(source, "  /macro save <name> - save the last recording", ChatFormatting.GRAY);
		feedback(source, "  /macro play <name> [--no-look] - replay a macro", ChatFormatting.GRAY);
		feedback(source, "  /macro list - list saved macros", ChatFormatting.GRAY);
		feedback(source, "  /macro delete <name> - delete a macro", ChatFormatting.GRAY);
		return 1;
	}

	private static int record(FabricClientCommandSource source) {
		MacroRecorder recorder = MacroModClient.recorder();

		if (recorder.isRecording()) {
			return error(source, "Already recording. Use /macro stop to finish.");
		}

		if (MacroModClient.player().isPlaying()) {
			return error(source, "A macro is playing. Use /macro stop first.");
		}

		if (source.getClient().player == null) {
			return error(source, "You need to be in a world to record.");
		}

		recorder.start();
		feedback(source, "Recording started. Everything you press, look at, type and run is captured.", ChatFormatting.GREEN);
		return 1;
	}

	private static int stop(FabricClientCommandSource source) {
		MacroPlayer player = MacroModClient.player();

		if (player.isPlaying()) {
			String playing = player.currentName();
			player.stop();
			feedback(source, "Stopped playback of '" + playing + "'.", ChatFormatting.YELLOW);
			return 1;
		}

		MacroRecorder recorder = MacroModClient.recorder();

		if (!recorder.isRecording()) {
			return error(source, "Nothing is recording or playing.");
		}

		Macro take = recorder.stop();
		feedback(source, "Recording stopped: %d ticks (%.1fs), %d events, %d messages."
				.formatted(take.lengthTicks(), take.lengthTicks() / 20.0F, take.eventCount(), take.messageCount()),
				ChatFormatting.GREEN);
		feedback(source, "Save it with /macro save <name>.", ChatFormatting.GRAY);
		return 1;
	}

	private static int save(FabricClientCommandSource source, String name) {
		if (!MacroStorage.isValidName(name)) {
			return error(source, "Invalid name '" + name + "'. Use 1-32 letters, digits, '_' or '-'.");
		}

		MacroRecorder recorder = MacroModClient.recorder();

		// Saving while recording implies "stop, then save".
		if (recorder.isRecording()) {
			recorder.stop();
		}

		Macro take = recorder.lastTake();

		if (take == null) {
			return error(source, "Nothing to save. Record something with /macro record first.");
		}

		boolean overwrote = MacroStorage.exists(name);

		try {
			MacroStorage.save(name, take);
		} catch (IOException e) {
			MacroModClient.LOGGER.error("Failed to save macro '{}'", name, e);
			return error(source, "Could not save macro: " + e.getMessage());
		}

		feedback(source, (overwrote ? "Overwrote '" : "Saved '") + name + "' ("
				+ take.lengthTicks() + " ticks, " + take.eventCount() + " events).", ChatFormatting.GREEN);
		return 1;
	}

	private static int play(FabricClientCommandSource source, String name, boolean applyLook) {
		if (!MacroStorage.isValidName(name)) {
			return error(source, "Invalid name '" + name + "'.");
		}

		if (!MacroStorage.exists(name)) {
			return error(source, "No macro named '" + name + "'. See /macro list.");
		}

		if (MacroModClient.recorder().isRecording()) {
			return error(source, "Still recording. Use /macro stop first.");
		}

		MacroPlayer player = MacroModClient.player();

		if (player.isPlaying()) {
			return error(source, "Already playing '" + player.currentName() + "'. Use /macro stop first.");
		}

		if (source.getClient().player == null) {
			return error(source, "You need to be in a world to play a macro.");
		}

		Macro macro;

		try {
			macro = MacroStorage.load(name);
		} catch (IOException e) {
			MacroModClient.LOGGER.error("Failed to load macro '{}'", name, e);
			return error(source, "Could not load macro: " + e.getMessage());
		}

		if (macro.frames().isEmpty()) {
			return error(source, "Macro '" + name + "' is empty.");
		}

		player.start(name, macro, applyLook);
		feedback(source, "Playing '" + name + "' (%d ticks, %.1fs)%s. Keep your hands off the keyboard; /macro stop cancels."
				.formatted(macro.lengthTicks(), macro.lengthTicks() / 20.0F, applyLook ? "" : " without look"),
				ChatFormatting.GREEN);
		return 1;
	}

	private static int list(FabricClientCommandSource source) {
		List<String> names = MacroStorage.list();

		if (names.isEmpty()) {
			feedback(source, "No saved macros yet.", ChatFormatting.GRAY);
			return 1;
		}

		feedback(source, names.size() + " saved macro" + (names.size() == 1 ? "" : "s") + ":", ChatFormatting.GOLD);

		for (String name : names) {
			String detail;

			try {
				Macro macro = MacroStorage.load(name);
				detail = "%d ticks, %.1fs, %d events".formatted(
						macro.lengthTicks(), macro.lengthTicks() / 20.0F, macro.eventCount());
			} catch (IOException e) {
				detail = "unreadable: " + e.getMessage();
			}

			feedback(source, "  " + name + " - " + detail, ChatFormatting.GRAY);
		}

		return 1;
	}

	private static int delete(FabricClientCommandSource source, String name) {
		if (!MacroStorage.isValidName(name)) {
			return error(source, "Invalid name '" + name + "'.");
		}

		if (MacroModClient.player().isPlaying() && name.equals(MacroModClient.player().currentName())) {
			return error(source, "'" + name + "' is playing right now. Use /macro stop first.");
		}

		try {
			if (!MacroStorage.delete(name)) {
				return error(source, "No macro named '" + name + "'.");
			}
		} catch (IOException e) {
			MacroModClient.LOGGER.error("Failed to delete macro '{}'", name, e);
			return error(source, "Could not delete macro: " + e.getMessage());
		}

		feedback(source, "Deleted '" + name + "'.", ChatFormatting.YELLOW);
		return 1;
	}

	private static void feedback(FabricClientCommandSource source, String message, ChatFormatting colour) {
		source.sendFeedback(Component.literal(message).withStyle(colour));
	}

	private static int error(FabricClientCommandSource source, String message) {
		source.sendError(Component.literal(message));
		return 0;
	}
}
