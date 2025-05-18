package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import thunder.hack.events.impl.EventTick;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.setting.impl.ColorSetting;
import thunder.hack.utility.player.PlayerEntityCopy;
import thunder.hack.utility.render.Render3DEngine;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

public class Blink extends Module {
    public Blink() {
        super("Blink", Category.MOVEMENT);
    }

    private final Setting<Boolean> pulse = new Setting<>("Pulse", false);
    private final Setting<Integer> pulsePackets = new Setting<>("PulsePackets", 20, 1, 1000, v -> pulse.getValue());
    private final Setting<Boolean> autoDisable = new Setting<>("AutoDisable", false);
    private final Setting<Boolean> disableOnVelocity = new Setting<>("DisableOnVelocity", false);
    private final Setting<Boolean> clearOnVelocity = new Setting<>("ClearOnVelocity", true, v -> false);
    private final Setting<Boolean> stopOnInteraction = new Setting<>("StopOnInteraction", true);
    private final Setting<Integer> disablePackets = new Setting<>("DisablePackets", 17, 1, 1000, v -> autoDisable.getValue());
    private final Setting<Boolean> render = new Setting<>("Render", true);
    private final Setting<RenderMode> renderMode = new Setting<>("Render Mode", RenderMode.Circle, value -> render.getValue());
    private final Setting<ColorSetting> circleColor = new Setting<>("Color", new ColorSetting(0xFFda6464), value -> render.getValue() && renderMode.getValue() == RenderMode.Circle || renderMode.getValue() == RenderMode.CircleAndModel || renderMode.getValue() == RenderMode.Box);
    private final Setting<Bind> cancel = new Setting<>("Cancel", new Bind(GLFW.GLFW_KEY_LEFT_SHIFT, false, false));

    private enum RenderMode {
        Circle,
        Model,
        CircleAndModel,
        Box
    }

    private PlayerEntityCopy blinkPlayer;
    public static Vec3d lastPos = Vec3d.ZERO;
    private Vec3d prevVelocity = Vec3d.ZERO;
    private float prevYaw = 0;
    private boolean prevSprinting = false;
    private final Queue<Packet<?>> storedPackets = new LinkedList<>();
    private final Queue<Packet<?>> storedTransactions = new LinkedList<>();
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private boolean need2SendBsAsap = false;
    private boolean need2CancelBsAsap = false;
    private Box renderbox;

    @Override
    public void onEnable() {
        if (mc.player == null
                || mc.world == null
                || mc.isIntegratedServerRunning()
                || mc.getNetworkHandler() == null) {
            disable();
            return;
        }

        storedTransactions.clear();
        lastPos = mc.player.getPos();
        prevVelocity = mc.player.getVelocity();
        prevYaw = mc.player.getYaw();
        prevSprinting = mc.player.isSprinting();
        mc.world.spawnEntity(new ClientPlayerEntity(mc, mc.world, mc.getNetworkHandler(), mc.player.getStatHandler(), mc.player.getRecipeBook(), mc.player.lastSprinting, mc.player.isSneaking()));
        sending.set(false);
        storedPackets.clear();
    }

    @Override
    public void onDisable() {
        if (mc.world == null || mc.player == null) return;

        while (!storedPackets.isEmpty())
            sendPacket(storedPackets.poll());

        if (blinkPlayer != null) blinkPlayer.deSpawn();
        blinkPlayer = null;

        storedPackets.clear(); // доверяй, но проверяй
    }

    @Override
    public String getDisplayInfo() {
        return Integer.toString(storedPackets.size());
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket vel && vel.getId() == mc.player.getId()) {
            if (disableOnVelocity.getValue()) disable(isRu() ? "Выключенно из-за велосити!" : "Disabled due to velocity!");
            // if (clearOnVelocity.getValue()) need2CancelBsAsap = true;
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (fullNullCheck()) return;

        Packet<?> packet = event.getPacket();

        if (sending.get()) {
            return;
        }

        if (need2CancelBsAsap) {
            storedPackets.clear();
        }

        if (packet instanceof CommonPongC2SPacket) {
            storedTransactions.add(packet);
        }

        if (pulse.getValue()) {
            if (packet instanceof PlayerMoveC2SPacket) {
                event.cancel();
                storedPackets.add(packet);
            }
        } else if (!(packet instanceof ChatMessageC2SPacket || packet instanceof TeleportConfirmC2SPacket || packet instanceof KeepAliveC2SPacket || packet instanceof AdvancementTabC2SPacket || packet instanceof ClientStatusC2SPacket)) {
            event.cancel();
            storedPackets.add(packet);
        }

        if (stopOnInteraction.getValue() && packet instanceof PlayerInteractEntityC2SPacket)
            need2SendBsAsap = true;
    }

    @EventHandler
    public void onUpdate(EventTick event) {
        if (fullNullCheck()) return;

        if (clearOnVelocity.getValue()) clearOnVelocity.setValue(false);

        if (need2CancelBsAsap) storedPackets.clear();

        if (isKeyPressed(cancel)) {
            storedPackets.clear();
            mc.player.setPos(lastPos.getX(), lastPos.getY(), lastPos.getZ());
            mc.player.setVelocity(prevVelocity);
            mc.player.setYaw(prevYaw);
            mc.player.setSprinting(prevSprinting);
            mc.player.setSneaking(false);
            mc.options.sneakKey.setPressed(false);
            sending.set(true);
            while (!storedTransactions.isEmpty())
                sendPacket(storedTransactions.poll());
            sending.set(false);
            disable(isRu() ? "Отменяю.." : "Canceling..");
            return;
        }

        if (pulse.getValue()) {
            if (storedPackets.size() >= pulsePackets.getValue()) {
                sendPackets();
            }
        }

        if (autoDisable.getValue()) {
            if (storedPackets.size() >= disablePackets.getValue()) {
                disable();
            }
        }

        if (need2SendBsAsap) {
            sendPackets();
            need2SendBsAsap = false;
        }

    }

    private void sendPackets() {
        if (mc.player == null) return;
        sending.set(true);

        while (!storedPackets.isEmpty()) {
            Packet<?> packet = storedPackets.poll();
            sendPacket(packet);
            if (packet instanceof PlayerMoveC2SPacket && !(packet instanceof PlayerMoveC2SPacket.LookAndOnGround)) {
                lastPos = new Vec3d(((PlayerMoveC2SPacket) packet).getX(mc.player.getX()), ((PlayerMoveC2SPacket) packet).getY(mc.player.getY()), ((PlayerMoveC2SPacket) packet).getZ(mc.player.getZ()));

                if (renderMode.getValue() == RenderMode.Model || renderMode.getValue() == RenderMode.CircleAndModel) {
                    blinkPlayer.deSpawn();
                    blinkPlayer = new PlayerEntityCopy();
                    blinkPlayer.spawn();
                }

                if (renderMode.getValue() == RenderMode.Box) renderbox = mc.player.getBoundingBox();
            }
        }

        sending.set(false);
        storedPackets.clear();
    }

    public void onRender3D(MatrixStack stack) {
        if (mc.player == null || mc.world == null) return;
        if (render.getValue() && lastPos != null) {
            if (renderMode.getValue() == RenderMode.Circle || renderMode.getValue() == RenderMode.CircleAndModel) {
                float[] hsb = Color.RGBtoHSB(circleColor.getValue().getRed(), circleColor.getValue().getGreen(), circleColor.getValue().getBlue(), null);
                float hue = (float) (System.currentTimeMillis() % 7200L) / 7200F;
                int rgb = Color.getHSBColor(hue, hsb[1], hsb[2]).getRGB();
                ArrayList<Vec3d> vecs = new ArrayList<>();
                double x = lastPos.x;
                double y = lastPos.y;
                double z = lastPos.z;

                for (int i = 0; i <= 360; ++i) {
                    Vec3d vec = new Vec3d(x + Math.sin((double) i * Math.PI / 180.0) * 0.5D, y + 0.01, z + Math.cos((double) i * Math.PI / 180.0) * 0.5D);
                    vecs.add(vec);
                }

                for (int j = 0; j < vecs.size() - 1; ++j) {
                    Render3DEngine.drawLine(vecs.get(j), vecs.get(j + 1), new Color(rgb));
                    hue += (1F / 360F);
                    rgb = Color.getHSBColor(hue, hsb[1], hsb[2]).getRGB();
                }
            }
            if (renderMode.getValue() == RenderMode.Model || renderMode.getValue() == RenderMode.CircleAndModel) {
                if (blinkPlayer == null) {
                    blinkPlayer = new PlayerEntityCopy();
                    blinkPlayer.spawn();
                }
            }
            if (renderMode.getValue() == RenderMode.Box && renderbox != null) {
                 Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(renderbox, circleColor.getValue().getColorObject(), 1));
            }
        }
    }
}

