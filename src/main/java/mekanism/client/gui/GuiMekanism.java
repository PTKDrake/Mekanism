package mekanism.client.gui;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import com.mojang.blaze3d.systems.RenderSystem;
import mekanism.api.text.ILangEntry;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElement.IHoverable;
import mekanism.client.gui.element.GuiOverlayDialog;
import mekanism.client.gui.element.GuiTexturedElement;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.render.IFancyFontRenderer;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

//TODO: Add our own "addButton" type thing for elements that are just "drawn" but don't actually have any logic behind them
public abstract class GuiMekanism<CONTAINER extends Container> extends ContainerScreen<CONTAINER> implements IGuiWrapper, IFancyFontRenderer {

    private static final NumberFormat intFormatter = NumberFormat.getIntegerInstance();
    public static final ResourceLocation BASE_BACKGROUND = MekanismUtils.getResource(ResourceType.GUI, "base.png");
    //TODO: Look into defaulting this to true
    protected boolean dynamicSlots;
    protected List<GuiOverlayDialog> overlays = new ArrayList<>();
    protected List<GuiElement> focusListeners = new ArrayList<>();

    protected GuiMekanism(CONTAINER container, PlayerInventory inv, ITextComponent title) {
        super(container, inv, title);
    }

    @Override
    public void init() {
        super.init();
        initPreSlots();
        if (dynamicSlots) {
            addSlots();
        }
    }

    @Override
    public void tick() {
        super.tick();
        for (Widget widget : this.buttons) {
            if (widget instanceof GuiElement) {
                ((GuiElement) widget).tick();
            }
        }
    }

    protected void initPreSlots() {
    }

    protected IHoverable getOnHover(ILangEntry translationHelper) {
        return getOnHover((Supplier<ITextComponent>) translationHelper::translate);
    }

    protected IHoverable getOnHover(Supplier<ITextComponent> componentSupplier) {
        return (onHover, xAxis, yAxis) -> displayTooltip(componentSupplier.get(), xAxis, yAxis);
    }

    protected ResourceLocation getButtonLocation(String name) {
        return MekanismUtils.getResource(ResourceType.GUI_BUTTON, name + ".png");
    }

    @Override
    public void addFocusListener(GuiElement element) {
        focusListeners.add(element);
    }

    @Override
    public void removeFocusListener(GuiElement element) {
        focusListeners.remove(element);
    }

    @Override
    public void focusChange(GuiElement changed) {
        for (GuiElement element : focusListeners) {
            if (element != changed) {
                element.setFocused(false);
            }
        }
    }

    @Override
    public void incrementFocus(GuiElement current) {
        int index = focusListeners.indexOf(current);
        if (index != -1) {
            GuiElement next = focusListeners.get((index + 1) % focusListeners.size());
            next.setFocused(true);
            focusChange(next);
        }
    }

    @Override
    public void resize(Minecraft minecraft, int sizeX, int sizeY) {
        List<Pair<Integer, GuiElement>> prevElements = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i++) {
            Widget widget = buttons.get(i);
            if (widget instanceof GuiElement && ((GuiElement) widget).hasPersistentData()) {
                prevElements.add(Pair.of(i, (GuiElement) widget));
            }
        }
        focusListeners.clear();
        init(minecraft, sizeX, sizeY);
        prevElements.forEach(e -> {
            if (e.getLeft() < buttons.size()) {
                Widget widget = buttons.get(e.getLeft());
                // we're forced to assume that the children list is the same before and after the resize.
                // for verification, we run a lightweight class equality check
                if (widget.getClass() == e.getRight().getClass() && widget instanceof GuiElement) {
                    ((GuiElement) widget).syncFrom(e.getRight());
                }
            }
        });
    }
    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int xAxis = mouseX - getGuiLeft();
        int yAxis = mouseY - getGuiTop();
        // first render general foregrounds
        for (Widget widget : this.buttons) {
            if (widget instanceof GuiElement) {
                ((GuiElement) widget).onRenderForeground(mouseX, mouseY);
            }
        }
        // then render tooltips, so there's no clashing
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Widget widget = buttons.get(i);
            if (widget instanceof GuiElement && widget.isMouseOver(mouseX, mouseY)) {
                RenderSystem.translated(0, 0, 400);
                ((GuiElement) widget).renderToolTip(xAxis, yAxis);
                RenderSystem.translated(0, 0, -400);
            }
            if (widget instanceof GuiOverlayDialog) {
                break;
            }
        }
    }

    // override this to traverse in reverse order (so stacked or overlaying GUI elements receive clicks first)
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = children().size() - 1; i >= 0; i--) {
            IGuiEventListener listener = children().get(i);
            if (listener.mouseClicked(mouseX, mouseY, button)) {
                setFocused(listener);
                if (button == 0) {
                    setDragging(true);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return GuiUtils.checkChildren(buttons, (child) -> child.keyPressed(keyCode, scanCode, modifiers)) ||
              super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int keyCode) {
        return GuiUtils.checkChildren(buttons, (child) -> child.charTyped(c, keyCode)) || super.charTyped(c, keyCode);
    }

    /**
     * @apiNote mouseXOld and mouseYOld are just guessed mappings I couldn't find any usage from a quick glance.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double mouseXOld, double mouseYOld) {
        super.mouseDragged(mouseX, mouseY, button, mouseXOld, mouseYOld);
        return getFocused() != null && isDragging() && button == 0 && getFocused().mouseDragged(mouseX, mouseY, button, mouseXOld, mouseYOld);
    }

    protected boolean isMouseOverSlot(Slot slot, double mouseX, double mouseY) {
        return isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY);
    }

    @Override
    protected boolean isPointInRegion(int x, int y, int width, int height, double mouseX, double mouseY) {
        // overridden to prevent slot interactions when a GuiElement is blocking
        return super.isPointInRegion(x, y, width, height, mouseX, mouseY) &&
              !buttons.stream().anyMatch(button -> button.isMouseOver(mouseX, mouseY));
    }

    protected void addSlots() {
        int size = container.inventorySlots.size();
        for (int i = 0; i < size; i++) {
            Slot slot = container.inventorySlots.get(i);
            if (slot instanceof InventoryContainerSlot) {
                InventoryContainerSlot containerSlot = (InventoryContainerSlot) slot;
                ContainerSlotType slotType = containerSlot.getSlotType();
                DataType dataType = findDataType(containerSlot);
                //Shift the slots by one as the elements include the border of the slot
                SlotType type;
                if (dataType != null) {
                    type = SlotType.get(dataType);
                } else if (slotType == ContainerSlotType.INPUT || slotType == ContainerSlotType.OUTPUT || slotType == ContainerSlotType.EXTRA) {
                    type = SlotType.NORMAL;
                } else if (slotType == ContainerSlotType.POWER) {
                    type = SlotType.POWER;
                } else if (slotType == ContainerSlotType.NORMAL || slotType == ContainerSlotType.VALIDITY) {
                    type = SlotType.NORMAL;
                } else {//slotType == ContainerSlotType.IGNORED: don't do anything
                    continue;
                }
                GuiSlot guiSlot = new GuiSlot(type, this, slot.xPos - 1, slot.yPos - 1);
                SlotOverlay slotOverlay = containerSlot.getSlotOverlay();
                if (slotOverlay != null) {
                    guiSlot.with(slotOverlay);
                }
                if (slotType == ContainerSlotType.VALIDITY) {
                    int index = i;
                    guiSlot.validity(() -> checkValidity(index));
                }
                addButton(guiSlot);
            } else {
                addButton(new GuiSlot(SlotType.NORMAL, this, slot.xPos - 1, slot.yPos - 1));
            }
        }
    }

    @Nullable
    protected DataType findDataType(InventoryContainerSlot slot) {
        if (container instanceof MekanismTileContainer && ((MekanismTileContainer<?>) container).getTileEntity() instanceof ISideConfiguration) {
            ISideConfiguration tile = (ISideConfiguration) ((MekanismTileContainer<?>) container).getTileEntity();
            return tile.getActiveDataType(slot.getInventorySlot());
        }
        return null;
    }

    protected ItemStack checkValidity(int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY) {
        //Ensure the GL color is white as mods adding an overlay (such as JEI for bookmarks), might have left
        // it in an unexpected state.
        MekanismRenderer.resetColor();
        if (width < 8 || height < 8) {
            Mekanism.logger.warn("Gui: {}, was too small to draw the background of. Unable to draw a background for a gui smaller than 8 by 8.", getClass().getSimpleName());
            return;
        }
        GuiUtils.renderBackgroundTexture(BASE_BACKGROUND, 4, 4, getGuiLeft(), getGuiTop(), getXSize(), getYSize(), 256, 256);
    }

    @Override
    public FontRenderer getFont() {
        return font;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        this.renderBackground();
        super.render(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    public void renderItem(@Nonnull ItemStack stack, int xAxis, int yAxis, float scale) {
        if (!stack.isEmpty()) {
            try {
                RenderSystem.pushMatrix();
                RenderSystem.enableDepthTest();
                RenderHelper.enableStandardItemLighting();
                if (scale != 1) {
                    RenderSystem.scalef(scale, scale, scale);
                }
                itemRenderer.renderItemAndEffectIntoGUI(stack, xAxis, yAxis);
                RenderHelper.disableStandardItemLighting();
                RenderSystem.disableDepthTest();
                RenderSystem.popMatrix();
            } catch (Exception e) {
                Mekanism.logger.error("Failed to render stack into gui: " + stack, e);
            }
        }
    }

    @Override
    public void renderItemWithOverlay(@Nonnull ItemStack stack, int xAxis, int yAxis, float scale, String text) {
        if (!stack.isEmpty()) {
            try {
                RenderSystem.pushMatrix();
                RenderSystem.enableDepthTest();
                RenderHelper.enableStandardItemLighting();
                if (scale != 1) {
                    RenderSystem.scalef(scale, scale, scale);
                }
                itemRenderer.renderItemAndEffectIntoGUI(stack, xAxis, yAxis);
                itemRenderer.renderItemOverlayIntoGUI(font, stack, xAxis, yAxis, text);
                RenderHelper.disableStandardItemLighting();
                RenderSystem.disableDepthTest();
                RenderSystem.popMatrix();
            } catch (Exception e) {
                Mekanism.logger.error("Failed to render stack into gui: " + stack, e);
            }
        }
    }

    @Override
    public void renderItemTooltip(@Nonnull ItemStack stack, int xAxis, int yAxis) {
        renderTooltip(stack, xAxis, yAxis);
    }

    @Override
    public ItemRenderer getItemRenderer() {
        return itemRenderer;
    }

    protected static String formatInt(long l) {
        return intFormatter.format(l);
    }

    @Override
    public void addElement(GuiTexturedElement e) {
        addButton(e);
    }

    @Override
    public void removeElement(GuiTexturedElement e) {
        buttons.remove(e);
        children.remove(e);
    }

    //Some blit param namings
    //blit(int x, int y, int textureX, int textureY, int width, int height);
    //blit(int x, int y, TextureAtlasSprite icon, int width, int height);
    //blit(int x, int y, int textureX, int textureY, int width, int height, int textureWidth, int textureHeight);
    //blit(int x, int y, int zLevel, float textureX, float textureY, int width, int height, int textureWidth, int textureHeight);
    //blit(int x, int y, int desiredWidth, int desiredHeight, int textureX, int textureY, int width, int height, int textureWidth, int textureHeight);
    //innerBlit(int x, int endX, int y, int endY, int zLevel, int width, int height, float textureX, float textureY, int textureWidth, int textureHeight);
}