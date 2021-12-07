package com.mengcraft.simpleorm;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.List;

public interface IResourceManager {

    void saveResource(@NotNull String filename, boolean force);

    void saveResource(@NotNull String filename, @NotNull InputStream contents);

    void saveObject(@NotNull String filename, @NotNull Object obj);

    @Nullable InputStream getResource(@NotNull String filename);

    @Nullable <T> T getObject(@NotNull Class<T> cls, @NotNull String filename);

    void reloadConfig();

    @NotNull FileConfiguration getConfig();

    void saveDefaultConfig();

    void saveConfig();

    void saveConfig(@NotNull String filename, @NotNull FileConfiguration config);

    void sync(List<String> filenames);

    List<String> list();
}
