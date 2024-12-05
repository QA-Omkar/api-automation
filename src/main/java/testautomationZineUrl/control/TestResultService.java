package testautomationZineUrl.control;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestResultService {
    private static List<String[]> testRecords;

    // Method to set the test records
    public static void setTestRecords(List<String[]> records) {
        testRecords = records;
    }

    // Method to get the test records
    public static List<String[]> getTestRecords() {
        return testRecords;
    }
}
