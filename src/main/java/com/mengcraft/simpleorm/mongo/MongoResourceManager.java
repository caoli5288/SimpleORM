package com.mengcraft.simpleorm.mongo;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.mengcraft.simpleorm.IResourceManager;
import com.mengcraft.simpleorm.ORM;
import com.mengcraft.simpleorm.lib.Utils;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Level;

@RequiredArgsConstructor
public class MongoResourceManager implements IResourceManager {

    private static final String CONFIG_YML_FILENAME = "config.yml";

    private final JavaPlugin owner;
    private final GridFS fs;
    private FileConfiguration config;

    @Override
    public void saveResource(@NotNull String filename, boolean force) {
        Preconditions.checkArgument(!Utils.isNullOrEmpty(filename));
        InputStream stream = owner.getResource(filename);
        Objects.requireNonNull(stream, String.format("The embedded resource %s cannot be found in %s", filename, owner.getName()));

        GridFSDBFile obj = fs.findOne(filename);
        if (obj == null) {
            fs.createFile(stream, filename).save();
        } else if (force) {
            fs.remove(filename);
            fs.createFile(stream, filename).save();
        } else {
            owner.getLogger().warning(String.format("Could not save %s to file manager because already exists.", filename));
        }
    }

    @Override
    public void saveResource(@NotNull String filename, @NotNull InputStream contents) {
        Preconditions.checkArgument(!Utils.isNullOrEmpty(filename));
        Preconditions.checkNotNull(contents);

        GridFSDBFile obj = fs.findOne(filename);
        if (obj != null) {
            fs.remove(filename);
        }
        fs.createFile(contents, filename).save();
    }

    @Override
    public void saveObject(@NotNull String filename, @NotNull Object obj) {
        Preconditions.checkArgument(!Utils.isNullOrEmpty(filename));
        Preconditions.checkNotNull(obj);

        saveResource(filename, new ByteArrayInputStream(Utils.YAML.dump(ORM.serialize(obj)).getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    @Nullable
    public InputStream getResource(@NotNull String filename) {
        Preconditions.checkArgument(!Utils.isNullOrEmpty(filename));

        GridFSDBFile obj = fs.findOne(filename);
        if (obj == null) {
            return null;
        }
        return obj.getInputStream();
    }

    @Override
    public <T> @Nullable T getObject(@NotNull Class<T> cls, @NotNull String filename) {
        Preconditions.checkArgument(!Utils.isNullOrEmpty(filename));
        Preconditions.checkNotNull(cls);

        InputStream stream = getResource(filename);
        if (stream == null) {
            return null;
        }

        return ORM.deserialize(cls, Utils.YAML.load(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    @Override
    public void reloadConfig() {
        config = new YamlConfiguration();
        InputStream stream = getResource(CONFIG_YML_FILENAME);
        if (stream != null) {
            try {
                config.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (IOException | InvalidConfigurationException e) {
                owner.getLogger().log(Level.SEVERE, "Cannot load config.yml from resource manager.", e);
            }
        }

        stream = owner.getResource("config.yml");
        if (stream == null) {
            return;
        }
        config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, Charsets.UTF_8)));
    }

    @Override
    public @NotNull FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    @Override
    public void saveDefaultConfig() {
        GridFSDBFile file = fs.findOne(CONFIG_YML_FILENAME);
        if (file == null) {
            saveResource(CONFIG_YML_FILENAME, false);
        }
    }

    @Override
    public void saveConfig() {
        saveConfig(CONFIG_YML_FILENAME, getConfig());
    }

    @Override
    public void saveConfig(@NotNull String filename, @NotNull FileConfiguration config) {
        Preconditions.checkArgument(!Utils.isNullOrEmpty(filename));
        Preconditions.checkNotNull(config);

        saveResource(filename, new ByteArrayInputStream(config.saveToString().getBytes(StandardCharsets.UTF_8)));
    }
}
