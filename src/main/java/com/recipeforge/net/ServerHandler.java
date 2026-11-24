package com.recipeforge.net;

import com.recipeforge.RecipeForge;
import com.recipeforge.data.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ServerHandler implements IMessageHandler<PacketToServer, IMessage> {
    @Override
    public IMessage onMessage(final PacketToServer msg, final MessageContext ctx) {
        final EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                handle(player, msg);
            }
        });
        return null;
    }

    private static boolean canEdit(EntityPlayerMP p) {
        return p.canUseCommand(2, "recipeforge");
    }

    private static ListMsg build(boolean can) {
        ListMsg m = new ListMsg();
        m.can = can;
        m.entries = RecipeStore.load();
        m.stations = RecipeStore.stations();
        return m;
    }

    private static void send(EntityPlayerMP p, ListMsg m) {
        RecipeForge.NET.sendTo(new PacketToClient("list", Json.GSON.toJson(m)), p);
    }

    private static void handle(EntityPlayerMP p, PacketToServer msg) {
        boolean can = canEdit(p);
        ListMsg m = build(can);
        try {
            if ("save".equals(msg.action)) {
                if (!can) {
                    m.msg = "Нет прав: нужен оператор (в одиночной игре — включённые читы).";
                } else {
                    Entry e = Json.GSON.fromJson(msg.data, Entry.class);
                    String id = RecipeStore.upsert(e);
                    m = build(true);
                    m.savedId = id;
                    m.msg = "Сохранено в scripts/recipeforge.zs. Применится после перезапуска (или /ct reload, если стоят ZenUtils и ZenRecipeReload).";
                }
            } else if ("delete".equals(msg.action)) {
                if (!can) {
                    m.msg = "Нет прав: нужен оператор.";
                } else {
                    RecipeStore.delete(msg.data);
                    m = build(true);
                    m.msg = "Правка удалена, скрипт обновлён.";
                }
            }
        } catch (Exception ex) {
            RecipeForge.LOG.error("RecipeForge: ошибка", ex);
            m.msg = "Ошибка: " + ex.getMessage();
        }
        send(p, m);
    }
}
