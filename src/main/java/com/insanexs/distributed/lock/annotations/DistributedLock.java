package com.insanexs.distributed.lock.annotations;

import java.lang.annotation.*;

/**
 * @Author: insaneXs
 * @Description:
 * @Date: Create at 2019-03-21
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface DistributedLock {

    String key();

    long expirationTime() default 24 * 60 * 1000;

    long timeout() default 3000;
}