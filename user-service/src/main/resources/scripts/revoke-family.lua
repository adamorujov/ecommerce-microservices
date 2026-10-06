local familyKey = KEYS[1]

local tokenHashes =
    redis.call('SMEMBERS', familyKey)

for _, tokenHash in ipairs(tokenHashes) do

    local tokenKey =
        'refresh:' .. tokenHash

    local value =
        redis.call('GET', tokenKey)

    if value then

        local ttl =
            redis.call('TTL', tokenKey)

        local data =
            cjson.decode(value)

        data.status = 'REVOKED'

        redis.call(
            'SET',
            tokenKey,
            cjson.encode(data)
        )

        if ttl > 0 then
            redis.call('EXPIRE', tokenKey, ttl)
        end
    end
end

return 'OK'