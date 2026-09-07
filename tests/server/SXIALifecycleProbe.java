import github.saukiya.sxitem.SXItem;
import github.saukiya.tools.nms.NbtUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** 专用测试服探针：使用真实 SX 生成器、NBT 适配层和 Bukkit 事件链，禁止普通玩家调用。 */
public final class SXIALifecycleProbe extends JavaPlugin implements Listener {
    private Player player;
    private int breaks;
    private boolean cancelDamage;
    private BlockState savedBlock;
    private ItemStack timed;
    private ItemStack later;
    private ItemStack commandBefore;
    private long commandExpiry;

    /** 探针仅在依赖启用后注册，所有断言与事件分发都在服务器主线程执行。 */
    @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }
    /** 恢复真实挖掘使用的单个世界方块，避免重复测试累积场地修改。 */
    @Override public void onDisable() { restoreBlock(); }
    private void restoreBlock() { if (savedBlock != null) { savedBlock.update(true, false); savedBlock = null; } }

    /** LOW 模拟其他插件已取消的损耗，检验附属不会再次扣减。 */
    @EventHandler(priority = EventPriority.LOW)
    public void cancel(PlayerItemDamageEvent event) { if (event.getPlayer() == player && cancelDamage) event.setCancelled(true); }
    /** 只统计专用账号的损坏通知，与脚本输出分别断言避免重复广播。 */
    @EventHandler public void broken(PlayerItemBreakEvent event) {
        if (event.getPlayer() == player) {
            breaks++;
            say("BREAK key=" + SXItem.getItemManager().getItemKey(event.getBrokenItem()));
        }
    }

    /** 控制入口固定到隔离账号；失败同时写日志与聊天，使客户端可可靠终止而非误报成功。 */
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) || !sender.isOp() || !sender.getName().equals("SXIALifeTest")) return true;
        player = (Player) sender;
        try {
            switch (args[0]) {
                case "suite": suite(); break;
                case "timedcheck": timedCheck(); break;
                case "real": real(); break;
                case "realcheck": realCheck(); break;
                case "save": save(); break;
                case "verify": verify(); break;
                case "commands": commands(); break;
                case "repaircheck": repairCheck(); break;
                case "durabilitycheck": durabilityCheck(); break;
                case "atomiccheck": atomicCheck(); break;
                case "limitcheck": limitCheck(); break;
                default: throw new IllegalArgumentException("Unknown phase");
            }
            say("DONE " + args[0]);
        } catch (Throwable error) {
            getLogger().log(java.util.logging.Level.SEVERE, "PROBE FAILURE", error);
            say("FAIL " + error);
        }
        return true;
    }
    private void say(String text) { getLogger().info(text); player.sendMessage("[SXIA-PROBE] " + text); }
    private void check(boolean value, String name) { if (!value) throw new AssertionError(name); say("PASS " + name); }
    private ItemStack spawn(String key) { return SXItem.getItemManager().getItem(key, player, new Object[0]); }
    private Map<String, Object> state(ItemStack item) {
        Object raw = NbtUtil.getInst().getItemTagWrapper(item).get("SX-Attribute");
        if (!(raw instanceof Map)) throw new AssertionError("SX-Attribute is not a compound: " + raw);
        return new HashMap<>((Map<String, Object>) raw);
    }
    private void write(ItemStack item, Map<String, Object> values) {
        NbtUtil.ItemWrapper wrapper = NbtUtil.getInst().getItemTagWrapper(item);
        wrapper.set("SX-Attribute", values); wrapper.save();
    }
    private long number(ItemStack item, String key) { return ((Number) state(item).get(key)).longValue(); }
    private List<String> lore(ItemStack item) {
        List<String> lines = new ArrayList<>();
        for (String line : item.getItemMeta().getLore()) lines.add(ChatColor.stripColor(line));
        return lines;
    }
    /** 跨插件只反射公开入口；探针不打包生产类，避免出现与被测插件不同的单例。 */
    private Object invoke(String type, String method, Class<?>[] types, Object... values) throws Exception {
        Class<?> target = Class.forName("top.maplex.sxitemaction." + type, true, getServer().getPluginManager().getPlugin("SX-Item-Action").getClass().getClassLoader());
        return target.getMethod(method, types).invoke(target.getField("INSTANCE").get(null), values);
    }
    private Object inspect(ItemStack item) throws Exception { return invoke("lifecycle.ItemLifecycle", "inspect", new Class[]{ItemStack.class, Player.class}, item, player); }
    private boolean invalid(ItemStack item) throws Exception { Object s = inspect(item); return (Boolean) s.getClass().getMethod("getInvalid").invoke(s); }
    private void scan() throws Exception { invoke("listener.ItemLifecycleListener", "scanPlayer", new Class[]{Player.class}, player); }
    private void damage(ItemStack item, int amount) {
        PlayerItemDamageEvent event = new PlayerItemDamageEvent(player, item, amount);
        getServer().getPluginManager().callEvent(event);
        check(event.isCancelled(), "vanilla damage cancelled");
    }
    // 0 号快捷栏属于测试服 RPGInventory 的虚拟武器槽，使用普通 8 号槽避免占位物影响空槽断言。
    private void held(ItemStack item) { player.getInventory().setHeldItemSlot(8); player.getInventory().setItemInMainHand(item); }
    private void run(String command) { check(Bukkit.dispatchCommand(player, command), "command " + command); }

    private void suite() throws Exception {
        player.getInventory().clear(); player.getEnderChest().clear(); player.setItemOnCursor(null); breaks = 0;
        ItemStack item = spawn("SXIALifecycle");
        check(number(item, "Durability") == 50 && number(item, "MaxDurability") == 100, "spawn NBT 50/100");
        long expiry = number(item, "ExpiresAt");
        check(expiry > System.currentTimeMillis() + 3000000, "duration saved as absolute milliseconds");
        String date = SXItem.getSdf().get().format(new Date(expiry));
        check(lore(item).equals(Arrays.asList("before", "D 50/100", "mixed D 50/100 / T " + date, "", "after")), "whole-line duplicate inline native date lore");
        check(item.getDurability() == item.getType().getMaxDurability() / 2, "proportional vanilla durability");
        Map<String, Object> root = state(item); root.put("ProbeOther", "retained"); root.put("ProbeArray", new int[]{2,4,6}); write(item, root);
        damage(item, 3);
        check(number(item, "Durability") == 47 && "retained".equals(state(item).get("ProbeOther")), "damage preserves shared attribute fields");
        check(lore(item).get(1).equals("D 47/100") && lore(item).get(2).contains("D 47/100"), "all lore occurrences refresh");
        cancelDamage = true; damage(item, 8); cancelDamage = false;
        check(number(item, "Durability") == 47, "earlier cancellation preserves durability");
        ItemStack nativeItem = new ItemStack(Material.IRON_SWORD);
        org.bukkit.inventory.meta.ItemMeta nativeMeta = nativeItem.getItemMeta();
        nativeMeta.setLore(Arrays.asList("耐久度: 5/10")); nativeItem.setItemMeta(nativeMeta);
        damage(nativeItem, 1);
        check(lore(nativeItem).get(0).contains("4/10"), "native SX-Attribute durability remains active for other items");
        invoke("lifecycle.ItemLifecycle", "setDurability", new Class[]{ItemStack.class, int.class, Player.class}, item, 7, player);
        damage(item, 2);
        // 改变生成模板并通过 SX 的真实 update 入口更新；恢复模板防止后续用例串状态。
        File fixture = new File(SXItem.getInst().getDataFolder(), "Item/SXIALifecycleProbe.yml");
        byte[] original = java.nio.file.Files.readAllBytes(fixture.toPath());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(fixture);
        config.set("SXIALifecycle.Name", "updated-name");
        config.set("SXIALifecycle.Lore", Arrays.asList("after-update", "<l:TimeLimit>", "<l:CustomDurability>"));
        config.set("SXIALifecycle.CustomDurability.max", 999);
        config.set("SXIALifecycle.TimeLimit.duration", "7d");
        config.set("SXIALifecycle.NBT.SX-Attribute.ProbeOther", "template-updated");
        try {
            config.save(fixture); SXItem.getItemManager().loadItemData(); SXItem.getItemManager().updateItem(player, item);
            check("updated-name".equals(item.getItemMeta().getDisplayName()), "SX update applies changed template name");
            check("template-updated".equals(state(item).get("ProbeOther")), "SX update preserves new shared attributes");
            check(lore(item).equals(Arrays.asList("after-update", "T " + date, "D 5/7")), "SX update applies new lore order without duplication");
        } finally { java.nio.file.Files.write(fixture.toPath(), original); SXItem.getItemManager().loadItemData(); }
        check(number(item, "Durability") == 5 && number(item, "MaxDurability") == 7 && number(item, "ExpiresAt") == expiry, "SX update retains lifecycle state");
        check(!lore(item).toString().contains("SXIA:") && Collections.frequency(lore(item), "D 5/7") >= 1, "SX update resolves lore markers");
        held(item); run("si reload"); item = player.getInventory().getItemInMainHand();
        check(number(item, "Durability") == 5 && number(item, "ExpiresAt") == expiry, "SX reload retains state");
        check(!lore(spawn("SXIALifecycle")).toString().contains("SXIA:"), "reload re-registers whole-line variables");
        ItemStack random = spawn("RandomDurabilitySword");
        check(number(random, "Durability") * 2 == number(random, "MaxDurability"), "native local Random and nested calculation");
        ItemStack token = spawn("SXDateToken");
        check(number(token, "ExpiresAt") > System.currentTimeMillis() + 6L*86400000L, "native t date initialization");
        inspect(token); check(!lore(token).toString().contains("<"), "native time display resolved");
        ItemStack legacy = spawn("LegacySword");
        check(number(legacy, "Durability") == 5000 && number(legacy, "MaxDurability") == 10000, "legacy lore initializes NBT");
        ItemStack empty = spawn("SXIAEmpty");
        check(lore(empty).equals(Arrays.asList("before", "after")), "absent feature removes whole-line marker");
        ItemStack imported = spawn("ImportedBreakSword");
        check(number(imported, "Durability") == 2, "documented import fixture spawns");
        held(imported); damage(player.getInventory().getItemInMainHand(), 2);
        check(breaks == 1, "break event emitted once");
        ItemStack malformed = spawn("SXIALifecycle"); Map<String,Object> bad = state(malformed); bad.remove("Durability"); write(malformed, bad);
        check(invalid(malformed), "partial NBT rejected without reinitializing");
        ItemStack invalidBefore = malformed.clone(); SXItem.getItemManager().updateItem(player, malformed);
        check(malformed.equals(invalidBefore), "invalid-state update cancelled");
        check(invalid(spawn("SXIABadDate")), "invalid calendar date rejected");
        check(invalid(spawn("SXIABadDuration")), "overflow duration rejected");
        check(invalid(spawn("SXIABothExpiry")), "ambiguous expiry config rejected");
        // 独立槽位在同一周期内必须分别判断，不能按 SX 物品编号去重。
        player.getInventory().clear(); timed = spawn("SXIALifecycle"); later = spawn("SXIALifecycle");
        Map<String,Object> one = state(timed); one.put("ExpiresAt", System.currentTimeMillis() + 1500); write(timed, one);
        player.getInventory().setItem(7, timed); player.getInventory().setItem(6, later);
        player.getEnderChest().setItem(0, timed.clone());
        player.setItemOnCursor(timed.clone());
    }
    // TabooLib 的玩家命令可能排队执行；客户端等待命令完成消息后才调用以下断言，不能同 tick 推断结果。
    private void commands() throws Exception {
        player.getInventory().clear(); ItemStack item = spawn("SXIALifecycle");
        commandExpiry = number(item, "ExpiresAt"); held(item);
    }
    private void repairCheck() {
        ItemStack item = player.getInventory().getItemInMainHand();
        check(number(item, "Durability") == 100 && number(item, "ExpiresAt") == commandExpiry, "repair keeps expiry");
    }
    private void durabilityCheck() {
        ItemStack item = player.getInventory().getItemInMainHand();
        check(number(item, "Durability") == 7 && number(item, "MaxDurability") == 7, "durability command updates NBT");
        commandBefore = item.clone();
    }
    private void atomicCheck() { check(commandBefore.equals(player.getInventory().getItemInMainHand()), "invalid duration command is atomic"); }
    private void limitCheck() {
        long expiry = number(player.getInventory().getItemInMainHand(), "ExpiresAt");
        check(Math.abs(expiry - System.currentTimeMillis() - 120000) < 5000, "timelimit command persists requested duration");
    }
    private void timedCheck() throws Exception {
        scan();
        check(player.getInventory().getItem(7) == null && player.getInventory().getItem(6) != null, "per-slot expiry preserves same-ID live item");
        check(player.getEnderChest().getItem(0) == null && player.getItemOnCursor().getType() == Material.AIR, "ender chest and cursor expire");
        ItemStack expired = timed.clone();
        PlayerInteractEvent interact = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, expired, null, org.bukkit.block.BlockFace.UP);
        Bukkit.getPluginManager().callEvent(interact);
        check(interact.isCancelled(), "expired use blocked");
        Item dropped = player.getWorld().dropItem(player.getLocation(), expired);
        org.bukkit.event.entity.EntityPickupItemEvent pickup = new org.bukkit.event.entity.EntityPickupItemEvent(player, dropped, 0);
        Bukkit.getPluginManager().callEvent(pickup);
        check(pickup.isCancelled() && !dropped.isValid(), "expired dropped entity removed");
    }
    private void real() throws Exception {
        player.getInventory().clear(); player.setGameMode(GameMode.SURVIVAL); player.setFoodLevel(20); player.setHealth(20); breaks = 0;
        held(spawn("SXIARealPickaxe")); player.getInventory().setItem(1, spawn("SXIARealPickaxe"));
        Location origin = player.getLocation(); Block target = origin.clone().add(2, 0, 0).getBlock(); savedBlock = target.getState(); target.setType(Material.STONE);
        say("DIG " + target.getX() + " " + target.getY() + " " + target.getZ());
    }
    private void realCheck() throws Exception {
        try {
            check(player.getInventory().getItemInMainHand().getType() == Material.AIR, "real mining consumes final durability");
            check(number(player.getInventory().getItem(1), "Durability") == 1, "real break preserves other same-ID slot");
            check(breaks == 1, "real mining emits exactly one break");
        } finally { restoreBlock(); }
    }
    private void save() throws Exception {
        player.getInventory().clear(); ItemStack durable = spawn("SXIALifecycle"); damage(durable, 9); player.getInventory().setItem(8, durable);
        ItemStack expiredOffline = spawn("SXIALifecycle"); Map<String,Object> values = state(expiredOffline); values.put("ExpiresAt", System.currentTimeMillis()+3000); write(expiredOffline, values); player.getInventory().setItem(7, expiredOffline);
        getConfig().set("expiry", number(durable, "ExpiresAt")); saveConfig(); player.saveData();
    }
    private void verify() throws Exception {
        ItemStack item = player.getInventory().getItem(8);
        check(number(item, "Durability") == 41 && number(item, "ExpiresAt") == getConfig().getLong("expiry"), "restart preserves durability and deadline");
        check(player.getInventory().getItem(7) == null, "offline elapsed item removed after restart");
        player.getInventory().clear(); player.getEnderChest().clear(); player.setItemOnCursor(null);
    }
}
