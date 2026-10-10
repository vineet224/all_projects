import { Request, Response, NextFunction } from "express";
import { randomUUID } from "crypto";

import { logger } from "../utils/logger";

export function requestLogger(
  req: Request,
  res: Response,
  next: NextFunction,
): void {
  const requestId = randomUUID();

  const start = Date.now();

  res.setHeader("X-Request-Id", requestId);

  res.on("finish", () => {
    logger.info(
      {
        requestId,
        method: req.method,
        path: req.path,
        statusCode: res.statusCode,
        latencyMs: Date.now() - start,
      },
      "Request completed",
    );
  });

  next();
}
