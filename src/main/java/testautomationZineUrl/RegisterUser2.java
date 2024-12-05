package testautomationZineUrl;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.testng.annotations.Test;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class RegisterUser2 {

    private static final String BASE_URL = "https://zineup-api.codesncoffee.com/api/v1";
    private static final String CSV_FILE_PATH = "ZineApiTestReportInputPostCnc2.csv";
    private static final String OUTPUT_CSV_FILE_PATH = "ZineApiTestReport_Output.csv";
	public List<String[]> updatedRecords = new ArrayList<>(); // To store all updated records
    private String xZineToken = ""; // Store token
//    private String  ContentType = "";
    
    
    @Test(priority = 1)
    public void NewUserRegister() {
        try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
            List<String[]> records = reader.readAll();

            // Define the updated header with all required columns
            String[] header = { "TestName", "ApiUrl", "Endpoint", "Method", "Payload", "ExpectedStatus", "ActualStatus",
                    "ResponseBody", "Result", "Auth", "Headers", "Critical" };
            updatedRecords.add(header); // Add header only once at the beginning
            System.out.println("Record fetch: " + records.size());

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

                // Update CSV with Auth and Headers columns
                String[] updatedRecord = new String[] { 
                    testName, 
                    BASE_URL + endpoint, 
                    endpoint, 
                    method, 
                    payload.replace("\n", " ").replace(",", ";"), 
                    String.valueOf(expectedStatus), 
                    String.valueOf(actualStatus), 
                    responseBody.replace("\n", " ").replace(",", ";"), 
                    result, 
                    auth, // Include the "Auth" value
                    headersLogged,// Include the headers sent in the request
                    critical
                };

                updatedRecords.add(updatedRecord);
                if ("1".equals(critical) && "Fail".equals(result)) {
                    System.out.println("Critical test failed. Stopping CI/CD pipeline.");
                    // Throwing an exception to stop the pipeline
                    throw new RuntimeException("Critical test '" + testName + "' failed.");
                }
            }
        } catch (IOException | CsvException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
        }
    }

	@Test(priority = 2)
	public void VerifyOtp() {
	    try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
	        List<String[]> records = reader.readAll();
	        System.out.println("Record fetch: " + records.size());

	        for (int i = 4; i < 7; i++) { // Adjust indices for VerifyOtp test cases
	            String[] record = records.get(i);

	            String testName = record[0];
	            String endpoint = record[2];
	            String method = record[3];
	            String payload = record[4];
	            int expectedStatus = Integer.parseInt(record[5]);
	            String auth = record[9]; // Fetch "Auth" value
	            String headersLogged = "Content-Type: application/json, Accept: */*"; // Default headers with Accept: */*
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

	            // Update CSV with Auth and Headers columns
	            String[] updatedRecord = new String[] { 
	                testName, 
	                BASE_URL + endpoint, 
	                endpoint, 
	                method, 
	                payload.replace("\n", " ").replace(",", ";"), 
	                String.valueOf(expectedStatus), 
	                String.valueOf(actualStatus), 
	                responseBody.replace("\n", " ").replace(",", ";"), 
	                result, 
	                auth, // Include the "Auth" value
	                headersLogged, // Include the headers sent in the request
	                critical
	            };

	            updatedRecords.add(updatedRecord);
	            if ("1".equals(critical) && "Fail".equals(result)) {
	                System.out.println("Critical test failed. Stopping CI/CD pipeline.");
	                // Throwing an exception to stop the pipeline
	                throw new RuntimeException("Critical test '" + testName + "' failed.");
	            }
	        }
	    } catch (IOException | CsvException e) {
	        System.err.println("Error reading CSV file: " + e.getMessage());
	    }
	}

	@Test(priority = 3)
	public void Login() {
	    try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE_PATH))) {
	        List<String[]> records = reader.readAll();

	        System.out.println("Record fetch: " + records.size());

	        for (int i = 7; i < 10; i++) { // Adjust indices for Login test cases
	            String[] record = records.get(i);

	            String testName = record[0];
	            String endpoint = record[2];
	            String method = record[3];
	            String payload = record[4];
	            int expectedStatus = Integer.parseInt(record[5]);
	            String auth = record[9]; // Fetch "Auth" value
	            String headersLogged = "Content-Type: application/json, Accept: */*"; // Default headers with Accept: */*
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

	            // Update CSV with Auth and Headers columns
	            String[] updatedRecord = new String[] { 
	                testName, 
	                BASE_URL + endpoint, 
	                endpoint, 
	                method, 
	                payload.replace("\n", " ").replace(",", ";"), 
	                String.valueOf(expectedStatus), 
	                String.valueOf(actualStatus), 
	                responseBody.replace("\n", " ").replace(",", ";"), 
	                result, 
	                auth, // Include the "Auth" value
	                headersLogged, // Include the headers sent in the request
	                critical
	            };

	            updatedRecords.add(updatedRecord);
	            if ("1".equals(critical) && "Fail".equals(result)) {
	                System.out.println("Critical test failed. Stopping CI/CD pipeline.");
	                // Throwing an exception to stop the pipeline
	                throw new RuntimeException("Critical test '" + testName + "' failed.");
	            }
	        }
	    } catch (IOException | CsvException e) {
	        System.err.println("Error reading CSV file: " + e.getMessage());
	    }
	}

	@Test(priority = 4)
	public void GetUser() {
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
	                        throw new RuntimeException("x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
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

	            // Update CSV with Headers field
	            String[] updatedRecord = new String[] {
	                    testName,
	                    BASE_URL + endpoint,
	                    endpoint,
	                    method,
	                    payload,
	                    String.valueOf(expectedStatus),
	                    String.valueOf(actualStatus),
	                    responseBody.replace("\n", " ").replace(",", ";"),
	                    result,
	                    auth,
	                    headersLogged, // Add headers to CSV record
	                    critical
	            };

	            updatedRecords.add(updatedRecord);
	            if ("1".equals(critical) && "Fail".equals(result)) {
	                System.out.println("Critical test failed. Stopping CI/CD pipeline.");
	                // Throwing an exception to stop the pipeline
	                throw new RuntimeException("Critical test '" + testName + "' failed.");
	            }
	        }
	    } catch (IOException | CsvException e) {
	        System.err.println("Error reading CSV file: " + e.getMessage());
	    }
	}
	
	@Test(priority = 5)
	public void UpdateUser() {
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
	                        .header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE)
	                        .body(payload);

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

	            // Update CSV with Headers field
	            String[] updatedRecord = new String[] {
	                    testName,
	                    BASE_URL + endpoint,
	                    endpoint,
	                    method,
	                    payload,
	                    String.valueOf(expectedStatus),
	                    String.valueOf(actualStatus),
	                    responseBody.replace("\n", " ").replace(",", ";"),
	                    result,
	                    auth,
	                    headersLogged, // Add headers to CSV record
	                    critical
	            };

	            updatedRecords.add(updatedRecord);
	            if ("1".equals(critical) && "Fail".equals(result)) {
	                System.out.println("Critical test failed. Stopping CI/CD pipeline.");
	                // Throwing an exception to stop the pipeline
	                throw new RuntimeException("Critical test '" + testName + "' failed.");
	            }
	        }
	    } catch (IOException | CsvException e) {
	        System.err.println("Error reading CSV file: " + e.getMessage());
	    }
	}
	
	@Test(priority = 6)
	public void getTemplate() {
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
	                        throw new RuntimeException("x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
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

	            // Update CSV with Headers field
	            String[] updatedRecord = new String[] {
	                    testName,
	                    BASE_URL + endpoint,
	                    endpoint,
	                    method,
	                    payload,
	                    String.valueOf(expectedStatus),
	                    String.valueOf(actualStatus),
	                    responseBody.replace("\n", " ").replace(",", ";"), // Clean response body for CSV
	                    result,
	                    auth,
	                    headersLogged, // Add headers to CSV record
	                    critical
	            };

	            updatedRecords.add(updatedRecord);
	            if ("1".equals(critical) && "Fail".equals(result)) {
	                System.out.println("Critical test failed. Stopping CI/CD pipeline.");
	                // Throwing an exception to stop the pipeline
	                throw new RuntimeException("Critical test '" + testName + "' failed.");
	            }
	        }
	    } catch (IOException | CsvException e) {
	        System.err.println("Error reading CSV file: " + e.getMessage());
	    }
	}

	  @Test(priority = 7)
	  public void createZine() {
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
		                        .header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE)
		                        .body(payload);

		                // Add token only if "Auth" is 1
		                if (auth.equals("1")) {
		                    if (xZineToken == null || xZineToken.isEmpty()) {
		                        throw new RuntimeException("x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
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

		            // Update CSV with Headers field
		            String[] updatedRecord = new String[] {
		                    testName,
		                    BASE_URL + endpoint,
		                    endpoint,
		                    method,
		                    payload.replace("\n", " ").replace(",", ";"),
		                    String.valueOf(expectedStatus),
		                    String.valueOf(actualStatus),
		                    responseBody.replace("\n", " ").replace(",", ";"), // Clean response body for CSV
		                    result,
		                    auth,
		                    headersLogged, // Add headers to CSV record
		                    critical
		            };

		            updatedRecords.add(updatedRecord);
		            if ("1".equals(critical) && "Fail".equals(result)) {
		                System.out.println("Critical test failed. Stopping CI/CD pipeline.");
		                // Throwing an exception to stop the pipeline
		                throw new RuntimeException("Critical test '" + testName + "' failed.");
		            }
		        }
		    } catch (IOException | CsvException e) {
		        System.err.println("Error reading CSV file: " + e.getMessage());
		    }
		}
	  
	  @Test(priority =8)
	  public void GetCompleteZine() {
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
		                        throw new RuntimeException("x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
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

		            // Update CSV with Headers field
		            String[] updatedRecord = new String[]{
		                    testName,
		                    BASE_URL + endpoint,
		                    endpoint,
		                    method,
		                    payload,
		                    String.valueOf(expectedStatus),
		                    String.valueOf(actualStatus),
		                    responseBody.replace("\n", " ").replace(",", ";"), // Clean response body for CSV
		                    result,
		                    auth,
		                    headersLogged, // Add headers to CSV record
		                    critical
		            };

		            updatedRecords.add(updatedRecord);
		            if ("1".equals(critical) && "Fail".equals(result)) {
		                System.out.println("Critical test failed. Stopping CI/CD pipeline.");
		                // Throwing an exception to stop the pipeline
		                throw new RuntimeException("Critical test '" + testName + "' failed.");
		            }
		        }
		    } catch (IOException | CsvException e) {
		        System.err.println("Error reading CSV file: " + e.getMessage());
		    }
		}
	  
	  @Test(priority=9)
	  public void GetAllSQLZine() {
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
		                        throw new RuntimeException("x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
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

		            // Update CSV with Headers field
		            String[] updatedRecord = new String[]{
		                    testName,
		                    BASE_URL + endpoint,
		                    endpoint,
		                    method,
		                    payload,
		                    String.valueOf(expectedStatus),
		                    String.valueOf(actualStatus),
		                    responseBody.replace("\n", " ").replace(",", ";"), // Clean response body for CSV
		                    result,
		                    auth,
		                    headersLogged, // Add headers to CSV record
		                    critical
		            };

		            updatedRecords.add(updatedRecord);
		            if ("1".equals(critical) && "Fail".equals(result)) {
		                System.out.println("Critical test failed. Stopping CI/CD pipeline.");
		                // Throwing an exception to stop the pipeline
		                throw new RuntimeException("Critical test '" + testName + "' failed.");
		            }
		        }
		    } catch (IOException | CsvException e) {
		        System.err.println("Error reading CSV file: " + e.getMessage());
		    }
		}
	  
	  @Test(priority=10)
	  public void GetOthersZine() {
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
		                        throw new RuntimeException("x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
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

		            // Update CSV with Headers field
		            String[] updatedRecord = new String[]{
		                    testName,
		                    BASE_URL + endpoint,
		                    endpoint,
		                    method,
		                    payload,
		                    String.valueOf(expectedStatus),
		                    String.valueOf(actualStatus),
		                    responseBody.replace("\n", " ").replace(",", ";"), // Clean response body for CSV
		                    result,
		                    auth,
		                    headersLogged, // Add headers to CSV record
		                    critical
		            };

		            updatedRecords.add(updatedRecord);
		            if ("1".equals(critical) && "Fail".equals(result)) {
		                System.out.println("Critical test failed. Stopping CI/CD pipeline.");
		                // Throwing an exception to stop the pipeline
		                throw new RuntimeException("Critical test '" + testName + "' failed.");
		            }
		        }
		    } catch (IOException | CsvException e) {
		        System.err.println("Error reading CSV file: " + e.getMessage());
		    }
		}
	  
	  @Test(priority=11)
	  public void FetchOwnZine() {
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
		                        throw new RuntimeException("x-zine-token is missing. Ensure VerifyOtp test ran successfully.");
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

		            // Update CSV with Headers field
		            String[] updatedRecord = new String[]{
		                    testName,
		                    BASE_URL + endpoint,
		                    endpoint,
		                    method,
		                    payload,
		                    String.valueOf(expectedStatus),
		                    String.valueOf(actualStatus),
		                    responseBody.replace("\n", " ").replace(",", ";"), // Clean response body for CSV
		                    result,
		                    auth,
		                    headersLogged ,// Add headers to CSV record
		                    critical
		            };

		            updatedRecords.add(updatedRecord);
		            if ("1".equals(critical) && "Fail".equals(result)) {
		                System.out.println("Critical test failed. Stopping CI/CD pipeline.");
		                // Throwing an exception to stop the pipeline
		                throw new RuntimeException("Critical test '" + testName + "' failed.");
		            }
		        }
		    } catch (IOException | CsvException e) {
		        System.err.println("Error reading CSV file: " + e.getMessage());
		    }
		}
	  
	
//	@Test(priority = 12)
//	public void printAllRecords() {
//		System.out.println("----- Final Test Records -----");
//		for (String[] record : updatedRecords) {
//			System.out.println(String.join(" | ", record));
//		}
//		// Write updated records back to the CSV
//		  try (CSVWriter writer = new CSVWriter(new FileWriter(OUTPUT_CSV_FILE_PATH))) {
//	            writer.writeAll(updatedRecords);
//	        } catch (IOException e) {
//	            System.err.println("Error writing to CSV file: " + e.getMessage());
//	        }
//	}
	
	  @Test(priority = 12)
	    public void printAllRecords() {
	        // Print to console
	        System.out.println("----- Final Test Records -----");
	        for (String[] record : updatedRecords) {
	            System.out.println(String.join(" | ", record));
	        }

	        // Write the updated records to the CSV file
	        try (CSVWriter writer = new CSVWriter(new FileWriter(OUTPUT_CSV_FILE_PATH))) {
	            writer.writeAll(updatedRecords);
	        } catch (IOException e) {
	            System.err.println("Error writing to CSV file: " + e.getMessage());
	        }
	    }

	    @GetMapping("/testRecords")
	    public String displayTestRecords(org.springframework.ui.Model model) {
	        // Adding updated records to the model so they can be displayed in the browser
	        model.addAttribute("records", updatedRecords);

	        // Returning the view name where records will be displayed (e.g., 'testRecords.html' for Thymeleaf)
	        return "testRecords"; // Update this with your actual view template name (Thymeleaf or another template engine)
	    }

	    @GetMapping("/testRecords/json")
	    @ResponseBody
	    public List<String[]> getTestRecordsAsJson() {
	        // Return the updated records as JSON response
	        return updatedRecords;
	    }
	
	

}
