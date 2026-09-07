# SX-Item-Action 1.1.0

新增自定义耐久与物品限时，配置仍写在 SX-Item 物品的 `CustomDurability`、`TimeLimit` 节点中。

- 耐久和到期时间持久化到 NBT `SX-Attribute`，SX-Item update 保留已有状态。
- 自定义耐久接管原版损耗，提供比例耐久条及 `/sxia durability`、`/sxia repair` 命令。
- 限时支持有效时长、固定日期及 `/sxia timelimit` 命令；到期后清理物品。
- 数值显示使用 SX 原生 `<s:value>`、`<s:max-value>`，日期使用 `<s:time>` 和 SX 的 `TimeFormat`。
- Lore 支持整行引入 `<l:CustomDurability>`、`<l:TimeLimit>`，支持指定位置、重复引用及同行文字。
- 补充配置放置、重载、获取物品、合并现有配置和脚本 `import` 的完整示例，参见仓库 `examples/README.md`。
- 兼容旧 SX-Attribute 的 Lore 耐久监听器，避免其重复处理本插件管理的耐久物品。
- 修复增量构建沿用旧版 `plugin.yml` 版本号的问题，以及 Kether 示例脚本的注释语法。

## 已验证

- Gradle 构建通过。
- Paper 26.2-84 / Java 25 与 Paper 1.21.11-132 / Java 21。
- 两套环境均使用 SX-Item 4.5.10、SX-Attribute 4.0.0-beta.9。
- 实际验证 NBT 持久化、整行/重复/混排 Lore、原生变量与日期、update/reload 状态保留、管理命令、过期清理、非法状态拒绝、脚本 import 及 Kether/Fluxon 自检。
- 真实客户端挖掘到耐久耗尽，损坏动作一次，同编号另一件不受影响；重启后耐久和原截止时间不变，离线到期物品被清除。

详细范围及尚未覆盖的交互场景见仓库 `tests/server/results-1.1.0.md`。安装时替换旧版插件 JAR 并完整重启；物品及脚本引入步骤见 `examples/README.md`。

## SHA-256

`95ca18746497c0ad7e823e35267a47667ffb19ec4b16b3fb89e7124b5f52ba6e  SX-Item-Action-1.1.0.jar`
