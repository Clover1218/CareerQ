alter table users add column education_public boolean not null default false;
alter table users add column public_bio varchar(500);
create index idx_questions_author_public on questions(author_id, scope, visibility, id);
