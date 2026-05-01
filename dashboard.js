import { db, isDemoMode, mockStudents, mockDoubts, showToast } from './firebase-config.js';
import { collection, onSnapshot } from "https://www.gstatic.com/firebasejs/10.8.1/firebase-firestore.js";

// Dashboard Elements
const totalStudentsEl = document.getElementById('total-students');
const doubtsClearedEl = document.getElementById('doubts-cleared');
const doubtsAskedEl = document.getElementById('doubts-asked');
const doubtsAskedCard = document.getElementById('doubts-asked-card');

const recentDoubtsList = document.getElementById('recent-doubts-list');

const totalStudentsCard = document.getElementById('total-students-card');
const doubtsClearedCard = document.getElementById('doubts-cleared-card');

const totalEvaluationsEl = document.getElementById('total-evaluations');
const totalEvaluationsCard = document.getElementById('total-evaluations-card');

// Modal Elements
const doubtsModal = document.getElementById('doubts-modal');
const closeDoubtsModalBtn = document.getElementById('close-doubts-modal');
const viewAllDoubtsBtn = document.getElementById('view-all-doubts-btn');
const filterBtns = document.querySelectorAll('.filter-btn');
const doubtsFullList = document.getElementById('doubts-full-list');

const usersModal = document.getElementById('users-modal');
const closeUsersModalBtn = document.getElementById('close-users-modal');
const usersFullList = document.getElementById('users-full-list');

const evaluationsModal = document.getElementById('evaluations-modal');
const closeEvaluationsModalBtn = document.getElementById('close-evaluations-modal');
const evaluationsFullList = document.getElementById('evaluations-full-list');
const evaluationsSort = document.getElementById('evaluations-sort');

// State
let globalDoubts = [];
let globalStudents = [];
let globalEvaluations = [];
let chartInstance = null;

// Modal Logic
if(doubtsAskedCard) doubtsAskedCard.addEventListener('click', () => openDoubtsModalWithFilter('all'));
if(viewAllDoubtsBtn) viewAllDoubtsBtn.addEventListener('click', () => openDoubtsModalWithFilter('all'));
if(doubtsClearedCard) doubtsClearedCard.addEventListener('click', () => openDoubtsModalWithFilter('cleared'));
if(totalStudentsCard) totalStudentsCard.addEventListener('click', openUsersModal);
if(totalEvaluationsCard) totalEvaluationsCard.addEventListener('click', openEvaluationsModal);

if(closeDoubtsModalBtn) {
    closeDoubtsModalBtn.addEventListener('click', () => {
        doubtsModal.classList.remove('show');
    });
}

if(closeUsersModalBtn) {
    closeUsersModalBtn.addEventListener('click', () => {
        usersModal.classList.remove('show');
    });
}

if(closeEvaluationsModalBtn) {
    closeEvaluationsModalBtn.addEventListener('click', () => {
        evaluationsModal.classList.remove('show');
    });
}

if(doubtsModal) doubtsModal.addEventListener('click', (e) => { if(e.target === doubtsModal) doubtsModal.classList.remove('show'); });
if(usersModal) usersModal.addEventListener('click', (e) => { if(e.target === usersModal) usersModal.classList.remove('show'); });
if(evaluationsModal) evaluationsModal.addEventListener('click', (e) => { if(e.target === evaluationsModal) evaluationsModal.classList.remove('show'); });

if(evaluationsSort) {
    evaluationsSort.addEventListener('change', renderEvaluationsList);
}

function openDoubtsModalWithFilter(filter) {
    if(!doubtsModal) return;
    doubtsModal.classList.add('show');
    renderFullDoubtsList(filter);
    // Reset filters
    filterBtns.forEach(b => {
        b.classList.remove('active');
        if (b.getAttribute('data-filter') === filter) b.classList.add('active');
    });
}

function openUsersModal() {
    if(!usersModal) return;
    usersModal.classList.add('show');
    renderUsersList();
}

function openEvaluationsModal() {
    if(!evaluationsModal) return;
    evaluationsModal.classList.add('show');
    renderEvaluationsList();
}

filterBtns.forEach(btn => {
    btn.addEventListener('click', (e) => {
        filterBtns.forEach(b => b.classList.remove('active'));
        e.target.classList.add('active');
        renderFullDoubtsList(e.target.getAttribute('data-filter'));
    });
});

function loadDashboardData() {
    if (isDemoMode) {
        setTimeout(() => {
            globalStudents = mockStudents;
            globalDoubts = mockDoubts;
            updateDashboardMetrics();
            renderRecentDoubts();
            initChart();
        }, 800);
    } else {
        // Realtime Listener for Users
        onSnapshot(collection(db, "users"), (studentsSnap) => {
            globalStudents = studentsSnap.docs.map(doc => {
                const data = doc.data();
                return { id: doc.id, email: data.email || data.studentId || doc.id, name: data.name || data.displayName || '', ...data };
            });
            updateDashboardMetrics();
            initChart();
        }, (error) => {
            console.error("Error loading users: ", error);
            showToast("Error loading users", "error");
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
            updateDashboardMetrics();
            renderRecentDoubts();
            initChart();
            // If modal is open, update the list
            if(doubtsModal && doubtsModal.classList.contains('show')) {
                const activeFilter = document.querySelector('.filter-btn.active')?.getAttribute('data-filter') || 'all';
                renderFullDoubtsList(activeFilter);
            }
        }, (error) => {
            console.error("Error loading doubts: ", error);
            showToast("Error loading doubts", "error");
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
            if(totalEvaluationsEl) animateValue(totalEvaluationsEl, 0, globalEvaluations.length, 1000);
            initChart();
            if(evaluationsModal && evaluationsModal.classList.contains('show')) {
                renderEvaluationsList();
            }
        });
    }
}

function updateDashboardMetrics() {
    const totalStudents = globalStudents.length;
    const doubtsAsked = globalDoubts.length;
    const doubtsCleared = globalDoubts.filter(d => d.status === 'cleared').length;

    animateValue(totalStudentsEl, 0, totalStudents, 1000);
    animateValue(doubtsClearedEl, 0, doubtsCleared, 1000);
    animateValue(doubtsAskedEl, 0, doubtsAsked, 1000);
}

function renderRecentDoubts() {
    if(!recentDoubtsList) return;
    recentDoubtsList.innerHTML = '';
    // Show last 4 doubts
    const recent = [...globalDoubts].reverse().slice(0, 4);
    
    if (recent.length === 0) {
        recentDoubtsList.innerHTML = '<li>No doubts asked yet.</li>';
        return;
    }

    recent.forEach(doubt => {
        const li = document.createElement('li');
        li.className = 'doubt-item animate-up';
        li.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: flex-start;">
                <div>
                    <h4>${doubt.question}</h4>
                    <p>${doubt.studentEmail} &bull; ${doubt.date}</p>
                </div>
                <span class="status-badge status-${doubt.status}">${doubt.status}</span>
            </div>
        `;
        recentDoubtsList.appendChild(li);
    });
}

function renderFullDoubtsList(filter = 'all') {
    if(!doubtsFullList) return;
    doubtsFullList.innerHTML = '';
    
    let filteredDoubts = globalDoubts;
    if (filter !== 'all') {
        filteredDoubts = globalDoubts.filter(d => d.status === filter);
    }
    
    if (filteredDoubts.length === 0) {
        doubtsFullList.innerHTML = '<p class="text-center" style="padding: 20px;">No doubts found.</p>';
        return;
    }

    // Sort newest first
    filteredDoubts.sort((a, b) => new Date(b.date) - new Date(a.date));

    filteredDoubts.forEach((doubt, index) => {
        const div = document.createElement('div');
        div.className = 'doubt-card animate-up';
        div.style.setProperty('--delay', `${Math.min(index * 0.05, 0.5)}s`);
        div.innerHTML = `
            <div class="doubt-card-header">
                <span class="doubt-student"><i class="fa-solid fa-user-graduate" style="margin-right: 5px;"></i> ${doubt.studentEmail}</span>
                <span class="status-badge status-${doubt.status}">${doubt.status}</span>
            </div>
            <div class="doubt-question">"${doubt.question}"</div>
            <div class="doubt-footer">
                <span>Date: ${doubt.date || 'N/A'}</span>
            </div>
        `;
        doubtsFullList.appendChild(div);
    });
}

function renderUsersList() {
    if(!usersFullList) return;
    usersFullList.innerHTML = '';
    
    if (globalStudents.length === 0) {
        usersFullList.innerHTML = '<p class="text-center" style="padding: 20px;">No students found.</p>';
        return;
    }

    globalStudents.forEach((student, index) => {
        const div = document.createElement('div');
        div.className = 'doubt-card animate-up';
        div.style.setProperty('--delay', `${Math.min(index * 0.05, 0.5)}s`);
        div.innerHTML = `
            <div class="doubt-card-header">
                <span class="doubt-student"><i class="fa-solid fa-user" style="margin-right: 5px;"></i> ${student.email}</span>
            </div>
            <div class="doubt-footer">
                <span>User ID: ${student.id}</span>
            </div>
        `;
        usersFullList.appendChild(div);
    });
}

function renderEvaluationsList() {
    if(!evaluationsFullList) return;
    evaluationsFullList.innerHTML = '';
    if(globalEvaluations.length === 0) {
        evaluationsFullList.innerHTML = '<p class="text-center" style="padding: 20px;">No evaluations found.</p>';
        return;
    }
    
    let sortedEvals = [...globalEvaluations];
    const sortVal = evaluationsSort ? evaluationsSort.value : 'newest';

    if(sortVal === 'newest') {
        sortedEvals.sort((a,b) => new Date(b.date) - new Date(a.date));
    } else if (sortVal === 'high') {
        const levelWeight = { 'High': 3, 'Medium': 2, 'Low': 1 };
        sortedEvals.sort((a,b) => (levelWeight[b.understandingLevel] || 0) - (levelWeight[a.understandingLevel] || 0));
    } else if (sortVal === 'low') {
        const levelWeight = { 'High': 3, 'Medium': 2, 'Low': 1 };
        sortedEvals.sort((a,b) => (levelWeight[a.understandingLevel] || 0) - (levelWeight[b.understandingLevel] || 0));
    }

    sortedEvals.forEach((evalItem, index) => {
        const div = document.createElement('div');
        div.className = 'doubt-card animate-up';
        div.style.setProperty('--delay', `${Math.min(index * 0.05, 0.5)}s`);
        
        let levelColor = 'var(--info)';
        if(evalItem.understandingLevel === 'Low') levelColor = 'var(--danger)';
        if(evalItem.understandingLevel === 'High') levelColor = 'var(--success)';

        div.innerHTML = `
            <div class="doubt-card-header">
                <span class="doubt-student"><i class="fa-solid fa-user-check" style="margin-right: 5px;"></i> ${evalItem.studentId || 'Unknown'}</span>
                <span class="status-badge" style="background: ${levelColor}22; color: ${levelColor}; border: 1px solid ${levelColor}44;">Level: ${evalItem.understandingLevel || 'N/A'}</span>
            </div>
            <div style="font-size: 13px; color: var(--primary); margin-bottom: 5px;">Topic: ${evalItem.topic}</div>
            <div class="doubt-question"><strong>Answer:</strong> "${evalItem.answer}"</div>
            <div style="font-size: 13px; color: var(--text-muted); margin-bottom: 10px; background: rgba(0,0,0,0.2); padding: 10px; border-radius: 8px;">${evalItem.feedback}</div>
            <div class="doubt-footer">
                <span>Date: ${evalItem.date || 'N/A'}</span>
            </div>
        `;
        evaluationsFullList.appendChild(div);
    });
}

function initChart() {
    const canvas = document.getElementById('analyticsChart');
    if(!canvas) return;
    const ctx = canvas.getContext('2d');
    
    if (chartInstance) {
        chartInstance.destroy();
    }

    const totalStudents = globalStudents.length;
    const totalDoubts = globalDoubts.length;
    const clearedDoubts = globalDoubts.filter(d => d.status === 'cleared').length;
    const totalEvals = globalEvaluations.length;

    if(typeof Chart !== 'undefined') {
        Chart.defaults.color = '#94a3b8';
        Chart.defaults.font.family = "'Outfit', sans-serif";

        chartInstance = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: ['Students', 'Asked Doubts', 'Cleared Doubts', 'Evaluations'],
                datasets: [{
                    label: 'Platform Metrics',
                    data: [totalStudents, totalDoubts, clearedDoubts, totalEvals],
                    backgroundColor: [
                        'rgba(59, 130, 246, 0.8)', // Primary blue
                        'rgba(245, 158, 11, 0.8)', // Warning yellow/orange
                        'rgba(16, 185, 129, 0.8)', // Success green
                        'rgba(139, 92, 246, 0.8)'  // Accent purple
                    ],
                    borderColor: [
                        'rgba(59, 130, 246, 1)',
                        'rgba(245, 158, 11, 1)',
                        'rgba(16, 185, 129, 1)',
                        'rgba(139, 92, 246, 1)'
                    ],
                    borderWidth: 1,
                    borderRadius: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        grid: {
                            color: 'rgba(255, 255, 255, 0.05)'
                        }
                    },
                    x: {
                        grid: {
                            display: false
                        }
                    }
                }
            }
        });
    }
}

// Number animation utility
function animateValue(obj, start, end, duration) {
    if(!obj) return;
    let startTimestamp = null;
    const step = (timestamp) => {
        if (!startTimestamp) startTimestamp = timestamp;
        const progress = Math.min((timestamp - startTimestamp) / duration, 1);
        obj.innerHTML = Math.floor(progress * (end - start) + start);
        if (progress < 1) {
            window.requestAnimationFrame(step);
        }
    };
    window.requestAnimationFrame(step);
}

// Initialize
document.addEventListener('DOMContentLoaded', loadDashboardData);
