package codes.kooper.blockify.protocol;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.BlockifyPlaceEvent;
import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.models.View;
import codes.kooper.blockify.types.BlockifyPosition;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class BlockPlaceAdapter extends SimplePacketListenerAbstract {

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);
            BlockifyPosition position = new BlockifyPosition(wrapper.getBlockPosition().getX(), wrapper.getBlockPosition().getY(), wrapper.getBlockPosition().getZ());
            Player player = (Player) event.getPlayer();
            List<Stage> stages = Blockify.instance.getStageManager().getStages(player.getUniqueId());
            View view = stages.stream()
                    .flatMap(stage -> stage.getViews().stream())
                    .filter(view1 -> view1.hasBlock(position))
                    .findFirst()
                    .orElse(null);
            if (view == null) return;
            if (!view.hasBlock(position)) return;
            Bukkit.getScheduler().runTask(Blockify.instance, () -> new BlockifyPlaceEvent(player, position.toPosition(), view, view.getStage()).callEvent());
            event.setCancelled(true);
        }
    }

}
