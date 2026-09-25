package com.deanp.blog.persistence;

import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** CI 전용 DB 연결을 읽기 전용으로 확인한 뒤에만 스키마 재구성을 허용한다. */
public final class CiDatabaseGate {
    private static final String USER = "dean_p_blog_app_ci";

    private CiDatabaseGate() {
    }

    /**
     * CI 환경을 확인하고 reset 요청일 때에만 blog 스키마를 초기화한다.
     * @param args preflight 또는 reset
     * @throws SQLException DB 확인 실패
     */
    public static void main(String[] args) throws SQLException {
        if (args.length != 1 || !("preflight".equals(args[0]) || "reset".equals(args[0]))) {
            throw new IllegalArgumentException("Expected preflight or reset");
        }
        String url = System.getenv("CI_DB_URL");
        String user = System.getenv("CI_DB_USER");
        String password = System.getenv("CI_DB_PASSWORD");
        verify(url, user, password);
        if ("reset".equals(args[0])) {
            Flyway flyway = Flyway.configure().dataSource(url, user, password)
                    .defaultSchema("blog").schemas("blog").cleanDisabled(false).load();
            flyway.clean();
            flyway.migrate();
        }
    }

    /**
     * 입력 대상과 서버의 실제 DB·계정 식별자를 모두 확인한다. 오류에 자격증명을 포함하지 않는다.
     * @param url CI JDBC URL
     * @param user 허용된 CI 계정
     * @param password CI 암호
     * @throws SQLException 접속 또는 식별자 확인 실패
     */
    public static void verify(String url, String user, String password) throws SQLException {
        String allowedHost = System.getenv("CI_DB_ALLOWED_HOST");
        String allowedPort = System.getenv("CI_DB_ALLOWED_PORT");
        String serverAddress = System.getenv("CI_DB_SERVER_ADDR");
        String serverPort = System.getenv("CI_DB_SERVER_PORT");
        validateTarget(url, user, password, allowedHost, allowedPort, serverAddress, serverPort);

        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT current_database(), current_user, inet_server_addr()::text, inet_server_port(),
                            (SELECT rolsuper OR rolcreatedb OR rolcreaterole FROM pg_roles WHERE rolname = current_user)
                     """)) {
            if (!result.next()) {
                throw new IllegalStateException("CI DB server identity missing");
            }
            verifyIdentity(result.getString(1), result.getString(2), result.getString(3),
                    (Integer) result.getObject(4), result.getBoolean(5), serverAddress, Integer.parseInt(serverPort));
            System.out.println("CI DB verified: " + result.getString(1) + "/" + result.getString(2)
                    + " at " + result.getString(3) + ":" + result.getInt(4));
        }
    }

    /** DB 연결 전에 외부 주입된 접속 대상과 기대 서버 주소의 형식을 확인한다. */
    static void validateTarget(String url, String user, String password, String allowedHost,
                               String allowedPort, String serverAddress, String serverPort) {
        if (allowedHost == null || !allowedHost.matches("[a-zA-Z0-9.-]+")
                || !validPort(allowedPort) || serverAddress == null || serverAddress.isBlank()
                || !validPort(serverPort) || !USER.equals(user) || password == null || password.isBlank()
                || !("jdbc:postgresql://" + allowedHost + ":" + allowedPort + "/dean_p_blog_ci").equals(url)) {
            throw new IllegalStateException("CI DB target or credentials missing/mismatched");
        }
    }

    /** 실제 연결의 DB·계정·서버 식별자를 독립 설정의 기대값과 비교한다. */
    static void verifyIdentity(String database, String user, String address, Integer port,
                               boolean elevated, String expectedAddress, int expectedPort) {
        if (!"dean_p_blog_ci".equals(database) || !USER.equals(user)
                || !expectedAddress.equals(address) || port == null || port != expectedPort || elevated) {
            throw new IllegalStateException("CI DB server identity or role mismatch");
        }
    }

    /** @return TCP 포트로 유효한 숫자인지 여부 */
    private static boolean validPort(String value) {
        if (value == null || !value.matches("[0-9]{1,5}")) {
            return false;
        }
        int port = Integer.parseInt(value);
        return port > 0 && port <= 65535;
    }
}
