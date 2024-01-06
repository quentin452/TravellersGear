package travellersgear.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.input.Keyboard;

import travellersgear.TravellersGear;
import travellersgear.client.handlers.ActiveAbilityHandler;
import travellersgear.client.handlers.CustomizeableGuiHandler;
import travellersgear.common.network.MessageOpenGui;
import travellersgear.common.network.MessageSlotSync;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

public class KeyHandler
{
	public static KeyBinding openInventory = new KeyBinding("TG.keybind.openInv", 71, "key.categories.inventory");
	public static KeyBinding activeAbilitiesWheel = new KeyBinding("TG.keybind.activeaAbilities", 19, "key.categories.inventory");
	public boolean[] keyDown = {false,false};
	public static float abilityRadial;
	public static boolean abilityLock = false;

	public KeyHandler()
	{
		ClientRegistry.registerKeyBinding(openInventory);
		ClientRegistry.registerKeyBinding(activeAbilitiesWheel);
	}

    private long lastInventoryActionTime = 0;
    private long lastAbilitiesActionTime = 0;
    private static final long INVENTORY_ACTION_DELAY = 500;
    private static final long ABILITIES_ACTION_DELAY = 500;

    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != Side.SERVER && event.phase == TickEvent.Phase.START && FMLClientHandler.instance().getClient().inGameHasFocus) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player == null) return;

            long currentTime = System.currentTimeMillis();
            boolean inventoryKeyPressed = openInventory.getIsKeyPressed() && !keyDown[0] && (currentTime - lastInventoryActionTime >= INVENTORY_ACTION_DELAY);
            boolean abilitiesWheelKeyPressed = activeAbilitiesWheel != null && activeAbilitiesWheel.getIsKeyPressed() && !keyDown[1] && ActiveAbilityHandler.instance.buildActiveAbilityList(player).length > 0 && (currentTime - lastAbilitiesActionTime >= ABILITIES_ACTION_DELAY);

            if (inventoryKeyPressed) {
                boolean[] hidden = new boolean[CustomizeableGuiHandler.moveableInvElements.size()];
                for (int bme = 0; bme < hidden.length; bme++) {
                    hidden[bme] = CustomizeableGuiHandler.moveableInvElements.get(bme).hideElement;
                }
                TravellersGear.packetHandler.sendToServer(new MessageSlotSync(player, hidden));
                TravellersGear.packetHandler.sendToServer(new MessageOpenGui(player, 0));
                keyDown[0] = true;
            } else if (keyDown[0]) {
                keyDown[0] = false;
            }

            if (abilitiesWheelKeyPressed) {
                if (abilityLock) {
                    abilityLock = false;
                    keyDown[1] = true;
                } else if (FMLClientHandler.instance().getClient().inGameHasFocus) {
                    abilityRadial = Math.min(1f, abilityRadial + ClientProxy.activeAbilityGuiSpeed);
                    if (abilityRadial >= 1) {
                        abilityLock = true;
                        keyDown[1] = true;
                    }
                }
            } else {
                if (keyDown[1]) {
                    assert activeAbilitiesWheel != null;
                    if (!activeAbilitiesWheel.getIsKeyPressed()) {
                        keyDown[1] = false;
                    }
                }
                if (!abilityLock) {
                    abilityRadial = Math.max(0f, abilityRadial - ClientProxy.activeAbilityGuiSpeed);
                }
            }
        }
    }
}
