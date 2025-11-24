package no.kobler.rtb.smoothing;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Redis-backed SmoothingService implementation using an atomic Lua script.
 * <p>
 * Token bucket fields are stored in a Redis hash per campaign key:
 * HKEY = smoothing:bucket:{campaignId}
 * fields: tokens (float), last (epoch seconds)
 * <p>
 * The Lua script does:
 * - read tokens,last
 * - compute refill = min(capacity, tokens + elapsed * refillRate)
 * - if refill >= amount -> consume amount, write tokens & last, return 1
 * - else write refreshed tokens & last, return 0
 * <p>
 * Refund is done by a small Lua script that re-adds tokens (capped to capacity).
 */
@Service
@ConditionalOnProperty(name = "smoothing.type", havingValue = "redis")
public class RedisSmoothingService implements SmoothingService {

    private static final Logger log = LoggerFactory.getLogger(RedisSmoothingService.class);

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> consumeScript;
    private final DefaultRedisScript<Long> refundScript;

    // configuration (10 NOK per 10s)
    private static final double CAPACITY = 10.0;
    private static final double REFILL_RATE_PER_SECOND = 1.0; // 10 NOK / 10s

    private static final String KEY_PREFIX = "smoothing:bucket:";

    public RedisSmoothingService(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis template is required");
        this.consumeScript = new DefaultRedisScript<>();
        this.consumeScript.setScriptText(CONSUME_LUA);
        this.consumeScript.setResultType(Long.class);

        this.refundScript = new DefaultRedisScript<>();
        this.refundScript.setScriptText(REFUND_LUA);
        this.refundScript.setResultType(Long.class);
    }

    @Override
    public boolean tryConsume(long campaignId, double amount) {
        Objects.requireNonNull(redis, "redis template is required");
        String key = KEY_PREFIX + campaignId;
        String now = String.valueOf(Instant.now().getEpochSecond());
        // ARGV: amount, capacity, refill_rate_per_second, now
        List<String> args = List.of(
                Double.toString(amount),
                Double.toString(CAPACITY),
                Double.toString(REFILL_RATE_PER_SECOND),
                now
        );

        Long result;
        try {
            result = redis.execute(consumeScript, Collections.singletonList(key), args.toArray(new String[0]));
        } catch (Exception e) {
            log.error("Redis consume script failed for campaignId={}, amount={}, error={}", campaignId, amount, e.getMessage());
            // Fail-safe: if Redis fails, don't allow consumption (prefer safe) — or you may choose to allow.
            return false;
        }
        return result != null && result == 1L;
    }

    @Override
    public void refund(long campaignId, double amount) {
        String key = KEY_PREFIX + campaignId;
        List<String> args = List.of(
                Double.toString(amount),
                Double.toString(CAPACITY)
        );
        try {
            Long res = redis.execute(refundScript, Collections.singletonList(key), args.toArray(new String[0]));
            if (res == null || res != 1L) {
                log.warn("Refund script returned {} for campaignId={} amount={}", res, campaignId, amount);
            }
        } catch (Exception e) {
            log.error("Redis refund script failed for campaignId={}, amount={}, error={}", campaignId, amount, e.getMessage());
            // best-effort: nothing else to do; tokens may be inconsistent for a short time
        }
    }

    @Override
    public double availableTokens(long campaignId) {
        String key = KEY_PREFIX + campaignId;
        try {
            List<String> vals = redis.opsForHash().multiGet(key, List.of("tokens", "last")).stream()
                    .map(o -> o == null ? null : o.toString())
                    .toList();
            String tokensStr = vals.get(0);
            String lastStr = vals.get(1);
            long now = Instant.now().getEpochSecond();
            double tokens = tokensStr != null ? Double.parseDouble(tokensStr) : CAPACITY;
            long last = lastStr != null ? Long.parseLong(lastStr) : now;
            long elapsed = now - last;
            if (elapsed > 0) {
                tokens = Math.min(CAPACITY, tokens + elapsed * REFILL_RATE_PER_SECOND);
            }
            return tokens;
        } catch (Exception e) {
            log.error("Failed to read tokens for campaignId={}, error={}", campaignId, e.getMessage());
            return 0.0;
        }
    }

    // Lua script for atomic consume: returns 1 on success, 0 on failure
    // KEYS[1] = bucket key
    // ARGV[1] = amount
    // ARGV[2] = capacity
    // ARGV[3] = refill_rate_per_second
    // ARGV[4] = now (epoch seconds)
    private static final String CONSUME_LUA =
            """
                    local key = KEYS[1]
                    local amount = tonumber(ARGV[1])
                    local capacity = tonumber(ARGV[2])
                    local refill = tonumber(ARGV[3])
                    local now = tonumber(ARGV[4])
                    local data = redis.call('HMGET', key, 'tokens', 'last')
                    local tokens = tonumber(data[1]) or capacity
                    local last = tonumber(data[2]) or now
                    local elapsed = now - last
                    if elapsed > 0 then
                      tokens = math.min(capacity, tokens + elapsed * refill)
                      last = now
                    end
                    if tokens + 1e-9 >= amount then
                      tokens = tokens - amount
                      redis.call('HMSET', key, 'tokens', tostring(tokens), 'last', tostring(last))
                      redis.call('EXPIRE', key, 3600)
                      return 1
                    else
                      redis.call('HMSET', key, 'tokens', tostring(tokens), 'last', tostring(last))
                      redis.call('EXPIRE', key, 3600)
                      return 0
                    end
                    """;

    // Lua script for refund: add tokens back up to capacity, returns 1
    // KEYS[1] = bucket key
    // ARGV[1] = amount
    // ARGV[2] = capacity
    private static final String REFUND_LUA =
            """
                    local key = KEYS[1]
                    local amount = tonumber(ARGV[1])
                    local capacity = tonumber(ARGV[2])
                    local data = redis.call('HMGET', key, 'tokens', 'last')
                    local tokens = tonumber(data[1]) or capacity
                    tokens = math.min(capacity, tokens + amount)
                    local last = tonumber(data[2]) or tonumber(redis.call('TIME')[1])
                    redis.call('HMSET', key, 'tokens', tostring(tokens), 'last', tostring(last))
                    redis.call('EXPIRE', key, 3600)
                    return 1
                    """;
}
