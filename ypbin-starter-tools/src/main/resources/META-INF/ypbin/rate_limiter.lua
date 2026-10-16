-- 固定窗口限流 Lua 脚本（原子执行）
-- KEYS[1] 限流键
-- ARGV[1] 窗口秒数（调用方保证 >= 1，见 RateLimitAspect 的 fail-fast 校验）
-- 返回：当前窗口内累计次数（语义不变）
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
elseif redis.call('TTL', KEYS[1]) == -1 then
    -- TTL 兜底：仅靠「INCR 结果为 1 时设过期」不够。若键因异常原因已存在却没有过期时间
    -- （例如历史版本脚本留下的残留键、人工 SET 创建的键、主从切换/持久化恢复中 TTL 丢失），
    -- 那么 INCR 永远不等于 1，EXPIRE 永远不会再执行 ⇒ 键永久驻留、计数只增不减，
    -- 该接口/用户被永久限流。故键存在且 TTL == -1（无过期）时补一次 EXPIRE 自愈。
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return current
