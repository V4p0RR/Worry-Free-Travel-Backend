--判断秒杀券资格的lua脚本

--参数列表
-- 优惠券id 要拼接为key
local voucherId = ARGV[1]
--用户id 判断一人一单
local userId = ARGV[2]
--订单id
local orderId = ARGV[3]

--生成key
--优惠卷key 用..拼接
local stockKey = 'seckill:stock:' .. voucherId
--订单的key 里面保存购买过此优惠券的用户id
local orderKey = 'seckill:order:' .. voucherId

--业务逻辑
--1.判断库存是否充足 将字符串转为数字类型再比较
local stock = redis.call('get', stockKey)

if (not stock or tonumber(stock) <= 0) then
  --库存不足 返回1
  return 1
end

--2.判断用户是否购买过 用sismember
if (redis.call('sismember', orderKey, userId) == 1) then
  --如果存在，说明用户已经购买过 返回2
  return 2
end

--3.扣减库存(预) incrby -1
redis.call('incrby', stockKey, -1)

--4.下单(保存userId) sadd userId
redis.call('sadd', orderKey, userId)

--5.发送消息到消息队列
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)

--6.返回0 代表秒杀成功
return 0
