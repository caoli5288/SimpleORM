local k = 'sa:' .. ARGV[1] .. ':hb:' .. ARGV[2]
local r = redis.call('SET', k, '', 'XX', 'EX', '20')
if r == 'OK' then
    return 1
end
return 0