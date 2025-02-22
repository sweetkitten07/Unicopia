package com.minelittlepony.unicopia.diet;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.minelittlepony.unicopia.entity.player.Pony;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.UseAction;
import net.minecraft.util.Util;

public record Effect(
        List<TagKey<Item>> tags,
        Optional<FoodComponent> foodComponent,
        Ailment ailment
) implements Predicate<ItemStack> {
    public static final Effect EMPTY = new Effect(List.of(), Optional.empty(), Ailment.EMPTY);
    public static final Codec<Effect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TagKey.unprefixedCodec(RegistryKeys.ITEM).listOf().fieldOf("tags").forGetter(Effect::tags),
            FoodAttributes.CODEC.optionalFieldOf("food_component").forGetter(Effect::foodComponent),
            Ailment.CODEC.fieldOf("ailment").forGetter(Effect::ailment)
    ).apply(instance, Effect::new));

    public Effect(PacketByteBuf buffer) {
        this(buffer.readList(b -> TagKey.of(RegistryKeys.ITEM, b.readIdentifier())), buffer.readOptional(FoodAttributes::read), new Ailment(buffer));
    }

    public void afflict(Pony pony, ItemStack stack) {
        ailment().effects().afflict(pony.asEntity(), stack);
    }

    public void appendTooltip(ItemStack stack, List<Text> tooltip, TooltipContext context) {
        int size = tooltip.size();
        tags.forEach(tag -> {
            if (stack.isIn(tag)) {
                tooltip.add(Text.literal(" ").append(Text.translatable(Util.createTranslationKey("tag", tag.id()))).formatted(Formatting.GRAY));
            }
        });
        if (tooltip.size() == size) {
            if (stack.isFood()) {
                tooltip.add(Text.literal(" ").append(Text.translatable("tag.unicopia.food_types.fruits_and_vegetables")).formatted(Formatting.GRAY));
            } else if (stack.getUseAction() == UseAction.DRINK) {
                tooltip.add(Text.literal(" ").append(Text.translatable("tag.unicopia.food_types.drinks")).formatted(Formatting.GRAY));
            }
        }

        if (context.isAdvanced() && stack.isFood()) {
            if (!ailment().effects().isEmpty()) {
                tooltip.add(Text.translatable("unicopia.diet.side_effects").formatted(Formatting.DARK_PURPLE));
                ailment().effects().appendTooltip(tooltip);
            }
        }
    }

    public void toBuffer(PacketByteBuf buffer) {
        buffer.writeCollection(tags, (b, t) -> b.writeIdentifier(t.id()));
        buffer.writeOptional(foodComponent, FoodAttributes::write);
        ailment.toBuffer(buffer);
    }

    @Override
    public boolean test(ItemStack stack) {
        return tags.stream().anyMatch(stack::isIn);
    }
}