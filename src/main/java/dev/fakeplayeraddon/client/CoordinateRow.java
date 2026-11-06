package dev.fakeplayeraddon.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/** Original action-list row whose contents are three native Minecraft text boxes. */
public final class CoordinateRow extends Button {
    public final EditBox[] fields = new EditBox[3];

    public CoordinateRow(
            Font font, int x, int width, String[] values, BiConsumer<Integer, String> changed) {
        super(x, 0, width, 20, Component.literal("X Y Z"), b -> {}, DEFAULT_NARRATION);
        active = false;
        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            fields[i] =
                    new EditBox(
                            font,
                            0,
                            0,
                            Math.max(20, (width - 4) / 3),
                            20,
                            Component.literal(axes[i]));
            fields[i].setHint(Component.literal(axes[i]));
            fields[i].setMaxLength(11);
            fields[i].setValue(values[i]);
            fields[i].setResponder(text -> changed.accept(index, text));
        }
    }

    public void layout(int top, int bottom, boolean enabled) {
        int width = (getWidth() - 4) / 3;
        for (int i = 0; i < 3; i++) {
            var field = fields[i];
            field.setX(getX() + i * (width + 2));
            field.setY(getY());
            field.setWidth(width);
            field.visible = getY() >= top && getY() + 20 <= bottom;
            field.active = enabled && field.visible;
            if (!field.visible) field.setFocused(false);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        for (var field : fields) field.extractRenderState(graphics, x, y, delta);
    }
}
