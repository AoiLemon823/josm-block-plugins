package org.openstreetmap.josm.plugins.blockfill;
import java.util.*;
public final class PlaceNameGeneratorTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        Set<String> used=new HashSet<>();PlaceNameGenerator g=new PlaceNameGenerator(7);
        for(var level:PlaceNameGenerator.ResidentialLevel.values()){
            Set<String> unique=new HashSet<>();
            for(int batch=0;batch<10;batch++){
                var names=g.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","百叶路",level.name(),20,used);
                for(String n:names){check(!n.contains("中国")&&!n.contains("中粮")&&!n.contains("中车"),"real country/brand leakage");unique.add(n);g.markUsed(n);used.add(n);}
            }
            check(unique.size()==200,"each residential style supports 200 distinct names: "+level);
        }
        for(var level:PlaceNameGenerator.CommercialLevel.values()){
            Set<String> unique=new HashSet<>();for(int batch=0;batch<10;batch++){
                for(String n:g.candidates(PlaceNameGenerator.Kind.COMMERCIAL,"榆川市","静海区","百叶路",level.name(),20,used)){unique.add(n);g.markUsed(n);used.add(n);check(!n.contains("奢品")&&!n.contains("云端汇"),"luxury style must not be literal label");}
            }check(unique.size()==200,"each mall style supports 200 distinct names: "+level);
        }
        // Merely requesting candidates must not consume them or prevent choosing the previewed name.
        PlaceNameGenerator a=new PlaceNameGenerator(31),b=new PlaceNameGenerator(31);
        var batch=a.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川","静海","龙华路","OLD_HOUSING",12,List.of());
        var same=b.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川","静海","龙华路","OLD_HOUSING",12,List.of());check(batch.equals(same),"seeded determinism");
        for(String n:batch)a.markUsed(n);
        check(a.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川","静海","龙华路","OLD_HOUSING",20,List.of()).stream().noneMatch(batch::contains),"committed names cannot repeat");
        boolean failed=false;try{g.next(PlaceNameGenerator.Kind.RESIDENTIAL,"","静海区","","IMPROVEMENT",List.of());}catch(IllegalArgumentException ex){failed=true;}check(failed,"blank context rejected");
        // A supplied developer prefix is honored on new builds, while local village/community naming ignores it.
        check(g.next(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","","IMPROVEMENT","华粮房地产开发有限公司",List.of()).startsWith("华粮·"),"normalized developer prefix on improvement homes");
        check(g.next(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","","LUXURY","华粮",List.of()).startsWith("华粮·"),"developer prefix on luxury homes");
        check(!g.next(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","龙华路","URBAN_VILLAGE","华粮",List.of()).contains("华粮"),"developer must not affect villages");
        check(!g.next(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","百叶路","OLD_HOUSING","华粮",List.of()).contains("华粮"),"developer must not affect old communities");
        check(!g.next(PlaceNameGenerator.Kind.COMMERCIAL,"榆川市","静海区","百叶路","COMMUNITY","景润",List.of()).contains("景润"),"developer must not affect community retail");
        check(g.next(PlaceNameGenerator.Kind.COMMERCIAL,"榆川市","静海区","","MIDRANGE","景润",List.of()).startsWith("景润"),"developer prefix on midrange retail");
        for(var style:PlaceNameGenerator.BusinessLevel.values())check(g.next(PlaceNameGenerator.Kind.BUSINESS,"榆川市","静海区","",""+style,"景润",List.of()).startsWith("景润"),"developer on office style "+style);
        check(g.next(PlaceNameGenerator.Kind.COMMERCIAL,"榆川市","静海区","","MIDRANGE","景润房地产开发集团",List.of()).startsWith("景润"),"legal developer suffix normalized");
        boolean collision=false;Set<String> names=new HashSet<>();for(int i=0;i<20;i++){String n=g.next(PlaceNameGenerator.Kind.BUSINESS,"榆川市","静海区","","OFFICE","",names);check(names.add(PlaceNameGenerator.normalize(n)),"business names must dedupe");}
        System.out.println("PASS: expanded residential/commercial vocabulary, developer scope, 4 business-office styles, dedupe, morphology");
    }
}
