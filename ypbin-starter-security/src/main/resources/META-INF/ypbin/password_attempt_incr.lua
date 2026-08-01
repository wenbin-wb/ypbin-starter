-- 密码错误计数递增 Lua 脚本（原子执行）
-- KEYS[1] 计数键
-- ARGV[1] 锁定窗口秒数（仅首次失败时设置过期）
-- 返回：递增后的失败次数
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return count
