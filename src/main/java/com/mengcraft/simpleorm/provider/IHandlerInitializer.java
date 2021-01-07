package com.mengcraft.simpleorm.provider;

import com.mengcraft.simpleorm.EbeanHandler;
import org.bukkit.plugin.Plugin;

public interface IHandlerInitializer {

    void initialize(Plugin plugin, EbeanHandler handler);

    default boolean isEnabled(Plugin plugin) {
        return true;
    }
}
