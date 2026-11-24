package com.recipeforge;

import com.recipeforge.net.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = RecipeForge.MODID, name = "RecipeForge", version = "0.1.0", acceptedMinecraftVersions = "[1.12.2]")
public class RecipeForge {
    public static final String MODID = "recipeforge";
    public static final Logger LOG = LogManager.getLogger(MODID);
    public static SimpleNetworkWrapper NET;

    @SidedProxy(clientSide = "com.recipeforge.ClientProxy", serverSide = "com.recipeforge.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        NET = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        NET.registerMessage(ServerHandler.class, PacketToServer.class, 0, Side.SERVER);
        NET.registerMessage(ClientHandler.class, PacketToClient.class, 1, Side.CLIENT);
        proxy.preInit();
    }
}
