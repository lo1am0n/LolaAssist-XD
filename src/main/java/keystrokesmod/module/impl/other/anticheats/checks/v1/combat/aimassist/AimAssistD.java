package keystrokesmod.module.impl.other.anticheats.checks.v1.combat.aimassist;

import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.module.impl.other.anticheats.Check;
import keystrokesmod.module.impl.other.anticheats.TRPlayer;
import keystrokesmod.module.impl.other.anticheats.config.AdvancedConfig;
import keystrokesmod.module.impl.world.Scaffold;
import keystrokesmod.utility.RotationUtils;
import net.minecraft.util.MovingObjectPosition;
import org.jetbrains.annotations.NotNull;

/**
 * @see Scaffold#getYaw()
 * @see Scaffold#onPreMotion(PreMotionEvent)
 */
public class AimAssistD extends Check {
    public AimAssistD(@NotNull TRPlayer player) {
        super("Aim Assist (D)", player);
    }

    @Override
    public void _onTick() {
        if (player.currentRot.equals(player.lastRot)) return;
        if (AdvancedConfig.aimAOnlyOnSwing && !(!player.lastSwing && player.currentSwing)) return;

        float deltaYaw = player.currentRot.y - player.lastRot.y;
        float deltaPitch = player.currentRot.x - player.lastRot.x;

        if (deltaYaw < AdvancedConfig.aimAMinDeltaYaw || deltaPitch < AdvancedConfig.aimAMinDeltaPitch) return;

        if (player.currentSwing && !player.lastSwing) {
            boolean suspiciousRotationChange = false;
            if (Math.abs(deltaYaw) >= 25) {
                suspiciousRotationChange = true;
            }
            if (Math.abs(deltaPitch) >= 25) {
                suspiciousRotationChange = true;
            }

            MovingObjectPosition hitResult = RotationUtils.rayCast(3.0, player.currentRot.y, player.currentRot.x);

            if (suspiciousRotationChange && hitResult != null && hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                flag("snapped onto enemy. Yaw Diff: " + Math.abs(deltaYaw) + ", Pitch Diff: " + Math.abs(deltaPitch));
            }
        }
    }

    @Override
    public int getAlertBuffer() {
        return 0;
    }

    @Override
    public boolean isDisabled() {
        return false;
    }
}
