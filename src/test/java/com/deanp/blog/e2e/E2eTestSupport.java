package com.deanp.blog.e2e;

import com.deanp.blog.persistence.TestPostgresql;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PageAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 서버를 무작위 포트로 띄우고 Chromium으로 화면 흐름을 검증하는 E2E 테스트의 공통 준비를 맡는다.
 * 로컬은 일회용 PostgreSQL 컨테이너, CI는 검증된 전용 DB를 쓴다. 서버 컨텍스트와 DB는 모든 E2E 클래스가 공유하므로
 * 각 테스트는 {@link #stamp}를 붙인 고유한 이름으로 데이터를 만든다.
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
abstract class E2eTestSupport {

    /** 기동 시 만들어지는 테스트용 관리자 아이디 */
    protected static final String ADMIN_LOGIN_ID = "e2e-admin";
    private static final String ADMIN_PASSWORD = "e2e-password-1234";
    private static final TestPostgresql DB = new TestPostgresql();
    private static final Path MEDIA_ROOT = temporaryDirectory();
    private static final Path FAILURE_SHOTS = Paths.get("build", "reports", "e2e");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final DateTimeFormatter LOCAL_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"));

    private static Playwright playwright;
    private static Browser browser;

    /** 테스트마다 다른 이름을 만들기 위한 접미사 */
    protected final String stamp = System.currentTimeMillis() + "-" + SEQUENCE.incrementAndGet();
    /** 테스트마다 새로 여는 브라우저 컨텍스트 */
    protected BrowserContext context;
    /** 테스트가 조작하는 기본 탭 */
    protected Page page;

    private final List<String> consoleErrors = new ArrayList<>();

    @LocalServerPort
    private int port;

    /** 실패한 테스트의 마지막 화면을 build/reports/e2e에 남긴다. 브라우저가 이미 죽었으면 원래 실패만 남긴다. */
    @RegisterExtension
    final AfterTestExecutionCallback failureScreenshot = extension -> {
        if (extension.getExecutionException().isPresent() && page != null) {
            try {
                page.screenshot(new Page.ScreenshotOptions().setFullPage(true)
                        .setPath(FAILURE_SHOTS.resolve(extension.getRequiredTestClass().getSimpleName()
                                + "-" + extension.getRequiredTestMethod().getName() + ".png")));
            } catch (RuntimeException ignored) {
                // 화면을 찍지 못해도 테스트 실패 원인을 가리지 않는다.
            }
        }
    };

    /**
     * 테스트 DB, 관리자 계정, 미디어 저장 위치를 서버 설정에 넣는다.
     *
     * @param registry 동적 설정 저장소
     */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        DB.register(registry);
        registry.add("ADMIN_LOGIN_ID", () -> ADMIN_LOGIN_ID);
        registry.add("ADMIN_PASSWORD", () -> ADMIN_PASSWORD);
        registry.add("APP_MEDIA_STORAGE_ROOT", MEDIA_ROOT::toString);
    }

    /**
     * 클래스마다 헤드리스 Chromium을 한 번 띄운다. 처음 실행하면 Playwright가 브라우저를 내려받는다.
     * 컨테이너의 작은 /dev/shm(64MB) 때문에 화면이 죽지 않도록 공유 메모리 대신 임시 파일을 쓴다.
     */
    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setArgs(List.of("--disable-dev-shm-usage")));
    }

    /** 클래스가 끝나면 브라우저를 닫는다. */
    @AfterAll
    static void closeBrowser() {
        if (playwright != null) {
            playwright.close();
        }
    }

    /** PC 폭의 새 컨텍스트를 열고, 확인 창은 수락하며, 콘솔 오류를 모은다. */
    @BeforeEach
    void openPage() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 1000)
                .setBaseURL("http://localhost:" + port));
        page = context.newPage();
        page.onConsoleMessage(message -> {
            if ("error".equals(message.type())) {
                consoleErrors.add(message.text() + " @ " + message.location());
            }
        });
        page.onPageError(consoleErrors::add);
        page.onDialog(dialog -> dialog.accept());
    }

    /** 테스트마다 컨텍스트를 닫아 로그인 상태를 나누지 않는다. */
    @AfterEach
    void closePage() {
        if (context != null) {
            context.close();
        }
    }

    /** 테스트용 관리자로 로그인하고 관리 대시보드에 도착한 것을 확인한다. */
    protected void login() {
        page.navigate("/login");
        page.fill("#username", ADMIN_LOGIN_ID);
        page.fill("#password", ADMIN_PASSWORD);
        page.click("button[type=submit]");
        page.waitForURL("**/admin");
    }

    /**
     * 폼 제출 버튼을 누르고, 리다이렉트 뒤 새 화면이 다 뜰 때까지 기다린다.
     * 저장할 때마다 같은 주소로 돌아오므로 주소 대신 새 문서 응답을 기다린다.
     *
     * @param selector 제출 버튼 선택자
     */
    protected void submit(String selector) {
        page.waitForResponse(response -> response.request().isNavigationRequest()
                && "GET".equals(response.request().method()), () -> page.click(selector));
        page.waitForLoadState();
    }

    /**
     * 리다이렉트를 따라가지 않고 현재 로그인 상태로 요청한 응답 코드를 돌려준다.
     *
     * @param path 서버 경로
     * @return HTTP 상태 코드
     */
    protected int status(String path) {
        return context.request().get(path, RequestOptions.create().setMaxRedirects(0)).status();
    }

    /**
     * 로그인하지 않은 새 브라우저로 화면을 한 번 열고 닫는다. 방문 통계에 잡히는 조회를 만들 때 쓴다.
     *
     * @param path 서버 경로
     */
    protected void anonymousVisit(String path) {
        try (BrowserContext anonymous = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL("http://localhost:" + port))) {
            anonymous.newPage().navigate(path);
        }
    }

    /**
     * 현재 화면이 가로로 넘친 정도를 잰다.
     *
     * @return 가로 넘침 픽셀 수
     */
    protected int horizontalOverflow() {
        return ((Number) page.evaluate("document.documentElement.scrollWidth - window.innerWidth")).intValue();
    }

    /**
     * 모바일 폭(390px)으로 바꿔 주어진 화면들에 가로 넘침이 없는지 확인한다.
     *
     * @param paths 서버 경로
     */
    protected void assertNoMobileOverflow(String... paths) {
        page.setViewportSize(390, 844);
        for (String path : paths) {
            page.navigate(path);
            assertThat(horizontalOverflow()).as("모바일 가로 넘침: %s", path).isLessThanOrEqualTo(1);
        }
        page.setViewportSize(1440, 1000);
    }

    /**
     * 지금까지 브라우저 콘솔에 오류가 없었는지 확인한다. 파비콘 404와, 테스트가 일부러 낸 오류 문구는 무시한다.
     *
     * @param expected 테스트가 일부러 낸 오류에 들어 있는 문구
     */
    protected void assertNoConsoleErrors(String... expected) {
        assertThat(consoleErrors.stream().filter(error -> !error.contains("favicon"))
                .filter(error -> java.util.Arrays.stream(expected).noneMatch(error::contains))).isEmpty();
    }

    /**
     * 현재 주소 /admin/{종류}/{id}/... 에서 식별자를 꺼낸다.
     *
     * @return 식별자
     */
    protected String idFromUrl() {
        return URI.create(page.url()).getPath().split("/")[3];
    }

    /**
     * 시각을 관리 화면의 발행 시각 입력값으로 바꾼다.
     *
     * @param instant 시각
     * @return 한국 시간 기준 datetime-local 입력값
     */
    protected static String localInput(Instant instant) {
        return LOCAL_INPUT.format(instant);
    }

    /**
     * 주소가 정규식과 맞을 때까지 기다린다.
     *
     * @param regex 주소 정규식
     */
    protected void waitForUrl(String regex) {
        page.waitForURL(Pattern.compile(regex));
    }

    /**
     * 조건을 만족할 때까지 다시 확인하는 Playwright 단언을 만든다.
     *
     * @param locator 대상 요소
     * @return 자동 대기 단언
     */
    protected static LocatorAssertions expect(Locator locator) {
        return PlaywrightAssertions.assertThat(locator);
    }

    /**
     * 조건을 만족할 때까지 다시 확인하는 Playwright 단언을 만든다.
     *
     * @param target 대상 탭
     * @return 자동 대기 단언
     */
    protected static PageAssertions expect(Page target) {
        return PlaywrightAssertions.assertThat(target);
    }

    /**
     * 미디어 파일을 둘 임시 폴더를 만든다.
     *
     * @return 임시 폴더
     */
    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("blog-e2e-media");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
