#!/bin/sh
# #21 법정 행정구역 경계 SHP -> PostGIS region 테이블 적재
#
# 사용법:
#   DATABASE_URL=postgres://user:pass@host:5432/db \
#   ./load_region.sh <시도.shp> <시군구.shp> <읍면동.shp>
#
# 필요 도구: shp2pgsql, psql (postgis 클라이언트)
# SHP 좌표계 기본값은 EPSG:5179. 다르면 SRC_SRID 로 지정한다.
# SHP 속성 컬럼명 기본값(도로명주소 전자지도 TL_SCCO_* 기준): CTPRVN_CD/CTP_KOR_NM, SIG_CD/SIG_KOR_NM, EMD_CD/EMD_KOR_NM
#   다르면 SIDO_CODE_COL 등 환경변수로 덮어쓴다.
# 원본 SHP 인코딩이 CP949 면 기본값 그대로 두면 된다 (SHP_ENCODING).

set -eu

if [ "$#" -ne 3 ]; then
	echo "usage: DATABASE_URL=... $0 <sido.shp> <sigungu.shp> <emd.shp>" >&2
	exit 1
fi
: "${DATABASE_URL:?DATABASE_URL 이 필요합니다}"

SIDO_SHP=$1
SIG_SHP=$2
EMD_SHP=$3
SRC_SRID=${SRC_SRID:-5179}
SHP_ENCODING=${SHP_ENCODING:-CP949}
SIDO_CODE_COL=${SIDO_CODE_COL:-ctprvn_cd}
SIDO_NAME_COL=${SIDO_NAME_COL:-ctp_kor_nm}
SIG_CODE_COL=${SIG_CODE_COL:-sig_cd}
SIG_NAME_COL=${SIG_NAME_COL:-sig_kor_nm}
EMD_CODE_COL=${EMD_CODE_COL:-emd_cd}
EMD_NAME_COL=${EMD_NAME_COL:-emd_kor_nm}

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

load_raw() {
	# $1 = shp, $2 = raw table
	shp2pgsql -d -s "${SRC_SRID}:4326" -W "$SHP_ENCODING" -g geom "$1" "$2" | psql "$DATABASE_URL" -q -v ON_ERROR_STOP=1
}

echo "[1/4] 스키마 확인"
psql "$DATABASE_URL" -q -v ON_ERROR_STOP=1 -f "$SCRIPT_DIR/01_schema.sql"

echo "[2/4] SHP 원본 적재"
load_raw "$SIDO_SHP" raw_sido
load_raw "$SIG_SHP" raw_sigungu
load_raw "$EMD_SHP" raw_emd

echo "[3/4] staging 정규화"
psql "$DATABASE_URL" -q -v ON_ERROR_STOP=1 <<SQL
DROP TABLE IF EXISTS stg_sido, stg_sigungu, stg_emd;
CREATE TABLE stg_sido    AS SELECT ${SIDO_CODE_COL}::varchar AS sido_cd, ${SIDO_NAME_COL}::varchar AS name, geom FROM raw_sido;
CREATE TABLE stg_sigungu AS SELECT ${SIG_CODE_COL}::varchar  AS sig_cd,  ${SIG_NAME_COL}::varchar  AS name, geom FROM raw_sigungu;
CREATE TABLE stg_emd     AS SELECT ${EMD_CODE_COL}::varchar  AS emd_cd,  ${EMD_NAME_COL}::varchar  AS name, geom FROM raw_emd;
SQL

echo "[4/4] region 적재 + 검증"
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$SCRIPT_DIR/02_load_region.sql"

psql "$DATABASE_URL" -q -v ON_ERROR_STOP=1 -c "DROP TABLE IF EXISTS raw_sido, raw_sigungu, raw_emd, stg_sido, stg_sigungu, stg_emd;"

echo "완료. 기존 사진 백필은 03_backfill_photo.sql 을 실행하세요."
