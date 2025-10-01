package jnu.econovation.isekai.prompt.model.vo;

import static jnu.econovation.isekai.prompt.constant.PromptConstants.PERSONA_NAME_MAX_LENGTH;
import static jnu.econovation.isekai.prompt.constant.PromptConstants.PERSONA_NAME_MIN_LENGTH;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Embeddable;
import jnu.econovation.isekai.common.exception.client.BadDataLengthException;

@Embeddable
public record PersonaName(
        String value
) {

    @JsonCreator
    public PersonaName {
        validate(value);
    }

    private void validate(String value) {
        if (value == null || value.length() < PERSONA_NAME_MIN_LENGTH
                || value.length() > PERSONA_NAME_MAX_LENGTH) {
            throw new BadDataLengthException("페르소나 이름", PERSONA_NAME_MIN_LENGTH,
                    PERSONA_NAME_MAX_LENGTH);
        }
    }
}
