import express, { Request, Response, NextFunction } from "express";
import dotenv from "dotenv";

// Load environment variables from .env file
dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

// Built-in middleware
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Basic logger middleware
app.use((req: Request, _res: Response, next: NextFunction) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
  next();
});

// Root route
app.get("/", (_req: Request, res: Response) => {
  res.json({
    message: "Welcome to the Node.js + Express.js + TypeScript API!",
    timestamp: new Date().toISOString(),
  });
});

// Health check endpoint
app.get("/health", (_req: Request, res: Response) => {
  res.status(200).json({
    status: "ok",
    uptime: process.uptime(),
    timestamp: new Date().toISOString(),
  });
});

// Sample API route with typed data
interface User {
  id: number;
  name: string;
  email: string;
}

const sampleUsers: User[] = [
  { id: 1, name: "Alice Smith", email: "alice@example.com" },
  { id: 2, name: "Bob Johnson", email: "bob@example.com" },
];

app.get("/api/users", (_req: Request, res: Response) => {
  res.json({ success: true, data: sampleUsers });
});

// 404 handler
app.use((req: Request, res: Response) => {
  res.status(404).json({
    error: "Not Found",
    message: `Cannot ${req.method} ${req.path}`,
  });
});

// Global error handler
app.use((err: Error, _req: Request, res: Response, _next: NextFunction) => {
  console.error("Unhandled Error:", err);
  res.status(500).json({
    error: "Internal Server Error",
    message:
      process.env.NODE_ENV === "production"
        ? "Something went wrong."
        : err.message,
  });
});

// Start server
const server = app.listen(PORT, () => {
  console.log(`🚀 Server is running on http://localhost:${PORT}`);
  console.log(`📡 Environment: ${process.env.NODE_ENV || "development"}`);
});

// Graceful shutdown handling
const handleShutdown = (signal: string) => {
  console.log(`\nReceived ${signal}. Shutting down gracefully...`);
  server.close(() => {
    console.log("HTTP server closed.");
    process.exit(0);
  });

  // Force close after 10s if not completed
  setTimeout(() => {
    console.error("Forcing shutdown after timeout.");
    process.exit(1);
  }, 10000);
};

process.on("SIGINT", () => handleShutdown("SIGINT"));
process.on("SIGTERM", () => handleShutdown("SIGTERM"));

export default app;
