<div align="right">

[English](README.md) · 中文

</div>

# Helm Pad

> 把手里这部 Android 手机变成一只贴在键盘旁的小型蓝牙键鼠——这样我就可以一边端着咖啡、一边接着推 AI 代理,人不必从椅背上挪开。

**网站:** <https://huozhou.github.io/HelmPad/>

<p align="center">
  <img src="assets/hero.svg" alt="Helm Pad hero" width="100%">
</p>

## 为什么做它

我大部分时间在终端里跟 AI agent 结对编程。它打字,我给它点拍子——*"行"*、*"不"*、*"Esc"*、*"切模型"*、*"开新会话"*。每一下都很小,一天下来很多。

让我不顺的不是工作,是姿势。每点一下都得把手抬离键盘、或把眼睛从 diff 上挪走。更难受的是 agent 在跑长任务、我端起咖啡往后靠的那一会——它一停下来要我点头,杯子要放下、人要坐起来、鼠标要找到——那份闲暇还没成型就没了。

于是我用手里这部手机做了一只小型蓝牙键鼠。

## 它做的事

对电脑来说,这部手机就是一只标准的蓝牙键盘加鼠标。不用装伴侣 app、不用装驱动、不走任何权限弹窗。

手机这边:

- **八个宏**,按你主用的 agent 分三套:**Claude Code**、**Codex**、**Cursor**。Approve / Esc / ↑↓ 在三套里位置一致。
- **一片真正的触控板**占满剩下的屏——移动、滚动、右键、拖拽。
- **Cmd 还是 Ctrl**,按你配对的是 Mac 还是 Windows 自动切。

<p align="center">
  <img src="assets/loop.svg" alt="Steering an agent without leaving the keyboard" width="100%">
</p>

我最常用的那一刻:agent 在跑,一只手机一杯咖啡,半刷不刷地翻点别的。它停下来要我点头,拇指一点 *Approve*,人继续靠着,咖啡继续端着。

## 怎么用

一台 Android 9+ 手机,一台 Mac 或 Windows。

1. 在 Releases 下最新 APK 装上。
2. 在电脑蓝牙设置里像配普通键盘一样把手机配对进去。
3. 打开 Helm Pad,跟着首次启动的引导走一遍即可。

## 许可

MIT — 见 [LICENSE](./LICENSE)。
