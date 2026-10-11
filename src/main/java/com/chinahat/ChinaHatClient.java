package com.chinahat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

public class ChinaHatClient implements ClientModInitializer {

    private static KeyBinding settingsKey;

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("chinahat", "main"));

    private static final Identifier HAT_TEXTURE = Identifier.of("chinahat", "textures/hat.png");

    private boolean hatEnabled = true;
    private float hatSize = 1.0f;   // 0.3 .. 2.0
    private int hatRed = 255;
    private int hatGreen = 225;
    private int hatBlue = 150;
    private Path configPath;

    @Override
    public void onInitializeClient() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("chinahat.json");
        loadConfig();

        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chinahat.settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        WorldRenderEvents.LAST.register(this::renderHat);
    }

    private void onTick(MinecraftClient client) {
        while (settingsKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new HatSettingsScreen(Text.literal("ChinaHat — настройка шляпы")));
            }
        }
    }

    // ===================== отрисовка шляпы =====================

    private void renderHat(WorldRenderContext context) {
        if (!hatEnabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity me = client.player;
        if (me == null) return;

        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, HAT_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        double camX = context.camera().getPos().x;
        double camY = context.camera().getPos().y;
        double camZ = context.camera().getPos().z;

        float r = hatRed / 255f;
        float g = hatGreen / 255f;
        float b = hatBlue / 255f;
        float w = 0.75f * hatSize;
        float h = 1.1f * hatSize;

        MatrixStack ms = context.matrixStack();

        for (ClientPlayerEntity player : Collections.singletonList(me)) {
            var pos = player.getLerpedPos(context.tickDelta());
            double baseY = pos.y + player.getHeight() + 0.1;

            ms.push();
            ms.translate(pos.x - camX, baseY - camY, pos.z - camZ);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
            Matrix4f m = ms.peek().getPositionMatrix();

            BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            bb.vertex(m, -w, 0, 0).texture(0, 1).color(r, g, b, 1f).next();
            bb.vertex(m,  w, 0, 0).texture(1, 1).color(r, g, b, 1f).next();
            bb.vertex(m,  w, h, 0).texture(1, 0).color(r, g, b, 1f).next();
            bb.vertex(m, -w, h, 0).texture(0, 0).color(r, g, b, 1f).next();
            BufferRenderer.drawWithGlobalProgram(bb.end());

            ms.pop();
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ===================== конфиг =====================

    private void loadConfig() {
        try {
            if (!Files.exists(configPath)) return;
            JsonObject o = new Gson().fromJson(Files.readString(configPath), JsonObject.class);
            if (o == null) return;
            if (o.has("hat_enabled")) hatEnabled = o.get("hat_enabled").getAsBoolean();
            if (o.has("hat_size")) hatSize = o.get("hat_size").getAsFloat();
            if (o.has("hat_red")) hatRed = o.get("hat_red").getAsInt();
            if (o.has("hat_green")) hatGreen = o.get("hat_green").getAsInt();
            if (o.has("hat_blue")) hatBlue = o.get("hat_blue").getAsInt();
        } catch (Exception ignored) {
        }
    }

    private void saveConfig() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("hat_enabled", hatEnabled);
            o.addProperty("hat_size", hatSize);
            o.addProperty("hat_red", hatRed);
            o.addProperty("hat_green", hatGreen);
            o.addProperty("hat_blue", hatBlue);
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception ignored) {
        }
    }

    // ===================== экран настроек =====================

    private class HatSettingsScreen extends Screen {
        HatSettingsScreen(Text title) {
            super(title);
        }

        @Override
        protected void init() {
            int cx = (this.width - 200) / 2;

            addDrawableChild(new OptionSlider(cx, 40, 200, 20, "Цвет R",
                    () -> hatRed / 255.0, v -> { hatRed = (int) Math.round(v * 255); saveConfig(); }));

            addDrawableChild(new OptionSlider(cx, 66, 200, 20, "Цвет G",
                    () -> hatGreen / 255.0, v -> { hatGreen = (int) Math.round(v * 255); saveConfig(); }));

            addDrawableChild(new OptionSlider(cx, 92, 200, 20, "Цвет B",
                    () -> hatBlue / 255.0, v -> { hatBlue = (int) Math.round(v * 255); saveConfig(); }));

            addDrawableChild(new OptionSlider(cx, 118, 200, 20, "Размер",
                    () -> (hatSize - 0.3) / 1.7, v -> { hatSize = (float) (0.3 + v * 1.7); saveConfig(); }));

            addDrawableChild(ButtonWidget.builder(
                            Text.literal(hatEnabled ? "Шляпа: ВКЛ" : "Шляпа: ВЫКЛ"),
                            b -> {
                                hatEnabled = !hatEnabled;
                                b.setMessage(Text.literal(hatEnabled ? "Шляпа: ВКЛ" : "Шляпа: ВЫКЛ"));
                                saveConfig();
                            })
                    .dimensions(cx, 148, 200, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close())
                    .dimensions(cx, 178, 200, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }

    private static class OptionSlider extends SliderWidget {
        private final String label;
        private final Consumer<Double> setter;

        OptionSlider(int x, int y, int w, int h, String label, DoubleSupplier getter, Consumer<Double> setter) {
            super(x, y, w, h, Text.empty(), getter.getAsDouble());
            this.label = label;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            String text;
            if ("Размер".equals(label)) {
                text = label + ": " + String.format("%.2f", 0.3 + this.value * 1.7);
            } else {
                text = label + ": " + (int) Math.round(this.value * 255);
            }
            setMessage(Text.literal(text));
        }

        @Override
        protected void applyValue() {
            setter.accept(this.value);
        }
    }
}
