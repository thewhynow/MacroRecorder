package com.thewhynow.macromod.record;

import com.mojang.blaze3d.platform.InputConstants;
import com.thewhynow.macromod.macro.Macro;
import com.thewhynow.macromod.macro.TrackedKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Captures one take. Driven from {@code ClientTickEvents.END_CLIENT_TICK}, which runs after the
 * player's input tick, so the movement state read here is the one vanilla used this tick.
 */
public final class MacroRecorder {
	/** Look changes below this many degrees are not worth a frame. */
	private static final float LOOK_EPSILON = 0.01F;

	private boolean recording;
	private int tick;
	private final List<Macro.Frame> frames = new ArrayList<>();
	private final List<Macro.Message> pendingMessages = new ArrayList<>();
	private final Set<String> keysDown = new HashSet<>();
	private final List<String> tickPressed = new ArrayList<>();
	private final List<String> tickReleased = new ArrayList<>();
	private Macro.MacroInput lastInput = Macro.MacroInput.NONE;
	private Float lastYaw;
	private Float lastPitch;
	private Macro lastTake;

	public boolean isRecording() {
		return recording;
	}

	public int currentTick() {
		return tick;
	}

	/** The most recent finished take, saved or not, or null if nothing has been recorded yet. */
	public Macro lastTake() {
		return lastTake;
	}

	public void start() {
		recording = true;
		tick = 0;
		frames.clear();
		pendingMessages.clear();
		keysDown.clear();
		tickPressed.clear();
		tickReleased.clear();
		lastInput = Macro.MacroInput.NONE;
		lastYaw = null;
		lastPitch = null;
	}

	/** Ends the take and returns it. The take is also kept as {@link #lastTake()} for {@code /macro save}. */
	public Macro stop() {
		recording = false;

		// Flush anything typed after the last tick, then release every key still held so a replay
		// does not leave the player walking.
		if (!pendingMessages.isEmpty() || !keysDown.isEmpty() || !lastInput.equals(Macro.MacroInput.NONE)) {
			frames.add(new Macro.Frame(
					tick,
					lastInput.equals(Macro.MacroInput.NONE) ? null : Macro.MacroInput.NONE,
					null,
					keysDown.isEmpty() ? null : new ArrayList<>(keysDown),
					null,
					null,
					pendingMessages.isEmpty() ? null : new ArrayList<>(pendingMessages)));
			pendingMessages.clear();
			keysDown.clear();
		}

		lastTake = new Macro(Macro.CURRENT_VERSION, null, tick, true, List.copyOf(frames));
		frames.clear();
		return lastTake;
	}

	/** Drops an in-progress take without keeping it (used on disconnect). */
	public void abort() {
		recording = false;
		frames.clear();
		pendingMessages.clear();
		keysDown.clear();
		tickPressed.clear();
		tickReleased.clear();
	}

	public void tick(Minecraft client) {
		if (!recording) {
			return;
		}

		LocalPlayer player = client.player;

		if (player == null) {
			tick++;
			return;
		}

		Macro.MacroInput input = snapshot(player.input.keyPresses);
		Macro.MacroInput keys = input.equals(lastInput) ? null : input;

		// Transitions seen by the mixin this tick, including taps that started and ended
		// between ticks, which a once-per-tick isDown() poll would never see.
		List<String> pressed = tickPressed.isEmpty() ? null : new ArrayList<>(tickPressed);
		List<String> released = tickReleased.isEmpty() ? null : new ArrayList<>(tickReleased);
		tickPressed.clear();
		tickReleased.clear();

		// Reconcile against the live state: vanilla can release keys without going through
		// KeyMapping.set (opening a screen, losing focus), and a macro must not hold those forever.
		for (KeyMapping mapping : TrackedKeys.actionKeys(client.options)) {
			String name = mapping.getName();
			boolean down = mapping.isDown();
			boolean tracked = keysDown.contains(name);

			if (down && !tracked) {
				if (pressed == null) pressed = new ArrayList<>(1);
				pressed.add(name);
				keysDown.add(name);
			} else if (!down && tracked) {
				if (released == null) released = new ArrayList<>(1);
				released.add(name);
				keysDown.remove(name);
			}
		}

		float yaw = player.getYRot();
		float pitch = player.getXRot();
		boolean lookChanged = lastYaw == null
				|| Math.abs(yaw - lastYaw) > LOOK_EPSILON
				|| Math.abs(pitch - lastPitch) > LOOK_EPSILON;

		List<Macro.Message> messages = pendingMessages.isEmpty() ? null : new ArrayList<>(pendingMessages);
		pendingMessages.clear();

		if (keys != null || pressed != null || released != null || messages != null || lookChanged) {
			frames.add(new Macro.Frame(
					tick,
					keys,
					pressed,
					released,
					lookChanged ? yaw : null,
					lookChanged ? pitch : null,
					messages));
			lastInput = input;
			lastYaw = yaw;
			lastPitch = pitch;
		}

		tick++;
	}

	/** Called from the KeyMapping mixin for every physical press and release. */
	public void onKeyStateChanged(InputConstants.Key key, boolean down) {
		if (!recording) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		if (client.options == null) {
			return;
		}

		for (KeyMapping mapping : TrackedKeys.actionKeys(client.options)) {
			if (!mapping.matches(key)) {
				continue;
			}

			String name = mapping.getName();

			// Only real transitions: drops key repeats, and releases of keys that were never
			// held (vanilla fires those for letters typed while a screen was open).
			if (down ? keysDown.add(name) : keysDown.remove(name)) {
				(down ? tickPressed : tickReleased).add(name);
			}
		}
	}

	public void onChatSent(String message) {
		if (recording) {
			pendingMessages.add(new Macro.Message(Macro.Message.Kind.CHAT, message));
		}
	}

	public void onCommandSent(String command) {
		// Never record the command that is controlling the recording.
		if (recording && !isOwnCommand(command)) {
			pendingMessages.add(new Macro.Message(Macro.Message.Kind.COMMAND, command));
		}
	}

	private static boolean isOwnCommand(String command) {
		String root = command.trim();
		int space = root.indexOf(' ');

		if (space >= 0) {
			root = root.substring(0, space);
		}

		return root.equals("macro");
	}

	private static Macro.MacroInput snapshot(Input input) {
		return new Macro.MacroInput(
				input.forward(),
				input.backward(),
				input.left(),
				input.right(),
				input.jump(),
				input.shift(),
				input.sprint());
	}
}
