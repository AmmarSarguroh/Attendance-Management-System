import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.*;
import java.time.LocalDate;
import java.time.YearMonth;
import org.json.*;

public class AttendanceServer {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/new_attendance_system";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "password";
    
    public static void main(String[] args) throws Exception {
        // Initialize database
        initDatabase();
        
        // Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        // Define endpoints
        server.createContext("/attendance/student", new AddStudentHandler());
        server.createContext("/attendance/students", new GetStudentsHandler());
        server.createContext("/attendance/student/delete", new DeleteStudentHandler());
        server.createContext("/attendance/mark", new MarkAttendanceHandler()); // Kept for single student marking if needed
        server.createContext("/attendance/markClassAttendance", new MarkClassAttendanceHandler()); // New for batch marking
        server.createContext("/attendance/records", new GetAttendanceHandler());
        server.createContext("/attendance/record/delete", new DeleteAttendanceHandler());
        server.createContext("/attendance/studentAttendanceById", new GetStudentAttendanceByIdHandler()); // New for individual student report
        server.createContext("/attendance/attendanceByClassAndDate", new GetAttendanceByClassAndDateHandler()); // New for marking screen
        server.createContext("/attendance/monthlyClassAttendance", new GetMonthlyClassAttendanceHandler()); // New for class report
        
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port 8080");
    }
    
    private static void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            Statement stmt = conn.createStatement();
            
            // Create students table - Added class_grade
            stmt.execute("CREATE TABLE IF NOT EXISTS students (" +
                "id INT PRIMARY KEY AUTO_INCREMENT," +
                "name VARCHAR(100) NOT NULL," +
                "roll_number VARCHAR(50) NOT NULL," + // Removed UNIQUE constraint for roll_number if it can be same across classes
                "class_grade INT NOT NULL," + // New field for class
                "UNIQUE KEY unique_student_per_class (roll_number, class_grade))"); // Roll number unique per class

            // Create attendance table
            stmt.execute("CREATE TABLE IF NOT EXISTS attendance (" +
                "id INT PRIMARY KEY AUTO_INCREMENT," +
                "student_id INT NOT NULL," +
                "date DATE NOT NULL," +
                "status VARCHAR(20) NOT NULL," +
                "FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE," +
                "UNIQUE KEY unique_attendance (student_id, date))");
            
            System.out.println("Database initialized successfully");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
    
    private static void enableCORS(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }
    
    static class AddStudentHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    JSONObject json = new JSONObject(body);
                    
                    String name = json.getString("name");
                    String rollNumber = json.getString("rollNumber");
                    int classGrade = json.getInt("classGrade");
                    
                    try (Connection conn = getConnection()) {
                        String sql = "INSERT INTO students (name, roll_number, class_grade) VALUES (?, ?, ?)";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setString(1, name);
                        pstmt.setString(2, rollNumber);
                        pstmt.setInt(3, classGrade);
                        pstmt.executeUpdate();
                        
                        String response = "{\"success\": true}";
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                } catch (SQLException e) {
                    System.err.println("SQL Error adding student: " + e.getMessage());
                    String response = "{\"error\": \"Database error: " + e.getMessage() + "\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    String response = "{\"error\": \"" + e.getMessage() + "\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            }
        }
    }
    
    static class GetStudentsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            try (Connection conn = getConnection()) {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String classGradeFilter = params.get("classGrade");

                String sql = "SELECT * FROM students";
                if (classGradeFilter != null && !classGradeFilter.isEmpty()) {
                    sql += " WHERE class_grade = ?";
                }
                sql += " ORDER BY class_grade, name";

                PreparedStatement pstmt = conn.prepareStatement(sql);
                if (classGradeFilter != null && !classGradeFilter.isEmpty()) {
                    pstmt.setInt(1, Integer.parseInt(classGradeFilter));
                }
                ResultSet rs = pstmt.executeQuery();
                
                JSONArray students = new JSONArray();
                while (rs.next()) {
                    JSONObject student = new JSONObject();
                    student.put("id", rs.getInt("id"));
                    student.put("name", rs.getString("name"));
                    student.put("rollNumber", rs.getString("roll_number"));
                    student.put("classGrade", rs.getInt("class_grade"));
                    students.put(student);
                }
                
                String response = students.toString();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                String response = "[]";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }
    
    static class DeleteStudentHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    JSONObject json = new JSONObject(body);
                    
                    int studentId = json.getInt("id");
                    
                    try (Connection conn = getConnection()) {
                        String sql = "DELETE FROM students WHERE id = ?";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setInt(1, studentId);
                        int rows = pstmt.executeUpdate();
                        
                        String response = "{\"success\": " + (rows > 0) + "}";
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String response = "{\"error\": \"" + e.getMessage() + "\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            }
        }
    }
    
    static class MarkAttendanceHandler implements HttpHandler { // Kept for backward compatibility or individual marking
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    JSONObject json = new JSONObject(body);
                    
                    int studentId = json.getInt("studentId");
                    String date = json.getString("date");
                    String status = json.getString("status");
                    
                    try (Connection conn = getConnection()) {
                        String sql = "INSERT INTO attendance (student_id, date, status) " +
                                   "VALUES (?, ?, ?) " +
                                   "ON DUPLICATE KEY UPDATE status = ?";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setInt(1, studentId);
                        pstmt.setString(2, date);
                        pstmt.setString(3, status);
                        pstmt.setString(4, status);
                        pstmt.executeUpdate();
                        
                        String response = "{\"success\": true}";
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String response = "{\"error\": \"" + e.getMessage() + "\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            }
        }
    }

    static class MarkClassAttendanceHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    JSONArray jsonArray = new JSONArray(body);
                    
                    try (Connection conn = getConnection()) {
                        conn.setAutoCommit(false); // Start transaction
                        String sql = "INSERT INTO attendance (student_id, date, status) " +
                                   "VALUES (?, ?, ?) " +
                                   "ON DUPLICATE KEY UPDATE status = ?";
                        PreparedStatement pstmt = conn.prepareStatement(sql);

                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject json = jsonArray.getJSONObject(i);
                            int studentId = json.getInt("studentId");
                            String date = json.getString("date");
                            String status = json.getString("status");

                            pstmt.setInt(1, studentId);
                            pstmt.setString(2, date);
                            pstmt.setString(3, status);
                            pstmt.setString(4, status);
                            pstmt.addBatch();
                        }
                        
                        pstmt.executeBatch();
                        conn.commit(); // Commit transaction
                        
                        String response = "{\"success\": true}";
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    try (Connection conn = getConnection()) { // Get a fresh connection to rollback if autoCommit was true
                        conn.rollback(); // Rollback transaction on error
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                    String response = "{\"error\": \"" + e.getMessage() + "\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            }
        }
    }
    
    static class GetAttendanceHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            try (Connection conn = getConnection()) {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String classGradeFilter = params.get("classGrade");

                String sql = "SELECT a.id, s.name, s.roll_number, s.class_grade, a.date, a.status " +
                           "FROM attendance a " +
                           "JOIN students s ON a.student_id = s.id";
                
                if (classGradeFilter != null && !classGradeFilter.isEmpty()) {
                    sql += " WHERE s.class_grade = ?";
                }
                sql += " ORDER BY a.date DESC, s.class_grade, s.name";

                PreparedStatement pstmt = conn.prepareStatement(sql);
                if (classGradeFilter != null && !classGradeFilter.isEmpty()) {
                    pstmt.setInt(1, Integer.parseInt(classGradeFilter));
                }
                ResultSet rs = pstmt.executeQuery();
                
                JSONArray records = new JSONArray();
                while (rs.next()) {
                    JSONObject record = new JSONObject();
                    record.put("id", rs.getInt("id"));
                    record.put("studentName", rs.getString("name"));
                    record.put("rollNumber", rs.getString("roll_number"));
                    record.put("classGrade", rs.getInt("class_grade"));
                    record.put("date", rs.getString("date"));
                    record.put("status", rs.getString("status"));
                    records.put(record);
                }
                
                String response = records.toString();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                String response = "[]";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }
    
    static class DeleteAttendanceHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    JSONObject json = new JSONObject(body);
                    
                    int attendanceId = json.getInt("id");
                    
                    try (Connection conn = getConnection()) {
                        String sql = "DELETE FROM attendance WHERE id = ?";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setInt(1, attendanceId);
                        int rows = pstmt.executeUpdate();
                        
                        String response = "{\"success\": " + (rows > 0) + "}";
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String response = "{\"error\": \"" + e.getMessage() + "\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            }
        }
    }

    // New handler: Get attendance for a specific student by ID
    static class GetStudentAttendanceByIdHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try (Connection conn = getConnection()) {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String studentIdStr = params.get("studentId");

                if (studentIdStr == null || studentIdStr.isEmpty()) {
                    String response = "{\"error\": \"studentId parameter is required\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(400, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                }
                int studentId = Integer.parseInt(studentIdStr);

                String sql = "SELECT date, status FROM attendance WHERE student_id = ? ORDER BY date DESC";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setInt(1, studentId);
                ResultSet rs = pstmt.executeQuery();

                JSONArray records = new JSONArray();
                while (rs.next()) {
                    JSONObject record = new JSONObject();
                    record.put("date", rs.getString("date"));
                    record.put("status", rs.getString("status"));
                    records.put(record);
                }

                String response = records.toString();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                String response = "{\"error\": \"" + e.getMessage() + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    // New handler: Get attendance records for a specific class and date (for marking)
    static class GetAttendanceByClassAndDateHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try (Connection conn = getConnection()) {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String classGradeStr = params.get("classGrade");
                String dateStr = params.get("date");

                if (classGradeStr == null || classGradeStr.isEmpty() || dateStr == null || dateStr.isEmpty()) {
                    String response = "{\"error\": \"classGrade and date parameters are required\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(400, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                }
                int classGrade = Integer.parseInt(classGradeStr);
                
                String sql = "SELECT s.id AS studentId, a.status " +
                             "FROM students s " +
                             "LEFT JOIN attendance a ON s.id = a.student_id AND a.date = ? " +
                             "WHERE s.class_grade = ?";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, dateStr);
                pstmt.setInt(2, classGrade);
                ResultSet rs = pstmt.executeQuery();

                JSONArray records = new JSONArray();
                while (rs.next()) {
                    JSONObject record = new JSONObject();
                    record.put("studentId", rs.getInt("studentId"));
                    record.put("status", rs.getString("status") != null ? rs.getString("status") : "Unknown");
                    records.put(record);
                }

                String response = records.toString();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                String response = "{\"error\": \"" + e.getMessage() + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }


    // New handler: Get monthly class attendance summary
    static class GetMonthlyClassAttendanceHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try (Connection conn = getConnection()) {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String classGradeStr = params.get("classGrade");
                String monthStr = params.get("month"); // Format YYYY-MM

                if (classGradeStr == null || classGradeStr.isEmpty() || monthStr == null || monthStr.isEmpty()) {
                    String response = "{\"error\": \"classGrade and month parameters are required\"}";
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(400, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                }
                int classGrade = Integer.parseInt(classGradeStr);
                
                // Calculate start and end date for the month
                YearMonth yearMonth = YearMonth.parse(monthStr);
                LocalDate startDate = yearMonth.atDay(1);
                LocalDate endDate = yearMonth.atEndOfMonth();

                String sql = "SELECT s.id AS studentId, s.name, s.roll_number, " +
                             "SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) AS presentDays, " +
                             "SUM(CASE WHEN a.status = 'Absent' THEN 1 ELSE 0 END) AS absentDays " +
                             "FROM students s " +
                             "LEFT JOIN attendance a ON s.id = a.student_id AND a.date BETWEEN ? AND ? " +
                             "WHERE s.class_grade = ? " +
                             "GROUP BY s.id, s.name, s.roll_number " +
                             "ORDER BY s.name";
                
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, startDate.toString());
                pstmt.setString(2, endDate.toString());
                pstmt.setInt(3, classGrade);
                ResultSet rs = pstmt.executeQuery();

                JSONArray monthlySummary = new JSONArray();
                while (rs.next()) {
                    JSONObject studentSummary = new JSONObject();
                    studentSummary.put("studentId", rs.getInt("studentId"));
                    studentSummary.put("studentName", rs.getString("name"));
                    studentSummary.put("rollNumber", rs.getString("roll_number"));
                    studentSummary.put("presentDays", rs.getInt("presentDays"));
                    studentSummary.put("absentDays", rs.getInt("absentDays"));
                    monthlySummary.put(studentSummary);
                }

                String response = monthlySummary.toString();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                String response = "{\"error\": \"" + e.getMessage() + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }


    // Helper to parse query parameters
    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}