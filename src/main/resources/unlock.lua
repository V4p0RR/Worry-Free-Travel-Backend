--比较锁中标识是否与线程中的一致

if (redis.call('get', KEYS[1]) == ARGV[1]) then
    --如果一致则删除锁
    return redis.call('del', KEYS[1])
else
    --如果不一致则返回0
    return 0
end
