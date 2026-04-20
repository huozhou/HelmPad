<div align="right">

[English](README.md) · 中文

</div>

# Helm Pad

> 把手里这部 Android 手机变成一只贴在键盘旁的小型蓝牙键鼠,这样我就可以一边端着咖啡、一边接着推 AI 代理,人不必从椅背上挪开。

**网站:** <https://huozhou.github.io/HelmPad/>

<p align="center">
  <img src="assets/hero.svg" alt="Helm Pad hero" width="100%">
</p>

## 为什么做它

我大部分时间在终端里跟 AI agent 结对编程。打字主要是它在打,我做的是不停给它点拍子——*"行,跑这个"*、*"不,换个思路"*、*"Esc,重来"*、*"切个模型"*、*"开新会话"*。每一下都很小,但一天下来很多。

让我开始觉得不顺的不是工作,是姿势。每点一下都得把手抬离键盘、或把眼睛从 diff 上挪走。更尴尬的是 agent 在跑长任务、我端起咖啡往后靠的那一会——它一冷不防停下来要我点头,杯子要放下、人要坐起来、鼠标要找到、点完才能再靠回去——那一份闲暇还没成型就没了。

于是我用手里这部 Android 手机做了一只小型蓝牙键鼠。

## 它做的事

对 Mac(或 Windows)来说,这部手机就是一只标准蓝牙键盘加鼠标——电脑端不用装伴侣 app、不用装驱动、不用走任何权限弹窗。在手机这边:

- 八个宏分两排,按你主用的 agent 有三套 profile:**Claude Code**、**Codex**、**Cursor**。每套 profile 在 CLI(`codex`、`cursor-agent`)和 GUI(Codex 桌面版、Cursor / VS Code 聊天面板)下都通用。Approve / Esc / Switch model / New session 在三套里的位置一致;中间两格各自按 agent 的实际常用命令调了(Cycle mode、`/approvals`、`/diff` 等)。
- 剩下的整片屏幕是一只真正的触控板——单指移动+点击、双指滚动、双指点击 = 右键、长按拖动。
- 自动识别手机连的是 Mac 还是 Windows,自动用对应的修饰键(Cmd / Ctrl)。同一部手机在两套机器之间换,我不必动手切。

<p align="center">
  <img src="assets/loop.svg" alt="Steering an agent without leaving the keyboard" width="100%">
</p>

我自己最常用的那一刻:agent 在跑,一只手机一杯咖啡,半刷不刷地翻点别的。它停下来要我点头,我拇指一点 *Approve*,人继续靠着,咖啡继续端着——那一份闲暇没被打断。

## 怎么用

需要一台 Android 9+ 的手机、一台 Mac 或 Windows。

1. 在 Releases 下最新 APK 装上。
2. 在电脑蓝牙设置里像配普通键盘一样把手机配对进去。
3. 打开 Helm Pad,跟着首次启动的引导走一遍。最后一步会让你挑主用的 CLI——默认预选 Claude Code,只用 Claude 的用户直接 Next 即可;以后随时能在 设置 → 当前 profile 里切。

装在你日常在用的主力手机上没问题;不想跟个人使用混在一起的话,装在一台单独留在桌上的备用手机上也行。电脑端不必装任何东西。

## 许可

采用 MIT 许可证 — 见 [LICENSE](./LICENSE)。
