-- Seckill Lua Script
-- Parameters: ARGV[1] = voucherId, ARGV[2] = userId

local voucherId = ARGV[1]
local userId = ARGV[2]
-- 传递订单id
-- 实现在lua脚本中将订单推到消息队列中的功能
local orderId=ARGV[3]
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
-- 发送订单消息到消息队列中
redis.call('Xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)

return 0