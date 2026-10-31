package com.retile.client;

import net.fabricmc.api.ClientModInitializer;
import com.retile.brush.*;
import com.retile.network.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.*;
import java.util.concurrent.ThreadLocalRandom;

public class RetileClient implements ClientModInitializer {
	public static BrushSettings settings;
	private static long nextSeed = ThreadLocalRandom.current().nextLong();
	private static boolean strokeActive;
	private static boolean strokeFixedSeed;
	private static boolean strokeErrorShown;
	private static long strokeSeed;
	private static long lastSentTime;
	private static BlockPos lastSentCenter;
	private static int strokeSlot;
	@Override
	public void onInitializeClient() {
		settings = BrushConfig.load();
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!world.isClientSide() || !BrushSelection.isBrush(player)) return InteractionResult.PASS;
			open(); return InteractionResult.FAIL;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClientSide() || !BrushSelection.isBrush(player)) return InteractionResult.PASS;
			open(); return InteractionResult.FAIL;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tickStroke(client);
			BrushHotkeys.tick(client);
			BrushPreview.tick(client);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearStroke());
		BrushPreview.register();
	}
	private static void open() {
		Minecraft client = Minecraft.getInstance();
		if (client.screen == null) client.setScreen(new BrushScreen());
	}
	public static BlockPos target(Minecraft client) {
		if (client.player == null || client.level == null) return null;
		HitResult hit = client.player.pick(BrushConstants.RAYCAST_DISTANCE, 1f, false);
		return hit.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) hit).getBlockPos() : null;
	}
	private static void tickStroke(Minecraft client) {
		if (client.level == null || !BrushSelection.isBrush(client.player) || client.screen != null
				|| client.getOverlay() != null || client.player.isUsingItem()
				|| strokeActive && client.player.getInventory().getSelectedSlot() != strokeSlot) {
			endStroke();
			return;
		}
		boolean pressed = false;
		while (client.options.keyAttack.consumeClick()) pressed = true;
		boolean held = client.options.keyAttack.isDown();
		// A queued new press also catches release/repress between two client ticks.
		if (strokeActive && (!held || pressed)) endStroke();
		if (!strokeActive) {
			if (!pressed) return;
			if (!held) { applySingle(client); return; }
			strokeSeed = seedForNextApply();
			strokeFixedSeed = settings.fixedSeed;
			strokeSlot = client.player.getInventory().getSelectedSlot();
			strokeActive = true;
		}
		BlockPos center = target(client);
		if (center == null) return;
		int spacing = Math.max(1, settings.radius / BrushConstants.STROKE_SPACING_DIVISOR);
		long now = System.nanoTime();
		if (lastSentCenter != null && (center.distSqr(lastSentCenter) < (double) spacing * spacing
				|| now - lastSentTime < BrushConstants.APPLY_THROTTLE_MS * 1_000_000L)) return;
		if (!canApply()) return;
		ApplyBrushPayload.Phase phase = lastSentCenter == null
				? ApplyBrushPayload.Phase.START : ApplyBrushPayload.Phase.CONTINUE;
		ClientPlayNetworking.send(ApplyBrushPayload.from(center, settings, strokeSeed, phase));
		lastSentCenter = center.immutable();
		lastSentTime = now;
	}
	private static boolean canApply() {
		String error = !settings.isValidForApply() ? "no_replacements"
				: !ClientPlayNetworking.canSend(ApplyBrushPayload.TYPE) || !ClientPlayNetworking.canSend(StrokeEndPayload.TYPE)
				? "server_missing" : null;
		if (error == null) return true;
		if (!strokeActive || !strokeErrorShown) message(error);
		strokeErrorShown = strokeActive;
		return false;
	}
	private static void applySingle(Minecraft client) {
		BlockPos center = target(client); if (center == null) return;
		if (!canApply()) return;
		long seed = seedForNextApply();
		ClientPlayNetworking.send(ApplyBrushPayload.from(center, settings, seed));
		if (!settings.fixedSeed) nextSeed = ThreadLocalRandom.current().nextLong();
	}
	/** Called before screens/world teardown, while the connection can still send the end packet. */
	public static void endStroke() {
		if (!strokeActive) return;
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() != null && client.getConnection().getConnection().isConnected()
				&& ClientPlayNetworking.canSend(StrokeEndPayload.TYPE)) ClientPlayNetworking.send(new StrokeEndPayload());
		clearStroke();
	}
	private static void clearStroke() {
		if (strokeActive && lastSentCenter != null && !strokeFixedSeed) nextSeed = ThreadLocalRandom.current().nextLong();
		strokeActive = false;
		strokeErrorShown = false;
		lastSentCenter = null;
		lastSentTime = 0;
	}
	public static long seedForNextApply() {
		return strokeActive ? strokeSeed : settings.fixedSeed ? settings.seed : nextSeed;
	}
	public static void message(String key) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) client.player.displayClientMessage(Component.translatable("retile.message." + key), true);
	}
	public static void sendHistory(boolean redo) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !client.player.isCreative()) return;
		if (redo ? ClientPlayNetworking.canSend(RedoPayload.TYPE) : ClientPlayNetworking.canSend(UndoPayload.TYPE)) {
			ClientPlayNetworking.send(redo ? new RedoPayload() : new UndoPayload());
		} else message("server_missing");
	}
}
