CREATE TABLE app_config (
  key VARCHAR(255) PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Initiale Werte aus application.properties
INSERT INTO app_config (key, value) VALUES
  ('jahrbuch.upload.max-size', '2097152'),
  ('jahrbuch.upload.allowed-types', '.jpg,.jpeg,.png,.gif,.bmp,.webp,.tiff,.tif'),
  ('jahrbuch.captures.path', '/data/captures/');