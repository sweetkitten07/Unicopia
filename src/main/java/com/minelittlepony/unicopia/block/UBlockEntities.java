package com.minelittlepony.unicopia.block;

import com.minelittlepony.unicopia.block.cloud.CloudBedBlock;
import com.minelittlepony.unicopia.block.cloud.CloudChestBlock;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.BlockEntityType.Builder;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;

public interface UBlockEntities {
    BlockEntityType<WeatherVaneBlock.WeatherVane> WEATHER_VANE = create("weather_vane", BlockEntityType.Builder.create(WeatherVaneBlock.WeatherVane::new, UBlocks.WEATHER_VANE));
    BlockEntityType<CloudBedBlock.Tile> FANCY_BED = create("fancy_bed", BlockEntityType.Builder.create(CloudBedBlock.Tile::new, UBlocks.CLOTH_BED, UBlocks.CLOUD_BED));
    BlockEntityType<ChestBlockEntity> CLOUD_CHEST = create("cloud_chest", BlockEntityType.Builder.create(CloudChestBlock.TileData::new, UBlocks.CLOUD_CHEST));

    static <T extends BlockEntity> BlockEntityType<T> create(String id, Builder<T> builder) {
        return Registry.register(Registries.BLOCK_ENTITY_TYPE, id, builder.build(null));
    }

    static void bootstrap() {}
}
