package com.insanexs.distributed.lock.redis.aop;

import com.insanexs.distributed.lock.annotations.DistributedLock;
import com.insanexs.distributed.lock.redis.DistributedLockTool;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: insaneXs
 * @Description:
 * @Date: Create at 2019-03-21
 */
public class DistributedLockAOP {
    private ThreadLocal<String> threadUUID = new ThreadLocal<>();
    private static Map<String, Integer> counterMap = new ConcurrentHashMap<>();
    private static final String LOCK_PREFIX = "DISTRIBUTED-LOCK:";

    @Resource
    DistributedLockTool<String> distributedLockTool;

    @Around(value = "@annotation(com.lanxincn.common.redis.distributedLock.annotations.DistributedLock)")
    public Object lock(ProceedingJoinPoint pjp) {
        String uuid = threadUUID.get();
        if(StringUtils.isEmpty(uuid)){
            uuid = UUID.randomUUID().toString();
            threadUUID.set(uuid);
        }


        Method method = getMethod(pjp);
        DistributedLock annotation = method.getAnnotation(DistributedLock.class);
        String key = parseKey(annotation.key(), method, pjp.getArgs());
        long expirationTime = annotation.expirationTime();
        long timeout = annotation.timeout();
        if(!distributedLockTool.tryAcquireLock(LOCK_PREFIX + key, uuid, expirationTime, timeout)){
            return null;
        }


        Integer counter = counterMap.get(key);
        if(counter == null)
            counter = 0;
        counter++;
        counterMap.put(key, counter);

        Object result = null;
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
        } finally {
            counter = counterMap.get(key);
            if(counter == null || counter - 1 == 0){
                counterMap.remove(key);
                distributedLockTool.tryReleaseLock(LOCK_PREFIX + key, uuid);
            }else{
                counterMap.put(key, counter - 1);
            }
        }

        return result;
    }

    /**
     * 获取被拦截方法对象
     */
    public Method getMethod(ProceedingJoinPoint pjp) {
        MethodSignature methodSignature = (MethodSignature)pjp.getSignature();
        Method method = methodSignature.getMethod();
        return method;

    }

    /**
     * 使用Spring提供的方式解析注解中的key，动态解析Spel
     * @param key original key
     * @param method 拦截的方法
     * @param args 拦截的方法参数
     * @return
     */
    private String parseKey(String key, Method method, Object[] args) {


        //解析被拦截方法的方法参数名
        LocalVariableTableParameterNameDiscoverer u =
                new LocalVariableTableParameterNameDiscoverer();
        String[] paraNameArr = u.getParameterNames(method);

        //使用Spel进行key的解析
        ExpressionParser parser = new SpelExpressionParser();
        //创建Spel上下文
        StandardEvaluationContext context = new StandardEvaluationContext();

        for (int i = 0; i < paraNameArr.length; i++) {
            context.setVariable(paraNameArr[i], args[i]);
        }

        return parser.parseExpression(key).getValue(context, String.class);
    }
}
