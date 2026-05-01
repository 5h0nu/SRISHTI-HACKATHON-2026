import { db, showToast } from './firebase-config.js';
import { doc, setDoc, collection, getDocs, deleteDoc } from "https://www.gstatic.com/firebasejs/10.8.1/firebase-firestore.js";

document.addEventListener('DOMContentLoaded', () => {
    const uploadBtn = document.getElementById('upload-dataset-btn');
    if (uploadBtn) {
        uploadBtn.addEventListener('click', uploadDataset);
    }
    loadDatasets();
});

async function loadDatasets() {
    const tableBody = document.getElementById('datasets-table-body');
    if (!tableBody) return;

    try {
        const querySnapshot = await getDocs(collection(db, "datasets"));
        
        let datasets = [];
        querySnapshot.forEach(docSnap => {
            datasets.push({ id: docSnap.id, ...docSnap.data() });
        });
        
        // Sort by createdAt descending
        datasets.sort((a, b) => {
            const timeA = a.createdAt && a.createdAt.toMillis ? a.createdAt.toMillis() : (a.createdAt ? new Date(a.createdAt).getTime() : 0);
            const timeB = b.createdAt && b.createdAt.toMillis ? b.createdAt.toMillis() : (b.createdAt ? new Date(b.createdAt).getTime() : 0);
            return timeB - timeA;
        });

        tableBody.innerHTML = '';
        
        if (datasets.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="4" class="text-center">No datasets uploaded yet.</td></tr>';
            return;
        }

        datasets.forEach((data) => {
            const itemsCount = data.data ? Object.keys(data.data).length : 0;
            
            // Format date
            let dateStr = 'Unknown';
            if (data.createdAt) {
                const dateObj = data.createdAt.toDate ? data.createdAt.toDate() : new Date(data.createdAt);
                dateStr = dateObj.toLocaleDateString();
            }

            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td style="font-weight: 500;">${data.title || data.id}</td>
                <td>${itemsCount} terms</td>
                <td>${dateStr}</td>
                <td>
                    <button class="btn-text-only delete-dataset-btn" data-id="${data.id}" style="color: var(--danger);">
                        <i class="fa-solid fa-trash"></i> Delete
                    </button>
                </td>
            `;
            tableBody.appendChild(tr);
        });

        // Add event listeners for delete buttons
        document.querySelectorAll('.delete-dataset-btn').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const id = e.currentTarget.getAttribute('data-id');
                if (confirm(`Are you sure you want to delete dataset "${id}"?`)) {
                    await deleteDataset(id);
                }
            });
        });

    } catch (error) {
        console.error("Error loading datasets:", error);
        tableBody.innerHTML = '<tr><td colspan="4" class="text-center" style="color: var(--danger);">Error loading datasets</td></tr>';
    }
}

async function deleteDataset(id) {
    try {
        await deleteDoc(doc(db, "datasets", id));
        showToast("Dataset deleted successfully", "success");
        loadDatasets(); // Reload table
    } catch (error) {
        console.error("Error deleting dataset:", error);
        showToast("Error deleting dataset", "error");
    }
}

async function uploadDataset() {
    const title = document.getElementById("title").value.trim().toLowerCase();
    const rawData = document.getElementById("dataset").value.trim();
    const uploadBtn = document.getElementById('upload-dataset-btn');

    if (!title || !rawData) {
        showToast("Please enter title and dataset data.", "error");
        return;
    }

    const datasetObject = {};
    const lines = rawData.split("\n");

    lines.forEach(line => {
        const parts = line.split(":");

        if (parts.length >= 2) {
            const key = parts[0].trim().toLowerCase();
            const value = parts.slice(1).join(":").trim();

            if (key && value) {
                datasetObject[key] = value;
            }
        }
    });

    if (Object.keys(datasetObject).length === 0) {
        showToast("Invalid format. Use keyword: answer", "error");
        return;
    }

    const originalText = uploadBtn.innerHTML;
    uploadBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Uploading...';
    uploadBtn.disabled = true;

    try {
        await setDoc(doc(db, "datasets", title), {
            title: title,
            data: datasetObject,
            createdAt: new Date()
        });

        showToast("Dataset uploaded successfully!", "success");
        document.getElementById("title").value = "";
        document.getElementById("dataset").value = "";
        
        // Reload datasets to show the newly added one
        loadDatasets();
    } catch (error) {
        showToast("Error: " + error.message, "error");
        console.error("Error uploading dataset: ", error);
    } finally {
        uploadBtn.innerHTML = originalText;
        uploadBtn.disabled = false;
    }
}
