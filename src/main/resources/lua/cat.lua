local k = 'sa:' .. ARGV[1] .. ':cat:' .. ARGV[2]
local j = 'sa:' .. ARGV[1] .. ':hb'
local t = redis.call('TIME')[1] - 20
while (true) do
    local act = redis.call('SRANDMEMBER', k)
    if (not act) then
        return nil
    end
    local sys = string.sub(act, 0, string.find(act, ':') - 1)
    local _t = redis.call('HGET', j, sys)
    if (not _t) then
        return nil
    end
    if (tonumber(_t) >= t) then
        return act
    else
        redis.call('SREM', k, act)
    end
end