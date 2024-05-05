package codes.kooper.blockify.protocol;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.models.View;
import codes.kooper.blockify.types.BlockifyChunk;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
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
            List<Stage> stages = Blockify.getInstance().getStageManager().getStages(player.getUniqueId());
            if (stages == null || stages.isEmpty()) {
                return;
            }

            // Loop through the stages and views to check if the chunk is in the view.
            for (Stage stage : stages) {
                for (View view : stage.getViews()) {
                    BlockifyChunk blockifyChunk = new BlockifyChunk(chunkX, chunkZ);
                    // If the chunk is being sent to the player, return.
                    if (Blockify.getInstance().getBlockChangeManager().getChunksBeingSent().get(player.getUniqueId()) != null && Blockify.getInstance().getBlockChangeManager().getChunksBeingSent().get(player.getUniqueId()).contains(blockifyChunk.getChunkKey())) {
                        return;
                    }
                    // If the view contains the chunk, send the chunk's blocks to the player.
                    if (view.getBlocks().containsKey(blockifyChunk)) {
                        Blockify.getInstance().getBlockChangeManager().sendChunkPacket(stage, player, blockifyChunk, view.getBlocks());
                    }
                }
            }
        }
    }

}
