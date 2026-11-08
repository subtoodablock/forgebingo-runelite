package com.forgebingo;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ForgeBingoPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ForgeBingoPlugin.class);
        RuneLite.main(args);
    }
}
