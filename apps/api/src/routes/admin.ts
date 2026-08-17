import { FastifyInstance } from 'fastify';
import { chefs, menuItems, users } from '../infrastructure/mockStore.js';

export async function adminRoutes(app: FastifyInstance) {
  app.get('/api/v1/admin/summary', async () => ({
    users: users.length,
    chefs: chefs.length,
    pendingChefs: chefs.filter(c => c.status === 'PENDING_APPROVAL').length,
    menuItems: menuItems.length,
    ordersToday: 0,
    revenueToday: 0,
  }));

  app.get('/api/v1/admin/chefs', async () => chefs);

  app.post('/api/v1/admin/chefs/:id/approve', async (req: any, reply) => {
    const chef = chefs.find(c => c.id === req.params.id);
    if (!chef) return reply.code(404).send({ error: 'Chef not found' });
    chef.status = 'APPROVED';
    return chef;
  });
}
