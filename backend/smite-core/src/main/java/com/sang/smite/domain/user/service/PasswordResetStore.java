package com.sang.smite.domain.user.service;

import java.util.Optional;

/**
 * 비밀번호 재설정 토큰을 저장하고 조회하는 저장소 인터페이스입니다.
 */
public interface PasswordResetStore {
    /**
     * 재설정 토큰을 저장합니다.
     * @param token 생성된 UUID 토큰
     * @param email 유저 이메일
     * @param ttlInMinutes 만료 시간(분)
     */
    void save(String token, String email, long ttlInMinutes);

    /**
     * 토큰을 통해 이메일을 조회합니다.
     * @param token 검증할 토큰
     * @return 유저 이메일 (토큰이 유효하지 않으면 empty)
     */
    Optional<String> getEmailByToken(String token);

    /**
     * 사용된 토큰을 삭제합니다.
     * @param token 삭제할 토큰
     */
    void remove(String token);
}
