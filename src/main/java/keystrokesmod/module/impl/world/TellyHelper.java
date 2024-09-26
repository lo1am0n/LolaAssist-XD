package keystrokesmod.module.impl.world;

import keystrokesmod.Raven;
import keystrokesmod.event.*;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.other.RotationHandler;
import keystrokesmod.module.impl.other.SlotHandler;
import keystrokesmod.module.impl.other.anticheats.utils.world.PlayerRotation;
import keystrokesmod.module.impl.world.scaffold.IScaffoldRotation;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.utils.ModeOnly;
import keystrokesmod.utility.*;
import keystrokesmod.utility.aim.AimSimulator;
import keystrokesmod.utility.aim.RotationData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class TellyHelper extends Module {
    private final SliderSetting yawDifference;
    private final SliderSetting pitchDifference;
    private final ButtonSetting hypixelTest;

    private double fDelay = 0.0D;
    private long l = 0L;
    private int f = 0;
    private MovingObjectPosition lm = null;
    private BlockPos lp = null;

    public TellyHelper() {
        super("Telly Helper", category.world, 0);
        this.registerSetting(yawDifference = new SliderSetting("Max Yaw Dist", 35.0, 1.0, 90.0, 1.0));
        this.registerSetting(pitchDifference = new SliderSetting("Max Pitch Dist", 35.0, 1.0, 90.0, 1.0));
        this.registerSetting(hypixelTest = new ButtonSetting("Hypixel (Test)", true));
    }

    public Vec3 getPlacePossibility(double offsetY, double original) { // rise
        List<Vec3> possibilities = new ArrayList<>();
        int range = 5;
        for (int x = -range; x <= range; ++x) {
            for (int y = -range; y <= range; ++y) {
                for (int z = -range; z <= range; ++z) {
                    final Block block = BlockUtils.blockRelativeToPlayer(x, y, z);
                    if (!block.getMaterial().isReplaceable()) {
                        for (int x2 = -1; x2 <= 1; x2 += 2) {
                            possibilities.add(new Vec3(mc.thePlayer.posX + x + x2, mc.thePlayer.posY + y, mc.thePlayer.posZ + z));
                        }
                        for (int y2 = -1; y2 <= 1; y2 += 2) {
                            possibilities.add(new Vec3(mc.thePlayer.posX + x, mc.thePlayer.posY + y + y2, mc.thePlayer.posZ + z));
                        }
                        for (int z2 = -1; z2 <= 1; z2 += 2) {
                            possibilities.add(new Vec3(mc.thePlayer.posX + x, mc.thePlayer.posY + y, mc.thePlayer.posZ + z + z2));
                        }
                    }
                }
            }
        }

        possibilities.removeIf(vec3 -> mc.thePlayer.getDistance(vec3.xCoord, vec3.yCoord, vec3.zCoord) > 5);

        if (possibilities.isEmpty()) {
            return null;
        }
        possibilities.sort(Comparator.comparingDouble(vec3 -> {
            final double d0 = (mc.thePlayer.posX) - vec3.xCoord;
            final double d1 = ((mc.thePlayer.posY) - 1) + offsetY - vec3.yCoord;
            final double d2 = (mc.thePlayer.posZ) - vec3.zCoord;
            return MathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
        }));

        return possibilities.get(0);
    }

    private @Nullable Scaffold.EnumFacingOffset getEnumFacing(final Vec3 position) {
        for (int x2 = -1; x2 <= 1; x2 += 2) {
            if (!BlockUtils.getBlock(position.xCoord + x2, position.yCoord, position.zCoord).getMaterial().isReplaceable()) {
                if (x2 > 0) {
                    return new Scaffold.EnumFacingOffset(EnumFacing.WEST, new Vec3(x2, 0, 0));
                } else {
                    return new Scaffold.EnumFacingOffset(EnumFacing.EAST, new Vec3(x2, 0, 0));
                }
            }
        }

        for (int y2 = -1; y2 <= 1; y2 += 2) {
            if (!BlockUtils.getBlock(position.xCoord, position.yCoord + y2, position.zCoord).getMaterial().isReplaceable()) {
                if (y2 < 0) {
                    return new Scaffold.EnumFacingOffset(EnumFacing.UP, new Vec3(0, y2, 0));
                }
            }
        }

        for (int z2 = -1; z2 <= 1; z2 += 2) {
            if (!BlockUtils.getBlock(position.xCoord, position.yCoord, position.zCoord + z2).getMaterial().isReplaceable()) {
                if (z2 < 0) {
                    return new Scaffold.EnumFacingOffset(EnumFacing.SOUTH, new Vec3(0, 0, z2));
                } else {
                    return new Scaffold.EnumFacingOffset(EnumFacing.NORTH, new Vec3(0, 0, z2));
                }
            }
        }

        return null;
    }

    public float[] generateSearchSequence(float value) {
        int length = (int) value * 2;
        float[] sequence = new float[length + 1];

        int index = 0;
        sequence[index++] = 0;

        for (int i = 1; i <= value; i++) {
            sequence[index++] = i;
            sequence[index++] = -i;
        }

        return sequence;
    }

    private double getRandom() {
        return Utils.randomizeInt(-90, 90) / 100.0;
    }

    private boolean forceStrict(float value) {
        return (inBetween(-170, -105, value) || inBetween(-80, 80, value) || inBetween(98, 170, value)) && !inBetween(-10, 10, value);
    }

    private boolean inBetween(float min, float max, float value) {
        return value >= min && value <= max;
    }

    private double original = 0.0;
    private MovingObjectPosition rayCasted = null;
    private MovingObjectPosition rayCastedV2 = null;
    private boolean alreadyPlaced = false;
    private boolean forceStrict = false;
    private float targetYaw = -9999f;
    private float targetPitch = -9999f;

    private float theYaw = -9999f;
    private float thePitch = -9999f;

    private boolean canAim = false;
    private boolean canPlace = false;

    private double placeY = 0.0;

    public float getYawHypixel() {
        float yaw = 180.0f;

        return mc.thePlayer.rotationYaw + yaw;
    }

    @SubscribeEvent
    public void onRotation(RotationEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }

        if (canAim) {
            event.setYaw(hypixelTest.isToggled() ? getYawHypixel() : targetYaw);
            event.setPitch(targetPitch);

            canPlace = true;
        }

    }

    @Override
    public void onEnable() throws Throwable {
        original = mc.thePlayer.posY;

        rayCasted = null;
        forceStrict = false;
        targetYaw = -9999f;
        targetPitch = -9999f;
        alreadyPlaced = false;
        placeY = mc.thePlayer.posY - 1;

        canPlace = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPreUpdate(PreUpdateEvent event) {
        float yawDist = Math.abs(targetYaw - mc.thePlayer.rotationYaw);
        float pitchDist = Math.abs(targetPitch - mc.thePlayer.rotationPitch);

        if (mc.thePlayer.motionY > 0.419 && mc.thePlayer.motionY < 0.421) {
            placeY = mc.thePlayer.posY - 1;
        }
        if (canAim) {
            if (yawDist > yawDifference.getInput()) {
                rayCasted = null;
                forceStrict = false;
                canAim = false;
                theYaw = mc.thePlayer.rotationYaw;
                thePitch = mc.thePlayer.rotationPitch;
            }
            if (pitchDist > pitchDifference.getInput()) {
                rayCasted = null;
                forceStrict = false;

                theYaw = mc.thePlayer.rotationYaw;
                thePitch = mc.thePlayer.rotationPitch;
            }
        }
        else {
            theYaw = mc.thePlayer.rotationYaw;
            thePitch = mc.thePlayer.rotationPitch;

            if (yawDist <= yawDifference.getInput() && pitchDist <= pitchDifference.getInput()) {
                canAim = true;
                theYaw = targetYaw;
                thePitch = targetPitch;
            }
        }

        if (canPlace) {
            if (rayCastedV2 != null && rayCastedV2.typeOfHit == MovingObjectType.BLOCK && !alreadyPlaced) {
                if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), rayCastedV2.getBlockPos(), rayCastedV2.sideHit, rayCastedV2.hitVec)) {
                    mc.thePlayer.swingItem();
                }
                alreadyPlaced = true;
            }
            canPlace = false;
        }

        Vec3 targetVec3 = getPlacePossibility(0, placeY);
        if (targetVec3 == null) {
            return;
        }
        BlockPos targetPos = new BlockPos(targetVec3.xCoord, targetVec3.yCoord, targetVec3.zCoord);

        rayCasted = null;
        float searchYaw = 25;

        searchYaw = 360;

        Scaffold.EnumFacingOffset enumFacing = getEnumFacing(targetVec3);
        if (enumFacing == null) {
            return;
        }
        targetPos = targetPos.add(enumFacing.getOffset().xCoord, enumFacing.getOffset().yCoord, enumFacing.getOffset().zCoord);
        float[] targetRotation = RotationUtils.getRotations(targetPos);
        float[] searchPitch = new float[]{78, 12};

        for (int i = 0; i < 2; i++) {
            if (i == 1 && Utils.overPlaceable(-1)) {
                searchYaw = 180;
                searchPitch = new float[]{65, 25};
            } else if (i == 1) {
                break;
            }
            for (float checkYaw : generateSearchSequence(searchYaw)) {
                float playerYaw = targetRotation[0];
                float fixedYaw = (float) (playerYaw - checkYaw + getRandom());
                double deltaYaw = Math.abs(playerYaw - fixedYaw);
                if (i == 1 && (inBetween(75, 95, (float) deltaYaw)) || deltaYaw > 500) {
                    continue;
                }
                for (float checkPitch : generateSearchSequence(searchPitch[1])) {
                    float fixedPitch = RotationUtils.clampTo90((float) (targetRotation[1] + checkPitch + getRandom()));
                    MovingObjectPosition raycast = RotationUtils.rayTraceCustom(mc.playerController.getBlockReachDistance(), fixedYaw, fixedPitch);
                    if (raycast != null) {
                        if (raycast.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                            if (raycast.getBlockPos().equals(targetPos) && raycast.sideHit == enumFacing.getEnumFacing()) {
                                if (rayCasted == null || !BlockUtils.isSamePos(raycast.getBlockPos(), rayCasted.getBlockPos())) {
                                    if (mc.thePlayer.getHeldItem().getItem() instanceof ItemBlock && ((ItemBlock) mc.thePlayer.getHeldItem().getItem()).canPlaceBlockOnSide(mc.theWorld, raycast.getBlockPos(), raycast.sideHit, mc.thePlayer, mc.thePlayer.getHeldItem())) {
                                        if (rayCasted == null) {
                                            forceStrict = (forceStrict(checkYaw)) && i == 1;

                                            rayCasted = raycast;
                                            targetYaw = fixedYaw;
                                            targetPitch = fixedPitch;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (rayCasted != null) {
                break;
            }
        }

        if (rayCasted != null) {
            rayCastedV2 = rayCasted;
            alreadyPlaced = false;
        }
    }


}
