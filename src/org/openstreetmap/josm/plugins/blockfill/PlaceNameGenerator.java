package org.openstreetmap.josm.plugins.blockfill;

import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Offline fictional names. Previewing never reserves names; only successful creation does. */
public final class PlaceNameGenerator {
    public enum Kind { RESIDENTIAL, COMMERCIAL, BUSINESS, OTHER }
    public enum ResidentialLevel { URBAN_VILLAGE, OLD_HOUSING, FIRST_HOME, IMPROVEMENT, LUXURY }
    public enum CommercialLevel { COMMUNITY, MIDRANGE, PREMIUM, LUXURY }
    public enum BusinessLevel { OFFICE, BUSINESS_CENTER, PREMIUM_OFFICE, HQ_CAMPUS }
    public enum DeveloperScope { IGNORE, RESIDENTIAL, COMMERCIAL, ALL }
    private final Random random;
    private final Set<String> issued=new HashSet<>();
    private final Map<String,List<String>> vocabulary=new LinkedHashMap<>();
    private final Set<String> referenceNames=new HashSet<>();
    private static final List<String> REJECT_FRAGMENTS=List.of("十恶不赦","忘恩负义","狼心狗肺","穷凶极恶","丧心病狂","杀人放火","作奸犯科","为非作歹","祸国殃民","遗臭万年","罔谈彼短","靡恃己长","墨悲丝染","祸因恶积","愚蒙等诮","孤陋寡闻","临深履薄","沉默寂寥","遐迩一体","率宾归王","治本于农","务兹稼穑","奄宅曲阜","践土会盟","坐朝问道","爱育黎首","臣伏戎羌","索居闲处","散虑逍遥","戚谢欢招","具膳餐饭","饱饫烹宰","矫手顿足","悦豫且康");
    public PlaceNameGenerator(){this(new Random());}
    public PlaceNameGenerator(long seed){this(new Random(seed));}
    public PlaceNameGenerator(Random random){
        this.random=Objects.requireNonNull(random);
        try(InputStream in=getClass().getResourceAsStream("/name-vocabulary.tsv")){
            if(in==null)throw new IOException("缺少 name-vocabulary.tsv");
            try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;int number=0;while((line=r.readLine())!=null){number++;if(line.isBlank()||line.startsWith("#"))continue;String[] parts=line.split("\t",2);if(parts.length!=2||parts[0].isBlank())throw new IOException("无效命名词库第"+number+"行");List<String> values=List.of(parts[1].split("\\|",-1));if(values.isEmpty()||values.stream().anyMatch(v->v.isBlank()||v.length()>10||v.matches(".*[，。；：？！、].*")))throw new IOException("第"+number+"行含空词、过长句段或标点句子");if(vocabulary.putIfAbsent(parts[0],values)!=null)throw new IOException("重复命名词池："+parts[0]);}}
            Set<String> required=Set.of("nature","oldCore","oldSuffix","unit","village","firstName","firstSuffix","brand","improveProject","elegantCore","elegantHousingSuffix","developerResidential","luxuryBrand","luxuryProject","luxuryResidenceSuffix","developerLuxury","communityCore","communitySuffix","midSuffix","developerMall","premiumCore","premiumSuffix","luxuryMallCore","luxuryMallSuffix","businessCenterSuffix","hqCore","hqSuffix","officeSuffix","premiumOfficeCore","premiumOfficeSuffix","districtMallSuffix");
            if(!vocabulary.keySet().containsAll(required)){Set<String> missing=new HashSet<>(required);missing.removeAll(vocabulary.keySet());throw new IOException("缺少命名词池："+missing);}
        }catch(IOException ex){throw new IllegalStateException("命名词库加载失败",ex);}
        try(InputStream in=getClass().getResourceAsStream("/name-reference-blocklist.txt")){
            if(in!=null)try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)if(!line.isBlank()&&!line.startsWith("#"))referenceNames.add(normalize(line));}
        }catch(IOException ex){throw new IllegalStateException("命名排重表加载失败",ex);}
    }
    public synchronized void markUsed(String name){if(name!=null&&!name.isBlank())issued.add(normalize(name));}
    public synchronized List<String> candidates(Kind kind,String city,String district,String road,String level,int count,Collection<String> existing){return candidates(kind,city,district,road,level,"",count,existing);}
    public synchronized List<String> candidates(Kind kind,String city,String district,String road,String level,String developer,int count,Collection<String> existing){
        String c=clean(city),d=clean(district),r=clean(road),brand=normalizeDeveloper(developer);
        if(c.isBlank()||d.isBlank())throw new IllegalArgumentException("请填写架空城市和区县名称。");
        if(kind==Kind.OTHER)throw new IllegalArgumentException("仅支持住宅、零售商业和商务办公地块命名。");
        int wanted=Math.max(1,Math.min(20,count));Set<String> used=new HashSet<>(issued);
        if(existing!=null)for(String name:existing)if(name!=null)used.add(normalize(name));
        List<String> result=new ArrayList<>();
        for(int attempts=0;result.size()<wanted&&attempts<wanted*400;attempts++){
            String name=make(kind,placeStem(c),placeStem(d),r,level,brand);String key=normalize(name);
            if(name.matches(".*([\\p{IsHan}])\\1.*")||REJECT_FRAGMENTS.stream().anyMatch(name::contains)||name.matches(".*(第一|第二|第三|第四|第五|第六|第七|第八|第九|第十)[一二三四五六七八九十百千]+.*"))continue;
            if(kind==Kind.RESIDENTIAL&&"FIRST_HOME".equals(level)&&(name.contains("序")||name.contains("著")||name.contains("玺台")||name.contains("嘉佰道")))continue;
            if(!referenceNames.contains(key)&&used.add(key))result.add(name);
        }
        if(result.size()<wanted)throw new IllegalStateException("当前条件下候选不足，请更换道路或等级。");
        return List.copyOf(result);
    }
    public synchronized String next(Kind kind,String city,String district,String road,String level,Collection<String> existing){return next(kind,city,district,road,level,"",existing);}
    public synchronized String next(Kind kind,String city,String district,String road,String level,String developer,Collection<String> existing){return candidates(kind,city,district,road,level,developer,1,existing).get(0);}
    private String make(Kind kind,String c,String d,String r,String level,String developer){
        String place=random.nextInt(5)==0?c:d;
        String natural=word("nature");
        if(kind==Kind.RESIDENTIAL){
            return switch(ResidentialLevel.valueOf(level)){
                case URBAN_VILLAGE -> village(r,d);
                case OLD_HOUSING -> oldHousing(r,d);
                case FIRST_HOME -> !developer.isBlank()?developer+"·"+word("firstName"):switch(random.nextInt(4)){
                    case 0 -> place+word("firstSuffix");
                    case 1 -> (r.isBlank()?place:roadStem(r))+word("firstSuffix");
                    default -> word("firstName");
                };
                case IMPROVEMENT -> !developer.isBlank()?developer+"·"+word("improveProject"):switch(random.nextInt(6)){
                    case 0,1,2,3 -> word("improveProject");
                    case 4 -> place+word("elegantHousingSuffix");
                    default -> (r.isBlank()?place:roadStem(r))+word("elegantHousingSuffix");
                };
                case LUXURY -> !developer.isBlank()?developer+"·"+word("developerLuxury"):switch(random.nextInt(6)){
                    case 0 -> word("luxuryBrand")+"·"+word("luxuryProject");
                    case 1,2,3 -> word("luxuryProject");
                    case 4 -> place+word("luxuryResidenceSuffix");
                    default -> (r.isBlank()?place:roadStem(r))+word("luxuryResidenceSuffix");
                };
            };
        }
        if(kind==Kind.BUSINESS){return switch(BusinessLevel.valueOf(level)){
            case OFFICE -> developer.isBlank()?place+word("officeSuffix"):developer+"·"+word("officeSuffix");
            case BUSINESS_CENTER -> developer.isBlank()?place+word("businessCenterSuffix"):developer+"·"+d+word("businessCenterSuffix");
            case PREMIUM_OFFICE -> developer.isBlank()?word("premiumOfficeCore")+word("premiumOfficeSuffix"):developer+"·"+word("premiumOfficeCore");
            case HQ_CAMPUS -> developer.isBlank()?word("hqCore")+word("hqSuffix"):developer+"·"+d+word("hqSuffix");
        };}
        return switch(CommercialLevel.valueOf(level)){
            case COMMUNITY -> switch(random.nextInt(5)){
                case 0 -> (r.isBlank()?d:r)+word("communitySuffix");
                case 1 -> word("communityCore")+word("communitySuffix");
                case 2 -> d+"·"+word("communityCore");
                case 3 -> natural+"商街";
                default -> (r.isBlank()?d:roadStem(r))+"·"+word("communityCore");
            };
            case MIDRANGE -> !developer.isBlank()?switch(random.nextInt(5)){case 0->developer+"·"+word("midSuffix");case 1->developer+d+word("districtMallSuffix");case 2->developer+"·"+d+word("districtMallSuffix");case 3->developer+"·"+word("developerMall");default->developer+d+word("communitySuffix");}:switch(random.nextInt(5)){
                case 0 -> place+word("midSuffix");
                case 1 -> word("brand")+word("midSuffix");
                case 2 -> word("brand")+"·"+place+word("midSuffix");
                case 3 -> natural+word("midSuffix");
                default -> (r.isBlank()?natural:roadStem(r))+word("midSuffix");
            };
            case PREMIUM -> !developer.isBlank()?developer+"·"+word("developerMall"):switch(random.nextInt(5)){
                case 0 -> word("brand")+"·"+place+word("premiumSuffix");
                case 1 -> word("premiumCore")+word("premiumSuffix");
                case 2 -> place+word("premiumCore");
                case 3 -> word("brand")+word("premiumSuffix");
                default -> word("premiumCore")+"·"+d;
            };
            case LUXURY -> !developer.isBlank()?developer+d+word("districtMallSuffix"):switch(random.nextInt(5)){
                case 0 -> word("luxuryMallCore")+word("luxuryMallSuffix");
                case 1 -> place+word("luxuryMallCore");
                case 2 -> word("luxuryBrand")+"·"+word("luxuryMallCore");
                case 3 -> word("luxuryMallCore")+"·"+place;
                default -> word("luxuryBrand")+word("luxuryMallSuffix");
            };
        };
    }
    private String village(String road,String district){
        String stem=!road.isBlank()&&random.nextInt(3)!=0?roadStem(road):word("village");
        String village=stem+(stem.endsWith("村")?"":"村");
        return switch(random.nextInt(5)){case 0 -> village;case 1,2 -> village+(1+random.nextInt(16))+"组";case 3 -> village+pick("东片","西片","南片","北片");default -> district+"·"+village;};
    }
    private String oldHousing(String road,String district){
        return switch(random.nextInt(6)){
            case 0,1 -> (road.isBlank()?word("oldCore")+"路":road)+(2+random.nextInt(298))+"号院";
            case 2 -> word("oldCore")+word("oldSuffix");
            case 3 -> word("unit")+pick("宿舍","家属院","职工新村","生活区");
            case 4 -> (road.isBlank()?district:roadStem(road))+pick("东里","西里","南里","北里","新村");
            default -> word("oldCore")+pick("一村","二村","三村","四村","里弄");
        };
    }
    private String word(String pool){List<String> values=vocabulary.get(pool);if(values==null||values.isEmpty())throw new IllegalStateException("词库缺少 "+pool);return values.get(random.nextInt(values.size()));}
    private String pick(String... values){return values[random.nextInt(values.length)];}
    static String normalize(String value){return value.replaceAll("[\\s·.。]","");}
    private static String normalizeDeveloper(String value){String result=clean(value);result=result.replaceFirst("(房地产开发有限公司|房地产开发集团|房地产开发|开发有限公司|开发集团|控股集团|置业集团|开发商|地产集团|置业公司|置业有限公司|地产有限公司|集团公司|有限公司|地产|置业|集团)$","").trim();return result.isBlank()?clean(value):result;}
    private static String roadStem(String road){String s=road.replaceFirst("(大道|大街|公路|路|街|巷|道)$","");return s.isBlank()?road:s;}
    private static String placeStem(String place){String s=place.replaceFirst("(特别行政区|自治县|自治州|新区|市|区|县)$","");return s.isBlank()?place:s;}
    private static String clean(String value){return value==null?"":value.trim().replaceAll("[.。·,，;；/\\\\]","·").replace("中国","华夏").replace("中粮","华粮").replace("中车","华车").replace("中建","华建").replace("中铁","华铁");}
}
