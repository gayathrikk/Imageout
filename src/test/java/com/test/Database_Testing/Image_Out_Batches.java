package Automation.test;

import com.jcraft.jsch.*;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import org.testng.Assert;
import org.testng.annotations.Test;

public class Imageout_pathVerify {

    class QueryResult {
        int id;
        String filename;
        String jp2Path;
        boolean isQC;

        public QueryResult(int id, String filename, String jp2Path, boolean isQC) {
            this.id = id;
            this.filename = filename;
            this.jp2Path = jp2Path;
            this.isQC = isQC;
        }
    }

    @Test
    public void testDB() {
        String url = "jdbc:mysql://apollo2.humanbrain.in:3306/HBA_V2";
        String username = "root";
        String password = "Health#123";

        // Establish connection to the database
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            System.out.println("MYSQL database connected");

            // Get slidebatch ID from user input
            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter the slidebatch ID:");
            int slidebatchId = scanner.nextInt();
            scanner.close();

            // Execute the query and collect results
            List<QueryResult> queryResults = executeAndCollectQueryResults(connection, slidebatchId);

            // Print the query results in a table format
            printQueryResults(queryResults);

            // List to collect incorrect paths
            List<String> incorrectPaths = new ArrayList<>();

            // Check formats for each result
            for (QueryResult result : queryResults) {
                List<String> providedFormats = executeSSHCommand(result.filename);
                if (!isPathValid(result.jp2Path)) {
                    incorrectPaths.add(result.jp2Path); // Add incorrect path to the list
                }
            }

            // Print all incorrect paths
            if (!incorrectPaths.isEmpty()) {
                System.out.println("Incorrect JP2 Paths:");
                for (String path : incorrectPaths) {
                    System.out.println(path);
                }
            } else {
                System.out.println("All paths are correct.");
            }

            // Fail the test if there are incorrect paths
            Assert.assertTrue(incorrectPaths.isEmpty(), "Some JP2 paths did not match the expected format.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<QueryResult> executeAndCollectQueryResults(Connection connection, int slidebatchId) {
        List<QueryResult> queryResults = new ArrayList<>();
        String query = "SELECT slidebatch.id, slide.filename, slide.jp2Path, huron_slideinfo.isQC "
                     + "FROM slidebatch "
                     + "LEFT JOIN slide ON slide.slidebatch = slidebatch.id "
                     + "LEFT JOIN huron_slideinfo ON huron_slideinfo.slide = slide.id "
                     + "WHERE slidebatch.id = " + slidebatchId;

        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String filename = resultSet.getString("filename");
                String jp2Path = resultSet.getString("jp2Path");
                boolean isQC = resultSet.getBoolean("isQC");

                queryResults.add(new QueryResult(id, filename, jp2Path, isQC));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return queryResults;
    }

    private void printQueryResults(List<QueryResult> queryResults) {
        // Print table header
        System.out.println("Query Result:");
        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.printf("%-10s | %-45s | %-60s | %-5s%n", "ID", "Filename", "JP2 Path", "Is QC");
        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------------------------------");

        // Print table rows
        for (QueryResult result : queryResults) {
            System.out.printf("%-10d | %-45s | %-60s | %-5b%n", 
                result.id, 
                result.filename, 
                result.jp2Path, 
                result.isQC
            );
        }

        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------------------------------");
    }

    private List<String> executeSSHCommand(String filename) {
        List<String> providedFormats = new ArrayList<>();
        // Example SSH execution logic
        try {
            String host = "ap7v1.humanbrain.in";
            int port = 22;
            String user = "hbp";
            String password = "hbp";

            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, port);
            session.setPassword(password);

            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            String command = "cd /path/to/directory && ls | grep " + filename;

            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);
            channelExec.setErrStream(System.err);

            InputStream in = channelExec.getInputStream();
            channelExec.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                providedFormats.add(line.trim());
            }

            channelExec.disconnect();
            session.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return providedFormats;
    }

    private boolean isPathValid(String jp2Path) {
        // Define the expected prefix
        String expectedPrefix = "/ddn/storageIIT/humanbrain/analytics";

        // Check if jp2Path starts with the expected prefix
        return jp2Path.startsWith(expectedPrefix);
    }

    private List<String> filterSectionNumbers(List<String> sectionNumbers) {
        List<String> filteredSections = new ArrayList<>();
        for (String section : sectionNumbers) {
            if (section.startsWith("SE_")) {
                filteredSections.add(section);
            }
        }
        return filteredSections;
    }
}
