package au.com.live.havrer0.vshop;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
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
				sql.query("CREATE TABLE `Selling` ( `SellingID`	INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE, `ItemName`	TEXT NOT NULL, `ItemMetadata`	INTEGER NOT NULL DEFAULT 0, `ItemAmount`	INTEGER NOT NULL, `Seller`	TEXT NOT NULL, `Price`	REAL NOT NULL)");
			} catch (SQLException e) {				
				e.printStackTrace();
			}
	    	try {
				sql.query("CREATE TABLE `Transactions` ( `TransactionID`	INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE, `SellingID`	INTEGER NOT NULL, `Purchaser`	TEXT NOT NULL, `AmountPurchased`	INTEGER NOT NULL)");
			} catch (SQLException e) {				
				e.printStackTrace();
			}
			
            config.set("setupDone", true);
	    }
	    this.getLogger().info("VShop version 1.0 has been loaded!");
	}
	
	@Override
	public void onDisable() {
		saveYamls();
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
		Player player = (Player) sender;
		PlayerInventory inv = player.getInventory();
		
		if (cmd.getName().equalsIgnoreCase("vs") || cmd.getName().equalsIgnoreCase("vshop") && player.hasPermission("vshop.info")) {
			sender.sendMessage(ChatColor.GREEN + "VShop is running version: " + ChatColor.AQUA + this.getDescription().getVersion().toString() + ChatColor.GREEN + ".");
			return true;
		}
		
		if (cmd.getName().equalsIgnoreCase("sell") && sender instanceof Player && player.hasPermission("vshop.sell")) {
			int pitemamt = 0;
			if (args.length < 2){
				return false;
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
		
		if (cmd.getName().equalsIgnoreCase("cancel") && sender instanceof Player && player.hasPermission("vshop.cancel")) {
			if (args.length < 2) {
				return false;
			}
			if (args[1].equalsIgnoreCase("all")) {
				try {
					ResultSet res = sql.query("SELECT * FROM Selling WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "' AND Seller='" + player.getUniqueId().toString() + "';");
					sql.query("DELETE FROM Selling WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + player.getUniqueId().toString() + "';");
					ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), res.getInt("ItemAmount"));
					pris.setDurability(res.getShort("ItemMetadata"));
					inv.addItem(pris);
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
				try {
					ResultSet res = sql.query("SELECT * FROM Selling WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "' AND Seller='" + player.getUniqueId().toString() + "';");
					sql.query("UPDATE Selling SET ItemAmount ='" + (res.getInt("ItemAmount") - Integer.parseInt(args[1])) + "' WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + player.getUniqueId().toString() + "';");
					ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), Integer.parseInt(args[1]));
					pris.setDurability(res.getShort("ItemMetadata"));
					inv.addItem(pris);
					player.sendMessage(ChatColor.GREEN + "You cancelled " + ChatColor.AQUA + args[1] + ChatColor.GREEN + ". Amount remaining: " + ChatColor.AQUA + (res.getInt("ItemAmount") - Integer.parseInt(args[1])) + ChatColor.GREEN + ".");
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
			}
		}
		
		if (cmd.getName().equalsIgnoreCase("buy") && sender instanceof Player && player.hasPermission("vshop.buy")) {			
			if (args.length < 2) {
				return false;
			}
			try {			
				//Gets a list of items that match the item the player is searching for and stores it in res.
                ResultSet res = sql.query("SELECT * FROM Selling WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "' ORDER BY Price ASC;");
				//Checks if the item amount is enough for the player's request
				if (res.getInt("ItemAmount") >= Integer.parseInt(args[1])) {
					//Checks if the player has enough money, if so, withdraws the money and deposits it into the seller's account.
					if (eco.has(this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))), (res.getDouble("Price") * Integer.parseInt(args[1])))) {
						eco.withdrawPlayer(player, (res.getDouble("Price") * Integer.parseInt(args[1])));
						eco.depositPlayer(this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))), (res.getDouble("Price") * Integer.parseInt(args[1])));
						//If the listing is left with no items, it deletes it
						if ((res.getInt("ItemAmount") - Integer.parseInt(args[1])) == 0) {
							sql.query("DELETE FROM Selling WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + res.getString("Seller") + "';");
						}
						//Or it changes the amount to reflect the purchase
						sql.query("UPDATE Selling SET ItemAmount ='" + (res.getInt("ItemAmount") - Integer.parseInt(args[1])) + "' WHERE ItemName='" + res.getString("ItemName") + "' AND ItemMetadata='" + res.getString("ItemMetadata") + "' AND Seller='" + res.getString("Seller") + "';");
						//Gives the player the item
						ItemStack pris = new ItemStack(Material.getMaterial(res.getString("ItemName")), Integer.parseInt(args[1]));
						pris.setDurability(res.getShort("ItemMetadata"));
						inv.addItem(pris);
						player.sendMessage(ChatColor.GREEN + "You bought " + ChatColor.AQUA + args[1] + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from " + ChatColor.DARK_GREEN + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + ChatColor.GREEN + " for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * Integer.parseInt(args[1]))) + ChatColor.GREEN + ".");
						this.getServer().getPlayer(UUID.fromString(res.getString("Seller"))).sendMessage(ChatColor.DARK_GREEN + player.getName() + ChatColor.GREEN + " bought " + ChatColor.AQUA + args[1] + " " + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + " from your listing for " + ChatColor.AQUA + eco.format((res.getDouble("Price") * Integer.parseInt(args[1]))) + ChatColor.GREEN + "." );
						this.getLogger().info(player.getName().toString() + " bought " + args[1] + " " + Items.itemByName(args[0]).getName().toString() + " from " + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + " for " + eco.format((res.getDouble("Price") * Integer.parseInt(args[1]))) + ".");
						res.close();
						return true;
					} else {
						player.sendMessage(ChatColor.RED + "You don't have enough money for that.");
						return true;
					}
				} else {
					player.sendMessage(ChatColor.RED + "Not enough items available to service request, try a lower amount.");
					return true;
				}
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
		
		if (cmd.getName().equalsIgnoreCase("search") && sender instanceof Player && player.hasPermission("vshop.search")) {
			if (args.length < 1) {
				return false;
			}
			try {
				ResultSet res = sql.query("SELECT * FROM Selling WHERE ItemName='" + Items.itemByName(args[0]).getType().toString() + "' AND ItemMetadata='" + Items.itemByName(args[0]).toStack().getDurability() + "' ORDER BY Price ASC;");
				player.sendMessage(ChatColor.GREEN + "Listings of " + ChatColor.AQUA + Items.itemByName(args[0]).getName().toString() + ChatColor.GREEN + ".");
				while (res.next()) {
					player.sendMessage(ChatColor.DARK_GREEN + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + eco.format(res.getDouble("Price"))  + ChatColor.GREEN + " ID:" + res.getInt("SellingID"));
				}
				res.close();
				return true;
			} catch (SQLException e) {
				if (e.getMessage().contains("closed")) {
					player.sendMessage(ChatColor.RED + "No one is selling that item.");
				} else {
					e.printStackTrace();
				}
			}
			
		}
		
		if (cmd.getName().equalsIgnoreCase("stock") && sender instanceof Player && player.hasPermission("vshop.stock")) {
			if (args.length < 1) {
				try {
					ResultSet res = sql.query("SELECT * FROM Selling WHERE Seller='" + player.getUniqueId().toString() +  "' ORDER BY SellingID DESC;");
					player.sendMessage(ChatColor.DARK_GREEN + "Your" + ChatColor.GREEN +  " listings.");
					while (res.next()) {
						player.sendMessage(ChatColor.AQUA + Items.itemByType(Material.getMaterial(res.getString("ItemName")), res.getShort("ItemMetadata")).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + eco.format(res.getDouble("Price")) + ChatColor.GREEN + " x " + ChatColor.AQUA + res.getInt("ItemAmount") + ChatColor.GREEN + " ID:" + res.getInt("SellingID"));
					}
					res.close();
					return true;
				} catch (SQLException e) {
					if (e.getMessage().contains("closed")) {
						player.sendMessage(ChatColor.RED + "You must not be selling anything.");
						return true;
					} else {
						e.printStackTrace();
						return true;
					}
				}
			}
			try {
				ResultSet res = sql.query("SELECT * FROM Selling WHERE Seller='" + this.getServer().getOfflinePlayer(args[0]).getUniqueId().toString() +  "' ORDER BY SellingID DESC;");
				player.sendMessage(ChatColor.DARK_GREEN + args[0].toString() + "'s" + ChatColor.GREEN +  " listings.");
				while (res.next()) {
					player.sendMessage(ChatColor.AQUA + Items.itemByType(Material.getMaterial(res.getString("ItemName")), res.getShort("ItemMetadata")).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + eco.format(res.getDouble("Price")) + ChatColor.GREEN + " x " + ChatColor.AQUA + res.getInt("ItemAmount") + ChatColor.GREEN + " ID:" + res.getInt("SellingID"));
				}
				res.close();
				return true;
			} catch (SQLException e) {
				if (e.getMessage().contains("closed")) {
					player.sendMessage(ChatColor.RED + "That player must not be selling anything.");
					return true;
				} else {
					e.printStackTrace();
					return true;
				}
			}
		}
		
		if (cmd.getName().equalsIgnoreCase("listings") && sender instanceof Player && player.hasPermission("vshop.listings")){
			try {
				ResultSet res = sql.query("SELECT * FROM Selling ORDER BY SellingID DESC;");
				while (res.next()) {
					player.sendMessage(ChatColor.DARK_GREEN + this.getServer().getOfflinePlayer(UUID.fromString(res.getString("Seller"))).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + Items.itemByType(Material.getMaterial(res.getString("ItemName")), res.getShort("ItemMetadata")).getName().toString() + ChatColor.GREEN + ": " + ChatColor.AQUA + eco.format(res.getDouble("Price")) + ChatColor.GREEN + " x " + ChatColor.AQUA + res.getInt("ItemAmount") + ChatColor.GREEN + " ID:" + res.getInt("SellingID"));
				}
				res.close();
				return true;
			} catch (SQLException e) {
				if (e.getMessage().contains("closed")) {
					player.sendMessage(ChatColor.RED + "No one must be selling anything.");
				} else {
					player.sendMessage(ChatColor.RED + "An SQL error occurred.");
					e.printStackTrace();
					return true;
				}
			}
			
		}
		
		if (cmd.getName().equalsIgnoreCase("vsa") && sender instanceof Player && player.hasPermission("vshop.admin")) {
			String sqlq = "";
			for (int i = 0; i < args.length; i++) {
				sqlq += args[i] + " ";
			}
			sqlq.trim();
			player.sendMessage(sqlq.toString());
			try {
				sql.query(sqlq);
			} catch (SQLException e) {
				player.sendMessage(ChatColor.RED + "Your query returned an error.");
				player.sendMessage(ChatColor.RED + "This error message may help: " + e.getLocalizedMessage());
				e.printStackTrace();
				return true;
			}
			return true;
		}
		return false;
	}
}