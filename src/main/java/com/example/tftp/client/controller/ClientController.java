package com.example.tftp.client.controller;

import com.example.tftp.client.view.ClientGUI;
import com.example.tftp.io.TftpReader;
import com.example.tftp.io.TftpWriter;
import com.example.tftp.model.TftpException;
import com.example.tftp.server.TftpLogger;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.InetAddress;

public class ClientController {
    private final ClientGUI gui;

    private volatile boolean transferInProgress = false;
    private volatile boolean cancelRequested = false;

    private long currentTransferred = 0;
    private long knownTotal = -1;

    public ClientController(ClientGUI view) {
        this.gui = view;

        TftpLogger.init("./log");
        TftpLogger.log("Client UI started");
    }

    public void browseFile() {
        if (transferInProgress) {
            gui.logError("Transfer in progress. Please wait or cancel.");
            return;
        }
        String p = gui.showFileChooser();
        if (p != null) gui.setFilePath(p);
    }

    public void download() {
        if (transferInProgress) {
            gui.logError("Another transfer is already in progress.");
            return;
        }

        String address = gui.getAddress();
        String portStr = gui.getPort();
        String remoteFilename = gui.getDownloadFilename();

        if (address.isEmpty() || portStr.isEmpty()) {
            gui.logError("Please enter server address and port.");
            return;
        }
        if (remoteFilename.isEmpty()) {
            gui.logError("Please enter remote filename to download.");
            return;
        }

        TftpLogger.log("Starting download request: remote=" + remoteFilename +
                ", server=" + address + ":" + portStr);

        String savePath = gui.showSaveDialog(remoteFilename);
        if (savePath == null) return;

        TftpLogger.log("Saving file to: " + savePath);

        File localFile = new File(savePath);
        if (localFile.exists()) {
            int result = JOptionPane.showConfirmDialog(
                    null,
                    "File '" + localFile.getName() + "' already exists.\nDo you want to overwrite it?",
                    "Confirm Overwrite",
                    JOptionPane.YES_NO_OPTION
            );
            if (result != JOptionPane.YES_OPTION) {
                gui.log("Download cancelled: file already exists.");
                TftpLogger.log("Download cancelled: user refused overwrite");
                return;
            }
        }

        try {
            InetAddress server = InetAddress.getByName(address);
            int port = Integer.parseInt(portStr);
            startDownload(server, port, remoteFilename, localFile);
        } catch (Exception e) {
            gui.logError("Download start failed: " + e.getMessage());
            TftpLogger.log("Download start failed: " + e.getMessage());
        }
    }

    private void startDownload(InetAddress server, int port, String remoteFilename, File localFile) {
        transferInProgress = true;
        cancelRequested = false;
        currentTransferred = 0;
        knownTotal = -1;

        gui.updateConnectionStatus("Connected to " + server.getHostAddress() + ":" + port, new Color(0,150,0));
        gui.updateStatus("Downloading...", Color.BLUE);
        gui.updateFileInfo("Downloading: " + remoteFilename);
        gui.setProgressIndeterminate(true);

        TftpLogger.log("Connecting to server " + server.getHostAddress() + ":" + port);
        TftpLogger.log("Downloading file: " + remoteFilename);

        TftpReader reader = new TftpReader();

        Runnable transfer = () -> {
            try {
                reader.readFile(remoteFilename, localFile, server, port, new TftpReader.ProgressCallback() {
                    @Override
                    public void onProgress(long transferred, long total) {
                        currentTransferred = transferred;
                        knownTotal = total > 0 ? total : -1;

                        TftpLogger.log("Download progress: " + transferred + "/" + total);

                        SwingUtilities.invokeLater(() ->
                                gui.setProgress(transferred, knownTotal, gui.getCurrentBlockSize())
                        );
                    }

                    @Override
                    public void onLog(String message) {
                        TftpLogger.log("CLIENT: " + message);
                        SwingUtilities.invokeLater(() -> gui.log(message));
                    }
                });

                if (cancelRequested) {
                    SwingUtilities.invokeLater(() -> {
                        gui.log("Download cancelled by user");
                        gui.updateStatus("Download cancelled", Color.ORANGE);
                    });
                    TftpLogger.log("Download cancelled by user");
                    if (localFile.exists()) localFile.delete();
                } else {
                    long finalSize = localFile.exists() ? localFile.length() : currentTransferred;

                    SwingUtilities.invokeLater(() -> {
                        gui.setProgress(finalSize, finalSize, gui.getCurrentBlockSize());
                        gui.updateProgress(1.0, "Completed");
                        gui.updateStatus("Download completed", new Color(0,150,0));
                        gui.updateFileInfo("Saved: " + localFile.getName() + " (" + gui.formatBytes(finalSize) + ")");
                        gui.log("Download completed: " + localFile.getAbsolutePath());
                    });

                    TftpLogger.log("Download completed: " + localFile.getAbsolutePath());
                }
            } catch (TftpException te) {
                SwingUtilities.invokeLater(() -> {
                    gui.logError(
                            "\n──────── TFTP ERROR ────────" +
                                    "\nFile: " + remoteFilename +
                                    "\nError Code: " + te.getErrorCode() +
                                    "\nMessage: " + te.getMessage() +
                                    "\n──────────────────────────────\n"
                    );
                    gui.updateStatus("TFTP Error", Color.RED);
                });
                TftpLogger.log("TFTP Error during download: " + te.getMessage());
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    gui.logError("Download failed: " + ex.getMessage());
                    gui.updateStatus("Download failed", Color.RED);
                });
                TftpLogger.log("Download failed: " + ex.getMessage());
            } finally {
                transferInProgress = false;
                cancelRequested = false;
                SwingUtilities.invokeLater(() -> gui.setProgressIndeterminate(false));
            }
        };

        new Thread(transfer, "tftp-download").start();
    }

    public void upload() {
        if (transferInProgress) {
            gui.logError("Another transfer is already in progress.");
            return;
        }

        String address = gui.getAddress();
        String portStr = gui.getPort();
        String filePath = gui.getUploadFilename();

        if (address.isEmpty() || portStr.isEmpty()) {
            gui.logError("Please enter server address and port.");
            return;
        }

        if (filePath.isEmpty()) {
            gui.logError("Please select a file to upload.");
            return;
        }

        File localFile = new File(filePath);
        if (!localFile.exists()) {
            gui.logError("File not found: " + filePath);
            return;
        }

        TftpLogger.log("Starting upload request: file=" + localFile.getAbsolutePath() +
                ", server=" + address + ":" + portStr);

        try {
            InetAddress server = InetAddress.getByName(address);
            int port = Integer.parseInt(portStr);

            startUpload(server, port, localFile);
        } catch (Exception e) {
            gui.logError("Upload start failed: " + e.getMessage());
            TftpLogger.log("Upload start failed: " + e.getMessage());
        }
    }

    private void startUpload(InetAddress server, int port, File localFile) {
        transferInProgress = true;
        cancelRequested = false;
        currentTransferred = 0;
        knownTotal = localFile.length();

        gui.updateConnectionStatus("Connected to " + server.getHostAddress() + ":" + port, new Color(0,150,0));
        gui.updateStatus("Uploading...", Color.BLUE);
        gui.updateFileInfo("Uploading: " + localFile.getName());
        gui.setProgressIndeterminate(false);
        gui.setProgress(0, knownTotal, gui.getCurrentBlockSize());

        TftpLogger.log("Connecting to server " + server.getHostAddress() + ":" + port);
        TftpLogger.log("Uploading file: " + localFile.getAbsolutePath());

        TftpWriter writer = new TftpWriter();

        Runnable transfer = () -> {
            try {
                writer.writeFile(localFile.getName(), localFile, server, port, new TftpWriter.ProgressCallback() {
                    @Override
                    public void onProgress(long transferred, long total) {
                        currentTransferred = transferred;
                        knownTotal = total > 0 ? total : -1;

                        TftpLogger.log("Upload progress: " + transferred + "/" + total);

                        SwingUtilities.invokeLater(() ->
                                gui.setProgress(transferred, knownTotal, gui.getCurrentBlockSize())
                        );
                    }

                    @Override
                    public void onLog(String message) {
                        TftpLogger.log("CLIENT: " + message);
                        SwingUtilities.invokeLater(() -> gui.log(message));
                    }
                });

                if (cancelRequested) {
                    SwingUtilities.invokeLater(() -> {
                        gui.log("Upload cancelled by user");
                        gui.updateStatus("Upload cancelled", Color.ORANGE);
                    });
                    TftpLogger.log("Upload cancelled by user");
                } else {
                    SwingUtilities.invokeLater(() -> {
                        gui.setProgress(knownTotal, knownTotal, gui.getCurrentBlockSize());
                        gui.updateProgress(1.0, "Completed");
                        gui.updateStatus("Upload completed", new Color(0,150,0));
                        gui.updateFileInfo("Uploaded: " + localFile.getName() + " (" + gui.formatBytes(knownTotal) + ")");
                        gui.log("Upload completed: " + localFile.getAbsolutePath());
                    });

                    TftpLogger.log("Upload completed: " + localFile.getAbsolutePath());
                }
            } catch (TftpException te) {
                SwingUtilities.invokeLater(() -> {
                    gui.logError(
                            "\n──────── TFTP ERROR ────────" +
                                    "\nFile: " + localFile.getName() +
                                    "\nError Code: " + te.getErrorCode() +
                                    "\nMessage: " + te.getMessage() +
                                    "\n──────────────────────────────\n"
                    );
                    gui.updateStatus("TFTP Error", Color.RED);
                });
                TftpLogger.log("TFTP Error during upload: " + te.getMessage());
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    gui.logError("Upload failed: " + ex.getMessage());
                    gui.updateStatus("Upload failed", Color.RED);
                });
                TftpLogger.log("Upload failed: " + ex.getMessage());
            } finally {
                transferInProgress = false;
                cancelRequested = false;
                SwingUtilities.invokeLater(() -> gui.setProgressIndeterminate(false));
            }
        };

        new Thread(transfer, "tftp-upload").start();
    }

    public void cancel() {
        if (!transferInProgress) return;
        cancelRequested = true;

        gui.log("Cancelling transfer...");
        gui.updateStatus("Cancelling...", Color.ORANGE);

        TftpLogger.log("User requested transfer cancellation");
    }
}
