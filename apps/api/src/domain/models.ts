export type UserRole = 'CUSTOMER' | 'CHEF' | 'ADMIN';
export type ChefStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
export type OrderStatus = 'PLACED' | 'CONFIRMED' | 'PREPARING' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'CANCELLED';

export interface User {
  id: string;
  phone: string;
  name: string;
  role: UserRole;
}

export interface Chef {
  id: string;
  userId: string;
  kitchenName: string;
  cuisine: string;
  rating: number;
  status: ChefStatus;
}

export interface MenuItem {
  id: string;
  chefId: string;
  name: string;
  description: string;
  price: number;
  category: string;
  isVeg: boolean;
  imageUrl: string;
}
