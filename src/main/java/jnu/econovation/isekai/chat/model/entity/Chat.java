package jnu.econovation.isekai.chat.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jnu.econovation.isekai.common.entity.BaseEntity;
import jnu.econovation.isekai.chat.model.vo.Speaker;
import jnu.econovation.isekai.member.entity.Member;
import jnu.econovation.isekai.persona.model.entity.Persona;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_member_id", nullable = false)
    private Member hostMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Speaker speaker;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    Chat(Member hostMember, Persona persona, Speaker speaker, String content) {
        this.hostMember = hostMember;
        this.persona = persona;
        this.speaker = speaker;
        this.content = content;
    }

}
