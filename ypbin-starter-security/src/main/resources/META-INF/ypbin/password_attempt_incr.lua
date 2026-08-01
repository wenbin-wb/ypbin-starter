-- 密码错误计数递增 Lua 脚本（原子执行）
-- KEYS[1] 计数键
-- ARGV[1] 观察窗口秒数（首次失败时设置，作为错误累计窗口）
-- ARGV[2] 锁定阈值（达到即锁定）
-- ARGV[3] 锁定时长秒数（达到阈值时刷新为满额锁定，防止惩罚被观察窗口 TTL 提前放行）
-- 返回：递增后的失败次数
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
if count >= tonumber(ARGV[2]) then
    -- 达到锁定阈值：用满额锁定时长覆盖剩余 TTL，确保从锁定时刻起惩罚足额生效
    redis.call('EXPIRE', KEYS[1], ARGV[3])
end
return count
