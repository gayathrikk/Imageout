package com.test.Database_Testing;

import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class demo {

    @Test
    public void testDB() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("Driver loaded");

            String url = "jdbc:mysql://apollo2.humanbrain.in:3306/HBA_V2";
            String username = "root";
            String password = "Health#123";
            Connection connection = DriverManager.getConnection(url, username, password);
            System.out.println("MYSQL database connected");

            executeAndPrintQuery(connection);
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeAndPrintQuery(Connection connection) {
        try {
            Statement statement = connection.createStatement();
            String query = "SELECT id, name, datalocation, arrival_date, totalImages " +
                           "FROM `slidebatch` " +
                           "WHERE (process_status = 6 OR process_status = 11) " +
                           "AND `arrival_date` < DATE_SUB(CURDATE(), INTERVAL 1 DAY);";

            ResultSet resultSet = statement.executeQuery(query);

            int IdWidth = 10;
            int nameWidth = 40;
            int datalocationWidth = 30;
            int arrival_dateWidth = 20;
            int totalImagesWidth = 20;
            int daysWidth = 15; // Width for the new column

            // Building email content
            StringBuilder emailContent = new StringBuilder();
            emailContent.append("<html><body><pre>");
            emailContent.append("<b>This is an automatically generated email,</b>\n\n");
            emailContent.append("For your attention and action:\n");
            emailContent.append("The following batches have QC pending for more than 1 day:\n\n");
            emailContent.append(String.format("%-" + IdWidth + "s %-"+ nameWidth + "s %-"+ datalocationWidth + "s %-"+ arrival_dateWidth + "s %-" + totalImagesWidth + "s %-" + daysWidth + "s%n",
                    "Id", "name", "datalocation", "arrival_date", "totalImages", "No.of days"));

            // Adding separator line
            String separatorLine = "-".repeat(IdWidth + nameWidth + datalocationWidth + arrival_dateWidth + totalImagesWidth + daysWidth);
            emailContent.append(separatorLine).append("\n");

            boolean dataFound = false;

            while (resultSet.next()) {
                dataFound = true;
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                String datalocation = resultSet.getString("datalocation");
                String arrivalDateStr = resultSet.getString("arrival_date"); // Assuming arrival_date is stored as a String
                int totalImages = resultSet.getInt("totalImages");

                LocalDate arrivalDate = LocalDate.parse(arrivalDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                long daysDifference = ChronoUnit.DAYS.between(arrivalDate, LocalDate.now());

                emailContent.append(String.format("%-" + IdWidth + "d %-" + nameWidth + "s %-" + datalocationWidth + "s %-" + arrival_dateWidth + "s %-" + totalImagesWidth + "d %-" + daysWidth + "d%n",
                        id, name, datalocation, arrivalDateStr, totalImages, daysDifference));
            }

            

            // Close the statement
            resultSet.close();
            statement.close();

          
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
