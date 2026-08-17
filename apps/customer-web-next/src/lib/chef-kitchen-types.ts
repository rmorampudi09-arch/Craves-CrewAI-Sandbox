export type KitchenStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "SUSPENDED";
export type EditableKitchenStatus = "DRAFT" | "ACTIVE" | "INACTIVE";

export type ChefKitchen = {
  id: string;
  kitchenName: string;
  displayName: string | null;
  description: string | null;
  phoneNumber: string | null;
  email: string | null;
  addressLine1: string;
  addressLine2: string | null;
  landmark: string | null;
  areaName: string | null;
  city: string;
  state: string;
  postalCode: string | null;
  latitude: number | null;
  longitude: number | null;
  status: KitchenStatus;
  createdAt: string;
  updatedAt: string;
};

export type ChefKitchenInput = {
  kitchenName: string;
  displayName: string | null;
  description: string | null;
  phoneNumber: string | null;
  email: string | null;
  addressLine1: string;
  addressLine2: string | null;
  landmark: string | null;
  areaName: string | null;
  city: string;
  state: string;
  postalCode: string | null;
  latitude: number | null;
  longitude: number | null;
  status: EditableKitchenStatus;
};
