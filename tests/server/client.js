/** 真实协议客户端驱动探针；不启动/停止服务端，所有结果都来自本次连接。 */
const mineflayer = require('mineflayer')
const fs = require('node:fs')
const assert = require('node:assert/strict')
const phase = process.argv[2] || 'exercise'
const output = process.argv[3]
const messages = []
const bot = mineflayer.createBot({host: '127.0.0.1', port: 25565, username: 'SXIALifeTest', auth: 'offline', version: '1.21.11'})
let failure
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
bot.on('messagestr', text => { messages.push(text); process.stdout.write(text + '\n'); if (text.includes('[SXIA-PROBE] FAIL')) failure = new Error(text) })
bot.on('error', error => { failure = error })
bot.on('kicked', reason => { failure = new Error(JSON.stringify(reason)) })
/** 每步有独立截止时间；读取消息只是同步信号，断言本身在服务器主线程执行。 */
async function until(condition, timeout = 30000) {
  const end = Date.now() + timeout
  while (!condition()) { if (failure) throw failure; if (Date.now() > end) throw new Error('Timed out waiting for server evidence'); await pause(100) }
}
async function probe(name) {
  const from = messages.length
  bot.chat('/sxiaprobe ' + name)
  await until(() => messages.slice(from).some(text => text.includes('[SXIA-PROBE] DONE ' + name)))
}
async function command(text, response) {
  const from = messages.length
  bot.chat('/' + text)
  await until(() => messages.slice(from).some(line => line.includes(response)))
}
async function main() {
  await new Promise((resolve, reject) => { bot.once('spawn', resolve); bot.once('error', reject); setTimeout(() => reject(new Error('Spawn timeout')), 60000).unref() })
  // 命令与 NBT 检查无需等待全部周边区块；真实挖掘阶段单独等待目标方块同步。
  await pause(3000)
  if (phase === 'verify') {
    await probe('verify')
  } else {
    await probe('suite')
    await pause(2500)
    await probe('timedcheck')
    assert(messages.some(text => text.includes('你的装备耐久耗尽，已损坏')), 'imported Kether break script did not execute')
    await probe('commands')
    await command('sxia repair', '已更新主手物品'); await probe('repaircheck')
    await command('sxia durability 7', '已更新主手物品'); await probe('durabilitycheck')
    await command('sxia timelimit invalid', '时长格式'); await probe('atomiccheck')
    await command('sxia timelimit 2m', '已更新主手物品'); await probe('limitcheck')
    bot.chat('/sxia selftest')
    await until(() => messages.some(text => text.includes('自检通过：Kether、Fluxon、动作链与事件取消均正常')))
    await probe('real')
    const location = messages.findLast(text => text.includes('[SXIA-PROBE] DIG ')).match(/DIG (-?\d+) (-?\d+) (-?\d+)/)
    const point = bot.entity.position.clone(); point.set(Number(location[1]), Number(location[2]), Number(location[3]))
    bot.setQuickBarSlot(8)
    await pause(700)
    await until(() => bot.blockAt(point)?.name === 'stone')
    const block = bot.blockAt(point)
    assert(block && block.name === 'stone', 'real mining fixture missing')
    await bot.dig(block)
    await pause(1200)
    await probe('realcheck')
    assert.equal(messages.filter(text => text.includes('SXIA_REAL_BREAK')).length, 1, 'real break action should execute once')
    await probe('save')
  }
  fs.writeFileSync(output, JSON.stringify({status: 'passed', phase, messages}, null, 2))
  bot.quit('lifecycle testing complete')
}
main().then(() => { setTimeout(() => process.exit(0), 500) }).catch(error => {
  fs.writeFileSync(output, JSON.stringify({status: 'failed', phase, error: error.stack, messages}, null, 2))
  process.stderr.write(error.stack + '\n'); bot.quit(); setTimeout(() => process.exit(1), 500)
})
