package thunder.hack.features.modules.client;

import net.minecraft.block.entity.VaultBlockEntity;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public class ClientSpoof extends Module {

    public ClientSpoof() {
        super("ClientSpoof", Category.CLIENT);
    }

    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Vanilla);
    private final Setting<String> custom = new Setting<>("Client", "feather", v-> mode.getValue() == Mode.Custom);

    public enum Mode {
        Vanilla, Lunar1_20_4, Lunar1_20_1, Custom, RamdomClient, Null
    }
    private final String[] Client = {
            "lunarclient:1.21.1",
            "badlionclient:1.20.4",
            "featherclient:1.20.4",
            "labymod:1.20",
            "salwyrrclient:1.21.5",
            "optifine:1.21"
    };

    @SuppressWarnings("StringOperationCanBeSimplified")
    public String getClientName() {
        return switch (mode.getValue()) {
            case Vanilla -> "vanilla";
            case Lunar1_20_4 -> "lunarclient:1.20.4";
            case Lunar1_20_1 -> "lunarclient:1.20.1";
            case Custom -> custom.getValue().toString();
            case RamdomClient -> Client[new java.util.Random().nextInt(Client.length)];
            default -> null;
        };
    }

}
