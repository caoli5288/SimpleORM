local cat = 'sa:' .. ARGV[1] .. ':cat:' .. ARGV[2]
local j = 'sa:' .. ARGV[1] .. ':hb:'
while true do
  local ref = redis.call('SRANDMEMBER', cat)
  if not ref then
    return nil
  end
  local sys = string.sub(ref, 0, string.find(ref, ':') - 1)
  local t = redis.call('TTL', j .. sys)
  if t >= 0 then
    return ref
  else
    redis.call('SREM', cat, ref)
  end
end
