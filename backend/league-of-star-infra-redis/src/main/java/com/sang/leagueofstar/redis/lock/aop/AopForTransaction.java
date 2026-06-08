package com.sang.leagueofstar.redis.lock.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분산 락 해제 전 트랜잭션 커밋을 보장하기 위한 트랜잭션 처리기입니다.
 */
@Component
public class AopForTransaction {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object proceed(final ProceedingJoinPoint joinPoint) throws Throwable {
        return joinPoint.proceed();
    }
}
