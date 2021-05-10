local cat = 'sa:' .. ARGV[1] .. ':cat:' .. ARGV[2]
local j = 'sa:' .. ARGV[1] .. ':hb:'
local count = tonumber(ARGV[3])
local out = {}
local cache = {}
while #out < count do
    local act = redis.call('SRANDMEMBER', cat)
    if not act then
        return out
    end
    local sys = string.sub(act, 0, string.find(act, ':') - 1)
    local t = cache[sys]
    if not t then
        t = redis.call("TTL", j .. sys)
        cache[sys] = t
    end
    if (t >= 0) then
        table.insert(out, act)
    else
        redis.call('SREM', cat, act)
    end
end
return out