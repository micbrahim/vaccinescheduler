package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;

    private static String[] checks = {
            "Password is at least 8 characters: ",
            "Passwrod contains both uppercase and lowercase letters: ",
            "Password contains a mixture of letters and numbers: ",
            "Password includes at least one special character, from '!', '@', '#', '?': "}
            ;

    public static String[] patterns = {".{8,}", "\\b(?![a-z]+\\b|[A-Z]+\\b)[a-zA-Z]+", "[a-zA-Z][0-9]", "[!@#$?]+"};

    private static String[] checkPassword(String password, String[] checks, String[] patterns) {
        Pattern pattern;
        String response = "";
        String[] header = new String[2];
        header[0] = "t";
        for (int i = 0; i < checks.length; i++) {
            response += checks[i];
            pattern = Pattern.compile(patterns[i]);
            if (pattern.matcher(password).find()) {
                response += "Yes\n";
            } else {
                response += "No\n";
                header[0] = "x";
            }
        }
        header[1] = response.trim();
        return header;
    }

    public static void main(String[] args) {
        // printing greetings text
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>");  //TODO: implement create_patient (Part 1)
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>");  // TODO: implement login_patient (Part 1)
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>");  // TODO: implement search_caregiver_schedule (Part 2)
        System.out.println("> reserve <date> <vaccine>");  // TODO: implement reserve (Part 2)
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>");  // TODO: implement cancel (extra credit)
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments");  // TODO: implement show_appointments (Part 2)
        System.out.println("> logout");  // TODO: implement logout (Part 2)
        System.out.println("> quit");
        System.out.println();

        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            if (operation.equals("create_patient")) {
                createPatient(tokens);
            } else if (operation.equals("create_caregiver")) {
                createCaregiver(tokens);
            } else if (operation.equals("login_patient")) {
                loginPatient(tokens);
            } else if (operation.equals("login_caregiver")) {
                loginCaregiver(tokens);
            } else if (operation.equals("search_caregiver_schedule")) {
                searchCaregiverSchedule(tokens);
            } else if (operation.equals("reserve")) {
                reserve(tokens);
            } else if (operation.equals("upload_availability")) {
                uploadAvailability(tokens);
            } else if (operation.equals("cancel")) {
                cancel(tokens);
            } else if (operation.equals("add_doses")) {
                addDoses(tokens);
            } else if (operation.equals("show_appointments")) {
                showAppointments(tokens);
            } else if (operation.equals("logout")) {
                logout(tokens);
            } else if (operation.equals("quit")) {
                System.out.println("Bye!");
                return;
            } else {
                System.out.println("Invalid operation name!");
            }
        }
    }

    private static void createPatient(String[] tokens) {
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsPatient(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        //password check
        String[] passwordHead = checkPassword(password, checks, patterns);
        if (passwordHead[0].equals("x")) {
            System.out.println("Password did not meet the requirements, try again.");
            System.out.println(passwordHead[1]);
            return;
        }
        //create patient
        try {
            currentPatient = new Patient.PatientBuilder(username, salt, hash).build();
            // save to patient information to our database
            currentPatient.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsPatient(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Patients WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void createCaregiver(String[] tokens) {
        // create_caregiver <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsCaregiver(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // password check
        String[] passwordHead = checkPassword(password, checks, patterns);
        if (passwordHead[0].equals("x")) {
            System.out.println("Password did not meet the requirements, try again.");
            System.out.println(passwordHead[1]);
            return;
        }
        // create the caregiver
        try {
            currentCaregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build();
            // save to caregiver information to our database
            currentCaregiver.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsCaregiver(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Caregivers WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        // login_patient <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Patient patient = null;
        try {
            patient = new Patient.PatientGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (patient == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentPatient = patient;
        }
    }

    private static void loginCaregiver(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        try {
            caregiver = new Caregiver.CaregiverGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (caregiver == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentCaregiver = caregiver;
        }
    }

    private static void searchCaregiverSchedule(String[] tokens) {
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }

        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        try {
            String getCaregivers = "SELECT Username FROM Availabilities WHERE Time = ? ORDER BY Username";
            Date date = Date.valueOf(tokens[1]);
            PreparedStatement statement = con.prepareStatement(getCaregivers);
            statement.setDate(1, date);
            ResultSet resultSet = statement.executeQuery();
            ArrayList<String> usernames = new ArrayList<String>();
            while (resultSet.next()) {
                String username = resultSet.getString("Username");
                System.out.print(username + " ");
                usernames.add(username);
            }
            if (usernames.size() == 0) {
                System.out.println("Please try again!");
                return;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Please try again!");
        }
        try {
            String getVaccines = "SELECT * FROM Vaccines";
            PreparedStatement statement1 = con.prepareStatement(getVaccines);
            ResultSet resultSet1 = statement1.executeQuery();
            while (resultSet1.next()) {
                String vaccine = resultSet1.getString("Name");
                int doses = resultSet1.getInt("Doses");
                System.out.print(vaccine + " " + doses + " ");
            }
            System.out.println();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Please try again!");
        } finally {
            cm.closeConnection();
        }
    }

    private static void reserve(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        // checking if a caregiver is logged in instead of a patient
        if(currentCaregiver != null){
            System.out.println("Please login as a patient!");
            return;
        }
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        Date d = Date.valueOf(date);
        String vaccineName = tokens[2];
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        try {
            Vaccine vaccine = new Vaccine.VaccineGetter(vaccineName).get();
            int vaccineDoses = vaccine.getAvailableDoses();
            if(vaccineDoses == 0){
                System.out.println("Not enough available doses!");
                return;
            } else{
                vaccine.decreaseAvailableDoses(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Please try again!");
        }
        try {
            String getCareGivers = "SELECT DISTINCT Username FROM Availabilities WHERE Time = ? ORDER BY Username";
            PreparedStatement statement = con.prepareStatement(getCareGivers);
            statement.setDate(1, d);
            ResultSet resultSet = statement.executeQuery();
            ArrayList<String> usernames = new ArrayList<String>();
            while(resultSet.next()){
                String username = resultSet.getString("Username");
                usernames.add(username);
            }
            if(usernames.size() == 0){
                System.out.println("No Caregiver is available!");
                return;
            }
            String CaregiverUser = usernames.get(0);
            String addAppointment = "INSERT INTO Appointments VALUES (? , ?, ?, ?)";
            PreparedStatement statementC = con.prepareStatement(addAppointment);
            statementC.setString(1, CaregiverUser);
            statementC.setString(2, currentPatient.getUsername());
            statementC.setString(3, vaccineName);
            statementC.setDate(4, d);
            statementC.executeUpdate();
            //removing availability
            String deleteAvailability = "DELETE FROM Availabilities WHERE Time = ? AND Username = ?";
            PreparedStatement statementD = con.prepareStatement(deleteAvailability);
            statementD.setDate(1, d);
            statementD.setString(2,CaregiverUser);
            statementD.executeUpdate();
            //printing
            String retrieveAppointmentID = "SELECT ApptId FROM Appointments WHERE ApptTime = ? AND CaregiverUser = ? ";
            PreparedStatement statementR = con.prepareStatement(retrieveAppointmentID);
            statementR.setDate(1, d);
            statementR.setString(2,CaregiverUser);
            ResultSet resultSet2 = statementR.executeQuery();
            while(resultSet2.next()) {
                int ApptId = resultSet2.getInt("ApptId");
                System.out.println("Appointment ID: " + ApptId + ", Caregiver username: " + CaregiverUser);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Please try again!");
        } finally {
            cm.closeConnection();
        }
    }

    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability");
            e.printStackTrace();
        }
    }

    private static void cancel(String[] tokens) {
        // TODO: Extra credit

    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String vaccineName = tokens[1];
        int doses = Integer.parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    private static void showAppointments(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        if (tokens.length != 1) {
            System.out.println("Please try again!");
            return;
        }
        if(currentCaregiver != null){
            ConnectionManager cm = new ConnectionManager();
            Connection con = cm.createConnection();
            String getAppts = "SELECT ApptId, VaccineName, ApptTime, PatientUser FROM Appointments WHERE CaregiverUser = ? ORDER BY ApptId";
            try{
                PreparedStatement statement = con.prepareStatement(getAppts);
                statement.setString(1, currentCaregiver.getUsername());
                ResultSet resultSet = statement.executeQuery();
                while(resultSet.next()){
                    int ApptId = resultSet.getInt("ApptId");
                    String VaccineName = resultSet.getString("VaccineName");
                    Date ApptTime = resultSet.getDate("ApptTime");
                    String PatientUser = resultSet.getString("PatientUser");
                    System.out.println(ApptId + " " + VaccineName + " " + ApptTime + " " + PatientUser);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                System.out.println("Please try again!");
            } finally {
                cm.closeConnection();
            }
        }
        else if(currentPatient != null){
            ConnectionManager cm = new ConnectionManager();
            Connection con = cm.createConnection();
            String getAppts = "SELECT ApptId, VaccineName, ApptTime, CaregiverUser FROM Appointments WHERE PatientUser = ? ORDER BY ApptId";
            try {
                PreparedStatement statement = con.prepareStatement(getAppts);
                statement.setString(1, currentPatient.getUsername());
                ResultSet resultSet = statement.executeQuery();
                while(resultSet.next()){
                    int ApptId = resultSet.getInt("ApptId");
                    String VaccineName = resultSet.getString("VaccineName");
                    Date ApptTime = resultSet.getDate("ApptTime");
                    String CaregiverUser = resultSet.getString("CaregiverUser");

                    System.out.println(ApptId + " " + VaccineName + " " + ApptTime + " " + CaregiverUser);
                }

            } catch (SQLException e) {
                e.printStackTrace();
                System.out.println("Please try again!");
            } finally {
                cm.closeConnection();
            }
        }
        else{
            System.out.println("Please try again!");
        }
    }

    private static void logout(String[] tokens) {
            if (tokens.length != 1) {
                System.out.println("Please try again!");
                return;
            }
            if(currentCaregiver== null && currentPatient == null){
                System.out.println("Please login first!");
                return;
            }
            if(currentCaregiver != null){
                currentCaregiver = null;
                System.out.println("Successfully logged out!");
            } else{
                currentPatient = null;
                System.out.println("Successfully logged out!");
            }
    }
}
