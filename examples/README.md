# 自定义耐久与限时物品接入示例

本文从放置文件开始，说明如何加载配置、领取物品和引用脚本。文中的 `plugins/` 均相对于你的 Minecraft 服务器根目录。

## 1. 安装与目录

安装 SX-Item 4.x 和包含本次耐久/限时功能的 SX-Item-Action，完整启动服务器一次。执行 `/sxia status`，确认 SX-Item 已启用。测试时使用 OP 玩家；本插件管理命令的权限为 `sxitemaction.admin`，`/si` 命令权限由 SX-Item 管理。

无需额外安装 AdvancedDurability、SXAttribute 或变量扩展包。耐久和限时模块已内置，变量直接使用 SX-Item 自己的解析器。

示例文件的用途：

| 本目录中的文件 | 复制到服务器的位置 | 用途 |
|---|---|---|
| [lifecycle-items.yml](lifecycle-items.yml) | `plugins/SX-Item/Item/lifecycle-items.yml` | 完整物品示例，可独立使用 |
| [import-items.yml](import-items.yml) | `plugins/SX-Item/Item/import-items.yml` | 可选：演示动作引用独立脚本 |
| [scripts/kether/lifecycle-break.ks](scripts/kether/lifecycle-break.ks) | `plugins/SX-Item-Action/scripts/kether/lifecycle-break.ks` | 供 import-items.yml 引用的脚本 |

物品配置由 **SX-Item** 加载，脚本文件由 **SX-Item-Action** 加载。请使用 UTF-8 保存文件，YAML 使用空格缩进。此仓库的 `examples/` 是手动复制的示例目录，不会自动安装到服务器。

## 2. 整份引入并领取第一件物品

1. 将 `lifecycle-items.yml` 复制到上表对应位置。首次只需要这一个文件，它的动作脚本已内联。
2. 确认示例物品编号没有与其他文件重复，例如 `TrialSword`；有冲突时修改最左侧的编号，并在领取命令中使用新编号。
3. 在游戏内执行 `/si reload`，查看控制台是否有 YAML、重复编号或加载错误。
4. 执行 `/si give TrialSword`，领取一把七天有效期、10000 自定义耐久的剑。
5. 手持剑攻击实体或正常使用，观察剩余耐久及原版耐久条变化。

给指定在线玩家发放时，命令顺序是：

```text
/si give TrialSword 玩家名 1
```

控制台执行时省略开头的 `/`，并填写玩家名。不要使用原版 `/give` 创建同名物品，本插件需要 SX-Item 的身份标记。

`/sxia reload` 仅重载预制脚本，不能代替 `/si reload` 加载物品配置。

## 3. 每个示例如何测试

| 物品编号 | 获取命令 | 功能和预期 |
|---|---|---|
| `TrialSword` | `/si give TrialSword` | 10000 耐久，生成后有效七天；耐久耗尽时发送内联动作消息 |
| `RandomDurabilitySword` | `/si give RandomDurabilitySword` | 用 SX 本地 Random 生成最大耐久，并用嵌套计算设置初始耐久 |
| `SXDateToken` | `/si give SXDateToken` | 用 SX 原生 `<t:7d>` 生成到期日期，显示已经保存的截止时间 |
| `FestivalToken` | `/si give FestivalToken` | 固定日期到期；领取前按 SX 的日期格式修改为你需要的未来日期 |
| `LegacySword` | `/si give LegacySword` | 演示首次从旧格式 Lore 初始化；导入后以 NBT 状态为准 |

为了快速观察效果，可以对 `TrialSword` 执行以下操作：

```text
/sxia durability 2
```

此命令将当前主手物品设置为 `2/2`。正常使用以消耗耐久，剩余一点时执行 `/sxia repair` 可补满；再次消耗到零会损坏并触发消息。命令修改的是手中这一件物品，不修改配置里的 `max: 10000`。

再领取一件进行限时测试：

```text
/si give TrialSword
/sxia timelimit 30s
```

先确保新物品在主手，再执行第二条命令。等待约 30 秒后，该物品会被清理；同编号的其他物品保留各自的截止时间。限时到期不会调用 `on-item-break`，该动作对应耐久损坏。

## 4. 合并到已有物品

已有 `MySword` 时，在它的节点内添加以下内容，不要再创建第二个同名节点：

```yaml
MySword:
  ID: DIAMOND_SWORD
  Name: '&a我的长剑'
  Lore:
    - '&7装备说明'
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

保留你原来的 `ID`、`Name`、`Lore`、`Random` 和 `Action` 内容，按需合并。`CustomDurability`、`TimeLimit` 与 `Action` 都在物品节点下，彼此同级；不要放进 `NBT`、`Lore` 或 `Action` 内部。

只需要自定义耐久时，删除整个 `TimeLimit` 节点和 `<l:TimeLimit>` 引用；只需要限时时，删除整个 `CustomDurability` 节点和 `<l:CustomDurability>` 引用。物品可以有任意材质的限时功能，但自定义耐久要求材质本身具有原版耐久，例如剑、工具或护甲。

修改配置后执行 `/si reload`，再 `/si give MySword` 生成新物品测试。已经生成的物品会保留已保存的耐久和期限，不能通过重载免费修复或续期；已有物品可用管理命令修改。

## 5. SX 变量怎么引用

本模块向 SX 原生变量上下文注入以下名字，无需安装占位符扩展或在 `Random` 中再次定义：

| 写法 | 含义 | 使用位置 |
|---|---|---|
| `<s:value>` | 已保存的当前耐久 | `CustomDurability.lore` |
| `<s:max-value>` | 已保存的最大耐久 | `CustomDurability.lore`；也可在 `current` 初始化表达式中引用 |
| `<s:time>` | 按 SX 日期格式生成的已保存截止时间 | `TimeLimit.lore` |

上表中的数值变量用于模块内部模板。要把整行插入顶层 `Lore`，使用下列原生锁定变量：

```yaml
Lore:
  - '&7装备说明'
  - '<l:CustomDurability>'
  - '<l:TimeLimit>'
  - '&8其他说明'
```

`<l:CustomDurability>` 展开整个 `CustomDurability.lore`，`<l:TimeLimit>` 展开整个 `TimeLimit.lore`，按你安排的顺序显示。消耗耐久或修改期限时会原位更新，不会额外在末尾重复追加。无需手动注册变量，也不要在 Random 中占用 `CustomDurability`、`TimeLimit` 这两个名字。

模块的 `lore` 模板内不要再次引用自身的整行变量。可以重复放置整行变量，或添加前后文字，如 `'&8[ <l:CustomDurability> &8]'`。没有对应功能时变量为空；不写整行变量的旧配置仍沿用末尾追加显示行的方式。

整行变量仅用于顶层 Lore；不要把它们或上表中的状态变量用于 `Name`、动作 `variables` 等其他上下文。修改顶层 Lore 顺序后用新物品或 SX 的物品 update 验证，既有耐久和期限不会被重置。

`max`、`current`、`duration`、`expires-at` 和两个模块的 `lore` 都经过 SX 原生解析器。例如：

```yaml
RandomDurabilitySword:
  ID: IRON_SWORD
  Name: '&e随机耐久长剑'
  Lore:
    - '<l:CustomDurability>'
  Random:
    initialDurability:
      - '1000'
      - '2000'
  CustomDurability:
    max: '<s:initialDurability>'
    current: '<c:int <s:max-value> / 2>'
    lore: '&7耐久度: <s:value>/<s:max-value>'
```

先解析 `max`，再注入 `max-value` 供 `current` 使用。该示例得到 `500/1000` 或 `1000/2000`，耐久条约为一半。`current` 必须为整数，`<c:int ...>` 使用 SX 的整数计算结果。Random 只在初始化数值时参与取值，已保存的耐久不会每秒重新随机。

## 6. 日期格式和两种限时配置

日期格式统一由 `plugins/SX-Item/Config.yml` 中的 `TimeFormat` 决定，默认：

```yaml
TimeFormat: 'yyyy/MM/dd HH:mm'
```

修改此配置后执行 `/si reload`。如果希望显示秒，可设置为 `'yyyy/MM/dd HH:mm:ss'`，并相应修改所有手写固定日期的格式。

方式一：直接指定有效时长，保存精确截止时间：

```yaml
TimeLimit:
  duration: 30s
  lore: '&7到期时间: <s:time>'
```

支持纯秒数 `600`，或 `7d`、`1d2h30m` 等 d/h/m/s 组合。游戏离线或服务器关闭期间仍计时。

方式二：使用 SX 原生时间表达式生成日期：

```yaml
TimeLimit:
  expires-at: '<t:7d>'
  lore: '&7到期时间: <s:time>'
```

也可将 `expires-at` 写成固定日期，如 `'2026/12/31 23:59'`，格式必须匹配当前 `TimeFormat`，并确认日期仍在未来。`duration` 与 `expires-at` 必须二选一，不能同时填写。

`<t:7d>` 负责首次生成截止日期；`<s:time>` 负责显示保存后的日期。不要用 `<t:7d>` 作为截止时间的显示文本，否则 SX 会每次按当前时间重新生成“七天后”的文字。默认 `TimeFormat` 不含秒，因此 `<t:...>` 日期文本只有分钟精度；验证 30 秒到期时建议用 `duration: 30s`。

## 7. 使用 Action.import 引入独立脚本

耐久和限时不需要 `import` 即可运行。`import` 用于复用动作脚本，例如为多种物品共用一段耐久损坏提示。

1. 把 [scripts/kether/lifecycle-break.ks](scripts/kether/lifecycle-break.ks) 复制到 `plugins/SX-Item-Action/scripts/kether/lifecycle-break.ks`。
2. 把 [import-items.yml](import-items.yml) 复制到 `plugins/SX-Item/Item/import-items.yml`。
3. 依次执行 `/sxia reload`、`/sxia validate`、`/si reload`。
4. 执行 `/si give ImportedBreakSword`，手持该剑使用至耐久耗尽，观察脚本消息。

脚本文件内容：

```kether
# 消息由各物品的 Action.variables 提供，使同一脚本可以复用。
tell color &message
```

物品中的引用方式：

```yaml
Action:
  on-item-break:
    engine: kether
    import: lifecycle-break
    variables:
      message: '&c你的装备耐久耗尽，已损坏！'
```

`import: lifecycle-break` 对应 Kether 目录中的 `lifecycle-break.ks`，不填写磁盘路径；扩展名可以省略。此处用 `import` 替代该动作的内联 `script`。修改脚本内容后执行 `/sxia reload`；修改物品配置后执行 `/si reload`。

此处 `&message` 是 Kether 的脚本变量语法，`<s:...>` 是 SX 的配置表达式语法。两者的写法与上下文不同，示例消息无需引用状态变量。

## 8. 确认存储与排查问题

手持测试物品执行 `/si nbt`，可检查 `SX-Attribute` 下的 `Durability`、`MaxDurability`、`ExpiresAt`。本插件会自动保存这些字段，不需要额外编写 NBT 配置。`ExpiresAt` 是内部毫秒时间戳，玩家看到的是经 SX 格式化后的日期。

| 现象 | 检查方法 |
|---|---|
| `/si give` 找不到物品 | 确认文件位于 SX-Item 的 `Item` 目录，执行 `/si reload`，检查编号和 YAML 缩进 |
| 没有耐久或限时显示 | 确认插件版本包含新增功能，配置与 Action 同级，并重新生成物品 |
| 领取后立即消失 | 检查固定到期日期是否已过期、是否符合当前 SX TimeFormat |
| 修改配置后旧物品没补满或续期 | 这是状态保留行为；使用新物品测试，或执行 durability/repair/timelimit 管理命令 |
| 显示 `<s:...>` 原文或变量异常 | 确认变量拼写、放置位置和 Random 定义，查看控制台中的 SX 表达式错误 |
| import 找不到脚本 | 核对引擎、目录、文件名，执行 `/sxia reload` 和 `/sxia validate` |

本说明与示例已做静态检查，尚未代表真实服务端验收通过。完整验收场景见 [lifecycle-manual.md](../tests/lifecycle-manual.md)。
