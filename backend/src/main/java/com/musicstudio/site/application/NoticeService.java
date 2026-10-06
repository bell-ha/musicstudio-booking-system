package com.musicstudio.site.application;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.site.domain.Notice;
import com.musicstudio.site.domain.NoticeRepository;
import com.musicstudio.site.domain.NoticeVisibility;

/** 공지 (UC-11, 12). 최근 50개만 주고 페이지는 나누지 않는다. */
@Service
public class NoticeService {

    private final NoticeRepository notices;
    private final Clock clock;

    NoticeService(NoticeRepository notices, Clock clock) {
        this.notices = notices;
        this.clock = clock;
    }

    /** 내 역할이 볼 수 있는 것만. 학생에게는 강사 이상 공지가 없다 */
    @Transactional(readOnly = true)
    public List<Notice> list(long orgId, MembershipRole role) {
        List<NoticeVisibility> visible = Arrays.stream(NoticeVisibility.values()).filter(v -> v.visibleTo(role)).toList();
        return notices.findTop50ByOrganizationIdAndVisibilityInOrderByPinnedDescCreatedAtDesc(orgId, visible);
    }

    @Transactional(readOnly = true)
    public List<Notice> publicNotices(long orgId) {
        return notices.findTop10ByOrganizationIdAndVisibilityOrderByPinnedDescCreatedAtDesc(orgId, NoticeVisibility.PUBLIC);
    }

    @Transactional
    public Notice create(long orgId, long authorMembershipId, String title, String body, NoticeVisibility visibility,
                         boolean pinned) {
        Notice notice = new Notice(orgId, authorMembershipId, clock.instant());
        notice.write(title.trim(), body, visibility, pinned, clock.instant());
        return notices.save(notice);
    }

    @Transactional
    public Notice update(long orgId, long noticeId, String title, String body, NoticeVisibility visibility,
                         boolean pinned) {
        Notice notice = find(orgId, noticeId);
        notice.write(title.trim(), body, visibility, pinned, clock.instant());
        return notice;
    }

    @Transactional
    public void delete(long orgId, long noticeId) {
        notices.delete(find(orgId, noticeId));
    }

    private Notice find(long orgId, long noticeId) {
        return notices.findByIdAndOrganizationId(noticeId, orgId).orElseThrow(() -> ApiException.notFound("공지를 찾을 수 없습니다"));
    }
}
