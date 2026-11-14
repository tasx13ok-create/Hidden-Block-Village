package narutodestinationvillages.compat;
import java.util.*;
/** Independently implemented lookup using original public rank/default-value metadata. */
public final class XpRankMultipliers {
 private XpRankMultipliers(){}
 public static Map<String,Float> defaults(){Map<String,Float> result=new LinkedHashMap<>();
  result.put("Villager",1.0f);
  result.put("Genin",1.1f);
  result.put("Chunin",1.2f);
  result.put("TokubetsuJonin",1.3f);
  result.put("Jonin",1.4f);
  result.put("Missing-Nin",1.5f);
  result.put("ExplosionCorps",1.7f);
  result.put("Taka",1.7f);
  result.put("Hebi",1.7f);
  result.put("oftheBloodyMist",1.6f);
  result.put("Samurai",1.2f);
  result.put("Anbu",1.5f);
  result.put("MedicalTeam",1.5f);
  result.put("IntelTeam",1.5f);
  result.put("AnbuCaptain",1.6f);
  result.put("MedicalCaptain",1.6f);
  result.put("IntelCaptain",1.6f);
  result.put("Akatsuki",2.0f);
  result.put("KinkakuForce",1.9f);
  result.put("12GuardianNinja",1.9f);
  result.put("PuppetBrigade",1.7f);
  result.put("AcademyStudent",1.0f);
  result.put("7NinjaSwordsmen",1.8f);
  result.put("Kage",2.0f);
  result.put("AkatsukiLeader",2.0f);
  result.put("Sannin",1.9f);
  result.put("D-Rank",1.1f);
  result.put("C-Rank",1.2f);
  result.put("B-Rank",1.3f);
  result.put("A-Rank",1.5f);
  result.put("S-Rank",1.7f);
  result.put("TailedBeast",2.0f);
  result.put("Advisor",1.9f);
  result.put("ShadowKage",1.95f);
  result.put("WhiteFang",2.0f);
  result.put("oftheSharingan",2.0f);
  result.put("YellowFlash",2.0f);
  result.put("GreenBeast",2.0f);
  result.put("Hokage",2.0f);
  result.put("Kazekage",2.0f);
  result.put("Mizukage",2.0f);
  result.put("Tsuchikage",2.0f);
  result.put("Raikage",2.0f);
  result.put("Rogue",1.5f);
  result.put("SeniorAkatsuki",2.0f);
  return Collections.unmodifiableMap(result);
 }
}
