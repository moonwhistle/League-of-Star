-- Issue 92. Repeated LIGHTNING cooldown migration
-- Flyway is not used in this project. Apply manually only to an existing
-- local/dev schema that still has the old per-user one-action unique index.
--
-- New schemas should follow docs/DB/DDL.md and should not create this index.

ALTER TABLE game_actions
    DROP INDEX uk_game_room_user;
