package village.vale;

import net.minecraft.nbt.NBTTagCompound;

/** Versioned, forward-only personal course progress; rewards are settled separately. */
public final class AcademyProgress {
    public static final int VERSION=1, COMPLETE=6;
    public static final String[] TITLES={"Arrival","Choose your training nature","Walk the three blue markers","Defend: evade, guard, substitute","Land three academy arts","Cook and deliver a travel ration","Academy basics complete"};
    private AcademyProgress() {}
    public static boolean readable(NBTTagCompound s){return s.getInteger("schema")==VERSION&&s.getInteger("stage")>=0&&s.getInteger("stage")<=COMPLETE;}
    public static NBTTagCompound fresh(){NBTTagCompound s=new NBTTagCompound();s.setInteger("schema",VERSION);return s;}
    public static boolean advance(NBTTagCompound s,int expected){
        if(!readable(s)||expected<0||expected>=COMPLETE||s.getInteger("stage")!=expected)return false;
        s.setInteger("stage",expected+1);s.setInteger("objective",0);return true;
    }
    public static boolean marker(NBTTagCompound s,int index,boolean legitimateMovement){
        if(!readable(s)||s.getInteger("stage")!=2||!legitimateMovement||index!=s.getInteger("objective")||index>=3)return false;
        s.setInteger("objective",index+1);return true;
    }
    public static boolean defense(NBTTagCompound s,int flag){
        if(!readable(s)||s.getInteger("stage")!=3||(flag!=1&&flag!=2&&flag!=4))return false;
        int before=s.getInteger("objective");s.setInteger("objective",before|flag);return before!=(before|flag);
    }
    public static boolean art(NBTTagCompound s){
        if(!readable(s)||s.getInteger("stage")!=4||s.getInteger("objective")>=3)return false;
        s.setInteger("objective",s.getInteger("objective")+1);return true;
    }
    public static boolean ready(NBTTagCompound s){
        if(!readable(s))return false;int stage=s.getInteger("stage"),count=s.getInteger("objective");
        return stage==2&&count==3||stage==3&&count==7||stage==4&&count==3||stage==5&&s.getBoolean("craftedRation");
    }
}
