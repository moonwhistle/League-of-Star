-- Issue 90. League of Star naming migration
-- Flyway is not used in this project. Apply manually only to an existing local/dev
-- schema that still has the previous smite/dragon physical column names.

CREATE DATABASE IF NOT EXISTS league_of_star
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Run the ALTER statements below inside the existing application schema before
-- renaming the database, or update the schema name in the command to match the
-- current environment.
ALTER TABLE game_rooms
    CHANGE COLUMN dragon_max_hp star_core_max_hp INT NOT NULL DEFAULT 10000;

ALTER TABLE game_actions
    CHANGE COLUMN smite_time_ms lightning_time_ms INT NOT NULL,
    CHANGE COLUMN dragon_hp_at_smite star_core_hp_at_lightning INT NOT NULL;

-- Optional local-dev database rename. MySQL has no single RENAME DATABASE
-- command; dump/restore is the safest route:
--
-- mysqldump -u smite -p smite > smite.sql
-- mysql -u root -p -e "CREATE DATABASE league_of_star CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
-- mysql -u root -p league_of_star < smite.sql
