package com.smileprotocol.registry;

import com.smileprotocol.SmileProtocol;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Tags del mod. */
public final class ModTags {

    /** Obstaculos que se pueden saltar con vault (README 12.4). */
    public static final TagKey<Block> VAULTABLE = TagKey.create(Registries.BLOCK, SmileProtocol.id("vaultable"));

    private ModTags() {}
}
