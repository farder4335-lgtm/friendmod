package com.friendmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.friendmod.FriendList;

/** Makes the name of a friend (shown above the player's head) slightly green. */
@Mixin(Entity.class)
public abstract class FriendNameMixin {
	private static final int FRIEND_COLOR = 0x7FD97F;

	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void friendmod$colorFriend(CallbackInfoReturnable<Component> cir) {
		if ((Object) this instanceof Player player && FriendList.contains(player.getName().getString())) {
			cir.setReturnValue(cir.getReturnValue().copy().withStyle(style -> style.withColor(FRIEND_COLOR)));
		}
	}
}
