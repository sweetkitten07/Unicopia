package com.minelittlepony.unicopia;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import com.minelittlepony.unicopia.ability.magic.Affine;

import net.minecraft.entity.player.PlayerEntity;

public enum Race implements Affine {
    /**
     * The default, unset race.
     * This is used if there are no other races.
     */
    HUMAN(false, false, false),
    EARTH(false, false, true),
    UNICORN(true, false, false),
    PEGASUS(false, true, false),
    BAT(false, true, false),
    ALICORN(true, true, true),
    CHANGELING(false, true, false);

    private final boolean magic;
    private final boolean flight;
    private final boolean earth;

    private final static Map<Integer, Race> REGISTRY = Arrays.stream(values()).collect(Collectors.toMap(Enum::ordinal, Function.identity()));

    Race(boolean magic, boolean flight, boolean earth) {
        this.magic = magic;
        this.flight = flight;
        this.earth = earth;
    }

    @Override
    public Affinity getAffinity() {
        return this == CHANGELING ? Affinity.BAD : Affinity.NEUTRAL;
    }

    public boolean hasIronGut() {
        return isUsable() && this != CHANGELING;
    }

    public boolean isUsable() {
        return !isDefault();
    }

    public boolean isDefault() {
        return this == HUMAN;
    }

    public boolean isOp() {
        return this == ALICORN;
    }

    public boolean canFly() {
        return flight;
    }

    public boolean canCast() {
        return magic;
    }

    public boolean canUseEarth() {
        return earth;
    }

    public boolean canInteractWithClouds() {
        return canFly() && this != CHANGELING && this != BAT;
    }

    public String getTranslationKey() {
        return String.format("unicopia.race.%s", name().toLowerCase());
    }

    public boolean isPermitted(PlayerEntity sender) {
        if (isOp() && (sender == null || !sender.abilities.creativeMode)) {
            return false;
        }

        Set<Race> whitelist = Unicopia.getConfig().getSpeciesWhiteList();

        return isDefault()
                || whitelist.isEmpty()
                || whitelist.contains(this);
    }

    public Race validate(PlayerEntity sender) {
        if (!isPermitted(sender)) {
            if (this == EARTH) {
                return HUMAN;
            }

            return EARTH.validate(sender);
        }

        return this;
    }


    public boolean equals(String s) {
        return name().equalsIgnoreCase(s)
                || getTranslationKey().equalsIgnoreCase(s);
    }

    public static Race fromName(String s, Race def) {
        if (!Strings.isNullOrEmpty(s)) {
            for (Race i : values()) {
                if (i.equals(s)) return i;
            }
        }

        try {
            return fromId(Integer.parseInt(s));
        } catch (NumberFormatException e) { }

        return def;
    }

    public static Race fromName(String name) {
        return fromName(name, EARTH);
    }

    public static Race fromId(int id) {
        return REGISTRY.getOrDefault(id, EARTH);
    }
}
