package com.recipeforge;

import com.recipeforge.client.GuiForge;
import com.recipeforge.data.Json;
import com.recipeforge.data.ListMsg;
import com.recipeforge.net.PacketToServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class ClientProxy extends CommonProxy {
    public static KeyBinding OPEN;

    @Override
    public void preInit() {
        OPEN = new KeyBinding("key.recipeforge.open", Keyboard.KEY_K, "key.categories.recipeforge");
        ClientRegistry.registerKeyBinding(OPEN);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (OPEN.isPressed()) {
            RecipeForge.NET.sendToServer(new PacketToServer("list", ""));
        }
    }

    @Override
    public void clientMessage(final String action, final String data) {
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (!"list".equals(action)) return;
                ListMsg m = Json.GSON.fromJson(data, ListMsg.class);
                if (mc.currentScreen instanceof GuiForge) {
                    ((GuiForge) mc.currentScreen).setData(m);
                } else {
                    mc.displayGuiScreen(new GuiForge(m));
                }
            }
        });
    }
}
