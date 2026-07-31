-- 分布式锁释放 Lua 脚本（原子执行）
-- KEYS[1] 锁键
-- ARGV[1] 锁持有者标识（加锁时写入的唯一值）
-- 返回：1 表示释放成功，0 表示锁不存在或持有者不匹配（不误删他人的锁）
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
else
    return 0
end
