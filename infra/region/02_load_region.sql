-- #21 region 기준 데이터 적재 (staging -> region)
-- 선행: load_region.sh 가 SHP 3종을 stg_sido / stg_sigungu / stg_emd 로 적재해 둔 상태 (EPSG:4326 변환 완료).
--   stg_sido    (sido_cd varchar,  name varchar, geom geometry)
--   stg_sigungu (sig_cd varchar,   name varchar, geom geometry)
--   stg_emd     (emd_cd varchar,   name varchar, geom geometry)
-- 한 트랜잭션으로 전체 교체한다. photo 와 FK 가 없으므로 재적재해도 사진 데이터에 영향이 없다.

BEGIN;

TRUNCATE region;

-- 1) 읍면동 원본을 정리 (코드 8자리, 유효 MultiPolygon)
CREATE TEMP TABLE tmp_emd ON COMMIT DROP AS
SELECT LEFT(emd_cd, 8)                                                       AS code,
       MIN(name)                                                            AS name,
       ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_Union(geom)), 3))      AS geom
FROM stg_emd
GROUP BY LEFT(emd_cd, 8);

-- 2) 시도 (하위 읍면동 union 으로 center/envelope 계산 -> 시도 SHP 경계와 미세하게 다를 수 있으나 판정에는 읍면동만 쓴다)
INSERT INTO region (code, level, name, parent_code, center, west, south, east, north, geom)
SELECT s.code, 1, s.name, NULL,
       ST_PointOnSurface(u.geom), ST_XMin(u.geom), ST_YMin(u.geom), ST_XMax(u.geom), ST_YMax(u.geom), NULL
FROM (SELECT LEFT(sido_cd, 2) AS code, MIN(name) AS name FROM stg_sido GROUP BY LEFT(sido_cd, 2)) s
JOIN (SELECT LEFT(code, 2) AS code, ST_Union(geom) AS geom FROM tmp_emd GROUP BY LEFT(code, 2)) u
  ON u.code = s.code;

-- 3) 일반구를 가진 "시" 행 (예: 41111 수원시 장안구 -> 41110 수원시)
--    이름이 '...시...구' 형태면 일반구로 보고, 앞 4자리 + '0' 을 시 코드로 한다.
--    코드 끝자리로는 판별할 수 없다: 11215 광진구, 28177 미추홀구, 43745 증평군처럼 끝자리가 0이 아닌 자치구·군이 있다.
--    SHP 에 시 행이 있으면 그 이름을, 없으면 일반구 이름의 시 부분("수원시장안구" -> "수원시")을 쓴다.
--    center/envelope 은 하위 읍면동 union 으로 계산한다.
INSERT INTO region (code, level, name, parent_code, center, west, south, east, north, geom)
SELECT c.code, 2,
       COALESCE((SELECT s.name FROM stg_sigungu s WHERE LEFT(s.sig_cd, 5) = c.code LIMIT 1), c.fallback_name),
       LEFT(c.code, 2),
       ST_PointOnSurface(u.geom), ST_XMin(u.geom), ST_YMin(u.geom), ST_XMax(u.geom), ST_YMax(u.geom), NULL
FROM (
    SELECT LEFT(sig_cd, 4) || '0' AS code, MIN(substring(name from '^(.+?시).+구$')) AS fallback_name
    FROM stg_sigungu
    WHERE name ~ '^.+시.+구$'
    GROUP BY LEFT(sig_cd, 4)
) c
JOIN (SELECT LEFT(code, 4) || '0' AS code, ST_Union(geom) AS geom
      FROM tmp_emd
      GROUP BY LEFT(code, 4)) u
  ON u.code = c.code;

-- 4) 나머지 시군구 (일반구, 자치구, 군, 일반구 없는 시). 3) 에서 넣은 시 코드는 건너뛴다.
--    일반구('...시...구')의 parent 는 3) 에서 만든 시, 나머지는 시도.
INSERT INTO region (code, level, name, parent_code, center, west, south, east, north, geom)
SELECT g.code, 2, g.name,
       CASE WHEN g.name ~ '^.+시.+구$' THEN LEFT(g.code, 4) || '0' ELSE LEFT(g.code, 2) END,
       ST_PointOnSurface(u.geom), ST_XMin(u.geom), ST_YMin(u.geom), ST_XMax(u.geom), ST_YMax(u.geom), NULL
FROM (SELECT LEFT(sig_cd, 5) AS code, MIN(name) AS name FROM stg_sigungu GROUP BY LEFT(sig_cd, 5)) g
JOIN (SELECT LEFT(code, 5) AS code, ST_Union(geom) AS geom FROM tmp_emd GROUP BY LEFT(code, 5)) u
  ON u.code = g.code
WHERE NOT EXISTS (SELECT 1 FROM region r WHERE r.code = g.code);

-- 5) 읍면동
INSERT INTO region (code, level, name, parent_code, center, west, south, east, north, geom)
SELECT e.code, 3, e.name, LEFT(e.code, 5),
       ST_PointOnSurface(e.geom), ST_XMin(e.geom), ST_YMin(e.geom), ST_XMax(e.geom), ST_YMax(e.geom), e.geom
FROM tmp_emd e;

COMMIT;

-- 6) 검증 (모두 기대값이 나와야 한다)
SELECT level, count(*) FROM region GROUP BY level ORDER BY level;          -- 2026-10 기준 1: 16, 2: 시군구 SHP 행 수 + 일반구를 가진 시 수
SELECT count(*) AS orphan FROM region c                                    -- 0
WHERE c.parent_code IS NOT NULL AND NOT EXISTS (SELECT 1 FROM region p WHERE p.code = c.parent_code);
SELECT code, name, parent_code FROM region WHERE code IN ('11680', '41110', '41111', '36110');
--   11680 강남구 -> 11, 41110 수원시 -> 41, 41111 수원시 장안구 -> 41110, 36110 세종특별자치시 -> 36
