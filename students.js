import { db, isDemoMode, mockStudents, mockDoubts, showToast } from './firebase-config.js';
import { collection, onSnapshot } from "https://www.gstatic.com/firebasejs/10.8.1/firebase-firestore.js";

const studentsTableBody = document.getElementById('students-table-body');
let globalStudents = [];
let globalDoubts = [];
let globalEvaluations = [];

// Analytics Modal Elements
const studentAnalyticsModal = document.getElementById('student-analytics-modal');
const closeAnalyticsModalBtn = document.getElementById('close-analytics-modal');
const analyticsStudentEmail = document.getElementById('analytics-student-email');
const modalTotalDoubts = document.getElementById('modal-total-doubts');
const modalClearedDoubts = document.getElementById('modal-cleared-doubts');
const modalPendingDoubts = document.getElementById('modal-pending-doubts');
const modalStudentLogs = document.getElementById('modal-student-logs');
const modalStudentEvaluations = document.getElementById('modal-student-evaluations');

let studentChartInstance = null;

if(closeAnalyticsModalBtn) {
    closeAnalyticsModalBtn.addEventListener('click', () => studentAnalyticsModal.classList.remove('show'));
}
if(studentAnalyticsModal) {
    studentAnalyticsModal.addEventListener('click', (e) => {
        if(e.target === studentAnalyticsModal) studentAnalyticsModal.classList.remove('show');
    });
}

function loadStudentsData() {
    if (isDemoMode) {
        setTimeout(() => {
            globalStudents = mockStudents;
            globalDoubts = mockDoubts;
            renderStudentsTable();
        }, 800);
    } else {
        // Realtime Listener for Users
        onSnapshot(collection(db, "users"), (studentsSnap) => {
            globalStudents = studentsSnap.docs.map(doc => {
                const data = doc.data();
                return { id: doc.id, email: data.email || data.studentId || doc.id, name: data.name || data.displayName || '', ...data };
            });
            renderStudentsTable();
        }, (error) => {
            console.error("Error loading users: ", error);
            showToast("Error loading student data", "error");
        });

        // Realtime Listener for Doubts (student_logs)
        onSnapshot(collection(db, "student_logs"), (doubtsSnap) => {
            globalDoubts = doubtsSnap.docs.map(doc => {
                const data = doc.data();
                return {
                    id: doc.id,
                    studentEmail: data.studentId || 'Unknown',
                    studentId: data.studentId || 'Unknown',
                    question: data.studentInput || 'No input recorded',
                    status: data.aiAnalysis ? 'cleared' : 'pending',
                    date: data.timestamp ? (data.timestamp.toDate ? data.timestamp.toDate().toLocaleDateString() : new Date(data.timestamp).toLocaleDateString()) : 'N/A',
                    ...data
                };
            });
            renderStudentsTable();
        }, (error) => {
            console.error("Error loading doubts: ", error);
            showToast("Error loading doubts data", "error");
        });

        // Realtime Listener for Evaluations
        onSnapshot(collection(db, "student_evaluations"), (evalsSnap) => {
            globalEvaluations = evalsSnap.docs.map(doc => {
                const data = doc.data();
                return {
                    id: doc.id,
                    date: data.timestamp ? (data.timestamp.toDate ? data.timestamp.toDate().toLocaleDateString() : new Date(data.timestamp).toLocaleDateString()) : 'N/A',
                    ...data
                };
            });
        }, (error) => {
            console.error("Error loading evaluations: ", error);
        });
    }
}

function renderStudentsTable() {
    if(!studentsTableBody) return;
    studentsTableBody.innerHTML = '';
    
    if (globalStudents.length === 0) {
        studentsTableBody.innerHTML = '<tr><td colspan="4" class="text-center">No students found.</td></tr>';
        return;
    }

    globalStudents.forEach((student, index) => {
        // Calculate student stats
        const studentDoubts = globalDoubts.filter(d => d.studentId === student.id || d.studentEmail === student.email);
        const asked = studentDoubts.length;
        const cleared = studentDoubts.filter(d => d.status === 'cleared').length;
        
        let status = 'Good';
        let statusColor = 'var(--success)';
        if (asked > 0 && cleared < asked) {
            status = 'Needs Help';
            statusColor = 'var(--warning)';
        }

        const studentEvals = globalEvaluations.filter(e => e.studentId === student.email || e.studentId === student.id);
        const evalCount = studentEvals.length;

        const tr = document.createElement('tr');
        tr.className = 'animate-up';
        tr.style.setProperty('--delay', `${index * 0.1}s`);
        tr.style.cursor = 'pointer';
        tr.addEventListener('click', () => openStudentAnalytics(student.email, student.id));
        tr.innerHTML = `
            <td>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <div class="avatar" style="width: 30px; height: 30px; font-size: 12px;">${student.name ? student.name.charAt(0) : student.email.charAt(0).toUpperCase()}</div>
                    ${student.email}
                </div>
            </td>
            <td>${asked}</td>
            <td>${cleared}</td>
            <td><span class="status-badge" style="background: rgba(139, 92, 246, 0.1); color: var(--accent); border: 1px solid rgba(139, 92, 246, 0.3);">${evalCount}</span></td>
            <td style="color: ${statusColor}; font-weight: 500;">${status}</td>
        `;
        studentsTableBody.appendChild(tr);
    });
}

function openStudentAnalytics(studentEmail, studentId) {
    if(!studentAnalyticsModal) return;
    studentAnalyticsModal.classList.add('show');
    analyticsStudentEmail.textContent = studentEmail;

    // Filter doubts for this student
    const studentDoubts = globalDoubts.filter(d => d.studentEmail === studentEmail || d.studentId === studentId);
    
    const total = studentDoubts.length;
    const cleared = studentDoubts.filter(d => d.status === 'cleared').length;
    const pending = total - cleared;

    modalTotalDoubts.textContent = total;
    modalClearedDoubts.textContent = cleared;
    modalPendingDoubts.textContent = pending;

    // Render Logs (Doubts)
    modalStudentLogs.innerHTML = '';
    if(total === 0) {
        modalStudentLogs.innerHTML = '<p class="text-center" style="padding: 20px;">No logs found for this student.</p>';
    } else {
        // Sort newest first
        studentDoubts.sort((a, b) => new Date(b.date) - new Date(a.date)).forEach((doubt, i) => {
            const div = document.createElement('div');
            div.className = 'doubt-card animate-up';
            div.style.setProperty('--delay', `${Math.min(i * 0.05, 0.5)}s`);
            div.style.marginBottom = '10px';
            div.style.padding = '15px';
            div.innerHTML = `
                <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                    <span style="font-size: 12px; color: var(--text-muted);"><i class="fa-regular fa-clock"></i> ${doubt.date}</span>
                    <span class="status-badge status-${doubt.status}">${doubt.status}</span>
                </div>
                <div style="font-size: 14px;">"${doubt.question}"</div>
            `;
            modalStudentLogs.appendChild(div);
        });
    }

    // Render Evaluations
    if(modalStudentEvaluations) {
        modalStudentEvaluations.innerHTML = '';
        const studentEvals = globalEvaluations.filter(e => e.studentId === studentEmail || e.studentId === studentId);
        
        if(studentEvals.length === 0) {
            modalStudentEvaluations.innerHTML = '<p class="text-center" style="padding: 20px;">No evaluations yet.</p>';
        } else {
            studentEvals.sort((a, b) => new Date(b.date) - new Date(a.date)).forEach((evalItem, i) => {
                const div = document.createElement('div');
                div.className = 'doubt-card animate-up';
                div.style.setProperty('--delay', `${Math.min(i * 0.05, 0.5)}s`);
                div.style.marginBottom = '10px';
                div.style.padding = '15px';
                
                let levelColor = 'var(--info)';
                if(evalItem.understandingLevel === 'Low') levelColor = 'var(--danger)';
                if(evalItem.understandingLevel === 'High') levelColor = 'var(--success)';

                div.innerHTML = `
                    <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                        <span style="font-size: 13px; color: var(--primary);">Topic: ${evalItem.topic}</span>
                        <span class="status-badge" style="background: ${levelColor}22; color: ${levelColor}; border: 1px solid ${levelColor}44;">Level: ${evalItem.understandingLevel || 'N/A'}</span>
                    </div>
                    <div style="font-size: 14px; margin-bottom: 8px;"><strong>Answer:</strong> "${evalItem.answer}"</div>
                    <div style="font-size: 12px; color: var(--text-muted); background: rgba(0,0,0,0.2); padding: 8px; border-radius: 6px;">${evalItem.feedback}</div>
                `;
                modalStudentEvaluations.appendChild(div);
            });
        }
    }

    // Render Chart
    initStudentChart(cleared, pending);
}

function initStudentChart(cleared, pending) {
    const canvas = document.getElementById('studentAnalyticsChart');
    if(!canvas) return;
    const ctx = canvas.getContext('2d');
    
    if (studentChartInstance) {
        studentChartInstance.destroy();
    }

    if(typeof Chart !== 'undefined') {
        Chart.defaults.color = '#94a3b8';
        Chart.defaults.font.family = "'Outfit', sans-serif";

        studentChartInstance = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: ['Cleared', 'Pending'],
                datasets: [{
                    data: [cleared, pending],
                    backgroundColor: ['rgba(16, 185, 129, 0.8)', 'rgba(245, 158, 11, 0.8)'],
                    borderColor: ['rgba(16, 185, 129, 1)', 'rgba(245, 158, 11, 1)'],
                    borderWidth: 1,
                    hoverOffset: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '75%',
                plugins: {
                    legend: { position: 'bottom', labels: { padding: 10, usePointStyle: true, boxWidth: 8 } }
                }
            }
        });
    }
}

// Initialize
document.addEventListener('DOMContentLoaded', loadStudentsData);
