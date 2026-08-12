-- Event-driven FIFO matching prototype.
-- KEYS[1]: waiting queue ZSET
-- KEYS[2]: durable match job Stream
-- ARGV[1]: user ID
-- ARGV[2]: queue order score

if redis.call('ZSCORE', KEYS[1], ARGV[1]) then
    return {'DUPLICATE'}
end

redis.call('ZADD', KEYS[1], 'NX', ARGV[2], ARGV[1])

if redis.call('ZCARD', KEYS[1]) < 2 then
    return {'QUEUED'}
end

local entries = redis.call('ZRANGE', KEYS[1], 0, 1, 'WITHSCORES')
redis.call('ZREM', KEYS[1], entries[1], entries[3])

local jobId = redis.call(
    'XADD', KEYS[2], '*',
    'userA', entries[1],
    'entryA', entries[2],
    'userB', entries[3],
    'entryB', entries[4]
)

return {'MATCHED', jobId, entries[1], entries[2], entries[3], entries[4]}
