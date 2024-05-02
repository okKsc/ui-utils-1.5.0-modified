package org.uiutils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Timer;
import java.util.TimerTask;

public class MainClient implements ClientModInitializer {
    public static Font monospace;
    public static Color darkWhite;

    public static KeyBinding restoreScreenKey;

    public static Logger LOGGER = LoggerFactory.getLogger("ui-utils");
    public static MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void onInitializeClient() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac") || os.contains("darwin") || os.contains("osx")) {
            SharedVariables.isMac = true;
        }

        // register "restore screen" key
        restoreScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Restore Screen", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "UI Utils"));

        // register event for END_CLIENT_TICK
        ClientTickEvents.END_CLIENT_TICK.register((client) -> {
            // detect if the "restore screen" keybinding is pressed
            while (restoreScreenKey.wasPressed()) {
                if (SharedVariables.storedScreen != null && SharedVariables.storedScreenHandler != null && client.player != null) {
                    client.setScreen(SharedVariables.storedScreen);
                    client.player.currentScreenHandler = SharedVariables.storedScreenHandler;
                }
            }
        });

        // set java.awt.headless to false if os is not mac (allows for JFrame guis to be used)
        if (!SharedVariables.isMac) {
            System.setProperty("java.awt.headless", "false");
            monospace = new Font(Font.MONOSPACED, Font.PLAIN, 10);
            darkWhite = new Color(220, 220, 220);
        }
    }

    @SuppressWarnings("all")
    public static void createText(MinecraftClient mc, DrawContext context, TextRenderer textRenderer) {
        // display the current gui's sync id, revision, and credit
        context.drawText(textRenderer, "Sync Id: " + mc.player.currentScreenHandler.syncId, 200, 5, Color.WHITE.getRGB(), false);
        context.drawText(textRenderer, "Revision: " + mc.player.currentScreenHandler.getRevision(), 200, 35, Color.WHITE.getRGB(), false);
    }

    public static void createWidgets(MinecraftClient mc, Screen screen) {
        // register "close without packet" button in all HandledScreens
        screen.addDrawableChild(ButtonWidget.builder(Text.of("Close without packet"), (button) -> {
            // closes the current gui without sending a packet to the current server
            mc.setScreen(null);
        }).width(115).position(5, 5).build());

        // register "de-sync" button in all HandledScreens
        screen.addDrawableChild(ButtonWidget.builder(Text.of("De-sync"), (button) -> {
            // keeps the current gui open client-side and closed server-side
            if (mc.getNetworkHandler() != null && mc.player != null) {
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            } else {
                LOGGER.warn("Minecraft network handler or player was null while using 'De-sync' in UI Utils.");
            }
        }).width(115).position(5, 35).build());

        // register "send packets" button in all HandledScreens
        screen.addDrawableChild(ButtonWidget.builder(Text.of("Send UI packets: " + SharedVariables.sendUIPackets), (button) -> {
            // tells the client if it should send any gui related packets
            SharedVariables.sendUIPackets = !SharedVariables.sendUIPackets;
            button.setMessage(Text.of("Send UI packets: " + SharedVariables.sendUIPackets));
        }).width(140).position(5, 65).build());

        // register "delay packets" button in all HandledScreens
        screen.addDrawableChild(ButtonWidget.builder(Text.of("Delay UI packets: " + SharedVariables.delayUIPackets), (button) -> {
            // toggles a setting to delay all gui related packets to be used later when turning this setting off
            SharedVariables.delayUIPackets = !SharedVariables.delayUIPackets;
            button.setMessage(Text.of("Delay UI packets: " + SharedVariables.delayUIPackets));
            if (!SharedVariables.delayUIPackets && !SharedVariables.delayedUIPackets.isEmpty() && mc.getNetworkHandler() != null) {
                for (Packet<?> packet : SharedVariables.delayedUIPackets) {
                    mc.getNetworkHandler().sendPacket(packet);
                }
                if (mc.player != null) {
                    mc.player.sendMessage(Text.of("Sent " + SharedVariables.delayedUIPackets.size() + " packets."));
                }
                SharedVariables.delayedUIPackets.clear();
            }
        }).width(140).position(5, 95).build());

        // register "save gui" button in all HandledScreens
        screen.addDrawableChild(ButtonWidget.builder(Text.of("Save GUI"), (button) -> {
            // saves the current gui to a variable to be accessed later
            if (mc.player != null) {
                SharedVariables.storedScreen = mc.currentScreen;
                SharedVariables.storedScreenHandler = mc.player.currentScreenHandler;
            }
        }).width(115).position(5, 125).build());

        // register "disconnect and send packets" button in all HandledScreens
        screen.addDrawableChild(ButtonWidget.builder(Text.of("Disconnect and send packets"), (button) -> {
            // sends all "delayed" gui related packets before disconnecting, use: potential race conditions on non-vanilla servers
            SharedVariables.delayUIPackets = false;
            if (mc.getNetworkHandler() != null) {
                for (Packet<?> packet : SharedVariables.delayedUIPackets) {
                    mc.getNetworkHandler().sendPacket(packet);
                }
                mc.getNetworkHandler().getConnection().disconnect(Text.of("Disconnecting (UI-UTILS)"));
            } else {
                LOGGER.warn("Minecraft network handler (mc.getNetworkHandler()) is null while client is disconnecting.");
            }
            SharedVariables.delayedUIPackets.clear();
        }).width(160).position(5, 155).build());

        screen.addDrawableChild(ButtonWidget.builder(Text.of("Delay packets: " + SharedVariables.delayPackets), (button) -> {
            // toggles a setting to delay all gui related packets to be used later when turning this setting off
            SharedVariables.delayPackets = !SharedVariables.delayPackets;
            button.setMessage(Text.of("Delay packets: " + SharedVariables.delayPackets));
            if (!SharedVariables.delayPackets && !SharedVariables.delayedPackets.isEmpty() && mc.getNetworkHandler() != null) {
                for (Packet<?> packet : SharedVariables.delayedPackets) {
                    mc.getNetworkHandler().sendPacket(packet);
                }
                if (mc.player != null) {
                    mc.player.sendMessage(Text.of("Sent " + SharedVariables.delayedPackets.size() + " packets."));
                }
                SharedVariables.delayedPackets.clear();
            }
        }).width(115).position(5, 185).build());

        screen.addDrawableChild(ButtonWidget.builder(Text.of("Copy GUI Title JSON"), (button) -> {
            try {
                if (mc.currentScreen == null) {
                    throw new IllegalStateException("The current minecraft screen (mc.currentScreen) is null");
                }
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(Text.Serializer.toSortedJsonString(mc.currentScreen.getTitle())), null);
            } catch (IllegalStateException e) {
                LOGGER.error("Error while copying title JSON to clipboard", e);
            }
        }).width(115).position(5, 215).build());

    }


    @NotNull
    private static JButton getPacketOptionButton(String label) {
        JButton button = new JButton(label);
        button.setFocusable(false);
        button.setBorder(BorderFactory.createEtchedBorder());
        button.setBackground(darkWhite);
        button.setFont(monospace);
        return button;
    }

    public static boolean isInteger(String string) {
        try {
            Integer.parseInt(string);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static SlotActionType stringToSlotActionType(String string) {
        // converts a string to SlotActionType
        return switch (string) {
            case "PICKUP" -> SlotActionType.PICKUP;
            case "QUICK_MOVE" -> SlotActionType.QUICK_MOVE;
            case "SWAP" -> SlotActionType.SWAP;
            case "CLONE" -> SlotActionType.CLONE;
            case "THROW" -> SlotActionType.THROW;
            case "QUICK_CRAFT" -> SlotActionType.QUICK_CRAFT;
            case "PICKUP_ALL" -> SlotActionType.PICKUP_ALL;
            default -> null;
        };
    }

    public static void queueTask(Runnable runnable, long delayMs) {
        // queues a task for minecraft to run
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                MinecraftClient.getInstance().send(runnable);
            }
        };
        timer.schedule(task, delayMs);
    }
}
