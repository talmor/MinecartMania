package com.afforess.minecartmania;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.persistence.PersistenceException;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import com.afforess.minecartmania.commands.Command;
import com.afforess.minecartmania.commands.CommandType;
import com.afforess.minecartmania.config.AdminControlsSettingParser;
import com.afforess.minecartmania.config.ChestControlSettingParser;
import com.afforess.minecartmania.config.CoreSettingParser;
import com.afforess.minecartmania.config.LocaleParser;
import com.afforess.minecartmania.config.MinecartManiaConfigurationParser;
import com.afforess.minecartmania.config.Settings;
import com.afforess.minecartmania.config.SignCommandsSettingParser;
import com.afforess.minecartmania.listeners.*;
import com.afforess.minecartmaniacore.debug.MinecartManiaLogger;
import com.afforess.minecartmaniacore.entity.Item;
import com.afforess.minecartmaniacore.entity.MinecartManiaMinecartDataTable;
import com.afforess.minecartmaniacore.entity.MinecartOwner;

public class MinecartMania extends JavaPlugin {

	public static MinecartManiaLogger log = MinecartManiaLogger.getInstance();

	public static PermissionManager permissions;


	public static Plugin instance;
	public static File file;

	@Deprecated
	public static String dataDirectory = "plugins" + File.separator + "MinecartMania";



	private static final int DATABASE_VERSION = 3;

	public void onLoad() {
		setNaggable(false);
	}

	public void onEnable(){
		instance = this;
		file = this.getFile();


		MinecartManiaConfigurationParser.read("MinecartManiaConfiguration.xml", dataDirectory, new CoreSettingParser());
		MinecartManiaConfigurationParser.read("MinecartManiaLocale.xml", dataDirectory, new LocaleParser());
		//MinecartManiaConfigurationParser.read(this.getDescription().getName().replace("Reborn", "") + "Configuration.xml", MinecartManiaCore.getDataDirectoryRelativePath(), new AdminControlsSettingParser());
		//MinecartManiaConfigurationParser.read(this.getDescription().getName().replace("Reborn", "") + "Configuration.xml", MinecartManiaCore.getDataDirectoryRelativePath(), new SignCommandsSettingParser());
		//	MinecartManiaConfigurationParser.read(this.getDescription().getName().replace("Reborn", "") + "Configuration.xml", MinecartManiaCore.getDataDirectoryRelativePath(), new ChestControlSettingParser());

		permissions = new PermissionManager(getServer());

		getServer().getPluginManager().registerEvents( new MinecartManiaCoreListener(), this);
		getServer().getPluginManager().registerEvents(new ChunkListener(), this);
		getServer().getPluginManager().registerEvents( new BlockListener(), this);
		getServer().getPluginManager().registerEvents( new PlayerListener(), this);
		getServer().getPluginManager().registerEvents( new VehicleListener(), this);
		getServer().getPluginManager().registerEvents( new MinecartTimer(), this);

		//TODO - don't use these anymore
		getServer().getPluginManager().registerEvents( new SignsActionListener(), this);
		getServer().getPluginManager().registerEvents( new StationsActionListener(), this);
		getServer().getPluginManager().registerEvents( new FarmingActionListener(), this);
		getServer().getPluginManager().registerEvents( new ChestActionListener(), this);

		log.info( this.getDescription().getName() + " version " + this.getDescription().getVersion() + " is enabled!" );



		//database setup
		File ebeans = new File(new File(this.getDataFolder().getParent()).getParent(), "ebean.properties");
		if (!ebeans.exists()) {
			try {
				ebeans.createNewFile();
				PrintWriter pw = new PrintWriter(ebeans);
				pw.append("# General logging level: (none, explicit, all)");
				pw.append('\n');
				pw.append("ebean.logging=none");
				pw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		setupDatabase();

		log.info( this.getDescription().getName() + " version " + this.getDescription().getVersion() + " is enabled!" );
	}

	public void onDisable(){
		getServer().getScheduler().cancelTasks(this);
		log.info( this.getDescription().getName() + " version " + this.getDescription().getVersion() + " is disabled!" );
	}



	public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String commandLabel, String[] args) {
		String commandPrefix;
		if (commandLabel.equals("mm")) {
			if (args == null || args.length == 0) {
				return false;
			}
			commandPrefix = args[0];
			if (args.length > 1) {
				args = Arrays.copyOfRange(args, 1, args.length);
			}
			else {
				String[] temp = {};
				args = temp;
			}
		}
		else {
			commandPrefix = commandLabel;
		}
		commandPrefix = commandPrefix.toLowerCase();

		Command command = getMinecartManiaCommand(commandPrefix);
		if (command == null) {
			return false;
		}
		if (command.canExecuteCommand(sender)) {
			command.onCommand(sender, cmd, commandPrefix, args);
		}
		else {
			sender.sendMessage(LocaleParser.getTextKey("LackPermissionForCommand"));
		}


		return true;
	}

	public static Command getMinecartManiaCommand(String command) {
		for (CommandType c : CommandType.values()){
			if (c.toString().equalsIgnoreCase(command)) {
				return c.getCommand();
			}
		}
		return null;
	}


	private int getDatabaseVersion() {
		try {
			getDatabase().find(MinecartOwner.class).findRowCount();
		} catch (PersistenceException ex) {
			return 0;
		}
		try {
			getDatabase().find(MinecartManiaMinecartDataTable.class).findRowCount();
		} catch (PersistenceException ex) {
			return 1;
		}
		try {
			getDatabase().find(MinecartManiaMinecartDataTable.class).findList();
		} catch (PersistenceException ex) {
			return 2;
		}
		return DATABASE_VERSION;
	}

	protected void setupInitialDatabase() {
		try {
			getDatabase().find(MinecartOwner.class).findRowCount();
			getDatabase().find(MinecartManiaMinecartDataTable.class).findRowCount();
		}
		catch (PersistenceException ex) {
			log.info("Installing database");
			installDDL();
		}
	}

	protected void setupDatabase() {
		int version = getDatabaseVersion();
		switch(version) {
		case 0: setupInitialDatabase(); break;
		case 1: upgradeDatabase(1); break;
		case 2: upgradeDatabase(2); break;
		case 3: /*up to date database*/break;
		}
	}

	private void upgradeDatabase(int current) {
		log.info(String.format("Upgrading database from version %d to version %d", current, DATABASE_VERSION));
		if (current == 1 || current == 2) {
			this.removeDDL();
			setupInitialDatabase();
		}
		/*
		 * Add additional versions here
		 */
	}


	@Override
	public List<Class<?>> getDatabaseClasses() {
		List<Class<?>> list = new ArrayList<Class<?>>();
		list.add(MinecartOwner.class);
		list.add(MinecartManiaMinecartDataTable.class);
		return list;
	}

	public static MinecartMania getInstance() {
		return (MinecartMania)instance;
	}

	public static PluginDescriptionFile getPluginDescription() {
		return instance.getDescription();
	}

	public static File getPluginFile() {
		return file;
	}


	public static String getDataDirectoryRelativePath() {
		return dataDirectory;
	}

	public static void callEvent(Event event) {
		Bukkit.getServer().getPluginManager().callEvent(event);
	}

	private void reloadMyConfig(){
		this.saveDefaultConfig();
		this.reloadConfig();

		Settings.RangeXZ = getConfig().getInt("RangeXZ",4);
		Settings.RangeY = getConfig().getInt("RangeY",4);
		Settings.DefaultDerailedFrictionPercent = getConfig().getInt("DerailedFrictionPercent",100);
		Settings.DefaultPassengerFrictionPercent = getConfig().getInt("PassengerFrictionPercent",100);
		Settings.DefaultEmptyFrictionPercent = getConfig().getInt("EmptyFrictionPercent",100);
		Settings.KillPlayersOnTrackMinnimumSpeed = getConfig().getInt("KillPlayersOnTrackMinnimumSpeed",90);

		Settings.ClearTrack = getConfig().getBoolean("ClearItemsOnTrack",true);
		Settings.KillMobsOnTrack = getConfig().getBoolean("KillMobsOnTrack",true);
		Settings.KillPlayersOnTrack = getConfig().getBoolean("KillPlayersOnTrack",false);		
		Settings.IgnorePlayersOnTrack = getConfig().getBoolean("IgnorePlayersOnTrack", true);
		Settings.LimitedSignRange = getConfig().getBoolean("LimitedSignRange",true);
		Settings.DefaultSlowWhenEmpty = getConfig().getBoolean("SlowWhenEmpty",true); 

		Settings.DefaultMaxSpeedPercent = getConfig().getInt("MaxSpeedPercent",200);
		Settings.DisappearonDisconnect = getConfig().getBoolean("SlowWhenEmpty",true); 



		//		schematicsFolder = getConfig().getString("SchematicsFolder",this.getDataFolder() + File.separator + "schematics" + File.separator);
		//		CompleteMessage = getConfig().getString("DefaultTexts.BuildComplete","");
		//		CancelMessage = getConfig().getString("DefaultTexts.BuildCanceled","");
		//		StartedMessage = getConfig().getString("DefaultTexts.BuildStarted","");
		//		CollectingMessage =  getConfig().getString("DefaultTexts.BuildCollecting","");
		//		MarkMessage = getConfig().getString("DefaultTexts.Mark","");
		//		SurveyMessage = getConfig().getString("DefaultTexts.Survey","");
		//		SupplyListMessage = getConfig().getString("DefaultTexts.Supply_List","");
		//		SupplyNeedMessage = getConfig().getString("DefaultTexts.Supply_Need_Item","");
		//		SupplyDontNeedMessage = getConfig().getString("DefaultTexts.Supply_Dont_Need_Item","");
		//		SupplyTakenMessage = getConfig().getString("DefaultTexts.Supply_Item_Taken","");
		//		for (String M:getConfig().getStringList("MarkMaterials")){
		//			if (getMat(M) > 0) this.MarkMats.add(getMat(M));
		//		}
		//
		//		if (this.MarkMats.isEmpty()) this.MarkMats.add(Material.GLASS.getId());

	}




}
