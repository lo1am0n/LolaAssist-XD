package keystrokesmod.module.impl.combat;

import keystrokesmod.Raven;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.other.SlotHandler;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.*;
import keystrokesmod.utility.CoolDown;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class BlockHit extends Module {
    public static SliderSetting range, chance, waitMsMin, waitMsMax, hitPerMin, hitPerMax, postDelayMin, postDelayMax;
    public static DescriptionSetting eventTypeDesc;
    public static ModeSetting eventType;
    private final String[] MODES = new String[]{"PRE", "POST"};
    public static ButtonSetting onlyPlayers, onRightMBHold, hypixelBH;
    public static boolean executingAction, hitCoolDown, alreadyHit, safeGuard;
    public static int hitTimeout, hitsWaited;
    private CoolDown actionTimer = new CoolDown(0), postDelayTimer = new CoolDown(0);
    private boolean waitingForPostDelay;

    public BlockHit() {
        super("BlockHit", category.combat, "Automatically blockHit");
        this.registerSetting(onlyPlayers = new ButtonSetting("Only combo players", true));
        this.registerSetting(onRightMBHold = new ButtonSetting("When holding down rmb", true));
        this.registerSetting(waitMsMin = new SliderSetting("Action Time Min (MS)", 110, 1, 500, 1));
        this.registerSetting(waitMsMax = new SliderSetting("Action Time Max (MS)", 150, 1, 500, 1));
        this.registerSetting(hitPerMin = new SliderSetting("Once every Min hits", 1, 1, 10, 1));
        this.registerSetting(hitPerMax = new SliderSetting("Once every Max hits", 1, 1, 10, 1));
        this.registerSetting(postDelayMin = new SliderSetting("Post Delay Min (MS)", 10,  0, 500, 1));
        this.registerSetting(postDelayMax = new SliderSetting("Post Delay Max (MS)", 40, 0, 500, 1));
        this.registerSetting(chance =  new SliderSetting("Chance %", 100, 0, 100, 1));
        this.registerSetting(range = new SliderSetting("Range: ", 3, 1, 6, 0.05));
        this.registerSetting(eventType = new ModeSetting("Value: ", MODES, 1));
        this.registerSetting(hypixelBH = new ButtonSetting("Hypixel Blink Mode", true));
    }

    public void guiUpdate() {
        Utils.correctValue(waitMsMin, waitMsMax);
        Utils.correctValue(hitPerMin, hitPerMax);
        Utils.correctValue(postDelayMin, postDelayMax);
    }

    private CoolDown hypixelTimer = new CoolDown(0);
    private int randomizedDelayHypixel = 250;
    @SubscribeEvent
    public void onTick(TickEvent.RenderTickEvent e) {
        if(!Utils.nullCheck())
            return;


        if (hypixelBH.isToggled()) {
            if (hypixelTimer.getElapsedTime() >= randomizedDelayHypixel) {
                hypixelTimer.start();
                randomizedDelayHypixel = RandomUtils.nextInt(350, 500);

                blinkedPackets.clear();
                hypixelBlinking = false;
            }
            if (!didUnblock) {
                // move fix
                mc.thePlayer.moveForward *= 0.2f;
                mc.thePlayer.moveStrafing *= 0.2f;
            }
        }

        if(onRightMBHold.isToggled() && !Utils.tryingToCombo()){
            if(!safeGuard || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                safeGuard = true;
                finishCombo();
            }
            return;
        }
        if(waitingForPostDelay){
            if(postDelayTimer.hasFinished()){
                executingAction = true;
                startCombo();
                waitingForPostDelay = false;
                if(safeGuard) safeGuard = false;
                actionTimer.start();
            }
            return;
        }

        if(executingAction) {
            if(actionTimer.hasFinished()){
                executingAction = false;
                finishCombo();
                return;
            }else {
                return;
            }
        }

        if(onRightMBHold.isToggled() && Utils.tryingToCombo()) {
            if(mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) {
                if(!safeGuard  || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                    safeGuard = true;
                    finishCombo();
                }
                return;
            } else {
                Entity target = mc.objectMouseOver.entityHit;
                if(target.isDead) {
                    if(!safeGuard  || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                        safeGuard = true;
                        finishCombo();
                    }
                    return;
                }
            }
        }

        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof Entity && Mouse.isButtonDown(0)) {
            Entity target = mc.objectMouseOver.entityHit;
            if(target.isDead) {
                if(onRightMBHold.isToggled() && Mouse.isButtonDown(1) && Mouse.isButtonDown(0)) {
                    if(!safeGuard  || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                        safeGuard = true;
                        finishCombo();
                    }
                }
                return;
            }

            if (mc.thePlayer.getDistanceToEntity(target) <= range.getInput()) {
                if ((target.hurtResistantTime >= 10 && MODES[(int) eventType.getInput()] == MODES[1]) || (target.hurtResistantTime <= 10 && MODES[(int) eventType.getInput()] == MODES[0])) {

                    if (onlyPlayers.isToggled()){
                        if (!(target instanceof EntityPlayer)){
                            return;
                        }
                    }

                    if(AntiBot.isBot(target)){
                        return;
                    }


                    if (hitCoolDown && !alreadyHit) {
                        hitsWaited++;
                        if(hitsWaited >= hitTimeout){
                            hitCoolDown = false;
                            hitsWaited = 0;
                        } else {
                            alreadyHit = true;
                            return;
                        }
                    }

                    if(!(chance.getInput() == 100 || Math.random() <= chance.getInput() / 100))
                        return;

                    if(!alreadyHit){
                        guiUpdate();
                        if(hitPerMin.getInput() == hitPerMax.getInput()) {
                            hitTimeout =  (int) hitPerMin.getInput();
                        } else {

                            hitTimeout = ThreadLocalRandom.current().nextInt((int) hitPerMin.getInput(), (int) hitPerMax.getInput());
                        }
                        hitCoolDown = true;
                        hitsWaited = 0;

                        actionTimer.setCooldown((long)ThreadLocalRandom.current().nextDouble(waitMsMin.getInput(),  waitMsMax.getInput()+0.01));
                        if(postDelayMax.getInput() != 0){
                            postDelayTimer.setCooldown((long)ThreadLocalRandom.current().nextDouble(postDelayMin.getInput(),  postDelayMax.getInput()+0.01));
                            postDelayTimer.start();
                            waitingForPostDelay = true;
                        } else {
                            executingAction = true;
                            startCombo();
                            actionTimer.start();
                            alreadyHit = true;
                            if(safeGuard) safeGuard = false;
                        }
                        alreadyHit = true;
                    }
                } else {
                    if(alreadyHit){
                        alreadyHit = false;
                    }

                    if(safeGuard) safeGuard = false;
                }
            }
        }
    }

    private boolean hypixelBlinking = false;
    private final ConcurrentLinkedQueue<Packet<?>> blinkedPackets = new ConcurrentLinkedQueue<>();

    private void releasePackets() {
        try {
            synchronized (blinkedPackets) {
                for (Packet<?> packet : blinkedPackets) {
                    if (packet instanceof C09PacketHeldItemChange) {
                        Raven.badPacketsHandler.playerSlot = ((C09PacketHeldItemChange) packet).getSlotId();
                    }
                    PacketUtils.sendPacketNoEvent(packet);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Utils.sendModuleMessage(this, "&cThere was an error releasing blinked packets");
        }
        blinkedPackets.clear();
        hypixelBlinking = false;
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!Utils.nullCheck() || !hypixelBlinking) {
            return;
        }
        Packet<?> packet = e.getPacket();
        if (packet.getClass().getSimpleName().startsWith("S")) {
            return;
        }
        blinkedPackets.add(e.getPacket());
        e.setCanceled(true);
    }

    private boolean didUnblock = false;
    @Override
    public void onEnable() throws Throwable {
        hypixelBlinking = false;
        didUnblock = false;
        blinkedPackets.clear();
    }

    @Override
    public void onDisable() throws Throwable {
        releasePackets();
    }

    private void finishCombo() {
        if (hypixelBH.isToggled()) {
            if (!didUnblock) {
                didUnblock = true;
                setBlockState(false, false, true);
                Utils.sendMessage("unblocked");
            }
        }
        else {
            int key = mc.gameSettings.keyBindUseItem.getKeyCode();
            KeyBinding.setKeyBindState(key, false);
            Utils.setMouseButtonState(1, false);
        }
    }

    private void setBlockState(boolean state, boolean sendBlock, boolean sendUnBlock) {
        if (Utils.holdingSword()) {
            if (sendBlock && state && Utils.holdingSword() && !Raven.badPacketsHandler.C07) {
                sendBlock();
            } else if (sendUnBlock && !state) {
                unBlock();
            }
        }
    }

    private void sendBlock() {
        mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(SlotHandler.getHeldItem()));
    }

    private void unBlock() {
        if (!Utils.holdingSword()) {
            return;
        }
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    }

    private void startCombo() {
        if(Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) {
            if (hypixelBH.isToggled()) {
                releasePackets();

                setBlockState(true, true, false);
                hypixelBlinking = true;

                Utils.sendMessage("blocked");

                didUnblock = false;
            }
            else {
                int key = mc.gameSettings.keyBindUseItem.getKeyCode();
                KeyBinding.setKeyBindState(key, true);
                KeyBinding.onTick(key);
                Utils.setMouseButtonState(1, true);
            }
        }
    }
}