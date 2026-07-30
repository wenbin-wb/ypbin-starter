-- nonce 防重放 Lua 脚本（原子执行）
-- KEYS[1] nonce 键
-- ARGV[1] 过期秒数
-- 返回：1 首次使用（放行），0 已存在（重放）
if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[1]) then
    return 1
else
    return 0
end
