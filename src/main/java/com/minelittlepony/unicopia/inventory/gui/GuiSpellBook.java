package com.minelittlepony.unicopia.inventory.gui;

import java.io.IOException;

import org.lwjgl.opengl.GL11;

import com.minelittlepony.unicopia.enchanting.IPageUnlockListener;
import com.minelittlepony.unicopia.enchanting.PagesList;
import com.minelittlepony.unicopia.inventory.slot.SlotEnchanting;
import com.minelittlepony.unicopia.player.IPlayer;
import com.minelittlepony.unicopia.player.PlayerSpeciesList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;

public class GuiSpellBook extends GuiContainer implements IPageUnlockListener {
	private static int currentPage = 0;
	private static ResourceLocation spellBookPageTextures = new ResourceLocation("unicopia", "textures/gui/container/pages/page-" + currentPage + ".png");

	private static final ResourceLocation spellBookGuiTextures = new ResourceLocation("unicopia", "textures/gui/container/book.png");

	private IPlayer playerExtension;

	private PageButton nextPage;
	private PageButton prevPage;

	public GuiSpellBook(EntityPlayer player) {
		super(new ContainerSpellBook(player.inventory, player.world, new BlockPos(player)));
		player.openContainer = inventorySlots;
		((ContainerSpellBook)inventorySlots).setListener(this);
		xSize = 405;
        ySize = 219;
        allowUserInput = true;
        playerExtension = PlayerSpeciesList.instance().getPlayer(player);
	}

	@Override
	public void initGui() {
		super.initGui();
		buttonList.clear();

		int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;

		buttonList.add(nextPage = new PageButton(1, x + 360, y + 160, true));
        buttonList.add(prevPage = new PageButton(2, x + 20, y + 160, false));
	}

	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		initGui();

		if (button.id == 1) {
			nextPage();
		} else {
			prevPage();
		}
	}

	public void nextPage() {
		if (currentPage == 0) {
			playerExtension.unlockPage(1);
		}
		if (currentPage < PagesList.getTotalPages() - 1) {
			currentPage++;
			spellBookPageTextures = new ResourceLocation("unicopia", "textures/gui/container/pages/page-" + currentPage + ".png");

			onPageUnlocked();
			PagesList.readPage(currentPage);
		}
	}

	@Override
	public void onPageUnlocked() {
		if (PagesList.hasUnreadPagesAfter(currentPage)) {
		    nextPage.triggerShake();
		}

        if (PagesList.hasUnreadPagesBefore(currentPage)) {
            prevPage.triggerShake();
        }
	}

	public void prevPage() {
		if (currentPage > 0) {
			currentPage--;
			spellBookPageTextures = new ResourceLocation("unicopia", "textures/gui/container/pages/page-" + currentPage + ".png");

			onPageUnlocked();
			PagesList.readPage(currentPage);
		}
	}

	@Override
	protected void drawGradientRect(int left, int top, int width, int height, int startColor, int endColor) {
		Slot slot = getSlotUnderMouse();
		if (slot == null || left != slot.xPos || top != slot.yPos || !drawSlotOverlay(slot)) {
			super.drawGradientRect(left, top, width, height, startColor, endColor);
		}
	}

	protected boolean drawSlotOverlay(Slot slot) {
		if (slot instanceof SlotEnchanting) {
			GlStateManager.enableBlend();
	        GL11.glDisable(GL11.GL_ALPHA_TEST);
			mc.getTextureManager().bindTexture(spellBookGuiTextures);
	        drawModalRectWithCustomSizedTexture(slot.xPos - 1, slot.yPos - 1, 51, 223, 18, 18, 512, 256);
	        GL11.glEnable(GL11.GL_ALPHA_TEST);
	        GlStateManager.disableBlend();
			return true;
		}
		return false;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
	    super.drawScreen(mouseX, mouseY, partialTicks);

	    renderHoveredToolTip(mouseX, mouseY);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		if (PagesList.getTotalPages() > 0) {
    		String text = (currentPage + 1) + "/" + PagesList.getTotalPages();
    		fontRenderer.drawString(text, 203 - fontRenderer.getStringWidth(text)/2, 165, 0x0);
		}
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		GlStateManager.color(1, 1, 1, 1);

        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        mc.getTextureManager().bindTexture(spellBookGuiTextures);
        drawModalRectWithCustomSizedTexture(left, top, 0, 0, xSize, ySize, 512, 256);

        GlStateManager.enableBlend();
        GL11.glDisable(GL11.GL_ALPHA_TEST);

        if (playerExtension.hasPageUnlock(currentPage)) {
        	if (mc.getTextureManager().getTexture(spellBookPageTextures) != TextureUtil.MISSING_TEXTURE) {
		        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		        mc.getTextureManager().bindTexture(spellBookPageTextures);
		        drawModalRectWithCustomSizedTexture(left, top, 0, 0, xSize, ySize, 512, 256);
        	}
        }

    	mc.getTextureManager().bindTexture(spellBookGuiTextures);
        drawModalRectWithCustomSizedTexture(left + 152, top + 49, 407, 2, 100, 101, 512, 256);

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GlStateManager.disableBlend();
	}

	static class PageButton extends GuiButton {
        private final boolean direction;

        private int shakesLeft = 0;
        private float shakeCount = 0;

        public PageButton(int id, int x, int y, boolean direction) {
            super(id, x, y, 23, 13, "");
            this.direction = direction;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (visible) {
            	int x = this.x;
            	int y = this.y;
            	if (shakesLeft > 0) {
            		shakeCount += (float)Math.PI/2;
            		if (shakeCount >= Math.PI * 2) {
            			shakeCount %= Math.PI*2;
            			shakesLeft--;
            		}
	            	x += (int)(Math.sin(shakeCount)*3);
	            	y -= (int)(Math.sin(shakeCount)*3);
            	}

                boolean hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + width && mouseY < this.y + height;
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                mc.getTextureManager().bindTexture(spellBookGuiTextures);
                int u = 0;
                int v = 220;
                if (hovered) u += 23;
                if (!direction) v += 13;
                drawModalRectWithCustomSizedTexture(x, y, u, v, 23, 13, 512, 256);
            }
        }

        public void triggerShake() {
        	if (shakesLeft <= 0) {
        		shakesLeft = 5;
        	}
        }
    }
}
