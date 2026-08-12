-- KEYS[1]: FIFO matching queue ZSET
-- KEYS[2]: MatchJob Stream
-- ARGV[1]: maximum number of users to pair (even number, at least 2)

local maxUsers = tonumber(ARGV[1])
if not maxUsers or maxUsers < 2 or maxUsers % 2 ~= 0 then
    return redis.error_reply('maxUsers must be an even number greater than or equal to 2')
end

local entries = redis.call('ZRANGE', KEYS[1], 0, maxUsers - 1, 'WITHSCORES')
local userCount = math.floor(#entries / 2)
local pairedUserCount = userCount - (userCount % 2)

if pairedUserCount < 2 then
    return 0
end

local jobCount = 0
for userIndex = 1, pairedUserCount, 2 do
    local firstEntryIndex = (userIndex - 1) * 2 + 1
    local secondEntryIndex = userIndex * 2 + 1
    local userA = entries[firstEntryIndex]
    local userAEntryTime = entries[firstEntryIndex + 1]
    local userB = entries[secondEntryIndex]
    local userBEntryTime = entries[secondEntryIndex + 1]

    redis.call('XADD', KEYS[2], '*',
            'userA', userA,
            'userAEntryTime', userAEntryTime,
            'userB', userB,
            'userBEntryTime', userBEntryTime)
    redis.call('ZREM', KEYS[1], userA, userB)
    jobCount = jobCount + 1
end

return jobCount
