package codes.kooper.blockify.protocol;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.BlockifyBreakEvent;
import codes.kooper.blockify.events.BlockifyInteractEvent;
import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.models.View;
import codes.kooper.blockify.types.BlockifyPosition;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
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

            // Get stages the player is in. If the player is not in any stages, return.
            List<Stage> stages = Blockify.getInstance().getStageManager().getStages(player);
            if (stages == null || stages.isEmpty()) {
                return;
            }

            BlockifyPosition position = new BlockifyPosition(wrapper.getBlockPosition().getX(), wrapper.getBlockPosition().getY(), wrapper.getBlockPosition().getZ());

            // Loop through all stages and views to find the block
            for (Stage stage : stages) {
                for (View view : stage.getViews()) {
                    if (view.hasBlock(position)) {
                        // Get block data from view
                        BlockData blockData = view.getBlock(position);

                        // Call BlockifyInteractEvent to handle custom interaction
                        Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> new BlockifyInteractEvent(player, position, blockData, view, view.getStage()).callEvent());

                        // Check if block is breakable, if not, send block change packet to cancel the break
                        if (!view.isBreakable()) {
                            player.sendBlockChange(position.toLocation(player.getWorld()), blockData);
                            return;
                        }

                        // Block break functionality
                        if (actionType == DiggingAction.FINISHED_DIGGING || canInstantBreak(player, blockData)) {
                            Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> {
                                // Call BlockifyBreakEvent
                                BlockifyBreakEvent blockifyBreakEvent = new BlockifyBreakEvent(player, position, blockData, view, view.getStage());
                                blockifyBreakEvent.callEvent();
                                // If block is not cancelled, break the block, otherwise, revert the block
                                if (!blockifyBreakEvent.isCancelled()) {
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
    private boolean canInstantBreak(Player player, BlockData blockData) {
        return blockData.getDestroySpeed(player.getInventory().getItemInMainHand(), true) >= blockData.getMaterial().getHardness() * 30 || player.getGameMode() == GameMode.CREATIVE;
    }
}