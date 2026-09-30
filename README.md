<img alt="OctoDroid" align="right" src="https://raw.githubusercontent.com/3299074036/OctoDroid/master/app/src/main/res/drawable-xxhdpi/octodroid.png">

OctoDroid
=========

OctoDroid 是一款开源的 GitHub Android 客户端，让你随时随地刷 GitHub、看代码、管 Issue。
本仓库是基于 [slapperwan/gh4a](https://github.com/slapperwan/gh4a) 的中文定制版：全量中文汉化，并加入了多项实用功能。

下载
----

前往 [Releases](https://github.com/3299074036/OctoDroid/releases) 下载最新 APK。

主要功能
---------

### 仓库
* 浏览仓库、分支、标签
* 关注 / 取消关注仓库
* 查看 Pull Request、贡献者、Issue
* 私有仓库 ZIP 下载、Release 页面源码包下载
* **README 一键翻译**：支持 Google / 有道 / 百度 / DeepL，跳过代码块、无感并行翻译、可切回原文

### 用户
* 查看资料、动态时间线
* 关注 / 取关、粉丝 / 正在关注
* 查看公开仓库、Star、组织

### Issue / PR
* 列表、筛选（标签、经办人、里程碑）
* 创建 / 编辑 / 关闭 / 重开 Issue，评论
* Issue 模板自动加载

### 提交与代码
* 提交详情、文件 Diff（彩色高亮）
* 代码浏览、语法高亮
* **内置图片查看器**：手势缩放、旋转

### 探索
* 公开时间线、趋势仓库（日 / 周 / 月 / 总榜）
* **Topic 发现**：搜任意 topic，12 个热门标签
* GitHub 博客

### 本版新增
* **导航栏自定义**：拖拽排序、显示 / 隐藏任意入口
* **Star 更新**：一页看完所有 Star 仓库的最新 Release
* **最近浏览**：看过的仓库 / Issue / PR 本地记录
* **Star 分组**：给 Star 仓库建分组管理
* 深色模式开关、语言选择（中文 / 英文 / 跟随系统）
* 搜索排序、全局字号、通知逐页加载

如何构建
---------

- 安装 Android SDK platform 与 build-tools
- 在 [GitHub 开发者设置](https://github.com/settings/developers) 为自己注册一个 OAuth 应用
  * 名字随意
  * 回调 URL 必须填 `gh4a://oauth`
- 在仓库根目录创建 `client.properties`，内容如下（该文件已加入 `.gitignore`，不会被提交）：
```
ClientId="<应用设置里显示的 CLIENT ID>"
ClientSecret="<应用设置里显示的 CLIENT SECRET>"
```

- 用 Gradle 构建：

```bash
./gradlew assembleDebug
```

- 查看全部可用任务：

```bash
./gradlew tasks
```

用到的开源库
-------------

* [android-gif-drawable](https://github.com/koral--/android-gif-drawable)
* [AndroidSVG](https://github.com/BigBadaboom/androidsvg)
* [AndroidX](https://github.com/androidx/androidx)
* [emoji-java](https://github.com/vdurmont/emoji-java)
* [GitHubSdk](https://github.com/maniac103/GitHubSdk)
* [HoloColorPicker](https://github.com/LarsWerkman/HoloColorPicker)
* [MarkdownEdit](https://github.com/Tunous/MarkdownEdit)
* [Material Design Icons](https://github.com/google/material-design-icons)
* [PrettyTime](https://github.com/ocpsoft/prettytime)
* [Recycler Fast Scroll](https://github.com/pluscubed/recycler-fast-scroll)
* [Retrofit](https://github.com/square/retrofit)
* [RxAndroid](https://github.com/ReactiveX/RxAndroid)
* [RxJava](https://github.com/ReactiveX/RxJava)
* [RxLoader](https://github.com/maniac103/RxLoader)
* [SmoothProgressBar](https://github.com/castorflex/SmoothProgressBar)

致谢
----

* 上游项目 [slapperwan/gh4a](https://github.com/slapperwan/gh4a) 及所有贡献者
* [maniac103](https://github.com/maniac103) - 功能改进与修 bug
* [Tunous](https://github.com/Tunous) - 功能改进与修 bug
* [zquestz](https://github.com/zquestz) - 应用图标
