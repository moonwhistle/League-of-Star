-- KEYS[1]: pending timeout ZSET
-- KEYS[2]: processing timeout ZSET
-- ARGV[1]: matchId
-- ARGV[2]: nowMillis
-- ARGV[3]: processingExpireAtMillis

local score = redis.call('ZSCORE', KEYS[1], ARGV[1])

if not score then
    return 0
end

if tonumber(score) > tonumber(ARGV[2]) then
    return 0
end

local removed = redis.call('ZREM', KEYS[1], ARGV[1])
if removed == 1 then
    redis.call('ZADD', KEYS[2], ARGV[3], ARGV[1])
    return 1
end

return 0
