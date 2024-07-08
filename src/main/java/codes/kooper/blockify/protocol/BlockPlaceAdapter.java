package codes.kooper.blockify.protocol;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.BlockifyPlaceEvent;
import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.models.View;
import codes.kooper.blockify.types.BlockifyPosition;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class BlockPlaceAdapter extends SimplePacketListenerAbstract {

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            // Wrapper for the packet
            WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);
            Player player = (Player) event.getPlayer();

            // Get the stages the player is in. If the player is not in any stages, return.
            List<Stage> stages = Blockify.getInstance().getStageManager().getStages(player);
            if (stages == null || stages.isEmpty()) {
                return;
            }

            BlockifyPosition position = new BlockifyPosition(wrapper.getBlockPosition().getX(), wrapper.getBlockPosition().getY(), wrapper.getBlockPosition().getZ());

            // Check if the block is in any of the views in the stages
            for (Stage stage : stages) {
                for (View view : stage.getViews()) {
                    if (view.hasBlock(position)) {
                        // Call the event and cancel the placement
                        Bukkit.getScheduler().runTask(Blockify.getInstance(), () ->  Bukkit.getPluginManager().callEvent(new BlockifyPlaceEvent(player, position.toVector(), view, stage)));
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
            Player player = (Player) event.getPlayer();

            // Get the stages the player is in. If the player is not in any stages, return.
            List<Stage> stages = Blockify.getInstance().getStageManager().getStages(player);
            if (stages == null || stages.isEmpty()) {
                return;
            }
            
            BlockifyPosition position = new BlockifyPosition(wrapper.getBlockPosition().getX(), wrapper.getBlockPosition().getY(), wrapper.getBlockPosition().getZ());
            for (Stage stage : stages) {
                for (View view : stage.getViews()) {
                    if (view.hasBlock(position)) {
                        if (wrapper.getBlockState().getType().getName().equalsIgnoreCase(view.getBlock(position).getMaterial().name())) continue;
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

}
