local result = redis.call('setnx',KEYS[1],ARGV[1])
if(result == 1) then
    local result = redis.call('pexpire',KEYS[1],ARGV[2])
    return result
else
    return result
end