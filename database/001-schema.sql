SET NAMES utf8mb4;
CREATE TABLE visitors(id VARCHAR(64) PRIMARY KEY, token VARCHAR(64) UNIQUE NOT NULL);
CREATE TABLE templates(id VARCHAR(64) PRIMARY KEY,title VARCHAR(120) NOT NULL,category VARCHAR(60) NOT NULL,description VARCHAR(500) NOT NULL,required_image_count INT NOT NULL,color VARCHAR(16) NOT NULL);
CREATE TABLE assets(id VARCHAR(64) PRIMARY KEY, visitor_id VARCHAR(64) NOT NULL, mime VARCHAR(64) NOT NULL, size_bytes BIGINT NOT NULL, path VARCHAR(500) NOT NULL);
CREATE TABLE tasks(id VARCHAR(64) PRIMARY KEY,visitor_id VARCHAR(64) NOT NULL,template_id VARCHAR(64) NOT NULL,title VARCHAR(120) NOT NULL,status VARCHAR(20) NOT NULL,current_stage VARCHAR(30) NOT NULL,failure_stage VARCHAR(30),error_message VARCHAR(500),result_path VARCHAR(500),simulation TEXT NOT NULL,state_version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),finished_at TIMESTAMP(3) NULL,INDEX owner_created(visitor_id,created_at,id));
CREATE TABLE task_assets(task_id VARCHAR(64) NOT NULL,asset_id VARCHAR(64) NOT NULL,slot_index INT NOT NULL,PRIMARY KEY(task_id,slot_index));
CREATE TABLE task_steps(task_id VARCHAR(64) NOT NULL,stage VARCHAR(30) NOT NULL,sequence_no INT NOT NULL,status VARCHAR(20) NOT NULL,started_at TIMESTAMP(3) NULL,finished_at TIMESTAMP(3) NULL,error_message VARCHAR(500),PRIMARY KEY(task_id,stage));
