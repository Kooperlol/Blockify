package codes.kooper.blockify.protocol;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.BlockifyBreakEvent;
import codes.kooper.blockify.events.BlockifyInteractEvent;
import codes.kooper.blockify.models.Stage;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Objects;

public class BlockDigAdapter extends SimplePacketListenerAbstract {

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            // Packet wrapper
            WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
            DiggingAction actionType = wrapper.getAction();

            // Extract information from wrapper
            Player player = event.getPlayer();

            // Get stages the player is in. If the player is not in any stages, return.
            List<Stage> stages = Blockify.getInstance().getStageManager().getStages(player);
            if (stages == null || stages.isEmpty()) {
                return;
            }

            BlockifyPosition position = new BlockifyPosition(wrapper.getBlockPosition().getX(), wrapper.getBlockPosition().getY(), wrapper.getBlockPosition().getZ());

            // Find the block in any stage and view using streams
            stages.stream()
                    .filter(stage -> stage.getWorld() == player.getWorld())
                    .flatMap(stage -> stage.getViews().stream())
                    .filter(view -> view.hasBlock(position)).min((view1, view2) -> Integer.compare(view2.getZIndex(), view1.getZIndex()))
                    .ifPresent(view -> {
                        // Get block data from view
                        BlockData blockData = view.getBlock(position);

                        // Call BlockifyInteractEvent to handle custom interaction
                        Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> new BlockifyInteractEvent(player, position, blockData, view, view.getStage()).callEvent());

                        // Check if block is breakable, if not, send block change packet to cancel the break
                        if (!view.isBreakable()) {
                            event.setCancelled(true);
                            return;
                        }

                        // Block break functionality
                        if (actionType == DiggingAction.FINISHED_DIGGING || canInstantBreak(player, blockData)) {
                            BlockifyBreakEvent blockifyBreakEvent = new BlockifyBreakEvent(player, position, blockData, view, view.getStage());
                            blockifyBreakEvent.callEvent();

                            // Set to air
                            view.setBlock(position, Material.AIR.createBlockData());
                            for (Player audienceMember : view.getStage().getAudience().getOnlinePlayers()) {
                                audienceMember.sendBlockChange(position.toLocation(player.getWorld()), Material.AIR.createBlockData());
                            }

                            // If block is not cancelled, break the block, otherwise, revert the block
                            if (blockifyBreakEvent.isCancelled()) {
                                for (Player audienceMember : view.getStage().getAudience().getOnlinePlayers()) {
                                    audienceMember.sendBlockChange(position.toLocation(player.getWorld()), blockData);
                                }
                                view.setBlock(position, blockData);
                            }
                        }
                    });
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
        ItemStack tool = player.getInventory().getItemInMainHand();
        int hasteLevel = player.hasPotionEffect(PotionEffectType.HASTE) ? Objects.requireNonNull(player.getPotionEffect(PotionEffectType.HASTE)).getAmplifier() + 1 : 0;

        // Creative mode always allows instant breaking
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        // Get break speed (already includes efficiency enchantment effects)
        double breakSpeed = blockData.getDestroySpeed(tool, true);

        // Apply haste effect separately
        double hasteMultiplier = 1 + (hasteLevel * 0.2);
        breakSpeed *= hasteMultiplier;

        double hardness = blockData.getMaterial().getHardness();

        // If break speed is at least 30x the hardness, it's an instant break
        return breakSpeed >= hardness * 30;
    }
}