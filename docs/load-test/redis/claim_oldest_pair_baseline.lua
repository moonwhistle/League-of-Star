-- Baseline: claim exactly one FIFO pair per Redis round trip.
-- KEYS[1]: FIFO matching queue ZSET

local entries = redis.call('ZRANGE', KEYS[1], 0, 1, 'WITHSCORES')
if #entries < 4 then
    return {}
end

redis.call('ZREM', KEYS[1], entries[1], entries[3])
return {entries[1], entries[2], entries[3], entries[4]}
