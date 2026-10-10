import { Request, Response, NextFunction } from "express";
import { ZodError } from "zod";

export function errorHandler(
  err: Error,
  _req: Request,
  res: Response,
  _next: NextFunction,
) {
  console.error("[ErrorMiddleware] Unhandled Error:", err);

  if (err instanceof ZodError) {
    res.status(400).json({
      error: "Validation Error",
      details: err.errors.map((e) => ({
        path: e.path.join("."),
        message: e.message,
      })),
    });
    return;
  }

  res.status(500).json({
    error: "Internal Server Error",
    message:
      err.message || "An unexpected error occurred in the Context Engine.",
  });
}
