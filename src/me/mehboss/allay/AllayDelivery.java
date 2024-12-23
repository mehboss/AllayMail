package me.mehboss.allay;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.mehboss.allay.Main.DeliveryData;

public class AllayDelivery implements Listener {

	@EventHandler
	public void onAllayInteract(PlayerInteractEntityEvent event) {
		if (!(event.getRightClicked() instanceof Allay))
			return;

		Allay allay = (Allay) event.getRightClicked();
		Player player = event.getPlayer();

		String recipientName = ChatColor.stripColor(allay.getCustomName());

		if (recipientName == null)
			return;

		if (player.getItemInUse() != null || event.getHand() == EquipmentSlot.OFF_HAND)
			return;

		if (Bukkit.getPlayer(recipientName) == null) {
			return;
		}

		// Check if the Allay is currently delivering items
		if (activeDeliveries().containsKey(allay.getUniqueId()) && !(recipientName.equals(player.getName()))) {
			Main.getInstance().sendMessage(player, null, "On-Delivery");
			return;
		}

		if (!(activeDeliveries().containsKey(allay.getUniqueId())) && recipientName.equals(player.getName())) {
			Main.getInstance().sendMessage(player, null, "Deliver-To-Self");
			return;
		}

		// If the Allay has a valid player name, open inventory
		Inventory allayInventory = Main.getInstance().allayInventories.computeIfAbsent(allay.getUniqueId(),
				id -> Bukkit.createInventory(null, 9, ChatColor.GOLD + "Delivery Inventory"));
		player.openInventory(allayInventory);
		allay.setMemory(MemoryKey.LIKED_PLAYER, player.getUniqueId());
		allay.setRemoveWhenFarAway(false);
	}

	public UUID getAllayUUIDFromInventory(Inventory inventory) {
		for (Map.Entry<UUID, Inventory> entry : Main.getInstance().allayInventories.entrySet()) {
			if (entry.getValue().equals(inventory)) {
				return entry.getKey();
			}
		}
		return null;
	}

	public void initiateDelivery(Player sender, Allay allay, String recipientName, ItemStack[] items) {
		Player recipient = Bukkit.getPlayer(recipientName);
		if (recipient == null) {
			Main.getInstance().sendMessage(sender, null, "Recipient-Is-Offline");
			return;
		}

		if (!(getConfig().getBoolean("Allow-Cross-World"))
				&& !(allay.getWorld().getName().equals(recipient.getWorld().getName()))) {
			Main.getInstance().sendMessage(sender, null, "Not-Allowed");
			return;
		}

		Main.getInstance().sendMessage(sender, recipient, "Allay-Delivery-Initiated");

		DeliveryData deliveryData = new DeliveryData(sender, recipientName, items);
		activeDeliveries().put(allay.getUniqueId(), deliveryData);
		Main.getInstance().allayCooldowns.put(sender.getUniqueId(), System.currentTimeMillis());

		// Check if Allay and recipient are in different worlds
		if (!allay.getWorld().getName().equals(recipient.getLocation().getWorld().getName())) {

			// Teleport Allay to a safe location near the recipient
			Location safeLocation = allayReturn().findSafeLocation(recipient.getLocation(),
					getConfig().getInt("Safe-Location-Radius"));
			if (safeLocation != null) {
				allay.teleport(safeLocation);
			} else {
				Main.getInstance().sendMessage(sender, null, "No-Safe-Location");
				return;
			}
		}

		// Move the Allay to the recipient
		new BukkitRunnable() {
			int attempts = 0;

			@Override
			public void run() {
				if (!allay.isValid()) {
					Main.getInstance().sendMessage(sender, null, "Allay-Died");
					allayInventory().dropItems(allay);
					activeDeliveries().remove(allay.getUniqueId());
					this.cancel();
					return;
				}

				Location recipientLocation = recipient.getLocation();

				if (attempts > getConfig().getInt("Find-Delay")) {
					Main.getInstance().sendMessage(sender, null, "Failed-Recipient-Delivery");
					allayReturn().returnTimer(allay, sender);
					this.cancel();
					return;
				}

				allay.setMemory(MemoryKey.LIKED_PLAYER, recipient.getUniqueId());

				if (getConfig().getBoolean("Teleport-Nearby")
						&& allay.getLocation().distance(recipientLocation) >= getConfig().getInt("Initiate-Teleport")) {
					allay.teleport(
							allayReturn().findSafeLocation(recipientLocation, getConfig().getInt("Teleport-Distance")));
				}

				if (allay.getLocation().distance(recipientLocation) > 20) {
					Vector direction = recipientLocation.toVector().subtract(allay.getLocation().toVector())
							.normalize();
					allay.setVelocity(direction.multiply(getConfig().getDouble("Allay-Speed-Multiplier")));
				}

				if (allay.getLocation().distance(recipientLocation) < 5) {
					this.cancel();
					Main.getInstance().sendMessage(recipient, sender, "Allay-Notify-Recipient");
					Main.getInstance().sendMessage(sender, recipient, "Allay-Notify-Sender");
					allayReturn().startReturnToSenderTimer(allay, sender);
				}

				attempts++;
			}
		}.runTaskTimer(Main.getInstance(), 0, 20);
	}

	FileConfiguration getConfig() {
		return Main.getInstance().getConfig();
	}

	AllayReturn allayReturn() {
		return Main.getInstance().allayReturn;
	}

	AllayInventory allayInventory() {
		return Main.getInstance().allayInventory;
	}

	Map<UUID, DeliveryData> activeDeliveries() {
		return Main.getInstance().activeDeliveries;
	}

}
