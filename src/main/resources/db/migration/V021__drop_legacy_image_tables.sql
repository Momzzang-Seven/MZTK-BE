-- 레거시 이미지 테이블 일괄 제거: V021 (post_images) + V023 (answer_images) 통합.
-- 이미지 관리는 V015 의 통합 images 테이블 + reference_id 로 일원화됨.
-- answer_images 는 실제로 생성되지 않았을 수 있으므로 IF EXISTS 로 방어.

DROP TABLE IF EXISTS public.post_images;
DROP TABLE IF EXISTS public.answer_images;
