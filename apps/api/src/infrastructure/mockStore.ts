import { Chef, MenuItem, User } from '../domain/models.js';

export const users: User[] = [
  { id: 'u_customer_1', phone: '+919876543210', name: 'Rohan', role: 'CUSTOMER' },
  { id: 'u_admin_1', phone: '+918019166645', name: 'Raviteja', role: 'ADMIN' },
];

export const chefs: Chef[] = [
  { id: 'chef_1', userId: 'u_chef_1', kitchenName: "Meena's Kitchen", cuisine: 'North Indian • Home Style', rating: 4.8, status: 'APPROVED' },
  { id: 'chef_2', userId: 'u_chef_2', kitchenName: "Sarita's Kitchen", cuisine: 'South Indian • Traditional', rating: 4.9, status: 'APPROVED' },
  { id: 'chef_3', userId: 'u_chef_3', kitchenName: "Priya's Kitchen", cuisine: 'Gujarati • Thalis', rating: 4.7, status: 'PENDING_APPROVAL' },
];

export const menuItems: MenuItem[] = [
  { id: 'm1', chefId: 'chef_1', name: 'Paneer Butter Masala', description: 'Soft paneer cubes in rich tomato gravy.', price: 120, category: 'Lunch', isVeg: true, imageUrl: 'https://images.unsplash.com/photo-1631452180519-c014fe946bc7?q=80&w=800' },
  { id: 'm2', chefId: 'chef_2', name: 'Dal Tadka', description: 'Homely dal tempered with spices.', price: 90, category: 'Dinner', isVeg: true, imageUrl: 'https://images.unsplash.com/photo-1546833999-b9f581a1996d?q=80&w=800' },
  { id: 'm3', chefId: 'chef_1', name: 'Chicken Biryani', description: 'Hyderabadi-style dum biryani.', price: 150, category: 'Lunch', isVeg: false, imageUrl: 'https://images.unsplash.com/photo-1563379091339-03246963d29a?q=80&w=800' },
];
