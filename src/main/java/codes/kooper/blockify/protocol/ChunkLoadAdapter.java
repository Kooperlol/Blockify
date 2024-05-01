package codes.kooper.blockify.protocol;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.models.View;
import codes.kooper.blockify.types.BlockifyChunk;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public class ChunkLoadAdapter extends SimplePacketListenerAbstract {

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            Player player = (Player) event.getPlayer();
            WrapperPlayServerChunkData chunkData = new WrapperPlayServerChunkData(event);
            int chunkX = chunkData.getColumn().getX();
            int chunkZ = chunkData.getColumn().getZ();


            View view = Blockify.instance.getStageManager().getStages(player.getUniqueId()).stream()
                    .flatMap(stage -> stage.getViews().stream())
                    .filter(view1 -> view1.hasChunk(chunkX, chunkZ))
                    .findFirst()
                    .orElse(null);

            if (view == null) return;

            Set<BlockState> blockStates = new HashSet<>();
            view.getBlocks().get(new BlockifyChunk(chunkX, chunkZ)).forEach((position, material) -> blockStates.add(position.getBlockState(player.getWorld(), material)));
            player.sendBlockChanges(blockStates);
        }
    }

}
