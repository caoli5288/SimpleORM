package com.mengcraft.simpleorm.lib;

import lombok.SneakyThrows;
import lombok.val;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created on 15-12-13.
 */
public class LibraryLoader {

    @SneakyThrows
    public static void load(JavaPlugin plugin, Library library) {
        val lib = library.getFile();

        if (!library.isLoadable()) {
            load(plugin, library, lib);
        }

        for (Library sub : library.getSublist()) {
            load(plugin, sub);
        }

        val add = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        add.setAccessible(true);
        val classLoader = (URLClassLoader) plugin.getClass().getClassLoader();
        add.invoke(classLoader, lib.toURI().toURL());

        plugin.getLogger().info("Load library " + lib + " done");
    }

    @SneakyThrows
    static void load(JavaPlugin plugin, Library library, File lib) {
        plugin.getLogger().info("Loading library " + library);

        val run = CompletableFuture.runAsync(() -> {
            while (!library.isLoadable()) {
                library.init();
            }
        });

        while (!run.isDone()) {
            try {
                run.get(1, TimeUnit.SECONDS);
            } catch (TimeoutException | InterruptedException ignore) {
            }
            plugin.getLogger().info((lib.length() / 1024) + "kb");
        }

        if (run.isCompletedExceptionally()) throw new IllegalStateException("init");
    }

}
