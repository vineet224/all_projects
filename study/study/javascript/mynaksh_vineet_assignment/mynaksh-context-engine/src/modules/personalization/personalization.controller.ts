import { Request, Response, NextFunction } from "express";

import {
  PersonalizationDecision,
  PersonalizeRequest,
  PersonalizeResponse,
} from "../../types/personalization.types";

import {
  PersonalizationService,
  personalizeRequestSchema,
} from "./personalization.service";
import { logger } from "../../utils/logger";

export class PersonalizationController {
  constructor(private readonly service: PersonalizationService) {}

  personalize = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const request = personalizeRequestSchema.parse(
        req.body,
      ) as PersonalizeRequest;

      const response: PersonalizeResponse =
        await this.service.personalize(request);

      return res.json(response);
    } catch (error) {
      next(error);
    }
  };

  debug = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const request = personalizeRequestSchema.parse(
        req.body,
      ) as PersonalizeRequest;

      const response: PersonalizationDecision =
        await this.service.debug(request);

      return res.json(response);
    } catch (error) {
      next(error);
    }
  };
}
