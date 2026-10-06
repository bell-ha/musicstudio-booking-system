package com.musicstudio.practice.domain;

import org.springframework.data.repository.Repository;

/** 읽기와 dirty checking 수정만 한다. save가 없어서 INSERT 경로가 생기지 않는다. */
public interface PracticeSettingsRepository extends Repository<PracticeSettings, Long> {

    PracticeSettings findById(Long organizationId);
}
