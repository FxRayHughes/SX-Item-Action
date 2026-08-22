# SX-Item-Action

为 **SX-Item 4.x** 物品添加点击、攻击、消耗、丢弃、定时等动作，并使用 Kether 或 Fluxon 编写效果。

[下载最新版](https://github.com/FxRayHughes/SX-Item-Action/releases/latest) · [SX-Item](https://github.com/Saukiya/SX-Item) · [问题反馈](https://github.com/FxRayHughes/SX-Item-Action/issues)

## 功能

- 动作直接写在原有 SX-Item 物品配置中，不需要创建另一套物品。
- 支持 Kether 与 Fluxon 1.7.2，两种脚本可以在同一动作链中混用。
- 支持内联脚本，也支持通过 `import` 复用独立脚本文件。
- 支持 SX-Item 表达式、Random 变量与 PlaceholderAPI 变量。
- 支持在配置或脚本中取消点击、攻击、丢弃等 Bukkit 事件。
- 只识别由 SX-Item 生成的物品，不会按名称、Lore 或材质误判其他物品。

## 安装

### 前置插件

- [SX-Item 4.x](https://github.com/Saukiya/SX-Item/releases/latest)

### 安装步骤

1. 从 [Releases](https://github.com/FxRayHughes/SX-Item-Action/releases/latest) 下载 `SX-Item-Action-*.jar`。
2. 将 SX-Item 与本插件放入服务器的 `plugins` 目录。
3. 完整启动服务器，不要使用插件管理器热加载。
4. 首次启动会自动下载并隔离 Fluxon 运行库，请确保服务器能够访问 Maven Central 与 TabooProject 仓库。
5. 进入服务器执行 `/sxia status`，确认 SX-Item 显示为“已启用”。

当前版本已在以下环境完成真实玩家测试：

- **Paper 26.2 + SX-Item 4.5.10**
- **Paper 1.21.11 + SX-Item 4.4.9**

其他服务端版本以 SX-Item 自身支持范围和实际测试为准。Minecraft 26.2 用户请使用 **SX-Item-Action 1.0.1 或更高版本**，旧版内置的 TabooLib 版本映射无法识别 26.2。

## 快速开始

打开一个现有的 SX-Item 物品配置，在物品节点中加入 `Action`：

```yaml
WelcomeStick:
  ID: STICK
  Name: '&a欢迎法杖'
  Action:
    on-right-click:
      engine: kether
      cancel: true
      variables:
        message: '&a你使用了欢迎法杖！'
      script:
        - 'tell color &message'
```

重载 SX-Item 的物品配置并重新生成该物品。玩家右键时会收到消息，同时右键事件会被取消。

> 已经生成的旧物品是否自动取得新动作取决于 SX-Item 的更新方式。排查问题时建议重新生成一件物品测试。

## 动作配置

每个动作节点支持以下字段：

| 字段 | 是否必填 | 说明 |
|---|---:|---|
| `engine` | 否 | `kether` 或 `fluxon`，省略时使用 Kether |
| `script` | 与 `import` 二选一 | 直接写脚本，可使用单行字符串、多行文本或 YAML 列表 |
| `import` | 与 `script` 二选一 | 导入预制脚本，文件扩展名可以省略 |
| `cancel` | 否 | 脚本执行前同步取消当前事件，默认 `false` |
| `variables` | 否 | 传给脚本的变量，会先经过 SX-Item 表达式解析 |

同一个入口可以配置多个动作，它们会按顺序执行并共享 `vars`：

```yaml
Action:
  on-right-click:
    - engine: kether
      variables:
        message: '&e先执行 Kether'
      script:
        - 'tell color &message'
    - engine: fluxon
      script: |-
        &player.sendMessage("§b再执行 Fluxon")
```

## 支持的触发器

| 配置键 | 触发时机 |
|---|---|
| `on-left-click` | 主手左键空气或方块 |
| `on-right-click` | 主手右键空气或方块 |
| `on-right-click-entity` | 主手右键实体 |
| `on-attack` | 使用主手物品攻击实体 |
| `on-block-break` | 使用物品破坏方块 |
| `on-block-place` | 放置物品对应的方块 |
| `on-item-break` | 物品耐久耗尽并损坏 |
| `on-consume` | 食用或饮用物品 |
| `on-drop` | 丢弃物品 |
| `on-pickup` | 拾取物品 |
| `on-swap-to-mainhand` | 换手后物品进入主手 |
| `on-swap-to-offhand` | 换手后物品进入副手 |
| `on-timer` | 玩家背包持有该物品时每秒执行一次 |

定时动作会按玩家和 SX-Item 物品编号去重。同一玩家背包中放置多件相同编号的物品，也只会每秒执行一次。

## 使用变量

Kether 与 Fluxon 都能读取以下内置变量：

| 变量 | 内容 |
|---|---|
| `player` | 当前玩家 |
| `item` | 触发动作的物品 |
| `event` | 当前 Bukkit 事件 |
| `trigger` | 当前触发器名称 |
| `itemId` | SX-Item 物品编号 |
| `context` | 动作上下文，可用于取消事件 |
| `vars` | 当前动作链共享的可变变量表 |

`variables` 中声明的值也会成为同名顶层变量：

```yaml
Random:
  damage:
    - '10'
    - '20'

Action:
  on-attack:
    engine: fluxon
    variables:
      damage: '<s:damage>'
      message: '&c本次伤害：<s:damage>'
    script: |-
      &player.sendMessage(&message)
```

字符串变量在进入脚本前会交给 SX-Item 的表达式系统处理，因此可以继续使用项目中已经配置好的 Random、数值表达式和 PlaceholderAPI 占位符。

## 预制脚本

首次启动后会生成以下目录：

```text
plugins/SX-Item-Action/scripts/
├─ kether/
│  └─ example.ks
└─ fluxon/
   └─ example.fs
```

新建脚本后，在物品中使用 `import`：

```yaml
Action:
  on-consume:
    engine: kether
    import: heal
    variables:
      amount: 10
```

对应文件为 `plugins/SX-Item-Action/scripts/kether/heal.ks`。Fluxon 脚本同理放在 `scripts/fluxon/`，扩展名为 `.fs`。

修改脚本后执行 `/sxia reload`。如果脚本没有加载，使用 `/sxia validate` 查看目录、扩展名、空文件或重复名称诊断。

## 取消事件

最稳妥的方式是在动作配置中加入：

```yaml
cancel: true
```

需要按条件决定时，可以在脚本中操作当前事件。

Kether：

```kether
if check &shouldCancel then {
  sx-event cancel
}
```

Fluxon：

```fluxon
if &shouldCancel then &context.cancelEvent()
```

Kether 还提供 `sx-event uncancel`、`sx-event cancelled`；Fluxon 可以调用 `&context.uncancelEvent()`、`&context.isEventCancelled()`。

事件取消必须发生在脚本第一次等待、异步调用或其他挂起操作之前。需要稳定取消时优先使用同步的 `cancel: true`。

## 管理命令

| 命令 | 说明 |
|---|---|
| `/sxia reload` | 重载 Kether 与 Fluxon 预制脚本 |
| `/sxia status` | 查看 SX-Item 状态、脚本数量和动作统计 |
| `/sxia validate` | 检查预制脚本目录和文件问题 |
| `/sxia selftest` | 由在线玩家执行 Kether、Fluxon、动作链和事件取消自检 |

管理权限：`sxitemaction.admin`

## 常见问题

### 插件提示未检测到 SX-Item

确认安装的是 SX-Item 4.x，并完整重启服务器。不要使用 PlugMan 等工具单独热加载本插件。

### 动作没有触发

1. 使用 `/sxia status` 确认两个插件均已启用。
2. 确认测试物品确实由 SX-Item 生成，而不是用原版命令创建的同名物品。
3. 确认 `Action` 写在物品节点内部，并重新生成一件物品测试。
4. 使用 `/sxia validate` 检查导入脚本。
5. 查看控制台中的物品编号、触发器和脚本错误。

### 首次启动很慢或 Fluxon 加载失败

首次启动需要下载 Fluxon 1.7.2 及运行依赖。检查服务器是否能够访问：

- `https://repo.tabooproject.org/repository/releases`
- `https://repo.maven.apache.org/maven2`

依赖成功缓存后，后续启动会直接复用本插件的私有重定向运行库。

### `import` 提示找不到脚本

确认引擎、目录和扩展名对应：Kether 使用 `scripts/kether/*.ks`，Fluxon 使用 `scripts/fluxon/*.fs`。文件名不区分大小写，但建议统一使用小写英文。

## 安全提示

Fluxon 脚本可以通过 Bukkit 对象访问服务器运行时，应当视为管理员代码。不要安装或导入来源不明的脚本文件。

## 从源码构建

项目使用 Gradle Wrapper。构建产物位于 `build/libs/SX-Item-Action-<版本>.jar`。
