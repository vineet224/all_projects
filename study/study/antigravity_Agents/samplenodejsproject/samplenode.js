const express = require('express');
const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

app.get('/', (req, res) => {
  res.send('Hello, World! Simple Node.js server is running.');
});

app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

app.listen(PORT, () => {
  console.log(`Server is listening on port ${PORT}`);
});

/*
To initialize a Node.js + Express.js + TypeScript project, run the following commands
in your terminal from this directory:

  npm init -y
  npm install express
  npm install -D typescript ts-node @types/node @types/express nodemon
  npx tsc --init

Then update package.json scripts:
  "scripts": {
    "start": "node dist/server.js",
    "build": "tsc",
    "dev": "nodemon --exec ts-node src/server.ts"
  }

Recommended tsconfig.json compilerOptions:
  {
    "target": "ES2020",
    "module": "commonjs",
    "rootDir": "src",
    "outDir": "dist",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true
  }

Suggested project structure:
  project-root/
    src/
      server.ts
    dist/
    package.json
    tsconfig.json

Example src/server.ts:

  import express, { Request, Response } from 'express';

  const app = express();
  const PORT = process.env.PORT || 3000;

  app.use(express.json());

  app.get('/', (req: Request, res: Response) => {
    res.send('Hello, World! TypeScript + Express server is running.');
  });

  app.listen(PORT, () => {
    console.log(`Server is listening on port ${PORT}`);
  });
*/
