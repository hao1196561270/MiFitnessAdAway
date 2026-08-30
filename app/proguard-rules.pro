# ---- libxposed 模块必需规则（官方 README）----

# 模块入口类（java_init.list 按全名引用，必须保留类名与无参构造）
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# 注解类仅编译期使用
-dontwarn io.github.libxposed.annotation.**

# 混淆后重写 META-INF/xposed/java_init.list 中的入口类名
-adaptresourcefilecontents META-INF/xposed/java_init.list

# ---- libxposed service（RemotePreferences 设置页）----
-keep class io.github.libxposed.service.** { *; }
-keep class io.github.libxposed.api.** { *; }

# ---- 模块自身（manifest 引用类由 AGP 自动 keep，这里兜底）----
-keep class io.github.hao1196561270.mifitnessadaway.MiFitnessApp { *; }
-keep class io.github.hao1196561270.mifitnessadaway.SettingsActivity { *; }