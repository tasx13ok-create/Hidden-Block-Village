package village.fieldhud;

/** Authored 12px seal drawings in a16px tile; independent of item textures. */
public final class SealArt {
    public enum Mark {
        EMBER("ember","Ember Ward",0xED8963,new String[]{
            "000000000000","000000100000","000001310000","000013221000",
            "000132221000","001322221100","001222122100","011221122110",
            "012221222210","001222222100","000111111000","000000000000"}),
        CURRENT("current","Current Ward",0x59C6D9,new String[]{
            "000000000000","000000000000","000000111000","000001232100",
            "000112223210","001232112210","012321012210","012210012210",
            "001221122100","000122221000","000011110000","000000000000"}),
        GALE("gale","Gale Ward",0xACD2A0,new String[]{
            "000000000000","000000000000","000011100000","000123210000",
            "001232210000","012221110000","012110000000","012211111100",
            "001232223210","000111112210","000000001100","000000000000"}),
        FLINT("flint","Flint Ward",0xC5B8A0,new String[]{
            "000000000000","000001100000","000012210000","000123321000",
            "001232232100","012322223210","012222222210","001222222100",
            "000122221000","000012210000","000001100000","000000000000"}),
        BLOOM("bloom","Bloom Ward",0x85CBAA,new String[]{
            "000000000000","000110011000","001231123100","001232223100",
            "000122221000","001222222100","012223322210","012222222210",
            "001221122100","000110011000","000000000000","000000000000"}),
        RIME("rime","Rime Ward",0xBEDFF1,new String[]{
            "000000000000","001001100100","000112211000","000012210000",
            "001112211100","012223322210","012223322210","001112211100",
            "000012210000","000112211000","001001100100","000000000000"}),
        STORM("storm","Storm Ward",0xB4BEF3,new String[]{
            "000000000000","000001110000","000012210000","000122100000",
            "001232100000","012332211100","001112332210","000001232100",
            "000001221000","000012210000","000011100000","000000000000"}),
        DUSK("dusk","Dusk Ward",0xA4A3CD,new String[]{
            "000000000000","000011110000","000123210000","001232100000",
            "012321000000","012210000000","012210000000","012321000000",
            "001232110000","000123221100","000011110000","000000000000"}),
        DAWN("dawn","Dawn Ward",0xE8CF91,new String[]{
            "000000000000","000001100000","001001100100","000111111000",
            "001232232100","012322223210","012322223210","001232232100",
            "000111111000","001001100100","000001100000","000000000000"}),
        SCROLL("scroll","",0xD8C7A5,new String[]{
            "000000000000","011111111110","012333332210","012211112210",
            "012233322210","012211112210","012233322210","012211112210",
            "012222222210","011111111110","000110011000","000000000000"}),
        WEAPON("weapon","",0xBDD3DA,new String[]{
            "000000000000","001100001100","001210012100","000121121000",
            "000012210000","000012210000","000121121000","001210012100",
            "012100001210","011000000110","000000000000","000000000000"}),
        SEAL("seal","",0x94CDD8,new String[]{
            "000000000000","000011110000","001122221100","012211112210",
            "012101102210","012012210210","012012210210","012201101210",
            "012211112210","001122221100","000011110000","000000000000"});

        public final String key,order;
        public final int tone;
        private final byte[] pixels=new byte[256];
        private final int[] palette=new int[5];
        private static final Mark[] ALL=values();
        Mark(String key,String order,int tone,String[] drawing) {
            this.key=key;this.order=order;this.tone=tone;
            if(drawing.length!=12)throw new IllegalArgumentException(key+" needs12 rows");
            for(int y=0;y<12;y++) {
                if(drawing[y].length()!=12)throw new IllegalArgumentException(key+" row"+y+" needs12 columns");
                for(int x=0;x<12;x++) {
                    int v=drawing[y].charAt(x)-'0';if(v<0||v>3)throw new IllegalArgumentException(key+" bad pigment");
                    if(v>0)pixels[(y+2)*16+x+2]=(byte)(v+1);
                }
            }
            for(int y=1;y<15;y++)for(int x=1;x<15;x++)if(pixel(x,y)>=2) {
                edge(x-1,y);edge(x+1,y);edge(x,y-1);edge(x,y+1);
            }
            palette[1]=0x0B1722;palette[2]=mix(0x192D3B,tone,.32);palette[3]=tone;palette[4]=mix(tone,0xEFF8F9,.64);
        }
        private void edge(int x,int y){if(pixels[y*16+x]==0)pixels[y*16+x]=1;}
        public int pixel(int x,int y){return x<0||y<0||x>=16||y>=16?0:pixels[y*16+x];}
        public int color(int x,int y,double sheen) {
            int v=pixel(x,y);if(v==0)return 0;
            int color=palette[v];
            if(v>=3&&sheen>0) {
                double band=30*(1-Math.max(0,Math.min(1,sheen)));
                double strength=Math.max(0,1-Math.abs(x+y-band)/2.0)*Math.min(1,sheen*2);
                color=mix(color,0xF4FBFF,strength*.7);
            }
            return color;
        }
    }
    private SealArt() {}
    private static int mix(int a,int b,double t) {
        t=Math.max(0,Math.min(1,t));int r=(int)Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t);
        int g=(int)Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t),blue=(int)Math.round((a&255)*(1-t)+(b&255)*t);
        return (r<<16)|(g<<8)|blue;
    }
    public static Mark forOrder(String order){for(Mark m:Mark.ALL)if(!m.order.isEmpty()&&m.order.equals(order))return m;return Mark.SEAL;}
    public static Mark forArt(String art){if(art!=null)for(Mark m:Mark.ALL)if(!m.order.isEmpty()&&art.startsWith(m.key+"_"))return m;return Mark.SEAL;}
    public static Mark forAbility(String id,String school){
        if(school!=null)for(Mark m:Mark.ALL)if(!m.order.isEmpty()&&m.key.equals(school))return m;
        return forArt(id==null?null:id.substring(id.lastIndexOf('/')+1));
    }
}
