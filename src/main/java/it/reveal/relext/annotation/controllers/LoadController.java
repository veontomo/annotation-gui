package it.reveal.relext.annotation.controllers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.prefs.Preferences;
import java.util.zip.GZIPInputStream;

import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.reveal.nlt.xdg.textstructure.Constituent;
import it.reveal.nlt.xdg.textstructure.ConstituentList;
import it.reveal.nlt.xdg.textstructure.Paragraph;
import it.reveal.nlt.xdg.textstructure.Token;
import it.reveal.nlt.xdg.textstructure.XDG;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;

/**
 * Controller for loading and annotating the text.
 * @author Andrew
 *
 */
public class LoadController {

    private static final Logger logger = LoggerFactory.getLogger(LoadController.class);

    /**
     * Text area in which the user selects the words and annotate them.
     */
    @FXML
    private TextArea text;

    /**
     * Click on this button allows the user to load the file from the file system
     */
    @FXML
    private Button loadBtn;

    /**
     * Text field that displays the path of the loaded file
     */
    @FXML
    private Text fileOrigin;

    /**
     * list of available entity types
     */
    @FXML
    ListView<ListItem> entityTypes;

    /**
     * list of available relation types
     */
    @FXML
    ListView<ListItem> relationTypes;

    /**
     * text area containing the content of {@link #text} in which the annotated words are highlighted
     */
    @FXML
    TextFlow textFlow;

    /**
     * list of offsets at which words of {@link #text} starts
     */
    private final List<Integer> startOffsets = new ArrayList<>();

    /**
     * list of recognised words of {@link #text} 
     */
    private final List<Text> words = new ArrayList<>();

    /**
     * preferences in which the path of the last loaded file is saved
     */
    private final Preferences pref = Preferences.userNodeForPackage(LoadController.class);

    /**
     * token under which the name of the last loaded file is saved in the {@link #pref}  
     */
    private final String selectedFileNameToken = "last-selected-file";

    /**
     * token under which the folder of the last loaded file is saved in the {@link #pref}  
     */
    private final String selectedDirToken = "last-selected-dir";

    private final List<ListItem> entities = new ArrayList<>(2);

    private final List<ListItem> relations = new ArrayList<>(3);

    public LoadController() {
        this.entities.add(new ListItem("Person", "Soggetto fisico", Color.GREEN));
        this.entities.add(new ListItem("Location", "Posizione", Color.BLUE));
        this.relations.add(new ListItem("Familiar with", "HA_RAPPORTO_CON", Color.RED));
        this.relations.add(new ListItem("Visits place", "FREQUENTA_LUOGO", Color.BROWN));
        this.relations.add(new ListItem("Source of 'familiar with'", "FONTE_INFORMATIVA_DI_HA_RAPPORTO_CON", Color.TEAL));

    }

    @FXML
    public void initialize() {
        logger.info("Initialize the controller");
        this.text.setEditable(false);
        this.text.setWrapText(true);

        textFlow.setTextAlignment(TextAlignment.JUSTIFY);
        textFlow.setPrefSize(550, 300);
        text.setOnContextMenuRequested(new EventHandler<Event>() {
            public void handle(Event arg0) {
                logger.info("selected text: {}, range: {}", text.getSelectedText(), text.getSelection());
            }
        });
        ContextMenu contextMenu = new ContextMenu();
        final ObservableList<MenuItem> items = contextMenu.getItems();
        entities.forEach(e -> {
            final MenuItem m = new MenuItem(e.label);
            m.setOnAction(i -> {
                markAsSelected(e.color, text.getSelection());
            });
            items.add(m);
        });
        text.setContextMenu(contextMenu);
        final Callback<ListView<ListItem>, ListCell<ListItem>> c = lv -> new ListCell<ListItem>() {
            @Override
            protected void updateItem(ListItem item, boolean empty) {
                if (empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.label);
                    setStyle(item.getStyle());
                }
            }
        };

        this.entityTypes.setItems(FXCollections.observableArrayList(entities));
        this.entityTypes.setCellFactory(c);
        this.relationTypes.setItems(FXCollections.observableArrayList(relations));
        this.relationTypes.setCellFactory(c);

    }

    private void markAsSelected(Color color, IndexRange selection) {
        logger.info("action: {}, selection: {}", color, text.getSelection());
        final List<Integer> result = new ArrayList<>(5);
        final int startOffset = selection.getStart();
        final int size = this.startOffsets.size();
        for (int i = 0; i < size; i++) {
            final Integer current = this.startOffsets.get(i);
            if (current == null) {
                continue;
            }
            if (current == startOffset) {
                result.add(i);
                break;
            }

        }
        logger.info("Found: {}", result);
        result.forEach(i -> this.words.get(i)
            .setFill(color));

    }

    /**
     * Load the file
     * @param event
     */
    @FXML
    protected void loadTextAction(ActionEvent event) {

        final File file = chooseFile(pref);
        if (file != null && file.exists() && !file.isDirectory()) {
            saveInPreferences(file);
            final String inputFile = file.getAbsolutePath();
            this.fileOrigin.setText(inputFile);

            it.reveal.nlt.xdg.textstructure.Text t = null;
            String content = "";
            if (inputFile.endsWith(".xml.gz")) {
                try {
                    t = it.reveal.nlt.xdg.textstructure.Text.loadFromStream(new GZIPInputStream(new FileInputStream(inputFile)));
                    showTextInfo(t);
                    content = t.getSurface();
                    loadParsedText(textFlow, t);

                } catch (JAXBException | IOException e) {
                    logger.error("Failed to extract RevNLT Text from {}", inputFile, e);
                }
            } else if (inputFile.endsWith(".txt")) {
                final List<String> lines = readLines(file);
                content = String.join("\n", lines);
                textFlow.getChildren()
                    .add(new Text(content));
            }
            this.text.setText(content);

        } else {
            this.fileOrigin.setText("No file found");
            this.text.clear();
        }

    }

    /**
     * Save the information about the file in the preferences for future usage 
     * @param file
     */
    private void saveInPreferences(final File file) {
        pref.put(selectedFileNameToken, file.getName());
        pref.put(selectedDirToken, file.getParent());
    }

    /**
     * Allow a user to choose a file from the file system using the preferences that might contain information about latest selected file.
     * 
     * @param preferences
     * @return
     */
    private File chooseFile(final Preferences preferences) {
        final String name = preferences.get(selectedFileNameToken, "");
        final String folder = preferences.get(selectedDirToken, "");
        logger.info("last selected name: {}, dir: {}", name, folder);

        final FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select a file");
        if (!name.isEmpty()) {
            fileChooser.setInitialFileName(name);
        }
        if (!folder.isEmpty()) {
            fileChooser.setInitialDirectory(new File(folder));
        }
        final Stage stage = (Stage) ((Node) loadBtn).getScene()
            .getWindow();

        return fileChooser.showOpenDialog(stage);
    }

    private void loadParsedText(TextFlow textFlow, it.reveal.nlt.xdg.textstructure.Text t) {
        for (Paragraph p : t.getParagraphs()) {
            for (XDG xdg : p.getXdgs()) {
                final ConstituentList constit = xdg.getConstituents()
                    .getSimpleConstituentList();
                for (Constituent c : constit) {
                    final Vector<Token> tokens = c.getTokenSpan();
                    final List<String> tokensStr = new ArrayList<>(tokens.size());
                    for (final Token token : tokens) {
                        tokensStr.add(token.getSurface());
                    }
                    final String tmp = String.join(" ", tokensStr);
                    final Text text = new Text(tmp);
                    words.add(text);
                    startOffsets.add(c.getTokenStartOffset());
                    words.add(new Text(" "));
                    startOffsets.add(null);
                }
            }
            words.add(new Text("\n"));
            startOffsets.add(null);
        }

        textFlow.getChildren()
            .addAll(words);

    }

    private void showTextInfo(it.reveal.nlt.xdg.textstructure.Text t) {
        for (Paragraph p : t.getParagraphs()) {
            for (XDG xdg : p.getXdgs()) {
                final ConstituentList constit = xdg.getConstituents()
                    .getSimpleConstituentList();
                for (Constituent c : constit) {
                    logger.info("lemma: {}, start: {}, end: {}", c.getTokenSpan(), c.getTokenStartOffset(), c.getTokenEndOffset());
                }
            }
        }

    }

    public static List<String> readLines(File file) {
        final List<String> lines = new ArrayList<>();
        try (final BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            while (line != null) {
                lines.add(line);
                line = reader.readLine();
            }
        } catch (FileNotFoundException e) {
            System.out.println("No such file");
        } catch (IOException e) {
            System.out.println("IO exception");

        }
        return lines;
    }

    /**
     * Utility class for wrapping list item properties
     * @author Andrew
     *
     */
    private static class ListItem {
        final String label;
        final String value;
        final Color color;

        public ListItem(String label, String value, Color color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }

        public String getStyle() {
            String hex = String.format("#%02X%02X%02X", times255(color.getRed()), times255(color.getGreen()), times255(color.getBlue()));
            return "-fx-text-fill:" + hex;
        }

        private int times255(double d) {
            return (int) (d * 255);
        }
    }
}
