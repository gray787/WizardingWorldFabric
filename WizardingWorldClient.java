package com.example.wizardingworld.client;

import com.example.wizardingworld.WizardingWorldMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class WizardingWorldClient implements ClientModInitializer {
    private static KeyMapping spellbook;
    @Override public void onInitializeClient() {
        spellbook = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.wizardingworld.spellbook", GLFW.GLFW_KEY_Z, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (spellbook.consumeClick()) client.setScreen(new SpellbookScreen());
        });
    }
    private static class SpellbookScreen extends Screen {
        protected SpellbookScreen() { super(Component.literal("Wizarding Spellbook")); }
        @Override public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
            super.render(g,mouseX,mouseY,delta);
            g.drawCenteredString(font,"WIZARDING SPELLBOOK",width/2,35,0xFFD875);
            g.drawCenteredString(font,"Your learned spells are stored by the server.",width/2,58,0xFFFFFF);
            g.drawCenteredString(font,"Use /hogwarts spells to view the spell archive.",width/2,78,0xC9B5FF);
            g.drawCenteredString(font,"Press ESC to close",width/2,height-35,0xAAAAAA);
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
