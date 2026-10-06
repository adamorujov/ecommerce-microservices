local value = redis.call('GET', KEYS[1])

if not value then
    return 'NOT_FOUND'
end

local data = cjson.decode(value)

if data.status ~= 'ACTIVE' then
    return data.status
end

data.status = 'USED'

redis.call(
    'SET',
    KEYS[1],
    cjson.encode(data)
)

return 'OK'