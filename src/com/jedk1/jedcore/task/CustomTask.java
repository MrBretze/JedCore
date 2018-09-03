package com.jedk1.jedcore.task;

import org.bukkit.plugin.java.JavaPlugin;

public abstract class CustomTask implements Runnable
{
    private int id = -1;
    private JavaPlugin javaPlugin;

    public CustomTask(JavaPlugin javaPlugin, long l, long l1) {
        this.javaPlugin = javaPlugin;
        this.id = javaPlugin.getServer().getScheduler().scheduleSyncRepeatingTask(javaPlugin, this, l, l1);
    }

    public int getId() {
        return id;
    }

    public JavaPlugin getJavaPlugin() {
        return javaPlugin;
    }

    public void cancel() {
        getJavaPlugin().getServer().getScheduler().cancelTask(getId());
    }
}
