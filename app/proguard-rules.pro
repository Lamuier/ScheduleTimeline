# Room 实体：字段名即列映射依据，保守整体保留。
# 其余 data 包类（TimelineBuilder / ScheduleExport / ScheduleRepository 等）
# 交给 R8 混淆优化；Room runtime 自带 consumer 规则已保留 *_Impl 反射查找。
-keep class com.lamuier.scheduletimeline.data.ScheduleEvent { *; }
-keep class com.lamuier.scheduletimeline.data.Category { *; }
