package codes.kooper.blockify.models;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.types.BlockifyChunk;
import codes.kooper.blockify.types.BlockifyPosition;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class Stage {
    private final String name;
    private final World world;
    private BlockifyPosition maxPosition, minPosition;
    private final Set<View> views;
    private int chunksPerTick;
    private final Audience audience;

    public Stage(String name, World world, BlockifyPosition pos1, BlockifyPosition pos2, Audience audience) {
        this.name = name;
        this.world = world;
        this.maxPosition = new BlockifyPosition(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
        this.minPosition = new BlockifyPosition(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        this.views = new HashSet<>();
        this.audience = audience;
        this.chunksPerTick = 1;
    }

    public boolean isLocationWithin(Location location) {
        return location.getWorld().equals(world)
                && location.getBlockX() >= minPosition.getX() && location.getBlockX() <= maxPosition.getX()
                && location.getBlockY() >= minPosition.getY() && location.getBlockY() <= maxPosition.getY()
                && location.getBlockZ() >= minPosition.getZ() && location.getBlockZ() <= maxPosition.getZ();
    }

    /**
     * Sends all blocks (from all views) to the audience.
     * Call this after you've done incremental updates (e.g., added/removed views for players).
     */
    public void sendBlocksToAudience() {
        Blockify.getInstance().getBlockChangeManager().sendBlockChanges(this, audience, getChunks(), false);
    }

    /**
     * Refreshes a specific set of blocks to the audience.
     * Use this after making incremental block-level changes.
     */
    public void refreshBlocksToAudience(Set<BlockifyPosition> blocks) {
        for (Player player : audience.getOnlinePlayers()) {
            Blockify.getInstance().getBlockChangeManager().sendMultiBlockChange(player, blocks);
        }
    }

    public void addView(View view) {
        if (views.stream().anyMatch(v -> v.getName().equalsIgnoreCase(view.getName()))) {
            Blockify.getInstance().getLogger().warning("View with name " + view.getName() + " already exists in stage " + name + "!");
            return;
        }
        views.add(view);
    }

    public void removeView(View view) {
        views.remove(view);
    }

    public View getView(String viewName) {
        for (View view : views) {
            if (view.getName().equalsIgnoreCase(viewName)) {
                return view;
            }
        }
        return null;
    }

    public Set<BlockifyChunk> getChunks() {
        Set<BlockifyChunk> chunks = new HashSet<>();
        for (int x = minPosition.getX() >> 4; x <= maxPosition.getX() >> 4; x++) {
            for (int z = minPosition.getZ() >> 4; z <= maxPosition.getZ() >> 4; z++) {
                chunks.add(new BlockifyChunk(x, z));
            }
        }
        return chunks;
    }

    /**
     * Add a given view to a player. Uses existing methods:
     * - Stage: getView(...)
     * - View: getBlocks() (already present in View)
     * - BlockChangeManager: addViewToPlayer(player, view)
     */
    public void addViewForPlayer(Player player, View view) {
        // This method uses BlockChangeManager's addViewToPlayer to merge the view's blocks into player's cache
        Blockify.getInstance().getBlockChangeManager().addViewToPlayer(player, view);
        // After updating what the player sees, refresh all blocks
        sendBlocksToAudience();
    }

    /**
     * Add a view to a player by name.
     */
    public void addViewForPlayer(Player player, String viewName) {
        View view = getView(viewName);
        if (view == null) {
            player.sendMessage("View not found: " + viewName);
            return;
        }
        addViewForPlayer(player, view);
    }

    /**
     * Remove a given view from a player. Uses existing methods:
     * - View: getBlocks()
     * - BlockChangeManager: removeViewFromPlayer(player, view)
     */
    public void removeViewForPlayer(Player player, View view) {
        // Remove view's blocks from player's cache
        Blockify.getInstance().getBlockChangeManager().removeViewFromPlayer(player, view);
        // Refresh all blocks for audience after removing the view
        sendBlocksToAudience();
    }

    /**
     * Remove a view from a player by name.
     */
    public void removeViewForPlayer(Player player, String viewName) {
        View view = getView(viewName);
        if (view == null) {
            player.sendMessage("View not found: " + viewName);
            return;
        }
        removeViewForPlayer(player, view);
    }
}