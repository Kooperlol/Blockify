package codes.kooper.blockify.models;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Setter
@Getter
public class Audience {

    private boolean arePlayersHidden = false;
    private Set<UUID> players;

    public Audience(Set<UUID> players) {
        this.players = players;
    }

    public Audience(Set<UUID> players, boolean arePlayersHidden) {
        this.players = players;
        this.arePlayersHidden = arePlayersHidden;
    }

    public Set<UUID> addPlayer(UUID player) {
        players.add(player);
        return players;
    }

    public Set<UUID> removePlayer(UUID player) {
        players.remove(player);
        return players;
    }
}