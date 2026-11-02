-- 幂等占位 Lua 脚本（原子执行）
-- KEYS[1] 幂等键
-- ARGV[1] 占位属主（调用方唯一标识；失败释放时据此核对，避免误删他人的占位）
-- ARGV[2] 过期秒数
-- 返回：1 表示占位成功（首次），0 表示键已存在（重复）
if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then
    return 1
else
    return 0
end
