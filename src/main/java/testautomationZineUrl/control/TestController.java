package testautomationZineUrl.control;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.testng.TestNG;
import org.testng.annotations.Test;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import com.opencsv.exceptions.CsvValidationException;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

@RestController
@RequestMapping("/api") // Base URL: http://localhost:8080/api
public class TestController {

    private static final String BASE_URL = "https://zineup-api.codesncoffee.com/api/v1";
    private static final String CSV_FILE_PATH = "ZineApiTestReportInputPostCnc2.csv";
    private static final String OUTPUT_CSV_FILE_PATH = "ZineApiTestReport_Output.csv";
    private static final String OUTPUTFOLDER_PATH_STRING = "ReportOutput"; // Path to OutputFiles folder
	private static final int MAX_REPORTS = 5;
	
    private final List<String[]> updatedRecords = new ArrayList<>(); // To store all updated records
    private String xZineToken = ""; // Store token
    public final Map<String, String> testResults = new HashMap<>();

    // Method to handle the test execution
    @PostMapping("/run-tests")
    public Map<String, Object> runTests(@RequestParam MultipartFile file) {

        try {
        	// Path for the uploaded file
            Path targetPath = Path.of(CSV_FILE_PATH);
            Path tempFilePath = Files.createTempFile("uploaded-temp-file-", ".csv");
            try {

                // Save the uploaded file to a temporary location first
                Files.copy(file.getInputStream(), tempFilePath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Temporary file created at: " + tempFilePath);

                // Now copy the file to the desired CSV_FILE_PATH
                Files.copy(tempFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("File uploaded and saved at: " + targetPath.toString());

                // Initialize TestNG and add the listener to capture output
                TestNG testng = new TestNG();
                CaptureOutput captureOutput = new CaptureOutput();
                testng.addListener(captureOutput);

                // Specify the test classes to run
                testng.setTestClasses(new Class[] { TestController.class }); // Adjust according to your setup
                testng.run(); // Run the tests

                // Now return the content of the updated output.csv after test execution
                return readCsvFileAsJson();

            } catch(Exception e) {
            	JSONObject response = new JSONObject();
            	response.put("error", e.toString());
            	return response.toMap();
            }
            finally {
                // Delete the temporary file after use
                if (Files.exists(tempFilePath)) {
                    try {
                        Files.delete(tempFilePath);
                        System.out.println("Temporary file deleted: " + tempFilePath);
                    } catch (IOException e) {
                        System.err.println("Failed to delete temporary file: " + e.getMessage());
                    }
                }
            }
        } catch(Exception e) {
        	JSONObject response = new JSONObject();
        	response.put("error", e.toString());
        	return response.toMap();
        }
        
        
    }

    // Get status endpoint
    @GetMapping("/status") // Full URL: http://localhost:8080/api/status
    public String getStatus() {
        return "Service is running";
    }

	@Test(priority = 1)
	public void VerifyOtp() {
		String testname = "VerifyOtp";

		try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
			List<String[]> records = reader.readAll();
			System.out.println("Record fetch: " + records.size());

			// Define the updated header with all required columns
			String[] header = { "TestName", "ApiUrl", "Endpoint", "Method", "Payload", "ExpectedStatus", "ActualStatus",
					"ResponseBody", "Result", "Auth", "Headers", "Critical" };
			updatedRecords.add(header); // Add header only once at the beginning
			System.out.println("Record fetch: " + records.size());

			for (int i = 4; i < 7; i++) { // Adjust indices for VerifyOtp test cases
				String[] record = records.get(i);

				String testName = record[0];

				String endpoint = record[2];
				String method = record[3];
				String payload = record[4];
				int expectedStatus = Integer.parseInt(record[5]);
				String auth = record[9]; // Fetch "Auth" value
				String headersLogged = "Content-Type: application/json, Accept: */*"; // Default headers with Accept:
																						// */*
				String critical = record[11];

				String responseBody = "";
				int actualStatus = 0;
				String result = "Fail";

				try {
					// Create the request
					RequestSpecification request = RestAssured.given()
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE) // Add Accept header
							.body(payload);

					// Add token if "Auth" is "1"
					if (auth.equals("1")) {
						if (xZineToken == null || xZineToken.isEmpty()) {
							throw new RuntimeException(
									"x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
						}
						request.header("x-zine-token", xZineToken);
						headersLogged += ", x-zine-token: " + xZineToken; // Log token if included
					}

					// Send the POST request
					Response response = request.when().post(BASE_URL + endpoint);

					// Extract response details
					responseBody = response.getBody().asString();
					actualStatus = response.getStatusCode();
					result = actualStatus == expectedStatus ? "Pass" : "Fail";

					// Extract x-zine-token if conditions are met
					if (endpoint.equals("/user/verify-otp") && expectedStatus == 200 && actualStatus == 200) {
						JsonPath jsonPath = new JsonPath(responseBody);
						xZineToken = jsonPath.getString("data.x-zine-token");
						System.out.println("Extracted x-zine-token: " + xZineToken);
					}

				} catch (Exception e) {
					responseBody = "Error: " + e.getMessage();
					System.err.println("Error processing test case '" + testName + "': " + e.getMessage());
				}

				// Log test details including headers
				System.out.println("Test: " + testName);
				System.out.println("Endpoint: " + endpoint);
				System.out.println("Method: " + method);
				System.out.println("Payload: " + payload);
				System.out.println("Expected Status: " + expectedStatus);
				System.out.println("Actual Status: " + actualStatus);
				System.out.println("Response: " + responseBody);
				System.out.println("Result: " + result);
				System.out.println("Auth: " + auth);
				System.out.println("Headers: " + headersLogged);
				System.out.println("Critical: " + critical);

				System.out.println("----------------------------------------------------------");
				testResults.put(testName, "Result: " + result + " - " + responseBody); // Store the result

				// Update CSV with Auth and Headers columns
				String[] updatedRecord = new String[] { testName, BASE_URL + endpoint, endpoint, method,
						payload.replace("\n", " ").replace(",", ";"), String.valueOf(expectedStatus),
						String.valueOf(actualStatus), responseBody.replace("\n", " ").replace(",", ";"), result, auth, // Include
																														// the
																														// "Auth"
																														// value
						headersLogged, // Include the headers sent in the request
						critical };

				updatedRecords.add(updatedRecord);
				if ("1".equals(critical) && "Fail".equals(result)) {
					System.out.println("Critical test failed. Stopping CI/CD pipeline.");
					// Throwing an exception to stop the pipeline
					throw new RuntimeException("Critical test '" + testName + "' failed.");
				}
			}
		} catch (IOException | CsvException e) {
			System.err.println("Error reading CSV file: " + e.getMessage());
			testResults.put(testname, "Error: " + e.getMessage());
		}
	}

	@Test(priority = 2)
	public void NewUserRegister() {

		String testname = "New User Registration";

		try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
			List<String[]> records = reader.readAll();

			// Start from the second row (index 1) to skip the header row
			for (int i = 1; i < 4; i++) {
				String[] record = records.get(i);

				String testName = record[0];
				String endpoint = record[2];
				String method = record[3];
				String payload = record[4];
				int expectedStatus = Integer.parseInt(record[5]);
				String auth = record[9]; // Get the "Auth" value from column 10
				String critical = record[11];
				String responseBody = "";
				int actualStatus = 0;
				String result = "Fail";
				String headersLogged = "Content-Type: application/json, Accept: */*"; // Default headers with Accept

				try {
					// Create the request
					RequestSpecification request = RestAssured.given()
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE) // Add Accept header
							.body(payload);

					// Add token if "Auth" is "1"
					if (auth.equals("1")) {
						if (xZineToken == null || xZineToken.isEmpty()) {
							throw new RuntimeException(
									"x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
						}
						request.header("x-zine-token", xZineToken);
						headersLogged += ", x-zine-token: " + xZineToken; // Log token if included
					}

					// Send the POST request
					Response response = request.when().post(BASE_URL + endpoint);

					// Extract response details
					responseBody = response.getBody().asString();
					actualStatus = response.getStatusCode();
					result = actualStatus == expectedStatus ? "Pass" : "Fail";

				} catch (Exception e) {
					responseBody = "Error: " + e.getMessage();
					System.err.println("Error processing test case '" + testName + "': " + e.getMessage());

				}

				// Log test details including headers
				System.out.println("Test: " + testName);
				System.out.println("Endpoint: " + endpoint);
				System.out.println("Method: POST");
				System.out.println("Payload: " + payload);
				System.out.println("Expected Status: " + expectedStatus);
				System.out.println("Actual Status: " + actualStatus);
				System.out.println("Response: " + responseBody);
				System.out.println("Result: " + result);
				System.out.println("Auth: " + auth);
				System.out.println("Headers: " + headersLogged);
				System.out.println("Critical: " + critical);
				System.out.println("----------------------------------------------------------");
				testResults.put(testName, "Result: " + result + " - " + responseBody); // Store the result

				// Update CSV with Auth and Headers columns
				String[] updatedRecord = new String[] { testName, BASE_URL + endpoint, endpoint, method,
						payload.replace("\n", " ").replace(",", ";"), String.valueOf(expectedStatus),
						String.valueOf(actualStatus), responseBody.replace("\n", " ").replace(",", ";"), result, auth, // Include
																														// the
																														// "Auth"
																														// value
						headersLogged, // Include the headers sent in the request
						critical };

				updatedRecords.add(updatedRecord);
				if ("1".equals(critical) && "Fail".equals(result)) {
					System.out.println("Critical test failed. Stopping CI/CD pipeline.");

					// Throwing an exception to stop the pipeline
					throw new RuntimeException("Critical test '" + testName + "' failed.");
				}
			}
		} catch (IOException | CsvException e) {
			System.err.println("Error reading CSV file: " + e.getMessage());
			testResults.put(testname, "Error: " + e.getMessage());

		}

	}

	@Test(priority = 3)
	public void Login() {
		String testname = "Login";

		try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
			List<String[]> records = reader.readAll();

			System.out.println("Record fetch: " + records.size());

			for (int i = 7; i < 10; i++) { // Adjust indices for Login test cases
				String[] record = records.get(i);

				// Extracting values from CSV record
				String testName = record[0];
				String endpoint = record[2];
				String method = record[3];
				String payload = record[4];
				int expectedStatus = Integer.parseInt(record[5]);
				String auth = record[9]; // Fetch "Auth" value
				String headersLogged = "Content-Type: application/json, Accept: */*"; // Default headers with Accept:
																						// */*
				String critical = record[11];

				String responseBody = "";
				int actualStatus = 0;
				String result = "Fail";

				try {
					// Create the request
					RequestSpecification request = RestAssured.given()
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE) // Add Accept header
							.body(payload);

					// Add token if "Auth" is "1"
					if (auth.equals("1")) {
						if (xZineToken == null || xZineToken.isEmpty()) {
							throw new RuntimeException(
									"x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
						}
						request.header("x-zine-token", xZineToken);
						headersLogged += ", x-zine-token: " + xZineToken; // Log token if included
					}

					// Send the POST request
					Response response = request.when().post(BASE_URL + endpoint);

					// Extract response details
					responseBody = response.getBody().asString();
					actualStatus = response.getStatusCode();
					result = actualStatus == expectedStatus ? "Pass" : "Fail";

				} catch (Exception e) {
					responseBody = "Error: " + e.getMessage();
					System.err.println("Error processing test case '" + testName + "': " + e.getMessage());
				}

				// Logging test details including headers and response
				logTestDetails(testName, endpoint, method, payload, expectedStatus, actualStatus, responseBody, result,
						auth, headersLogged, critical);

				// Update the result into the map
				testResults.put(testName, "Result: " + result + " - " + responseBody);

				// Update CSV with Auth and Headers columns
				updateCSV(testName, endpoint, method, payload, expectedStatus, actualStatus, responseBody, result, auth,
						headersLogged, critical);

				if ("1".equals(critical) && "Fail".equals(result)) {
					System.out.println("Critical test failed. Stopping CI/CD pipeline.");
					throw new RuntimeException("Critical test '" + testName + "' failed.");
				}
			}
		} catch (IOException | CsvException e) {
			System.err.println("Error reading CSV file: " + e.getMessage());
			testResults.put(testname, "Error: " + e.getMessage());
		}
	}

	private void logTestDetails(String testName, String endpoint, String method, String payload, int expectedStatus,
			int actualStatus, String responseBody, String result, String auth, String headersLogged, String critical) {
		System.out.println("Test: " + testName);
		System.out.println("Endpoint: " + endpoint);
		System.out.println("Method: " + method);
		System.out.println("Payload: " + payload);
		System.out.println("Expected Status: " + expectedStatus);
		System.out.println("Actual Status: " + actualStatus);
		System.out.println("Response: " + responseBody);
		System.out.println("Result: " + result);
		System.out.println("Auth: " + auth);
		System.out.println("Headers: " + headersLogged);
		System.out.println("Critical: " + critical);
		System.out.println("----------------------------------------------------------");
	}

	private void updateCSV(String testName, String endpoint, String method, String payload, int expectedStatus,
			int actualStatus, String responseBody, String result, String auth, String headersLogged, String critical) {
		String[] updatedRecord = new String[] { testName, BASE_URL + endpoint, endpoint, method,
				payload.replace("\n", " ").replace(",", ";"), String.valueOf(expectedStatus),
				String.valueOf(actualStatus), responseBody.replace("\n", " ").replace(",", ";"), result, auth, // Include
																												// the
																												// "Auth"
																												// value
				headersLogged, // Include the headers sent in the request
				critical };

		updatedRecords.add(updatedRecord);
	}

	@Test(priority = 4)
	public void GetUser() {
		String testname = "GetUser";

		try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
			List<String[]> records = reader.readAll();

			for (int i = 10; i < 12; i++) { // Adjust for getUser test cases
				String[] record = records.get(i);

				String testName = record[0];
				String endpoint = record[2];
				String method = record[3];
				String payload = record[4];
				int expectedStatus = Integer.parseInt(record[5]);
				String auth = record[9]; // Get value of the "Auth" column
				String critical = record[11];

				String responseBody = "";
				int actualStatus = 0;
				String result = "Fail";
				String headersLogged = "Content-Type: application/json, Accept: */*";

				try {
					// Request specification setup
					RequestSpecification request = RestAssured.given()
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);

					// Add token to headers if required by "Auth" column
					if (auth.equals("1")) {
						if (xZineToken == null || xZineToken.isEmpty()) {
							throw new RuntimeException(
									"x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
						}
						request.header("x-zine-token", xZineToken);
						headersLogged += ", x-zine-token: " + xZineToken; // Log token in headers
					}
					// Send the GET request
					Response response = request.when().get(BASE_URL + endpoint);

					// Extract response details
					responseBody = response.getBody().asString();
					actualStatus = response.getStatusCode();
					result = actualStatus == expectedStatus ? "Pass" : "Fail";

				} catch (Exception e) {
					responseBody = "Error: " + e.getMessage();
					System.err.println("Error processing test case '" + testName + "': " + e.getMessage());
				}

				// Log test details including headers
				System.out.println("Test: " + testName);
				System.out.println("Endpoint: " + endpoint);
				System.out.println("Method: " + method);
				System.out.println("Payload: " + payload);
				System.out.println("Expected Status: " + expectedStatus);
				System.out.println("Actual Status: " + actualStatus);
				System.out.println("Response: " + responseBody);
				System.out.println("Result: " + result);
				System.out.println("Auth: " + auth);
				System.out.println("Headers: " + headersLogged);
				System.out.println("Critical: " + critical);
				System.out.println("----------------------------------------------------------");
				testResults.put(testName, "Result: " + result + " - " + responseBody); // Store the result

				// Update CSV with Headers field
				String[] updatedRecord = new String[] { testName, BASE_URL + endpoint, endpoint, method, payload,
						String.valueOf(expectedStatus), String.valueOf(actualStatus),
						responseBody.replace("\n", " ").replace(",", ";"), result, auth, headersLogged, // Add headers
																										// to CSV record
						critical };

				updatedRecords.add(updatedRecord);
				if ("1".equals(critical) && "Fail".equals(result)) {
					System.out.println("Critical test failed. Stopping CI/CD pipeline.");
					// Throwing an exception to stop the pipeline
					throw new RuntimeException("Critical test '" + testName + "' failed.");
				}
			}
		} catch (IOException | CsvException e) {
			System.err.println("Error reading CSV file: " + e.getMessage());
			testResults.put(testname, "Error: " + e.getMessage());

		}
	}

	@Test(priority = 5)
	public void UpdateUser() {
		String testname = "UpdateUser";

		try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
			List<String[]> records = reader.readAll();

			for (int i = 12; i < 15; i++) { // Adjust for updateUser test cases
				String[] record = records.get(i);

				String testName = record[0];
				String endpoint = record[2];
				String method = record[3];
				String payload = record[4];
				int expectedStatus = Integer.parseInt(record[5]);
				String auth = record[9]; // Get value of the "Auth" column
				String critical = record[11];

				String responseBody = "";
				int actualStatus = 0;
				String result = "Fail";
				String headersLogged = "Content-Type: application/json, Accept: */*";

				try {
					// Create the request
					RequestSpecification request = RestAssured.given()
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE).body(payload);

					// Add token only if "Auth" is 1
					if (auth.equals("1")) {
						if (xZineToken == null || xZineToken.isEmpty()) {
							throw new RuntimeException(
									"x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
						}
						request.header("x-zine-token", xZineToken);
						headersLogged += ", x-zine-token: " + xZineToken; // Add token to headers log
					}

					// Send the PATCH request
					Response response = request.when().patch(BASE_URL + endpoint);

					// Extract response details
					responseBody = response.getBody().asString();
					actualStatus = response.getStatusCode();
					result = actualStatus == expectedStatus ? "Pass" : "Fail";

				} catch (Exception e) {
					responseBody = "Error: " + e.getMessage();
					System.err.println("Error processing test case '" + testName + "': " + e.getMessage());
				}

				// Log test details including headers
				System.out.println("Test: " + testName);
				System.out.println("Endpoint: " + endpoint);
				System.out.println("Method: PATCH");
				System.out.println("Payload: " + payload);
				System.out.println("Expected Status: " + expectedStatus);
				System.out.println("Actual Status: " + actualStatus);
				System.out.println("Response: " + responseBody);
				System.out.println("Result: " + result);
				System.out.println("Auth: " + auth);
				System.out.println("Headers: " + headersLogged);
				System.out.println("Critical: " + critical);
				System.out.println("----------------------------------------------------------");
				testResults.put(testName, "Result: " + result + " - " + responseBody); // Store the result

				// Update CSV with Headers field
				String[] updatedRecord = new String[] { testName, BASE_URL + endpoint, endpoint, method, payload,
						String.valueOf(expectedStatus), String.valueOf(actualStatus),
						responseBody.replace("\n", " ").replace(",", ";"), result, auth, headersLogged, // Add headers
																										// to CSV record
						critical };

				updatedRecords.add(updatedRecord);
				if ("1".equals(critical) && "Fail".equals(result)) {
					System.out.println("Critical test failed. Stopping CI/CD pipeline.");
					// Throwing an exception to stop the pipeline
					throw new RuntimeException("Critical test '" + testName + "' failed.");
				}
			}
		} catch (IOException | CsvException e) {
			System.err.println("Error reading CSV file: " + e.getMessage());
			testResults.put(testname, "Error: " + e.getMessage());

		}
	}

	@Test(priority = 6)
	public void GetTemplate() {
		String testname = "GetTemplate";

		try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
			List<String[]> records = reader.readAll();

			// Loop through the records specific to getTemplate test cases
			for (int i = 15; i < 17; i++) { // Adjust indices based on your CSV structure
				String[] record = records.get(i);

				String testName = record[0];
				String endpoint = record[2];
				String method = record[3];
				String payload = record[4]; // Not used for GET, but included for logging
				int expectedStatus = Integer.parseInt(record[5]);
				String auth = record[9]; // Get value of the "Auth" column
				String critical = record[11];

				String responseBody = "";
				int actualStatus = 0;
				String result = "Fail";
				String headersLogged = "Content-Type: application/json, Accept: */*";

				try {
					// Create the request
					RequestSpecification request = RestAssured.given()
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);

					// Add token only if "Auth" is 1
					if (auth.equals("1")) {
						if (xZineToken == null || xZineToken.isEmpty()) {
							throw new RuntimeException(
									"x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
						}
						request.header("x-zine-token", xZineToken);
						headersLogged += ", x-zine-token: " + xZineToken; // Log token
					}

					// Send the GET request
					Response response = request.when().get(BASE_URL + endpoint);

					// Extract response details
					responseBody = response.getBody().asString();
					actualStatus = response.getStatusCode();
					result = actualStatus == expectedStatus ? "Pass" : "Fail";

					// If response body is empty, log it clearly
					if (responseBody.isEmpty()) {
						responseBody = "No content returned from API.";
					}

				} catch (Exception e) {
					responseBody = "Error: " + e.getMessage();
					System.err.println("Error processing test case '" + testName + "': " + e.getMessage());
				}

				// Log test details including headers
				System.out.println("Test: " + testName);
				System.out.println("Endpoint: " + endpoint);
				System.out.println("Method: GET");
				System.out.println("Expected Status: " + expectedStatus);
				System.out.println("Actual Status: " + actualStatus);
				System.out.println("Response: " + responseBody); // This prints the response body
				System.out.println("Result: " + result);
				System.out.println("Auth: " + auth);
				System.out.println("Headers: " + headersLogged);
				System.out.println("Critical: " + critical);
				System.out.println("----------------------------------------------------------");
				testResults.put(testName, "Result: " + result + " - " + responseBody); // Store the result

				// Update CSV with Headers field
				String[] updatedRecord = new String[] { testName, BASE_URL + endpoint, endpoint, method, payload,
						String.valueOf(expectedStatus), String.valueOf(actualStatus),
						responseBody.replace("\n", " ").replace(",", ";"), // Clean response body for CSV
						result, auth, headersLogged, // Add headers to CSV record
						critical };

				updatedRecords.add(updatedRecord);
				if ("1".equals(critical) && "Fail".equals(result)) {
					System.out.println("Critical test failed. Stopping CI/CD pipeline.");
					// Throwing an exception to stop the pipeline
					throw new RuntimeException("Critical test '" + testName + "' failed.");
				}
			}
		} catch (IOException | CsvException e) {
			System.err.println("Error reading CSV file: " + e.getMessage());
			testResults.put(testname, "Error: " + e.getMessage());

		}
	}

	@Test(priority = 7)
	public void CreateZine() {
		String testname = "CreateZine";

		try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
			List<String[]> records = reader.readAll();

			// Loop through the records specific to createZine test cases
			for (int i = 17; i < 20; i++) { // Adjust indices based on your CSV structure
				String[] record = records.get(i);
				String testName = record[0];
				String endpoint = record[2];
				String method = record[3];
				String payload = record[4];
				int expectedStatus = Integer.parseInt(record[5]);
				String auth = record[9]; // Get value of the "Auth" column
				String critical = record[11];

				String responseBody = "";
				int actualStatus = 0;
				String result = "Fail";
				String headersLogged = "Content-Type: application/json, Accept: */*";

				try {
					// Create the request
					RequestSpecification request = RestAssured.given()
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE).body(payload);

					// Add token only if "Auth" is 1
					if (auth.equals("1")) {
						if (xZineToken == null || xZineToken.isEmpty()) {
							throw new RuntimeException(
									"x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
						}
						request.header("x-zine-token", xZineToken);
						headersLogged += ", x-zine-token: " + xZineToken; // Log token
					}

					// Send the POST request
					Response response = request.when().post(BASE_URL + endpoint);

					// Extract response details
					responseBody = response.getBody().asString();
					actualStatus = response.getStatusCode();
					result = actualStatus == expectedStatus ? "Pass" : "Fail";

					// If response body is empty, log it clearly
					if (responseBody.isEmpty()) {
						responseBody = "No content returned from API.";
					}

				} catch (Exception e) {
					responseBody = "Error: " + e.getMessage();
					System.err.println("Error processing test case '" + testName + "': " + e.getMessage());
				}

				// Log test details including headers
				System.out.println("Test: " + testName);
				System.out.println("Endpoint: " + endpoint);
				System.out.println("Method: POST");
				System.out.println("Expected Status: " + expectedStatus);
				System.out.println("Actual Status: " + actualStatus);
				System.out.println("Response: " + responseBody); // This prints the response body
				System.out.println("Result: " + result);
				System.out.println("Auth: " + auth);
				System.out.println("Headers: " + headersLogged);
				System.out.println("Critical: " + critical);
				System.out.println("----------------------------------------------------------");
				testResults.put(testName, "Result: " + result + " - " + responseBody); // Store the result

				// Update CSV with Headers field
				String[] updatedRecord = new String[] { testName, BASE_URL + endpoint, endpoint, method,
						payload.replace("\n", " ").replace(",", ";"), String.valueOf(expectedStatus),
						String.valueOf(actualStatus), responseBody.replace("\n", " ").replace(",", ";"), // Clean
																											// response
																											// body for
																											// CSV
						result, auth, headersLogged, // Add headers to CSV record
						critical };

				updatedRecords.add(updatedRecord);
				if ("1".equals(critical) && "Fail".equals(result)) {
					System.out.println("Critical test failed. Stopping CI/CD pipeline.");
					// Throwing an exception to stop the pipeline
					throw new RuntimeException("Critical test '" + testName + "' failed.");
				}
			}
		} catch (IOException | CsvException e) {
			System.err.println("Error reading CSV file: " + e.getMessage());
			testResults.put(testname, "Error: " + e.getMessage());

		}
	}

	@Test(priority = 8)
	public void GetCompleteZine() {
		String testname = "GetCompleteZine";

		try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
			List<String[]> records = reader.readAll();

			// Loop through the records specific to Get Complete Zine test cases
			for (int i = 20; i < 22; i++) { // Adjust indices based on your CSV structure
				String[] record = records.get(i);

				String testName = record[0];
				String endpoint = record[2];
				String method = record[3];
				String payload = record[4]; // Not used for GET, but included for logging
				int expectedStatus = Integer.parseInt(record[5]);
				String auth = record[9]; // Get value of the "Auth" column
				String critical = record[11];

				String responseBody = "";
				int actualStatus = 0;
				String result = "Fail";
				String headersLogged = "Content-Type: application/json, Accept: */*";

				try {
					// Create the request
					RequestSpecification request = RestAssured.given()
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);

					// Add token only if "Auth" is 1
					if ("1".equals(auth)) {
						if (xZineToken == null || xZineToken.isEmpty()) {
							throw new RuntimeException(
									"x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
						}
						request.header("x-zine-token", xZineToken);
						headersLogged += ", x-zine-token: " + xZineToken; // Log token
					}

					// Send the GET request
					Response response = request.when().get(BASE_URL + endpoint);

					// Extract response details
					responseBody = response.getBody().asString();
					actualStatus = response.getStatusCode();
					result = actualStatus == expectedStatus ? "Pass" : "Fail";

					// Handle empty response body
					if (responseBody.isEmpty()) {
						responseBody = "No content returned from API.";
					}

				} catch (Exception e) {
					responseBody = "Error: " + e.getMessage();
					System.err.println("Error processing test case '" + testName + "': " + e.getMessage());
				}

				// Log test details including headers
				System.out.println("Test: " + testName);
				System.out.println("Endpoint: " + endpoint);
				System.out.println("Method: GET");
				System.out.println("Expected Status: " + expectedStatus);
				System.out.println("Actual Status: " + actualStatus);
				System.out.println("Response: " + responseBody); // This prints the response body
				System.out.println("Result: " + result);
				System.out.println("Auth: " + auth);
				System.out.println("Headers: " + headersLogged);
				System.out.println("Critical: " + critical);
				System.out.println("----------------------------------------------------------");
				testResults.put(testName, "Result: " + result + " - " + responseBody); // Store the result

				// Update CSV with Headers field
				String[] updatedRecord = new String[] { testName, BASE_URL + endpoint, endpoint, method, payload,
						String.valueOf(expectedStatus), String.valueOf(actualStatus),
						responseBody.replace("\n", " ").replace(",", ";"), // Clean response body for CSV
						result, auth, headersLogged, // Add headers to CSV record
						critical };

				updatedRecords.add(updatedRecord);
				if ("1".equals(critical) && "Fail".equals(result)) {
					System.out.println("Critical test failed. Stopping CI/CD pipeline.");
					// Throwing an exception to stop the pipeline
					throw new RuntimeException("Critical test '" + testName + "' failed.");
				}
			}
		} catch (IOException | CsvException e) {
			System.err.println("Error reading CSV file: " + e.getMessage());
			testResults.put(testname, "Error: " + e.getMessage());

		}
	}

	@Test(priority = 9)
	public void GetAllSqlZine() {
		String testname = "GetAllSqlZine";

		try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
			List<String[]> records = reader.readAll();

			// Loop through the records specific to Get Complete Zine test cases
			for (int i = 22; i < 23; i++) { // Adjust indices based on your CSV structure
				String[] record = records.get(i);

				String testName = record[0];
				String endpoint = record[2];
				String method = record[3];
				String payload = record[4]; // Not used for GET, but included for logging
				int expectedStatus = Integer.parseInt(record[5]);
				String auth = record[9]; // Get value of the "Auth" column
				String critical = record[11];

				String responseBody = "";
				int actualStatus = 0;
				String result = "Fail";
				String headersLogged = "Content-Type: application/json, Accept: */*";

				try {
					// Create the request
					RequestSpecification request = RestAssured.given()
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);

					// Add token only if "Auth" is 1
					if ("1".equals(auth)) {
						if (xZineToken == null || xZineToken.isEmpty()) {
							throw new RuntimeException(
									"x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
						}
						request.header("x-zine-token", xZineToken);
						headersLogged += ", x-zine-token: " + xZineToken; // Log token
					}

					// Send the GET request
					Response response = request.when().get(BASE_URL + endpoint);

					// Extract response details
					responseBody = response.getBody().asString();
					actualStatus = response.getStatusCode();
					result = (actualStatus == expectedStatus) ? "Pass" : "Fail";

					// Handle empty response body
					if (responseBody.isEmpty()) {
						responseBody = "No content returned from API.";
					}

				} catch (Exception e) {
					responseBody = "Error: " + e.getMessage();
					System.err.println("Error processing test case '" + testName + "': " + e.getMessage());
				}

				// Log test details including headers
				System.out.println("Test: " + testName);
				System.out.println("Endpoint: " + endpoint);
				System.out.println("Method: GET");
				System.out.println("Expected Status: " + expectedStatus);
				System.out.println("Actual Status: " + actualStatus);
				System.out.println("Response: " + responseBody); // This prints the response body
				System.out.println("Result: " + result);
				System.out.println("Auth: " + auth);
				System.out.println("Headers: " + headersLogged);
				System.out.println("Critical: " + critical);
				System.out.println("----------------------------------------------------------");
				testResults.put(testName, "Result: " + result + " - " + responseBody); // Store the result

				// Update CSV with Headers field
				String[] updatedRecord = new String[] { testName, BASE_URL + endpoint, endpoint, method, payload,
						String.valueOf(expectedStatus), String.valueOf(actualStatus),
						responseBody.replace("\n", " ").replace(",", ";"), // Clean response body for CSV
						result, auth, headersLogged, // Add headers to CSV record
						critical };

				updatedRecords.add(updatedRecord);
				if ("1".equals(critical) && "Fail".equals(result)) {
					System.out.println("Critical test failed. Stopping CI/CD pipeline.");
					// Throwing an exception to stop the pipeline
					throw new RuntimeException("Critical test '" + testName + "' failed.");
				}
			}
		} catch (IOException | CsvException e) {
			System.err.println("Error reading CSV file: " + e.getMessage());
			testResults.put(testname, "Error: " + e.getMessage());

		}
	}

	@Test(priority = 10)
	public void GetOthersZine() {
		String testname = "GetOthersZine";

		try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
			List<String[]> records = reader.readAll();

			// Loop through the records specific to Get Complete Zine test cases
			for (int i = 23; i < 25; i++) { // Adjust indices based on your CSV structure
				String[] record = records.get(i);

				String testName = record[0];
				String endpoint = record[2];
				String method = record[3];
				String payload = record[4]; // Not used for GET, but included for logging
				int expectedStatus = Integer.parseInt(record[5]);
				String auth = record[9]; // Get value of the "Auth" column
				String critical = record[11];

				String responseBody = "";
				int actualStatus = 0;
				String result = "Fail";
				String headersLogged = "Content-Type: application/json, Accept: */*";

				try {
					// Create the request
					RequestSpecification request = RestAssured.given()
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);

					// Add token only if "Auth" is 1
					if ("1".equals(auth)) {
						if (xZineToken == null || xZineToken.isEmpty()) {
							throw new RuntimeException(
									"x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
						}
						request.header("x-zine-token", xZineToken);
						headersLogged += ", x-zine-token: " + xZineToken; // Log token
					}

					// Send the GET request
					Response response = request.when().get(BASE_URL + endpoint);

					// Extract response details
					responseBody = response.getBody().asString();
					actualStatus = response.getStatusCode();
					result = (actualStatus == expectedStatus) ? "Pass" : "Fail";

					// Handle empty response body
					if (responseBody.isEmpty()) {
						responseBody = "No content returned from API.";
					}

				} catch (Exception e) {
					responseBody = "Error: " + e.getMessage();
					System.err.println("Error processing test case '" + testName + "': " + e.getMessage());
				}

				// Log test details including headers
				System.out.println("Test: " + testName);
				System.out.println("Endpoint: " + endpoint);
				System.out.println("Method: GET");
				System.out.println("Expected Status: " + expectedStatus);
				System.out.println("Actual Status: " + actualStatus);
				System.out.println("Response: " + responseBody); // This prints the response body
				System.out.println("Result: " + result);
				System.out.println("Auth: " + auth);
				System.out.println("Headers: " + headersLogged);
				System.out.println("Critical: " + critical);
				System.out.println("----------------------------------------------------------");
				testResults.put(testName, "Result: " + result + " - " + responseBody); // Store the result

				// Update CSV with Headers field
				String[] updatedRecord = new String[] { testName, BASE_URL + endpoint, endpoint, method, payload,
						String.valueOf(expectedStatus), String.valueOf(actualStatus),
						responseBody.replace("\n", " ").replace(",", ";"), // Clean response body for CSV
						result, auth, headersLogged, // Add headers to CSV record
						critical };

				updatedRecords.add(updatedRecord);
				if ("1".equals(critical) && "Fail".equals(result)) {
					System.out.println("Critical test failed. Stopping CI/CD pipeline.");
					// Throwing an exception to stop the pipeline
					throw new RuntimeException("Critical test '" + testName + "' failed.");
				}
			}
		} catch (IOException | CsvException e) {
			System.err.println("Error reading CSV file: " + e.getMessage());
			testResults.put(testname, "Error: " + e.getMessage());

		}
	}

	@Test(priority = 11)
	public void FetchOwnZine() {
		String testname = "FetchOwnZine";

		try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
			List<String[]> records = reader.readAll();

			// Loop through the records specific to Get Complete Zine test cases
			for (int i = 25; i < 27; i++) { // Adjust indices based on your CSV structure
				String[] record = records.get(i);

				String testName = record[0];
				String endpoint = record[2];
				String method = record[3];
				String payload = record[4]; // Not used for GET, but included for logging
				int expectedStatus = Integer.parseInt(record[5]);
				String auth = record[9]; // Get value of the "Auth" column
				String critical = record[11];

				String responseBody = "";
				int actualStatus = 0;
				String result = "Fail";
				String headersLogged = "Content-Type: application/json, Accept: */*";

				try {
					// Create the request
					RequestSpecification request = RestAssured.given()
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);

					// Add token only if "Auth" is 1
					if ("1".equals(auth)) {
						if (xZineToken == null || xZineToken.isEmpty()) {
							throw new RuntimeException(
									"x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
						}
						request.header("x-zine-token", xZineToken);
						headersLogged += ", x-zine-token: " + xZineToken; // Log token
					}

					// Send the GET request
					Response response = request.when().get(BASE_URL + endpoint);

					// Extract response details
					responseBody = response.getBody().asString();
					actualStatus = response.getStatusCode();
					result = (actualStatus == expectedStatus) ? "Pass" : "Fail";

					// Handle empty response body
					if (responseBody.isEmpty()) {
						responseBody = "No content returned from API.";
					}

				} catch (Exception e) {
					responseBody = "Error: " + e.getMessage();
					System.err.println("Error processing test case '" + testName + "': " + e.getMessage());
				}

				// Log test details including headers
				System.out.println("Test: " + testName);
				System.out.println("Endpoint: " + endpoint);
				System.out.println("Method: GET");
				System.out.println("Expected Status: " + expectedStatus);
				System.out.println("Actual Status: " + actualStatus);
				System.out.println("Response: " + responseBody); // This prints the response body
				System.out.println("Result: " + result);
				System.out.println("Auth: " + auth);
				System.out.println("Headers: " + headersLogged);
				System.out.println("Critical: " + critical);
				System.out.println("----------------------------------------------------------");
				testResults.put(testName, "Result: " + result + " - " + responseBody); // Store the result

				// Update CSV with Headers field
				String[] updatedRecord = new String[] { testName, BASE_URL + endpoint, endpoint, method, payload,
						String.valueOf(expectedStatus), String.valueOf(actualStatus),
						responseBody.replace("\n", " ").replace(",", ";"), // Clean response body for CSV
						result, auth, headersLogged, // Add headers to CSV record
						critical };

				updatedRecords.add(updatedRecord);
				if ("1".equals(critical) && "Fail".equals(result)) {
					System.out.println("Critical test failed. Stopping CI/CD pipeline.");
					// Throwing an exception to stop the pipeline
					throw new RuntimeException("Critical test '" + testName + "' failed.");
				}
			}
		} catch (IOException | CsvException e) {
			System.err.println("Error reading CSV file: " + e.getMessage());
			testResults.put(testname, "Error: " + e.getMessage());

		}
	}
	
	
	

  @Test(priority=12)
  public void printAllRecords() {
      // Print to console
      System.out.println("----- Final Test Records -----");
      for (String[] record : updatedRecords) {
          System.out.println(String.join(" | ", record));
      }

    
//    Write updated records back to the CSV
	  try (CSVWriter writer = new CSVWriter(new FileWriter(OUTPUT_CSV_FILE_PATH))) {
            writer.writeAll(updatedRecords);
        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
     
      
	  // Generate today's date and time
      LocalDateTime now = LocalDateTime.now();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
      String dateTimeString = now.format(formatter);

      // Ensure the folder exists, create it if it doesn't
      File outputFolder = new File(OUTPUTFOLDER_PATH_STRING);
      if (!outputFolder.exists()) {
          if (outputFolder.mkdirs()) {
              System.out.println("Created ReportOutput directory.");
          } else {
              System.err.println("Failed to create ReportOutput directory.");
              return;
          }
      }

      // Generate the unique file name with today's date and time (e.g., ReportOutput_2024-12-05_14-30-45.csv)
      String fileName = OUTPUTFOLDER_PATH_STRING + File.separator + "ReportOutput_" + dateTimeString + ".csv";
      File outputFile = new File(fileName);

      // Write the updated records to the CSV file with date and time in the name
      try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile))) {
          writer.writeAll(updatedRecords); // Write all test records to the new CSV
          System.out.println("Test records saved to: " + outputFile.getAbsolutePath());
      } catch (IOException e) {
          System.err.println("Error writing to CSV file: " + e.getMessage());
      }
      
      // Delete old reports
      keepLatestReports(outputFolder, MAX_REPORTS);
  }


  private static void keepLatestReports(File folder, int maxReports) {
      File[] files = folder.listFiles((dir, name) -> name.startsWith("ReportOutput_") && name.endsWith(".csv"));
      if (files != null && files.length > maxReports) {
          // Sort files by last modified time (newest first)
          Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

          // Keep the latest 7 reports and delete older ones
          for (int i = maxReports; i < files.length; i++) {
              try {
                  Files.delete(files[i].toPath());
                  System.out.println("Deleted old report: " + files[i].getName());
              } catch (IOException e) {
                  System.err.println("Error deleting file: " + files[i].getName() + " - " + e.getMessage());
              }
          }
      }
      
      
      
      
      
  }

  
//Method to read the CSV file and return the content as JSON
  private Map<String, Object> readCsvFileAsJson() throws CsvValidationException, JSONException {
	  JSONObject response = new JSONObject();
	  
	    JSONArray jsonArray = new JSONArray();
	    boolean criticalFail = false; // Track if any Critical = 1 test case fails

	    try (CSVReader csvReader = new CSVReader(new FileReader(OUTPUT_CSV_FILE_PATH))) {
	        String[] nextLine;
	        while ((nextLine = csvReader.readNext()) != null) {
	            JSONObject jsonObject = new JSONObject();

	            // Map CSV data to JSON fields
	            jsonObject.put("TestName", nextLine[0]);
	            jsonObject.put("ApiUrl", nextLine[1]);
	            jsonObject.put("Endpoint", nextLine[2]);
	            jsonObject.put("Method", nextLine[3]);
	            jsonObject.put("Payload", nextLine[4]);
	            jsonObject.put("ExpectedStatus", nextLine[5]);
	            jsonObject.put("ActualStatus", nextLine[6]);
	            jsonObject.put("ResponseBody", nextLine[7]);
	            jsonObject.put("Result", nextLine[8]);
	            jsonObject.put("Auth", nextLine[9]);
	            jsonObject.put("Headers", nextLine[10]);
	            jsonObject.put("Critical", nextLine[11]);

	            // Check for critical failure
	            if ("1".equals(nextLine[11]) && "Fail".equalsIgnoreCase(nextLine[8])) {
	                criticalFail = true;
	            }

	            jsonArray.put(jsonObject);
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	        response.put("error",  e.getMessage() );
	        return response.toMap();
	    } 

	   

	    // Prepend Overall Result to the JSON array
	    response.put("result", criticalFail ? "Fail" : "Pass");
	    response.put("data",jsonArray);
	    

	    // Return the JSON response 
	    return response.toMap(); 
	}
}