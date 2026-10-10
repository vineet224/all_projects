import { Router } from "express";

import {
  mockUsers,
  mockKundlis,
  mockHoroscopes,
  mockPanchang,
} from "./mock-data";

const router = Router(); //  TODO this is not used also partial failures ok so when my existing node js service will make an external http call will those first come to this router and then resolved here only by this mock or go to actual internet

router.get("/users/:userId", (req, res) => {
  const user = mockUsers[req.params.userId];

  if (!user) {
    return res.status(404).json({
      error: "User not found",
    });
  }

  return res.json(user);
});

router.get("/kundli/:userId", (req, res) => {
  const kundli = mockKundlis[req.params.userId];

  if (!kundli) {
    return res.status(404).json({
      error: "Kundli not found",
    });
  }

  return res.json(kundli);
});

router.get("/horoscope/:userId", (req, res) => {
  const horoscope = mockHoroscopes[req.params.userId];

  if (!horoscope) {
    return res.status(404).json({
      error: "Horoscope not found",
    });
  }

  return res.json(horoscope);
});

router.get("/panchang", (_req, res) => {
  return res.json(mockPanchang);
});

export default router;
