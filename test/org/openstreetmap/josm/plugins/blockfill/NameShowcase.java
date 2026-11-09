package org.openstreetmap.josm.plugins.blockfill;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class NameShowcase {
 public static void main(String[] args)throws Exception{
  PlaceNameGenerator g=new PlaceNameGenerator(20261102);StringBuilder t=new StringBuilder("# 0.4.0 命名样例\n\n上下文：榆川市、静海区、百叶路。全部由新版打包词库生成。\n");
  for(var l:PlaceNameGenerator.ResidentialLevel.values())t.append("\n## 住宅 ").append(l).append("\n\n").append(String.join("、",g.candidates(PlaceNameGenerator.Kind.RESIDENTIAL,"榆川市","静海区","百叶路",l.name(),12,List.of()))).append("\n");
  for(var l:PlaceNameGenerator.CommercialLevel.values())t.append("\n## 商业 ").append(l).append("（开发商：景润）\n\n").append(String.join("、",g.candidates(PlaceNameGenerator.Kind.COMMERCIAL,"榆川市","静海区","百叶路",l.name(),"景润",12,List.of()))).append("\n");
  for(var l:PlaceNameGenerator.BusinessLevel.values())t.append("\n## 商务 ").append(l).append("（开发商：景润）\n\n").append(String.join("、",g.candidates(PlaceNameGenerator.Kind.BUSINESS,"榆川市","静海区","百叶路",l.name(),"景润",12,List.of()))).append("\n");
  Files.writeString(Path.of("docs/NAME-EXAMPLES.md"),t,StandardCharsets.UTF_8);
 }
}
