package com.musicstudio.practice.api;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.organization.api.CurrentMember;
import com.musicstudio.organization.api.OrgRole;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.practice.application.FloorPlanService;
import com.musicstudio.practice.application.FloorPlanService.RoomPlacement;
import com.musicstudio.practice.domain.Floor;
import com.musicstudio.practice.domain.FloorLayout;
import com.musicstudio.practice.domain.PracticePolicy;
import com.musicstudio.practice.domain.Room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 연습실 층·평면도·방·정책 (API 13~18). */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/practice")
class FloorPlanController {

    private final FloorPlanService service;

    FloorPlanController(FloorPlanService service) {
        this.service = service;
    }

    @GetMapping("/floors")
    @OrgRole
    OverviewResponse floors(CurrentMember me) {
        FloorPlanService.Overview overview = service.overview(me.organizationId());
        boolean manager = me.role() == MembershipRole.OWNER || me.role() == MembershipRole.MANAGER;
        List<FloorResponse> floors = overview.floors().stream()
                .map(f -> FloorResponse.of(f, overview.rooms().stream()
                        .filter(r -> Objects.equals(r.getFloorId(), f.getId())).toList()))
                .toList();
        List<RoomResponse> unplaced = manager
                ? overview.rooms().stream().filter(r -> r.getFloorId() == null).map(RoomResponse::of).toList()
                : List.of();
        return new OverviewResponse(overview.policy(), floors, unplaced);
    }

    @PostMapping("/floors")
    @OrgRole(MembershipRole.MANAGER)
    ResponseEntity<FloorResponse> createFloor(CurrentMember me, @Valid @RequestBody CreateFloorRequest req) {
        Floor floor = service.createFloor(me.organizationId(), req.name(),
                req.sortOrder() == null ? 0 : req.sortOrder(),
                req.width() == null ? Floor.DEFAULT_WIDTH : req.width(),
                req.height() == null ? Floor.DEFAULT_HEIGHT : req.height());
        return ResponseEntity.status(HttpStatus.CREATED).eTag(Long.toString(floor.getVersion()))
                .body(FloorResponse.of(floor, List.of()));
    }

    @PutMapping("/floors/{floorId}")
    @OrgRole(MembershipRole.MANAGER)
    ResponseEntity<FloorResponse> saveFloor(CurrentMember me, @PathVariable long floorId,
                                            @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                            @Valid @RequestBody SaveFloorRequest req) {
        Floor floor = service.saveFloor(me.organizationId(), floorId, version(ifMatch), req.name(), req.sortOrder(),
                req.layout(), req.rooms());
        List<Room> rooms = service.overview(me.organizationId()).rooms().stream()
                .filter(r -> Objects.equals(r.getFloorId(), floorId)).toList();
        return ResponseEntity.ok().eTag(Long.toString(floor.getVersion())).body(FloorResponse.of(floor, rooms));
    }

    @PostMapping("/rooms")
    @OrgRole(MembershipRole.MANAGER)
    ResponseEntity<RoomResponse> createRoom(CurrentMember me, @Valid @RequestBody CreateRoomRequest req) {
        Room room = service.createRoom(me.organizationId(), req.name(), req.capacity() == null ? 1 : req.capacity(),
                req.equipment() == null ? List.of() : req.equipment());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.of(room));
    }

    @PatchMapping("/rooms/{roomId}")
    @OrgRole(MembershipRole.MANAGER)
    RoomResponse updateRoom(CurrentMember me, @PathVariable long roomId, @Valid @RequestBody UpdateRoomRequest req) {
        return RoomResponse.of(service.updateRoom(me.organizationId(), roomId, req.name(), req.capacity(),
                req.equipment(), req.bookable()));
    }

    @PutMapping("/policy")
    @OrgRole(MembershipRole.MANAGER)
    PracticePolicy changePolicy(CurrentMember me, @RequestBody PracticePolicy policy) {
        return service.changePolicy(me.organizationId(), policy);
    }

    /** If-Match: "5", 5, W/"5" 모두 받는다. 숫자가 아니면 낡은 버전으로 본다. */
    private static Long version(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String v = ifMatch.replace("W/", "").replace("\"", "").trim();
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** 크기를 빼면 {@link Floor#DEFAULT_WIDTH}×{@link Floor#DEFAULT_HEIGHT} */
    record CreateFloorRequest(@NotBlank @Size(max = 50) String name, Integer sortOrder,
                              Integer width, Integer height) {
    }

    record SaveFloorRequest(@NotBlank @Size(max = 50) String name, int sortOrder,
                            @NotNull FloorLayout layout, @NotNull List<RoomPlacement> rooms) {
    }

    record CreateRoomRequest(@NotBlank @Size(max = 50) String name, @Min(1) Integer capacity, List<String> equipment) {
    }

    record UpdateRoomRequest(@Size(min = 1, max = 50) String name, @Min(1) Integer capacity, List<String> equipment,
                             Boolean bookable) {
    }

    record OverviewResponse(PracticePolicy policy, List<FloorResponse> floors, List<RoomResponse> unplacedRooms) {
    }

    record FloorResponse(Long id, String name, int sortOrder, long version, FloorLayout layout,
                         List<RoomResponse> rooms) {
        static FloorResponse of(Floor f, List<Room> rooms) {
            return new FloorResponse(f.getId(), f.getName(), f.getSortOrder(), f.getVersion(), f.getLayout(),
                    rooms.stream().map(RoomResponse::of).toList());
        }
    }

    record RoomResponse(Long id, String name, int capacity, List<String> equipment, boolean bookable,
                        Long floorId, Short x, Short y, Short w, Short h) {
        static RoomResponse of(Room r) {
            return new RoomResponse(r.getId(), r.getName(), r.getCapacity(), r.getEquipment(), r.isBookable(),
                    r.getFloorId(), r.getX(), r.getY(), r.getW(), r.getH());
        }
    }
}
