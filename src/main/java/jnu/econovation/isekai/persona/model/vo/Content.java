package jnu.econovation.isekai.persona.model.vo;

import static jnu.econovation.isekai.persona.constant.PersonaConstants.CONTENT_MAX_LENGTH;
import static jnu.econovation.isekai.persona.constant.PersonaConstants.CONTENT_MIN_LENGTH;
import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Embeddable;
import jnu.econovation.isekai.common.exception.client.BadDataLengthException;

@Embeddable
public record Content(
        String value
) {

    @JsonCreator
    public Content {
        validate(value);
    }

    private void validate(String value) {
        if (value == null || value.length() < CONTENT_MIN_LENGTH
                || value.length() > CONTENT_MAX_LENGTH) {
            throw new BadDataLengthException("내용", CONTENT_MIN_LENGTH, CONTENT_MAX_LENGTH);
        }
    }
}
