package com.badbones69.crazyenvoys.tasks;

import ch.jalu.configme.SettingsManager;
import com.badbones69.crazyenvoys.CrazyEnvoys;
import com.badbones69.crazyenvoys.api.CrazyManager;
import com.badbones69.crazyenvoys.api.enums.Messages;
import com.badbones69.crazyenvoys.config.ConfigManager;
import com.badbones69.crazyenvoys.config.types.ConfigKeys;
import com.ryderbelserion.fusion.paper.FusionPaper;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import com.ryderbelserion.fusion.paper.builders.folia.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guides players to the supply drop with a per-player boss bar.
 *
 * Before the event it shows the announced drop zone coordinates and a countdown,
 * during the event it shows an arrow pointing at the nearest unclaimed crate along
 * with the distance. The progress bar fills as the event approaches, then tracks
 * how close the player is to the loot.
 */
public class BossBarManager {

    private @NotNull final CrazyEnvoys plugin = CrazyEnvoys.get();

    private @NotNull final Server server = this.plugin.getServer();

    private @NotNull final FusionPaper fusion = this.plugin.getFusion();

    private @NotNull final SettingsManager config = ConfigManager.getConfig();

    private @NotNull final CrazyManager crazyManager = this.plugin.getCrazyManager();

    // Clockwise from straight ahead, one arrow per 45 degrees of relative yaw.
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private final Map<UUID, BossBar> bars = new HashMap<>();

    private ScheduledTask task;

    /**
     * Schedules the repeating update task. Call once on plugin enable.
     */
    public void start() {
        final long interval = Math.max(1, this.config.getProperty(ConfigKeys.envoys_boss_bar_update_interval));

        this.task = new FoliaScheduler(this.plugin, Scheduler.global_scheduler) {
            @Override
            public void run() {
                tick();
            }
        }.runAtFixedRate(interval, interval);
    }

    /**
     * Cancels the update task and removes every boss bar. Call on plugin disable.
     */
    public void stop() {
        try {
            this.task.cancel();
        } catch (Exception ignored) {}

        hideAll();
    }

    /**
     * Removes the boss bar from every player.
     */
    public void hideAll() {
        for (final Map.Entry<UUID, BossBar> entry : this.bars.entrySet()) {
            final Player player = this.server.getPlayer(entry.getKey());

            if (player != null) player.hideBossBar(entry.getValue());
        }

        this.bars.clear();
    }

    private void tick() {
        if (!this.config.getProperty(ConfigKeys.envoys_boss_bar_toggle)) {
            if (!this.bars.isEmpty()) hideAll();

            return;
        }

        final boolean active = this.crazyManager.isEnvoyActive();

        final int window = this.crazyManager.getTimeSeconds(this.config.getProperty(ConfigKeys.envoys_boss_bar_countdown_duration));
        final int countdownSeconds = active ? 0 : getCountdownSeconds(window);

        if (!active && countdownSeconds <= 0) {
            if (!this.bars.isEmpty()) hideAll();

            return;
        }

        final Location zone = this.crazyManager.getDropZone();

        if (zone == null || zone.getWorld() == null) {
            if (!this.bars.isEmpty()) hideAll();

            return;
        }

        // Snapshot, claims shrink the active envoy set while we iterate players.
        final List<Block> crates = active ? new ArrayList<>(this.crazyManager.getActiveEnvoys()) : new ArrayList<>();

        for (final Player player : this.server.getOnlinePlayers()) {
            if (!isEligible(player, zone)) {
                hide(player);

                continue;
            }

            if (active) {
                updateActiveBar(player, zone, crates);
            } else {
                updateCountdownBar(player, zone, countdownSeconds, window);
            }
        }

        this.bars.keySet().removeIf(uuid -> this.server.getPlayer(uuid) == null);
    }

    private int getCountdownSeconds(final int window) {
        if (!this.config.getProperty(ConfigKeys.envoys_boss_bar_countdown_toggle)) return 0;

        if (!this.config.getProperty(ConfigKeys.envoys_run_time_toggle)) return 0;

        final Calendar next = this.crazyManager.getNextEnvoy();

        if (next == null) return 0;

        final long secondsLeft = (next.getTimeInMillis() - System.currentTimeMillis()) / 1000;

        if (secondsLeft <= 0 || secondsLeft > window) return 0;

        return (int) secondsLeft;
    }

    private boolean isEligible(final Player player, final Location zone) {
        if (this.crazyManager.isIgnoringMessages(player.getUniqueId())) return false;

        if (!player.getWorld().equals(zone.getWorld())) return false;

        if (this.config.getProperty(ConfigKeys.envoys_world_messages)) {
            return this.config.getProperty(ConfigKeys.envoys_allowed_worlds).contains(player.getWorld().getName());
        }

        return true;
    }

    private void updateCountdownBar(final Player player, final Location zone, final int secondsLeft, final int window) {
        final Map<String, String> placeholders = this.crazyManager.getZonePlaceholders();

        placeholders.put("{time}", this.crazyManager.getNextEnvoyTime());
        placeholders.put("{arrow}", getArrow(player, zone));

        final float progress = window <= 0 ? 0.0f : (float) clamp(1.0 - (double) secondsLeft / window);

        show(player, Messages.boss_bar_countdown.getMessage(player, placeholders), progress);
    }

    private void updateActiveBar(final Player player, final Location zone, final List<Block> crates) {
        Location target = null;
        double best = Double.MAX_VALUE;

        for (final Block crate : crates) {
            if (!crate.getWorld().equals(player.getWorld())) continue;

            final double distance = flatDistance(player.getLocation(), crate.getLocation());

            if (distance < best) {
                best = distance;

                target = crate.getLocation();
            }
        }

        // Crates may still be falling right after the event starts.
        if (target == null) {
            target = zone;
            best = flatDistance(player.getLocation(), zone);
        }

        final Map<String, String> placeholders = this.crazyManager.getZonePlaceholders();

        placeholders.put("{arrow}", getArrow(player, target));
        placeholders.put("{distance}", String.valueOf((int) best));

        final int maxDistance = Math.max(1, this.config.getProperty(ConfigKeys.envoys_boss_bar_max_tracking_distance));

        show(player, Messages.boss_bar_active.getMessage(player, placeholders), (float) clamp(1.0 - best / maxDistance));
    }

    private void show(final Player player, final String title, final float progress) {
        final BossBar bar = this.bars.computeIfAbsent(player.getUniqueId(), uuid -> BossBar.bossBar(Component.empty(), 0.0f, getColor(), getOverlay()));

        bar.name(this.fusion.asComponent(title));
        bar.progress(progress);
        bar.color(getColor());
        bar.overlay(getOverlay());

        // Idempotent, also re-attaches the bar if the player relogged between updates.
        player.showBossBar(bar);
    }

    private void hide(final Player player) {
        final BossBar bar = this.bars.remove(player.getUniqueId());

        if (bar != null) player.hideBossBar(bar);
    }

    private String getArrow(final Player player, final Location target) {
        final Location location = player.getLocation();

        final double dx = target.getX() - location.getX();
        final double dz = target.getZ() - location.getZ();

        final double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));

        double diff = (targetYaw - location.getYaw()) % 360;

        if (diff < 0) diff += 360;

        return ARROWS[(int) Math.round(diff / 45) % 8];
    }

    private double flatDistance(final Location from, final Location to) {
        final double dx = to.getX() - from.getX();
        final double dz = to.getZ() - from.getZ();

        return Math.sqrt(dx * dx + dz * dz);
    }

    private double clamp(final double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private BossBar.Color getColor() {
        try {
            return BossBar.Color.valueOf(this.config.getProperty(ConfigKeys.envoys_boss_bar_color).toUpperCase());
        } catch (IllegalArgumentException exception) {
            return BossBar.Color.RED;
        }
    }

    private BossBar.Overlay getOverlay() {
        try {
            return BossBar.Overlay.valueOf(this.config.getProperty(ConfigKeys.envoys_boss_bar_overlay).toUpperCase());
        } catch (IllegalArgumentException exception) {
            return BossBar.Overlay.PROGRESS;
        }
    }
}
