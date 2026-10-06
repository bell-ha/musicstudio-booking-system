package com.musicstudio.practice.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.practice.domain.Floor;
import com.musicstudio.practice.domain.FloorLayout;
import com.musicstudio.practice.domain.FloorRepository;
import com.musicstudio.practice.domain.PracticeBooking;
import com.musicstudio.practice.domain.PracticeBookingRepository;
import com.musicstudio.practice.domain.PracticePolicy;
import com.musicstudio.practice.domain.PracticeSettingsRepository;
import com.musicstudio.practice.domain.Room;
import com.musicstudio.practice.domain.RoomRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/** 층과 평면도, 방, 예약 정책 (UC-20, 21, 22). */
@Service
public class FloorPlanService {

    private static final int MAX_GRID = 100;

    private final FloorRepository floors;
    private final RoomRepository rooms;
    private final PracticeBookingRepository bookings;
    private final PracticeSettingsRepository settings;
    private final EntityManager entityManager;
    private final Clock clock;

    FloorPlanService(FloorRepository floors, RoomRepository rooms, PracticeBookingRepository bookings,
                     PracticeSettingsRepository settings, EntityManager entityManager, Clock clock) {
        this.floors = floors;
        this.rooms = rooms;
        this.bookings = bookings;
        this.settings = settings;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview(long orgId) {
        return new Overview(settings.findById(orgId).policy(),
                floors.findByOrganizationIdOrderBySortOrderAscIdAsc(orgId),
                rooms.findByOrganizationIdOrderByNameAsc(orgId));
    }

    // ---------- 정책 ----------

    @Transactional
    public PracticePolicy changePolicy(long orgId, PracticePolicy policy) {
        settings.findById(orgId).changePolicy(policy);
        return policy;
    }

    // ---------- 방 ----------

    @Transactional
    public Room createRoom(long orgId, String name, int capacity, List<String> equipment) {
        return saveRoom(new Room(orgId, name.trim(), capacity, equipment));
    }

    @Transactional
    public Room updateRoom(long orgId, long roomId, String name, Integer capacity, List<String> equipment,
                           Boolean bookable) {
        Room room = rooms.findByIdAndOrganizationId(roomId, orgId).orElseThrow(() -> ApiException.notFound("방을 찾을 수 없습니다"));
        room.update(name == null ? null : name.trim(), capacity, equipment, bookable);
        return saveRoom(room);
    }

    private Room saveRoom(Room room) {
        try {
            return rooms.saveAndFlush(room);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ROOM_NAME_TAKEN", "같은 이름의 방이 있습니다");
        }
    }

    // ---------- 층과 평면도 ----------

    @Transactional
    public Floor createFloor(long orgId, String name, int sortOrder, int width, int height) {
        if (width < 1 || height < 1 || width > MAX_GRID || height > MAX_GRID) {
            throw invalidLayout("격자 크기는 1~" + MAX_GRID + "칸입니다");
        }
        return floors.save(new Floor(orgId, name.trim(), sortOrder, width, height));
    }

    /**
     * 층 하나를 통째로 바꾼다 (UC-20). rooms는 이 층에 놓일 방 전체이고, 빠진 방은 평면도에서 빠진다.
     * expectedVersion은 클라이언트가 읽어 간 버전(If-Match)이다.
     */
    @Transactional
    public Floor saveFloor(long orgId, long floorId, Long expectedVersion, String name, int sortOrder,
                           FloorLayout layout, List<RoomPlacement> placements) {
        if (expectedVersion == null) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "precondition-required", "IF_MATCH_REQUIRED",
                    "If-Match 헤더가 필요합니다");
        }
        Floor floor = floors.findByIdAndOrganizationId(floorId, orgId).orElseThrow(() -> ApiException.notFound("층을 찾을 수 없습니다"));
        if (floor.getVersion() != expectedVersion) {
            throw stale(floor.getVersion());
        }

        Set<Long> ids = placements.stream().map(RoomPlacement::id).collect(Collectors.toSet());
        if (ids.size() != placements.size()) {
            throw invalidLayout("같은 방을 두 번 놓을 수 없습니다");
        }
        Map<Long, Room> placed = rooms.findByIdInAndOrganizationId(ids, orgId).stream()
                .collect(Collectors.toMap(Room::getId, Function.identity()));
        if (placed.size() != ids.size()) {
            throw invalidLayout("없는 방이 들어 있습니다");
        }
        validateLayout(layout, placements, placed);

        List<Room> removed = rooms.findByFloorId(floorId).stream().filter(r -> !ids.contains(r.getId())).toList();
        if (!removed.isEmpty()) {
            List<PracticeBooking> future = bookings.findFutureByRooms(
                    removed.stream().map(Room::getId).toList(), Instant.now(clock));
            if (!future.isEmpty()) {
                Map<Long, String> names = removed.stream().collect(Collectors.toMap(Room::getId, Room::getName));
                java.time.ZoneId zone = settings.findById(orgId).zone();
                throw ApiException.conflict("ROOM_HAS_FUTURE_BOOKINGS", "앞으로의 예약이 있는 방은 평면도에서 뺄 수 없습니다")
                        .with("bookings", future.stream().map(b -> Map.of(
                                "bookingId", b.getId(), "roomId", b.getRoomId(), "roomName", names.get(b.getRoomId()),
                                "startsAt", b.getStartsAt().atZone(zone).toOffsetDateTime(),
                                "endsAt", b.getEndsAt().atZone(zone).toOffsetDateTime())).toList());
            }
        }

        boolean floorChanged = floor.update(name.trim(), sortOrder, layout);
        removed.forEach(Room::unplace);
        placements.forEach(p -> placed.get(p.id()).place(floorId, p.x(), p.y(), p.w(), p.h()));
        if (!floorChanged) {
            // 방 배치만 바뀌어도 층 버전을 올려서, 다른 관리자의 낡은 화면이 덮어쓰지 못하게 한다.
            entityManager.lock(floor, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        try {
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
            throw stale(null); // 위 버전 비교와 커밋 사이에 다른 관리자가 먼저 저장했다
        }
        return floor;
    }

    /**
     * 격자 한 장에 칠해 가며 확인한다: 격자 안인지, 벽이면서 복도인 칸, 방끼리 또는 방과 벽·복도의 겹침.
     */
    static void validateLayout(FloorLayout layout, List<RoomPlacement> placements, Map<Long, Room> rooms) {
        int width = layout.width();
        int height = layout.height();
        if (width < 1 || height < 1 || width > MAX_GRID || height > MAX_GRID) {
            throw invalidLayout("격자 크기는 1~" + MAX_GRID + "칸입니다");
        }
        String[][] cell = new String[width][height];
        for (List<Integer> c : layout.walls()) {
            int[] xy = inGrid(c, width, height, "벽");
            cell[xy[0]][xy[1]] = "벽";
        }
        for (List<Integer> c : layout.corridors()) {
            int[] xy = inGrid(c, width, height, "복도");
            if ("벽".equals(cell[xy[0]][xy[1]])) {
                throw invalidLayout("(%d, %d) 칸이 벽이면서 복도입니다".formatted(xy[0], xy[1]));
            }
            cell[xy[0]][xy[1]] = "복도";
        }
        for (RoomPlacement p : placements) {
            String name = rooms.get(p.id()).getName();
            if (p.w() < 1 || p.h() < 1 || p.x() < 0 || p.y() < 0 || p.x() + p.w() > width || p.y() + p.h() > height) {
                throw invalidLayout("방 " + name + "의 자리가 격자를 벗어납니다");
            }
            for (int x = p.x(); x < p.x() + p.w(); x++) {
                for (int y = p.y(); y < p.y() + p.h(); y++) {
                    if (cell[x][y] != null) {
                        throw invalidLayout("방 " + name + "의 자리에 이미 " + cell[x][y] + "이 있습니다");
                    }
                    cell[x][y] = name;
                }
            }
        }
    }

    private static int[] inGrid(List<Integer> c, int width, int height, String what) {
        if (c == null || c.size() != 2 || c.get(0) < 0 || c.get(1) < 0 || c.get(0) >= width || c.get(1) >= height) {
            throw invalidLayout(what + " 칸 " + c + "의 위치가 격자를 벗어납니다");
        }
        return new int[] {c.get(0), c.get(1)};
    }

    private static ApiException invalidLayout(String title) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-layout", "LAYOUT_INVALID", title);
    }

    private static ApiException stale(Long currentVersion) {
        ApiException e = new ApiException(HttpStatus.PRECONDITION_FAILED, "stale-version", "STALE_VERSION",
                "다른 관리자가 먼저 저장했습니다. 다시 불러와 주세요");
        return currentVersion == null ? e : e.with("currentVersion", currentVersion);
    }

    public record RoomPlacement(long id, int x, int y, int w, int h) {
    }

    public record Overview(PracticePolicy policy, List<Floor> floors, List<Room> rooms) {
    }
}
