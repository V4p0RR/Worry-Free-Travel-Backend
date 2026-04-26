-- 判断秒杀券资格的Lua脚本（Kafka版）
-- 与seckill.lua的区别：不写Redis Stream，消息由Java代码发送Kafka

local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 1.判断库存是否充足 将字符串转为数字类型再比较
local stock = redis.call('get', stockKey)

if (not stock or tonumber(stock) <= 0) then
    --库存不足 返回1
    return 1
end

-- 2. 判断是否已下单
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2 -- 重复下单
end

-- 3. 扣减库存
redis.call('incrby', stockKey, -1)

-- 4. 记录下单用户
redis.call('sadd', orderKey, userId)

-- 5. 返回0代表校验通过，Java代码会负责发送Kafka消息
return 0
