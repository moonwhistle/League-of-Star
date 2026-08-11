-- KEYS[1]: MatchJob Stream
-- KEYS[2...]: user status keys (A, B per job)
-- ARGV[1]: consumer group
-- ARGV[2]: status TTL seconds
-- ARGV[3]: job count
-- ARGV[4...]: Stream message IDs

local group = ARGV[1]
local statusTtl = ARGV[2]
local jobCount = tonumber(ARGV[3])

-- 모든 메시지가 아직 PEL에 있는지 먼저 확인해 일부만 확정되는 상태를 방지한다.
for index = 1, jobCount do
    local messageId = ARGV[index + 3]
    local pending = redis.call('XPENDING', KEYS[1], group, messageId, messageId, 1)
    if #pending == 0 then
        return 0
    end
end

local messageIds = {}
for index = 1, jobCount do
    local firstStatusKeyIndex = 2 + (index - 1) * 2
    redis.call('SET', KEYS[firstStatusKeyIndex], 'FOUND', 'EX', statusTtl)
    redis.call('SET', KEYS[firstStatusKeyIndex + 1], 'FOUND', 'EX', statusTtl)
    messageIds[index] = ARGV[index + 3]
end

local acknowledged = redis.call('XACK', KEYS[1], group, unpack(messageIds))
if acknowledged == jobCount then
    redis.call('XDEL', KEYS[1], unpack(messageIds))
end
return acknowledged
