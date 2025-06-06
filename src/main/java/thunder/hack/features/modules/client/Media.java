package thunder.hack.features.modules.client;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.injection.accesors.IGameMessageS2CPacket;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

import java.util.Random;

public final class Media extends Module {
    public static final Setting<Boolean> skinProtect = new Setting<>("Skin Protect", true);
    public static final Setting<Boolean> nickProtect = new Setting<>("Nick Protect", true);
    public static final Setting<Boolean> randomname = new Setting<>("Random Name", true);

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new Random();

    public Media() {
        super("Media", Category.CLIENT);
    }

    // Helper method to generate a random name
    public static String generateRandomName(int length) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return result.toString();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.@NotNull Receive e) {
        if (e.getPacket() instanceof GameMessageS2CPacket pac && nickProtect.getValue()) {
            for (PlayerListEntry ple : mc.player.networkHandler.getPlayerList()) {
                if (pac.content().getString().contains(ple.getProfile().getName())) {
                    IGameMessageS2CPacket packet = (IGameMessageS2CPacket) e.getPacket(); // Cast to the accessor interface
                    // If randomname is enabled, replace with a random name, otherwise with "Protected"
                    String replacement = randomname.getValue() ? generateRandomName(8) : "Protected";
                    packet.setContent(Text.of(pac.content().getString().replace(ple.getProfile().getName(), replacement)));
                }
            }
        }
    }
}