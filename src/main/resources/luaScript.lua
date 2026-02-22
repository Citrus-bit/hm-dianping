-- 锁的key
local lockKey = KEYS[1]
-- 线程标识
local threadId = ARGV[1]

-- 获取锁中的线程标识
local lockValue = redis.call("GET", lockKey)

-- 判断是否与当前线程标识一致
if lockValue == threadId then
    -- 一致，释放锁
    return redis.call("DEL", lockKey)
end

return 0
