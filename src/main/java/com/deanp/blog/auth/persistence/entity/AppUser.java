package com.deanp.blog.auth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 블로그 사용자 계정과 인증 상태를 blog.app_user 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "app_user", schema = "blog")
public class AppUser {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "login_id")
    private String loginId;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "name")
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * 로그인할 수 있는 활성 사용자를 만든다.
     *
     * @param loginId 로그인 아이디
     * @param passwordHash 해시된 비밀번호
     * @param name 표시 이름
     * @return 저장할 사용자
     */
    public static AppUser create(String loginId, String passwordHash, String name) {
        AppUser user = new AppUser();
        Instant now = Instant.now();
        user.id = UUID.randomUUID();
        user.loginId = loginId;
        user.passwordHash = passwordHash;
        user.name = name;
        user.status = "ACTIVE";
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    /**
     * 비밀번호 해시를 교체한다.
     *
     * @param passwordHash 새로 해시된 비밀번호
     */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }
}
