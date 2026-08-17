import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import { config } from './config.js';
import { healthRoutes } from './routes/health.js';
import { authRoutes } from './routes/auth.js';
import { catalogRoutes } from './routes/catalog.js';
import { adminRoutes } from './routes/admin.js';

const app = Fastify({ logger: true });

await app.register(helmet);
await app.register(cors, { origin: config.corsOrigins, credentials: true });
await app.register(healthRoutes);
await app.register(authRoutes);
await app.register(catalogRoutes);
await app.register(adminRoutes);

app.setErrorHandler((error, _req, reply) => {
  app.log.error(error);
  reply.code(500).send({ error: 'Internal server error', message: config.nodeEnv === 'development' ? error.message : 'Please try again later.' });
});

app.listen({ port: config.port, host: '0.0.0.0' });
