# 数字华容道 · Klotski

基于 **Kotlin + JavaFX WebView** 的数字华容道游戏。Kotlin 负责不可变棋局状态和规则，HTML/CSS/JavaScript 负责渲染与交互。

## 功能

- 棋盘边长可在 **2 ~ 215** 之间自由选择。
- 支持鼠标点击、方向键移动，以及撤销、重做、重新开始和重新打乱。
- 鼠标滚轮缩放，大棋盘支持拖动查看局部。
- 计时、步数统计，完成后显示成绩面板。
- 支持普通全屏、无边框全屏和一键退出。
- 随机棋局必定可解，不会出现无解局面。

最大边长由 `Int.MAX_VALUE` 的四次方根向下取整得到：

```text
floor(Int.MAX_VALUE^(1/4)) = 215
```

## 环境要求

- JDK 21+
- Maven 3.9+

## 运行

```bash
mvn clean javafx:run
```

## 构建

```bash
mvn clean package
```

生成的 JAR 位于 `target/klotski-1.0-SNAPSHOT.jar`。由于项目使用 JavaFX WebView，推荐使用 `mvn javafx:run` 启动。

## 打包 Windows 便携版

项目提供 `package-exe.ps1` 脚本，只生成便携版：

```powershell
powershell -ExecutionPolicy Bypass -File .\package-exe.ps1
```

需要：

- JDK 21+，且 `JAVA_HOME` 指向该 JDK
- Maven

产物：

```text
dist/
├── Klotski/
│   └── Klotski.exe        # 便携版入口
└── SHA256SUMS.txt         # 可执行文件校验值
```

运行时必须保留 `dist/Klotski` 整个文件夹，不能只复制 `Klotski.exe`。便携版已内置 Java 运行时和 JavaFX，目标电脑不需要额外安装 Java。可执行文件未进行代码签名，Windows 可能显示 SmartScreen“未知发布者”提示。

## 玩法与快捷键

1. 数字按从左到右、从上到下的顺序排列，`0` 表示空白格。
2. 点击与空白格相邻的数字，或使用方向键，让数字滑入空白格。
3. 将数字恢复为升序，并把 `0` 移回右下角，即完成游戏。

| 操作 | 快捷键 / 方式 |
| --- | --- |
| 移动数字 | 点击数字、方向键 |
| 撤销 / 重做 | `Ctrl+Z` / `Ctrl+Y` |
| 缩放棋盘 | 鼠标滚轮、右下角 `+` / `−` |
| 适应窗口 | 双击画布、右下角“适应” |
| 全屏 | `F11` 或顶部“全屏”按钮 |
| 退出全屏 | `Esc` 或顶部“退出全屏” |
| 退出应用 | 顶部“退出”按钮 |

## 项目结构

```text
klotski/
├── pom.xml
├── README.md
└── src/main/
    ├── kotlin/com/thxy/
    │   ├── Main.kt                 # 程序入口
    │   ├── KlotskiApp.kt           # JavaFX WebView 窗口
    │   ├── GameBridge.kt           # 棋盘状态桥接层
    │   ├── WindowBridge.kt         # 全屏、无边框和退出控制
    │   ├── WindowBounds.kt         # 普通窗口位置/大小快照
    │   ├── Klotski.kt              # 不可变棋盘模型与移动规则
    │   └── gamestate/
    │       ├── GameState.kt        # 不可变游戏状态与状态转换
    │       ├── MoveDirection.kt    # 数字块移动方向
    │       └── Move.kt             # 一次移动及其反方向
    └── resources/com/thxy/
        ├── game.html               # 页面结构
        ├── game.css                # 界面样式
        ├── game.js                 # 棋盘渲染和交互
        └── error.html              # 加载失败页面模板
```

项目尽量遵循“一类一文件”的组织方式，游戏状态相关类型统一放在 `gamestate` 子包中。

## 技术说明

### 不可变状态与纯转换

- `Klotski` 是不可变棋盘，每次有效移动都会返回新的 `Klotski`。
- `GameState` 是不可变数据类，`newGame`、`move`、`undo`、`redo`、`restart`、`shuffle` 都采用不可变状态转换：接收旧状态，返回新状态，不修改输入（随机生成除外）。
- `GameBridge` 只持有当前的 `GameState` 引用，并把状态序列化为 JSON。
- Canvas 绘制、事件监听等 DOM 副作用仍然由 JavaScript 完成；游戏规则和状态管理采用函数式风格。

### 坐标与方向

- 坐标从 `1` 开始，格式为 `(x, y)`，其中 `x` 是列，`y` 是行。
- `up()`、`down()`、`left()`、`right()` 按**数字块的移动方向**命名，空白格会向反方向移动。
- `snapshot()` 返回按行优先排列的棋盘副本，供 WebView 渲染。

### 可解随机棋局

`random()` 不采用随机走步，而是：

1. 随机排列数字 `1 ~ n²-1`；
2. 将空白格固定在右下角；
3. 使用树状数组计算逆序数，必要时交换两个数字，使逆序数为偶数；
4. 根据滑块拼图的可解性条件，保证生成的棋局一定可解。

该算法复杂度为 `O(n² log n)`，即使生成 215×215 棋盘也只需要毫秒级时间。

### WebView 通信与窗口控制

- `GameBridge` 通过 JavaFX 的 `JSObject` 暴露给页面，JavaScript 调用 `start`、`move`、`undo` 等方法后收到 JSON 格式的棋盘状态。
- `WindowBridge` 负责普通全屏、无边框全屏、恢复普通窗口和退出应用；状态变化会主动回传给页面，保证按钮状态同步。
- 无边框全屏使用独立的无装饰 `Stage`，切换时会保留同一个 `Scene`，因此游戏进度不会丢失。
- 无边框窗口不设置置顶，其他应用切换到前台时可以正常显示在游戏窗口上方。

### 界面与性能

- 使用 Canvas 绘制棋盘：小格子跳过数字文本，放大后根据格子大小自动计算字号。
- 支持 HiDPI、滚轮缩放、拖动平移和窗口自适应。
- 拖动时只重绘可见区域，并缓存共用字号计算；棋盘使用 Canvas 而不是大量 DOM 节点。
- 页面资源全部位于 `src/main/resources/com/thxy`，无需网络连接。
