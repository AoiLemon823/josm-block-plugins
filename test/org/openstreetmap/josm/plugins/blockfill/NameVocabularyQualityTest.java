package org.openstreetmap.josm.plugins.blockfill;
import java.util.*;
public final class NameVocabularyQualityTest {
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[]args){
  PlaceNameGenerator g=new PlaceNameGenerator(64031103);Set<String> existing=new HashSet<>();int total=0;
  for(String level:List.of("FIRST_HOME","IMPROVEMENT","LUXURY"))for(int batch=0;batch<75;batch++)for(String name:g.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","锦江路",level,20,existing)){
   check(name.length()<=32,"overlong name: "+name);check(!name.matches(".*([\\p{IsHan}])\\1.*"),"adjacent repeated Han character: "+name);
   for(String banned:List.of("十恶不赦","忘恩负义","狼心狗肺","穷凶极恶","丧心病狂","罔谈彼短","治本于农","坐朝问道","悦豫且康","遗臭万年","奄宅曲阜","率宾归王"))check(!name.contains(banned),"banned phrase: "+name);
   check(existing.add(PlaceNameGenerator.normalize(name)),"duplicate: "+name);g.markUsed(name);total++;
  }
  PlaceNameGenerator tier=new PlaceNameGenerator(20261104);Set<String> tierUsed=new HashSet<>();
  for(String level:List.of("IMPROVEMENT","LUXURY"))for(String n:tier.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","锦江路",level,20,tierUsed)){tierUsed.add(PlaceNameGenerator.normalize(n));tier.markUsed(n);}
  var first=tier.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","锦江路","FIRST_HOME",20,tierUsed);
  check(first.stream().noneMatch(n->n.contains("序")||n.contains("著")||n.contains("玺台")||n.contains("嘉佰道")),"first-home tier drifted into improvement/luxury terminology");
  System.out.println("PASS: "+total+" randomized names; banned prose, repeated chars, length, duplicates and tier separation");
 }
}
