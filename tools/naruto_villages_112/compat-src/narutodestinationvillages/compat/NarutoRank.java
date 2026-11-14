package narutodestinationvillages.compat;
public enum NarutoRank {
    VILLAGER("Villager","Villager",0,0),
    ACADEMY_STUDENT("AcademyStudent","Academy Student",21,1),
    GENIN("Genin","Genin",1,2),
    CHUNIN("Chunin","Chunin",2,3),
    TOKUBETSU_JONIN("TokubetsuJonin","Tokubetsu Jonin",3,4),
    JONIN("Jonin","Jonin",4,5),
    MISSING_NIN("Missing-Nin","Missing-Nin",5,5),
    EXPLOSION_CORPS("ExplosionCorps","Explosion Corps",6,5),
    TAKA("Taka","Taka",7,5),
    HEBI("Hebi","Hebi",8,5),
    OF_THE_BLOODY_MIST("oftheBloodyMist","of the Bloody Mist",9,5),
    SAMURAI("Samurai","Samurai",10,5),
    ANBU("Anbu","Anbu",11,6),
    MEDICAL_TEAM("MedicalTeam","Medical Team",12,6),
    INTEL_TEAM("IntelTeam","Intel Team",13,6),
    ANBU_CAPTAIN("AnbuCaptain","Anbu Captain",14,7),
    MEDICAL_CAPTAIN("MedicalCaptain","Medical Captain",15,7),
    INTEL_CAPTAIN("IntelCaptain","Intel Captain",16,7),
    AKATSUKI("Akatsuki","Akatsuki",17,7),
    KINKAKU_FORCE("KinkakuForce","Kinkaku Force",18,7),
    TWELVE_GUARDIAN_NINJA("12GuardianNinja","12 Guardian Ninja",19,7),
    PUPPET_BRIGADE("PuppetBrigade","Puppet Brigade",20,7),
    SEVEN_NINJA_SWORDSMEN("7NinjaSwordsmen","7 Ninja Swordsmen o/t Mist",22,7),
    KAGE("Kage","Kage",23,8),
    AKATSUKI_LEADER("AkatsukiLeader","Akatsuki Leader",24,8),
    SANNIN("Sannin","Sannin",25,8),
    D_RANK("D-Rank","D-Rank",26,2),
    C_RANK("C-Rank","C-Rank",27,3),
    B_RANK("B-Rank","B-Rank",28,4),
    A_RANK("A-Rank","A-Rank",29,5),
    S_RANK("S-Rank","S-Rank",30,6),
    TAILED_BEAST("TailedBeast","Tailed Beast",31,8),
    ADVISOR("Advisor","Advisor",32,8),
    SHADOW_KAGE("ShadowKage","Shadow Kage",33,8),
    WHITE_FANG("WhiteFang","White Fang",34,8),
    OF_THE_SHARINGAN("oftheSharingan","of the Sharingan",35,8),
    YELLOW_FLASH("YellowFlash","Yellow Flash",36,8),
    GREEN_BEAST("GreenBeast","Green Beast",37,8),
    HOKAGE("Hokage","Hokage",-1,9),
    KAZEKAGE("Kazekage","Kazekage",-1,9),
    MIZUKAGE("Mizukage","Mizukage",-1,9),
    TSUCHIKAGE("Tsuchikage","Tsuchikage",-1,9),
    RAIKAGE("Raikage","Raikage",-1,9),
    ROGUE("Rogue","Rogue",-1,5),
    SENIOR_AKATSUKI("SeniorAkatsuki","Senior Akatsuki",-1,8);

    private final String id,display;private final int legacy,tier;
    NarutoRank(String i,String d,int l,int t){id=i;display=d;legacy=l;tier=t;}
    public String getId(){return id;}public String getDisplayName(){return display;}
    public int getLegacyInt(){return legacy;}public int getTier(){return tier;}
    public static NarutoRank fromId(String name){if(name!=null)for(NarutoRank r:values())if(r.id.equalsIgnoreCase(name)||r.display.equalsIgnoreCase(name)||r.name().equalsIgnoreCase(name))return r;return null;}
    public static NarutoRank fromLegacyInt(int value){for(NarutoRank r:values())if(r.legacy==value&&value>=0)return r;return null;}
}
