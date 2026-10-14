package at.variabilityanalysisgui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;

public class StartupController {

    @FXML
    private TextField variantFileTextField;

    @FXML
    private TextField extractionFileTextField;

    @FXML
    private Button startButton;

    @FXML
    public void initialize(){

    }

    @FXML
    private void chooseVariantFile(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Variants File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        fileChooser.setInitialDirectory(new File("./"));
        Window window = startButton.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(window);

        if (selectedFile != null) {
            variantFileTextField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void chooseExtractionFile(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Extraction File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Json Files", "*.json"));
        fileChooser.setInitialDirectory(new File("./"));
        Window window = startButton.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(window);

        if (selectedFile != null) {
            extractionFileTextField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void startVarflix() throws IOException {
        if(!variantFileTextField.getText().isEmpty() && !extractionFileTextField.getText().isEmpty()){
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/MainViewNew.fxml"));

            Scene scene = new Scene(loader.load(), 900, 700);
            scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

            Stage stage = new Stage();
            stage.initModality(Modality.NONE);
            stage.setTitle("Varflix");
            stage.setScene(scene);

            Controller controller = loader.getController();

            controller.init(variantFileTextField.getText(), extractionFileTextField.getText());

            stage.show();

            ((Stage) startButton.getScene().getWindow()).close();
        }

    }

}
