import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class SubtitleTranslatorGUI {
    private static final String SOURCE_LANG = "en";
    private static final String TARGET_LANG = "vi";
    private JFrame frame;
    private JTextField folderPathField;
    private JPasswordField apiKeyPathField;
    private JTextArea logArea;
    private JButton startButton;
    private JProgressBar progressBar;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SubtitleTranslatorGUI::new);
    }

    public SubtitleTranslatorGUI() {
        frame = new JFrame("Tool dịch file sub Anh-Việt (file.srt)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 200);
        frame.setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout());
        folderPathField = new JTextField();
        JButton browseButton = new JButton("Tìm kiếm");
        browseButton.addActionListener(e -> chooseFolder());
        JLabel urlLabel = new JLabel("Đường dẫn folder: ");

        topPanel.add(urlLabel, BorderLayout.WEST);
        topPanel.add(folderPathField, BorderLayout.CENTER);
        topPanel.add(browseButton, BorderLayout.EAST);

        JPanel apiKeyPanel = new JPanel(new BorderLayout());
        apiKeyPathField = new JPasswordField();
        JLabel apiKeyLabel = new JLabel("Cloud Translation API Key: ");
        JButton clearButton = new JButton("Xoá hết");
        apiKeyPanel.add(apiKeyLabel, BorderLayout.WEST);
        apiKeyPanel.add(apiKeyPathField, BorderLayout.CENTER);
        clearButton.addActionListener(e -> clearAll());
        apiKeyPanel.add(clearButton, BorderLayout.EAST);

        logArea = new JTextArea();
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

        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BorderLayout());

        northPanel.add(topPanel, BorderLayout.NORTH);
        northPanel.add(apiKeyPanel, BorderLayout.SOUTH);

        frame.add(northPanel, BorderLayout.NORTH);
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
                    processFile(srtFiles.get(i), translate);
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
                .filter(path -> path.toString().endsWith("_en.srt"))
                .collect(Collectors.toList());
    }

    private void processFile(Path file, Translate translate) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<String> translatedLines = lines.stream()
                .map(line -> translateText(line, translate))
                .collect(Collectors.toList());

        String newFileName = file.toString().replace("_en.srt", "_vi_cv.srt");
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