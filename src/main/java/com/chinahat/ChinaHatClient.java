package com.chinahat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

public class ChinaHatClient implements ClientModInitializer {

    private static KeyBinding settingsKey;

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("chinahat", "main"));

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
        HudRenderCallback.EVENT.register(this::renderHat);
    }

    private void onTick(MinecraftClient client) {
        while (settingsKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new HatSettingsScreen(Text.literal("ChinaHat")));
            }
        }
    }

    // ===================== шляпа (HUD + проекция 3D -> экран) =====================

    private void renderHat(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!hatEnabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity me = client.player;
        if (me == null || client.world == null || client.options.hudHidden) return;

                Vec3d camPos = new Vec3d(me.getX(), me.getY() + 1.62, me.getZ());
        double hx = me.getX();
        double hy = me.getY() + me.getHeight() + 0.15;
        double hz = me.getZ();


        double dx = hx - camPos.x;
        double dy = hy - camPos.y;
        double dz = hz - camPos.z;

        double yaw = Math.toRadians(me.getYaw());
        double pitch = Math.toRadians(me.getPitch());

        // базис камеры
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz =  Math.cos(yaw) * Math.cos(pitch);

        double rx = -fz, ry = 0.0, rz = fx;                 // right = forward x (0,1,0)
        double ux = ry * fz - rz * fy;                      // up = right x forward
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        double vx = dx * rx + dy * ry + dz * rz;
        double vy = dx * ux + dy * uy + dz * uz;
        double vz = dx * fx + dy * fy + dz * fz;

        if (vz < 0.15) return;                              // за спиной / слишком близко

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        double aspect = (double) sw / sh;
        double fov = Math.toRadians(client.options.getFov().getValue());
        double f = 1.0 / Math.tan(fov / 2.0);

        double ndcX = (vx * f / aspect) / vz;
        double ndcY = (vy * f) / vz;
        int cx = (int) Math.round(sw / 2.0 * (1.0 + ndcX));
        int cy = (int) Math.round(sh / 2.0 * (1.0 - ndcY));

        // перспективный размер: ближе = больше
        int hatH = (int) Math.round(140.0 * hatSize / vz);
        hatH = Math.max(6, Math.min(160, hatH));
        int hatW = Math.max(4, Math.round(hatH * 1.35f));

        int color = 0xFF000000 | (hatRed << 16) | (hatGreen << 8) | hatBlue;
        int dark = 0xFF000000
                | (Math.max(0, (int) (hatRed * 0.75)) << 16)
                | (Math.max(0, (int) (hatGreen * 0.75)) << 8)
                | Math.max(0, (int) (hatBlue * 0.75));

        // поля шляпы — чуть шире и ниже основания
        ctx.fill(cx - hatW / 2 - 2, cy, cx + hatW / 2 + 2, cy + Math.max(2, hatH / 10), dark);
        // конус — стопка сужающихся полос
        int rows = 9;
        int rowH = Math.max(1, hatH / rows);
        for (int i = 0; i <= rows; i++) {
            float t = i / (float) rows;                     // 0 = низ, 1 = верх
            int w = Math.max(1, Math.round(hatW * (1f - t * 0.88f)));
            int y = cy - i * rowH;
            ctx.fill(cx - w / 2, y - rowH, cx + w / 2, y, i == rows ? dark : color);
        }
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
