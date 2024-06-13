package com.test.Database_Testing;

import com.jcraft.jsch.*;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

public class Image_Out_Batches {

    class QueryResult {
        int id;
        String biosample;
        String series;
        String section;
        String filename;
        boolean isQC;

        public QueryResult(int id, String biosample, String series, String section, String filename, boolean isQC) {
            this.id = id;
            this.biosample = biosample;
            this.series = series;
            this.section = section;
            this.filename = filename;
            this.isQC = isQC;
        }
    }

    @Parameters("slidebatchId")
    @Test
   public void testDB(@Optional("defaultSlidebatchId") String slidebatchId) {
        Connection connection = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("Driver loaded");

            String url = "jdbc:mysql://apollo2.humanbrain.in:3306/HBA_V2";
            String username = "root";
            String password = "Health#123";
            connection = DriverManager.getConnection(url, username, password);
            System.out.println("MYSQL database connected");

            int slidebatchIdInt = Integer.parseInt(slidebatchId);

            // Execute the query and collect results
            List<QueryResult> queryResults = executeAndCollectQueryResults(connection, slidebatchIdInt);

            // Print the query results
            printQueryResults(queryResults);

            // Execute SSH commands for each query result and check formats
            for (QueryResult result : queryResults) {
                List<String> providedFormats = executeSSHCommand(result.biosample, result.series, result.section);
                checkAndPrintMissingFormats(providedFormats);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private List<QueryResult> executeAndCollectQueryResults(Connection connection, int slidebatchId) {
        List<QueryResult> queryResults = new ArrayList<>();
        Statement statement = null;
        ResultSet resultSet = null;

        try {
            statement = connection.createStatement();
            String query = "SELECT slidebatch.id, slide.filename, huron_slideinfo.isQC FROM slidebatch "
                         + "LEFT JOIN slide ON slide.slidebatch = slidebatch.id "
                         + "LEFT JOIN huron_slideinfo ON huron_slideinfo.slide = slide.id "
                         + "WHERE slidebatch.id = " + slidebatchId;

            resultSet = statement.executeQuery(query);

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String filename = resultSet.getString("filename");
                boolean isQC = resultSet.getBoolean("isQC");

                // Extract the biosample value
                String biosample = extractValue(filename, "B_", '_', "Biosample");

                // Extract the series value
                String series = extractValue(filename, "ST_", '-', "Series");

                // Extract the section value
                String section = extractValue(filename, "SE_", '.', "Section");

                queryResults.add(new QueryResult(id, biosample, series, section, filename, isQC));
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (statement != null) statement.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return queryResults;
    }

    private String extractValue(String filename, String prefix, char delimiter, String valueType) {
        String value = "";
        try {
            int startIndex = filename.indexOf(prefix) + prefix.length();
            int endIndex = filename.indexOf(delimiter, startIndex);
            if (endIndex == -1) {
                endIndex = filename.lastIndexOf(delimiter);
            }
            value = filename.substring(startIndex, endIndex);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    private void printQueryResults(List<QueryResult> queryResults) {
        int qcTrueCount = 0;
        int qcFalseCount = 0;

        for (QueryResult result : queryResults) {
            if (result.isQC) {
                qcTrueCount++;
            } else {
                qcFalseCount++;
            }
        }
        System.out.println("Total no.of sections: " + queryResults.size());
        System.out.println("Total no.of QC passed sections: " + qcTrueCount);
        System.out.println("Total no.of QC failed sections: " + qcFalseCount);
        System.out.println("Query Result:");
        System.out.println("--------------------------------------------------------------------------------------------------------");
        System.out.printf("%-10s | %-10s | %-10s | %-10s | %-45s | %-10s%n", "ID", "Biosample", "Series", "Section", "Filename", "isQC");
        System.out.println("--------------------------------------------------------------------------------------------------------");
        for (QueryResult result : queryResults) {
            System.out.printf("%-10d | %-10s | %-10s | %-10s | %-45s | %-10b%n", result.id, result.biosample, result.series, result.section, result.filename, result.isQC);
        }
        System.out.println("--------------------------------------------------------------------------------------------------------");
    }

    private List<String> executeSSHCommand(String biosample, String series, String section) {
        List<String> providedFormats = new ArrayList<>();
        try {
            String host = "ap7v1.humanbrain.in";
            int port = 22;
            String user = "hbp";
            String password = "hbp";

            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, port);
            session.setPassword(password);

            // Avoid asking for key confirmation
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect();

            // Create the command
            String command = "cd /lustre/data/store10PB/repos1/iitlab/humanbrain/analytics/"
                            + biosample + "/" + series
                            + " && ls | grep SE_" + section;

            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);
            channelExec.setErrStream(System.err);

            InputStream in = channelExec.getInputStream();
            channelExec.connect();

            // Read the output from the command
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                providedFormats.add(line.trim()); // Assuming each line is a filename
            }

            channelExec.disconnect();
            session.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return providedFormats;
    }

    private void checkAndPrintMissingFormats(List<String> providedFormats) {
        // Expected formats
        Set<String> expectedFormats = new HashSet<>(Arrays.asList(
                "compressed.jp2", "dirInfo.txt", "downsampled.tif", "lossless.jp2",
                "macro.jpg", "thumbnail.jpg", "thumbnail_original.jpg",
                "thumbnail_small.jpg", "label.jpg"
        ));

        // Extract section numbers and group provided formats by section
        Map<String, Set<String>> sectionFormatsMap = new HashMap<>();
        for (String format : providedFormats) {
            int sectionStart = format.indexOf("SE_") + 3;
            int sectionEnd = format.indexOf('_', sectionStart);
            if (sectionEnd == -1) {
                sectionEnd = format.indexOf('-', sectionStart);
            }
            if (sectionStart != -1 && sectionEnd != -1) {
                String sectionNumber = format.substring(sectionStart, sectionEnd);
                sectionFormatsMap.putIfAbsent(sectionNumber, new HashSet<String>());
                for (String expected : expectedFormats) {
                    if (format.endsWith(expected)) {
                        sectionFormatsMap.get(sectionNumber).add(expected);
                        break;
                    }
                }
            }
        }

        // Check for missing formats in each section
        for (Map.Entry<String, Set<String>> entry : sectionFormatsMap.entrySet()) {
            String sectionNumber = entry.getKey();
            Set<String> providedSuffixes = entry.getValue();
            Set<String> missingFormats = new HashSet<>(expectedFormats);
            missingFormats.removeAll(providedSuffixes);
            System.out.println("Section " + sectionNumber + " missing formats: " + missingFormats);
        }
    }
}
