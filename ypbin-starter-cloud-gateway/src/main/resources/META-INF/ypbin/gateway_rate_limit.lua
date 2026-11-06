-- 网关固定窗口计数（原子执行）：INCR 与 EXPIRE 必须在同一脚本内完成。
-- KEYS[1] 计数键（键名已包含窗口序号，窗口切换即换键）
-- ARGV[1] 本窗口剩余秒数 + 1（调用方保证 >= 1）
--
-- 仅在「首次自增」或「键存在但没有过期时间」时设置过期：
-- 前者是正常路径；后者是自愈路径——若键因主从切换/持久化恢复/人工写入而丢失 TTL，
-- INCR 不会再等于 1，过期就永远不会被设置，该维度键将永久驻留且计数只增不减（永久限流）。
local current = redis.call('INCR', KEYS[1])
if current == 1 or redis.call('TTL', KEYS[1]) == -1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return current
