import React, { useState, useEffect } from "react";
import { 
  Menu, 
  X, 
  MessageSquare, 
  FileText, 
  Upload, 
  Trash2,
  Moon, 
  Sun, 
  Send,
  User,
  Bot
} from "lucide-react";

export default function App() {
  const [messages, setMessages] = useState([
    { sender: "bot", text: "Hello 👋 I'm PolicyBot. How can I assist you today?" }
  ]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [uploadedDocs, setUploadedDocs] = useState([]); 
  const [files, setFiles] = useState(null);

  const [documents, setDocuments] = useState([]); // ✅ document history
  const [darkMode, setDarkMode] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [activeTab, setActiveTab] = useState("chat");

  // 📌 Fetch history on mount
  useEffect(() => {
    fetchDocs();
  }, []);

  const fetchDocs = async () => {
    try {
      const res = await fetch("http://localhost:8080/api/docs/history");
      const data = await res.json();
      setDocuments(data);
    } catch (err) {
      console.error("Error fetching history:", err);
    }
  };

  // 📌 Handle document upload
  const handleUpload = async () => {
    if (!files) return;

    const formData = new FormData();
    formData.append("file", files[0]); 

    try {
      // Upload to Pinecone
      await fetch("http://localhost:8080/api/rag/upload", {
        method: "POST",
        body: formData,
      });

      // Upload to MySQL
      await fetch("http://localhost:8080/api/docs/upload", {
        method: "POST",
        body: formData,
      });

      setUploadedDocs([...uploadedDocs, files[0].name]);
      await fetchDocs();
      setFiles(null);

      setMessages((prev) => [...prev, { sender: "bot", text: "✅ File uploaded successfully" }]);
    } catch (err) {
      console.error(err);
      setMessages((prev) => [...prev, { sender: "bot", text: "❌ Upload failed" }]);
    }
  };

  // 📌 Handle delete
  const handleDelete = async (docId) => {
    if (!window.confirm("Are you sure you want to delete this document?")) return;

    try {
      await fetch(`http://localhost:8080/api/docs/${docId}`, { method: "DELETE" });
      await fetchDocs();
    } catch (err) {
      console.error("Delete error:", err);
    }
  };

  // 📌 Handle chat question
  const handleSend = async () => {
    if (!input.trim()) return;

    setMessages((prev) => [...prev, { sender: "user", text: input }]);
    const userMessage = input;
    setInput("");
    setLoading(true);

    try {
      const encodedQ = encodeURIComponent(userMessage);
      const response = await fetch(`http://localhost:8080/api/query/ask?question=${encodedQ}`);
      const data = await response.text();

      setMessages((prev) => [
        ...prev,
        { sender: "bot", text: data || "⚠️ No response received" },
      ]);
    } catch (error) {
      setMessages((prev) => [
        ...prev,
        { sender: "bot", text: "❌ Error connecting to server" },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const toggleTheme = () => setDarkMode(!darkMode);

  const themeClasses = darkMode 
    ? "bg-gray-900 text-white" 
    : "bg-gray-50 text-gray-900";

  const cardClasses = darkMode 
    ? "bg-gray-800 border-gray-700" 
    : "bg-white border-gray-200";

  const inputClasses = darkMode 
    ? "bg-gray-700 border-gray-600 text-white placeholder-gray-400" 
    : "bg-white border-gray-300 text-gray-900 placeholder-gray-500";

  return (
    <div className={`min-h-screen transition-colors duration-300 ${themeClasses}`}>
      {/* Header */}
      <div className={`sticky top-0 z-50 ${cardClasses} border-b px-4 py-3 flex items-center justify-between`}>
        <button
          onClick={() => setSidebarOpen(!sidebarOpen)}
          className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
        >
          {sidebarOpen ? <X size={24} /> : <Menu size={24} />}
        </button>
        
        <div className="flex items-center space-x-2">
          <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center">
            <FileText size={20} className="text-white" />
          </div>
          <div>
            <h1 className="font-bold text-lg">PolicyBot</h1>
            <p className="text-sm opacity-60">Document Q&A System</p>
          </div>
        </div>

        <button
          onClick={toggleTheme}
          className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
        >
          {darkMode ? <Sun size={20} /> : <Moon size={20} />}
        </button>
      </div>

      <div className="flex">
        {/* Sidebar */}
        <div className={`fixed inset-y-0 left-0 z-40 w-80 transform transition-transform duration-300 ${
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        } ${cardClasses} border-r`}>
          <div className="pt-20 pb-4">
            <div className="px-4 mb-6">
              <h3 className="text-sm font-semibold opacity-60 mb-3 uppercase tracking-wider">Navigation</h3>
              <nav className="space-y-2">
                <button
                  onClick={() => { setActiveTab("chat"); setSidebarOpen(false); }}
                  className={`w-full flex items-center space-x-3 px-3 py-2 rounded-lg text-left transition-colors ${
                    activeTab === "chat" ? "bg-blue-100 text-blue-600 dark:bg-blue-900" : "hover:bg-gray-100 dark:hover:bg-gray-700"
                  }`}
                >
                  <MessageSquare size={20} />
                  <span>Q&A Chat</span>
                </button>
                
                <button
                  onClick={() => { setActiveTab("upload"); setSidebarOpen(false); }}
                  className={`w-full flex items-center space-x-3 px-3 py-2 rounded-lg text-left transition-colors ${
                    activeTab === "upload" ? "bg-blue-100 text-blue-600 dark:bg-blue-900" : "hover:bg-gray-100 dark:hover:bg-gray-700"
                  }`}
                >
                  <Upload size={20} />
                  <span>Upload</span>
                </button>

                {/* ✅ Documents Section */}
                <div className="mt-4">
                  <h4 className="text-xs uppercase font-semibold opacity-60 mb-2">Documents</h4>
                  <ul className="space-y-2">
                    {documents.length === 0 && (
                      <li className="text-xs text-gray-400">No docs yet</li>
                    )}
                    {documents.map((doc) => (
                      <li key={doc.id} className="flex items-center justify-between bg-gray-700/20 p-2 rounded-lg">
                        <div className="flex items-center space-x-2">
                          <FileText size={16} className="text-blue-400" />
                          <span className="truncate w-32">{doc.name}</span>
                        </div>
                        <Trash2
                          size={16}
                          className="text-red-400 cursor-pointer hover:text-red-600"
                          onClick={() => handleDelete(doc.id,doc.name)}
                        />
                      </li>
                    ))}
                  </ul>
                </div>
              </nav>
            </div>
          </div>
        </div>

        {/* Main Content */}
        <div className={`flex-1 transition-all duration-300 ${sidebarOpen ? "ml-80" : "ml-0"}`}>
          <div className="p-4 max-w-4xl mx-auto">
            {/* Chat Tab */}
            {activeTab === "chat" && (
              <div className="space-y-4">
                <div className={`${cardClasses} rounded-2xl p-6 border`}>
                  <div className="flex items-center justify-between mb-2">
                    <h2 className="text-2xl font-bold">Policy Q&A Assistant</h2>
                    <div className="flex items-center space-x-2 text-sm opacity-60">
                      <FileText size={16} />
                      <span>{documents.length} documents available</span>
                    </div>
                  </div>
                  <p className="opacity-70">Ask questions about company policies and procedures</p>
                </div>

                {/* Chat Messages */}
                <div className={`${cardClasses} rounded-2xl border flex flex-col h-96`}>
                  <div className="flex-1 overflow-y-auto p-4 space-y-4">
                    {messages.map((msg, index) => (
                      <div key={index} className={`flex items-start space-x-3 ${msg.sender === "user" ? "flex-row-reverse space-x-reverse" : ""}`}>
                        <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                          msg.sender === "user" ? "bg-blue-600" : "bg-gray-600"
                        }`}>
                          {msg.sender === "user" 
                            ? <User size={16} className="text-white" /> 
                            : <Bot size={16} className="text-white" />
                          }
                        </div>
                        <div className={`max-w-xs lg:max-w-md px-4 py-2 rounded-2xl ${
                          msg.sender === "user" 
                            ? "bg-blue-600 text-white" 
                            : darkMode ? "bg-gray-700" : "bg-gray-100"
                        }`}>
                          <p className="text-sm">{msg.text}</p>
                        </div>
                      </div>
                    ))}
                    {loading && (
                      <div className="flex items-start space-x-3">
                        <div className="w-8 h-8 rounded-full bg-gray-600 flex items-center justify-center">
                          <Bot size={16} className="text-white" />
                        </div>
                        <div className={`px-4 py-2 rounded-2xl ${darkMode ? "bg-gray-700" : "bg-gray-100"}`}>
                          <div className="flex space-x-1">
                            <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
                            <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: "0.1s"}}></div>
                            <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: "0.2s"}}></div>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                  
                  {/* Input Area */}
                  <div className="p-4 border-t border-gray-200 dark:border-gray-700">
                    <div className="flex space-x-2">
                      <input
                        type="text"
                        className={`flex-1 ${inputClasses} rounded-full px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 border`}
                        placeholder="Type your question..."
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && handleSend()}
                      />
                      <button
                        onClick={handleSend}
                        disabled={!input.trim() || loading}
                        className="w-10 h-10 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 text-white rounded-full flex items-center justify-center transition-colors"
                      >
                        <Send size={16} />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Upload Tab */}
            {activeTab === "upload" && (
              <div className={`${cardClasses} rounded-2xl p-6 border`}>
                <h2 className="text-2xl font-bold mb-4">📂 Document Upload</h2>
                <div className="space-y-4">
                  <div className={`border-2 border-dashed rounded-lg p-8 text-center ${darkMode ? "border-gray-600" : "border-gray-300"}`}>
                    <Upload size={48} className="mx-auto opacity-30 mb-4" />
                    <input
                      type="file"
                      onChange={(e) => setFiles(e.target.files)}
                      className="mb-4 block w-full text-sm file:mr-4 file:py-2 file:px-4 file:rounded-full file:border-0 file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
                    />
                    <button
                      onClick={handleUpload}
                      disabled={!files}
                      className="px-6 py-3 bg-green-600 hover:bg-green-700 disabled:bg-gray-400 text-white rounded-lg transition-colors"
                    >
                      Upload File
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Overlay for mobile sidebar */}
      {sidebarOpen && (
        <div 
          className="fixed inset-0 bg-black bg-opacity-50 z-30 md:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}
    </div>
  );
}
