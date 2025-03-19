import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.*;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import com.google.protobuf.ByteString;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SubtitleTranslatorGUI {
    private static final String SOURCE_LANG = "en";
    private static final String TARGET_LANG = "vi";
    private JFrame frame;
    private JTextField folderPathField;
    private JTextField endWithTextField;
    private JTextField jsonFilePathField;
    private JTextArea logArea;
    private JButton startButton;
    private JProgressBar progressBar;
    private JRadioButton enToViButton;
    private JRadioButton enToViEnButton;
    private JRadioButton enToViTTSButton;
    private JRadioButton viToViTTSButton;

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
        jsonFilePathField = new JTextField();
        JButton browseJsonButton = new JButton("Chọn file JSON");
        browseJsonButton.addActionListener(e -> chooseJsonFile());
        apiKeyPanel.add(new JLabel("Service Account JSON: "), BorderLayout.WEST);
        apiKeyPanel.add(jsonFilePathField, BorderLayout.CENTER);
        apiKeyPanel.add(browseJsonButton, BorderLayout.EAST);

        JPanel endWithPanel = new JPanel(new BorderLayout());
        JLabel endWithLabel = new JLabel("File kết thúc với: ");
        JButton clearButton = new JButton("Xoá hết");
        endWithTextField = new JTextField();
        clearButton.addActionListener(e -> clearAll());
        endWithPanel.add(endWithLabel, BorderLayout.WEST);
        endWithPanel.add(endWithTextField, BorderLayout.CENTER);
        endWithPanel.add(clearButton, BorderLayout.EAST);


        enToViButton = new JRadioButton("Anh -> Việt", true);
        enToViEnButton = new JRadioButton("Anh -> Việt + Anh");
        enToViTTSButton = new JRadioButton("Anh -> Việt");
        viToViTTSButton = new JRadioButton("Việt -> Việt");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(enToViButton);
        modeGroup.add(enToViEnButton);
        modeGroup.add(enToViTTSButton);
        modeGroup.add(viToViTTSButton);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel modeLabel = new JLabel("Chế độ dịch: ");
        modePanel.add(modeLabel);
        modePanel.add(enToViButton);
        modePanel.add(enToViEnButton);

        JPanel ttsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel ttsLabel = new JLabel("Chế độ thuyết minh: ");
        ttsPanel.add(ttsLabel);
        ttsPanel.add(enToViTTSButton);
        ttsPanel.add(viToViTTSButton);

        topPanel.add(folderPanel);
        topPanel.add(apiKeyPanel);
        topPanel.add(endWithPanel);
        topPanel.add(modePanel);
        topPanel.add(ttsPanel);

        logArea = new JTextArea(10, 40);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        startButton = new JButton("Bắt đầu dịch");
        startButton.addActionListener(e -> startProcess());

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
        jsonFilePathField.setText("");
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

    private void startProcess() {
        progressBar.setValue(0);
        startButtonStatus(true);
        appendLog("Đang chuẩn bị dịch sub file.srt từ tiếng Anh sang tiếng Việt...\n");
        String folderPath = folderPathField.getText().trim();
        if (folderPath.isEmpty()) {
            appendLog("Vui lòng chọn thư mục!\n");
            startButtonStatus(false);
            return;
        }
        Boolean isBilingual = enToViEnButton.isSelected();
        Boolean isTTS = enToViTTSButton.isSelected() || viToViTTSButton.isSelected();
        new Thread(() -> {
            try {
                String jsonFilePath = jsonFilePathField.getText().trim();
                if (jsonFilePathField.getText().trim().isEmpty()) {
                    appendLog("Hãy chọn file JSON của Google Service Account!\n");
                    startButtonStatus(false);
                    return;
                }
                try {
                    GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(jsonFilePath))
                            .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
                    Translate translate = TranslateOptions.newBuilder().setCredentials(credentials).build().getService();
                    TextToSpeechClient text2Speech = TextToSpeechClient.create(TextToSpeechSettings.newBuilder().setCredentialsProvider(() -> credentials).build());
                    List<Path> srtFiles = findSrtFiles(folderPath);
                    int totalFiles = srtFiles.size();

                    for (int i = 0; i < totalFiles; i++) {
                        if (isTTS){
                            processFileTTS(srtFiles.get(i), translate, text2Speech);
                        } else {
                            processFile(srtFiles.get(i), translate, isBilingual);
                        }
                        int progress = (int) (((i + 1) / (double) totalFiles) * 100);
                        SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                    }
                    startButtonStatus(false);
                    appendLog("Dịch hoàn tất!\n");
                } catch (IOException e) {
                    appendLog("Không tìm thấy file JSON của Google Service Account!\n");
                    startButtonStatus(false);
                    return;
                }
            } catch (Exception e) {
                appendLog("Error: " + e.getMessage() + "\n");
                startButtonStatus(false);
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
                    translatedLines.add(line.replaceAll("\\s*\\d+$", ""));
                }
                translatedLines.add(translateText(line, translate).replaceAll("\\s*\\d+$", ""));
            }
        }

        String newFileName = file.toString().replace(endWithTextField.getText()+".srt", isBilingual ? "_vi-en.srt" : "_vi.srt");
        Files.write(Paths.get(newFileName), translatedLines);

        appendLog("Đã dịch: " + newFileName + "\n");
    }

    private String translateText(String text, Translate translate) {
        Translation translation = translate.translate(text,
                Translate.TranslateOption.sourceLanguage(SOURCE_LANG),
                Translate.TranslateOption.targetLanguage(TARGET_LANG));
        return translation.getTranslatedText().replace("--&gt;", "-->");
    }
    private void appendLog(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength()); // Cuộn xuống cuối
    }
    private void chooseJsonFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
        int returnVal = fileChooser.showOpenDialog(frame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            jsonFilePathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }
    private void processFileTTS(Path file, Translate translate, TextToSpeechClient text2Speech) throws IOException, InterruptedException {
        List<String> lines = Files.readAllLines(file);
        List<Map<String, Object>> srtEntries = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        String currentTimestamp = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                if (currentTimestamp != null && currentText.length() > 0) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("timestamp", currentTimestamp);
                    entry.put("text", currentText.toString());
                    srtEntries.add(entry);
                    currentText.setLength(0);
                    currentTimestamp = null;
                }
            } else if (line.matches("\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}")) {
                currentTimestamp = line;
            } else if (!line.matches("\\d+")) {
                if (currentText.length() > 0) currentText.append(" ");
                currentText.append(enToViTTSButton.isSelected() ? translateText(line, translate) : line);
            }
        }

        if (currentTimestamp != null && currentText.length() > 0) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("timestamp", currentTimestamp);
            entry.put("text", currentText.toString());
            srtEntries.add(entry);
        }

        List<Path> tempAudioFiles = new ArrayList<>();
        for (int i = 0; i < srtEntries.size(); i++) {
            Map<String, Object> entry = srtEntries.get(i);
            String text = (String) entry.get("text");
            String tempOutput = file.toString().replace(endWithTextField.getText() + ".srt", "_temp_" + i + ".mp3");
            generateSpeech(text, tempOutput, text2Speech);

            long audioDurationMillis = getAudioDuration(tempOutput);
            String[] times = ((String) entry.get("timestamp")).split(" --> ");
            appendLog("Debug timestamp: " + times[0] + " | " + times[1] + "\n"); // Debug timestamp
            long startMillis = parseTimestampToMillis(times[0]);
            long endMillis = parseTimestampToMillis(times[1]);
            long expectedDurationMillis = endMillis - startMillis;
            entry.put("audioDuration", audioDurationMillis);
            entry.put("expectedDuration", expectedDurationMillis);
            entry.put("startMillis", startMillis);

            tempAudioFiles.add(Paths.get(tempOutput));
            appendLog("Đã tạo đoạn audio tạm: " + tempOutput + " (thời lượng thực: " + audioDurationMillis + "ms, mong muốn: " + expectedDurationMillis + "ms)\n");
        }

        String outputMp3Path = file.toString().replace(endWithTextField.getText() + ".srt", "_vi.mp3");
        concatenateAudioWithTiming(srtEntries, tempAudioFiles, outputMp3Path);

        for (Path tempFile : tempAudioFiles) {
            Files.deleteIfExists(tempFile);
        }

        appendLog("Đã tạo thuyết minh hoàn chỉnh: " + outputMp3Path + "\n");
    }

    private void concatenateAudioWithTiming(List<Map<String, Object>> srtEntries, List<Path> audioFiles, String outputPath) throws IOException, InterruptedException {
        String ffmpegPathRaw = getClass().getResource("/ffmpeg.exe").getPath();
        String ffmpegPath = ffmpegPathRaw.startsWith("file:") ? new File(ffmpegPathRaw.substring(5)).getAbsolutePath() : ffmpegPathRaw;
        if (ffmpegPath.startsWith("/")) {
            ffmpegPath = ffmpegPath.substring(1); // Loại bỏ dấu / đầu tiên nếu có
        }

        // Tạo file script .bat
        Path ffmpegScript = Paths.get("ffmpeg_script.bat");
        List<String> scriptLines = new ArrayList<>();
        StringBuilder commandLine = new StringBuilder();
        commandLine.append("\"").append(ffmpegPath).append("\" -y");

        // Thêm từng file audio bằng -i
        for (Path audioFile : audioFiles) {
            String audioFilePath = audioFile.toString().replace("\\", "/"); // Chuẩn hóa dấu phân cách
            commandLine.append(" -i \"").append(audioFilePath).append("\"");
        }

        // Xây dựng filter_complex
        StringBuilder filterComplex = new StringBuilder();
        long previousEndMillis = 0;

        for (int i = 0; i < srtEntries.size(); i++) {
            long startMillis = (long) srtEntries.get(i).get("startMillis");
            long endMillis = parseTimestampToMillis(((String) srtEntries.get(i).get("timestamp")).split(" --> ")[1]);
            long audioDurationMillis = (long) srtEntries.get(i).get("audioDuration");
            long expectedDurationMillis = (long) srtEntries.get(i).get("expectedDuration");

            long delayMillis = startMillis - previousEndMillis;
            if (delayMillis > 0) {
                filterComplex.append(String.format("[%d:a]adelay=%d[a%d];", i, delayMillis, i));
            } else {
                filterComplex.append(String.format("[%d:a]anull[a%d];", i, i));
            }

            if (audioDurationMillis < expectedDurationMillis) {
                double paddingSeconds = (expectedDurationMillis - audioDurationMillis) / 1000.0;
                filterComplex.append(String.format("[a%d]apad=pad_dur=%.3f[ap%d];", i, paddingSeconds, i));
            } else if (audioDurationMillis > expectedDurationMillis) {
                double trimSeconds = expectedDurationMillis / 1000.0;
                filterComplex.append(String.format("[a%d]atrim=0:%.3f[ap%d];", i, trimSeconds, i));
            } else {
                filterComplex.append(String.format("[a%d]anull[ap%d];", i, i));
            }
            previousEndMillis = endMillis;
        }

        for (int i = 0; i < srtEntries.size(); i++) {
            filterComplex.append(String.format("[ap%d]", i));
        }
        filterComplex.append(String.format("concat=n=%d:v=0:a=1[outa]", srtEntries.size()));

        String outputPathNormalized = Paths.get(outputPath).toString().replace("\\", "/");
        commandLine.append(" -filter_complex \"").append(filterComplex.toString()).append("\"");
        commandLine.append(" -map \"[outa]\"");
        commandLine.append(" \"").append(outputPathNormalized).append("\"");

        scriptLines.add("@echo off");
        scriptLines.add(commandLine.toString());
        Files.write(ffmpegScript, scriptLines);

        // Chạy file .bat
        List<String> command = new ArrayList<>();
        command.add("cmd.exe");
        command.add("/c");
        command.add(ffmpegScript.toAbsolutePath().toString());

        appendLog("Lệnh FFmpeg (qua .bat): " + String.join(" ", command) + "\n");
        appendLog("Nội dung script: " + String.join("\n", scriptLines) + "\n");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendLog("FFmpeg: " + line + "\n");
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            appendLog("FFmpeg gặp lỗi, mã lỗi: " + exitCode + "\n");
        } else {
            appendLog("Đã gộp audio thành công: " + outputPath + "\n");
        }

        Files.deleteIfExists(ffmpegScript);
    }

    // Hàm lấy thời lượng audio bằng FFmpeg
    private long getAudioDuration(String audioPath) throws IOException, InterruptedException {
        String ffmpegPath = getClass().getResource("/ffmpeg.exe").getPath();
        if (ffmpegPath.startsWith("file:")) {
            ffmpegPath = new File(ffmpegPath.substring(5)).getAbsolutePath();
        }
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(audioPath);
        command.add("-f");
        command.add("null");
        command.add("-");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        process.waitFor();

        String result = output.toString();
        int durationIndex = result.indexOf("Duration: ");
        if (durationIndex == -1) {
            appendLog("Không tìm thấy Duration trong output FFmpeg!\n");
            return 0;
        }
        // Tìm vị trí dấu phẩy hoặc khoảng trắng sau Duration để cắt chính xác
        int endIndex = result.indexOf(",", durationIndex + 10);
        if (endIndex == -1) endIndex = result.indexOf(" ", durationIndex + 10);
        if (endIndex == -1) endIndex = durationIndex + 22; // Dự phòng
        String durationStr = result.substring(durationIndex + 10, endIndex).trim();
        appendLog("Debug getAudioDuration - Duration extracted: " + durationStr + "\n");
        return parseFFmpegDuration(durationStr);
    }

    private long parseFFmpegDuration(String duration) {
        appendLog("Debug parseFFmpegDuration - Input: " + duration + "\n");
        try {
            // Tách bằng : hoặc . hoặc , để xử lý mọi trường hợp
            String[] parts = duration.replace(",", ".").split(":|\\.");
            if (parts.length < 3 || parts.length > 4) {
                appendLog("Lỗi: Định dạng duration FFmpeg không đúng - " + duration + "\n");
                return 0;
            }
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = Integer.parseInt(parts[1].trim());
            int seconds = Integer.parseInt(parts[2].trim());
            int millis = (parts.length == 4) ? Integer.parseInt(parts[3].trim()) * 10 : 0; // Nếu không có phần thập phân, dùng 0
            return hours * 3600000L + minutes * 60000L + seconds * 1000L + millis;
        } catch (NumberFormatException e) {
            appendLog("Lỗi phân tích duration FFmpeg: " + duration + " - " + e.getMessage() + "\n");
            return 0;
        }
    }

    private long parseTimestampToMillis(String timestamp) {
        try {
            String[] parts = timestamp.split(":|,");
            if (parts.length != 4) {
                appendLog("Lỗi: Timestamp không đúng định dạng - " + timestamp + "\n");
                return 0; // Trả về 0 nếu không hợp lệ
            }
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = Integer.parseInt(parts[1].trim());
            int seconds = Integer.parseInt(parts[2].trim());
            int millis = Integer.parseInt(parts[3].trim());
            return hours * 3600000L + minutes * 60000L + seconds * 1000L + millis;
        } catch (NumberFormatException e) {
            appendLog("Lỗi phân tích timestamp: " + timestamp + " - " + e.getMessage() + "\n");
            return 0; // Trả về 0 nếu phân tích thất bại
        }
    }

    private void generateSpeech(String text, String outputPath, TextToSpeechClient text2Speech) throws IOException {
        SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
        VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode("vi-VN")
                .setName("vi-VN-Standard-B") // Chọn giọng vi-VN-Standard-B
                .setSsmlGender(SsmlVoiceGender.MALE) // Giọng này là nam
                .build();
        AudioConfig audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.MP3)
                .setSpeakingRate(1.4) // Giữ tốc độ mặc định, điều chỉnh thời lượng sau
                .build();

        SynthesizeSpeechResponse response = text2Speech.synthesizeSpeech(input, voice, audioConfig);
        ByteString audioContents = response.getAudioContent();
        try (OutputStream out = new FileOutputStream(outputPath)) {
            out.write(audioContents.toByteArray());
        }
    }
}
