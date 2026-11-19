package com.friendmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import com.friendmod.FriendTracker;

/** Colours the name above a player's head: green for friends, red for a player a friend is hitting. */
@Mixin(Player.class)
public abstract class PlayerNameMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void friendmod$color(CallbackInfoReturnable<Component> cir) {
		Player self = (Player) (Object) this;
		Integer color = FriendTracker.colorFor(self.getPlainTextName(), self.getUUID());
		if (color != null) {
			cir.setReturnValue(cir.getReturnValue().copy().withStyle(style -> style.withColor(color)));
		}
	}
}
