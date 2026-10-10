import { Request, Response, NextFunction } from "express";

export function requestLogger(req: Request, res: Response, next: NextFunction) {
  const start = Date.now();

  res.on("finish", () => {
    const duration = Date.now() - start;
    console.log(
      `[HTTP] ${req.method} ${req.originalUrl} | Status: ${res.statusCode} | Latency: ${duration}ms`,
    );
  });

  next();
}
