-- posts 테이블의 title 컬럼에서 NOT NULL 제약조건을 제거합니다.
ALTER TABLE public.posts ALTER COLUMN title DROP NOT NULL;