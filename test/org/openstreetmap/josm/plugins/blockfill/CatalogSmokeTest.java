package org.openstreetmap.josm.plugins.blockfill;
public final class CatalogSmokeTest {
    public static void main(String[] args)throws Exception {
        var entries=LanduseCatalog.load();
        if(entries.size()<20)throw new AssertionError("expected full common urban class list");
        if(entries.stream().noneMatch(e->e.code().equals("1401")&&e.osmTags().equals(java.util.Map.of("leisure","park"))))throw new AssertionError("missing park mapping");
        if(entries.stream().noneMatch(e->e.code().equals("0804")&&e.osmTags().equals(java.util.Map.of("amenity","school"))))throw new AssertionError("missing school mapping");
        if(entries.stream().noneMatch(e->e.code().equals("0806")&&e.osmTags().equals(java.util.Map.of("amenity","hospital"))))throw new AssertionError("missing hospital mapping");
        if(entries.stream().noneMatch(e->e.code().equals("0901")&&e.name().contains("商业")))throw new AssertionError("missing commercial class");
        if(entries.stream().noneMatch(e->e.code().equals("0701")&&e.osmTags().equals(java.util.Map.of("landuse","residential"))))throw new AssertionError("missing residential mapping");
        if(entries.stream().noneMatch(e->e.code().equals("0901")&&e.osmTags().equals(java.util.Map.of("landuse","commercial"))))throw new AssertionError("missing commercial mapping");
        System.out.println("PASS: UTF-8 catalog, "+entries.size()+" classes, CN-2023 codes, optional OSM tags");
    }
}
