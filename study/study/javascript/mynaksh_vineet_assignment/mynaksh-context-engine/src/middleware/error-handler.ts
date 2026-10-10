import { Request, Response, NextFunction } from "express";

import { ZodError } from "zod";

import { logger } from "../utils/logger";

export function errorHandler(
  error: unknown,
  _req: Request,
  res: Response,
  _next: NextFunction,
): void {
  if (error instanceof ZodError) {
    console.error("Validation error: " + JSON.stringify(error.flatten()));
    res.status(400).json({
      error: {
        code: "VALIDATION_ERROR",
        message: "Invalid request",
        details: error.flatten(),
      },
    });

    return;
  }

  logger.error(
    {
      error: error instanceof Error ? error.message : error,
    },
    "Unhandled error",
  );

  res.status(500).json({
    error: {
      code: "INTERNAL_ERROR",
      message: "Please try again later",
    },
  });
}
