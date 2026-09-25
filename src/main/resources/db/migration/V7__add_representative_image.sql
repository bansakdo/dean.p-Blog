ALTER TABLE blog.post_detail ADD COLUMN representative_image_id uuid;
-- Only a file attached to this post can be selected. Clear selection before unlinking.
ALTER TABLE blog.post_detail ADD CONSTRAINT fk_post_representative_image
    FOREIGN KEY (id, representative_image_id)
    REFERENCES blog.post_attached_file (post_detail_id, attached_file_id);
