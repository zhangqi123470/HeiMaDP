-- Seckill Lua Script
-- Parameters: ARGV[1] = voucherId, ARGV[2] = userId

local voucherId = ARGV[1]
local userId = ARGV[2]

-- Build Redis keys
local stockKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. voucherId

-- 1. Check stock
local stock = redis.call('get', stockKey)
if stock == nil then
    return 1
end

local stockNum = tonumber(stock)
if stockNum == nil or stockNum <= 0 then
    return 1
end

-- 2. Check if user already ordered
local isMember = redis.call('sismember', orderKey, userId)
if isMember == 1 then
    return 2
end

-- 3. Decrease stock
redis.call('incrby', stockKey, -1)

-- 4. Record user order
redis.call('sadd', orderKey, userId)

return 0