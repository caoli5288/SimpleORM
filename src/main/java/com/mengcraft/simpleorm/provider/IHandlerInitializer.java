package com.mengcraft.simpleorm.provider;

import com.mengcraft.simpleorm.EbeanHandler;
import org.bukkit.plugin.Plugin;

public interface IHandlerInitializer {

    void initialize(Plugin plugin, EbeanHandler handler);
}
