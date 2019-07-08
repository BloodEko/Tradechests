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

// TODO permissions

public class TradechestPlugin extends JavaPlugin implements Listener {

    public static HashSet<Location> chests = new HashSet<>();
    public static HashSet<Location> render = new HashSet<>();
    public static HashSet<Location> remove = new HashSet<>();
    
    File              dataFile;
    YamlConfiguration dataConfig;
    
    public static ItemStack  item = new ItemStack(Material.CHEST);
    
    public static Particle   particle;
    public static int        particleRange;
    public static int        particleQty;
    public static double     particleSpeed;
    public static double     particleHeight;
    public static long       particleDelay;
    
    public static JavaPlugin instance;
    
    
    @Override
    public void onEnable() {
        instance = this;
        loadConfig();
        loadChestData();
        renderParticles();
        getServer().getPluginManager().registerEvents(this, this);
    }
    
    @Override
    public void onDisable() {
        saveChestData();
    }
    
    
    public static void loadConfig() {
        //Config
        instance.saveDefaultConfig();
        instance.reloadConfig();
        
        //Messages
        Messages.loadMessages(instance.getConfig());
        
        //Particle
        particle = Particle.valueOf(instance.getConfig().getString("particle.name"));
        particleRange  = instance.getConfig().getInt    ("particle.range");
        particleQty    = instance.getConfig().getInt    ("particle.qty");
        particleSpeed  = instance.getConfig().getDouble ("particle.speed");
        particleHeight = instance.getConfig().getDouble ("particle.height");
        particleDelay  = instance.getConfig().getLong   ("particle.delay");
        
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
            list.add(getString(loc));
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
                chests.add(getLocation(world, loc));
            }
        }
    }
    
    private Location getLocation(String world, String loc) {
        String[] locs = loc.split(",");
        
        World w = getServer().getWorld(world);
        int   x = Integer.parseInt(locs[0]);
        int   y = Integer.parseInt(locs[1]);
        int   z = Integer.parseInt(locs[2]);
        
        return new Location(w, x, y, z);
    }
    
    private String getString(Location loc) {
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
                if (!inRange(player.getLocation(), loc)) {
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
    
    private boolean inRange(Location loc1, Location loc2) {
        if (loc1.getWorld() != loc2.getWorld()) {
            return false;
        }
        
        double absX = loc1.getX() - loc2.getX();
        double absY = loc1.getY() - loc2.getY();
        double absZ = loc1.getZ() - loc2.getZ();
        
        double abs1 = absX*absX + absY*absY + absZ*absZ;
        double abs2 = particleRange * particleRange;

        return abs1 < abs2;
    }

    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String name, String[] args) {
        
        if (args.length == 0) {
            sender.sendMessage(Messages.helpText);
            return true;
        }
        
        switch(args[0].toUpperCase()) {
            case "INFO":
                sender.sendMessage(Messages.showRadius  + TradechestPlugin.particleRange);
                sender.sendMessage(Messages.totalChest  + chests.size());
                sender.sendMessage(Messages.loadedChest + render.size());
                return true;
            
            case "RELOAD":
                sender.sendMessage(Messages.reload);
                TradechestPlugin.loadConfig();
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
        
        // off hand packet, ignore.
        if (event.getHand()   == EquipmentSlot.OFF_HAND 
         || event.getAction() != Action.RIGHT_CLICK_BLOCK
         || event.getPlayer().isSneaking()) {
            return; 
        }
        Block block = event.getClickedBlock();
        
        if (block.getType() != Material.CHEST || !chests.contains(block.getLocation())) {
            return;
        }
        if (!(block.getState() instanceof InventoryHolder)) {
           chests.remove(block.getLocation());
           return;
        }
        
        Inventory inv = ((InventoryHolder) block.getState()).getInventory();
        
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
}



