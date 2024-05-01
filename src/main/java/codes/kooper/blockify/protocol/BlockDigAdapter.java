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
            WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
            DiggingAction actionType = digging.getAction();

            Player player = (Player) event.getPlayer();
            BlockifyPosition position = new BlockifyPosition(digging.getBlockPosition().getX(), digging.getBlockPosition().getY(), digging.getBlockPosition().getZ());
            List<Stage> stages = Blockify.instance.getStageManager().getStages(player.getUniqueId());
            View view = stages.stream()
                    .flatMap(stage -> stage.getViews().stream())
                    .filter(view1 -> view1.hasBlock(position))
                    .findFirst()
                    .orElse(null);
            if (view == null) return;
            BlockData blockData = view.getBlock(position);

            if (actionType == DiggingAction.START_DIGGING) {
                Bukkit.getScheduler().runTask(Blockify.instance, () -> new BlockifyInteractEvent(player, position.toPosition(), blockData, view, view.getStage()).callEvent());
                if (player.getGameMode() == GameMode.CREATIVE ||
                        blockData.getMaterial().getHardness() == 0 ||
                        ((blockData.getDestroySpeed(player.getInventory().getItemInMainHand(), true) >= blockData.getMaterial().getHardness() * 30) && !player.isFlying() ||
                                (blockData.getDestroySpeed(player.getInventory().getItemInMainHand(), true) >= blockData.getMaterial().getHardness() * 150) && player.isFlying())) {
                    if (!view.isBreakable()) {
                        player.sendBlockChange(position.toLocation(player.getWorld()), player.getWorld().getBlockData(position.toLocation(player.getWorld())));
                        return;
                    }
                    Bukkit.getScheduler().runTask(Blockify.instance, () -> {
                        BlockifyBreakEvent ghostBreakEvent = new BlockifyBreakEvent(player, position.toPosition(), blockData, view, view.getStage());
                        ghostBreakEvent.callEvent();
                        if (!ghostBreakEvent.isCancelled()) {
                            player.sendBlockChange(position.toLocation(player.getWorld()), Material.AIR.createBlockData());
                            view.setBlock(position, Material.AIR.createBlockData());
                        }
                    });
                }
            }
        }
    }

}
