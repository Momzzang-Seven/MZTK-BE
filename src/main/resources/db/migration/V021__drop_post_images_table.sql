-- Drop the post_images table which is no longer needed.
-- Post images are now managed entirely by the images module with reference_id FK.
DROP TABLE IF EXISTS public.post_images;
