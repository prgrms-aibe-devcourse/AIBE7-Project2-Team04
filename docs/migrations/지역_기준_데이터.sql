-- Region Entity와 프론트엔드 선택 목록이 사용하는 행정구역 기준 데이터를 등록합니다.
-- src/main/resources/import.sql과 동일한 코드·표시명·대표 좌표를 사용합니다.
-- 대표 좌표는 사용자의 실시간 위치가 아니며 Point는 경도, 위도 순서입니다.

INSERT INTO public.regions (
    region_code,
    city_do,
    sigungu,
    full_name,
    center_location
)
VALUES
    ('11110', '서울특별시', '종로구', '서울특별시 종로구', ST_SetSRID(ST_MakePoint(126.9783, 37.5730), 4326)::geography),
    ('11140', '서울특별시', '중구', '서울특별시 중구', ST_SetSRID(ST_MakePoint(126.9975, 37.5641), 4326)::geography),
    ('11170', '서울특별시', '용산구', '서울특별시 용산구', ST_SetSRID(ST_MakePoint(126.9810, 37.5311), 4326)::geography),
    ('11200', '서울특별시', '성동구', '서울특별시 성동구', ST_SetSRID(ST_MakePoint(127.0374, 37.5635), 4326)::geography),
    ('11440', '서울특별시', '마포구', '서울특별시 마포구', ST_SetSRID(ST_MakePoint(126.9016, 37.5662), 4326)::geography),
    ('11560', '서울특별시', '영등포구', '서울특별시 영등포구', ST_SetSRID(ST_MakePoint(126.8964, 37.5264), 4326)::geography),
    ('11650', '서울특별시', '서초구', '서울특별시 서초구', ST_SetSRID(ST_MakePoint(127.0324, 37.4836), 4326)::geography),
    ('11680', '서울특별시', '강남구', '서울특별시 강남구', ST_SetSRID(ST_MakePoint(127.0473, 37.5172), 4326)::geography),
    ('11740', '서울특별시', '강동구', '서울특별시 강동구', ST_SetSRID(ST_MakePoint(127.1238, 37.5301), 4326)::geography),
    ('41110', '경기도', '수원시', '경기도 수원시', ST_SetSRID(ST_MakePoint(127.0286, 37.2635), 4326)::geography),
    ('41130', '경기도', '성남시', '경기도 성남시', ST_SetSRID(ST_MakePoint(127.1189, 37.3827), 4326)::geography),
    ('41280', '경기도', '고양시', '경기도 고양시', ST_SetSRID(ST_MakePoint(126.7778, 37.6622), 4326)::geography),
    ('41460', '경기도', '용인시', '경기도 용인시', ST_SetSRID(ST_MakePoint(127.0975, 37.3223), 4326)::geography),
    ('28185', '인천광역시', '연수구', '인천광역시 연수구', ST_SetSRID(ST_MakePoint(126.6788, 37.4098), 4326)::geography),
    ('28200', '인천광역시', '남동구', '인천광역시 남동구', ST_SetSRID(ST_MakePoint(126.7314, 37.4472), 4326)::geography),
    ('26230', '부산광역시', '부산진구', '부산광역시 부산진구', ST_SetSRID(ST_MakePoint(129.0573, 35.1601), 4326)::geography),
    ('26350', '부산광역시', '해운대구', '부산광역시 해운대구', ST_SetSRID(ST_MakePoint(129.1636, 35.1631), 4326)::geography),
    ('27260', '대구광역시', '수성구', '대구광역시 수성구', ST_SetSRID(ST_MakePoint(128.6253, 35.8569), 4326)::geography),
    ('30200', '대전광역시', '유성구', '대전광역시 유성구', ST_SetSRID(ST_MakePoint(127.3563, 36.3622), 4326)::geography),
    ('29140', '광주광역시', '서구', '광주광역시 서구', ST_SetSRID(ST_MakePoint(126.8900, 35.1521), 4326)::geography),
    ('31140', '울산광역시', '남구', '울산광역시 남구', ST_SetSRID(ST_MakePoint(129.3300, 35.5438), 4326)::geography),
    ('36110', '세종특별자치시', '세종시', '세종특별자치시 세종시', ST_SetSRID(ST_MakePoint(127.2890, 36.4800), 4326)::geography),
    ('50110', '제주특별자치도', '제주시', '제주특별자치도 제주시', ST_SetSRID(ST_MakePoint(126.5312, 33.5007), 4326)::geography),
    ('50130', '제주특별자치도', '서귀포시', '제주특별자치도 서귀포시', ST_SetSRID(ST_MakePoint(126.5601, 33.2541), 4326)::geography)
ON CONFLICT (region_code) DO UPDATE
SET city_do = EXCLUDED.city_do,
    sigungu = EXCLUDED.sigungu,
    full_name = EXCLUDED.full_name,
    center_location = EXCLUDED.center_location;
