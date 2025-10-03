# Quiz Application (Java + MySQL)

## Overview
A Java Swing-based Quiz Application with:
- Student/Admin login
- Timed quizzes with MCQs
- Review mode with explanations
- Admin panel to manage users/questions
- MySQL database integration

## Setup
1. Import `quizdb.sql` into MySQL.
2. Update `DB_URL`, `DB_USER`, `DB_PASS` in `QuizApplication.java`.
3. Compile and run:
   ```bash
   javac src/QuizApplication.java
   java -cp .;lib/mysql-connector-j-8.0.xx.jar src/QuizApplication
