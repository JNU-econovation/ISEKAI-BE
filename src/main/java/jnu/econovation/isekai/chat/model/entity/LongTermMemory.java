package jnu.econovation.isekai.chat.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jnu.econovation.isekai.common.entity.BaseEntity;
import jnu.econovation.isekai.member.entity.Member;
import jnu.econovation.isekai.persona.model.entity.Persona;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LongTermMemory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_member_id", nullable = false)
    private Member hostMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    @Array(length = 768)
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] embedding;

    @Builder
    LongTermMemory(Persona persona, Member hostMember, String summary, float[] embedding) {
        this.persona = persona;
        this.summary = summary;
        this.hostMember = hostMember;
        this.embedding = embedding;
    }
}
