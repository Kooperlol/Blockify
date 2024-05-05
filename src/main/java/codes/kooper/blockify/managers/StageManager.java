package codes.kooper.blockify.managers;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.CreateStageEvent;
import codes.kooper.blockify.events.DeleteStageEvent;
import codes.kooper.blockify.models.Stage;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public class StageManager {
    private final Map<String, Stage> stages = new HashMap<>();

    public void createStage(Stage stage) {
        if (stages.containsKey(stage.getName())) {
            Blockify.instance.getLogger().warning("Stage with name " + stage.getName() + " already exists!");
            return;
        }
        new CreateStageEvent(stage).callEvent();
        stages.put(stage.getName(), stage);
    }
    public Stage getStage(String name) {
        return stages.get(name);
    }

    public void deleteStage(String name) {
        new DeleteStageEvent(stages.get(name)).callEvent();
        stages.remove(name);
    }

    public boolean hasStage(String name) {
        return stages.containsKey(name);
    }
    
    public List<Stage> getStages(UUID player) {
        return stages.values().stream().filter(stage -> stage.getAudience().getPlayers().contains(player)).toList();
    }
}