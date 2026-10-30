# PigDrop

> 中文名「猪猪传送门」。桌面显示名、应用内品牌、手机上传页标题都是 **猪猪传送门**；包名与 Release 附件名沿用 `PigDrop`，下载目录是 `Download/PigDrop/`。

电视上打开应用，屏幕上就是一个二维码。手机扫一下，浏览器里选文件，文件直接落到电视的「下载」目录。

手机不装 App，文件不过云，全程只在你家局域网里走。

<table>
<tr>
<td width="50%"><img src="screenshots/tv-noir.png" alt="夜航主题"></td>
<td width="50%"><img src="screenshots/tv-paper.png" alt="素纸主题"></td>
</tr>
<tr>
<td align="center"><sub>夜航 · 深色</sub></td>
<td align="center"><sub>素纸 · 浅色</sub></td>
</tr>
</table>

## 怎么用

1. 电视和手机连同一个 Wi-Fi。
2. 电视上打开「PigDrop」，左边就是二维码和地址。
3. 手机扫码（或手动输入 `http://电视IP:8080`），页面上点「选择文件」。
4. 传完文件出现在电视列表里，手机上也能看到「电视上已有」的清单。

电视端全程只用遥控器：`↑↓←→` 移动，`OK` 打开；把焦点移到行内的「打开 / 删除」上再按 `OK`；右上角「管理」可以批量删。

**想装 APK 的话**：第一次点「打开」会被拦一次——Android 8.0 起系统默认不允许任何应用安装软件。应用会给出中文提示和「去打开设置」按钮，在那一页把「猪猪传送门」的开关打开，返回后会自动接着装，不用重新点。

<table>
<tr>
<td width="30%"><img src="screenshots/phone-upload.png" alt="手机端上传页"></td>
<td width="35%"><img src="screenshots/tv-manage.png" alt="批量管理"></td>
<td width="35%"><img src="screenshots/tv-settings.png" alt="设置"></td>
</tr>
<tr>
<td align="center"><sub>手机端：不用装 App</sub></td>
<td align="center"><sub>批量管理</sub></td>
<td align="center"><sub>设置</sub></td>
</tr>
</table>

<table>
<tr>
<td width="50%"><img src="screenshots/tv-empty.png" alt="空状态"></td>
<td width="50%"><img src="screenshots/tv-open-failed.png" alt="打不开时的提示"></td>
</tr>
<tr>
<td align="center"><sub>还没收到文件时</sub></td>
<td align="center"><sub>电视上没有能打开它的应用时，按类型给下一步</sub></td>
</tr>
</table>

## 功能

- **手机端零安装**：上传页是电视端现场提供的 HTML，扫码即用，不依赖任何外部服务。
- **边收边写**：`PUT` 裸二进制流直接写进目标文件，不落临时文件，大文件不会占双份内存。
- **两套主题**：夜航（深色）、素纸（浅色）。手机上打开的上传页会跟着电视主题一起变。
- **遥控器全程可达**：行内左右依次是「打开 / 删除」，上下换行、末行封顶、浮层焦点不外逃，不靠几何就近猜。
- **管理已接收文件**：单选、多选、全选、删除、清空，删除前有二次确认，默认焦点在「取消」。
- **打不开时说人话**：电视上没有对应应用时，按文件类型给下一步建议，而不是弹一句英文 toast 或者干脆没反应。
- **Android 6.0 起**（API 23），同时出 `armeabi-v7a` 和 `arm64-v8a` 两个包。

## 下载安装

到 [Releases](https://github.com/andyzhang1214/Pig-Drop/releases) 下对应架构的 APK：

| 机型 | 文件 |
|---|---|
| 近几年的电视 / 盒子（64 位） | `PigDrop-1.0.0-arm64-v8a.apk` |
| 老电视、老盒子（32 位） | `PigDrop-1.0.0-armeabi-v7a.apk` |

分不清就装 `arm64-v8a`，装不上再试另一个。

装到电视：`adb install PigDrop-1.0.0-arm64-v8a.apk`，或者拷到 U 盘用电视自带的文件管理器打开。包已用正式密钥签名，不是 debug 包。

## 自己构建

需要 JDK 17+ 和 Android SDK（Platform 36）。

```bash
cd android
./gradlew :app:assembleRelease      # 正式包
./gradlew :app:assembleDebug        # 调试包
```

产物：

```
android/app/build/outputs/apk/release/app-arm64-v8a-release.apk
android/app/build/outputs/apk/release/app-armeabi-v7a-release.apk
android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk        # 调试用
android/app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
```

签名信息读工程根目录的 `keystore.properties`（已在 `.gitignore` 里，仓库不含密钥）。文件不存在时 release 自动回落到 debug 签名，克隆下来照样能构建——只是签名不同，不能覆盖安装官方包。

跑单元测试：

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

## 目录

```
android/                 Android TV 应用
  app/src/main/java/com/zhuzhu/sudi/
    core/                纯 Kotlin：体积/时间文案、类型识别、文件名净化、索引编解码、流搬运
    net/                 内嵌 HTTP 服务、局域网 IP、二维码、手机页注入
    store/               存储三档降级、收到记录仓库
    ui/                  调色板、绘制、列表与行、浮层
  app/src/main/assets/www/index.html   手机端上传页（占位符由电视端注入）
  app/src/test/          29 条单元测试
design/
  prototype.html         单文件 HTML 原型（四套主题的设计定稿）
  DESIGN.md              设计上下文与取舍记录
screenshots/
```

## 已知限制

- 只能同局域网，跨网段/VLAN 不通。
- 没有断点续传，传一半断开要重传。
- 只在 API 34 的 Android TV 模拟器上验证过完整流程（含 30 路并发压测）；API 23–28 那两档存储降级路径没有真机跑过。
- 仓库里的截图来自一台 1080p 的 API 34 模拟器，真实电视上的字体渲染可能有细微差异。
- 手机端上传页不超过 6 条「电视上已有」的预览，只是清单，不能管理文件。

## 设计记录

配色、排版、遥控器焦点模型、文案口径都记在 [`design/DESIGN.md`](design/DESIGN.md)。改界面之前建议先读它，`design/prototype.html` 是当时定稿的可交互原型。

## 免责声明

这是个**个人写着玩的项目**，不是产品，没有团队维护，也不盈利。

- 作者是业余时间写着玩的，不承诺能用、不承诺好用、不承诺以后还继续管。
- 代码按「现状」提供，**不附带任何明示或暗示的担保**，包括但不限于适销性、特定用途适用性和不侵权。
- 用它在电视上装东西、删文件、占空间，**出了任何问题作者都不承担责任**——文件丢了、设备变砖、保修失效、被家人骂，都算你自己的。
- 你下载、安装、运行或以任何方式使用本项目，即视为**已阅读并同意**以上全部内容。不同意就不要下载。
- 第三方依赖（NanoHTTPD、ZXing、androidx 等）各自遵循其原始开源许可证。

## 鸣谢

- **[DeepSeek](https://www.deepseek.com/)** —— 项目的代码、文档和设计记录主要由 DeepSeek 模型撰写
- **[WorkBuddy](https://www.workbuddy.cn/)** —— 在它的环境里完成构建、模拟器验证和逐屏截图

