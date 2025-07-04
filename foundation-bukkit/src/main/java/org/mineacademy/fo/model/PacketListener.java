package org.mineacademy.fo.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketEvent;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.chat.ChatTypes;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.platform.BukkitPlugin;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/**
 * Extend this class to listen to packets. Requires PacketEvents.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PacketListener {

	/**
	 * Called automatically when you use {@link org.mineacademy.fo.annotation.AutoRegister}, inject
	 * your packet listeners here.
	 */
	public abstract void onRegister();

	/**
	 * A convenience shortcut to add a packet listener
	 *
	 * @param adapter The packet adapter instance to add.
	 */
	protected void addPacketListener(final SimpleAdapter adapter) {
		HookManager.addPacketListener(adapter);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Receiving
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * A convenience method for listening to Client>Server packets of the given type.
	 * By default, the listener is registered with {@link PacketListenerPriority#NORMAL}.
	 *
	 * @param type The packet type to exclusively listen for, e.g. {@code PacketType.Play.Client.CHAT_COMMAND}.
	 * @param consumer The consumer to call when the packet is received.
	 */
	protected void addReceivingListener(final PacketTypeCommon type, final Consumer<PacketReceiveEvent> consumer) {
		this.addReceivingListener(PacketListenerPriority.NORMAL, type, consumer);
	}

	/**
	 * A convenience method for listening to Client>Server packets of the given type and priority.
	 *
	 * @param priority The priority of the listener, see {@link PacketListenerPriority}.
	 * @param type The packet type to exclusively listen for, e.g. {@code PacketType.Play.Client.CHAT_COMMAND}.
	 * @param consumer The consumer to call when the packet is received.
	 */
	protected void addReceivingListener(final PacketListenerPriority priority, final PacketTypeCommon type, final Consumer<PacketReceiveEvent> consumer) {
		this.addPacketListener(new SimpleAdapter(priority, type) {

			/**
			 * @see com.github.retrooper.packetevents.event.PacketListener#onPacketReceive(PacketReceiveEvent)
			 */
			@Override
			public void onPacketReceiving(final PacketReceiveEvent event) {
				if (event.getPlayer() != null) // especially during login/config phase, player may often be null.
					consumer.accept(event);
			}
		});
	}

	// ------------------------------------------------------------------------------------------------------------
	// Sending
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * A convenience method for listening to Server>Client packets of the given type.
	 *
	 * @param type The packet type to exclusively listen for, e.g. {@code PacketType.Play.Server.CHAT_MESSAGE}.
	 * @param consumer The consumer to call when the packet is sent.
	 */
	protected void addSendingListener(final PacketTypeCommon type, final Consumer<PacketSendEvent> consumer) {
		this.addSendingListener(PacketListenerPriority.NORMAL, type, consumer);
	}

	/**
	 * A convenience method for listening to Server>Client packets of the given type and priority.
	 *
	 * @param priority The priority of the listener, see {@link PacketListenerPriority}.
	 * @param type The packet type to exclusively listen for, e.g. {@code PacketType.Play.Server.CHAT_MESSAGE}.
	 * @param consumer The consumer to call when the packet is sent.
	 */
	protected void addSendingListener(final PacketListenerPriority priority, final PacketTypeCommon type, final Consumer<PacketSendEvent> consumer) {
		this.addPacketListener(new SimpleAdapter(priority, type) {

			/**
			 * @see com.github.retrooper.packetevents.event.PacketListener#onPacketSend(PacketSendEvent)
			 */
			@Override
			public void onPacketSend(@NotNull final PacketSendEvent event) {
				if (event.getPlayer() != null)
					consumer.accept(event);
			}

			@Override
			public void onPacketReceive(@NotNull final PacketReceiveEvent event) {
				if (type == PacketType.Play.Server.CHAT_MESSAGE || type == PacketType.Play.Client.CHAT_MESSAGE) { // todo, check
					// Packet can be both sided
				} else
					super.onPacketReceiving(event);
			}
		});
	}

	/**
	 * Sets the hoverable text in the server's menu
	 * To use this, create a new addSendingListener for PacketType.Status.Server.RESPONSE
	 * and get the {@link JsonObject} from {@link WrapperStatusServerResponse#getComponent()}.
	 * You can then get the "players" JsonObject by <code>JsonObject.getAsJsonObject("players")</code>,
	 * and can subsequently call <code>JsonObject.add("sample", hoverTextArray)</code>
	 * <p>
	 * <pre>
	 * {@code
	 * @Override
	 * public void onPacketSend(final PacketSendEvent event) {
	 *		WrapperStatusServerResponse response = new WrapperStatusServerResponse(event);
	 *      JsonObject component = response.getComponent();
	 *
	 *		// We prepare the hover text array.
	 *		JsonArray hoverText = compileHoverText("&cVery cool text!", "&aAnother line of text");
	 *		component.getAsJsonObject("players").add("sample", hoverText); // We replace the "sample" of players with our hover text.
	 *
	 *		response.setComponent(component);
	 * }
	 * }
	 * </pre>
	 *
	 * @param hoverTexts The text lines to be displayed when hovering over the text in the server's menu.
	 */
	protected JsonArray compileHoverText(final String... hoverTexts) {
		JsonArray array = new JsonArray();

		for (final String hoverText : hoverTexts) {
			final JsonObject sample = new JsonObject();

			final String colorized = CompChatColor.translateColorCodes(hoverText);
			sample.addProperty("name", colorized);
			sample.addProperty("id", UUID.randomUUID().toString());

			array.add(sample);
		}

		return array;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * A convenience adapter for handling chat packets doing most of the heavy work for you.
	 */
	protected abstract class SimpleChatAdapter extends SimpleAdapter {

		/**
		 * Players being processed RIGHT NOW inside the method. Prevents dead loop.
		 */
		private final Set<String> processedPlayers = new HashSet<>();

		/**
		 * Create a new chat listener
		 */
		public SimpleChatAdapter() {
			super(PacketListenerPriority.HIGHEST, MinecraftVersion.atLeast(V.v1_19) ? PacketType.Play.Server.SYSTEM_CHAT_MESSAGE : PacketType.Play.Server.CHAT_MESSAGE); // todo check
		}

		@Override
		public void onPacketSend(final PacketSendEvent event) {
			final Player player = event.getPlayer();
			final String playerName = player.getName();

			// Ignore dummy instances and disabled plugin or processed players
			if (!player.isOnline() || !BukkitPlugin.getInstance().isEnabled() || this.processedPlayers.contains(playerName))
				return;

			final PacketTypeCommon type = event.getPacketType();
			Component component;

			if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
				final WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(event);

				// Split apart these conditions to make this more readable
				if (packet.isOverlay())
					return;
				if (packet.getType() != null && packet.getType() == ChatTypes.GAME_INFO)
					return;

				component = packet.getMessage();
			} else if (type == PacketType.Play.Server.CHAT_MESSAGE) {
				final WrapperPlayServerChatMessage packet = new WrapperPlayServerChatMessage(event);
				final ChatMessage message = packet.getMessage();

				if (message.getType() == ChatTypes.GAME_INFO)
					return;

				component = message.getChatContent();
			} else {
				return; // Not a chat message
			}

			// Lock processing to one instance only to prevent another packet filtering
			try {
				this.processedPlayers.add(playerName);

				final boolean legacy = MinecraftVersion.olderThan(V.v1_16);
				String json = SimpleComponent.fromAdventure(component).toAdventureJson(null, legacy);

				if (json != null && json.length() < 50_000) {

					// This flag effectivelly doubles processing time from ~0.3ms to ~0.6ms that is why it needs to be explicitly enabled
					final boolean editJson = this.editJson();
					final Component oldJson = editJson ? SimpleComponent.fromAdventureJson(json, legacy).toAdventure(null) : null;

					try {
						json = this.onJsonMessage(player, json);

					} catch (final EventHandledException ex) {
						event.setCancelled(true);

						return;
					}

					if (editJson) {
						final Component newJson = GsonComponentSerializer.gson().deserialize(json);

						if (!newJson.equals(oldJson)) {
							if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
								final WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(event);
								packet.setMessage(newJson);
							} else {
								final WrapperPlayServerChatMessage packet = new WrapperPlayServerChatMessage(event);
								final ChatMessage message = packet.getMessage();

								message.setChatContent(newJson);
								packet.setMessage(message);
                            }
						}
					}
				}

			} finally {
				this.processedPlayers.remove(player.getName());
			}
		}

		/**
		 * Called when the chat message packet is received.
		 * <p>
		 * If you edit the jsonMessage we do NOT set it back unless you call
		 * {@link #editJson()} and set it to true.
		 * <p>
		 * To cancel the packet, throw {@link EventHandledException}
		 *
		 * @param player The player who sent the chat message.
		 * @param json The JSON of the message component sent by the player.
		 *
		 * @return
		 */
		protected String onJsonMessage(final Player player, final String json) {
			return json;
		}

		/**
		 * For performance purposes, json message in {@link #jsonMessage} is not edited by default
		 * Return true in order to call {@link #onJsonMessage(Player, String)}.
		 *
		 * @return
		 */
		protected boolean editJson() {
			return false;
		}
	}

	/**
	 * A convenience class so that you don't have to specify which plugin is the owner of the packet adapter.
	 * <p>
	 * By default, PacketEvents doesn't allow you to register packet listeners for specific packet types, so we do a check manually.
	 * This class has now become abstract so that you can extend it and implement your own packet handling logic.
	 */
	protected abstract class SimpleAdapter implements com.github.retrooper.packetevents.event.PacketListener {

		/**
		 * The packet we're listening for
		 */
		@Getter
		private final PacketTypeCommon type;

		/**
		 * Create a new packet adapter for the given packet type
		 *
		 * @param type The packet type to listen for. E.g. {@code PacketType.Play.Client.CHAT_COMMAND}.
		 */
		public SimpleAdapter(final PacketTypeCommon type) {
			this(PacketListenerPriority.NORMAL, type);
		}

		/**
		 * Create a new packet adapter for the given packet type with the given priority
		 *
		 * @param priority The priority of the listener, see {@link PacketListenerPriority}.
		 * @param type The packet type to listen for. E.g. {@code PacketType.Play.Client.CHAT_COMMAND}.
		 */
		public SimpleAdapter(final PacketListenerPriority priority, final PacketTypeCommon type) {
			PacketEvents.getAPI().getEventManager().registerListener(this, priority);

			this.type = type;
		}

		/**
		 * This method is automatically fired when the client sends the {@link #type} to the server.
		 *
		 * @param event The packet receive event.
		 */
		@Override
		public void onPacketReceive(@NotNull final PacketReceiveEvent event) {
			if (event.getPacketType() == this.type) {
				this.onPacketReceiving(event);
			}
		}

		public void onPacketReceiving(PacketReceiveEvent event) {
			throw new FoException("Override onPacketReceiving to handle sending client>server packet type " + this.type);
		};

		public void onPacketSending(PacketSendEvent event) {
			throw new FoException("Override onPacketReceiving to handle sending server>client packet type " + this.type);
		}

		/**
		 * This method is automatically fired when the server wants to send the {@link #type} to the client.
		 *
		 * @param event The packet send event.
		 */
		@Override
		public void onPacketSend(@NotNull final PacketSendEvent event) {
			if (event.getPacketType() == this.type) {
				this.onPacketSending(event);
			}
		}
	}
}
