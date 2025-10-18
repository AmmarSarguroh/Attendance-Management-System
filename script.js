const API_URL = 'http://localhost:8080/attendance';

// Helper function to show messages
function showMessage(elementId, message, type) {
    const el = document.getElementById(elementId);
    if (el) {
        el.textContent = message;
        el.className = `message ${type}`;
        el.style.display = 'block';
        setTimeout(() => el.style.display = 'none', 3000);
    }
}

// Student Management Functions (students.html)

async function addStudent() {
    const name = document.getElementById('studentName').value;
    const rollNumber = document.getElementById('rollNumber').value;
    const classGrade = document.getElementById('studentClass').value;

    if (!name || !rollNumber || !classGrade) {
        showMessage('studentMessage', 'Please fill all fields', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_URL}/student`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({name, rollNumber, classGrade})
        });

        if (response.ok) {
            showMessage('studentMessage', 'Student added successfully!', 'success');
            document.getElementById('studentName').value = '';
            document.getElementById('rollNumber').value = '';
            document.getElementById('studentClass').value = ''; // Reset class selection
            loadStudents();
            loadStudentSelects(); // Refresh student dropdowns on other pages
        } else {
            const errorData = await response.json();
            showMessage('studentMessage', `Error adding student: ${errorData.error || response.statusText}`, 'error');
        }
    } catch (error) {
        showMessage('studentMessage', 'Server error: ' + error.message, 'error');
    }
}

async function deleteStudent(id, name) {
    if (!confirm(`Are you sure you want to delete student "${name}"? This will also delete all their attendance records.`)) {
        return;
    }

    try {
        const response = await fetch(`${API_URL}/student/delete`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({id})
        });

        if (response.ok) {
            loadStudents();
            loadStudentSelects(); // Refresh student dropdowns on other pages
            loadAttendance(); // Refresh attendance records if on attendance page
        } else {
            const errorData = await response.json();
            alert(`Error deleting student: ${errorData.error || response.statusText}`);
        }
    } catch (error) {
        alert('Server error: ' + error.message);
    }
}

async function loadStudents() {
    const filterClass = document.getElementById('filterClass') ? document.getElementById('filterClass').value : '';
    let url = `${API_URL}/students`;
    if (filterClass) {
        url += `?classGrade=${filterClass}`;
    }

    try {
        const response = await fetch(url);
        const students = await response.json();
        
        const tbody = document.querySelector('#studentsTable tbody');
        if (tbody) {
            tbody.innerHTML = students.map(s => `
                <tr>
                    <td>${s.id}</td>
                    <td>${s.name}</td>
                    <td>${s.rollNumber}</td>
                    <td>Class ${s.classGrade}</td>
                    <td>
                        <button class="delete-btn" onclick="deleteStudent(${s.id}, '${s.name}')">Delete</button>
                    </td>
                </tr>
            `).join('');
        }
    } catch (error) {
        console.error('Error loading students:', error);
    }
}

// Common function to load student select dropdowns across pages
async function loadStudentSelects() {
    try {
        const response = await fetch(`${API_URL}/students`);
        const students = await response.json();
        
        // For individual attendance marking (if we were to have it, not used currently for daily mark)
        // const select = document.getElementById('studentSelect'); 
        // if (select) {
        //     select.innerHTML = '<option value="">-- Select Student --</option>' +
        //         students.map(s => `<option value="${s.id}">${s.name} (Class ${s.classGrade})</option>`).join('');
        // }

        // For student report page
        const reportStudentSelect = document.getElementById('reportStudentSelect');
        if (reportStudentSelect) {
            reportStudentSelect.innerHTML = '<option value="">-- Select Student --</option>' +
                students.map(s => `<option value="${s.id}" data-name="${s.name}" data-roll="${s.rollNumber}" data-class="${s.classGrade}">${s.name} (Class ${s.classGrade} - Roll ${s.rollNumber})</option>`).join('');
        }

    } catch (error) {
        console.error('Error loading student select dropdowns:', error);
    }
}

// Call this on script load or DOMContentLoaded for pages needing student dropdowns
// If the current page is not students.html, it will still try to populate these.
// For now, load on DOMContentLoaded in student_report.html
function loadStudentsForReportSelect() {
    loadStudentSelects();
}


// Attendance Marking Functions (attendance.html)

async function loadStudentsForAttendance() {
    const classGrade = document.getElementById('attendanceClass').value;
    const attendanceDate = document.getElementById('attendanceDate').value;
    const studentAttendanceListDiv = document.getElementById('studentAttendanceList');
    studentAttendanceListDiv.innerHTML = ''; // Clear previous list

    if (!classGrade) {
        studentAttendanceListDiv.innerHTML = '<p>Please select a class to mark attendance.</p>';
        return;
    }
    if (!attendanceDate) {
        studentAttendanceListDiv.innerHTML = '<p>Please select a date.</p>';
        return;
    }

    try {
        const studentsResponse = await fetch(`${API_URL}/students?classGrade=${classGrade}`);
        const students = await studentsResponse.json();

        if (students.length === 0) {
            studentAttendanceListDiv.innerHTML = `<p>No students found for Class ${classGrade}.</p>`;
            return;
        }

        const attendanceResponse = await fetch(`${API_URL}/attendanceByClassAndDate?classGrade=${classGrade}&date=${attendanceDate}`);
        const existingAttendance = await attendanceResponse.json();
        const existingAttendanceMap = new Map(existingAttendance.map(a => [a.studentId, a.status]));

        let html = `<h3>Students in Class ${classGrade} for ${attendanceDate}</h3>`;
        students.forEach(s => {
            const currentStatus = existingAttendanceMap.get(s.id) || 'Unknown';
            html += `
                <div class="student-attendance-item">
                    <span>${s.name} (Roll: ${s.rollNumber})</span>
                    <div class="attendance-status-group">
                        <label>
                            <input type="radio" name="status_${s.id}" value="Present" ${currentStatus === 'Present' ? 'checked' : ''}> Present
                        </label>
                        <label>
                            <input type="radio" name="status_${s.id}" value="Absent" ${currentStatus === 'Absent' ? 'checked' : ''}> Absent
                        </label>
                    </div>
                </div>
            `;
        });
        studentAttendanceListDiv.innerHTML = html;

    } catch (error) {
        console.error('Error loading students for attendance:', error);
        studentAttendanceListDiv.innerHTML = '<p class="error">Error loading students for attendance. Please try again.</p>';
    }
}

async function submitClassAttendance() {
    const classGrade = document.getElementById('attendanceClass').value;
    const attendanceDate = document.getElementById('attendanceDate').value;

    if (!classGrade || !attendanceDate) {
        showMessage('attendanceMessage', 'Please select a class and date', 'error');
        return;
    }

    const studentsResponse = await fetch(`${API_URL}/students?classGrade=${classGrade}`);
    const students = await studentsResponse.json();

    const attendanceRecords = [];
    students.forEach(s => {
        const presentRadio = document.querySelector(`input[name="status_${s.id}"][value="Present"]`);
        const absentRadio = document.querySelector(`input[name="status_${s.id}"][value="Absent"]`);
        
        let status = 'Unknown';
        if (presentRadio && presentRadio.checked) {
            status = 'Present';
        } else if (absentRadio && absentRadio.checked) {
            status = 'Absent';
        }

        // Only add if status is explicitly marked (Present/Absent)
        if (status !== 'Unknown') {
            attendanceRecords.push({
                studentId: s.id,
                date: attendanceDate,
                status: status
            });
        }
    });

    if (attendanceRecords.length === 0) {
        showMessage('attendanceMessage', 'No attendance marked for any student in this class.', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_URL}/markClassAttendance`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(attendanceRecords)
        });

        if (response.ok) {
            showMessage('attendanceMessage', 'Class attendance marked successfully!', 'success');
            loadAttendance(); // Refresh the general attendance table
            loadStudentsForAttendance(); // Refresh the marking section to show updated statuses
        } else {
            const errorData = await response.json();
            showMessage('attendanceMessage', `Error marking attendance: ${errorData.error || response.statusText}`, 'error');
        }
    } catch (error) {
        showMessage('attendanceMessage', 'Server error: ' + error.message, 'error');
    }
}


async function loadAttendance() {
    const viewAttendanceClass = document.getElementById('viewAttendanceClass') ? document.getElementById('viewAttendanceClass').value : '';
    let url = `${API_URL}/records`;
    if (viewAttendanceClass) {
        url += `?classGrade=${viewAttendanceClass}`;
    }

    try {
        const response = await fetch(url);
        const records = await response.json();
        
        const tbody = document.querySelector('#attendanceTable tbody');
        if (tbody) {
            tbody.innerHTML = records.map(r => `
                <tr>
                    <td>${r.studentName}</td>
                    <td>${r.rollNumber}</td>
                    <td>Class ${r.classGrade}</td>
                    <td>${r.date}</td>
                    <td class="status-${r.status.toLowerCase()}">${r.status}</td>
                    <td>
                        <button class="delete-btn" onclick="deleteAttendance(${r.id}, '${r.studentName}', '${r.date}')">Delete</button>
                    </td>
                </tr>
            `).join('');
        }
    } catch (error) {
        console.error('Error loading attendance:', error);
    }
}

async function deleteAttendance(id, studentName, date) {
    if (!confirm(`Are you sure you want to delete attendance record for "${studentName}" on ${date}?`)) {
        return;
    }

    try {
        const response = await fetch(`${API_URL}/record/delete`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({id})
        });

        if (response.ok) {
            loadAttendance();
            // Also refresh student report if it's open and for this student
            const selectedStudentId = document.getElementById('reportStudentSelect') ? document.getElementById('reportStudentSelect').value : null;
            if (selectedStudentId) {
                const currentStudentData = Array.from(document.getElementById('reportStudentSelect').options).find(opt => opt.value === selectedStudentId);
                if (currentStudentData && currentStudentData.dataset.name === studentName) { // Crude check, better with studentId
                     getStudentAttendanceById();
                }
            }
        } else {
            const errorData = await response.json();
            alert(`Error deleting attendance record: ${errorData.error || response.statusText}`);
        }
    } catch (error) {
        alert('Server error: ' + error.message);
    }
}


// Student Report Functions (student_report.html)

async function getStudentAttendanceById() {
    const studentSelect = document.getElementById('reportStudentSelect');
    const studentId = studentSelect.value;
    const studentReportDetails = document.getElementById('studentReportDetails');
    const studentReportMessage = document.getElementById('studentReportMessage');

    studentReportDetails.style.display = 'none';
    studentReportMessage.style.display = 'none';
    
    if (!studentId) {
        studentReportMessage.textContent = 'Please select a student.';
        studentReportMessage.className = 'message error';
        studentReportMessage.style.display = 'block';
        return;
    }

    const selectedOption = studentSelect.options[studentSelect.selectedIndex];
    const studentName = selectedOption.dataset.name;
    const studentRoll = selectedOption.dataset.roll;
    const studentClass = selectedOption.dataset.class;

    try {
        const response = await fetch(`${API_URL}/studentAttendanceById?studentId=${studentId}`);
        const attendanceRecords = await response.json();

        if (attendanceRecords.length === 0) {
            studentReportMessage.textContent = `No attendance records found for ${studentName}.`;
            studentReportMessage.className = 'message error';
            studentReportMessage.style.display = 'block';
            return;
        }

        let totalDays = attendanceRecords.length;
        let daysPresent = attendanceRecords.filter(r => r.status === 'Present').length;
        let daysAbsent = attendanceRecords.filter(r => r.status === 'Absent').length;
        let attendancePercentage = totalDays > 0 ? ((daysPresent / totalDays) * 100).toFixed(2) : 0;

        document.getElementById('reportStudentName').textContent = studentName;
        document.getElementById('reportStudentRoll').textContent = studentRoll;
        document.getElementById('reportStudentClass').textContent = studentClass;
        document.getElementById('totalDays').textContent = totalDays;
        document.getElementById('daysPresent').textContent = daysPresent;
        document.getElementById('daysAbsent').textContent = daysAbsent;
        document.getElementById('attendancePercentage').textContent = attendancePercentage;

        const tbody = document.querySelector('#individualAttendanceTable tbody');
        tbody.innerHTML = attendanceRecords.map(r => `
            <tr>
                <td>${r.date}</td>
                <td class="status-${r.status.toLowerCase()}">${r.status}</td>
            </tr>
        `).join('');

        studentReportDetails.style.display = 'block';

    } catch (error) {
        console.error('Error fetching student attendance by ID:', error);
        studentReportMessage.textContent = 'Error fetching student attendance. Please try again.';
        studentReportMessage.className = 'message error';
        studentReportMessage.style.display = 'block';
    }
}


// Class Report Functions (class_report.html)

async function loadMonthlyClassAttendance() {
    const classGrade = document.getElementById('reportClassSelect').value;
    const monthYear = document.getElementById('reportMonthSelect').value; // e.g., "YYYY-MM"
    const classReportDetails = document.getElementById('classReportDetails');
    const classReportMessage = document.getElementById('classReportMessage');

    classReportDetails.style.display = 'none';
    classReportMessage.style.display = 'none';

    if (!classGrade || !monthYear) {
        classReportMessage.textContent = 'Please select both class and month.';
        classReportMessage.className = 'message error';
        classReportMessage.style.display = 'block';
        return;
    }

    document.getElementById('reportClassName').textContent = `Class ${classGrade}`;
    document.getElementById('reportMonthYear').textContent = monthYear;

    try {
        const response = await fetch(`${API_URL}/monthlyClassAttendance?classGrade=${classGrade}&month=${monthYear}`);
        const classAttendanceSummary = await response.json();

        const tbody = document.querySelector('#monthlyClassAttendanceTable tbody');
        tbody.innerHTML = ''; // Clear previous data

        if (classAttendanceSummary.length === 0) {
            classReportMessage.textContent = `No attendance records found for Class ${classGrade} in ${monthYear}.`;
            classReportMessage.className = 'message error';
            classReportMessage.style.display = 'block';
            return;
        }

        classAttendanceSummary.forEach(student => {
            const totalDaysMarked = student.presentDays + student.absentDays;
            const percentage = totalDaysMarked > 0 ? ((student.presentDays / totalDaysMarked) * 100).toFixed(2) : 0;
            tbody.innerHTML += `
                <tr>
                    <td>${student.studentName}</td>
                    <td>${student.rollNumber}</td>
                    <td>${student.presentDays}</td>
                    <td>${student.absentDays}</td>
                    <td>${totalDaysMarked}</td>
                    <td>${percentage}%</td>
                </tr>
            `;
        });

        classReportDetails.style.display = 'block';

    } catch (error) {
        console.error('Error fetching monthly class attendance:', error);
        classReportMessage.textContent = 'Error fetching monthly class attendance. Please try again.';
        classReportMessage.className = 'message error';
        classReportMessage.style.display = 'block';
    }
}

// Initial loads for pages
// (These are typically called from the respective HTML files' script blocks
// or conditionally based on the current page to avoid errors)
// Example: if (document.getElementById('studentsTable')) { loadStudents(); }