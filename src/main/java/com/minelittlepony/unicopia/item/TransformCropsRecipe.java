package com.minelittlepony.unicopia.item;

import java.util.HashSet;
import java.util.Set;

import com.minelittlepony.unicopia.block.state.StateUtil;
import com.minelittlepony.unicopia.entity.player.Pony;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SingleStackInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;

public class TransformCropsRecipe implements Recipe<TransformCropsRecipe.PlacementArea> {
    public static final int RADIUS = 3;
    public static final int SIDE_LENGTH = (2 * RADIUS) + 1;
    public static final int AREA = (SIDE_LENGTH * SIDE_LENGTH) - 1;
    public static final int MINIMUM_INPUT = 9;

    private final Block target;
    private final BlockState catalyst;
    private final BlockState output;

    public TransformCropsRecipe(Block target, BlockState catalyst, BlockState output) {
        this.output = output;
        this.target = target;
        this.catalyst = catalyst;
    }

    public ItemStack getTargetAsItem() {
        return target.asItem().getDefaultStack();
    }

    public ItemStack getCatalyst() {
        return catalyst.getBlock().getPickStack(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, catalyst);
    }

    public ItemStack getOutput() {
        return output.getBlock().getPickStack(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, output);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return URecipes.TRANSFORM_CROP_SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return URecipes.GROWING;
    }

    @Override
    public boolean matches(PlacementArea inventory, World world) {
        return world.getBlockState(inventory.position()).isOf(target);
    }

    @Override
    public ItemStack craft(PlacementArea inventory, DynamicRegistryManager manager) {
        return getResult(manager);
    }

    @Override
    public ItemStack getResult(DynamicRegistryManager manager) {
        return output.getBlock().asItem().getDefaultStack();
    }

    public Result checkPattern(World world, BlockPos pos) {
        BlockPos center = pos.down();
        Set<BlockPos> matches = new HashSet<>();
        for (BlockPos cell : BlockPos.iterateInSquare(center, RADIUS, Direction.EAST, Direction.NORTH)) {
            if (cell.equals(center)) {
                continue;
            }
            if (!world.getBlockState(cell).equals(catalyst)) {
                break;
            }
            matches.add(cell.toImmutable());
        }
        return new Result(this, matches);
    }

    public BlockState getResult(World world, BlockPos pos) {
        return StateUtil.copyState(world.getBlockState(pos), output);
    }

    @Override
    public boolean fits(int width, int height) {
        return width >= SIDE_LENGTH && height >= SIDE_LENGTH;
    }

    public static class Serializer implements RecipeSerializer<TransformCropsRecipe> {
        private static final Codec<TransformCropsRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Registries.BLOCK.getCodec().fieldOf("target").forGetter(recipe -> recipe.target),
                BlockState.CODEC.fieldOf("consume").forGetter(recipe -> recipe.catalyst),
                BlockState.CODEC.fieldOf("output").forGetter(recipe -> recipe.output)
        ).apply(instance, TransformCropsRecipe::new));

        @Override
        public Codec<TransformCropsRecipe> codec() {
            return CODEC;
        }

        @Override
        public TransformCropsRecipe read(PacketByteBuf buffer) {
            return new TransformCropsRecipe(
                    buffer.readRegistryValue(Registries.BLOCK),
                    Block.getStateFromRawId(buffer.readInt()),
                    Block.getStateFromRawId(buffer.readInt())
            );
        }

        @Override
        public void write(PacketByteBuf buffer, TransformCropsRecipe recipe) {
            buffer.writeRegistryValue(Registries.BLOCK, recipe.target);
            buffer.writeInt(Block.getRawIdFromState(recipe.catalyst));
            buffer.writeInt(Block.getRawIdFromState(recipe.output));
        }
    }

    public static record PlacementArea (Pony pony, BlockPos position) implements SingleStackInventory {
        @Override
        public ItemStack getStack(int var1) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeStack(int slot, int count) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setStack(int slot, ItemStack stack) { }

        @Override
        public void markDirty() { }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return true;
        }
    }

    public record Result (TransformCropsRecipe recipe, Set<BlockPos> matchedLocations) {
        public boolean shoudTransform(Random random) {
            return random.nextInt(AREA) < matchedLocations().size();
        }
    }
}
