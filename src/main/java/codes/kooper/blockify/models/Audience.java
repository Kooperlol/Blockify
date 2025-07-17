package codes.kooper.blockify.models;

import codes.kooper.blockify.Blockify;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

@Setter
@Getter
public class Audience {
    private boolean arePlayersHidden;
    private final Set<UUID> players;
    private final Map<UUID, Float> miningSpeeds;

    /**
     * @param players The set of players
     */
    public static Audience fromPlayers(Set<Player> players) {
        return new Audience(players.stream().map(Player::getUniqueId).collect(Collectors.toSet()), false);
    }

    /**
     * @param players The set of players
     */
    public static Audience fromUUIDs(Set<UUID> players) {
        return new Audience(players, false);
    }

    /**
     * @param players The set of players
     * @param arePlayersHidden Whether the players are hidden
     */
    public static Audience fromPlayers(Set<Player> players, boolean arePlayersHidden) {
        return new Audience(players.stream().map(Player::getUniqueId).collect(Collectors.toSet()), arePlayersHidden);
    }

    /**
     * @param players The set of players
     * @param arePlayersHidden Whether the players are hidden
     */
    public static Audience fromUUIDs(Set<UUID> players, boolean arePlayersHidden) {
        return new Audience(players, arePlayersHidden);
    }

    /**
     * @param players The set of players
     * @param arePlayersHidden Whether the players are hidden
     */
    private Audience(Set<UUID> players, boolean arePlayersHidden) {
        this.players = players;
        this.arePlayersHidden = arePlayersHidden;
        this.miningSpeeds = new HashMap<>();
    }

    /**
     * @param player The player to add
     * @return The set of uuids of players
     */
    public Set<UUID> addPlayer(Player player) {
        return addPlayer(player.getUniqueId());
    }

    /**
     * @param player The uuid of a player to add
     * @return The set of uuids of players
     */
    public Set<UUID> addPlayer(UUID player) {
        players.add(player);
        return players;
    }

    /**
     * @param player The player to remove
     * @return The set of uuids of players
     */
    public Set<UUID> removePlayer(Player player) {
        return removePlayer(player.getUniqueId());
    }

    /**
     * @param player The uuid of a player to remove
     * @return The set of uuids of players
     */
    public Set<UUID> removePlayer(UUID player) {
        players.remove(player);
        return players;
    }

    /**
     * @return A set of online players in the audience
     */
    public Set<Player> getOnlinePlayers() {
        List<Player> onlinePlayers = new ArrayList<>();
        for (UUID player : players) {
            Player p = Blockify.getInstance().getServer().getPlayer(player);
            if (p != null) {
                onlinePlayers.add(p);
            }
        }
        return new HashSet<>(onlinePlayers);
    }


    /**
     * Sets the mining speed for a player
     * @param player The player
     * @param speed The speed
     */
    public void setMiningSpeed(Player player, float speed) {
        setMiningSpeed(player.getUniqueId(), speed);
    }

    /**
     * Sets the mining speed for a player
     * @param player The player's UUID
     * @param speed The speed
     */
    public void setMiningSpeed(UUID player, float speed) {
        if (speed < 0 || speed == 1) {
            Blockify.getInstance().getLogger().warning("Invalid mining speed for player " + player + ": " + speed);
            return;
        }
        miningSpeeds.put(player, speed);
    }

    /**
     * Resets the mining speed for a player
     * @param player The player
     */
    public void resetMiningSpeed(Player player) {
        resetMiningSpeed(player.getUniqueId());
    }

    /**
     * Resets the mining speed for a player
     * @param player The player's UUID
     */
    public void resetMiningSpeed(UUID player) {
        miningSpeeds.remove(player);
    }

    /**
     * Gets the mining speed of a player
     * @param player The player
     * @return The mining speed
     */
    public float getMiningSpeed(Player player) {
        return getMiningSpeed(player.getUniqueId());
    }

    /**
     * Gets the mining speed of a player
     * @param player The player's UUID
     * @return The mining speed
     */
    public float getMiningSpeed(UUID player) {
        return miningSpeeds.getOrDefault(player, 1f);
    }

}