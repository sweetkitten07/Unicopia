package com.minelittlepony.unicopia.client.render.entity;

import com.minelittlepony.unicopia.InteractionManager;
import com.minelittlepony.unicopia.Unicopia;
import com.minelittlepony.unicopia.client.render.entity.model.ModelCuccoon;
import com.minelittlepony.unicopia.entity.CuccoonEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

public class RenderCuccoon extends RenderLivingBase<CuccoonEntity> {

    private static final Identifier TEXTURE = new Identifier(Unicopia.MODID, "textures/entity/cuccoon.png");

    public RenderCuccoon(RenderManager manager) {
        super(manager, new ModelCuccoon(), 1);
    }

    @Override
    protected Identifier getEntityTexture(CuccoonEntity entity) {
        return TEXTURE;
    }

    @Override
    protected float getDeathMaxRotation(CuccoonEntity entity) {
        return 0;
    }

    @Override
    public void doRender(CuccoonEntity entity, double x, double y, double z, float entityYaw, float partialTicks) {

        if (entity.isBeingRidden()) {
            Entity rider = entity.getPassengers().get(0);

            if (!(rider == MinecraftClient.instance().player) || InteractionManager.instance().getViewMode() != 0) {
                GlStateManager.enableAlpha();
                GlStateManager.enableBlend();

                renderManager.renderEntity(rider, x, y + rider.getYOffset(), z, entityYaw, partialTicks, true);

                GlStateManager.disableBlend();
                GlStateManager.disableAlpha();
            }


        }

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }
}
