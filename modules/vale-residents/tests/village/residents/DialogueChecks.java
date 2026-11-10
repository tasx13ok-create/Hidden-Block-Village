package village.residents;

/** Bounded schema/authoring checks; native permissions, rewards and persistence are separate. */
public final class DialogueChecks {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static void rejects(String text,String why){try{DialogueBook.parse(text);throw new AssertionError(why);}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        String json=DialogueBook.defaultJson();DialogueBook.Book b=DialogueBook.parse(json);
        check(b.roles.size()==3,"Three complete role definitions");
        check(b.roles.get(ResidentRole.ACADEMY).pages.size()==2,"Academy has usable linked advice page");
        check(b.roles.get(ResidentRole.CONTRACTS).pages.get("welcome").choices.size()==3,"Contracts has menu, accept and claim");
        rejects(json.replace("\"schema\":1","\"schema\":2"),"Future schema retained/rejected");
        rejects(json.replace("\"schema\":1","\"schema\":\"1\""),"Wrong schema type");
        rejects(json.replace("\"schema\":1","\"schema\":1.0"),"Noninteger schema");
        rejects(json.replace("\"schema\":1","\"schema\":1,\"scripts\":[]"),"No arbitrary script extension");
        rejects(json.replace("\"action\":\"ACADEMY\"","\"action\":\"COMMAND\",\"target\":\"op\""),"No arbitrary command action");
        rejects(json.replace("\"action\":\"ACADEMY\"","\"action\":\"ACADEMY\",\"target\":\"op\""),"Menu action rejects target");
        rejects(json.replace("\"target\":\"advice\"","\"target\":\"missing\""),"Dangling page edge");
        rejects(json.replace("\"start\":\"welcome\"","\"start\":\"missing\""),"Missing start");
        rejects(json.replace("\"id\":\"advice\",\"lines\"","\"id\":\"welcome\",\"lines\""),"Duplicate page ID");
        rejects(json.replace("\"id\":\"advice\",\"label\"","\"id\":\"study\",\"label\""),"Duplicate choice ID");
        rejects(json.replace("\"target\":\"supply_run\"","\"target\":\"supply_run;op\""),"Identifier injection");
        rejects(json.replace("Study techniques","Bad\\nlabel"),"Control character in label");
        rejects(json.replace("Study techniques",new String(new char[65]).replace('\0','x')),"Label bound");
        rejects(new String(new char[65537]).replace('\0',' '),"Definition memory bound");
        check(b.roles.get(ResidentRole.CONTRACTS).pages.get("welcome").choices.get(1).target.equals("supply_run"),"Original supply contract authoring target");
        try{b.roles.clear();throw new AssertionError("Mutable roles");}catch(UnsupportedOperationException expected){checks++;}
        try{b.roles.get(ResidentRole.ACADEMY).pages.clear();throw new AssertionError("Mutable pages");}catch(UnsupportedOperationException expected){checks++;}
        try{b.roles.get(ResidentRole.ACADEMY).pages.get("welcome").choices.clear();throw new AssertionError("Mutable choices");}catch(UnsupportedOperationException expected){checks++;}
        System.out.println("DIALOGUE_COMPONENT_CHECKS_PASS "+checks);
    }
}
