-- 记录一次违规；达到阈值时写入自动封禁键（带 TTL）。
-- KEYS[1] 违规计数键, KEYS[2] 自动封禁键
-- ARGV[1] 窗口秒数, ARGV[2] 阈值, ARGV[3] 封禁原因, ARGV[4] 封禁秒数
--
-- 封禁写入使用 SET NX：已有人工封禁（通常更长、原因更可信）时保留原值，
-- 自动封禁不能把人工封禁改短，也不能因反复达标无限续期。
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
if count >= tonumber(ARGV[2]) then
    redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[4], 'NX')
    redis.call('DEL', KEYS[1])
end
return count
