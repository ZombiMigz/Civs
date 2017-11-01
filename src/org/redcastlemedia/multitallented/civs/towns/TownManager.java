package org.redcastlemedia.multitallented.civs.towns;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.redcastlemedia.multitallented.civs.Civs;
import org.redcastlemedia.multitallented.civs.LocaleManager;
import org.redcastlemedia.multitallented.civs.civilians.Civilian;
import org.redcastlemedia.multitallented.civs.civilians.CivilianManager;
import org.redcastlemedia.multitallented.civs.items.ItemManager;
import org.redcastlemedia.multitallented.civs.regions.Region;
import org.redcastlemedia.multitallented.civs.regions.RegionManager;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.util.*;

public class TownManager {

    private static TownManager townManager = null;
    private HashMap<String, Town> towns = new HashMap<>();
    private List<Town> sortedTowns = new ArrayList<>();
    private HashMap<UUID, Town> invites = new HashMap<>();

    public TownManager() {
        townManager = this;
    }

    public void loadAllTowns() {
        File townFolder = new File(Civs.getInstance().getDataFolder(), "towns");
        if (!townFolder.exists()) {
            townFolder.mkdir();
        }
        try {
            for (File file : townFolder.listFiles()) {
                FileConfiguration config = new YamlConfiguration();
                try {
                    config.load(file);

                    loadTown(config, file.getName().replace(".yml", ""));
                } catch (Exception e) {
                    Civs.logger.warning("Unable to read from towns/" + file.getName());
                }
            }
        } catch (NullPointerException npe) {
            Civs.logger.severe("Unable to read from town folder!");
        }
    }

    public List<Town> getTowns() { return sortedTowns; }
    public Town getTown(String name) {
        return towns.get(name);
    }

    public Town getTownAt(Location location) {
        ItemManager itemManager = ItemManager.getInstance();
        for (Town town : sortedTowns) {
            TownType townType = (TownType) itemManager.getItemType(town.getType());
            int radius = townType.getBuildRadius();
            int radiusY = townType.getBuildRadiusY();
            Location townLocation = town.getLocation();

            if (!townLocation.getWorld().equals(location.getWorld())) {
                continue;
            }

            if (townLocation.getX() - radius >= location.getX()) {
                break;
            }

            if (townLocation.getX() + radius >= location.getX() &&
                    townLocation.getZ() + radius >= location.getZ() &&
                    townLocation.getZ() - radius <= location.getZ() &&
                    townLocation.getY() - radiusY <= location.getY() &&
                    townLocation.getY() + radiusY >= location.getY()) {
                return town;
            }

        }
        return null;
    }

    public void checkCriticalRequirements(Region region) {
        Town town = getTownAt(region.getLocation());
        RegionManager regionManager = RegionManager.getInstance();
        if (town == null) {
            return;
        }
        TownType townType = (TownType) ItemManager.getInstance().getItemType(town.getType());
        if (!townType.getCriticalReqs().contains(region.getType())) {
            return;
        }
        boolean hasReq = false;
        for (Region containedRegion : regionManager.getContainingRegions(town.getLocation(), townType.getBuildRadius())) {
            if (containedRegion.getType().equals(region.getType()) && region != containedRegion) {
                hasReq = true;
            }
        }
        if (!hasReq) {
            removeTown(town, true);
        }
    }

    public boolean checkIntersect(Location location, TownType townType) {
        Location[] locationCheck = new Location[9];
        locationCheck[0] = location;
        locationCheck[1] = new Location(location.getWorld(),
                location.getX() + townType.getBuildRadius(),
                Math.min(location.getY() + townType.getBuildRadiusY(), location.getWorld().getMaxHeight()),
                location.getZ() + townType.getBuildRadius());
        locationCheck[2] = new Location(location.getWorld(),
                location.getX() - townType.getBuildRadius(),
                Math.min(location.getY() + townType.getBuildRadiusY(), location.getWorld().getMaxHeight()),
                location.getZ() + townType.getBuildRadius());
        locationCheck[3] = new Location(location.getWorld(),
                location.getX() + townType.getBuildRadius(),
                Math.min(location.getY() + townType.getBuildRadiusY(), location.getWorld().getMaxHeight()),
                location.getZ() - townType.getBuildRadius());
        locationCheck[4] = new Location(location.getWorld(),
                location.getX() - townType.getBuildRadius(),
                Math.min(location.getY() + townType.getBuildRadiusY(), location.getWorld().getMaxHeight()),
                location.getZ() - townType.getBuildRadius());
        locationCheck[5] = new Location(location.getWorld(),
                location.getX() + townType.getBuildRadius(),
                Math.max(location.getY() - townType.getBuildRadiusY(), 0),
                location.getZ() + townType.getBuildRadius());
        locationCheck[6] = new Location(location.getWorld(),
                location.getX() - townType.getBuildRadius(),
                Math.max(location.getY() - townType.getBuildRadiusY(), 0),
                location.getZ() + townType.getBuildRadius());
        locationCheck[7] = new Location(location.getWorld(),
                location.getX() + townType.getBuildRadius(),
                Math.max(location.getY() - townType.getBuildRadiusY(), 0),
                location.getZ() - townType.getBuildRadius());
        locationCheck[8] = new Location(location.getWorld(),
                location.getX() - townType.getBuildRadius(),
                Math.max(location.getY() - townType.getBuildRadiusY(), 0),
                location.getZ() - townType.getBuildRadius());
        for (Location currentLocation : locationCheck) {
            if (getTownAt(currentLocation) != null) {
                return true;
            }
        }
        return false;
    }

    private void loadTown(FileConfiguration config, String name) {

        HashMap<UUID, String> people = new HashMap<>();
        for (String key : config.getConfigurationSection("people").getKeys(false)) {
            people.put(UUID.fromString(key), config.getString("people." + key));
        }
        Town town = new Town(name,
                config.getString("type"),
                Region.idToLocation(config.getString("location")),
                people);
        addTown(town);
    }
    public void addTown(Town town) {
        towns.put(town.getName().toLowerCase(), town);
        sortedTowns.add(town);
        if (sortedTowns.size() > 1) {
            Collections.sort(sortedTowns, new Comparator<Town>() {

                @Override
                public int compare(Town o1, Town o2) {
                    ItemManager itemManager = ItemManager.getInstance();
                    TownType townType1 = (TownType) itemManager.getItemType(o1.getType());
                    TownType townType2 = (TownType) itemManager.getItemType(o2.getType());
                    if (o1.getLocation().getX() - townType1.getBuildRadius() >
                            o2.getLocation().getX() - townType2.getBuildRadius()) {
                        return 1;
                    } else if (o1.getLocation().getX() - townType1.getBuildRadius() <
                            o2.getLocation().getX() - townType2.getBuildRadius()) {
                        return -1;
                    }
                    return 0;
                }
            });
        }
    }
    public void removeTown(Town town, boolean broadcast) {
        if (broadcast) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Civilian civ = CivilianManager.getInstance().getCivilian(player.getUniqueId());
                player.sendMessage(Civs.getPrefix() + LocaleManager.getInstance().getTranslation(civ.getLocale(),
                        "town-destroyed").replace("$1", town.getName()));
            }
        }
        towns.remove(town.getName().toLowerCase());
        sortedTowns.remove(town);
        if (Civs.getInstance() == null) {
            return;
        }
        File townFolder = new File(Civs.getInstance().getDataFolder(), "towns");
        if (!townFolder.exists()) {
            townFolder.mkdir();
        }
        File townFile = new File(townFolder, town.getName().toLowerCase() + ".yml");
        townFile.delete();
    }
    public void addInvite(UUID uuid, Town town) {
        invites.put(uuid, town);
    }
    public void clearInvite(UUID uuid) {
        invites.remove(uuid);
    }
    public Town getInviteTown(UUID uuid) {
        return invites.get(uuid);
    }
    public boolean acceptInvite(UUID uuid) {
        if (!invites.containsKey(uuid)) {
            return false;
        }
        Town town = invites.get(uuid);
        town.getPeople().put(uuid, "member");
        saveTown(town);
        invites.remove(uuid);
        return true;
    }

    public void saveTown(Town town) {
        if (Civs.getInstance() == null) {
            return;
        }
        File townFolder = new File(Civs.getInstance().getDataFolder(), "towns");
        if (!townFolder.exists()) {
            townFolder.mkdir();
        }
        File townFile = new File(townFolder, town.getName().toLowerCase() + ".yml");
        try {
            if (!townFile.exists()) {
                townFile.createNewFile();
            }
            FileConfiguration config = new YamlConfiguration();
            config.load(townFile);


            config.set("name", town.getName());
            config.set("type", town.getType());
            config.set("location", Region.locationToString(town.getLocation()));
            for (UUID key : town.getPeople().keySet()) {
                config.set("people." + key, town.getPeople().get(key));
            }

            //TODO save all town properties
            config.save(townFile);
        } catch (Exception e) {
            Civs.logger.severe("Unable to save town " + town.getName().toLowerCase() + ".yml");
        }
    }

    public static TownManager getInstance() {
        if (townManager == null) {
            new TownManager();
        }
        return townManager;
    }
}
