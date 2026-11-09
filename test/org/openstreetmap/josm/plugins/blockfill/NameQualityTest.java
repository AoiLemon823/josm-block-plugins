package org.openstreetmap.josm.plugins.blockfill;
import java.util.*;
public final class NameQualityTest {
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        int total=0;
        for(String level:List.of("FIRST_HOME","IMPROVEMENT","LUXURY")){
            PlaceNameGenerator g=new PlaceNameGenerator(64031103L+level.hashCode());Set<String> used=new HashSet<>();
            for(int batch=0;batch<8;batch++)for(String n:g.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","百叶路",level,20,used)){
                check(n.length()<=32,"overlong name: "+n);
                check(!n.matches(".*([\\p{IsHan}])\\1.*"),"duplicate Han character: "+n);
                for(String banned:List.of("十恶不赦","忘恩负义","狼心狗肺","穷凶极恶","丧心病狂","罔谈彼短","治本于农","坐朝问道","悦豫且康","遗臭万年","奄宅曲阜","率宾归王"))check(!n.contains(banned),"banned prose: "+n);
                check(used.add(PlaceNameGenerator.normalize(n)),"duplicate generated name: "+n+" (batch "+batch+", level "+level+")");g.markUsed(n);total++;
            }
        }
        PlaceNameGenerator ordinary=new PlaceNameGenerator(20261104);
        var first=ordinary.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","锦江路","FIRST_HOME",20,List.of());
        check(first.stream().noneMatch(n->n.contains("序")||n.contains("著")||n.contains("玺台")||n.contains("嘉佰道")),"刚需档混入改善/豪宅项目词："+first);
        PlaceNameGenerator improveGen=new PlaceNameGenerator(20261105);var improve=improveGen.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","锦江路","IMPROVEMENT",20,List.of());
        check(improve.stream().anyMatch(n->n.contains("锦江序")||n.contains("臻礼著")||n.contains("嘉佰道")),"改善档未覆盖文雅项目名: "+improve);
        PlaceNameGenerator luxuryGen=new PlaceNameGenerator(20261106);var luxury=luxuryGen.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","锦江路","LUXURY",20,List.of());
        check(luxury.stream().anyMatch(n->n.contains("别墅")||n.contains("山庄")||n.contains("院墅")),"豪宅档缺少别墅/山庄风格: "+luxury);
        System.out.println("PASS: "+total+" residential stress samples; tier separation and premium/luxury lexicon");
    }
}
