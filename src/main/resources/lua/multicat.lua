local k = 'sa:' .. ARGV[1] .. ':cat:' .. ARGV[2]
local j = 'sa:' .. ARGV[1] .. ':hb'
local cnt = tonumber(ARGV[3])
local t = redis.call('TIME')[1] - 20
local res = {}
local cache = {}
while (#res < cnt) do
    local act = redis.call('SRANDMEMBER', k)
    if not act then
        return res
    end
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
        table.insert(res, act)
    else
        redis.call('SREM', k, act)
    end
end
return res