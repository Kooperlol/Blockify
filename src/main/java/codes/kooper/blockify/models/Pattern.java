package codes.kooper.blockify.models;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.data.BlockData;

import java.util.Map;
import java.util.function.Predicate;

@Getter
@Setter
public class Pattern {
    private Map<BlockData, Double> blockDataPercentages;

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

        this.blockDataPercentages = blockDataPercentages;
    }

    public BlockData getRandomBlockData() {
        double random = Math.random();
        double sum = 0.0;
        for (Map.Entry<BlockData, Double> entry : blockDataPercentages.entrySet()) {
            sum += entry.getValue();
            if (random < sum) {
                return entry.getKey();
            }
        }
        return null;
    }

}
