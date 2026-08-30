# 提交到 LSPosed 官方模块仓库（Xposed-Modules-Repo）

目标：[modules.lsposed.org](https://modules.lsposed.org) / github.com/Xposed-Modules-Repo，由 LSPosed 团队运营，支持 libxposed API 102 模块（已有多个同类模块收录）。

## 第 1 步：提交换机（发 issue）

访问 https://github.com/Xposed-Modules-Repo/submission/issues/new

标题：`[submission] io.github.hao1196561270.mifitnessadaway`

正文（可直接复制）：

```
Package name: io.github.hao1196561270.mifitnessadaway
Module name: MiFitnessAdAway
Source repository: https://github.com/hao1196561270/MiFitnessAdAway

小米运动健康（com.mi.health 3.58.0）去广告模块，libxposed API 102 现代模块。
Remove ads from Xiaomi Mi Fitness (com.mi.health 3.58.0), modern libxposed API 102 module.
```

bot 会自动创建 `Xposed-Modules-Repo/io.github.hao1196561270.mifitnessadaway` 仓库并邀请你为 admin（接受邀请后进入第 2 步）。

## 第 2 步：填充仓库内容

在新仓库（`Xposed-Modules-Repo/io.github.hao1196561270.mifitnessadaway`）根目录添加：

| 文件 | 说明 | 内容来源 |
|---|---|---|
| `SUMMARY` | 首页展示的一句话简介 | 见下方 |
| `README.md` | 模块完整说明 | `docs/xmr-README.md`（已准备好） |
| `SOURCE_URL` | 源仓库地址（可选） | `https://github.com/hao1196561270/MiFitnessAdAway` |
| `LICENSE` | 许可证 | 项目根 LICENSE（Apache 2.0） |

**SUMMARY 内容**（一行）：

```
小米运动健康去广告 Mi Fitness ad-blocker
```

## 第 3 步：发布 Release（必须带 APK）

在 `Xposed-Modules-Repo/io.github.hao1196561270.mifitnessadaway` 的 Releases 页新建：

```
Tag:     1.0-1.0        ← 格式必须是 [versionCode]-[versionName]
Title:   v1.0
Content: changelog（如"首个正式版 / Initial release"）
Attach:  MiFitnessAdAway-v1.0.1.apk（正式签名版）
```

APK 文件：`E:\Harness\MiFitnessAdAway\MiFitnessAdAway-v1.0.1.apk`（正式签名，已上传到你的 GitHub Release）。

## 生效时间

- 上传后 bot 自动同步，约 5 分钟内在 modules.lsposed.org 可见
- 仓库不完整（缺 SUMMARY/README/APK）时不会显示
- 以后更新：直接在新仓库发新 Release（Tag `versionCode-versionName` + APK），bot 自动同步

## 注意

- Release Tag 格式错误或未附带 APK 时 bot 无法同步，务必按 `数字-版本名` 格式
- 每次更新都走"发新 Release + 新 APK"，不要在旧 Release 上只替换 APK（bot 检测不到）