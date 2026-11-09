package org.openstreetmap.josm.plugins.blockfill;

import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.*;

/** Dependency-free planar faces. Intersections are ONLY the supplied shared vertex IDs. */
public final class BlockGeometry {
    public static final int MAX_EDGES = 40000;
    public static final int MAX_RING_POINTS = 2500;
    public record Vertex(long id, double x, double y) { }
    public record Segment(Vertex a, Vertex b) { }
    public record Point(double x, double y) { }
    public record Face(List<Vertex> vertices, double area, Path2D.Double path) { }
    public record Candidate(List<Point> points, double area, String error) {
        public boolean valid() { return error == null; }
        public static Candidate fail(String message) { return new Candidate(List.of(), 0, message); }
    }
    private record Arc(long to, int edge, double angle) { }
    private record Edge(long a, long b) { }
    private final List<Face> faces;
    private final List<Face> holes;
    public final int edgeCount;
    public final int ignoredBridgeCount;
    public final int rejectedRingCount;

    public BlockGeometry(Collection<Segment> segments) {
        Map<Long, Vertex> vertices = new HashMap<>();
        List<Edge> edges = new ArrayList<>();
        Set<String> dedup = new HashSet<>();
        Map<Long, List<Arc>> graph = new HashMap<>();
        for (Segment s : segments) {
            Vertex a = s.a(), b = s.b();
            if (a.id() == b.id() || Math.hypot(a.x()-b.x(), a.y()-b.y()) < 1e-7) continue;
            String key = Math.min(a.id(), b.id())+":"+Math.max(a.id(), b.id());
            if (!dedup.add(key)) continue;
            if (edges.size() >= MAX_EDGES) throw new IllegalArgumentException("道路超过 40000 条线段，请缩小数据范围或使用“仅选中道路”。");
            vertices.put(a.id(), a); vertices.put(b.id(), b);
            int i = edges.size(); edges.add(new Edge(a.id(), b.id()));
            graph.computeIfAbsent(a.id(), k -> new ArrayList<>()).add(new Arc(b.id(), i, Math.atan2(b.y()-a.y(), b.x()-a.x())));
            graph.computeIfAbsent(b.id(), k -> new ArrayList<>()).add(new Arc(a.id(), i, Math.atan2(a.y()-b.y(), a.x()-b.x())));
        }
        edgeCount = edges.size();
        Set<Integer> bridges = bridges(graph);
        ignoredBridgeCount = bridges.size();
        for (List<Arc> list : graph.values()) {
            list.removeIf(a -> bridges.contains(a.edge()));
            list.sort(Comparator.comparingDouble(Arc::angle));
        }
        Set<String> visited = new HashSet<>();
        List<Face> built = new ArrayList<>();
        List<Face> inner = new ArrayList<>();
        int rejected = 0;
        for (Edge e : edges) {
            for (int direction = 0; direction < 2; direction++) {
                long first = direction == 0 ? e.a() : e.b();
                long second = direction == 0 ? e.b() : e.a();
                if (visited.contains(first+":"+second)) continue;
                List<Vertex> ring = new ArrayList<>();
                long from = first, to = second;
                boolean closed = false;
                for (int step = 0; step <= edges.size()*2; step++) {
                    List<Arc> atFrom = graph.get(from);
                    boolean exists = false;
                    for (Arc a : atFrom) if (a.to() == to) { exists = true; break; }
                    if (!exists || !visited.add(from+":"+to)) break;
                    ring.add(vertices.get(from));
                    List<Arc> atTo = graph.get(to);
                    int reverse = -1;
                    for (int j = 0; j < atTo.size(); j++) if (atTo.get(j).to() == from) { reverse = j; break; }
                    if (reverse < 0) break;
                    Arc next = atTo.get((reverse - 1 + atTo.size()) % atTo.size());
                    from = to; to = next.to();
                    if (from == first && to == second) { closed = true; break; }
                }
                if (!closed || ring.size() < 3) continue;
                double signed = signedArea(ring);
                if (ring.size() > MAX_RING_POINTS || !simple(ring)) { rejected++; continue; }
                if (signed <= -0.01) {
                    List<Vertex> reversed = new ArrayList<>(ring);
                    Collections.reverse(reversed);
                    inner.add(new Face(List.copyOf(reversed), -signed, pathVertices(reversed)));
                    continue;
                }
                if (signed <= 0.01) continue;
                built.add(new Face(List.copyOf(ring), signed, pathVertices(ring)));
            }
        }
        built.sort(Comparator.comparingDouble(Face::area));
        faces = List.copyOf(built);
        holes = List.copyOf(inner);
        rejectedRingCount = rejected;
    }
    public List<Face> faces() { return faces; }

    public Candidate at(double x, double y, double insetMeters) {
        Face chosen = null;
        for (Face f : faces) if (f.path().contains(x, y)) { chosen = f; break; }
        if (chosen == null) {
            for(Face f:faces)for(int i=0;i<f.vertices().size();i++){Vertex a=f.vertices().get(i),b=f.vertices().get((i+1)%f.vertices().size());if(java.awt.geom.Line2D.ptSegDist(a.x(),a.y(),b.x(),b.y(),x,y)<0.02)return Candidate.fail("鼠标位于道路边界上；请移入街区内部。");}
            return Candidate.fail("此处没有可用的道路闭合街区；检查断口、共享节点、图层或道路筛选。");
        }
        if(chosen!=null)for(Face other:faces)if(other!=chosen&&Math.abs(other.area()-chosen.area())<Math.max(0.01,chosen.area()*1e-9)) {
            for(int i=0;i<other.vertices().size();i++){Vertex a=other.vertices().get(i),b=other.vertices().get((i+1)%other.vertices().size());if(java.awt.geom.Line2D.ptSegDist(a.x(),a.y(),b.x(),b.y(),x,y)<0.02)return Candidate.fail("鼠标位于相邻地块共用道路边界上；请移入地块内部。");}
        }
        for (Face f : holes) {
            if (f.area() >= chosen.area()) continue;
            boolean strictlyInside = true;
            for (Vertex v : f.vertices()) if (!chosen.path().contains(v.x(), v.y())) { strictlyInside = false; break; }
            if (strictlyInside) return Candidate.fail("街区包含独立内环，第一版不生成带孔地块；请分割或调整道路筛选。");
        }
        for (Face f : faces) {
            if (f == chosen || f.area() <= chosen.area()) continue;
            if(f.path().contains(x,y))return Candidate.fail("街区内部存在独立闭合道路内环；请先检查或分割，避免误填。");
        }
        if (!Double.isFinite(insetMeters) || insetMeters < 0 || insetMeters > 100) return Candidate.fail("退界距离应在 0—100 米之间。");
        if (insetMeters == 0) {
            List<Point> p = chosen.vertices().stream().map(v -> new Point(v.x(), v.y())).toList();
            return new Candidate(p, chosen.area(), null);
        }
        Area inset = new Area(chosen.path());
        inset.subtract(new Area(new BasicStroke((float)(insetMeters*2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(chosen.path())));
        if (inset.isEmpty()) return Candidate.fail("退界距离过大，地块已消失。");
        List<List<Point>> rings = rings(inset);
        if (rings.size() != 1) return Candidate.fail("退界后出现多个分离区域或孔洞，请减小退界距离。");
        List<Point> points = rings.get(0);
        if (points.size() < 3 || points.size() > MAX_RING_POINTS) return Candidate.fail("退界后的轮廓过于复杂。");
        if (!path(points).contains(x, y)) return Candidate.fail("鼠标位于道路退界带内，请移到地块更内部。");
        return new Candidate(List.copyOf(points), Math.abs(signedPoints(points)), null);
    }
    public static Path2D.Double path(List<Point> p) {
        Path2D.Double path = new Path2D.Double();
        if (!p.isEmpty()) { path.moveTo(p.get(0).x(), p.get(0).y()); for (int i=1;i<p.size();i++) path.lineTo(p.get(i).x(),p.get(i).y()); path.closePath(); }
        return path;
    }
    private static Path2D.Double pathVertices(List<Vertex> p) { return path(p.stream().map(v -> new Point(v.x(), v.y())).toList()); }
    private static double signedArea(List<Vertex> p) { return signedPoints(p.stream().map(v -> new Point(v.x(), v.y())).toList()); }
    private static double signedPoints(List<Point> p) {
        if (p.isEmpty()) return 0;
        // Translate origin to avoid catastrophic cancellation at projected global coordinates.
        double x0=p.get(0).x(), y0=p.get(0).y(), a=0;
        for (int i=0;i<p.size();i++) { Point q=p.get(i), r=p.get((i+1)%p.size()); a+=(q.x()-x0)*(r.y()-y0)-(r.x()-x0)*(q.y()-y0); }
        return a/2;
    }
    private static List<List<Point>> rings(Area area) {
        List<List<Point>> result = new ArrayList<>(); List<Point> current = null; double[] c = new double[6];
        PathIterator it = area.getPathIterator(null, 0.2);
        while (!it.isDone()) {
            int type = it.currentSegment(c);
            if (type == PathIterator.SEG_MOVETO) { current = new ArrayList<>(); current.add(new Point(c[0], c[1])); }
            else if (type == PathIterator.SEG_LINETO && current != null) { Point last=current.get(current.size()-1); if(Math.hypot(last.x()-c[0],last.y()-c[1])>1e-6)current.add(new Point(c[0],c[1])); }
            else if (type == PathIterator.SEG_CLOSE && current != null) {
                if(current.size()>1) { Point a=current.get(0),b=current.get(current.size()-1); if(Math.hypot(a.x()-b.x(),a.y()-b.y())<1e-6)current.remove(current.size()-1); }
                if(current.size()>=3 && Math.abs(signedPoints(current))>0.01)result.add(current); current=null;
            }
            it.next();
        }
        return result;
    }
    private static boolean simple(List<Vertex> p) {
        Set<Long> seen = new HashSet<>(); for(Vertex v:p) if(!seen.add(v.id()))return false;
        for(int i=0;i<p.size();i++)for(int j=i+1;j<p.size();j++) {
            if(j==i+1 || (i==0 && j==p.size()-1))continue;
            Vertex a=p.get(i),b=p.get((i+1)%p.size()),c=p.get(j),d=p.get((j+1)%p.size());
            if(java.awt.geom.Line2D.linesIntersect(a.x(),a.y(),b.x(),b.y(),c.x(),c.y(),d.x(),d.y()))return false;
        }
        return true;
    }
    /** Iterative Tarjan bridge search: long roads do not overflow the Java call stack. */
    private static Set<Integer> bridges(Map<Long,List<Arc>> graph) {
        Map<Long,Integer> discovery=new HashMap<>(), low=new HashMap<>(), cursor=new HashMap<>(), parentEdge=new HashMap<>();
        Map<Long,Long> parent=new HashMap<>(); Set<Integer> result=new HashSet<>(); int time=0;
        for(long root:graph.keySet()) {
            if(discovery.containsKey(root))continue;
            Deque<Long> stack=new ArrayDeque<>(); stack.push(root); discovery.put(root,++time); low.put(root,time);
            while(!stack.isEmpty()) {
                long u=stack.peek(); int index=cursor.getOrDefault(u,0); List<Arc> adjacent=graph.get(u);
                if(index<adjacent.size()) {
                    Arc arc=adjacent.get(index); cursor.put(u,index+1);
                    if(arc.edge()==parentEdge.getOrDefault(u,-1))continue;
                    if(!discovery.containsKey(arc.to())) { parent.put(arc.to(),u); parentEdge.put(arc.to(),arc.edge()); discovery.put(arc.to(),++time);low.put(arc.to(),time);stack.push(arc.to()); }
                    else low.put(u,Math.min(low.get(u),discovery.get(arc.to())));
                } else {
                    stack.pop(); if(parent.containsKey(u)) {long p=parent.get(u);if(low.get(u)>discovery.get(p))result.add(parentEdge.get(u));low.put(p,Math.min(low.get(p),low.get(u)));}
                }
            }
        }
        return result;
    }
}
