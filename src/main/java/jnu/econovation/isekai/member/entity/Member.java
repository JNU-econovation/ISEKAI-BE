package jnu.econovation.isekai.member.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jnu.econovation.isekai.common.entity.BaseEntity;
import jnu.econovation.isekai.member.vo.Email;
import jnu.econovation.isekai.member.vo.Nickname;
import jnu.econovation.isekai.member.vo.Oauth2Provider;
import jnu.econovation.isekai.member.vo.Role;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "email", nullable = false, unique = true))
    private Email email;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "nickname", nullable = false, unique = true))
    private Nickname nickname;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Oauth2Provider provider;

    @Builder
    Member(Oauth2Provider provider, Nickname nickname, Email email) {
        this.provider = provider;
        this.email = email;
        this.nickname = nickname;
        this.role = Role.USER;
    }
}