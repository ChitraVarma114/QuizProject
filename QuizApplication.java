import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;

public class QuizApplication extends JFrame {
    // ===== DB CONFIG =====
    static final String DB_URL = "jdbc:mysql://localhost:3306/quizdb?useSSL=false&serverTimezone=UTC";
    static final String DB_USER = "root";
    static final String DB_PASS = "1234"; // your password

    // ===== USER/STATE =====
    private int userId = -1;
    private String userRole = "";
    private String selectedSubject = "";
    private ArrayList<Question> questions;
    private int questionIndex = 0, score = 0, attemptCount = 0;
    private ArrayList<Integer> incorrectIndices = new ArrayList<>();
    private ArrayList<String> subjectsCache = new ArrayList<>();

    // ===== QUIZ UI =====
    private JLabel scoreLabel, timerLabel, questionLabel, progressLabel;
    private JProgressBar progressBar;
    private JRadioButton[] optionButtons;
    private ButtonGroup optionsGroup;
    private JButton nextButton;
    private javax.swing.Timer timer;
    private int timeLeft;
    private int timeLimit = 20; // seconds per question

    // ===== MODELS =====
    private static class Question {
        int id;
        String questionText, optionA, optionB, optionC, optionD, correctOption, explanation;
        String userAnswer = ""; // for review mode
        boolean wasIncorrect = false;
        Question(int id, String q, String a, String b, String c, String d, String correct, String exp) {
            this.id = id; questionText = q; optionA = a; optionB = b; optionC = c; optionD = d;
            correctOption = correct; explanation = exp;
        }
    }

    public QuizApplication() {
        setTitle("Quiz Application");
        setSize(1000, 720);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        showLoginScreen();
    }

    // ---------- SMALL UI HELPERS ----------
    private JPanel makeLabeledPanel(String label, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(8, 4));
        JLabel l = new JLabel(label, JLabel.RIGHT);
        l.setPreferredSize(new Dimension(140, 28));
        comp.setPreferredSize(new Dimension(260, 28));
        p.add(l, BorderLayout.WEST);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 14f));
        return b;
    }

    // ================= LOGIN =================
    private void showLoginScreen() {
        getContentPane().removeAll();
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(new EmptyBorder(40, 40, 40, 40));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 12, 12, 12);
        gbc.anchor = GridBagConstraints.LINE_END;

        JLabel title = new JLabel("Quiz Application – Login");
        title.setFont(new Font("Arial", Font.BOLD, 22));
        gbc.gridwidth = 2; gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.CENTER;
        root.add(title, gbc);

        gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.LINE_END;
        JLabel lblUser = new JLabel("Username:");
        JTextField tfUser = new JTextField(18);
        JLabel lblPass = new JLabel("Password:");
        JPasswordField pfPass = new JPasswordField(18);
        JLabel lblRole = new JLabel("Role:");
        JComboBox<String> cbRole = new JComboBox<>(new String[]{"student", "admin"});
        JButton btnLogin = primaryButton("Login");

        gbc.gridy = 1; gbc.gridx = 0; root.add(lblUser, gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START; root.add(tfUser, gbc);

        gbc.gridy = 2; gbc.gridx = 0; gbc.anchor = GridBagConstraints.LINE_END; root.add(lblPass, gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START; root.add(pfPass, gbc);

        gbc.gridy = 3; gbc.gridx = 0; gbc.anchor = GridBagConstraints.LINE_END; root.add(lblRole, gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START; root.add(cbRole, gbc);

        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        root.add(btnLogin, gbc);

        btnLogin.addActionListener(e -> {
            String username = tfUser.getText().trim();
            String password = new String(pfPass.getPassword());
            String role = (String) cbRole.getSelectedItem();
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter username and password", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                PreparedStatement pst = con.prepareStatement("SELECT user_id FROM user WHERE username=? AND password=? AND role=?");
                pst.setString(1, username); pst.setString(2, password); pst.setString(3, role);
                ResultSet rs = pst.executeQuery();
                if (rs.next()) {
                    userId = rs.getInt("user_id");
                    userRole = role;
                    // Prefetch subjects for faster UIs
                    subjectsCache = getSubjects();
                    if (userRole.equalsIgnoreCase("admin")) showAdminPanel();
                    else showQuizSelection();
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid username/password/role.", "Login Failed", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        setContentPane(root);
        revalidate(); repaint(); setVisible(true);
    }

    // ============ SUBJECT SELECTION ============
    private ArrayList<String> getSubjects() {
        ArrayList<String> list = new ArrayList<>();
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT DISTINCT subject FROM questions ORDER BY subject");
            while (rs.next()) list.add(rs.getString(1));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return list;
    }

    private void showQuizSelection() {
        getContentPane().removeAll();
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(new EmptyBorder(40, 60, 40, 60));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 20, 12, 20);

        JLabel title = new JLabel("Choose a Subject");
        title.setFont(new Font("Arial", Font.BOLD, 22));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; root.add(title, gbc);

        JLabel lblSubject = new JLabel("Select Subject:");
        JComboBox<String> cbSubjects = new JComboBox<>();
        (subjectsCache.isEmpty() ? getSubjects() : subjectsCache).forEach(cbSubjects::addItem);

        JLabel lblTime = new JLabel("Time per Question (sec):");
        JSpinner spTime = new JSpinner(new SpinnerNumberModel(timeLimit, 10, 120, 5));

        JButton btnStart = primaryButton("Start Quiz");
        JButton btnLogout = new JButton("Logout");

        gbc.gridwidth = 1; gbc.gridy = 1; gbc.gridx = 0; root.add(lblSubject, gbc);
        gbc.gridx = 1; root.add(cbSubjects, gbc);

        gbc.gridy = 2; gbc.gridx = 0; root.add(lblTime, gbc);
        gbc.gridx = 1; root.add(spTime, gbc);

        gbc.gridy = 3; gbc.gridx = 0; root.add(btnStart, gbc);
        gbc.gridx = 1; root.add(btnLogout, gbc);

        btnStart.addActionListener(e -> {
            Object sel = cbSubjects.getSelectedItem();
            if (sel == null) {
                JOptionPane.showMessageDialog(this, "No subjects found. Please contact admin.", "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            selectedSubject = sel.toString();
            timeLimit = (Integer) spTime.getValue();
            loadQuestions(selectedSubject);
            if (questions.size() == 0) {
                JOptionPane.showMessageDialog(this, "No questions found for this subject.", "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            showQuizScreen();
        });

        btnLogout.addActionListener(e -> {
            userId = -1; userRole = ""; selectedSubject = "";
            showLoginScreen();
        });

        setContentPane(root);
        revalidate(); repaint(); setVisible(true);
    }

    private void loadQuestions(String subject) {
        questions = new ArrayList<>();
        incorrectIndices.clear();
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            PreparedStatement pst = con.prepareStatement("SELECT * FROM questions WHERE subject = ?");
            pst.setString(1, subject);
            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                questions.add(new Question(
                        rs.getInt("question_id"),
                        rs.getString("question_text"),
                        rs.getString("option_a"),
                        rs.getString("option_b"),
                        rs.getString("option_c"),
                        rs.getString("option_d"),
                        rs.getString("correct_option"),
                        rs.getString("explanation")
                ));
            }
            Collections.shuffle(questions);
            if (questions.size() > 5) questions = new ArrayList<>(questions.subList(0, 5));
            questionIndex = 0; score = 0;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ============ QUIZ PANEL ============
    private void showQuizScreen() {
        getContentPane().removeAll();

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(new EmptyBorder(24, 36, 24, 36));

        // Top row: Score, Timer, Progress
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints tg = new GridBagConstraints();
        tg.insets = new Insets(4, 8, 4, 8);
        tg.gridx = 0; tg.gridy = 0; tg.anchor = GridBagConstraints.LINE_START;
        scoreLabel = new JLabel("Score: 0");
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 18));
        topPanel.add(scoreLabel, tg);

        tg.gridx = 1; timerLabel = new JLabel("Time left: " + timeLimit + "s");
        timerLabel.setFont(new Font("Arial", Font.BOLD, 18));
        topPanel.add(timerLabel, tg);

        tg.gridx = 2;
        progressLabel = new JLabel("Q 1/" + questions.size());
        progressLabel.setFont(new Font("Arial", Font.BOLD, 18));
        topPanel.add(progressLabel, tg);

        main.add(topPanel);
        main.add(Box.createVerticalStrut(8));

        progressBar = new JProgressBar(0, questions.size());
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        main.add(progressBar);

        main.add(Box.createVerticalStrut(16));

        // Question
        questionLabel = new JLabel();
        questionLabel.setFont(new Font("Arial", Font.PLAIN, 22));
        questionLabel.setBorder(new EmptyBorder(12, 0, 16, 0));
        questionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(questionLabel);

        // Options
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        optionButtons = new JRadioButton[4];
        optionsGroup = new ButtonGroup();
        Font optionFont = new Font("Arial", Font.PLAIN, 19);
        for (int i = 0; i < 4; i++) {
            optionButtons[i] = new JRadioButton();
            optionButtons[i].setFont(optionFont);
            optionButtons[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            optionsGroup.add(optionButtons[i]);
            optionsPanel.add(optionButtons[i]);
            optionsPanel.add(Box.createVerticalStrut(8));
        }
        main.add(optionsPanel);
        main.add(Box.createVerticalStrut(14));

        // Bottom
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        nextButton = primaryButton("Next");
        JButton quitBtn = new JButton("Quit");
        bottomPanel.add(quitBtn);
        bottomPanel.add(nextButton);
        main.add(bottomPanel);

        setContentPane(main);

        nextButton.addActionListener(e -> nextQuestion());
        quitBtn.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this, "End the quiz and submit?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                timer.stop();
                questionIndex = questions.size();
                endQuiz();
            }
        });

        displayQuestion();
        revalidate(); repaint(); setVisible(true);
    }

    private void displayQuestion() {
        if (questionIndex >= questions.size()) { endQuiz(); return; }
        Question q = questions.get(questionIndex);
        progressLabel.setText("Q " + (questionIndex + 1) + "/" + questions.size());
        progressBar.setValue(questionIndex);

        String qHtml = String.format("<html><body style='width: 750px'>Q%d: %s</body></html>", (questionIndex + 1), escapeHtml(q.questionText));
        questionLabel.setText(qHtml);
        optionButtons[0].setText("A. " + q.optionA);
        optionButtons[1].setText("B. " + q.optionB);
        optionButtons[2].setText("C. " + q.optionC);
        optionButtons[3].setText("D. " + q.optionD);
        optionsGroup.clearSelection();
        for (JRadioButton rb : optionButtons) rb.setEnabled(true);

        timeLeft = timeLimit;
        timerLabel.setForeground(Color.BLACK);
        timerLabel.setText("Time left: " + timeLeft + "s");
        startTimer();
    }

    private void startTimer() {
        if (timer != null) timer.stop();
        timer = new javax.swing.Timer(1000, e -> {
            timeLeft--;
            if (timeLeft <= 5) timerLabel.setForeground(new Color(180, 0, 0));
            timerLabel.setText("Time left: " + Math.max(timeLeft, 0) + "s");
            if (timeLeft <= 0) { timer.stop(); nextQuestion(); }
        });
        timer.start();
    }

    private void nextQuestion() {
        if (timer != null) timer.stop();
        Question q = questions.get(questionIndex);
        String selected = "";
        for (int i = 0; i < optionButtons.length; i++) {
            if (optionButtons[i].isSelected()) selected = "" + "ABCD".charAt(i);
        }
        q.userAnswer = selected; // store for review
        boolean correctAnswer = selected.equalsIgnoreCase(q.correctOption);
        if (correctAnswer) {
            score++;
            scoreLabel.setText("Score: " + score);
            q.wasIncorrect = false;
        } else {
            q.wasIncorrect = true;
            incorrectIndices.add(questionIndex);
        }
        questionIndex++;
        if (questionIndex >= questions.size()) endQuiz(); else displayQuestion();
    }

    private void endQuiz() {
        recordAttempt();
        progressBar.setValue(questions.size());

        // Post-quiz message
        String summaryHtml;
        if (incorrectIndices.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("<b>Explanations for incorrect answers:</b><br><br>");
            for (int i : incorrectIndices) {
                Question q = questions.get(i);
                sb.append("Q").append(i + 1).append(": ").append(escapeHtml(q.questionText)).append("<br>")
                  .append("<b>Your Answer:</b> ").append(q.userAnswer.isEmpty() ? "(None)" : q.userAnswer)
                  .append(" &nbsp; <b>Correct:</b> ").append(q.correctOption).append("<br>")
                  .append("<b>Explanation:</b> ").append(q.explanation != null ? escapeHtml(q.explanation) : "No explanation").append("<br><br>");
            }
            summaryHtml = sb.toString();
        } else {
            summaryHtml = "Amazing! All answers correct.";
        }
        String msg = "<html><h2>Your Score: " + score + "/" + questions.size() + "</h2>" +
                (score >= Math.ceil(questions.size() * 0.6) ? "<p>Qualified for next level.</p>" : "<p>Try again!</p>") +
                summaryHtml + "</html>";

        JLabel resultLabel = new JLabel(msg);
        resultLabel.setFont(new Font("Arial", Font.PLAIN, 15));
        JScrollPane scrollPane = new JScrollPane(resultLabel);
        scrollPane.setPreferredSize(new Dimension(760, 420));
        JOptionPane.showMessageDialog(this, scrollPane, "Quiz Results", JOptionPane.INFORMATION_MESSAGE);

        // Open Review Mode window
        openReviewWindow();

        // Back to selection
        showQuizSelection();
    }

    private void openReviewWindow() {
        JFrame review = new JFrame("Review Mode – " + selectedSubject);
        review.setSize(1000, 600);
        review.setLocationRelativeTo(this);

        String[] cols = {"#", "Question", "Your Answer", "Correct", "Result", "Explanation"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        int idx = 1;
        for (Question q : questions) {
            String result = q.userAnswer.equalsIgnoreCase(q.correctOption) ? "Correct" : "Wrong";
            model.addRow(new Object[]{
                    idx++,
                    q.questionText,
                    q.userAnswer.isEmpty() ? "(None)" : q.userAnswer,
                    q.correctOption,
                    result,
                    q.explanation == null ? "" : q.explanation
            });
        }

        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(420);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(5).setPreferredWidth(420);

        JScrollPane sp = new JScrollPane(table);

        JButton exportBtn = primaryButton("Export Review as CSV");
        exportBtn.addActionListener(e -> exportTableToCSV(table, "review_" + selectedSubject + ".csv"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel summary = new JLabel("Score: " + score + "/" + questions.size() + "   |   Subject: " + selectedSubject);
        summary.setFont(summary.getFont().deriveFont(Font.BOLD, 14f));
        top.add(summary);
        top.add(exportBtn);

        review.setLayout(new BorderLayout(8, 8));
        review.add(top, BorderLayout.NORTH);
        review.add(sp, BorderLayout.CENTER);
        review.setVisible(true);
    }

    private void recordAttempt() {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            PreparedStatement pst = con.prepareStatement(
                    "INSERT INTO quiz_attempts(user_id, subject, score, total_questions, attempt_time) VALUES (?, ?, ?, ?, NOW())");
            pst.setInt(1, userId);
            pst.setString(2, selectedSubject);
            pst.setInt(3, score);
            pst.setInt(4, questions.size());
            pst.executeUpdate();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save attempt: " + ex.getMessage(),
                    "DB Error", JOptionPane.ERROR_MESSAGE);
        }

        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            PreparedStatement pst = con.prepareStatement(
                    "SELECT COUNT(*) AS cnt FROM quiz_attempts WHERE user_id=? AND subject=?");
            pst.setInt(1, userId);
            pst.setString(2, selectedSubject);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) attemptCount = rs.getInt("cnt");
            JOptionPane.showMessageDialog(this, "Total attempts for this subject: " + attemptCount);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to get attempts count: " + ex.getMessage(),
                    "DB Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ============== ADMIN PANEL (TABS) ==============
    private void showAdminPanel() {
        getContentPane().removeAll();

        JTabbedPane tabs = new JTabbedPane();

        tabs.addTab("Questions", buildQuestionsTab());
        tabs.addTab("Users", buildUsersTab());
        tabs.addTab("Attempts", buildAttemptsTab());

        JButton back = new JButton("Logout");
        back.addActionListener(e -> showLoginScreen());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(back);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(tabs, BorderLayout.CENTER);
        wrap.add(south, BorderLayout.SOUTH);

        setContentPane(wrap);
        revalidate(); repaint(); setVisible(true);
    }

    // --- Questions Tab ---
    private JPanel buildQuestionsTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Top filter
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel lblSubject = new JLabel("Subject:");
        JComboBox<String> cbSubjects = new JComboBox<>();
        (subjectsCache.isEmpty() ? getSubjects() : subjectsCache).forEach(cbSubjects::addItem);
        cbSubjects.insertItemAt("All", 0);
        cbSubjects.setSelectedIndex(0);

        JLabel lblSearch = new JLabel("Search:");
        JTextField tfSearch = new JTextField(18);
        JButton btnRefresh = new JButton("Refresh");
        JButton btnExport = new JButton("Export CSV");

        filter.add(lblSubject); filter.add(cbSubjects);
        filter.add(lblSearch); filter.add(tfSearch);
        filter.add(btnRefresh); filter.add(btnExport);

        // Table
        String[] cols = {"ID", "Subject", "Question", "A", "B", "C", "D", "Correct", "Explanation"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {60, 120, 380, 140, 140, 140, 140, 80, 240};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane sp = new JScrollPane(table);

        // Form (add/remove)
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(0, 8, 0, 0));

        JTextField tfSubject = new JTextField();
        JTextField tfQuestion = new JTextField();
        JTextField tfA = new JTextField(); JTextField tfB = new JTextField();
        JTextField tfC = new JTextField(); JTextField tfD = new JTextField();
        JComboBox<String> cbCorrect = new JComboBox<>(new String[]{"A", "B", "C", "D"});
        JTextField tfExplanation = new JTextField();
        JTextField tfRemoveId = new JTextField();

        JButton btnAdd = primaryButton("Add Question");
        JButton btnRemove = new JButton("Remove by ID");

        form.add(makeLabeledPanel("Subject:", tfSubject));
        form.add(makeLabeledPanel("Question:", tfQuestion));
        form.add(makeLabeledPanel("Option A:", tfA));
        form.add(makeLabeledPanel("Option B:", tfB));
        form.add(makeLabeledPanel("Option C:", tfC));
        form.add(makeLabeledPanel("Option D:", tfD));
        form.add(makeLabeledPanel("Correct:", cbCorrect));
        form.add(makeLabeledPanel("Explanation:", tfExplanation));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(btnAdd);
        form.add(btns);

        form.add(Box.createVerticalStrut(10));
        form.add(makeLabeledPanel("Remove Question ID:", tfRemoveId));
        JPanel btns2 = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns2.add(btnRemove);
        form.add(btns2);

        // Layout
        panel.add(filter, BorderLayout.NORTH);
        panel.add(sp, BorderLayout.CENTER);
        panel.add(form, BorderLayout.EAST);

        // Actions
        Runnable loadTable = () -> {
            model.setRowCount(0);
            String subject = cbSubjects.getSelectedItem() == null ? "All" : cbSubjects.getSelectedItem().toString();
            String keyword = tfSearch.getText().trim().toLowerCase();
            String sql = "SELECT * FROM questions";
            boolean whereAdded = false;
            if (!"All".equalsIgnoreCase(subject)) {
                sql += " WHERE subject = ?";
                whereAdded = true;
            }
            if (!keyword.isEmpty()) {
                sql += whereAdded ? " AND " : " WHERE ";
                sql += "(LOWER(question_text) LIKE ? OR LOWER(option_a) LIKE ? OR LOWER(option_b) LIKE ? OR LOWER(option_c) LIKE ? OR LOWER(option_d) LIKE ?)";
            }
            sql += " ORDER BY question_id";
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                PreparedStatement pst = con.prepareStatement(sql);
                int idx = 1;
                if (!"All".equalsIgnoreCase(subject)) pst.setString(idx++, subject);
                if (!keyword.isEmpty()) {
                    String like = "%" + keyword + "%";
                    pst.setString(idx++, like); pst.setString(idx++, like); pst.setString(idx++, like); pst.setString(idx++, like); pst.setString(idx++, like);
                }
                ResultSet rs = pst.executeQuery();
                while (rs.next()) {
                    model.addRow(new Object[]{
                            rs.getInt("question_id"),
                            rs.getString("subject"),
                            rs.getString("question_text"),
                            rs.getString("option_a"),
                            rs.getString("option_b"),
                            rs.getString("option_c"),
                            rs.getString("option_d"),
                            rs.getString("correct_option"),
                            rs.getString("explanation")
                    });
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            }
        };
        loadTable.run();

        btnRefresh.addActionListener(e -> loadTable.run());
        cbSubjects.addActionListener(e -> loadTable.run());
        tfSearch.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { loadTable.run(); }
        });

        btnExport.addActionListener(e -> exportTableToCSV(table, "questions_export.csv"));

        btnAdd.addActionListener(e -> {
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
		if (tfSubject.getText().trim().isEmpty() ||
    			tfQuestion.getText().trim().isEmpty() ||
    			tfA.getText().trim().isEmpty() ||
    			tfB.getText().trim().isEmpty() ||
    			tfC.getText().trim().isEmpty() ||
    			tfD.getText().trim().isEmpty() ||
    			cbCorrect.getSelectedItem() == null ||
    			tfExplanation.getText().trim().isEmpty()) {
    
    			JOptionPane.showMessageDialog(this, "All fields (Subject, Question, Options, Correct Answer, Explanation) are required!", "Input Error", JOptionPane.ERROR_MESSAGE);
    			return;
		}

                PreparedStatement pst = con.prepareStatement(
                        "INSERT INTO questions(subject, question_text, option_a, option_b, option_c, option_d, correct_option, explanation) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
                pst.setString(1, tfSubject.getText().trim());
                pst.setString(2, tfQuestion.getText().trim());
                pst.setString(3, tfA.getText().trim()); pst.setString(4, tfB.getText().trim());
                pst.setString(5, tfC.getText().trim()); pst.setString(6, tfD.getText().trim());
                pst.setString(7, (String) cbCorrect.getSelectedItem()); pst.setString(8, tfExplanation.getText().trim());
                pst.executeUpdate();
                JOptionPane.showMessageDialog(this, "Question added!");
                if (!subjectsCache.contains(tfSubject.getText().trim())) subjectsCache = getSubjects();
                tfSubject.setText(""); tfQuestion.setText(""); tfA.setText(""); tfB.setText("");
                tfC.setText(""); tfD.setText(""); tfExplanation.setText("");
                loadTable.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnRemove.addActionListener(e -> {
            String idStr = tfRemoveId.getText().trim();
            if (idStr.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter an ID", "Input Error", JOptionPane.ERROR_MESSAGE); return; }
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                PreparedStatement pst = con.prepareStatement("DELETE FROM questions WHERE question_id = ?");
                pst.setInt(1, Integer.parseInt(idStr));
                int rows = pst.executeUpdate();
                JOptionPane.showMessageDialog(this, rows > 0 ? "Removed!" : "ID not found.");
                loadTable.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        return panel;
    }

    // --- Users Tab ---
    private JPanel buildUsersTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JTextField tfUsername = new JTextField();
        JTextField tfPassword = new JTextField();
        JComboBox<String> cbRole = new JComboBox<>(new String[]{"student", "admin"});
        JButton btnAddUser = primaryButton("Add User");

        JTextField tfRemoveUsername = new JTextField();
        JButton btnRemoveUser = new JButton("Remove User");

        form.add(makeLabeledPanel("Username:", tfUsername));
        form.add(makeLabeledPanel("Password:", tfPassword));
        form.add(makeLabeledPanel("Role:", cbRole));
        JPanel p1 = new JPanel(new FlowLayout(FlowLayout.RIGHT)); p1.add(btnAddUser); form.add(p1);

        form.add(Box.createVerticalStrut(8));
        form.add(makeLabeledPanel("Remove Username:", tfRemoveUsername));
        JPanel p2 = new JPanel(new FlowLayout(FlowLayout.RIGHT)); p2.add(btnRemoveUser); form.add(p2);

        // List table
        String[] cols = {"User ID", "Username", "Role"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        JScrollPane sp = new JScrollPane(table);

        JButton btnRefresh = new JButton("Refresh");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT)); top.add(btnRefresh);

        Runnable loadUsers = () -> {
            model.setRowCount(0);
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                ResultSet rs = con.createStatement().executeQuery("SELECT user_id, username, role FROM user ORDER BY user_id");
                while (rs.next()) model.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3)});
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            }
        };
        loadUsers.run();

        btnRefresh.addActionListener(e -> loadUsers.run());

        btnAddUser.addActionListener(e -> {
            String username = tfUsername.getText().trim();
            String password = tfPassword.getText().trim();
            String role = cbRole.getSelectedItem().toString();
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username & password required", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                PreparedStatement pst = con.prepareStatement("INSERT INTO user(username, password, role) VALUES (?, ?, ?)");
                pst.setString(1, username); pst.setString(2, password); pst.setString(3, role);
                pst.executeUpdate();
                JOptionPane.showMessageDialog(this, "User added!");
                tfUsername.setText(""); tfPassword.setText("");
                loadUsers.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnRemoveUser.addActionListener(e -> {
            String username = tfRemoveUsername.getText().trim();
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username required", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                PreparedStatement pst = con.prepareStatement("DELETE FROM user WHERE username=?");
                pst.setString(1, username);
                int affected = pst.executeUpdate();
                JOptionPane.showMessageDialog(this, affected > 0 ? "User removed!" : "Username not found");
                tfRemoveUsername.setText("");
                loadUsers.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        panel.add(top, BorderLayout.NORTH);
        panel.add(sp, BorderLayout.CENTER);
        panel.add(form, BorderLayout.EAST);
        return panel;
    }

    // --- Attempts Tab ---
// --- Attempts Tab ---
private JPanel buildAttemptsTab() {
    JPanel panel = new JPanel(new BorderLayout(12, 12));
    panel.setBorder(new EmptyBorder(16, 16, 16, 16));

    JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JTextField tfUsername = new JTextField(14);
    JComboBox<String> cbSubject = new JComboBox<>();
    (subjectsCache.isEmpty() ? getSubjects() : subjectsCache).forEach(cbSubject::addItem);
    cbSubject.insertItemAt("All", 0); cbSubject.setSelectedIndex(0);

    JButton btnLoad = new JButton("Load");
    JButton btnExport = new JButton("Export CSV");

    filter.add(new JLabel("Username:"));
    filter.add(tfUsername);
    filter.add(new JLabel("Subject:"));
    filter.add(cbSubject);
    filter.add(btnLoad);
    filter.add(btnExport);

    String[] cols = {"Username", "Subject", "Score", "Total", "Date/Time"};
    DefaultTableModel model = new DefaultTableModel(cols, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    JTable table = new JTable(model);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    int[] widths = {160, 160, 80, 80, 240};
    for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
    JScrollPane sp = new JScrollPane(table);

    Runnable loadAttempts = () -> {
        model.setRowCount(0);
        String uname = tfUsername.getText().trim();
        String subj = cbSubject.getSelectedItem() == null ? "All" : cbSubject.getSelectedItem().toString();

        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Check if user exists
            int uid = -1;
            if (!uname.isEmpty()) {
                PreparedStatement pstUser = con.prepareStatement("SELECT user_id FROM user WHERE username=?");
                pstUser.setString(1, uname);
                ResultSet rsUser = pstUser.executeQuery();
                if (rsUser.next()) uid = rsUser.getInt("user_id");
                else {
                    JOptionPane.showMessageDialog(this, "User not found: " + uname, "Info", JOptionPane.INFORMATION_MESSAGE);
                    return; // stop loading
                }
            }

            String sql = "SELECT u.username, qa.subject, qa.score, qa.total_questions, qa.attempt_time " +
                    "FROM quiz_attempts qa JOIN user u ON qa.user_id = u.user_id";
            boolean whereAdded = false;
            if (uid != -1) { sql += " WHERE u.user_id = ?"; whereAdded = true; }
            if (!"All".equalsIgnoreCase(subj)) {
                sql += whereAdded ? " AND" : " WHERE";
                sql += " qa.subject = ?";
            }
            sql += " ORDER BY qa.attempt_time DESC";

            PreparedStatement pst = con.prepareStatement(sql);
            int idx = 1;
            if (uid != -1) pst.setInt(idx++, uid);
            if (!"All".equalsIgnoreCase(subj)) pst.setString(idx++, subj);

            ResultSet rs = pst.executeQuery();
            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                model.addRow(new Object[]{
                        rs.getString(1),
                        rs.getString(2),
                        rs.getInt(3),
                        rs.getInt(4),
                        rs.getString(5)
                });
            }
            if (!hasRows) {
                JOptionPane.showMessageDialog(this, "No attempts found" + 
                        (!uname.isEmpty() ? " for user: " + uname : "") + 
                        (!"All".equalsIgnoreCase(subj) ? " and subject: " + subj : ""),
                        "Info", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
        }
    };

    btnLoad.addActionListener(e -> loadAttempts.run());
    btnExport.addActionListener(e -> exportTableToCSV(table, "attempts_export.csv"));

    panel.add(filter, BorderLayout.NORTH);
    panel.add(sp, BorderLayout.CENTER);

    return panel;
} 


    // ============== UTILS ==============
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void exportTableToCSV(JTable table, String suggestedName) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(suggestedName));
        int ret = fc.showSaveDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try (FileWriter fw = new FileWriter(f)) {
                // headers
                for (int c = 0; c < table.getColumnCount(); c++) {
                    fw.write(table.getColumnName(c));
                    if (c < table.getColumnCount() - 1) fw.write(",");
                }
                fw.write("\n");
                // rows
                for (int r = 0; r < table.getRowCount(); r++) {
                    for (int c = 0; c < table.getColumnCount(); c++) {
                        Object val = table.getValueAt(r, c);
                        String text = (val == null) ? "" : val.toString().replace("\"", "\"\"");
                        boolean needQuote = text.contains(",") || text.contains("\"") || text.contains("\n");
                        fw.write(needQuote ? ("\"" + text + "\"") : text);
                        if (c < table.getColumnCount() - 1) fw.write(",");
                    }
                    fw.write("\n");
                }
                fw.flush();
                JOptionPane.showMessageDialog(this, "Exported: " + f.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ============== MAIN ==============
    public static void main(String[] args) {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "MySQL Driver not found!", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        SwingUtilities.invokeLater(QuizApplication::new);
    }
}
