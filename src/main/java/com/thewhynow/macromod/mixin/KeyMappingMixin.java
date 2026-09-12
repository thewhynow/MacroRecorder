package com.thewhynow.macromod.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thewhynow.macromod.MacroModClient;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports every physical key transition to the recorder.
 *
 * <p>{@code KeyMapping.set} is what the keyboard and mouse handlers call on each press and release,
 * so this sees taps that begin and end inside a single tick — which polling {@code isDown()} once per
 * tick cannot. Vanilla itself only reacts to those through the click counter, which is exactly what
 * playback reproduces.
 */
@Mixin(KeyMapping.class)
public class KeyMappingMixin {
	@Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V", at = @At("HEAD"))
	private static void macromod$onKeySet(InputConstants.Key key, boolean down, CallbackInfo ci) {
		MacroModClient.recorder().onKeyStateChanged(key, down);
	}
}
