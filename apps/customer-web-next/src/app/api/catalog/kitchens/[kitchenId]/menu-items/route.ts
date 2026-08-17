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
          ? "KITCHEN_NOT_FOUND"
          : status === 400
            ? "INVALID_KITCHEN_ID"
            : "KITCHEN_MENU_UNAVAILABLE",
      message,
    },
    { status },
  );
}

export async function GET(
  _request: NextRequest,
  context: { params: Promise<{ kitchenId: string }> },
) {
  const { kitchenId } = await context.params;
  if (!isUuid(kitchenId)) {
    return failure(400, "A valid kitchen ID is required.");
  }

  try {
    const [kitchenResponse, menuResponse] = await Promise.all([
      publicApiFetch(`/catalog/kitchens/${encodeURIComponent(kitchenId)}`),
      publicApiFetch(
        `/catalog/kitchens/${encodeURIComponent(kitchenId)}/menu-items`,
      ),
    ]);

    const kitchen = await kitchenResponse.json().catch(() => null);
    const menu = await menuResponse.json().catch(() => null);

    if (!kitchenResponse.ok) {
      return failure(
        kitchenResponse.status === 404 ? 404 : 502,
        kitchenResponse.status === 404
          ? "This home kitchen is no longer available."
          : "The home-kitchen details could not be loaded.",
      );
    }

    if (!menuResponse.ok) {
      return failure(502, "This kitchen's menu could not be loaded.");
    }

    if (!Array.isArray(menu) || menu.length > 500) {
      return failure(502, "Catalog returned an invalid kitchen menu.");
    }

    const items = menu.map((item) => parsePublicMenuItemDetail(item, kitchen));
    if (items.some((item) => item === null)) {
      return failure(502, "Catalog kitchen-menu response validation failed.");
    }

    return NextResponse.json(items, {
      headers: { "Cache-Control": "no-store" },
    });
  } catch (error) {
    const timeout = error instanceof Error && error.name === "AbortError";
    return failure(
      timeout ? 504 : 502,
      timeout
        ? "The kitchen menu request timed out."
        : "This kitchen's menu is temporarily unavailable.",
    );
  }
}
