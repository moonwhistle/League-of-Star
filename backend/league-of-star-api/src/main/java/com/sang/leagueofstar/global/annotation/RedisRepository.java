package com.sang.leagueofstar.global.annotation;

import org.springframework.stereotype.Repository;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis Repository를 명시적으로 구분하기 위한 어노테이션입니다.
 * 이 어노테이션이 붙은 인터페이스는 Redis Repository로 스캔됩니다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repository
public @interface RedisRepository {
}
