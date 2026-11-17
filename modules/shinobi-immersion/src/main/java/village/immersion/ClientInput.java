package village.immersion;

/** Client jump state, isolated so the server never loads client classes. */
final class ClientInput {
    private ClientInput() {}
    static boolean jump() {
        try { return net.minecraft.client.Minecraft.getMinecraft().gameSettings.keyBindJump.isKeyDown(); }
        catch (Throwable serverSide) { return false; }
    }
    static boolean local(net.minecraft.entity.player.EntityPlayer p) {
        try { return p == net.minecraft.client.Minecraft.getMinecraft().player; }
        catch (Throwable serverSide) { return false; }
    }
}
