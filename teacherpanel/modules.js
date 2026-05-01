import { db, storage, showToast, isDemoMode } from './firebase-config.js';
import { collection, doc, setDoc, getDocs, serverTimestamp, deleteDoc } from "https://www.gstatic.com/firebasejs/10.8.1/firebase-firestore.js";
import { ref, uploadBytesResumable, getDownloadURL, deleteObject } from "https://www.gstatic.com/firebasejs/10.8.1/firebase-storage.js";

document.addEventListener('DOMContentLoaded', () => {
    const uploadForm = document.getElementById('upload-form');
    const uploadBtn = document.getElementById('upload-btn');
    const moduleTitleInput = document.getElementById('module-title');
    const moduleUrlInput = document.getElementById('module-url');
    const modulesTableBody = document.getElementById('modules-table-body');

    loadModules();

    uploadForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const title = moduleTitleInput.value.trim();
        const url = moduleUrlInput.value.trim();

        if (!title || !url) {
            showToast('Please provide both a title and a link.', 'error');
            return;
        }

        if (isDemoMode) {
            showToast('Demo Mode: Saving simulated.', 'success');
            uploadForm.reset();
            return;
        }

        try {
            uploadBtn.disabled = true;
            uploadBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Saving...';

            console.log("Saving metadata to Firestore...");
            const safeTitleId = title.replace(/\//g, '-');
            const moduleRef = doc(db, 'module', safeTitleId);
            
            await setDoc(moduleRef, {
                title: title,
                downloadURL: url,
                pdf: url,
                uploadedAt: serverTimestamp()
            });
            console.log("Metadata saved to Firestore");

            showToast('Module saved successfully!', 'success');
            uploadForm.reset();
            loadModules();

        } catch (error) {
            console.error("Error saving module:", error);
            showToast('Error saving module: ' + error.message, 'error');
        } finally {
            uploadBtn.disabled = false;
            uploadBtn.innerHTML = '<i class="fa-solid fa-cloud-arrow-up"></i> Save Module';
        }
    });

    async function loadModules() {
        if (isDemoMode) {
            modulesTableBody.innerHTML = '<tr><td colspan="3" class="text-center">No modules in demo mode</td></tr>';
            return;
        }

        try {
            const querySnapshot = await getDocs(collection(db, 'module'));
            
            if (querySnapshot.empty) {
                modulesTableBody.innerHTML = '<tr><td colspan="3" class="text-center">No modules found.</td></tr>';
                return;
            }

            modulesTableBody.innerHTML = '';
            
            querySnapshot.forEach((docSnap) => {
                const data = docSnap.data();
                const tr = document.createElement('tr');
                
                // Format date safely
                let dateStr = 'Unknown';
                if (data.uploadedAt && data.uploadedAt.toDate) {
                    dateStr = data.uploadedAt.toDate().toLocaleDateString();
                }

                tr.innerHTML = `
                    <td>
                        <div style="font-weight: 500;">${data.title}</div>
                        <div style="font-size: 12px; color: var(--text-muted);">External Link</div>
                    </td>
                    <td>${dateStr}</td>
                    <td>
                        <a href="${data.downloadURL}" target="_blank" class="btn-text-only" style="color: var(--primary); text-decoration: none; margin-right: 15px;">
                            <i class="fa-solid fa-download"></i> Download
                        </a>
                        <button class="btn-text-only delete-btn" data-id="${docSnap.id}" style="color: var(--danger);">
                            <i class="fa-solid fa-trash"></i> Delete
                        </button>
                    </td>
                `;
                modulesTableBody.appendChild(tr);
            });

            // Add delete listeners
            document.querySelectorAll('.delete-btn').forEach(btn => {
                btn.addEventListener('click', async (e) => {
                    const id = e.currentTarget.getAttribute('data-id');
                    if (confirm('Are you sure you want to delete this module?')) {
                        await deleteModule(id);
                    }
                });
            });

        } catch (error) {
            console.error("Error loading modules:", error);
            modulesTableBody.innerHTML = `<tr><td colspan="3" class="text-center" style="color: var(--danger);">Error loading modules</td></tr>`;
        }
    }

    async function deleteModule(id) {
        try {
            // Delete from firestore
            await deleteDoc(doc(db, 'module', id));

            showToast('Module deleted successfully', 'success');
            loadModules();
        } catch (error) {
            console.error("Error deleting module:", error);
            showToast('Error deleting module', 'error');
        }
    }
});
