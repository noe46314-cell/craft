package com.recipeforge;

public class CommonProxy {
    public void preInit() {}
    /** Вызывается на клиенте при получении данных от сервера. На сервере ничего не делает. */
    public void clientMessage(String action, String data) {}
}
