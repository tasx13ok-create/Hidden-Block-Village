package village.dungeonguard.client;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.dungeonguard.AreaState;
import village.dungeonguard.CommonProxy;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    private final AreaBannerHud hud = new AreaBannerHud();
    private Configuration configuration;
    @Override public void preInit(File directory) {
        configuration = new Configuration(new File(directory, "dungeonguard-client.cfg"));
        configuration.load();
        hud.enabled = configuration.getBoolean("showAreaBanner", "display", true, "Show the transparent dungeon/boss area banner.");
        if (configuration.hasChanged()) configuration.save();
        MinecraftForge.EVENT_BUS.register(hud);
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new ToggleCommand());
    }
    @Override public void receiveArea(final AreaState area) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        final NetHandlerPlayClient connection = minecraft.getConnection();
        minecraft.addScheduledTask(new Runnable() {
            @Override public void run() {
                if (connection != null && connection == minecraft.getConnection() && minecraft.world != null) hud.accept(area);
            }
        });
    }
    @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() { @Override public void run() { hud.clear(); } });
    }
    private final class ToggleCommand extends CommandBase {
        @Override public String getName() { return "dghud"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }
        @Override public String getUsage(ICommandSender sender) { return "/dghud [on|off]"; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off")))
                throw new CommandException("Use /dghud [on|off].");
            hud.enabled = args.length == 0 ? !hud.enabled : args[0].equalsIgnoreCase("on");
            configuration.get("display", "showAreaBanner", true).set(hud.enabled); configuration.save();
            sender.sendMessage(new TextComponentString("[Dungeon Guard] Area banner " + (hud.enabled ? "enabled." : "hidden.")));
        }
    }
}
