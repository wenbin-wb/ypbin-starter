-- 固定窗口限流 Lua 脚本（原子执行）
-- KEYS[1] 限流键
-- ARGV[1] 窗口秒数
-- 返回：当前窗口内累计次数
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return current
