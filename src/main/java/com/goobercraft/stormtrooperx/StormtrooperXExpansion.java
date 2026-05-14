package com.goobercraft.stormtrooperx;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * PlaceholderAPI expansion exposing StormtrooperX state. Registered at plugin
 * enable when PlaceholderAPI is present; {@link #persist()} keeps it across
 * {@code /papi reload}.
 *
 * <p>{@code %stormtrooperx_optout%} — {@code "true"}/{@code "false"} for whether
 * the player has opted out. <b>Online-only:</b> backed by
 * {@link OptOutManager#isOptedOut(java.util.UUID)}, so offline players resolve
 * to {@code "false"} regardless of persisted state.</p>
 */
public class StormtrooperXExpansion extends PlaceholderExpansion {

    private final StormtrooperX plugin;
    private final OptOutManager optOutManager;

    public StormtrooperXExpansion(StormtrooperX plugin, OptOutManager optOutManager) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        if (optOutManager == null) {
            throw new IllegalArgumentException("optOutManager cannot be null");
        }
        this.plugin = plugin;
        this.optOutManager = optOutManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "stormtrooperx";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Survive {@code /papi reload}. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        if (params.equalsIgnoreCase("optout")) {
            return Boolean.toString(optOutManager.isOptedOut(player.getUniqueId()));
        }
        // Unknown placeholder — return null so PAPI leaves the literal text in place
        return null;
    }
}
