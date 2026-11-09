-- #21 기존 사진 region_code 백필. 02_load_region.sql 이후 실행한다. 멱등.
-- 판정 규칙은 앱의 ExposedRegionAdapter.resolveEupMyeonDongCode 와 같다: ST_Covers 1차, 5km 이내 최근접 2차.

-- 1차: 읍면동 경계 안
-- 경계선 위 좌표는 앱 판정과 같도록 code 가 가장 작은 읍면동으로 정한다 (ORDER BY r.code)
UPDATE photo p
SET region_code = (
    SELECT r.code
    FROM region r
    WHERE r.level = 3
      AND ST_Covers(r.geom, p.location)
    ORDER BY r.code
    LIMIT 1
)
WHERE p.region_code IS NULL;

-- 2차: 해안/섬/경계 오차 (5km 이내 최근접 읍면동)
UPDATE photo p
SET region_code = (
    SELECT r.code
    FROM region r
    WHERE r.level = 3
      AND ST_DWithin(r.geom::geography, p.location::geography, 5000)
    ORDER BY r.geom <-> p.location, r.code
    LIMIT 1
)
WHERE p.region_code IS NULL;

-- 잔여 확인: 남은 사진은 지도 클러스터에서 제외된다 (z>=15 개별 사진에는 그대로 보인다)
SELECT count(*) AS unassigned_active
FROM photo
WHERE region_code IS NULL AND is_deleted = false;
