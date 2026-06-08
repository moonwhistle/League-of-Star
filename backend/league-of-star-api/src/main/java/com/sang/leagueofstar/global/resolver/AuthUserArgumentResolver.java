package com.sang.leagueofstar.global.resolver;

import com.sang.leagueofstar.auth.security.dto.AuthenticatedUser;
import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean hasAnnotation = parameter.hasParameterAnnotation(AuthUser.class);
        boolean isLongType = Long.class.isAssignableFrom(parameter.getParameterType());
        return hasAnnotation && isLongType;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthUser annotation = parameter.getParameterAnnotation(AuthUser.class);
        boolean required = annotation != null && annotation.required();

        // 1. 인증 정보가 없거나 익명 사용자(anonymousUser)인 경우 처리
        if (authentication == null || "anonymousUser".equals(authentication.getPrincipal())) {
            if (required) {
                throw new ApiException(ApiErrorCode.AUTH_UNAUTHORIZED);
            }
            return null;
        }

        // 2. Principal에서 userId(Long)를 추출
        Object principal = authentication.getPrincipal();
        
        if (principal instanceof Long userId) {
            return userId;
        }
        
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser.userId();
        }

        // 3. 기대한 타입이 아닐 경우 예외 처리
        if (required) {
            throw new ApiException(ApiErrorCode.AUTH_UNAUTHORIZED);
        }
        return null;
    }
}
