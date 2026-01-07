package jnu.econovation.isekai.character.model.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jnu.econovation.isekai.character.model.vo.CharacterName;
import jnu.econovation.isekai.character.model.vo.Persona;
import jnu.econovation.isekai.common.entity.BaseEntity;
import jnu.econovation.isekai.member.entity.Member;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Character extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Member author;

    @Column(nullable = false)
    private String live2dModelUrl;

    @Column(nullable = false)
    private String backgroundUrl;

    @Column(nullable = false)
    private String live2dModelNukkiUrl;

    @Column(nullable = false)
    private String thumbnailUrl;

    @AttributeOverride(name = "value", column = @Column(name = "character_name", nullable = false))
    private CharacterName name;

    @AttributeOverride(name = "value", column = @Column(name = "persona", nullable = false))
    private Persona persona;

    @Column(nullable = false)
    private String voiceId;

    @Column(nullable = false)
    private Boolean isPublic;

    @Builder
    Character(
            Member author,
            String live2dModelUrl,
            String backgroundUrl,
            String live2dModelNukkiUrl,
            String thumbnailUrl,
            CharacterName name,
            Persona persona,
            String voiceId,
            Boolean isPublic
    ) {
        this.author = author;
        this.live2dModelUrl = live2dModelUrl;
        this.backgroundUrl = backgroundUrl;
        this.live2dModelNukkiUrl = live2dModelNukkiUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.name = name;
        this.persona = persona;
        this.voiceId = voiceId;
        this.isPublic = isPublic;
    }
}
