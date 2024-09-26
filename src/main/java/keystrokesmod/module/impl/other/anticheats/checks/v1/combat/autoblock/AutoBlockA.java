package keystrokesmod.module.impl.other.anticheats.checks.v1.combat.autoblock;

import keystrokesmod.module.impl.other.Anticheat;
import keystrokesmod.module.impl.other.anticheats.Check;
import keystrokesmod.module.impl.other.anticheats.TRPlayer;
import keystrokesmod.module.impl.other.anticheats.config.AdvancedConfig;
import org.jetbrains.annotations.NotNull;

public class AutoBlockA extends Check {
    public AutoBlockA(@NotNull TRPlayer player) {
        super("Auto Block (A)", player);
    }

    @Override
    public void _onTick() {
        if (player.fabricPlayer.isBlocking() && !player.lastSwing && player.currentSwing) {
            flag("impossible hit.");
        }
    }

    @Override
    public int getAlertBuffer() {
        return AdvancedConfig.autoBlockAAlertBuffer;
    }

    @Override
    public boolean isDisabled() {
        return !Anticheat.getCombatCheck().isToggled();
    }
}
