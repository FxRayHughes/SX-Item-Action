# SX-Item-Action 1.1.0

新增自定义耐久与物品限时，配置仍写在 SX-Item 物品的 `CustomDurability`、`TimeLimit` 节点中。

- 耐久和到期时间持久化到 NBT `SX-Attribute`，SX-Item update 保留已有状态。
- 自定义耐久接管原版损耗，提供比例耐久条及 `/sxia durability`、`/sxia repair` 命令。
- 限时支持有效时长、固定日期及 `/sxia timelimit` 命令；到期后清理物品。
- 数值显示使用 SX 原生 `<s:value>`、`<s:max-value>`，日期使用 `<s:time>` 和 SX 的 `TimeFormat`。
- Lore 支持整行引入 `<l:CustomDurability>`、`<l:TimeLimit>`，支持指定位置、重复引用及同行文字。
- 补充配置放置、重载、获取物品、合并现有配置和脚本 `import` 的完整示例，参见仓库 `examples/README.md`。

## 发布前验证状态

当前为发布草稿，未附加可安装 JAR。未执行构建（用户未要求）；本次新增功能尚未在服务端实际测试。此前 1.0.x 的动作功能测试不能替代本次验证。完成构建和 `tests/lifecycle-manual.md` 中的实际验收后，应更新本段为实际结果，并附加 1.1.0 JAR 及 SHA-256。
