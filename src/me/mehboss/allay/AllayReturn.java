package me.mehboss.allay;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.mehboss.allay.Main.DeliveryData;

public class AllayReturn {

	public void returnAllayToSender(Allay allay, Player sender) {
		Main.getInstance().sendMessage(sender, null, "Allay-Returning");
		returnTimer(allay, sender);
	}

	public void startReturnToSenderTimer(Allay allay, Player sender) {
		// Wait 20 seconds before returning to the sender
		new BukkitRunnable() {
			@Override
			public void run() {
				if (!allay.isValid() || (!activeDeliveries().containsKey(allay.getUniqueId()))
						|| sender.getUniqueId().equals(allay.getMemory(MemoryKey.LIKED_PLAYER))) {
					activeDeliveries().remove(allay.getUniqueId());
					this.cancel();
					return;
				}

				Main.getInstance().sendMessage(sender, null, "Allay-Returning");
				returnTimer(allay, sender);
			}
		}.runTaskLater(Main.getInstance(), 20 * getConfig().getInt("Return-Delay"));
	}

	public void returnTimer(Allay allay, Player sender) {
		// Schedule the return journey of the Allay
		new BukkitRunnable() {
			int attempts = 0;

			@Override
			public void run() {
				if (!allay.isValid() || !sender.isOnline()) {
					unpairAllay(allay);
					allayInventory().dropItems(allay);
					activeDeliveries().remove(allay.getUniqueId());
					this.cancel();
					return;
				}

				Location senderLocation = sender.getLocation();

				// Check if Allay and sender are in different worlds
				if (!allay.getWorld().getName().equals(senderLocation.getWorld().getName())) {
					// Teleport Allay to a safe location near the sender
					Location safeLocation = findSafeLocation(senderLocation,
							getConfig().getInt("Safe-Location-Radius"));
					if (safeLocation != null) {
						allay.teleport(safeLocation);
					} else {
						allay.teleport(sender);
					}
				}

				allay.setMemory(MemoryKey.LIKED_PLAYER, sender.getUniqueId());

				if (getConfig().getBoolean("Teleport-Nearby")
						&& allay.getLocation().distance(senderLocation) >= getConfig().getInt("Initiate-Teleport")) {
					allay.teleport(findSafeLocation(senderLocation, getConfig().getInt("Teleport-Distance")));
				}

				if (allay.getLocation().distance(senderLocation) > 20) {
					Vector direction = sender.getLocation().toVector().subtract(allay.getLocation().toVector())
							.normalize();
					allay.setVelocity(direction.multiply(getConfig().getDouble("Allay-Speed-Multiplier")));
				}

				if (allay.getLocation().distance(senderLocation) < 5) {
					if (getConfig().getBoolean("Reset-Allay")) {
						allayInventory().dropItems(allay);
						unpairAllay(allay);
					}

					Main.getInstance().sendMessage(sender, null, "Allay-Returned");
					activeDeliveries().remove(allay.getUniqueId());
					this.cancel();
					return;
				}

				if (attempts > getConfig().getInt("Find-Delay")) {
					Main.getInstance().sendMessage(sender, null, "Failed-Sender-Return");
					allay.teleport(senderLocation);
				}

				attempts++;
			}
		}.runTaskTimer(Main.getInstance(), 0, 20);
	}

	public Location findSafeLocation(Location origin, int radius) {
		World world = origin.getWorld();
		if (world == null) {
			return origin;
		}

		int minY = world.getMinHeight(); // Minimum Y for the world (e.g., Nether is not 0)
		int maxY = world.getMaxHeight(); // Maximum Y for the world

		// Apply a fixed offset in X and Z directions (e.g., a fixed distance away in a
		// straight line)
		int offsetX = radius; // Adjust this to be the desired distance in X
		int offsetZ = radius; // Adjust this to be the desired distance in Z

		// Start the search from the offset location
		Location startLocation = origin.clone().add(offsetX, 0, offsetZ);

		// Check if the block at the current Y is passable
		if (world.getBlockAt(startLocation).isPassable()
				&& world.getBlockAt(startLocation.clone().add(0, 1, 0)).isPassable()) {
			// Return the location with the same Y-level if safe
			return startLocation.clone().add(0, 1, 0);
		}

		// If not passable, iterate through nearby Y-levels to find a safe spot
		for (int y = startLocation.getBlockY() - radius; y <= startLocation.getBlockY() + radius; y++) {
			if (y < minY || y > maxY)
				continue;

			Location potentialLocation = startLocation.clone();
			potentialLocation.setY(y);

			// Check if the block and the one above it are passable
			if (world.getBlockAt(potentialLocation).isPassable()
					&& world.getBlockAt(potentialLocation.clone().add(0, 1, 0)).isPassable()) {
				return potentialLocation.add(0, 1, 0); // Return the safe location
			}
		}

		return origin; // No safe location found
	}

	public void unpairAllay(Allay allay) {
		if (getConfig().getBoolean("Reset-Allay")) {
			allay.setCustomName(null);
			allay.setTarget(null);
			allay.setRemoveWhenFarAway(true);
		}
	}

	FileConfiguration getConfig() {
		return Main.getInstance().getConfig();
	}

	AllayInventory allayInventory() {
		return Main.getInstance().allayInventory;
	}

	Map<UUID, DeliveryData> activeDeliveries() {
		return Main.getInstance().activeDeliveries;
	}
}
