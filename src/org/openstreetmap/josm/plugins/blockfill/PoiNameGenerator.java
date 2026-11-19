package org.openstreetmap.josm.plugins.blockfill;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * POI 名称生成器 — 场景特化版
 * <p>
 * 读取 poi-name-parts.tsv，按场景前缀过滤，用 (seed XOR 坐标哈希) 随机拼接词根和后缀。
 * 同一坐标重复生成时名字不变，不同坐标各异。
 * <p>
 * 场景前缀：oldtown_ / village_ / street_ / mall_
 */
public class PoiNameGenerator {

    public enum Scene {
        OLDTOWN("oldtown_", "老城区"),
        VILLAGE("village_", "城中村"),
        STREET("street_", "街边商业"),
        MALL("mall_", "综合体");

        final String prefix;
        final String displayName;

        Scene(String prefix, String displayName) {
            this.prefix = prefix;
            this.displayName = displayName;
        }
    }

    /** POI 类型定义（type 去掉场景前缀后的基础类型）*/
    public static class PoiEntry {
        final String type;           // 如 "restaurant"
        final String osmTags;        // "amenity=restaurant;cuisine=chinese"
        final String[] rootWords;    // 词根数组
        final String[] suffixes;     // 后缀数组

        PoiEntry(String type, String osmTags, String rootWords, String suffixes) {
            this.type = type;
            this.osmTags = osmTags;
            this.rootWords = rootWords.split("\\|");
            this.suffixes = suffixes.split("\\|");
        }

        /** 生成名字：用 seed XOR 坐标哈希选词根和后缀 */
        String generateName(long seed, double lat, double lon) {
            long hash = seed ^ coordHash(lat, lon);
            Random rng = new Random(hash);
            String root = rootWords[rng.nextInt(rootWords.length)];
            String suffix = suffixes[rng.nextInt(suffixes.length)];
            return root + suffix;
        }

        /** 坐标哈希：微小偏移也产生不同哈希，同一位置稳定 */
        private static long coordHash(double lat, double lon) {
            long latBits = Double.doubleToLongBits(lat);
            long lonBits = Double.doubleToLongBits(lon);
            return latBits * 31L + lonBits;
        }

        /** 返回 OSM 标签键值对列表 */
        Map<String, String> parseTags() {
            Map<String, String> tags = new LinkedHashMap<>();
            for (String pair : osmTags.split(";")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) tags.put(kv[0].trim(), kv[1].trim());
            }
            return tags;
        }
    }

    private final Map<String, List<PoiEntry>> entriesByType = new LinkedHashMap<>();
    private final long seed;

    /**
     * 构造器
     * @param scenes 启用的场景集合（可多选），决定加载哪些行
     * @param seed 全局随机种子
     */
    public PoiNameGenerator(Set<Scene> scenes, long seed) {
        this.seed = seed;
        loadEntries(scenes);
    }

    /** 从 resources/poi-name-vocabulary.tsv 加载词库 */
    private void loadEntries(Set<Scene> scenes) {
        try (InputStream is = getClass().getResourceAsStream("/poi-name-vocabulary.tsv");
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\t");
                if (parts.length != 4) continue;

                String fullType = parts[0].trim();
                String osmTags = parts[1].trim();
                String rootWords = parts[2].trim();
                String suffixes = parts[3].trim();

                // 场景过滤：只加载匹配前缀的行
                boolean match = false;
                String baseType = fullType;
                for (Scene scene : scenes) {
                    if (fullType.startsWith(scene.prefix)) {
                        match = true;
                        baseType = fullType.substring(scene.prefix.length());
                        break;
                    }
                }
                if (!match) continue;

                PoiEntry entry = new PoiEntry(baseType, osmTags, rootWords, suffixes);
                entriesByType.computeIfAbsent(baseType, k -> new ArrayList<>()).add(entry);
            }
        } catch (IOException | NullPointerException e) {
            // 资源文件缺失或解析失败，entriesByType 保持空
        }
    }

    /** 获取所有可用的 POI 基础类型 */
    public Set<String> availableTypes() {
        return entriesByType.keySet();
    }

    /**
     * 生成 POI 节点
     * @param type POI 基础类型（去掉场景前缀后的名称，如 "restaurant"）
     * @param lat 纬度
     * @param lon 经度
     * @return 包含 name 和 OSM 标签的 Map，如果类型不存在返回空
     */
    public Map<String, String> generate(String type, double lat, double lon) {
        List<PoiEntry> candidates = entriesByType.get(type);
        if (candidates == null || candidates.isEmpty()) return Map.of();

        // 如果该类型在多个场景下都有词条，随机选一个（用坐标哈希保证同位置稳定）
        long hash = seed ^ PoiEntry.coordHash(lat, lon);
        Random rng = new Random(hash);
        PoiEntry entry = candidates.get(rng.nextInt(candidates.size()));

        Map<String, String> result = new LinkedHashMap<>(entry.parseTags());
        result.put("name", entry.generateName(seed, lat, lon));
        return result;
    }

    /**
     * 批量生成 POI 节点
     * @param type POI 基础类型
     * @param coords 坐标列表（[lat, lon]）
     * @return 每个坐标对应的标签 Map 列表
     */
    public List<Map<String, String>> generateBatch(String type, List<double[]> coords) {
        List<Map<String, String>> results = new ArrayList<>();
        for (double[] coord : coords) {
            results.add(generate(type, coord[0], coord[1]));
        }
        return results;
    }
}
