package keystrokesmod.module.impl.world;

import keystrokesmod.Raven;
import keystrokesmod.event.*;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Notifications;
import keystrokesmod.module.impl.other.SlotHandler;
import keystrokesmod.module.impl.other.anticheats.utils.alert.LogUtils;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.ModeSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.script.packets.serverbound.C03;
import keystrokesmod.script.packets.serverbound.C08;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.Reflection;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Mouse;

import java.util.concurrent.CopyOnWriteArrayList;

public class AutoPlace extends Module {
    private final SliderSetting frameDelay;
    private final SliderSetting minPlaceDelay;
    private final ButtonSetting disableLeft;
    private final ButtonSetting holdRight;
    private final ButtonSetting fastPlaceJump;
    private final ButtonSetting pitchCheck;
    private final ButtonSetting postPlace;

    private final ButtonSetting bypassMode = new ButtonSetting("Bypass Mode", false);

    private double fDelay = 0.0D;
    private long l = 0L;
    private int f = 0;
    private MovingObjectPosition lm = null;
    private BlockPos lp = null;

    public AutoPlace() {
        super("AutoPlace", category.world, 0);
        this.registerSetting(new DescriptionSetting("Best with safewalk."));
        this.registerSetting(frameDelay = new SliderSetting("Frame delay", 0.0D, 0.0D, 30.0D, 1.0D));
        this.registerSetting(minPlaceDelay = new SliderSetting("Min place delay", 1.0, 1.0, 500.0, 5.0));
        this.registerSetting(disableLeft = new ButtonSetting("Disable left", false));
        this.registerSetting(holdRight = new ButtonSetting("Hold right", false));
        this.registerSetting(fastPlaceJump = new ButtonSetting("Fast place on jump", false));
        this.registerSetting(pitchCheck = new ButtonSetting("Pitch check", false));
        this.registerSetting(postPlace = new ButtonSetting("Post place", false, "Place block on PostUpdate event"));
    }

    public CopyOnWriteArrayList<Packet> bypassPackets1 = new CopyOnWriteArrayList<>();
    public CopyOnWriteArrayList<Packet> bypassPackets2 = new CopyOnWriteArrayList<>();
    public boolean bypassSentC03 = false;
    public C03PacketPlayer bypassMovement = null;

    public boolean ignoreBypassPackets = false;
    public boolean bypassFirstMovement = false;

    public boolean shouldSendPackets = false;

    @SubscribeEvent
    public void onPacketSend(SendPacketEvent event) {
        if (!bypassMode.isToggled()) return;

        if (event.getPacket() instanceof C03PacketPlayer) {
            if (!bypassSentC03) {
                bypassSentC03 = true;
                bypassMovement = (C03PacketPlayer) event.getPacket();
            }

            if (!bypassFirstMovement) {
                bypassFirstMovement = true;
                return;
            }

            event.setCanceled(true);
        }

        if (event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            shouldSendPackets = true;

            if (bypassSentC03) {
                bypassPackets1.add(bypassMovement);
                bypassPackets2.add(event.getPacket());
                bypassSentC03 = false;
            }
            event.setCanceled(true);
        }
    }

    @Override
    public void onEnable() throws Throwable {
        bypassPackets1.clear();
        bypassPackets2.clear();

        bypassSentC03 = false;
        bypassMovement = null;

        ignoreBypassPackets = false;
        bypassFirstMovement = false;
    }

    public void guiUpdate() {
        if (this.fDelay != frameDelay.getInput()) {
            this.resetVariables();
        }

        this.fDelay = frameDelay.getInput();
    }

    public void onDisable() {
        if (holdRight.isToggled()) {
            this.rd(4);
        }

        this.resetVariables();

        if (bypassMode.isToggled()) {
            for (Packet packet : bypassPackets1) {
                mc.getNetHandler().addToSendQueue(packet);
            }
            for (Packet packet : bypassPackets2) {
                mc.getNetHandler().addToSendQueue(packet);
            }
        }

        bypassPackets1.clear();
        bypassPackets2.clear();
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent event) {
        ignoreBypassPackets = false;

        if (bypassMode.isToggled()) {
            ignoreBypassPackets = true;

            if (bypassPackets2.isEmpty()) return;
            if (bypassPackets1.isEmpty()) return;
            if (!shouldSendPackets) return;

            shouldSendPackets = false;

            // placements

            PacketUtils.sendPacketNoEvent(bypassPackets2.get(0));
            bypassPackets2.remove(0);

            // movements

            PacketUtils.sendPacketNoEvent(bypassPackets1.get(0));
            bypassPackets1.remove(0);

            Raven.mc.thePlayer.addChatMessage(new ChatComponentText(
                    "§5[Lola Assist]: §f Remaining Packets (Placements) = " + bypassPackets2.size() + " | " + "Remaining Packets (Movements) = " + bypassPackets1.size()
            ));

        }
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        if (!postPlace.isToggled())
            action();
    }

    @SubscribeEvent
    public void onPostUpdate(PostUpdateEvent event) {
        if (postPlace.isToggled())
            action();
    }

    private void action() {
        if (mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) {
            return;
        }
        final ItemStack getHeldItem = SlotHandler.getHeldItem();
        if (getHeldItem == null || !(getHeldItem.getItem() instanceof ItemBlock)) {
            return;
        }
        if (fastPlaceJump.isToggled() && holdRight.isToggled() && !ModuleManager.fastPlace.isEnabled() && Mouse.isButtonDown(1)) {
            if (mc.thePlayer.motionY > 0.0) {
                this.rd(1);
            }
            else if (!pitchCheck.isToggled()) {
                this.rd(1000);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void bh(DrawBlockHighlightEvent ev) {
        if (Utils.nullCheck()) {
            if (mc.currentScreen == null && !mc.thePlayer.capabilities.isFlying) {
                ItemStack i = mc.thePlayer.getHeldItem();
                if (i != null && i.getItem() instanceof ItemBlock) {
                    MovingObjectPosition m = mc.objectMouseOver;
                    if (disableLeft.isToggled() && Mouse.isButtonDown(0)) {
                        return;
                    }
                    if (m != null && m.typeOfHit == MovingObjectType.BLOCK && m.sideHit != EnumFacing.UP && m.sideHit != EnumFacing.DOWN) {
                        if (this.lm != null && (double) this.f < frameDelay.getInput()) {
                            ++this.f;
                        } else {
                            this.lm = m;
                            BlockPos pos = m.getBlockPos();
                            if (this.lp == null || pos.getX() != this.lp.getX() || pos.getY() != this.lp.getY() || pos.getZ() != this.lp.getZ()) {
                                Block b = mc.theWorld.getBlockState(pos).getBlock();
                                if (b != null && b != Blocks.air && !(b instanceof BlockLiquid)) {
                                    if (!holdRight.isToggled() || Mouse.isButtonDown(1)) {
                                        long n = System.currentTimeMillis();
                                        if (n - this.l >= minPlaceDelay.getInput()) {
                                            this.l = n;
                                            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, i, pos, m.sideHit, m.hitVec)) {
                                                Reflection.setButton(1, true);
                                                mc.thePlayer.swingItem();
                                                mc.getItemRenderer().resetEquippedProgress();
                                                Reflection.setButton(1, false);
                                                this.lp = pos;
                                                this.f = 0;
                                            }

                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }



    private void rd(int i) {
        try {
            if (Reflection.rightClickDelayTimerField != null) {
                Reflection.rightClickDelayTimerField.set(mc, i);
            }
        } catch (IllegalAccessException | IndexOutOfBoundsException ignored) {
        }
    }

    private void resetVariables() {
        this.lp = null;
        this.lm = null;
        this.f = 0;
    }


}
