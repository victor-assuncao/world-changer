package com.worldchanger.mixin;

import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the generic NeoForge attachment storage to World Changer. */
@Mixin(AttachmentHolder.class)
public interface AttachmentHolderMixin {
    @Accessor("attachments")
    Map<AttachmentType<?>, Object> worldchanger$getAttachments();

    @Invoker("deserializeAttachments")
    void worldchanger$deserializeAttachments(HolderLookup.Provider provider, CompoundTag tag);
}
