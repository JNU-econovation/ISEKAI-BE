package jnu.econovation.isekai.prompt.model.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jnu.econovation.isekai.common.entity.BaseEntity;
import jnu.econovation.isekai.member.entity.Member;
import jnu.econovation.isekai.prompt.model.vo.Content;
import jnu.econovation.isekai.prompt.model.vo.PersonaName;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prompt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member author;

    @AttributeOverride(name = "value", column = @Column(name = "persona_name", nullable = false))
    private PersonaName personaName;

    @AttributeOverride(name = "value", column = @Column(name = "content", nullable = false))
    private Content content;

    @Column(nullable = false)
    private boolean isPublic;

    @Builder
    Prompt(Member author, PersonaName personaName, Content content, boolean isPublic) {
        this.author = author;
        this.personaName = personaName;
        this.content = content;
        this.isPublic = isPublic;
    }

}
