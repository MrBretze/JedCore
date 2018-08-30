package com.jedk1.jedcore.ability.earthbending;

import com.jedk1.jedcore.JedCore;
import com.jedk1.jedcore.collision.AABB;
import com.jedk1.jedcore.collision.CollisionDetector;
import com.jedk1.jedcore.collision.CollisionUtil;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.jedk1.jedcore.util.BlockUtil;
import com.jedk1.jedcore.util.TempFallingBlock;
import com.jedk1.jedcore.util.VersionUtil;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.ParticleEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class EarthShardMasterDeluxeEditon extends EarthAbility implements AddonAbility
{
    public static int range;
    public static int abilityRange;

    public static double normalDmg;
    public static double metalDmg;

    public double upInTheSky;

    public static int maxShards;
    public static long cooldown;

    private boolean isOld = false;
    private boolean isThrown = false;
    private List<Location> selectedBlock = new ArrayList<>();
    private double abilityCollisionRadius;
    private double entityCollisionRadius;

    private HashMap<Block, BackupBlock> tbBlockTracker = new HashMap<>();

    private HashMap<Location, BackupBlock> blockToDrop = new HashMap<>();

    private List<Block> readyBlocksTracker = new ArrayList<>();
    private List<TempFallingBlock> fallingBlocks = new ArrayList<>();

    public EarthShardMasterDeluxeEditon(Player player)
    {
        super(player);

        if (!bPlayer.canBend(this))
        {
            return;
        }

        if (hasAbility(player, EarthShardMasterDeluxeEditon.class))
        {
            for (EarthShardMasterDeluxeEditon es : EarthShardMasterDeluxeEditon.getAbilities(player, EarthShardMasterDeluxeEditon.class))
            {
                if (es.isThrown && System.currentTimeMillis() - es.getStartTime() >= 20000)
                {
                    // Remove the old instance because it got into a broken state.
                    // This shouldn't affect normal gameplay because the cooldown is long enough that the
                    // shards should have already hit their target.
                    es.remove();
                } else
                {
                    es.select();
                    return;
                }
            }
        }

        setFields();
        raiseEarthBlock(getEarthSourceBlock(range));
        start();
    }

    public void setFields()
    {
        ConfigurationSection config = JedCoreConfig.getConfig(this.player);

        range = config.getInt("Abilities.Earth.EarthShardMasterDeluxeEditon.PrepareRange");
        upInTheSky = config.getDouble("Abilities.Earth.EarthShardMasterDeluxeEditon.UpInTheSky");
        abilityRange = config.getInt("Abilities.Earth.EarthShardMasterDeluxeEditon.AbilityRange");
        normalDmg = config.getDouble("Abilities.Earth.EarthShardMasterDeluxeEditon.Damage.Normal");
        metalDmg = config.getDouble("Abilities.Earth.EarthShardMasterDeluxeEditon.Damage.Metal");
        maxShards = config.getInt("Abilities.Earth.EarthShardMasterDeluxeEditon.MaxShards");
        cooldown = config.getLong("Abilities.Earth.EarthShardMasterDeluxeEditon.Cooldown");
        abilityCollisionRadius = config.getDouble("Abilities.Earth.EarthShardMasterDeluxeEditon.AbilityCollisionRadius");
        entityCollisionRadius = config.getDouble("Abilities.Earth.EarthShardMasterDeluxeEditon.EntityCollisionRadius");
    }

    public void select()
    {
        raiseEarthBlock(getEarthSourceBlock(range));
    }

    @SuppressWarnings("deprecation")
    public void raiseEarthBlock(Block block)
    {
        if (block == null)
        {
            return;
        }

        selectedBlock.add(block.getLocation());

        if (tbBlockTracker.size() >= maxShards)
        {
            return;
        }

        Vector blockVector = block.getLocation().toVector().toBlockVector().setY(0);

        // Don't select from locations that already have an EarthShardMasterDeluxeEditon block.
        for (Block tempBlock : tbBlockTracker.keySet())
        {
            if (tempBlock.getLocation().getWorld() != block.getWorld())
            {
                continue;
            }

            Vector tempBlockVector = tempBlock.getLocation().toVector().toBlockVector().setY(0);

            if (tempBlockVector.equals(blockVector))
            {
                return;
            }
        }

        if (isEarthbendable(block))
        {
            if (isMetal(block))
            {
                playMetalbendingSound(block.getLocation());
            } else
            {
                ParticleEffect.BLOCK_CRACK.display(new ParticleEffect.BlockData(block.getType(), block.getData()), 0.5F, 0, 0.5F, 0, 30, block.getLocation().add(0.5, 1, 0.5), 20);
                playEarthbendingSound(block.getLocation());
            }

            Material material = getCorrectType(block);
            byte data = block.getData();

            if (VersionUtil.isPassiveSand(block))
            {
                VersionUtil.revertSand(block);
            }

            if (block.getRelative(BlockFace.UP).getType().isTransparent() && block.getRelative(0, 2, 0).getType().isTransparent())
            {
                ListIterator<Location> iterator = selectedBlock.listIterator();

                while (iterator.hasNext())
                {
                    Location b = iterator.next();
                    if (b.getBlockX() == block.getX() && b.getBlockZ() == block.getZ() && b.getBlockY() == block.getY())
                    {
                        iterator.remove();
                        iterator.add(b.clone().add(0, 2, 0));
                    }
                }

                isOld = true;

                new TempFallingBlock(block.getLocation().clone().add(0.5, 0.0, 0.5), material, data, new Vector(0, 0.8, 0), this);
            } else
            {
                for (int y = 1; y < 20; y++)
                {
                    if (block.getRelative(0, y, 0).getType().isTransparent())
                        break;
                    else
                    {
                        Location location = block.getRelative(0, y, 0).getLocation();
                        blockToDrop.put(location, new BackupBlock(location.getBlock()));
                    }
                }

                new TempFallingBlock(block.getLocation().clone().add(0.5, 0.0, 0.5), material, data, new Vector(0, 0.1, 0), this);
            }
            tbBlockTracker.put(block, new BackupBlock(block));
            block.setType(Material.AIR);
            block.setData((byte) 0);
        }
    }

    @SuppressWarnings("deprecation")
    public Material getCorrectType(Block block)
    {
        if (block.getType().equals(Material.SAND))
        {
            if (block.getData() == (byte) 0x1)
            {
                return Material.RED_SANDSTONE;
            }

            return Material.SANDSTONE;
        }

        if (block.getType().equals(Material.GRAVEL))
        {
            return Material.COBBLESTONE;
        }

        return block.getType();
    }

    @SuppressWarnings("deprecation")
    public void progress()
    {
        if (player == null || !player.isOnline() || player.isDead())
        {
            remove();
            return;
        }

        if (!isThrown)
        {
            if (!bPlayer.canBendIgnoreCooldowns(this))
            {
                remove();
                return;
            }

            if (tbBlockTracker.isEmpty())
            {
                remove();
                return;
            }

            for (TempFallingBlock tfb : TempFallingBlock.getFromAbility(this))
            {
                FallingBlock fb = tfb.getFallingBlock();

                for (Location selections : selectedBlock)
                {
                    if (fb.isDead() || selections.getBlockX() == fb.getLocation().getBlockX() && selections.getBlockZ() == fb.getLocation().getBlockZ() && selections.getBlockY() == fb.getLocation().getBlockY())
                    {
                        tfb.getLocation().getBlock().setType(fb.getMaterial());
                        tfb.getLocation().getBlock().setData(fb.getBlockData());

                        readyBlocksTracker.add(tfb.getLocation().getBlock());

                        tfb.remove();
                    }
                }
            }
        } else
        {
            for (TempFallingBlock tfb : TempFallingBlock.getFromAbility(this))
            {
                FallingBlock fb = tfb.getFallingBlock();

                AABB collider = BlockUtil.getFallingBlockBoundsFull(fb).scale(entityCollisionRadius * 2.0);

                CollisionDetector.checkEntityCollisions(player, collider, (e) ->
                {
                    DamageHandler.damageEntity(e, isMetal(fb.getMaterial()) ? metalDmg : normalDmg, this);
                    ((LivingEntity) e).setNoDamageTicks(0);
                    ParticleEffect.BLOCK_CRACK.display(new ParticleEffect.BlockData(fb.getMaterial(), fb.getBlockData()), 0, 0, 0, 0, 20, fb.getLocation(), 20);
                    tfb.remove();
                    return false;
                });

                if (fb.getVelocity().getY() < 0)
                {
                    for (Location selections : selectedBlock)
                    {
                        int minus = isOld ? 2 : 0;

                        selections = new Location(selections.getWorld(), selections.getBlockX(), selections.getBlockY() - minus, selections.getBlockZ());

                        if (selections.getBlockX() == fb.getLocation().getBlockX() && selections.getBlockZ() == fb.getLocation().getBlockZ() && selections.getBlockY() == fb.getLocation().getBlockY())
                        {
                            BackupBlock bkb = tbBlockTracker.get(selections.getBlock());
                            if (bkb != null)
                            {
                                selections.getBlock().setType(bkb.getType());
                                selections.getBlock().setData(bkb.getData());
                            } else
                            {
                                selections.getBlock().setType(fb.getMaterial());
                                selections.getBlock().setData(fb.getBlockData());
                            }
                        }
                    }
                }

                if (fb.isDead())
                {
                    tfb.remove();
                }
            }

            if (TempFallingBlock.getFromAbility(this).isEmpty())
            {
                remove();
            }
        }
    }

    public static void throwShard(Player player)
    {
        if (hasAbility(player, EarthShardMasterDeluxeEditon.class))
        {
            for (EarthShardMasterDeluxeEditon es : EarthShardMasterDeluxeEditon.getAbilities(player, EarthShardMasterDeluxeEditon.class))
            {
                if (!es.isThrown)
                {
                    es.throwShard();
                    break;
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    public void throwShard()
    {
        if (isThrown)
        {
            return;
        }

        Location targetLocation = VersionUtil.getTargetedLocation(player, abilityRange);

        if (GeneralMethods.getTargetedEntity(player, abilityRange, new ArrayList<>()) != null)
        {
            targetLocation = GeneralMethods.getTargetedEntity(player, abilityRange, new ArrayList<>()).getLocation();
        }

        Vector vel = null;

        for (Block tb : readyBlocksTracker)
        {
            Location target = player.getTargetBlock(null, 30).getLocation();

            if (target.getBlockX() == tb.getX() && target.getBlockY() == tb.getY() && target.getBlockZ() == tb.getZ())
            {
                vel = player.getEyeLocation().getDirection().multiply(2).add(new Vector(0, 0.2, 0));
                break;
            }

            vel = GeneralMethods.getDirection(tb.getLocation(), targetLocation).normalize().multiply(2).add(new Vector(0, 0.2, 0));
        }

        for (Block tb : readyBlocksTracker)
        {
            TempFallingBlock block;
            fallingBlocks.add(block = new TempFallingBlock(tb.getLocation(), tb.getType(), tb.getData(), vel, this));

            Bukkit.getScheduler().runTaskLater(JedCore.plugin, () ->
            {
                if (!block.getFallingBlock().isDead())
                {
                    block.getFallingBlock().setVelocity(block.getFallingBlock().getVelocity().add(new Vector(0.0, upInTheSky, 0.0)));
                }
            }, 1);
        }


        List<Location> locs = new ArrayList<>(blockToDrop.keySet());

        locs.sort((o1, o2) ->
                o1.getBlockY() > o2.getBlockY() ? 1 : -1);

        System.out.println();

        for (Location location : locs)
        {
            System.out.println(location.toString());
            World world = location.getWorld();
            Block block = location.getBlock();
            BackupBlock backupBlock = blockToDrop.get(location);
            block.setType(Material.AIR);
            block.setData((byte) 0);
            world.spawnFallingBlock(location.add(0.5, 0, 0.5), backupBlock.getMaterialData());
        }

        revertBlocks();

        isThrown = true;

        if (player.isOnline())
        {
            bPlayer.addCooldown(this);
        }
    }

    public void revertBlocks()
    {
        /*for (Block b : tbBlockTracker.keySet())
        {
            b.setType(Material.AIR);
            b.setData((byte) 0);
        }*/

        for (Block b : readyBlocksTracker)
        {
            b.setType(Material.AIR);
            b.setData((byte) 0);
        }

        tbBlockTracker.clear();
        readyBlocksTracker.clear();
    }

    @Override
    public void remove()
    {
        // Destroy any remaining falling blocks.
        for (TempFallingBlock tfb : TempFallingBlock.getFromAbility(this))
        {
            tfb.remove();
        }

        revertBlocks();

        super.remove();
    }

    @Override
    public long getCooldown()
    {
        return cooldown;
    }

    @Override
    public Location getLocation()
    {
        return null;
    }

    @Override
    public List<Location> getLocations()
    {
        return fallingBlocks.stream().map(TempFallingBlock::getLocation).collect(Collectors.toList());
    }

    @Override
    public void handleCollision(Collision collision)
    {
        CollisionUtil.handleFallingBlockCollisions(collision, fallingBlocks);
    }

    @Override
    public double getCollisionRadius()
    {
        return abilityCollisionRadius;
    }

    @Override
    public String getName()
    {
        return "EarthShardMasterDeluxeEditon";
    }

    @Override
    public boolean isHarmlessAbility()
    {
        return false;
    }

    @Override
    public boolean isSneakAbility()
    {
        return true;
    }

    @Override
    public String getAuthor()
    {
        return JedCore.dev;
    }

    @Override
    public String getVersion()
    {
        return JedCore.version;
    }

    @Override
    public String getDescription()
    {
        ConfigurationSection config = JedCoreConfig.getConfig(this.player);
        return "* JedCore Addon *\n" + config.getString("Abilities.Earth.EarthShardMasterDeluxeEditon.Description");
    }

    @Override
    public void load()
    {

    }

    @Override
    public void stop()
    {

    }

    @Override
    public boolean isEnabled()
    {
        ConfigurationSection config = JedCoreConfig.getConfig(this.player);
        return config.getBoolean("Abilities.Earth.EarthShardMasterDeluxeEditon.Enabled");
    }

    private class BackupBlock
    {
        private Material type;
        private byte data;

        public BackupBlock(Block b)
        {
            this.type = b.getType();
            this.data = b.getData();
        }

        public Material getType()
        {
            return type;
        }

        public byte getData()
        {
            return data;
        }

        public MaterialData getMaterialData()
        {
            return new MaterialData(getType(), getData());
        }
    }
}
