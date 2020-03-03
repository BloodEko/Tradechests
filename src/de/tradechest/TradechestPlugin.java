package de.tradechest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bstats.bukkit.Metrics;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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

/**
 * Adds new chests to the server which can be opened everywhere 
 * and are displayed with particles to near players.
 */
public class TradechestPlugin extends JavaPlugin implements TabCompleter, Listener {
    
    ItemStack chestItem = new ItemStack(Material.CHEST);

    Particle   particle;
    int        particleQty;
    int        particleRange;
    double     particleSqrt;
    double     particleSpeed;
    double     particleHeight;
    long       particleDelay;
    
    int clicks;
    
    Set<Location> chests = new HashSet<>();
    Set<Location> render = new HashSet<>();
    Set<Location> remove = new HashSet<>();
    
    File              dataFile;
    YamlConfiguration dataConfig;
    
    
    @Override
    public void onEnable() {
        loadConfig();
        loadMetrics();
        getCommand("tradechest").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
            loadChestData();
            renderParticles();
        }, 0L);
    }
    
    @Override
    public void onDisable() {
        saveChestData();
    }
    
    /**
     * Creates the default config.yml file. If it already exists, reloads
     * that and overrides current settings, the changes are used directly.
     */
    public void loadConfig() {
        saveDefaultConfig();
        reloadConfig();
        
        Messages.loadMessages(getConfig());
        
        particle = Particle.valueOf(getConfig().getString("particle.name"));
        particleQty    = getConfig().getInt("particle.qty");
        particleSpeed  = getConfig().getDouble("particle.speed");
        particleHeight = getConfig().getDouble("particle.height");
        particleDelay  = getConfig().getLong("particle.delay");
        particleRange  = getConfig().getInt("particle.range");
        particleSqrt   = particleRange * particleRange;
        
        ItemMeta meta = chestItem.getItemMeta();
        meta.setDisplayName(Messages.itemName);
        meta.setLore(Messages.itemLore);
        chestItem.setItemMeta(meta);
    }
    
    /**
     * Clears data.yml and then writes a copy
     * of the current loaded chestdata to it.
     */
    public void saveChestData() {
        Map<World, List<String>> map = new HashMap<>();
        List<String> list;
        
        for (Location loc : chests) { // sort by World
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
    
    
    /**
     * Loads the chestdata from each world from data.yml into ram.
     * Doesn't clear the current data, so only useful during startup.
     */
    public void loadChestData() {
        dataFile   = new File(getDataFolder(), "data.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection worlds = dataConfig.getConfigurationSection("chests");
        if (worlds == null) {
            return;
        }
        
        for (String name : worlds.getKeys(false)) {
            loadWorld(name, worlds.getStringList(name));
        }
    }
    
    
    private void loadWorld(String name, List<String> locs) {
        World world = getServer().getWorld(name);
        
        if (world == null) {
            warn(Messages.loadWorldFail + name);
            return;
        }
        
        for (String loc : locs) {
            chests.add(asLocation(world, loc));
        }
    }
    
    
    private Location asLocation(World world, String loc) {
        String[] locs = loc.split(",");
        
        int   x = Integer.parseInt(locs[0]);
        int   y = Integer.parseInt(locs[1]);
        int   z = Integer.parseInt(locs[2]);
        
        return new Location(world, x, y, z);
    }
    
    private String asString(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
    
    
    private void warn(String msg) {
        getLogger().info("ERROR "+ msg);
    }
    
    private void loadMetrics() {
        Metrics metrics = new Metrics(this, 6666);
        metrics.addCustomChart(new Metrics.SingleLineChart("clicks", () -> clicks));
        metrics.addCustomChart(new Metrics.SingleLineChart("chests_loaded", () -> chests.size()));
        metrics.addCustomChart(new Metrics.SingleLineChart("chests_active", () -> render.size()));
    }
    
    
    /**
     * Starts a new Task which renders and displays particles
     * above all registered chests to near players.
     */
    private void renderParticles() {
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {

            render.clear();
            renderLocations();
            for (Location loc : render) {
                loc.getWorld().spawnParticle(particle, loc, particleQty, 0.2, 0.1, 0.2 , particleSpeed);
            }
            
        }, 10L, particleDelay);
    }
    
    /**
     * Defines all locations where particles should be spawned.
     * Broken chests which were deleted externally with WorldEdit
     * or other tools are automatically removed.
     */
    private void renderLocations() {
        for (Player player : getServer().getOnlinePlayers()) {
            for (Location loc : chests) {
                if (!isNear(player.getLocation(), loc)) {
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
    
    
    private boolean isNear(Location loc, Location chestLoc) {
        return loc.getWorld() == chestLoc.getWorld()
            && loc.distanceSquared(chestLoc) < particleSqrt;
    }

    
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String name, String[] args) {
        
        if (args.length == 0) {
            sender.sendMessage(Messages.helpText);
            return true;
        }
        
        switch(args[0].toUpperCase()) {
            case "INFO":
                sender.sendMessage(Messages.infoHeadLine);
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
                    ((Player) sender).getInventory().addItem(chestItem);
                    return true;
                }
        }

        sender.sendMessage(Messages.helpText);
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        String[] commands = {"info", "reload", "item", "help"};
        
        for (String cmd : commands) {
            if (cmd.startsWith(args[0])) {
                list.add(cmd);
            }
        }
        return list;
    }
    

    
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        
        Block  block = event.getBlock();
        Location loc = block.getLocation();
        
        if (block.getType() == Material.CHEST && chests.contains(loc)) {
            chests.remove(loc);
            
            event.setDropItems(false);
            block.getWorld().dropItem(loc, chestItem);
        }
    }
    
    
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        
        ItemStack item = event.getItemInHand();
        
        if (item.getType() == Material.CHEST && chestItem.isSimilar(item)) {
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
        
        if (allowAccess(inv.getHolder())) {
            event.getPlayer().openInventory(inv);
            event.setCancelled(true);
            clicks++;
        }
    }
    
    
    @EventHandler(priority=EventPriority.LOWEST)
    public void onOpenInvLow(InventoryOpenEvent event) {
        
        // let other plugins ignore the event
        if (allowAccess(event.getInventory().getHolder())) {
            event.setCancelled(true);
        }
    }
    
    
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onOpenInvHigh(InventoryOpenEvent event) {
        
        // finally allow opening
        if (allowAccess(event.getInventory().getHolder())) {
            event.setCancelled(false);
        }
    }
    
    /**
     * Access is considered safe when it is a single registered Chest,
     * or both locations of a DoubleChest are registered.
     */
    private boolean allowAccess(InventoryHolder holder) {
        
        if (holder instanceof Chest) {
            return chests.contains(holder.getInventory().getLocation());
        }
        
        if (holder instanceof DoubleChest) {
            DoubleChest chest = ((DoubleChest) holder);
            Chest       left  = (Chest) chest.getLeftSide();
            Chest       right = (Chest) chest.getRightSide();
            
            return chests.contains(left.getLocation()) 
                && chests.contains(right.getLocation());
        }
        
        return false;
    }
}


