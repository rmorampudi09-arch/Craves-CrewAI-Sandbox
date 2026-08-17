import {
  isDeliveryReadyAddress,
  type CustomerAddress,
  type DeliveryReadyAddress,
} from "./address-contract.ts";

export function selectActiveDeliveryAddress(
  addresses: readonly CustomerAddress[],
): DeliveryReadyAddress | null {
  const ready = addresses.filter(isDeliveryReadyAddress);
  return ready.find((address) => address.isDefault)
    ?? ready[0]
    ?? null;
}
