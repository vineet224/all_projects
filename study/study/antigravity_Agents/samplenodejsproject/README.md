# Node.js + Express.js + TypeScript Starter

A modern, clean starter template for building REST APIs with Node.js, Express.js, and TypeScript.

## 🚀 Features

- **TypeScript**: Strict type checking with modern ES2022 target.
- **Express 4.x**: Minimal and flexible web framework.
- **Fast Development**: Powered by `tsx` for near-instant TypeScript execution and hot reloading.
- **Environment Configuration**: Pre-configured with `dotenv`.
- **Health Check & Error Handling**: Includes `/health` endpoint, 404 handler, and global error handling middleware.
- **Graceful Shutdown**: Handles `SIGINT` and `SIGTERM` signals cleanly.

## 📁 Project Structure

```
.
├── src/
│   └── index.ts        # Application entrypoint & routes
├── dist/               # Compiled JavaScript files (generated on build)
├── .env                # Local environment variables
├── .env.example        # Environment variable template
├── .gitignore          # Git ignore rules
├── package.json        # Dependencies and scripts
├── tsconfig.json       # TypeScript configuration
└── README.md           # Documentation
```

## 🛠️ Getting Started

### 1. Install Dependencies

```bash
npm install
```

### 2. Run in Development Mode

Runs the server with file watching and auto-reloading:

```bash
npm run dev
```

The server will start at `http://localhost:3000`.

### 3. Build for Production

Compiles TypeScript files into JavaScript in the `dist/` directory:

```bash
npm run build
```

### 4. Run in Production Mode

Starts the compiled JavaScript server:

```bash
npm start
```

## 📡 API Endpoints

- `GET /` - Welcome message
- `GET /health` - Service health status & uptime
- `GET /api/users` - Sample typed endpoint

Ok so in this daily dairy can please only read March2026 tab, APRIL 2026 tab, May 2026 , June 2026, July 2026, August 2026 tabs and pick this data from these tabse {date, starting study at, starting clock hour} these three things only pick from those tabs now from the excellogtillsep9 pick these details seperately {date,wake up time , study hours} seperately and inner join them on the date value so we should have a new list that has date with {date, wake up time, study hours, starting study at, starting clock hour} and add them in seperate table in a seperate tab can you do this let me know if you need anything
