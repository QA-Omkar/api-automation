package testautomationZineUrl.control;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

	@GetMapping("/run-tests")
	public String runTests() throws CsvValidationException, JSONException {

		// Initialize TestNG and add the listener to capture output
		TestNG testng = new TestNG();
		CaptureOutput captureOutput = new CaptureOutput();
		testng.addListener(captureOutput);

		// Specify the test classes to run
		testng.setTestClasses(new Class[] { TestController.class }); // Adjust according to your setup
		testng.run(); // Run the tests

		// Now return the content of the updated output.csv after test execution
		return readCsvFileAsJson();
	}

	

	@GetMapping("/status") // Full URL: http://localhost:8080/api/status
	public String getStatus() {
		return "Service is running";

	}

	

	private static final String BASE_URL = "https://zineup-api.codesncoffee.com/api/v1";
	private static final String CSV_FILE_PATH = "ZineApiTestReportInputPostCnc2.csv";
	private static final String OUTPUT_CSV_FILE_PATH = "ZineApiTestReport_Output.csv";
    private static final String OUTPUTFOLDER_PATH_STRING = "ReportOutput"; // Path to OutputFiles folder
//    private static final String OUTPUTFOLDER_PATH_STRING = System.getProperty("java.io.tmpdir") + "ReportOutput"; // Saves to system temp folder

	private final List<String[]> updatedRecords = new ArrayList<>(); // To store all updated records
	private String xZineToken = ""; // Store token
	public final Map<String, String> testResults = new HashMap<>();

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

//        // Define the updated header with all required columns
//        String[] header = { "TestName", "ApiUrl", "Endpoint", "Method", "Payload", "ExpectedStatus", "ActualStatus",
//                "ResponseBody", "Result", "Auth", "Headers", "Critical" };
//        updatedRecords.add(header); // Add header only once at the beginning
//        System.out.println("Record fetch: " + records.size());

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

//@Test(priority = 2)
//public void VerifyOtp() {
//    String testname = "VerifyOtp";
//
//	
//    try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
//        List<String[]> records = reader.readAll();
//        System.out.println("Record fetch: " + records.size());
//
//        for (int i = 4; i < 7; i++) { // Adjust indices for VerifyOtp test cases
//            String[] record = records.get(i);
//
//            String testName = record[0];
//            String endpoint = record[2];
//            String method = record[3];
//            String payload = record[4];
//            int expectedStatus = Integer.parseInt(record[5]);
//            String auth = record[9]; // Fetch "Auth" value
//            String headersLogged = "Content-Type: application/json, Accept: */*"; // Default headers with Accept: */*
//            String critical = record[11];
//            
//
//            String responseBody = "";
//            int actualStatus = 0;
//            String result = "Fail";
//
//            try {
//                // Create the request
//                RequestSpecification request = RestAssured.given()
//                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
//                        .header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE) // Add Accept header
//                        .body(payload);
//
//                // Add token if "Auth" is "1"
//                if (auth.equals("1")) {
//                    if (xZineToken == null || xZineToken.isEmpty()) {
//                        throw new RuntimeException(
//                                "x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
//                    }
//                    request.header("x-zine-token", xZineToken);
//                    headersLogged += ", x-zine-token: " + xZineToken; // Log token if included
//                }
//
//                // Send the POST request
//                Response response = request.when().post(BASE_URL + endpoint);
//
//                // Extract response details
//                responseBody = response.getBody().asString();
//                actualStatus = response.getStatusCode();
//                result = actualStatus == expectedStatus ? "Pass" : "Fail";
//
//                // Extract x-zine-token if conditions are met
//                if (endpoint.equals("/user/verify-otp") && expectedStatus == 200 && actualStatus == 200) {
//                    JsonPath jsonPath = new JsonPath(responseBody);
//                    xZineToken = jsonPath.getString("data.x-zine-token");
//                    System.out.println("Extracted x-zine-token: " + xZineToken);
//                }
//
//            } catch (Exception e) {
//                responseBody = "Error: " + e.getMessage();
//                System.err.println("Error processing test case '" + testName + "': " + e.getMessage());
//            }
//
//            // Log test details including headers
//            System.out.println("Test: " + testName);
//            System.out.println("Endpoint: " + endpoint);
//            System.out.println("Method: " + method);
//            System.out.println("Payload: " + payload);
//            System.out.println("Expected Status: " + expectedStatus);
//            System.out.println("Actual Status: " + actualStatus);
//            System.out.println("Response: " + responseBody);
//            System.out.println("Result: " + result);
//            System.out.println("Auth: " + auth);
//            System.out.println("Headers: " + headersLogged);
//            System.out.println("Critical: " + critical);
//
//            System.out.println("----------------------------------------------------------");
//            testResults.put(testName, "Result: " + result + " - " + responseBody); // Store the result
//
//            // Update CSV with Auth and Headers columns
//            String[] updatedRecord = new String[] { 
//                testName, 
//                BASE_URL + endpoint, 
//                endpoint, 
//                method, 
//                payload.replace("\n", " ").replace(",", ";"), 
//                String.valueOf(expectedStatus), 
//                String.valueOf(actualStatus), 
//                responseBody.replace("\n", " ").replace(",", ";"), 
//                result, 
//                auth, // Include the "Auth" value
//                headersLogged, // Include the headers sent in the request
//                critical
//            };
//
//            updatedRecords.add(updatedRecord);
//            if ("1".equals(critical) && "Fail".equals(result)) {
//                System.out.println("Critical test failed. Stopping CI/CD pipeline.");
//                // Throwing an exception to stop the pipeline
//                throw new RuntimeException("Critical test '" + testName + "' failed.");
//            }
//        }
//    } catch (IOException | CsvException e) {
//        System.err.println("Error reading CSV file: " + e.getMessage());
//        testResults.put(testname, "Error: " + e.getMessage());
//    }
//}

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
		// Replacing newlines and commas in responseBody and payload to prevent CSV
		// formatting issues
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
  }

  
  
  
//Method to read the CSV file and return the content as JSON
	private String readCsvFileAsJson() throws CsvValidationException, JSONException {
		JSONArray jsonArray = new JSONArray();

		try (CSVReader csvReader = new CSVReader(new FileReader(OUTPUT_CSV_FILE_PATH))) {
			String[] nextLine;
			while ((nextLine = csvReader.readNext()) != null) {
				JSONObject jsonObject = new JSONObject();

				// Map CSV data to JSON fields
				jsonObject.put("testName", nextLine[0]);
				jsonObject.put("endpoint", nextLine[1]);
				jsonObject.put("method", nextLine[2]);
				jsonObject.put("payload", nextLine[3]);
				jsonObject.put("expectedStatus", nextLine[4]);
				jsonObject.put("actualStatus", nextLine[5]);
				jsonObject.put("responseBody", nextLine[6]);
				jsonObject.put("resultStatus", nextLine[7]);
				jsonObject.put("auth", nextLine[8]);
				jsonObject.put("headersLogged", nextLine[9]);
				jsonObject.put("critical", nextLine[10]);

				jsonArray.put(jsonObject);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return "{\"error\":\"Error reading CSV file: " + e.getMessage() + "\"}";
		}

		// Return the JSON response as a string
		return jsonArray.toString();
	}
  
  
  
  

//  @GetMapping("/testRecords")
//  public String displayTestRecords(org.springframework.ui.Model model) {
//      // Adding updated records to the model so they can be displayed in the browser
//      model.addAttribute("records", updatedRecords);
//
//      // Returning the view name where records will be displayed (e.g., 'testRecords.html' for Thymeleaf)
//      return "testRecords"; // Update this with your actual view template name (Thymeleaf or another template engine)
//  }
//
//  @GetMapping("/testRecords/json")
//  @ResponseBody
//  public List<String[]> getTestRecordsAsJson() {
//      // Return the updated records as JSON response
//      return updatedRecords;
//  }
//
//
//}

//  @Test(priority = 12)
//  public void printAllRecords() {
//
//      StringBuilder consoleOutput = new StringBuilder();
//      
//      // Print to console and store the result in consoleOutput
//      consoleOutput.append("----- Final Test Records -----\n");
//      for (String[] record : updatedRecords) {
//          String recordString = String.join(" | ", record);
//          consoleOutput.append(recordString).append("\n");
//      }
//
//      // Write the updated records to the CSV file
//      try (CSVWriter writer = new CSVWriter(new FileWriter(OUTPUT_CSV_FILE_PATH))) {
//          writer.writeAll(updatedRecords);
//      } catch (IOException e) {
//          System.err.println("Error writing to CSV file: " + e.getMessage());
//      }

//      // Store the console output in a static variable or a service to be accessed in the controller
//      TestResultService.setConsoleOutput(consoleOutput.toString());
//  }
//  @Test(priority = 12)
//  public void printAllRecords() {
//
//      StringBuilder consoleOutput = new StringBuilder();
//
//      // Print to console and store the result in consoleOutput
//      consoleOutput.append("----- Final Test Records -----\n");
//
//      // Assuming updatedRecords is a list of string arrays
//      for (String[] record : updatedRecords) {
//          // Assuming record array format is: [testName, endpoint, method, payload, expectedStatus, actualStatus, responseBody, result, auth, headers, critical]
//
//          // Create a new TestResult object for each record
//    	  TestResult testResult = new TestResult(); 
//          testResult.setTestName(record[0]);
//          testResult.setEndpoint(record[1]);
//          testResult.setMethod(record[2]);
//          testResult.setRequestPayload(record[3]);
//          testResult.setExpectedStatusCode(Integer.parseInt(record[4]));
//          testResult.setActualStatusCode(Integer.parseInt(record[5]));
//          testResult.setResponseBody(record[6]);
//          testResult.setTestPassed(record[7].equals("Success"));
//          testResult.setAuth(record[8]);
//          testResult.setHeaders(record[9]);
//          testResult.setResultMessage(record[6]); // Assuming response body can be a result message, adjust as needed
//          testResult.setTestCategory("Functional"); // Set your test category accordingly
//          testResult.setResponseTime(200); // You may need to capture actual response time, update accordingly
//
//          // Add the result to the TestResultService
//          testResultService.addTestResult(testResult); // Call the service method to store the record
//
//          // Append each record to console output for logging
//          String recordString = String.join(" | ", record);
//          consoleOutput.append(recordString).append("\n");
//      }
//
//      // Write the updated records to the CSV file
//      try (CSVWriter writer = new CSVWriter(new FileWriter(OUTPUT_CSV_FILE_PATH))) {
//          writer.writeAll(updatedRecords); // Write updatedRecords to CSV
//      } catch (IOException e) {
//          System.err.println("Error writing to CSV file: " + e.getMessage());
//      }
//
//      // Store the console output in the service (to be accessed by controller)
//      testResultService.setConsoleOutput(consoleOutput.toString()); // Set console output for later access by the controller
//  }
//  
}
