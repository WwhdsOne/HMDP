-- 1. 获取参数
-- 1.1 获取用户ID
local userId = ARGV[2]
-- 1.2 获取优惠卷ID
local voucherId = ARGV[1]

-- 2. 数据key
-- 2.1 库存key
local stockKey = "seckill:stock:" .. voucherId
-- 2.1 订单key
local orderKey = "seckill:order:" .. voucherId

-- 3. 脚本业务
-- 3.1 判断库存是否充足
if (tonumber(redis.call("get", stockKey)) <= 0) then
    return 1
end
-- 3.2 判断用户是否已经抢购过
if (redis.call("sismember",orderKey,userId) == 1) then
    return 2
end
-- 3.3 减库存
redis.call("decr", stockKey)
-- 3.4 下单保存用户信息
redis.call("sadd", orderKey, userId)

return 0
