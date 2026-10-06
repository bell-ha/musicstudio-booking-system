package com.musicstudio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.musicstudio.support.IntegrationTest;

@IntegrationTest
class MusicstudioApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 애플리케이션이_뜨고_마이그레이션이_적용된다() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isPositive();
    }

    @Test
    void 배타_제약에_필요한_btree_gist_확장이_있다() {
        Boolean exists = jdbc.queryForObject(
                "select exists(select 1 from pg_extension where extname = 'btree_gist')", Boolean.class);
        assertThat(exists).isTrue();
    }
}
