-- 按属主原子删除（compare-and-delete）
-- 分布式锁释放与幂等占位释放共用：只有值仍等于调用方标识时才删除，绝不误删他人持有的键。
-- KEYS[1] 键
-- ARGV[1] 持有者 / 占位属主标识（加锁或占位时写入的唯一值）
-- 返回：1 表示删除成功，0 表示键不存在或持有者不匹配
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
else
    return 0
end
