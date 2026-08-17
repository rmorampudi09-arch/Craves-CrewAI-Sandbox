import { FastifyInstance } from 'fastify';
import { chefs, menuItems } from '../infrastructure/mockStore.js';

export async function catalogRoutes(app: FastifyInstance) {
  app.get('/api/v1/chefs', async () => chefs.filter(c => c.status === 'APPROVED'));
  app.get('/api/v1/menu', async () => menuItems);
  app.get('/api/v1/menu/:chefId', async (req: any) => menuItems.filter(m => m.chefId === req.params.chefId));
}
