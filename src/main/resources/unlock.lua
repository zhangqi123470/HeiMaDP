
--比较当前线程的标识和锁中的提示是否一致
local id =redis.call('get',KEYS[1])
local threadId=ARGV[1]
--如果id和redis中的锁id一致的话则可以上锁
if(id==threadId) then
    return redis.call('del',KEYS[1])
end
return 0
--释放锁
