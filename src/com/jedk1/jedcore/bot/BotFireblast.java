package com.jedk1.jedcore.bot;

import com.projectkorra.projectkorra.util.ParticleEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

public class BotFireblast
{

    private static int distance = 28;

    public static void firebast(Location player, UUID shooter)
    {
        Location shootStart = player.clone();
        Location playerEyes = player.clone();
        Vector direction = playerEyes.getDirection();

        Location factor = playerEyes.clone();
        Location factorMinY = factor.clone().subtract(0.0, 0.5, 0.0);
        Location factorMaxY = factor.clone().add(0.0, 0.5, 0.0);
        Vector progress = direction.clone().multiply(0.5);

        Vector knock = playerEyes.clone().getDirection().multiply(1);

        final ArmorStand[] stand = {(ArmorStand) factor.clone().add(direction.clone().multiply(2)).getWorld().spawnEntity(factor, EntityType.ARMOR_STAND)};
        stand[0].setHelmet(new ItemStack(Material.JACK_O_LANTERN));
        stand[0].setSmall(true);
        stand[0].setVisible(false);
        stand[0].setInvulnerable(true);
        stand[0].setGravity(false);
        stand[0].setAI(false);
        stand[0].setCustomName("JedCodePluginArmorStandPasTouche");

        new Thread(() ->
        {
            while (true)
            {
                double distanceToStart = factor.distance(shootStart);

                if (distanceToStart >= 1 && distanceToStart <= 1.8)
                {
                    if (factor.getChunk().isLoaded())
                        new ParticleEffect.ParticlePacket(ParticleEffect.FLAME, 0.45F, 0.45F, 0.45F, 0, 35, true, null).sendTo(factor, 200D);
                } else if (distanceToStart >= 1.8 && distanceToStart <= (distance / 2))
                {
                    if (factor.getChunk().isLoaded())
                        new ParticleEffect.ParticlePacket(ParticleEffect.CRIT, 0.10F, 0.10F, 0.10F, 0, 10, true, null).sendTo(factor, 200D);
                    stand[0].teleport(factor.clone().subtract(0.0, 0.3, 0.0));
                } else if (distanceToStart >= (distance / 2) && distanceToStart <= (distance / 1.5))
                {
                    if (factor.getChunk().isLoaded())
                        new ParticleEffect.ParticlePacket(ParticleEffect.FLAME, 0.3F, 0.3F, 0.3F, 0, 15, true, null).sendTo(factor, 200D);
                    stand[0].teleport(factor.clone().subtract(0.0, 0.3, 0.0));
                } else if (distanceToStart >= (distance / 1.5))
                {
                    if (factor.getChunk().isLoaded())
                        new ParticleEffect.ParticlePacket(ParticleEffect.FLAME, 0.5F, 0.5F, 0.5F, 0, 15, true, null).sendTo(factor, 200D);
                    stand[0].teleport(factor.clone().subtract(0.0, 0.3, 0.0));
                } else if (distanceToStart >= (distance / 1.2) && distanceToStart <= (distance / 1.5))
                {
                    if (factor.getChunk().isLoaded())
                        new ParticleEffect.ParticlePacket(ParticleEffect.FLAME, 0.55F, 0.55F, 0.55F, 0, 15, true, null).sendTo(factor, 200D);
                    stand[0].teleport(factor.clone().subtract(0.0, 0.3, 0.0));
                }

                List<Entity> entities = applyEffect(factor, shooter, stand[0]);

                if (entities.size() > 0)
                {
                    List<Player> players = new ArrayList<>();
                    List<ArmorStand> stands = new ArrayList<>();

                    for (Entity e : entities)
                    {
                        if (e instanceof Player)
                            players.add((Player) e);
                        else if (e instanceof ArmorStand)
                            stands.add((ArmorStand) e);
                        else if (e instanceof Zombie)
                        {
                            e.setVelocity(knock);
                            ((Zombie) e).damage(3D);
                        }
                    }

                    if (stands.size() > 0)
                    {
                        for (ArmorStand armorStand : stands)
                        {
                            armorStand.remove();
                            new ParticleEffect.ParticlePacket(ParticleEffect.FLAME, 0.5F, 0.5F, 0.5F, 0.2F, 70, true, null).sendTo(armorStand.getLocation(), 200D);
                            new ParticleEffect.ParticlePacket(ParticleEffect.SMOKE, 0.2F, 0.2F, 0.2F, 0.1F, 100, true, null).sendTo(armorStand.getLocation(), 200D);
                        }
                    }

                    if (players.size() > 0)
                    {
                        for (Player p : players)
                        {
                            p.setVelocity(knock);
                            p.damage(2D);
                            playFireworkFlame(factor);
                        }
                    }

                    stand[0].remove();

                    Thread.currentThread().stop();
                    return;
                }

                factor.add(progress);
                factorMinY.add(progress);
                factorMaxY.add(progress);

                if (isWallHackBlock(factor) || isWaterBlock(factorMinY) || isWaterBlock(factorMaxY))
                {
                    stand[0].remove();

                    if (isWaterBlock(factorMinY))
                        playFireworkFlame(factorMinY);
                    else if (isWaterBlock(factorMaxY))
                        playFireworkFlame(factorMaxY);
                    else
                        playFireworkFlame(factor);

                    Thread.currentThread().stop();
                    return;
                }

                if (stand[0].isDead())
                {
                    stand[0].remove();
                    new ParticleEffect.ParticlePacket(ParticleEffect.FLAME, 0.5F, 0.5F, 0.5F, 0.2F, 70, true, null).sendTo(stand[0].getLocation(), 200D);
                    new ParticleEffect.ParticlePacket(ParticleEffect.SMOKE, 0.2F, 0.2F, 0.2F, 0.1F, 100, true, null).sendTo(stand[0].getLocation(), 200D);
                    Thread.currentThread().stop();
                    Thread.currentThread().stop();
                }

                if (distanceToStart >= distance)
                {
                    stand[0].remove();
                    Thread.currentThread().stop();
                    return;
                }

                try
                {
                    Thread.sleep(20);
                } catch (InterruptedException e)
                {
                }
            }
        }).start();
    }

    public static void firebast(Player player)
    {
        firebast(player.getEyeLocation(), player.getUniqueId());
    }

    private static void playFireworkFlame(Location location)
    {

        if (location.getBlock().getType() == Material.WATER || location.getBlock().getType() == Material.STATIONARY_WATER)
        {
            new ParticleEffect.ParticlePacket(ParticleEffect.CLOUD, 0.5F, 0.5F, 0.5F, 0.2F, 100, true, null).sendTo(location, 200D);
        } else
        {
            new ParticleEffect.ParticlePacket(ParticleEffect.FLAME, 0.5F, 0.5F, 0.5F, 0.2F, 100, true, null).sendTo(location, 200D);
        }
    }

    public static boolean isWaterBlock(Location location)
    {
        return location.getBlock().getType() == Material.WATER || location.getBlock().getType() == Material.STATIONARY_WATER;
    }

    public static boolean isWallHackBlock(Location location)
    {
        return location.getBlock().getType().isSolid() || location.getBlock().getType() == Material.WATER || location.getBlock().getType() == Material.STATIONARY_WATER;
    }

    private static ArrayList<Entity> applyEffect(Location location, UUID shooter, ArmorStand shootingArmor)
    {
        ArrayList<Entity> entities = new ArrayList<>();

        try
        {
            for (Entity nearbyEnity : location.getWorld().getNearbyEntities(location, 0.5, 0.7, 0.5))
            {
                if (!nearbyEnity.getUniqueId().toString().equalsIgnoreCase(shooter.toString()) && !nearbyEnity.getUniqueId().toString().equalsIgnoreCase(shootingArmor.getUniqueId().toString()))
                {
                    if (nearbyEnity instanceof ArmorStand)
                    {
                        ArmorStand stand = (ArmorStand) nearbyEnity;
                        if (!stand.getCustomName().equalsIgnoreCase("JedCodePluginArmorStandPasTouche"))
                            continue;

                        entities.add(stand);
                    } else if (nearbyEnity instanceof Player)
                    {
                        entities.add(nearbyEnity);
                    }
                }
            }
        } catch (NoSuchElementException e)
        {}

        return entities;
    }

}
