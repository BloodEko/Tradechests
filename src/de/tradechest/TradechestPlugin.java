package de.tradechest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;


public class TradechestPlugin extends JavaPlugin implements Listener {
    
    ItemStack         item   = new ItemStack(Material.CHEST);
    
    HashSet<Location> chests = new HashSet<>();
    HashSet<Location> render = new HashSet<>();
    HashSet<Location> remove = new HashSet<>();
    
    File              dataFile;
    YamlConfiguration dataConfig;
    
    Particle   particle;
    int        particleQty;
    int        particleRange;
    double     particleSqrt;
    double     particleSpeed;
    double     particleHeight;
    long       particleDelay;
    
    
    @Override
    public void onEnable() {
        loadConfig();
        loadChestData();
        renderParticles();
        getServer().getPluginManager().registerEvents(this, this);
    }
    
    @Override
    public void onDisable() {
        saveChestData();
    }
    
    
    public void loadConfig() {
        //Config
        saveDefaultConfig();
        reloadConfig();
        
        //Messages
        Messages.loadMessages(getConfig());
        
        //Particle
        particle = Particle.valueOf(getConfig().getString("particle.name"));
        particleQty    = getConfig().getInt    ("particle.qty");
        particleSpeed  = getConfig().getDouble ("particle.speed");
        particleHeight = getConfig().getDouble ("particle.height");
        particleDelay  = getConfig().getLong   ("particle.delay");
        particleRange  = getConfig().getInt    ("particle.range");
        particleSqrt   = particleRange * particleRange;
        
        //Item
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Messages.itemName);
        meta.setLore(Messages.itemLore);
        item.setItemMeta(meta);
    }
    
    public void saveChestData() {
        HashMap<World, List<String>> map = new HashMap<>();
        List<String> list;
        
        for (Location loc : chests) {
            list = map.get(loc.getWorld());
            if (list == null) {
                list = new ArrayList<>();
                map.put(loc.getWorld(), list);
            }
            list.add(asString(loc));
        }

        dataConfig.set("chests", null);
        
        for (World world : map.keySet()) {
            dataConfig.set("chests." + world.getName(), map.get(world));
        }
        
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    public void loadChestData() {
        dataFile   = new File(getDataFolder(), "data.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection sub = dataConfig.getConfigurationSection("chests");
        if (sub == null) {
            return;
        }
        
        for (String world : sub.getKeys(false)) {
            for (String loc : sub.getStringList(world)) {
                chests.add(asLocation(world, loc));
            }
        }
    }
    
    private Location asLocation(String world, String loc) {
        String[] locs = loc.split(",");
        
        World w = getServer().getWorld(world);
        int   x = Integer.parseInt(locs[0]);
        int   y = Integer.parseInt(locs[1]);
        int   z = Integer.parseInt(locs[2]);
        
        return new Location(w, x, y, z);
    }
    
    private String asString(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
    
    
    public void renderParticles() {
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {

            render.clear();
            renderLocations();
            for (Location loc : render) {
                loc.getWorld().spawnParticle(particle, loc, particleQty, 0.2, 0.1, 0.2 , particleSpeed);
            }
            
        }, 10L, particleDelay);
    }
    
    
    public void renderLocations() {
        
        for (Player player : getServer().getOnlinePlayers()) {
            for (Location loc : chests) {
                if (!intersects(player.getLocation(), loc)) {
                    continue;
                }
                if (loc.getBlock().getType() != Material.CHEST) {
                    remove.add(loc);
                    continue;
                }
                render.add(loc.clone().add(0.5, particleHeight, 0.5));
            }
        }
        
        for (Location loc : remove) {
            chests.remove(loc);
            render.remove(loc);
        }
        remove.clear();
    }
    
    private boolean intersects(Location loc, Location chestLoc) {
        if (loc.getWorld() != chestLoc.getWorld()) {
            return false;
        }
        return loc.distanceSquared(chestLoc) < particleSqrt;
    }

    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String name, String[] args) {
        
        if (args.length == 0) {
            sender.sendMessage(Messages.helpText);
            return true;
        }
        
        switch(args[0].toUpperCase()) {
            case "INFO":
                sender.sendMessage(Messages.showRadius  + particleRange);
                sender.sendMessage(Messages.totalChest  + chests.size());
                sender.sendMessage(Messages.loadedChest + render.size());
                return true;
            
            case "RELOAD":
                sender.sendMessage(Messages.reload);
                loadConfig();
                return true;
            
            case "ITEM":
                if (sender instanceof Player) {
                    sender.sendMessage(Messages.gotItem);
                    ((Player) sender).getInventory().addItem(item);
                    return true;
                }
        }

        sender.sendMessage(Messages.helpText);
        return true;
    }
    

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        
        Block  block = event.getBlock();
        Location loc = block.getLocation();
        
        if (block.getType() == Material.CHEST && chests.contains(loc)) {
            chests.remove(loc);
            
            event.setDropItems(false);
            block.getWorld().dropItem(loc, item);
        }
    }
    
    
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        
        ItemStack val = event.getItemInHand();
        
        if (val.getType() == Material.CHEST && item.isSimilar(val)) {
            chests.add(event.getBlock().getLocation());
            event.getPlayer().sendMessage(Messages.placedItem);
        }
    }
    
    
    @EventHandler(priority=EventPriority.LOWEST)
    public void onClickBlock(PlayerInteractEvent event) {

        Block block = event.getClickedBlock();
        
        if ( event.getHand()   == EquipmentSlot.OFF_HAND     // off hand packet, ignore.
         ||  event.getAction() != Action.RIGHT_CLICK_BLOCK
         ||  block.getType()   != Material.CHEST 
         ||  event.getPlayer().isSneaking()
         || !chests.contains(block.getLocation())) {
            return; 
        }
        
        Inventory inv = ((InventoryHolder) block.getState()).getInventory();
        
        if (!isSafe(inv.getHolder())) {
            return;
        }
        
        event.getPlayer().openInventory(inv);
        event.setCancelled(true);
    }
    
    
    @EventHandler(priority=EventPriority.LOWEST)
    public void onOpenInvLow(InventoryOpenEvent event) {
        
        // let other plugins ignore the event
        if (chests.contains(event.getInventory().getLocation())) {
            event.setCancelled(true);
        }
    }
    
    
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onOpenInvHigh(InventoryOpenEvent event) {
        
        // finally allow opening
        if (chests.contains(event.getInventory().getLocation())) {
            event.setCancelled(false);
        }
    }
    
    
    private boolean isSafe(InventoryHolder holder) {
        
        if (holder instanceof DoubleChest) {
            DoubleChest chest = ((DoubleChest) holder);
            Chest       left  = (Chest) chest.getLeftSide();
            Chest       right = (Chest) chest.getRightSide();
            
            return chests.contains(left.getLocation()) && chests.contains(right.getLocation());
        }
        return true;
    }
}



