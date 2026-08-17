import dotenv from 'dotenv';
dotenv.config();

export const config = {
  nodeEnv: process.env.NODE_ENV ?? 'development',
  port: Number(process.env.PORT ?? 8080),
  corsOrigins: (process.env.CORS_ORIGINS ?? 'http://localhost:5173,http://localhost:5174').split(',').map(x => x.trim()),
  jwtSecret: process.env.JWT_SECRET ?? 'dev-only-secret-change-me',
  databaseUrl: process.env.DATABASE_URL ?? '',
};
