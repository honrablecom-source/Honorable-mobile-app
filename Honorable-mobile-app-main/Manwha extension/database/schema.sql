CREATE TABLE IF NOT EXISTS novels (
  id text PRIMARY KEY,
  title text NOT NULL,
  normalized_title text NOT NULL,
  novel_fire_rank integer,
  source_site text NOT NULL DEFAULT 'novelfire.net',
  source_url text NOT NULL,
  wiki_hostname text,
  wiki_verified boolean NOT NULL DEFAULT false,
  wiki_checked_at timestamptz,
  wiki_discovery_version integer NOT NULL DEFAULT 0,
  character_count integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS characters (
  character_key text PRIMARY KEY,
  novel_id text NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
  wiki_page_id bigint NOT NULL,
  page_title text NOT NULL,
  display_name text NOT NULL,
  normalized_name text NOT NULL,
  role_code text NOT NULL DEFAULT 'CHARACTER' CHECK (role_code IN ('MC','FMC','ANT','SUPPORT','CHARACTER')),
  image_url text,
  summary text,
  profile_fields jsonb NOT NULL DEFAULT '{}'::jsonb,
  wiki_path text NOT NULL,
  source_categories jsonb NOT NULL DEFAULT '[]'::jsonb,
  fetched_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (novel_id, wiki_page_id)
);

ALTER TABLE novels ADD COLUMN IF NOT EXISTS wiki_checked_at timestamptz;
ALTER TABLE novels ADD COLUMN IF NOT EXISTS wiki_discovery_version integer NOT NULL DEFAULT 0;
ALTER TABLE characters ADD COLUMN IF NOT EXISTS profile_fields jsonb NOT NULL DEFAULT '{}'::jsonb;

-- Keep one canonical character name per novel while allowing the same name in
-- different novels. Prefer the record with the richest profile when cleaning
-- databases created before this constraint existed.
WITH duplicate_characters AS (
  SELECT character_key,
    row_number() OVER (
      PARTITION BY novel_id, normalized_name
      ORDER BY (image_url IS NOT NULL) DESC, (summary IS NOT NULL) DESC,
        length(profile_fields::text) DESC, fetched_at DESC, character_key
    ) AS duplicate_rank
  FROM characters
)
DELETE FROM characters
WHERE character_key IN (SELECT character_key FROM duplicate_characters WHERE duplicate_rank > 1);

DROP INDEX IF EXISTS characters_novel_name_idx;
CREATE UNIQUE INDEX IF NOT EXISTS characters_novel_unique_name_idx ON characters (novel_id, normalized_name);

CREATE TABLE IF NOT EXISTS source_bindings (
  source_site text NOT NULL,
  source_slug text NOT NULL,
  novel_id text NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
  source_url text NOT NULL,
  source_rank integer,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (source_site, source_slug)
);

CREATE INDEX IF NOT EXISTS source_bindings_novel_idx ON source_bindings (novel_id);

CREATE TABLE IF NOT EXISTS character_aliases (
  novel_id text NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
  character_key text NOT NULL REFERENCES characters(character_key) ON DELETE CASCADE,
  alias text NOT NULL,
  normalized_alias text NOT NULL,
  spoiler_level smallint NOT NULL DEFAULT 0,
  PRIMARY KEY (character_key, normalized_alias)
);

CREATE INDEX IF NOT EXISTS aliases_novel_name_idx ON character_aliases (novel_id, normalized_alias);

CREATE TABLE IF NOT EXISTS sync_runs (
  id bigserial PRIMARY KEY,
  source text NOT NULL,
  started_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  novel_count integer NOT NULL DEFAULT 0,
  character_count integer NOT NULL DEFAULT 0,
  status text NOT NULL DEFAULT 'running',
  error text
);
