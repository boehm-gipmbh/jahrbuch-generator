--
-- PostgreSQL database dump
--

-- Dumped from database version 17.4 (Debian 17.4-1.pgdg120+2)
-- Dumped by pg_dump version 17.4 (Debian 17.4-1.pgdg120+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: bilder; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bilder (
    priority integer,
    version integer NOT NULL,
    complete timestamp(6) with time zone,
    created timestamp(6) with time zone NOT NULL,
    id bigint NOT NULL,
    story_id bigint,
    user_id bigint NOT NULL,
    description character varying(1000),
    pfad character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    "position" integer,
);


--
-- Name: stories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stories (
    version integer NOT NULL,
    created timestamp(6) with time zone NOT NULL,
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    description character varying(1000),
    name character varying(255) NOT NULL
);


--
-- Name: texte; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.texte (
    priority integer,
    version integer NOT NULL,
    complete timestamp(6) with time zone,
    created timestamp(6) with time zone NOT NULL,
    id bigint NOT NULL,
    story_id bigint,
    user_id bigint NOT NULL,
    description text,
    title character varying(255),
    "position" integer
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    version integer NOT NULL,
    created timestamp(6) with time zone NOT NULL,
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    password character varying(255) NOT NULL
);


--
-- Name: bilder bilder_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bilder
    ADD CONSTRAINT bilder_pkey PRIMARY KEY (id);


--
-- Name: stories stories_name_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stories
    ADD CONSTRAINT stories_name_user_id_key UNIQUE (name, user_id);


--
-- Name: stories stories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stories
    ADD CONSTRAINT stories_pkey PRIMARY KEY (id);


--
-- Name: texte texte_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.texte
    ADD CONSTRAINT texte_pkey PRIMARY KEY (id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_name_key UNIQUE (name);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: bilder fkcfqc4r13lthh8qohgggfafqkv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bilder
    ADD CONSTRAINT fkcfqc4r13lthh8qohgggfafqkv FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: texte fkgpbfwsg00sn2k27g7aot8an8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.texte
    ADD CONSTRAINT fkgpbfwsg00sn2k27g7aot8an8 FOREIGN KEY (story_id) REFERENCES public.stories(id);


--
-- Name: texte fkr6in5lpmvn57qp99qfevec75c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.texte
    ADD CONSTRAINT fkr6in5lpmvn57qp99qfevec75c FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: stories fkshv2ytgbsn9w9mpu43mc6ln6j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stories
    ADD CONSTRAINT fkshv2ytgbsn9w9mpu43mc6ln6j FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: bilder fkt26e7o7h7yfkgb1wusmq9vmom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bilder
    ADD CONSTRAINT fkt26e7o7h7yfkgb1wusmq9vmom FOREIGN KEY (story_id) REFERENCES public.stories(id);


--
-- PostgreSQL database dump complete
--

