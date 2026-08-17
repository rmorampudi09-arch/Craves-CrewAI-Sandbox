import { NextRequest, NextResponse } from "next/server";
import {
  isUuid,
  parsePublicMenuItemDetail,
} from "@/lib/public-menu-item-contract";
import { publicApiFetch } from "@/lib/public-api";

function failure(status: number, message: string) {
  return NextResponse.json(
    {
      error:
        status === 404
          ? "MENU_ITEM_NOT_FOUND"
          : status === 400
            ? "INVALID_MENU_ITEM_ID"
            : "CATALOG_ITEM_UNAVAILABLE",
      message,
    },
    { status },
  );
}

export async function GET(
  _request: NextRequest,
  context: { params: Promise<{ menuItemId: string }> },
) {
  const { menuItemId } = await context.params;
  if (!isUuid(menuItemId)) return failure(400, "A valid menu item ID is required.");

  try {
    const itemResponse = await publicApiFetch(
      `/catalog/menu-items/${encodeURIComponent(menuItemId)}`,
    );
    const item = await itemResponse.json().catch(() => null);
    if (!itemResponse.ok) {
      return failure(
        itemResponse.status === 404 ? 404 : 502,
        itemResponse.status === 404
          ? "This menu item is no longer available."
          : "The menu item could not be loaded.",
      );
    }

    const kitchenId =
      item && typeof item === "object" && "kitchenId" in item
        ? String(item.kitchenId)
        : "";
    if (!isUuid(kitchenId)) {
      return failure(502, "Catalog returned an invalid kitchen reference.");
    }

    const kitchenResponse = await publicApiFetch(
      `/catalog/kitchens/${encodeURIComponent(kitchenId)}`,
    );
    const kitchen = await kitchenResponse.json().catch(() => null);
    if (!kitchenResponse.ok) {
      return failure(502, "The home-kitchen details could not be loaded.");
    }

    const detail = parsePublicMenuItemDetail(item, kitchen);
    return detail
      ? NextResponse.json(detail, {
          headers: { "Cache-Control": "public, max-age=60, stale-while-revalidate=120" },
        })
      : failure(502, "Catalog item response validation failed.");
  } catch (error) {
    const timeout = error instanceof Error && error.name === "AbortError";
    return failure(
      timeout ? 504 : 502,
      timeout
        ? "The menu item request timed out."
        : "The menu item is temporarily unavailable.",
    );
  }
}
