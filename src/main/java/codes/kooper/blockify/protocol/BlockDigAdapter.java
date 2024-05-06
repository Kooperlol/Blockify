package codes.kooper.blockify.protocol;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.BlockifyBreakEvent;
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
import org.bukkit.GameMode;
import org.bukkit.Material;
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
            List<Stage> stages = Blockify.getInstance().getStageManager().getStages(player.getUniqueId());
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
                        Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> new BlockifyInteractEvent(player, position.toPosition(), blockData, view, view.getStage()).callEvent());

                        // Check if block is breakable, if not, send block change packet to cancel the break
                        if (!view.isBreakable()) {
                            WrapperPlayServerBlockChange wrapperPlayServerBlockChange = new WrapperPlayServerBlockChange(new Vector3i(position.getX(), position.getY(), position.getZ()), SpigotConversionUtil.fromBukkitBlockData(blockData).getGlobalId());
                            PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapperPlayServerBlockChange);
                            return;
                        }

                        if (actionType == DiggingAction.START_DIGGING) {
                            if (canInstantBreak(player, blockData)) {
                                actionType = DiggingAction.FINISHED_DIGGING;
                            }
                        }

                        // Block break functionality
                        if (actionType == DiggingAction.FINISHED_DIGGING) {
                            Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> {
                                // Call BlockifyBreakEvent
                                BlockifyBreakEvent ghostBreakEvent = new BlockifyBreakEvent(player, position.toPosition(), blockData, view, view.getStage());
                                ghostBreakEvent.callEvent();
                                // If block is not cancelled, break the block, otherwise, revert the block
                                if (!ghostBreakEvent.isCancelled()) {
                                    Blockify.getInstance().getBlockChangeManager().sendBlockChange(view.getStage(), view.getStage().getAudience(), position, Material.AIR.createBlockData());
                                    view.setBlock(position, Material.AIR.createBlockData());
                                } else {
                                    player.sendBlockChange(position.toLocation(player.getWorld()), blockData);
                                }
                            });
                        }

                        return;
                    }
                }
            }
        }
    }

    /**
     * Check if player can instantly break block
     *
     * @param player    Player who is digging
     * @param blockData BlockData of the block
     * @return boolean
     */
    public boolean canInstantBreak(Player player, BlockData blockData) {
        return blockData.getDestroySpeed(player.getInventory().getItemInMainHand(), true) >= blockData.getMaterial().getHardness() * 30 || player.getGameMode() == GameMode.CREATIVE;
    }

}
