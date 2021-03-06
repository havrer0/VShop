package au.com.live.havrer0.vshop;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import lib.PatPeter.SQLibrary.Database;
import lib.PatPeter.SQLibrary.SQLite;
import net.milkbowl.vault.item.Items;
import net.milkbowl.vault.economy.*;


public final class VShop extends JavaPlugin implements Listener {
	File configFile;
	FileConfiguration config;
	Database sql = new SQLite(Logger.getLogger("Minecraft"), "[VShop] ", this.getDataFolder().getAbsolutePath(), "VShop", ".db");
	public static Economy eco = null;
	
	public void loadYamls() {
		try {
			try {
				config.load(configFile);
			} catch (InvalidConfigurationException e) {				
				e.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void saveYamls() {
		try {
			config.save(configFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        eco = rsp.getProvider();
        return eco != null;
    }

	@Override
	public void onEnable() {
		if (!setupEconomy()){
			this.getLogger().severe("Can't find Vault, Disabling");
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}
		this.getServer().getPluginManager().registerEvents(this, this);;
		configFile = new File(getDataFolder(), "config.yml");
	    config = new YamlConfiguration();
	    loadYamls();
	    sql.open();  
	    if (!config.getBoolean("setupDone")) {
	    	/*try {
				sql.query("CREATE TABLE `Players` ( `UUID`	TEXT NOT NULL UNIQUE, `PlayerName`	TEXT NOT NULL UNIQUE, PRIMARY KEY(UUID))");		
			} catch (SQLException e) {
				e.printStackTrace();
			}*/
	    	try {
				sql.query("CREATE TABLE `Selling` (`SellingID` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE, `ItemName` TEXT NOT NULL, `ItemMetadata` INTEGER NOT NULL DEFAULT 0, `ItemAmount` INTEGER NOT NULL, `Seller` TEXT NOT NULL, `Price` REAL NOT NULL, `Infinite` INTEGER  NOT NULL DEFAULT 0);");
			} catch (SQLException e) {				
				e.printStackTrace();
			}
	    	try {
				sql.query("CREATE TABLE `Transactions` (`TransactionID` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE, `ItemName` TEXT NOT NULL, `ItemMetadata` INTEGER NOT NULL DEFAULT 0, `Seller` TEXT NOT NULL, `Purchaser` TEXT NOT NULL, `Price` REAL NOT NULL, `AmountPurchased` INTEGER NOT NULL);");
			} catch (SQLException e) {				
				e.printStackTrace();
			}
            config.set("setupDone", true);
            config.set("taxEnabled", false);
            config.set("taxPercentAsDecimal", 0.1);
            config.set("useServerAccount", false);
            config.set("serverAccountName", "");
            config.set("createServerAccount", false);
	    }
	    if (config.getBoolean("createServerAccount")) {
            eco.createPlayerAccount(config.getString("serverAccountName"));
	    	eco.withdrawPlayer(config.getString("serverAccountName"), eco.getBalance(config.getString("serverAccountName")));
	    	config.set("createServerAccount", false);
	    }
	    this.getLogger().info("VShop version 1.0 has been loaded!");
	}
	
	@Override
	public void onDisable() {
		//No need to save configuration files, as they are not modified in game.
		//saveYamls();
		sql.close();
		this.getLogger().info("VShop version " + this.getDescription().getVersion().toString() + " has been unloaded!");
	}
	
	/*@EventHandler
	public boolean onPlayerJoin(PlayerJoinEvent event) {
		try {
			sql.query("UPDATE Players SET PlayerName='" + event.getPlayer().getName().toString() + "' WHERE UUID='" + event.getPlayer().getUniqueId().toString() + "';");
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			this.getLogger().severe("An update of the UUID-PlayerName database failed, this is usually due to a new player joining.");;
			event.getPlayer().sendMessage(e.getMessage());
			try {
				sql.query("INSERT INTO Players (UUID, PlayerName) VALUES ('" + event.getPlayer().getUniqueId().toString() + "','" + event.getPlayer().getName().toString() + "');");
				return true;
			} catch (SQLException e1) {				
				e1.printStackTrace();
				this.getLogger().severe("The insert of a new player's UUID and PlayerName failed, try deleting both config.yml and the database.");
				return false;
			}
		}
		//return false;
	}
*/
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		
		if (cmd.getName().equalsIgnoreCase("vs") || cmd.getName().equalsIgnoreCase("vshop") && sender.hasPermission("vshop.info")) {
			sender.sendMessage(ChatColor.GREEN + "VShop is running version: " + ChatColor.AQUA + this.getDescription().getVersion().toString() + ChatColor.GREEN + ".");
			return true;
		}
		
		if (cmd.getName().equalsIgnoreCase("sell") && sender.hasPermission("vshop.sell")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage(ChatColor.RED + "Senders without inventories cannot sell, try using an admin tool instead.");
				return true;
			}
			Player player = (Player) sender;
			PlayerInventory inv = player.getInventory();
			int pitemamt = 0;
			if (args.length < 2){
				return false;
			}
			if (Double.parseDouble(args[1]) < 0) {
				player.sendMessage(ChatColor.RED + "You can't use negative numbers!");
				return true;
			}
			if (args[0].equalsIgnoreCase("hand")) {
				//attempts to update a previous listing of the item, if that fails, it inserts it into a new row.
				try {
					if (Items.itemByType(inv.getItemInMainHand().getType()).isDurable()) {
						if (!(inv.getItemInMainHand().getDurability() == 0)) {
							player.sendMessage(ChatColor.RED + "You can't sell items that aren't at maximum durability.");
							return true;
						}
					}
					if (inv.getItemInMainHand().getType() == Material.AIR || inv.getItemInMainHand().getType() == null) {
						player.sendMessage(ChatColor.RED + "You can't sell air! What sort of server do you think this is?");
						return true;
					}
					ResultSet res = sql.query("SELECT * FROM Selling WHERE Seller='" + player.getUniqueId().toString() + "' AND ItemName='" + inv.getItemInMainHand().getType().toString() + "' AND ItemMetadata='" + inv.getItemInMainHand().getDurability() + "';");
					sql.query("UPDATE Selling SET ItemAmount='" + (res.getInt("ItemAmount") + inv.getItemInMainHand().getAmount()) +  "' WHERE Seller='" + player.getUniqueId().toString() + "' AND ItemName='" + inv.getItemInMainHand().getType().toString() + "' AND ItemMetadata='" + inv.getItemInMainHand().getDurability() + "';");
					res.close();
					try {
						ResultSet rs = sql.query("SELECT * FROM Selling WHERE Seller='" + player.getUniqueId().toString() + "' AND ItemName='" + inv.getItemInMainHand().getType().toString() + "' AND ItemMetadata='" + inv.getItemInMainHand().getDurability() + "';");
						player.sendMessage(ChatColor.GREEN + "Your listing of " + ChatColor.AQUA + Items.itemByType(inv.getItemInMainHand().getType(), inv.getItemInMainHand().getDurability()).getName().toString() + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format(rs.getDouble("Price")) + ChatColor.GREEN + " now has " + ChatColor.AQUA + rs.getInt("ItemAmount") + ChatColor.GREEN + " items." );
						inv.setItem(inv.getHeldItemSlot(), null);
						return true;
					} catch (SQLException e) {
						e.printStackTrace();
						player.sendMessage(ChatColor.RED + "An SQL error occured!");
						return true;
					}					
				} catch (NumberFormatException e) {
					e.printStackTrace();
					player.sendMessage(ChatColor.RED + "An error occurred with some numbers.");
				} catch (SQLException e) {
					try {
						sql.query("INSERT INTO Selling (ItemName, ItemMetadata, ItemAmount, Seller, Price) VALUES ('" + inv.getItemInMainHand().getType().toString() + "','" + inv.getItemInMainHand().getDurability() + "','" + inv.getItemInMainHand().getAmount() + "','" + player.getUniqueId().toString() + "','" + Float.parseFloat(args[1]) + "');");
					} catch (NumberFormatException e1) {
						e1.printStackTrace();
						player.sendMessage(ChatColor.RED + "An error occured with some numbers.");
						return false;
					} catch (SQLException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
						player.sendMessage(ChatColor.RED + "An SQL error occurred!");
						return true;
					}
				}
				player.sendMessage(ChatColor.GREEN + "You sold " + ChatColor.AQUA + inv.getItemInMainHand().getAmount() + " " + Items.itemByType(inv.getItemInMainHand().getType(), inv.getItemInMainHand().getDurability()).toString() + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format(Double.parseDouble(args[1])) + ChatColor.GREEN + ".");				
				inv.setItem(inv.getHeldItemSlot(), null);
				return true;
			} else {
				//attempts to update a previous listing of the item, if that fails, it inserts it into a new row.
				try {
					Items.itemByName(args[0]).getName().toString();
				} catch (NullPointerException e) {
					sender.sendMessage(ChatColor.RED + "That item name wasn't recognised.");
					return false;
				}
				try {
					if(Items.itemByName(args[0]).isDurable()) {
						player.sendMessage(ChatColor.RED + "You are not able to bulk-sell items that have a durability value. Sell them with /sell hand instead.");
						return true;
					}
					if (Items.itemByName(args[0]).getType() == Material.AIR) {
						player.sendMessage(ChatColor.RED + "You can't sell air! What sort of server do you think this is?");
						return true;
					}
					ResultSet res = sql.query("SELECT * FROM Selling WHERE Seller='" + player.getUniqueId().toString() + "' AND ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).getSubTypeId() + "';");
					for (int pos = 0; pos < inv.getContents().length; pos++) {
						if (inv.getContents()[pos] != null) {
							if (inv.getContents()[pos].getType().toString().equals(Items.itemByName(args[0]).getType().toString()) && (inv.getContents()[pos].getDurability() == Items.itemByName(args[0]).getSubTypeId())) {
								pitemamt = pitemamt + inv.getContents()[pos].getAmount();
							}
						}
					}
					if (pitemamt == 0) {
						player.sendMessage(ChatColor.RED + "You don't have any " + Items.itemByName(args[0]).getName().toString() + "s.");
						return true;
					}
					sql.query("UPDATE Selling SET ItemAmount='" + (res.getInt("ItemAmount") + pitemamt) + "' WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).getSubTypeId() + "' AND Seller='" + player.getUniqueId().toString() + "';");
					res.close();
					try {
						ResultSet rs = sql.query("SELECT * FROM Selling WHERE Seller='" + player.getUniqueId().toString() + "' AND ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).getSubTypeId() + "';");
						player.sendMessage(ChatColor.GREEN + "Your listing of " + ChatColor.AQUA + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format(rs.getDouble("Price")) + ChatColor.GREEN + " now has " + ChatColor.AQUA + rs.getInt("ItemAmount") + ChatColor.GREEN + " items." );
						ItemStack is = new ItemStack(Items.itemByName(args[0]).getType(), pitemamt);
						is.setDurability(Items.itemByName(args[0]).getSubTypeId());
						inv.removeItem(is);
						return true;
					} catch (SQLException e) {
						e.printStackTrace();
						player.sendMessage(ChatColor.RED + "An SQL error occured!");
						return true;
					}					
				} catch (NumberFormatException e) {
					e.printStackTrace();
					player.sendMessage(ChatColor.RED + "An error occurred with some numbers.");
				} catch (SQLException e) {
					try {
						sql.query("INSERT INTO Selling (ItemName, ItemMetadata, ItemAmount, Seller, Price) VALUES ('" + Items.itemByName(args[0]).getType().toString() + "','" + Items.itemByName(args[0]).getSubTypeId() + "','" + pitemamt + "','" + player.getUniqueId().toString() + "','" + Float.parseFloat(args[1]) + "');");
					} catch (NumberFormatException e1) {
						e1.printStackTrace();
						player.sendMessage(ChatColor.RED + "An error occured with some numbers.");
						return false;
					} catch (SQLException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
						player.sendMessage(ChatColor.RED + "An SQL error occurred!");
						return true;
					}
				}
				player.sendMessage(ChatColor.GREEN + "You sold " + ChatColor.AQUA + pitemamt + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format(Double.parseDouble(args[1])) + ChatColor.GREEN + ".");				
				ItemStack is = new ItemStack(Items.itemByName(args[0]).getType(), pitemamt);
				is.setDurability(Items.itemByName(args[0]).getSubTypeId());
				inv.removeItem(is);
				return true;
				
			}
		}
		
		if (cmd.getName().equalsIgnoreCase("cancel") && sender.hasPermission("vshop.cancel")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage(ChatColor.RED + "Senders without inventories cannot cancel items into their inventories, try using an admin tool instead.");
				return true;
			}
			Player player = (Player) sender;
			PlayerInventory inv = player.getInventory();
			if (args.length < 2) {
				return false;
			}
			try {
				Items.itemByName(args[0]).getName().toString();
			} catch (NullPointerException e) {
				sender.sendMessage(ChatColor.RED + "That item name wasn't recognised.");
				return false;
			}
			if (args[1].equalsIgnoreCase("all")) {
				try {
					ResultSet res = sql.query("SELECT * FROM Selling WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "' AND Seller='" + player.getUniqueId().toString() + "';");
					sql.query("DELETE FROM Selling WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + player.getUniqueId().toString() + "';");
					ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), res.getInt("ItemAmount"));
					pris.setDurability(res.getShort("ItemMetadata"));
					HashMap<Integer, ItemStack> nope = inv.addItem(pris);
					for(Entry<Integer, ItemStack> entry : nope.entrySet()) {					    	
						int amtLeft = entry.getValue().getAmount();
					   	while (amtLeft > 0) {
					   		if (amtLeft < entry.getValue().getMaxStackSize()) {
					   			entry.getValue().setAmount(amtLeft);
					   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
					   			amtLeft = 0;
					   		} else {
					   			entry.getValue().setAmount(entry.getValue().getMaxStackSize());
					   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
					   			amtLeft = amtLeft - entry.getValue().getMaxStackSize();
					   		}
					    }        
					}
					player.sendMessage(ChatColor.GREEN + "You cancelled " + ChatColor.AQUA + res.getInt("ItemAmount") + ChatColor.GREEN + " items.");
					res.close();
					return true;
				} catch (SQLException e) {
					if (e.getMessage().contains("closed")) {
						player.sendMessage(ChatColor.RED + "You must not be selling that item.");
						return true;
					} else {
						player.sendMessage(ChatColor.RED + "An SQL error occurred.");
						e.printStackTrace();
						return true;
					}
				}
			} else {
				if (Double.parseDouble(args[1]) < 0) {
					player.sendMessage(ChatColor.RED + "You can't use negative numbers!");
					return true;
				}
				try {
					ResultSet res = sql.query("SELECT * FROM Selling WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "' AND Seller='" + player.getUniqueId().toString() + "';");
					if (Integer.parseInt(args[1]) > res.getInt("ItemAmount")) {
						player.sendMessage(ChatColor.RED + "You're attempting to cancel too many items. Your listing only has " + res.getInt("ItemAmount") + " items.");
						return true;
					}
					if ((Integer.parseInt(args[1]) - res.getInt("ItemAmount")) == 0) {
						sql.query("DELETE FROM Selling WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + player.getUniqueId().toString() + "';");
						ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), Integer.parseInt(args[1]));
						pris.setDurability(res.getShort("ItemMetadata"));
					    HashMap<Integer, ItemStack> nope = inv.addItem(pris);
					    for(Entry<Integer, ItemStack> entry : nope.entrySet()) {					    	
							int amtLeft = entry.getValue().getAmount();
						   	while (amtLeft > 0) {
						   		if (amtLeft < entry.getValue().getMaxStackSize()) {
						   			entry.getValue().setAmount(amtLeft);
						   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
						   			amtLeft = 0;
						   		} else {
						   			entry.getValue().setAmount(entry.getValue().getMaxStackSize());
						   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
						   			amtLeft = amtLeft - entry.getValue().getMaxStackSize();
						   		}
						    }        
						}
						player.sendMessage(ChatColor.GREEN + "You cancelled all of the items in that listing.");
						res.close();
						return true;
					}
					else {
						sql.query("UPDATE Selling SET ItemAmount ='" + (res.getInt("ItemAmount") - Integer.parseInt(args[1])) + "' WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + player.getUniqueId().toString() + "';");
						ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), Integer.parseInt(args[1]));
						pris.setDurability(res.getShort("ItemMetadata"));
						HashMap<Integer, ItemStack> nope = inv.addItem(pris);
						for(Entry<Integer, ItemStack> entry : nope.entrySet()) {					    	
							int amtLeft = entry.getValue().getAmount();
						   	while (amtLeft > 0) {
						   		if (amtLeft < entry.getValue().getMaxStackSize()) {
						   			entry.getValue().setAmount(amtLeft);
						   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
						   			amtLeft = 0;
						   		} else {
						   			entry.getValue().setAmount(entry.getValue().getMaxStackSize());
						   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
						   			amtLeft = amtLeft - entry.getValue().getMaxStackSize();
						   		}
						    }        
						}
						player.sendMessage(ChatColor.GREEN + "You cancelled " + ChatColor.AQUA + args[1] + ChatColor.GREEN + ". Amount remaining: " + ChatColor.AQUA + (res.getInt("ItemAmount") - Integer.parseInt(args[1])) + ChatColor.GREEN + ".");
						res.close();
						return true;
					}
				} catch (SQLException e) {
					if (e.getMessage().contains("closed")) {
						player.sendMessage(ChatColor.RED + "You must not be selling that item.");
						return true;
					} else {
						player.sendMessage(ChatColor.RED + "An SQL error occurred.");
						e.printStackTrace();
						return true;
					}
				}
			}
		}
		
		if (cmd.getName().equalsIgnoreCase("buy") && sender.hasPermission("vshop.buy")) {			
			if (!(sender instanceof Player)) {
				sender.sendMessage(ChatColor.RED + "Senders without inventories cannot buy, try using an admin tool instead.");
				return true;
			}
			Player player = (Player) sender;
			PlayerInventory inv = player.getInventory();
			if (args.length < 2) {
				return false;
			}
			try {
				Items.itemByName(args[0]).getName().toString();
			} catch (NullPointerException e) {
				sender.sendMessage(ChatColor.RED + "That item name wasn't recognised.");
				return false;
			}
			try {
				if (Double.parseDouble(args[1]) < 0) {
					player.sendMessage(ChatColor.RED + "You can't use negative numbers!");
					return true;
				}
			} catch (NumberFormatException e) {
				player.sendMessage(ChatColor.RED + "That wasn't a number!");
				return false;
			}
			try {
				//first, checks for listings from the player
				ResultSet res = sql.query("SELECT * FROM Selling WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "' AND Seller='" + player.getUniqueId().toString() + "' ORDER BY Price ASC;");
                Integer amtRequested = Integer.parseInt(args[1]);
                Integer amtFulfilled = 0;
                while(res.next()) {
                	if (res.getInt("ItemAmount") == (amtRequested - amtFulfilled)) {	        
						sql.query("DELETE FROM Selling WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + res.getString("Seller") + "';");						
						//Gives the player the item
						ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), (amtRequested - amtFulfilled));
						pris.setDurability(res.getShort("ItemMetadata"));
						HashMap<Integer, ItemStack> nope = inv.addItem(pris);
						for(Entry<Integer, ItemStack> entry : nope.entrySet()) {					    	
							int amtLeft = entry.getValue().getAmount();
						   	while (amtLeft > 0) {
						   		if (amtLeft < entry.getValue().getMaxStackSize()) {
						   			entry.getValue().setAmount(amtLeft);
						   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
						   			amtLeft = 0;
						   		} else {
						   			entry.getValue().setAmount(entry.getValue().getMaxStackSize());
						   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
						   			amtLeft = amtLeft - entry.getValue().getMaxStackSize();
						   		}
						    }        
						}
					    player.sendMessage(ChatColor.GREEN + "You cancelled " + ChatColor.AQUA + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from your listing for " + ChatColor.AQUA + "free" + ChatColor.GREEN + ". Items left: " + ChatColor.AQUA + "0" + ChatColor.GREEN + ".");
						amtFulfilled = amtRequested;
            		
            	} else if (res.getInt("ItemAmount") > amtRequested) {	          
						sql.query("UPDATE Selling SET ItemAmount ='" + (res.getInt("ItemAmount") - (amtRequested - amtFulfilled)) + "' WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + res.getString("Seller") + "';");						
						//Gives the player the item
						ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), (amtRequested - amtFulfilled));
						pris.setDurability(res.getShort("ItemMetadata"));
						HashMap<Integer, ItemStack> nope = inv.addItem(pris);
						for(Entry<Integer, ItemStack> entry : nope.entrySet()) {					    	
							int amtLeft = entry.getValue().getAmount();
						   	while (amtLeft > 0) {
						   		if (amtLeft < entry.getValue().getMaxStackSize()) {
						   			entry.getValue().setAmount(amtLeft);
						   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
						   			amtLeft = 0;
						   		} else {
						   			entry.getValue().setAmount(entry.getValue().getMaxStackSize());
						   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
						   			amtLeft = amtLeft - entry.getValue().getMaxStackSize();
						   		}
						    }        
						}
						player.sendMessage(ChatColor.GREEN + "You cancelled " + ChatColor.AQUA + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from your listing for " + ChatColor.AQUA + "free" + ChatColor.GREEN + ". Items left: " + ChatColor.AQUA + (res.getInt("ItemAmount") - (amtRequested - amtFulfilled)) + ChatColor.GREEN + ".");							
            		    amtFulfilled = amtRequested;
            		
            	} else if (res.getInt("ItemAmount") < amtRequested) {
						amtFulfilled += res.getInt("ItemAmount");
						sql.query("DELETE FROM Selling WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + res.getString("Seller") + "';");						
						//Gives the player the item
						ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), res.getInt("ItemAmount"));
						pris.setDurability(res.getShort("ItemMetadata"));
						HashMap<Integer, ItemStack> nope = inv.addItem(pris);
						for(Entry<Integer, ItemStack> entry : nope.entrySet()) {					    	
							int amtLeft = entry.getValue().getAmount();
						   	while (amtLeft > 0) {
						   		if (amtLeft < entry.getValue().getMaxStackSize()) {
						   			entry.getValue().setAmount(amtLeft);
						   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
						   			amtLeft = 0;
						   		} else {
						   			entry.getValue().setAmount(entry.getValue().getMaxStackSize());
						   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
						   			amtLeft = amtLeft - entry.getValue().getMaxStackSize();
						   		}
						    }        
						}
					   player.sendMessage(ChatColor.GREEN + "You cancelled " + ChatColor.AQUA + res.getInt("ItemAmount") + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from your listing for " + ChatColor.AQUA + "free" + ChatColor.GREEN + ". Items left: " + ChatColor.AQUA + "0" + ChatColor.GREEN + ".");
            		}
                }
                if (amtFulfilled == amtRequested) {
                	player.sendMessage(ChatColor.GREEN + "Fulfilled all the items you requested using items from your own listing.");
                	return true;
            	}
                //then checks other player's listings
                res = sql.query("SELECT * FROM Selling WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "' ORDER BY Price ASC;");
                while (res.next()) {
                	if (res.getBoolean("Infinite")) {
                		if (eco.has(player, (res.getDouble("Price") * (amtRequested - amtFulfilled)))) {
							eco.withdrawPlayer(player, (res.getDouble("Price") * (amtRequested - amtFulfilled)));
							if (config.getBoolean("useServerAccount")) {
								eco.depositPlayer(config.getString("serverAccountName"), (res.getDouble("Price") * (amtRequested - amtFulfilled)));
							}
							ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), (amtRequested - amtFulfilled));
							pris.setDurability(res.getShort("ItemMetadata"));
							HashMap<Integer, ItemStack> nope = inv.addItem(pris);
							for(Entry<Integer, ItemStack> entry : nope.entrySet()) {					    	
								int amtLeft = entry.getValue().getAmount();
							   	while (amtLeft > 0) {
							   		if (amtLeft < entry.getValue().getMaxStackSize()) {
							   			entry.getValue().setAmount(amtLeft);
							   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
							   			amtLeft = 0;
							   		} else {
							   			entry.getValue().setAmount(entry.getValue().getMaxStackSize());
							   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
							   			amtLeft = amtLeft - entry.getValue().getMaxStackSize();
							   		}
							    }        
							}
							player.sendMessage(ChatColor.GREEN + "You bought " + ChatColor.AQUA + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from " + ChatColor.DARK_GREEN + "Server" + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * (amtRequested - amtFulfilled))) + ChatColor.GREEN + ".");
							this.getLogger().info(player.getName().toString() + " bought " + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + " from " + "Server" + " for " + eco.format((res.getDouble("Price") * (amtRequested - amtFulfilled))) + ".");
							sql.query("INSERT INTO Transactions (ItemName, ItemMetadata, Seller, Purchaser, Price, AmountPurchased) VALUES ('" + Items.itemByName(args[0]).getType().toString() +"','" + Items.itemByName(args[0]).toStack().getDurability() + "','Server','" + player.getUniqueId().toString() + "','" + res.getDouble("Price") + "','" + (amtRequested - amtFulfilled) + "');");
						} else {
							player.sendMessage(ChatColor.RED + "You don't have enough money.");
							return true;
						}
                		amtFulfilled = amtRequested;
                		
                	} else if (res.getInt("ItemAmount") == (amtRequested - amtFulfilled)) {
                		if (eco.has(player, (res.getDouble("Price") * (amtRequested - amtFulfilled)))) {
							eco.withdrawPlayer(player, (res.getDouble("Price") * (amtRequested - amtFulfilled)));
							if (config.getBoolean("taxEnabled")) {
								if (config.getBoolean("useServerAccount")) {
									eco.depositPlayer(this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))), ((res.getDouble("Price") * (amtRequested - amtFulfilled) * (1 - config.getDouble("taxPercentAsDecimal")))));
									eco.depositPlayer(config.getString("serverAccountName"), ((res.getDouble("Price") * (amtRequested - amtFulfilled) * config.getDouble("taxPercent"))));
								} else {
									eco.depositPlayer(this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))), ((res.getDouble("Price") * (amtRequested - amtFulfilled) * (1 - config.getDouble("taxPercentAsDecimal")))));
								}
							} else {
								eco.depositPlayer(this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))), ((res.getDouble("Price") * (amtRequested - amtFulfilled))));
							}
							sql.query("DELETE FROM Selling WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + res.getString("Seller") + "';");						
							//Gives the player the item
							ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), (amtRequested - amtFulfilled));
							pris.setDurability(res.getShort("ItemMetadata"));
							HashMap<Integer, ItemStack> nope = inv.addItem(pris);
							for(Entry<Integer, ItemStack> entry : nope.entrySet()) {					    	
								int amtLeft = entry.getValue().getAmount();
							   	while (amtLeft > 0) {
							   		if (amtLeft < entry.getValue().getMaxStackSize()) {
							   			entry.getValue().setAmount(amtLeft);
							   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
							   			amtLeft = 0;
							   		} else {
							   			entry.getValue().setAmount(entry.getValue().getMaxStackSize());
							   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
							   			amtLeft = amtLeft - entry.getValue().getMaxStackSize();
							   		}
							    }        
							}
							player.sendMessage(ChatColor.GREEN + "You bought " + ChatColor.AQUA + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from " + ChatColor.DARK_GREEN + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * (amtRequested - amtFulfilled))) + ChatColor.GREEN + ".");
							if (this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).isOnline()){
								if (config.getBoolean("taxEnabled")) {
									this.getServer().getPlayer(UUID.fromString(res.getString("Seller"))).sendMessage(ChatColor.DARK_GREEN + player.getName() + ChatColor.GREEN + " bought " + ChatColor.AQUA + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from your listing for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * (amtRequested - amtFulfilled))) + ChatColor.GREEN + ". After Tax: " + ChatColor.AQUA + eco.format((1 - config.getDouble("taxPercentAsDecimal")) * (res.getDouble("Price") * (amtRequested - amtFulfilled))) + ChatColor.GREEN + ". Items left: " + ChatColor.AQUA + "0" + ChatColor.GREEN + ".");
								} else {
									this.getServer().getPlayer(UUID.fromString(res.getString("Seller"))).sendMessage(ChatColor.DARK_GREEN + player.getName() + ChatColor.GREEN + " bought " + ChatColor.AQUA + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from your listing for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * (amtRequested - amtFulfilled))) + ChatColor.GREEN + ". Items left: " + ChatColor.AQUA + "0" + ChatColor.GREEN + ".");
								}
							}
							this.getLogger().info(player.getName().toString() + " bought " + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + " from " + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + " for " + eco.format((res.getDouble("Price") * (amtRequested - amtFulfilled))) + ".");
							sql.query("INSERT INTO Transactions (ItemName, ItemMetadata, Seller, Purchaser, Price, AmountPurchased) VALUES ('" + Items.itemByName(args[0]).getType().toString() +"','" + Items.itemByName(args[0]).toStack().getDurability() + "','" + res.getString("Seller") + "','" + player.getUniqueId().toString() + "','" + res.getDouble("Price") + "','" + (amtRequested - amtFulfilled) + "');");
						} else {
							player.sendMessage(ChatColor.RED + "You don't have enough money for that.");
							return true;
						}
                		amtFulfilled = amtRequested;
                		
                	} else if (res.getInt("ItemAmount") > amtRequested) {
                		if (eco.has(player, (res.getDouble("Price") * (amtRequested - amtFulfilled)))) {
							eco.withdrawPlayer(player, (res.getDouble("Price") * (amtRequested - amtFulfilled)));
							if (config.getBoolean("taxEnabled")) {
								if (config.getBoolean("useServerAccount")) {
									eco.depositPlayer(this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))), ((res.getDouble("Price") * (amtRequested - amtFulfilled) * (1 - config.getDouble("taxPercentAsDecimal")))));
									eco.depositPlayer(config.getString("serverAccountName"), ((res.getDouble("Price") * (amtRequested - amtFulfilled) * config.getDouble("taxPercent"))));
								} else {
									eco.depositPlayer(this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))), ((res.getDouble("Price") * (amtRequested - amtFulfilled) * (1 - config.getDouble("taxPercentAsDecimal")))));
								}
							} else {
								eco.depositPlayer(this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))), ((res.getDouble("Price") * (amtRequested - amtFulfilled))));
							}
							sql.query("UPDATE Selling SET ItemAmount ='" + (res.getInt("ItemAmount") - (amtRequested - amtFulfilled)) + "' WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + res.getString("Seller") + "';");						
							//Gives the player the item
							ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), (amtRequested - amtFulfilled));
							pris.setDurability(res.getShort("ItemMetadata"));
							HashMap<Integer, ItemStack> nope = inv.addItem(pris);
							for(Entry<Integer, ItemStack> entry : nope.entrySet()) {					    	
								int amtLeft = entry.getValue().getAmount();
							   	while (amtLeft > 0) {
							   		if (amtLeft < entry.getValue().getMaxStackSize()) {
							   			entry.getValue().setAmount(amtLeft);
							   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
							   			amtLeft = 0;
							   		} else {
							   			entry.getValue().setAmount(entry.getValue().getMaxStackSize());
							   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
							   			amtLeft = amtLeft - entry.getValue().getMaxStackSize();
							   		}
							    }        
							}
							player.sendMessage(ChatColor.GREEN + "You bought " + ChatColor.AQUA + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from " + ChatColor.DARK_GREEN + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * (amtRequested - amtFulfilled))) + ChatColor.GREEN + ".");
							if (this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).isOnline()){
								if (config.getBoolean("taxEnabled")) {
									this.getServer().getPlayer(UUID.fromString(res.getString("Seller"))).sendMessage(ChatColor.DARK_GREEN + player.getName() + ChatColor.GREEN + " bought " + ChatColor.AQUA + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from your listing for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * (amtRequested - amtFulfilled))) + ChatColor.GREEN + ". After Tax: " + ChatColor.AQUA + eco.format((1 - config.getDouble("taxPercentAsDecimal")) * (res.getDouble("Price") * (amtRequested - amtFulfilled))) + ChatColor.GREEN + ". Items left: " + ChatColor.AQUA + (res.getInt("ItemAmount") - (amtRequested - amtFulfilled)) + ChatColor.GREEN + ".");
								} else {
									this.getServer().getPlayer(UUID.fromString(res.getString("Seller"))).sendMessage(ChatColor.DARK_GREEN + player.getName() + ChatColor.GREEN + " bought " + ChatColor.AQUA + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from your listing for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * (amtRequested - amtFulfilled))) + ChatColor.GREEN + ". Items left: " + ChatColor.AQUA + (res.getInt("ItemAmount") - (amtRequested - amtFulfilled)) + ChatColor.GREEN + ".");
								}
							}
							this.getLogger().info(player.getName().toString() + " bought " + (amtRequested - amtFulfilled) + " " + Items.itemByName(args[0]).getName().toString() + " from " + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + " for " + eco.format((res.getDouble("Price") * (amtRequested - amtFulfilled))) + ".");
							sql.query("INSERT INTO Transactions (ItemName, ItemMetadata, Seller, Purchaser, Price, AmountPurchased) VALUES ('" + Items.itemByName(args[0]).getType().toString() +"','" + Items.itemByName(args[0]).toStack().getDurability() + "','" + res.getString("Seller") + "','" + player.getUniqueId().toString() + "','" + res.getDouble("Price") + "','" + (amtRequested - amtFulfilled) + "');");
						} else {
							player.sendMessage(ChatColor.RED + "You don't have enough money for that.");
							return true;
						}
                		amtFulfilled = amtRequested;
                		
                	} else if (res.getInt("ItemAmount") < amtRequested) {
                		if (eco.has(player, (res.getDouble("Price") * res.getInt("ItemAmount")))) {
							eco.withdrawPlayer(player, (res.getDouble("Price") * res.getInt("ItemAmount")));
							if (config.getBoolean("taxEnabled")) {
								if (config.getBoolean("useServerAccount")) {
									eco.depositPlayer(this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))), ((res.getDouble("Price") * res.getInt("ItemAmount") * (1 - config.getDouble("taxPercentAsDecimal")))));
									eco.depositPlayer(config.getString("serverAccountName"), ((res.getDouble("Price") * res.getInt("ItemAmount") * config.getDouble("taxPercent"))));
								} else {
									eco.depositPlayer(this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))), ((res.getDouble("Price") * res.getInt("ItemAmount") * (1 - config.getDouble("taxPercentAsDecimal")))));
								}
							} else {
								eco.depositPlayer(this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))), ((res.getDouble("Price") * res.getInt("ItemAmount"))));
							}							
							sql.query("DELETE FROM Selling WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + res.getString("Seller") + "';");						
							//Gives the player the item
							ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), res.getInt("ItemAmount"));
							pris.setDurability(res.getShort("ItemMetadata"));
							HashMap<Integer, ItemStack> nope = inv.addItem(pris);
							for(Entry<Integer, ItemStack> entry : nope.entrySet()) {					    	
								int amtLeft = entry.getValue().getAmount();
							   	while (amtLeft > 0) {
							   		if (amtLeft < entry.getValue().getMaxStackSize()) {
							   			entry.getValue().setAmount(amtLeft);
							   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
							   			amtLeft = 0;
							   		} else {
							   			entry.getValue().setAmount(entry.getValue().getMaxStackSize());
							   			player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
							   			amtLeft = amtLeft - entry.getValue().getMaxStackSize();
							   		}
							    }        
							}
							player.sendMessage(ChatColor.GREEN + "You bought " + ChatColor.AQUA + res.getInt("ItemAmount") + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from " + ChatColor.DARK_GREEN + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * res.getInt("ItemAmount"))) + ChatColor.GREEN + ".");
							if (this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).isOnline()){
								if (config.getBoolean("taxEnabled")) {
									this.getServer().getPlayer(UUID.fromString(res.getString("Seller"))).sendMessage(ChatColor.DARK_GREEN + player.getName() + ChatColor.GREEN + " bought " + ChatColor.AQUA + res.getInt("ItemAmount") + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from your listing for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * res.getInt("ItemAmount"))) + ChatColor.GREEN + ". After Tax: " + ChatColor.AQUA + eco.format((1 - config.getDouble("taxPercentAsDecimal")) * (res.getDouble("Price") * res.getInt("ItemAmount"))) + ChatColor.GREEN + ". Items left: " + ChatColor.AQUA + "0" + ChatColor.GREEN + ".");
								} else {
									this.getServer().getPlayer(UUID.fromString(res.getString("Seller"))).sendMessage(ChatColor.DARK_GREEN + player.getName() + ChatColor.GREEN + " bought " + ChatColor.AQUA + res.getInt("ItemAmount") + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from your listing for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * res.getInt("ItemAmount"))) + ChatColor.GREEN + ". Items left: " + ChatColor.AQUA + "0" + ChatColor.GREEN + ".");
								}
							}
							this.getLogger().info(player.getName().toString() + " bought " + res.getInt("ItemAmount") + " " + Items.itemByName(args[0]).getName().toString() + " from " + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + " for " + eco.format((res.getDouble("Price") * res.getInt("ItemAmount"))) + ".");
							sql.query("INSERT INTO Transactions (ItemName, ItemMetadata, Seller, Purchaser, Price, AmountPurchased) VALUES ('" + Items.itemByName(args[0]).getType().toString() +"','" + Items.itemByName(args[0]).toStack().getDurability() + "','" + res.getString("Seller") + "','" + player.getUniqueId().toString() + "','" + res.getDouble("Price") + "','" + res.getInt("ItemAmount") + "');");
							amtFulfilled += res.getInt("ItemAmount");
						} else {
							player.sendMessage(ChatColor.RED + "You don't have enough money for that.");
							return true;
						}
                		amtFulfilled += res.getInt("ItemAmount");
                	}
                	
                	if (amtFulfilled == amtRequested) {
                		break;
                	}
                }
                if (amtFulfilled == amtRequested) {
                	player.sendMessage(ChatColor.GREEN + "Fulfilled all the items you requested.");
            	} else {
            		player.sendMessage(ChatColor.GREEN + "Fulfilled " + ChatColor.AQUA + amtFulfilled + ChatColor.GREEN + " items out of the " + ChatColor.AQUA + amtRequested + ChatColor.GREEN + " items you requested.");	
            	}
                res.close();
                return true;
			} catch (SQLException e) {
				if (e.getMessage().contains("closed")) {
					player.sendMessage(ChatColor.RED + "No one is selling that item.");
					return true;
				} else {
				e.printStackTrace();
				player.sendMessage(ChatColor.RED + "An SQL error occured!");
				return true;
				}
			}
		}
		
		if (cmd.getName().equalsIgnoreCase("search") && sender.hasPermission("vshop.search")) {
			if (args.length < 1) {
				return false;
			}
			try {
				Items.itemByName(args[0]).getName().toString();
			} catch (NullPointerException e) {
				sender.sendMessage(ChatColor.RED + "That item name wasn't recognised.");
				return false;
			}
			try {
				ResultSet res;
				Integer curPage;
				if (args.length < 2) {
					curPage = 1;
					res = sql.query("SELECT * FROM Selling WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "' ORDER BY Price ASC LIMIT 6;");
				} else {
					curPage = Integer.parseInt(args[1]);
					res = sql.query("SELECT * FROM Selling WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "' ORDER BY Price ASC LIMIT 6 OFFSET " + ((curPage - 1) * 6) + ";");
				}
				sender.sendMessage(ChatColor.GREEN + "Listings of " + ChatColor.AQUA + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + ".");
				while (res.next()) {
					if (res.getBoolean("Infinite")) {
						sender.sendMessage(ChatColor.DARK_GREEN + "Server" + ChatColor.GREEN + ": " + ChatColor.AQUA + eco.format(res.getDouble("Price")) + ChatColor.GREEN + " x " + ChatColor.AQUA + "Infinite" + ChatColor.GREEN + " ID:" + res.getInt("SellingID"));
					} else {
						sender.sendMessage(ChatColor.DARK_GREEN + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + eco.format(res.getDouble("Price")) + ChatColor.GREEN + " x " + ChatColor.AQUA + res.getInt("ItemAmount")  + ChatColor.GREEN + " ID:" + res.getInt("SellingID"));
					}					
				}
				res = sql.query("SELECT COUNT(*) AS totalPages FROM Selling WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "';");
				if ((res.getInt("totalPages") % 6) > 0) {
					sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + ((res.getInt("totalPages") / 6) + 1));
				} else {
					sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + (res.getInt("totalPages") / 6));
				}
				res.close();
				return true;
			} catch (SQLException e) {
				if (e.getMessage().contains("closed")) {
					sender.sendMessage(ChatColor.RED + "No one is selling that item.");
				} else {
					e.printStackTrace();
				}
			}
			
		}
		
		if (cmd.getName().equalsIgnoreCase("stock") && sender.hasPermission("vshop.stock")) {
			if (args.length < 1) {
				try {
					if (!(sender instanceof Player)) {
						sender.sendMessage(ChatColor.RED + "You don't have a UUID.");
						return false;
					}
					Player player = (Player) sender;
					ResultSet res;
					Integer curPage;
					curPage = 1;
					res = sql.query("SELECT * FROM Selling WHERE Seller='" + player.getUniqueId().toString() +  "' ORDER BY SellingID DESC LIMIT 6;");
					sender.sendMessage(ChatColor.GREEN + "Your listings.");
					while (res.next()) {
						sender.sendMessage(ChatColor.AQUA + Items.itemByType(Material.getMaterial(res.getString("ItemName")), res.getShort("ItemMetadata")).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + eco.format(res.getDouble("Price")) + ChatColor.GREEN + " x " + ChatColor.AQUA + res.getInt("ItemAmount") + ChatColor.GREEN + " ID:" + res.getInt("SellingID"));
					}
					res = sql.query("SELECT COUNT(*) AS totalPages FROM Selling WHERE Seller='" + player.getUniqueId().toString() +  "';");
					if ((res.getInt("totalPages") % 6) > 0) {
						sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + ((res.getInt("totalPages") / 6) + 1));
					} else {
						sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + (res.getInt("totalPages") / 6));
					}
					res.close();
					return true;
				} catch (SQLException e) {
					if (e.getMessage().contains("closed")) {
						sender.sendMessage(ChatColor.RED + "You must not be selling anything.");
						return true;
					} else {
						e.printStackTrace();
						return true;
					}
				}
			}
			try {
				if (args[0].equalsIgnoreCase("Server")) {
					ResultSet res;
					Integer curPage;
					if (args.length < 2) {
						curPage = 1;
						res = sql.query("SELECT * FROM Selling WHERE Seller='Server' ORDER BY SellingID DESC LIMIT 6;");
					} else {
						curPage = Integer.parseInt(args[1]);
						res = sql.query("SELECT * FROM Selling WHERE Seller='Server' ORDER BY SellingID DESC LIMIT 6 OFFSET " + ((curPage - 1) * 6) + ";");
					}
					sender.sendMessage(ChatColor.DARK_GREEN + "Server's" + ChatColor.GREEN +  " listings.");
					while (res.next()) {
						sender.sendMessage(ChatColor.AQUA + Items.itemByType(Material.getMaterial(res.getString("ItemName")), res.getShort("ItemMetadata")).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + eco.format(res.getDouble("Price")) + ChatColor.GREEN + " x " + ChatColor.AQUA + "Infinite" + ChatColor.GREEN + " ID:" + res.getInt("SellingID"));
					}
					res = sql.query("SELECT COUNT(*) AS totalPages FROM Selling WHERE Seller='Server';");
					if ((res.getInt("totalPages") % 6) > 0) {
						sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + ((res.getInt("totalPages") / 6) + 1));
					} else {
						sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + (res.getInt("totalPages") / 6));
					}
					res.close();
					return true;
				}
				ResultSet res;
				Integer curPage;
				if (args.length < 2) {
					curPage = 1;
					res = sql.query("SELECT * FROM Selling WHERE Seller='" + this.getServer().getOfflinePlayer(args[0]).getUniqueId().toString() + "' ORDER BY SellingID DESC LIMIT 6;");
				} else {
					curPage = Integer.parseInt(args[1]);
					res = sql.query("SELECT * FROM Selling WHERE Seller='" + this.getServer().getOfflinePlayer(args[0]).getUniqueId().toString() + "' ORDER BY SellingID DESC LIMIT 6 OFFSET " + ((curPage - 1) * 6) + ";");
				}
				sender.sendMessage(ChatColor.DARK_GREEN + args[0].toString() + "'s" + ChatColor.GREEN +  " listings.");
				while (res.next()) {
					sender.sendMessage(ChatColor.AQUA + Items.itemByType(Material.getMaterial(res.getString("ItemName")), res.getShort("ItemMetadata")).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + eco.format(res.getDouble("Price")) + ChatColor.GREEN + " x " + ChatColor.AQUA + res.getInt("ItemAmount") + ChatColor.GREEN + " ID:" + res.getInt("SellingID"));
				}
				res = sql.query("SELECT COUNT(*) AS totalPages FROM Selling WHERE Seller='" + this.getServer().getOfflinePlayer(args[0]).getUniqueId().toString() + "';");
				if ((res.getInt("totalPages") % 6) > 0) {
					sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + ((res.getInt("totalPages") / 6) + 1));
				} else {
					sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + (res.getInt("totalPages") / 6));
				}
				res.close();
				return true;
			} catch (SQLException e) {
				if (e.getMessage().contains("closed")) {
					sender.sendMessage(ChatColor.RED + "That player must not be selling anything.");
					return true;
				} else {
					e.printStackTrace();
					return true;
				}
			}
		}
		
		if (cmd.getName().equalsIgnoreCase("listings") && sender.hasPermission("vshop.listings")){
			try {
				ResultSet res;
				Integer curPage;
				if (args.length < 1) {
					curPage = 1;
					res = sql.query("SELECT * FROM Selling ORDER BY SellingID DESC LIMIT 6;");
				} else {
					curPage = Integer.parseInt(args[0]);
					res = sql.query("SELECT * FROM Selling ORDER BY SellingID DESC LIMIT 6 OFFSET " + ((curPage - 1) * 6) + ";");
				}
				sender.sendMessage(ChatColor.GREEN + "Listings:");
				while (res.next()) {
					if (res.getBoolean("Infinite")) {
						sender.sendMessage(ChatColor.DARK_GREEN + "Server" + ChatColor.GREEN + ": " + ChatColor.AQUA + Items.itemByType(Material.getMaterial(res.getString("ItemName")), res.getShort("ItemMetadata")).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + eco.format(res.getDouble("Price")) + ChatColor.GREEN + " x " + ChatColor.AQUA + "Infinite" + ChatColor.GREEN + " ID:" + res.getInt("SellingID"));
					} else {
					sender.sendMessage(ChatColor.DARK_GREEN + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + Items.itemByType(Material.getMaterial(res.getString("ItemName")), res.getShort("ItemMetadata")).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + eco.format(res.getDouble("Price")) + ChatColor.GREEN + " x " + ChatColor.AQUA + res.getInt("ItemAmount") + ChatColor.GREEN + " ID:" + res.getInt("SellingID"));
					}
				}
				res = sql.query("SELECT COUNT(*) AS totalPages FROM Selling;");
				if ((res.getInt("totalPages") % 6) > 0) {
					sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + ((res.getInt("totalPages") / 6) + 1));
				} else {
					sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + (res.getInt("totalPages") / 6));
				}
				res.close();
				return true;
			} catch (SQLException e) {
				if (e.getMessage().contains("closed")) {
					sender.sendMessage(ChatColor.RED + "No one must be selling anything.");
				} else {
					sender.sendMessage(ChatColor.RED + "An SQL error occurred.");
					e.printStackTrace();
					return true;
				}
			}
			
		}
		
		if (cmd.getName().equalsIgnoreCase("changeprice") && sender.hasPermission("vshop.changeprice")) {
			if (args.length < 2) {
				return false;
			}
			try {
				Items.itemByName(args[0]).getName().toString();
			} catch (NullPointerException e) {
				sender.sendMessage(ChatColor.RED + "That item name wasn't recognised.");
				return false;
			}
			try {
				Double.parseDouble(args[1]);
			} catch (NumberFormatException e) {
				sender.sendMessage(ChatColor.RED + "That doesn't seem to be a number.");
				return false;
			}
			if (Double.parseDouble(args[1]) < 0) {
				sender.sendMessage(ChatColor.RED + "You can't use negative numbers!");
				return true;
			}
			
			Player player = (Player) sender;
			try {
				ResultSet res = sql.query("SELECT * FROM Selling WHERE Seller='" + player.getUniqueId() + "' AND ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "';");
				sql.query("UPDATE Selling SET Price='" + args[1] + "' WHERE Seller='" + player.getUniqueId() + "' AND ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "';");
				player.sendMessage(ChatColor.GREEN + "Successfully updated your price of " + ChatColor.AQUA + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + ". Its previous price was " + ChatColor.AQUA + eco.format(res.getDouble("Price")) + ChatColor.GREEN + ".");
				return true;
			} catch (SQLException e) {
				if (e.getMessage().contains("closed")) {
					sender.sendMessage(ChatColor.RED + "You must not be selling that item.");
					return true;
				} else {
					sender.sendMessage(ChatColor.RED + "An SQL error occurred.");
					e.printStackTrace();
					return true;
				}
			}
		}
		
		if (cmd.getName().equalsIgnoreCase("transactions") && sender.hasPermission("vshop.transactions")) {
			if (args.length < 1) {
				try {
					if (!(sender instanceof Player)) {
						sender.sendMessage(ChatColor.RED + "You don't have a UUID.");
						return false;
					}
					Player player = (Player) sender;
					ResultSet res;
					Integer curPage;
					curPage = 1;
					res = sql.query("SELECT * FROM Transactions WHERE Seller='" + player.getUniqueId().toString() + "' ORDER BY TransactionID DESC LIMIT 6;");
					sender.sendMessage(ChatColor.GREEN + "Your transactions.");
					while (res.next()) {
						player.sendMessage(ChatColor.DARK_GREEN + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Purchaser"))).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + res.getInt("AmountPurchased") + ChatColor.GREEN + " x " + Items.itemByType(Material.getMaterial(res.getString("ItemName")), res.getShort("ItemMetadata")).getName().toString() + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format((res.getInt("AmountPurchased") * res.getDouble("Price"))) + ChatColor.GREEN + ".");
					}
					res = sql.query("SELECT COUNT(*) AS totalPages FROM Transactions WHERE WHERE Seller='" + player.getUniqueId().toString() + "';");
					if ((res.getInt("totalPages") % 6) > 0) {
						sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + ((res.getInt("totalPages") / 6) + 1));
					} else {
						sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + (res.getInt("totalPages") / 6));
					}
					res.close();
					return true;
				} catch (SQLException e) {
					if (e.getMessage().contains("closed")) {
						sender.sendMessage(ChatColor.RED + "You must not have any transactions.");
						return true;
					} else {
						e.printStackTrace();
						return true;
					}
				}
			}
			try {
				if (args[0].equalsIgnoreCase("Server")) {
					ResultSet res;
					Integer curPage;
					if (args.length < 2) {
						curPage = 1;
						res = sql.query("SELECT * FROM Transactions WHERE Seller='Server' ORDER BY TransactionID DESC LIMIT 6;");
					} else {
						curPage = Integer.parseInt(args[1]);
						res = sql.query("SELECT * FROM Transactions WHERE Seller='Server' ORDER BY TransactionID DESC LIMIT 6 OFFSET " + ((curPage - 1) * 6) + ";");
					}
					sender.sendMessage(ChatColor.DARK_GREEN + "Server's" + ChatColor.GREEN +  " transactions.");
					while (res.next()) {
						sender.sendMessage(ChatColor.DARK_GREEN + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Purchaser"))).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + res.getInt("AmountPurchased") + ChatColor.GREEN + " x " + Items.itemByType(Material.getMaterial(res.getString("ItemName")), res.getShort("ItemMetadata")).getName().toString() + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format((res.getInt("AmountPurchased") * res.getDouble("Price"))) + ChatColor.GREEN + ".");
					}
					res = sql.query("SELECT COUNT(*) AS totalPages FROM Transactions WHERE Seller='Server';");
					if ((res.getInt("totalPages") % 6) > 0) {
						sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + ((res.getInt("totalPages") / 6) + 1));
					} else {
						sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + (res.getInt("totalPages") / 6));
					}
					res.close();
					return true;
				}
				ResultSet res;
				Integer curPage;
				if (args.length < 2) {
					curPage = 1;
					res = sql.query("SELECT * FROM Transactions WHERE Seller='" + this.getServer().getOfflinePlayer(args[0]).getUniqueId().toString() + "' ORDER BY TransactionID DESC LIMIT 6;");
				} else {
					curPage = Integer.parseInt(args[1]);
					res = sql.query("SELECT * FROM Transactions WHERE Seller='" + this.getServer().getOfflinePlayer(args[0]).getUniqueId().toString() + "' ORDER BY TransactionID DESC LIMIT 6 OFFSET " + ((curPage - 1) * 6) + ";");
				}
				sender.sendMessage(ChatColor.DARK_GREEN + args[0].toString() + "'s" + ChatColor.GREEN +  " transactions.");
				while (res.next()) {
					sender.sendMessage(ChatColor.DARK_GREEN + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Purchaser"))).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + res.getInt("AmountPurchased") + ChatColor.GREEN + " x " + Items.itemByType(Material.getMaterial(res.getString("ItemName")), res.getShort("ItemMetadata")).getName().toString() + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format((res.getInt("AmountPurchased") * res.getDouble("Price"))) + ChatColor.GREEN + ".");
				}
				res = sql.query("SELECT COUNT(*) AS totalPages FROM Transactions WHERE Seller='" + this.getServer().getOfflinePlayer(args[0]).getUniqueId().toString() + "';");
				if ((res.getInt("totalPages") % 6) > 0) {
					sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + ((res.getInt("totalPages") / 6) + 1));
				} else {
					sender.sendMessage(ChatColor.GREEN + "Page " + ChatColor.AQUA + curPage + ChatColor.GREEN + " of " + ChatColor.AQUA + (res.getInt("totalPages") / 6));
				}
				res.close();
				return true;
			} catch (SQLException e) {
				if (e.getMessage().contains("closed")) {
					sender.sendMessage(ChatColor.RED + "That player must not have any transactions.");
					return true;
				} else {
					e.printStackTrace();
					return true;
				}
			}
		}
		
		if (cmd.getName().equalsIgnoreCase("vsa") && sender.hasPermission("vshop.admin")) {
			if (args.length < 2) {
				//return false;
			}
			if (args[0].equalsIgnoreCase("query") && sender.hasPermission("vshop.admin.query")) {
				String sqlq = "";
				for (int i = 1; i < args.length; i++) {
					sqlq += args[i] + " ";
				}
				sqlq.trim();
				sender.sendMessage(sqlq.toString());
				try {
					sql.query(sqlq);
				} catch (SQLException e) {
					sender.sendMessage(ChatColor.RED + "Your query returned an error.");
					sender.sendMessage(ChatColor.RED + "This error message may help: " + e.getLocalizedMessage());
					e.printStackTrace();
					return true;
				}
				return true;	
			}
			if (args[0].equalsIgnoreCase("createinf") && sender.hasPermission("vshop.admin.createinf")) {
				try {
					sql.query("UPDATE Selling SET Seller='Server', Infinite='1', ItemAmount='1' WHERE SellingID='" + args[1] + "';");
					sender.sendMessage(ChatColor.GREEN + "Successfully created infinite listing.");
					return true;
				} catch (SQLException e) {
					sender.sendMessage(ChatColor.RED + "An SQL error occurred. The following message may help.");
					sender.sendMessage(e.getLocalizedMessage());
					e.printStackTrace();
					return true;
				}
			}
			if (args[0].equalsIgnoreCase("delete") && sender.hasPermission("vshop.admin.delete")) {
				try {
					sql.query("DELETE FROM Selling WHERE SellingID='" + args[1] + "';");
					sender.sendMessage(ChatColor.GREEN + "Successfully delete listing with ID " + args[1]);
					return true;
				} catch (SQLException e) {
					sender.sendMessage(ChatColor.RED + "An SQL error occurred. The following message may help.");
					sender.sendMessage(e.getLocalizedMessage());
					e.printStackTrace();
				}
			}
			if (args[0].equalsIgnoreCase("update")) {
				try {
					sql.query("CREATE TABLE `Transactions` (`TransactionID` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE, `ItemName` TEXT NOT NULL, `ItemMetadata` INTEGER NOT NULL DEFAULT 0, `Seller` TEXT NOT NULL, `Purchaser` TEXT NOT NULL, `Price` REAL NOT NULL, `AmountPurchased` INTEGER NOT NULL);");
				} catch (SQLException e) {				
					e.printStackTrace();
				}
				return true;	
			} 
			return false;
		}
		return false;
	}
}
