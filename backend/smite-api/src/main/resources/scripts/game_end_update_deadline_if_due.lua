-- KEYS[1]: game end pending ZSET
-- ARGV[1]: gameRoomId
-- ARGV[2]: nowMillis
-- ARGV[3]: naturalDeathAtMillis
-- Used by the scheduler after DB recheck. Only members that are still due are
-- moved to the recalculated deadline.

local currentScore = redis.call('ZSCORE', KEYS[1], ARGV[1])

if currentScore == false then
    return 0
end

if tonumber(currentScore) <= tonumber(ARGV[2]) then
    redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
    return 1
end

return 0
