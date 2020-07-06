package com.mengcraft.simpleorm;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@RequiredArgsConstructor
public class Listeners implements Listener {

    private final ORM plugin;

    @EventHandler
    public void on(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        p.removeMetadata(ORM.PLAYER_METADATA_KEY, plugin);// Or will leads leaks
    }
}
