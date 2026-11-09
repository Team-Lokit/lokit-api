-- #21 행정구역 기반 지도 클러스터링: 스키마
-- 앱 배포 "전에" 실행한다 (prod 는 ddl-auto=validate 라 photo.region_code 컬럼이 없으면 기동 실패).
-- 멱등: 여러 번 실행해도 안전하다.

CREATE EXTENSION IF NOT EXISTS postgis;

-- 행정구역 기준 테이블 (JPA 엔티티 아님. Exposed raw SQL 로만 읽는다)
--   level 1 = 시도(코드 2자리), 2 = 시군구(5자리, 일반구 + 일반구를 가진 시 포함), 3 = 읍면동(8자리, 행정동코드 adm_cd2 앞 8자리)
--   parent_code: 읍면동 -> 원본 시군구(일반구 포함), 일반구 -> 시, 시/자치구/군 -> 시도, 시도 -> NULL
--   center: 마커 대표점 (ST_PointOnSurface, 항상 구역 내부)
--   west/south/east/north: 구역 envelope (지도 bbox 교차 판정용)
--   geom: 읍면동만 보관 (사진 좌표 -> 읍면동 판정용)
CREATE TABLE IF NOT EXISTS region (
    code        varchar(8)   PRIMARY KEY,
    level       smallint     NOT NULL CHECK (level IN (1, 2, 3)),
    name        varchar(100) NOT NULL,
    parent_code varchar(8)   NULL REFERENCES region (code),
    center      geometry(Point, 4326) NOT NULL,
    west        double precision NOT NULL,
    south       double precision NOT NULL,
    east        double precision NOT NULL,
    north       double precision NOT NULL,
    geom        geometry(MultiPolygon, 4326) NULL,
    CONSTRAINT region_geom_emd_only CHECK ((level = 3) = (geom IS NOT NULL)),
    CONSTRAINT region_code_length CHECK (length(code) = CASE level WHEN 1 THEN 2 WHEN 2 THEN 5 ELSE 8 END)
);

CREATE INDEX IF NOT EXISTS idx_region_geom_emd ON region USING gist (geom) WHERE level = 3;
CREATE INDEX IF NOT EXISTS idx_region_geog_emd ON region USING gist ((geom::geography)) WHERE level = 3;

-- 사진의 읍면동 코드 (8자리). 시도/시군구는 앞 2/5자리로 파생한다.
-- NULL 허용: 백필 후에도 구역 밖 기존 사진은 NULL 로 남고 지도 클러스터에서 제외된다.
ALTER TABLE photo ADD COLUMN IF NOT EXISTS region_code varchar(8) NULL;

-- (couple_id, region_code) 인덱스는 src/main/resources/db/indexes.sql 에 있으며 앱 기동 시 CONCURRENTLY 로 생성된다.
