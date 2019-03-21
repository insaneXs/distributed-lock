package com.insanexs.distributed.lock.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.Assert;

import java.util.Arrays;

/**
 * @Author: insaneXs
 * @Description:
 * @Date: Create at 2019-03-21
 */
public final class DistributedLockTool<V> {
    private Logger logger = LoggerFactory.getLogger(DistributedLockTool.class);
    private RedisTemplate<String, V> redisTemplate;
    private RedisSerializer keySerializer;
    private RedisSerializer valueSerializer;

    public DistributedLockTool(RedisTemplate<String, V> redisTemplate){
        this(redisTemplate, new StringRedisSerializer(), new StringRedisSerializer());
    }

    public DistributedLockTool(RedisTemplate<String, V> redisTemplate, RedisSerializer keySerializer, RedisSerializer valueSerializer){
        this.redisTemplate = redisTemplate;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    /**
     * 尝试获得锁
     * @param key 键
     * @param value 值
     * @param expirationTime 过期时间
     * @return
     */
    public boolean tryAcquireLock(String key, V value, long expirationTime){
        if(value.equals(redisTemplate.opsForValue().get(key)))
            return true;
        return redisTemplate.execute((RedisConnection connection) ->
                connection.set(keySerializer.serialize(key), valueSerializer.serialize(value), Expiration.milliseconds(expirationTime),RedisStringCommands.SetOption.ifAbsent())
        );
    }

    /**
     * 尝试释放锁
     * @param key 键
     * @param exceptedValue 期待值
     * @return
     */
    public boolean tryReleaseLock(String key, V exceptedValue){
        Assert.notNull(exceptedValue, "excepted value can not be null");

        Boolean result = false;
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        try {
            RedisScript<Boolean> luaScript = new DefaultRedisScript<>(script, Boolean.class);
            Object[] args = {exceptedValue};
            result = redisTemplate.execute(luaScript, Arrays.asList(key), args);
        } catch (Exception e) {
            // do nothing
            logger.error("executes release lock error;" + e);
        }
        return result;
    }

    /**
     * 在指定时间内尝试获取分布式锁
     * @param key 键
     * @param value 值
     * @param expirationTime 过期时间
     * @param timeout 超时时间 毫秒
     * @return
     */
    public boolean tryAcquireLock(String key, V value, long expirationTime, long timeout){
        long deadline = System.currentTimeMillis() + timeout;
        int count = 1;
        try {
            for (; ; ) {
                long time = deadline - System.currentTimeMillis();
                if (time < 0)
                    return false;
                boolean ret = tryAcquireLock(key, value, expirationTime);
                if (ret)
                    return ret;
                logger.warn("try acquire lock failed " + count + " times" );
                count++;
                Thread.sleep(10);
            }
        }catch (InterruptedException e){
            logger.warn("try acquire lock failed " + count + " times" );
            return false;
        }
    }

}

