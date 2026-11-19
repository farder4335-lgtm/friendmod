package com.friendmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import com.friendmod.FriendTracker;

/** Same colouring in the player list (Tab). */
@Mixin(PlayerTabOverlay.class)
public abstract class TabListMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void friendmod$color(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		Integer color = FriendTracker.colorFor(info.getProfile().name(), info.getProfile().id());
		if (color != null) {
			cir.setReturnValue(cir.getReturnValue().copy().withStyle(style -> style.withColor(color)));
		}
	}
}
