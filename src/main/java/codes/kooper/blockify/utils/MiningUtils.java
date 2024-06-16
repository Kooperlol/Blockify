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
import lombok.Getter;
import net.minecraft.world.item.enchantment.Enchantments;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_19_R3.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Getter
public class MiningUtils {
    private final ConcurrentHashMap<View, ConcurrentHashMap<BlockifyPosition, BlockifyBlockStage>> blockStages;

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
        Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, Integer.MAX_VALUE, -1, true, false, false)));

        // Update block stage periodically
        if (!blockStages.containsKey(view) || blockStages.get(view) == null || blockStages.get(view).get(position) == null) {
            blockStages.putIfAbsent(view, new ConcurrentHashMap<>());
            blockStages.get(view).put(position, new BlockifyBlockStage(ThreadLocalRandom.current().nextInt(999999999) + 1000, (byte) 0, System.currentTimeMillis()));
        }
        if (blockStages.get(view).get(position).getTask() == 0) {
            blockStages.get(view).get(position).setTask(Bukkit.getScheduler().runTaskTimerAsynchronously(Blockify.getInstance(), () -> {
                if (player.isOnline() && blockStages.get(view).containsKey(position)) {
                    updateBlockStage(player, position, blockData, view);
                }
            }, 0, 1).getTaskId());
        }

        // Check if cancelled digging
        if (actionType == DiggingAction.CANCELLED_DIGGING && blockStages.get(view).containsKey(position)) {
            Bukkit.getScheduler().cancelTask(blockStages.get(view).get(position).getTask());
            blockStages.get(view).get(position).setTask(0);
            Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> player.removePotionEffect(PotionEffectType.SLOW_DIGGING));
            return;
        }

        // Check if player can instantly break block (CREATIVE) or if mining speed is 0
        if (actionType == DiggingAction.START_DIGGING && (player.getGameMode() == GameMode.CREATIVE || view.getStage().getAudience().getMiningSpeed(player) == 0)) {
            actionType = DiggingAction.FINISHED_DIGGING;
            blockStages.get(view).get(position).setStage((byte) 9);
            if (view.getStage().getAudience().getMiningSpeed(player) == 0) {
                for (Player member : view.getStage().getAudience().getOnlinePlayers()) {
                    WrapperPlayServerBlockBreakAnimation wrapperPlayServerBlockBreakAnimation = new WrapperPlayServerBlockBreakAnimation(blockStages.get(view).get(position).getEntityId(), new Vector3i(position.getX(), position.getY(), position.getZ()), (byte) 9);
                    PacketEvents.getAPI().getPlayerManager().getUser(member).writePacket(wrapperPlayServerBlockBreakAnimation);
                }
            }
        }

        // Block break functionality (CREATIVE)
        if (actionType == DiggingAction.FINISHED_DIGGING && blockStages.get(view).get(position).getStage() >= 9 && player.getGameMode() == GameMode.CREATIVE) {
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
        // Block break functionality
        if (actionType == DiggingAction.FINISHED_DIGGING || canInstantBreak(player, blockData)) {
            Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> {
                // Call BlockifyBreakEvent
                BlockifyBreakEvent blockifyBreakEvent = new BlockifyBreakEvent(player, position.toPosition(), blockData, view, view.getStage());
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
    }

    /**
     * Check if player can instantly break block
     *
     * @param player    Player who is digging
     * @param blockData BlockData of the block
     * @return boolean
     */
    public boolean canInstantBreak(Player player, BlockData blockData) {
        return getDestroySpeed(player.getInventory().getItemInMainHand(), blockData) >= blockData.getMaterial().getHardness() * 30 || player.getGameMode() == GameMode.CREATIVE;
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
        if (!blockStages.containsKey(view) || blockStages.get(view) == null || !blockStages.get(view).containsKey(position)) return;
        // Get block stage and check if it is null, if so, remove it from the map and return
        BlockifyBlockStage blockStage = blockStages.get(view).get(position);
        if (blockStage == null) {
            blockStages.get(view).remove(position);
            if (blockStages.get(view).isEmpty()) {
                blockStages.remove(view);
            }
            return;
        }
        // If the time difference between the last updated time and the current time is greater than a 1/9th of the mining time, update the block stage
        if (System.currentTimeMillis() - blockStage.getLastUpdated() > (calculateMiningTimeInMilliseconds(blockData, player) / 9) * view.getStage().getAudience().getMiningSpeed(player)) {
            // Increment block stage
            blockStage.setStage((byte) (blockStage.getStage() + 1));
            // Update last updated time to current time
            blockStage.setLastUpdated(System.currentTimeMillis());
            // If block stage is 9, break the block
            if (blockStage.getStage() == 9) {
                breakCustomBlock(player, position, blockData, view);
                Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> {
                    player.spawnParticle(Particle.BLOCK_CRACK, position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5, 10, 0, 0, 0, blockData);
                    player.playSound(player.getLocation(), blockData.getSoundGroup().getBreakSound(), 1, 1);
                });
            }
        }
        // Send block break animation packet
        if (!blockStages.containsKey(view) || blockStages.get(view) == null || !blockStages.get(view).containsKey(position) || blockStages.get(view).get(position).getStage() >= 9) return;
        for (Player member : view.getStage().getAudience().getOnlinePlayers()) {
            WrapperPlayServerBlockBreakAnimation wrapperPlayServerBlockBreakAnimation = new WrapperPlayServerBlockBreakAnimation(blockStages.get(view).get(position).getEntityId(), new Vector3i(position.getX(), position.getY(), position.getZ()), blockStages.get(view).get(position).getStage());
            PacketEvents.getAPI().getPlayerManager().sendPacket(member, wrapperPlayServerBlockBreakAnimation);
        }
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
        Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> {
            // Call BlockifyBreakEvent
            BlockifyBreakEvent blockifyBreakEvent = new BlockifyBreakEvent(player, position.toPosition(), blockData, view, view.getStage());
            blockifyBreakEvent.callEvent();
            // If block stage exists, cancel the task and remove it from the map
            resetViewBlockAnimation(view, Set.of(position));
            // Remove mining fatigue effect
            player.removePotionEffect(PotionEffectType.SLOW_DIGGING);
            // If block is not cancelled, break the block, otherwise, revert the block
            if (!blockifyBreakEvent.isCancelled()) {
                Blockify.getInstance().getBlockChangeManager().sendBlockChange(view.getStage(), view.getStage().getAudience(), position, Material.AIR.createBlockData());
                view.setBlock(position, Material.AIR.createBlockData());
            } else {
                player.sendBlockChange(position.toLocation(player.getWorld()), blockData);
            }
        });
    }

    /**
     * Reset view's block animation at the specified positions
     *
     * @param view      View of the player
     * @param positions Set of BlockifyPosition
     */
    public void resetViewBlockAnimation(View view, Set<BlockifyPosition> positions) {
        if (!blockStages.containsKey(view) || blockStages.get(view) == null) return;
        for (BlockifyPosition position : positions) {
            if (blockStages.get(view).containsKey(position)) {
                Bukkit.getScheduler().cancelTask(blockStages.get(view).get(position).getTask());
                int id = blockStages.get(view).remove(position).getEntityId();
                for (Player member : view.getStage().getAudience().getOnlinePlayers()) {
                    WrapperPlayServerBlockBreakAnimation wrapperPlayServerBlockBreakAnimation = new WrapperPlayServerBlockBreakAnimation(id, new Vector3i(position.getX(), position.getY(), position.getZ()), (byte) -1);
                    PacketEvents.getAPI().getPlayerManager().getUser(member).writePacket(wrapperPlayServerBlockBreakAnimation);
                }
                if (blockStages.get(view).isEmpty()) {
                    blockStages.remove(view);
                    return;
                }
            }
        }
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
        // Efficiency level
        int efficiencyLevel = player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.DIG_SPEED);
        // Check if player can harvest the block
        boolean canHarvest = isPreferredTool && block.requiresCorrectToolForDrops();
        // If player is using the preferred tool, get the speed multiplier, otherwise, set it to 1.0
        if (isPreferredTool) {
            speedMultiplier = getToolSpeed(player.getInventory().getItemInMainHand(), block);
            // If player can't harvest the block, set the speed multiplier to 1.0
            if (!canHarvest) {
                speedMultiplier = 1.0;
            } else if (efficiencyLevel > 0) {
                // If player can harvest the block and has efficiency level, increase the speed multiplier
                speedMultiplier += Math.pow(efficiencyLevel, 2) + 1;
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

    private double getDestroySpeed(ItemStack itemStack, BlockData blockData) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        float speed = nmsItemStack.getDestroySpeed(CraftBlockData.newData(blockData.getMaterial(), null).getState());
        if (speed > 1.0F) {
            int enchantLevel = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, nmsItemStack);
            if (enchantLevel > 0) {
                speed += enchantLevel * enchantLevel + 1;
            }
        }
        return speed;
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