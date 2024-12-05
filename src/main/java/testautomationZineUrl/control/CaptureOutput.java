package testautomationZineUrl.control;

import lombok.Getter;
import org.testng.ITestListener;
import org.testng.ITestResult;
import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
public class CaptureOutput implements ITestListener {
    // Method to fetch the list of updated records
    private final List<String[]> updatedRecords = new ArrayList<>();
    private static final String OUTPUT_CSV_FILE_PATH = "output.csv"; // Path for the CSV file

    @Override
    public void onTestStart(ITestResult result) {
        // Dynamically capture test details (this is just an example)
        String testName = result.getName();
        String endpoint = "/dynamicEndpoint";  // Use dynamic values as required
        String method = "GET";
        String payload = "{ \"data\": \"value\" }";
        int expectedStatus = 200;
        int actualStatus = 200;
        String responseBody = "{ \"response\": \"success\" }";
        String resultStatus = "Success";
        String auth = "1";
        String headersLogged = "Content-Type: application/json";
        String critical = "0";

        // Add the test record to the list
        String[] record = new String[]{
            testName,
            endpoint,
            method,
            payload,
            String.valueOf(expectedStatus),
            String.valueOf(actualStatus),
            responseBody.replace("\n", " ").replace(",", ";"),
            resultStatus,
            auth,
            headersLogged,
            critical
        };

        updatedRecords.add(record);
    }

    @Override
    public void onFinish(org.testng.ITestContext context) {
        // Write all updated records to the CSV file after tests finish
        try (CSVWriter writer = new CSVWriter(new FileWriter(OUTPUT_CSV_FILE_PATH))) {
            writer.writeAll(updatedRecords);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
