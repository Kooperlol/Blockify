package codes.kooper.blockify.models;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.types.BlockifyChunk;
import codes.kooper.blockify.types.BlockifyPosition;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class Stage {
    private final String name;
    private final World world;
    private BlockifyPosition maxPosition;
    private BlockifyPosition minPosition;
    private final Set<View> views;
    private final Audience audience;

    public Stage(String name, World world, BlockifyPosition pos1, BlockifyPosition pos2, Audience audience) {
        this.name = name;
        this.world = world;
        this.maxPosition = new BlockifyPosition(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
        this.minPosition = new BlockifyPosition(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        this.views = new HashSet<>();
        this.audience = audience;
    }

    public boolean isPlayerWithinStage(Player player) {
        return player.getWorld().equals(world) && player.getLocation().getBlockX() >= minPosition.getX() && player.getLocation().getBlockX() <= maxPosition.getX() && player.getLocation().getBlockY() >= minPosition.getY() && player.getLocation().getBlockY() <= maxPosition.getY() && player.getLocation().getBlockZ() >= minPosition.getZ() && player.getLocation().getBlockZ() <= maxPosition.getZ();
    }

    public void sendBlocksToAudience() {
        Map<BlockifyPosition, BlockData> blocks = new HashMap<>();
        for (View view : views) {
            blocks.putAll(view.getMultiBlockChanges());
        }
        Blockify.instance.getBlockChangeManager().sendBlockChanges(this, audience, blocks);
    }

    public void addView(View view) {
        views.add(view);
    }

    public void removeView(View view) {
        views.remove(view);
    }

    public Set<BlockifyChunk> getChunks() {
        Set<BlockifyChunk> chunks = new HashSet<>();
        for (View view : views) {
            chunks.addAll(view.getBlocks().keySet());
        }
        return chunks;
    }
}