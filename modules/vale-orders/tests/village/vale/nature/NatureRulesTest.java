package village.vale.nature;

import java.util.HashSet;
import java.util.Set;

/** Run without Forge bootstrap: geometry limits and conservative restoration policy. */
public final class NatureRulesTest {
    private static int assertions;
    private static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        for(int[] facing:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            int[][] wall=NatureRules.cover(100,80,-100,facing[0],facing[1]);Set<String> unique=new HashSet<>();
            for(int[] block:wall){unique.add(block[0]+","+block[1]+","+block[2]);
                check((block[0]-100)*facing[0]+(block[2]+100)*facing[1]==2,"Wall moved behind user");
                check(block[1]>=80&&block[1]<=81,"Cover height unbounded");
                check(Math.abs((block[0]-100)*-facing[1]+(block[2]+100)*facing[0])<=1,"Cover width unbounded");
            }check(unique.size()==6,"Overlapping cover cells");
        }
        check(NatureRules.cone(0,0,8,0,0,1,8,.78),"Forward cone excludes endpoint");
        check(!NatureRules.cone(0,0,8.01,0,0,1,8,.78),"Cone exceeds range");
        check(!NatureRules.cone(0,0,-1,0,0,1,8,.78),"Cone hits behind user");
        check(!NatureRules.cone(0,0,0,0,0,1,8,.78),"Zero-distance cone normalizes unsafely");
        check(!NatureRules.cone(Double.NaN,0,2,0,0,1,8,.78),"Nonfinite geometry accepted");
        check(!NatureRules.cone(0,0,2,0,0,0,8,.78),"Zero look accepted");
        check(NatureRules.cone(3,0,3,0,0,1,6,.48)&&!NatureRules.cone(3,0,3,0,0,1,8,.78),"Water must catch a flank Fire misses");
        check(NatureRules.roomFor(0,0,6),"Empty world cannot place cover");
        check(!NatureRules.roomFor(0,6,6),"Owner budget permits two walls");
        check(!NatureRules.roomFor(768,0,6),"World cover budget exceeded");
        check(!NatureRules.roomFor(-1,0,6),"Corrupt negative budget accepted");
        check(NatureRules.mayRestore(true,false,4,4),"Owned state cannot restore");
        check(!NatureRules.mayRestore(false,false,4,4),"Unloaded chunk forced by restore");
        check(!NatureRules.mayRestore(true,true,4,4),"Tile data may be overwritten");
        check(!NatureRules.mayRestore(true,false,5,4),"Player edit may be overwritten");
        boolean rejected=false;try{NatureRules.cover(0,0,0,1,1);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Diagonal wall direction accepted");
        System.out.println("Nature rules: "+assertions+" assertions passed");
    }
}
