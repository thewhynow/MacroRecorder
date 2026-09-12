package com.thewhynow.macromod.macro;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The key mappings MacroMod records and replays.
 *
 * <p>Movement keys are handled separately from action keys: vanilla turns the movement keys into the
 * {@code Input} record every tick, so those are stored as a seven-boolean snapshot, while action keys
 * are stored as press/release deltas keyed by {@link KeyMapping#getName()} (e.g. {@code key.attack}),
 * which keeps a macro working after the player rebinds a key.
 */
public final class TrackedKeys {
	private TrackedKeys() {
	}

	/** Keys vanilla folds into the movement {@code Input} record each tick. */
	public static List<KeyMapping> movementKeys(Options options) {
		return List.of(
				options.keyUp,
				options.keyDown,
				options.keyLeft,
				options.keyRight,
				options.keyJump,
				options.keyShift,
				options.keySprint);
	}

	/** Keys recorded as discrete press/release events. */
	public static List<KeyMapping> actionKeys(Options options) {
		List<KeyMapping> keys = new ArrayList<>(List.of(
				options.keyAttack,
				options.keyUse,
				options.keyPickItem,
				options.keyInventory,
				options.keyDrop,
				options.keySwapOffhand));

		keys.addAll(Arrays.asList(options.keyHotbarSlots));
		return keys;
	}

	/** Resolves a recorded key name back to a live mapping, or null if this client has no such key. */
	public static KeyMapping byName(Options options, String name) {
		for (KeyMapping mapping : actionKeys(options)) {
			if (mapping.getName().equals(name)) {
				return mapping;
			}
		}

		return KeyMapping.get(name);
	}
}
