package com.retile;
import com.retile.brush.*;
import com.retile.history.UndoHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Dependency-free unit runner; Gradle check executes it and fails on any assertion. */
public final class BrushTests {
    private static int passed;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void test(String name, Runnable body) { body.run(); passed++; System.out.println("PASS " + name); }
    public static void main(String[] args) {
        BrushSettings s = new BrushSettings();
        Identifier a = Identifier.parse("minecraft:quartz_block"), b = Identifier.parse("minecraft:white_wool");
        s.replacements.add(new BrushSettings.WeightedEntry(a, 1)); s.replacements.add(new BrushSettings.WeightedEntry(b, 3));
        test("chaos +1: horizontal layers", () -> {
            s.chaos=1;
            for(int y=-20;y<20;y++) { Identifier expected=BrushLogic.pick(new BlockPos(0,y,0),s,3456);
                for(int x=-10;x<10;x++) for(int z=-10;z<10;z++) check(expected.equals(BrushLogic.pick(new BlockPos(x,y,z),s,3456)),"Layer differs"); }
        });
        test("chaos -1: vertical columns", () -> {
            s.chaos=-1;
            for(int x=-10;x<10;x++) for(int z=-10;z<10;z++) { Identifier expected=BrushLogic.pick(new BlockPos(x,0,z),s,9876);
                for(int y=-20;y<20;y++) check(expected.equals(BrushLogic.pick(new BlockPos(x,y,z),s,9876)),"Column differs"); }
        });
        test("chaos 0: weighted distribution within 3%", () -> {
            s.chaos=0; int count=0, total=200_000;
            for(int i=0;i<total;i++) {
                Identifier picked=BrushLogic.pick(new BlockPos(i%200-100,i/200%100-50,i/20_000-5),s,456);
                check(a.equals(picked)||b.equals(picked),"Unexpected replacement");
                if(a.equals(picked)) count++;
            }
            double fraction=count/(double)total;
            check(Math.abs(fraction-.25)<=.25*.03,"First weight differs: " + fraction);
            check(Math.abs((1-fraction)-.75)<=.75*.03,"Second weight differs: " + (1-fraction));
        });
        test("fixed seed and intermediate chaos are deterministic", () -> {
            for(float chaos : new float[]{-1,-.65f,0,.45f,1}) { s.chaos=chaos;
                List<Identifier> first=new ArrayList<>();
                for(int i=0;i<1000;i++) first.add(BrushLogic.pick(new BlockPos(i-500,i%17,i%43),s,564738));
                for(int i=999;i>=0;i--) check(first.get(i).equals(BrushLogic.pick(new BlockPos(i-500,i%17,i%43),s,564738)),"Seed differs"); }
        });
        test("different seeds produce different results", () -> {
            for(float chaos : new float[]{-1,-.65f,0,.45f,1}) {
                s.chaos=chaos; boolean differs=false;
                for(int i=0;i<1000;i++) {
                    BlockPos position=new BlockPos(i-500,i%17-8,i%43-21);
                    if(!BrushLogic.pick(position,s,564738).equals(BrushLogic.pick(position,s,564739))) differs=true;
                }
                check(differs,"Different seeds gave identical results at chaos " + chaos);
            }
        });
        test("radius 1 sphere contains seven blocks", () -> {
            var shape=BrushShape.collectPositions(new BlockPos(-15,72,3),1,BrushShapeType.SPHERE,false,-64,320);
            check(shape.size()==7,"Sphere count"); check(new HashSet<>(shape).size()==7,"Duplicate positions");
        });
        test("cylinder and normalized absolute height range", () -> {
            var shape=BrushShape.collectPositions(new BlockPos(0,12,0),1,BrushShapeType.CYLINDER,false,0,0);
            check(shape.size()==15,"Cylinder count");
            var clipped=BrushShape.collectPositions(new BlockPos(0,12,0),3,BrushShapeType.SPHERE,true,13,11);
            check(clipped.stream().allMatch(p -> p.getY()>=11&&p.getY()<=13),"Height clipping");
        });
        test("history: oldest steps evicted and stack transfers preserve total", () -> {
            UndoHistory h=new UndoHistory(3,100); for(int i=0;i<5;i++) h.push(step(i,2));
            check(h.undoSize()==3&&h.blockCount()==6,"Step limit");
            check(h.undo().positions()[0]==4,"Undo order"); check(h.blockCount()==6&&h.redoSize()==1,"Combined total");
            check(h.redo().positions()[0]==4,"Redo order"); h.undo(); h.undo();
            check(h.undo().positions()[0]==2&&h.undo()==null,"Oldest steps retained incorrectly");
        });
        test("history: block cap, redo clearing and no-op applications", () -> {
            UndoHistory h=new UndoHistory(30,5); h.push(step(1,3)); h.push(step(2,3));
            check(h.undoSize()==1&&h.blockCount()==3,"Block cap"); h.undo(); h.beginApply();
            check(h.redoSize()==0&&h.blockCount()==0,"Redo clearing"); h.push(step(3,0)); check(h.undoSize()==0,"Empty step");
            h.push(step(4,6)); check(h.undoSize()==0&&h.blockCount()==0,"Oversize step");
        });
        test("history defaults: 30 steps and 500000 blocks", () -> {
            UndoHistory h=new UndoHistory(); for(int i=0;i<31;i++) h.push(step(i,1)); check(h.undoSize()==30,"Default step cap");
            h=new UndoHistory(); h.push(step(1,250001)); h.push(step(2,250000)); check(h.undoSize()==1&&h.blockCount()==250000,"Default block cap");
        });
        passed += BrushAlgorithmChecks.run(new BrushAlgorithmChecks.Probe() {
            public String pick(int x, int y, int z, float chaos, long seed) {
                BrushSettings settings = s.copy();
                settings.chaos = chaos;
                return BrushLogic.pick(new BlockPos(x, y, z), settings, seed).toString();
            }

            public List<BrushAlgorithmChecks.Position> shape(int x, int y, int z, int radius,
                    boolean cylinder, boolean limitHeight, int minY, int maxY) {
                return BrushShape.collectPositions(new BlockPos(x, y, z), radius,
                        cylinder ? BrushShapeType.CYLINDER : BrushShapeType.SPHERE, limitHeight, minY, maxY)
                        .stream().map(pos -> new BrushAlgorithmChecks.Position(pos.getX(), pos.getY(), pos.getZ())).toList();
            }
        });
        System.out.println(passed + " tests passed");
    }
    private static UndoHistory.Step step(long marker,int count) {
        long[] positions=new long[count]; if(count>0) positions[0]=marker;
        int[] after=new int[count]; Arrays.fill(after,1);
        return new UndoHistory.Step("minecraft:overworld",positions,new int[count],after);
    }
}
