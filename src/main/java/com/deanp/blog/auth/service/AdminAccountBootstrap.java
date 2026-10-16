package com.deanp.blog.auth.service;

import com.deanp.blog.auth.persistence.entity.AppUser;
import com.deanp.blog.auth.persistence.entity.AuthGroup;
import com.deanp.blog.auth.persistence.entity.GroupMenu;
import com.deanp.blog.auth.persistence.entity.GroupMenuId;
import com.deanp.blog.auth.persistence.entity.UserGroup;
import com.deanp.blog.auth.persistence.entity.UserGroupId;
import com.deanp.blog.auth.persistence.repository.AppUserRepository;
import com.deanp.blog.auth.persistence.repository.AuthGroupRepository;
import com.deanp.blog.auth.persistence.repository.GroupMenuRepository;
import com.deanp.blog.auth.persistence.repository.UserGroupRepository;
import com.deanp.blog.menu.persistence.entity.Menu;
import com.deanp.blog.menu.persistence.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환경변수로 받은 관리자 계정을 기동 시 준비하고 글 관리 그룹 권한을 연결한다.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminAccountBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 10;
    private static final String ADMIN_GROUP_NAME = "관리자";
    private static final String POST_ADMIN_MENU_NAME = "글 관리";

    private final AdminBootstrapProperties properties;
    private final AppUserRepository users;
    private final AuthGroupRepository groups;
    private final MenuRepository menus;
    private final GroupMenuRepository groupMenus;
    private final UserGroupRepository userGroups;
    private final PasswordEncoder passwordEncoder;

    /**
     * 설정이 있으면 관리자 계정과 권한 연결을 맞춘다. 설정이 없으면 아무것도 하지 않는다.
     *
     * @param args 애플리케이션 실행 인자
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 아이디와 비밀번호가 모두 비어 있으면 건너뛰고, 한쪽만 있거나 너무 짧으면 기동을 멈춘다.
        boolean hasLoginId = properties.loginId() != null && !properties.loginId().isBlank();
        boolean hasPassword = properties.password() != null && !properties.password().isBlank();
        if (!hasLoginId && !hasPassword) {
            return;
        }
        if (!hasLoginId || !hasPassword) {
            throw new IllegalStateException("ADMIN_LOGIN_ID와 ADMIN_PASSWORD는 함께 설정해야 합니다.");
        }
        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        String loginId = properties.loginId().strip();

        // 계정이 없으면 만들고, 있으면 비밀번호가 설정과 다를 때만 해시를 교체한다.
        AppUser user = users.findByLoginId(loginId).orElse(null);
        if (user == null) {
            user = users.save(AppUser.create(loginId, passwordEncoder.encode(properties.password()), loginId));
            log.info("관리자 계정을 만들었습니다. loginId={}", loginId);
        } else if (!passwordEncoder.matches(properties.password(), user.getPasswordHash())) {
            user.changePassword(passwordEncoder.encode(properties.password()));
            log.info("관리자 계정의 비밀번호를 설정값으로 교체했습니다. loginId={}", loginId);
        }

        // 관리자 그룹, 글 관리 메뉴, 그룹의 쓰기 권한, 사용자 소속을 없는 것만 채운다.
        AuthGroup group = groups.findByName(ADMIN_GROUP_NAME)
                .orElseGet(() -> groups.save(AuthGroup.create(ADMIN_GROUP_NAME, "글 관리 권한 그룹")));
        Menu menu = menus.findFirstByPathOrderByCreatedAtAsc(BlogUserDetailsService.POST_ADMIN_MENU_PATH)
                .orElseGet(() -> menus.save(Menu.create(POST_ADMIN_MENU_NAME, BlogUserDetailsService.POST_ADMIN_MENU_PATH)));
        GroupMenuId permission = new GroupMenuId(group.getId(), menu.getId(), BlogUserDetailsService.WRITE_PERMISSION);
        if (!groupMenus.existsById(permission)) {
            groupMenus.save(GroupMenu.create(permission));
        }
        UserGroupId membership = new UserGroupId(user.getId(), group.getId());
        if (!userGroups.existsById(membership)) {
            userGroups.save(UserGroup.create(membership));
        }
    }
}
