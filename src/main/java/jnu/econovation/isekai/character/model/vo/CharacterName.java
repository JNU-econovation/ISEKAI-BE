package jnu.econovation.isekai.character.model.vo;

import static jnu.econovation.isekai.character.constant.CharacterConstants.CHARACTER_NAME_MAX_LENGTH;
import static jnu.econovation.isekai.character.constant.CharacterConstants.CHARACTER_NAME_MIN_LENGTH;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Embeddable;
import jnu.econovation.isekai.common.exception.client.BadDataLengthException;

@Embeddable
public record CharacterName(
        String value
) {

    @JsonCreator
    public CharacterName {
        validate(value);
    }

    private void validate(String value) {
        if (value == null || value.length() < CHARACTER_NAME_MIN_LENGTH
                || value.length() > CHARACTER_NAME_MAX_LENGTH) {
            throw new BadDataLengthException("캐릭터 이름", CHARACTER_NAME_MIN_LENGTH,
                    CHARACTER_NAME_MAX_LENGTH);
        }
    }
}
