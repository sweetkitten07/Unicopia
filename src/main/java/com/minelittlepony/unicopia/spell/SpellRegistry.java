package com.minelittlepony.unicopia.spell;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class SpellRegistry {

    private static final SpellRegistry instance = new SpellRegistry();

    public static SpellRegistry instance() {
        return instance;
    }

    public static boolean stackHasEnchantment(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey("spell");
    }

    private final Map<String, Entry> entries = new HashMap<>();

    private SpellRegistry() {
        registerSpell("shield", 0xffff00, SpellShield::new);
        registerSpell("charge", 0x0000ff, SpellCharge::new);
    }

    public IMagicEffect getSpellFromName(String name) {
        if (entries.containsKey(name)) {
            return entries.get(name).create();
        }

        return null;
    }

    public IMagicEffect createEffectFromNBT(NBTTagCompound compound) {
        if (compound.hasKey("effect_id")) {
            IMagicEffect effect = getSpellFromName(compound.getString("effect_id"));

            if (effect != null) {
                effect.readFromNBT(compound.getCompoundTag("effect"));
            }

            return effect;
        }

        return null;
    }

    public NBTTagCompound serializeEffectToNBT(IMagicEffect effect) {
        NBTTagCompound compound = effect.toNBT();

        compound.setString("effect_id", effect.getName());

        return compound;
    }

    public IDispenceable getDispenseActionFrom(ItemStack stack) {
        String key = getKeyFromStack(stack);

        if (entries.containsKey(key)) {
            Entry entry = entries.get(key);
            if (entry.canDispense) {
                return entry.create();
            }
        }

        return null;
    }

    public IMagicEffect getSpellFromItemStack(ItemStack stack) {
        return getSpellFromName(getKeyFromStack(stack));
    }

    public void registerSpell(String key, int tint, Callable<IMagicEffect> factory) {
        try {
            entries.put(key, new Entry(factory, tint));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ItemStack disenchantStack(ItemStack stack) {
        if (stackHasEnchantment(stack)) {
            stack.getTagCompound().removeTag("spell");
        }

        return stack;
    }

    public ItemStack enchantStack(ItemStack stack, ItemStack from) {
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setString("spell", getKeyFromStack(from));

        return stack;
    }

    public ItemStack enchantStack(ItemStack stack, String name) {
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setString("spell", name);

        return stack;
    }

    @Nonnull
    public static String getKeyFromStack(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound() || !stack.getTagCompound().hasKey("spell")) {
            return "";
        }

        return stack.getTagCompound().getString("spell");
    }

    public int getSpellTintFromStack(ItemStack stack) {
        return getSpellTint(getKeyFromStack(stack));
    }

    public int getSpellTint(String key) {
        if (entries.containsKey(key)) {
            return entries.get(key).color;
        }

        return 0xffffff;
    }

    public Set<String> getAllNames() {
        return entries.keySet();
    }

    class Entry {
        Callable<IMagicEffect> factory;

        int color;

        boolean canDispense;

        Entry(Callable<IMagicEffect> factory, int color) throws Exception {
            this.factory = factory;
            this.color = color;
            this.canDispense = factory.call() instanceof IDispenceable;
        }

        @SuppressWarnings("unchecked")
        <T extends IMagicEffect> T create() {
            try {
                return (T) factory.call();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}
