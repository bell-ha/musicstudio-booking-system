package com.musicstudio.experiment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import com.musicstudio.experiment.BookingOverlapExperimentTest.Method;
import com.musicstudio.experiment.BookingOverlapExperimentTest.RunResult;
import com.musicstudio.experiment.BookingOverlapExperimentTest.Scenario;

/**
 * 실험 결과를 Markdown 문서로 만든다. 해석은 사람이 쓴다.
 * 다시 돌려도 기존 문서의 수동 해석 블록은 그대로 둔다.
 */
final class Report {

    static final String MANUAL_START = "<!-- 수동 해석 시작 -->";
    static final String MANUAL_END = "<!-- 수동 해석 끝 -->";

    private Report() {
    }

    static String render(List<RunResult> results, Map<String, String> env) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 0001. 예약 겹침 방지 방식 비교\n\n");
        sb.append("| 실행일 | 관련 문서 |\n|---|---|\n");
        sb.append("| ").append(LocalDate.now()).append(" | [ADR 0010](../adr/0010-예약-겹침-방지.md) |\n\n");
        sb.append("> 이 문서는 `cd backend && ./gradlew experiment`가 만든다. 숫자는 손대지 않는다.\n\n");

        sb.append("## 목적\n");
        sb.append("같은 방, 같은 시간에 요청이 몰릴 때 겹친 예약이 저장되는지 본다. ");
        sb.append("ADR 0010의 후보 4가지를 같은 조건에서 재고, 막는지와 비용을 비교한다.\n\n");

        sb.append("## 환경\n");
        env.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        sb.append("- 부하 발생기(JVM)와 DB(Docker 컨테이너)가 한 대의 노트북에서 같이 돈다. 네트워크 지연이 거의 없고, CPU를 서로 나눠 쓴다.\n");
        sb.append("- 데이터 양: 실험 테이블은 매 회차 비우고 시작한다. 회차가 끝날 때 많아야 1,000행이다.\n\n");

        sb.append("## 방법\n");
        sb.append("- 요청 1,000건을 가상 스레드에 하나씩 올린다. 모두 준비되면 `CountDownLatch` 하나로 한꺼번에 놓는다.\n");
        sb.append("- 요청 하나 = 트랜잭션 하나. 격리 수준은 기본값 READ COMMITTED. 앱 서비스를 거치지 않고 JDBC로 직접 보낸다.\n");
        sb.append("- 모든 요청의 시간은 같다: ").append(BookingOverlapExperimentTest.START).append(" ~ ")
                .append(BookingOverlapExperimentTest.END).append(" (`[시작, 끝)`).\n");
        sb.append("- 방식 0~2는 배타 제약이 **없는** 테이블(`exp_booking_plain`)에 쓴다. 제약이 있으면 DB가 버그를 가린다. ");
        sb.append("방식 3만 배타 제약이 있는 테이블(`exp_booking_excl`)에 쓴다.\n");
        sb.append("- 시나리오 A: 1,000건 모두 같은 방. 정답은 저장 1행.\n");
        sb.append("- 시나리오 B: 방 50개에 20건씩. 정답은 저장 50행, 겹친 방 0개.\n");
        sb.append("- 방식마다 3회씩 돌린다. 회차마다 테이블을 비운다. 측정 전에 방식마다 시나리오 B로 워밍업 1회를 돌리고 버린다.\n\n");
        sb.append("| # | 방식 | 트랜잭션 안에서 하는 일 |\n|---|---|---|\n");
        sb.append("| 0 | 아무 조치 없음 | 겹치는 행 `count(*)` → 0이면 INSERT |\n");
        sb.append("| 1 | 겹치는 기존 행에 FOR UPDATE | 겹치는 행 `SELECT ... FOR UPDATE` → 없으면 INSERT |\n");
        sb.append("| 2 | advisory lock (방·날짜) | `pg_advisory_xact_lock(hashtextextended('room:'||방||':'||날짜, 0))` → 검사 → INSERT |\n");
        sb.append("| 3 | EXCLUDE 제약만 | 바로 INSERT. SQLState `23P01`이면 거절로 센다 |\n\n");
        sb.append("지표 뜻:\n");
        sb.append("- **저장된 행**: 끝난 뒤 테이블에 실제로 남은 행 수. 정확성의 기준이다.\n");
        sb.append("- **겹친 방**: 2행 이상 저장된 방의 수. 0이어야 한다.\n");
        sb.append("- **거절**: 검사에서 겹침을 보고 INSERT를 안 했거나, `23P01`을 받은 수.\n");
        sb.append("- **기타 오류**: 그 밖의 SQL 예외. SQLState로 나눈다 (`40P01` 교착, `40001` 직렬화 실패).\n");
        sb.append("- **p50·p99**: 요청 하나의 시간. 커넥션 풀 대기를 포함한다. 처리량 = 1,000 / 전체 시간.\n\n");

        sb.append("## 결과\n");
        for (Scenario s : Scenario.values()) {
            List<RunResult> rs = results.stream().filter(r -> r.scenario() == s).toList();
            sb.append("\n### 시나리오 ").append(s.id).append(". ").append(s.label)
                    .append(" (정답: 저장 ").append(s.rooms).append("행)\n\n");
            sb.append("요약 (3회의 최소~최대)\n\n");
            sb.append("| # | 방식 | 저장된 행 | 겹친 방 | 거절 | 기타 오류 | 처리량 (req/s) | p50 (ms) | p99 (ms) |\n");
            sb.append("|---|---|---|---|---|---|---|---|---|\n");
            for (Method m : Method.values()) {
                List<RunResult> ms = rs.stream().filter(r -> r.method() == m).toList();
                sb.append("| ").append(m.no).append(" | ").append(m.label)
                        .append(" | ").append(range(ms, r -> r.rows(), "%.0f"))
                        .append(" | ").append(range(ms, r -> r.overlappedRooms(), "%.0f"))
                        .append(" | ").append(range(ms, r -> r.conflicts(), "%.0f"))
                        .append(" | ").append(range(ms, r -> r.errorCount(), "%.0f"))
                        .append(" | ").append(range(ms, RunResult::throughput, "%.0f"))
                        .append(" | ").append(range(ms, RunResult::p50Ms, "%.1f"))
                        .append(" | ").append(range(ms, RunResult::p99Ms, "%.1f"))
                        .append(" |\n");
            }
            sb.append("\n회차별\n\n");
            sb.append("| # | 회차 | 저장된 행 | 겹친 방 | 앱이 본 성공 | 거절 | 기타 오류 | 전체 (ms) | 처리량 (req/s) | p50 (ms) | p99 (ms) |\n");
            sb.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
            for (RunResult r : rs) {
                sb.append("| ").append(r.method().no).append(" | ").append(r.run())
                        .append(" | ").append(r.rows())
                        .append(" | ").append(r.overlappedRooms())
                        .append(" | ").append(r.okSeen())
                        .append(" | ").append(r.conflicts())
                        .append(" | ").append(errors(r.errors()))
                        .append(" | ").append("%.0f".formatted(r.wallMs()))
                        .append(" | ").append("%.0f".formatted(r.throughput()))
                        .append(" | ").append("%.1f".formatted(r.p50Ms()))
                        .append(" | ").append("%.1f".formatted(r.p99Ms()))
                        .append(" |\n");
            }
        }

        sb.append("\n## 해석\n");
        sb.append(MANUAL_START).append("\n(실행 후 사람이 쓴다)\n").append(MANUAL_END).append("\n\n");

        sb.append("## 한계\n");
        sb.append("- 부하 발생기와 DB가 한 기계에 있다. 절대 수치(처리량, 지연)는 이 기계에서만 뜻이 있다. 방식끼리의 상대 비교로만 본다.\n");
        sb.append("- 네트워크 왕복이 거의 0이다. 실제 배포에서는 트랜잭션이 길어져 경쟁 구간이 넓어진다.\n");
        sb.append("- 회차가 3번뿐이다. 분산을 말하기에는 적다.\n");
        sb.append("- JDBC로 직접 잰다. JPA, 검증 로직, HTTP는 빠져 있다. R2(같은 사람), R4(하루 한도)는 재지 않았다.\n");
        sb.append("- PostgreSQL은 컨테이너 기본 설정이다. 튜닝하지 않았다.\n");
        return sb.toString();
    }

    /** 기존 문서에 수동 해석이 있으면 그것을 살린다. */
    static void write(Path out, String doc) throws IOException {
        Files.createDirectories(out.getParent());
        if (Files.exists(out)) {
            String old = Files.readString(out);
            int a = old.indexOf(MANUAL_START);
            int b = old.indexOf(MANUAL_END);
            int na = doc.indexOf(MANUAL_START);
            int nb = doc.indexOf(MANUAL_END);
            if (a >= 0 && b > a && na >= 0 && nb > na) {
                doc = doc.substring(0, na) + old.substring(a, b) + doc.substring(nb);
            }
        }
        Files.writeString(out, doc);
    }

    private static String range(List<RunResult> rs, ToDoubleFunction<RunResult> f, String fmt) {
        double min = rs.stream().mapToDouble(f).min().orElse(0);
        double max = rs.stream().mapToDouble(f).max().orElse(0);
        String lo = fmt.formatted(min);
        String hi = fmt.formatted(max);
        return lo.equals(hi) ? lo : lo + "~" + hi;
    }

    private static String errors(Map<String, Integer> errors) {
        if (errors.isEmpty()) {
            return "0";
        }
        return errors.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", "));
    }
}
