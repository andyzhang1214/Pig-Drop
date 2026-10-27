# NanoHTTPD 在部分分支里按名字反射构造 TLS 工厂类，混淆后会找不到
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# ZXing 的编码器按类名做扩展查找
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# 清单里登记的组件（Activity / Application）由 AGP 自动保留，这里只做显式声明，
# 避免以后有人把 keep 规则当成可选项删掉
-keep class com.zhuzhu.sudi.MainActivity { *; }
-keep class com.zhuzhu.sudi.App { *; }
