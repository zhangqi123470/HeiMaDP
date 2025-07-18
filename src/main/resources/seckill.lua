--传递Redis参数到lua脚本中
local voucherID=ARGV[1]
local userID=ARGV[2]
--判断秒杀券库存是否充足
local stockKey="seckill:stock"..voucherID
local orderKey="seckill:order"..voucherID
--从redis中找对应voucherID的券是否还有库存
if(tonumber(redis.call('get',stockKey))<0) then
    return 1
end
--从redis中找对应userID的下单信息中是否有对应的voucherID
if(redis.call('sismember',orderKey,userID)==1) then
    return 2
end
--扣减库存
redis.call('incrby',stockKey,-1)
--保存用户下单信息到Redis中
redis.call('sadd',orderKey,userID)

return 0