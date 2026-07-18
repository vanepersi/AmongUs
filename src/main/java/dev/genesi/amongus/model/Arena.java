package dev.genesi.amongus.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Arena {

    private final String name;
    private Location lobby;
    private Location cafeteria;
    private Location emergencyButton;
    private Location electrical;
    private final List<Location> spawns = new ArrayList<>();
    private final List<Location> vents = new ArrayList<>();
    private final List<Location> reactorPanels = new ArrayList<>();
    private final List<TaskPoint> tasks = new ArrayList<>();
    private Double entryFeeOverride;

    public Arena(String name) {
        this.name = name.toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return name;
    }

    public Location getLobby() {
        return cloneLocation(lobby);
    }

    public void setLobby(Location lobby) {
        this.lobby = cloneLocation(lobby);
    }

    public Location getCafeteria() {
        return cloneLocation(cafeteria);
    }

    public void setCafeteria(Location cafeteria) {
        this.cafeteria = cloneLocation(cafeteria);
    }

    public Location getEmergencyButton() {
        return cloneLocation(emergencyButton);
    }

    public void setEmergencyButton(Location emergencyButton) {
        this.emergencyButton = cloneLocation(emergencyButton);
    }

    public Location getElectrical() {
        return cloneLocation(electrical);
    }

    public void setElectrical(Location electrical) {
        this.electrical = cloneLocation(electrical);
    }

    public List<Location> getSpawns() {
        return cloneList(spawns);
    }

    public void addSpawn(Location location) {
        spawns.add(cloneLocation(location));
    }

    public void clearSpawns() {
        spawns.clear();
    }

    public List<Location> getVents() {
        return cloneList(vents);
    }

    public void addVent(Location location) {
        vents.add(cloneLocation(location));
    }

    public void clearVents() {
        vents.clear();
    }

    public List<Location> getReactorPanels() {
        return cloneList(reactorPanels);
    }

    public void addReactorPanel(Location location) {
        reactorPanels.add(cloneLocation(location));
    }

    public void clearReactorPanels() {
        reactorPanels.clear();
    }

    public List<TaskPoint> getTasks() {
        return List.copyOf(tasks);
    }

    public void addTask(TaskPoint point) {
        tasks.add(point);
    }

    public void clearTasks() {
        tasks.clear();
    }

    public Double getEntryFeeOverride() {
        return entryFeeOverride;
    }

    public void setEntryFeeOverride(Double entryFeeOverride) {
        this.entryFeeOverride = entryFeeOverride;
    }

    public boolean isReady() {
        return lobby != null
                && cafeteria != null
                && emergencyButton != null
                && !spawns.isEmpty()
                && !tasks.isEmpty()
                && !vents.isEmpty();
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lobby", serializeLocation(lobby));
        map.put("cafeteria", serializeLocation(cafeteria));
        map.put("emergency-button", serializeLocation(emergencyButton));
        map.put("electrical", serializeLocation(electrical));
        map.put("spawns", serializeLocations(spawns));
        map.put("vents", serializeLocations(vents));
        map.put("reactor-panels", serializeLocations(reactorPanels));
        List<Map<String, Object>> taskMaps = new ArrayList<>();
        for (TaskPoint task : tasks) {
            taskMaps.add(task.serialize());
        }
        map.put("tasks", taskMaps);
        if (entryFeeOverride != null) {
            map.put("entry-fee", entryFeeOverride);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Arena deserialize(String name, ConfigurationSection section) {
        Arena arena = new Arena(name);
        if (section == null) {
            return arena;
        }
        arena.lobby = deserializeLocation(section.getConfigurationSection("lobby"));
        arena.cafeteria = deserializeLocation(section.getConfigurationSection("cafeteria"));
        arena.emergencyButton = deserializeLocation(section.getConfigurationSection("emergency-button"));
        arena.electrical = deserializeLocation(section.getConfigurationSection("electrical"));
        List<?> spawnList = section.getList("spawns");
        if (spawnList != null) {
            for (Object entry : spawnList) {
                if (entry instanceof Map<?, ?> map) {
                    Location loc = deserializeLocationMap((Map<String, Object>) map);
                    if (loc != null) {
                        arena.spawns.add(loc);
                    }
                }
            }
        }
        List<?> ventList = section.getList("vents");
        if (ventList != null) {
            for (Object entry : ventList) {
                if (entry instanceof Map<?, ?> map) {
                    Location loc = deserializeLocationMap((Map<String, Object>) map);
                    if (loc != null) {
                        arena.vents.add(loc);
                    }
                }
            }
        }
        List<?> reactorList = section.getList("reactor-panels");
        if (reactorList != null) {
            for (Object entry : reactorList) {
                if (entry instanceof Map<?, ?> map) {
                    Location loc = deserializeLocationMap((Map<String, Object>) map);
                    if (loc != null) {
                        arena.reactorPanels.add(loc);
                    }
                }
            }
        }
        List<?> taskList = section.getList("tasks");
        if (taskList != null) {
            for (Object entry : taskList) {
                if (entry instanceof Map<?, ?> map) {
                    TaskPoint point = TaskPoint.deserialize((Map<String, Object>) map);
                    if (point != null) {
                        arena.tasks.add(point);
                    }
                }
            }
        }
        if (section.contains("entry-fee")) {
            arena.entryFeeOverride = section.getDouble("entry-fee");
        }
        return arena;
    }

    private static List<Map<String, Object>> serializeLocations(List<Location> locations) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Location location : locations) {
            Map<String, Object> serialized = serializeLocation(location);
            if (serialized != null) {
                list.add(serialized);
            }
        }
        return list;
    }

    private static Map<String, Object> serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }

    private static Location deserializeLocation(ConfigurationSection section) {
        if (section == null || !section.contains("world")) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world"));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    private static Location deserializeLocationMap(Map<String, Object> map) {
        if (map == null || !map.containsKey("world")) {
            return null;
        }
        World world = Bukkit.getWorld(String.valueOf(map.get("world")));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                toDouble(map.get("x")),
                toDouble(map.get("y")),
                toDouble(map.get("z")),
                (float) toDouble(map.get("yaw")),
                (float) toDouble(map.get("pitch"))
        );
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
    }

    private static List<Location> cloneList(List<Location> source) {
        List<Location> copy = new ArrayList<>(source.size());
        for (Location location : source) {
            copy.add(cloneLocation(location));
        }
        return copy;
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Arena arena)) {
            return false;
        }
        return Objects.equals(name, arena.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public static final class TaskPoint {
        private final TaskType type;
        private final Location location;

        public TaskPoint(TaskType type, Location location) {
            this.type = type;
            this.location = location.clone();
        }

        public TaskType getType() {
            return type;
        }

        public Location getLocation() {
            return location.clone();
        }

        public Map<String, Object> serialize() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type.name());
            map.put("location", serializeLocation(location));
            return map;
        }

        @SuppressWarnings("unchecked")
        public static TaskPoint deserialize(Map<String, Object> map) {
            if (map == null) {
                return null;
            }
            Object typeObj = map.get("type");
            Object locObj = map.get("location");
            if (typeObj == null || !(locObj instanceof Map<?, ?> locMap)) {
                return null;
            }
            TaskType type;
            try {
                type = TaskType.valueOf(String.valueOf(typeObj).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
            Location location = deserializeLocationMap((Map<String, Object>) locMap);
            if (location == null) {
                return null;
            }
            return new TaskPoint(type, location);
        }
    }
}
