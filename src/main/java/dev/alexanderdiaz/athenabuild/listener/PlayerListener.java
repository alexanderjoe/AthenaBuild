package dev.alexanderdiaz.athenabuild.listener;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Arrays;
import java.util.List;

public class PlayerListener implements Listener {
    private final AthenaBuild plugin;

    public PlayerListener(AthenaBuild plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setGameMode(GameMode.CREATIVE);

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> playSounds(player), 20);
    }

    private void playSounds(Player player) {
        List<Sound> sounds = Arrays.asList(Sound.NOTE_PLING, Sound.NOTE_PIANO, Sound.NOTE_PIANO, Sound.NOTE_PLING, Sound.NOTE_BASS, Sound.NOTE_PIANO, Sound.NOTE_PLING, Sound.LEVEL_UP, Sound.ORB_PICKUP);
        float[] pitches = {1.5f, 1.2f, 1.2f, 1.5f, 0.8f, 1.2f, 1.5f, 1.0f, 1.2f};

        for (int i = 0; i < sounds.size(); i++) {
            int finalI = i;
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> player.playSound(player.getLocation(), sounds.get(finalI), 0.5f, pitches[finalI]), i * 4L);
        }
    }
}
