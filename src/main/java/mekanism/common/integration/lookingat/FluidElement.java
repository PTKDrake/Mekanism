package mekanism.common.integration.lookingat;

import javax.annotation.Nonnull;
import mekanism.api.math.MathUtils;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.FluidType;
import mekanism.common.MekanismLang;
import mekanism.common.util.text.TextUtils;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

public class FluidElement extends LookingAtElement {

    @Nonnull
    protected final FluidStack stored;
    protected final int capacity;

    public FluidElement(@Nonnull FluidStack stored, int capacity) {
        super(0xFF000000, 0xFFFFFF);
        this.stored = stored;
        this.capacity = capacity;
    }

    @Override
    public int getScaledLevel(int level) {
        if (capacity == 0 || stored.getAmount() == Integer.MAX_VALUE) {
            return level;
        }
        return MathUtils.clampToInt(level * (double) stored.getAmount() / capacity);
    }

    @Override
    public TextureAtlasSprite getIcon() {
        return stored.isEmpty() ? null : MekanismRenderer.getFluidTexture(stored, FluidType.STILL);
    }

    @Override
    public Component getText() {
        int amount = stored.getAmount();
        if (amount == Integer.MAX_VALUE) {
            return MekanismLang.GENERIC_STORED.translate(stored, MekanismLang.INFINITE);
        }
        return MekanismLang.GENERIC_STORED_MB.translate(stored, TextUtils.format(amount));
    }

    @Override
    protected boolean applyRenderColor() {
        MekanismRenderer.color(stored);
        return true;
    }
}