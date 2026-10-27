# 墨词 Moci

一款面向 Android 平板的中英双向默写应用。它可以直接读取「不背单词」导出的双栏「单词 / 释义」PDF 词表，自动按 20 词分组；支持手写笔与键盘默写、自动批改、错题集和本地进度保存。

[![Release](https://img.shields.io/github/v/release/chenbiwei520-alt/Moci?label=release)](https://github.com/chenbiwei520-alt/Moci/releases/latest)
[![License](https://img.shields.io/github/license/chenbiwei520-alt/Moci)](LICENSE)
![Platform](https://img.shields.io/badge/Android-12%2B-3ddc84)

> 本项目与「不背单词」官方无关，仅解析用户自行导出的词表 PDF；词表内容版权归原权利人所有。

## 功能

- **PDF 词表导入**：读取双栏「单词 / 释义」词表，自动过滤页眉页脚、页码、书名、公众号等版式噪声，并校验词条编号是否连续。
- **双向默写**：中默英（看释义写单词）与英默中（看单词写释义），两个方向各自独立记录进度与得分。
- **20 词一组**：导入后自动分组，可按组练习，也可直接查看历史结果并重新默写。
- **手写默写**：接入 Google ML Kit Digital Ink，在端侧识别手写内容；停笔后自动识别，提供橡皮擦、撤销与清空。默写页默认打开手写模式。
- **键盘输入**：支持中英文输入法，可与手写混用。
- **自动批改**：对大小写、空格、词形变化、冠词、轻微拼写误差做容错判断，结果分为正确、半对、错误、未作答，可在结果页逐词改判。
- **错题集**：所有半对与错误的词条自动归入错题集，以多列瀑布流卡片展示，支持只练错题。
- **进度本地保存**：词书、成绩、错题全部保存在设备本地；重新导入同名词书时按最新记录合并，不会清空已有进度。
- **JSON 备份**：词书列表底部可导出/导入备份，用于换机、重装，以及从 Debug 版迁移到 Release 版。
- **平板与 HyperOS 风格**：基于 Compose 与 [Miuix](https://github.com/compose-miuix-ui/miuix) 构建，界面随系统深浅色切换。

## 下载

前往 [Releases](https://github.com/chenbiwei520-alt/Moci/releases/latest) 下载最新的 `Moci-1.0.1-arm64.apk`（1.0.0 存在启动崩溃问题，请勿使用）。

- 系统要求：Android 12（API 31）及以上，**仅 arm64-v8a**。
- 手写识别依赖 Google ML Kit：首次使用需联网下载约 20 MB 的识别模型，并要求设备带有 Google Play 服务。无 GMS 的设备仍可使用键盘默写。
- 安装时需允许「安装未知来源应用」。

## 使用

1. 在「不背单词」中导出词表 PDF（双栏「单词 / 释义」版式，详见下节）。
2. 打开墨词，点击首页右上角「+」或空状态中的「导入词书」，选择 PDF。
3. 进入词书后选择「中默英」或「英默中」，点任意分组开始默写。
4. 手写或键盘作答，交卷后查看批改结果；半对与错误会自动进入错题集。
5. 换设备或重装前，在词书列表底部「导出备份」；新设备安装后点「从备份导入」。

## 支持的词表格式

当前解析器适配「不背单词」导出的双栏词表 PDF：

- 每页为「单词 | 释义」双栏排版，词条带连续编号；
- PDF 文本可选中（扫描版 / 纯图片版暂不支持）；
- 自动识别并过滤 `Word Meaning` 表头、页码、`共 N 词` 页脚、书名与公众号等重复噪声行；
- 解析失败时会提示具体页码与原因，例如「第 3 页词条编号不连续」；
- 其他版式的 PDF 可能需要调整 `VocabTextParser` 的解析规则。

## 数据与隐私

- 所有数据仅保存在应用私有目录，不上传任何服务器。
- 应用无账号体系、无统计 SDK、无广告。
- `INTERNET` 与 `ACCESS_NETWORK_STATE` 权限仅用于 ML Kit 下载手写识别模型。
- 备份文件由用户通过系统文件选择器自行选择保存位置。

## 从 Debug 版迁移进度

Debug 版与 Release 版签名不同，无法直接覆盖安装。迁移步骤：

1. 安装与旧 Debug 包签名一致的 Debug 包覆盖安装（数据保留）。
2. 打开应用，在词书列表底部「导出备份」。
3. 卸载 Debug 版。
4. 安装 Release 版，在词书列表底部或空状态页「从备份导入」。

备份按分组最新记录合并，重复导入同一份备份是幂等的。

## 构建

环境：JDK 17、Android SDK 37（compileSdk 37 / targetSdk 36 / minSdk 31）。

```bash
# Debug
./gradlew :app:assembleDebug

# 单元测试（31 个用例）
./gradlew :app:testDebugUnitTest

# Release（需要签名配置，见下）
./gradlew :app:assembleRelease
```

Release 签名通过项目根目录的 `keystore.properties` 读取，该文件与 `keystore/` 目录已被 `.gitignore` 排除，请勿提交：

```properties
storeFile=keystore/your-release.jks
storePassword=******
keyAlias=your-alias
keyPassword=******
```

Release 构建开启 R8 混淆与资源压缩，并仅打包 `arm64-v8a`，APK 约 14 MB。产物位于 `app/build/outputs/apk/release/`。若需在 x86 模拟器运行，请使用 Debug 包。

## 技术栈

| 组件 | 版本 / 说明 |
| --- | --- |
| Kotlin | 2.4.20 |
| Jetpack Compose | Compose Multiplatform 1.12.0（Android） |
| Miuix | 0.9.4，HyperOS 设计组件 |
| PDFBox-Android | 2.0.27.0，PDF 文本抽取 |
| ML Kit Digital Ink | 19.0.0，手写识别 |
| kotlinx.serialization | JSON 本地存储与备份 |
| Android Gradle Plugin | 9.3.1，R8 混淆与资源压缩 |

## 已知限制

- 仅适配当前「不背单词」导出的双栏词表版式，扫描版 PDF 不支持。
- Release 包仅含 arm64-v8a，x86 / armeabi-v7a 设备请自行构建 Debug 包。
- 手写识别需要 Google Play 服务与首次联网下载模型。
- 界面主要面向平板横屏设计，手机上体验可能受限。

## 致谢

- [Miuix](https://github.com/compose-miuix-ui/miuix) — HyperOS 风格 Compose 组件库
- [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) — PDF 文本解析
- [ML Kit Digital Ink](https://developers.google.com/ml-kit/vision/digital-ink-recognition) — 手写识别
- [JetBrains Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)

## License

[MIT](LICENSE) © 2026 chenbiwei520-alt

「不背单词」及相关词表内容的版权归其各自权利人所有，本项目仅提供个人学习用途的词表解析与默写工具。
