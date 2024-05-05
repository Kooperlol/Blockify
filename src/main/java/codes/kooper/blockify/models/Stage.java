package codes.kooper.blockify.models;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.types.BlockifyChunk;
import codes.kooper.blockify.types.BlockifyPosition;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class Stage {
    private final String name;
    private final World world;
    private BlockifyPosition maxPosition, minPosition;
    private final Set<View> views;
    private boolean chunkSorting = false;
    private final Audience audience;

    public Stage(String name, World world, BlockifyPosition pos1, BlockifyPosition pos2, Audience audience) {
        this.name = name;
        this.world = world;
        this.maxPosition = new BlockifyPosition(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
        this.minPosition = new BlockifyPosition(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        this.views = new HashSet<>();
        this.audience = audience;
    }

    public boolean isLocationWithin(Location location) {
        return location.getWorld().equals(world) && location.getBlockX() >= minPosition.getX() && location.getBlockX() <= maxPosition.getX() && location.getBlockY() >= minPosition.getY() && location.getBlockY() <= maxPosition.getY() && location.getBlockZ() >= minPosition.getZ() && location.getBlockZ() <= maxPosition.getZ();
    }

    public void sendBlocksToAudience() {
        ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blocks = new ConcurrentHashMap<>();
        for (View view : views) {
            blocks.putAll(view.getBlocks());
        }
        Blockify.instance.getBlockChangeManager().sendBlockChanges(this, audience, blocks);
    }

    public void addView(View view) {
        if (views.stream().anyMatch(v -> v.getName().equalsIgnoreCase(view.getName()))) {
            Blockify.instance.getLogger().warning("View with name " + view.getName() + " already exists in stage " + name + "!");
            return;
        }
        views.add(view);
    }

    public void removeView(View view) {
        views.remove(view);
    }

    public View getView(String name) {
        for (View view : views) {
            if (view.getName().equalsIgnoreCase(name)) {
                return view;
            }
        }
        return null;
    }

    /**
     * Get all chunks that are being used by this stage.
     *
     * @return Set of chunks
     */
    public Set<BlockifyChunk> getChunks() {
        Set<BlockifyChunk> chunks = new HashSet<>();
        for (int x = minPosition.getX() >> 4; x <= maxPosition.getX() >> 4; x++) {
            for (int z = minPosition.getZ() >> 4; z <= maxPosition.getZ() >> 4; z++) {
                chunks.add(new BlockifyChunk(x, z));
            }
        }
        return chunks;
    }
}