package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Pestana creativa con los bloques e items del mod, para los administradores. */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SmileProtocol.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.smileprotocol"))
            .icon(() -> new ItemStack(ModItems.ELEVATOR_BUTTON.get()))
            .displayItems((params, out) -> ModItems.ITEMS.getEntries().forEach(i -> out.accept(i.get())))
            .build());

    private ModCreativeTabs() {}
}
