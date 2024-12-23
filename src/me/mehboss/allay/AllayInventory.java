package me.mehboss.allay;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Allay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class AllayInventory implements Listener {

	@EventHandler
	public void onAllayDeath(EntityDeathEvent event) {
		if (event.getEntityType() != EntityType.ALLAY)
			return;

		Allay allay = (Allay) event.getEntity();

		if (!(Main.getInstance().allayInventories.containsKey(allay.getUniqueId())))
			return;

		dropItems(allay);
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		Player player = (Player) event.getPlayer();
		Inventory closedInventory = event.getInventory();

		// Check if this inventory belongs to an Allay
		UUID allayUUID = allayDelivery().getAllayUUIDFromInventory(closedInventory);
		if (allayUUID == null)
			return;

		Allay allay = (Allay) Bukkit.getEntity(allayUUID);
		if (allay == null)
			return;

		if (Main.getInstance().activeDeliveries.containsKey(allay.getUniqueId())) {
			if (closedInventory.isEmpty()) {
				Player sender = Main.getInstance().activeDeliveries.get(allay.getUniqueId()).getSender();
				allayReturn().returnAllayToSender(allay, sender);
			}

			return;
		}

		if (!(player.hasPermission("allaymail.use"))) {
			Main.getInstance().sendMessage(player, null, "No-Permission");
			return;
		}

		if (!(player.hasPermission("allaymail.bypass"))
				&& Main.getInstance().allayCooldowns.containsKey(player.getUniqueId())) {
			Long lastUse = Main.getInstance().allayCooldowns.get(player.getUniqueId());
			Long timePassed = Math.max(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - lastUse), 0);

			if (getConfig().getLong("Cooldown") > 0 && (timePassed < getConfig().getLong("Cooldown"))) {
				Long timeLeft = getConfig().getLong("Cooldown") - timePassed;

				if (getConfig().isSet("Cooldown-Active"))
					player.sendMessage(ChatColor.translateAlternateColorCodes('&',
							getConfig().getString("Cooldown-Active").replaceAll("%timeleft%", timeLeft.toString())));
				return;
			}
		}

		// Check if there are items in the inventory
		if (closedInventory.isEmpty()) {
			Main.getInstance().sendMessage(player, null, "No-Items-Added");
			return;
		}

		// Initiate delivery if items exist
		String recipientName = ChatColor.stripColor(allay.getCustomName());
		allayDelivery().initiateDelivery(player, allay, recipientName, closedInventory.getContents());
	}

	public void dropItems(Allay allay) {

		if (!(getConfig().getBoolean("Drop-On-Death")))
			return;

		if (Main.getInstance().allayInventories.containsKey(allay.getUniqueId())) {
			if (allay.getLocation() == null)
				return;

			World allayWorld = allay.getWorld();
			Location allayLocation = allay.getLocation();

			for (ItemStack item : Main.getInstance().allayInventories.get(allay.getUniqueId())) {
				if (item == null || item.getType() == Material.AIR)
					continue;

				allayWorld.dropItem(allayLocation, item);
			}

			Main.getInstance().allayInventories.remove(allay.getUniqueId());
		}
	}

	FileConfiguration getConfig() {
		return Main.getInstance().getConfig();
	}

	AllayDelivery allayDelivery() {
		return Main.getInstance().allayDelivery;
	}

	AllayReturn allayReturn() {
		return Main.getInstance().allayReturn;
	}
}
