package mekanism.client.gui.element.button;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import mekanism.api.RelativeSide;
import mekanism.api.text.EnumColor;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.lib.Color;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.network.to_server.PacketConfigurationUpdate;
import mekanism.common.network.to_server.PacketConfigurationUpdate.ConfigurationPacket;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SideDataButton extends MekanismButton {

    private final Supplier<DataType> dataTypeSupplier;
    private final Supplier<EnumColor> colorSupplier;

    public SideDataButton(IGuiWrapper gui, int x, int y, RelativeSide slotPos, Supplier<DataType> dataTypeSupplier, Supplier<EnumColor> colorSupplier,
          BlockEntity tile, Supplier<TransmissionType> transmissionType, ConfigurationPacket packetType, IHoverable onHover) {
        super(gui, x, y, 14, 14, TextComponent.EMPTY,
              () -> Mekanism.packetHandler().sendToServer(new PacketConfigurationUpdate(packetType, tile.getBlockPos(), Screen.hasShiftDown() ? 2 : 0, slotPos, transmissionType.get())),
              () -> Mekanism.packetHandler().sendToServer(new PacketConfigurationUpdate(packetType, tile.getBlockPos(), 1, slotPos, transmissionType.get())), onHover);
        this.dataTypeSupplier = dataTypeSupplier;
        this.colorSupplier = colorSupplier;
    }

    @Override
    public void drawBackground(@Nonnull PoseStack matrix, int mouseX, int mouseY, float partialTicks) {
        DataType dataType = getDataType();
        EnumColor color = dataType == null ? null : getColor();
        boolean doColor = color != null && color != EnumColor.GRAY;
        if (doColor) {
            Color c = Color.rgbi(color.getRgbCode()[0], color.getRgbCode()[1], color.getRgbCode()[2]);
            double[] hsv = c.hsvArray();
            hsv[1] = Math.max(0, hsv[1] - 0.25F);
            hsv[2] = Math.min(1, hsv[2] + 0.4F);
            MekanismRenderer.color(Color.hsv(hsv[0], hsv[1], hsv[2]));
        } else {
            MekanismRenderer.resetColor();
        }
        super.drawBackground(matrix, mouseX, mouseY, partialTicks);
        if (doColor) {
            MekanismRenderer.resetColor();
        }
    }

    @Override
    protected boolean resetColorBeforeRender() {
        return false;
    }

    public DataType getDataType() {
        return this.dataTypeSupplier.get();
    }

    public EnumColor getColor() {
        return this.colorSupplier.get();
    }
}