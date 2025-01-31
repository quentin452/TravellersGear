package travellersgear.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.resources.IResource;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import travellersgear.TravellersGear;
import travellersgear.api.RenderTravellersGearEvent;
import travellersgear.api.TravellersGearAPI;
import travellersgear.client.gui.GuiArmorStand;
import travellersgear.client.gui.GuiButtonGear;
import travellersgear.client.gui.GuiConfigDisplayItems;
import travellersgear.client.gui.GuiTravellersInv;
import travellersgear.client.gui.GuiTravellersInvCustomization;
import travellersgear.client.handlers.ActiveAbilityHandler;
import travellersgear.client.handlers.CustomizeableGuiHandler;
import travellersgear.common.CommonProxy;
import travellersgear.common.blocks.TileEntityArmorStand;
import travellersgear.common.inventory.SlotRestricted;
import travellersgear.common.network.MessageOpenGui;
import travellersgear.common.network.MessageRequestNBTSync;
import travellersgear.common.network.MessageSlotSync;
import travellersgear.common.util.ModCompatability;
import travellersgear.common.util.TGClientCommand;
import travellersgear.common.util.Utils;
import baubles.api.BaublesApi;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ClientProxy extends CommonProxy
{
	public static HashMap<String, ItemStack[]> equipmentMap = new HashMap<String, ItemStack[]>();
	public static HashMap<String, ToolDisplayInfo[]> toolDisplayMap = new HashMap<String, ToolDisplayInfo[]>();
	public static int[] equipmentButtonPos;
	public static float activeAbilityGuiSpeed;
	public static float titleOffset;
	@Override
	public void preInit(FMLPreInitializationEvent event)
	{
		Configuration cfg = new Configuration(event.getSuggestedConfigurationFile());
		cfg.load();
		equipmentButtonPos = cfg.get("Options", "Button Position", new int[]{27,9}, "The position of the Equipment Button in the Inventory").getIntList();
		activeAbilityGuiSpeed = cfg.getFloat("Radial Speed", "Options", .15f, .05f, 1f, "The speed at which the radial for active abilities opens. Default is 15% per tick, minimum is 5%, maximum is 100%");
		titleOffset = (float)cfg.get("Options", "Title Offset", 0d, "Configures the vertical offset of the title above the players head. 0 is default, set to 1 to render above the players name, the other offsets will use that scale.").getDouble();
		cfg.save();

		CustomizeableGuiHandler.instance.preInit(event);
	}
	@SubscribeEvent
	public void loadTextures(TextureStitchEvent event)
	{
		List<ResourceLocation> txts = new ArrayList<ResourceLocation>();
		txts.add(new ResourceLocation("travellersgear","textures/gui/inventory_book.png"));
		txts.add(new ResourceLocation("travellersgear","textures/gui/inventory_digital.png"));
		txts.add(new ResourceLocation("travellersgear","textures/gui/inventory_epic.png"));
		int c=0;
		ResourceLocation customN = new ResourceLocation("travellersgear","textures/gui/inventory_custom"+c+".png");
		while(resourceExists(customN) && c<32)
		{
			txts.add(customN);
			c++;
			customN = new ResourceLocation("travellersgear","textures/gui/inventory_custom"+c+".png");
		}
		CustomizeableGuiHandler.invTextures = txts.toArray(new ResourceLocation[0]);
	}
	boolean resourceExists(ResourceLocation rl)
	{
		IResource r = null;
		try{
			r = Minecraft.getMinecraft().getResourceManager().getResource(rl);
		}catch(Exception e)
		{
			return false;
		}
		return r!=null;
	}
	@Override
	public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z)
	{
		switch(ID)
		{
		case 0:
			return new GuiTravellersInv(player);
		case 1:
			return new GuiArmorStand(player.inventory, (TileEntityArmorStand) world.getTileEntity(x, y, z));
		case 2:
			return new GuiTravellersInvCustomization(player);
		case 3:
			return new GuiConfigDisplayItems(player);
		}
		return null;
	}
	@SubscribeEvent
	public void guiPostInit(GuiScreenEvent.InitGuiEvent.Post event)
	{
		if(event.gui instanceof GuiInventory)
		{
			int xSize = 176;
			int ySize = 166;

			int guiLeft = (event.gui.width - xSize) / 2;
			int guiTop = (event.gui.height - ySize) / 2;
			if( !event.gui.mc.thePlayer.getActivePotionEffects().isEmpty() && ModCompatability.isNeiHidden())
				guiLeft = 160 + (event.gui.width - xSize - 200) / 2;
			event.buttonList.add(new GuiButtonGear(106, guiLeft + equipmentButtonPos[0], guiTop + equipmentButtonPos[1]));
		}
	}
	@SubscribeEvent
	public void guiDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event)
	{
		if(Loader.isModLoaded("GalacticraftCore"))
			if(event.gui instanceof GuiInventory)
			{
				List<GuiButtonGear> bList = null;
				try{
					bList = (List<GuiButtonGear>) GuiScreen.class.getDeclaredFields()[4].get(event.gui);
				}
				catch(Exception e){e.printStackTrace();}
				if(bList!=null)
					for(Object o : bList)
						if(o instanceof GuiButtonGear)
							return;
				int xSize = 176;
				int ySize = 166;
				int guiLeft = (event.gui.width - xSize) / 2;
				int guiTop = (event.gui.height - ySize) / 2;
				if( !event.gui.mc.thePlayer.getActivePotionEffects().isEmpty() && ModCompatability.isNeiHidden())
					guiLeft = 160 + (event.gui.width - xSize - 200) / 2;
				bList.add(new GuiButtonGear(106, guiLeft + equipmentButtonPos[0], guiTop + equipmentButtonPos[1]));
			}
	}
	@SubscribeEvent
	public void guiPostAction(GuiScreenEvent.ActionPerformedEvent.Post event)
	{
		if(event.gui instanceof GuiInventory && event.button.getClass().equals(GuiButtonGear.class))
		{
			boolean[] hidden = new boolean[CustomizeableGuiHandler.moveableInvElements.size()];
			for(int bme=0;bme<hidden.length;bme++)
				hidden[bme] = CustomizeableGuiHandler.moveableInvElements.get(bme).hideElement;
			TravellersGear.packetHandler.sendToServer(new MessageSlotSync(event.gui.mc.thePlayer,hidden));
//			PacketPipeline.INSTANCE.sendToServer(new PacketSlotSync(event.gui.mc.thePlayer,hidden));
			TravellersGear.packetHandler.sendToServer(new MessageOpenGui(event.gui.mc.thePlayer, 0));
//			PacketPipeline.INSTANCE.sendToServer(new PacketOpenGui(event.gui.mc.thePlayer, 0));
		}
	}


	@SubscribeEvent
	public void renderPlayerSpecialPre(RenderPlayerEvent.Specials.Pre event)
	{
		if(BaublesApi.getBaubles(event.entityPlayer)!=null)
			for(int i=0;i<BaublesApi.getBaubles(event.entityPlayer).getSizeInventory();i++)
			{
				ItemStack bb = BaublesApi.getBaubles(event.entityPlayer).getStackInSlot(i);
				if(bb!=null && bb.getItem().getArmorModel(event.entityPlayer, bb, 0)!=null)
				{
					GL11.glPushMatrix();
					GL11.glColor4f(1, 1, 1, 1);
					renderTravellersItem(bb, i, event.entityPlayer, event.renderer, event.partialRenderTick);
					GL11.glPopMatrix();
				}
			}
		if(equipmentMap.get(event.entityPlayer.getCommandSenderName())!=null)
		{
			for(int i=0;i<equipmentMap.get(event.entityPlayer.getCommandSenderName()).length;i++)
			{
				ItemStack eq = equipmentMap.get(event.entityPlayer.getCommandSenderName())[i];
				if(eq!=null && eq.getItem().getArmorModel(event.entityPlayer, eq, 0)!=null)
				{
					if(i==0)
						event.renderCape = false;
					GL11.glPushMatrix();
					GL11.glColor4f(1, 1, 1, 1);
					renderTravellersItem(eq, i, event.entityPlayer, event.renderer, event.partialRenderTick);
					GL11.glPopMatrix();
				}
			}
			if(Loader.isModLoaded("Botania"))
				this.handleBotaniaRenders(event.entityPlayer, event);
		}
		else if(event.entityPlayer.getPlayerCoordinates()!=null)
			TravellersGear.packetHandler.sendToServer(new MessageRequestNBTSync(event.entityPlayer,Minecraft.getMinecraft().thePlayer));
//			PacketPipeline.INSTANCE.sendToServer(new PacketRequestNBTSync(event.entityPlayer,Minecraft.getMinecraft().thePlayer));

		if(toolDisplayMap.get(event.entityPlayer.getCommandSenderName())!=null)
		{
			for(ToolDisplayInfo tdi : toolDisplayMap.get(event.entityPlayer.getCommandSenderName()))
				if(tdi!=null)
				{
					ItemStack stack = event.entityPlayer.inventory.getStackInSlot(tdi.slot);
					if(tdi.hideWhenEquipped&& ItemStack.areItemStacksEqual(stack, event.entityPlayer.getCurrentEquippedItem()))
						continue;

					if(stack!=null)
					{
						GL11.glPushMatrix();
						boolean isBlock = MinecraftForgeClient.getItemRenderer(stack, ItemRenderType.EQUIPPED)==null && stack.getItemSpriteNumber()==0 && stack.getItem() instanceof ItemBlock;
						if(tdi.rotateWithHead)
						{
							GL11.glRotatef(event.entityPlayer.rotationYawHead-event.entityPlayer.renderYawOffset, 0, 1, 0);
							GL11.glRotatef(event.entityPlayer.rotationPitch, 1, 0, 0);
						}
						GL11.glTranslated(.5,.5,0);
						GL11.glScalef(tdi.scale[0],tdi.scale[1],tdi.scale[2]);
						GL11.glTranslated(-.5,-.5,0);

						GL11.glTranslated(-.5/tdi.scale[0],-.25/tdi.scale[1], 0);

						if(tdi.translation!=null && tdi.translation.length>2)
							GL11.glTranslatef(tdi.translation[0]/tdi.scale[0],tdi.translation[1]/tdi.scale[1],tdi.translation[2]/tdi.scale[2]);

						GL11.glTranslated(.5,.5,0);
						if(tdi.rotation!=null && tdi.rotation.length>2)
						{
							GL11.glRotatef(tdi.rotation[1], 0,1,0);
							GL11.glRotatef(tdi.rotation[2], 0,0,1);
							GL11.glRotatef(tdi.rotation[0], 1,0,0);
						}
						if(!isBlock)
							GL11.glTranslated(-.5,-.5,0);

						Minecraft.getMinecraft().getTextureManager().bindTexture(Minecraft.getMinecraft().getTextureManager().getResourceLocation(stack.getItemSpriteNumber()));
						if(MinecraftForgeClient.getItemRenderer(stack, ItemRenderType.EQUIPPED)==null)
						{
							if(stack.getItemSpriteNumber()==0 && stack.getItem() instanceof ItemBlock)
							{
								Block b = Block.getBlockFromItem(stack.getItem());
								if(b.getRenderBlockPass()!=0)
								{
									GL11.glDepthMask(false);
									RenderBlocks.getInstance().renderBlockAsItem(b, stack.getItemDamage(), 1.0F);
									GL11.glDepthMask(true);
								}
								else
									RenderBlocks.getInstance().renderBlockAsItem(b, stack.getItemDamage(), 1.0F);
							}
							else
							{
								for(int pass=0; pass<stack.getItem().getRenderPasses(stack.getItemDamage()); pass++)
								{
									IIcon icon = event.entityPlayer.getItemIcon(stack, pass);
									ItemRenderer.renderItemIn2D(Tessellator.instance, icon.getMinU(),icon.getMinV(),icon.getMaxU(),icon.getMaxV(), icon.getIconWidth(), icon.getIconHeight(), 0.0625F);
								}
							}
						}
						else
						{
							GL11.glEnable(GL11.GL_BLEND);
							OpenGlHelper.glBlendFunc(770, 771, 0, 1);
							IItemRenderer customRender = MinecraftForgeClient.getItemRenderer(stack, ItemRenderType.EQUIPPED);
							customRender.renderItem(ItemRenderType.EQUIPPED, stack, RenderBlocks.getInstance(),event.entityPlayer);
						}

						GL11.glPopMatrix();
					}
				}
		}
	}
	public static float interpolateRotation(float par1, float par2, float par3)
	{
		float f3;
		for (f3 = par2 - par1; f3 < -180.0F; f3 += 360.0F)
			;
		while (f3 >= 180.0F)
			f3 -= 360.0F;
		return par1 + par3 * f3;
	}

	public static boolean renderTravellersItem(ItemStack stack, int slot, EntityPlayer player, RenderPlayer renderer, float partialRenderTick)
	{
		RenderTravellersGearEvent renderEvent = new RenderTravellersGearEvent(player, renderer, stack, partialRenderTick);
		MinecraftForge.EVENT_BUS.post(renderEvent);
		if(!renderEvent.shouldRender)
			return false;

		ModelBiped m = stack.getItem().getArmorModel(player, stack, 4+slot);
		if(m==null)
			return false;
		String tex = stack.getItem().getArmorTexture(stack, player, 4+slot, null);
		if(tex!=null && !tex.isEmpty())
			Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation(stack.getItem().getArmorTexture(stack, player, 0, null)));

		m.aimedBow = renderer.modelBipedMain.aimedBow;
		m.heldItemRight = renderer.modelBipedMain.heldItemRight;
		m.heldItemLeft = renderer.modelBipedMain.heldItemLeft;
		m.onGround = renderer.modelBipedMain.onGround;
		m.isRiding = renderer.modelBipedMain.isRiding;
		m.isChild = renderer.modelBipedMain.isChild;
		m.isSneak = renderer.modelBipedMain.isSneak;
		float f2 = interpolateRotation(player.prevRenderYawOffset, player.renderYawOffset, partialRenderTick);
		float f3 = interpolateRotation(player.prevRotationYawHead, player.rotationYawHead, partialRenderTick);
		float f4;
		if (player.isRiding() && player.ridingEntity instanceof EntityLivingBase)
		{
			EntityLivingBase entitylivingbase1 = (EntityLivingBase)player.ridingEntity;
			f2 = interpolateRotation(entitylivingbase1.prevRenderYawOffset, entitylivingbase1.renderYawOffset, partialRenderTick);
			f4 = Math.min(85.0F, Math.max(-85.0F, MathHelper.wrapAngleTo180_float(f3 - f2)));
			f2 = f3 - f4;
			if (f4 * f4 > 2500.0F)
				f2 += f4 * 0.2F;
		}
		float f13 = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialRenderTick;
		f4 = player.ticksExisted+partialRenderTick;
		float f5 = 0.0625F;
		float f6 = Math.min(1, player.prevLimbSwingAmount + (player.limbSwingAmount - player.prevLimbSwingAmount) * partialRenderTick);
		float f7 = (player.isChild()?3:1) *(player.limbSwing - player.limbSwingAmount * (1.0F - partialRenderTick));
		m.setLivingAnimations(player, f7, f6, partialRenderTick);
		m.render(player, f7, f6, f4, f3 - f2, f13, f5);
		return true;
	}

	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void onTooltip(ItemTooltipEvent event)
	{
		if(Minecraft.getMinecraft().currentScreen!=null && Minecraft.getMinecraft().currentScreen.getClass().equals(GuiTravellersInv.class))
		{
			if(event.itemStack.getItem().getClass().getName().endsWith("Knapsack"))
			{
				GuiTravellersInv guiContainer = (GuiTravellersInv)Minecraft.getMinecraft().currentScreen;
				int mX = Mouse.getEventX() * guiContainer.width / guiContainer.mc.displayWidth;
				int mY = guiContainer.height - Mouse.getEventY() * guiContainer.height / guiContainer.mc.displayHeight - 1;
				mX -= ((guiContainer.width - 218) / 2);
				mY -= ((guiContainer.height - 200) / 2);
				if(Utils.getSlotAtPosition(guiContainer, mX, mY) instanceof SlotRestricted && ((SlotRestricted)Utils.getSlotAtPosition(guiContainer, mX, mY)).type==SlotRestricted.SlotType.TINKERS_BAG)
					event.toolTip.add(1,EnumChatFormatting.AQUA+StatCollector.translateToLocal("TG.guitext.rightclickToOpen"));
			}
			event.toolTip.add(EnumChatFormatting.LIGHT_PURPLE+StatCollector.translateToLocal("TG.guitext.linkInChat"));
		}
	}

	@SubscribeEvent
	public void renderPlayerPre(RenderPlayerEvent.Pre event)
	{
		if(TravellersGearAPI.getTitleForPlayer(event.entityPlayer)==null || TravellersGearAPI.getTitleForPlayer(event.entityPlayer).isEmpty())
			return;
		String title=StatCollector.translateToLocal(TravellersGearAPI.getTitleForPlayer(event.entityPlayer));
		if(!RenderManager.instance.livingPlayer.equals(event.entityPlayer) && title!=null && !title.isEmpty() && !event.entityPlayer.isSneaking() && !Minecraft.getMinecraft().gameSettings.hideGUI)
		{	
			double d0 = event.entityPlayer.lastTickPosX + (event.entityPlayer.posX - event.entityPlayer.lastTickPosX) * (double)event.partialRenderTick;
			double d1 = event.entityPlayer.lastTickPosY + (event.entityPlayer.posY - event.entityPlayer.lastTickPosY) * (double)event.partialRenderTick;
			double d2 = event.entityPlayer.lastTickPosZ + (event.entityPlayer.posZ - event.entityPlayer.lastTickPosZ) * (double)event.partialRenderTick;
			d0 -= RenderManager.renderPosX;
			d1 -= RenderManager.renderPosY;
			d2 -= RenderManager.renderPosZ;

			int maxDist=64;
			double d3 = event.entityPlayer.getDistanceSqToEntity(RenderManager.instance.livingPlayer);

			if (d3 <= (double)(maxDist * maxDist))
			{
				FontRenderer fontrenderer = RenderManager.instance.getFontRenderer();
				float f = 0.75F;
				float f1 = 0.016666668F * f;
				float fOffset = .375f*titleOffset;
				GL11.glPushMatrix();
				GL11.glTranslatef((float)d0 + 0.0F, (float)d1 + event.entityPlayer.height + 0.2625F +fOffset, (float)d2);
				GL11.glNormal3f(0.0F, 1.0F, 0.0F);
				GL11.glRotatef(-RenderManager.instance.playerViewY, 0.0F, 1.0F, 0.0F);
				GL11.glRotatef(RenderManager.instance.playerViewX, 1.0F, 0.0F, 0.0F);
				GL11.glScalef(-f1, -f1, f1);
				GL11.glDisable(GL11.GL_LIGHTING);
				GL11.glDepthMask(false);
				GL11.glDisable(GL11.GL_DEPTH_TEST);
				GL11.glEnable(GL11.GL_BLEND);
				OpenGlHelper.glBlendFunc(770, 771, 1, 0);
				Tessellator tessellator = Tessellator.instance;
				byte b0 = 0;
				if (event.entityPlayer.getCommandSenderName().equalsIgnoreCase("deadmau5"))
					b0 = -10;
				GL11.glDisable(GL11.GL_TEXTURE_2D);
				tessellator.startDrawingQuads();
				int j = fontrenderer.getStringWidth(title) / 2;
				tessellator.setColorRGBA_F(0.0F, 0.0F, 0.0F, 0.25F);
				tessellator.addVertex((double)(-j - 1), (double)(-1 + b0), 0.0D);
				tessellator.addVertex((double)(-j - 1), (double)(8 + b0), 0.0D);
				tessellator.addVertex((double)(j + 1), (double)(8 + b0), 0.0D);
				tessellator.addVertex((double)(j + 1), (double)(-1 + b0), 0.0D);
				tessellator.draw();
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				fontrenderer.drawString(title, -fontrenderer.getStringWidth(title) / 2, b0, 553648127);
				GL11.glEnable(GL11.GL_DEPTH_TEST);
				GL11.glDepthMask(true);
				fontrenderer.drawString(title, -fontrenderer.getStringWidth(title) / 2, b0, -1);
				GL11.glEnable(GL11.GL_LIGHTING);
				GL11.glDisable(GL11.GL_BLEND);
				GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
				GL11.glPopMatrix();
			}
		}
	}

	@Override
	public void init()
	{
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(ActiveAbilityHandler.instance);
		CustomizeableGuiHandler.instance.init();

		FMLCommonHandler.instance().bus().register(new KeyHandler());
		RenderingRegistry.registerBlockHandler(new BlockRenderArmorStand());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityArmorStand.class, new TileRenderArmorStand());
		ClientCommandHandler.instance.registerCommand(new TGClientCommand());

	}

	@Override
	public World getClientWorld()
	{
		return FMLClientHandler.instance().getClient().theWorld;
	}

	@Optional.Method(modid = "Botania")
	public void handleBotaniaRenders(EntityPlayer player, RenderPlayerEvent event)
	{
		ItemStack[] tgInv = equipmentMap.get(player.getCommandSenderName());
		//BODY
		for(int i=0;i<tgInv.length;i++)
		{
			ItemStack eq = tgInv[i];
			if(eq!=null && eq.getItem() instanceof vazkii.botania.api.item.ICosmeticAttachable)
			{
				vazkii.botania.api.item.ICosmeticAttachable attachable = (vazkii.botania.api.item.ICosmeticAttachable) eq.getItem();
				ItemStack cosmetic = attachable.getCosmeticItem(eq);
				if(cosmetic != null)
				{
					GL11.glPushMatrix();
					GL11.glColor4f(1F, 1F, 1F, 1F);
					((vazkii.botania.api.item.IBaubleRender) cosmetic.getItem()).onPlayerBaubleRender(cosmetic, event, vazkii.botania.api.item.IBaubleRender.RenderType.BODY);
					GL11.glPopMatrix();
				}
			}
		}

		//HEAD
		float yaw = player.prevRotationYawHead + (player.rotationYawHead - player.prevRotationYawHead) * event.partialRenderTick;
		float yawOffset = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * event.partialRenderTick;
		float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * event.partialRenderTick;
		GL11.glPushMatrix();
		GL11.glRotatef(yawOffset, 0, -1, 0);
		GL11.glRotatef(yaw - 270, 0, 1, 0);
		GL11.glRotatef(pitch, 0, 0, 1);
		//BODY
		for(int i=0;i<tgInv.length;i++)
		{
			ItemStack eq = tgInv[i];
			if(eq!=null && eq.getItem() instanceof vazkii.botania.api.item.ICosmeticAttachable)
			{
				vazkii.botania.api.item.ICosmeticAttachable attachable = (vazkii.botania.api.item.ICosmeticAttachable) eq.getItem();
				ItemStack cosmetic = attachable.getCosmeticItem(eq);
				if(cosmetic != null)
				{
					GL11.glPushMatrix();
					GL11.glColor4f(1F, 1F, 1F, 1F);
					((vazkii.botania.api.item.IBaubleRender) cosmetic.getItem()).onPlayerBaubleRender(cosmetic, event, vazkii.botania.api.item.IBaubleRender.RenderType.HEAD);
					GL11.glPopMatrix();
				}
			}
		}
		GL11.glPopMatrix();

	}
}