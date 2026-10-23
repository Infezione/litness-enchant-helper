package com.litness.optimizer.mixin;

import com.litness.optimizer.tooltip.LitnessTooltipHandler;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void litness_getEnchantedBookTooltipImage(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.is(Items.ENCHANTED_BOOK)) {
            Optional<TooltipComponent> component = LitnessTooltipHandler.getApplicableItemsTooltipData(self);
            if (component.isPresent()) {
                cir.setReturnValue(component);
            }
        }
    }
}
