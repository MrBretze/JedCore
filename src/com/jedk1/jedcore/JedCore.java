package com.jedk1.jedcore;

import com.google.common.reflect.ClassPath;
import com.jedk1.jedcore.bot.BotFireblast;
import com.jedk1.jedcore.command.Commands;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.jedk1.jedcore.listener.AbilityListener;
import com.jedk1.jedcore.listener.CommandListener;
import com.jedk1.jedcore.listener.JCListener;
import com.jedk1.jedcore.scoreboard.BendingBoard;
import com.jedk1.jedcore.task.CustomTask;
import com.jedk1.jedcore.util.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.projectkorra.projectkorra.ability.CoreAbility;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class JedCore extends JavaPlugin implements Listener
{
    public static JedCore plugin;
    public static Logger log;
    public static String dev;
    public static String version;
    public static boolean logDebug;

    @Override
    public void onEnable()
    {
        if (!isJava8orHigher())
        {
            getLogger().info("JedCore requires Java 8+! Disabling JedCore...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }


        Bukkit.getPluginManager().registerEvents(this, this);

        plugin = this;
        JedCore.log = this.getLogger();
        new JedCoreConfig(this);

        logDebug = JedCoreConfig.getConfig((World) null).getBoolean("Properties.LogDebug");

        UpdateChecker.fetch();

        if (!isSpigot())
        {
            log.info("Bukkit detected, JedCore will not function properly.");
        }

        dev = this.getDescription().getAuthors().toString().replace("[", "").replace("]", "");
        version = this.getDescription().getVersion();

        JCMethods.registerDisabledWorlds();
        CoreAbility.registerPluginAbilities(plugin, "com.jedk1.jedcore.ability");
        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(new JCListener(this), this);
        getServer().getPluginManager().registerEvents(new ChiRestrictor(), this);
        getServer().getPluginManager().registerEvents(new CooldownEnforcer(), this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new JCManager(this), 0, 1);

        BendingBoard.updateOnline();
        new Commands();

        FireTick.loadMethod();

        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                JCMethods.registerCombos();
                BendingBoard.loadOtherCooldowns();
                initializeCollisions();
            }
        }.runTaskLater(this, 1);

        try
        {
            MetricsLite metrics = new MetricsLite(this);
            metrics.start();
            log.info("Initialized Metrics.");
        } catch (IOException e)
        {
            log.info("Failed to submit statistics for MetricsLite.");
        }
    }

    public void initializeCollisions()
    {
        boolean enabled = this.getConfig().getBoolean("Properties.AbilityCollisions.Enabled");

        if (!enabled)
        {
            getLogger().info("Collisions disabled.");
            return;
        }

        try
        {
            ClassPath cp = ClassPath.from(this.getClassLoader());

            for (ClassPath.ClassInfo info : cp.getTopLevelClassesRecursive("com.jedk1.jedcore.ability"))
            {
                try
                {
                    @SuppressWarnings("unchecked")
                    Class<? extends CoreAbility> abilityClass = (Class<? extends CoreAbility>) Class.forName(info.getName());

                    if (abilityClass == null) continue;

                    CollisionInitializer initializer = new CollisionInitializer<>(abilityClass);
                    initializer.initialize();
                } catch (Exception e)
                {

                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private boolean isSpigot()
    {
        return plugin.getServer().getVersion().toLowerCase().contains("spigot");
    }

    private boolean isJava8orHigher()
    {
        String[] versionTokens = System.getProperty("java.version").split("\\.|_|-b");

        // This is usually "1", but Java 9 changes versioning to use "9".
        int main = Integer.valueOf(versionTokens[0]);

        if (main > 1)
        {
            return true;
        }

        int major = Integer.valueOf(versionTokens[1]);

        return major >= 8;
    }

    private List<EntityPlayer> zombieShootingList = new ArrayList<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (label.equalsIgnoreCase("fireblast") && sender instanceof Player)
        {
            BotFireblast.firebast((Player) sender);
            return true;
        }

        if (label.equalsIgnoreCase("fbtest") && sender instanceof Player)
        {
            if (args.length > 0)
            {
                if (args[0].equalsIgnoreCase("stop"))
                {
                    for (EntityPlayer zombie : zombieShootingList)
                        zombie.die();

                    return true;
                }
            }

            Location featLoc = ((Player) sender).getLocation();
            featLoc.setPitch(((Player) sender).getEyeLocation().getPitch());
            featLoc.setYaw(((Player) sender).getEyeLocation().getYaw());

            MinecraftServer nmsServer = ((CraftServer) Bukkit.getServer()).getServer();
            WorldServer nmsWorld = ((CraftWorld) featLoc.getWorld()).getHandle();

            GameProfile profile = new GameProfile(UUID.randomUUID(), "Fireblast Bot");

            if (zombieShootingList.size() > 0)
            {
                profile.getProperties().put("textures", zombieShootingList.get(0).getProfile().getProperties().get("textures").iterator().next());
            } else
            {

                try
                {
                    HttpURLConnection connection = (HttpURLConnection) new URL("https://sessionserver.mojang.com/session/minecraft/profile/d8a23d7ba11448bb954496fed44243eb?unsigned=false").openConnection();

                    JSONArray response = (JSONArray) ((JSONObject) new JSONParser().parse(new InputStreamReader(connection.getInputStream()))).get("properties");
                    JSONObject a = (JSONObject) response.get(0);

                    profile.getProperties().put("textures", new Property((String) a.get("name"), (String) a.get("value"), (String) a.get("signature")));


                } catch (IOException | ParseException e)
                {
                    e.printStackTrace();
                }
            }

            EntityPlayer human = new EntityPlayer(nmsServer, nmsWorld, profile,
                    new PlayerInteractManager(nmsWorld));

            Bukkit.getScheduler().runTaskLater(this, () ->
            {
                human.setLocation(featLoc.getX(), featLoc.getY(), featLoc.getZ(), featLoc.getYaw(), featLoc.getPitch());
                human.setHeadRotation(featLoc.getYaw());
                human.world.entityJoinedWorld(human, false);
                for (Player p : Bukkit.getOnlinePlayers())
                    show(p, human);

            }, 10);

            zombieShootingList.add(human);

            new CustomTask(this, 20, 50)
            {

                @Override
                public void run()
                {
                    int onLinePlayer = Bukkit.getOnlinePlayers().size();

                    if (onLinePlayer > 0)
                    {
                        BotFireblast.firebast(featLoc.clone().add(0.0D, 1.5D, 0.0D).clone(), human.getUniqueID());
                    }
                }
            };
            return true;
        }
        return false;
    }

    public void show(Player target, EntityPlayer entity)
    {
        PlayerConnection connection = ((CraftPlayer) target).getHandle().playerConnection;
        connection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, entity));
        connection.sendPacket(new PacketPlayOutNamedEntitySpawn(entity));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        for (EntityPlayer player : zombieShootingList)
            show(event.getPlayer(), player);
    }

    public void onDisable()
    {
        for (EntityPlayer zombie : zombieShootingList)
        {
            zombie.die();
        }

        zombieShootingList.clear();

        RegenTempBlock.revertAll();
        TempFallingBlock.removeAllFallingBlocks();
    }

    public static void logDebug(String message)
    {
        if (logDebug)
        {
            plugin.getLogger().info(message);
        }
    }
}
