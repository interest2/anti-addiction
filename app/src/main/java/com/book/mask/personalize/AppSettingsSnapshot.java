package com.book.mask.personalize;

/**
 * 某个 APP 的「每-APP 用户配置」快照，用于删除自定义 APP 后可撤销恢复。
 * <p>
 * 各字段为 null 表示删除前该项从未被单独设置过（走全局默认），恢复时对其不作处理，
 * 避免把默认值固化成显式的每-APP 键。仅覆盖用户配置项，不含休闲计数、关闭记录等运行态数据。
 */
public class AppSettingsSnapshot {
    public Integer floatingTopOffset;
    public Integer floatingBottomOffset;
    public String hintSource;
    public String hintCustom;
    public Boolean monitoringEnabled;
}
