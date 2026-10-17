-- 分布式锁续期 Lua 脚本（原子执行）
-- KEYS[1] 锁键
-- ARGV[1] 锁持有者标识（加锁时写入的唯一值）
-- ARGV[2] 续期后的 TTL（毫秒）
-- 返回：1 表示续期成功；0 表示锁不存在或持有者不匹配（绝不续他人的锁）
--
-- 必须「GET 比对 + PEXPIRE」在同一次脚本执行内完成：拆成两条命令就是「先查再踩」——
-- 查询时锁还是自己的，中间锁过期、他人抢到并写入了新的 owner，随后自己的 PEXPIRE 会把
-- 他人的锁续上，两个持有者同时自以为持锁。锁一旦不属于自己（或已被删除），这里绝不重建。
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('PEXPIRE', KEYS[1], ARGV[2])
else
    return 0
end
