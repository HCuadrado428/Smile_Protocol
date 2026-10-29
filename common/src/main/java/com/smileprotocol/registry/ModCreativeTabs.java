package com.smileprotocol.registry;

import com.smileprotocol.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Pestana creativa con los bloques e items del mod, para los administradores. */
public final class ModCreativeTabs {

    public static final Supplier<CreativeModeTab> MAIN = Services.PLATFORM.register(BuiltInRegistries.CREATIVE_MODE_TAB, "main",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.smileprotocol"))
                    .icon(() -> new ItemStack(ModItems.ELEVATOR_BUTTON.get()))
                    .displayItems((params, out) -> ModItems.all().forEach(i -> out.accept(i.get())))
                    .build());

    private ModCreativeTabs() {}

    public static void init() {}
}
