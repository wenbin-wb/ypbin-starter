-- 幂等占位 Lua 脚本（原子执行）
-- KEYS[1] 幂等键
-- ARGV[1] 过期秒数
-- 返回：1 表示占位成功（首次），0 表示键已存在（重复）
if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[1]) then
    return 1
else
    return 0
end
