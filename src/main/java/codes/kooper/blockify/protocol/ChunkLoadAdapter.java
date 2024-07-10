package codes.kooper.blockify.protocol;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.types.BlockifyChunk;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class ChunkLoadAdapter extends SimplePacketListenerAbstract {

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            Player player = (Player) event.getPlayer();

            // Wrapper for the chunk data packet
            WrapperPlayServerChunkData chunkData = new WrapperPlayServerChunkData(event);
            int chunkX = chunkData.getColumn().getX();
            int chunkZ = chunkData.getColumn().getZ();

            // Get the stages the player is in. If the player is not in any stages, return.
            List<Stage> stages = Blockify.getInstance().getStageManager().getStages(player);
            if (stages == null || stages.isEmpty()) {
                return;
            }

            // Loop through the stages and views to check if the chunk is in the view.
            for (Stage stage : stages) {

                // If the chunk is not in the world, return.
                if (!stage.getWorld().equals(player.getWorld())) return;

                if (stage.getChunks().contains(new BlockifyChunk(chunkX, chunkZ))) {
                    BlockifyChunk blockifyChunk = new BlockifyChunk(chunkX, chunkZ);

                    // Cancel the packet to prevent the player from seeing the chunk
                    event.setCancelled(true);

                    // Send the chunk packet to the player
                    Bukkit.getServer().getScheduler().runTaskAsynchronously(Blockify.getInstance(), () -> Blockify.getInstance().getBlockChangeManager().sendChunkPacket(stage, player, blockifyChunk, false));
                }
            }
        }
    }

}
