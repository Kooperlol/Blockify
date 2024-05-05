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

    /**
     * Handle custom digging
     *
     * @param player     Player who is digging
     * @param view       View of the player
     * @param actionType DiggingAction
     * @param blockData  BlockData of the block
     * @param position   BlockifyPosition
     */
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

    /**
     * Handle normal digging
     *
     * @param player     Player who is digging
     * @param view       View of the player
     * @param actionType DiggingAction
     * @param blockData  BlockData of the block
     * @param position   BlockifyPosition
     */
    public void handleNormalDigging(Player player, View view, DiggingAction actionType, BlockData blockData, BlockifyPosition position) {
        // Check if player can instantly break block, if so, set actionType to FINISHED_DIGGING
        if (actionType == DiggingAction.START_DIGGING) {
            if (canInstantBreak(player, blockData)) {
                actionType = DiggingAction.FINISHED_DIGGING;
            }
        }

        // Block break functionality
        if (actionType == DiggingAction.FINISHED_DIGGING) {
            Bukkit.getScheduler().runTask(Blockify.instance, () -> {
                // Call BlockifyBreakEvent
                BlockifyBreakEvent ghostBreakEvent = new BlockifyBreakEvent(player, position.toPosition(), blockData, view, view.getStage());
                ghostBreakEvent.callEvent();
                // If block is not cancelled, break the block, otherwise, revert the block
                if (!ghostBreakEvent.isCancelled()) {
                    player.sendBlockChange(position.toLocation(player.getWorld()), Material.AIR.createBlockData());
                    view.setBlock(position, Material.AIR.createBlockData());
                } else {
                    player.sendBlockChange(position.toLocation(player.getWorld()), blockData);
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
    public boolean canInstantBreak(Player player, BlockData blockData) {
        return blockData.getDestroySpeed(player.getInventory().getItemInMainHand(), true) >= blockData.getMaterial().getHardness() * 30 || player.getGameMode() == GameMode.CREATIVE;
    }

    /**
     * Update block stage
     *
     * @param player    Player who is digging
     * @param position  BlockifyPosition
     * @param blockData BlockData of the block
     * @param view      View of the player
     */
    public void updateBlockStage(Player player, BlockifyPosition position, BlockData blockData, View view) {
        // Check if block stage exists, if not, return
        if (!blockStages.containsKey(position) || blockStages.get(position) == null) return;
        // Get block stage and check if it is null, if so, remove it from the map and return
        BlockifyBlockStage blockStage = blockStages.get(position);
        if (blockStage == null) {
            blockStages.remove(position);
            return;
        }
        // If the time difference between the last updated time and the current time is greater than a 1/9th of the mining time, update the block stage
        if (System.currentTimeMillis() - blockStage.getLastUpdated() > (calculateMiningTimeInMilliseconds(blockData, player) / 9) * view.getStage().getAudience().getMiningSpeed(player.getUniqueId())) {
            // Increment block stage
            blockStage.setStage((byte) (blockStage.getStage() + 1));
            // Update last updated time to current time
            blockStage.setLastUpdated(System.currentTimeMillis());
            // If block stage is greater than or equal to 9, break the block
            if (blockStage.getStage() >= 9) {
                breakCustomBlock(player, position, blockData, view);
                player.spawnParticle(Particle.BLOCK_CRACK, position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5, 10, 0, 0, 0, blockData);
                player.playSound(player.getLocation(), blockData.getSoundGroup().getBreakSound(), 1, 1);
            }
        }
        // Send block break animation packet
        WrapperPlayServerBlockBreakAnimation wrapperPlayServerBlockBreakAnimation = new WrapperPlayServerBlockBreakAnimation(new Random().nextInt(999999999) + 1000, new Vector3i(position.getX(), position.getY(), position.getZ()), blockStages.get(position).getStage());
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapperPlayServerBlockBreakAnimation);
    }

    /**
     * Breaks a custom block
     *
     * @param player     Player who is breaking the block
     * @param position   BlockifyPosition
     * @param blockData  BlockData of the block
     * @param view       View of the player
     */
    public void breakCustomBlock(Player player, BlockifyPosition position, BlockData blockData, View view) {
        // Run synchronously as using Spigot API
        Bukkit.getScheduler().runTask(Blockify.instance, () -> {
            // Call BlockifyBreakEvent
            BlockifyBreakEvent ghostBreakEvent = new BlockifyBreakEvent(player, position.toPosition(), blockData, view, view.getStage());
            ghostBreakEvent.callEvent();
            // If block stage exists, cancel the task and remove it from the map
            if (blockStages.containsKey(position)) {
                Bukkit.getScheduler().cancelTask(blockStages.get(position).getTask());
                blockStages.remove(position);
            }
            // Remove mining fatigue effect
            player.removePotionEffect(PotionEffectType.SLOW_DIGGING);
            // If block is not cancelled, break the block, otherwise, revert the block
            if (!ghostBreakEvent.isCancelled()) {
                player.sendBlockChange(position.toLocation(player.getWorld()), Material.AIR.createBlockData());
                view.setBlock(position, Material.AIR.createBlockData());
            } else {
                player.sendBlockChange(position.toLocation(player.getWorld()), blockData);
            }
        });
    }

    /**
     * Calculates the total mining time in milliseconds.
     * Referenced from <a href="https://minecraft.fandom.com/wiki/Breaking#Calculation">Minecraft Fandom</a>.
     *
     * @param block  BlockData of the block
     * @param player Player who is mining
     * @return double Time to break block in milliseconds
     */
    private double calculateMiningTimeInMilliseconds(BlockData block, Player player) {
        double speedMultiplier = 1.0;
        // Check if player is using the preferred tool
        boolean isPreferredTool = block.isPreferredTool(player.getInventory().getItemInMainHand());
        // Check if player can harvest the block
        boolean canHarvest = isPreferredTool && block.requiresCorrectToolForDrops();
        // If player is using the preferred tool, get the speed multiplier, otherwise, set it to 1.0
        if (isPreferredTool) {
            speedMultiplier = getToolSpeed(player.getInventory().getItemInMainHand(), block);
            if (!canHarvest) {
                speedMultiplier = 1.0;
            }
        }

        // Check if player is flying, if so, divide the speed multiplier by 5
        if (player.isFlying()) {
            speedMultiplier /= 5;
        }

        // Calculate the damage based on the speed multiplier and block hardness
        double damage = speedMultiplier / block.getMaterial().getHardness();

        // If player can harvest the block, divide the damage by 30, otherwise, divide it by 100
        if (canHarvest) {
            damage /= 30;
        } else {
            damage /= 100;
        }

        // Check if player can instantly break the block
        if (damage > 1) {
            return 0;
        }

        // Calculate the mining time in ticks and convert it to milliseconds
        double ticks = Math.round(1 / damage);
        double seconds = ticks / 20;
        return seconds * 1000;
    }

    /**
     * Get the tool speed.
     * Referenced from <a href="https://minecraft.fandom.com/wiki/Breaking#Speed">Minecraft Fandom</a>.
     *
     * @param item      ItemStack of the tool
     * @param blockData BlockData of the block
     * @return int Speed of the tool
     */
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