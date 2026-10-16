package com.deanp.blog.auth.service;

import com.deanp.blog.auth.persistence.repository.AppUserRepository;
import com.deanp.blog.auth.persistence.repository.AuthGroupRepository;
import com.deanp.blog.auth.persistence.repository.GroupMenuRepository;
import com.deanp.blog.auth.persistence.repository.UserGroupRepository;
import com.deanp.blog.menu.persistence.repository.MenuRepository;
import com.deanp.blog.persistence.TestPostgresql;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 격리된 PostgreSQL에서 환경변수 관리자 계정 준비가 반복 실행에도 같은 결과를 내는지 검증한다. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AdminAccountBootstrapTest {

    private static final TestPostgresql DB = new TestPostgresql();
    private static final String LOGIN_ID = "bootstrap-admin";

    @Autowired
    private AppUserRepository users;
    @Autowired
    private AuthGroupRepository groups;
    @Autowired
    private MenuRepository menus;
    @Autowired
    private GroupMenuRepository groupMenus;
    @Autowired
    private UserGroupRepository userGroups;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private BlogUserDetailsService userDetails;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    /** @param registry 테스트 전용 데이터베이스 설정 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.register(registry);
    }

    /** 로컬 일회용 DB를 종료한다. */
    @AfterAll
    static void stopDatabase() {
        DB.stop();
    }

    /** 두 번 실행해도 계정·그룹·메뉴·권한·소속이 하나씩만 생기고 글 관리 권한으로 로그인할 수 있다. */
    @Test
    void createsAdminOnceAndGrantsPostWrite() {
        bootstrap(LOGIN_ID, "first-password").run(null);
        bootstrap(LOGIN_ID, "first-password").run(null);
        entityManager.flush();

        assertThat(count("SELECT count(*) FROM blog.app_user WHERE login_id = ?", LOGIN_ID)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM blog.auth_group WHERE name = '관리자'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM blog.menu WHERE path = ?", BlogUserDetailsService.POST_ADMIN_MENU_PATH)).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM blog.user_group ug
                JOIN blog.app_user u ON u.id = ug.user_id
                JOIN blog.group_menu gm ON gm.group_id = ug.group_id
                WHERE u.login_id = ? AND gm.permission_code = ?
                """, LOGIN_ID, BlogUserDetailsService.WRITE_PERMISSION)).isEqualTo(1);
        assertThat(userDetails.loadUserByUsername(LOGIN_ID).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(BlogUserDetailsService.POST_WRITE);
    }

    /** 설정한 비밀번호가 바뀌면 다음 기동 때 저장된 해시를 새 비밀번호로 교체한다. */
    @Test
    void replacesPasswordWhenConfigurationChanges() {
        bootstrap(LOGIN_ID, "first-password").run(null);
        bootstrap(LOGIN_ID, "second-password").run(null);
        entityManager.flush();

        String hash = users.findByLoginId(LOGIN_ID).orElseThrow().getPasswordHash();
        assertThat(passwordEncoder.matches("second-password", hash)).isTrue();
        assertThat(passwordEncoder.matches("first-password", hash)).isFalse();
    }

    /** 설정이 모두 비면 건너뛰고, 한쪽만 있거나 비밀번호가 짧으면 기동을 멈춘다. */
    @Test
    void validatesConfiguration() {
        bootstrap("", "").run(null);
        assertThat(count("SELECT count(*) FROM blog.app_user WHERE login_id = ?", LOGIN_ID)).isZero();

        assertThatThrownBy(() -> bootstrap(LOGIN_ID, "").run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> bootstrap(LOGIN_ID, "short").run(null)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * @param loginId 설정할 관리자 아이디
     * @param password 설정할 관리자 비밀번호
     * @return 테스트 트랜잭션 안에서 실행할 관리자 계정 준비기
     */
    private AdminAccountBootstrap bootstrap(String loginId, String password) {
        return new AdminAccountBootstrap(new AdminBootstrapProperties(loginId, password),
                users, groups, menus, groupMenus, userGroups, passwordEncoder);
    }

    /** @param sql 개수를 세는 SQL @param args 바인딩 값 @return 조회한 개수 */
    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
