package codes.kooper.blockify.models;

import codes.kooper.blockify.Blockify;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Setter
@Getter
public class Audience {
    private boolean arePlayersHidden = false;
    private final Set<UUID> players;
    private final Map<UUID, Float> miningSpeeds;

    /**
     * @param players The players to show the block to
     */
    public Audience(Set<UUID> players) {
        this.players = players;
        this.miningSpeeds = new HashMap<>();
    }

    /**
     * @param players The players to show the block to
     * @param arePlayersHidden Whether the players should be hidden
     */
    public Audience(Set<UUID> players, boolean arePlayersHidden) {
        this.players = players;
        this.arePlayersHidden = arePlayersHidden;
        this.miningSpeeds = new HashMap<>();
    }

    /**
     * @param player The player to add
     * @return The set of players
     */
    public Set<UUID> addPlayer(UUID player) {
        players.add(player);
        return players;
    }

    /**
     * @param player The player to remove
     * @return The set of players
     */
    public Set<UUID> removePlayer(UUID player) {
        players.remove(player);
        return players;
    }

    /**
     * Sets the mining speed for a player
     * @param player The player
     * @param speed The speed
     */
    public void setMiningSpeed(UUID player, float speed) {
        if (speed <= 0 || speed == 1) {
            Blockify.instance.getLogger().warning("Invalid mining speed for player " + player + ": " + speed);
            return;
        }
        miningSpeeds.put(player, speed);
    }

    /**
     * Resets the mining speed for a player
     * @param player The player
     */
    public void resetMiningSpeed(UUID player) {
        miningSpeeds.remove(player);
    }

    /**
     * Gets the mining speed of a player
     * @param player The player
     * @return The mining speed
     */
    public float getMiningSpeed(UUID player) {
        return miningSpeeds.getOrDefault(player, 1f);
    }

}