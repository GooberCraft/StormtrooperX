package com.goobercraft.stormtrooperx;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * PlaceholderAPI expansion exposing StormtrooperX state.
 *
 * <p>Available placeholders:</p>
 * <ul>
 *   <li>{@code %stormtrooperx_optout%} — {@code "true"} or {@code "false"}
 *       indicating whether the player has opted out of mob accuracy nerfs.
 *       <b>Online-only:</b> backed by {@link OptOutManager#isOptedOut(java.util.UUID)},
 *       which only consults the in-memory cache populated for online players.
 *       Offline players will resolve to {@code "false"} even if their
 *       persisted state is "opted out". Tools that need persisted state for
 *       offline players should query the database directly.</li>
 * </ul>
 *
 * <p>Registered conditionally at plugin enable when PlaceholderAPI is present.
 * {@link #persist()} returns true so the expansion survives {@code /papi reload}.</p>
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
