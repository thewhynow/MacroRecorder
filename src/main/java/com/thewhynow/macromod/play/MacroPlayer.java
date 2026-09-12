package com.thewhynow.macromod.play;

import com.mojang.blaze3d.platform.InputConstants;
import com.thewhynow.macromod.macro.Macro;
import com.thewhynow.macromod.macro.TrackedKeys;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replays a macro by driving the vanilla key mappings. Runs on {@code ClientTickEvents.START_CLIENT_TICK},
 * which fires at the head of {@code Minecraft.tick()} — before {@code handleKeybinds()} and before the
 * player's input tick — so state written here is picked up in the same tick.
 *
 * <p>Frames are sparse deltas, but the held state is re-asserted every tick: vanilla (and the player's
 * own keyboard) can clear key state at any time, and a missed re-assert on a sparse macro would leave
 * the player walking, or stuck still, for the rest of the replay.
 */
public final class MacroPlayer {
	private Macro macro;
	private String name;
	private boolean applyLook;
	private int tick;
	private int frameIndex;
	private boolean lookInitialised;
	private Macro.MacroInput heldInput = Macro.MacroInput.NONE;
	private final Set<String> heldActions = new HashSet<>();

	public boolean isPlaying() {
		return macro != null;
	}

	public String currentName() {
		return name;
	}

	public void start(String name, Macro macro, boolean applyLook) {
		this.macro = macro;
		this.name = name;
		this.applyLook = applyLook;
		this.tick = 0;
		this.frameIndex = 0;
		this.lookInitialised = false;
		this.heldInput = Macro.MacroInput.NONE;
		this.heldActions.clear();
	}

	/** Stops playback and releases every key playback may have forced down. */
	public void stop() {
		macro = null;
		name = null;
		tick = 0;
		frameIndex = 0;
		heldInput = Macro.MacroInput.NONE;
		heldActions.clear();

		Minecraft client = Minecraft.getInstance();

		if (client.options != null) {
			releaseAll(client.options);
		}
	}

	public void tick(Minecraft client) {
		if (macro == null) {
			return;
		}

		LocalPlayer player = client.player;

		if (player == null || client.getConnection() == null) {
			stop();
			return;
		}

		List<Macro.Frame> frames = macro.frames();

		while (frameIndex < frames.size() && frames.get(frameIndex).tick() <= tick) {
			apply(client, player, frames.get(frameIndex));
			frameIndex++;
		}

		assertHeldState(client.options);
		tick++;

		if (frameIndex >= frames.size() && tick > macro.lengthTicks()) {
			stop();
		}
	}

	private void apply(Minecraft client, LocalPlayer player, Macro.Frame frame) {
		Options options = client.options;

		if (frame.keys() != null) {
			heldInput = frame.keys();
		}

		if (frame.pressed() != null) {
			for (String keyName : frame.pressed()) {
				heldActions.add(keyName);
				KeyMapping mapping = TrackedKeys.byName(options, keyName);

				// Vanilla starts an action on consumeClick() and continues it while isDown(), so a press
				// needs the click counter bumped as well as the held state, which assertHeldState() sets.
				if (mapping != null && !mapping.isUnbound()) {
					InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(mapping);

					if (bound != null) {
						KeyMapping.click(bound);
					}
				}
			}
		}

		if (frame.released() != null) {
			heldActions.removeAll(frame.released());
		}

		if (applyLook && frame.yaw() != null && frame.pitch() != null) {
			float yaw = frame.yaw();
			float pitch = frame.pitch();

			player.setYRot(yaw);
			player.setXRot(pitch);
			player.setYHeadRot(yaw);

			if (!lookInitialised) {
				// Avoid a one-frame interpolation smear from wherever the player was looking.
				player.yRotO = yaw;
				player.xRotO = pitch;
				player.yHeadRotO = yaw;
				lookInitialised = true;
			}
		}

		if (frame.messages() != null) {
			ClientPacketListener connection = client.getConnection();

			for (Macro.Message message : frame.messages()) {
				if (connection == null || message.text() == null || message.text().isEmpty()) {
					continue;
				}

				if (message.kind() == Macro.Message.Kind.COMMAND) {
					connection.sendCommand(message.text());
				} else {
					connection.sendChat(message.text());
				}
			}
		}
	}

	/** Writes the macro's current key state over whatever the game (or the player) last set. */
	private void assertHeldState(Options options) {
		options.keyUp.setDown(heldInput.forward());
		options.keyDown.setDown(heldInput.backward());
		options.keyLeft.setDown(heldInput.left());
		options.keyRight.setDown(heldInput.right());
		options.keyJump.setDown(heldInput.jump());
		options.keyShift.setDown(heldInput.shift());
		options.keySprint.setDown(heldInput.sprint());

		for (KeyMapping mapping : TrackedKeys.actionKeys(options)) {
			mapping.setDown(heldActions.contains(mapping.getName()));
		}
	}

	private static void releaseAll(Options options) {
		for (KeyMapping mapping : TrackedKeys.movementKeys(options)) {
			mapping.setDown(false);
		}

		for (KeyMapping mapping : TrackedKeys.actionKeys(options)) {
			mapping.setDown(false);
		}
	}
}
