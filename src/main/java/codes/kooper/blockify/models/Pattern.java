package codes.kooper.blockify.models;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.data.BlockData;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Predicate;

@Getter
@Setter
public class Pattern {
    private final NavigableMap<Double, BlockData> blockDataMap = new TreeMap<>();
    private double totalWeight;

    /**
     * Creates a new Pattern with the given BlockData and their respective percentages.
     *
     * @param blockDataPercentages A map of BlockData and their respective percentages.
     * @throws IllegalArgumentException If the percentage values are not in the range [0.0, 1.0],
     *                                  if the map is empty, or if the sum of percentages exceeds 1.0.
     */
    public Pattern(Map<BlockData, Double> blockDataPercentages) {
        Predicate<Double> inRange = value -> value >= 0.0 && value <= 1.0;

        if (!blockDataPercentages.values().stream().allMatch(inRange)) {
            throw new IllegalArgumentException("Percentage values must be in the range [0.0, 1.0]");
        }

        if (blockDataPercentages.isEmpty()) {
            throw new IllegalArgumentException("Pattern must contain at least one BlockData with a non-zero percentage");
        }

        double sum = blockDataPercentages.values().stream().mapToDouble(value -> value).sum();
        if (Math.round(sum * 100000) / 100000.0 > 1.0) {
            throw new IllegalArgumentException("Sum of percentages must not exceed 1.0");
        }

        for (Map.Entry<BlockData, Double> entry : blockDataPercentages.entrySet()) {
            totalWeight += entry.getValue();
            blockDataMap.put(totalWeight, entry.getKey());
        }
    }

    /**
     * Returns a random BlockData from the Pattern based on the percentages.
     *
     * @return A random BlockData from the Pattern.
     */
    public BlockData getRandomBlockData() {
        double random = Math.random() * totalWeight;
        return blockDataMap.higherEntry(random).getValue();
    }
}
