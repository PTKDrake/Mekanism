package mekanism.common.inventory.container.slot;

import javax.annotation.Nonnull;
import mekanism.api.Action;
import mekanism.api.inventory.slot.IInventorySlot;
import net.minecraft.item.ItemStack;

public interface IInsertableSlot {

    //TODO: Improve these java docs at some point
    /**
     * Basically a container slot's equivalent of {@link IInventorySlot#insertItem(ItemStack, Action)}
     */
    @Nonnull
    ItemStack insertItem(@Nonnull ItemStack stack, Action action);
}