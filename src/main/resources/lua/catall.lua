local cat = 'sa:' .. ARGV[1] .. ':cat:' .. ARGV[2]
local j = 'sa:' .. ARGV[1] .. ':hb:'
local out = {}
local cache = {}
for _, ref in ipairs(redis.call('SMEMBERS', cat)) do
  local sys = string.sub(ref, 0, string.find(ref, ':') - 1)
  local t = cache[sys]
  if not t then
    t = redis.call("TTL", j .. sys)
    cache[sys] = t
  end
  if (t >= 0) then
    table.insert(out, ref)
  else
    redis.call('SREM', cat, ref)
  end
end
return out
