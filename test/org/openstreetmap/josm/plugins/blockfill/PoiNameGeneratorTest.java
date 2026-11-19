package org.openstreetmap.josm.plugins.blockfill;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for PoiNameGenerator without JUnit dependency.
 * Run directly via main method with assert enabled.
 */
public class PoiNameGeneratorTest {
    public static void main(String[] args) {
        testLoadsEntriesFromTsvAndFiltersScene();
        testGeneratesSameNameForSameCoordinate();
        testGeneratesDifferentNamesForDifferentCoordinates();
        testMultipleScenesCombineEntries();
        testBatchGenerationProducesDifferentNames();
        testReturnsEmptyForUnknownType();
        testParsesMultipleTagsCorrectly();

        System.out.println("All POI name generator tests passed.");
    }

    static void testLoadsEntriesFromTsvAndFiltersScene() {
        // 只加载老城区场景
        PoiNameGenerator gen = new PoiNameGenerator(Set.of(PoiNameGenerator.Scene.OLDTOWN), 12345L);
        Set<String> types = gen.availableTypes();

        // 应该包含 oldtown_ 开头的类型（去掉前缀后）
        assert types.contains("restaurant") : "Should load oldtown_restaurant as 'restaurant'";
        assert types.contains("shop") : "Should load oldtown_shop as 'shop'";

        // 生成名字并验证格式
        Map<String, String> poi = gen.generate("restaurant", 25.0, 110.0);
        assert !poi.isEmpty() : "Should generate POI";
        assert poi.containsKey("name") : "Should have name tag";
        assert poi.containsKey("amenity") : "Should have amenity tag";
        assert "restaurant".equals(poi.get("amenity"));

        String name = poi.get("name");
        assert !name.isEmpty() : "Name should not be empty";
        // 老城区餐馆词根：老川|川府|蜀香|渝味|乡味|家常|灶头  后缀：饭庄|酒楼|餐馆|菜馆|食府|老店|食堂
        // 名字应该是词根+后缀组合
        assert name.length() >= 4 && name.length() <= 12 : "Name length should be reasonable: " + name;
    }

    static void testGeneratesSameNameForSameCoordinate() {
        PoiNameGenerator gen = new PoiNameGenerator(Set.of(PoiNameGenerator.Scene.STREET), 99L);

        Map<String, String> poi1 = gen.generate("cafe", 30.5, 120.3);
        Map<String, String> poi2 = gen.generate("cafe", 30.5, 120.3);

        assert poi1.get("name").equals(poi2.get("name")) : "Same coordinate should produce same name";
    }

    static void testGeneratesDifferentNamesForDifferentCoordinates() {
        PoiNameGenerator gen = new PoiNameGenerator(Set.of(PoiNameGenerator.Scene.VILLAGE), 777L);

        Map<String, String> poi1 = gen.generate("pharmacy", 25.0, 110.0);
        Map<String, String> poi2 = gen.generate("pharmacy", 25.1, 110.1);

        assert !poi1.get("name").equals(poi2.get("name")) : "Different coordinates should produce different names";
    }

    static void testMultipleScenesCombineEntries() {
        // 同时加载老城区和综合体
        PoiNameGenerator gen = new PoiNameGenerator(
            Set.of(PoiNameGenerator.Scene.OLDTOWN, PoiNameGenerator.Scene.MALL), 555L);

        Set<String> types = gen.availableTypes();

        // "cinema" 只在 mall_ 下存在
        assert types.contains("cinema") : "Should load mall-only types";

        // "restaurant" 在两个场景下都存在，应该能生成名字
        Map<String, String> poi = gen.generate("restaurant", 26.0, 111.0);
        assert !poi.isEmpty() : "Should generate restaurant from combined scenes";
        assert poi.containsKey("name");
    }

    static void testBatchGenerationProducesDifferentNames() {
        PoiNameGenerator gen = new PoiNameGenerator(Set.of(PoiNameGenerator.Scene.STREET), 42L);

        List<double[]> coords = List.of(
            new double[]{25.0, 110.0},
            new double[]{25.1, 110.1},
            new double[]{25.2, 110.2}
        );

        List<Map<String, String>> pois = gen.generateBatch("barber", coords);

        assert pois.size() == 3 : "Should generate 3 POIs";

        String name1 = pois.get(0).get("name");
        String name2 = pois.get(1).get("name");
        String name3 = pois.get(2).get("name");

        assert name1 != null;
        assert name2 != null;
        assert name3 != null;

        // 三个名字应该各不相同（坐标不同）
        assert !name1.equals(name2);
        assert !name2.equals(name3);
        assert !name1.equals(name3);
    }

    static void testReturnsEmptyForUnknownType() {
        PoiNameGenerator gen = new PoiNameGenerator(Set.of(PoiNameGenerator.Scene.OLDTOWN), 1L);

        Map<String, String> poi = gen.generate("nonexistent_type", 25.0, 110.0);
        assert poi.isEmpty() : "Should return empty map for unknown type";
    }

    static void testParsesMultipleTagsCorrectly() {
        // 如果将来 tsv 里有多标签条目，确保能正确解析
        PoiNameGenerator gen = new PoiNameGenerator(Set.of(PoiNameGenerator.Scene.MALL), 9L);

        // mall_cinema 应该有 amenity=cinema
        Map<String, String> cinema = gen.generate("cinema", 30.0, 120.0);
        assert !cinema.isEmpty();
        assert "cinema".equals(cinema.get("amenity"));
        assert cinema.containsKey("name");
    }
}
