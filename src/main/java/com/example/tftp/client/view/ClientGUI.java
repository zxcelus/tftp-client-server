package com.example.tftp.client.view;

import com.example.tftp.client.controller.ClientController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ClientGUI extends JFrame {

    // ----------------------------- FIELDS -----------------------------
    private JTextField addressField;
    private JTextField portField;
    private JTextField downloadField;
    private JTextField uploadField;
    private JButton getButton;
    private JButton putButton;
    private JButton browseButton;
    private JButton stopButton;
    private JButton quitButton;
    private JTextArea logArea;
    private JProgressBar progressBar;

    // Status components
    private JLabel connectionStatusLabel;
    private JLabel transferStatusLabel;
    private JLabel fileInfoLabel;
    private JLabel transferredLabel;
    private JLabel blocksLabel;
    private int currentBlockSize = 512;

    private ClientController controller;

    public ClientGUI() {
        setTitle("TFTP Client");
        setSize(1000, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        controller = new ClientController(this);

        JPanel leftPanel = createLeftPanel();
        JPanel centerPanel = createCenterPanel();

        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);

        setupActionListeners();
        log("TFTP Client started");
        updateStatus("Ready", Color.BLACK);
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.setPreferredSize(new Dimension(350, 0));

        // Server Configuration
        panel.add(createSectionTitle("SERVER CONFIGURATION"));
        panel.add(Box.createVerticalStrut(5));
        panel.add(createServerPanel());
        panel.add(Box.createVerticalStrut(15));

        // Download
        panel.add(createSectionTitle("DOWNLOAD FROM SERVER"));
        panel.add(Box.createVerticalStrut(5));
        panel.add(createDownloadPanel());
        panel.add(Box.createVerticalStrut(15));

        // Upload
        panel.add(createSectionTitle("UPLOAD TO SERVER"));
        panel.add(Box.createVerticalStrut(5));
        panel.add(createUploadPanel());
        panel.add(Box.createVerticalStrut(20));

        // Control Buttons
        panel.add(createControlButtons());

        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 0, 10, 10));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("TFTP CLIENT", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(new Color(0, 70, 130));
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        panel.add(headerPanel, BorderLayout.NORTH);

        JPanel statusPanel = createEnhancedStatusPanel();
        panel.add(statusPanel, BorderLayout.CENTER);

        // Progress Bar
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JLabel progressTextLabel = new JLabel("Progress:");
        progressTextLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        progressPanel.add(progressTextLabel, BorderLayout.WEST);

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        progressPanel.add(progressBar, BorderLayout.CENTER);

        panel.add(progressPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createEnhancedStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
                "Transfer Status",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14),
                new Color(0, 70, 130)
        ));
        panel.setBackground(new Color(250, 250, 255));

        JPanel gridPanel = new JPanel(new GridBagLayout());
        gridPanel.setBackground(new Color(250, 250, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Connection Status
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel connLabel = new JLabel("Connection:");
        connLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gridPanel.add(connLabel, gbc);

        gbc.gridx = 1;
        connectionStatusLabel = createStatusValueLabel("Disconnected");
        gridPanel.add(connectionStatusLabel, gbc);

        // Transfer Status
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel transLabel = new JLabel("Status:");
        transLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gridPanel.add(transLabel, gbc);

        gbc.gridx = 1;
        transferStatusLabel = createStatusValueLabel("Ready");
        gridPanel.add(transferStatusLabel, gbc);

        // File Info
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel fileLabel = new JLabel("File:");
        fileLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gridPanel.add(fileLabel, gbc);

        gbc.gridx = 1;
        fileInfoLabel = createStatusValueLabel("No file selected");
        gridPanel.add(fileInfoLabel, gbc);

        // Transferred
        gbc.gridx = 0; gbc.gridy = 3;
        JLabel transferredTextLabel = new JLabel("Transferred:");
        transferredTextLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gridPanel.add(transferredTextLabel, gbc);

        gbc.gridx = 1;
        transferredLabel = createStatusValueLabel("0 B / 0 B");
        gridPanel.add(transferredLabel, gbc);

        // Blocks
        gbc.gridx = 0; gbc.gridy = 4;
        JLabel blocksTextLabel = new JLabel("Blocks:");
        blocksTextLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gridPanel.add(blocksTextLabel, gbc);

        gbc.gridx = 1;
        blocksLabel = createStatusValueLabel("-");
        gridPanel.add(blocksLabel, gbc);

        panel.add(gridPanel, BorderLayout.CENTER);

        // Log area at the bottom
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(new EmptyBorder(10, 5, 5, 5));
        logPanel.setBackground(new Color(250, 250, 255));

        JLabel logTitle = new JLabel("Activity Log:");
        logTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        logPanel.add(logTitle, BorderLayout.NORTH);

        logArea = new JTextArea(10, 30);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        logArea.setBackground(new Color(240, 240, 245));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        logPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(logPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JLabel createStatusValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(Color.DARK_GRAY);
        return label;
    }

    private JPanel createSectionTitle(String title) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(new Color(0, 70, 130));
        panel.add(label);
        return panel;
    }

    private JPanel createServerPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 5, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                new EmptyBorder(8, 8, 8, 8)
        ));
        panel.setBackground(new Color(245, 245, 250));

        addressField = new JTextField("127.0.0.1");
        portField = new JTextField("69");
        browseButton = createStyledButton("Browse...", new Color(80, 140, 220));

        panel.add(createLabeledField("Server Address:", addressField));
        panel.add(createLabeledField("Port:", portField));
        panel.add(browseButton);

        return panel;
    }

    private JPanel createDownloadPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                new EmptyBorder(8, 8, 8, 8)
        ));
        panel.setBackground(new Color(245, 245, 250));

        downloadField = new JTextField();
        downloadField.setToolTipText("Enter remote filename to download");
        panel.add(downloadField, BorderLayout.CENTER);

        getButton = createStyledButton("DOWNLOAD", new Color(60, 150, 60));
        getButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panel.add(getButton, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createUploadPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                new EmptyBorder(8, 8, 8, 8)
        ));
        panel.setBackground(new Color(245, 245, 250));

        uploadField = new JTextField();
        uploadField.setEditable(false);
        uploadField.setBackground(Color.WHITE);
        panel.add(uploadField, BorderLayout.CENTER);

        putButton = createStyledButton("UPLOAD", new Color(50, 120, 200));
        putButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panel.add(putButton, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createControlButtons() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 10));
        panel.setOpaque(false);

        quitButton = createStyledButton("QUIT", new Color(220, 80, 80));
        quitButton.setFont(new Font("Segoe UI", Font.BOLD, 12));

        stopButton = createStyledButton("STOP", new Color(255, 140, 60));
        stopButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        stopButton.setEnabled(true);

        panel.add(quitButton);
        panel.add(stopButton);

        return panel;
    }

    private JPanel createLabeledField(String labelText, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        panel.add(label, BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton button = new JButton(text);
        button.setBackground(bg);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));

        // Hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(bg.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(bg);
            }
        });

        return button;
    }

    // ==================== PUBLIC METHODS ====================

    public void setProgress(long bytesTransferred, long totalBytes, int blockSize) {
        SwingUtilities.invokeLater(() -> {
            String transferredStr = formatBytes(bytesTransferred);
            String totalStr = (totalBytes > 0) ? formatBytes(totalBytes) : "??";

            if (totalBytes > 0) {
                progressBar.setIndeterminate(false);
                progressBar.setMaximum(100);
                int percent = (int)((bytesTransferred * 100L) / Math.max(1L, totalBytes));
                percent = Math.max(0, Math.min(100, percent));
                progressBar.setValue(percent);
                progressBar.setString(String.format("%s / %s (%d%%)", transferredStr, totalStr, percent));
                transferredLabel.setText(String.format("%s / %s", transferredStr, totalStr));
            } else {
                progressBar.setIndeterminate(true);
                progressBar.setString(transferredStr + " (transferring...)");
                transferredLabel.setText(transferredStr + " / " + totalStr);
            }

            // Blocks info
            if (blockSize > 0 && bytesTransferred > 0) {
                long blocksDone = (bytesTransferred + blockSize - 1) / blockSize;
                if (totalBytes > 0) {
                    long blocksTotal = (totalBytes + blockSize - 1) / blockSize;
                    blocksLabel.setText(blocksDone + " / " + blocksTotal);
                } else {
                    blocksLabel.setText(blocksDone + " / ?");
                }
            } else {
                blocksLabel.setText("-");
            }
        });
    }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logArea.append("[" + timestamp + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateStatus(String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            transferStatusLabel.setText(status);
            transferStatusLabel.setForeground(color);
        });
    }

    public void updateFileInfo(String info) {
        SwingUtilities.invokeLater(() -> fileInfoLabel.setText(info));
    }

    public void updateConnectionStatus(String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            connectionStatusLabel.setText(status);
            connectionStatusLabel.setForeground(color);
        });
    }

    public void setProgressIndeterminate(boolean indeterminate) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(indeterminate);
            if (indeterminate) {
                progressBar.setString("Transferring...");
                progressBar.setForeground(new Color(70, 130, 180));
            } else {
                progressBar.setString("Ready");
                progressBar.setForeground(new Color(70, 130, 180));
            }
        });
    }

    public void updateProgress(double progress, String statusText) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue((int)(progress * 100));
            progressBar.setString(statusText);
        });
    }

    public String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public void logError(String msg) {
        log("ERROR: " + msg);
    }

    public void setFilePath(String path) {
        SwingUtilities.invokeLater(() -> uploadField.setText(path));
    }

    // ==================== GETTERS ====================

    public String getAddress() { return addressField.getText().trim(); }
    public String getPort() { return portField.getText().trim(); }
    public String getUploadFilename() { return uploadField.getText().trim(); }
    public String getDownloadFilename() { return downloadField.getText().trim(); }

    // ==================== DIALOGS ====================

    public String showFileChooser() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select file to upload");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int result = fileChooser.showOpenDialog(this);
        return (result == JFileChooser.APPROVE_OPTION) ?
                fileChooser.getSelectedFile().getAbsolutePath() : null;
    }

    public String showSaveDialog(String suggestedName) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save file as");
        fileChooser.setSelectedFile(new java.io.File(suggestedName));

        int result = fileChooser.showSaveDialog(this);
        return (result == JFileChooser.APPROVE_OPTION) ?
                fileChooser.getSelectedFile().getAbsolutePath() : null;
    }

    public boolean confirmOverwrite(String fileName) {
        int result = JOptionPane.showConfirmDialog(
                this,
                "File '" + fileName + "' already exists.\nDo you want to overwrite it?",
                "Confirm Overwrite",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return result == JOptionPane.YES_OPTION;
    }

    // ==================== ACTION LISTENERS ====================

    private void setupActionListeners() {
        browseButton.addActionListener(e -> controller.browseFile());
        getButton.addActionListener(e -> controller.download());
        putButton.addActionListener(e -> controller.upload());
        stopButton.addActionListener(e -> controller.cancel());
        quitButton.addActionListener(e -> System.exit(0));
    }

    public int getCurrentBlockSize() {
        return currentBlockSize;
    }

    // ==================== MAIN ====================

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI();
            gui.setVisible(true);
        });
    }
}