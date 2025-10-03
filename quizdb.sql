CREATE DATABASE quizdb;
USE quizdb;

CREATE TABLE user (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(50) NOT NULL,
    role ENUM('student','admin') NOT NULL
);

CREATE TABLE questions (
    question_id INT AUTO_INCREMENT PRIMARY KEY,
    subject VARCHAR(50) NOT NULL,
    question_text TEXT NOT NULL,
    option_a VARCHAR(255),
    option_b VARCHAR(255),
    option_c VARCHAR(255),
    option_d VARCHAR(255),
    correct_option CHAR(1),
    explanation TEXT
);

CREATE TABLE quiz_attempts (
    attempt_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    subject VARCHAR(50),
    score INT,
    total_questions INT,
    attempt_time TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- Sample users
INSERT INTO user (username, password, role) VALUES ('admin', 'admin123', 'admin');
INSERT INTO user (username, password, role) VALUES ('student1', 'pass123', 'student');
