import { FastifyInstance } from 'fastify';

export async function healthRoutes(app: FastifyInstance) {
  app.get('/health', async () => ({ status: 'ok', service: 'craves-api' }));
  app.get('/api/v1/health', async () => ({ status: 'ok', service: 'craves-api', version: 'v1' }));
}
