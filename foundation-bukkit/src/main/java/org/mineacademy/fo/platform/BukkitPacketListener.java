package org.mineacademy.fo.platform;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.model.PacketListener;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.remain.CompMetadata;
import org.mineacademy.fo.remain.Remain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Listens to and intercepts packets using Foundation inbuilt features
 */
@AutoRegister(hideIncompatibilityWarnings = false)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BukkitPacketListener extends PacketListener {

	/**
	 * The singleton of this class to auto register it.
	 */
	@Getter(value = AccessLevel.MODULE)
	private static final PacketListener instance = new BukkitPacketListener();

	/**
	 * Registers our packet listener for some of the more advanced features of Foundation
	 */
	@Override
	public void onRegister() {

		// "Fix" a Folia bug preventing Conversation API from working properly
		if (Remain.isFolia())
			this.addReceivingListener(PacketType.Play.Client.CHAT_MESSAGE, event -> {
				final WrapperPlayClientChatMessage packet = new WrapperPlayClientChatMessage(event);
				final String message = packet.getMessage();
				final Player player = event.getPlayer();

				if (player.isConversing()) {

					// Ensure to run sync since packets are async
					Platform.runTask(() -> player.acceptConversationInput(message));

					event.setCancelled(true);
				}
			});

		// Support editing signs on legacy Minecraft versions
		if (!Remain.hasPlayerOpenSignMethod())
			this.addReceivingListener(PacketType.Play.Client.UPDATE_SIGN, event -> {
				final Player player = event.getPlayer();
				final WrapperPlayClientUpdateSign packet = new WrapperPlayClientUpdateSign(event);
				final MetadataValue rawMetadata = CompMetadata.getTempMetadata(player, CompMetadata.TAG_OPENED_SIGN);

				if (rawMetadata == null)
					return;

				final Location metadataLocation = (Location) rawMetadata.value();

				final Vector3i position = packet.getBlockPosition();
				final String[] lines = packet.getTextLines();

				final Location location = new Location(
						player.getWorld(),
						position.x,
						position.y,
						position.z
				);

				if (location.equals(metadataLocation)) {
					CompMetadata.removeTempMetadata(player, CompMetadata.TAG_OPENED_SIGN);

					final Block block = player.getWorld().getBlockAt(location);
					final BlockState state = block.getState();

					if (state instanceof Sign) {
						final Sign sign = (Sign) state;

						for (int line = 0; line < lines.length; line++) {
							final String rawLine = lines[line];
							final String signText = SimpleComponent.fromMiniAmpersand(rawLine).toLegacySection(null); // TODO - need to verify if this.. works

							sign.setLine(line, signText);
						}

						sign.update(true);
					}
				}
			});
	}
}