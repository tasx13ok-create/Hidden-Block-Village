package village.vale;

/** Pure worn-texture contract. Resource identity only; never grants a mode or alters combat. */
public final class ArmorSkinPaths {
    private static final String[] WARDS={"ember","current","gale","flint","bloom","rime","storm","dusk","dawn"};
    private ArmorSkinPaths(){}
    public static String texture(String ward,boolean leggings,boolean lightningActive){
        boolean known=false;for(String value:WARDS)if(value.equals(ward)){known=true;break;}
        if(!known)throw new IllegalArgumentException("Unknown ward texture family");
        String family="storm".equals(ward)&&lightningActive?"lightning_chakra":ward;
        return "valeorders:textures/models/armor/"+family+"_layer_"+(leggings?"2":"1")+".png";
    }
    public static String garment(String category,String key,boolean leggings){
        if(!"focus".equals(category)&&!"outfits".equals(category))throw new IllegalArgumentException("Unknown garment family");
        String prefix="focus".equals(category)?"focus_":"outfit_";
        if(key==null||!key.startsWith(prefix)||!key.matches("[a-z_]+"))throw new IllegalArgumentException("Invalid garment key");
        return "valeorders:textures/models/armor/"+category+"/"+key+"_layer_"+(leggings?"2":"1")+".png";
    }
}
