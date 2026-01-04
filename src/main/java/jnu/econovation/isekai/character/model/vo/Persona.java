package jnu.econovation.isekai.character.model.vo;

import static jnu.econovation.isekai.character.constant.CharacterConstants.PERSONA_MAX_LENGTH;
import static jnu.econovation.isekai.character.constant.CharacterConstants.PERSONA_MIN_LENGTH;
import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Embeddable;
import jnu.econovation.isekai.common.exception.client.BadDataLengthException;

@Embeddable
public record Persona(
        String value
) {

    @JsonCreator
    public Persona {
        validate(value);
    }

    private void validate(String value) {
        if (value == null || value.length() < PERSONA_MIN_LENGTH
                || value.length() > PERSONA_MAX_LENGTH) {
            throw new BadDataLengthException("성격", PERSONA_MIN_LENGTH, PERSONA_MAX_LENGTH);
        }
    }
}
