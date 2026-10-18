#!/usr/bin/env python3
"""Host-JVM layout benchmark; run after compileDebugKotlin. Not a device frame test."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

SOURCE = r'''import com.lamuier.scheduletimeline.data.*;
import java.util.*;
import java.lang.management.ManagementFactory;
public class TimelineBenchmark {
 static volatile Object sink;
 static List<TimelineItem.Event> fixture(int n) {
  var rows = new ArrayList<TimelineItem.Event>();
  for (int i=0;i<n;i++) {
   var e = new ScheduleEvent(i+1,"Event"+i,"","",480+i,900+i,"","2026-10-15","Team"+i,
    (i%2==0?EventType.PERFORMANCE:EventType.TOKUTEN).getStorage(),"",null,false);
   rows.add(new TimelineItem.Event(e,null,List.of()));
  }
  return rows;
 }
 public static void main(String[] args) {
  var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
  boolean allocation = bean.isThreadAllocatedMemorySupported();
  if(allocation && !bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
  long thread = Thread.currentThread().getId();
  var metrics = new TimelineTextMetrics();
  System.out.print("[");
  boolean comma=false;
  for (int n : new int[]{10,50,100,200,300}) {
   var group = new TimelineItem.OverlapGroup(fixture(n));
   for(int w=0;w<5;w++) sink=TimelineLayoutPlan.Companion.build(group,metrics);
   long[] times=new long[9], bytes=new long[9]; int segments=0;
   for(int r=0;r<9;r++) {
    long allocated=allocation?bean.getThreadAllocatedBytes(thread):0;
    long start=System.nanoTime();
    var plan=TimelineLayoutPlan.Companion.build(group,metrics);
    times[r]=System.nanoTime()-start;
    bytes[r]=allocation?bean.getThreadAllocatedBytes(thread)-allocated:-1;
    sink=plan; segments=plan.getSegments().size();
   }
   Arrays.sort(times); Arrays.sort(bytes);
   if(comma)System.out.print(","); comma=true;
   System.out.printf(Locale.ROOT,"{\"events\":%d,\"segments\":%d,\"median_ms\":%.6f,\"median_allocated_bytes\":%d}",n,segments,times[4]/1e6,bytes[4]);
  }
  System.out.println("]");
 }
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--classes", type=Path, default=Path("app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"))
    parser.add_argument("--kotlin-stdlib", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    java = str(Path(args.java_home) / "bin" / "java") if args.java_home else shutil.which("java")
    jars = sorted((Path.home()/".gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.3.10").glob("*/*.jar"))
    jar = args.kotlin_stdlib or (jars[0] if jars else None)
    if not java or not jar or not args.classes.is_dir():
        parser.error("JDK 17+, compiled Debug classes and Kotlin stdlib are required")
    with tempfile.TemporaryDirectory(prefix="timeline-benchmark-") as temp:
        source = Path(temp) / "TimelineBenchmark.java"
        source.write_text(SOURCE, encoding="utf-8")
        result = subprocess.run([java,"-Xmx768m","--class-path",os.pathsep.join([str(args.classes.resolve()),str(jar.resolve())]),str(source)], check=True, text=True, capture_output=True)
    report = {"kind":"host-jvm-layout", "fixture":"start=480+i,end=900+i,alternating-types,empty-overlapNotes", "warmup":5,"samples":9,"results":json.loads(result.stdout)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
