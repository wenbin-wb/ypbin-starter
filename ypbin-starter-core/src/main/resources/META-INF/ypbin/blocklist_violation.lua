-- 记录一次违规；达到阈值时写入自动封禁键（带 TTL）。
-- KEYS[1] 违规计数键, KEYS[2] 自动封禁键
-- ARGV[1] 窗口秒数, ARGV[2] 阈值, ARGV[3] 封禁原因, ARGV[4] 封禁秒数
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
if count >= tonumber(ARGV[2]) then
    redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[4])
    redis.call('DEL', KEYS[1])
end
return count
