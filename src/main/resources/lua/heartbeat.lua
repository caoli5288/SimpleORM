local k = 'sa:' .. ARGV[1] .. ':hb'
local sys = ARGV[2]
local old = redis.call('HGET', k, sys)
if not old then
    return 0
end
local t = redis.call('TIME')[1]
if (t - old > 20) then
    return 0
end
redis.call('HSET', k, sys, t)
return 1
