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
            WrapperPlayServerChunkData chunkData = new WrapperPlayServerChunkData(event);
            int chunkX = chunkData.getColumn().getX();
            int chunkZ = chunkData.getColumn().getZ();
            List<Stage> stages = Blockify.instance.getStageManager().getStages(player.getUniqueId());

            if (stages == null || stages.isEmpty()) {
                return;
            }

            for (Stage stage : stages) {
                for (View view : stage.getViews()) {
                    BlockifyChunk blockifyChunk = new BlockifyChunk(chunkX, chunkZ);
                    if (Blockify.instance.getBlockChangeManager().getChunksBeingSent().get(player.getUniqueId()) != null && Blockify.instance.getBlockChangeManager().getChunksBeingSent().get(player.getUniqueId()).contains(blockifyChunk.getChunkKey())) {
                        return;
                    }
                    if (view.getBlocks().containsKey(blockifyChunk)) {
                        Blockify.instance.getBlockChangeManager().sendChunkPacket(stage, player, blockifyChunk, view.getBlocks());
                    }
                }
            }
        }
    }

}
