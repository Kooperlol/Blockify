package codes.kooper.blockify.utils;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.BlockifyBreakEvent;
import codes.kooper.blockify.models.View;
import codes.kooper.blockify.types.BlockifyBlockStage;
import codes.kooper.blockify.types.BlockifyPosition;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class MiningUtils {

    private final ConcurrentHashMap<BlockifyPosition, BlockifyBlockStage> blockStages;

    public MiningUtils() {
        this.blockStages = new ConcurrentHashMap<>();
    }

    public void handleCustomDigging(Player player, View view, DiggingAction actionType, BlockData blockData, BlockifyPosition position) {
        // Affect player with mining fatigue
        Bukkit.getScheduler().runTask(Blockify.instance, () -> player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, Integer.MAX_VALUE, -1, true, false, false)));

        // Update block stage periodically
        if (!blockStages.containsKey(position)) {
            blockStages.put(position, new BlockifyBlockStage((byte) 0, System.currentTimeMillis()));
        }
        if (blockStages.get(position).getTask() == 0) {
            blockStages.get(position).setTask(Bukkit.getScheduler().runTaskTimerAsynchronously(Blockify.instance, () -> {
                if (player.isOnline() && blockStages.containsKey(position)) {
                    updateBlockStage(player, position, blockData, view);
                }
            }, 0, 1).getTaskId());
        }

        // Check if cancelled digging
        if (actionType == DiggingAction.CANCELLED_DIGGING && blockStages.containsKey(position)) {
            Bukkit.getScheduler().cancelTask(blockStages.get(position).getTask());
            blockStages.get(position).setTask(0);
            Bukkit.getScheduler().runTask(Blockify.instance, () -> player.removePotionEffect(PotionEffectType.SLOW_DIGGING));
            return;
        }

        // Check if player can instantly break block (CREATIVE)
        if (actionType == DiggingAction.START_DIGGING && player.getGameMode() == GameMode.CREATIVE) {
            actionType = DiggingAction.FINISHED_DIGGING;
            blockStages.get(position).setStage((byte) 9);
        }

        // Block break functionality
        if (actionType == DiggingAction.FINISHED_DIGGING && blockStages.get(position).getStage() >= 9) {
            breakCustomBlock(player, position, blockData, view);
        }
    }

    public void handleNormalDigging(Player player, View view, DiggingAction actionType, BlockData blockData, BlockifyPosition position) {
        // Check if player can instantly break block
        if (actionType == DiggingAction.START_DIGGING) {
            if (canInstantBreak(player, blockData)) {
                actionType = DiggingAction.FINISHED_DIGGING;
            }
        }

        // Block break functionality
        if (actionType == DiggingAction.FINISHED_DIGGING) {
            Bukkit.getScheduler().runTask(Blockify.instance, () -> {
                BlockifyBreakEvent ghostBreakEvent = new BlockifyBreakEvent(player, position.toPosition(), blockData, view, view.getStage());
                ghostBreakEvent.callEvent();
                if (!ghostBreakEvent.isCancelled()) {
                    player.sendBlockChange(position.toLocation(player.getWorld()), Material.AIR.createBlockData());
                    view.setBlock(position, Material.AIR.createBlockData());
                } else {
                    player.sendBlockChange(position.toLocation(player.getWorld()), blockData);
                }
            });
        }
    }

    public boolean canInstantBreak(Player player, BlockData blockData) {
        return blockData.getDestroySpeed(player.getInventory().getItemInMainHand(), true) >= blockData.getMaterial().getHardness() * 30 || player.getGameMode() == GameMode.CREATIVE;
    }

    public void updateBlockStage(Player player, BlockifyPosition position, BlockData blockData, View view) {
        if (!blockStages.containsKey(position) || blockStages.get(position) == null) return;
        BlockifyBlockStage blockStage = blockStages.get(position);
        if (blockStage == null) {
            blockStages.remove(position);
            return;
        }
        if (System.currentTimeMillis() - blockStage.getLastUpdated() > (calculateMiningTimeInMilliseconds(blockData, player) / 9) * view.getStage().getAudience().getMiningSpeed(player.getUniqueId())) {
            blockStage.setStage((byte) (blockStage.getStage() + 1));
            blockStage.setLastUpdated(System.currentTimeMillis());
            if (blockStage.getStage() >= 9) {
                breakCustomBlock(player, position, blockData, view);
                player.spawnParticle(Particle.BLOCK_CRACK, position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5, 10, 0, 0, 0, blockData);
                player.playSound(player.getLocation(), blockData.getSoundGroup().getBreakSound(), 1, 1);
            }
        }
        WrapperPlayServerBlockBreakAnimation wrapperPlayServerBlockBreakAnimation = new WrapperPlayServerBlockBreakAnimation(new Random().nextInt(999999999) + 1000, new Vector3i(position.getX(), position.getY(), position.getZ()), blockStages.get(position).getStage());
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapperPlayServerBlockBreakAnimation);
    }

    public void breakCustomBlock(Player player, BlockifyPosition position, BlockData blockData, View view) {
        Bukkit.getScheduler().runTask(Blockify.instance, () -> {
            BlockifyBreakEvent ghostBreakEvent = new BlockifyBreakEvent(player, position.toPosition(), blockData, view, view.getStage());
            ghostBreakEvent.callEvent();
            if (blockStages.containsKey(position)) {
                Bukkit.getScheduler().cancelTask(blockStages.get(position).getTask());
                blockStages.remove(position);
            }
            player.removePotionEffect(PotionEffectType.SLOW_DIGGING);
            if (!ghostBreakEvent.isCancelled()) {
                player.sendBlockChange(position.toLocation(player.getWorld()), Material.AIR.createBlockData());
                view.setBlock(position, Material.AIR.createBlockData());
            } else {
                player.sendBlockChange(position.toLocation(player.getWorld()), blockData);
            }
        });
    }

    private double calculateMiningTimeInMilliseconds(BlockData block, Player player) {
        double speedMultiplier = 1.0;
        boolean isPreferredTool = block.isPreferredTool(player.getInventory().getItemInMainHand());
        boolean canHarvest = isPreferredTool && block.requiresCorrectToolForDrops();
        if (isPreferredTool) {
            speedMultiplier = getToolSpeed(player.getInventory().getItemInMainHand(), block);
            if (!canHarvest) {
                speedMultiplier = 1.0;
            }
        }

        if (player.isFlying()) {
            speedMultiplier /= 5;
        }

        double damage = speedMultiplier / block.getMaterial().getHardness();

        if (canHarvest) {
            damage /= 30;
        } else {
            damage /= 100;
        }

        if (damage > 1) {
            return 0;
        }

        double ticks = Math.round(1 / damage);
        double seconds = ticks / 20;
        return seconds * 1000;
    }

    private int getToolSpeed(ItemStack item, BlockData blockData) {
        if (item.getType().name().contains("SWORD") && blockData.getMaterial() == Material.COBWEB) {
            return 15;
        }
        return switch (item.getType()) {
            case WOODEN_PICKAXE, WOODEN_SHOVEL, WOODEN_AXE, WOODEN_SWORD -> 2;
            case STONE_PICKAXE, STONE_SHOVEL, STONE_AXE, STONE_SWORD -> 4;
            case IRON_PICKAXE, IRON_SHOVEL, IRON_AXE, IRON_SWORD -> 6;
            case GOLDEN_PICKAXE, GOLDEN_SHOVEL, GOLDEN_AXE, GOLDEN_SWORD -> 12;
            case DIAMOND_PICKAXE, DIAMOND_SHOVEL, DIAMOND_AXE, DIAMOND_SWORD -> 8;
            case SHEARS -> {
                if (blockData.getMaterial().name().contains("LEAVES")) {
                    yield 15;
                } else if (blockData.getMaterial().name().contains("WOOL")) {
                    yield 5;
                } else if (blockData.getMaterial().name().contains("CARPET")) {
                    yield 5;
                }
                yield switch (blockData.getMaterial()) {
                    case VINE, GLOW_LICHEN -> 1;
                    case COBWEB -> 15;
                    default -> 2;
                };
            }
            default -> 1;
        };
    }

}