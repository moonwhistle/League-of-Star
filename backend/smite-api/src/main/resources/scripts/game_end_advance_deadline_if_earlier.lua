-- KEYS[1]: game end pending ZSET
-- ARGV[1]: gameRoomId
-- ARGV[2]: naturalDeathAtMillis
-- Used after a failed SMITE. Missing members may be inserted, but existing members
-- are only moved earlier and never pushed later.

local currentScore = redis.call('ZSCORE', KEYS[1], ARGV[1])
local nextScore = tonumber(ARGV[2])

if currentScore == false or tonumber(currentScore) > nextScore then
    redis.call('ZADD', KEYS[1], nextScore, ARGV[1])
    return 1
end

return 0
