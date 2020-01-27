package com.minelittlepony.unicopia.network;

import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.minelittlepony.jumpingcastle.api.Channel;
import com.minelittlepony.jumpingcastle.api.Message;
import com.minelittlepony.unicopia.SpeciesList;
import com.minelittlepony.unicopia.ability.IData;
import com.minelittlepony.unicopia.ability.IPower;
import com.minelittlepony.unicopia.ability.PowersRegistry;
import com.minelittlepony.unicopia.entity.capabilities.IPlayer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

public class MsgPlayerAbility implements Message, Message.Handler<MsgPlayerAbility> {

    private static final Gson gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    @Expose
    private UUID senderId;

    @Expose
    private String powerIdentifier;

    @Expose
    private String abilityJson;

    public MsgPlayerAbility(PlayerEntity player, IPower<?> power, IData data) {
        senderId = player.getUuid();
        powerIdentifier = power.getKeyName();
        abilityJson = gson.toJson(data, power.getPackageType());
    }

    private <T extends IData> void apply(IPower<T> power, Channel channel) {
        MinecraftServer server = channel.getServer();
        IPlayer player = SpeciesList.instance().getPlayer(server.getPlayerManager().getPlayer(senderId));
        if (player == null) {
            return;
        }

        T data = gson.fromJson(abilityJson, power.getPackageType());

        power.apply(player, data);
    }

    @Override
    public void onPayload(MsgPlayerAbility message, Channel channel) {
        PowersRegistry.instance().getPowerFromName(powerIdentifier).ifPresent(power -> apply(power, channel));
    }
}
