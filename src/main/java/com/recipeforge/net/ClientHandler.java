package com.recipeforge.net;

import com.recipeforge.RecipeForge;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ClientHandler implements IMessageHandler<PacketToClient, IMessage> {
    @Override
    public IMessage onMessage(PacketToClient msg, MessageContext ctx) {
        RecipeForge.proxy.clientMessage(msg.action, msg.data);
        return null;
    }
}
