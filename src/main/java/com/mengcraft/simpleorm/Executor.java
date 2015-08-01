package com.mengcraft.simpleorm;

import java.util.Collection;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Executor implements CommandExecutor {
    
    private final EbeanManager manager = EbeanManager.DEFAULT;
    private final Main main;

    public Executor(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
            String lable, String[] args) {
        Collection<EbeanHandler> handlers = manager.handers();
        if (handlers.size() != 0) {
            for (EbeanHandler handler : manager.handers()) {
                sender.sendMessage("[SimpleORM] " + handler);
            }
        } else {
            sender.sendMessage("[SimpleORM] No registered handler!");
        }
        return true;
    }

    public void install() {
        main.getCommand("simpleorm").setExecutor(this);
    }

}
