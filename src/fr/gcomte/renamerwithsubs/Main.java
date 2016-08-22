package fr.gcomte.renamerwithsubs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class Main extends Application {

    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");

    private static final List<String> VIDEO_EXTENSIONS_ACCEPTED = Arrays.asList(".mp4", ".avi", ".mkv");
    private static final List<String> SUBS_EXTENSIONS_ACCEPTED = Arrays.asList(".srt", ".sub");

    private Stage stage;
    private final FileChooser fileChooser = new FileChooser();

    private TextArea logs;
    private TableView<Entry<File, File>> tableView;
    // private Button cancelButton;

    private final static Comparator<File> COMPARATOR = (f1, f2) -> {
        if (f1.getName() == "") {
            return 1;
        }
        if (f2.getName() == "") {
            return -1;
        }
        return f1.getName().compareTo(f2.getName());
    };
    private Map<File, File> map = new TreeMap<>(COMPARATOR);
    private Map<File, File> previousMap;
    private ObservableList<Entry<File, File>> obsList;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            stage = primaryStage;
            final BorderPane borderPane = new BorderPane();

            // North : actions pane
            final Pane actionsPane = buildActionsPane();
            BorderPane.setMargin(actionsPane, new Insets(5.0));
            borderPane.setTop(actionsPane);

            // Center : table
            tableView = buildTable();
            borderPane.setCenter(tableView);

            // South : logs
            logs = buildLogs();
            borderPane.setBottom(logs);

            // Scene
            final Scene scene = new Scene(borderPane, 1000, 600);
            scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
            stage.setScene(scene);

            // Stage
            stage.setTitle("Renamer With Subs");
            stage.getIcons().add(new Image(getClass().getResourceAsStream("rws.png")));
            stage.show();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private Pane buildActionsPane() {
        final Pane actionsPane = new FlowPane(5.0, 0.0);
        final Image importImage = new Image(getClass().getResourceAsStream("add182.png"));
        final Button importFiles = new Button("Import", new ImageView(importImage));
        importFiles.setOnAction(event -> {
            final List<File> files = fileChooser.showOpenMultipleDialog(stage);
            if (files != null) {
                importFiles(files);
            }
        });
        final Image clearImage = new Image(getClass().getResourceAsStream("rubbish.png"));
        final Button clearFiles = new Button("Clear table", new ImageView(clearImage));
        clearFiles.setOnAction(event -> {
            map.clear();
            refreshTableData();
        });
        final Image playImage = new Image(getClass().getResourceAsStream("play106.png"));
        final Button renameVideos = new Button("Rename video clips", new ImageView(playImage));
        renameVideos.setTooltip(
                new Tooltip("Rename video clips files with the same name as their corresponding subtitles."));
        renameVideos.setOnAction(event -> renameFiles(true));
        final Button renameSubs = new Button("Rename subtitles", new ImageView(playImage));
        renameSubs.setTooltip(new Tooltip("Rename subtitles files with the same name as their corresponding videos."));
        renameSubs.setOnAction(event -> renameFiles(false));
        // cancelButton = new Button("Cancel");
        // cancelButton.setDisable(true);
        // cancelButton.setOnAction(event -> cancelAction());
        final Image infoImage = new Image(getClass().getResourceAsStream("rounded59.png"));
        final Button aboutButton = new Button("About", new ImageView(infoImage));
        aboutButton.setOnAction(event -> popUpAbout());
        actionsPane.getChildren().addAll(importFiles, clearFiles, renameVideos, renameSubs, aboutButton);
        return actionsPane;
    }

    private TableView<Entry<File, File>> buildTable() {
        final TableView<Entry<File, File>> tableView = new TableView<>();
        tableView.setPlaceholder(new Label("Drag-and-drop files here or click on the 'Import' button."));

        final TableColumn<Entry<File, File>, String> videosColumn = new TableColumn<>("Video clips");
        videosColumn.setCellValueFactory(p -> {
            return new SimpleStringProperty(p.getValue().getKey().getName());
        });

        final TableColumn<Entry<File, File>, String> subsColumn = new TableColumn<>("Subtitles");
        subsColumn.setCellValueFactory(p -> {
            return new SimpleStringProperty(p.getValue().getValue().getName());
        });

        tableView.getColumns().addAll(videosColumn, subsColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // ROW FACTORY : drag and drop rows from table view to table view
        tableView.setRowFactory((TableView<Entry<File, File>> tv) -> {
            final TableRow<Entry<File, File>> row = new TableRow<>();

            row.setOnDragDetected((MouseEvent event) -> {
                if (!row.isEmpty()) {
                    final Integer index = row.getIndex();
                    final Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    // TODO view of the sub file name only
                    db.setDragView(row.snapshot(null, null));
                    // final String subFileName =
                    // tableView.getItems().get(index).getValue();
                    // db.setDragView(new Image(new
                    // ByteArrayInputStream(subFileName.getBytes(StandardCharsets.UTF_8))));
                    final ClipboardContent cc = new ClipboardContent();
                    cc.put(SERIALIZED_MIME_TYPE, index);
                    db.setContent(cc);
                    event.consume();
                }
            });

            row.setOnDragOver((DragEvent event) -> {
                final Dragboard db = event.getDragboard();
                if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                    if (row.getIndex() != ((Integer) db.getContent(SERIALIZED_MIME_TYPE)).intValue()) {
                        event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                        event.consume();
                    }
                }
            });

            row.setOnDragDropped((DragEvent event) -> {
                final Dragboard db = event.getDragboard();
                if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                    if (row.isEmpty()) {
                        event.setDropCompleted(false);
                        event.consume();
                    } else {
                        final int draggedIndex = (Integer) db.getContent(SERIALIZED_MIME_TYPE);
                        final int dropIndex = row.getIndex();
                        final Entry<File, File> sourceEntry = tableView.getItems().get(draggedIndex);
                        final Entry<File, File> targetEntry = tableView.getItems().get(dropIndex);
                        final File sourceKey = sourceEntry.getKey();
                        final File targetKey = targetEntry.getKey();
                        final File sourceValue = sourceEntry.getValue();
                        final File targetValue = targetEntry.getValue();
                        map.put(targetKey, sourceValue);
                        map.put(sourceKey, targetValue);

                        refreshTableData();

                        event.setDropCompleted(true);
                        event.consume();
                    }
                }
            });

            return row;
        });

        // TABLE VIEW : drag and drop files into table view
        tableView.setOnDragOver((DragEvent event) -> {
            event.acceptTransferModes(TransferMode.MOVE);
            event.consume();
        });
        tableView.setOnDragDropped((DragEvent event) -> {
            final Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles()) {
                importFiles(dragboard.getFiles());
            }
            event.setDropCompleted(true);
            event.consume();
        });

        return tableView;
    }

    private TextArea buildLogs() {
        final TextArea logs = new TextArea();
        logs.setEditable(false);
        logs.setWrapText(true);
        logs.appendText("Welcome to 'Rename with subs'\n"
                + "To begin, import your video clip(s) and subtitle(s) files by clicking the 'Import' button"
                + " or by dragging and dropping them in the middle of the screen\n");
        return logs;
    }

    private void importFiles(List<File> files) {
        final List<File> videosFiles = new ArrayList<>();
        final List<File> subsFiles = new ArrayList<>();
        for (final File file : files) {
            final String name = file.getName();
            final int lastIndexOf = name.lastIndexOf('.');
            if (lastIndexOf == -1) {
                logLine("Impossible to import file " + name);
            } else {
                final String extension = name.substring(lastIndexOf).toLowerCase();
                if (VIDEO_EXTENSIONS_ACCEPTED.contains(extension)) {
                    videosFiles.add(file);
                } else if (SUBS_EXTENSIONS_ACCEPTED.contains(extension)) {
                    subsFiles.add(file);
                } else {
                    logLine("Impossible to import file " + name);
                }
            }
        }
        int maxSize = videosFiles.size();
        if (subsFiles.size() > maxSize) {
            maxSize = subsFiles.size();
        }
        for (int i = 0; i < maxSize; ++i) {
            File video = null;
            if (i >= videosFiles.size()) {
                video = new File("");
            } else {
                video = videosFiles.get(i);
            }
            File sub = null;
            if (i >= subsFiles.size()) {
                sub = new File("");
            } else {
                sub = subsFiles.get(i);
            }
            map.put(video, sub);
        }
        refreshTableData();
    }

    private void renameFiles(boolean renameVideos) {
        final Map<File, File> futureMap = new TreeMap<>(COMPARATOR);
        for (final Entry<File, File> entry : map.entrySet()) {
            final File video = entry.getKey();
            final File sub = entry.getValue();
            if (video.getName() == "" || sub.getName() == "") {
                logLine("Skipped files \"" + video.getName() + "\" & \"" + sub.getName() + "\"");
                continue;
            }
            File source, target;
            if (renameVideos) {
                source = sub;
                target = video;
            } else {
                source = video;
                target = sub;
            }
            final String extension = target.getName().substring(target.getName().lastIndexOf('.'));
            final String pathWOExt = source.getAbsolutePath().substring(0, source.getAbsolutePath().lastIndexOf('.'));
            final File newFile = new File(pathWOExt + extension);
            final boolean success = target.renameTo(newFile);
            if (success) {
                logLine("Renamed \"" + target.getName() + "\" to \"" + newFile.getName() + "\"");
                if (renameVideos) {
                    futureMap.put(newFile, sub);
                } else {
                    futureMap.put(video, newFile);
                }
            } else {
                logLine("Impossible to rename \"" + target.getName() + "\" into \"" + newFile.getName() + "\"");
                futureMap.put(video, sub);
            }
        }
        // previousMap = new TreeMap<>(map);
        // cancelButton.setDisable(false);
        map = futureMap;
        refreshTableData();
    }

    private void logLine(String line) {
        logs.appendText(line + "\n");
    }

    private void refreshTableData() {
        if (obsList != null) {
            obsList.removeAll(obsList);
        }
        obsList = FXCollections.observableArrayList(map.entrySet());
        tableView.setItems(obsList);
    }

    private void popUpAbout() {
        final WebView webview = new WebView();
        final WebEngine engine = webview.getEngine();
        engine.loadContent("<div align=\"center\" style=\"font-family: sans-serif;font-size: 12px\">"
                + "Renamer with subs made by Guillaume Comte in 2015."
                + "<br><br>Icons made by <a href=\"http://www.google.com\""
                + " title=\"Google\">Google</a> from <a href=\"http://www.flaticon.com\""
                + " title=\"Flaticon\">www.flaticon.com</a> is licensed under"
                + " <a href=\"http://creativecommons.org/licenses/by/3.0/\""
                + " title=\"Creative Commons BY 3.0\">CC BY 3.0</a></div>");

        final Stage about = new Stage();
        about.setTitle("About");
        about.setScene(new Scene(webview, 500, 70));
        about.showAndWait();
    }

    // private void cancelAction() {
    // map = new TreeMap<>(previousMap);
    // refreshTableData();
    // cancelButton.setDisable(true);
    // }
}
