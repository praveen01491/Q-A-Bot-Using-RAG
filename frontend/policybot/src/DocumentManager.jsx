import React, { useState, useEffect } from "react";

export default function DocumentManager() {
  const [file, setFile] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(false);

  // Fetch history on mount
  useEffect(() => {
    fetch("http://localhost:8080/api/docs/history")
      .then((res) => res.json())
      .then((data) => setDocuments(data))
      .catch((err) => console.error("Error fetching history:", err));
  }, []);

  // Handle file upload
  const handleUpload = async () => {
    if (!file) return alert("Please select a file first!");
    const formData = new FormData();
    formData.append("file", file);

    setLoading(true);
    try {
      // 1. Upload to Pinecone
      await fetch("http://localhost:8080/api/rag/upload", {
        method: "POST",
        body: formData,
      });

      // 2. Upload to MySQL
      await fetch("http://localhost:8080/api/docs/upload", {
        method: "POST",
        body: formData,
      });

      alert("✅ File uploaded to Pinecone + MySQL");

      // Refresh history after upload
      const updated = await fetch("http://localhost:8080/api/docs/history").then((r) =>
        r.json()
      );
      setDocuments(updated);
      setFile(null);
    } catch (err) {
      console.error("Upload error:", err);
      alert("Upload failed!");
    } finally {
      setLoading(false);
    }
  };

  // Handle delete
  const handleDelete = async (docId) => {
    if (!window.confirm("Are you sure you want to delete this document?")) return;

    try {
      await fetch(`http://localhost:8080/api/docs/${docId}`, {
        method: "DELETE",
      });

       await fetch(
      `http://localhost:8080/api/rag/delete?filename=${encodeURIComponent(filename)}`,
      { method: "DELETE" }
    );
      alert("Document deleted!");

      // Refresh history after delete
      const updated = await fetch("http://localhost:8080/api/docs/history").then((r) =>
        r.json()
      );
      setDocuments(updated);
    } catch (err) {
      console.error("Delete error:", err);
      alert("Delete failed!");
    }
  };

  return (
    <div className="max-w-2xl mx-auto p-6 bg-white shadow-lg rounded-2xl">
      <h2 className="text-xl font-bold mb-4">📂 Document Manager</h2>

      {/* File Upload */}
      <div className="flex items-center gap-2 mb-4">
        <input
          type="file"
          onChange={(e) => setFile(e.target.files[0])}
          className="border rounded p-2"
        />
        <button
          onClick={handleUpload}
          disabled={loading}
          className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded"
        >
          {loading ? "Uploading..." : "Upload"}
        </button>
      </div>

      {/* History */}
      <h3 className="text-lg font-semibold mb-2">Uploaded Documents</h3>
      {documents.length === 0 ? (
        <p className="text-gray-500">No documents uploaded yet.</p>
      ) : (
        <ul className="space-y-2">
          {documents.map((doc) => (
            <li
              key={doc.id}
              className="flex justify-between items-center border p-3 rounded-lg"
            >
              <span className="font-medium">{doc.name}</span>
              <button
                onClick={() => handleDelete(doc.id)}
                className="bg-red-500 hover:bg-red-600 text-white px-3 py-1 rounded"
              >
                Delete
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
