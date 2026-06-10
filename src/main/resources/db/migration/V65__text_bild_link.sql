CREATE TABLE text_bild_link (
    text_id BIGINT NOT NULL REFERENCES text(id) ON DELETE CASCADE,
    bild_id BIGINT NOT NULL REFERENCES bild(id) ON DELETE CASCADE,
    PRIMARY KEY (text_id, bild_id)
);

CREATE INDEX idx_text_bild_link_bild ON text_bild_link(bild_id);
