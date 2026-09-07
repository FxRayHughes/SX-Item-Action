# SX-Item-Action

为 **SX-Item 4.x** 物品添加自定义耐久、限时有效期，以及点击、攻击、消耗、丢弃、定时等动作，使用 Kether 或 Fluxon 编写效果。

[下载最新版](https://github.com/FxRayHughes/SX-Item-Action/releases/latest) · [SX-Item](https://github.com/Saukiya/SX-Item) · [问题反馈](https://github.com/FxRayHughes/SX-Item-Action/issues)

## 功能

- 动作直接写在原有 SX-Item 物品配置中，不需要创建另一套物品。
- 支持 Kether 与 Fluxon 1.7.2，两种脚本可以在同一动作链中混用。
- 支持内联脚本，也支持通过 `import` 复用独立脚本文件。
- 支持 SX-Item 表达式、Random 变量与 PlaceholderAPI 变量。
- 支持在配置或脚本中取消点击、攻击、丢弃等 Bukkit 事件。
- 自定义耐久沿用原版损耗与耐久附魔，按比例显示原版耐久条，支持命令修复。
- 限时物品支持生成后有效时长或固定截止日期，离线继续计时，到期自动清理。
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

此前发布的动作功能已在以下环境完成真实玩家测试：

- **Paper 26.2 + SX-Item 4.5.10**
- **Paper 1.21.11 + SX-Item 4.4.9**

1.1.0 新增的耐久、限时和整行 Lore 变量尚未完成构建与服务端测试，验收场景见 [测试清单](tests/lifecycle-manual.md)。上述历史测试结果不代表新增功能已经通过验证。

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

## 自定义耐久与物品限时

两个模块内置于本插件，无需安装 AdvancedDurability 或 SXAttribute。配置仍与 `Action` 同级，无需手写 NBT：

```yaml
TrialSword:
  ID: DIAMOND_SWORD
  Name: '&b试用长剑'
  Lore:
    - '&7一把具有自定义耐久的限时武器'
    - '<l:CustomDurability>'
    - '<l:TimeLimit>'
  CustomDurability:
    max: 10000
    current: 10000
    lore: '&7耐久度: <s:value>/<s:max-value>'
  TimeLimit:
    duration: 7d
    lore: '&7到期时间: <s:time>'
```

完整接入步骤见 [示例使用指南](examples/README.md)，其中包含目录位置、整份引入、合并到已有物品、获取命令、SX 变量与日期、独立脚本 `import` 和排查方法。

直接体验：将 [lifecycle-items.yml](examples/lifecycle-items.yml) 复制到服务器的 `plugins/SX-Item/Item/lifecycle-items.yml`，执行 `/si reload`，再执行 `/si give TrialSword`。无需手写 NBT 或额外注册变量。`/sxia reload` 仅重载脚本，不能代替物品配置重载。

### 自定义耐久

| 字段 | 说明 |
|---|---|
| `CustomDurability.max` | 必填，1～2147483647 的整数，支持 SX 表达式，仅支持有原版耐久的材质 |
| `CustomDurability.current` | 初始剩余耐久，默认等于最大值，范围 0～max；支持 SX 表达式 |
| `CustomDurability.lore` | 显示模板，默认 `&7耐久度: <s:value>/<s:max-value>` |

参考 [AdvancedDurability](https://archives.mcbbs.co/read.php?tid=733915)：直接采用 Bukkit 耐久事件提供的损耗，不重复计算耐久附魔概率；例如 `5000/10000` 显示约一半的原版耐久条。原版不可破坏物品仍遵守原版不产生损耗事件的行为。

耐久耗尽时销毁受损的一件物品，并触发已有的 `Action.on-item-break`。通过 `/sxia durability 10000` 设置主手物品最大耐久并补满，通过 `/sxia repair` 修复。修复不会延长到期时间。自定义耐久物品禁止经验修补；受耐久或限时管理的物品禁止铁砧、砂轮和合成输出，避免转换物品时清掉状态。

### 物品限时

| 字段 | 说明 |
|---|---|
| `TimeLimit.duration` | 从生成时开始的有效时长：`600` 表示 600 秒，也支持 `7d`、`1d2h30m`、`30s` |
| `TimeLimit.expires-at` | 固定截止时间，格式跟随 SX-Item 的 `TimeFormat`；也支持 SX 原生 `<t:7d>` |
| `TimeLimit.lore` | 默认 `&7到期时间: <s:time>` |

`duration` 和 `expires-at` 必须且只能填写一个。初始数值、时长、固定日期和显示文本均交给 SX-Item 的 `ExpressionHandler` 解析，支持物品 Random、全局变量和嵌套表达式。也可用 `/sxia timelimit 7d` 从命令执行时起重新设置当前主手物品的期限。

到期会清除整叠物品，停止动作触发，并拦截点击、攻击、放置、消耗、发射及换手。在线玩家背包（含护甲、副手）、末影箱、光标与当前打开的容器每秒检查；登录、打开容器、拾取与使用时也检查。关闭的容器、地面掉落物和离线背包在再次访问、拾取或登录时清理，不会主动加载区块或离线玩家。

参考 SXAttribute 2.x 的“到期时间”格式，SX-Item 生成的物品也可使用原有兼容 Lore：

```yaml
Lore:
  - '&7Durability: 5000/10000'
  - '&7到期时间: 2026/12/31 23:59'
```

耐久兼容 `耐久度: 当前/最大` 与 `Durability: 当前/最大`，日期兼容中文或英文冒号。显式配置节点优先于 Lore；兼容行仅用于首次初始化，之后修改 Lore 不会改变已保存的状态。SXAttribute 的原实现会排除过期物品属性，本插件采用到期清理，不直接修改第三方属性插件的缓存。

### 在 Lore 中插入完整显示行

顶层 `Lore` 可通过 `<l:CustomDurability>` 插入 `CustomDurability.lore` 的完整内容，通过 `<l:TimeLimit>` 插入 `TimeLimit.lore` 的完整内容。变量由插件自动注册到 SX 原生 Random 系统，无需手写 RandomString 文件：

```yaml
Lore:
  - '&7装备说明'
  - ''
  - '<l:CustomDurability>'
  - '<l:TimeLimit>'
  - '&8其他说明'
```

这两行的位置由 `Lore` 顺序决定。耐久消耗、修复、续期后会在原位置刷新，不重复追加；相同变量可引用多次，也可以写成 `'&8[ <l:CustomDurability> &8]'`。`CustomDurability` 和 `TimeLimit` 是预留变量名，请勿在本地 Random 中定义同名变量。

`CustomDurability.lore` 内继续使用 `<s:value>/<s:max-value>`，`TimeLimit.lore` 内继续使用 `<s:time>`。不要让模块模板再引用自身的整行变量。没有对应功能时整行变量为空；没有使用整行变量的旧配置继续采用原来的末尾追加行为。

修改顶层 Lore 的排列后，执行 `/si reload` 并生成新物品测试，或让启用 SX update 的物品执行更新。插件会在 `/si reload` 后重新注册变量，已保存的耐久和截止时间仍保留。

### SX 原生变量与日期格式

显示文本使用 SX 原生变量，不再使用 `%value%`、`%max-value%`、`%time%`：

| 变量 | 内容 |
|---|---|
| `<s:value>` | 当前物品 NBT 中的剩余耐久 |
| `<s:max-value>` | 当前物品 NBT 中的最大耐久 |
| `<s:time>` | 当前物品 NBT 截止时间，使用 SX 的日期格式器格式化 |

这三个名字在本模块的解析上下文中优先于同名 Random。可以使用嵌套计算，例如 `current: '<c:int <s:max-value> / 2>'`；最大耐久先解析，再作为 `max-value` 注入。其他 SX 变量也通过同一原生解析器处理，玩家事件和命令会传递玩家上下文。数值变量应放在本模块的对应配置中；顶层 `Lore` 通过 `<l:CustomDurability>` 和 `<l:TimeLimit>` 引用完整显示行。`Name` 或动作 `variables` 不提供这些运行状态变量。

日期显示和固定日期解析都使用 SX-Item `SXItem.getSdf()` 的格式及时区，跟随 `plugins/SX-Item/Config.yml` 中的 `TimeFormat`，默认 `yyyy/MM/dd HH:mm`。例如：

```yaml
TimeLimit:
  expires-at: '<t:7d>'
  lore: '&7到期时间: <s:time>'
```

`<t:7d>` 只在首次初始化期限时执行，其生成文本按 SX 日期格式解析后保存为毫秒时间戳。SX 的 `TimeFormat` 若省略秒，使用 `<t:...>` 生成的期限也只有分钟精度；需要准确秒数时可用 `duration: 30s`，其保存值不经日期文本往返。显示截止时间应使用 `<s:time>`；直接把 `<t:7d>` 放进显示模板会按 SX 原生语义反复计算“从现在起七天后”。

### 状态保存与更新

**配置写法不变，实际运行状态统一存储在物品 NBT 的 `SX-Attribute` 节点下。**首次检测按配置初始化并写入；后续耐久损耗、修复、过期判断和续期均使用这里的状态：

| NBT 路径 | 保存内容 |
|---|---|
| `SX-Attribute.Durability` | 当前耐久整数 |
| `SX-Attribute.MaxDurability` | 最大耐久整数 |
| `SX-Attribute.ExpiresAt` | 固定到期时间，Unix 毫秒整数 |

`DurabilityLore`、`ExpiryLore` 和 `LoreBindings` 同样存于该节点，记录显示行及其模板对应关系，仅用于在原位置更新 Lore。无需在物品配置中手动声明这些内部字段。节点中其他属性保持原有内容，读回验证支持嵌套数据和 NBT 数组。

这些运行状态在 SX-Item update 时保留，背包转移、离线和服务器重启不会重置。修改配置中的 `max` 或有效期只影响新物品；已有物品需用管理命令显式修改。安装前已有的物品在首次检测时初始化相对有效期。显示模板仍从原配置读取。

日期、数值或存储异常会记录控制台日志并阻止物品使用，不会因错误配置删除物品。没有这些配置、兼容 Lore 或持久化状态的物品保持原有行为。不要同时让其他耐久插件接管同一物品。

动作链在每个后续动作开始前重新检查有效期；单条脚本内部已经开始的等待或异步操作由脚本自行控制。本次新增模块尚未经过实际服务端验证，历史测试环境记录仅对应原有动作功能。

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
| `/sxia durability <最大耐久>` | 为主手 SX-Item 设置自定义最大耐久并补满 |
| `/sxia repair` | 修复主手 SX-Item 的自定义耐久，不续期 |
| `/sxia timelimit <时长>` | 从现在起重新设置主手 SX-Item 有效期，如 `7d`、`600` |

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
