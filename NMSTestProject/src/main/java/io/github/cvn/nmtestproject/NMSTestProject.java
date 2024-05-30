package io.github.cvn.nmtestproject;

import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerInterface;
import net.minecraft.world.item.SignItem;
import org.bukkit.craftbukkit.v1_19_R3.CraftServer;
import org.bukkit.plugin.java.JavaPlugin;

public final class NMSTestProject extends JavaPlugin {
    @Override
    public void onEnable() {
        // Plugin startup logic
        CraftServer server = (CraftServer) getServer();
        MinecraftServer mcserver = server.getServer();

        mcserver.setPort(25565);
        mcserver.setUsesAuthentication(true);

        // all these things are named differently under mojang, spigot and intermediary mapped names
        ServerInterface serverInterface = (ServerInterface) mcserver;
        System.out.println(serverInterface.getServerIp());

        System.out.println(Bootstrap.class.getName());
        System.out.println(SignItem.class.getName());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
