package com.minelittlepony.unicopia.block.cloud;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.minelittlepony.unicopia.block.state.StateUtil;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public class SoggyCloudStairsBlock extends CloudStairsBlock implements Soakable {

    private final Supplier<Block> dryBlock;

    public SoggyCloudStairsBlock(BlockState baseState, Settings settings, Supplier<Block> dryBlock) {
        super(baseState, settings);
        setDefaultState(getDefaultState().with(MOISTURE, 7));
        this.dryBlock = dryBlock;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(MOISTURE);
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        return dryBlock.get().getPickStack(world, pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateWithMoisture(BlockState state, int moisture) {
        if (moisture <= 0) {
            return StateUtil.copyState(state, dryBlock.get().getDefaultState());
        }
        return StateUtil.copyState(state, getDefaultState()).with(MOISTURE, moisture);
    }
}
