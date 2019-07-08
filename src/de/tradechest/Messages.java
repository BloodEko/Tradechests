package de.tradechest;

import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;

public class Messages {
    
    public static String helpText;
    public static String showRadius;
    public static String reload;
    public static String gotItem;
    public static String placedItem;
    
    public static String totalChest;
    public static String loadedChest;
    
    public static String       itemMaterial;
    public static String       itemName;
    public static List<String> itemLore;
    
    public static void loadMessages(FileConfiguration conf) {
        helpText   = conf.getString("message.helptext");
        showRadius = conf.getString("message.showRadius");
        reload     = conf.getString("message.reload");
        gotItem    = conf.getString("message.gotItem");
        placedItem = conf.getString("message.placedItem");
        
        totalChest   = conf.getString("message.totalChest");
        loadedChest  = conf.getString("message.loadedChest");
        
        itemMaterial = conf.getString("item.material");
        itemName     = conf.getString("item.name");
        itemLore     = conf.getStringList("item.lore");
    }
}
