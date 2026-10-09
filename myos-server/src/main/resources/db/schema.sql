CREATE TABLE IF NOT EXISTS my_user (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  email VARCHAR(255),
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(100),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS goal (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  title VARCHAR(200) NOT NULL,
  description TEXT,
  category VARCHAR(64),
  start_date DATE,
  end_date DATE,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  progress INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  name VARCHAR(200) NOT NULL,
  description TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
  progress INT NOT NULL DEFAULT 0,
  start_date DATE,
  target_date DATE,
  github_url VARCHAR(500),
  demo_url VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  goal_id BIGINT REFERENCES goal(id),
  project_id BIGINT REFERENCES project(id),
  title VARCHAR(300) NOT NULL,
  description TEXT,
  priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  status VARCHAR(20) NOT NULL DEFAULT 'TODO',
  due_date DATE,
  completed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS daily_log (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  log_date DATE NOT NULL,
  log_type VARCHAR(32) NOT NULL,
  title VARCHAR(300) NOT NULL,
  content TEXT,
  duration_minutes INT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS learning_record (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  title VARCHAR(300) NOT NULL,
  category VARCHAR(64),
  source VARCHAR(500),
  content TEXT,
  understanding TEXT,
  practice TEXT,
  conclusion TEXT,
  duration_minutes INT,
  learned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS content_record (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  title VARCHAR(300) NOT NULL,
  platform VARCHAR(64),
  content TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  published_at TIMESTAMP,
  views INT NOT NULL DEFAULT 0,
  likes INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_item (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  title VARCHAR(300) NOT NULL,
  category VARCHAR(64),
  summary TEXT,
  source VARCHAR(500),
  content TEXT,
  conclusion TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS review (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  review_type VARCHAR(32) NOT NULL,
  review_date DATE NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  good_things TEXT,
  bad_things TEXT,
  wasted_time TEXT,
  unfinished TEXT,
  biggest_gain TEXT,
  next_focus TEXT,
  ai_summary TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_task_user_due_date ON task(user_id, due_date);
CREATE INDEX IF NOT EXISTS idx_learning_user_learned_at ON learning_record(user_id, learned_at);
CREATE INDEX IF NOT EXISTS idx_content_user_created_at ON content_record(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_project_user_status ON project(user_id, status);
