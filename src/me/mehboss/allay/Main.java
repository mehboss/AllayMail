package me.mehboss.allay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import me.mehboss.utils.Metrics;
import me.mehboss.utils.UpdateChecker;

public class Main extends JavaPlugin {

	public Map<UUID, DeliveryData> activeDeliveries = new HashMap<>();
	public Map<UUID, Inventory> allayInventories = new HashMap<>();
	public Map<UUID, Long> allayCooldowns = new HashMap<>();
	public static Main instance;
	public AllayReturn allayReturn;
	public AllayDelivery allayDelivery;
	public AllayInventory allayInventory;

	Boolean uptodate = true;
	String newupdate = null;
	
	@Override
	public void onEnable() {
		instance = this;
		allayReturn = new AllayReturn();
		allayDelivery = new AllayDelivery();
		allayInventory = new AllayInventory();

		Bukkit.getPluginManager().registerEvents(new AllayInventory(), this);
		Bukkit.getPluginManager().registerEvents(new AllayDelivery(), this);

		getLogger().log(Level.INFO,
				"Made by MehBoss on Spigot. For support please PM me and I will get back to you as soon as possible!");
		getConfig().options().copyDefaults(true);
		saveDefaultConfig();
		
		registerUpdateChecker();
		registerBstats();
	}

	void registerUpdateChecker() {
		new UpdateChecker(this, 121448).getVersion(version -> {

			newupdate = version;

			if (getDescription().getVersion().compareTo(version) >= 0) {
				getLogger().log(Level.INFO, "Checking for updates..");
				getLogger().log(Level.INFO,
						"We are all up to date with the latest version. Thank you for using allay mail :)");
			} else {
				getLogger().log(Level.INFO, "Checking for updates..");
				getLogger().log(Level.WARNING,
						"An update has been found! This could be bug fixes or additional features. Please update AllayMail at https://www.spigotmc.org/resources/mehboss.121448/");
				uptodate = false;

			}
		});

	}
	
	void registerBstats() {
		int pluginId = 24207;
		Metrics metrics = new Metrics(this, pluginId);
		metrics.addCustomChart(new Metrics.MultiLineChart("players_and_servers", new Callable<Map<String, Integer>>() {

			@Override
			public Map<String, Integer> call() throws Exception {
				Map<String, Integer> valueMap = new HashMap<>();
				valueMap.put("servers", 1);
				valueMap.put("players", Bukkit.getOnlinePlayers().size());
				return valueMap;
			}
		}));
	}
	
	void sendMessage(Player sender, Player recipient, String path) {
		if (getConfig().isSet(path)) {

			String message = ChatColor.translateAlternateColorCodes('&', getConfig().getString(path)
					.replaceAll("%delay%", String.valueOf(getConfig().getInt("Return-Delay"))));

			if (recipient == null) {
				sender.sendMessage(message);
			} else {
				sender.sendMessage(message.replaceAll("%player%", recipient.getName()));
			}
		}
	}

	public static Main getInstance() {
		return instance;
	}

	static class DeliveryData {
		private final Player sender;
		private final String recipient;
		private final ItemStack[] items;

		public DeliveryData(Player sender, String recipient, ItemStack[] items) {
			this.sender = sender;
			this.recipient = recipient;
			this.items = items;
		}

		public Player getSender() {
			return sender;
		}

		public String getRecipient() {
			return recipient;
		}

		public ItemStack[] getItems() {
			return items;
		}

		public Boolean hasItems() {
			for (ItemStack item : items) {
				if (item != null) {
					return true;
				}
			}
			return false;
		}
	}
}