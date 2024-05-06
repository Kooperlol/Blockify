package codes.kooper.blockify.models;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Setter
@Getter
public class Audience {
    private boolean arePlayersHidden = false;
    private final Set<UUID> players;

    /**
     * @param players The players to show the block to
     */
    public Audience(Set<UUID> players) {
        this.players = players;
    }

    /**
     * @param players The players to show the block to
     * @param arePlayersHidden Whether the players should be hidden
     */
    public Audience(Set<UUID> players, boolean arePlayersHidden) {
        this.players = players;
        this.arePlayersHidden = arePlayersHidden;
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

}