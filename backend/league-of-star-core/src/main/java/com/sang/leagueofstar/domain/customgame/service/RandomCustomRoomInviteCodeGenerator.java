package com.sang.leagueofstar.domain.customgame.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class RandomCustomRoomInviteCodeGenerator implements CustomRoomInviteCodeGenerator {

    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int INVITE_CODE_LENGTH = 6;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder code = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            code.append(CODE_CHARS[secureRandom.nextInt(CODE_CHARS.length)]);
        }
        return code.toString();
    }
}
