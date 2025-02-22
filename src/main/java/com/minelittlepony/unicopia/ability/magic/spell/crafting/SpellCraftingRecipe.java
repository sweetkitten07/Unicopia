package com.minelittlepony.unicopia.ability.magic.spell.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.minelittlepony.unicopia.ability.magic.spell.effect.SpellType;
import com.minelittlepony.unicopia.ability.magic.spell.trait.SpellTraits;
import com.minelittlepony.unicopia.container.inventory.SpellbookInventory;
import com.minelittlepony.unicopia.item.EnchantableItem;
import com.minelittlepony.unicopia.item.URecipes;
import com.minelittlepony.unicopia.util.CodecUtils;
import com.minelittlepony.unicopia.util.InventoryUtil;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeCodecs;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/**
 * A recipe for creating a new spell from input traits and items.
 */
public class SpellCraftingRecipe implements SpellbookRecipe {
    /**
     * The ingredient to modify
     */
    final IngredientWithSpell material;

    /**
     * The required traits
     */
    final TraitIngredient requiredTraits;

    /**
     * Items required for crafting.
     */
    final List<IngredientWithSpell> requiredItems;

    /**
     * The resulting item
     */
    final ItemStack output;

    private SpellCraftingRecipe(IngredientWithSpell material, TraitIngredient requiredTraits, List<IngredientWithSpell> requiredItems, ItemStack output) {
        this.material = material;
        this.requiredTraits = requiredTraits;
        this.requiredItems = requiredItems;
        this.output = output;
    }

    @Override
    public void buildCraftingTree(CraftingTreeBuilder builder) {
        builder.input(material.getMatchingStacks());
        for (var ingredient : requiredItems) {
            builder.input(ingredient.getMatchingStacks());
        }
        requiredTraits.min().ifPresent(min -> {
            min.forEach(e -> builder.input(e.getKey(), e.getValue()));
        });
        builder.result(output);
    }

    @Override
    public int getPriority() {
        return requiredItems.isEmpty() ? 0 : -1;
    }

    @Override
    public boolean matches(SpellbookInventory inventory, World world) {

        if (!material.test(inventory.getItemToModify())) {
            return false;
        }

        if (requiredItems.isEmpty()) {
            return requiredTraits.test(inventory.getTraits());
        }

        var outstandingRequirements = new ArrayList<>(requiredItems);
        var ingredients = InventoryUtil.slots(inventory)
                .filter(slot -> !inventory.getStack(slot).isEmpty())
                .map(slot -> Pair.of(slot, inventory.getStack(slot)))
                .collect(Collectors.toList());

        outstandingRequirements.removeIf(requirement -> {
            var found = ingredients.stream().filter(pair -> requirement.test(pair.getSecond())).findAny();
            found.ifPresent(ingredients::remove);
            return found.isPresent();
        });

        if (!outstandingRequirements.isEmpty()) {
            return false;
        }

        return requiredTraits.test(SpellTraits.union(
            ingredients.stream().map(pair -> SpellTraits.of(pair.getSecond()).multiply(pair.getFirst())).toArray(SpellTraits[]::new)
        ));
    }

    @Override
    public ItemStack craft(SpellbookInventory inventory, DynamicRegistryManager registries) {
        return getResult(registries).copy();
    }

    @Override
    public boolean fits(int width, int height) {
        return (width * height) > 0;
    }

    @Override
    public ItemStack getResult(DynamicRegistryManager registries) {
        return output;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return URecipes.TRAIT_REQUIREMENT;
    }

    public static class Serializer implements RecipeSerializer<SpellCraftingRecipe> {
        private static final Codec<ItemStack> RESULT_CODEC = CodecUtils.extend(RecipeCodecs.CRAFTING_RESULT, SpellType.REGISTRY.getCodec().fieldOf("spell")).xmap(
                pair -> pair.getSecond().map(spell -> EnchantableItem.enchant(pair.getFirst().orElse(ItemStack.EMPTY), spell)).orElse(pair.getFirst().orElse(ItemStack.EMPTY)),
                stack -> Pair.of(Optional.of(stack), EnchantableItem.getSpellKeyOrEmpty(stack))
        );

        private static final Codec<SpellCraftingRecipe> CODEC = RecordCodecBuilder.<SpellCraftingRecipe>create(instance -> instance.group(
                IngredientWithSpell.CODEC.fieldOf("material").forGetter(recipe -> recipe.material),
                TraitIngredient.CODEC.fieldOf("traits").forGetter(recipe -> recipe.requiredTraits),
                IngredientWithSpell.CODEC.listOf().fieldOf("ingredients").forGetter(recipe -> recipe.requiredItems),
                RESULT_CODEC.fieldOf("result").forGetter(recipe -> recipe.output)
        ).apply(instance, SpellCraftingRecipe::new));

        @Override
        public Codec<SpellCraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public SpellCraftingRecipe read(PacketByteBuf buf) {
            return new SpellCraftingRecipe(
                    IngredientWithSpell.fromPacket(buf),
                    TraitIngredient.fromPacket(buf),
                    buf.readCollection(DefaultedList::ofSize, IngredientWithSpell::fromPacket),
                    buf.readItemStack()
            );
        }

        @Override
        public void write(PacketByteBuf buf, SpellCraftingRecipe recipe) {
            recipe.material.write(buf);
            recipe.requiredTraits.write(buf);
            buf.writeCollection(recipe.requiredItems, (b, i) -> i.write(b));
            buf.writeItemStack(recipe.output);
        }
    }
}
