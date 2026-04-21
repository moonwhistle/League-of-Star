package com.sang.smite.global.resolver;

import com.sang.smite.auth.infrastructure.jwt.JwtTokenProvider;
import com.sang.smite.common.exception.ApiErrorCode;
import com.sang.smite.common.exception.ApiException;
import com.sang.smite.global.resolver.annotation.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtTokenProvider tokenProvider;

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

        if (authentication == null || authentication.getCredentials() == null) {
            if (required) {
                throw new ApiException(ApiErrorCode.AUTH_UNAUTHORIZED);
            }
            return null;
        }

        String token = authentication.getCredentials().toString();
        return tokenProvider.getUserId(token);
    }
}
