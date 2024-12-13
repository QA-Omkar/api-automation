package testautomationZineUrl.control;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

@RestController
@RequestMapping("/api")
public class TestControllerExperiment {

    private static String authToken = ""; // Store auth token globally
    private String key = "Authtoken";

    @PostMapping("/run-tests/inputfile")
    public ResponseEntity<Map<String, Object>> runTests(@RequestParam("file") MultipartFile file, @RequestParam("token-key") String tokenKey) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, String>> results = new ArrayList<>();

        try {
            if (file == null || file.isEmpty()) {
                response.put("error", "The uploaded file is empty or missing.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            if (tokenKey != null) {
                key = tokenKey;
            }

            List<String[]> inputData = readCsvFile(file);

            if (inputData == null || inputData.size() <= 1) { // Check for header only or no data
                response.put("error", "The uploaded file contains no valid data.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Prepare output data with header at 0th row
            String[] headers = inputData.get(0); // Get header from the first row
            List<String[]> outputData = new ArrayList<>();
            outputData.add(new String[] { "TestName", "BaseUrl", "Endpoint", "ApiUrl", "Method", "Payload", "ExpectedStatus", "Critical", "Auth", "ResponseBody", "ActualStatus", "Result" });

            // Identify relevant column indices
            Map<String, Integer> columnIndices = getColumnIndices(headers);
            if (columnIndices == null) {
                response.put("error", "Required columns are missing in the uploaded file.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            for (String[] row : inputData.subList(1, inputData.size())) { // Skip header row
                String testName = row[columnIndices.get("TestName")];
                String baseUrl = row[columnIndices.get("BaseUrl")];
                String endpoint = row[columnIndices.get("Endpoint")];
                String method = row[columnIndices.get("Method")];
                String payload = row[columnIndices.get("Payload")];
                String expectedStatus = row[columnIndices.get("ExpectedStatus")];
                String critical = row[columnIndices.get("Critical")];
                String auth = row[columnIndices.get("Auth")];
                String responseBody = "";
                String apiUrl = baseUrl + endpoint;

                System.out.println("Executing Test: " + testName);
                System.out.println("API URL: " + apiUrl);
                System.out.println("Method: " + method);
                System.out.println("Payload: " + payload);

                String actualStatus = "";
                String result = "FAIL";

                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    HttpRequestBase request = createHttpRequest(method, apiUrl, payload);

                    // Set Authorization header if auth token is required
                    if ("1".equals(auth) && !authToken.isEmpty()) {
                        request.setHeader(key, authToken);
                    } else if ("2".equals(auth)) {
                        // Store token if auth = 2
                        HttpResponse response1 = httpClient.execute(request);
                        responseBody = EntityUtils.toString(response1.getEntity());
                        authToken = extractToken(responseBody); // Store the token for later use
                        System.out.println("Token Stored: " + authToken);
                    }

                    HttpResponse response1 = httpClient.execute(request);
                    actualStatus = String.valueOf(response1.getStatusLine().getStatusCode());
                    responseBody = EntityUtils.toString(response1.getEntity());

                    result = expectedStatus.equals(actualStatus) ? "PASS" : "FAIL";
                } catch (Exception e) {
                    responseBody = e.getMessage();
                    actualStatus = "ERROR";
                }

                Map<String, String> resultMap = new HashMap<>();
                resultMap.put("TestName", testName);
                resultMap.put("ApiUrl", apiUrl);
                resultMap.put("ActualStatus", actualStatus);
                resultMap.put("Result", result);

                results.add(resultMap);

                outputData.add(new String[] {
                        testName, baseUrl, endpoint, apiUrl, method, payload, expectedStatus, critical,
                        auth, responseBody, actualStatus, result
                });

                // Stop execution if critical test fails
                if ("1".equals(critical) && "FAIL".equals(result)) {
                    System.out.println("Critical test failed: " + testName);
                    response.put("error", "Critical test failed. Stopping execution.");
                    break;
                }
                
                
            }

            // Save output CSV in ReportOutput folder
            saveCsvFile("ReportOutput", Objects.requireNonNull(file.getOriginalFilename()), outputData);

            // Prepare output response
            response.put("message", "Test completed successfully.");
            response.put("result", results);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Failed to process the input file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private HttpRequestBase createHttpRequest(String method, String url, String payload) throws UnsupportedEncodingException {
        switch (method.toUpperCase()) {
            case "POST":
                HttpPost post = new HttpPost(url);
                post.setEntity(new StringEntity(payload));
                post.setHeader("Content-Type", "application/json");
                return post;
            case "GET":
                return new HttpGet(url);
            case "PUT":
                HttpPut put = new HttpPut(url);
                put.setEntity(new StringEntity(payload));
                put.setHeader("Content-Type", "application/json");
                return put;
            case "PATCH":
                HttpPatch patch = new HttpPatch(url);
                patch.setEntity(new StringEntity(payload));
                patch.setHeader("Content-Type", "application/json");
                return patch;
            case "DELETE":
                return new HttpDelete(url);
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
    }

    private String extractToken(String responseBody) {
        return responseBody.substring(responseBody.indexOf("token\":\"") + 8, responseBody.indexOf("\"", responseBody.indexOf("token\":\"") + 8));
    }

    private List<String[]> readCsvFile(MultipartFile file) throws IOException {
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            return reader.readAll();
        }
        catch (Exception e) {
        	
        
        }
		return null;
    }

    private Map<String, Integer> getColumnIndices(String[] headers) {
        Map<String, Integer> indices = new HashMap<>();
        String[] requiredColumns = {"TestName", "BaseUrl", "Endpoint", "Method", "Payload", "ExpectedStatus", "Critical", "Auth"};

        for (String column : requiredColumns) {
            int index = Arrays.asList(headers).indexOf(column);
            if (index == -1) {
                return null;
            }
            indices.put(column, index);
        }
        return indices;
    }

    private void saveCsvFile(String outputFolder, String inputCsvName, List<String[]> data) throws IOException {
        File folder = new File(outputFolder);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String currentTime = new SimpleDateFormat("HH-mm-ss").format(new Date());
        String outputFileName = "test_report_" + currentDate + "_" + currentTime + ".csv";
        File outputFile = new File(folder, outputFileName);

        try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile))) {
            writer.writeAll(data);
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".csv"));
        if (files != null && files.length > 5) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = 5; i < files.length; i++) {
                files[i].delete();
            }
        }
    }
}