package com.mengcraft.simpleorm;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Debugger implements CommandExecutor {
    
    private final EbeanManager manager = EbeanManager.DEFAULT;

    @Override
    public boolean onCommand(CommandSender sender, Command command,
            String lable, String[] args) {
        for (EbeanHandler handler : manager.handers()) {
            sender.sendMessage("[SimpleORM] " + handler.toString());
        }
        return true;
    }

}
