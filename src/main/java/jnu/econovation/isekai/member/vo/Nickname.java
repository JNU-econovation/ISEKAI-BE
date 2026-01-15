package jnu.econovation.isekai.member.vo;

import static jnu.econovation.isekai.member.constant.MemberConstants.NICKNAME_MAX_LENGTH;
import static jnu.econovation.isekai.member.constant.MemberConstants.NICKNAME_MIN_LENGTH;

import jakarta.persistence.Embeddable;
import jnu.econovation.isekai.common.exception.client.BadDataLengthException;

@Embeddable
public record Nickname(String value) {

    public Nickname {
        validate(value);
    }

    private void validate(String value) {
        if (value == null || value.length() < NICKNAME_MIN_LENGTH) {
            throw new BadDataLengthException("닉네임", NICKNAME_MIN_LENGTH, NICKNAME_MAX_LENGTH);
        }

        if (value.length() > NICKNAME_MAX_LENGTH) {
            throw new BadDataLengthException("닉네임", NICKNAME_MIN_LENGTH, NICKNAME_MAX_LENGTH);
        }
    }
}