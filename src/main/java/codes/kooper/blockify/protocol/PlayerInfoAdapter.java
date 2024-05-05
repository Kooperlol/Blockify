package codes.kooper.blockify.protocol;

import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import org.bukkit.Bukkit;

import java.util.UUID;

public class PlayerInfoAdapter extends SimplePacketListenerAbstract {

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        // Cancels the player info remove packet, so that the player is not removed from the tab list when hidden.
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_REMOVE) {
            WrapperPlayServerPlayerInfoRemove wrapper = new WrapperPlayServerPlayerInfoRemove(event);
            for (UUID uuid : wrapper.getProfileIds()) {
                if (Bukkit.getPlayer(uuid) != null) {
                    event.setCancelled(true);
                }
            }
        }
    }

}
