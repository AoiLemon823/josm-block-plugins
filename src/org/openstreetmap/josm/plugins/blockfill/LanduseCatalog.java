package org.openstreetmap.josm.plugins.blockfill;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.awt.Color;
import java.util.*;

/** User-editable UTF-8 TSV, optionally overridden by -Dblockfill.catalog=absolutePath. */
public final class LanduseCatalog {
    public record Entry(String name, String code, Color color, Map<String,String> osmTags) {
        @Override public String toString() { return name + "  [" + code + "]"; }
    }
    public static List<Entry> load() throws IOException {
        String override=System.getProperty("blockfill.catalog");
        InputStream stream=override==null?LanduseCatalog.class.getResourceAsStream("/landuse.tsv"):new FileInputStream(override);
        if(stream==null)throw new IOException("缺少 landuse.tsv");
        List<Entry> entries=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            String line; int number=0;
            while((line=reader.readLine())!=null) {
                number++; if(line.isBlank()||line.startsWith("#"))continue;
                String[] fields=line.split("\t",-1);
                if(fields.length!=4)throw new IOException("分类表第 "+number+" 行应为四列");
                Map<String,String> tags=new LinkedHashMap<>();
                if(!fields[3].isBlank())for(String item:fields[3].split(";")) {
                    String[] kv=item.split("=",2); if(kv.length!=2||kv[0].isBlank()||kv[1].isBlank())throw new IOException("无效标签："+item);
                    tags.put(kv[0],kv[1]);
                }
                entries.add(new Entry(fields[0],fields[1],Color.decode(fields[2]),Collections.unmodifiableMap(tags)));
            }
        }
        if(entries.isEmpty())throw new IOException("分类表不能为空");
        return List.copyOf(entries);
    }
}
