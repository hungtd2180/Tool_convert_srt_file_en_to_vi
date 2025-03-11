import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SubtitleTranslatorGUI {
    private static final String SOURCE_LANG = "en";
    private static final String TARGET_LANG = "vi";
    private JFrame frame;
    private JTextField folderPathField;
    private JTextField endWithTextField;
    private JPasswordField apiKeyPathField;
    private JTextArea logArea;
    private JButton startButton;
    private JProgressBar progressBar;
    private JRadioButton enToViButton;
    private JRadioButton enToViEnButton;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SubtitleTranslatorGUI::new);
    }

    public SubtitleTranslatorGUI() {
        frame = new JFrame("Tool dịch file sub Anh-Việt (file.srt)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 500);
        frame.setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new GridLayout(5, 1, 5, 5));

        folderPathField = new JTextField();
        JButton browseButton = new JButton("Tìm kiếm");
        browseButton.addActionListener(e -> chooseFolder());
        JLabel urlLabel = new JLabel("Đường dẫn folder: ");

        JPanel folderPanel = new JPanel(new BorderLayout());
        folderPanel.add(urlLabel, BorderLayout.WEST);
        folderPanel.add(folderPathField, BorderLayout.CENTER);
        folderPanel.add(browseButton, BorderLayout.EAST);

        JPanel apiKeyPanel = new JPanel(new BorderLayout());
        apiKeyPathField = new JPasswordField();
        JLabel apiKeyLabel = new JLabel("Cloud Translation API Key: ");
        JButton clearButton = new JButton("Xoá hết");
        clearButton.addActionListener(e -> clearAll());
        apiKeyPanel.add(apiKeyLabel, BorderLayout.WEST);
        apiKeyPanel.add(apiKeyPathField, BorderLayout.CENTER);
        apiKeyPanel.add(clearButton, BorderLayout.EAST);

        JPanel endWithPanel = new JPanel(new BorderLayout());
        JLabel endWithLabel = new JLabel("File kết thúc với: ");
        endWithTextField = new JTextField();
        endWithPanel.add(endWithLabel, BorderLayout.WEST);
        endWithPanel.add(endWithTextField, BorderLayout.CENTER);

        JPanel modePanel = new JPanel(new FlowLayout());
        JLabel modeLabel = new JLabel("Chế độ dịch: ");
        enToViButton = new JRadioButton("Anh -> Việt", true);
        enToViEnButton = new JRadioButton("Anh -> Việt + Anh");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(enToViButton);
        modeGroup.add(enToViEnButton);
        modePanel.add(modeLabel);
        modePanel.add(enToViButton);
        modePanel.add(enToViEnButton);

        topPanel.add(folderPanel);
        topPanel.add(apiKeyPanel);
        topPanel.add(endWithPanel);
        topPanel.add(modePanel);

        logArea = new JTextArea(10, 40);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        startButton = new JButton("Bắt đầu dịch");
        startButton.addActionListener(e -> startTranslation());

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(startButton, BorderLayout.NORTH);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        ImageIcon icon = new ImageIcon(getClass().getResource("/BronieSW.jpg"));
        frame.setIconImage(icon.getImage());

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    private void clearAll() {
        folderPathField.setText("");
        apiKeyPathField.setText("");
        endWithTextField.setText("");
    }
    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnVal = chooser.showOpenDialog(frame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            folderPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
    private void startButtonStatus(Boolean status) {
        if (status){
            startButton.setText("Đang tiến hành dịch...");
            startButton.setEnabled(false);
        } else {
            startButton.setText("Bắt đầu dịch");
            startButton.setEnabled(true);
        }
    }

    private void startTranslation() {
        progressBar.setValue(0);
        startButtonStatus(true);
        logArea.append("Đang chuẩn bị dịch sub file.srt từ tiếng Anh sang tiếng Việt...\n");
        String folderPath = folderPathField.getText().trim();
        if (folderPath.isEmpty()) {
            logArea.append("Vui lòng chọn thư mục!\n");
            startButtonStatus(false);
            return;
        }
        Boolean isBilingual = enToViEnButton.isSelected();
        new Thread(() -> {
            try {
                if (apiKeyPathField.getText().trim().isEmpty()) {
                    logArea.append("Hãy nhập Cloud Translation API Key của bạn!\n");
                    startButtonStatus(false);
                    return;
                }
                Translate translate = TranslateOptions.newBuilder().setApiKey(apiKeyPathField.getText().trim()).build().getService();
                List<Path> srtFiles = findSrtFiles(folderPath);
                int totalFiles = srtFiles.size();

                for (int i = 0; i < totalFiles; i++) {
                    processFile(srtFiles.get(i), translate, isBilingual);
                    int progress = (int) (((i + 1) / (double) totalFiles) * 100);
                    SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                }
                startButtonStatus(false);
                logArea.append("Dịch hoàn tất!\n");
            } catch (Exception e) {
                logArea.append("Error: " + e.getMessage() + "\n");
                e.printStackTrace();
            }
        }).start();
    }

    private List<Path> findSrtFiles(String folderPath) throws IOException {
        return Files.walk(Paths.get(folderPath))
                .filter(path -> path.toString().endsWith(endWithTextField.getText() + ".srt"))
                .collect(Collectors.toList());
    }

    private void processFile(Path file, Translate translate, Boolean isBilingual) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<String> cleanedLines = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();

        boolean foundTimestamp = false; // Biến đánh dấu đã tìm thấy timestamp đầu tiên

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Nếu tìm thấy dòng thời gian đầu tiên, bắt đầu xử lý file
            if (line.matches("\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}")) {
                foundTimestamp = true; // Đánh dấu đã tìm thấy timestamp đầu tiên

                // Nếu có nội dung trước đó, thêm vào danh sách
                if (currentText.length() > 0) {
                    cleanedLines.add(currentText.toString());
                    cleanedLines.add(""); // Thêm dòng trống giữa các đoạn
                    currentText.setLength(0);
                }
                cleanedLines.add(line);
            } else if (foundTimestamp) {
                // Chỉ xử lý nội dung nếu đã tìm thấy timestamp đầu tiên
                if (currentText.length() > 0) {
                    currentText.append(" ");
                }
                currentText.append(line);
            }
        }

        // Thêm đoạn cuối cùng vào danh sách
        if (currentText.length() > 0) {
            cleanedLines.add(currentText.toString());
            cleanedLines.add("");
        }

        List<String> translatedLines = new ArrayList<>();

        for (String line : cleanedLines) {
            // Không dịch dòng thời gian
            if (line.matches("\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}")) {
                translatedLines.add(line);
            } else {
                if (isBilingual) {
                    translatedLines.add(line);
                }
                translatedLines.add(translateText(line, translate));
            }
        }

        String newFileName = file.toString().replace(".srt", isBilingual ? "_vi-en.srt" : "_vi.srt");
        Files.write(Paths.get(newFileName), translatedLines);

        logArea.append("Đã dịch: " + newFileName + "\n");
    }

    private String translateText(String text, Translate translate) {
        Translation translation = translate.translate(text,
                Translate.TranslateOption.sourceLanguage(SOURCE_LANG),
                Translate.TranslateOption.targetLanguage(TARGET_LANG));
        return translation.getTranslatedText().replace("--&gt;", "-->");
    }
}