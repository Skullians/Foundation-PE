package org.mineacademy.fo.platform;

import java.util.List;
import java.util.stream.Collectors;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.recipe.data.MerchantOffer;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMerchantOffers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.enchant.SimpleEnchantment;
import org.mineacademy.fo.model.PacketListener;
import org.mineacademy.fo.remain.CompItemFlag;
import org.mineacademy.fo.remain.CompMaterial;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitItemStack;
import static io.github.retrooper.packetevents.util.SpigotConversionUtil.toBukkitItemStack;

/**
 * Listens to and intercepts packets using Foundation inbuilt features
 */
@AutoRegister(hideIncompatibilityWarnings = true, doNotAutoRegister = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BukkitEnchantPacketListener extends PacketListener {

	/**
	 * The singleton of this class to auto register it.
	 */
	@Getter(value = AccessLevel.MODULE)
	private static final PacketListener instance = new BukkitEnchantPacketListener();

	/**
	 * Registers our packet listener for some of the more advanced features of Foundation
	 */
	@Override
	public void onRegister() {

		// To ensure, the client isn't trying to create an item with our fake enchantment lore
		// Because that would cause duplicate entries if the enchantment is upgraded/remove
		// Ex:
		//   BlackNova II (fake lore)
		//   BlackNova I  (actual lore of the item)
		this.addReceivingListener(PacketType.Play.Client.CREATIVE_INVENTORY_ACTION, event -> {
			final WrapperPlayClientCreativeInventoryAction packet = new WrapperPlayClientCreativeInventoryAction(event);
			final ItemStack item = toBukkitItemStack(packet.getItemStack());

			if (item != null && !CompMaterial.isAir(item.getType()) && !CompItemFlag.HIDE_ENCHANTS.has(item)) {
				final ItemStack newItem = SimpleEnchantment.removeEnchantmentLores(item);

				// I don't like this casting; however, we have the utility methods in HookManager which have the checks for PacketEvents.
				// I mainly created them because of converting List<ItemStack>s, but wanted to stick with conventions.
				if (newItem != null)
					packet.setItemStack(fromBukkitItemStack(newItem));
			}
		});

		// Auto placement of our lore when items are custom enchanted
		this.addSendingListener(PacketType.Play.Server.SET_SLOT, event -> {
			final WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(event);
			ItemStack item = toBukkitItemStack(packet.getItem());

			if (item != null && !CompMaterial.isAir(item.getType()) && !CompItemFlag.HIDE_ENCHANTS.has(item)) {
				item = SimpleEnchantment.addEnchantmentLores(item);

				// Write the item
				if (item != null)
					packet.setItem(fromBukkitItemStack(item));
			}
		});

		this.addSendingListener(PacketType.Play.Server.WINDOW_ITEMS, event -> {
			final WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);

			// for older versions, this is not needed because I believe they use an array
			final List<ItemStack> itemStacks = packet.getItems().stream()
					.map(SpigotConversionUtil::toBukkitItemStack)
					.collect(Collectors.toList());
			if (itemStacks != null) {
				boolean changed = false;
				final int size = itemStacks.size();
				for (int j = 0; j < size; j++) {

					ItemStack item = itemStacks.get(j);
					if (item != null && !CompMaterial.isAir(item.getType()) && !CompItemFlag.HIDE_ENCHANTS.has(item)) {
						item = SimpleEnchantment.addEnchantmentLores(item);

						if (item == null)
							continue;

						itemStacks.set(j, item);
						changed = true;
					}
				}
				if (changed)
					packet.setItems(itemStacks.stream()
							.map(SpigotConversionUtil::fromBukkitItemStack)
							.collect(Collectors.toList()));
			}
		});

		if (MinecraftVersion.atLeast(V.v1_9))
			this.addSendingListener(PacketType.Play.Server.MERCHANT_OFFERS, event -> {
				final WrapperPlayServerMerchantOffers packet = new WrapperPlayServerMerchantOffers(event);
				final List<MerchantOffer> offers = packet.getMerchantOffers();

				boolean changed = false;

				for (int i = 0; i < offers.size(); i++) {
					final MerchantOffer offer = offers.get(i);
					ItemStack item = toBukkitItemStack(offer.getOutputItem());

					if (!CompMaterial.isAir(item.getType()) && !CompItemFlag.HIDE_ENCHANTS.has(item)) {
						item = SimpleEnchantment.addEnchantmentLores(item);

						if (item == null)
							continue;

						offer.setOutputItem(fromBukkitItemStack(item));
						offers.set(i, offer);

						changed = true;
					}
				}

				if (changed)
					packet.setMerchantOffers(offers);
			});
	}
}