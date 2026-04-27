package com.sang.smite.common.service;

/**
 * 범용 이메일 발송 인터페이스입니다.
 * 도메인 계층에서는 기술 스택에 종속되지 않고 이 인터페이스를 통해 메일을 발송합니다.
 */
public interface EmailService {
    
    /**
     * 기본 텍스트 이메일을 발송합니다.
     * @param to 수신자 이메일 주소
     * @param subject 메일 제목
     * @param content 메일 본문 (Plain Text)
     */
    void sendTextEmail(String to, String subject, String content);
}
