/******************************************************************************
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.                       *
 * Multiverse 2 is licensed under the BSD License.                            *
 * For more information please check the README.md file included              *
 * with this project.                                                         *
 ******************************************************************************/

package com.onarandombox.MultiverseCore;

import com.fernferret.allpay.AllPay;
import com.fernferret.allpay.GenericBank;
import com.onarandombox.MultiverseCore.api.BlockSafety;
import com.onarandombox.MultiverseCore.api.Core;
import com.onarandombox.MultiverseCore.api.LocationManipulation;
import com.onarandombox.MultiverseCore.api.MVPlugin;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseMessaging;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import com.onarandombox.MultiverseCore.api.SafeTTeleporter;
import com.onarandombox.MultiverseCore.commands.*;
import com.onarandombox.MultiverseCore.destination.AnchorDestination;
import com.onarandombox.MultiverseCore.destination.BedDestination;
import com.onarandombox.MultiverseCore.destination.CannonDestination;
import com.onarandombox.MultiverseCore.destination.DestinationFactory;
import com.onarandombox.MultiverseCore.destination.ExactDestination;
import com.onarandombox.MultiverseCore.destination.PlayerDestination;
import com.onarandombox.MultiverseCore.destination.WorldDestination;
import com.onarandombox.MultiverseCore.event.MVVersionEvent;
import com.onarandombox.MultiverseCore.listeners.MVEntityListener;
import com.onarandombox.MultiverseCore.listeners.MVPlayerListener;
import com.onarandombox.MultiverseCore.listeners.MVPluginListener;
import com.onarandombox.MultiverseCore.listeners.MVWeatherListener;
import com.onarandombox.MultiverseCore.utils.*;
import com.onarandombox.MultiverseCore.updating.Updater;
import com.pneumaticraft.commandhandler.CommandHandler;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The implementation of the Multiverse-{@link Core}.
 */
public class MultiverseCore extends JavaPlugin implements MVPlugin, Core {
    private static final int PROTOCOL = 12;
    // Global Multiverse config variable, states whether or not
    // Multiverse should stop other plugins from teleporting players
    // to worlds.
    // TODO This is REALLY bad style! We have to change this!
    // No, I'm NOT going to suppress these warnings because we HAVE TO CHANGE THIS!
    public static boolean EnforceAccess;
    public static boolean PrefixChat;
    public static boolean DisplayPermErrors;
    public static boolean TeleportIntercept;
    public static boolean FirstSpawnOverride;
    public static int GlobalDebug = 0;
    private static Map<String, String> teleportQueue = new HashMap<String, String>();

    private AnchorManager anchorManager = new AnchorManager(this);

    /**
     * This method is used to find out who is teleporting a player.
     * @param playerName The teleported player (the teleportee).
     * @return The player that teleported the other one (the teleporter).
     */
    public static String getPlayerTeleporter(String playerName) {
        if (teleportQueue.containsKey(playerName)) {
            String teleportee = teleportQueue.get(playerName);
            teleportQueue.remove(playerName);
            return teleportee;
        }
        return null;
    }

    /**
     * This method is used to add a teleportation to the teleportQueue.
     *
     * @param teleporter The name of the player that initiated the teleportation.
     * @param teleportee The name of the player that was teleported.
     */
    public static void addPlayerToTeleportQueue(String teleporter, String teleportee) {
        staticLog(Level.FINEST, "Adding mapping '" + teleporter + "' => '" + teleportee + "' to teleport queue");
        teleportQueue.put(teleportee, teleporter);
    }

    @Override
    public String toString() {
        return "The Multiverse-Core Plugin";
    }

    /**
     * {@inheritDoc}
     * @deprecated This is now deprecated, nobody needs it any longer.
     * All version info-dumping is now done with {@link MVVersionEvent}.
     */
    @Override
    @Deprecated
    public String dumpVersionInfo(String buffer) {
        return buffer;
    }

    @Override
    public MultiverseCore getCore() {
        return this;
    }

    @Override
    public void setCore(MultiverseCore core) {
        // This method is required by the interface (so core is effectively a plugin of itself) and therefore
        // this is never used.
    }

    @Override
    public int getProtocolVersion() {
        return MultiverseCore.PROTOCOL;
    }

    // Useless stuff to keep us going.
    private static final Logger LOGGER = Logger.getLogger("Minecraft");
    private static DebugLog debugLog;

    // Setup our Map for our Commands using the CommandHandler.
    private CommandHandler commandHandler;

    private static final String LOG_TAG = "[Multiverse-Core]";

    // Multiverse Permissions Handler
    private MVPermissions ph;

    // Configurations
    private FileConfiguration multiverseConfig = null;

    private MVWorldManager worldManager = new WorldManager(this);

    // Setup the block/player/entity listener.
    private MVPlayerListener playerListener = new MVPlayerListener(this);
    private MVEntityListener entityListener = new MVEntityListener(this);
    private MVPluginListener pluginListener = new MVPluginListener(this);
    private MVWeatherListener weatherListener = new MVWeatherListener(this);

    //public UpdateChecker updateCheck;

    // HashMap to contain information relating to the Players.
    private HashMap<String, MVPlayerSession> playerSessions;
    private GenericBank bank = null;
    private AllPay banker;
    private int pluginCount;
    private DestinationFactory destFactory;
    private SpoutInterface spoutInterface = null;
    private static final double ALLPAY_VERSION = 5;
    private static final double CH_VERSION = 4;
    private MultiverseMessaging messaging;
    private BlockSafety blockSafety;
    private LocationManipulation locationManipulation;
    private SafeTTeleporter safeTTeleporter;

    private File serverFolder = new File(System.getProperty("user.dir"));

    @Override
    public void onLoad() {
        // Create our DataFolder
        getDataFolder().mkdirs();
        // Setup our Debug Log
        debugLog = new DebugLog("Multiverse-Core", getDataFolder() + File.separator + "debug.log");
        // Setup our BlockSafety
        this.blockSafety = new SimpleBlockSafety(this);
        // Setup our LocationManipulation
        this.locationManipulation = new SimpleLocationManipulation();
        // Setup our SafeTTeleporter
        this.safeTTeleporter = new SimpleSafeTTeleporter(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileConfiguration getMVConfiguration() {
        return this.multiverseConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GenericBank getBank() {
        return this.bank;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onEnable() {
        //this.worldManager = new WorldManager(this);
        // Perform initial checks for AllPay
        if (!this.validateAllpay() || !this.validateCH()) {
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.messaging = new MVMessaging();
        this.banker = new AllPay(this, LOG_TAG + " ");
        // Output a little snippet to show it's enabled.
        this.log(Level.INFO, "- Version " + this.getDescription().getVersion() + " (API v" + PROTOCOL + ") Enabled - By " + getAuthors());
        // Load the defaultWorldGenerators
        this.worldManager.getDefaultWorldGenerators();

        this.registerEvents();
        // Setup Permissions, we'll do an initial check for the Permissions plugin then fall back on isOP().
        this.ph = new MVPermissions(this);

        this.bank = this.banker.loadEconPlugin();

        // Setup the command manager
        this.commandHandler = new CommandHandler(this, this.ph);
        // Call the Function to assign all the Commands to their Class.
        this.registerCommands();

        // Initialize the Destination factor AFTER the commands
        this.initializeDestinationFactory();

        this.playerSessions = new HashMap<String, MVPlayerSession>();

        // Start the Update Checker
        // updateCheck = new UpdateChecker(this.getDescription().getName(), this.getDescription().getVersion());
        this.getServer().getScheduler().scheduleAsyncRepeatingTask(this, new Updater(this), 0L, 1200L);

        // Call the Function to load all the Worlds and setup the HashMap
        // When called with null, it tries to load ALL
        // this function will be called every time a plugin registers a new envtype with MV
        // Setup & Load our Configuration files.
        loadConfigs();
        if (this.multiverseConfig != null) {
            this.worldManager.loadDefaultWorlds();
            this.worldManager.loadWorlds(true);
        } else {
            this.log(Level.SEVERE, "Your configs were not loaded. Very little will function in Multiverse.");
        }
        this.anchorManager.loadAnchors();

        // Now set the firstspawnworld (after the worlds are loaded):
        // Default as the server.props world.
        this.worldManager.setFirstSpawnWorld(this.multiverseConfig.getString("firstspawnworld", getDefaultWorldName()));
        // We have to set this one here, if it's not present, we don't know the name of the default world.
        // and this one won't be in the defaults yml file.
        try {
            this.multiverseConfig.set("firstspawnworld", this.worldManager.getFirstSpawnWorld().getName());
        } catch (NullPointerException e) {
            // A test that had no worlds loaded was being run. This should never happen in production
        }
        this.saveMVConfig();
        // Check to see if spout was already loaded (most likely):
        if (this.getServer().getPluginManager().getPlugin("Spout") != null) {
            this.setSpout();
            this.log(Level.INFO, "Spout integration enabled.");
        }
    }

    private boolean validateAllpay() {
        try {
            this.banker = new AllPay(this, "Verify");
            if (this.banker.getVersion() >= ALLPAY_VERSION) {
                return true;
            }
        } catch (Throwable t) {
        }
        LOGGER.info(String.format("%s - Version %s was NOT ENABLED!!!", LOG_TAG, this.getDescription().getVersion()));
        LOGGER.info(String.format("%s A plugin that has loaded before %s has an incompatible version of AllPay (an internal library)!",
                LOG_TAG, this.getDescription().getName()));
        LOGGER.info(String.format("%s The Following Plugins MAY out of date: %s", LOG_TAG, AllPay.pluginsThatUseUs.toString()));
        LOGGER.info(String.format("%s This plugin needs AllPay v%f or higher and another plugin has loaded v%f!",
                LOG_TAG, ALLPAY_VERSION, this.banker.getVersion()));
        return false;
    }

    private boolean validateCH() {
        try {
            this.commandHandler = new CommandHandler(this, null);
            if (this.commandHandler.getVersion() >= CH_VERSION) {
                return true;
            }
        } catch (Throwable t) {
        }
        LOGGER.info(String.format("%s - Version %s was NOT ENABLED!!!", LOG_TAG, this.getDescription().getVersion()));
        LOGGER.info(String.format("%s A plugin that has loaded before %s has an incompatible version of CommandHandler (an internal library)!",
                LOG_TAG, this.getDescription().getName()));
        LOGGER.info(String.format("%s Please contact this plugin author!!!", LOG_TAG));
        LOGGER.info(String.format("%s This plugin needs CommandHandler v%f or higher and another plugin has loaded v%f!",
                LOG_TAG, CH_VERSION, this.commandHandler.getVersion()));
        return false;
    }

    private void initializeDestinationFactory() {
        this.destFactory = new DestinationFactory(this);
        this.destFactory.registerDestinationType(WorldDestination.class, "");
        this.destFactory.registerDestinationType(WorldDestination.class, "w");
        this.destFactory.registerDestinationType(ExactDestination.class, "e");
        this.destFactory.registerDestinationType(PlayerDestination.class, "pl");
        this.destFactory.registerDestinationType(CannonDestination.class, "ca");
        this.destFactory.registerDestinationType(BedDestination.class, "b");
        this.destFactory.registerDestinationType(AnchorDestination.class, "a");
    }

    /**
     * Function to Register all the Events needed.
     */
    private void registerEvents() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this.playerListener, this);
        pm.registerEvents(this.entityListener, this);
        pm.registerEvents(this.pluginListener, this);
        pm.registerEvents(this.weatherListener, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadConfigs() {
        // Now grab the Configuration Files.
        this.multiverseConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        Configuration coreDefaults = YamlConfiguration.loadConfiguration(this.getClass().getResourceAsStream("/defaults/config.yml"));
        this.multiverseConfig.setDefaults(coreDefaults);
        this.multiverseConfig.options().copyDefaults(true);
        this.saveMVConfig();
        this.multiverseConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        this.worldManager.loadWorldConfig(new File(getDataFolder(), "worlds.yml"));

        // Setup the Debug option, we'll default to false because this option will not be in the default config.
        GlobalDebug = this.multiverseConfig.getInt("debug", 0);
        // Lets cache these values due to the fact that they will be accessed many times.
        EnforceAccess = this.multiverseConfig.getBoolean("enforceaccess", false);
        PrefixChat = this.multiverseConfig.getBoolean("worldnameprefix", true);
        // Should MV Intercept teleports by other plugins?
        TeleportIntercept = this.multiverseConfig.getBoolean("teleportintercept", true);
        // Should MV do the first spawn stuff?
        FirstSpawnOverride = this.multiverseConfig.getBoolean("firstspawnoverride", true);
        // Should permissions errors display to users?
        DisplayPermErrors = this.multiverseConfig.getBoolean("displaypermerrors", true);

        this.messaging.setCooldown(this.multiverseConfig.getInt("messagecooldown", 5000)); // SUPPRESS CHECKSTYLE: MagicNumberCheck
        // Update the version of the config!
        this.multiverseConfig.set("version", coreDefaults.get("version"));

        // Remove old values.
        this.multiverseConfig.set("enforcegamemodes", null);
        this.multiverseConfig.set("bedrespawn", null);
        this.multiverseConfig.set("opfallback", null);

        this.saveMVConfigs();
    }

    /**
     * Safely return a world name.
     * (The tests call this with no worlds loaded)
     *
     * @return The default world name.
     */
    private String getDefaultWorldName() {
        if (this.getServer().getWorlds().size() > 0) {
            return this.getServer().getWorlds().get(0).getName();
        }
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MultiverseMessaging getMessaging() {
        return this.messaging;
    }

    /**
     * Register Multiverse-Core commands to Command Manager.
     */
    private void registerCommands() {
        // Intro Commands
        this.commandHandler.registerCommand(new HelpCommand(this));
        this.commandHandler.registerCommand(new VersionCommand(this));
        this.commandHandler.registerCommand(new ListCommand(this));
        this.commandHandler.registerCommand(new InfoCommand(this));
        this.commandHandler.registerCommand(new CreateCommand(this));
        this.commandHandler.registerCommand(new ImportCommand(this));
        this.commandHandler.registerCommand(new ReloadCommand(this));
        this.commandHandler.registerCommand(new SetSpawnCommand(this));
        this.commandHandler.registerCommand(new CoordCommand(this));
        this.commandHandler.registerCommand(new TeleportCommand(this));
        this.commandHandler.registerCommand(new WhoCommand(this));
        this.commandHandler.registerCommand(new SpawnCommand(this));
        // Dangerous Commands
        this.commandHandler.registerCommand(new UnloadCommand(this));
        this.commandHandler.registerCommand(new LoadCommand(this));
        this.commandHandler.registerCommand(new RemoveCommand(this));
        this.commandHandler.registerCommand(new DeleteCommand(this));
        this.commandHandler.registerCommand(new RegenCommand(this));
        this.commandHandler.registerCommand(new ConfirmCommand(this));
        // Modification commands
        this.commandHandler.registerCommand(new ModifyCommand(this));
        this.commandHandler.registerCommand(new PurgeCommand(this));
        this.commandHandler.registerCommand(new ModifyAddCommand(this));
        this.commandHandler.registerCommand(new ModifySetCommand(this));
        this.commandHandler.registerCommand(new ModifyRemoveCommand(this));
        this.commandHandler.registerCommand(new ModifyClearCommand(this));
        this.commandHandler.registerCommand(new ConfigCommand(this));
        this.commandHandler.registerCommand(new AnchorCommand(this));
        // Misc Commands
        this.commandHandler.registerCommand(new EnvironmentCommand(this));
        this.commandHandler.registerCommand(new DebugCommand(this));
        this.commandHandler.registerCommand(new GeneratorCommand(this));
        this.commandHandler.registerCommand(new CheckCommand(this));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDisable() {
        debugLog.close();
        this.banker = null;
        this.bank = null;
        log(Level.INFO, "- Disabled");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MVPlayerSession getPlayerSession(Player player) {
        if (this.playerSessions.containsKey(player.getName())) {
            return this.playerSessions.get(player.getName());
        } else {
            this.playerSessions.put(player.getName(), new MVPlayerSession(player, this.multiverseConfig, this));
            return this.playerSessions.get(player.getName());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated This is deprecated.
     */
    @Override
    @Deprecated
    public com.onarandombox.MultiverseCore.utils.SafeTTeleporter getTeleporter() {
        return new com.onarandombox.MultiverseCore.utils.SafeTTeleporter(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MVPermissions getMVPerms() {
        return this.ph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        if (!this.isEnabled()) {
            sender.sendMessage("This plugin is Disabled!");
            return true;
        }
        ArrayList<String> allArgs = new ArrayList<String>(Arrays.asList(args));
        allArgs.add(0, command.getName());
        return this.commandHandler.locateAndRunCommand(sender, allArgs, DisplayPermErrors);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(Level level, String msg) {
        staticLog(level, msg);
    }

    /**
     * Logs a message at the specified level.
     *
     * @param level The Log-{@link Level}.
     * @param msg The message to log.
     */
    public static void staticLog(Level level, String msg) {
        if (level == Level.FINE && GlobalDebug >= 1) {
            staticDebugLog(Level.INFO, msg);
            return;
        } else if (level == Level.FINER && GlobalDebug >= 2) {
            staticDebugLog(Level.INFO, msg);
            return;
        } else if (level == Level.FINEST && GlobalDebug >= 3) {
            staticDebugLog(Level.INFO, msg);
            return;
        } else if (level != Level.FINE && level != Level.FINER && level != Level.FINEST) {
            LOGGER.log(level, String.format("%s %s", LOG_TAG, msg));
            debugLog.log(level, String.format("%s %s", LOG_TAG, msg));
        }
    }

    /**
     * Print messages to the Debug Log, if the servers in Debug Mode then we also wan't to print the messages to the
     * standard Server Console.
     *
     * @param level The Log-{@link Level}
     * @param msg The message
     */
    public static void staticDebugLog(Level level, String msg) {
        LOGGER.log(level, "[MVCore-Debug] " + msg);
        debugLog.log(level, "[MVCore-Debug] " + msg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAuthors() {
        String authors = "";
        ArrayList<String> auths = this.getDescription().getAuthors();
        if (auths.size() == 0) {
            return "";
        }

        if (auths.size() == 1) {
            return auths.get(0);
        }

        for (int i = 0; i < auths.size(); i++) {
            if (i == this.getDescription().getAuthors().size() - 1) {
                authors += " and " + this.getDescription().getAuthors().get(i);
            } else {
                authors += ", " + this.getDescription().getAuthors().get(i);
            }
        }
        return authors.substring(2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandHandler getCommandHandler() {
        return this.commandHandler;
    }

    /**
     * Gets the log-tag.
     *
     * @return The log-tag
     */
    // TODO this should be static!
    public String getTag() {
        return MultiverseCore.LOG_TAG;
    }

    /**
     * Shows a message that the given world is not a MultiverseWorld.
     *
     * @param sender The {@link CommandSender} that should receive the message
     * @param worldName The name of the invalid world
     */
    public void showNotMVWorldMessage(CommandSender sender, String worldName) {
        sender.sendMessage("Multiverse doesn't know about " + ChatColor.DARK_AQUA + worldName + ChatColor.WHITE + " yet.");
        sender.sendMessage("Type " + ChatColor.DARK_AQUA + "/mv import ?" + ChatColor.WHITE + " for help!");
    }

    /**
     * Removes a player-session.
     *
     * @param player The {@link Player} that owned the session.
     */
    public void removePlayerSession(Player player) {
        if (this.playerSessions.containsKey(player.getName())) {
            this.playerSessions.remove(player.getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPluginCount() {
        return this.pluginCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementPluginCount() {
        this.pluginCount += 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decrementPluginCount() {
        this.pluginCount -= 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AllPay getBanker() {
        return this.banker;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBank(GenericBank bank) {
        this.bank = bank;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DestinationFactory getDestFactory() {
        return this.destFactory;
    }

    /**
     * This is a convenience method to allow the QueuedCommand system to call it. You should NEVER call this directly.
     *
     * @param teleporter The Person requesting that the teleport should happen.
     * @param p          Player The Person being teleported.
     * @param l          The potentially unsafe location.
     */
    public void teleportPlayer(CommandSender teleporter, Player p, Location l) {
        // This command is the override, and MUST NOT TELEPORT SAFELY
        this.getSafeTTeleporter().safelyTeleport(teleporter, p, l, false);
    }

    /**
     * Gets the server's root-folder as {@link File}.
     *
     * @return The server's root-folder
     */
    public File getServerFolder() {
        return serverFolder;
    }

    /**
     * Sets this server's root-folder.
     *
     * @param newServerFolder The new server-root
     */
    public void setServerFolder(File newServerFolder) {
        if (!newServerFolder.isDirectory())
            throw new IllegalArgumentException("That's not a folder!");

        this.serverFolder = newServerFolder;
    }

    /**
     * Initializes Spout.
     */
    public void setSpout() {
        this.spoutInterface = new SpoutInterface();
        this.commandHandler.registerCommand(new SpoutCommand(this));
    }

    /**
     * Gets our {@link SpoutInterface}.
     *
     * @return The {@link SpoutInterface} we're using.
     */
    public SpoutInterface getSpout() {
        return this.spoutInterface;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MVWorldManager getMVWorldManager() {
        return this.worldManager;
    }

    /**
     * Gets the {@link MVPlayerListener}.
     *
     * @return The {@link MVPlayerListener}.
     */
    public MVPlayerListener getPlayerListener() {
        return this.playerListener;
    }

    /**
     * Gets the {@link MVEntityListener}.
     *
     * @return The {@link MVEntityListener}.
     */
    public MVEntityListener getEntityListener() {
        return this.entityListener;
    }

    /**
     * Gets the {@link MVWeatherListener}.
     *
     * @return The {@link MVWeatherListener}.
     */
    public MVWeatherListener getWeatherListener() {
        return this.weatherListener;
    }

    /**
     * Saves the Multiverse-Config.
     *
     * @return Whether the Multiverse-Config was successfully saved
     */
    public boolean saveMVConfig() {
        try {
            this.multiverseConfig.save(new File(getDataFolder(), "config.yml"));
            return true;
        } catch (IOException e) {
            this.log(Level.SEVERE, "Could not save Multiverse config.yml config. Please check your file permissions.");
            return false;
        }
    }

    /**
     * Saves the world config.
     *
     * @return Whether the world-config was successfully saved
     */
    public boolean saveWorldConfig() {
        return this.worldManager.saveWorldsConfig();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean saveMVConfigs() {
        return this.saveMVConfig() && this.saveWorldConfig();
    }

    /**
     * NOT deprecated for the time as queued commands use this.
     * However, this is not in the API and other plugins should therefore not use it.
     *
     * @param name World to delete
     * @return True if success, false if fail.
     */
    public Boolean deleteWorld(String name) {
        return this.worldManager.deleteWorld(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean regenWorld(String name, Boolean useNewSeed, Boolean randomSeed, String seed) {
        MultiverseWorld world = this.worldManager.getMVWorld(name);
        if (world == null) {
            return false;
        }

        List<Player> ps = world.getCBWorld().getPlayers();

        if (useNewSeed) {
            // Set the worldseed.
            if (randomSeed) {
                Random random = new Random();
                Long newseed = random.nextLong();
                seed = newseed.toString();
            }
            ((WorldManager) this.worldManager).getConfigWorlds().set("worlds." + name + ".seed", seed);
        }
        if (this.worldManager.deleteWorld(name, false)) {
            this.worldManager.loadWorlds(false);
            SafeTTeleporter teleporter = this.getSafeTTeleporter();
            Location newSpawn = this.getServer().getWorld(name).getSpawnLocation();
            // Send all players that were in the old world, BACK to it!
            for (Player p : ps) {
                teleporter.safelyTeleport(null, p, newSpawn, true);
            }
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AnchorManager getAnchorManager() {
        return this.anchorManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockSafety getBlockSafety() {
        return blockSafety;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBlockSafety(BlockSafety bs) {
        this.blockSafety = bs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LocationManipulation getLocationManipulation() {
        return locationManipulation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLocationManipulation(LocationManipulation locationManipulation) {
        this.locationManipulation = locationManipulation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SafeTTeleporter getSafeTTeleporter() {
        return safeTTeleporter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSafeTTeleporter(SafeTTeleporter safeTTeleporter) {
        this.safeTTeleporter = safeTTeleporter;
    }
}
