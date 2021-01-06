package com.mengcraft.simpleorm.lib;

import com.mengcraft.simpleorm.Reflect;
import lombok.SneakyThrows;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URLClassLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created on 15-12-13.
 */
public class LibraryLoader {

    private static final ClassLoader GLOBAL_LOADER;

    static {
        ClassLoader bukkitLoader = Bukkit.class.getClassLoader();
        GLOBAL_LOADER = bukkitLoader instanceof URLClassLoader ? bukkitLoader : null;
    }

    public static void load(JavaPlugin plugin, Library library) {
        load(plugin, library, false);
    }

    @SneakyThrows
    public static void load(JavaPlugin plugin, Library library, boolean global) {
        if (library.present()) {
            plugin.getLogger().info("Library " + library + " present");
        } else {
            if (!library.isLoadable()) {
                init(plugin, library);
            }

            for (Library sub : library.getSublist()) {
                load(plugin, sub, global);
            }

            val lib = library.getFile();
            ClassLoader classLoader;
            if (global && GLOBAL_LOADER != null) {
                classLoader = GLOBAL_LOADER;
            } else {
                classLoader = Reflect.getLoader(plugin);
            }
            Utils.addUrl((URLClassLoader) classLoader, lib.toURI().toURL());

            plugin.getLogger().info("Load library " + lib + " done");
        }
    }

    @SneakyThrows
    public static void init(JavaPlugin plugin, Library library) {
        plugin.getLogger().info("Loading library " + library);

        val run = CompletableFuture.runAsync(() -> {
            while (!library.isLoadable()) {
                library.init();
            }
        });

        val lib = library.getFile();

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
