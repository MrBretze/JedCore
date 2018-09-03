package com.jedk1.jedcore;

import com.google.common.reflect.ClassPath;
import com.jedk1.jedcore.command.Commands;
import com.jedk1.jedcore.command.Fireblast;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.jedk1.jedcore.listener.AbilityListener;
import com.jedk1.jedcore.listener.CommandListener;
import com.jedk1.jedcore.listener.JCListener;
import com.jedk1.jedcore.scoreboard.BendingBoard;
import com.jedk1.jedcore.task.CustomTask;
import com.jedk1.jedcore.util.*;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class JedCore extends JavaPlugin
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

    private List<ArmorStand> standShootingList = new ArrayList<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (label.equalsIgnoreCase("fireblast") && sender instanceof Player)
        {
            Fireblast.firebast((Player) sender);
            return true;
        }

        if (label.equalsIgnoreCase("fbtest") && sender instanceof Player)
        {
            if (args.length > 0)
            {
                if (args[0].equalsIgnoreCase("stop"))
                {
                    for (ArmorStand stand : standShootingList)
                        stand.remove();

                    return true;
                }
            }

            Location featLoc = ((Player) sender).getLocation();
            ArmorStand stand = (ArmorStand) ((Player) sender).getWorld().spawnEntity(featLoc, EntityType.ARMOR_STAND);
            stand.setCustomName("JE TIRE TOUT LES 5SEC");
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);
            stand.setGravity(true);
            stand.setItemInHand(new ItemStack(Material.FISHING_ROD));
            stand.setVisible(true);

            standShootingList.add(stand);

            new CustomTask(this, 20, 5 * 20)
            {
                @Override
                public void run()
                {
                    if (stand.isDead())
                    {
                        cancel();
                        return;
                    }

                    Location location = stand.getEyeLocation();
                    UUID shooter = stand.getUniqueId();
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "FIRE !");
                    Fireblast.firebast(location.clone(), shooter);
                }
            };
            return true;
        }
        return false;
    }

    public void onDisable()
    {
        for (ArmorStand stand : standShootingList)
            stand.remove();

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
