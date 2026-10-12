# SX-Item-Action 1.1.1

## 修复

- 修复左键实体时 Bukkit 不派发 `PlayerInteractEvent`，导致 `on-left-click` 不触发的问题。
- 通过监听玩家挥手动作并沿视线检测实体补发左键动作；实际攻击仍由 `on-attack` 独立处理。

## 构建

构建产物：`build/libs/SX-Item-Action-1.1.1.jar`
