import { Router } from "express";

import { PersonalizationController } from "./personalization.controller";

export function createPersonalizationRoutes(
  controller: PersonalizationController,
): Router {
  const router = Router();

  router.post("/personalize", controller.personalize);

  router.post("/debug/personalization", controller.debug);

  return router;
}
