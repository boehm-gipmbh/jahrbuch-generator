-- user_roles fehlt in V1 (war im Original-Dump nicht enthalten)
CREATE TABLE IF NOT EXISTS public.user_roles (
    id bigint NOT NULL,
    role character varying(255),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (id) REFERENCES public.users(id)
);
