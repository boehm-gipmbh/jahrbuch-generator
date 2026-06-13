-- Hibernate-Sequenz für Cluster-ID-Generierung (analog zu stories_seq, bilder_seq usw.)
CREATE SEQUENCE IF NOT EXISTS cluster_seq START WITH 1 INCREMENT BY 50;