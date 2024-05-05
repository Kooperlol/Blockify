package codes.kooper.blockify.protocol;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.BlockifyInteractEvent;
import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.models.View;
import codes.kooper.blockify.types.BlockifyPosition;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.List;

public class BlockDigAdapter extends SimplePacketListenerAbstract {

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            // Packet wrapper
            WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
            DiggingAction actionType = wrapper.getAction();

            // Extract information from wrapper
            Player player = (Player) event.getPlayer();
            BlockifyPosition position = new BlockifyPosition(wrapper.getBlockPosition().getX(), wrapper.getBlockPosition().getY(), wrapper.getBlockPosition().getZ());

            // Get stages the player is in. If the player is not in any stages, return.
            List<Stage> stages = Blockify.instance.getStageManager().getStages(player.getUniqueId());
            if (stages == null || stages.isEmpty()) {
                return;
            }

            // Loop through all stages and views to find the block
            for (Stage stage : stages) {
                for (View view : stage.getViews()) {
                    if (view.hasBlock(position)) {
                        // Get block data from view
                        BlockData blockData = view.getBlock(position);

                        // Call BlockifyInteractEvent to handle custom interaction
                        Bukkit.getScheduler().runTask(Blockify.instance, () -> new BlockifyInteractEvent(player, position.toPosition(), blockData, view, view.getStage()).callEvent());

                        // Check if block is breakable, if not, send block change packet to cancel the break
                        if (!view.isBreakable()) {
                            WrapperPlayServerBlockChange wrapperPlayServerBlockChange = new WrapperPlayServerBlockChange(new Vector3i(position.getX(), position.getY(), position.getZ()), SpigotConversionUtil.fromBukkitBlockData(blockData).getGlobalId());
                            PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapperPlayServerBlockChange);
                            return;
                        }

                        // Check if player has custom mining speed, if so, handle custom digging, else handle normal digging
                        if (view.getStage().getAudience().getMiningSpeed(player.getUniqueId()) != 1) {
                            Blockify.instance.getMiningUtils().handleCustomDigging(player, view, actionType, blockData, position);
                        } else {
                            Blockify.instance.getMiningUtils().handleNormalDigging(player, view, actionType, blockData, position);
                        }

                        return;
                    }
                }
            }
        }
    }

}
