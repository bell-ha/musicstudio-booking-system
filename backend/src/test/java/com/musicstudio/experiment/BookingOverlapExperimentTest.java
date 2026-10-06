package com.musicstudio.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * ADR 0010 실험 계획. 같은 방·같은 시간에 1,000건을 동시에 보내고 겹침 방지 방식 4가지를 비교한다.
 * 앱 서비스를 거치지 않고 JDBC로 직접 잰다. 격리 수준은 기본값(READ COMMITTED)이다.
 * 일반 빌드에서는 돌지 않는다. {@code ./gradlew experiment}로 실행한다.
 */
@Tag("experiment")
class BookingOverlapExperimentTest {

    static final int REQUESTS = 1_000;
    static final int RUNS = 3;
    static final int POOL_SIZE = Integer.getInteger("experiment.poolSize", 20);
    static final LocalDate DATE = LocalDate.of(2026, 10, 7);
    static final Instant START = DATE.atTime(10, 0).toInstant(ZoneOffset.UTC);
    static final Instant END = DATE.atTime(11, 0).toInstant(ZoneOffset.UTC);

    static PostgreSQLContainer postgres;
    static HikariDataSource ds;

    enum Method {
        NONE("0", "아무 조치 없음", "exp_booking_plain"),
        FOR_UPDATE("1", "겹치는 기존 행에 FOR UPDATE", "exp_booking_plain"),
        ADVISORY("2", "advisory lock (방·날짜, 트랜잭션 단위)", "exp_booking_plain"),
        EXCLUDE("3", "EXCLUDE 제약만", "exp_booking_excl");

        final String no;
        final String label;
        final String table;

        Method(String no, String label, String table) {
            this.no = no;
            this.label = label;
            this.table = table;
        }
    }

    /** 시나리오 A: 1,000건 모두 같은 방. 시나리오 B: 방 50개에 20건씩. */
    enum Scenario {
        ONE_ROOM("A", "같은 방 1개에 1,000건", 1),
        FIFTY_ROOMS("B", "방 50개에 20건씩 1,000건", 50);

        final String id;
        final String label;
        final int rooms;

        Scenario(String id, String label, int rooms) {
            this.id = id;
            this.label = label;
            this.rooms = rooms;
        }
    }

    record RunResult(Scenario scenario, Method method, int run, int okSeen, int conflicts,
                     Map<String, Integer> errors, int rows, int overlappedRooms,
                     double wallMs, double p50Ms, double p99Ms) {

        double throughput() {
            return REQUESTS / (wallMs / 1000.0);
        }

        int errorCount() {
            return errors.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    @BeforeAll
    static void start() throws SQLException {
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
        postgres.start();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(POOL_SIZE);
        config.setMinimumIdle(POOL_SIZE);
        // 1,000건이 줄을 서도 커넥션 대기 시간 초과가 나지 않게 넉넉히 둔다.
        // 120초로 두었을 때 방식 3이 교착 대기로 느려지며 풀 대기 시간 초과가 섞여 원인을 가렸다.
        config.setConnectionTimeout(Long.getLong("experiment.connectionTimeoutMs", 1_800_000));
        ds = new HikariDataSource(config);

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("create extension if not exists btree_gist");
            // 방식 0~2용. 배타 제약이 없어야 DB가 버그를 가리지 않는다.
            st.execute("""
                    create table exp_booking_plain (
                        id        bigserial   primary key,
                        room_id   bigint      not null,
                        starts_at timestamptz not null,
                        ends_at   timestamptz not null,
                        check (ends_at > starts_at))""");
            st.execute("create index ix_exp_plain_room on exp_booking_plain (room_id, starts_at)");
            // 방식 3용. 운영 테이블과 같은 배타 제약.
            st.execute("""
                    create table exp_booking_excl (
                        id        bigserial   primary key,
                        room_id   bigint      not null,
                        starts_at timestamptz not null,
                        ends_at   timestamptz not null,
                        check (ends_at > starts_at),
                        constraint ex_exp_room exclude using gist
                            (room_id with =, tstzrange(starts_at, ends_at, '[)') with &&))""");
        }
    }

    @AfterAll
    static void stop() {
        if (ds != null) {
            ds.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void compare() throws Exception {
        // 워밍업: 커넥션, JIT, 실행 계획을 데운다. 결과에 넣지 않는다.
        // 시나리오 A의 방식 3은 한 번에 수 분이 걸려서 워밍업은 빠른 시나리오 B로 한다.
        for (Method m : Method.values()) {
            runOnce(Scenario.FIFTY_ROOMS, m, 0);
        }

        List<RunResult> results = new ArrayList<>();
        for (Scenario s : Scenario.values()) {
            for (Method m : Method.values()) {
                for (int run = 1; run <= RUNS; run++) {
                    RunResult r = runOnce(s, m, run);
                    results.add(r);
                    System.out.printf("[%s] 방식 %s run %d: 저장 %d행, 충돌 %d, 오류 %s, %.0fms%n",
                            s.id, m.no, run, r.rows(), r.conflicts(), r.errors(), r.wallMs());
                }
            }
        }

        String doc = Report.render(results, environment());
        System.out.println(doc);
        Path out = Path.of(System.getProperty("experiment.out", "build/experiments"))
                .resolve("0001-예약-겹침-방지-비교.md");
        Report.write(out, doc);
        System.out.println("결과 문서: " + out.toAbsolutePath().normalize());

        // 방식 2, 3은 겹침을 막아야 한다. 0, 1은 결과를 기록만 한다.
        for (RunResult r : results) {
            if (r.method() == Method.ADVISORY || r.method() == Method.EXCLUDE) {
                assertThat(r.overlappedRooms()).as("%s 방식 %s", r.scenario(), r.method()).isZero();
                assertThat(r.rows()).isEqualTo(r.scenario().rooms);
            }
        }
    }

    RunResult runOnce(Scenario s, Method m, int run) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("truncate exp_booking_plain, exp_booking_excl restart identity");
        }

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        Map<String, Integer> errors = new ConcurrentHashMap<>();
        long[] latencies = new long[REQUESTS];
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>(REQUESTS);
        long wallStart;
        long wallEnd;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < REQUESTS; i++) {
                int idx = i;
                long roomId = 1 + (i % s.rooms);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    // 지연 시간은 커넥션 대기를 포함한다. 사용자가 느끼는 시간에 가깝다.
                    long t0 = System.nanoTime();
                    try {
                        if (attempt(m, roomId)) {
                            ok.incrementAndGet();
                        } else {
                            conflict.incrementAndGet();
                        }
                    } catch (SQLException e) {
                        if ("23P01".equals(e.getSQLState())) {
                            conflict.incrementAndGet();
                        } else {
                            errors.merge(classify(e), 1, Integer::sum);
                        }
                    }
                    latencies[idx] = System.nanoTime() - t0;
                    return null;
                }));
            }
            ready.await();
            wallStart = System.nanoTime();
            go.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
            wallEnd = System.nanoTime();
        }

        int rows;
        int overlapped;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("select count(*) from " + m.table)) {
                rs.next();
                rows = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery(
                    "select count(*) from (select room_id from " + m.table + " group by room_id having count(*) > 1) x")) {
                rs.next();
                overlapped = rs.getInt(1);
            }
        }

        Arrays.sort(latencies);
        return new RunResult(s, m, run, ok.get(), conflict.get(), new TreeMap<>(errors), rows, overlapped,
                (wallEnd - wallStart) / 1e6, percentile(latencies, 50), percentile(latencies, 99));
    }

    /** true = 저장함, false = 겹쳐서 거절함. */
    static boolean attempt(Method m, long roomId) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                boolean saved = switch (m) {
                    case NONE -> {
                        if (countOverlapping(c, roomId) > 0) {
                            yield false;
                        }
                        insert(c, m.table, roomId);
                        yield true;
                    }
                    case FOR_UPDATE -> {
                        try (PreparedStatement ps = c.prepareStatement("select id from " + m.table
                                + " where room_id = ? and starts_at < ? and ends_at > ? for update")) {
                            bindOverlap(ps, roomId);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    yield false;
                                }
                            }
                        }
                        insert(c, m.table, roomId);
                        yield true;
                    }
                    case ADVISORY -> {
                        try (PreparedStatement ps = c.prepareStatement(
                                "select pg_advisory_xact_lock(hashtextextended('room:' || ? || ':' || ?::date, 0))")) {
                            ps.setLong(1, roomId);
                            ps.setString(2, DATE.toString());
                            ps.executeQuery().close();
                        }
                        if (countOverlapping(c, roomId) > 0) {
                            yield false;
                        }
                        insert(c, m.table, roomId);
                        yield true;
                    }
                    case EXCLUDE -> {
                        insert(c, m.table, roomId); // 겹치면 23P01
                        yield true;
                    }
                };
                c.commit();
                return saved;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    static int countOverlapping(Connection c, long roomId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "select count(*) from exp_booking_plain where room_id = ? and starts_at < ? and ends_at > ?")) {
            bindOverlap(ps, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static void bindOverlap(PreparedStatement ps, long roomId) throws SQLException {
        ps.setLong(1, roomId);
        ps.setTimestamp(2, Timestamp.from(END));
        ps.setTimestamp(3, Timestamp.from(START));
    }

    static void insert(Connection c, String table, long roomId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "insert into " + table + " (room_id, starts_at, ends_at) values (?, ?, ?)")) {
            ps.setLong(1, roomId);
            ps.setTimestamp(2, Timestamp.from(START));
            ps.setTimestamp(3, Timestamp.from(END));
            ps.executeUpdate();
        }
    }

    static String classify(SQLException e) {
        String state = e.getSQLState();
        if (state == null) {
            return "기타(" + e.getClass().getSimpleName() + ")";
        }
        return switch (state) {
            case "40P01" -> "40P01 교착";
            case "40001" -> "40001 직렬화 실패";
            default -> state;
        };
    }

    static double percentile(long[] sortedNanos, int p) {
        int idx = (int) Math.ceil(p / 100.0 * sortedNanos.length) - 1;
        return sortedNanos[Math.max(0, idx)] / 1e6;
    }

    static Map<String, String> environment() throws SQLException {
        Map<String, String> env = new java.util.LinkedHashMap<>();
        env.put("CPU", cpuName() + ", 논리 코어 " + Runtime.getRuntime().availableProcessors() + "개 (JVM 기준)");
        env.put("OS", System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        env.put("Java", System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("show server_version")) {
            rs.next();
            env.put("PostgreSQL", rs.getString(1) + " (Testcontainers postgres:17, 기본 설정)");
        }
        try {
            var info = DockerClientFactory.instance().client().infoCmd().exec();
            env.put("Docker", info.getServerVersion() + ", 엔진에 할당된 CPU " + info.getNCPU()
                    + "개, 메모리 " + (info.getMemTotal() / (1024 * 1024 * 1024)) + "GiB");
        } catch (RuntimeException e) {
            env.put("Docker", "정보를 읽지 못함");
        }
        env.put("커넥션 풀", "HikariCP, 최대 " + POOL_SIZE + "개 (최소 유휴도 같은 값), 커넥션 대기 한도 "
                + ds.getConnectionTimeout() / 1000 + "초");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("show deadlock_timeout")) {
            rs.next();
            env.put("deadlock_timeout", rs.getString(1) + " (기본값)");
        }
        return env;
    }

    static String cpuName() {
        try {
            Process p = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string").start();
            String name = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return name.isEmpty() ? "알 수 없음" : name;
        } catch (IOException e) {
            return "알 수 없음";
        }
    }
}
