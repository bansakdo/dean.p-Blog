package com.deanp.blog.auth.service;

import com.deanp.blog.auth.persistence.entity.AppUser;
import com.deanp.blog.auth.persistence.repository.AppUserRepository;
import com.deanp.blog.auth.persistence.repository.GroupMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 로그인 아이디로 사용자를 찾고 그룹 권한을 로그인 권한으로 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class BlogUserDetailsService implements UserDetailsService {

    /** 글 관리 화면에 필요한 로그인 권한 이름이다. */
    public static final String POST_WRITE = "POST_WRITE";
    /** 글 관리 권한을 부여할 때 연결하는 메뉴 경로다. */
    public static final String POST_ADMIN_MENU_PATH = "/admin/posts";
    /** 글을 쓰고 고칠 수 있는 그룹-메뉴 권한 코드다. */
    public static final String WRITE_PERMISSION = "WRITE";

    private static final String ACTIVE = "ACTIVE";

    private final AppUserRepository users;
    private final GroupMenuRepository groupMenus;
    private final LoginAttemptService loginAttempts;

    /**
     * 로그인 처리에 쓸 사용자 정보를 만든다. 비활성 사용자와 최근 실패가 많은 사용자는 로그인할 수 없다.
     *
     * @param username 로그인 아이디
     * @return 비밀번호 해시와 권한을 담은 사용자 정보
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByLoginId(username)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 사용자입니다."));

        // 소속 그룹이 글 관리 메뉴의 쓰기 권한을 가진 경우에만 글 관리 권한을 준다.
        boolean canWritePosts = groupMenus.hasActivePermission(user.getId(), POST_ADMIN_MENU_PATH, WRITE_PERMISSION);

        return User.withUsername(user.getLoginId())
                .password(user.getPasswordHash())
                .disabled(!ACTIVE.equals(user.getStatus()))
                // 잠긴 계정은 비밀번호를 비교하기 전에 거부되므로 맞는 비밀번호를 넣어도 잠금이 풀리지 않는다.
                .accountLocked(loginAttempts.isLocked(user.getLoginId()))
                .authorities(canWritePosts ? List.of(new SimpleGrantedAuthority(POST_WRITE)) : List.of())
                .build();
    }
}
