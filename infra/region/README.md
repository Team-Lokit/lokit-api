# 행정구역 기반 지도 클러스터링 (#21) — DB 적용 절차

지도 클러스터는 사진의 법정동 코드(`photo.region_code`, 8자리)로 묶인다.

| 줌 | 단위 | 코드 |
|---|---|---|
| `z < 8` | 시·도 | 앞 2자리 |
| `8 ≤ z < 12` | 시·군·구 (일반구는 시로 묶음) | 앞 5자리 → 시 승격 |
| `12 ≤ z < 15` | 읍·면·동 (법정동) | 8자리 |
| `z ≥ 15` | 개별 사진 | — |

어떤 읍면동에도 속하지 않는 좌표(경계 밖 5km 초과)의 사진은 업로드/위치수정이 `400 PHOTO_002` 로 거부된다.

## 파일

| 파일 | 내용 |
|---|---|
| `01_schema.sql` | `region` 테이블, `photo.region_code` 컬럼 (멱등) |
| `load_region.sh` | SHP 3종 → staging → `region` 적재 (`02_load_region.sql` 호출) |
| `02_load_region.sql` | staging → region 변환 (전체 교체, 한 트랜잭션) + 검증 쿼리 |
| `03_backfill_photo.sql` | 기존 사진 `region_code` 백필 (멱등) |

`(couple_id, region_code)` 인덱스는 `src/main/resources/db/indexes.sql` 에 있고 앱 기동 시 생성된다.

## 원천 데이터

법정구역 경계 SHP 3종 (시도 / 시군구 / 읍면동). 예: 도로명주소 전자지도의 `TL_SCCO_CTPRVN`, `TL_SCCO_SIG`, `TL_SCCO_EMD`
(EPSG:5179, CP949). 컬럼명·좌표계가 다르면 `load_region.sh` 상단의 환경변수로 바꾼다.

## 배포 순서 (반드시 이 순서)

region 데이터가 비어 있으면 **모든 업로드가 거부**되고(fail-closed), prod 는 `ddl-auto: validate` 라
`photo.region_code` 컬럼이 없으면 **앱이 기동하지 않는다**.

1. `psql "$DATABASE_URL" -f infra/region/01_schema.sql`
2. `DATABASE_URL=... infra/region/load_region.sh sido.shp sigungu.shp emd.shp`
   - 출력된 검증 쿼리 확인: orphan 0, `41111 → 41110`, `11680 → 11`
3. `psql "$DATABASE_URL" -f infra/region/03_backfill_photo.sql` — `unassigned_active` 확인
4. 앱 배포
5. 앱 기동 후 첫 지도 조회 때 region 카탈로그가 메모리에 로드된다 (빈 결과는 캐시하지 않으므로 2 를 늦게 해도 재기동 없이 복구된다).

`region` 테이블은 JPA 엔티티가 아니라서 어떤 프로필에서도 자동 생성되지 않는다.
- **dev** (`ddl-auto: update`): `photo.region_code` 컬럼은 생기지만 `region` 테이블이 없다. 1–2 를 실행하지 않으면
  업로드와 `zoom < 15` 지도 조회가 500 으로 실패한다. dev 배포 전에도 1–3 을 실행한다.
- **local** (`ddl-auto: create-drop`): Hibernate 는 엔티티 테이블만 지우고 만들므로 `region` 은 재기동해도 남는다. 로컬 DB 에 2 를 한 번 실행해 두면 된다(1 은 JPA 가 `photo.region_code` 를 만들어 주므로 region 테이블 생성 용도로만 필요).

## 데이터 갱신 (행정구역 개편 시)

`load_region.sh` 를 다시 실행하면 `region` 을 통째로 교체한다. 앱 인스턴스는 카탈로그를 메모리에 들고 있으므로
**재기동해야 새 카탈로그가 반영**된다. 이미 저장된 `photo.region_code` 가 폐지된 코드라면 `03_backfill_photo.sql` 의 조건을
`WHERE p.region_code IS NULL OR p.region_code NOT IN (SELECT code FROM region WHERE level = 3)` 로 바꿔 재배정한다.
