package village.fieldhud;

/** Display-only training summaries from the synchronized level, never political rank grants. */
public final class TrainingGrade {
    private TrainingGrade(){}
    public static String forLevel(int level){
        if(level<1)return "Awaiting record";
        if(level<5)return "Academy learner";
        if(level<12)return "Field trainee";
        if(level<24)return "Chakra adept";
        if(level<40)return "Veteran shinobi";
        return "Elite fighter";
    }
}
