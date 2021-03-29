local k = 'sa:' .. ARGV[1] .. ':cat:' .. ARGV[2]
local j = 'sa:' .. ARGV[1] .. ':hb'
local t = redis.call('TIME')[1] - 20
local ret = {}
local cache = {}
for _, act in ipairs(redis.call('SMEMBERS', k)) do
    local sys = string.sub(act, 0, string.find(act, ':') - 1)
    local _t = cache[sys]
    if (not _t) then
        _t = redis.call("HGET", j, sys)
        if (not _t) then
            _t = 0
        else
            _t = tonumber(_t)
        end
        cache[sys] = _t
    end
    if (_t >= t) then
        table.insert(ret, act)
    else
        redis.call('SREM', k, act)
    end
end
return ret