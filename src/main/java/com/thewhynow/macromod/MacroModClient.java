package com.thewhynow.macromod;

import com.thewhynow.macromod.command.MacroCommands;
import com.thewhynow.macromod.play.MacroPlayer;
import com.thewhynow.macromod.record.MacroRecorder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MacroModClient implements ClientModInitializer {
	public static final String MOD_ID = "macromod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final MacroRecorder RECORDER = new MacroRecorder();
	private static final MacroPlayer PLAYER = new MacroPlayer();

	public static MacroRecorder recorder() {
		return RECORDER;
	}

	public static MacroPlayer player() {
		return PLAYER;
	}

	@Override
	public void onInitializeClient() {
		// Playback writes key state at the head of the tick, before handleKeybinds() reads it;
		// recording samples at the tail, after the player's input tick has run.
		ClientTickEvents.START_CLIENT_TICK.register(PLAYER::tick);
		ClientTickEvents.END_CLIENT_TICK.register(RECORDER::tick);

		ClientSendMessageEvents.CHAT.register(RECORDER::onChatSent);
		ClientSendMessageEvents.COMMAND.register(RECORDER::onCommandSent);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PLAYER.stop();
			RECORDER.abort();
		});

		ClientCommandRegistrationCallback.EVENT.register(MacroCommands::register);

		LOGGER.info("MacroMod ready - /macro record to start");
	}
}
