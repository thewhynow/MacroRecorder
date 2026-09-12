package com.thewhynow.macromod.macro;

import java.util.List;

/**
 * A recorded macro: a sparse, per-tick list of input deltas plus the chat messages and
 * commands that were sent while recording.
 *
 * <p>Frames are sparse — a tick with no change at all produces no frame, and {@link Frame#keys()}
 * is {@code null} when the movement state is unchanged from the previous frame.
 */
public record Macro(int version, String name, int lengthTicks, boolean look, List<Frame> frames) {
	public static final int CURRENT_VERSION = 1;

	/** A single tick of a macro. Every field except {@link #tick()} may be null/empty. */
	public record Frame(
			int tick,
			MacroInput keys,
			List<String> pressed,
			List<String> released,
			Float yaw,
			Float pitch,
			List<Message> messages) {
	}

	/** The seven movement booleans vanilla derives from the key mappings each tick. */
	public record MacroInput(
			boolean forward,
			boolean backward,
			boolean left,
			boolean right,
			boolean jump,
			boolean shift,
			boolean sprint) {
		public static final MacroInput NONE = new MacroInput(false, false, false, false, false, false, false);
	}

	public record Message(Kind kind, String text) {
		public enum Kind { CHAT, COMMAND }
	}

	public int eventCount() {
		int count = 0;

		for (Frame frame : frames) {
			if (frame.keys() != null) count++;
			if (frame.pressed() != null) count += frame.pressed().size();
			if (frame.released() != null) count += frame.released().size();
			if (frame.messages() != null) count += frame.messages().size();
		}

		return count;
	}

	public int messageCount() {
		int count = 0;

		for (Frame frame : frames) {
			if (frame.messages() != null) count += frame.messages().size();
		}

		return count;
	}
}
