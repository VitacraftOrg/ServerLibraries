package net.vitacraft.serverlibraries.mixin;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.collection.DefaultedList;
import net.vitacraft.serverlibraries.api.event.EventsRegistry;
import net.vitacraft.serverlibraries.api.event.events.items.ItemSmeltEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
public class AbstractFurnaceBlockEntityMixin {
    @Inject(method = "craftRecipe", at = @At("HEAD"), cancellable = true)
    private static void onCraftRecipe(DynamicRegistryManager registryManager, RecipeEntry<?> recipe, DefaultedList<ItemStack> slots, int count, CallbackInfoReturnable<Boolean> cir) {
        ItemStack inputItem = slots.get(0);
        ItemStack fuel = slots.get(1);
        ItemStack outputItem = recipe.value().getResult(registryManager);
        ItemSmeltEvent event = new ItemSmeltEvent(inputItem, outputItem, fuel);
        EventsRegistry.dispatchEvent(event);
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }
}
