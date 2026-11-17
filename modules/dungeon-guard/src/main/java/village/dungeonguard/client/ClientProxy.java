package village.dungeonguard.client;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.dungeonguard.AreaState;
import village.dungeonguard.CommonProxy;
import village.dungeonguard.client.AreaBannerHud;
import village.dungeonguard.client.QuietFireWarnings;

@SideOnly(value=Side.CLIENT)
public final class ClientProxy
extends CommonProxy {
    private final AreaBannerHud hud = new AreaBannerHud();
    private Configuration configuration;
    private boolean quietFireWarnings = true;

    @Override
    public void preInit(File directory) {
        this.configuration = new Configuration(new File(directory, "dungeonguard-client.cfg"));
        this.configuration.load();
        this.hud.enabled = this.configuration.getBoolean("showAreaBanner", "display", true, "Show the transparent dungeon/boss area banner.");
        this.quietFireWarnings = this.configuration.getBoolean("quietFireWarnings", "display", true, "Hide only the repeated standalone system message 'you cannot light fires'. Player chat and other warnings remain visible.");
        if (this.configuration.hasChanged()) {
            this.configuration.save();
        }
        MinecraftForge.EVENT_BUS.register((Object)this.hud);
        MinecraftForge.EVENT_BUS.register((Object)this);
        ClientCommandHandler.instance.registerCommand((ICommand)new ToggleCommand());
    }

    @Override
    public void receiveArea(final AreaState area) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        final NetHandlerPlayClient connection = minecraft.getConnection();
        final WorldClient world = minecraft.world;
        final EntityPlayerSP player = minecraft.player;
        minecraft.addScheduledTask(new Runnable(){

            @Override
            public void run() {
                if (connection != null && connection == minecraft.getConnection() && world != null && world == minecraft.world && player != null && player == minecraft.player) {
                    ClientProxy.this.hud.accept(area);
                }
            }
        });
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void chat(ClientChatReceivedEvent event) {
        if (this.quietFireWarnings && QuietFireWarnings.shouldSuppress(event.getType() == ChatType.SYSTEM, event.getMessage() == null ? null : event.getMessage().getUnformattedText())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable(){

            @Override
            public void run() {
                ClientProxy.this.hud.clear();
            }
        });
    }

    private final class ToggleCommand
    extends CommandBase {
        private ToggleCommand() {
        }

        public String getName() {
            return "dghud";
        }

        public int getRequiredPermissionLevel() {
            return 0;
        }

        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return true;
        }

        public String getUsage(ICommandSender sender) {
            return "/dghud [on|off]";
        }

        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length > 1 || args.length == 1 && !args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off")) {
                throw new CommandException("Use /dghud [on|off].", new Object[0]);
            }
            ((ClientProxy)ClientProxy.this).hud.enabled = args.length == 0 ? !((ClientProxy)ClientProxy.this).hud.enabled : args[0].equalsIgnoreCase("on");
            ClientProxy.this.configuration.get("display", "showAreaBanner", true).set(((ClientProxy)ClientProxy.this).hud.enabled);
            ClientProxy.this.configuration.save();
            sender.sendMessage((ITextComponent)new TextComponentString("[Dungeon Guard] Area banner " + (((ClientProxy)ClientProxy.this).hud.enabled ? "enabled." : "hidden.")));
        }
    }
}

